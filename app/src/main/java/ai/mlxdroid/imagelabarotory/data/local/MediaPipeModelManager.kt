package ai.mlxdroid.imagelabarotory.data.local

import android.content.Context
import ai.mlxdroid.imagelabarotory.data.model.ModelDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val modelDir = File(context.filesDir, "mediapipe_sd_model")

    private val _downloadState = MutableStateFlow<ModelDownloadState>(
        if (isModelReady()) ModelDownloadState.Ready else ModelDownloadState.NotDownloaded
    )
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    fun isModelReady(): Boolean {
        return modelDir.exists() && modelDir.isDirectory && (modelDir.listFiles()?.isNotEmpty() == true)
    }

    fun getModelDirectoryPath(): String {
        return modelDir.absolutePath
    }

    suspend fun downloadModel() {
        if (isModelReady()) {
            _downloadState.value = ModelDownloadState.Ready
            return
        }

        _downloadState.value = ModelDownloadState.Downloading(0f)

        withContext(Dispatchers.IO) {
            try {
                // Clean up any partial downloads
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                }
                modelDir.mkdirs()

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(600, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(MODEL_DOWNLOAD_URL)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    _downloadState.value = ModelDownloadState.Error(
                        "Download failed: HTTP ${response.code}"
                    )
                    return@withContext
                }

                val body = response.body ?: run {
                    _downloadState.value = ModelDownloadState.Error("Empty response from server")
                    return@withContext
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()

                // If the URL points to a ZIP file, extract it
                if (MODEL_DOWNLOAD_URL.endsWith(".zip", ignoreCase = true)) {
                    extractZip(inputStream, modelDir, contentLength)
                } else {
                    // Direct file download (single model file)
                    val tempFile = File(modelDir, "model.tmp")
                    downloadToFile(inputStream, tempFile, contentLength)
                }

                if (isModelReady()) {
                    _downloadState.value = ModelDownloadState.Ready
                } else {
                    _downloadState.value = ModelDownloadState.Error(
                        "Download completed but model files are missing"
                    )
                }
            } catch (e: Exception) {
                _downloadState.value = ModelDownloadState.Error(
                    "Download failed: ${e.message}"
                )
                // Clean up partial download
                modelDir.deleteRecursively()
            }
        }
    }

    private fun extractZip(
        inputStream: java.io.InputStream,
        targetDir: File,
        totalBytes: Long,
    ) {
        var bytesRead = 0L
        val buffer = ByteArray(8192)

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                            bytesRead += len
                            if (totalBytes > 0) {
                                _downloadState.value = ModelDownloadState.Downloading(
                                    (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun downloadToFile(
        inputStream: java.io.InputStream,
        file: File,
        totalBytes: Long,
    ) {
        var bytesRead = 0L
        val buffer = ByteArray(8192)

        FileOutputStream(file).use { fos ->
            var len: Int
            while (inputStream.read(buffer).also { len = it } > 0) {
                fos.write(buffer, 0, len)
                bytesRead += len
                if (totalBytes > 0) {
                    _downloadState.value = ModelDownloadState.Downloading(
                        (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                    )
                }
            }
        }
    }

    fun deleteModel() {
        modelDir.deleteRecursively()
        _downloadState.value = ModelDownloadState.NotDownloaded
    }

    companion object {
        // TODO: Replace with your hosted pre-converted SD 1.5 model ZIP URL
        // Convert the model using MediaPipe's convert.py script, then ZIP and host the output
        const val MODEL_DOWNLOAD_URL = "https://example.com/mediapipe-sd15-model.zip"
    }
}
