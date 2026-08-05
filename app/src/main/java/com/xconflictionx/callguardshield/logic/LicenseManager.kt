package com.xconflictionx.callguardshield.logic

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.xconflictionx.callguardshield.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Manages app licensing and integrity checks for Google Play Store.
 */
object LicenseManager {

    /**
     * Checks if the app has a valid license/integrity state.
     * In DEBUG mode or if BYPASS_LICENSE_CHECK is true, it always returns DEBUG.
     */
    suspend fun checkLicense(context: Context): LicenseStatus {
        if (BuildConfig.DEBUG || BuildConfig.BYPASS_LICENSE_CHECK) {
            return LicenseStatus.Valid(AppMode.DEBUG)
        }

        return try {
            val integrityManager = IntegrityManagerFactory.create(context.applicationContext)
            
            // Request an integrity token (Basic check for Play Store installation)
            val tokenRequest = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(38581640934) // From the Google Drive project
                .build()
            
            integrityManager.requestIntegrityToken(tokenRequest).await()
            
            // For a simple implementation, if we get a token from Play Integrity, 
            // we assume it's a valid PRO version for now.
            LicenseStatus.Valid(AppMode.PRO)
        } catch (e: Exception) {
            // If the check fails (e.g. sideloaded), enter EVALUATION mode instead of hard-locking
            LicenseStatus.Valid(AppMode.EVALUATION)
        }
    }
}

enum class AppMode { DEBUG, PRO, EVALUATION }

sealed class LicenseStatus {
    data class Valid(val mode: AppMode) : LicenseStatus()
    data class Invalid(val message: String) : LicenseStatus()
}
