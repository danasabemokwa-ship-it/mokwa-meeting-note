package com.meetnotes.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.meetnotes.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ApiProvider(val label: String, val consoleUrl: String) {
    OPENAI("OpenAI", "https://platform.openai.com/api-keys"),
    GEMINI("Google Gemini", "https://aistudio.google.com/app/apikey"),
    ANTHROPIC("Anthropic", "https://console.anthropic.com/settings/keys"),
}

/**
 * Stores API keys encrypted with a hardware-backed (Android Keystore) master key.
 *
 * HOW TO ADD YOUR KEYS
 *  1. Recommended: open the app → Settings → "API keys" and paste each key. They never leave the device
 *     except in the Authorization header of requests to that provider.
 *  2. Developer shortcut: add OPENAI_API_KEY / GEMINI_API_KEY / ANTHROPIC_API_KEY to local.properties.
 *     They are compiled into BuildConfig and used only when no key was entered in Settings.
 *     Never distribute an APK built this way.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_api_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Keystore problems are rare (e.g. corrupted keystore after restore). We deliberately do not
            // fall back to plain-text storage; cloud features simply stay unavailable.
            Log.e("SecureKeyStore", "Encrypted storage unavailable", e)
            null
        }
    }

    fun get(provider: ApiProvider): String? =
        prefs?.getString(provider.name, null)?.takeIf { it.isNotBlank() } ?: buildConfigKey(provider)

    fun set(provider: ApiProvider, key: String) {
        prefs?.edit()?.apply {
            if (key.isBlank()) remove(provider.name) else putString(provider.name, key.trim())
        }?.apply()
    }

    fun has(provider: ApiProvider): Boolean = get(provider) != null

    /** Shows only the last 4 characters so users can tell which key is saved. */
    fun masked(provider: ApiProvider): String? = get(provider)?.let { "••••" + it.takeLast(4) }

    private fun buildConfigKey(provider: ApiProvider): String? = when (provider) {
        ApiProvider.OPENAI -> BuildConfig.OPENAI_API_KEY
        ApiProvider.GEMINI -> BuildConfig.GEMINI_API_KEY
        ApiProvider.ANTHROPIC -> BuildConfig.ANTHROPIC_API_KEY
    }.takeIf { it.isNotBlank() }
}
