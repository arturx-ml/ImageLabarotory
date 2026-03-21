package ai.mlxdroid.imagelabarotory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ai.mlxdroid.imagelabarotory.ui.gallery.GalleryScreen
import ai.mlxdroid.imagelabarotory.ui.theme.ImageLabarotoryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageLabarotoryTheme {
                GalleryScreen()
            }
        }
    }
}
