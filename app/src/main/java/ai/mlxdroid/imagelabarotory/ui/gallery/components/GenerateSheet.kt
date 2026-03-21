package ai.mlxdroid.imagelabarotory.ui.gallery.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ai.mlxdroid.imagelabarotory.ui.gallery.ImageSizePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateSheet(
    prompt: String,
    isGenerating: Boolean,
    errorMessage: String?,
    sizePreset: ImageSizePreset,
    numInferenceSteps: Int,
    guidanceScale: Float,
    negativePrompt: String,
    seed: String,
    showAdvancedSettings: Boolean,
    onPromptChanged: (String) -> Unit,
    onSizePresetChanged: (ImageSizePreset) -> Unit,
    onInferenceStepsChanged: (Int) -> Unit,
    onGuidanceScaleChanged: (Float) -> Unit,
    onNegativePromptChanged: (String) -> Unit,
    onSeedChanged: (String) -> Unit,
    onToggleAdvancedSettings: () -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Create Image",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(16.dp))

            // Prompt
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Describe the image you want to create...") },
                minLines = 3,
                maxLines = 5,
                enabled = !isGenerating,
            )

            Spacer(Modifier.height(16.dp))

            // Image Size Presets
            Text(
                text = "Image Size",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ImageSizePreset.entries.forEachIndexed { index, preset ->
                    SegmentedButton(
                        selected = sizePreset == preset,
                        onClick = { onSizePresetChanged(preset) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ImageSizePreset.entries.size,
                        ),
                        enabled = !isGenerating,
                    ) {
                        Text(preset.label)
                    }
                }
            }
            Text(
                text = "${sizePreset.width} × ${sizePreset.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Error card
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Advanced Settings Toggle
            val chevronRotation = animateFloatAsState(
                targetValue = if (showAdvancedSettings) 180f else 0f,
                label = "chevron",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isGenerating) { onToggleAdvancedSettings() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Advanced Settings",
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (showAdvancedSettings) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronRotation.value),
                )
            }

            AnimatedVisibility(
                visible = showAdvancedSettings,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))

                    // Inference Steps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Inference Steps",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "$numInferenceSteps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = numInferenceSteps.toFloat(),
                        onValueChange = { onInferenceStepsChanged(it.toInt()) },
                        valueRange = 1f..50f,
                        steps = 48,
                        enabled = !isGenerating,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Guidance Scale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Guidance Scale",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "%.1f".format(guidanceScale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = guidanceScale,
                        onValueChange = { onGuidanceScaleChanged(it) },
                        valueRange = 1f..20f,
                        enabled = !isGenerating,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Negative Prompt
                    OutlinedTextField(
                        value = negativePrompt,
                        onValueChange = onNegativePromptChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Negative Prompt") },
                        placeholder = { Text("Things to avoid...") },
                        maxLines = 2,
                        enabled = !isGenerating,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Seed
                    OutlinedTextField(
                        value = seed,
                        onValueChange = onSeedChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seed") },
                        placeholder = { Text("Random") },
                        singleLine = true,
                        enabled = !isGenerating,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (seed.isNotEmpty()) {
                                IconButton(onClick = { onSeedChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear seed")
                                }
                            }
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Generate Button
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = prompt.isNotBlank() && !isGenerating,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Generating...")
                } else {
                    Text("Generate")
                }
            }
        }
    }
}
