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
 * Routes:
 * - home: Home screen with Take Photo / Upload Photo options
 * - camera: Camera screen with real-time face detection (requires permission)
 * - gallery: Gallery picker for uploading existing photos
 * - preview: Preview and validation screen
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
                        navController.navigate("camera")
                    },
                    onUploadPhoto = {
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

            // Gallery picker screen
            composable("gallery") {
                GalleryPickerScreen(
                    onPhotoSelected = { uri ->
                        val encodedUri = Uri.encode(uri.toString())
                        navController.navigate("preview/uri/$encodedUri")
                    },
                    onBack = {
                        navController.popBackStack()
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
                        navController.popBackStack()
                        navController.navigate("camera")
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

            // Preview screen (from gallery upload)
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
                        navController.popBackStack()
                        navController.navigate("gallery")
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
