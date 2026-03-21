package ai.mlxdroid.imagelabarotory.ui.gallery

import ai.mlxdroid.imagelabarotory.util.GeneratedImage

enum class ImageSizePreset(val label: String, val width: Int, val height: Int) {
    SQUARE("Square", 1024, 1024),
    PORTRAIT("Portrait", 768, 1024),
    LANDSCAPE("Landscape", 1024, 768),
}

data class GalleryUiState(
    val images: List<GeneratedImage> = emptyList(),
    val isGenerating: Boolean = false,
    val prompt: String = "",
    val errorMessage: String? = null,
    val showGenerateSheet: Boolean = false,
    // Generation settings
    val sizePreset: ImageSizePreset = ImageSizePreset.SQUARE,
    val numInferenceSteps: Int = 4,
    val guidanceScale: Float = 3.5f,
    val negativePrompt: String = "",
    val seed: String = "",
    val showAdvancedSettings: Boolean = false,
    // Full-screen image viewer
    val selectedImage: GeneratedImage? = null,
)
