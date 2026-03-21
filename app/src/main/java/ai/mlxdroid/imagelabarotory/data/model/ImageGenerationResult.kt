package ai.mlxdroid.imagelabarotory.data.model

sealed interface ImageGenerationResult {
    data class Success(val imageBytes: ByteArray) : ImageGenerationResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return imageBytes.contentEquals(other.imageBytes)
        }

        override fun hashCode(): Int = imageBytes.contentHashCode()
    }

    data class Error(val message: String) : ImageGenerationResult
}
