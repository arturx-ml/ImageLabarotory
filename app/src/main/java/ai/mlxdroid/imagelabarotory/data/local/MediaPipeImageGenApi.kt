package ai.mlxdroid.imagelabarotory.data.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
import java.io.File
import javax.inject.Inject

class MediaPipeImageGenApi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: MediaPipeModelManager,
) : ImageGenerationApi {

    override val provider: ApiProvider = ApiProvider.MEDIA_PIPE

    private var imageGenerator: ImageGenerator? = null

    override suspend fun generateImage(
        request: ImageGenerationRequest,
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        try {
            // Check device capability BEFORE touching native code to prevent SIGSEGV
            if (!modelManager.isDeviceSupported) {
                val reason = modelManager.unsupportedReason ?: "Device not supported"
                Log.w(TAG, "Generate blocked — $reason")
                return@withContext ImageGenerationResult.Error(reason)
            }

            if (!modelManager.isModelReady()) {
                Log.w(TAG, "Generate called but model is not downloaded")
                return@withContext ImageGenerationResult.Error(
                    "Model not downloaded. Please download the model first."
                )
            }

            Log.i(TAG, "Starting image generation — prompt=\"${request.prompt}\", " +
                "steps=${request.numInferenceSteps}, seed=${request.seed}")

            // Log model directory contents for debugging
            logModelDirectory()

            val startTime = System.currentTimeMillis()

            Log.d(TAG, "Getting or creating ImageGenerator...")
            val generator = getOrCreateGenerator()
            Log.d(TAG, "ImageGenerator ready (took ${System.currentTimeMillis() - startTime}ms)")

            val seed = (request.seed ?: System.currentTimeMillis()).toInt()
            Log.d(TAG, "Using seed=$seed, steps=${request.numInferenceSteps}")

            val generateStart = System.currentTimeMillis()
            Log.d(TAG, ">>> Calling ImageGenerator.generate() — this may take a while...")
            val result = generator.generate(
                request.prompt,
                request.numInferenceSteps,
                seed,
            )
            val generateTime = System.currentTimeMillis() - generateStart
            Log.d(TAG, "<<< ImageGenerator.generate() completed in ${generateTime}ms")

            if (result == null) {
                Log.e(TAG, "ImageGenerator.generate() returned null")
                return@withContext ImageGenerationResult.Error("Generation returned null result")
            }

            val generatedImage = result.generatedImage()
            if (generatedImage == null) {
                Log.e(TAG, "result.generatedImage() returned null")
                return@withContext ImageGenerationResult.Error("Generated image is null")
            }

            val extractStart = System.currentTimeMillis()
            Log.d(TAG, "Extracting bitmap from MPImage...")
            val bitmap = BitmapExtractor.extract(generatedImage)
                ?: run {
                    Log.e(TAG, "BitmapExtractor.extract() returned null")
                    return@withContext ImageGenerationResult.Error("Failed to extract generated image")
                }
            Log.d(TAG, "Bitmap extracted — ${bitmap.width}x${bitmap.height}, " +
                "config=${bitmap.config}, took ${System.currentTimeMillis() - extractStart}ms")

            val compressStart = System.currentTimeMillis()
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imageBytes = stream.toByteArray()
            Log.d(TAG, "PNG compressed — ${imageBytes.size} bytes (${imageBytes.size / 1024} KB), " +
                "took ${System.currentTimeMillis() - compressStart}ms")

            val totalTime = System.currentTimeMillis() - startTime
            if (imageBytes.isEmpty()) {
                Log.e(TAG, "Generated image bytes are empty after compression")
                ImageGenerationResult.Error("Generated image is empty")
            } else {
                Log.i(TAG, "Image generation complete — total=${totalTime}ms " +
                    "(generate=${generateTime}ms, extract+compress=${totalTime - generateTime}ms), " +
                    "output=${imageBytes.size / 1024} KB")
                ImageGenerationResult.Success(imageBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "On-device generation failed", e)
            // Release generator on error to allow fresh init on retry
            releaseGenerator()
            ImageGenerationResult.Error("On-device generation failed: ${e.message}")
        }
    }

    private fun getOrCreateGenerator(): ImageGenerator {
        return imageGenerator ?: run {
            val modelPath = modelManager.getModelDirectoryPath()
            Log.d(TAG, "Creating new ImageGenerator from $modelPath")

            // Verify the directory exists and has files
            val dir = File(modelPath)
            val fileCount = dir.listFiles()?.size ?: 0
            val totalSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            Log.d(TAG, "Model directory: exists=${dir.exists()}, files=$fileCount, " +
                "totalSize=${totalSize / (1024 * 1024)} MB")

            val initStart = System.currentTimeMillis()
            Log.d(TAG, ">>> Calling ImageGenerator.createFromOptions() — native init...")
            val options = ImageGenerator.ImageGeneratorOptions.builder()
                .setImageGeneratorModelDirectory(modelPath)
                .build()
            Log.d(TAG, "Options built, calling createFromOptions now...")
            ImageGenerator.createFromOptions(context, options).also {
                imageGenerator = it
                Log.i(TAG, "<<< ImageGenerator created in ${System.currentTimeMillis() - initStart}ms")
            }
        }
    }

    fun releaseGenerator() {
        if (imageGenerator != null) {
            Log.d(TAG, "Releasing ImageGenerator")
            try {
                imageGenerator?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing ImageGenerator", e)
            }
            imageGenerator = null
        }
    }

    private fun logModelDirectory() {
        val dir = File(modelManager.getModelDirectoryPath())
        if (!dir.exists()) {
            Log.e(TAG, "Model directory does not exist: ${dir.absolutePath}")
            return
        }
        val files = dir.listFiles() ?: emptyArray()
        val binCount = files.count { it.extension == "bin" }
        val otherFiles = files.filter { it.extension != "bin" }.map { it.name }
        val totalSize = files.sumOf { it.length() }
        Log.d(TAG, "Model directory: ${files.size} files ($binCount .bin), " +
            "total=${totalSize / (1024 * 1024)} MB")
        if (otherFiles.isNotEmpty()) {
            Log.d(TAG, "Non-bin files: $otherFiles")
        }
        // Log a few .bin files as sanity check
        files.filter { it.extension == "bin" }.take(3).forEach {
            Log.v(TAG, "  Sample: ${it.name} (${it.length()} bytes)")
        }
    }

    companion object {
        private const val TAG = "MediaPipeImageGen"
    }
}
