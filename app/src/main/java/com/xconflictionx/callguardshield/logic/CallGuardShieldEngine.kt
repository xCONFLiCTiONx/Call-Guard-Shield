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
    private val locationManager = LocationManager(context)

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

        // 1. Whitelist (Highest Priority)
        val whitelistMatch = dao.findWhitelistMatch(normalizedIncoming)
        if (whitelistMatch != null) {
            return BlockResult.Allow(isContact = false)
        }

        // 2. Contacts (Allows known people)
        val isContact = isNumberInContacts(phoneNumber)
        if (isContact) {
            return BlockResult.Allow(isContact = true)
        }

        // 3. Rules for non-contacts
        if (settings.blockNonContacts) {
            return BlockResult.Block("Not in Contacts", isContact = false)
        }

        // 4. Blacklist (Indexed Prefix Match)
        val blacklistMatch = dao.findBlacklistMatch(normalizedIncoming)
        if (blacklistMatch != null) {
            val label = blacklistMatch.label?.let { " ($it)" } ?: ""
            return BlockResult.Block("Blacklisted Match$label", isContact = false)
        }

        // 5. Global Spam (Indexed Prefix Match)
        if (settings.enabledDictionaries.contains("global")) {
            val globalSpamMatch = dao.findGlobalSpamByPattern(normalizedIncoming)
            if (globalSpamMatch != null) {
                return BlockResult.Block(globalSpamMatch.label, isContact = false)
            }
        }

        // 6. Dynamic "Out of State" Blocking
        val areaCode = extractAreaCode(phoneNumber)
        if (areaCode != null) {
            val callerState = AreaCodeManager.getStateForAreaCode(areaCode)
            if (settings.blockNonArkansas && callerState != null) {
                val myState = locationManager.getCurrentState() ?: "Arkansas"
                if (callerState != myState) {
                    return BlockResult.Block("Out of State ($callerState)", isContact = false)
                }
            }
        }

        // 7. International
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

    private fun extractAreaCode(number: String): String? {
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        val cleanNumber = if (normalized.startsWith("+1")) normalized.substring(2) else normalized
        return if (cleanNumber.length >= 10) {
            cleanNumber.substring(0, 3)
        } else null
    }

    private fun isInternational(number: String): Boolean {
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        return normalized.startsWith("+") && !normalized.startsWith("+1")
    }
}
