package ai.mlxdroid.imagelabarotory.di

import ai.mlxdroid.imagelabarotory.BuildConfig
import ai.mlxdroid.imagelabarotory.data.remote.ImageGenerationApi
import ai.mlxdroid.imagelabarotory.data.remote.huggingface.HuggingFaceApiImpl
import ai.mlxdroid.imagelabarotory.data.remote.huggingface.HuggingFaceApiService
import ai.mlxdroid.imagelabarotory.data.repository.ImageRepository
import ai.mlxdroid.imagelabarotory.data.repository.ImageRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.HF_API_KEY}")
                .build()
            chain.proceed(request)
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideHuggingFaceApiService(client: OkHttpClient): HuggingFaceApiService {
        return Retrofit.Builder()
            .baseUrl("https://router.huggingface.co/hf-inference/")
            .client(client)
            .build()
            .create(HuggingFaceApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideImageGenerationApi(
        huggingFaceApiImpl: HuggingFaceApiImpl,
    ): ImageGenerationApi = huggingFaceApiImpl

    @Provides
    @Singleton
    fun provideImageRepository(
        imageRepositoryImpl: ImageRepositoryImpl,
    ): ImageRepository = imageRepositoryImpl
}
