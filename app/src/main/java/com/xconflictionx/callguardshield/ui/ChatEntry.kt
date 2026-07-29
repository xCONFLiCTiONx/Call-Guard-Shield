package com.xconflictionx.callguardshield.ui

import com.xconflictionx.callguardshield.data.entity.PhoneLookupResult

sealed class ChatEntry {
    data class UserMessage(val text: String) : ChatEntry()
    data class IntelReport(
        val result: PhoneLookupResult, 
        val wasAutoApplied: Boolean = true,
        val oldAccuracy: Int? = null
    ) : ChatEntry()
    data class ErrorMessage(val text: String) : ChatEntry()
}
