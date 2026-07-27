package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "area_code_blocks")
data class AreaCodeBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val areaCode: String
)
