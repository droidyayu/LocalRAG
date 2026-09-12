package com.ayushig.localrag.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Shown instead of the chat when the model file is absent. It states the exact absolute path and
 * the push command, because the file is deliberately not bundled in the APK.
 */
@Composable
fun ModelMissingScreen(
    expectedPath: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Model not found", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The 304 MB model file is not bundled in the APK. Push it to this exact path:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = expectedPath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text("Command:", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "adb push gemma3-270m-it-q8.litertlm \\\n  $expectedPath",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Download it from huggingface.co/litert-community/gemma-3-270m-it after accepting " +
                    "the Gemma license. Check the file size before trusting it — a scripted " +
                    "download of a gated repo returns an HTML error page.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRetry) { Text("Check again") }
        }
    }
}
