package ai.mlxdroid.imagelabarotory.ui.screens

import ai.mlxdroid.imagelabarotory.data.repository.ModelRepository
import ai.mlxdroid.imagelabarotory.ml.GenerationState
import ai.mlxdroid.imagelabarotory.ml.ModelManager
import ai.mlxdroid.imagelabarotory.ml.TextToImageGenerator
import ai.mlxdroid.imagelabarotory.ml.TextToImageParams
import ai.mlxdroid.imagelabarotory.ui.components.GenerationProgress
import ai.mlxdroid.imagelabarotory.ui.components.ImagePreview
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToImageScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val modelRepository = remember { ModelRepository(context) }
    val modelManager = remember { ModelManager(context, modelRepository, useGpu = true) }
    val generator = remember { TextToImageGenerator(modelManager) }

    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var steps by remember { mutableIntStateOf(4) }
    var guidanceScale by remember { mutableFloatStateOf(7.5f) }

    var generationState by remember { mutableStateOf<GenerationState>(GenerationState.Idle) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var isModelLoading by remember { mutableStateOf(true) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    // Auto-load the model on IO thread when the screen appears
    LaunchedEffect(Unit) {
        isModelLoading = true
        modelLoadError = null
        val result = withContext(Dispatchers.IO) {
            modelManager.loadModel("sd_v14_512")
        }
        isModelLoading = false
        result.onFailure { error ->
            modelLoadError = "Failed to load model: ${error.message}"
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Text to Image") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (isModelLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading model (this may take ~30 seconds)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            modelLoadError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (generatedBitmap != null) {
                ImagePreview(bitmap = generatedBitmap)
            }

            if (isGenerating || generationState is GenerationState.Error) {
                GenerationProgress(state = generationState)
            }

            Text(
                text = "Prompt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your image description...") },
                minLines = 3,
                maxLines = 5
            )

            Text(
                text = "Negative Prompt (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = negativePrompt,
                onValueChange = { negativePrompt = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What to avoid in the image...") },
                minLines = 2,
                maxLines = 3
            )

            Text(
                text = "Generation Steps: $steps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Slider(
                value = steps.toFloat(),
                onValueChange = { steps = it.toInt() },
                valueRange = 10f..50f,
                steps = 7
            )

            Text(
                text = "Guidance Scale: ${"%.1f".format(guidanceScale)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Slider(
                value = guidanceScale,
                onValueChange = { guidanceScale = it },
                valueRange = 1f..15f
            )

            Button(
                onClick = {
                    if (prompt.isNotBlank()) {
                        isGenerating = true
                        coroutineScope.launch {
                            val params = TextToImageParams(
                                prompt = prompt,
                                negativePrompt = negativePrompt,
                                steps = steps,
                                guidanceScale = guidanceScale
                            )

                            generator.generate(params)
                                .onEach { state ->
                                    generationState = state
                                    if (state is GenerationState.Success) {
                                        generatedBitmap = state.bitmap
                                        isGenerating = false
                                    } else if (state is GenerationState.Error) {
                                        isGenerating = false
                                    }
                                }
                                .launchIn(this)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = prompt.isNotBlank() && !isGenerating && !isModelLoading && modelLoadError == null
            ) {
                Text(if (isGenerating) "Generating..." else "Generate Image")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
