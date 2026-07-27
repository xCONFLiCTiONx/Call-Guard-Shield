package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whitelist",
    indices = [Index(value = ["number"])]
)
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val label: String? = null
)
