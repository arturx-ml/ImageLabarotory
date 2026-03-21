package ai.mlxdroid.imagelabarotory.ml.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import java.io.Closeable
import java.io.File
import java.util.EnumSet

/**
 * Text Encoder model wrapper — ONNX Runtime backend
 *
 * Converts tokenized text (token IDs) into embeddings via the anthrapper
 * quantized CLIP text encoder (text_encoder_quant.ort, INT8, ~124 MB).
 *
 * Input:  LongArray token IDs [1, 77]
 * Output: FloatArray embeddings [1, 77, 768] → flattened to size 59136
 */
class TextEncoderModel(
    private val modelFile: File,
    private val useGpu: Boolean = true
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    companion object {
        private const val TAG = "TextEncoderModel"
        const val MAX_SEQ_LENGTH = 77
        const val EMBEDDING_DIM = 768
    }

    init {
        Log.d(TAG, "Initializing text encoder from ${modelFile.absolutePath}")
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
            val inputs  = session.inputNames.toList()
            val outputs = session.outputNames.toList()
            Log.d(TAG, "Inputs:  $inputs")
            Log.d(TAG, "Outputs: $outputs")
        } catch (e: Exception) {
            Log.w(TAG, "Could not log session info: ${e.message}")
        }
    }

    /**
     * Encode token IDs to embeddings.
     *
     * @param tokenIds IntArray of length 77
     * @return FloatArray of size 77 * 768 = 59136
     */
    fun encode(tokenIds: IntArray): FloatArray {
        require(tokenIds.size == MAX_SEQ_LENGTH) {
            "Token IDs must be length $MAX_SEQ_LENGTH, got ${tokenIds.size}"
        }

        val startTime = System.currentTimeMillis()

        // ORT expects INT64 input — anthrapper text encoder uses input_ids: [1, 77] INT64
        val inputName = session.inputNames.first()
        val ids64 = Array(1) { LongArray(MAX_SEQ_LENGTH) { i -> tokenIds[i].toLong() } }
        val inputTensor = OnnxTensor.createTensor(env, ids64)

        val result = session.run(mapOf(inputName to inputTensor))
        inputTensor.close()

        // Output: last_hidden_state [1, 77, 768]
        val outputName = session.outputNames.first()
        @Suppress("UNCHECKED_CAST")
        val raw = result.get(outputName).get().value as Array<Array<FloatArray>>
        result.close()

        Log.d(TAG, "Text encoding in ${System.currentTimeMillis() - startTime}ms")

        // Flatten [1, 77, 768] → FloatArray(59136)
        val flat = FloatArray(MAX_SEQ_LENGTH * EMBEDDING_DIM)
        var idx = 0
        for (token in raw[0]) for (v in token) flat[idx++] = v
        return flat
    }

    fun isInitialized(): Boolean = true

    override fun close() {
        Log.d(TAG, "Closing text encoder")
        session.close()
        env.close()
    }
}
