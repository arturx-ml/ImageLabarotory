package ai.mlxdroid.imagelabarotory.data.repository

import ai.mlxdroid.imagelabarotory.util.ModelDownloader
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val type: ModelType,
    val resolution: Int,
    val estimatedSize: Long,
    val downloadUrl: String,
    val estimatedTimeSeconds: Int,
    val quality: Quality
)

enum class ModelType {
    TEXT_TO_IMAGE,
    IMAGE_TO_IMAGE,
    BOTH
}

enum class Quality {
    FAST,
    MEDIUM,
    HIGH
}

class ModelRepository(context: Context) {
    private val modelDownloader = ModelDownloader(context)

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(getAvailableModels())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    init {
        refreshDownloadedModels()
    }

    private fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                id = "sd_v14_512",
                name = "Stable Diffusion v1.4 (512px)",
                description = "Full quality Stable Diffusion v1.4. Generates 512x512 images. Requires 2GB storage for 3 model files (text encoder, diffusion model, decoder). GPU acceleration recommended.",
                type = ModelType.BOTH,
                resolution = 512,
                estimatedSize = 2048 * 1024 * 1024L, // ~2GB for all 3 models
                // Note: This URL is for the text encoder. The download system needs to be updated
                // to support downloading all 3 required files (text_encoder, diffusion_model, decoder)
                downloadUrl = "https://huggingface.co/keras-sd/text-encoder-tflite/resolve/main/text_encoder.tflite",
                estimatedTimeSeconds = 60,
                quality = Quality.HIGH
            )
            // Additional models can be added here in the future:
            // - SD v1.5 with improved quality
            // - Smaller quantized versions for faster inference
            // - Domain-specific fine-tuned models
        )
    }

    fun refreshDownloadedModels() {
        val downloaded = _availableModels.value
            .filter { modelDownloader.isModelDownloaded(it.id) }
            .map { it.id }
            .toSet()
        _downloadedModels.value = downloaded
    }

    fun getModelInfo(modelId: String): ModelInfo? {
        return _availableModels.value.find { it.id == modelId }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        return modelDownloader.isModelDownloaded(modelId)
    }

    fun getModelFile(modelId: String) = modelDownloader.getModelFile(modelId)

    fun deleteModel(modelId: String): Boolean {
        val result = modelDownloader.deleteModel(modelId)
        if (result) {
            refreshDownloadedModels()
        }
        return result
    }

    fun getTotalModelsSize(): Long {
        return modelDownloader.getTotalModelsSize()
    }

    fun getModelsByType(type: ModelType): List<ModelInfo> {
        return _availableModels.value.filter {
            it.type == type || it.type == ModelType.BOTH
        }
    }
}
