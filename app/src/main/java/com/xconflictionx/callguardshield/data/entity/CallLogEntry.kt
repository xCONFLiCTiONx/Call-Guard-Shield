package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_log")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val number: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean,
    val reason: String? = null,
    val isContact: Boolean,
    val callerId: String? = null, // System provided name
    val callerName: String? = null, // Gemini or Contact verified name
    val ownerName: String? = null, // Extracted Person Name
    val companyName: String? = null, // Extracted Business Name
    val callerInfo: String? = null // Gemini reputation report
)
