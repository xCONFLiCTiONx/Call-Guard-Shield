package com.xconflictionx.callguardshield.data.dao

import androidx.room.*
import com.xconflictionx.callguardshield.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallGuardShieldDao {
    // Blocked Calls
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedCall(call: BlockedCall)

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllBlockedCalls(): Flow<List<BlockedCall>>

    @Delete
    suspend fun deleteBlockedCall(call: BlockedCall)

    // Blacklist
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklistEntry(entry: BlacklistEntry)

    @Query("SELECT * FROM blacklist")
    fun getBlacklist(): Flow<List<BlacklistEntry>>

    @Query("SELECT * FROM blacklist")
    suspend fun getBlacklistSync(): List<BlacklistEntry>

    @Delete
    suspend fun deleteBlacklistEntry(entry: BlacklistEntry)

    @Query("SELECT * FROM blacklist WHERE pattern = :pattern LIMIT 1")
    suspend fun findBlacklistByPattern(pattern: String): BlacklistEntry?

    @Query("DELETE FROM blacklist WHERE pattern = :pattern")
    suspend fun deleteBlacklistByPattern(pattern: String)

    @Query("DELETE FROM blacklist WHERE source = :source")
    suspend fun deleteBlacklistBySource(source: String)

    // Whitelist
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelistEntry(entry: WhitelistEntry)

    @Query("SELECT * FROM whitelist")
    fun getWhitelist(): Flow<List<WhitelistEntry>>

    @Query("SELECT * FROM whitelist")
    suspend fun getWhitelistSync(): List<WhitelistEntry>

    @Query("SELECT * FROM whitelist WHERE number = :number LIMIT 1")
    suspend fun findWhitelistByNumber(number: String): WhitelistEntry?

    @Query("DELETE FROM whitelist WHERE number = :number")
    suspend fun deleteWhitelistByNumber(number: String)

    @Delete
    suspend fun deleteWhitelistEntry(entry: WhitelistEntry)

    // Area Code Blocks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAreaCodeBlock(block: AreaCodeBlock)

    @Query("SELECT * FROM area_code_blocks")
    fun getAreaCodeBlocks(): Flow<List<AreaCodeBlock>>

    @Query("SELECT * FROM area_code_blocks")
    suspend fun getAreaCodeBlocksSync(): List<AreaCodeBlock>

    @Delete
    suspend fun deleteAreaCodeBlock(block: AreaCodeBlock)

    // Prefix Blocks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrefixBlock(block: PrefixBlock)

    @Query("SELECT * FROM prefix_blocks")
    fun getPrefixBlocks(): Flow<List<PrefixBlock>>

    @Query("SELECT * FROM prefix_blocks")
    suspend fun getPrefixBlocksSync(): List<PrefixBlock>

    @Delete
    suspend fun deletePrefixBlock(block: PrefixBlock)

    // Global Spam (Background dictionaries)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalSpamEntry(entry: GlobalSpamEntry)

    @Query("SELECT * FROM global_spam")
    suspend fun getGlobalSpamSync(): List<GlobalSpamEntry>

    @Query("SELECT COUNT(*) FROM global_spam")
    fun getGlobalSpamCount(): Flow<Int>

    @Query("DELETE FROM global_spam WHERE dictionaryId = :id")
    suspend fun deleteGlobalSpamByDictionary(id: String)

    @Query("SELECT * FROM global_spam WHERE :incomingNumber LIKE pattern || '%' LIMIT 1")
    suspend fun findGlobalSpamByPattern(incomingNumber: String): GlobalSpamEntry?

    @Query("SELECT COUNT(*) FROM global_spam WHERE dictionaryId = :id")
    fun getGlobalSpamCountByDictionary(id: String): Flow<Int>

    @Query("SELECT * FROM blacklist WHERE :incomingNumber LIKE pattern || '%' LIMIT 1")
    suspend fun findBlacklistMatch(incomingNumber: String): BlacklistEntry?

    @Query("SELECT * FROM whitelist WHERE number = :number LIMIT 1")
    suspend fun findWhitelistMatch(number: String): WhitelistEntry?

    // Call Log (All calls)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogEntry(entry: CallLogEntry)

    @Query("SELECT * FROM call_log ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntry>>

    @Query("DELETE FROM call_log WHERE id = :id")
    suspend fun deleteCallLogById(id: Long)

    @Delete
    suspend fun deleteCallLogEntry(entry: CallLogEntry)

    @Query("DELETE FROM call_log")
    suspend fun deleteAllCallLogs()

    @Query("DELETE FROM call_log WHERE id NOT IN (SELECT id FROM call_log ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimCallLog(limit: Int)

    @Query("UPDATE call_log SET callerInfo = :info WHERE id = :id")
    suspend fun updateCallLogInfo(id: Long, info: String)

    // Phone Lookup Cache
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLookupResult(result: PhoneLookupResult)

    @Query("SELECT * FROM phone_lookup_cache WHERE phoneNumber = :number")
    suspend fun getLookupResult(number: String): PhoneLookupResult?

    @Query("DELETE FROM phone_lookup_cache")
    suspend fun clearLookupCache()

    @Transaction
    suspend fun getLocalIntelReport(number: String): String {
        val sb = StringBuilder("Local Database Search Results for $number:\n")
        
        findWhitelistMatch(number)?.let {
            sb.append("- Found in your Whitelist (Matches your allowed callers).\n")
        }
        
        findBlacklistMatch(number)?.let {
            sb.append("- Found in your Blacklist (Matches your blocked patterns).\n")
        }
        
        findGlobalSpamByPattern(number)?.let {
            sb.append("- Found in Verified Spam Database: ${it.label}\n")
        }
        
        if (sb.length < 50) sb.append("- No matches found in local database.\n")
        return sb.toString()
    }
}
