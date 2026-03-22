package ai.mlxdroid.imagelabarotory.domain.usecase

import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import ai.mlxdroid.imagelabarotory.util.ImageStorage
import javax.inject.Inject

class LoadImagesUseCase @Inject constructor(
    private val imageStorage: ImageStorage,
) {
    suspend operator fun invoke(): List<GeneratedImage> {
        return imageStorage.loadImages()
    }
}
