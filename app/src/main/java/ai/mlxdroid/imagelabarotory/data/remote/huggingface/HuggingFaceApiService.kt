package ai.mlxdroid.imagelabarotory.data.remote.huggingface

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface HuggingFaceApiService {

    @POST("models/black-forest-labs/FLUX.1-dev")
    suspend fun generateImage(
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
