package com.meetnotes.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meetnotes.app.domain.model.AppSettings
import com.meetnotes.app.domain.model.AudioQuality
import com.meetnotes.app.domain.model.SummarizerType
import com.meetnotes.app.domain.model.SummaryTone
import com.meetnotes.app.domain.model.TranscriptionEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENGINE = stringPreferencesKey("transcription_engine")
        val SUMMARIZER = stringPreferencesKey("summarizer")
        val LANGUAGE = stringPreferencesKey("language")
        val NIGERIAN = booleanPreferencesKey("nigerian_speech")
        val GLOSSARY = stringPreferencesKey("glossary")
        val TONE = stringPreferencesKey("default_tone")
        val QUALITY = stringPreferencesKey("audio_quality")
        val AUTO_PROCESS = booleanPreferencesKey("auto_process")
        val AUTO_DELETE = intPreferencesKey("auto_delete_days")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val ANTHROPIC_MODEL = stringPreferencesKey("anthropic_model")
        val USER_NAME = stringPreferencesKey("user_name")
        val ORGANISATION = stringPreferencesKey("organisation")
        val RECIPIENTS = stringPreferencesKey("default_recipients")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { read(it) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            transcriptionEngine = p[Keys.ENGINE].toEnum(d.transcriptionEngine),
            summarizer = p[Keys.SUMMARIZER].toEnum(d.summarizer),
            language = p[Keys.LANGUAGE] ?: d.language,
            nigerianSpeech = p[Keys.NIGERIAN] ?: d.nigerianSpeech,
            glossary = p[Keys.GLOSSARY] ?: d.glossary,
            defaultTone = p[Keys.TONE].toEnum(d.defaultTone),
            audioQuality = p[Keys.QUALITY].toEnum(d.audioQuality),
            autoProcess = p[Keys.AUTO_PROCESS] ?: d.autoProcess,
            autoDeleteDays = p[Keys.AUTO_DELETE] ?: d.autoDeleteDays,
            onboardingDone = p[Keys.ONBOARDING] ?: d.onboardingDone,
            openAiModel = p[Keys.OPENAI_MODEL]?.takeIf { it.isNotBlank() } ?: d.openAiModel,
            geminiModel = p[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: d.geminiModel,
            anthropicModel = p[Keys.ANTHROPIC_MODEL]?.takeIf { it.isNotBlank() } ?: d.anthropicModel,
            userName = p[Keys.USER_NAME] ?: d.userName,
            organisation = p[Keys.ORGANISATION] ?: d.organisation,
            defaultRecipients = p[Keys.RECIPIENTS] ?: d.defaultRecipients,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[Keys.ENGINE] = s.transcriptionEngine.name
        p[Keys.SUMMARIZER] = s.summarizer.name
        p[Keys.LANGUAGE] = s.language
        p[Keys.NIGERIAN] = s.nigerianSpeech
        p[Keys.GLOSSARY] = s.glossary
        p[Keys.TONE] = s.defaultTone.name
        p[Keys.QUALITY] = s.audioQuality.name
        p[Keys.AUTO_PROCESS] = s.autoProcess
        p[Keys.AUTO_DELETE] = s.autoDeleteDays
        p[Keys.ONBOARDING] = s.onboardingDone
        p[Keys.OPENAI_MODEL] = s.openAiModel.trim()
        p[Keys.GEMINI_MODEL] = s.geminiModel.trim()
        p[Keys.ANTHROPIC_MODEL] = s.anthropicModel.trim()
        p[Keys.USER_NAME] = s.userName.trim()
        p[Keys.ORGANISATION] = s.organisation.trim()
        p[Keys.RECIPIENTS] = s.defaultRecipients.trim()
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

/** Languages offered in Settings (code to label). Nigerian English / Pidgin use English models plus Nigerian context. */
val LANGUAGES: List<Pair<String, String>> = listOf(
    "en-NG" to "Nigerian English",
    "pcm" to "Nigerian Pidgin",
    "auto" to "Auto-detect (mixed languages)",
    "en" to "English (international)",
    "ha" to "Hausa",
    "yo" to "Yoruba",
    "ig" to "Igbo (best with Gemini)",
    "fr" to "French",
    "ar" to "Arabic",
    "sw" to "Swahili",
    "pt" to "Portuguese",
    "es" to "Spanish",
    "de" to "German",
)
