package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "global_spam",
    indices = [Index(value = ["pattern"])]
)
data class GlobalSpamEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val label: String,
    val dictionaryId: String // e.g., "global", "debt", "marketing"
)
