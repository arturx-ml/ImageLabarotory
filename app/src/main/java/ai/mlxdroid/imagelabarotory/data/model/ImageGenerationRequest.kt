package ai.mlxdroid.imagelabarotory.data.model

data class ImageGenerationRequest(
    val prompt: String,
    val width: Int = 1024,
    val height: Int = 1024,
)
