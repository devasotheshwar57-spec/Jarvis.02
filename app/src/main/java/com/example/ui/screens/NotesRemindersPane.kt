package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.JarvisNote
import com.example.data.model.JarvisReminder
import com.example.data.model.UiState
import com.example.reminder.ReminderScheduler
import com.example.ui.JarvisViewModel
import com.example.ui.components.HudPanelCard
import com.example.ui.theme.DimSlate
import com.example.ui.theme.HoloBorder
import com.example.ui.theme.HoloPanel
import com.example.ui.theme.HoloPanelElevated
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCrimson
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesPane(viewModel: JarvisViewModel) {
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HudPanelCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NEW ARCHIVE NOTE",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    placeholder = { Text("Note title...", color = DimSlate) },
                    singleLine = true,
                    colors = hudTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )
                OutlinedTextField(
                    value = noteBody,
                    onValueChange = { noteBody = it },
                    placeholder = { Text("Details...", color = DimSlate) },
                    minLines = 2,
                    maxLines = 4,
                    colors = hudTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )
                Button(
                    onClick = {
                        viewModel.createNote(noteTitle, noteBody)
                        noteTitle = ""
                        noteBody = ""
                    },
                    enabled = noteTitle.isNotBlank() || noteBody.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = VoidBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_note_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Note", fontWeight = FontWeight.Bold)
                }
            }
        }

        when (val state = notesState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }

            is UiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudCrimson
                )
            }

            is UiState.Success -> {
                val sorted = remember(state.data) {
                    state.data.sortedByDescending { it.pinned }
                }
                if (sorted.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NoteAlt,
                            contentDescription = null,
                            tint = DimSlate,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No notes saved yet. Capture ideas above or save directly from Jarvis replies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorted, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onTogglePin = { viewModel.toggleNotePinned(note) },
                                onDelete = { viewModel.deleteNote(note.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: JarvisNote,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HoloPanel,
        border = BorderStroke(
            width = 1.dp,
            color = if (note.pinned) NeonCyan.copy(alpha = 0.7f) else HoloBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (note.pinned) NeonCyan else IceWhite,
                    fontWeight = FontWeight.SemiBold
                )
                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (note.pinned) "Unpin note" else "Pin note",
                    tint = if (note.pinned) NeonCyan else MutedSteel
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete note",
                    tint = HudCrimson
                )
            }
        }
    }
}

@Composable
fun RemindersPane(
    viewModel: JarvisViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var selectedDelayMinutes by remember { mutableLongStateOf(15L) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onShowMessage("Notification permission denied; reminder saved in cloud list without alert.")
        }
    }

    val scheduleAndSave = {
        val delayMs = if (selectedDelayMinutes > 0L) {
            System.currentTimeMillis() + selectedDelayMinutes * 60_000L
        } else {
            0L
        }
        if (delayMs > 0L && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifPerm) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.createReminder(
            title = title,
            dueAtMillis = delayMs,
            onScheduled = { id, rTitle, triggerAt ->
                ReminderScheduler.scheduleReminder(context, id, rTitle, triggerAt)
            }
        )
        title = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HudPanelCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SCHEDULE REMINDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Remind me to...", color = DimSlate) },
                    singleLine = true,
                    colors = hudTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_title_input")
                )

                val presets = listOf(
                    15L to "15 min",
                    60L to "1 hour",
                    180L to "3 hours",
                    0L to "No timer"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (mins, label) ->
                        FilterChip(
                            selected = selectedDelayMinutes == mins,
                            onClick = { selectedDelayMinutes = mins },
                            label = {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                selectedLabelColor = NeonCyan,
                                containerColor = HoloPanelElevated,
                                labelColor = MutedSteel
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Button(
                    onClick = scheduleAndSave,
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = VoidBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_reminder_button")
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Set Reminder", fontWeight = FontWeight.Bold)
                }
            }
        }

        when (val state = remindersState) {
            is UiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }

            is UiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudCrimson
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Alarm,
                            contentDescription = null,
                            tint = DimSlate,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active reminders. Schedule one above to receive a local notification.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.data, key = { it.id }) { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onToggle = { viewModel.toggleReminder(reminder) },
                                onDelete = {
                                    viewModel.deleteReminder(reminder.id) { id ->
                                        ReminderScheduler.cancelReminder(context, id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: JarvisReminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val dueLabel = remember(reminder.dueAtMillis) {
        if (reminder.dueAtMillis > 0L) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(reminder.dueAtMillis))
        } else {
            "Unscheduled"
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HoloPanel,
        border = BorderStroke(1.dp, HoloBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonCyan,
                    uncheckedColor = MutedSteel,
                    checkmarkColor = VoidBlack
                )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reminder.completed) DimSlate else IceWhite,
                    textDecoration = if (reminder.completed) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = dueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reminder.dueAtMillis > System.currentTimeMillis() && !reminder.completed) HudAmber else MutedSteel
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete reminder",
                    tint = HudCrimson
                )
            }
        }
    }
}

@Composable
fun hudTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = HoloBorder,
    focusedContainerColor = HoloPanelElevated,
    unfocusedContainerColor = HoloPanelElevated,
    focusedTextColor = IceWhite,
    unfocusedTextColor = IceWhite,
    cursorColor = NeonCyan
)
