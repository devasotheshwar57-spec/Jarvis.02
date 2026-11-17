package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.JarvisLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "JarvisAiRepository"

enum class AiSpeedMode(val modelName: String, val shortLabel: String, val displayLabel: String) {
    FAST("gemini-3.5-flash-lite", "FAST", "Gemini 3.5 Flash-Lite (Ultra Fast)"),
    BALANCED("gemini-3.8-flash", "PRO", "Gemini 3.8 Flash (Balanced)")
}

class JarvisAiRepository {

    private val fastModel by lazy {
        Firebase.ai.generativeModel(modelName = AiSpeedMode.FAST.modelName)
    }

    private val balancedModel by lazy {
        Firebase.ai.generativeModel(modelName = AiSpeedMode.BALANCED.modelName)
    }

    private fun modelFor(speedMode: AiSpeedMode) = when (speedMode) {
        AiSpeedMode.FAST -> fastModel
        AiSpeedMode.BALANCED -> balancedModel
    }

    fun streamAssistantReply(
        history: List<ChatMessage>,
        userPrompt: String,
        image: Bitmap?,
        language: JarvisLanguage,
        currentDateTimeText: String,
        speedMode: AiSpeedMode = AiSpeedMode.FAST
    ): Flow<String> = flow {
        val systemDirective = buildString {
            appendLine("You are JARVIS, a futuristic, hyper-intelligent personal AI assistant inspired by Iron Man's HUD AI.")
            appendLine("Be concise, articulate, polite, and practical.")
            appendLine("Current device date & time: $currentDateTimeText.")
            appendLine("Language rule: ${language.promptInstruction}")
            appendLine("Do not invent real-time external sensor data or claim actions were executed on external accounts unless stated.")
        }

        val recentHistory = history.takeLast(10).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                text(msg.text)
            }
        }

        val chatHistory = listOf(
            content("user") { text("[SYSTEM DIRECTIVE]\n$systemDirective") },
            content("model") {
                text(
                    when (language) {
                        JarvisLanguage.ENGLISH -> "Understood. Systems calibrated. Ready to assist."
                        JarvisLanguage.HINDI -> "समझ गया। सिस्टम तैयार हैं। बताइए, मैं आपकी क्या सहायता कर सकता हूँ?"
                        JarvisLanguage.TELUGU -> "అర్థమైంది. సిస్టమ్‌లు సిద్ధంగా ఉన్నాయి. నేను మీకు ఎలా సహాయపడగలను?"
                    }
                )
            }
        ) + recentHistory

        val userContent = content("user") {
            if (image != null) {
                image(image)
            }
            text(
                userPrompt.ifBlank {
                    when (language) {
                        JarvisLanguage.ENGLISH -> "Analyze this image in detail."
                        JarvisLanguage.HINDI -> "इस छवि का विस्तार से विश्लेषण करें।"
                        JarvisLanguage.TELUGU -> "ఈ చిత్రాన్ని వివరంగా విశ్లేషించండి."
                    }
                }
            )
        }

        val chat = modelFor(speedMode).startChat(history = chatHistory)
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(userContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Jarvis AI returned an empty response. Please try again.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            throw IllegalStateException("AI response timed out after 60 seconds. Please check your connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            throw e
        }
    }

    fun streamWebSummary(
        query: String,
        searchSnippets: String,
        language: JarvisLanguage,
        speedMode: AiSpeedMode = AiSpeedMode.FAST
    ): Flow<String> = flow {
        val prompt = buildString {
            appendLine("You are JARVIS. ${language.promptInstruction}")
            appendLine("Summarize the following live web search results clearly and accurately for the user's query: \"$query\".")
            appendLine("Cite key facts concisely and note if the retrieved snippets don't fully answer the query.")
            appendLine()
            appendLine(searchSnippets)
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                modelFor(speedMode).generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Summary returned empty output.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Web summary timed out", e)
            throw IllegalStateException("Summary timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Web summary failed", e)
            throw e
        }
    }
}
