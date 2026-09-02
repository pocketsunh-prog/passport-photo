package com.passportphoto.app.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.passportphoto.app.camera.CameraViewModel
import com.passportphoto.app.camera.PassportValidator

/**
 * Screen for validating and saving local image files.
 * Users can select an image from their device, validate it against passport requirements,
 * and save it (with or without background).
 */
@Composable
fun LocalFileValidateScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val validationResult by viewModel.validationResult.collectAsState()
    val processedImage by viewModel.processedImage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFile by remember { mutableStateOf<java.io.File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedFile = null
            // Don't auto-validate, let user click Validate button
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
                text = "Validate Local File",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = {
                    selectedUri = null
                    selectedFile = null
                    viewModel.reset()
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }

        // Instructions
        Text(
            text = "Select a photo from your device to validate against passport requirements",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Photo preview area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(350.dp)
                .clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (isProcessing) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Validating...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else selectedUri != null?.let {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(selectedUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Selected photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } ?: run {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No photo selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Select file button
        Button(
            onClick = { filePickerLauncher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select Photo",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Validation results
        if (isProcessing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Validating...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else if (validationResult != null) {
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
        } else if (selectedUri != null) {
            Text(
                text = "Click 'Validate Photo' to check requirements",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Validate button (always show when photo is selected)
        if (selectedUri != null && !isProcessing) {
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        viewModel.validateUploadedImage(context, uri)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Validate Photo",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Save buttons (always show when photo is selected)
        if (selectedUri != null && !isProcessing) {
            // Save with transparent background
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        val saved = viewModel.saveUriWithTransparentBackground(context, uri)
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
                enabled = selectedUri != null
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save No Background",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save as JPEG
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        val saved = viewModel.saveUriToGallery(context, uri)
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
                enabled = selectedUri != null
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save to Gallery",
                    style = MaterialTheme.typography.titleLarge
                )
            }
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

            // Divider
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Gray.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            CheckItem("Resolution ≥ 1200×1600", result.resolutionOk)
            CheckItem("File size ≤ 5 MB", result.fileSizeOk)
            CheckItem("White background", result.backgroundOk)
            CheckItem("Good lighting", result.brightnessOk)
            CheckItem("Adequate contrast", result.contrastOk)
            CheckItem("Head size 32-36mm", result.headSizeOk)

            if (result.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.Gray.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Issues:",
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
