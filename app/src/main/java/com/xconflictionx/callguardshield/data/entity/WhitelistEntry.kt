package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "whitelist"
)
data class WhitelistEntry(
    @PrimaryKey val number: String,
    val label: String? = null
)
