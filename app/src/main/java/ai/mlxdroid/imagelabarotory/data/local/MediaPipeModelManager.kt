package ai.mlxdroid.imagelabarotory.data.local

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val modelDir = File(context.filesDir, "mediapipe_sd_model")
    /** Tracks bytes already written — used for resume with HTTP Range header */
    private val bytesDownloaded = AtomicLong(0L)

    private val _downloadState = MutableStateFlow<ModelDownloadState>(
        if (isModelReady()) ModelDownloadState.Ready else ModelDownloadState.NotDownloaded
    )
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    // Download control flags
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    @Volatile private var activeCall: okhttp3.Call? = null

    /** Minimum total RAM in bytes required for on-device SD 1.5 (~8 GB) */
    private val deviceTotalRam: Long
    private val gpuRenderer: String

    /** Whether this device meets hardware requirements */
    val isDeviceSupported: Boolean

    /** Human-readable reason if device is not supported, null if supported */
    val unsupportedReason: String?

    init {
        // RAM check
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        deviceTotalRam = memInfo.totalMem
        val ramGb = "%.1f".format(deviceTotalRam / (1024.0 * 1024 * 1024))

        // GPU check — need a temporary EGL context to query GL_RENDERER
        gpuRenderer = queryGpuRenderer()
        val gpuSupported = isGpuSupported(gpuRenderer)
        val ramSupported = deviceTotalRam >= MIN_RAM_BYTES

        isDeviceSupported = ramSupported && gpuSupported
        unsupportedReason = when {
            !ramSupported && !gpuSupported -> "Device has $ramGb GB RAM (need 8+) and unsupported GPU ($gpuRenderer)"
            !ramSupported -> "Device has $ramGb GB RAM. On-device generation requires 8+ GB RAM."
            !gpuSupported -> "GPU not supported ($gpuRenderer). Requires Adreno 640+ or Mali-G78+."
            else -> null
        }

        Log.d(TAG, "Initialized — RAM=${ramGb} GB, GPU=$gpuRenderer, " +
            "supported=$isDeviceSupported, reason=$unsupportedReason")

        if (!isDeviceSupported) {
            _downloadState.value = ModelDownloadState.Error(
                unsupportedReason ?: "Device not supported"
            )
        }
    }

    fun isModelReady(): Boolean {
        val exists = modelDir.exists() && modelDir.isDirectory
        val fileCount = modelDir.listFiles()?.size ?: 0
        val ready = exists && fileCount > 0
        Log.d(TAG, "isModelReady=$ready (exists=$exists, files=$fileCount)")
        return ready
    }

    fun getModelDirectoryPath(): String {
        Log.d(TAG, "getModelDirectoryPath=${modelDir.absolutePath}")
        return modelDir.absolutePath
    }

    /**
     * Starts or resumes the model download.
     * ZIP downloads always restart from scratch (can't resume mid-ZIP).
     * The download respects [isPaused] and [isCancelled] flags.
     */
    suspend fun downloadModel() {
        if (!isDeviceSupported) {
            Log.w(TAG, "Download blocked — $unsupportedReason")
            _downloadState.value = ModelDownloadState.Error(
                unsupportedReason ?: "Device not supported"
            )
            return
        }

        if (isModelReady()) {
            Log.i(TAG, "Model already downloaded, skipping")
            _downloadState.value = ModelDownloadState.Ready
            return
        }

        // Reset control flags
        isPaused.set(false)
        isCancelled.set(false)
        bytesDownloaded.set(0L)

        Log.i(TAG, "Starting model download from $MODEL_DOWNLOAD_URL")
        _downloadState.value = ModelDownloadState.Downloading(0f)

        withContext(Dispatchers.IO) {
            try {
                // Clean up any partial downloads for ZIP (can't resume ZIP extraction)
                if (modelDir.exists()) {
                    Log.d(TAG, "Cleaning up partial download at ${modelDir.absolutePath}")
                    modelDir.deleteRecursively()
                }
                modelDir.mkdirs()

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(600, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = Request.Builder()
                    .url(MODEL_DOWNLOAD_URL)
                    .build()

                Log.d(TAG, "Executing HTTP request...")
                val call = client.newCall(request)
                activeCall = call
                val response = call.execute()
                Log.d(TAG, "HTTP response: code=${response.code}, message=${response.message}")

                if (!response.isSuccessful) {
                    val error = "Download failed: HTTP ${response.code}"
                    Log.e(TAG, error)
                    _downloadState.value = ModelDownloadState.Error(error)
                    return@withContext
                }

                val body = response.body ?: run {
                    Log.e(TAG, "Empty response body from server")
                    _downloadState.value = ModelDownloadState.Error("Empty response from server")
                    return@withContext
                }

                val contentLength = body.contentLength()
                Log.i(TAG, "Download started — contentLength=${formatBytes(contentLength)}")
                val inputStream = body.byteStream()

                if (MODEL_DOWNLOAD_URL.endsWith(".zip", ignoreCase = true)) {
                    Log.d(TAG, "Detected ZIP file, will download and extract")
                    extractZip(inputStream, modelDir, contentLength)
                } else {
                    Log.d(TAG, "Direct file download (non-ZIP)")
                    val tempFile = File(modelDir, "model.tmp")
                    downloadToFile(inputStream, tempFile, contentLength)
                }

                // Check final state based on control flags
                when {
                    isCancelled.get() -> {
                        Log.i(TAG, "Download was cancelled, cleaning up")
                        modelDir.deleteRecursively()
                        _downloadState.value = ModelDownloadState.NotDownloaded
                    }
                    isPaused.get() -> {
                        val progress = if (contentLength > 0) {
                            (bytesDownloaded.get().toFloat() / contentLength).coerceIn(0f, 1f)
                        } else 0f
                        Log.i(TAG, "Download paused at ${formatBytes(bytesDownloaded.get())}")
                        _downloadState.value = ModelDownloadState.Paused(progress)
                    }
                    isModelReady() -> {
                        val fileCount = modelDir.listFiles()?.size ?: 0
                        val totalSize = modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        Log.i(TAG, "Download complete — $fileCount files, total=${formatBytes(totalSize)}")
                        _downloadState.value = ModelDownloadState.Ready
                    }
                    else -> {
                        Log.e(TAG, "Download completed but model files are missing in ${modelDir.absolutePath}")
                        _downloadState.value = ModelDownloadState.Error(
                            "Download completed but model files are missing"
                        )
                    }
                }
            } catch (e: java.io.IOException) {
                if (isPaused.get()) {
                    val progress = bytesDownloaded.get().toFloat() / 1 // will be recalculated
                    Log.i(TAG, "Download interrupted by pause")
                    // State already set by pauseDownload()
                } else if (isCancelled.get()) {
                    Log.i(TAG, "Download interrupted by cancel")
                    modelDir.deleteRecursively()
                    _downloadState.value = ModelDownloadState.NotDownloaded
                } else {
                    Log.e(TAG, "Download failed with exception", e)
                    _downloadState.value = ModelDownloadState.Error(
                        "Download failed: ${e.message}"
                    )
                    modelDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed with exception", e)
                _downloadState.value = ModelDownloadState.Error(
                    "Download failed: ${e.message}"
                )
                modelDir.deleteRecursively()
            } finally {
                activeCall = null
            }
        }
    }

    /** Pause the current download. The service can later call [downloadModel] to resume. */
    fun pauseDownload() {
        Log.i(TAG, "Pause requested")
        isPaused.set(true)
        val progress = _downloadState.value.let {
            if (it is ModelDownloadState.Downloading) it.progress else 0f
        }
        _downloadState.value = ModelDownloadState.Paused(progress)
        activeCall?.cancel()
    }

    /** Cancel the current download and clean up partial files. */
    fun cancelDownload() {
        Log.i(TAG, "Cancel requested")
        isCancelled.set(true)
        activeCall?.cancel()
        // State will be set to NotDownloaded in the download coroutine's catch/finally
    }

    fun deleteModel() {
        Log.i(TAG, "Deleting model at ${modelDir.absolutePath}")
        modelDir.deleteRecursively()
        _downloadState.value = ModelDownloadState.NotDownloaded
        Log.d(TAG, "Model deleted")
    }

    private fun extractZip(
        inputStream: java.io.InputStream,
        targetDir: File,
        totalBytes: Long,
    ) {
        var bytesRead = 0L
        var entryCount = 0
        val buffer = ByteArray(8192)

        Log.d(TAG, "Starting ZIP extraction to ${targetDir.absolutePath}")

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Check control flags
                if (isPaused.get() || isCancelled.get()) {
                    Log.d(TAG, "ZIP extraction interrupted (paused=${isPaused.get()}, cancelled=${isCancelled.get()})")
                    break
                }

                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            if (isPaused.get() || isCancelled.get()) break
                            fos.write(buffer, 0, len)
                            bytesRead += len
                            bytesDownloaded.set(bytesRead)
                            if (totalBytes > 0) {
                                _downloadState.value = ModelDownloadState.Downloading(
                                    (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                    entryCount++
                    if (entryCount % 100 == 0) {
                        val progress = if (totalBytes > 0) {
                            "%.1f%%".format((bytesRead.toFloat() / totalBytes) * 100)
                        } else {
                            formatBytes(bytesRead)
                        }
                        Log.d(TAG, "  Extracted $entryCount files so far ($progress)")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        Log.i(TAG, "ZIP extraction done — $entryCount files, ${formatBytes(bytesRead)} read")
    }

    private fun downloadToFile(
        inputStream: java.io.InputStream,
        file: File,
        totalBytes: Long,
    ) {
        var bytesRead = 0L
        val buffer = ByteArray(8192)

        Log.d(TAG, "Downloading to file: ${file.absolutePath}")

        FileOutputStream(file).use { fos ->
            var len: Int
            while (inputStream.read(buffer).also { len = it } > 0) {
                if (isPaused.get() || isCancelled.get()) break
                fos.write(buffer, 0, len)
                bytesRead += len
                bytesDownloaded.set(bytesRead)
                if (totalBytes > 0) {
                    _downloadState.value = ModelDownloadState.Downloading(
                        (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                    )
                }
            }
        }

        Log.i(TAG, "File download: ${formatBytes(bytesRead)} written")
    }

    /**
     * Query the GPU renderer string using a temporary EGL context.
     * Returns the GL_RENDERER string or "unknown" if it can't be determined.
     */
    private fun queryGpuRenderer(): String {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "unknown"

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "unknown"

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            if (numConfigs[0] == 0) return "unknown"

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0]!!, surfaceAttribs, 0)

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)
            return GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query GPU renderer", e)
            return "unknown"
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
            }
        }
    }

    /**
     * Check if the GPU is powerful enough for SD 1.5 inference.
     * Known working: Adreno 640+, Mali-G78+, Mali-G720+
     */
    private fun isGpuSupported(renderer: String): Boolean {
        val r = renderer.lowercase()

        // Adreno GPUs — need 640 or higher
        val adrenoMatch = Regex("adreno.*?(\\d+)").find(r)
        if (adrenoMatch != null) {
            val model = adrenoMatch.groupValues[1].toIntOrNull() ?: 0
            return model >= 640
        }

        // Mali GPUs — need G78 or higher (G-series with number >= 78)
        val maliGMatch = Regex("mali-g(\\d+)").find(r)
        if (maliGMatch != null) {
            val model = maliGMatch.groupValues[1].toIntOrNull() ?: 0
            return model >= 78
        }

        // Unknown GPU — allow it and let it fail gracefully at runtime
        Log.w(TAG, "Unknown GPU: $renderer — allowing but may crash")
        return true
    }

    companion object {
        private const val TAG = "MediaPipeModelManager"
        private const val MIN_RAM_BYTES = 8L * 1024 * 1024 * 1024 // 8 GB
        const val MODEL_DOWNLOAD_URL = "https://github.com/arturx-ml/ImageLabarotory/releases/download/model-sd15-v1/mediapipe-sd15-model.zip"

        private fun formatBytes(bytes: Long): String = when {
            bytes < 0 -> "unknown"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
