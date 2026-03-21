package ai.mlxdroid.imagelabarotory.domain.usecase

import android.content.Context
import android.content.Intent
import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import ai.mlxdroid.imagelabarotory.util.ImageStorage
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ShareImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStorage: ImageStorage,
) {
    operator fun invoke(image: GeneratedImage) {
        val imageFile = imageStorage.getImageFile(image)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Image").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
