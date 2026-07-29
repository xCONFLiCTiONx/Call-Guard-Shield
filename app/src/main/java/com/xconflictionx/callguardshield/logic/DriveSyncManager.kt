package com.xconflictionx.callguardshield.logic

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.xconflictionx.callguardshield.data.entity.BackupContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Collections

object DriveSyncManager {
    private const val TAG = "DriveSyncManager"
    private const val BACKUP_FILE_NAME = "call_guard_backup.json"

    private var driveService: Drive? = null

    suspend fun signIn(context: Context): String? = withContext(Dispatchers.Main) {
        try {
            val credentialManager = CredentialManager.create(context)
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("YOUR_SERVER_CLIENT_ID") // In a real app, this would be injected
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                val email = credential.id
                initializeDriveService(context, email)
                return@withContext email
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            null
        }
    }

    private fun initializeDriveService(context: Context, accountName: String) {
        val googleCredential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccountName = accountName
        }

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            googleCredential
        ).setApplicationName("Call Guard Shield").build()
    }

    suspend fun uploadBackup(context: Context, container: BackupContainer): Boolean = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext false
        try {
            val json = Gson().toJson(container)
            val tempFile = java.io.File(context.cacheDir, BACKUP_FILE_NAME)
            tempFile.writeText(json)

            // Find existing file in appDataFolder
            val query = "name = '$BACKUP_FILE_NAME' and trashed = false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .execute()

            val existingFile = result.files.firstOrNull()

            val mediaContent = FileContent("application/json", tempFile)
            val metadata = File().apply {
                name = BACKUP_FILE_NAME
                if (existingFile == null) {
                    parents = Collections.singletonList("appDataFolder")
                }
            }

            if (existingFile != null) {
                service.files().update(existingFile.id, null, mediaContent).execute()
            } else {
                service.files().create(metadata, mediaContent).execute()
            }
            tempFile.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            false
        }
    }

    suspend fun downloadBackup(): BackupContainer? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        try {
            val query = "name = '$BACKUP_FILE_NAME' and trashed = false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(id, modifiedTime)")
                .execute()

            val file = result.files.firstOrNull() ?: return@withContext null

            val outputStream = ByteArrayOutputStream()
            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
            val json = outputStream.toString()
            Gson().fromJson(json, BackupContainer::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }
}
