package ai.mlxdroid.imagelabarotory.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.mlxdroid.imagelabarotory.util.GeneratedImage
import java.io.File

@Composable
fun ImageGrid(
    images: List<GeneratedImage>,
    getImageFile: (GeneratedImage) -> File,
    modifier: Modifier = Modifier,
    onImageClick: (GeneratedImage) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(images, key = { it.id }) { image ->
            ImageCard(
                imageFile = getImageFile(image),
                prompt = image.prompt,
                onClick = { onImageClick(image) },
            )
        }
    }
}
