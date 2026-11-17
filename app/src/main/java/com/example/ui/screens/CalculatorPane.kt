package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.JarvisViewModel
import com.example.ui.components.HudPanelCard
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.HoloBorder
import com.example.ui.theme.HoloPanel
import com.example.ui.theme.HoloPanelElevated
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCrimson
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.VoidBlack

@Composable
fun CalculatorPane(viewModel: JarvisViewModel) {
    val state by viewModel.calculatorState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Digital HUD Readout
        HudPanelCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = NeonCyan.copy(alpha = 0.55f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = state.expression.ifEmpty { "0" },
                    style = MaterialTheme.typography.titleLarge,
                    color = IceWhite,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_expression_text")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.errorMessage ?: "= ${state.previewResult}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (state.errorMessage != null) HudCrimson else NeonCyan,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc_result_text")
                )
            }
        }

        // Scientific function strip
        val sciButtons = listOf(
            "sin(" to "sin",
            "cos(" to "cos",
            "tan(" to "tan",
            "sqrt(" to "√",
            "log(" to "log",
            "^" to "^",
            "pi" to "π"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sciButtons.forEach { (token, label) ->
                CalcKey(
                    label = label,
                    isAccent = false,
                    isOperator = false,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.appendCalcToken(token) }
                )
            }
        }

        // Primary Keypad
        val rows = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "=")
        )

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val isEquals = key == "="
                    val isOp = key in listOf("÷", "×", "−", "+")
                    val isUtility = key in listOf("C", "⌫")
                    CalcKey(
                        label = key,
                        isAccent = isEquals,
                        isOperator = isOp,
                        isUtility = isUtility,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calc_key_$key"),
                        onClick = {
                            when (key) {
                                "C" -> viewModel.clearCalc()
                                "⌫" -> viewModel.backspaceCalc()
                                "=" -> viewModel.evaluateCalc()
                                else -> viewModel.appendCalcToken(key)
                            }
                        }
                    )
                }
            }
        }

        // Tape History
        if (state.tape.isNotEmpty()) {
            Text(
                text = "RECENT COMPUTATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSteel,
                modifier = Modifier.padding(top = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.tape) { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HoloPanel,
                        border = BorderStroke(1.dp, HoloBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.appendCalcToken(item.result) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.expression,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "= ${item.result}",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    isAccent: Boolean,
    isOperator: Boolean,
    isUtility: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isAccent -> NeonCyan
        isOperator -> ArcBlue.copy(alpha = 0.28f)
        isUtility -> HoloPanelElevated
        else -> HoloPanel
    }
    val textColor = when {
        isAccent -> VoidBlack
        isOperator -> NeonCyan
        isUtility -> HudAmber
        else -> IceWhite
    }
    val border = when {
        isAccent -> NeonCyan
        isOperator -> ArcBlue.copy(alpha = 0.7f)
        else -> HoloBorder
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, border),
        modifier = modifier.height(50.dp)
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = if (isAccent || isOperator) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
