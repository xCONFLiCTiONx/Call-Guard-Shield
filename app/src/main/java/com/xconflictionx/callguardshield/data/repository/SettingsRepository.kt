package com.xconflictionx.callguardshield.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val IS_PAUSED = booleanPreferencesKey("is_paused")
        val ALLOW_ONLY_CONTACTS = booleanPreferencesKey("allow_only_contacts")
        val BLOCK_UNKNOWN = booleanPreferencesKey("block_unknown")
        val BLOCK_OUT_OF_STATE = booleanPreferencesKey("block_out_of_state")
        val BLOCK_INTERNATIONAL = booleanPreferencesKey("block_international")
        val FIRST_RUN_SYNC_COMPLETE = booleanPreferencesKey("first_run_sync_complete")
        val ENABLED_DICTIONARIES = stringSetPreferencesKey("enabled_dictionaries")
        val SHOW_CONTACTS_IN_HISTORY = booleanPreferencesKey("show_contacts_in_history")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val LAST_MAINTENANCE_TIME = longPreferencesKey("last_maintenance_time")
        val AUTO_MAINTENANCE_ENABLED = booleanPreferencesKey("auto_maintenance_enabled")
        val CACHE_AGE_DAYS = intPreferencesKey("cache_age_days")
        val SELECTED_GEMINI_MODEL = stringPreferencesKey("selected_gemini_model")
        val AI_REALTIME_BLOCKING = booleanPreferencesKey("ai_realtime_blocking")
        val BLOCK_DEBT_COLLECTORS = booleanPreferencesKey("block_debt_collectors")
        val BLOCK_TELEMARKETERS = booleanPreferencesKey("block_telemarketers")
        val AI_BLOCKING_ACCURACY = intPreferencesKey("ai_blocking_accuracy")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val WHITELIST_ENABLED = booleanPreferencesKey("whitelist_enabled")
        val BLACKLIST_ENABLED = booleanPreferencesKey("blacklist_enabled")
        val DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
        val APP_THEME = stringPreferencesKey("app_theme")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            isPaused = preferences[PreferencesKeys.IS_PAUSED] ?: false,
            allowOnlyContacts = preferences[PreferencesKeys.ALLOW_ONLY_CONTACTS] ?: false,
            blockUnknown = preferences[PreferencesKeys.BLOCK_UNKNOWN] ?: false,
            blockOutOfState = preferences[PreferencesKeys.BLOCK_OUT_OF_STATE] ?: false,
            blockInternational = preferences[PreferencesKeys.BLOCK_INTERNATIONAL] ?: false,
            firstRunSyncComplete = preferences[PreferencesKeys.FIRST_RUN_SYNC_COMPLETE] ?: false,
            enabledDictionaries = preferences[PreferencesKeys.ENABLED_DICTIONARIES] ?: emptySet(),
            showContactsInHistory = preferences[PreferencesKeys.SHOW_CONTACTS_IN_HISTORY] ?: false,
            lastSyncTime = preferences[PreferencesKeys.LAST_SYNC_TIME] ?: 0L,
            lastMaintenanceTime = preferences[PreferencesKeys.LAST_MAINTENANCE_TIME] ?: 0L,
            autoMaintenanceEnabled = preferences[PreferencesKeys.AUTO_MAINTENANCE_ENABLED] ?: false,
            cacheAgeDays = preferences[PreferencesKeys.CACHE_AGE_DAYS] ?: 30,
            selectedGeminiModel = preferences[PreferencesKeys.SELECTED_GEMINI_MODEL] ?: "gemini-3.1-flash-lite",
            aiRealTimeBlocking = preferences[PreferencesKeys.AI_REALTIME_BLOCKING] ?: false,
            blockDebtCollectors = preferences[PreferencesKeys.BLOCK_DEBT_COLLECTORS] ?: false,
            blockTelemarketers = preferences[PreferencesKeys.BLOCK_TELEMARKETERS] ?: false,
            aiBlockingAccuracy = preferences[PreferencesKeys.AI_BLOCKING_ACCURACY] ?: 90,
            googleAccountEmail = preferences[PreferencesKeys.GOOGLE_ACCOUNT_EMAIL],
            whitelistEnabled = preferences[PreferencesKeys.WHITELIST_ENABLED] ?: false,
            blacklistEnabled = preferences[PreferencesKeys.BLACKLIST_ENABLED] ?: false,
            debugEnabled = preferences[PreferencesKeys.DEBUG_ENABLED] ?: false,
            theme = AppTheme.valueOf(preferences[PreferencesKeys.APP_THEME] ?: AppTheme.SYSTEM.name)
        )
    }

    suspend fun updateIsPaused(paused: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.IS_PAUSED] = paused }
    }

    suspend fun updateAllowOnlyContacts(allow: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ALLOW_ONLY_CONTACTS] = allow }
    }

    suspend fun updateBlockUnknown(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_UNKNOWN] = block }
    }

    suspend fun updateBlockOutOfState(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_OUT_OF_STATE] = block }
    }

    suspend fun updateBlockInternational(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_INTERNATIONAL] = block }
    }

    suspend fun setFirstRunSyncComplete(complete: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.FIRST_RUN_SYNC_COMPLETE] = complete }
    }

    suspend fun updateEnabledDictionaries(dictionaries: Set<String>) {
        context.dataStore.edit { it[PreferencesKeys.ENABLED_DICTIONARIES] = dictionaries }
    }

    suspend fun updateShowContactsInHistory(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_CONTACTS_IN_HISTORY] = show }
    }

    suspend fun updateLastSyncTime(time: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_SYNC_TIME] = time }
    }

    suspend fun updateLastMaintenanceTime(time: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_MAINTENANCE_TIME] = time }
    }

    suspend fun updateAutoMaintenanceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_MAINTENANCE_ENABLED] = enabled }
    }

    suspend fun updateCacheAgeDays(days: Int) {
        context.dataStore.edit { it[PreferencesKeys.CACHE_AGE_DAYS] = days }
    }

    suspend fun updateSelectedGeminiModel(model: String) {
        context.dataStore.edit { it[PreferencesKeys.SELECTED_GEMINI_MODEL] = model }
    }

    suspend fun updateAiRealTimeBlocking(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AI_REALTIME_BLOCKING] = enabled }
    }

    suspend fun updateBlockDebtCollectors(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_DEBT_COLLECTORS] = block }
    }

    suspend fun updateBlockTelemarketers(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_TELEMARKETERS] = block }
    }

    suspend fun updateAiBlockingAccuracy(accuracy: Int) {
        context.dataStore.edit { it[PreferencesKeys.AI_BLOCKING_ACCURACY] = accuracy }
    }

    suspend fun updateWhitelistEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WHITELIST_ENABLED] = enabled }
    }

    suspend fun updateBlacklistEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLACKLIST_ENABLED] = enabled }
    }

    suspend fun updateDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DEBUG_ENABLED] = enabled }
    }

    suspend fun updateTheme(theme: AppTheme) {
        context.dataStore.edit { it[PreferencesKeys.APP_THEME] = theme.name }
    }

    suspend fun updateGoogleAccountEmail(email: String?) {
        context.dataStore.edit { 
            if (email == null) it.remove(PreferencesKeys.GOOGLE_ACCOUNT_EMAIL)
            else it[PreferencesKeys.GOOGLE_ACCOUNT_EMAIL] = email 
        }
    }

    suspend fun restoreAllSettings(s: UserSettings) {
        context.dataStore.edit {
            it[PreferencesKeys.IS_PAUSED] = s.isPaused
            it[PreferencesKeys.ALLOW_ONLY_CONTACTS] = s.allowOnlyContacts
            it[PreferencesKeys.BLOCK_UNKNOWN] = s.blockUnknown
            it[PreferencesKeys.BLOCK_OUT_OF_STATE] = s.blockOutOfState
            it[PreferencesKeys.BLOCK_INTERNATIONAL] = s.blockInternational
            it[PreferencesKeys.ENABLED_DICTIONARIES] = s.enabledDictionaries
            it[PreferencesKeys.SHOW_CONTACTS_IN_HISTORY] = s.showContactsInHistory
            it[PreferencesKeys.AUTO_MAINTENANCE_ENABLED] = s.autoMaintenanceEnabled
            it[PreferencesKeys.CACHE_AGE_DAYS] = s.cacheAgeDays
            it[PreferencesKeys.SELECTED_GEMINI_MODEL] = s.selectedGeminiModel
            it[PreferencesKeys.AI_REALTIME_BLOCKING] = s.aiRealTimeBlocking
            it[PreferencesKeys.BLOCK_DEBT_COLLECTORS] = s.blockDebtCollectors
            it[PreferencesKeys.BLOCK_TELEMARKETERS] = s.blockTelemarketers
            it[PreferencesKeys.AI_BLOCKING_ACCURACY] = s.aiBlockingAccuracy
            it[PreferencesKeys.WHITELIST_ENABLED] = s.whitelistEnabled
            it[PreferencesKeys.BLACKLIST_ENABLED] = s.blacklistEnabled
            it[PreferencesKeys.DEBUG_ENABLED] = s.debugEnabled
            it[PreferencesKeys.APP_THEME] = s.theme.name
        }
    }
}

data class UserSettings(
    val isPaused: Boolean,
    val allowOnlyContacts: Boolean,
    val blockUnknown: Boolean,
    val blockOutOfState: Boolean,
    val blockInternational: Boolean,
    val firstRunSyncComplete: Boolean,
    val enabledDictionaries: Set<String>,
    val showContactsInHistory: Boolean,
    val lastSyncTime: Long,
    val lastMaintenanceTime: Long,
    val autoMaintenanceEnabled: Boolean,
    val cacheAgeDays: Int,
    val selectedGeminiModel: String,
    val aiRealTimeBlocking: Boolean,
    val blockDebtCollectors: Boolean,
    val blockTelemarketers: Boolean,
    val aiBlockingAccuracy: Int,
    val googleAccountEmail: String?,
    val whitelistEnabled: Boolean,
    val blacklistEnabled: Boolean,
    val debugEnabled: Boolean,
    val theme: AppTheme
)
