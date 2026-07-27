package com.xconflictionx.callguardshield.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.BlacklistEntry
import com.xconflictionx.callguardshield.data.entity.WhitelistEntry
import com.xconflictionx.callguardshield.logic.CryptoManager
import com.xconflictionx.callguardshield.logic.GeminiPhoneLookupService
import com.xconflictionx.callguardshield.logic.PhoneHelper
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class BulkIdentifyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isBlacklist = inputData.getBoolean("isBlacklist", true)
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.callGuardShieldDao()
        val settingsRepo = SettingsRepository(applicationContext)
        
        try {
            val settings = settingsRepo.settingsFlow.first()
            val apiKey = CryptoManager.getGeminiApiKey(applicationContext) ?: return Result.failure()
            val service = GeminiPhoneLookupService(applicationContext, apiKey, settings.selectedGeminiModel, dao)
            
            val listToIdentify = if (isBlacklist) dao.getBlacklistSync() else dao.getWhitelistSync()
            
            if (listToIdentify.isEmpty()) return Result.success()

            listToIdentify.forEachIndexed { index, entry ->
                val number = if (entry is BlacklistEntry) entry.pattern else (entry as WhitelistEntry).number
                
                // Update Progress for UI
                val progress = (index + 1).toFloat() / listToIdentify.size
                setProgress(workDataOf("progress" to progress, "number" to number))

                try {
                    // This service already has internal 30-day caching, 
                    // so it will skip rescanning automatically.
                    val result = service.lookup(setOf(number))
                    if (result != null) {
                        val bestName = result.companyName ?: result.ownerName ?: "Unknown"
                        val formattedInfo = buildString {
                            append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
                            append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
                            append(result.summary?.take(60))
                        }
                        
                        if (isBlacklist) dao.updateBlacklistLabelByNumber(number, bestName)
                        else dao.updateWhitelistLabelByNumber(number, bestName)
                        
                        dao.updateCallLogByNumber(number, bestName, formattedInfo)
                    }
                } catch (e: Exception) {
                    Log.e("BULK_WORKER", "Failed on $number: ${e.message}")
                }

                // Rate limit protection
                if (index < listToIdentify.size - 1) {
                    delay(4000)
                }
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("BULK_WORKER", "Critical failure", e)
            return Result.retry()
        }
    }
}
