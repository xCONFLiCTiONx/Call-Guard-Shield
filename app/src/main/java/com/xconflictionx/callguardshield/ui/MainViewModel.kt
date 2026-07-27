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
import com.xconflictionx.callguardshield.worker.BulkIdentifyWorker
import com.xconflictionx.callguardshield.worker.SpamSyncWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Scanner
import java.util.UUID

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
    val allGlobalSpam = dao.getAllGlobalSpamEntries().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _lastLookupResult = MutableStateFlow<PhoneLookupResult?>(null)

    private val _investigationStatus = MutableStateFlow<String?>(null)
    val investigationStatus = _investigationStatus.asStateFlow()

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

    private val _backgroundLocationGranted = MutableStateFlow(false)
    val backgroundLocationGranted = _backgroundLocationGranted.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val consoleLogs = _consoleLogs.asStateFlow()

    private val _isIdentifying = MutableStateFlow(false)
    val isIdentifying = _isIdentifying.asStateFlow()

    private val _bulkProgress = MutableStateFlow<Float?>(null)
    val bulkProgress = _bulkProgress.asStateFlow()

    private fun getLookupService(): GeminiPhoneLookupService {
        return GeminiPhoneLookupService(
            getApplication(),
            CryptoManager.getGeminiApiKey(getApplication()) ?: "",
            settings.value.selectedGeminiModel,
            dao
        )
    }

    init {
        testGeminiKey()
        refreshGeminiModels()
        refreshBatteryStatus()
        refreshLocationStatus()
        logToConsole("SYSTEM", "MainViewModel initialized", LogLevel.INFO)
    }

    fun refreshLocationStatus() {
        try {
            val context = getApplication<Application>()
            val hasFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            val hasBackgroundLocation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            _backgroundLocationGranted.value = hasFineLocation && hasBackgroundLocation
        } catch (e: Exception) {
            logToConsole("SYSTEM", "Failed to refresh location status: ${e.message}", LogLevel.ERROR)
        }
    }

    fun requestBackgroundLocation(context: android.content.Context) {
        logToConsole("SYSTEM", "Guiding user to enable Background Location (All the time)", LogLevel.INFO)
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logToConsole("SYSTEM", "Failed to open settings: ${e.message}", LogLevel.ERROR)
        }
    }

    fun logToConsole(tag: String, message: String, level: LogLevel) {
        if (level == LogLevel.ERROR) {
            val entry = ConsoleEntry(tag = tag, message = message, level = level)
            _consoleLogs.value = (listOf(entry) + _consoleLogs.value).take(100)
        }
        
        when (level) {
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
    }

    fun clearConsole() {
        _consoleLogs.value = emptyList()
    }

    fun refreshBatteryStatus() {
        try {
            val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            _isIgnoringBatteryOptimizations.value = pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        } catch (e: Exception) {
            logToConsole("BATTERY", "Failed to check status: ${e.message}", LogLevel.ERROR)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            logToConsole("BATTERY", "Failed to request permission: ${e.message}", LogLevel.ERROR)
        }
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
            try {
                settingsRepo.updateIsPaused(!settings.value.isPaused)
            } catch (e: Exception) {
                logToConsole("SETTINGS", "Failed to toggle pause: ${e.message}", LogLevel.ERROR)
            }
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
            try {
                blockNonArkansas?.let { settingsRepo.updateBlockNonArkansas(it) }
                blockInternational?.let { settingsRepo.updateBlockInternational(it) }
                showContactsInHistory?.let { settingsRepo.updateShowContactsInHistory(it) }
                blockNonContacts?.let { settingsRepo.updateBlockNonContacts(it) }
                blockUnknown?.let { settingsRepo.updateBlockUnknown(it) }
            } catch (e: Exception) {
                logToConsole("SETTINGS", "Failed to update settings: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun updateDictionaryEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val current = settings.value.enabledDictionaries.toMutableSet()
                if (enabled) current.add(id) else current.remove(id)
                settingsRepo.updateEnabledDictionaries(current)
                if (enabled) forceSync()
                else {
                    dao.deleteGlobalSpamByDictionary(id)
                }
            } catch (e: Exception) {
                logToConsole("DICTIONARY", "Failed to update $id: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                logToConsole("SYNC", "Manual sync requested", LogLevel.INFO)
                val data = Data.Builder().putBoolean("force", true).build()
                val request = OneTimeWorkRequestBuilder<SpamSyncWorker>()
                    .setInputData(data)
                    .build()
                
                WorkManager.getInstance(getApplication()).enqueue(request)
                kotlinx.coroutines.delay(1500)
            } catch (e: Exception) {
                logToConsole("SYNC", "Failed to enqueue sync: ${e.message}", LogLevel.ERROR)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun saveGeminiKey(key: String) {
        try {
            CryptoManager.saveGeminiApiKey(getApplication(), key)
            testGeminiKey()
            refreshGeminiModels()
            logToConsole("CRYPTO", "API Key saved and validated", LogLevel.INFO)
        } catch (e: Exception) {
            logToConsole("CRYPTO", "Failed to save key: ${e.message}", LogLevel.ERROR)
        }
    }

    fun clearGeminiKey() {
        try {
            CryptoManager.clearGeminiApiKey(getApplication())
            _apiKeyStatus.value = "Not Configured"
            _availableModels.value = emptyList()
            logToConsole("CRYPTO", "API Key cleared", LogLevel.INFO)
        } catch (e: Exception) {
            logToConsole("CRYPTO", "Failed to clear key: ${e.message}", LogLevel.ERROR)
        }
    }

    fun testGeminiKey() {
        viewModelScope.launch {
            try {
                val key = CryptoManager.getGeminiApiKey(getApplication())
                _apiKeyStatus.value = if (key.isNullOrBlank()) "Not Configured" else "Connected"
            } catch (e: Exception) {
                logToConsole("CRYPTO", "Key test failed: ${e.message}", LogLevel.ERROR)
            }
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
                    logToConsole("GEMINI", "Discovered ${_availableModels.value.size} models", LogLevel.INFO)
                } catch (e: Exception) {
                    logToConsole("GEMINI", "Failed to fetch models: ${e.message}", LogLevel.ERROR)
                }
            }
        }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            try {
                settingsRepo.updateSelectedGeminiModel(model)
                logToConsole("SETTINGS", "Selected model changed to $model", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("SETTINGS", "Failed to update model: ${e.message}", LogLevel.ERROR)
            }
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
            _isIdentifying.value = true
            _bulkProgress.value = null
            clearChat()
            addChatMessage(ChatEntry.UserMessage("Starting technical intel scan for: $number..."))
            
            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) {
                    val error = "Error: API Key missing in Settings."
                    addChatMessage(ChatEntry.ErrorMessage(error))
                    return@launch
                }
                
                val model = settings.value.selectedGeminiModel
                addChatMessage(ChatEntry.UserMessage("📡 Connection: $model (Search Grounding: On)"))
                
                val result = getLookupService().lookup(setOf(number, PhoneHelper.normalizeToE164(number)))
                
                if (result != null) {
                    _lastLookupResult.value = result
                    addChatMessage(ChatEntry.IntelReport(result))
                    
                    // Sync the new intelligence to History and Lists
                    val bestName = result.companyName ?: result.ownerName ?: "Unknown"
                    val formattedInfo = buildString {
                        append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
                        append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
                        append(result.summary?.take(60))
                    }
                    dao.updateCallLogByNumber(number, bestName, formattedInfo)
                    dao.updateBlacklistLabelByNumber(number, bestName)
                    dao.updateWhitelistLabelByNumber(number, bestName)
                    
                } else {
                    val error = "Error: No reputable data found."
                    addChatMessage(ChatEntry.ErrorMessage(error))
                }
            } catch (e: Exception) {
                val displayMsg = "Error: ${e.message ?: "Technical scan failed."}"
                addChatMessage(ChatEntry.ErrorMessage(displayMsg))
                logToConsole("INVESTIGATION", "Lookup failed for $number: $displayMsg", LogLevel.ERROR)
            } finally {
                _isIdentifying.value = false
                _investigationStatus.value = null
            }
        }
    }

    fun refineInvestigation(number: String) {
        viewModelScope.launch {
            _isIdentifying.value = true
            clearChat()
            val model = settings.value.selectedGeminiModel
            addChatMessage(ChatEntry.UserMessage("🔍 CRITICAL RE-VERIFICATION: $number"))
            addChatMessage(ChatEntry.UserMessage("📡 Using model: $model (Deep Cross-Reference)"))
            try {
                val result = getLookupService().lookupDeep(number)
                if (result != null) {
                    _lastLookupResult.value = result
                    addChatMessage(ChatEntry.IntelReport(result))
                    
                    // Sync the new deep intelligence to History and Lists
                    val bestName = result.companyName ?: result.ownerName ?: "Unknown"
                    val formattedInfo = buildString {
                        append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
                        append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
                        append(result.summary?.take(60))
                    }
                    dao.updateCallLogByNumber(number, bestName, formattedInfo)
                    dao.updateBlacklistLabelByNumber(number, bestName)
                    dao.updateWhitelistLabelByNumber(number, bestName)
                    
                } else {
                    addChatMessage(ChatEntry.ErrorMessage("Error: Even deep search returned no definitive results."))
                }
            } catch (e: Exception) {
                val displayMsg = "Error: ${e.message ?: "Deep scan failed."}"
                addChatMessage(ChatEntry.ErrorMessage(displayMsg))
                logToConsole("INVESTIGATION", "Deep lookup failed for $number: $displayMsg", LogLevel.ERROR)
            } finally {
                _isIdentifying.value = false
                _investigationStatus.value = null
            }
        }
    }

    fun clearLookupCache() {
        viewModelScope.launch {
            try {
                dao.clearLookupCache()
                _lastLookupResult.value = null
                logToConsole("DATABASE", "Lookup cache cleared", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("DATABASE", "Failed to clear cache: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun addToBlacklist(number: String, label: String?) {
        viewModelScope.launch {
            try {
                val normalized = PhoneHelper.normalizeToE164(number)
                dao.insertBlacklistEntry(BlacklistEntry(pattern = normalized, label = label ?: "Manual Block"))
                logToConsole("LIST", "Added to Blacklist: $normalized", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to add to Blacklist: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun removeFromBlacklist(entry: BlacklistEntry) {
        viewModelScope.launch {
            try {
                dao.deleteBlacklistEntry(entry)
                logToConsole("LIST", "Removed from Blacklist: ${entry.pattern}", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to remove from Blacklist: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun updateBlacklistLabel(pattern: String, newLabel: String?) {
        viewModelScope.launch {
            try {
                dao.findBlacklistByPattern(pattern)?.let {
                    dao.insertBlacklistEntry(it.copy(label = newLabel ?: "Manual Block"))
                    logToConsole("LIST", "Updated Blacklist label: $pattern", LogLevel.INFO)
                }
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to update label: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun addToWhitelist(number: String, label: String?) {
        viewModelScope.launch {
            try {
                val normalized = PhoneHelper.normalizeToE164(number)
                dao.insertWhitelistEntry(WhitelistEntry(number = normalized, label = label ?: "Allowed Caller"))
                logToConsole("LIST", "Added to Whitelist: $normalized", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to add to Whitelist: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun bulkIdentify(isBlacklist: Boolean) {
        viewModelScope.launch {
            _isIdentifying.value = true
            
            // Ensure we have the latest list from the database
            val listToIdentify = if (isBlacklist) {
                dao.getBlacklist().first()
            } else {
                dao.getWhitelist().first()
            }

            if (listToIdentify.isEmpty()) {
                addChatMessage(ChatEntry.UserMessage("The list is empty. Nothing to identify."))
                _isIdentifying.value = false
                return@launch
            }

            addChatMessage(ChatEntry.UserMessage("🔍 Bulk Identification started for ${listToIdentify.size} numbers..."))
            logToConsole("BULK", "Starting identification for ${listToIdentify.size} entries", LogLevel.INFO)

            listToIdentify.forEachIndexed { index, entry ->
                val number = if (entry is BlacklistEntry) entry.pattern else (entry as WhitelistEntry).number
                _bulkProgress.value = (index + 1).toFloat() / listToIdentify.size
                addChatMessage(ChatEntry.UserMessage("Processing [${index + 1}/${listToIdentify.size}]: $number"))
                
                try {
                    val result = getLookupService().lookup(setOf(number))
                    if (result != null) {
                        val bestName = result.companyName ?: result.ownerName ?: "Unknown"
                        val formattedInfo = buildString {
                            append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
                            append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
                            append(result.summary?.take(60))
                        }
                        
                        if (isBlacklist) dao.updateBlacklistLabelByNumber(number, bestName)
                        else dao.updateWhitelistLabelByNumber(number, bestName)
                        
                        dao.updateCallLogByNumber(number, bestName, formattedInfo)
                        logToConsole("BULK", "Success: $number -> $bestName", LogLevel.INFO)
                    }
                } catch (e: Exception) {
                    logToConsole("BULK", "Failed on $number: ${e.message}", LogLevel.ERROR)
                }
                
                if (index < listToIdentify.size - 1) {
                    kotlinx.coroutines.delay(4000)
                }
            }
            
            addChatMessage(ChatEntry.UserMessage("✅ Bulk Identification Complete."))
            _bulkProgress.value = null
            _isIdentifying.value = false
        }
    }

    fun removeFromWhitelist(entry: WhitelistEntry) {
        viewModelScope.launch {
            try {
                dao.deleteWhitelistEntry(entry)
                logToConsole("LIST", "Removed from Whitelist: ${entry.number}", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to remove from Whitelist: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun updateWhitelistLabel(number: String, newLabel: String?) {
        viewModelScope.launch {
            try {
                dao.findWhitelistByNumber(number)?.let {
                    dao.insertWhitelistEntry(it.copy(label = newLabel ?: "Allowed Caller"))
                    logToConsole("LIST", "Updated Whitelist label: $number", LogLevel.INFO)
                }
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to update label: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun deleteCallLogEntry(log: CallLogEntry) {
        viewModelScope.launch {
            try {
                dao.deleteCallLogEntry(log)
            } catch (e: Exception) {
                logToConsole("HISTORY", "Failed to delete log: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                dao.deleteAllCallLogs()
                logToConsole("HISTORY", "Call history cleared", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("HISTORY", "Failed to clear history: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun getExportData(isBlacklist: Boolean, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = if (isBlacklist) {
                    blacklist.value.joinToString("\n") { "${it.pattern},${it.label}" }
                } else {
                    whitelist.value.joinToString("\n") { "${it.number},${it.label}" }
                }
                onComplete(data)
            } catch (e: Exception) {
                logToConsole("EXPORT", "Failed to generate export: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun importNumbers(uri: Uri, toBlacklist: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                logToConsole("IMPORT", "Starting import from URI", LogLevel.INFO)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val text = Scanner(stream).useDelimiter("\\A").next()
                    handleBulkProcessing(text, toBlacklist)
                }
                logToConsole("IMPORT", "Successfully processed import", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("IMPORT", "Import failed: ${e.message}", LogLevel.ERROR)
            }
            onComplete()
        }
    }

    fun pasteNumbers(text: String, toBlacklist: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                handleBulkProcessing(text, toBlacklist)
                logToConsole("IMPORT", "Successfully processed pasted text", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("IMPORT", "Paste failed: ${e.message}", LogLevel.ERROR)
            }
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
