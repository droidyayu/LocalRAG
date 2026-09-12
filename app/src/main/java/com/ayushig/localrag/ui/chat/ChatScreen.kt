package com.ayushig.localrag.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushig.localrag.domain.model.ChatMessage
import com.ayushig.localrag.domain.model.EngineState
import com.ayushig.localrag.domain.model.GenerationMetrics
import com.ayushig.localrag.domain.model.GenerationSettings
import com.ayushig.localrag.domain.model.Role

/** The only composable that touches the ViewModel. */
@Composable
fun ChatRoute(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::onSend,
        onStop = viewModel::onStop,
        onClearChat = viewModel::onClearChat,
        onReloadEngine = viewModel::onReloadEngine,
        onToggleDebugPanel = viewModel::onToggleDebugPanel,
        onSettingsChange = viewModel::onSettingsChange,
        onRetryModelCheck = viewModel::loadEngine,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClearChat: () -> Unit,
    onReloadEngine: () -> Unit,
    onToggleDebugPanel: () -> Unit,
    onSettingsChange: (GenerationSettings) -> Unit,
    onRetryModelCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.modelPresent) {
        ModelMissingScreen(
            expectedPath = uiState.expectedModelPath,
            onRetry = onRetryModelCheck,
            modifier = modifier,
        )
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gemma 3 270M IT", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${uiState.settings.backend} · litertlm ${uiState.libraryVersion}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    AssistChip(onClick = {}, label = { Text(uiState.engineState.label()) })
                    OverflowMenu(
                        onClearChat = onClearChat,
                        onReloadEngine = onReloadEngine,
                        onToggleDebugPanel = onToggleDebugPanel,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            EngineBanner(uiState)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message -> MessageRow(message) }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            InputRow(
                input = uiState.input,
                canSend = uiState.canSend,
                isGenerating = uiState.isGenerating,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
            )
        }
    }

    if (uiState.showDebugPanel) {
        DebugPanel(
            settings = uiState.settings,
            onSettingsChange = onSettingsChange,
            onDismiss = onToggleDebugPanel,
        )
    }
}

@Composable
private fun EngineBanner(uiState: ChatUiState) {
    when (val state = uiState.engineState) {
        is EngineState.Loading -> Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "Loading model… ${uiState.loadingElapsedSeconds}s",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        is EngineState.Failed -> Text(
            text = "Engine failed: ${state.reason}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        else -> Unit
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                // The caret makes progressive streaming visible; text arriving all at once is a bug.
                Text(
                    text = message.text + if (message.isStreaming) "▌" else "",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message.metrics?.let { MetricsLine(it) }
        }
    }
}

@Composable
private fun MetricsLine(metrics: GenerationMetrics) {
    Text(
        text = "TTFT %dms · %.1f tok/s · ~%d tok · %.1fs".format(
            metrics.timeToFirstTokenMs,
            metrics.tokensPerSecond,
            metrics.approxTokenCount,
            metrics.totalTimeMs / 1000.0,
        ),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
    )
}

@Composable
private fun InputRow(
    input: String,
    canSend: Boolean,
    isGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask something") },
        )
        Button(onClick = onSend, enabled = canSend) { Text("Send") }
        OutlinedButton(onClick = onStop, enabled = isGenerating) { Text("Stop") }
    }
}

@Composable
private fun OverflowMenu(
    onClearChat: () -> Unit,
    onReloadEngine: () -> Unit,
    onToggleDebugPanel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = android.R.drawable.ic_menu_more,
                ),
                contentDescription = "More",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Clear chat") },
                onClick = { expanded = false; onClearChat() },
            )
            DropdownMenuItem(
                text = { Text("Reload engine") },
                onClick = { expanded = false; onReloadEngine() },
            )
            DropdownMenuItem(
                text = { Text("Debug panel") },
                onClick = { expanded = false; onToggleDebugPanel() },
            )
        }
    }
}

private fun EngineState.label(): String = when (this) {
    EngineState.Idle -> "idle"
    EngineState.Loading -> "loading"
    is EngineState.Ready -> "ready ${loadTimeMs}ms"
    is EngineState.Failed -> "failed"
}
