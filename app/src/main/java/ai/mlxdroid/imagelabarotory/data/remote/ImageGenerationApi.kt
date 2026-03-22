package ai.mlxdroid.imagelabarotory.data.remote

import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult

interface ImageGenerationApi {
    val provider: ApiProvider
    suspend fun generateImage(request: ImageGenerationRequest): ImageGenerationResult
}
