package ai.mlxdroid.imagelabarotory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ai.mlxdroid.imagelabarotory.ui.gallery.GalleryScreen
import ai.mlxdroid.imagelabarotory.ui.theme.ImageLabarotoryTheme
import ai.mlxdroid.imagelabarotory.util.ImageStorage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageStorage: ImageStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageLabarotoryTheme {
                GalleryScreen(imageStorage = imageStorage)
            }
        }
    }
}