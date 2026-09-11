package com.xconflictionx.callguardshield.data.entity

import com.xconflictionx.callguardshield.data.repository.UserSettings

/**
 * A container object used for exporting and importing the entire application database.
 */
data class BackupContainer(
    val blacklist: List<BlacklistEntry> = emptyList(),
    val whitelist: List<WhitelistEntry> = emptyList(),
    val callLogs: List<CallLogEntry> = emptyList(),
    val blockedCalls: List<BlockedCall> = emptyList(),
    val prefixBlocks: List<PrefixBlock> = emptyList(),
    val lookupCache: List<PhoneLookupResult> = emptyList(),
    val geminiApiKey: String? = null,
    val settings: UserSettings? = null,
    val exportDate: Long = System.currentTimeMillis(),
    val version: Int = 1
)
