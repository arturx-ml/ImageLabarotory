package ai.mlxdroid.imagelabarotory.data.repository

import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult

interface ImageRepository {
    suspend fun generateImage(
        request: ImageGenerationRequest,
        provider: ApiProvider,
    ): ImageGenerationResult
}
