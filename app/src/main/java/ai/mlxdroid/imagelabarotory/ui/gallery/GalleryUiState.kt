package ai.mlxdroid.imagelabarotory.ui.gallery

import ai.mlxdroid.imagelabarotory.util.GeneratedImage

data class GalleryUiState(
    val images: List<GeneratedImage> = emptyList(),
    val isGenerating: Boolean = false,
    val prompt: String = "",
    val errorMessage: String? = null,
    val showGenerateSheet: Boolean = false,
)
