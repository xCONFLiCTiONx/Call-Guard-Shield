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
        val isMaintenance = inputData.getBoolean("isMaintenance", false)
        val listType = if (isBlacklist) "Blacklist" else "Whitelist"
        Log.i("BULK_WORKER", "Starting Bulk Identify (Maintenance=$isMaintenance) for $listType")
        delay(500) // Brief delay for UI feedback
        
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.callGuardShieldDao()
        val settingsRepo = SettingsRepository(applicationContext)
        
        try {
            val settings = settingsRepo.settingsFlow.first()
            val apiKey = CryptoManager.getGeminiApiKey(applicationContext)
            if (apiKey.isNullOrBlank()) {
                Log.e("BULK_WORKER", "Aborting: Gemini API Key missing in Settings")
                return Result.failure()
            }
            val service = GeminiPhoneLookupService(applicationContext, apiKey, settings.selectedGeminiModel, dao)
            
            val listToIdentify = if (isBlacklist) dao.getBlacklistSync() else dao.getWhitelistSync()
            Log.i("BULK_WORKER", "Found ${listToIdentify.size} entries in $listType")
            
            if (listToIdentify.isEmpty()) {
                Log.i("BULK_WORKER", "Nothing to process (list is empty)")
                return Result.success()
            }

            var processedCount = 0
            listToIdentify.forEachIndexed { index, entry ->
                val number = if (entry is BlacklistEntry) entry.pattern else (entry as WhitelistEntry).number

                // Update Progress for UI
                val progress = (index + 1).toFloat() / listToIdentify.size
                setProgress(workDataOf("progress" to progress)) // Don't set number yet

                try {
                    // Respect 30-day cache logic in service (forceRefresh=false)
                    // This will hit Gemini if data is > 30 days old OR if info is missing (Unknown)
                    val result = service.lookup(setOf(number), forceRefresh = false)
                    
                    if (result != null) {
                        if (result.isCached) {
                            Log.d("BULK_WORKER", "Skipping $number (Data is < 30 days old)")
                        } else {
                            // Fresh from Gemini
                            processedCount++
                            setProgress(workDataOf("progress" to progress, "number" to number))
                            
                            val bestName = result.companyName ?: result.ownerName ?: "Unknown"
                            if (bestName != "Unknown") {
                                val formattedInfo = buildString {
                                    append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
                                    append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
                                    append(result.summary?.take(60))
                                }
                                
                                if (isBlacklist) dao.updateBlacklistLabelByNumber(number, bestName)
                                else dao.updateWhitelistLabelByNumber(number, bestName)
                                
                                dao.updateCallLogByNumber(number, bestName, result.ownerName, result.companyName, formattedInfo)
                                Log.i("BULK_WORKER", "Updated $number to $bestName")
                            }
                            
                            // Rate limit protection - only delay if we actually hit the network
                            delay(2000)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BULK_WORKER", "Failed on $number: ${e.message}")
                }
            }
            
            Log.i("BULK_WORKER", "Bulk Identify completed. Processed $processedCount items.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("BULK_WORKER", "Critical failure during bulk processing", e)
            return Result.retry()
        }
    }
}
