package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class JarvisLanguage(
    val code: String,
    val nativeLabel: String,
    val englishLabel: String,
    val localeTag: String,
    val promptInstruction: String
) {
    ENGLISH(
        code = "en",
        nativeLabel = "English",
        englishLabel = "English",
        localeTag = "en-US",
        promptInstruction = "Always respond in crisp, intelligent English."
    ),
    HINDI(
        code = "hi",
        nativeLabel = "हिन्दी",
        englishLabel = "Hindi",
        localeTag = "hi-IN",
        promptInstruction = "Always respond naturally in Hindi (Devanagari script, हिन्दी), keeping technical terms clear."
    ),
    TELUGU(
        code = "te",
        nativeLabel = "తెలుగు",
        englishLabel = "Telugu",
        localeTag = "te-IN",
        promptInstruction = "Always respond naturally in Telugu (Telugu script, తెలుగు), keeping technical terms clear."
    );

    companion object {
        fun fromCode(raw: String?): JarvisLanguage =
            entries.firstOrNull { it.code == raw } ?: ENGLISH
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val lastMessage: String = "",
    val languageCode: String = "en",
    val messageCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val role: String = "user",
    val text: String = "",
    val hasImage: Boolean = false,
    val languageCode: String = "en",
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JarvisNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JarvisReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val dueAtMillis: Long = 0L,
    val completed: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class JarvisSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val languageCode: String = "en",
    val autoSpeak: Boolean = true,
    val speechRate: Double = 1.0,
    val speechPitch: Double = 1.0,
    val updatedAt: Timestamp? = null
)
