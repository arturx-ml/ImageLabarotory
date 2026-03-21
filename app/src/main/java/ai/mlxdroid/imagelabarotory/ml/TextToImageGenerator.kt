package ai.mlxdroid.imagelabarotory.ml

import ai.mlxdroid.imagelabarotory.util.BitmapUtils
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed class GenerationState {
    object Idle : GenerationState()
    data class Loading(val message: String) : GenerationState()
    data class Generating(val progress: Float, val step: Int, val totalSteps: Int) : GenerationState()
    data class Success(val bitmap: Bitmap, val seed: Long) : GenerationState()
    data class Error(val message: String) : GenerationState()
}

data class TextToImageParams(
    val prompt: String,
    val negativePrompt: String = "",
    val seed: Long = Random.nextLong(),
    val steps: Int = 20,
    val guidanceScale: Float = 7.5f,
    val width: Int = 512,
    val height: Int = 512
)

class TextToImageGenerator(
    private val modelManager: ModelManager
) {

    fun generate(params: TextToImageParams): Flow<GenerationState> = flow {
        try {
            emit(GenerationState.Idle)
            emit(GenerationState.Loading("Loading model..."))

            val model = modelManager.getCurrentModel()
                ?: throw Exception("No model loaded. Please select and load a model first.")

            emit(GenerationState.Loading("Preparing generation..."))

            // Create input for the pipeline
            val input = ai.mlxdroid.imagelabarotory.ml.pipeline.TextToImageInput(
                prompt = params.prompt,
                negativePrompt = params.negativePrompt,
                seed = params.seed,
                steps = params.steps,
                guidanceScale = params.guidanceScale,
                width = params.width,
                height = params.height
            )

            // Generate image with progress tracking
            val resultBitmap = withContext(Dispatchers.Default) {
                model.generate(input) { progress ->
                    val step = (progress * params.steps).toInt().coerceIn(1, params.steps)
                    // Note: Can't emit from this callback as it's on a different coroutine
                    // The actual progress updates happen inside the model
                }
            }

            if (resultBitmap != null) {
                emit(GenerationState.Success(resultBitmap, params.seed))
            } else {
                emit(GenerationState.Error("Generation failed - model returned null"))
            }

        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)
}
