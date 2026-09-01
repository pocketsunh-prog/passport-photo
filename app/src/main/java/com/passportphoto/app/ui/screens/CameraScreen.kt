package com.passportphoto.app.ui.screens

import android.content.Context
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.passportphoto.app.camera.CameraManager
import com.passportphoto.app.camera.CameraViewModel
import com.passportphoto.app.ui.components.FaceGuideOverlay
import com.passportphoto.app.ui.theme.AccentGreen
import com.passportphoto.app.ui.theme.AccentRed
import com.passportphoto.app.ui.theme.OverlayGreen
import com.passportphoto.app.ui.theme.OverlayRed
import com.passportphoto.app.ui.theme.OverlayYellow

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onPhotoTaken: (java.io.File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisResult by viewModel.analysisResult.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val currentNotice by viewModel.currentNotice.collectAsState()
    val currentFacing by viewModel.currentFacing.collectAsState()
    val isCameraReady by viewModel.isCameraReady.collectAsState()
    val captureError by viewModel.captureError.collectAsState()

    // Initialize camera before creating PreviewView
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Start camera after preview view is created
    DisposableEffect(previewView) {
        viewModel.startCamera(lifecycleOwner, previewView)
        onDispose {
            // Cleanup handled by ViewModel.onCleared()
        }
    }

    // Show capture error as toast
    LaunchedEffect(captureError) {
        captureError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearCaptureError()
        }
    }

    // Navigate to preview when photo is captured
    val capturedFile by viewModel.capturedFile.collectAsState()
    LaunchedEffect(capturedFile) {
        capturedFile?.let { file ->
            onPhotoTaken(file)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Face guide overlay
        FaceGuideOverlay(
            isCompliant = analysisResult.isCompliant,
            headPosition = analysisResult.headPosition,
            headSizeRatio = analysisResult.headSizeRatio,
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Camera facing indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (currentFacing == CameraManager.CameraFacing.FRONT)
                        Icons.Default.FrontHand
                    else
                        Icons.Default.CameraRear,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (currentFacing == CameraManager.CameraFacing.FRONT) "Front" else "Back",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Camera ready indicator
            if (isCameraReady) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Camera Ready",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Notice banner
            NoticeBanner(
                notice = currentNotice,
                isCompliant = analysisResult.isCompliant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Capture and switch camera buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch camera button
                IconButton(
                    onClick = { viewModel.switchCamera() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    enabled = isCameraReady
                ) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = if (isCameraReady) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Capture button
                Button(
                    onClick = {
                        viewModel.capturePhoto(context)
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .border(
                            width = 4.dp,
                            color = if (analysisResult.isCompliant) OverlayGreen else OverlayYellow,
                            shape = CircleShape
                        ),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (analysisResult.isCompliant) {
                            AccentGreen.copy(alpha = 0.9f)
                        } else {
                            Color.White.copy(alpha = 0.8f)
                        }
                    ),
                    enabled = !isCapturing && isCameraReady
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Capture",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                }

                // Spacer to balance layout
                Spacer(modifier = Modifier.size(56.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    !isCameraReady -> "Initializing camera..."
                    isCapturing -> "Capturing..."
                    analysisResult.isCompliant -> "Ready to capture!"
                    else -> "Adjust position"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !isCameraReady -> Color.White.copy(alpha = 0.7f)
                    analysisResult.isCompliant -> OverlayGreen
                    else -> Color.White
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NoticeBanner(
    notice: CameraManager.PhotoNotice,
    isCompliant: Boolean
) {
    val (message, color, icon) = when (notice) {
        CameraManager.PhotoNotice.NO_FACE -> Triple(
            "No face detected. Position your face in the frame.",
            AccentRed,
            Icons.Default.Error
        )
        CameraManager.PhotoNotice.MULTIPLE_FACES -> Triple(
            "Multiple faces detected. Only one person in frame.",
            AccentRed,
            Icons.Default.Error
        )
        CameraManager.PhotoNotice.NOT_CENTERED -> Triple(
            "Center your face in the oval guide.",
            OverlayYellow,
            Icons.Default.Warning
        )
        CameraManager.PhotoNotice.TOO_FAR -> Triple(
            "Move closer to the camera.",
            OverlayYellow,
            Icons.Default.Warning
        )
        CameraManager.PhotoNotice.TOO_CLOSE -> Triple(
            "Move back from the camera.",
            OverlayYellow,
            Icons.Default.Warning
        )
        CameraManager.PhotoNotice.NOT_FRONTAL -> Triple(
            "Look straight at the camera. Neutral expression.",
            OverlayYellow,
            Icons.Default.Warning
        )
        CameraManager.PhotoNotice.TILTED -> Triple(
            "Keep your head straight. Don't tilt.",
            OverlayYellow,
            Icons.Default.Warning
        )
        CameraManager.PhotoNotice.GLASSES_DETECTED -> Triple(
            "Remove glasses for the photo.",
            AccentRed,
            Icons.Default.Error
        )
        CameraManager.PhotoNotice.SHADOWS_DETECTED -> Triple(
            "Avoid shadows on face and background.",
            OverlayYellow,
            Icons.Default.Warning
        )
        CameraManager.PhotoNotice.GOOD_POSITION -> Triple(
            "✓ Good position! Hold steady.",
            AccentGreen,
            Icons.Default.CheckCircle
        )
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "notice_color"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = animatedColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
