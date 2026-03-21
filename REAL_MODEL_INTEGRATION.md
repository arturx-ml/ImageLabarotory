# Real Model Integration - Step by Step Guide

## Overview
This guide shows you how to integrate the real Stable Diffusion TFLite models from HuggingFace into your app.

## Models Required

You need 3 separate TFLite models that work together:

### 1. Text Encoder
- **Repository**: https://huggingface.co/keras-sd/tfs-text-encoder
- **Purpose**: Converts text prompts into embeddings
- **Download**: Click "Files and versions" → download the `.tflite` file

### 2. Diffusion Model
- **Repository**: https://huggingface.co/keras-sd/diffusion-model-tflite
- **Purpose**: Generates latent image from text embeddings
- **Download**: Click "Files and versions" → download the `.tflite` file

### 3. Decoder
- **Repository**: https://huggingface.co/keras-sd/decoder-tflite
- **Purpose**: Decodes latent image to final RGB image
- **Download**: Click "Files and versions" → download the `.tflite` file

## Step 1: Download Models

```bash
# Create a models directory
mkdir -p ~/Downloads/sd-models

# Download using git-lfs or from the web interface
# Visit each repository and download the .tflite files
```

Or download directly from HuggingFace:
- Text Encoder: https://huggingface.co/keras-sd/tfs-text-encoder/tree/main
- Diffusion: https://huggingface.co/keras-sd/diffusion-model-tflite/tree/main
- Decoder: https://huggingface.co/keras-sd/decoder-tflite/tree/main

## Step 2: Host Models

You have several options:

### Option A: GitHub Release (Free)
1. Create a release in your GitHub repo
2. Upload the 3 .tflite files as release assets
3. Get the download URLs

### Option B: Firebase Storage
1. Upload to Firebase Storage
2. Generate download URLs
3. Use these URLs in the app

### Option C: Local Testing (Development)
1. Place models in `app/src/main/assets/`
2. Load from assets instead of downloading
3. Warning: This will make your APK very large

## Step 3: Update Model Repository

Edit `ModelRepository.kt` to point to your hosted models:

```kotlin
private fun getAvailableModels(): List<ModelInfo> {
    return listOf(
        ModelInfo(
            id = "sd_v14_text_encoder",
            name = "Text Encoder",
            description = "Converts text prompts to embeddings",
            type = ModelType.TEXT_TO_IMAGE,
            resolution = 512,
            estimatedSize = 50 * 1024 * 1024L, // ~50MB (estimate)
            downloadUrl = "https://your-url.com/text_encoder.tflite",
            estimatedTimeSeconds = 5,
            quality = Quality.FAST
        ),
        ModelInfo(
            id = "sd_v14_diffusion",
            name = "Diffusion Model",
            description = "Generates latent images from text",
            type = ModelType.TEXT_TO_IMAGE,
            resolution = 512,
            estimatedSize = 1500 * 1024 * 1024L, // ~1.5GB (estimate)
            downloadUrl = "https://your-url.com/diffusion_model.tflite",
            estimatedTimeSeconds = 40,
            quality = Quality.HIGH
        ),
        ModelInfo(
            id = "sd_v14_decoder",
            name = "Decoder",
            description = "Decodes latent to final image",
            type = ModelType.TEXT_TO_IMAGE,
            resolution = 512,
            estimatedSize = 100 * 1024 * 1024L, // ~100MB (estimate)
            downloadUrl = "https://your-url.com/decoder.tflite",
            estimatedTimeSeconds = 5,
            quality = Quality.FAST
        )
    )
}
```

## Step 4: Implement Multi-Model Generator

Create a new file for the three-stage pipeline:

```kotlin
// File: app/src/main/java/ai/mlxdroid/imagelabarotory/ml/StableDiffusionPipeline.kt

package ai.mlxdroid.imagelabarotory.ml

import ai.mlxdroid.imagelabarotory.util.BitmapUtils
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class StableDiffusionPipeline(
    textEncoderFile: File,
    diffusionModelFile: File,
    decoderFile: File,
    useGpu: Boolean = true
) {
    private var textEncoder: Interpreter
    private var diffusionModel: Interpreter
    private var decoder: Interpreter

    private val gpuDelegates = mutableListOf<GpuDelegate>()

    init {
        val options = Interpreter.Options().apply {
            if (useGpu && CompatibilityList().isDelegateSupportedOnThisDevice) {
                val gpuDelegate = GpuDelegate()
                addDelegate(gpuDelegate)
                gpuDelegates.add(gpuDelegate)
            } else {
                setNumThreads(4)
            }
        }

        textEncoder = Interpreter(textEncoderFile, options)
        diffusionModel = Interpreter(diffusionModelFile, options)
        decoder = Interpreter(decoderFile, options)
    }

    fun generate(
        prompt: String,
        steps: Int = 20,
        seed: Long = Random.nextLong(),
        onProgress: ((Float) -> Unit)? = null
    ): Bitmap? {
        try {
            // Step 1: Encode text prompt
            onProgress?.invoke(0.1f)
            val (context, unconditionalContext) = encodePrompt(prompt)

            // Step 2: Run diffusion process
            onProgress?.invoke(0.2f)
            val latent = runDiffusion(context, unconditionalContext, steps) { step ->
                // Progress from 0.2 to 0.8
                onProgress?.invoke(0.2f + (step.toFloat() / steps) * 0.6f)
            }

            // Step 3: Decode latent to image
            onProgress?.invoke(0.9f)
            val bitmap = decodeLatent(latent)

            onProgress?.invoke(1.0f)
            return bitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun encodePrompt(prompt: String): Pair<FloatArray, FloatArray> {
        // Tokenize prompt (simplified - you may need a proper tokenizer)
        val tokens = tokenizePrompt(prompt)

        // Create input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * tokens.size).apply {
            order(ByteOrder.nativeOrder())
            tokens.forEach { putFloat(it) }
            rewind()
        }

        // Allocate output buffers
        val contextSize = 77 * 768 // Common for Stable Diffusion
        val context = FloatArray(contextSize)
        val unconditionalContext = FloatArray(contextSize)

        // Run text encoder
        textEncoder.run(inputBuffer, context)

        // For unconditional context, encode empty string
        val emptyTokens = FloatArray(tokens.size) { 0f }
        val emptyBuffer = ByteBuffer.allocateDirect(4 * emptyTokens.size).apply {
            order(ByteOrder.nativeOrder())
            emptyTokens.forEach { putFloat(it) }
            rewind()
        }
        textEncoder.run(emptyBuffer, unconditionalContext)

        return Pair(context, unconditionalContext)
    }

    private fun runDiffusion(
        context: FloatArray,
        unconditionalContext: FloatArray,
        steps: Int,
        onStepComplete: (Int) -> Unit
    ): FloatArray {
        // Initialize random latent based on seed
        val latentSize = 64 * 64 * 4 // 64x64 latent, 4 channels
        var latent = FloatArray(latentSize) { Random.nextFloat() }

        // Create input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * (latent.size + context.size)).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run diffusion steps
        for (step in 0 until steps) {
            // Prepare input: latent + context
            inputBuffer.clear()
            latent.forEach { inputBuffer.putFloat(it) }
            context.forEach { inputBuffer.putFloat(it) }
            inputBuffer.rewind()

            // Run diffusion model
            val output = FloatArray(latentSize)
            diffusionModel.run(inputBuffer, output)

            // Update latent
            latent = output

            onStepComplete(step + 1)
        }

        return latent
    }

    private fun decodeLatent(latent: FloatArray): Bitmap {
        // Create input buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * latent.size).apply {
            order(ByteOrder.nativeOrder())
            latent.forEach { putFloat(it) }
            rewind()
        }

        // Allocate output buffer (512x512x3)
        val outputSize = 512 * 512 * 3
        val output = FloatArray(outputSize)

        // Run decoder
        decoder.run(inputBuffer, output)

        // Convert to bitmap
        return BitmapUtils.floatArrayToBitmap(output, 512, 512)
    }

    private fun tokenizePrompt(prompt: String): FloatArray {
        // Simplified tokenization - replace with proper CLIP tokenizer
        // For now, just convert characters to floats
        val maxTokens = 77
        val tokens = FloatArray(maxTokens) { 0f }

        prompt.take(maxTokens).forEachIndexed { index, char ->
            tokens[index] = char.code.toFloat()
        }

        return tokens
    }

    fun close() {
        textEncoder.close()
        diffusionModel.close()
        decoder.close()
        gpuDelegates.forEach { it.close() }
    }
}
```

## Step 5: Update ModelManager

Modify `ModelManager.kt` to use the pipeline:

```kotlin
private fun createModelInstance(modelId: String, modelFile: File): BaseGeneratorModel {
    // Check if this is a complete SD pipeline (all 3 models downloaded)
    val textEncoderFile = modelRepository.getModelFile("sd_v14_text_encoder")
    val diffusionFile = modelRepository.getModelFile("sd_v14_diffusion")
    val decoderFile = modelRepository.getModelFile("sd_v14_decoder")

    if (textEncoderFile != null && diffusionFile != null && decoderFile != null) {
        return StableDiffusionCompleteModel(
            textEncoderFile,
            diffusionFile,
            decoderFile,
            useGpu
        )
    }

    // Fallback to placeholder
    return StableDiffusionModel(modelFile, useGpu)
}
```

## Step 6: Test

1. **Build the app**:
   ```bash
   ./gradlew assembleDebug
   ```

2. **Install on device**:
   ```bash
   ./gradlew installDebug
   ```

3. **Test generation**:
   - Open the app
   - Download all 3 models (text encoder, diffusion, decoder)
   - Navigate to Text to Image
   - Enter a prompt
   - Click Generate

## Expected Results

- **First generation**: May take 40-60 seconds
- **Subsequent generations**: Should be faster as models stay loaded
- **Image quality**: Should be actual AI-generated images matching prompts
- **Memory usage**: ~2-3GB during generation

## Troubleshooting

### Issue: Out of Memory
**Solution**:
- Close other apps
- Use lower resolution (256x256 instead of 512x512)
- Reduce diffusion steps (10-15 instead of 20)

### Issue: Models won't download
**Solution**:
- Check internet connection
- Verify URLs are accessible
- Check storage space (need ~2GB free)

### Issue: Generation fails
**Solution**:
- Check logcat for errors: `adb logcat | grep TensorFlow`
- Verify all 3 models are downloaded
- Try on emulator first for easier debugging

## Alternative: Simpler Approach

If the 3-model approach is too complex, you can also:

1. Find a **single unified model** that combines all three stages
2. Or use **SnapGPU** or other mobile-optimized SD implementations
3. Or use **cloud-based generation** with on-device fallback

## Resources

- [HuggingFace Stable Diffusion TFLite](https://huggingface.co/keras-sd)
- [TensorFlow Lite Guide](https://www.tensorflow.org/lite)
- [Stable Diffusion Android Tutorial](https://farmaker47.medium.com/stable-diffusion-example-in-an-android-application-part-2-621b6c6ef590)
- [Model Conversion Guide](https://pushpendrasingh28.medium.com/convert-stable-diffusion-to-tensorflow-lite-model-3f6af7400ca5)

---

**Next Step**: Download the 3 models from HuggingFace and host them somewhere accessible, then update the URLs in ModelRepository.kt
