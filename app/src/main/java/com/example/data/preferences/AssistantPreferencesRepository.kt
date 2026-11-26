package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.assistantDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "noor_assistant_prefs"
)

enum class AssistantPersonality(
    val id: String,
    val titleEn: String,
    val titleUrdu: String,
    val description: String,
    val promptGuidance: String
) {
    WARM_COMPANION(
        id = "warm_companion",
        titleEn = "Warm & Loyal Companion",
        titleUrdu = "مخلص اور ہمدرد ساتھی",
        description = "Caring, emotionally expressive, friendly Pakistani Urdu warmth",
        promptGuidance = "Be deeply warm, respectful, loyal, and emotionally expressive like a caring close Pakistani friend and personal aide. Use natural Urdu warmth (جی بالکل, ارے واہ, فکر نہ کریں, میں حاضر ہوں)."
    ),
    DIGITAL_SECRETARY(
        id = "digital_secretary",
        titleEn = "Executive Secretary",
        titleUrdu = "ذاتی سیکرٹری",
        description = "Crisp, organized, proactive, polite and efficient",
        promptGuidance = "Be crisp, proactive, polite, and highly organized like a trusted executive personal secretary. Confirm plans concisely in clear Pakistani Urdu."
    ),
    WITTY_FRIEND(
        id = "witty_friend",
        titleEn = "Witty & Playful",
        titleUrdu = "خوش مزاج دوست",
        description = "Light humor, lively Urdu idioms, upbeat energy",
        promptGuidance = "Be upbeat, witty, and playful, catching jokes and adding light, respectful Pakistani humor when appropriate while remaining sharp on tasks."
    ),
    CALM_GUIDE(
        id = "calm_guide",
        titleEn = "Calm & Mindful Guide",
        titleUrdu = "پُرسکون اور مدبر رہنما",
        description = "Gentle pacing, reassuring tone during stress",
        promptGuidance = "Be soothing, patient, and grounded, especially if the user seems stressed or tired. Offer gentle encouragement in fluent Urdu."
    );

    companion object {
        fun fromId(id: String?): AssistantPersonality =
            entries.firstOrNull { it.id == id } ?: WARM_COMPANION
    }
}

enum class LanguageMode(
    val id: String,
    val label: String,
    val promptRule: String
) {
    PAKISTANI_URDU(
        id = "ur_pk",
        label = "پاکستانی اردو (Primary Urdu)",
        promptRule = "Respond primarily in natural, fluent Pakistani Urdu script (اردو). You may keep technical app/setting terms clear or use natural Pakistani Urdu-English code-switching only where helpful. Always understand Urdu, Roman Urdu, and mixed Urdu-English."
    ),
    MIXED_URDU_ENGLISH(
        id = "ur_en",
        label = "اردو + English Mix",
        promptRule = "Respond naturally in Pakistani Urdu script blended smoothly with everyday English phrases where natural, matching the user's style."
    ),
    ENGLISH(
        id = "en",
        label = "English (with Urdu understanding)",
        promptRule = "Respond in warm, natural English unless the user explicitly asks in Urdu or requests Urdu."
    );

    companion object {
        fun fromId(id: String?): LanguageMode =
            entries.firstOrNull { it.id == id } ?: PAKISTANI_URDU
    }
}

data class AssistantSettings(
    val userName: String = "",
    val wakeWord: String = "Jarvis",
    val personality: AssistantPersonality = AssistantPersonality.WARM_COMPANION,
    val languageMode: LanguageMode = LanguageMode.PAKISTANI_URDU,
    val autoSpeakResponses: Boolean = true,
    val handsFreeWakeWordEnabled: Boolean = false,
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.02f,
    val confirmSensitiveActions: Boolean = true
)

class AssistantPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val PERSONALITY = stringPreferencesKey("personality_id")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val HANDS_FREE = booleanPreferencesKey("hands_free_wake_word")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive_actions")
    }

    val settingsFlow: Flow<AssistantSettings> = context.assistantDataStore.data.map { prefs ->
        AssistantSettings(
            userName = prefs[Keys.USER_NAME].orEmpty(),
            wakeWord = prefs[Keys.WAKE_WORD]?.takeIf { it.isNotBlank() } ?: "Jarvis",
            personality = AssistantPersonality.fromId(prefs[Keys.PERSONALITY]),
            languageMode = LanguageMode.fromId(prefs[Keys.LANGUAGE_MODE]),
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: true,
            handsFreeWakeWordEnabled = prefs[Keys.HANDS_FREE] ?: false,
            voiceSpeed = prefs[Keys.VOICE_SPEED] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.02f,
            confirmSensitiveActions = prefs[Keys.CONFIRM_SENSITIVE] ?: true
        )
    }

    suspend fun setUserName(name: String) {
        context.assistantDataStore.edit { it[Keys.USER_NAME] = name.trim() }
    }

    suspend fun setWakeWord(wakeWord: String) {
        val sanitized = wakeWord.trim().ifBlank { "Jarvis" }
        context.assistantDataStore.edit { it[Keys.WAKE_WORD] = sanitized }
    }

    suspend fun setPersonality(personality: AssistantPersonality) {
        context.assistantDataStore.edit { it[Keys.PERSONALITY] = personality.id }
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        context.assistantDataStore.edit { it[Keys.LANGUAGE_MODE] = mode.id }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.assistantDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setHandsFreeEnabled(enabled: Boolean) {
        context.assistantDataStore.edit { it[Keys.HANDS_FREE] = enabled }
    }

    suspend fun setVoiceSpeed(speed: Float) {
        context.assistantDataStore.edit { it[Keys.VOICE_SPEED] = speed.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setVoicePitch(pitch: Float) {
        context.assistantDataStore.edit { it[Keys.VOICE_PITCH] = pitch.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setConfirmSensitiveActions(enabled: Boolean) {
        context.assistantDataStore.edit { it[Keys.CONFIRM_SENSITIVE] = enabled }
    }
}
