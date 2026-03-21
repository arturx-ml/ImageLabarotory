package ai.mlxdroid.imagelabarotory.ml.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import java.io.Closeable
import java.io.File
import java.util.EnumSet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * LCM UNet diffusion model wrapper — ONNX Runtime backend
 *
 * Implements Latent Consistency Model inference. Unlike standard DDIM which
 * requires 20-50 steps, LCM converges in 4-8 steps via consistency distillation.
 * No classifier-free guidance needed (guidance_scale ignored — LCM runs one
 * forward pass per step, halving computation vs standard SD).
 *
 * Model: unet_lcm_int8.ort (~860 MB INT8)
 * Input per step:
 *   - sample:                 [1, 4, 64, 64]  latent
 *   - timestep:               [1]             INT64
 *   - encoder_hidden_states:  [1, 77, 768]    text embeddings
 * Output per step:
 *   - out_sample:             [1, 4, 64, 64]  predicted denoised latent
 */
class DiffusionModel(
    private val modelFile: File,
    private val useGpu: Boolean = true
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    companion object {
        private const val TAG = "DiffusionModel"

        const val LATENT_C = 4
        const val LATENT_H = 64
        const val LATENT_W = 64

        const val DEFAULT_STEPS = 4
        // LCM uses w=guidance_scale-1 internally, typical range 1-2
        const val DEFAULT_W          = 8.0f
        const val TIMESTEP_COND_DIM  = 256

        // LCM timestep schedule — evenly spaced across [0, 999], 50 DDPM steps
        // These are the "consistency" timesteps the model was distilled on
        private val LCM_TIMESTEPS = intArrayOf(999, 759, 499, 259)

        // DDPM noise schedule (linear beta, 1000 steps)
        // alphas_cumprod[t] = prod(1 - beta_i) for i in 0..t
        private val ALPHAS_CUMPROD: FloatArray by lazy { computeAlphasCumprod() }

        private fun computeAlphasCumprod(): FloatArray {
            val betaStart = 0.00085f
            val betaEnd   = 0.012f
            val n = 1000
            val betas = FloatArray(n) { i ->
                val t = i.toFloat() / (n - 1)
                (betaStart + t * (betaEnd - betaStart))
            }
            val alphas = FloatArray(n) { i -> 1f - betas[i] }
            val ac = FloatArray(n)
            var prod = 1f
            for (i in 0 until n) { prod *= alphas[i]; ac[i] = prod }
            return ac
        }
    }

    init {
        Log.d(TAG, "Initializing LCM diffusion model from ${modelFile.absolutePath}")
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
     * Run LCM inference to produce a denoised latent.
     *
     * @param contextEmbeddings  FloatArray [77*768] from text encoder
     * @param seed               Random seed for reproducibility
     * @param steps              Number of LCM steps (1-8, default 4)
     * @param onProgress         Progress callback 0.0..1.0
     * @return FloatArray [4*64*64] denoised latent (NCHW flattened)
     */
    fun diffuse(
        contextEmbeddings: FloatArray,
        unconditionalEmbeddings: FloatArray,  // kept for API compat — not used in LCM
        seed: Long,
        steps: Int = DEFAULT_STEPS,
        guidanceScale: Float = DEFAULT_W,
        onProgress: ((Float) -> Unit)? = null
    ): FloatArray {
        val latentSize = LATENT_C * LATENT_H * LATENT_W
        val rng = Random(seed)

        // 1. Sample initial noise and scale by sigma_max
        val timesteps = LCM_TIMESTEPS.take(steps)
        val sigmaMax = sigmaFromTimestep(timesteps.first())
        var latent = FloatArray(latentSize) { rng.nextFloat() * 2f - 1f }
        // Scale noise: x_0 ~ N(0, sigma_max^2)
        for (i in latent.indices) latent[i] = latent[i] * sigmaMax

        Log.d(TAG, "LCM inference: $steps steps, seed=$seed")
        val totalStart = System.currentTimeMillis()

        for ((stepIdx, t) in timesteps.withIndex()) {
            val stepStart = System.currentTimeMillis()
            Log.d(TAG, "Step ${stepIdx + 1}/$steps — timestep $t")

            // 2. UNet forward pass
            val noisePred = runUNet(latent, t.toLong(), contextEmbeddings)

            // 3. LCM scheduler step
            latent = lcmStep(latent, noisePred, t, timesteps.getOrNull(stepIdx + 1))

            val stepMs = System.currentTimeMillis() - stepStart
            Log.d(TAG, "  Step done in ${stepMs}ms")
            onProgress?.invoke((stepIdx + 1).toFloat() / steps)
        }

        Log.d(TAG, "LCM total: ${System.currentTimeMillis() - totalStart}ms")
        return latent
    }

    /**
     * Single UNet forward pass.
     * Inputs: sample [1,4,64,64], timestep [1] INT64,
     *         encoder_hidden_states [1,77,768], timestep_cond [1,256]
     * Output: out_sample [1,4,64,64] → flat FloatArray
     */
    private fun runUNet(
        latent: FloatArray,
        timestep: Long,
        embeddings: FloatArray
    ): FloatArray {
        val inputNames = session.inputNames.toList()

        // sample [1, 4, 64, 64]
        val sampleNchw = flatToNchw4d(latent, 1, LATENT_C, LATENT_H, LATENT_W)
        val sampleTensor = OnnxTensor.createTensor(env, sampleNchw)

        // timestep [1] — INT64
        val tsTensor = OnnxTensor.createTensor(env, longArrayOf(timestep))

        // encoder_hidden_states [1, 77, 768]
        val embeds = flatToNchw3d(embeddings, 1, 77, 768)
        val embedTensor = OnnxTensor.createTensor(env, embeds)

        // timestep_cond [1, 256] — LCM guidance scale sinusoidal embedding
        // w = guidance_scale - 1 (DEFAULT_W = 8.0 → w = 7.0)
        val wEmbedding = getGuidanceScaleEmbedding(DEFAULT_W - 1f, TIMESTEP_COND_DIM)
        val wTensor = OnnxTensor.createTensor(env, Array(1) { wEmbedding })

        val inputMap = buildInputMap(inputNames, sampleTensor, tsTensor, embedTensor, wTensor)

        val result = session.run(inputMap)
        sampleTensor.close(); tsTensor.close(); embedTensor.close(); wTensor.close()

        val outputName = session.outputNames.first()
        @Suppress("UNCHECKED_CAST")
        val out = result.get(outputName).get().value as Array<Array<Array<FloatArray>>>
        result.close()

        return nchwToFlat(out[0])
    }

    /**
     * Sinusoidal embedding of the guidance scale w, matching the LCM training code.
     * get_guidance_scale_embedding(w, embedding_dim=256) from diffusers LCM pipeline.
     */
    private fun getGuidanceScaleEmbedding(w: Float, dim: Int): FloatArray {
        val half = dim / 2
        val freqs = FloatArray(half) { i ->
            val exp = i.toFloat() / (half - 1)
            (1.0 / Math.pow(10000.0, exp.toDouble())).toFloat()
        }
        val wScaled = w * 1000f
        val embed = FloatArray(dim)
        for (i in 0 until half) {
            embed[i]        = sin(wScaled * freqs[i])
            embed[i + half] = cos(wScaled * freqs[i])
        }
        return embed
    }

    /**
     * LCM consistency step.
     * Computes the denoised estimate and optionally re-adds noise for the next step.
     */
    private fun lcmStep(
        sample: FloatArray,
        modelOutput: FloatArray,
        currentT: Int,
        nextT: Int?
    ): FloatArray {
        val alphaT  = ALPHAS_CUMPROD[currentT]
        val sigmaT  = sqrt(1f - alphaT)
        val sqrtAT  = sqrt(alphaT)

        // Predicted denoised sample (x_0 estimate)
        val denoised = FloatArray(sample.size) { i ->
            (sample[i] - sigmaT * modelOutput[i]) / sqrtAT
        }
        denoised.clampInPlace(-1f, 1f)

        // If this is the last step, return denoised directly
        if (nextT == null) return denoised

        // Re-add noise for the next timestep
        val alphaNext = ALPHAS_CUMPROD[nextT]
        val sqrtANext = sqrt(alphaNext)
        val sigmaN    = sqrt(1f - alphaNext)

        return FloatArray(sample.size) { i ->
            sqrtANext * denoised[i] + sigmaN * modelOutput[i]
        }
    }

    /** sigma (noise level) at timestep t = sqrt((1 - alpha_t) / alpha_t) */
    private fun sigmaFromTimestep(t: Int): Float {
        val a = ALPHAS_CUMPROD[t]
        return sqrt((1f - a) / a)
    }

    /** Build the input name→tensor map, tolerating different export naming conventions */
    private fun buildInputMap(
        names: List<String>,
        sample: OnnxTensor,
        timestep: OnnxTensor,
        embeddings: OnnxTensor,
        timestepCond: OnnxTensor
    ): Map<String, OnnxTensor> {
        val map = mutableMapOf<String, OnnxTensor>()
        for (name in names) {
            when {
                "timestep_cond" in name                                     -> map[name] = timestepCond
                "sample"        in name                                     -> map[name] = sample
                "timestep"      in name || name == "t"                      -> map[name] = timestep
                "encoder"       in name || "hidden" in name || "context" in name -> map[name] = embeddings
                else -> {
                    Log.w(TAG, "Unknown input '$name' — skipping")
                }
            }
        }
        return map
    }

    // ── tensor layout helpers ──────────────────────────────────────────────

    private fun flatToNchw4d(flat: FloatArray, n: Int, c: Int, h: Int, w: Int)
            : Array<Array<Array<FloatArray>>> =
        Array(n) { Array(c) { ci -> Array(h) { hi ->
            FloatArray(w) { wi -> flat[ci * h * w + hi * w + wi] }
        }}}

    private fun flatToNchw3d(flat: FloatArray, n: Int, s: Int, d: Int)
            : Array<Array<FloatArray>> =
        Array(n) { Array(s) { si -> FloatArray(d) { di -> flat[si * d + di] } }}

    private fun nchwToFlat(nchw: Array<Array<FloatArray>>): FloatArray {
        val c = nchw.size; val h = nchw[0].size; val w = nchw[0][0].size
        return FloatArray(c * h * w) { i ->
            val ci = i / (h * w); val hi = (i % (h * w)) / w; val wi = i % w
            nchw[ci][hi][wi]
        }
    }

    private fun FloatArray.clampInPlace(min: Float, max: Float) {
        for (i in indices) this[i] = this[i].coerceIn(min, max)
    }

    fun isInitialized(): Boolean = true

    override fun close() {
        Log.d(TAG, "Closing LCM diffusion model")
        session.close()
        env.close()
    }
}
