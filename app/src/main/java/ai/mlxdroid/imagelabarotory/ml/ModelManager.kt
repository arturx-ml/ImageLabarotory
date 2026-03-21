package ai.mlxdroid.imagelabarotory.ml

import ai.mlxdroid.imagelabarotory.data.repository.ModelRepository
import ai.mlxdroid.imagelabarotory.ml.models.BaseGeneratorModel
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ModelManager(
    private val context: Context,
    private val modelRepository: ModelRepository,
    private val useGpu: Boolean = true
) {
    private val mutex = Mutex()
    private var currentModel: LoadedModel? = null

    data class LoadedModel(
        val modelId: String,
        val model: BaseGeneratorModel
    )

    suspend fun loadModel(modelId: String): Result<BaseGeneratorModel> = mutex.withLock {
        try {
            if (currentModel?.modelId == modelId && currentModel?.model?.isInitialized() == true) {
                return@withLock Result.success(currentModel!!.model)
            }

            unloadCurrentModel()

            val modelFile = modelRepository.getModelFile(modelId)
                ?: return@withLock Result.failure(Exception("Model file not found. Please download the model first."))

            val model = createModelInstance(modelId, modelFile)
            currentModel = LoadedModel(modelId, model)

            Result.success(model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createModelInstance(modelId: String, modelFile: File): BaseGeneratorModel {
        // For SD models, modelFile is actually the models directory
        // containing text_encoder, diffusion_model, and decoder files
        return StableDiffusionModel(context, modelFile, useGpu)
    }

    private fun unloadCurrentModel() {
        currentModel?.model?.close()
        currentModel = null
        System.gc()
    }

    suspend fun unloadModel() = mutex.withLock {
        unloadCurrentModel()
    }

    fun getCurrentModel(): BaseGeneratorModel? {
        return currentModel?.model
    }

    fun getCurrentModelId(): String? {
        return currentModel?.modelId
    }

    fun isModelLoaded(modelId: String): Boolean {
        return currentModel?.modelId == modelId && currentModel?.model?.isInitialized() == true
    }
}

class StableDiffusionModel(
    context: Context,
    modelsDir: File,
    useGpu: Boolean = true
) : BaseGeneratorModel(modelsDir, useGpu) {

    private val pipeline: ai.mlxdroid.imagelabarotory.ml.pipeline.StableDiffusionPipeline

    init {
        // Find the three ORT model files in the models directory
        val textEncoderFile = File(modelsDir, "text_encoder_quant.ort")
        val diffusionModelFile = File(modelsDir, "unet_lcm_int8.onnx")
        val decoderFile = File(modelsDir, "decoder_quant.ort")

        // Verify all files exist
        require(textEncoderFile.exists()) { "text_encoder_quant.ort not found: ${textEncoderFile.absolutePath}" }
        require(diffusionModelFile.exists()) { "unet_lcm_int8.onnx not found: ${diffusionModelFile.absolutePath}" }
        require(decoderFile.exists()) { "decoder_quant.ort not found: ${decoderFile.absolutePath}" }

        // Initialize pipeline
        pipeline = ai.mlxdroid.imagelabarotory.ml.pipeline.StableDiffusionPipeline(
            context = context,
            textEncoderFile = textEncoderFile,
            diffusionModelFile = diffusionModelFile,
            decoderFile = decoderFile,
            useGpu = useGpu
        )
    }

    override val inputSize: Int = 512
    override val outputSize: Int = 512

    override fun generate(
        input: Any,
        onProgress: ((Float) -> Unit)?
    ): android.graphics.Bitmap? {
        return when (input) {
            is ai.mlxdroid.imagelabarotory.ml.pipeline.TextToImageInput -> {
                pipeline.generate(input, onProgress)
            }
            is ai.mlxdroid.imagelabarotory.ml.pipeline.ImageToImageInput -> {
                pipeline.generateImageToImage(input, onProgress)
            }
            else -> {
                throw IllegalArgumentException("Invalid input type: ${input::class.java.simpleName}")
            }
        }
    }

    override fun isInitialized(): Boolean = pipeline.isInitialized()

    override fun close() {
        pipeline.close()
        super.close()
    }
}
