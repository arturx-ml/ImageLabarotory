package ai.mlxdroid.imagelabarotory.ml

import ai.mlxdroid.imagelabarotory.util.BitmapUtils
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class ImageToImageParams(
    val sourceImage: Bitmap,
    val prompt: String,
    val negativePrompt: String = "",
    val strength: Float = 0.75f,
    val seed: Long = Random.nextLong(),
    val steps: Int = 20,
    val guidanceScale: Float = 7.5f
)

class ImageToImageGenerator(
    private val modelManager: ModelManager
) {

    fun generate(params: ImageToImageParams): Flow<GenerationState> = flow {
        try {
            emit(GenerationState.Idle)
            emit(GenerationState.Loading("Loading model..."))

            val model = modelManager.getCurrentModel()
                ?: throw Exception("No model loaded. Please select and load a model first.")

            emit(GenerationState.Loading("Processing source image..."))

            val processedImage = BitmapUtils.resizeBitmap(
                params.sourceImage,
                model.inputSize,
                model.inputSize
            )

            emit(GenerationState.Loading("Preparing generation..."))

            // Create input for the pipeline
            val input = ai.mlxdroid.imagelabarotory.ml.pipeline.ImageToImageInput(
                sourceImage = processedImage,
                prompt = params.prompt,
                negativePrompt = params.negativePrompt,
                strength = params.strength,
                seed = params.seed,
                steps = params.steps,
                guidanceScale = params.guidanceScale
            )

            // Generate image with progress tracking
            val resultBitmap = withContext(Dispatchers.Default) {
                model.generate(input) { progress ->
                    // Note: Image-to-image may not be fully implemented yet in the pipeline
                    // Progress tracking will be added when implementation is complete
                }
            }

            if (resultBitmap != null) {
                emit(GenerationState.Success(resultBitmap, params.seed))
            } else {
                emit(GenerationState.Error("Generation failed - image-to-image not yet fully implemented"))
            }

        } catch (e: Exception) {
            emit(GenerationState.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)
}
