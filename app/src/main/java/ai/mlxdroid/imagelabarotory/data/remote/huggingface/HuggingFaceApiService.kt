package ai.mlxdroid.imagelabarotory.data.remote.huggingface

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface HuggingFaceApiService {

    @POST
    suspend fun generateImage(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
