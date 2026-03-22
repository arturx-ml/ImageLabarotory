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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ModelDownloadState
import ai.mlxdroid.imagelabarotory.ui.gallery.ImageSizePreset
import ai.mlxdroid.imagelabarotory.ui.theme.ImageLabarotoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateSheet(
    prompt: String,
    isGenerating: Boolean,
    errorMessage: String?,
    selectedProvider: ApiProvider,
    modelDownloadState: ModelDownloadState,
    sizePreset: ImageSizePreset,
    numInferenceSteps: Int,
    guidanceScale: Float,
    negativePrompt: String,
    seed: String,
    showAdvancedSettings: Boolean,
    onPromptChanged: (String) -> Unit,
    onProviderChanged: (ApiProvider) -> Unit,
    onDownloadModel: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
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
        GenerateSheetContent(
            prompt = prompt,
            isGenerating = isGenerating,
            errorMessage = errorMessage,
            selectedProvider = selectedProvider,
            modelDownloadState = modelDownloadState,
            sizePreset = sizePreset,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            negativePrompt = negativePrompt,
            seed = seed,
            showAdvancedSettings = showAdvancedSettings,
            onPromptChanged = onPromptChanged,
            onProviderChanged = onProviderChanged,
            onDownloadModel = onDownloadModel,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onCancelDownload = onCancelDownload,
            onSizePresetChanged = onSizePresetChanged,
            onInferenceStepsChanged = onInferenceStepsChanged,
            onGuidanceScaleChanged = onGuidanceScaleChanged,
            onNegativePromptChanged = onNegativePromptChanged,
            onSeedChanged = onSeedChanged,
            onToggleAdvancedSettings = onToggleAdvancedSettings,
            onGenerate = onGenerate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateSheetContent(
    prompt: String,
    isGenerating: Boolean,
    errorMessage: String?,
    selectedProvider: ApiProvider,
    modelDownloadState: ModelDownloadState,
    sizePreset: ImageSizePreset,
    numInferenceSteps: Int,
    guidanceScale: Float,
    negativePrompt: String,
    seed: String,
    showAdvancedSettings: Boolean,
    onPromptChanged: (String) -> Unit,
    onProviderChanged: (ApiProvider) -> Unit,
    onDownloadModel: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSizePresetChanged: (ImageSizePreset) -> Unit,
    onInferenceStepsChanged: (Int) -> Unit,
    onGuidanceScaleChanged: (Float) -> Unit,
    onNegativePromptChanged: (String) -> Unit,
    onSeedChanged: (String) -> Unit,
    onToggleAdvancedSettings: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOnDevice = selectedProvider == ApiProvider.MEDIA_PIPE
    val isModelReady = modelDownloadState is ModelDownloadState.Ready
    val isDownloading = modelDownloadState is ModelDownloadState.Downloading

    Column(
        modifier = modifier
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

        // Provider Selector
        Text(
            text = "Generation Mode",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ApiProvider.entries.forEachIndexed { index, provider ->
                SegmentedButton(
                    selected = selectedProvider == provider,
                    onClick = { onProviderChanged(provider) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ApiProvider.entries.size,
                    ),
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        provider.displayName,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Model download card (for on-device only)
        if (isOnDevice) {
            Spacer(Modifier.height(12.dp))
            ModelStatusCard(
                downloadState = modelDownloadState,
                onDownload = onDownloadModel,
                onPause = onPauseDownload,
                onResume = onResumeDownload,
                onCancel = onCancelDownload,
            )
        }

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

        // Image Size Presets (cloud only)
        if (selectedProvider.supportsSizePresets) {
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
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            preset.label,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Text(
                text = "${sizePreset.width} \u00D7 ${sizePreset.height}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = "Image Size",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "512 \u00D7 512",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

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
                    valueRange = 1f..selectedProvider.maxSteps.toFloat(),
                    steps = selectedProvider.maxSteps - 2,
                    enabled = !isGenerating,
                )

                // Guidance Scale (cloud only)
                if (selectedProvider.supportsGuidanceScale) {
                    Spacer(Modifier.height(8.dp))
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
                }

                // Negative Prompt (cloud only)
                if (selectedProvider.supportsNegativePrompt) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = negativePrompt,
                        onValueChange = onNegativePromptChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Negative Prompt") },
                        placeholder = { Text("Things to avoid...") },
                        maxLines = 2,
                        enabled = !isGenerating,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Seed (always available)
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
        val canGenerate = prompt.isNotBlank() && !isGenerating &&
            (!isOnDevice || isModelReady)

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = canGenerate,
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Generating...")
            } else if (isOnDevice && !isModelReady) {
                Text("Download model first")
            } else {
                Text("Generate")
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    downloadState: ModelDownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (downloadState) {
                is ModelDownloadState.Ready -> MaterialTheme.colorScheme.primaryContainer
                is ModelDownloadState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (downloadState) {
                is ModelDownloadState.NotDownloaded -> {
                    Text(
                        text = "SD 1.5 Model Required",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Download the on-device model (~1.8 GB) to generate images locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Download Model")
                    }
                }

                is ModelDownloadState.Downloading -> {
                    Text(
                        text = "Downloading Model...",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(downloadState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                is ModelDownloadState.Paused -> {
                    Text(
                        text = "Download Paused",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(downloadState.progress * 100).toInt()}% — paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                is ModelDownloadState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Model ready",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                is ModelDownloadState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    // Only show retry if it's not a device capability issue
                    val isDeviceIssue = downloadState.message.contains("RAM", ignoreCase = true) ||
                        downloadState.message.contains("GPU", ignoreCase = true) ||
                        downloadState.message.contains("supported", ignoreCase = true)
                    if (!isDeviceIssue) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Retry Download")
                        }
                    }
                }
            }
        }
    }
}

// Previews

@Preview(showBackground = true)
@Composable
private fun GenerateSheetPreview() {
    ImageLabarotoryTheme {
        GenerateSheetContent(
            prompt = "A cat wearing a space helmet",
            isGenerating = false,
            errorMessage = null,
            selectedProvider = ApiProvider.HUGGING_FACE,
            modelDownloadState = ModelDownloadState.NotDownloaded,
            sizePreset = ImageSizePreset.SQUARE,
            numInferenceSteps = 4,
            guidanceScale = 3.5f,
            negativePrompt = "",
            seed = "",
            showAdvancedSettings = false,
            onPromptChanged = {},
            onProviderChanged = {},
            onDownloadModel = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
            onSizePresetChanged = {},
            onInferenceStepsChanged = {},
            onGuidanceScaleChanged = {},
            onNegativePromptChanged = {},
            onSeedChanged = {},
            onToggleAdvancedSettings = {},
            onGenerate = {},
        )
    }
}

@Preview(showBackground = true, name = "On-Device - Model Not Downloaded")
@Composable
private fun GenerateSheetOnDevicePreview() {
    ImageLabarotoryTheme {
        GenerateSheetContent(
            prompt = "A futuristic city",
            isGenerating = false,
            errorMessage = null,
            selectedProvider = ApiProvider.MEDIA_PIPE,
            modelDownloadState = ModelDownloadState.NotDownloaded,
            sizePreset = ImageSizePreset.SQUARE,
            numInferenceSteps = 20,
            guidanceScale = 3.5f,
            negativePrompt = "",
            seed = "",
            showAdvancedSettings = false,
            onPromptChanged = {},
            onProviderChanged = {},
            onDownloadModel = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
            onSizePresetChanged = {},
            onInferenceStepsChanged = {},
            onGuidanceScaleChanged = {},
            onNegativePromptChanged = {},
            onSeedChanged = {},
            onToggleAdvancedSettings = {},
            onGenerate = {},
        )
    }
}

@Preview(showBackground = true, name = "On-Device - Model Ready")
@Composable
private fun GenerateSheetOnDeviceReadyPreview() {
    ImageLabarotoryTheme {
        GenerateSheetContent(
            prompt = "A sunset over mountains",
            isGenerating = false,
            errorMessage = null,
            selectedProvider = ApiProvider.MEDIA_PIPE,
            modelDownloadState = ModelDownloadState.Ready,
            sizePreset = ImageSizePreset.SQUARE,
            numInferenceSteps = 20,
            guidanceScale = 3.5f,
            negativePrompt = "",
            seed = "",
            showAdvancedSettings = true,
            onPromptChanged = {},
            onProviderChanged = {},
            onDownloadModel = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
            onSizePresetChanged = {},
            onInferenceStepsChanged = {},
            onGuidanceScaleChanged = {},
            onNegativePromptChanged = {},
            onSeedChanged = {},
            onToggleAdvancedSettings = {},
            onGenerate = {},
        )
    }
}

@Preview(showBackground = true, name = "Generating State")
@Composable
private fun GenerateSheetGeneratingPreview() {
    ImageLabarotoryTheme {
        GenerateSheetContent(
            prompt = "A beautiful landscape",
            isGenerating = true,
            errorMessage = null,
            selectedProvider = ApiProvider.HUGGING_FACE,
            modelDownloadState = ModelDownloadState.NotDownloaded,
            sizePreset = ImageSizePreset.SQUARE,
            numInferenceSteps = 4,
            guidanceScale = 3.5f,
            negativePrompt = "",
            seed = "",
            showAdvancedSettings = false,
            onPromptChanged = {},
            onProviderChanged = {},
            onDownloadModel = {},
            onPauseDownload = {},
            onResumeDownload = {},
            onCancelDownload = {},
            onSizePresetChanged = {},
            onInferenceStepsChanged = {},
            onGuidanceScaleChanged = {},
            onNegativePromptChanged = {},
            onSeedChanged = {},
            onToggleAdvancedSettings = {},
            onGenerate = {},
        )
    }
}
