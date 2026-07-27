package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blacklist",
    indices = [Index(value = ["pattern"])]
)
data class BlacklistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val label: String? = null,
    val source: String = "manual" // e.g., "manual", "global", "debt", "marketing"
)
