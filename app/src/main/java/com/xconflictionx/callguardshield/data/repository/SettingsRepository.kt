package com.xconflictionx.callguardshield.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val IS_PAUSED = booleanPreferencesKey("is_paused")
        val BLOCK_NON_CONTACTS = booleanPreferencesKey("block_non_contacts")
        val BLOCK_UNKNOWN = booleanPreferencesKey("block_unknown")
        val BLOCK_NON_ARKANSAS = booleanPreferencesKey("block_non_arkansas")
        val BLOCK_INTERNATIONAL = booleanPreferencesKey("block_international")
        val FIRST_RUN_SYNC_COMPLETE = booleanPreferencesKey("first_run_sync_complete")
        val ENABLED_DICTIONARIES = stringSetPreferencesKey("enabled_dictionaries")
        val SHOW_CONTACTS_IN_HISTORY = booleanPreferencesKey("show_contacts_in_history")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val LAST_MAINTENANCE_TIME = longPreferencesKey("last_maintenance_time")
        val AUTO_MAINTENANCE_ENABLED = booleanPreferencesKey("auto_maintenance_enabled")
        val CACHE_AGE_DAYS = intPreferencesKey("cache_age_days")
        val SELECTED_GEMINI_MODEL = stringPreferencesKey("selected_gemini_model")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            isPaused = preferences[PreferencesKeys.IS_PAUSED] ?: false,
            blockNonContacts = preferences[PreferencesKeys.BLOCK_NON_CONTACTS] ?: false,
            blockUnknown = preferences[PreferencesKeys.BLOCK_UNKNOWN] ?: false,
            blockNonArkansas = preferences[PreferencesKeys.BLOCK_NON_ARKANSAS] ?: false,
            blockInternational = preferences[PreferencesKeys.BLOCK_INTERNATIONAL] ?: false,
            firstRunSyncComplete = preferences[PreferencesKeys.FIRST_RUN_SYNC_COMPLETE] ?: false,
            enabledDictionaries = preferences[PreferencesKeys.ENABLED_DICTIONARIES] ?: emptySet(),
            showContactsInHistory = preferences[PreferencesKeys.SHOW_CONTACTS_IN_HISTORY] ?: false,
            lastSyncTime = preferences[PreferencesKeys.LAST_SYNC_TIME] ?: 0L,
            lastMaintenanceTime = preferences[PreferencesKeys.LAST_MAINTENANCE_TIME] ?: 0L,
            autoMaintenanceEnabled = preferences[PreferencesKeys.AUTO_MAINTENANCE_ENABLED] ?: false,
            cacheAgeDays = preferences[PreferencesKeys.CACHE_AGE_DAYS] ?: 30,
            selectedGeminiModel = preferences[PreferencesKeys.SELECTED_GEMINI_MODEL] ?: "gemini-1.5-flash"
        )
    }

    suspend fun updateIsPaused(paused: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.IS_PAUSED] = paused }
    }

    suspend fun updateBlockNonContacts(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_NON_CONTACTS] = block }
    }

    suspend fun updateBlockUnknown(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_UNKNOWN] = block }
    }

    suspend fun updateBlockNonArkansas(block: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_NON_ARKANSAS] = block }
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
}

data class UserSettings(
    val isPaused: Boolean,
    val blockNonContacts: Boolean,
    val blockUnknown: Boolean,
    val blockNonArkansas: Boolean,
    val blockInternational: Boolean,
    val firstRunSyncComplete: Boolean,
    val enabledDictionaries: Set<String>,
    val showContactsInHistory: Boolean,
    val lastSyncTime: Long,
    val lastMaintenanceTime: Long,
    val autoMaintenanceEnabled: Boolean,
    val cacheAgeDays: Int,
    val selectedGeminiModel: String
)
