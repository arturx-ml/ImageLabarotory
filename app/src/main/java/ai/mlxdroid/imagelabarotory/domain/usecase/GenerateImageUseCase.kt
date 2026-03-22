package ai.mlxdroid.imagelabarotory.domain.usecase

import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.repository.ImageRepository
import ai.mlxdroid.imagelabarotory.ui.gallery.ImageSizePreset
import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import ai.mlxdroid.imagelabarotory.util.ImageStorage
import javax.inject.Inject

class GenerateImageUseCase @Inject constructor(
    private val repository: ImageRepository,
    private val imageStorage: ImageStorage,
) {
    suspend operator fun invoke(
        prompt: String,
        sizePreset: ImageSizePreset,
        numInferenceSteps: Int,
        guidanceScale: Float,
        negativePrompt: String?,
        seed: Long?,
        provider: ApiProvider,
    ): Result<GeneratedImage> {
        val request = ImageGenerationRequest(
            prompt = prompt,
            width = if (provider.supportsSizePresets) sizePreset.width else 512,
            height = if (provider.supportsSizePresets) sizePreset.height else 512,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            negativePrompt = negativePrompt,
            seed = seed,
        )

        return when (val result = repository.generateImage(request, provider)) {
            is ImageGenerationResult.Success -> {
                val saved = imageStorage.saveImage(result.imageBytes, prompt)
                Result.success(saved)
            }
            is ImageGenerationResult.Error -> {
                Result.failure(Exception(result.message))
            }
        }
    }
}
