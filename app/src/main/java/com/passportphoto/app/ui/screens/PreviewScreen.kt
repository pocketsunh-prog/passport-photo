package com.passportphoto.app.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.passportphoto.app.camera.CameraViewModel
import com.passportphoto.app.camera.PassportValidator
import java.io.File

@Composable
fun PreviewScreen(
    photoFile: File?,
    photoUri: Uri?,
    viewModel: CameraViewModel,
    onRetake: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val validationResult by viewModel.validationResult.collectAsState()
    val processedImage by viewModel.processedImage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var hasProcessed by remember { mutableStateOf(false) }

    // Process the image when screen loads
    LaunchedEffect(photoFile, photoUri) {
        if (!hasProcessed) {
            hasProcessed = true
            when {
                photoFile != null -> viewModel.processCapturedImage(context, photoFile)
                photoUri != null -> viewModel.processUploadedImage(context, photoUri)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = "Review Photo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(onClick = onRetake) {
                Icon(Icons.Default.Refresh, contentDescription = "Retake")
            }
        }

        // Photo preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            when {
                isProcessing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing image...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                processedImage != null -> {
                    val displayFile = processedImage!!.file
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(displayFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Passport photo preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                photoFile != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                photoUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Validation results
        if (validationResult != null) {
            ValidationResultCard(validationResult!!)

            Spacer(modifier = Modifier.height(16.dp))

            if (validationResult!!.isValid) {
                Text(
                    text = "✓ Photo meets passport requirements!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                Text(
                    text = "⚠ Photo needs adjustments",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        // Save with transparent background button
        Button(
            onClick = {
                val fileToSave = processedImage?.file ?: photoFile
                fileToSave?.let { file ->
                    val saved = viewModel.saveToGalleryWithTransparentBackground(context, file)
                    if (saved) {
                        onSaved()
                    } else {
                        Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3)
            ),
            enabled = !isProcessing && (processedImage != null || photoFile != null)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save No Background",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save normal button
        Button(
            onClick = {
                val fileToSave = processedImage?.file ?: photoFile
                fileToSave?.let { file ->
                    val saved = viewModel.saveToGallery(context, file)
                    if (saved) {
                        onSaved()
                    } else {
                        Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            enabled = !isProcessing && (processedImage != null || photoFile != null)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save to Gallery",
                style = MaterialTheme.typography.titleLarge
            )
        }

        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retake / Re-upload")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ValidationResultCard(result: PassportValidator.ValidationResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Validation Results",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Photo info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Resolution", "${result.photoInfo.width} × ${result.photoInfo.height}")
                InfoItem("Size", formatFileSize(result.photoInfo.fileSizeBytes))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem("Head", "${(result.photoInfo.headSizePercent * 100).toInt()}% of height")
                InfoItem("DPI", "~${result.photoInfo.estimatedDpi}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            DividerLine()

            Spacer(modifier = Modifier.height(12.dp))

            // Check items
            CheckItem("Resolution ≥ 1200×1600", result.resolutionOk)
            CheckItem("File size ≤ 5 MB", result.fileSizeOk)
            CheckItem("White background", result.backgroundOk)
            CheckItem("Good lighting", result.brightnessOk)
            CheckItem("Adequate contrast", result.contrastOk)
            CheckItem("Head size 32-36mm", result.headSizeOk)

            if (result.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                DividerLine()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Issues to fix:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF44336)
                )

                result.issues.forEach { issue ->
                    Text(
                        text = "• ${issue.toDisplayString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF44336),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CheckItem(label: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (passed) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (passed) 0.8f else 0.6f
            )
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.Gray.copy(alpha = 0.2f))
    )
}

private fun PassportValidator.ValidationIssue.toDisplayString(): String = when (this) {
    PassportValidator.ValidationIssue.RESOLUTION_TOO_LOW -> "Image resolution is below 1200×1600 pixels"
    PassportValidator.ValidationIssue.FILE_TOO_LARGE -> "File size exceeds 5 MB"
    PassportValidator.ValidationIssue.BACKGROUND_NOT_WHITE -> "Background is not plain white"
    PassportValidator.ValidationIssue.TOO_DARK -> "Image is too dark"
    PassportValidator.ValidationIssue.TOO_BRIGHT -> "Image is too bright"
    PassportValidator.ValidationIssue.LOW_CONTRAST -> "Image has low contrast"
    PassportValidator.ValidationIssue.HEAD_TOO_SMALL -> "Head is too small in frame (need 32-36mm)"
    PassportValidator.ValidationIssue.HEAD_TOO_LARGE -> "Head is too large in frame"
    PassportValidator.ValidationIssue.NOT_PORTRAIT_ORIENTATION -> "Image should be in portrait orientation"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
