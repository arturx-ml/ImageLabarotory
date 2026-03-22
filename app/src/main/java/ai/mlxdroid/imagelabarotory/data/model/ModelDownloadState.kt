package ai.mlxdroid.imagelabarotory.data.model

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState
    data class Downloading(val progress: Float) : ModelDownloadState
    data class Paused(val progress: Float) : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}
