package ai.mlxdroid.imagelabarotory.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationRequest
import ai.mlxdroid.imagelabarotory.data.model.ImageGenerationResult
import ai.mlxdroid.imagelabarotory.data.repository.ImageRepository
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

    fun onGenerate() {
        val prompt = _uiState.value.prompt.trim()
        if (prompt.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

            val result = repository.generateImage(ImageGenerationRequest(prompt = prompt))

            when (result) {
                is ImageGenerationResult.Success -> {
                    val saved = imageStorage.saveImage(result.imageBytes, prompt)
                    _uiState.update { state ->
                        state.copy(
                            isGenerating = false,
                            images = listOf(saved) + state.images,
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
