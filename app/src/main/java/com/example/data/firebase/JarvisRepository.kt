package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.JarvisLanguage
import com.example.data.model.JarvisNote
import com.example.data.model.JarvisReminder
import com.example.data.model.JarvisSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchedUserId(expectedUid: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access your Jarvis archive.")
        check(uid == expectedUid) { "Active user changed; refusing cross-user access." }
        return uid
    }

    private fun handleFlowError(error: Throwable, path: String, op: OperationType, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // --- SESSIONS & MESSAGES ---
    fun observeSessions(userId: String): Flow<List<ChatSession>> {
        val path = "users/$userId/sessions"
        return db.collection("users").document(userId).collection("sessions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error -> handleFlowError(error, path, OperationType.LIST, userId) }
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/sessions/$sessionId/messages"
        return db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error -> handleFlowError(error, path, OperationType.LIST, userId) }
    }

    fun newSessionId(userId: String): String =
        db.collection("users").document(userId).collection("sessions").document().id

    suspend fun createSessionWithFirstMessage(
        userId: String,
        sessionId: String,
        title: String,
        text: String,
        hasImage: Boolean,
        language: JarvisLanguage
    ): Result<Unit> {
        val sessionRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
        val msgRef = sessionRef.collection("messages").document()
        return try {
            val uid = requireMatchedUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            val cleanTitle = title.trim().ifEmpty { "New Briefing" }.take(120)
            val cleanText = text.trim().ifEmpty { "[Empty]" }.take(16000)

            val sessionPayload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "lastMessage" to cleanText.replace("\n", " ").take(300),
                "languageCode" to language.code,
                "messageCount" to 1L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val msgPayload = mapOf(
                "userId" to uid,
                "sessionId" to sessionId,
                "role" to "user",
                "text" to cleanText,
                "hasImage" to hasImage,
                "languageCode" to language.code,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(sessionRef, sessionPayload)
            batch.set(msgRef, msgPayload)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionRef.path)
            Result.failure(e)
        }
    }

    suspend fun createSession(
        userId: String,
        title: String,
        language: JarvisLanguage
    ): Result<String> {
        val docRef = db.collection("users").document(userId).collection("sessions").document()
        return try {
            val uid = requireMatchedUserId(userId)
            val cleanTitle = title.trim().ifEmpty { "New Briefing" }.take(120)
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "lastMessage" to "",
                "languageCode" to language.code,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        userId: String,
        sessionId: String,
        role: String,
        text: String,
        hasImage: Boolean,
        language: JarvisLanguage,
        updatedTitle: String? = null,
        newMessageCount: Long
    ): Result<Unit> {
        val sessionRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
        val msgRef = sessionRef.collection("messages").document()
        return try {
            val uid = requireMatchedUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            val cleanText = text.trim().ifEmpty { "[Empty]" }.take(16000)
            val msgPayload = mapOf(
                "userId" to uid,
                "sessionId" to sessionId,
                "role" to if (role == "model") "model" else "user",
                "text" to cleanText,
                "hasImage" to hasImage,
                "languageCode" to language.code,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val sessionUpdates = mutableMapOf<String, Any>(
                "lastMessage" to cleanText.replace("\n", " ").take(300),
                "languageCode" to language.code,
                "messageCount" to newMessageCount.coerceIn(0L, 10000L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!updatedTitle.isNullOrBlank()) {
                sessionUpdates["title"] = updatedTitle.trim().take(120)
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(sessionRef, sessionUpdates)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> {
        val sessionRef = db.collection("users").document(userId)
            .collection("sessions").document(sessionId)
        return try {
            requireMatchedUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID cannot be blank." }
            val messagesSnapshot = sessionRef.collection("messages").get().await()
            val batch = db.batch()
            messagesSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.delete(sessionRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionRef.path)
            Result.failure(e)
        }
    }

    // --- NOTES ---
    fun observeNotes(userId: String): Flow<List<JarvisNote>> {
        val path = "users/$userId/notes"
        return db.collection("users").document(userId).collection("notes")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    JarvisNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error -> handleFlowError(error, path, OperationType.LIST, userId) }
    }

    suspend fun createNote(
        userId: String,
        title: String,
        content: String,
        pinned: Boolean = false
    ): Result<String> {
        val docRef = db.collection("users").document(userId).collection("notes").document()
        return try {
            val uid = requireMatchedUserId(userId)
            val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
            val cleanContent = content.trim().take(5000)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "pinned" to pinned,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        title: String,
        content: String,
        pinned: Boolean
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("notes").document(noteId)
        return try {
            requireMatchedUserId(userId)
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
            val cleanContent = content.trim().take(5000)
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("notes").document(noteId)
        return try {
            requireMatchedUserId(userId)
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- REMINDERS ---
    fun observeReminders(userId: String): Flow<List<JarvisReminder>> {
        val path = "users/$userId/reminders"
        return db.collection("users").document(userId).collection("reminders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    JarvisReminder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error -> handleFlowError(error, path, OperationType.LIST, userId) }
    }

    suspend fun createReminder(
        userId: String,
        title: String,
        dueAtMillis: Long
    ): Result<String> {
        val docRef = db.collection("users").document(userId).collection("reminders").document()
        return try {
            val uid = requireMatchedUserId(userId)
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Reminder title cannot be empty." }
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "dueAtMillis" to dueAtMillis.coerceAtLeast(0L),
                    "completed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleReminderCompleted(
        userId: String,
        reminderId: String,
        completed: Boolean
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("reminders").document(reminderId)
        return try {
            requireMatchedUserId(userId)
            require(reminderId.isNotBlank()) { "Reminder ID cannot be blank." }
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId).collection("reminders").document(reminderId)
        return try {
            requireMatchedUserId(userId)
            require(reminderId.isNotBlank()) { "Reminder ID cannot be blank." }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- SETTINGS ---
    fun observeSettings(userId: String): Flow<JarvisSettings> {
        val path = "users/$userId/preferences/settings"
        return db.collection("users").document(userId)
            .collection("preferences").document("settings")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    JarvisSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: JarvisSettings(userId = userId)
            }
            .catch { error -> handleFlowError(error, path, OperationType.GET, userId) }
    }

    suspend fun saveSettings(
        userId: String,
        language: JarvisLanguage,
        autoSpeak: Boolean,
        speechRate: Double,
        speechPitch: Double
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("preferences").document("settings")
        return try {
            val uid = requireMatchedUserId(userId)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "languageCode" to language.code,
                    "autoSpeak" to autoSpeak,
                    "speechRate" to speechRate.coerceIn(0.5, 2.0),
                    "speechPitch" to speechPitch.coerceIn(0.5, 2.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
