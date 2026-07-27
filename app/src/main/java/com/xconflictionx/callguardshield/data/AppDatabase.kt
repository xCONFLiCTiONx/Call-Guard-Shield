package com.xconflictionx.callguardshield.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xconflictionx.callguardshield.data.dao.CallGuardShieldDao
import com.xconflictionx.callguardshield.data.entity.*

@Database(
    entities = [
        BlockedCall::class,
        BlacklistEntry::class,
        WhitelistEntry::class,
        AreaCodeBlock::class,
        CallLogEntry::class,
        PrefixBlock::class,
        GlobalSpamEntry::class,
        PhoneLookupResult::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callGuardShieldDao(): CallGuardShieldDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_guard_shield_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
