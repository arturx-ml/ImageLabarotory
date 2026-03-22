package ai.mlxdroid.imagelabarotory.domain.usecase

import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import ai.mlxdroid.imagelabarotory.util.ImageStorage
import javax.inject.Inject

class DeleteImageUseCase @Inject constructor(
    private val imageStorage: ImageStorage,
) {
    suspend operator fun invoke(image: GeneratedImage) {
        imageStorage.deleteImage(image)
    }
}
