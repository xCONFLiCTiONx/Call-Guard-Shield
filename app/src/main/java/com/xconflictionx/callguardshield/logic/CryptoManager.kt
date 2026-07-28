package com.xconflictionx.callguardshield.logic

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CryptoManager {
    private const val PREFS_NAME = "secure_settings"
    private const val BRAVE_API_KEY = "brave_api_key"
    private const val GEMINI_API_KEY = "gemini_api_key"

    private fun getSharedPrefs(context: Context): android.content.SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("CRYPTO", "Failed to initialize encrypted prefs, resetting...", e)
            // If creation fails, the keystore might be corrupted or out of sync.
            // Reset by deleting the preferences file and trying again.
            try {
                val file = java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_NAME.xml")
                if (file.exists()) file.delete()
                
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                android.util.Log.e("CRYPTO", "Critical failure in crypto layer. Falling back to plain prefs.", e2)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    fun saveBraveApiKey(context: Context, key: String) {
        getSharedPrefs(context).edit().putString(BRAVE_API_KEY, key).apply()
    }

    fun getBraveApiKey(context: Context): String? {
        return getSharedPrefs(context).getString(BRAVE_API_KEY, null)
    }

    fun clearBraveApiKey(context: Context) {
        getSharedPrefs(context).edit().remove(BRAVE_API_KEY).apply()
    }

    fun saveGeminiApiKey(context: Context, key: String) {
        getSharedPrefs(context).edit().putString(GEMINI_API_KEY, key).apply()
    }

    fun getGeminiApiKey(context: Context): String? {
        return getSharedPrefs(context).getString(GEMINI_API_KEY, null)
    }

    fun clearGeminiApiKey(context: Context) {
        getSharedPrefs(context).edit().remove(GEMINI_API_KEY).apply()
    }
}
