package com.ayushig.localrag.demo.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushig.localrag.android.LocalRagState
import com.ayushig.localrag.android.Passage

@Composable
fun DocsSearchRoute(
    modifier: Modifier = Modifier,
    viewModel: DocsSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DocsSearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::onSearch,
        modifier = modifier,
    )
}

/**
 * A window onto retrieveOnly. It exists so a bad answer can be diagnosed as a retrieval problem or
 * a generation problem without guessing, which is also how the evaluation harness will drive the
 * library.
 */
@Composable
fun DocsSearchScreen(
    uiState: DocsSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Retrieval", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Raw retrieveOnly results for judging retrieval accuracy — no generation.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = engineLine(uiState.engine),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the docs") },
                singleLine = true,
            )
            Button(
                onClick = onSearch,
                enabled = uiState.query.isNotBlank() && uiState.engine is LocalRagState.Ready,
            ) {
                Text("Search")
            }
        }

        if (uiState.searched) {
            Text(
                text = "${uiState.passages.size} passages in ${uiState.lastQueryMs}ms",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(uiState.passages, key = { _, passage -> passage.chunkId }) { rank, passage ->
                PassageCard(rank = rank + 1, passage = passage)
            }
        }
    }
}

@Composable
private fun PassageCard(rank: Int, passage: Passage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = passage.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(onClick = {}, label = { Text(passage.source.name) })
            }
            passage.heading?.let { heading ->
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = passage.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "#%d  %s  score %.2f".format(rank, passage.chunkId, passage.score),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun engineLine(state: LocalRagState): String = when (state) {
    LocalRagState.Idle -> "idle"
    LocalRagState.Loading -> "loading bundle"
    is LocalRagState.Failed -> "failed: ${state.reason}"
    is LocalRagState.Ready ->
        "${state.chunkCount} chunks - content v${state.contentVersion} - " +
            (if (state.usingVectors) "hybrid" else "BM25 only") + " - " +
            (if (state.canGenerate) "generation on" else "retrieval only")
}
