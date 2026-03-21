# Model Integration Quick Start Guide

## Current State
The app is **fully functional** with placeholder generation. All UI, navigation, and infrastructure is complete. You just need to integrate real TensorFlow Lite models.

## Where to Add Real Model Logic

### Location
**File:** `app/src/main/java/ai/mlxdroid/imagelabarotory/ml/ModelManager.kt`

**Class:** `StableDiffusionModel`

**Method:** `generate()` at line 56-63

### Current Placeholder Code
```kotlin
override fun generate(
    input: Any,
    onProgress: ((Float) -> Unit)?
): android.graphics.Bitmap? {
    validateInterpreter()

    // TODO: Replace with real model inference
    return null
}
```

### What to Implement

#### Step 1: Handle Input
```kotlin
override fun generate(
    input: Any,
    onProgress: ((Float) -> Unit)?
): android.graphics.Bitmap? {
    validateInterpreter()

    // Cast input to appropriate type
    val params = when (input) {
        is ai.mlxdroid.imagelabarotory.ml.TextToImageParams -> input
        is ai.mlxdroid.imagelabarotory.ml.ImageToImageParams -> input
        else -> throw IllegalArgumentException("Unsupported input type")
    }

    // Continue with inference...
}
```

#### Step 2: Prepare Inputs for Model
```kotlin
// For text-to-image
val textParams = params as TextToImageParams

// Encode prompt (you may need a separate text encoder model)
val promptEmbedding = encodeText(textParams.prompt)
val negativeEmbedding = encodeText(textParams.negativePrompt)

// Initialize random latent based on seed
val random = Random(textParams.seed)
val latent = initializeRandomLatent(random)
```

#### Step 3: Run Inference Loop
```kotlin
val steps = textParams.steps
val guidanceScale = textParams.guidanceScale

for (step in 0 until steps) {
    // Get timestep
    val t = getTimestep(step, steps)

    // Run UNet
    val noisePrediction = runUNet(latent, t, promptEmbedding)

    // Apply guidance
    val guidedPrediction = applyGuidance(
        noisePrediction,
        negativeEmbedding,
        guidanceScale
    )

    // Update latent
    latent = schedulerStep(latent, guidedPrediction, t)

    // Report progress
    onProgress?.invoke((step + 1).toFloat() / steps)
}
```

#### Step 4: Decode to Image
```kotlin
// Decode latent to pixel space
val outputArray = decodeLatent(latent)

// Convert to bitmap
return ai.mlxdroid.imagelabarotory.util.BitmapUtils.floatArrayToBitmap(
    outputArray,
    outputSize,
    outputSize
)
```

## Model Architecture Patterns

### Pattern 1: Single Monolithic Model
If your TFLite model includes everything (text encoder, UNet, decoder):

```kotlin
interpreter?.run {
    // Single inference call
    val inputs = arrayOf(promptTokens, seed, steps, guidanceScale)
    val outputs = HashMap<Int, Any>()
    outputs[0] = outputImageBuffer

    runForMultipleInputsOutputs(inputs, outputs)

    return bufferToBitmap(outputImageBuffer)
}
```

### Pattern 2: Separate Models
If you have separate models for each component:

```kotlin
// Load models
val textEncoder = Interpreter(textEncoderFile)
val unet = Interpreter(unetFile)
val decoder = Interpreter(decoderFile)

// 1. Encode text
val promptEmbedding = FloatArray(77 * 768)
textEncoder.run(promptTokens, promptEmbedding)

// 2. Diffusion loop
for (step in 0 until steps) {
    unet.run(arrayOf(latent, timestep, promptEmbedding), noisePrediction)
    latent = updateLatent(latent, noisePrediction)
}

// 3. Decode
decoder.run(latent, imageOutput)
```

## Example Models to Try

### 1. Pre-converted Mobile Models
Search on HuggingFace for:
- "stable-diffusion-v1-5-tflite"
- "stable-diffusion-mobile"
- "sd-turbo-tflite"

### 2. Convert Your Own
Using TensorFlow Lite Converter:

```python
import tensorflow as tf
from diffusers import StableDiffusionPipeline

# Load model
pipe = StableDiffusionPipeline.from_pretrained("runwayml/stable-diffusion-v1-5")

# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(pipe.unet)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]

tflite_model = converter.convert()

with open('sd_mobile.tflite', 'wb') as f:
    f.write(tflite_model)
```

## Testing Strategy

### 1. Start Simple
```kotlin
// First, just load the model successfully
override fun generate(...): Bitmap? {
    validateInterpreter()

    // Create a solid color bitmap to verify it works
    return Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.GREEN)
    }
}
```

### 2. Test Model Loading
```kotlin
override fun generate(...): Bitmap? {
    validateInterpreter()

    // Get input/output details
    val inputDetails = interpreter?.getInputTensor(0)
    val outputDetails = interpreter?.getOutputTensor(0)

    android.util.Log.d("Model", "Input shape: ${inputDetails?.shape()?.contentToString()}")
    android.util.Log.d("Model", "Output shape: ${outputDetails?.shape()?.contentToString()}")

    return null
}
```

### 3. Test Simple Inference
```kotlin
override fun generate(...): Bitmap? {
    validateInterpreter()

    // Create dummy input
    val input = FloatArray(1 * 77 * 768) // Example shape
    val output = FloatArray(1 * 512 * 512 * 3)

    interpreter?.run(input, output)

    // Convert to bitmap
    return BitmapUtils.floatArrayToBitmap(output, 512, 512)
}
```

### 4. Add Real Inputs
Replace dummy inputs with actual text embeddings and random noise.

## Common Issues & Solutions

### Issue: Out of Memory
**Solution:**
```kotlin
// Use lower precision
val options = Interpreter.Options().apply {
    setNumThreads(4)
    setUseNNAPI(false) // Try without NNAPI first
}

// Or reduce image size
override val inputSize: Int = 256 // Instead of 512
```

### Issue: Slow Inference
**Solution:**
```kotlin
// Enable GPU delegate
val gpuDelegate = GpuDelegate()
val options = Interpreter.Options().apply {
    addDelegate(gpuDelegate)
}
```

### Issue: Model Input Mismatch
**Solution:**
```kotlin
// Inspect model
val inputTensor = interpreter?.getInputTensor(0)
Log.d("Model", "Expected input: ${inputTensor?.shape()?.contentToString()}")
Log.d("Model", "Data type: ${inputTensor?.dataType()}")
```

## Debugging Checklist

- [ ] Model file loads successfully
- [ ] Input shapes match model expectations
- [ ] Output shapes are correct
- [ ] No crashes during inference
- [ ] Memory usage is acceptable
- [ ] Inference time is reasonable
- [ ] Output looks like an image (not random noise)
- [ ] Generated images match prompts

## Performance Targets

| Device Tier | Resolution | Target Time |
|-------------|------------|-------------|
| Low (4GB)   | 256x256    | 30-60s      |
| Mid (6GB)   | 384x384    | 25-45s      |
| High (8GB+) | 512x512    | 15-30s      |

## Example: Minimal Working Implementation

```kotlin
class StableDiffusionModel(
    modelFile: File,
    useGpu: Boolean = true
) : BaseGeneratorModel(modelFile, useGpu) {

    override val inputSize: Int = 512
    override val outputSize: Int = 512

    override fun generate(
        input: Any,
        onProgress: ((Float) -> Unit)?
    ): Bitmap? {
        validateInterpreter()

        val params = input as ai.mlxdroid.imagelabarotory.ml.TextToImageParams

        // Allocate buffers
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        val outputBuffer = ByteBuffer.allocateDirect(4 * outputSize * outputSize * 3)

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        // Fill with random data based on seed
        val random = java.util.Random(params.seed)
        val inputFloats = FloatArray(inputSize * inputSize * 3)
        for (i in inputFloats.indices) {
            inputFloats[i] = random.nextFloat()
        }
        inputFloats.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        // Run inference
        interpreter?.run(inputBuffer, outputBuffer)

        // Convert output to bitmap
        outputBuffer.rewind()
        val outputFloats = FloatArray(outputSize * outputSize * 3)
        outputBuffer.asFloatBuffer().get(outputFloats)

        return ai.mlxdroid.imagelabarotory.util.BitmapUtils.floatArrayToBitmap(
            outputFloats,
            outputSize,
            outputSize
        )
    }
}
```

## Next Steps

1. **Get a model**: Download or convert a TFLite Stable Diffusion model
2. **Update URLs**: Put real download URLs in `ModelRepository.kt`
3. **Implement generate()**: Replace placeholder in `StableDiffusionModel.generate()`
4. **Test on device**: Build and run the app
5. **Optimize**: Tune performance based on device testing

## Resources

- [TensorFlow Lite Guide](https://www.tensorflow.org/lite/guide)
- [TF Lite GPU Delegate](https://www.tensorflow.org/lite/performance/gpu)
- [Model Optimization](https://www.tensorflow.org/lite/performance/model_optimization)
- [HuggingFace Models](https://huggingface.co/models?library=tf-lite)

---

**Remember**: The hard part (UI, architecture, utilities) is done! Model integration is just filling in the `generate()` method.
