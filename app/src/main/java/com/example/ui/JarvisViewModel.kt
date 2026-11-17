package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.JarvisAiRepository
import com.example.data.firebase.JarvisRepository
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.JarvisLanguage
import com.example.data.model.JarvisNote
import com.example.data.model.JarvisReminder
import com.example.data.model.JarvisSettings
import com.example.data.model.UiState
import com.example.data.search.WebSearchRepository
import com.example.data.search.WebSearchResponse
import com.example.util.ExpressionEvaluator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface AiTurnState {
    data object Idle : AiTurnState
    data object Thinking : AiTurnState
    data class Streaming(val text: String) : AiTurnState
    data class Error(val message: String) : AiTurnState
}

sealed interface WebSearchUiState {
    data object Idle : WebSearchUiState
    data object Loading : WebSearchUiState
    data class Success(
        val response: WebSearchResponse,
        val aiSummary: String = "",
        val isSummarizing: Boolean = false,
        val summaryError: String? = null
    ) : WebSearchUiState
    data class Error(val message: String) : WebSearchUiState
}

data class CalculatorTapeItem(
    val expression: String,
    val result: String
)

data class CalculatorUiState(
    val expression: String = "",
    val previewResult: String = "0",
    val errorMessage: String? = null,
    val tape: List<CalculatorTapeItem> = emptyList()
)

class JarvisViewModel(
    private val repository: JarvisRepository,
    private val aiRepository: JarvisAiRepository,
    private val searchRepository: WebSearchRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _aiTurnState = MutableStateFlow<AiTurnState>(AiTurnState.Idle)
    val aiTurnState: StateFlow<AiTurnState> = _aiTurnState.asStateFlow()

    private val _attachedImage = MutableStateFlow<Bitmap?>(null)
    val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _webSearchState = MutableStateFlow<WebSearchUiState>(WebSearchUiState.Idle)
    val webSearchState: StateFlow<WebSearchUiState> = _webSearchState.asStateFlow()

    private val _calculatorState = MutableStateFlow(CalculatorUiState())
    val calculatorState: StateFlow<CalculatorUiState> = _calculatorState.asStateFlow()

    private var activeAiJob: Job? = null
    private var activeSummaryJob: Job? = null

    private fun unwrapFirestoreError(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    val sessionsState: StateFlow<UiState<List<ChatSession>>> = repository.observeSessions(currentUserId)
        .map<List<ChatSession>, UiState<List<ChatSession>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing sessions", root)
            emit(UiState.Error(root.message ?: "Failed to load chat history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, sessionId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreError(error)
                        Log.w(TAG, "Error observing messages", root)
                        emit(UiState.Error(root.message ?: "Failed to load conversation messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    val notesState: StateFlow<UiState<List<JarvisNote>>> = repository.observeNotes(currentUserId)
        .map<List<JarvisNote>, UiState<List<JarvisNote>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing notes", root)
            emit(UiState.Error(root.message ?: "Failed to load notes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val remindersState: StateFlow<UiState<List<JarvisReminder>>> = repository.observeReminders(currentUserId)
        .map<List<JarvisReminder>, UiState<List<JarvisReminder>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing reminders", root)
            emit(UiState.Error(root.message ?: "Failed to load reminders"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val settingsState: StateFlow<JarvisSettings> = repository.observeSettings(currentUserId)
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing settings", root)
            emit(JarvisSettings(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = JarvisSettings(userId = currentUserId)
        )

    fun attachImage(bitmap: Bitmap?) {
        _attachedImage.value = bitmap
    }

    fun startNewSession() {
        activeAiJob?.cancel()
        _activeSessionId.value = null
        _aiTurnState.value = AiTurnState.Idle
        _attachedImage.value = null
    }

    fun selectSession(sessionId: String) {
        activeAiJob?.cancel()
        _activeSessionId.value = sessionId
        _aiTurnState.value = AiTurnState.Idle
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun dismissAiError() {
        if (_aiTurnState.value is AiTurnState.Error) {
            _aiTurnState.value = AiTurnState.Idle
        }
    }

    fun sendMessage(
        rawPrompt: String,
        onCompletedReply: (String) -> Unit = {}
    ) {
        val image = _attachedImage.value
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && image == null) return

        val currentMessages = (messagesState.value as? UiState.Success)?.data.orEmpty()
        val activeLanguage = JarvisLanguage.fromCode(settingsState.value.languageCode)
        val displayPrompt = prompt.ifEmpty { "Analyze attached image" }

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiTurnState.value = AiTurnState.Thinking
            _attachedImage.value = null

            try {
                // Ensure we have a session ID in Firestore
                var sessionId = _activeSessionId.value
                val isFirstTurn = sessionId.isNullOrBlank()
                if (isFirstTurn) {
                    val created = repository.createSession(
                        userId = currentUserId,
                        title = displayPrompt.take(48),
                        language = activeLanguage
                    )
                    sessionId = created.getOrElse { err ->
                        _aiTurnState.value = AiTurnState.Error(
                            err.localizedMessage ?: "Failed to create chat session."
                        )
                        return@launch
                    }
                    _activeSessionId.value = sessionId
                }

                val currentCount = currentMessages.size.toLong()
                val appendUserResult = repository.appendMessage(
                    userId = currentUserId,
                    sessionId = sessionId!!,
                    role = "user",
                    text = displayPrompt,
                    hasImage = image != null,
                    language = activeLanguage,
                    updatedTitle = if (currentCount == 0L) displayPrompt.take(48) else null,
                    newMessageCount = currentCount + 1L
                )
                if (appendUserResult.isFailure) {
                    _bannerMessage.value = "Message saved locally; cloud sync encountered an error."
                }

                val nowText = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm:ss a z", Locale.getDefault())
                    .format(Date())

                var finalReply = ""
                aiRepository.streamAssistantReply(
                    history = currentMessages,
                    userPrompt = displayPrompt,
                    image = image,
                    language = activeLanguage,
                    currentDateTimeText = nowText
                ).collect { accumulated ->
                    finalReply = accumulated
                    _aiTurnState.value = AiTurnState.Streaming(accumulated)
                }

                if (finalReply.isNotBlank()) {
                    repository.appendMessage(
                        userId = currentUserId,
                        sessionId = sessionId,
                        role = "model",
                        text = finalReply,
                        hasImage = false,
                        language = activeLanguage,
                        newMessageCount = currentCount + 2L
                    )
                    _aiTurnState.value = AiTurnState.Idle
                    onCompletedReply(finalReply)
                } else {
                    _aiTurnState.value = AiTurnState.Error("Jarvis returned an empty response.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage failed", e)
                _aiTurnState.value = AiTurnState.Error(
                    e.localizedMessage ?: "Unable to reach Jarvis AI right now."
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val res = repository.deleteSession(currentUserId, sessionId)
            if (res.isSuccess) {
                if (_activeSessionId.value == sessionId) {
                    _activeSessionId.value = null
                }
                _bannerMessage.value = "Session deleted."
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete session."
            }
        }
    }

    // --- NOTES ---
    fun createNote(title: String, content: String) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() && cleanContent.isEmpty()) {
            _bannerMessage.value = "Enter a note title or content."
            return
        }
        viewModelScope.launch {
            val res = repository.createNote(
                userId = currentUserId,
                title = cleanTitle.ifEmpty { cleanContent.take(40) },
                content = cleanContent
            )
            _bannerMessage.value = if (res.isSuccess) {
                "Note saved to Jarvis archive."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save note."
            }
        }
    }

    fun toggleNotePinned(note: JarvisNote) {
        viewModelScope.launch {
            val res = repository.updateNote(
                userId = currentUserId,
                noteId = note.id,
                title = note.title,
                content = note.content,
                pinned = !note.pinned
            )
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update note."
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val res = repository.deleteNote(currentUserId, noteId)
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete note."
            }
        }
    }

    // --- REMINDERS ---
    fun createReminder(
        title: String,
        dueAtMillis: Long,
        onScheduled: (id: String, title: String, dueAtMillis: Long) -> Unit = { _, _, _ -> }
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _bannerMessage.value = "Enter a reminder description."
            return
        }
        viewModelScope.launch {
            val res = repository.createReminder(currentUserId, cleanTitle, dueAtMillis)
            if (res.isSuccess) {
                val id = res.getOrThrow()
                if (dueAtMillis > System.currentTimeMillis()) {
                    onScheduled(id, cleanTitle, dueAtMillis)
                }
                _bannerMessage.value = "Reminder added."
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save reminder."
            }
        }
    }

    fun toggleReminder(reminder: JarvisReminder) {
        viewModelScope.launch {
            val res = repository.toggleReminderCompleted(
                userId = currentUserId,
                reminderId = reminder.id,
                completed = !reminder.completed
            )
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update reminder."
            }
        }
    }

    fun deleteReminder(reminderId: String, onDeleted: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteReminder(currentUserId, reminderId)
            if (res.isSuccess) {
                onDeleted(reminderId)
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete reminder."
            }
        }
    }

    // --- SETTINGS ---
    fun updateSettings(
        language: JarvisLanguage = JarvisLanguage.fromCode(settingsState.value.languageCode),
        autoSpeak: Boolean = settingsState.value.autoSpeak,
        speechRate: Double = settingsState.value.speechRate,
        speechPitch: Double = settingsState.value.speechPitch
    ) {
        viewModelScope.launch {
            val res = repository.saveSettings(
                userId = currentUserId,
                language = language,
                autoSpeak = autoSpeak,
                speechRate = speechRate,
                speechPitch = speechPitch
            )
            if (res.isFailure) {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save settings."
            }
        }
    }

    // --- WEB SEARCH ---
    fun performWebSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val lang = JarvisLanguage.fromCode(settingsState.value.languageCode)
        activeSummaryJob?.cancel()
        viewModelScope.launch {
            _webSearchState.value = WebSearchUiState.Loading
            val res = searchRepository.search(trimmed, lang)
            _webSearchState.value = res.fold(
                onSuccess = { WebSearchUiState.Success(it) },
                onFailure = { WebSearchUiState.Error(it.localizedMessage ?: "Search unavailable.") }
            )
        }
    }

    fun summarizeCurrentSearch() {
        val current = _webSearchState.value as? WebSearchUiState.Success ?: return
        val lang = JarvisLanguage.fromCode(settingsState.value.languageCode)
        val snippets = buildString {
            current.response.instantAnswer?.let { appendLine("Instant Answer: $it") }
            current.response.results.forEachIndexed { idx, item ->
                appendLine("${idx + 1}. [${item.source}] ${item.title}: ${item.snippet}")
            }
        }
        activeSummaryJob?.cancel()
        activeSummaryJob = viewModelScope.launch {
            _webSearchState.value = current.copy(isSummarizing = true, summaryError = null, aiSummary = "")
            try {
                aiRepository.streamWebSummary(
                    query = current.response.query,
                    searchSnippets = snippets,
                    language = lang
                ).collect { text ->
                    val latest = _webSearchState.value as? WebSearchUiState.Success ?: return@collect
                    _webSearchState.value = latest.copy(aiSummary = text, isSummarizing = true)
                }
                val latest = _webSearchState.value as? WebSearchUiState.Success ?: return@launch
                _webSearchState.value = latest.copy(isSummarizing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latest = _webSearchState.value as? WebSearchUiState.Success ?: return@launch
                _webSearchState.value = latest.copy(
                    isSummarizing = false,
                    summaryError = e.localizedMessage ?: "Failed to summarize search results."
                )
            }
        }
    }

    // --- CALCULATOR ---
    fun appendCalcToken(token: String) {
        val nextExpr = _calculatorState.value.expression + token
        updateCalcPreview(nextExpr)
    }

    fun backspaceCalc() {
        val current = _calculatorState.value.expression
        if (current.isNotEmpty()) {
            updateCalcPreview(current.dropLast(1))
        }
    }

    fun clearCalc() {
        _calculatorState.value = _calculatorState.value.copy(
            expression = "",
            previewResult = "0",
            errorMessage = null
        )
    }

    fun evaluateCalc() {
        val expr = _calculatorState.value.expression.trim()
        if (expr.isEmpty()) return
        val res = ExpressionEvaluator.evaluate(expr)
        res.fold(
            onSuccess = { value ->
                val formatted = ExpressionEvaluator.formatResult(value)
                val newItem = CalculatorTapeItem(expression = expr, result = formatted)
                _calculatorState.value = _calculatorState.value.copy(
                    expression = formatted,
                    previewResult = formatted,
                    errorMessage = null,
                    tape = (listOf(newItem) + _calculatorState.value.tape).take(12)
                )
            },
            onFailure = { err ->
                _calculatorState.value = _calculatorState.value.copy(
                    errorMessage = err.message ?: "Invalid expression"
                )
            }
        )
    }

    private fun updateCalcPreview(expr: String) {
        if (expr.isEmpty()) {
            _calculatorState.value = _calculatorState.value.copy(
                expression = "",
                previewResult = "0",
                errorMessage = null
            )
            return
        }
        val eval = ExpressionEvaluator.evaluate(expr)
        val preview = eval.getOrNull()?.let { ExpressionEvaluator.formatResult(it) }
            ?: _calculatorState.value.previewResult
        _calculatorState.value = _calculatorState.value.copy(
            expression = expr,
            previewResult = preview,
            errorMessage = null
        )
    }

    private companion object {
        const val TAG = "JarvisVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
