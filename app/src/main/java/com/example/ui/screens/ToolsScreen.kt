package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.JarvisViewModel
import com.example.ui.theme.HoloBorder
import com.example.ui.theme.HoloPanel
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonCyan

enum class JarvisToolTab(val label: String) {
    CALCULATOR("Calc"),
    NOTES("Notes"),
    REMINDERS("Alerts"),
    SEARCH("Search")
}

@Composable
fun ToolsScreen(
    viewModel: JarvisViewModel,
    onShowMessage: (String) -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(JarvisToolTab.CALCULATOR) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JarvisToolTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { activeTab = tab },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                            selectedLabelColor = NeonCyan,
                            containerColor = HoloPanel,
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
                            .testTag("tool_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (activeTab) {
                JarvisToolTab.CALCULATOR -> CalculatorPane(viewModel = viewModel)
                JarvisToolTab.NOTES -> NotesPane(viewModel = viewModel)
                JarvisToolTab.REMINDERS -> RemindersPane(
                    viewModel = viewModel,
                    onShowMessage = onShowMessage
                )
                JarvisToolTab.SEARCH -> WebSearchPane(
                    viewModel = viewModel,
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}
