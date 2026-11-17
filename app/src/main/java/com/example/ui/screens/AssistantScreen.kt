package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ai.AiSpeedMode
import com.example.data.model.ChatMessage
import com.example.data.model.JarvisLanguage
import com.example.data.model.UiState
import com.example.ui.AiTurnState
import com.example.ui.JarvisViewModel
import com.example.ui.components.ArcReactorVisualizer
import com.example.ui.components.HudPanelCard
import com.example.ui.components.VisualizerMode
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.DimSlate
import com.example.ui.theme.HoloBorder
import com.example.ui.theme.HoloPanel
import com.example.ui.theme.HoloPanelElevated
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCrimson
import com.example.ui.theme.HudEmerald
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.VoidBlack
import com.example.util.ImageUtils
import com.example.voice.JarvisVoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantScreen(
    viewModel: JarvisViewModel,
    voiceManager: JarvisVoiceManager,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val aiTurnState by viewModel.aiTurnState.collectAsStateWithLifecycle()
    val speedMode by viewModel.speedMode.collectAsStateWithLifecycle()
    val pendingUserPrompt by viewModel.pendingUserPrompt.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImage.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val rmsLevel by voiceManager.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val voiceError by voiceManager.voiceError.collectAsStateWithLifecycle()

    val activeLanguage = JarvisLanguage.fromCode(settings.languageCode)

    var promptText by remember { mutableStateOf("") }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showMicRationale by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }
    var dictateToInputOnly by remember { mutableStateOf(false) }
    var showCameraRationale by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(voiceError) {
        voiceError?.let {
            onShowMessage(it)
            voiceManager.clearVoiceError()
        }
    }

    val handleRecognizedText: (String) -> Unit = { recognized ->
        if (dictateToInputOnly) {
            promptText = if (promptText.isBlank()) recognized else "$promptText $recognized"
        } else {
            promptText = ""
            viewModel.sendMessage(recognized) { reply ->
                if (settings.autoSpeak) {
                    voiceManager.speak(reply)
                }
            }
        }
    }

    val speechPromptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (transcript.isNotEmpty()) {
                handleRecognizedText(transcript)
            } else {
                onShowMessage("No speech recognized. Please try again.")
            }
        }
    }

    val startVoiceInput: (Boolean) -> Unit = { toInputOnly ->
        dictateToInputOnly = toInputOnly
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceManager.startListening(activeLanguage) { recognized ->
                handleRecognizedText(recognized)
            }
        } else if (voiceManager.canLaunchVoicePrompt(activeLanguage)) {
            speechPromptLauncher.launch(voiceManager.buildRecognizerIntent(activeLanguage))
        } else {
            onShowMessage("Speech-to-text service is not installed on this device.")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermanentlyDenied = false
            startVoiceInput(dictateToInputOnly)
        } else {
            val activity = context as? Activity
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                )
            micPermanentlyDenied = !canAskAgain
            if (micPermanentlyDenied) {
                showMicRationale = true
            } else {
                onShowMessage("Microphone permission denied. Voice-to-text requires microphone access.")
            }
        }
    }

    val triggerMicFlow: (Boolean) -> Unit = { toInputOnly ->
        dictateToInputOnly = toInputOnly
        if (isListening) {
            voiceManager.stopListening()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startVoiceInput(toInputOnly)
            } else {
                val activity = context as? Activity
                val canAskAgain = activity == null ||
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO
                    )
                micPermanentlyDenied = false
                if (canAskAgain) {
                    showMicRationale = true
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val bmp = ImageUtils.decodeScaledBitmap(context, uri)
                if (bmp != null) {
                    viewModel.attachImage(bmp)
                } else {
                    onShowMessage("Failed to load captured camera photo.")
                }
            }
        }
    }

    val launchCamera = {
        try {
            val uri = ImageUtils.createTempCameraUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            onShowMessage(e.localizedMessage ?: "Camera unavailable on this device.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            onShowMessage("Camera permission denied. You can still select photos from your gallery.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = ImageUtils.decodeScaledBitmap(context, uri)
                if (bmp != null) {
                    viewModel.attachImage(bmp)
                } else {
                    onShowMessage("Unable to decode selected image.")
                }
            }
        }
    }

    val visualizerMode = when {
        isListening -> VisualizerMode.LISTENING
        aiTurnState is AiTurnState.Thinking || aiTurnState is AiTurnState.Streaming -> VisualizerMode.THINKING
        isSpeaking -> VisualizerMode.SPEAKING
        else -> VisualizerMode.IDLE
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, aiTurnState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val timeFormatted = remember(nowMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMillis))
    }
    val dateFormatted = remember(nowMillis) {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(nowMillis)).uppercase()
    }

    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            containerColor = HoloPanelElevated,
            titleContentColor = NeonCyan,
            textContentColor = IceWhite,
            title = { Text("Microphone Access", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = if (micPermanentlyDenied) {
                        "Microphone permission was denied. Open App Settings to enable Microphone access for Jarvis voice-to-text."
                    } else {
                        "Jarvis uses your device microphone solely while listening for your voice-to-text input in ${activeLanguage.englishLabel}."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicRationale = false
                        if (micPermanentlyDenied) {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(settingsIntent)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                ) {
                    Text(if (micPermanentlyDenied) "Open Settings" else "Allow Microphone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text("Cancel", color = MutedSteel)
                }
            }
        )
    }

    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            containerColor = HoloPanelElevated,
            titleContentColor = NeonCyan,
            textContentColor = IceWhite,
            title = { Text("Camera Access", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Jarvis needs camera access to capture a photo for multimodal AI visual analysis.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCameraRationale = false
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                ) {
                    Text("Allow Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) {
                    Text("Cancel", color = MutedSteel)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HUD Telemetry Header (Date, Time, Language switcher, New Session)
        HudPanelCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (visualizerMode) {
                                        VisualizerMode.LISTENING -> HudEmerald
                                        VisualizerMode.THINKING -> HudAmber
                                        else -> NeonCyan
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan
                        )
                    }
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteel
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick AI speed toggle (FAST / PRO)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (speedMode == AiSpeedMode.FAST) {
                            NeonCyan.copy(alpha = 0.16f)
                        } else {
                            HoloPanelElevated
                        },
                        border = BorderStroke(
                            1.dp,
                            if (speedMode == AiSpeedMode.FAST) NeonCyan else HudAmber.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.toggleSpeedMode() }
                            .testTag("speed_mode_cycle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Toggle AI speed mode",
                                tint = if (speedMode == AiSpeedMode.FAST) NeonCyan else HudAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = speedMode.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (speedMode == AiSpeedMode.FAST) NeonCyan else HudAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Quick language cycle pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HoloPanelElevated,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val next = when (activeLanguage) {
                                    JarvisLanguage.ENGLISH -> JarvisLanguage.HINDI
                                    JarvisLanguage.HINDI -> JarvisLanguage.TELUGU
                                    JarvisLanguage.TELUGU -> JarvisLanguage.ENGLISH
                                }
                                viewModel.updateSettings(language = next)
                            }
                            .testTag("language_cycle_button")
                    ) {
                        Text(
                            text = activeLanguage.code.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    if (isSpeaking) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { voiceManager.stopSpeaking() },
                            modifier = Modifier.testTag("stop_tts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.stop_speaking_desc),
                                tint = HudAmber
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.startNewSession() },
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New session",
                            tint = IceWhite
                        )
                    }
                }
            }
        }

        // Main Conversation or Hero Arc Reactor Core
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            contentAlignment = Alignment.Center
        ) {
            val isAiActive = aiTurnState is AiTurnState.Thinking || aiTurnState is AiTurnState.Streaming
            when {
                messagesState is UiState.Loading && !isAiActive -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArcReactorVisualizer(mode = VisualizerMode.THINKING, size = 150.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SYNCHRONIZING NEURAL ARCHIVE...",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan
                        )
                    }
                }

                messages.isEmpty() && !isAiActive -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ArcReactorVisualizer(
                            mode = visualizerMode,
                            rmsLevel = rmsLevel,
                            size = 188.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = when (visualizerMode) {
                                VisualizerMode.LISTENING -> "LISTENING (${activeLanguage.nativeLabel})"
                                VisualizerMode.THINKING -> "PROCESSING NEURAL QUERY..."
                                VisualizerMode.SPEAKING -> "SYNTHESIZING VOICE..."
                                VisualizerMode.IDLE -> "JARVIS ONLINE"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isListening && partialTranscript.isNotBlank()) {
                                "\"$partialTranscript\""
                            } else {
                                when (activeLanguage) {
                                    JarvisLanguage.ENGLISH -> "Tap the microphone to speak, attach a photo for vision analysis, or type a command."
                                    JarvisLanguage.HINDI -> "बोलने के लिए माइक टैप करें, इमेज विश्लेषण के लिए फ़ोटो जोड़ें, या संदेश लिखें।"
                                    JarvisLanguage.TELUGU -> "మాట్లాడటానికి మైక్ నొక్కండి, చిత్ర విశ్లేషణ కోసం ఫోటో జోడించండి లేదా టైప్ చేయండి."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Compact HUD Core Strip at top of active conversation
                        item(key = "compact_core_hud") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                ArcReactorVisualizer(
                                    mode = visualizerMode,
                                    rmsLevel = rmsLevel,
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = when (visualizerMode) {
                                            VisualizerMode.LISTENING -> "VOICE INPUT ACTIVE"
                                            VisualizerMode.THINKING -> "ANALYZING..."
                                            VisualizerMode.SPEAKING -> "VOICE OUTPUT ACTIVE"
                                            VisualizerMode.IDLE -> "NEURAL LINK STANDBY"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NeonCyan
                                    )
                                    if (isListening && partialTranscript.isNotBlank()) {
                                        Text(
                                            text = partialTranscript,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = IceWhite,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onSpeak = { voiceManager.speak(msg.text) },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis", msg.text))
                                    onShowMessage("Copied to clipboard")
                                },
                                onSaveNote = {
                                    viewModel.createNote(
                                        title = msg.text.lineSequence().firstOrNull().orEmpty().take(48),
                                        content = msg.text
                                    )
                                }
                            )
                        }

                        val optimisticPrompt = pendingUserPrompt
                        val alreadyInMessages = optimisticPrompt != null &&
                            messages.lastOrNull()?.let { it.role == "user" && it.text == optimisticPrompt } == true
                        if (optimisticPrompt != null && !alreadyInMessages) {
                            item(key = "pending_user_bubble") {
                                ChatMessageBubble(
                                    message = ChatMessage(
                                        id = "pending_user",
                                        role = "user",
                                        text = optimisticPrompt
                                    ),
                                    onSpeak = {},
                                    onCopy = {},
                                    onSaveNote = {}
                                )
                            }
                        }

                        if (aiTurnState is AiTurnState.Thinking) {
                            item(key = "thinking_bubble") {
                                StreamingBubble(text = "Calibrating response...", isThinking = true)
                            }
                        } else if (aiTurnState is AiTurnState.Streaming) {
                            item(key = "streaming_bubble") {
                                StreamingBubble(
                                    text = (aiTurnState as AiTurnState.Streaming).text,
                                    isThinking = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error Banner for AI or Firestore stream errors
        val streamError = (messagesState as? UiState.Error)?.message
        val aiError = (aiTurnState as? AiTurnState.Error)?.message
        val activeError = aiError ?: streamError
        AnimatedVisibility(visible = activeError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = HudCrimson.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, HudCrimson.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhite,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.dismissAiError() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss error",
                            tint = IceWhite
                        )
                    }
                }
            }
        }

        // Attached Camera / Photo Preview Bar
        AnimatedVisibility(visible = attachedImage != null) {
            attachedImage?.let { bmp ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = HoloPanel,
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached image for vision analysis",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VISUAL PAYLOAD READY",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan
                            )
                            Text(
                                text = "Ask Jarvis anything about this image.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        }
                        IconButton(onClick = { viewModel.attachImage(null) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove attached image",
                                tint = IceWhite
                            )
                        }
                    }
                }
            }
        }

        // Composer + Large Neon Microphone Control Deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else showCameraRationale = true
                    },
                    modifier = Modifier.testTag("camera_capture_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.capture_camera_desc),
                        tint = NeonCyan
                    )
                }

                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("photo_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.attach_photo_desc),
                        tint = NeonCyan
                    )
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            text = if (isListening) "Listening..." else "Message Jarvis...",
                            color = DimSlate
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { triggerMicFlow(true) },
                            modifier = Modifier.testTag("dictate_to_input_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Dictate voice into text box",
                                tint = if (isListening && dictateToInputOnly) HudEmerald else NeonCyan
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = HoloBorder,
                        focusedContainerColor = HoloPanel,
                        unfocusedContainerColor = HoloPanel,
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        cursorColor = NeonCyan
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (promptText.isNotBlank() || attachedImage != null) {
                                val toSend = promptText
                                promptText = ""
                                viewModel.sendMessage(toSend) { reply ->
                                    if (settings.autoSpeak) voiceManager.speak(reply)
                                }
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_prompt_input")
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        val toSend = promptText
                        promptText = ""
                        viewModel.sendMessage(toSend) { reply ->
                            if (settings.autoSpeak) voiceManager.speak(reply)
                        }
                    },
                    enabled = promptText.isNotBlank() || attachedImage != null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (promptText.isNotBlank() || attachedImage != null) NeonCyan else HoloPanelElevated
                        )
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send_button_desc),
                        tint = if (promptText.isNotBlank() || attachedImage != null) VoidBlack else DimSlate
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large Futuristic Microphone Trigger
            LargeMicButton(
                isListening = isListening,
                onClick = { triggerMicFlow(false) }
            )
        }
    }
}

@Composable
private fun LargeMicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.12f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 550 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.22f))
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isListening) NeonCyan else HoloPanelElevated,
            border = BorderStroke(2.dp, if (isListening) IceWhite else NeonCyan),
            tonalElevation = 8.dp,
            modifier = Modifier
                .size(64.dp)
                .testTag("large_mic_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.mic_button_desc),
                    tint = if (isListening) VoidBlack else NeonCyan,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onSaveNote: () -> Unit
) {
    val isModel = message.role == "model"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isModel) 4.dp else 16.dp,
                bottomEnd = if (isModel) 16.dp else 4.dp
            ),
            color = if (isModel) HoloPanel else ArcBlue.copy(alpha = 0.25f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isModel) NeonCyan.copy(alpha = 0.4f) else ArcBlue.copy(alpha = 0.7f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isModel) "JARVIS" else "YOU",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isModel) NeonCyan else IceWhite,
                        fontWeight = FontWeight.Bold
                    )
                    if (message.hasImage) {
                        Text(
                            text = "VISION INPUT",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IceWhite
                )
                if (isModel) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.speak_response_desc),
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy response",
                                tint = MutedSteel,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onSaveNote, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = "Save response as note",
                                tint = MutedSteel,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, isThinking: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(NeonCyan, ArcBlue)),
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                ),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = HoloPanelElevated
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isThinking) NeonCyan else IceWhite
                )
            }
        }
    }
}
