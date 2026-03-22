package ai.mlxdroid.imagelabarotory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ai.mlxdroid.imagelabarotory.ui.gallery.GalleryScreen
import ai.mlxdroid.imagelabarotory.ui.theme.ImageLabarotoryTheme
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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

    private fun initLocalAi(){
        val engineConfig = EngineConfig(
            modelPath = "/path/to/your/model.litertlm", // Replace with your model path
            backend = Backend.GPU(), // Or Backend.NPU(nativeLibraryDir = "...")
            // Optional: Pick a writable dir. This can improve 2nd load time.
            // cacheDir = "/tmp/" or context.cacheDir.path (for Android)
        )

        val engine = Engine(engineConfig)
        engine.initialize()
        engine.close()
    }
}
