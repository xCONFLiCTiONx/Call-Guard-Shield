package com.xconflictionx.callguardshield.ui

import android.app.Application
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.google.gson.Gson
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.*
import com.xconflictionx.callguardshield.data.repository.*
import com.xconflictionx.callguardshield.logic.*
import com.xconflictionx.callguardshield.worker.BulkIdentifyWorker
import com.xconflictionx.callguardshield.worker.SpamSyncWorker
import com.xconflictionx.callguardshield.worker.CloudBackupWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withTimeout
import java.util.Scanner
import androidx.core.content.ContextCompat
import kotlin.time.Duration.Companion.milliseconds

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.callGuardShieldDao()
    private val settingsRepo = SettingsRepository(application)

    // --- UI State Flows ---

    val settings = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserSettings(
            isPaused = false,
            allowOnlyContacts = false,
            blockUnknown = false,
            blockOutOfState = false,
            blockInternational = false,
            firstRunSyncComplete = false,
            enabledDictionaries = emptySet(),
            showContactsInHistory = false,
            lastSyncTime = 0L,
            lastMaintenanceTime = 0L,
            autoMaintenanceEnabled = false,
            cacheAgeDays = 30,
            selectedGeminiModel = "gemini-3.1-flash-lite",
            aiRealTimeBlocking = false,
            blockDebtCollectors = false,
            blockTelemarketers = false,
            aiBlockingAccuracy = 90,
            googleAccountEmail = null,
            whitelistEnabled = false,
            blacklistEnabled = false,
            debugEnabled = false
        )
    )

    val blacklist = dao.getBlacklist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val whitelist = dao.getWhitelist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val lookupCache = dao.getAllLookupResultsFlow()
    private val rawGroupedLogs = dao.getRawGroupedLogs()

    // Unified helper for enriching any list of numbers with intel and call stats
    private fun enrichList(
        numbers: List<String>,
        labels: Map<String, String?>,
        stats: List<RawGroupedLog>,
        bl: List<BlacklistEntry>,
        wl: List<WhitelistEntry>,
        cache: List<PhoneLookupResult>
    ): List<GroupedEnrichedCallLog> {
        return numbers.map { num ->
            val intel = cache.find { it.phoneNumber == num }
            val stat = stats.find { it.number == num }
            val inBl = bl.any { num.startsWith(it.pattern.removeSuffix("%")) }
            val inWl = wl.any { it.number == num }
            val rawLabel = labels[num]
            
            val headline = intel?.manualLabel 
                ?: rawLabel
                ?: intel?.companyName 
                ?: intel?.ownerName 
                ?: when {
                    intel?.scam == true -> "Confirmed Scam"
                    intel?.spam == true -> "Potential Spam"
                    intel?.debtCollector == true -> "Debt Collector"
                    intel?.telemarketer == true -> "Telemarketer"
                    else -> num
                }

            val formattedInfo = intel?.let { res ->
                buildString {
                    append("Risk: ${if (res.scam) "HIGH" else if (res.spam) "MEDIUM" else "LOW"} • ")
                    append("Accuracy: ${res.accuracy}% • ")
                    append(res.summary?.take(60))
                }
            }

            GroupedEnrichedCallLog(
                number = num,
                label = rawLabel,
                count = stat?.count ?: 0,
                lastTimestamp = stat?.lastTimestamp ?: 0L,
                allTimestamps = stat?.csvTimestamps?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
                headline = headline,
                formattedInfo = formattedInfo,
                isBlocked = intel?.scam == true || intel?.spam == true || inBl,
                isContact = false,
                isInBlacklist = inBl,
                isInWhitelist = inWl,
                intel = intel
            )
        }
    }

    val blacklistFull = combine(blacklist, rawGroupedLogs, whitelist, lookupCache) { bl, stats, wl, cache ->
        enrichList(bl.map { it.pattern }, bl.associate { it.pattern to it.label }, stats, bl, wl, cache)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whitelistFull = combine(whitelist, rawGroupedLogs, blacklist, lookupCache) { wl, stats, bl, cache ->
        enrichList(wl.map { it.number }, wl.associate { it.number to it.label }, stats, bl, wl, cache)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enriched & Grouped Call Logs
    val groupedCallLogs = combine(
        dao.getRawGroupedLogs(),
        blacklist,
        whitelist,
        lookupCache
    ) { rawLogs, bl, wl, cache ->
        enrichList(rawLogs.map { it.number }, emptyMap(), rawLogs, bl, wl, cache)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalSpamCount = dao.getGlobalSpamCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _isBackupActive = MutableStateFlow(false)
    val isBackupActive = _isBackupActive.asStateFlow()

    private val _isGoogleDriveConnected = MutableStateFlow(false)
    val isGoogleDriveConnected = _isGoogleDriveConnected.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels = _availableModels.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

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

    private val _pendingIntelResult = MutableStateFlow<PhoneLookupResult?>(null)
    val pendingIntelResult = _pendingIntelResult.asStateFlow()

    private val _apiKeyStatus = MutableStateFlow("Unknown")
    val apiKeyStatus = _apiKeyStatus.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _backgroundLocationGranted = MutableStateFlow(false)
    val backgroundLocationGranted = _backgroundLocationGranted.asStateFlow()

    val geminiStage = StatusManager.geminiStage
    val driveError = StatusManager.driveError

    fun clearDriveError() { StatusManager.clearDriveError() }

    val securitySuggestions = groupedCallLogs.map { logs ->
        logs.asSequence()
            .filter { !it.isBlocked && !it.isContact }
            .filter { entry ->
                val info = entry.formattedInfo?.lowercase() ?: ""
                info.contains("risk: high") || info.contains("risk: medium") || 
                info.contains("scam") || info.contains("spam") ||
                info.contains("debt collector") || info.contains("telemarketer")
            }
            .distinctBy { it.number }
            .take(5)
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                ConsoleLogger.logs.onEach { _consoleLogs.value = it }.launchIn(this)
                val currentSettings = settingsRepo.settingsFlow.first()
                _isGoogleDriveConnected.value = currentSettings.googleAccountEmail != null
                
                testGeminiKey()
                refreshGeminiModels()
                refreshBatteryStatus()
                refreshLocationStatus()
                scheduleMonthlySpamSync()
                scheduleMonthlyMaintenance()
            } catch (e: Exception) {
                logToConsole("SYSTEM", "Initialization error: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    // --- Intelligence & Firewall ---

    fun performInvestigation(number: String) {
        viewModelScope.launch {
            _isIdentifying.value = true; _foregroundNumber.value = number; _pendingIntelResult.value = null
            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) return@launch
                
                withTimeout(25000.milliseconds) {
                    val result = getLookupService().lookupFast(number)
                    if (result != null) {
                        handleNewIntelResult(number, result)
                    }
                }
            } catch (e: Exception) { 
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    logToConsole("GEMINI", "Investigation timed out. Check connection.", LogLevel.ERROR)
                }
            } finally { 
                _isIdentifying.value = false; _foregroundNumber.value = null
                StatusManager.setGeminiStage(null)
            }
        }
    }

    fun performThoroughInvestigation(number: String) {
        viewModelScope.launch {
            _isIdentifying.value = true; _foregroundNumber.value = number; _pendingIntelResult.value = null
            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) return@launch
                
                withTimeout(90000.milliseconds) { // Longer timeout for 3 scans
                    val result = getLookupDeepService().lookupThorough(number)
                    if (result != null) {
                        handleNewIntelResult(number, result)
                    }
                }
            } catch (e: Exception) { 
            } finally { 
                _isIdentifying.value = false; _foregroundNumber.value = null
                StatusManager.setGeminiStage(null)
            }
        }
    }

    private suspend fun handleNewIntelResult(number: String, newResult: PhoneLookupResult) {
        val currentResult = dao.getLookupResult(number)
        val currentAccuracy = currentResult?.accuracy ?: -1
        val newAccuracy = newResult.accuracy

        // Always set pending so user can see what was found and manual button can show
        _pendingIntelResult.value = newResult

        // Auto-save logic: ONLY if new accuracy is higher AND there is no manual override label
        // However, we still keep it in pendingResult so the "Update" button can be shown if needed
        if (newAccuracy > currentAccuracy && currentResult?.manualLabel == null) {
            applyInvestigationResult(number, newResult)
            _selectedNumberIntel.value = newResult
        }
    }

    fun applyPendingIntelUpdate() {
        val pending = _pendingIntelResult.value ?: return
        viewModelScope.launch {
            // Respect manual label if it was already set by user
            val current = dao.getLookupResult(pending.phoneNumber)
            val finalResult = if (current?.manualLabel != null) {
                pending.copy(manualLabel = current.manualLabel)
            } else {
                pending
            }
            
            applyInvestigationResult(pending.phoneNumber, finalResult)
            _selectedNumberIntel.value = finalResult
            _pendingIntelResult.value = null
        }
    }

    fun isDataDifferent(new: PhoneLookupResult?, old: PhoneLookupResult?): Boolean {
        if (new == null || old == null) return new != old
        return new.ownerName != old.ownerName ||
               new.companyName != old.companyName ||
               new.category != old.category ||
               new.spam != old.spam ||
               new.scam != old.scam ||
               new.debtCollector != old.debtCollector ||
               new.telemarketer != old.telemarketer ||
               new.manualLabel != old.manualLabel ||
               new.accuracy != old.accuracy
    }

    fun performBulkInvestigation(isBlacklist: Boolean) {
        if (_isIdentifying.value) return
        viewModelScope.launch {
            _isIdentifying.value = true; _bulkProgress.value = 0f
            try {
                val list = if (isBlacklist) dao.getBlacklistSync() else dao.getWhitelistSync()
                val total = list.size
                if (total == 0) return@launch
                list.forEachIndexed { index, entry ->
                    if (!isActive) return@forEachIndexed
                    val number = if (entry is BlacklistEntry) entry.pattern else (entry as WhitelistEntry).number
                    _bulkProgress.value = (index + 1).toFloat() / total
                    val result = getLookupService().lookup(setOf(number), forceRefresh = false)
                    if (result != null && !result.isCached) {
                        _bulkNumber.value = number
                        val current = dao.getLookupResult(number)
                        val oldAcc = current?.accuracy ?: -1
                        if (result.accuracy > oldAcc) {
                            applyInvestigationResult(number, result)
                        }
                    }
                    delay(500)
                }
            } catch (e: Exception) { } finally { _isIdentifying.value = false; _bulkProgress.value = null; _bulkNumber.value = null }
        }
    }

    internal suspend fun applyInvestigationResult(number: String, result: PhoneLookupResult) {
        val normalized = PhoneHelper.normalizeToE164(number)
        dao.insertLookupResult(result)
        
        // Push label updates back to manual tables for robustness
        val bestName = result.manualLabel 
            ?: result.companyName 
            ?: result.ownerName
        
        if (bestName != null) {
            dao.updateBlacklistLabelByNumber(normalized, bestName)
            dao.updateWhitelistLabelByNumber(normalized, bestName)
        }

        // List Exclusivity: If identified as spam/scam and Auto-save is happening, move from white to black
        if (result.scam || result.spam) {
            val n = PhoneHelper.normalizeToE164(number)
            dao.deleteWhitelistByNumber(n)
            if (bestName != null) {
                dao.insertBlacklistEntry(BlacklistEntry(pattern = n, label = bestName))
            }
        }
    }

    // --- Settings & Synchronization ---

    fun testGeminiKey(overrideKey: String? = null) {
        viewModelScope.launch {
            try {
                val apiKey = overrideKey ?: CryptoManager.getGeminiApiKey(getApplication())
                if (apiKey.isNullOrBlank()) {
                    _apiKeyStatus.value = "Missing API Key"
                    return@launch
                }
                _apiKeyStatus.value = "Testing..."
                val success = GeminiPhoneLookupService.testApiKey(apiKey)
                if (success) {
                    _apiKeyStatus.value = "Connected"
                } else {
                    _apiKeyStatus.value = "Invalid API Key"
                }
            } catch (e: Exception) {
                _apiKeyStatus.value = "Error"
            }
        }
    }

    fun refreshGeminiModels() {
        viewModelScope.launch {
            try {
                val apiKey = CryptoManager.getGeminiApiKey(getApplication())
                if (!apiKey.isNullOrBlank()) {
                    val models = GeminiPhoneLookupService.fetchAvailableModels(apiKey)
                    if (models.isNotEmpty()) {
                        _availableModels.value = models
                        val current = settings.value.selectedGeminiModel
                        if (current.isBlank() || !models.contains(current)) {
                            val best = models.firstOrNull { it.contains("flash") } ?: models.first()
                            updateSelectedModel(best)
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val request = OneTimeWorkRequestBuilder<SpamSyncWorker>().setInputData(workDataOf("force" to true)).build()
                WorkManager.getInstance(getApplication()).enqueue(request)
                delay(1500)
            } finally { _isSyncing.value = false }
        }
    }

    fun triggerAutoBackup() {
        viewModelScope.launch {
            _isBackupActive.value = true
            try {
                val request = OneTimeWorkRequestBuilder<CloudBackupWorker>().addTag("CLOUD_BACKUP").setInputData(workDataOf("mode" to "BACKUP")).build()
                WorkManager.getInstance(getApplication()).enqueue(request)
                delay(2000)
            } finally { _isBackupActive.value = false }
        }
    }

    fun triggerCloudRestore() {
        viewModelScope.launch {
            _isBackupActive.value = true
            try {
                val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                    .addTag("CLOUD_RESTORE")
                    .setInputData(workDataOf("mode" to "RESTORE"))
                    .build()
                
                val workManager = WorkManager.getInstance(getApplication())
                workManager.enqueue(request)
                
                // Observe the result to trigger a refresh
                workManager.getWorkInfoByIdFlow(request.id)
                    .collect { workInfo ->
                        if (workInfo != null && workInfo.state.isFinished) {
                            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                // Successfully restored! Refresh AI key status and models
                                testGeminiKey()
                                refreshGeminiModels()
                            }
                            _isBackupActive.value = false
                        }
                    }
            } catch (e: Exception) {
                _isBackupActive.value = false
            }
        }
    }

    // --- Actions & Settings ---

    fun togglePause() { viewModelScope.launch { settingsRepo.updateIsPaused(!(settings.value.isPaused)) } }
    fun connectGoogleAccount(email: String) { viewModelScope.launch { settingsRepo.updateGoogleAccountEmail(email); _isGoogleDriveConnected.value = true } }
    fun disconnectGoogleDrive() { viewModelScope.launch { settingsRepo.updateGoogleAccountEmail(null); _isGoogleDriveConnected.value = false } }
    fun updateSetting(blockOutOfState: Boolean? = null, blockInternational: Boolean? = null, showContactsInHistory: Boolean? = null, allowOnlyContacts: Boolean? = null, blockUnknown: Boolean? = null) {
        viewModelScope.launch {
            blockOutOfState?.let { settingsRepo.updateBlockOutOfState(it) }
            blockInternational?.let { settingsRepo.updateBlockInternational(it) }
            showContactsInHistory?.let { settingsRepo.updateShowContactsInHistory(it) }
            allowOnlyContacts?.let { settingsRepo.updateAllowOnlyContacts(it) }
            blockUnknown?.let { settingsRepo.updateBlockUnknown(it) }
        }
    }
    
    fun updateDictionaryEnabled(id: String, enabled: Boolean) { 
        viewModelScope.launch { 
            val c = settings.value.enabledDictionaries.toMutableSet()
            if (enabled) {
                c.add(id)
                forceSync() // Trigger sync immediately when enabled
            } else {
                c.remove(id)
            }
            settingsRepo.updateEnabledDictionaries(c)
        } 
    }

    fun updateAiRealTimeBlocking(e: Boolean) { viewModelScope.launch { settingsRepo.updateAiRealTimeBlocking(e) } }
    fun updateBlockDebtCollectors(e: Boolean) { viewModelScope.launch { settingsRepo.updateBlockDebtCollectors(e) } }
    fun updateBlockTelemarketers(e: Boolean) { viewModelScope.launch { settingsRepo.updateBlockTelemarketers(e) } }
    fun updateAiBlockingAccuracy(c: Int) { viewModelScope.launch { settingsRepo.updateAiBlockingAccuracy(c) } }
    fun updateWhitelistEnabled(e: Boolean) { viewModelScope.launch { settingsRepo.updateWhitelistEnabled(e) } }
    fun updateBlacklistEnabled(e: Boolean) { viewModelScope.launch { settingsRepo.updateBlacklistEnabled(e) } }
    fun updateSelectedModel(m: String) { viewModelScope.launch { settingsRepo.updateSelectedGeminiModel(m) } }
    fun updateAutoMaintenance(e: Boolean) { viewModelScope.launch { settingsRepo.updateAutoMaintenanceEnabled(e) } }
    fun updateDebugEnabled(e: Boolean) { viewModelScope.launch { settingsRepo.updateDebugEnabled(e) } }
    fun saveGeminiKey(k: String) { 
        val trimmed = k.trim()
        viewModelScope.launch { 
            CryptoManager.saveGeminiApiKey(getApplication(), trimmed)
            testGeminiKey(trimmed)
            refreshGeminiModels() 
        } 
    }
    fun clearGeminiKey() { viewModelScope.launch { CryptoManager.saveGeminiApiKey(getApplication(), ""); _apiKeyStatus.value = "Missing API Key"; _availableModels.value = emptyList() } }
    fun clearLookupCache() { viewModelScope.launch { dao.clearLookupCache() } }
    fun runMaintenanceNow() { viewModelScope.launch { WorkManager.getInstance(getApplication()).enqueue(OneTimeWorkRequestBuilder<BulkIdentifyWorker>().setInputData(workDataOf("isMaintenance" to true)).build()) } }
    fun requestIgnoreBatteryOptimizations(c: Context) { try { c.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${c.packageName}") }) } catch (e: Exception) { } }
    fun requestBackgroundLocation(c: Context) { try { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${c.packageName}") }) } catch (e: Exception) { } }
    
    fun refreshLocationStatus() {
        try {
            val context = getApplication<Application>()
            val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasBg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED else true
            _backgroundLocationGranted.value = hasFine && hasBg
        } catch (e: Exception) { }
    }

    fun refreshBatteryStatus() {
        try {
            val context = getApplication<Application>()
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            _isIgnoringBatteryOptimizations.value = pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) { }
    }

    private fun getLookupService() = GeminiPhoneLookupService(CryptoManager.getGeminiApiKey(getApplication()) ?: "", settings.value.selectedGeminiModel, dao, settings.value.debugEnabled)
    private fun getLookupDeepService() = GeminiPhoneLookupService(CryptoManager.getGeminiApiKey(getApplication()) ?: "", settings.value.selectedGeminiModel, dao, settings.value.debugEnabled)

    fun fetchIntelForNumber(number: String) { 
        viewModelScope.launch { 
            _pendingIntelResult.value = null // Clear old scan when viewing new number
            _selectedNumberIntel.value = dao.getLookupResult(number) 
        } 
    }
    fun addToWhitelist(number: String, label: String?) { 
        viewModelScope.launch { 
            val n = PhoneHelper.normalizeToE164(number)
            if (n.isNotBlank()) {
                val inheritedLabel = label ?: dao.getLookupResult(n)?.let { it.manualLabel ?: it.companyName ?: it.ownerName }
                dao.deleteBlacklistByPattern(n) // List Exclusivity
                dao.insertWhitelistEntry(WhitelistEntry(number = n, label = inheritedLabel)) 
            }
        } 
    }
    fun addToBlacklist(number: String, label: String?) { 
        viewModelScope.launch { 
            val n = PhoneHelper.normalizeToE164(number)
            if (n.isNotBlank()) {
                val inheritedLabel = label ?: dao.getLookupResult(n)?.let { it.manualLabel ?: it.companyName ?: it.ownerName }
                dao.deleteWhitelistByNumber(n) // List Exclusivity
                dao.insertBlacklistEntry(BlacklistEntry(pattern = n, label = inheritedLabel)) 
            }
        } 
    }
    fun updateFullNumberDetails(old: String, updated: PhoneLookupResult) { 
        viewModelScope.launch { 
            applyInvestigationResult(updated.phoneNumber, updated)
            _selectedNumberIntel.value = updated 
        } 
    }

    fun logToConsole(tag: String, msg: String, lvl: LogLevel) { ConsoleLogger.log(tag, msg, lvl) }
    fun clearConsole() { ConsoleLogger.clear() }
    fun removeFromBlacklist(e: BlacklistEntry) { viewModelScope.launch { dao.deleteBlacklistEntry(e) } }
    fun removeFromWhitelist(e: WhitelistEntry) { viewModelScope.launch { dao.deleteWhitelistEntry(e) } }
    fun clearHistory() { viewModelScope.launch { dao.deleteAllCallLogs() } }
    fun deleteCallLogEntry(e: CallLogEntry) { viewModelScope.launch { dao.deleteCallLogEntry(e) } }
    fun deleteNumberFromHistory(number: String) { viewModelScope.launch { dao.deleteCallLogByNumber(number) } }

    fun getFullBackupData(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val key = CryptoManager.getGeminiApiKey(getApplication())?.trim()
            val container = BackupContainer(
                blacklist = dao.getBlacklistSync(), whitelist = dao.getWhitelistSync(),
                callLogs = dao.getAllCallLogsSync(), blockedCalls = dao.getAllBlockedCallsSync(),
                areaCodeBlocks = dao.getAreaCodeBlocksSync(), prefixBlocks = dao.getPrefixBlocksSync(),
                lookupCache = dao.getAllLookupResultsSync(), geminiApiKey = key,
                settings = settings.value
            )
            onComplete(Gson().toJson(container))
        }
    }

    fun importNumbers(uri: Uri, toBlacklist: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val text = Scanner(stream).useDelimiter("\\A").next()
                    if (text.trim().startsWith("{") && text.contains("lookupCache")) handleFullBackupRestore(text)
                    else handleBulkProcessing(text, toBlacklist)
                }
            } catch (e: Exception) { }
            onComplete()
        }
    }

    fun pasteNumbers(text: String, toBlacklist: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch { handleBulkProcessing(text, toBlacklist); onComplete() }
    }

    private suspend fun handleFullBackupRestore(json: String) {
        val container = Gson().fromJson(json, BackupContainer::class.java)
        
        // 1. Data Imports
        container.blacklist.forEach { dao.insertBlacklistEntry(it) }
        container.whitelist.forEach { dao.insertWhitelistEntry(it) }
        container.callLogs.forEach { dao.insertCallLogEntry(it) }
        container.blockedCalls.forEach { dao.insertBlockedCall(it) }
        container.areaCodeBlocks.forEach { dao.insertAreaCodeBlock(it) }
        container.prefixBlocks.forEach { dao.insertPrefixBlock(it) }
        container.lookupCache.forEach { dao.insertLookupResult(it) }

        // 2. Settings Import (Priority) - Applied before Key test to ensure correct model
        container.settings?.let { s ->
            settingsRepo.restoreAllSettings(s)
        }

        // 3. API Key Import - Automatically Save, Test and Refresh
        container.geminiApiKey?.trim()?.let { key -> 
            if (key.isNotBlank()) { 
                saveGeminiKey(key)
            } 
        }
    }

    private suspend fun handleBulkProcessing(text: String, toBlacklist: Boolean) {
        text.split("\n").filter { it.isNotBlank() }.forEach { line ->
            val parts = if (line.contains(":")) line.split(":", limit = 2) else listOf(line)
            val label = if (parts.size == 2) parts[0].trim() else null
            val numberPart = if (parts.size == 2) parts[1].trim() else parts[0].trim()
            val normalized = PhoneHelper.normalizeToE164(numberPart)
            if (normalized.isNotBlank()) {
                if (toBlacklist) dao.insertBlacklistEntry(BlacklistEntry(pattern = normalized, label = label ?: "Imported"))
                else dao.insertWhitelistEntry(WhitelistEntry(number = normalized, label = label ?: "Imported"))
            }
        }
    }

    private fun scheduleMonthlyMaintenance() {
        val workRequest = PeriodicWorkRequestBuilder<BulkIdentifyWorker>(
            30, java.util.concurrent.TimeUnit.DAYS
        ).addTag("MONTHLY_MAINTENANCE").build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "MonthlyMaintenance", ExistingPeriodicWorkPolicy.KEEP, workRequest
        )
    }

    private fun scheduleMonthlySpamSync() {
        val workRequest = PeriodicWorkRequestBuilder<SpamSyncWorker>(
            30, java.util.concurrent.TimeUnit.DAYS
        ).addTag("MONTHLY_SPAM_SYNC").build()
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "MonthlySpamSync", ExistingPeriodicWorkPolicy.KEEP, workRequest
        )
    }

    private fun addChatMessage(entry: ChatEntry) {
        // Purged as requested
    }
}
