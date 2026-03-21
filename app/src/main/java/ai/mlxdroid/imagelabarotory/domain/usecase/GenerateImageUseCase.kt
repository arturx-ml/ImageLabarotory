package ai.mlxdroid.imagelabarotory.domain.usecase

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
    ): Result<GeneratedImage> {
        val request = ImageGenerationRequest(
            prompt = prompt,
            width = sizePreset.width,
            height = sizePreset.height,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            negativePrompt = negativePrompt,
            seed = seed,
        )

        return when (val result = repository.generateImage(request)) {
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
