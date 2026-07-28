package com.xconflictionx.callguardshield.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.util.Scanner
import java.util.UUID

data class AutoQuery(val number: String, val label: String?)

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "GEMINI_LOG"
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.callGuardShieldDao()
    private val settingsRepo = SettingsRepository(application)
    
    // UI State
    val settings = settingsRepo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings(false, false, false, false, false, false, emptySet(), false, 0L, 0L, false, 30, "gemini-1.5-flash"))
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

    private val _bulkNumber = MutableStateFlow<String?>(null)
    val bulkNumber = _bulkNumber.asStateFlow()

    private val _foregroundNumber = MutableStateFlow<String?>(null)
    val foregroundNumber = _foregroundNumber.asStateFlow()

    private val _selectedNumberIntel = MutableStateFlow<PhoneLookupResult?>(null)
    val selectedNumberIntel = _selectedNumberIntel.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun fetchIntelForNumber(number: String) {
        viewModelScope.launch {
            _selectedNumberIntel.value = dao.getLookupResult(number)
        }
    }

    private fun getLookupService(): GeminiPhoneLookupService {
        return GeminiPhoneLookupService(
            getApplication(),
            CryptoManager.getGeminiApiKey(getApplication()) ?: "",
            settings.value.selectedGeminiModel,
            dao
        )
    }

    init {
        viewModelScope.launch {
            try {
                // Wait for real settings from DataStore (don't use settings.value which is a placeholder in the first frame)
                val currentSettings = settingsRepo.settingsFlow.first()
                if (!currentSettings.firstRunSyncComplete) {
                    logToConsole("SYSTEM", "First-run auto-sync triggered", LogLevel.INFO)
                    forceSync()
                }

                testGeminiKey()
                refreshGeminiModels()
                refreshBatteryStatus()
                refreshLocationStatus()
                logToConsole("SYSTEM", "MainViewModel initialized", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("SYSTEM", "Initialization error: ${e.message}", LogLevel.ERROR)
            }
        }
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
            try {
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
            } catch (e: Exception) {
                logToConsole("CRYPTO", "Failed to access API key: ${e.message}", LogLevel.ERROR)
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
            _foregroundNumber.value = number
            _bulkProgress.value = null
            clearChat()
            addChatMessage(ChatEntry.UserMessage("Starting technical intel scan for: $number..."))
            
            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) {
                    val error = "Error: API Key missing in Settings."
                    addChatMessage(ChatEntry.ErrorMessage(error))
                    logToConsole("INVESTIGATION", error, LogLevel.ERROR)
                    return@launch
                }
                
                val model = settings.value.selectedGeminiModel
                addChatMessage(ChatEntry.UserMessage("📡 Connection: $model (Search Grounding: On)"))
                
                // Get current info for comparison
                val existingIntel = dao.getLookupResult(number)

                // FORCE REFRESH: Manual single scan always fetches fresh data
                val result = getLookupService().lookup(setOf(number, PhoneHelper.normalizeToE164(number)), forceRefresh = true)
                
                if (result != null) {
                    val newConfidence = result.confidence ?: 0.0
                    val oldConfidence = existingIntel?.confidence ?: -1.0

                    if (newConfidence > oldConfidence) {
                        applyInvestigationResult(number, result)
                        addChatMessage(ChatEntry.IntelReport(result, wasAutoApplied = true, oldConfidence = oldConfidence))
                        logToConsole("INVESTIGATION", "Auto-updated $number (Higher confidence: $newConfidence > $oldConfidence)", LogLevel.INFO)
                    } else {
                        addChatMessage(ChatEntry.IntelReport(result, wasAutoApplied = false, oldConfidence = oldConfidence))
                        addChatMessage(ChatEntry.UserMessage("⚠️ Note: This result has lower/equal confidence compared to your current data."))
                    }
                    _lastLookupResult.value = result
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
                _foregroundNumber.value = null
                _investigationStatus.value = null
            }
        }
    }

    fun refineInvestigation(number: String) {
        viewModelScope.launch {
            _isIdentifying.value = true
            _foregroundNumber.value = number
            clearChat()
            val model = settings.value.selectedGeminiModel
            addChatMessage(ChatEntry.UserMessage("🔍 CRITICAL RE-VERIFICATION: $number"))
            addChatMessage(ChatEntry.UserMessage("📡 Using model: $model (Deep Cross-Reference)"))
            try {
                // Get current info for comparison
                val existingIntel = dao.getLookupResult(number)

                val result = getLookupService().lookupDeep(number)
                if (result != null) {
                    val newConfidence = result.confidence ?: 0.0
                    val oldConfidence = existingIntel?.confidence ?: -1.0

                    if (newConfidence > oldConfidence) {
                        applyInvestigationResult(number, result)
                        addChatMessage(ChatEntry.IntelReport(result, wasAutoApplied = true, oldConfidence = oldConfidence))
                        logToConsole("INVESTIGATION", "Auto-updated $number via Deep Scan (Higher confidence: $newConfidence > $oldConfidence)", LogLevel.INFO)
                    } else {
                        addChatMessage(ChatEntry.IntelReport(result, wasAutoApplied = false, oldConfidence = oldConfidence))
                        addChatMessage(ChatEntry.UserMessage("⚠️ Deep scan returned lower/equal confidence."))
                    }
                    _lastLookupResult.value = result
                } else {
                    addChatMessage(ChatEntry.ErrorMessage("Error: Even deep search returned no definitive results."))
                }
            } catch (e: Exception) {
                val displayMsg = "Error: ${e.message ?: "Deep scan failed."}"
                addChatMessage(ChatEntry.ErrorMessage(displayMsg))
                logToConsole("INVESTIGATION", "Deep lookup failed for $number: $displayMsg", LogLevel.ERROR)
            } finally {
                _isIdentifying.value = false
                _foregroundNumber.value = null
                _investigationStatus.value = null
            }
        }
    }

    suspend fun applyInvestigationResult(number: String, result: PhoneLookupResult) {
        // Save to cache
        dao.insertLookupResult(result.copy(phoneNumber = number, lookupDate = System.currentTimeMillis()))
        
        // Sync labels
        val bestName = result.companyName ?: result.ownerName ?: "Unknown"
        val formattedInfo = buildString {
            append("Risk: ${if (result.scam) "HIGH" else if (result.spam) "MEDIUM" else "LOW"} • ")
            append("Acc: ${(result.confidence?.times(100))?.toInt()}% • ")
            append(result.summary?.take(60))
        }
        
        // Update Call Logs (Direct match)
        dao.updateCallLogByNumber(number, bestName, result.ownerName, result.companyName, formattedInfo)
        
        // Update Lists (Pattern matching)
        val blackMatch = dao.findBlacklistMatch(number)
        if (blackMatch != null) {
            dao.updateBlacklistLabelByNumber(blackMatch.pattern, bestName)
        }
        
        val whiteMatch = dao.findWhitelistMatch(number)
        if (whiteMatch != null) {
            dao.updateWhitelistLabelByNumber(whiteMatch.number, bestName)
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
                
                // Duplicate check
                if (dao.findBlacklistByPattern(normalized) != null) {
                    _uiEvent.emit(UiEvent.ShowToast("This number is already in your Blacklist!"))
                    return@launch
                }
                if (dao.findWhitelistByNumber(normalized) != null) {
                    _uiEvent.emit(UiEvent.ShowToast("Conflict: This number is currently in your Whitelist!"))
                    return@launch
                }

                dao.insertBlacklistEntry(BlacklistEntry(pattern = normalized, label = label ?: "Manual Block"))
                logToConsole("LIST", "Added to Blacklist: $normalized", LogLevel.INFO)
                _uiEvent.emit(UiEvent.ShowToast("Added to Blacklist"))
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
                
                // Duplicate check
                if (dao.findWhitelistByNumber(normalized) != null) {
                    _uiEvent.emit(UiEvent.ShowToast("This number is already in your Whitelist!"))
                    return@launch
                }
                if (dao.findBlacklistMatch(normalized) != null) {
                    _uiEvent.emit(UiEvent.ShowToast("Conflict: This number is currently in your Blacklist!"))
                    return@launch
                }

                dao.insertWhitelistEntry(WhitelistEntry(number = normalized, label = label ?: "Allowed Caller"))
                logToConsole("LIST", "Added to Whitelist: $normalized", LogLevel.INFO)
                _uiEvent.emit(UiEvent.ShowToast("Added to Whitelist"))
            } catch (e: Exception) {
                logToConsole("LIST", "Failed to add to Whitelist: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun performBulkInvestigation(isBlacklist: Boolean) {
        if (_isIdentifying.value) {
            logToConsole("BULK", "Scan already in progress. Ignoring request.", LogLevel.WARN)
            return
        }
        
        viewModelScope.launch {
            _isIdentifying.value = true
            _bulkProgress.value = 0f
            clearChat()
            val listType = if (isBlacklist) "Blacklist" else "Whitelist"
            addChatMessage(ChatEntry.UserMessage("🚀 Starting Smart Bulk Scan for $listType..."))
            logToConsole("BULK", "Starting foreground bulk scan for $listType", LogLevel.INFO)

            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) {
                    val error = "Error: API Key missing in Settings."
                    addChatMessage(ChatEntry.ErrorMessage(error))
                    logToConsole("INVESTIGATION", error, LogLevel.ERROR)
                    return@launch
                }

                val listToIdentify = if (isBlacklist) dao.getBlacklistSync() else dao.getWhitelistSync()
                val total = listToIdentify.size
                
                if (total == 0) {
                    addChatMessage(ChatEntry.ErrorMessage("The $listType is empty. Nothing to scan."))
                    return@launch
                }

                addChatMessage(ChatEntry.UserMessage("Scanning for entries that haven't been verified in the last 30 days..."))

                var processedCount = 0
                listToIdentify.forEachIndexed { index, entry ->
                    if (!isActive) return@forEachIndexed
                    
                    val number = if (entry is BlacklistEntry) entry.pattern else (entry as WhitelistEntry).number
                    
                    _bulkProgress.value = (index + 1).toFloat() / total

                    try {
                        // Respect 30-day cache logic in service
                        val result = getLookupService().lookup(setOf(number), forceRefresh = false)
                        
                        if (result != null) {
                            if (result.isCached) {
                                // Data is fresh enough (< 30 days) - processed instantly
                                logToConsole("BULK", "Skipping $number: Data is < 30 days old.", LogLevel.INFO)
                            } else {
                                // Fresh from Gemini - show feedback and delay
                                _bulkNumber.value = number
                                processedCount++
                                
                                // Get current info for comparison
                                val existingIntel = dao.getLookupResult(number)
                                val newConfidence = result.confidence ?: 0.0
                                val oldConfidence = existingIntel?.confidence ?: -1.0

                                if (newConfidence > oldConfidence) {
                                    applyInvestigationResult(number, result)
                                    addChatMessage(ChatEntry.IntelReport(result, wasAutoApplied = true, oldConfidence = oldConfidence))
                                    logToConsole("BULK", "Auto-updated $number (Better confidence)", LogLevel.INFO)
                                } else {
                                    addChatMessage(ChatEntry.IntelReport(result, wasAutoApplied = false, oldConfidence = oldConfidence))
                                }
                                
                                // Rate limit protection - only delay if we actually made a network request
                                delay(2000)
                            }
                        }
                    } catch (e: Exception) {
                        logToConsole("BULK", "Failed on $number: ${e.message}", LogLevel.ERROR)
                    }
                }
                
                if (processedCount == 0) {
                    addChatMessage(ChatEntry.UserMessage("ℹ️ All numbers in this list are already up-to-date (recently verified)."))
                    addChatMessage(ChatEntry.UserMessage("💡 If you want to force a live refresh of every number now, please use the 'Run Now' maintenance button in Settings."))
                } else {
                    addChatMessage(ChatEntry.UserMessage("✅ Bulk scan complete. Found $processedCount updates."))
                }
                logToConsole("BULK", "Foreground bulk scan finished. Total updates: $processedCount", LogLevel.INFO)

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                addChatMessage(ChatEntry.ErrorMessage("Critical failure: $errorMsg"))
                logToConsole("BULK", "Foreground bulk scan failed: $errorMsg", LogLevel.ERROR)
            } finally {
                _isIdentifying.value = false
                _bulkProgress.value = null
                _bulkNumber.value = null
            }
        }
    }

    fun bulkIdentify(isBlacklist: Boolean) {
        viewModelScope.launch {
            try {
                val listType = if (isBlacklist) "Blacklist" else "Whitelist"
                logToConsole("BULK", "Queueing background bulk identification for $listType", LogLevel.INFO)
                
                val data = workDataOf("isBlacklist" to isBlacklist)
                val request = OneTimeWorkRequestBuilder<BulkIdentifyWorker>()
                    .setInputData(data)
                    .addTag("BULK_IDENTIFY")
                    .build()
                
                WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "bulk_identify",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                
                // Observe progress
                WorkManager.getInstance(getApplication())
                    .getWorkInfoByIdFlow(request.id)
                    .onEach { workInfo ->
                        if (workInfo != null) {
                            when (workInfo.state) {
                                WorkInfo.State.RUNNING -> {
                                    _isIdentifying.value = true
                                    _bulkProgress.value = workInfo.progress.getFloat("progress", 0f)
                                    _bulkNumber.value = workInfo.progress.getString("number")
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    _isIdentifying.value = false
                                    _bulkProgress.value = null
                                    _bulkNumber.value = null
                                    logToConsole("BULK", "Bulk identification completed successfully", LogLevel.INFO)
                                }
                                WorkInfo.State.FAILED -> {
                                    _isIdentifying.value = false
                                    _bulkProgress.value = null
                                    _bulkNumber.value = null
                                    logToConsole("BULK", "Bulk identification failed. Check technical logs.", LogLevel.ERROR)
                                }
                                WorkInfo.State.CANCELLED -> {
                                    _isIdentifying.value = false
                                    _bulkProgress.value = null
                                    _bulkNumber.value = null
                                    logToConsole("BULK", "Bulk identification cancelled", LogLevel.WARN)
                                }
                                else -> {
                                    // Handle ENQUEUED or BLOCKED if needed
                                    if (workInfo.state == WorkInfo.State.ENQUEUED) {
                                        _isIdentifying.value = true
                                    }
                                }
                            }
                        }
                    }.launchIn(viewModelScope)

            } catch (e: Exception) {
                logToConsole("BULK", "Failed to enqueue bulk identification: ${e.message}", LogLevel.ERROR)
            }
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

    fun updateFullNumberDetails(oldNumber: String, intel: PhoneLookupResult, isBlacklist: Boolean?) {
        viewModelScope.launch {
            try {
                val newNumber = intel.phoneNumber
                
                // 1. Handle Primary Key change if number was edited
                if (oldNumber != newNumber) {
                    logToConsole("SYSTEM", "Updating number from $oldNumber to $newNumber", LogLevel.INFO)
                    
                    // Remove old records
                    dao.deleteLookupResult(oldNumber)
                    dao.deleteBlacklistByPattern(oldNumber)
                    dao.deleteWhitelistByNumber(oldNumber)
                    // (History is kept as is for historical accuracy, or we could update it too)
                }

                // 2. Update/Insert Cache
                dao.insertLookupResult(intel.copy(lookupDate = System.currentTimeMillis()))
                
                // 3. Update List Label (if number exists in either)
                val bestName = intel.companyName ?: intel.ownerName ?: "Unknown"
                
                if (isBlacklist == true) {
                    dao.insertBlacklistEntry(BlacklistEntry(pattern = newNumber, label = bestName))
                } else if (isBlacklist == false) {
                    dao.insertWhitelistEntry(WhitelistEntry(number = newNumber, label = bestName))
                } else {
                    // Try to preserve existing list membership if it was edited from history
                    if (dao.findBlacklistByPattern(oldNumber) != null || dao.findBlacklistByPattern(newNumber) != null) {
                        dao.insertBlacklistEntry(BlacklistEntry(pattern = newNumber, label = bestName))
                    }
                    if (dao.findWhitelistByNumber(oldNumber) != null || dao.findWhitelistByNumber(newNumber) != null) {
                        dao.insertWhitelistEntry(WhitelistEntry(number = newNumber, label = bestName))
                    }
                }
                
                // 4. Update Call Log
                val formattedInfo = buildString {
                    append("Risk: ${if (intel.scam) "HIGH" else if (intel.spam) "MEDIUM" else "LOW"} • ")
                    append("Acc: ${(intel.confidence?.times(100))?.toInt()}% • ")
                    append(intel.summary?.take(60))
                }
                dao.updateCallLogByNumber(newNumber, bestName, intel.ownerName, intel.companyName, formattedInfo)
                if (oldNumber != newNumber) {
                    // Also update any history that was strictly under the old number
                    dao.updateCallLogByNumber(oldNumber, bestName, intel.ownerName, intel.companyName, formattedInfo)
                }
                
                // 5. Update UI state if this was the selected number
                if (_selectedNumberIntel.value?.phoneNumber == oldNumber) {
                    _selectedNumberIntel.value = intel
                }
                
                logToConsole("SYSTEM", "Manually updated details for $newNumber", LogLevel.INFO)
                _uiEvent.emit(UiEvent.ShowToast("Changes saved"))
            } catch (e: Exception) {
                logToConsole("SYSTEM", "Failed to update details: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun updateAutoMaintenance(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepo.updateAutoMaintenanceEnabled(enabled)
                if (enabled) schedulePeriodicMaintenance()
                else cancelPeriodicMaintenance()
                logToConsole("SETTINGS", "Auto-Maintenance set to $enabled", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("SETTINGS", "Failed to update maintenance: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun runMaintenanceNow() {
        viewModelScope.launch {
            try {
                logToConsole("BULK", "Manual maintenance scan started (Background)", LogLevel.INFO)
                
                val data = workDataOf("isBlacklist" to true, "isMaintenance" to true)
                val request = OneTimeWorkRequestBuilder<BulkIdentifyWorker>()
                    .setInputData(data)
                    .addTag("MAINTENANCE")
                    .build()
                
                WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "manual_maintenance",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                
                // Also run for whitelist
                val dataWhite = workDataOf("isBlacklist" to false, "isMaintenance" to true)
                val requestWhite = OneTimeWorkRequestBuilder<BulkIdentifyWorker>()
                    .setInputData(dataWhite)
                    .addTag("MAINTENANCE")
                    .build()
                
                WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "manual_maintenance_white",
                    ExistingWorkPolicy.REPLACE,
                    requestWhite
                )

                settingsRepo.updateLastMaintenanceTime(System.currentTimeMillis())
                
                // Link UI state to the combined progress of these workers if desired, 
                // but since user asked for "it keeps scanning if I close the app", 
                // we mostly rely on the worker. We'll observe the first one for UI feedback.
                observeWorker(request.id)

            } catch (e: Exception) {
                logToConsole("BULK", "Failed to start maintenance: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private fun schedulePeriodicMaintenance() {
        val request = PeriodicWorkRequestBuilder<BulkIdentifyWorker>(30, java.util.concurrent.TimeUnit.DAYS)
            .setInputData(workDataOf("isMaintenance" to true))
            .addTag("PERIODIC_MAINTENANCE")
            .build()
        
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "monthly_maintenance",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelPeriodicMaintenance() {
        WorkManager.getInstance(getApplication()).cancelUniqueWork("monthly_maintenance")
    }

    private fun observeWorker(workerId: UUID) {
        WorkManager.getInstance(getApplication())
            .getWorkInfoByIdFlow(workerId)
            .onEach { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            _isIdentifying.value = true
                            _bulkProgress.value = workInfo.progress.getFloat("progress", 0f)
                            _bulkNumber.value = workInfo.progress.getString("number")
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _isIdentifying.value = false
                            _bulkProgress.value = null
                            _bulkNumber.value = null
                            logToConsole("BULK", "Maintenance scan completed successfully", LogLevel.INFO)
                        }
                        WorkInfo.State.FAILED -> {
                            _isIdentifying.value = false
                            _bulkProgress.value = null
                            _bulkNumber.value = null
                            logToConsole("BULK", "Maintenance scan failed", LogLevel.ERROR)
                        }
                        else -> {}
                    }
                }
            }.launchIn(viewModelScope)
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

    fun getFullBackupData(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                logToConsole("BACKUP", "Generating full system backup...", LogLevel.INFO)
                val container = BackupContainer(
                    blacklist = dao.getBlacklistSync(),
                    whitelist = dao.getWhitelistSync(),
                    callLogs = dao.getAllCallLogsSync(),
                    blockedCalls = dao.getAllBlockedCallsSync(),
                    areaCodeBlocks = dao.getAreaCodeBlocksSync(),
                    prefixBlocks = dao.getPrefixBlocksSync(),
                    lookupCache = dao.getAllLookupResultsSync(),
                    geminiApiKey = CryptoManager.getGeminiApiKey(getApplication())
                )
                val json = Gson().toJson(container)
                onComplete(json)
                logToConsole("BACKUP", "Backup ready for export", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("BACKUP", "Failed to generate backup: ${e.message}", LogLevel.ERROR)
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
                logToConsole("IMPORT", "Checking file format...", LogLevel.INFO)
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val text = Scanner(stream).useDelimiter("\\A").next()
                    
                    if (text.trim().startsWith("{") && text.contains("lookupCache")) {
                        // This looks like a full backup
                        handleFullBackupRestore(text)
                    } else {
                        // Standard text list import
                        handleBulkProcessing(text, toBlacklist)
                    }
                }
                logToConsole("IMPORT", "Successfully processed import", LogLevel.INFO)
            } catch (e: Exception) {
                logToConsole("IMPORT", "Import failed: ${e.message}", LogLevel.ERROR)
            }
            onComplete()
        }
    }

    private suspend fun handleFullBackupRestore(json: String) {
        try {
            logToConsole("RESTORE", "Parsing backup container...", LogLevel.INFO)
            val container = Gson().fromJson(json, BackupContainer::class.java)
            
            logToConsole("RESTORE", "Restoring tables...", LogLevel.INFO)
            
            container.blacklist.forEach { entry -> dao.insertBlacklistEntry(entry) }
            container.whitelist.forEach { entry -> dao.insertWhitelistEntry(entry) }
            container.callLogs.forEach { entry -> dao.insertCallLogEntry(entry) }
            container.blockedCalls.forEach { entry -> dao.insertBlockedCall(entry) }
            container.areaCodeBlocks.forEach { entry -> dao.insertAreaCodeBlock(entry) }
            container.prefixBlocks.forEach { entry -> dao.insertPrefixBlock(entry) }
            container.lookupCache.forEach { entry -> dao.insertLookupResult(entry) }
            
            container.geminiApiKey?.let { key ->
                if (key.isNotBlank()) {
                    CryptoManager.saveGeminiApiKey(getApplication(), key)
                    testGeminiKey()
                    refreshGeminiModels()
                }
            }
            
            logToConsole("RESTORE", "Restore complete. Restored ${container.callLogs.size} logs and ${container.blacklist.size + container.whitelist.size} numbers.", LogLevel.INFO)
        } catch (e: Exception) {
            logToConsole("RESTORE", "Restore failed: ${e.message}", LogLevel.ERROR)
            throw e
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
