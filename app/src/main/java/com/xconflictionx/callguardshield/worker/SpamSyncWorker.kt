package com.xconflictionx.callguardshield.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.GlobalSpamEntry
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import com.xconflictionx.callguardshield.logic.PhoneHelper
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

class SpamSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val isForce = inputData.getBoolean("force", false)
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.callGuardShieldDao()
            val settingsRepo = SettingsRepository(applicationContext)
            val settings = settingsRepo.settingsFlow.first()
            
            // If the database setting is OFF, stop (but keep existing data cached)
            if (!settings.enabledDictionaries.contains("global")) {
                return Result.success()
            }

            // Smart Sync: Skip if not forced and updated recently (less than 23 hours ago)
            val lastSync = settings.lastSyncTime
            val now = System.currentTimeMillis()
            if (!isForce && now - lastSync < 23 * 60 * 60 * 1000) {
                return Result.success()
            }
            
            // Otherwise, perform the download
            val newEntries = mutableListOf<GlobalSpamEntry>()
            
            // 1. Fetch FCC Data (Live Reports)
            val fccJson = fetchUrl("https://opendata.fcc.gov/resource/sr6c-syda.json?\$select=caller_id_number,issue&\$where=caller_id_number%20IS%20NOT%20NULL&\$limit=2000")
            if (fccJson != null) {
                val arr = JSONArray(fccJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val number = obj.getString("caller_id_number")
                    val label = obj.optString("issue", "FCC Reported Spam")
                    val normalized = PhoneHelper.normalizeToE164(number)
                    if (normalized.isNotEmpty()) {
                        newEntries.add(GlobalSpamEntry(pattern = normalized, label = label, dictionaryId = "global"))
                    }
                }
            }

            // 2. Fetch CallShield Hot Ranges (Optimized Prefixes)
            val rangesJson = fetchUrl("https://raw.githubusercontent.com/SysAdminDoc/CallShield/main/data/hot_ranges.json")
            if (rangesJson != null) {
                try {
                    val arr = JSONArray(rangesJson)
                    for (i in 0 until arr.length()) {
                        val prefix = arr.getString(i)
                        val normalized = PhoneHelper.normalizeToE164(prefix)
                        if (normalized.isNotEmpty()) {
                            newEntries.add(GlobalSpamEntry(pattern = normalized, label = "Verified Robocall Prefix", dictionaryId = "global"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SPAM_SYNC", "Failed to parse hot ranges", e)
                }
            }

            // Only clear and update if we actually got some data
            if (newEntries.isNotEmpty()) {
                dao.deleteGlobalSpamByDictionary("global")
                newEntries.forEach { dao.insertGlobalSpamEntry(it) }
                Log.i("SPAM_SYNC", "Sync complete. Added ${newEntries.size} entries.")
            }
            
            settingsRepo.updateLastSyncTime(now)
            settingsRepo.setFirstRunSyncComplete(true)
            Result.success()
        } catch (e: Exception) {
            Log.e("SPAM_SYNC", "Sync failed", e)
            Result.retry()
        }
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            if (conn.responseCode == 200) {
                val scanner = Scanner(conn.inputStream).useDelimiter("\\A")
                if (scanner.hasNext()) scanner.next() else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
