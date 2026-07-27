package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_log")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean,
    val reason: String? = null,
    val isContact: Boolean,
    val callerInfo: String? = null
)
