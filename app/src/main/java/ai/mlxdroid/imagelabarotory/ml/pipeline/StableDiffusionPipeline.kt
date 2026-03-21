package ai.mlxdroid.imagelabarotory.ml.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.mlxdroid.imagelabarotory.ml.models.DecoderModel
import ai.mlxdroid.imagelabarotory.ml.models.DiffusionModel
import ai.mlxdroid.imagelabarotory.ml.models.TextEncoderModel
import ai.mlxdroid.imagelabarotory.ml.tokenizer.ClipTokenizer
import java.io.Closeable
import java.io.File

/**
 * Stable Diffusion Pipeline
 *
 * Orchestrates the complete image generation process by chaining together:
 * 1. Text Tokenization (CLIP tokenizer)
 * 2. Text Encoding (text encoder model)
 * 3. Diffusion Process (diffusion model)
 * 4. Image Decoding (decoder model)
 *
 * Models are loaded sequentially and released after each phase to minimize peak memory usage.
 *
 * @param context Android context for accessing tokenizer assets
 * @param textEncoderFile Path to text_encoder.tflite
 * @param diffusionModelFile Path to diffusion_model.tflite
 * @param decoderFile Path to decoder.tflite
 * @param useGpu Whether to use GPU acceleration
 */
class StableDiffusionPipeline(
    private val context: Context,
    private val textEncoderFile: File,
    private val diffusionModelFile: File,
    private val decoderFile: File,
    private val useGpu: Boolean = true
) : Closeable {

    private val tokenizer: ClipTokenizer = ClipTokenizer.fromAssets(context)

    companion object {
        private const val TAG = "StableDiffusionPipeline"
    }

    /**
     * Generate an image from a text prompt
     *
     * Each model is loaded, used, and immediately closed to keep peak memory low.
     *
     * @param input Generation parameters
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Generated image as Bitmap, or null on failure
     */
    fun generate(
        input: TextToImageInput,
        onProgress: ((Float) -> Unit)? = null
    ): Bitmap? {
        return try {
            Log.d(TAG, "Starting generation: '${input.prompt}'")
            Log.d(TAG, "Parameters: steps=${input.steps}, guidance=${input.guidanceScale}, seed=${input.seed}")

            // Phase 1: Tokenize prompts (0-5%)
            onProgress?.invoke(0.0f)
            Log.d(TAG, "Phase 1: Tokenizing prompts")
            val tokens = tokenizer.encode(input.prompt)
            val unconditionalTokens = tokenizer.encode(input.negativePrompt)
            onProgress?.invoke(0.05f)

            // Phase 2: Encode text to embeddings (5-10%), then release encoder
            Log.d(TAG, "Phase 2: Encoding text (loading text encoder ~121 MB)")
            val contextEmbeddings: FloatArray
            val unconditionalEmbeddings: FloatArray
            TextEncoderModel(textEncoderFile, useGpu).use { encoder ->
                contextEmbeddings = encoder.encode(tokens)
                unconditionalEmbeddings = encoder.encode(unconditionalTokens)
            }
            System.gc()
            onProgress?.invoke(0.10f)
            Log.d(TAG, "Phase 2 complete, text encoder released")

            // Phase 3: Diffusion process (10-90%), then release diffusion model
            Log.d(TAG, "Phase 3: Running diffusion process (loading diffusion model ~824 MB)")
            val latent: FloatArray
            DiffusionModel(diffusionModelFile, useGpu).use { diffusion ->
                latent = diffusion.diffuse(
                    contextEmbeddings = contextEmbeddings,
                    unconditionalEmbeddings = unconditionalEmbeddings,
                    seed = input.seed,
                    steps = input.steps,
                    guidanceScale = input.guidanceScale
                ) { diffusionProgress ->
                    val overallProgress = 0.1f + (diffusionProgress * 0.8f)
                    onProgress?.invoke(overallProgress)
                }
            }
            System.gc()
            onProgress?.invoke(0.90f)
            Log.d(TAG, "Phase 3 complete, diffusion model released")

            // Phase 4: Decode latent to image (90-100%), then release decoder
            Log.d(TAG, "Phase 4: Decoding to image (loading decoder ~48 MB)")
            val bitmap: Bitmap
            DecoderModel(decoderFile, useGpu).use { dec ->
                bitmap = dec.decode(latent)
            }
            System.gc()
            onProgress?.invoke(1.0f)
            Log.d(TAG, "Generation complete: ${bitmap.width}x${bitmap.height}")

            bitmap

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed: ${e.message}", e)
            null
        }
    }

    /**
     * Generate an image from an existing image and prompt (image-to-image)
     */
    fun generateImageToImage(
        input: ImageToImageInput,
        onProgress: ((Float) -> Unit)? = null
    ): Bitmap? {
        Log.w(TAG, "Image-to-image not yet implemented")
        return null
    }

    fun isInitialized(): Boolean = true

    override fun close() {
        // No persistent model instances to close — all freed after each phase
        Log.d(TAG, "Pipeline closed")
    }
}

/**
 * Input parameters for text-to-image generation
 */
data class TextToImageInput(
    val prompt: String,
    val negativePrompt: String = "",
    val seed: Long,
    val steps: Int = 20,
    val guidanceScale: Float = 7.5f,
    val width: Int = 512,
    val height: Int = 512
)

/**
 * Input parameters for image-to-image generation
 */
data class ImageToImageInput(
    val sourceImage: Bitmap,
    val prompt: String,
    val negativePrompt: String = "",
    val strength: Float = 0.75f,
    val seed: Long,
    val steps: Int = 20,
    val guidanceScale: Float = 7.5f
)
