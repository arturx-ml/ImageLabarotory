package ai.mlxdroid.imagelabarotory.ui.screens

import ai.mlxdroid.imagelabarotory.data.repository.ModelRepository
import ai.mlxdroid.imagelabarotory.ml.GenerationState
import ai.mlxdroid.imagelabarotory.ml.ImageToImageGenerator
import ai.mlxdroid.imagelabarotory.ml.ImageToImageParams
import ai.mlxdroid.imagelabarotory.ml.ModelManager
import ai.mlxdroid.imagelabarotory.ui.components.GenerationProgress
import ai.mlxdroid.imagelabarotory.ui.components.ImagePreview
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToImageScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val modelRepository = remember { ModelRepository(context) }
    val modelManager = remember { ModelManager(context, modelRepository, useGpu = true) }
    val generator = remember { ImageToImageGenerator(modelManager) }

    var sourceImage by remember { mutableStateOf<Bitmap?>(null) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var strength by remember { mutableFloatStateOf(0.75f) }
    var steps by remember { mutableIntStateOf(20) }
    var guidanceScale by remember { mutableFloatStateOf(7.5f) }

    var generationState by remember { mutableStateOf<GenerationState>(GenerationState.Idle) }
    var isGenerating by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            sourceImage = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Image to Image") },
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

            Text(
                text = "Source Image",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (sourceImage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Original",
                            style = MaterialTheme.typography.labelMedium
                        )
                        ImagePreview(bitmap = sourceImage)
                    }

                    if (generatedBitmap != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Generated",
                                style = MaterialTheme.typography.labelMedium
                            )
                            ImagePreview(bitmap = generatedBitmap)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(if (sourceImage == null) "Select Image" else "Change Image")
            }

            if (isGenerating) {
                GenerationProgress(state = generationState)
            }

            Text(
                text = "Transformation Prompt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("How to transform the image...") },
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
                placeholder = { Text("What to avoid...") },
                minLines = 2,
                maxLines = 3
            )

            Text(
                text = "Strength: ${"%.2f".format(strength)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Higher values make bigger changes to the original image",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Slider(
                value = strength,
                onValueChange = { strength = it },
                valueRange = 0.1f..1f
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
                    sourceImage?.let { bitmap ->
                        if (prompt.isNotBlank()) {
                            isGenerating = true
                            coroutineScope.launch {
                                val params = ImageToImageParams(
                                    sourceImage = bitmap,
                                    prompt = prompt,
                                    negativePrompt = negativePrompt,
                                    strength = strength,
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
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = sourceImage != null && prompt.isNotBlank() && !isGenerating
            ) {
                Text(if (isGenerating) "Generating..." else "Generate Image")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
