package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.search.WebSearchResult
import com.example.ui.JarvisViewModel
import com.example.ui.WebSearchUiState
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
import java.net.URLEncoder

@Composable
fun WebSearchPane(
    viewModel: JarvisViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val searchState by viewModel.webSearchState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val openUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            onShowMessage("No browser available to open link.")
        }
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
                    text = "LIVE WEB INTELLIGENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search the web...", color = DimSlate) },
                        singleLine = true,
                        colors = hudTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.performWebSearch(query) }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("web_search_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.performWebSearch(query) },
                        enabled = query.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = VoidBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("web_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search web")
                    }
                }
            }
        }

        when (val state = searchState) {
            is WebSearchUiState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = DimSlate,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Search live articles & references via DuckDuckGo and Wikipedia, then ask Jarvis to synthesize the findings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            }

            is WebSearchUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "QUERYING GLOBAL INDEX...",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan
                    )
                }
            }

            is WebSearchUiState.Error -> {
                HudPanelCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = HudCrimson.copy(alpha = 0.7f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IceWhite
                        )
                        if (query.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                                    openUrl("https://duckduckgo.com/?q=$encoded")
                                },
                                border = BorderStroke(1.dp, NeonCyan)
                            ) {
                                Text("Open in Browser", color = NeonCyan)
                            }
                        }
                    }
                }
            }

            is WebSearchUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "summarize_action") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.summarizeCurrentSearch() },
                                enabled = !state.isSummarizing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = VoidBlack
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("summarize_search_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isSummarizing) "Synthesizing..." else "Ask Jarvis to Summarize",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val encoded = URLEncoder.encode(state.response.query, "UTF-8")
                                    openUrl("https://duckduckgo.com/?q=$encoded")
                                },
                                border = BorderStroke(1.dp, HoloBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open full search in browser",
                                    tint = NeonCyan
                                )
                            }
                        }
                    }

                    if (state.aiSummary.isNotBlank() || state.summaryError != null) {
                        item(key = "ai_summary_card") {
                            HudPanelCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = NeonCyan.copy(alpha = 0.7f),
                                backgroundColor = HoloPanelElevated
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "JARVIS SYNTHESIS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NeonCyan
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (state.summaryError != null) {
                                        Text(
                                            text = state.summaryError,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = HudCrimson
                                        )
                                    } else {
                                        Text(
                                            text = state.aiSummary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = IceWhite
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(state.response.results) { item ->
                        SearchResultCard(item = item, onOpen = { openUrl(item.url) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: WebSearchResult,
    onOpen: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = HoloPanel,
        border = BorderStroke(1.dp, HoloBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.source.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
            }
            IconButton(onClick = onOpen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open article",
                    tint = NeonCyan
                )
            }
        }
    }
}
