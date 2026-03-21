package ai.mlxdroid.imagelabarotory.ml.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.graphics.Bitmap
import android.util.Log
import java.io.Closeable
import java.io.File
import java.util.EnumSet

/**
 * VAE Decoder model wrapper — ONNX Runtime backend
 *
 * Converts latent tensor to RGB image via the anthrapper quantized decoder
 * (decoder_quant.ort, INT8, ~48 MB). Compatible with both SD v1.4/1.5 and LCM
 * since all share the same VAE architecture.
 *
 * Input:  FloatArray latent [1, 4, 64, 64] → size 16384
 * Output: Bitmap 512×512 RGB
 */
class DecoderModel(
    private val modelFile: File,
    private val useGpu: Boolean = true
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    companion object {
        private const val TAG = "DecoderModel"
        const val LATENT_C = 4
        const val LATENT_H = 64
        const val LATENT_W = 64
        const val OUT_H    = 512
        const val OUT_W    = 512
    }

    init {
        Log.d(TAG, "Initializing decoder from ${modelFile.absolutePath}")
        session = createSession()
        logSessionInfo()
    }

    private fun createSession(): OrtSession {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
        }
        if (useGpu) {
            try {
                opts.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
                Log.d(TAG, "NNAPI EP enabled")
            } catch (t: Throwable) {
                Log.w(TAG, "NNAPI unavailable, using CPU: ${t.message}")
            }
        }
        return env.createSession(modelFile.absolutePath, opts)
    }

    private fun logSessionInfo() {
        try {
            Log.d(TAG, "Inputs:  ${session.inputNames.toList()}")
            Log.d(TAG, "Outputs: ${session.outputNames.toList()}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not log session info: ${e.message}")
        }
    }

    /**
     * Decode latent tensor to a 512×512 Bitmap.
     *
     * @param latent FloatArray of size 4*64*64 = 16384 (NCHW layout)
     */
    fun decode(latent: FloatArray): Bitmap {
        val expectedSize = LATENT_C * LATENT_H * LATENT_W
        require(latent.size == expectedSize) {
            "Latent must be size $expectedSize, got ${latent.size}"
        }

        val startTime = System.currentTimeMillis()

        val inputName = session.inputNames.first()

        // Reshape flat FloatArray → [1, 4, 64, 64] for ORT
        val latentNchw = Array(1) { Array(LATENT_C) { c ->
            Array(LATENT_H) { h ->
                FloatArray(LATENT_W) { w ->
                    latent[c * LATENT_H * LATENT_W + h * LATENT_W + w]
                }
            }
        }}
        val inputTensor = OnnxTensor.createTensor(env, latentNchw)

        val result = session.run(mapOf(inputName to inputTensor))
        inputTensor.close()

        Log.d(TAG, "Decoding in ${System.currentTimeMillis() - startTime}ms")

        // Output shape: [1, 3, 512, 512] NCHW, values in [-1, 1]
        val outputName = session.outputNames.first()
        @Suppress("UNCHECKED_CAST")
        val raw = result.get(outputName).get().value as Array<Array<Array<FloatArray>>>
        result.close()

        return nchwToBitmap(raw[0])
    }

    private fun nchwToBitmap(nchw: Array<Array<FloatArray>>): Bitmap {
        val bitmap = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(OUT_W * OUT_H)
        val r = nchw[0]; val g = nchw[1]; val b = nchw[2]
        for (h in 0 until OUT_H) {
            for (w in 0 until OUT_W) {
                val ri = denorm(r[h][w])
                val gi = denorm(g[h][w])
                val bi = denorm(b[h][w])
                pixels[h * OUT_W + w] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        bitmap.setPixels(pixels, 0, OUT_W, 0, 0, OUT_W, OUT_H)
        return bitmap
    }

    /** Convert [-1, 1] model output to [0, 255] */
    private fun denorm(v: Float): Int =
        (((v.coerceIn(-1f, 1f) + 1f) / 2f) * 255f).toInt().coerceIn(0, 255)

    fun isInitialized(): Boolean = true

    override fun close() {
        Log.d(TAG, "Closing decoder")
        session.close()
        env.close()
    }
}
