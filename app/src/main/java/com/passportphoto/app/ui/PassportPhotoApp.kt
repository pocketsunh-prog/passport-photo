package com.passportphoto.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.passportphoto.app.camera.CameraViewModel
import com.passportphoto.app.ui.screens.CameraScreen
import com.passportphoto.app.ui.screens.GalleryPickerScreen
import com.passportphoto.app.ui.screens.HomeScreen
import com.passportphoto.app.ui.screens.PermissionGate
import com.passportphoto.app.ui.screens.PreviewScreen
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main navigation for the Passport Photo app.
 */
@Composable
fun PassportPhotoApp() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel = remember { CameraViewModel() }

    var showSnackbar by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            // Home screen
            composable("home") {
                HomeScreen(
                    onTakePhoto = {
                        viewModel.reset()
                        navController.navigate("camera")
                    },
                    onUploadPhoto = {
                        viewModel.reset()
                        navController.navigate("gallery")
                    }
                )
            }

            // Camera screen (with permission gate)
            composable("camera") {
                PermissionGate(
                    onPermissionsGranted = { /* Permission granted, continue to camera */ }
                ) {
                    CameraScreen(
                        viewModel = viewModel,
                        onPhotoTaken = { file ->
                            val encodedPath = Uri.encode(file.absolutePath)
                            navController.navigate("preview/file/$encodedPath")
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            // Gallery picker screen (merged with validation and save)
            composable("gallery") {
                GalleryPickerScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onSaved = {
                        scope.launch {
                            showSnackbar = "Photo saved to gallery!"
                        }
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }

            // Preview screen (from camera capture)
            composable(
                route = "preview/file/{filePath}",
                arguments = listOf(navArgument("filePath") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: ""
                val filePath = Uri.decode(encodedPath)
                val file = remember(filePath) { File(filePath) }

                PreviewScreen(
                    photoFile = file,
                    photoUri = null,
                    viewModel = viewModel,
                    onRetake = {
                        viewModel.reset()
                        navController.popBackStack("home", inclusive = false)
                    },
                    onSaved = {
                        scope.launch {
                            showSnackbar = "Photo saved to gallery!"
                        }
                        navController.popBackStack("home", inclusive = false)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Preview screen (from gallery upload - legacy, now handled in GalleryPickerScreen)
            composable(
                route = "preview/uri/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("uri") ?: ""
                val uriString = Uri.decode(encodedUri)
                val uri = remember(uriString) { Uri.parse(uriString) }

                PreviewScreen(
                    photoFile = null,
                    photoUri = uri,
                    viewModel = viewModel,
                    onRetake = {
                        viewModel.reset()
                        navController.popBackStack("home", inclusive = false)
                    },
                    onSaved = {
                        scope.launch {
                            showSnackbar = "Photo saved to gallery!"
                        }
                        navController.popBackStack("home", inclusive = false)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    // Show snackbar messages
    LaunchedEffect(showSnackbar) {
        showSnackbar?.let { message ->
            snackbarHostState.showSnackbar(message)
            showSnackbar = null
        }
    }
}
