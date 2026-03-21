package ai.mlxdroid.imagelabarotory.ml.models

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

abstract class BaseGeneratorModel(
    modelFile: File,
    useGpu: Boolean = true
) : Closeable {

    abstract val inputSize: Int
    abstract val outputSize: Int

    abstract fun generate(
        input: Any,
        onProgress: ((Float) -> Unit)? = null
    ): Bitmap?

    override fun close() {}

    open fun isInitialized(): Boolean = true
}
