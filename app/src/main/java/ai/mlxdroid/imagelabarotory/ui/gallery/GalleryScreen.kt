package ai.mlxdroid.imagelabarotory.ui.gallery

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import ai.mlxdroid.imagelabarotory.ui.gallery.components.EmptyState
import ai.mlxdroid.imagelabarotory.ui.gallery.components.FullScreenImageViewer
import ai.mlxdroid.imagelabarotory.ui.gallery.components.GenerateSheet
import ai.mlxdroid.imagelabarotory.ui.gallery.components.ImageGrid
import ai.mlxdroid.imagelabarotory.util.ImageStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    imageStorage: ImageStorage,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

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
                    imageStorage = imageStorage,
                    onImageClick = viewModel::onImageSelected,
                )
            }
        }

        if (uiState.showGenerateSheet) {
            GenerateSheet(
                prompt = uiState.prompt,
                isGenerating = uiState.isGenerating,
                errorMessage = uiState.errorMessage,
                sizePreset = uiState.sizePreset,
                numInferenceSteps = uiState.numInferenceSteps,
                guidanceScale = uiState.guidanceScale,
                negativePrompt = uiState.negativePrompt,
                seed = uiState.seed,
                showAdvancedSettings = uiState.showAdvancedSettings,
                onPromptChanged = viewModel::onPromptChanged,
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
            val imageFile = imageStorage.getImageFile(image)
            FullScreenImageViewer(
                imageFile = imageFile,
                prompt = image.prompt,
                onDismiss = viewModel::onDismissImageViewer,
                onShare = {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        imageFile,
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                },
            )
        }
    }
}
