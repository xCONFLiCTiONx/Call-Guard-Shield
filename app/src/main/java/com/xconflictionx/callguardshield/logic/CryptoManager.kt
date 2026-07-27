package com.xconflictionx.callguardshield.logic

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CryptoManager {
    private const val PREFS_NAME = "secure_settings"
    private const val BRAVE_API_KEY = "brave_api_key"
    private const val GEMINI_API_KEY = "gemini_api_key"

    private fun getSharedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
