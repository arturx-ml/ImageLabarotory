package ai.mlxdroid.imagelabarotory

import ai.mlxdroid.imagelabarotory.ui.screens.HomeScreen
import ai.mlxdroid.imagelabarotory.ui.screens.ImageToImageScreen
import ai.mlxdroid.imagelabarotory.ui.screens.TextToImageScreen
import ai.mlxdroid.imagelabarotory.ui.theme.ImageLabarotoryTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageLabarotoryTheme {
                ImageLabApp()
            }
        }
    }
}
