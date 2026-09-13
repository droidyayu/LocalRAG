package com.ayushig.localrag.demo.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Shown above the transcript when the model file is absent.
 *
 * It is a banner rather than a full-screen block because account questions are answered from
 * repository data and work perfectly well without a model loaded.
 */
@Composable
fun ModelMissingBanner(
    expectedPath: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Model not found. Account questions still work; general chat does not.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (expanded) {
                Text(
                    text = "Push the 304 MB model to:",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = expectedPath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "Download it from huggingface.co/litert-community/gemma-3-270m-it " +
                        "after accepting the Gemma license, and check the file size before " +
                        "trusting it.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = if (expanded) onRetry else { { expanded = true } }) {
                Text(if (expanded) "Check again" else "Details")
            }
        }
    }
}
