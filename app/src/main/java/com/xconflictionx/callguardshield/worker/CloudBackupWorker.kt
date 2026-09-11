package com.xconflictionx.callguardshield.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import com.xconflictionx.callguardshield.data.AppDatabase
import com.xconflictionx.callguardshield.data.entity.BackupContainer
import com.xconflictionx.callguardshield.logic.CryptoManager
import com.xconflictionx.callguardshield.logic.ConsoleLogger
import com.xconflictionx.callguardshield.logic.GoogleDriveHelper
import com.xconflictionx.callguardshield.logic.StatusManager
import com.xconflictionx.callguardshield.ui.LogLevel
import kotlinx.coroutines.flow.first

class CloudBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString("mode") ?: "BACKUP"
        val context = applicationContext
        
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null || account.email == null) {
            Log.w("CLOUD_SYNC", "No signed in account found for $mode")
            // Don't log to console for background failures unless it's a specific API error we can fix
            return Result.failure()
        }

        return try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.callGuardShieldDao()
            val driveHelper = GoogleDriveHelper(context, account.email!!)
            
            // Silence "Connecting" message for a cleaner console
            val folderId = driveHelper.findOrCreateFolder("Call Guard Shield")
            val fileName = "backup.json"

            if (mode == "BACKUP") {
                Log.i("CLOUD_BACKUP", "Starting real backup to Google Drive...")
                setProgress(workDataOf("progress" to 0.2f))
                
                val settingsRepo = com.xconflictionx.callguardshield.data.repository.SettingsRepository(context)
                val currentSettings = settingsRepo.settingsFlow.first()

                val container = BackupContainer(
                    blacklist = dao.getBlacklistSync(),
                    whitelist = dao.getWhitelistSync(),
                    callLogs = dao.getAllCallLogsSync(),
                    blockedCalls = dao.getAllBlockedCallsSync(),
                    prefixBlocks = dao.getPrefixBlocksSync(),
                    lookupCache = dao.getAllLookupResultsSync(),
                    geminiApiKey = CryptoManager.getGeminiApiKey(context),
                    settings = currentSettings
                )
                
                val json = Gson().toJson(container)
                setProgress(workDataOf("progress" to 0.6f))
                
                val success = driveHelper.uploadBackup(folderId, fileName, json)
                if (success) {
                    Log.i("CLOUD_BACKUP", "Backup uploaded successfully")
                    // Silence success message
                    Result.success()
                } else {
                    Log.e("CLOUD_BACKUP", "Upload failed")
                    Result.retry()
                }
            } else {
                Log.i("CLOUD_RESTORE", "Starting real restore from Google Drive...")
                setProgress(workDataOf("progress" to 0.3f))
                
                val json = driveHelper.downloadBackup(folderId, fileName)
                if (json != null) {
                    val container = Gson().fromJson(json, BackupContainer::class.java)
                    setProgress(workDataOf("progress" to 0.7f))
                    
                    // Apply restore
                    container.blacklist.forEach { dao.insertBlacklistEntry(it) }
                    container.whitelist.forEach { dao.insertWhitelistEntry(it) }
                    container.callLogs.forEach { dao.insertCallLogEntry(it) }
                    container.blockedCalls.forEach { dao.insertBlockedCall(it) }
                    container.prefixBlocks.forEach { dao.insertPrefixBlock(it) }
                    container.lookupCache.forEach { dao.insertLookupResult(it) }
                    
                    // NEW: Restore settings profile
                    container.settings?.let { s ->
                        val settingsRepo = com.xconflictionx.callguardshield.data.repository.SettingsRepository(context)
                        settingsRepo.restoreAllSettings(s)
                    }

                    // NEW: Restore and save API Key
                    container.geminiApiKey?.trim()?.let { key ->
                        if (key.isNotBlank()) {
                            CryptoManager.saveGeminiApiKey(context, key)
                        }
                    }
                    
                    Log.i("CLOUD_RESTORE", "Restore completed successfully")
                    Result.success()
                } else {
                    Log.w("CLOUD_RESTORE", "No backup file found in Drive")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("403 Forbidden")) {
                StatusManager.setDriveError("API_DISABLED")
                ConsoleLogger.log("CLOUD", "ACTION REQUIRED: Google Drive API is disabled in your cloud project.", LogLevel.ERROR)
            } else {
                // Log other fatal errors for troubleshooting
                ConsoleLogger.log("CLOUD", "Sync error: ${e.message}", LogLevel.ERROR)
            }
            Log.e("CLOUD_SYNC", "$mode failed: ${e.message}", e)
            Result.retry()
        }
    }
}
