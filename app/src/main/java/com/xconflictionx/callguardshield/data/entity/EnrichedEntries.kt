package com.xconflictionx.callguardshield.data.entity

import androidx.room.Embedded
import androidx.room.Relation

interface EnrichedEntity {
    val number: String
    val label: String?
    val intel: PhoneLookupResult?

    val headline: String get() = intel?.manualLabel 
        ?: label 
        ?: intel?.companyName 
        ?: intel?.ownerName 
        ?: when {
            intel?.scam == true -> "Confirmed Scam"
            intel?.spam == true -> "Potential Scam"
            intel?.debtCollector == true -> "Debt Collector"
            intel?.telemarketer == true -> "Telemarketer"
            else -> number
        }

    val formattedInfo: String? get() = intel?.let { res ->
        buildString {
            append("Risk: ${if (res.scam) "HIGH" else if (res.spam) "MEDIUM" else "LOW"} • ")
            append("Accuracy: ${res.accuracy}% • ")
            append(res.summary?.take(60))
        }
    }
}

data class EnrichedCallLog(
    @Embedded val log: CallLogEntry,
    @Relation(
        parentColumn = "number",
        entityColumn = "phoneNumber"
    )
    override val intel: PhoneLookupResult?
) : EnrichedEntity {
    override val number: String get() = log.number
    override val label: String? get() = log.callerName // Maps to history's name field
}

data class EnrichedBlacklist(
    @Embedded val entry: BlacklistEntry,
    @Relation(
        parentColumn = "pattern",
        entityColumn = "phoneNumber"
    )
    override val intel: PhoneLookupResult?
) : EnrichedEntity {
    override val number: String get() = entry.pattern
    override val label: String? get() = entry.label
}

data class EnrichedWhitelist(
    @Embedded val entry: WhitelistEntry,
    @Relation(
        parentColumn = "number",
        entityColumn = "phoneNumber"
    )
    override val intel: PhoneLookupResult?
) : EnrichedEntity {
    override val number: String get() = entry.number
    override val label: String? get() = entry.label
}
