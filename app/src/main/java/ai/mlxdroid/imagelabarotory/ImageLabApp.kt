package ai.mlxdroid.imagelabarotory

import ai.mlxdroid.imagelabarotory.ui.screens.HomeScreen
import ai.mlxdroid.imagelabarotory.ui.screens.ImageToImageScreen
import ai.mlxdroid.imagelabarotory.ui.screens.TextToImageScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController


@Composable
fun ImageLabApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToTextToImage = {
                    navController.navigate("text_to_image")
                },
                onNavigateToImageToImage = {
                    navController.navigate("image_to_image")
                }
            )
        }

        composable("text_to_image") {
            TextToImageScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("image_to_image") {
            ImageToImageScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}