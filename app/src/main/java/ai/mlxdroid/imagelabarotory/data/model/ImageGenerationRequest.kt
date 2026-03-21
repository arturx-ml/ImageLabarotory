package ai.mlxdroid.imagelabarotory.data.model

data class ImageGenerationRequest(
    val prompt: String,
    val width: Int = 1024,
    val height: Int = 1024,
    val numInferenceSteps: Int = 4,
    val guidanceScale: Float = 3.5f,
    val negativePrompt: String? = null,
    val seed: Long? = null,
)
