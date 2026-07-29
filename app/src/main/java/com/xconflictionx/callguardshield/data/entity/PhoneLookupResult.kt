package com.xconflictionx.callguardshield.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "phone_lookup_cache")
data class PhoneLookupResult(
    @PrimaryKey val phoneNumber: String,
    val ownerName: String? = null,
    val companyName: String? = null,
    val category: String? = "Unknown",
    val accuracy: Int = 0, // Unified 0-100 scale
    val spam: Boolean = false,
    val scam: Boolean = false,
    val debtCollector: Boolean = false,
    val telemarketer: Boolean = false,
    val summary: String? = null,
    val evidence: List<String>? = emptyList(),
    val sources: List<String>? = emptyList(),
    val manualLabel: String? = null,
    val lastVerified: String? = null,
    val lookupDate: Long = System.currentTimeMillis(),
    val isCached: Boolean = false
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return Gson().toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return try {
            Gson().fromJson(value, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
