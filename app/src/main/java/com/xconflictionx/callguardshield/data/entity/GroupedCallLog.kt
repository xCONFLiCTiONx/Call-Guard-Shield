package com.xconflictionx.callguardshield.data.entity

data class RawGroupedLog(
    val number: String,
    val count: Int,
    val lastTimestamp: Long,
    val csvTimestamps: String
)

data class GroupedEnrichedCallLog(
    val number: String,
    val label: String?, // The raw label from Blacklist/Whitelist table
    val count: Int,
    val lastTimestamp: Long,
    val allTimestamps: List<Long>,
    val headline: String,
    val formattedInfo: String?,
    val isBlocked: Boolean,
    val isContact: Boolean,
    val isInBlacklist: Boolean,
    val isInWhitelist: Boolean,
    val intel: PhoneLookupResult?
)
