package ai.mlxdroid.imagelabarotory.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.mlxdroid.imagelabarotory.domain.usecase.DeleteImageUseCase
import ai.mlxdroid.imagelabarotory.domain.usecase.GenerateImageUseCase
import ai.mlxdroid.imagelabarotory.domain.usecase.LoadImagesUseCase
import ai.mlxdroid.imagelabarotory.domain.usecase.ShareImageUseCase
import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val generateImageUseCase: GenerateImageUseCase,
    private val loadImagesUseCase: LoadImagesUseCase,
    private val deleteImageUseCase: DeleteImageUseCase,
    private val shareImageUseCase: ShareImageUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadImages()
    }

    private fun loadImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val images = loadImagesUseCase()
            _uiState.update { it.copy(images = images) }
        }
    }

    fun onPromptChanged(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun onShowGenerateSheet() {
        _uiState.update { it.copy(showGenerateSheet = true, errorMessage = null) }
    }

    fun onDismissGenerateSheet() {
        _uiState.update { it.copy(showGenerateSheet = false) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Generation settings
    fun onSizePresetChanged(preset: ImageSizePreset) {
        _uiState.update { it.copy(sizePreset = preset) }
    }

    fun onInferenceStepsChanged(steps: Int) {
        _uiState.update { it.copy(numInferenceSteps = steps) }
    }

    fun onGuidanceScaleChanged(scale: Float) {
        _uiState.update { it.copy(guidanceScale = scale) }
    }

    fun onNegativePromptChanged(text: String) {
        _uiState.update { it.copy(negativePrompt = text) }
    }

    fun onSeedChanged(seed: String) {
        if (seed.isEmpty() || seed.all { it.isDigit() }) {
            _uiState.update { it.copy(seed = seed) }
        }
    }

    fun onToggleAdvancedSettings() {
        _uiState.update { it.copy(showAdvancedSettings = !it.showAdvancedSettings) }
    }

    // Full-screen image viewer
    fun onImageSelected(image: GeneratedImage) {
        _uiState.update { it.copy(selectedImage = image) }
    }

    fun onDismissImageViewer() {
        _uiState.update { it.copy(selectedImage = null) }
    }

    fun onShareImage(image: GeneratedImage) {
        shareImageUseCase(image)
    }

    fun onGenerate() {
        val state = _uiState.value
        val prompt = state.prompt.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

            val result = generateImageUseCase(
                prompt = prompt,
                sizePreset = state.sizePreset,
                numInferenceSteps = state.numInferenceSteps,
                guidanceScale = state.guidanceScale,
                negativePrompt = state.negativePrompt.ifBlank { null },
                seed = state.seed.toLongOrNull(),
            )

            result.fold(
                onSuccess = { saved ->
                    _uiState.update { s ->
                        s.copy(
                            isGenerating = false,
                            images = listOf(saved) + s.images,
                            prompt = "",
                            showGenerateSheet = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            errorMessage = error.message ?: "Unknown error",
                        )
                    }
                },
            )
        }
    }

    fun onDeleteImage(imageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val image = _uiState.value.images.find { it.id == imageId } ?: return@launch
            deleteImageUseCase(image)
            _uiState.update { state ->
                state.copy(images = state.images.filter { it.id != imageId })
            }
        }
    }
}
