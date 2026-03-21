package ai.mlxdroid.imagelabarotory.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.repository.ImageRepository
import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import ai.mlxdroid.imagelabarotory.util.ImageStorage
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
    private val repository: ImageRepository,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadImages()
    }

    private fun loadImages() {
        viewModelScope.launch(Dispatchers.IO) {
            val images = imageStorage.loadImages()
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
        // Only allow digits or empty
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

    fun onGenerate() {
        val state = _uiState.value
        val prompt = state.prompt.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

            val request = ImageGenerationRequest(
                prompt = prompt,
                width = state.sizePreset.width,
                height = state.sizePreset.height,
                numInferenceSteps = state.numInferenceSteps,
                guidanceScale = state.guidanceScale,
                negativePrompt = state.negativePrompt.ifBlank { null },
                seed = state.seed.toLongOrNull(),
            )

            val result = repository.generateImage(request)

            when (result) {
                is ImageGenerationResult.Success -> {
                    val saved = imageStorage.saveImage(result.imageBytes, prompt)
                    _uiState.update { s ->
                        s.copy(
                            isGenerating = false,
                            images = listOf(saved) + s.images,
                            prompt = "",
                            showGenerateSheet = false,
                        )
                    }
                }
                is ImageGenerationResult.Error -> {
                    _uiState.update { it.copy(isGenerating = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun onDeleteImage(imageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val image = _uiState.value.images.find { it.id == imageId } ?: return@launch
            imageStorage.deleteImage(image)
            _uiState.update { state ->
                state.copy(images = state.images.filter { it.id != imageId })
            }
        }
    }
}
