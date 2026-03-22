package ai.mlxdroid.imagelabarotory.data.repository

import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.remote.ImageGenerationApi
import javax.inject.Inject

class ImageRepositoryImpl @Inject constructor(
    private val apis: Map<ApiProvider, @JvmSuppressWildcards ImageGenerationApi>,
) : ImageRepository {

    override suspend fun generateImage(
        request: ImageGenerationRequest,
        provider: ApiProvider,
    ): ImageGenerationResult {
        val api = apis[provider]
            ?: return ImageGenerationResult.Error("Provider ${provider.displayName} is not available")
        return api.generateImage(request)
    }
}
