package com.passportphoto.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.passportphoto.app.camera.CameraManager

/**
 * Draws the passport photo face guide overlay on top of the camera preview.
 * Includes an oval face guide, head size indicators, and status coloring.
 * The face area is left completely transparent so the face is clearly visible.
 */
@Composable
fun FaceGuideOverlay(
    isCompliant: Boolean,
    headPosition: CameraManager.HeadPosition,
    headSizeRatio: Float,
    modifier: Modifier = Modifier
) {
    val guideColor by animateColorAsState(
        targetValue = when {
            isCompliant -> Color(0xFF4CAF50)
            headSizeRatio > 0f -> Color(0xFFFFC107)
            else -> Color(0xFFF44336)
        },
        animationSpec = tween(300),
        label = "guide_color"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate oval dimensions (passport photo face area)
            val ovalWidth = canvasWidth * 0.55f
            val ovalHeight = canvasHeight * 0.60f
            val ovalLeft = (canvasWidth - ovalWidth) / 2f
            val ovalTop = canvasHeight * 0.15f

            // Draw semi-transparent overlay in 4 rectangles around the oval
            // This leaves the oval area completely transparent to show the face
            val overlayColor = Color.Black.copy(alpha = 0.5f)

            // Top rectangle (above oval)
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, ovalTop)
            )

            // Bottom rectangle (below oval)
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, ovalTop + ovalHeight),
                size = Size(canvasWidth, canvasHeight - ovalTop - ovalHeight)
            )

            // Left rectangle (left of oval)
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, ovalTop),
                size = Size(ovalLeft, ovalHeight)
            )

            // Right rectangle (right of oval)
            drawRect(
                color = overlayColor,
                topLeft = Offset(ovalLeft + ovalWidth, ovalTop),
                size = Size(canvasWidth - ovalLeft - ovalWidth, ovalHeight)
            )

            // Draw the oval border (face guide)
            drawOval(
                color = guideColor,
                topLeft = Offset(ovalLeft, ovalTop),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw head size guide lines (top and bottom)
            val headGuideTop = ovalTop
            val headGuideBottom = ovalTop + ovalHeight
            val linePadding = 20.dp.toPx()

            // Top line
            drawLine(
                color = guideColor.copy(alpha = 0.6f),
                start = Offset(ovalLeft - linePadding, headGuideTop),
                end = Offset(ovalLeft + ovalWidth + linePadding, headGuideTop),
                strokeWidth = 1.5.dp.toPx()
            )

            // Bottom line
            drawLine(
                color = guideColor.copy(alpha = 0.6f),
                start = Offset(ovalLeft - linePadding, headGuideBottom),
                end = Offset(ovalLeft + ovalWidth + linePadding, headGuideBottom),
                strokeWidth = 1.5.dp.toPx()
            )

            // Draw horizontal center line
            drawLine(
                color = guideColor.copy(alpha = 0.25f),
                start = Offset(ovalLeft, ovalTop + ovalHeight / 2),
                end = Offset(ovalLeft + ovalWidth, ovalTop + ovalHeight / 2),
                strokeWidth = 1.dp.toPx()
            )

            // Draw vertical center line
            drawLine(
                color = guideColor.copy(alpha = 0.25f),
                start = Offset(ovalLeft + ovalWidth / 2, ovalTop),
                end = Offset(ovalLeft + ovalWidth / 2, ovalTop + ovalHeight),
                strokeWidth = 1.dp.toPx()
            )

            // Draw corner markers
            val markerSize = 20.dp.toPx()
            val markerWidth = 3.dp.toPx()

            // Top-left
            drawLine(guideColor, Offset(ovalLeft, ovalTop), Offset(ovalLeft + markerSize, ovalTop), markerWidth)
            drawLine(guideColor, Offset(ovalLeft, ovalTop), Offset(ovalLeft, ovalTop + markerSize), markerWidth)

            // Top-right
            drawLine(guideColor, Offset(ovalLeft + ovalWidth, ovalTop), Offset(ovalLeft + ovalWidth - markerSize, ovalTop), markerWidth)
            drawLine(guideColor, Offset(ovalLeft + ovalWidth, ovalTop), Offset(ovalLeft + ovalWidth, ovalTop + markerSize), markerWidth)

            // Bottom-left
            drawLine(guideColor, Offset(ovalLeft, ovalTop + ovalHeight), Offset(ovalLeft + markerSize, ovalTop + ovalHeight), markerWidth)
            drawLine(guideColor, Offset(ovalLeft, ovalTop + ovalHeight), Offset(ovalLeft, ovalTop + ovalHeight - markerSize), markerWidth)

            // Bottom-right
            drawLine(guideColor, Offset(ovalLeft + ovalWidth, ovalTop + ovalHeight), Offset(ovalLeft + ovalWidth - markerSize, ovalTop + ovalHeight), markerWidth)
            drawLine(guideColor, Offset(ovalLeft + ovalWidth, ovalTop + ovalHeight), Offset(ovalLeft + ovalWidth, ovalTop + ovalHeight - markerSize), markerWidth)

            // If face is detected, draw a face-position indicator
            if (headSizeRatio > 0f) {
                val indicatorSize = 12.dp.toPx()
                val indicatorX = ovalLeft + ovalWidth * headPosition.x + (ovalWidth * headPosition.width / 2f)
                val indicatorY = ovalTop + ovalHeight * headPosition.y + (ovalHeight * headPosition.height / 2f)

                drawCircle(
                    color = guideColor.copy(alpha = 0.8f),
                    radius = indicatorSize,
                    center = Offset(
                        indicatorX.coerceIn(ovalLeft + indicatorSize, ovalLeft + ovalWidth - indicatorSize),
                        indicatorY.coerceIn(ovalTop + indicatorSize, ovalTop + ovalHeight - indicatorSize)
                    )
                )
            }
        }

        // Compliance indicator in the top-right corner
        if (isCompliant) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
            ) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    drawCircle(
                        color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                        radius = size.minDimension / 2
                    )
                    drawCircle(
                        color = Color.White,
                        radius = size.minDimension / 2,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}
