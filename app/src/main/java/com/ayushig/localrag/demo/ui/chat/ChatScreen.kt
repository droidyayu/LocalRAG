package com.ayushig.localrag.demo.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushig.localrag.android.LocalRagState
import com.ayushig.localrag.demo.domain.model.ChatMessage
import com.ayushig.localrag.demo.domain.model.MessageSource
import com.ayushig.localrag.demo.domain.model.ModelOption
import com.ayushig.localrag.demo.domain.model.Role
import com.ayushig.localrag.demo.domain.model.ToolCallRecord

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
        onSuggestion = viewModel::onSuggestion,
        onModelSelected = viewModel::onModelSelected,
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
    onSuggestion: (String) -> Unit,
    onModelSelected: (ModelOption) -> Unit,
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
                        Text("Assistant", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${uiState.modelOption.shortName} · " +
                                "${uiState.retrievalMode()} · litertlm ${uiState.libraryVersion}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    AssistChip(onClick = {}, label = { Text(uiState.engineState.label()) })
                    OverflowMenu(
                        modelOption = uiState.modelOption,
                        availableModels = uiState.availableModels,
                        onModelSelected = onModelSelected,
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
                    option = uiState.modelOption,
                    expectedPath = uiState.expectedModelPath,
                    onRetry = onRetryModelCheck,
                )
            }
            EngineBanner(uiState)

            if (uiState.messages.isEmpty()) {
                EmptyAssistant(
                    engineReady = uiState.engineState is LocalRagState.Ready,
                    onSuggestion = onSuggestion,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageRow(
                            message = message,
                            activity = if (message.isStreaming) uiState.activeActivity else null,
                        )
                    }
                }
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

/**
 * The help-assistant front door: what it covers, with tappable starters. Static UI rather
 * than a seeded message, so the welcome never leaks into the turn history the model sees.
 */
@Composable
private fun EmptyAssistant(
    engineReady: Boolean,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ask about your account or the help documentation.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SUGGESTIONS.forEach { suggestion ->
            AssistChip(
                onClick = { onSuggestion(suggestion) },
                enabled = engineReady,
                label = { Text(suggestion) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val SUGGESTIONS = listOf(
    "What is my portfolio worth?",
    "How do I deposit funds?",
    "What are the brokerage charges?",
)

@Composable
private fun MessageRow(message: ChatMessage, activity: String?) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            if (!isUser && activity != null && message.text.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = activity,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // No empty bubble while the turn is still working: the working card holds the
            // spinner, the status, and the live tool steps instead.
            if (!isUser && message.isStreaming && message.text.isEmpty()) {
                WorkingCard(activity = activity, tools = message.tools)
            } else {
                Surface(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    // The caret makes progressive streaming visible; text arriving all at
                    // once is a bug.
                    Text(
                        text = message.text + if (message.isStreaming) "▌" else "",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!isUser && !message.isStreaming) {
                if (message.source != MessageSource.MODEL) {
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
                    if (message.tools.isNotEmpty() || message.sources.isNotEmpty()) {
                        TurnDetails(message)
                    }
                }
                message.timings?.let { timings ->
                    Text(
                        text = formatTurnTotal(timings),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * What the turn is doing right now, shown instead of an empty bubble. Each planned function
 * appears as a step that flips from running to done as results come back, so the assistant
 * reads as working rather than stalled.
 */
@Composable
private fun WorkingCard(activity: String?, tools: List<ToolCallRecord>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = activity ?: "Working…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            tools.forEach { record ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = record.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (record.finished) "done" else "running…",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.finished) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * What the turn did, collapsed to one line until tapped. Naming the functions and the
 * passages is what lets a wrong answer be traced to the wrong source.
 */
@Composable
private fun TurnDetails(message: ChatMessage) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 2.dp, start = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded },
        ) {
            Text(
                text = (if (expanded) "▾ " else "▸ ") +
                    turnSummary(message.tools.size, message.sources.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            message.timings?.let { timings ->
                Text(
                    text = formatTurnBreakdown(timings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            message.tools.forEach { record -> ToolDetail(record) }
            message.sources.forEach { source ->
                Text(
                    text = "· " + source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun turnSummary(toolCount: Int, sourceCount: Int): String {
    val parts = buildList {
        if (toolCount > 0) add("$toolCount function" + if (toolCount == 1) "" else "s")
        if (sourceCount > 0) add("$sourceCount document" + if (sourceCount == 1) "" else "s")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ToolDetail(record: ToolCallRecord) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = record.name,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatToolArgs(record.args),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (record.finished) {
                "${record.resultChars} chars read" + if (record.sources.isNotEmpty()) {
                    " · ${record.sources.size} document" +
                        if (record.sources.size == 1) "" else "s"
                } else {
                    ""
                }
            } else {
                "interrupted"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            // The keyboard Send key sends too: reaching for the Send button every turn is
            // the wrong ergonomics for a chat surface.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
        )
        Button(onClick = onSend, enabled = canSend) { Text("Send") }
        OutlinedButton(onClick = onStop, enabled = isGenerating) { Text("Stop") }
    }
}

@Composable
private fun OverflowMenu(
    modelOption: ModelOption,
    availableModels: Set<ModelOption>,
    onModelSelected: (ModelOption) -> Unit,
    onClearChat: () -> Unit,
    onReloadEngine: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_more),
                contentDescription = "More",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Model: ${modelOption.shortName}") },
                onClick = { expanded = false; picking = true },
            )
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
    if (picking) {
        ModelPickerDialog(
            selected = modelOption,
            availableModels = availableModels,
            onSelect = { picking = false; onModelSelected(it) },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Pushed models only: a missing file cannot run, so it shows its size and the push
 * command instead of a radio option.
 */
@Composable
private fun ModelPickerDialog(
    selected: ModelOption,
    availableModels: Set<ModelOption>,
    onSelect: (ModelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assistant model") },
        text = {
            Column {
                ModelOption.entries.forEach { option ->
                    val present = option in availableModels
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = option == selected,
                            enabled = present,
                            onClick = { if (present) onSelect(option) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (present) {
                                    "on device"
                                } else {
                                    "${option.approxSize} — push with scripts/push_model.sh"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun LocalRagState.label(): String = when (this) {
    LocalRagState.Idle -> "idle"
    LocalRagState.Loading -> "loading"
    is LocalRagState.Ready -> if (usingVectors) "ready · hybrid" else "ready · bm25"
    is LocalRagState.Failed -> "failed"
}
