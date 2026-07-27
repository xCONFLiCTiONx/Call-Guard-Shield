package com.xconflictionx.callguardshield.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.*
import com.xconflictionx.callguardshield.data.repository.SettingsRepository
import com.xconflictionx.callguardshield.data.repository.UserSettings
import com.xconflictionx.callguardshield.logic.CryptoManager
import com.xconflictionx.callguardshield.logic.GeminiModelService
import com.xconflictionx.callguardshield.logic.GeminiPhoneLookupService
import com.xconflictionx.callguardshield.logic.PhoneHelper
import com.xconflictionx.callguardshield.worker.SpamSyncWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Scanner

data class AutoQuery(val number: String, val label: String?)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "GEMINI_LOG"
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.callGuardShieldDao()
    private val settingsRepo = SettingsRepository(application)
    
    // UI State
    val settings = settingsRepo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings(false, false, false, false, false, false, emptySet(), false, 0L, 30, "gemini-flash-latest"))
    val callLogs = dao.getAllCallLogs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val blacklist = dao.getBlacklist().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val whitelist = dao.getWhitelist().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val globalSpamCount = dao.getGlobalSpamCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _lastLookupResult = MutableStateFlow<PhoneLookupResult?>(null)

    private val _apiKeyStatus = MutableStateFlow("Unknown")
    val apiKeyStatus = _apiKeyStatus.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels = _availableModels.asStateFlow()

    private val _pendingAutoQuery = MutableStateFlow<AutoQuery?>(null)
    val pendingAutoQuery = _pendingAutoQuery.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatEntry>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    private fun getLookupService(): GeminiPhoneLookupService {
        return GeminiPhoneLookupService(
            getApplication(),
            CryptoManager.getGeminiApiKey(getApplication()) ?: "",
            settings.value.selectedGeminiModel
        )
    }

    init {
        testGeminiKey()
        refreshGeminiModels()
        refreshBatteryStatus()
    }

    fun refreshBatteryStatus() {
        try {
            val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            _isIgnoringBatteryOptimizations.value = pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh battery status", e)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun addChatMessage(entry: ChatEntry) {
        _chatMessages.value = _chatMessages.value + entry
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        _lastLookupResult.value = null
    }

    fun togglePause() {
        viewModelScope.launch {
            settingsRepo.updateIsPaused(!settings.value.isPaused)
        }
    }

    fun updateSetting(
        blockNonArkansas: Boolean? = null,
        blockInternational: Boolean? = null,
        showContactsInHistory: Boolean? = null,
        blockNonContacts: Boolean? = null,
        blockUnknown: Boolean? = null
    ) {
        viewModelScope.launch {
            blockNonArkansas?.let { settingsRepo.updateBlockNonArkansas(it) }
            blockInternational?.let { settingsRepo.updateBlockInternational(it) }
            showContactsInHistory?.let { settingsRepo.updateShowContactsInHistory(it) }
            blockNonContacts?.let { settingsRepo.updateBlockNonContacts(it) }
            blockUnknown?.let { settingsRepo.updateBlockUnknown(it) }
        }
    }

    fun updateDictionaryEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value.enabledDictionaries.toMutableSet()
            if (enabled) current.add(id) else current.remove(id)
            settingsRepo.updateEnabledDictionaries(current)
            if (enabled) forceSync()
            else {
                dao.deleteGlobalSpamByDictionary(id)
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            val data = Data.Builder().putBoolean("force", true).build()
            val request = OneTimeWorkRequestBuilder<SpamSyncWorker>()
                .setInputData(data)
                .build()
            
            WorkManager.getInstance(getApplication()).enqueue(request)
            kotlinx.coroutines.delay(1500)
            _isSyncing.value = false
        }
    }

    fun saveGeminiKey(key: String) {
        CryptoManager.saveGeminiApiKey(getApplication(), key)
        testGeminiKey()
        refreshGeminiModels()
    }

    fun clearGeminiKey() {
        CryptoManager.clearGeminiApiKey(getApplication())
        _apiKeyStatus.value = "Not Configured"
        _availableModels.value = emptyList()
    }

    fun testGeminiKey() {
        viewModelScope.launch {
            val key = CryptoManager.getGeminiApiKey(getApplication())
            _apiKeyStatus.value = if (key.isNullOrBlank()) "Not Configured" else "Connected"
        }
    }

    fun testGeminiKey(key: String) {
        saveGeminiKey(key)
    }

    fun refreshGeminiModels() {
        viewModelScope.launch {
            val key = CryptoManager.getGeminiApiKey(getApplication())
            if (!key.isNullOrBlank()) {
                try {
                    val service = GeminiModelService.create()
                    val response = service.listModels(key)
                    _availableModels.value = response.models
                        .filter { it.supportedGenerationMethods.contains("generateContent") }
                        .map { it.name.substringAfter("models/") }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch models", e)
                }
            }
        }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            settingsRepo.updateSelectedGeminiModel(model)
        }
    }

    fun setAutoQuery(number: String, label: String?) {
        clearChat() 
        _pendingAutoQuery.value = AutoQuery(number, label)
    }

    fun clearAutoQuery() {
        _pendingAutoQuery.value = null
    }

    fun performInvestigation(number: String) {
        viewModelScope.launch {
            clearChat()
            addChatMessage(ChatEntry.UserMessage("🔍 Starting technical scan for: $number..."))
            
            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) {
                    addChatMessage(ChatEntry.ErrorMessage("Error: API Key missing in Settings."))
                    return@launch
                }
                
                val model = settings.value.selectedGeminiModel
                addChatMessage(ChatEntry.UserMessage("📡 Connection: $model (Search Grounding: On)"))
                
                val result = getLookupService().lookup(setOf(number, PhoneHelper.normalizeToE164(number)))
                
                if (result != null) {
                    _lastLookupResult.value = result
                    addChatMessage(ChatEntry.IntelReport(result))
                } else {
                    addChatMessage(ChatEntry.ErrorMessage("Error: No reputable data found."))
                }
            } catch (e: Exception) {
                val displayMsg = "Error: ${e.message ?: "Technical scan failed."}"
                addChatMessage(ChatEntry.ErrorMessage(displayMsg))
                Log.e(TAG, "Scan Failure", e)
            }
        }
    }

    fun refineInvestigation(number: String) {
        viewModelScope.launch {
            clearChat()
            val model = settings.value.selectedGeminiModel
            addChatMessage(ChatEntry.UserMessage("🔍 CRITICAL RE-VERIFICATION: $number"))
            addChatMessage(ChatEntry.UserMessage("📡 Using model: $model (Deep Cross-Reference)"))
            try {
                val result = getLookupService().lookupDeep(number)
                if (result != null) {
                    addChatMessage(ChatEntry.IntelReport(result))
                } else {
                    addChatMessage(ChatEntry.ErrorMessage("Error: Even deep search returned no definitive results."))
                }
            } catch (e: Exception) {
                val displayMsg = "Error: ${e.message ?: "Deep scan failed."}"
                addChatMessage(ChatEntry.ErrorMessage(displayMsg))
                Log.e(TAG, "Deep Scan Failure", e)
            }
        }
    }

    fun clearLookupCache() {
        viewModelScope.launch {
            dao.clearLookupCache()
            _lastLookupResult.value = null
        }
    }

    fun addToBlacklist(number: String, label: String?) {
        viewModelScope.launch {
            val normalized = PhoneHelper.normalizeToE164(number)
            dao.insertBlacklistEntry(BlacklistEntry(pattern = normalized, label = label ?: "Manual Block"))
        }
    }

    fun removeFromBlacklist(entry: BlacklistEntry) {
        viewModelScope.launch {
            dao.deleteBlacklistEntry(entry)
        }
    }

    fun updateBlacklistLabel(pattern: String, newLabel: String?) {
        viewModelScope.launch {
            dao.findBlacklistByPattern(pattern)?.let {
                dao.insertBlacklistEntry(it.copy(label = newLabel ?: "Manual Block"))
            }
        }
    }

    fun addToWhitelist(number: String, label: String?) {
        viewModelScope.launch {
            val normalized = PhoneHelper.normalizeToE164(number)
            dao.insertWhitelistEntry(WhitelistEntry(number = normalized, label = label ?: "Allowed Caller"))
        }
    }

    fun removeFromWhitelist(entry: WhitelistEntry) {
        viewModelScope.launch {
            dao.deleteWhitelistEntry(entry)
        }
    }

    fun updateWhitelistLabel(number: String, newLabel: String?) {
        viewModelScope.launch {
            dao.findWhitelistByNumber(number)?.let {
                dao.insertWhitelistEntry(it.copy(label = newLabel ?: "Allowed Caller"))
            }
        }
    }

    fun deleteCallLogEntry(log: CallLogEntry) {
        viewModelScope.launch {
            dao.deleteCallLogEntry(log)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAllCallLogs()
        }
    }

    fun getExportData(isBlacklist: Boolean, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val data = if (isBlacklist) {
                blacklist.value.joinToString("\n") { "${it.pattern},${it.label}" }
            } else {
                whitelist.value.joinToString("\n") { "${it.number},${it.label}" }
            }
            onComplete(data)
        }
    }

    fun importNumbers(uri: Uri, toBlacklist: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val text = Scanner(stream).useDelimiter("\\A").next()
                    handleBulkProcessing(text, toBlacklist)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
            }
            onComplete()
        }
    }

    fun pasteNumbers(text: String, toBlacklist: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            handleBulkProcessing(text, toBlacklist)
            onComplete()
        }
    }

    private suspend fun handleBulkProcessing(text: String, toBlacklist: Boolean) {
        text.split("\n").filter { it.isNotBlank() }.forEach { line ->
            val parts = if (line.contains(":")) line.split(":", limit = 2) else listOf(line)
            val label = if (parts.size == 2) parts[0].trim() else null
            val numberPart = if (parts.size == 2) parts[1].trim() else parts[0].trim()
            
            val normalized = PhoneHelper.normalizeToE164(numberPart)
            if (normalized.isNotBlank()) {
                if (toBlacklist) {
                    dao.insertBlacklistEntry(BlacklistEntry(pattern = normalized, label = label ?: "Imported"))
                } else {
                    dao.insertWhitelistEntry(WhitelistEntry(number = normalized, label = label ?: "Imported"))
                }
            }
        }
    }
}
