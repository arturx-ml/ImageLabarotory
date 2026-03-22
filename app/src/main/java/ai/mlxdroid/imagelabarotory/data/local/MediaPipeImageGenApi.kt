package ai.mlxdroid.imagelabarotory.data.local

import android.content.Context
import android.graphics.Bitmap
import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.remote.ImageGenerationApi
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class MediaPipeImageGenApi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: MediaPipeModelManager,
) : ImageGenerationApi {

    override val provider: ApiProvider = ApiProvider.MEDIA_PIPE

    private var imageGenerator: ImageGenerator? = null

    override suspend fun generateImage(
        request: ImageGenerationRequest,
    ): ImageGenerationResult = withContext(Dispatchers.Default) {
        try {
            if (!modelManager.isModelReady()) {
                return@withContext ImageGenerationResult.Error(
                    "Model not downloaded. Please download the model first."
                )
            }

            val generator = getOrCreateGenerator()

            val seed = (request.seed ?: System.currentTimeMillis()).toInt()
            val result = generator.generate(
                request.prompt,
                request.numInferenceSteps,
                seed,
            )

            val bitmap = BitmapExtractor.extract(result?.generatedImage())
                ?: return@withContext ImageGenerationResult.Error("Failed to extract generated image")

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imageBytes = stream.toByteArray()

            if (imageBytes.isEmpty()) {
                ImageGenerationResult.Error("Generated image is empty")
            } else {
                ImageGenerationResult.Success(imageBytes)
            }
        } catch (e: Exception) {
            // Release generator on error to allow fresh init on retry
            releaseGenerator()
            ImageGenerationResult.Error("On-device generation failed: ${e.message}")
        }
    }

    private fun getOrCreateGenerator(): ImageGenerator {
        return imageGenerator ?: run {
            val options = ImageGenerator.ImageGeneratorOptions.builder()
                .setImageGeneratorModelDirectory(modelManager.getModelDirectoryPath())
                .build()
            ImageGenerator.createFromOptions(context, options).also {
                imageGenerator = it
            }
        }
    }

    fun releaseGenerator() {
        imageGenerator?.close()
        imageGenerator = null
    }
}
