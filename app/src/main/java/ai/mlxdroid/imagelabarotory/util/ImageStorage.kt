package ai.mlxdroid.imagelabarotory.util

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedImage(
    val id: String,
    val prompt: String,
    val fileName: String,
    val createdAt: Long,
)

@Singleton
class ImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val imagesDir = File(context.filesDir, "generated").apply { mkdirs() }
    private val metadataFile = File(imagesDir, "metadata.json")

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, GeneratedImage::class.java)
    private val adapter = moshi.adapter<List<GeneratedImage>>(listType)

    fun saveImage(imageBytes: ByteArray, prompt: String): GeneratedImage {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.png"
        val file = File(imagesDir, fileName)
        file.writeBytes(imageBytes)

        val image = GeneratedImage(
            id = id,
            prompt = prompt,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
        )

        val existing = loadMetadata().toMutableList()
        existing.add(0, image)
        saveMetadata(existing)

        return image
    }

    fun loadImages(): List<GeneratedImage> {
        return loadMetadata()
    }

    fun getImageFile(image: GeneratedImage): File {
        return File(imagesDir, image.fileName)
    }

    fun deleteImage(image: GeneratedImage) {
        File(imagesDir, image.fileName).delete()
        val existing = loadMetadata().toMutableList()
        existing.removeAll { it.id == image.id }
        saveMetadata(existing)
    }

    private fun loadMetadata(): List<GeneratedImage> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            adapter.fromJson(metadataFile.readText()) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMetadata(images: List<GeneratedImage>) {
        metadataFile.writeText(adapter.toJson(images))
    }
}
