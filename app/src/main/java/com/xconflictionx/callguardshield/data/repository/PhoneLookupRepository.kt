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
        val normalized = phoneNumber.filter { it.isDigit() }
        
        // 1. Check Cache
        if (!forceRefresh) {
            val cached = dao.getLookupResult(normalized)
            val settings = settingsRepo.settingsFlow.first()
            if (cached != null && isCacheValid(cached, settings.cacheAgeDays)) {
                return cached
            }
        }

        // 2. Perform fresh lookup via Gemini
        val apiKey = CryptoManager.getGeminiApiKey(context) ?: return null
        val settings = settingsRepo.settingsFlow.first()
        val provider = GeminiPhoneLookupService(context, apiKey, settings.selectedGeminiModel)
        val variations = NumberNormalizer.getVariations(phoneNumber)

        val result = provider.lookup(variations)
        
        if (result != null) {
            dao.insertLookupResult(result.copy(phoneNumber = normalized))
        }
        
        return result
    }

    private fun isCacheValid(result: PhoneLookupResult, ageDays: Int): Boolean {
        val expiryMillis = ageDays.toLong() * 24 * 60 * 60 * 1000
        return (System.currentTimeMillis() - result.lookupDate) < expiryMillis
    }

    suspend fun clearCache() {
        dao.clearLookupCache()
    }
}
