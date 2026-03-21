package ai.mlxdroid.imagelabarotory.data.repository

import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.remote.ImageGenerationApi
import javax.inject.Inject

class ImageRepositoryImpl @Inject constructor(
    private val api: ImageGenerationApi,
) : ImageRepository {

    override suspend fun generateImage(request: ImageGenerationRequest): ImageGenerationResult {
        return api.generateImage(request)
    }
}
