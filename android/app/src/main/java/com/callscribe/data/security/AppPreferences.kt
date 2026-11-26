package com.callscribe.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class ExportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/markdown"),
    PLAIN_TEXT("txt", "text/plain")
}

enum class ChineseVariant {
    TRADITIONAL,
    SIMPLIFIED
}

class AppPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "callscribe_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val normalPrefs: SharedPreferences =
        context.getSharedPreferences("callscribe_settings", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = encryptedPrefs.getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
        set(value) {
            encryptedPrefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()
        }

    var watchedFolderUri: String?
        get() = normalPrefs.getString(KEY_WATCHED_FOLDER_URI, null)
        set(value) {
            normalPrefs.edit().putString(KEY_WATCHED_FOLDER_URI, value).apply()
        }

    var exportFormat: ExportFormat
        get() {
            val saved = normalPrefs.getString(KEY_EXPORT_FORMAT, ExportFormat.PLAIN_TEXT.name)
            return ExportFormat.entries.firstOrNull { it.name == saved } ?: ExportFormat.PLAIN_TEXT
        }
        set(value) {
            normalPrefs.edit().putString(KEY_EXPORT_FORMAT, value.name).apply()
        }

    var wifiOnlyUpload: Boolean
        get() = normalPrefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) {
            normalPrefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        }

    var enableAiSummary: Boolean
        get() = normalPrefs.getBoolean(KEY_ENABLE_AI_SUMMARY, false)
        set(value) {
            normalPrefs.edit().putBoolean(KEY_ENABLE_AI_SUMMARY, value).apply()
        }

    var chineseVariant: ChineseVariant
        get() {
            val saved = normalPrefs.getString(KEY_CHINESE_VARIANT, ChineseVariant.TRADITIONAL.name)
            return ChineseVariant.entries.firstOrNull { it.name == saved } ?: ChineseVariant.TRADITIONAL
        }
        set(value) {
            normalPrefs.edit().putString(KEY_CHINESE_VARIANT, value.name).apply()
        }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_WATCHED_FOLDER_URI = "watched_folder_uri"
        private const val KEY_EXPORT_FORMAT = "export_format"
        private const val KEY_WIFI_ONLY = "wifi_only_upload"
        private const val KEY_ENABLE_AI_SUMMARY = "enable_ai_summary"
        private const val KEY_CHINESE_VARIANT = "chinese_variant"
    }
}
