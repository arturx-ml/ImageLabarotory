package ai.mlxdroid.imagelabarotory.util

import android.graphics.Bitmap
import android.graphics.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BitmapUtils {

    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun centerCropBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetAspectRatio = targetWidth.toFloat() / targetHeight.toFloat()

        val scaledBitmap = if (aspectRatio > targetAspectRatio) {
            val scaledHeight = targetHeight
            val scaledWidth = (targetHeight * aspectRatio).toInt()
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            val scaledWidth = targetWidth
            val scaledHeight = (targetWidth / aspectRatio).toInt()
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        }

        val xOffset = (scaledBitmap.width - targetWidth) / 2
        val yOffset = (scaledBitmap.height - targetHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            xOffset,
            yOffset,
            targetWidth,
            targetHeight
        )

        if (scaledBitmap != bitmap && scaledBitmap != croppedBitmap) {
            scaledBitmap.recycle()
        }

        return croppedBitmap
    }

    fun normalizeBitmap(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(width * height * 3)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatArray[i * 3] = r
            floatArray[i * 3 + 1] = g
            floatArray[i * 3 + 2] = b
        }

        return floatArray
    }

    fun normalizeToRange(
        bitmap: Bitmap,
        mean: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f),
        std: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f)
    ): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(width * height * 3)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatArray[i * 3] = (r - mean[0]) / std[0]
            floatArray[i * 3 + 1] = (g - mean[1]) / std[1]
            floatArray[i * 3 + 2] = (b - mean[2]) / std[2]
        }

        return floatArray
    }

    fun bitmapToByteBuffer(
        bitmap: Bitmap,
        inputSize: Int = 512,
        mean: Float = 127.5f,
        std: Float = 127.5f
    ): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            byteBuffer.putFloat((r - mean) / std)
            byteBuffer.putFloat((g - mean) / std)
            byteBuffer.putFloat((b - mean) / std)
        }

        return byteBuffer
    }

    fun floatArrayToBitmap(floatArray: FloatArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            val r = (floatArray[i * 3].coerceIn(0f, 1f) * 255).toInt()
            val g = (floatArray[i * 3 + 1].coerceIn(0f, 1f) * 255).toInt()
            val b = (floatArray[i * 3 + 2].coerceIn(0f, 1f) * 255).toInt()

            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun denormalizeFloatArray(
        floatArray: FloatArray,
        width: Int,
        height: Int,
        mean: Float = 127.5f,
        std: Float = 127.5f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            val r = ((floatArray[i * 3] * std + mean).coerceIn(0f, 255f)).toInt()
            val g = ((floatArray[i * 3 + 1] * std + mean).coerceIn(0f, 255f)).toInt()
            val b = ((floatArray[i * 3 + 2] * std + mean).coerceIn(0f, 255f)).toInt()

            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun recycleBitmapSafely(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}
