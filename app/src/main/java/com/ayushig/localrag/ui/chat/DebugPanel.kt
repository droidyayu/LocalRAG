package com.ayushig.localrag.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayushig.localrag.domain.model.GenerationSettings
import com.ayushig.localrag.domain.model.LlmBackend

/**
 * Debug controls. Changing the backend rebuilds the engine; changing anything else rebuilds only
 * the conversation. Both are handled by the repository, not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugPanel(
    settings: GenerationSettings,
    onSettingsChange: (GenerationSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Debug", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LlmBackend.entries.forEach { backend ->
                    FilterChip(
                        selected = settings.backend == backend,
                        onClick = { onSettingsChange(settings.copy(backend = backend)) },
                        label = { Text(backend.name) },
                    )
                }
            }

            NumberField("temperature", settings.temperature.toString()) { value ->
                value.toFloatOrNull()?.let { onSettingsChange(settings.copy(temperature = it)) }
            }
            NumberField("topK", settings.topK.toString()) { value ->
                value.toIntOrNull()?.let { onSettingsChange(settings.copy(topK = it)) }
            }
            NumberField("topP", settings.topP.toString()) { value ->
                value.toFloatOrNull()?.let { onSettingsChange(settings.copy(topP = it)) }
            }
            NumberField("maxOutputToken", settings.maxOutputToken.toString()) { value ->
                value.toIntOrNull()?.let { onSettingsChange(settings.copy(maxOutputToken = it)) }
            }

            OutlinedTextField(
                value = settings.systemInstruction,
                onValueChange = { onSettingsChange(settings.copy(systemInstruction = it)) },
                label = { Text("system instruction") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onSettingsChange(GenerationSettings()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset to baseline")
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
