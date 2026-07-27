package com.xconflictionx.callguardshield.data.repository

import android.content.Context
import com.xconflictionx.callguardshield.data.dao.CallGuardShieldDao
import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult
import com.xconflictionx.callguardshield.logic.*
import kotlinx.coroutines.flow.first

class PhoneLookupRepository(
    private val context: Context,
    private val dao: CallGuardShieldDao
) {
    private val settingsRepo = SettingsRepository(context)

    suspend fun lookup(phoneNumber: String, forceRefresh: Boolean = false): PhoneLookupResult? {
        val apiKey = CryptoManager.getGeminiApiKey(context) ?: return null
        val settings = settingsRepo.settingsFlow.first()
        
        // Use the Service which now handles internal caching and rotation
        val service = GeminiPhoneLookupService(context, apiKey, settings.selectedGeminiModel, dao)
        
        // We use PhoneHelper for consistency across the app
        val variations = setOf(phoneNumber, PhoneHelper.normalizeToE164(phoneNumber))
        
        return if (forceRefresh) {
            service.lookupDeep(phoneNumber)
        } else {
            service.lookup(variations)
        }
    }

    suspend fun clearCache() {
        dao.clearLookupCache()
    }
}
