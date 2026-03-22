package ai.mlxdroid.imagelabarotory.data.remote.huggingface

import ai.mlxdroid.imagelabarotory.data.model.ApiProvider
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.remote.ImageGenerationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

class HuggingFaceApiImpl @Inject constructor(
    private val apiService: HuggingFaceApiService,
) : ImageGenerationApi {

    override val provider: ApiProvider = ApiProvider.HUGGING_FACE

    override suspend fun generateImage(request: ImageGenerationRequest): ImageGenerationResult {
        return try {
            val json = JSONObject().apply {
                put("inputs", request.prompt)
                put("parameters", JSONObject().apply {
                    put("width", request.width)
                    put("height", request.height)
                    put("num_inference_steps", request.numInferenceSteps)
                    put("guidance_scale", request.guidanceScale.toDouble())
                    if (!request.negativePrompt.isNullOrBlank()) {
                        put("negative_prompt", request.negativePrompt)
                    }
                    if (request.seed != null) {
                        put("seed", request.seed)
                    }
                })
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val url = "models/$MODEL_ID"
            val response = apiService.generateImage(url, body)

            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    ImageGenerationResult.Success(bytes)
                } else {
                    ImageGenerationResult.Error("Empty response from server")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val message = when (response.code()) {
                    401 -> "Invalid API key. Check your Hugging Face token."
                    429 -> "Rate limited. Please wait a moment and try again."
                    503 -> "Model is loading. Please try again in a few seconds."
                    else -> errorBody ?: "API error: ${response.code()}"
                }
                ImageGenerationResult.Error(message)
            }
        } catch (e: IOException) {
            ImageGenerationResult.Error("No internet connection. Please check your network.")
        } catch (e: Exception) {
            ImageGenerationResult.Error("Unexpected error: ${e.message}")
        }
    }

    companion object {
        const val MODEL_ID = "black-forest-labs/FLUX.1-schnell"
    }
}
