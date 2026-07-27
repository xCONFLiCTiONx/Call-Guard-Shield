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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                
                val isBlocked = result is BlockResult.Block
                val reason = if (result is BlockResult.Block) result.reason else null
                val isContact = when (result) {
                    is BlockResult.Allow -> result.isContact
                    is BlockResult.Block -> result.isContact
                }

                // Log entry first with system data
                val logEntry = CallLogEntry(
                    number = phoneNumber ?: "Unknown",
                    isBlocked = isBlocked,
                    reason = reason,
                    isContact = isContact,
                    callerId = systemName
                )
                
                val entryId = dao.insertCallLogEntry(logEntry)
                dao.trimCallLog(100)

                // Background Intel (Non-blocking)
                if (phoneNumber != null && !isContact) {
                    launch {
                        try {
                            val apiKey = CryptoManager.getGeminiApiKey(applicationContext)
                            if (!apiKey.isNullOrBlank()) {
                                val service = GeminiPhoneLookupService(applicationContext, apiKey, settings.selectedGeminiModel, dao)
                                val intel = service.lookup(setOf(phoneNumber))
                                intel?.let {
                                    val bestName = it.companyName ?: it.ownerName ?: "Unknown"
                                    val formattedInfo = buildString {
                                        append("Risk: ${if (it.scam) "HIGH" else if (it.spam) "MEDIUM" else "LOW"} • ")
                                        append("Acc: ${(it.confidence?.times(100))?.toInt()}% • ")
                                        append(it.summary?.take(60))
                                    }
                                    dao.updateCallLogDetailed(entryId, bestName, formattedInfo)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SERVICE_INTEL", "Bg intel failed", e)
                        }
                    }
                }

                if (isBlocked) {
                    responseBuilder.apply {
                        setDisallowCall(true)
                        setRejectCall(true)
                        setSkipNotification(true)
                        setSkipCallLog(true)
                    }
                } else {
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
