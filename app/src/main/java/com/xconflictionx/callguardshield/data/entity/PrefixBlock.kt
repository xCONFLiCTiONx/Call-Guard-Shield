package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prefix_blocks")
data class PrefixBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prefix: String // e.g., "870421"
)
