package ai.mlxdroid.imagelabarotory.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Specification for a single model file to download
 */
data class ModelFileSpec(
    val filename: String,
    val url: String,
    val size: Long
)

class ModelDownloader(private val context: Context) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }
    }

    // External app-specific storage - accessible via adb push without root
    // Tries getExternalFilesDir first, falls back to manually constructed path
    private val externalModelsDir: File? by lazy {
        val dir = try {
            context.getExternalFilesDir("models")
        } catch (e: Exception) {
            Log.w("ModelDownloader", "getExternalFilesDir failed: ${e.message}")
            null
        }
        // If getExternalFilesDir failed or returned null, build path manually
        dir ?: run {
            val extStorage = Environment.getExternalStorageDirectory()
            File(extStorage, "Android/data/${context.packageName}/files/models")
        }
    }

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun downloadModel(
        url: String,
        modelId: String,
        requireWifi: Boolean = true
    ): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Idle)

            if (requireWifi && !isWifiConnected()) {
                emit(DownloadState.Error("Wi-Fi connection required for downloading models"))
                return@flow
            }

            val modelFile = File(modelsDir, "$modelId.tflite")

            if (modelFile.exists()) {
                emit(DownloadState.Success(modelFile))
                return@flow
            }

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                return@flow
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            val tempFile = File(modelsDir, "$modelId.tflite.tmp")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            0
                        }

                        emit(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                    }
                }
            }

            if (tempFile.renameTo(modelFile)) {
                emit(DownloadState.Success(modelFile))
            } else {
                emit(DownloadState.Error("Failed to save model file"))
            }

        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download a set of model files (for multi-file models like Stable Diffusion)
     *
     * @param modelId Unique identifier for the model
     * @param files List of files to download
     * @param requireWifi Whether to require WiFi connection
     * @return Flow of download states with aggregated progress
     */
    fun downloadModelSet(
        modelId: String,
        files: List<ModelFileSpec>,
        requireWifi: Boolean = true
    ): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Idle)

            if (requireWifi && !isWifiConnected()) {
                emit(DownloadState.Error("Wi-Fi connection required for downloading models"))
                return@flow
            }

            // Create model directory
            val modelDir = File(modelsDir, modelId)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            // Check if all files already exist
            val allFilesExist = files.all { spec ->
                File(modelDir, spec.filename).exists()
            }

            if (allFilesExist) {
                emit(DownloadState.Success(modelDir))
                return@flow
            }

            // Calculate total size
            val totalSize = files.sumOf { it.size }
            var totalDownloaded = 0L

            // Download each file sequentially
            for ((index, fileSpec) in files.withIndex()) {
                val targetFile = File(modelDir, fileSpec.filename)

                // Skip if file already exists
                if (targetFile.exists()) {
                    totalDownloaded += targetFile.length()
                    val progress = ((totalDownloaded * 100) / totalSize).toInt()
                    emit(DownloadState.Downloading(progress, totalDownloaded, totalSize))
                    continue
                }

                val connection = URL(fileSpec.url).openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    emit(DownloadState.Error("Server returned HTTP ${connection.responseCode} for ${fileSpec.filename}"))
                    return@flow
                }

                val tempFile = File(modelDir, "${fileSpec.filename}.tmp")

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead

                            val progress = ((totalDownloaded * 100) / totalSize).toInt()
                            emit(DownloadState.Downloading(progress, totalDownloaded, totalSize))
                        }
                    }
                }

                if (!tempFile.renameTo(targetFile)) {
                    emit(DownloadState.Error("Failed to save ${fileSpec.filename}"))
                    return@flow
                }
            }

            emit(DownloadState.Success(modelDir))

        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get model file or directory
     *
     * For single-file models, returns the .tflite file
     * For multi-file models (like SD), returns the directory containing model files
     */
    fun getModelFile(modelId: String): File? {
        val requiredFiles = listOf("text_encoder_quant.ort", "unet_lcm_int8.onnx", "decoder_quant.ort")

        fun File.hasAllModelFiles() = requiredFiles.all { File(this, it).exists() }

        // Check internal multi-file model directory
        val internalDir = File(modelsDir, modelId)
        Log.d("ModelDownloader", "Checking internal: ${internalDir.absolutePath} exists=${internalDir.exists()}")
        if (internalDir.exists() && internalDir.isDirectory && internalDir.hasAllModelFiles()) {
            return internalDir
        }

        // Check external app-specific storage (adb-pushable location)
        val externalDir = externalModelsDir?.let { File(it, modelId) }
        Log.d("ModelDownloader", "Checking external: ${externalDir?.absolutePath} exists=${externalDir?.exists()}")
        if (externalDir != null && externalDir.exists() && externalDir.isDirectory && externalDir.hasAllModelFiles()) {
            return externalDir
        }

        // Fall back to single-file model (internal only)
        val modelFile = File(modelsDir, "$modelId.tflite")
        return if (modelFile.exists()) modelFile else null
    }

    /**
     * Check if a model is fully downloaded
     *
     * For multi-file models, checks that all required files exist
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return getModelFile(modelId)?.exists() == true
    }

    /**
     * Get the list of model files for a multi-file model
     */
    fun getModelFiles(modelId: String): List<File>? {
        val modelDir = File(modelsDir, modelId)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return null
        }

        val files = listOf(
            File(modelDir, "text_encoder.tflite"),
            File(modelDir, "diffusion_model.tflite"),
            File(modelDir, "decoder.tflite")
        )

        return if (files.all { it.exists() }) files else null
    }

    fun deleteModel(modelId: String): Boolean {
        // Try to delete multi-file model directory first
        val modelDir = File(modelsDir, modelId)
        if (modelDir.exists() && modelDir.isDirectory) {
            return modelDir.deleteRecursively()
        }

        // Fall back to single-file model
        val modelFile = File(modelsDir, "$modelId.tflite")
        return if (modelFile.exists()) {
            modelFile.delete()
        } else {
            false
        }
    }

    fun getModelSize(modelId: String): Long {
        val modelPath = getModelFile(modelId) ?: return 0L

        return if (modelPath.isDirectory) {
            // Sum sizes of all files in directory
            modelPath.listFiles()?.sumOf { it.length() } ?: 0L
        } else {
            modelPath.length()
        }
    }

    fun getAllDownloadedModels(): List<File> {
        return modelsDir.listFiles { file ->
            file.extension == "tflite"
        }?.toList() ?: emptyList()
    }

    fun getTotalModelsSize(): Long {
        return getAllDownloadedModels().sumOf { it.length() }
    }

    fun clearAllModels(): Boolean {
        return try {
            getAllDownloadedModels().forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }
}

fun Long.toReadableSize(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        kb >= 1 -> "%.2f KB".format(kb)
        else -> "$this B"
    }
}
