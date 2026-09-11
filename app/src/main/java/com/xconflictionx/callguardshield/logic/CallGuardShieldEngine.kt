package com.xconflictionx.callguardshield.logic

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import com.xconflictionx.callguardshield.data.dao.CallGuardShieldDao
import com.xconflictionx.callguardshield.data.repository.UserSettings

class CallGuardShieldEngine(
    private val context: Context,
    private val dao: CallGuardShieldDao
) {

    suspend fun shouldBlock(phoneNumber: String?, settings: UserSettings): BlockResult {
        if (settings.isPaused) return BlockResult.Allow(isContact = false)

        if (phoneNumber.isNullOrEmpty()) {
            return if (settings.blockUnknown) {
                BlockResult.Block("Unknown Number", isContact = false)
            } else {
                BlockResult.Allow(isContact = false)
            }
        }

        // Normalize incoming number for matching (E.164)
        val normalizedIncoming = PhoneHelper.normalizeToE164(phoneNumber)

        // 1. Whitelist (Highest Priority) - Absolute bypass (if enabled)
        if (settings.whitelistEnabled) {
            val whitelistMatch = dao.findWhitelistMatch(normalizedIncoming)
            if (whitelistMatch != null) {
                return BlockResult.Allow(isContact = false)
            }
        }

        // 2. Contacts (Allows known people) - Absolute bypass
        val isContact = isNumberInContacts(phoneNumber)
        if (isContact) {
            return BlockResult.Allow(isContact = true)
        }

        // 3. Rules for non-contacts - From here on, settings apply
        if (settings.allowOnlyContacts) {
            return BlockResult.Block("Not in Contacts", isContact = false)
        }

        // 4. Blacklist (Manual blocking) (if enabled)
        if (settings.blacklistEnabled) {
            val blacklistMatch = dao.findBlacklistMatch(normalizedIncoming)
            if (blacklistMatch != null) {
                val label = blacklistMatch.label?.let { " ($it)" } ?: ""
                return BlockResult.Block("Blacklisted Match$label", isContact = false)
            }
        }

        // 5. Global Spam DB (Community blocking)
        if (settings.enabledDictionaries.contains("global")) {
            val globalSpamMatch = dao.findGlobalSpamByPattern(normalizedIncoming)
            if (globalSpamMatch != null) {
                return BlockResult.Block(globalSpamMatch.label, isContact = false)
            }
        }

        // 6. International Blocking
        if (settings.blockInternational && isInternational(phoneNumber)) {
            return BlockResult.Block("International Call", isContact = false)
        }

        return BlockResult.Allow(isContact = false)
    }

    private fun isNumberInContacts(phoneNumber: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(phoneNumber)
            .build()
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun isInternational(number: String): Boolean {
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        return normalized.startsWith("+") && !normalized.startsWith("+1")
    }
}
