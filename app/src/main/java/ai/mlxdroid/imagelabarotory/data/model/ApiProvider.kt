package ai.mlxdroid.imagelabarotory.data.model

enum class ApiProvider(
    val displayName: String,
    val supportsGuidanceScale: Boolean = true,
    val supportsNegativePrompt: Boolean = true,
    val supportsSizePresets: Boolean = true,
    val defaultSteps: Int = 4,
    val maxSteps: Int = 50,
) {
    HUGGING_FACE(
        displayName = "Cloud (FLUX)",
        defaultSteps = 4,
    ),
    MEDIA_PIPE(
        displayName = "On-Device",
        supportsGuidanceScale = false,
        supportsNegativePrompt = false,
        supportsSizePresets = false,
        defaultSteps = 20,
    ),
}
