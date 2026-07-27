package com.xconflictionx.callguardshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.BlockedCall
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import com.xconflictionx.callguardshield.logic.BlockResult
import com.xconflictionx.callguardshield.logic.CallGuardShieldEngine
import com.xconflictionx.callguardshield.logic.PhoneHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsFilterReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val sender = messages.firstOrNull()?.originatingAddress ?: return

            scope.launch {
                val db = AppDatabase.getDatabase(context)
                val settingsRepo = SettingsRepository(context)
                val engine = CallGuardShieldEngine(context, db.callGuardShieldDao())
                
                val normalizedSender = PhoneHelper.normalizeToE164(sender)
                val settings = settingsRepo.settingsFlow.first()
                val result = engine.shouldBlock(normalizedSender, settings)
                
                if (result is BlockResult.Block) {
                    // Log the blocked SMS as a "Blocked Call/Msg"
                    db.callGuardShieldDao().insertBlockedCall(
                        BlockedCall(
                            number = sender,
                            reason = "SMS: ${result.reason}"
                        )
                    )
                    
                    // NOTE: Cannot abortBroadcast() on modern Android if not default app
                }
            }
        }
    }
}
