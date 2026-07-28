package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "blacklist"
)
data class BlacklistEntry(
    @PrimaryKey val pattern: String,
    val label: String? = null,
    val source: String = "manual" // e.g., "manual", "global", "debt", "marketing"
)
