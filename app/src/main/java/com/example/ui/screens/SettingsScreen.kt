package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutUser
import com.example.data.model.JarvisLanguage
import com.example.ui.JarvisViewModel
import com.example.ui.components.HudPanelCard
import com.example.ui.theme.HoloBorder
import com.example.ui.theme.HoloPanelElevated
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCrimson
import com.example.ui.theme.HudEmerald
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.VoidBlack
import com.example.voice.JarvisVoiceManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: JarvisViewModel,
    voiceManager: JarvisVoiceManager,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isSpeechAvailable by voiceManager.isSpeechAvailable.collectAsStateWithLifecycle()
    val isTtsReady by voiceManager.isTtsReady.collectAsStateWithLifecycle()
    val ttsNote by voiceManager.ttsLanguageNote.collectAsStateWithLifecycle()

    val activeLanguage = JarvisLanguage.fromCode(settings.languageCode)
    val user = Firebase.auth.currentUser

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "SYSTEM CONFIGURATION",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan
                )
            }

            // Language Selector (English, Hindi, Telugu)
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LANGUAGE MATRIX",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }
                        Text(
                            text = "Controls AI replies, speech recognition, and voice synthesis.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            JarvisLanguage.entries.forEach { lang ->
                                val selected = lang == activeLanguage
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSettings(language = lang) },
                                    label = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(lang.nativeLabel, style = MaterialTheme.typography.titleMedium)
                                            Text(lang.englishLabel, style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = NeonCyan,
                                        containerColor = HoloPanelElevated,
                                        labelColor = MutedSteel
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = HoloBorder,
                                        selectedBorderColor = NeonCyan
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_lang_${lang.code}")
                                )
                            }
                        }
                    }
                }
            }

            // Voice Synthesis Controls
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "VOICE SYNTHESIS (TTS)",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Speak AI Replies",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IceWhite
                                )
                                Text(
                                    text = "Automatically read Jarvis responses aloud",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                            Switch(
                                checked = settings.autoSpeak,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettings(autoSpeak = enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VoidBlack,
                                    checkedTrackColor = NeonCyan
                                ),
                                modifier = Modifier.testTag("auto_speak_switch")
                            )
                        }

                        Column {
                            Text(
                                text = "Speech Rate: ${String.format(Locale.US, "%.2fx", settings.speechRate)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan
                            )
                            Slider(
                                value = settings.speechRate.toFloat(),
                                onValueChange = { rate ->
                                    viewModel.updateSettings(speechRate = rate.toDouble())
                                },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan
                                )
                            )
                        }

                        Column {
                            Text(
                                text = "Voice Pitch: ${String.format(Locale.US, "%.2fx", settings.speechPitch)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan
                            )
                            Slider(
                                value = settings.speechPitch.toFloat(),
                                onValueChange = { pitch ->
                                    viewModel.updateSettings(speechPitch = pitch.toDouble())
                                },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan
                                )
                            )
                        }

                        if (ttsNote != null) {
                            Text(
                                text = ttsNote.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudAmber
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val sample = when (activeLanguage) {
                                    JarvisLanguage.ENGLISH -> "Jarvis vocal synthesis calibrated and online."
                                    JarvisLanguage.HINDI -> "जार्विस वॉयस सिस्टम ऑनलाइन और तैयार है।"
                                    JarvisLanguage.TELUGU -> "జార్విస్ వాయిస్ సిస్టమ్ సిద్ధంగా ఉంది."
                                }
                                voiceManager.speak(sample)
                            },
                            border = BorderStroke(1.dp, NeonCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_voice_button")
                        ) {
                            Text("Test Voice Output", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // System Diagnostics
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Memory, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM TELEMETRY",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }
                        DiagnosticRow("Firebase AI Logic (Gemini 3.8 Flash)", "ACTIVE", true)
                        DiagnosticRow("Cloud Firestore Sync", "ENCRYPTED", true)
                        DiagnosticRow(
                            "Speech Recognition",
                            if (isSpeechAvailable) "AVAILABLE" else "UNAVAILABLE",
                            isSpeechAvailable
                        )
                        DiagnosticRow(
                            "Text-to-Speech Engine",
                            if (isTtsReady) "READY" else "INITIALIZING",
                            isTtsReady
                        )
                    }
                }
            }

            // Account & Sign Out
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user?.displayName?.ifBlank { "Authorized Operator" } ?: "Operator",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IceWhite
                                )
                                Text(
                                    text = user?.email ?: "Signed in with Google",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                        }

                        Button(
                            onClick = {
                                voiceManager.stopSpeaking()
                                voiceManager.stopListening()
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = onSignedOut
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HudCrimson.copy(alpha = 0.2f),
                                contentColor = HudCrimson
                            ),
                            border = BorderStroke(1.dp, HudCrimson.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.sign_out),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, status: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedSteel,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (ok) HudEmerald else HudAmber,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) HudEmerald else HudAmber
            )
        }
    }
}
