package com.xconflictionx.callguardshield.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.CallLogEntry
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import com.xconflictionx.callguardshield.logic.BlockResult
import com.xconflictionx.callguardshield.logic.CallGuardShieldEngine
import com.xconflictionx.callguardshield.logic.CryptoManager
import com.xconflictionx.callguardshield.logic.GeminiPhoneLookupService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CallGuardShieldService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private val db by lazy { AppDatabase.getDatabase(applicationContext) }
    private val dao by lazy { db.callGuardShieldDao() }
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }
    private val engine by lazy { CallGuardShieldEngine(applicationContext, dao) }

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart
        val systemName = callDetails.callerDisplayName
        
        serviceScope.launch {
            try {
                val settings = settingsRepo.settingsFlow.first()
                val result = engine.shouldBlock(phoneNumber, settings)
                
                val responseBuilder = CallResponse.Builder()
                
                var isBlocked = result is BlockResult.Block
                var reason = if (result is BlockResult.Block) result.reason else null
                val isContact = when (result) {
                    is BlockResult.Allow -> result.isContact
                    is BlockResult.Block -> result.isContact
                }
                
                // 2. Real-Time AI Screening (if enabled and not already blocked/whitelisted)
                var aiResult: com.xconflictionx.callguardshield.data.entity.PhoneLookupResult? = null
                if (!isBlocked && !isContact && phoneNumber != null && settings.aiRealTimeBlocking) {
                    try {
                        val apiKey = CryptoManager.getGeminiApiKey(applicationContext)
                        if (!apiKey.isNullOrBlank()) {
                            val service = GeminiPhoneLookupService(applicationContext, apiKey, settings.selectedGeminiModel, dao)
                            // 2-second strict timeout for real-time blocking
                            aiResult = withTimeoutOrNull(2000) {
                                service.lookupRealTime(phoneNumber)
                            }
                            
                            aiResult?.let { intel ->
                                val confidence = intel.confidence ?: 0.0
                                val requiredConfidence = settings.aiBlockingConfidence / 100.0
                                val isHighConfidence = confidence >= requiredConfidence
                                
                                val isScamOrSpam = intel.scam || intel.spam
                                val isDebtCollectorMatch = settings.blockDebtCollectors && intel.debtCollector
                                val isTelemarketerMatch = settings.blockTelemarketers && intel.telemarketer
                                
                                val shouldAiBlock = isHighConfidence && (isScamOrSpam || isDebtCollectorMatch || isTelemarketerMatch)
                                
                                if (shouldAiBlock) {
                                    isBlocked = true
                                    reason = "AI: ${intel.category ?: "High Risk"} (${(confidence * 100).toInt()}%)"
                                } else if (isHighConfidence) {
                                    // It matched a risk category, but the specific shield is disabled
                                    if (intel.debtCollector) {
                                        Log.i("SERVICE_AI", "Detected Debt Collector but block setting is OFF")
                                    }
                                    if (intel.telemarketer) {
                                        Log.i("SERVICE_AI", "Detected Telemarketer but block setting is OFF")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SERVICE_AI", "Real-time AI check failed/timed out", e)
                    }
                }

                // 3. Log entry with the best data we have
                val logEntry = CallLogEntry(
                    number = phoneNumber ?: "Unknown",
                    isBlocked = isBlocked,
                    reason = reason,
                    isContact = isContact,
                    callerId = systemName,
                    ownerName = aiResult?.ownerName,
                    companyName = aiResult?.companyName,
                    callerInfo = aiResult?.let { 
                        "Risk: ${if (it.scam) "HIGH" else if (it.spam) "MEDIUM" else "LOW"} • Acc: ${(it.confidence?.times(100))?.toInt()}%"
                    }
                )
                
                dao.insertCallLogEntry(logEntry)
                dao.trimCallLog(100)

                if (isBlocked) {
                    responseBuilder.apply {
                        setDisallowCall(true)
                        setRejectCall(true)
                        setSkipNotification(true)
                        setSkipCallLog(true)
                    }
                } else {
                    // If not blocked but we have AI data, update history in background with full details
                    if (aiResult != null && phoneNumber != null) {
                        launch {
                            val bestName = aiResult.companyName ?: aiResult.ownerName ?: "Unknown"
                            val formattedInfo = buildString {
                                append("Risk: ${if (aiResult.scam) "HIGH" else if (aiResult.spam) "MEDIUM" else "LOW"} • ")
                                append("Acc: ${(aiResult.confidence?.times(100))?.toInt()}% • ")
                                append(aiResult.summary?.take(60))
                            }
                            dao.updateCallLogByNumber(phoneNumber, bestName, aiResult.ownerName, aiResult.companyName, formattedInfo)
                        }
                    }
                    
                    responseBuilder.apply {
                        setDisallowCall(false)
                        setRejectCall(false)
                        setSkipNotification(false)
                        setSkipCallLog(false)
                    }
                }
                
                respondToCall(callDetails, responseBuilder.build())
            } catch (e: Exception) {
                respondToCall(callDetails, CallResponse.Builder().setDisallowCall(false).build())
            }
        }
    }
}
