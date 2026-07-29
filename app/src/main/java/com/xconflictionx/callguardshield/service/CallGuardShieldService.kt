package com.xconflictionx.callguardshield.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.CallLogEntry
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import com.xconflictionx.callguardshield.logic.BlockResult
import com.xconflictionx.callguardshield.logic.CallGuardShieldEngine
import com.xconflictionx.callguardshield.logic.CryptoManager
import com.xconflictionx.callguardshield.logic.GeminiPhoneLookupService
import com.xconflictionx.callguardshield.logic.PhoneHelper
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
                
                // 2. Real-Time AI Screening & Background Identification
                var aiResult: com.xconflictionx.callguardshield.data.entity.PhoneLookupResult? = null
                if (!isContact && phoneNumber != null) {
                    val apiKey = CryptoManager.getGeminiApiKey(applicationContext)
                    if (!apiKey.isNullOrBlank()) {
                        // Start the scan immediately
                        val scanJob = serviceScope.async {
                            val service = GeminiPhoneLookupService(applicationContext, apiKey, settings.selectedGeminiModel, dao)
                            service.lookupRealTime(phoneNumber)
                        }

                        // If AI blocking is enabled, wait for a result to decide
                        if (!isBlocked && settings.aiRealTimeBlocking) {
                            try {
                                aiResult = withTimeoutOrNull(3000) { scanJob.await() }
                                
                                aiResult?.let { intel ->
                                    val accuracy = intel.accuracy
                                    val requiredAccuracy = settings.aiBlockingAccuracy
                                    val isHighAccuracy = accuracy >= requiredAccuracy
                                    
                                    val isScamOrSpam = intel.scam || intel.spam
                                    val isDebtCollectorMatch = settings.blockDebtCollectors && intel.debtCollector
                                    val isTelemarketerMatch = settings.blockTelemarketers && intel.telemarketer
                                    
                                    if (isHighAccuracy && (isScamOrSpam || isDebtCollectorMatch || isTelemarketerMatch)) {
                                        isBlocked = true
                                        reason = "AI: ${intel.category ?: "High Risk"} ($accuracy%)"
                                    }
                                }
                            } catch (e: Exception) { }
                        }

                        // If we don't have a result yet (timed out or blocking OFF), continue scan in background
                        if (aiResult == null) {
                            serviceScope.launch {
                                try {
                                    val res = scanJob.await()
                                    if (res != null) {
                                        dao.insertLookupResult(res) // Ensure cached for details sheet
                                        val bestName = res.manualLabel ?: res.companyName ?: res.ownerName ?: "Unknown"
                                        val formattedInfo = buildString {
                                            append("Risk: ${if (res.scam) "HIGH" else if (res.spam) "MEDIUM" else "LOW"} • ")
                                            append("Accuracy: ${res.accuracy}% • ")
                                            append(res.summary?.take(60))
                                        }
                                        val normalized = PhoneHelper.normalizeToE164(phoneNumber)
                                        dao.updateCallLogByNumber(normalized, bestName, res.ownerName, res.companyName, formattedInfo)
                                    }
                                } catch (e: Exception) { }
                            }
                        }
                    }
                }

                // 3. Log entry with the best data we have
                val normalizedNumber = phoneNumber?.let { PhoneHelper.normalizeToE164(it) } ?: "Unknown"
                val logEntry = CallLogEntry(
                    number = normalizedNumber,
                    isBlocked = isBlocked,
                    reason = reason,
                    isContact = isContact,
                    callerId = systemName,
                    ownerName = aiResult?.ownerName,
                    companyName = aiResult?.companyName,
                    callerInfo = aiResult?.let { 
                        "Risk: ${if (it.scam) "HIGH" else if (it.spam) "MEDIUM" else "LOW"} • Accuracy: ${it.accuracy}%"
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
                    if (aiResult != null) {
                        launch {
                            val bestName = aiResult.manualLabel ?: aiResult.companyName ?: aiResult.ownerName ?: "Unknown"
                            val formattedInfo = buildString {
                                append("Risk: ${if (aiResult.scam) "HIGH" else if (aiResult.spam) "MEDIUM" else "LOW"} • ")
                                append("Accuracy: ${aiResult.accuracy}% • ")
                                append(aiResult.summary?.take(60))
                            }
                            dao.updateCallLogByNumber(normalizedNumber, bestName, aiResult.ownerName, aiResult.companyName, formattedInfo)
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
