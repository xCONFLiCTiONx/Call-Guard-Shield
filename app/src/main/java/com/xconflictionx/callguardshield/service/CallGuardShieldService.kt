package com.xconflictionx.callguardshield.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.CallLogEntry
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import com.xconflictionx.callguardshield.logic.BlockResult
import com.xconflictionx.callguardshield.logic.CallGuardShieldEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallGuardShieldService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart
        
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val settingsRepo = SettingsRepository(applicationContext)
            val engine = CallGuardShieldEngine(applicationContext, db.callGuardShieldDao())
            
            val settings = settingsRepo.settingsFlow.first()
            val result = engine.shouldBlock(phoneNumber, settings)
            
            val responseBuilder = CallResponse.Builder()
            
            val isBlocked = result is BlockResult.Block
            val reason = if (result is BlockResult.Block) result.reason else null
            val isContact = when (result) {
                is BlockResult.Allow -> result.isContact
                is BlockResult.Block -> result.isContact
            }

            // Log EVERY call
            val dao = db.callGuardShieldDao()
            dao.insertCallLogEntry(
                CallLogEntry(
                    number = phoneNumber ?: "Unknown",
                    isBlocked = isBlocked,
                    reason = reason,
                    isContact = isContact
                )
            )
            
            // Trim history to 100 entries
            dao.trimCallLog(100)

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
        }
    }
}
