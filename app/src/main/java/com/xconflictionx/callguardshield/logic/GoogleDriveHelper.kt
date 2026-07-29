package com.xconflictionx.callguardshield.logic

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections

class GoogleDriveHelper(private val context: Context, accountName: String) {

    private val driveService: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccountName = accountName
        }

        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Call Guard Shield").build()
    }

    suspend fun findOrCreateFolder(folderName: String): String = withContext(Dispatchers.IO) {
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result: FileList = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val folder = result.files.firstOrNull()
        if (folder != null) {
            folder.id
        } else {
            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }
            val createdFolder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()
            createdFolder.id
        }
    }

    suspend fun uploadBackup(folderId: String, fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Find existing file
            val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
            val result: FileList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val existingFile = result.files.firstOrNull()

            val tempFile = java.io.File(context.cacheDir, fileName)
            tempFile.writeText(content)

            val mediaContent = FileContent("application/json", tempFile)
            val fileMetadata = File().apply {
                name = fileName
                if (existingFile == null) {
                    parents = Collections.singletonList(folderId)
                }
            }

            if (existingFile != null) {
                driveService.files().update(existingFile.id, null, mediaContent).execute()
            } else {
                driveService.files().create(fileMetadata, mediaContent).execute()
            }
            tempFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadBackup(folderId: String, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
            val result: FileList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val file = result.files.firstOrNull() ?: return@withContext null

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(file.id).executeMediaAndDownloadTo(outputStream)
            outputStream.toString()
        } catch (e: Exception) {
            null
        }
    }
}
