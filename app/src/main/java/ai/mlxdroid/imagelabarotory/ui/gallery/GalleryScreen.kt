package ai.mlxdroid.imagelabarotory.ui.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import ai.mlxdroid.imagelabarotory.ui.gallery.components.EmptyState
import ai.mlxdroid.imagelabarotory.ui.gallery.components.FullScreenImageViewer
import ai.mlxdroid.imagelabarotory.ui.gallery.components.GenerateSheet
import ai.mlxdroid.imagelabarotory.ui.gallery.components.ImageGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Image Laboratory") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::onShowGenerateSheet,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Generate image",
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.images.isEmpty() && !uiState.isGenerating) {
                EmptyState()
            } else {
                ImageGrid(
                    images = uiState.images,
                    getImageFile = viewModel::getImageFile,
                    onImageClick = viewModel::onImageSelected,
                )
            }
        }

        if (uiState.showGenerateSheet) {
            GenerateSheet(
                prompt = uiState.prompt,
                isGenerating = uiState.isGenerating,
                errorMessage = uiState.errorMessage,
                selectedProvider = uiState.selectedProvider,
                modelDownloadState = uiState.modelDownloadState,
                sizePreset = uiState.sizePreset,
                numInferenceSteps = uiState.numInferenceSteps,
                guidanceScale = uiState.guidanceScale,
                negativePrompt = uiState.negativePrompt,
                seed = uiState.seed,
                showAdvancedSettings = uiState.showAdvancedSettings,
                onPromptChanged = viewModel::onPromptChanged,
                onProviderChanged = viewModel::onProviderChanged,
                onDownloadModel = viewModel::onDownloadModel,
                onSizePresetChanged = viewModel::onSizePresetChanged,
                onInferenceStepsChanged = viewModel::onInferenceStepsChanged,
                onGuidanceScaleChanged = viewModel::onGuidanceScaleChanged,
                onNegativePromptChanged = viewModel::onNegativePromptChanged,
                onSeedChanged = viewModel::onSeedChanged,
                onToggleAdvancedSettings = viewModel::onToggleAdvancedSettings,
                onGenerate = viewModel::onGenerate,
                onDismiss = viewModel::onDismissGenerateSheet,
                onDismissError = viewModel::onDismissError,
            )
        }

        uiState.selectedImage?.let { image ->
            FullScreenImageViewer(
                imageFile = viewModel.getImageFile(image),
                prompt = image.prompt,
                onDismiss = viewModel::onDismissImageViewer,
                onShare = { viewModel.onShareImage(image) },
            )
        }
    }
}
