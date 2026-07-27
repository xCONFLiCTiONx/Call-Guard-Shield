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
                val currentLabel = if (entry is BlacklistEntry) entry.label else (entry as WhitelistEntry).label

                // Update Progress for UI
                val progress = (index + 1).toFloat() / listToIdentify.size
                setProgress(workDataOf("progress" to progress, "number" to number))

                // Check for manual labels - don't overwrite if it's already a decent name
                // unless it's the default "Manual Block", "Imported", etc.
                val isGenericLabel = currentLabel == null || 
                    currentLabel == "Manual Block" || 
                    currentLabel == "Imported" || 
                    currentLabel == "Allowed Caller"

                if (isGenericLabel) {
                    try {
                        // This service already has internal 30-day caching, 
                        // so it will skip rescanning automatically.
                        val result = service.lookup(setOf(number))
                        if (result != null) {
                            val bestName = result.companyName ?: result.ownerName ?: "Unknown"
                            
                            // Only update if we found something better than "Unknown"
                            if (bestName != "Unknown") {
                                val formattedInfo = buildString {
                                    append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
                                    append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
                                    append(result.summary?.take(60))
                                }
                                
                                if (isBlacklist) dao.updateBlacklistLabelByNumber(number, bestName)
                                else dao.updateWhitelistLabelByNumber(number, bestName)
                                
                                dao.updateCallLogByNumber(number, bestName, formattedInfo)
                                Log.i("BULK_WORKER", "Updated $number to $bestName")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BULK_WORKER", "Failed on $number: ${e.message}")
                    }

                    // Rate limit protection - only delay if we actually made a network request
                    // (The service returns instantly if cached)
                    if (index < listToIdentify.size - 1) {
                        delay(3500)
                    }
                }
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("BULK_WORKER", "Critical failure", e)
            return Result.retry()
        }
    }
}
