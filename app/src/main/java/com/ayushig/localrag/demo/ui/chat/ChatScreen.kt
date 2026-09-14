package com.ayushig.localrag.demo.ui.chat

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushig.localrag.android.LocalRagState
import com.ayushig.localrag.demo.domain.model.ChatMessage
import com.ayushig.localrag.demo.domain.model.MessageSource
import com.ayushig.localrag.demo.domain.model.Role

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
    onRetryModelCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // The source label appears when streaming ends, which grows the last item without changing
    // its text, so isStreaming has to be part of the key or the label lands below the fold.
    LaunchedEffect(
        uiState.messages.size,
        uiState.messages.lastOrNull()?.text,
        uiState.messages.lastOrNull()?.isStreaming,
    ) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gemma 4 E2B IT", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${uiState.retrievalMode()} · litertlm ${uiState.libraryVersion}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    AssistChip(onClick = {}, label = { Text(uiState.engineState.label()) })
                    OverflowMenu(
                        onClearChat = onClearChat,
                        onReloadEngine = onReloadEngine,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!uiState.modelPresent) {
                ModelMissingBanner(
                    expectedPath = uiState.expectedModelPath,
                    onRetry = onRetryModelCheck,
                )
            }
            EngineBanner(uiState)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message -> MessageRow(message) }
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

}

@Composable
private fun EngineBanner(uiState: ChatUiState) {
    when (val state = uiState.engineState) {
        is LocalRagState.Loading -> Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "Loading model… ${uiState.loadingElapsedSeconds}s",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        is LocalRagState.Failed -> Text(
            text = "Engine failed: ${state.reason}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
        else -> Unit
    }
}

private fun ChatUiState.retrievalMode(): String =
    if ((engineState as? LocalRagState.Ready)?.usingVectors == true) "hybrid" else "bm25"

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
            if (!message.isStreaming && message.source != MessageSource.MODEL) {
                Text(
                    text = when (message.source) {
                        MessageSource.PORTFOLIO_DATA -> "from your account"
                        MessageSource.DOCUMENTATION -> "from the help documentation"
                        MessageSource.NO_INFORMATION -> "no matching information"
                        MessageSource.MODEL -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                )
                // Naming the passages is what lets a wrong answer be traced to the wrong source.
                message.sources.take(2).forEach { source ->
                    Text(
                        text = "· " + source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
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
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_more),
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
        }
    }
}

private fun LocalRagState.label(): String = when (this) {
    LocalRagState.Idle -> "idle"
    LocalRagState.Loading -> "loading"
    is LocalRagState.Ready -> if (usingVectors) "ready · hybrid" else "ready · bm25"
    is LocalRagState.Failed -> "failed"
}
