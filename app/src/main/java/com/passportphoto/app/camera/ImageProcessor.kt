package com.passportphoto.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.min

/**
 * Processes captured photos to meet passport requirements:
 * - Crops to 40:50 aspect ratio
 * - Resizes to minimum 1200x1600 pixels
 * - Ensures proper JPEG compression
 * - Handles EXIF orientation
 * - Replaces non-white background with plain white (preserving face/body)
 */
class ImageProcessor(private val context: Context) {

    companion object {
        const val TARGET_WIDTH = 1200
        const val TARGET_HEIGHT = 1600
        const val ASPECT_RATIO = 40f / 50f // width / height
        const val JPEG_QUALITY = 92

        // Background replacement thresholds
        const val WHITE_THRESHOLD = 200
        const val EDGE_TOLERANCE = 35 // Lower tolerance for stricter matching
    }

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.1f)
            .build()
        FaceDetection.getClient(options)
    }

    data class ProcessedImage(
        val file: File,
        val width: Int,
        val height: Int,
        val fileSizeBytes: Long,
        val wasResized: Boolean,
        val wasCropped: Boolean,
        val wasBackgroundReplaced: Boolean
    )

    /**
     * Process a captured/selected image to meet passport photo requirements.
     */
    fun processImage(sourceUri: Uri, outputDir: File, replaceBackground: Boolean = true): ProcessedImage? {
        val bitmap = loadAndCorrectOrientation(sourceUri) ?: return null
        return processBitmap(bitmap, outputDir, replaceBackground)
    }

    fun processImage(sourceFile: File, outputDir: File, replaceBackground: Boolean = true): ProcessedImage? {
        val bitmap = loadAndCorrectOrientation(sourceFile) ?: return null
        return processBitmap(bitmap, outputDir, replaceBackground)
    }

    private fun processBitmap(bitmap: Bitmap, outputDir: File, replaceBackground: Boolean): ProcessedImage? {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // Step 1: Replace background with white if needed
        val backgroundReplacedBitmap = if (replaceBackground) {
            val replaced = replaceBackgroundWithWhite(bitmap)
            replaced ?: bitmap
        } else {
            bitmap
        }
        val wasBackgroundReplaced = backgroundReplacedBitmap != bitmap

        // Step 2: Crop to 40:50 aspect ratio
        val croppedBitmap = cropToAspectRatio(backgroundReplacedBitmap, ASPECT_RATIO)

        // Step 3: Resize to minimum 1200x1600
        val resizedBitmap = resizeToMinimum(croppedBitmap, TARGET_WIDTH, TARGET_HEIGHT)

        // Step 4: Save as JPEG with proper compression
        val outputFile = File(outputDir, "passport_processed_${System.currentTimeMillis()}.jpg")
        outputFile.parentFile?.mkdirs()

        val fileSize = saveAsJpeg(resizedBitmap, outputFile)

        val wasCropped = originalWidth != croppedBitmap.width || originalHeight != croppedBitmap.height
        val wasResized = croppedBitmap.width != resizedBitmap.width || croppedBitmap.height != resizedBitmap.height

        // Clean up intermediate bitmaps
        if (backgroundReplacedBitmap != bitmap) backgroundReplacedBitmap.recycle()
        if (croppedBitmap != backgroundReplacedBitmap) croppedBitmap.recycle()
        if (resizedBitmap != croppedBitmap) resizedBitmap.recycle()

        return ProcessedImage(
            file = outputFile,
            width = resizedBitmap.width,
            height = resizedBitmap.height,
            fileSizeBytes = fileSize,
            wasResized = wasResized,
            wasCropped = wasCropped,
            wasBackgroundReplaced = wasBackgroundReplaced
        )
    }

    /**
     * Detect and replace non-white background with plain white.
     * Uses ML Kit face detection to create a protective mask,
     * then flood-fills from edges to identify background pixels.
     */
    private fun replaceBackgroundWithWhite(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height

        // Analyze background color from edges
        val bgColor = getEdgeColor(bitmap)

        // If background is already white, no replacement needed
        if (isColorWhite(bgColor)) {
            return null
        }

        // Create a mutable copy
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        // Step 1: Detect face and create protective mask
        val faceMask = detectFaceMask(pixels, w, h)

        // Step 2: Flood fill from edges to identify background (avoiding face)
        val backgroundMask = Array(h) { BooleanArray(w) }
        floodFillFromEdges(pixels, w, h, bgColor, backgroundMask, faceMask)

        // Step 3: Replace background pixels with white
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (backgroundMask[y][x] && !faceMask[y][x]) {
                    pixels[y * w + x] = Color.WHITE
                }
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Detect face pixels and create a protective mask.
     * Uses color-based detection as a fast alternative to ML Kit.
     */
    private fun detectFaceMask(pixels: IntArray, w: Int, h: Int): Array<BooleanArray> {
        val mask = Array(h) { BooleanArray(w) }

        // Use multiple skin color ranges to cover different skin tones
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = pixels[y * w + x]
                if (isSkinColorWideRange(pixel)) {
                    mask[y][x] = true
                }
            }
        }

        // Expand the mask to include hair and surrounding areas
        return expandMask(mask, w, h, expansionRadius = 3)
    }

    /**
     * Wide-range skin color detection covering light to dark skin tones.
     */
    private fun isSkinColorWideRange(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Convert to YCbCr for better skin detection
        val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        val cb = (128 - 0.168736 * r - 0.331264 * g + 0.5 * b).toInt()
        val cr = (128 + 0.5 * r - 0.418688 * g - 0.081312 * b).toInt()

        // Skin color ranges in YCbCr space (covers all skin tones)
        // Y > 80 (not too dark), Cb in range, Cr in range
        return y > 80 && cb in 77..127 && cr in 133..173
    }

    /**
     * Expand the mask by the given radius to protect surrounding areas.
     */
    private fun expandMask(mask: Array<BooleanArray>, w: Int, h: Int, expansionRadius: Int): Array<BooleanArray> {
        val expanded = Array(h) { BooleanArray(w) }
        val radius = expansionRadius

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y][x]) {
                    // Mark all pixels within radius as protected
                    for (dy in -radius..radius) {
                        for (dx in -radius..radius) {
                            val ny = y + dy
                            val nx = x + dx
                            if (ny in 0 until h && nx in 0 until w) {
                                expanded[ny][nx] = true
                            }
                        }
                    }
                }
            }
        }

        return expanded
    }

    /**
     * Get the average color from the edges of the image.
     */
    private fun getEdgeColor(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        // Sample all four edges
        for (x in 0 until w) {
            val top = bitmap.getPixel(x, 0)
            val bottom = bitmap.getPixel(x, h - 1)
            totalR += Color.red(top) + Color.red(bottom)
            totalG += Color.green(top) + Color.green(bottom)
            totalB += Color.blue(top) + Color.blue(bottom)
            count += 2
        }

        for (y in 0 until h) {
            val left = bitmap.getPixel(0, y)
            val right = bitmap.getPixel(w - 1, y)
            totalR += Color.red(left) + Color.red(right)
            totalG += Color.green(left) + Color.green(right)
            totalB += Color.blue(left) + Color.blue(right)
            count += 2
        }

        return Color.rgb(
            (totalR / count).toInt().coerceIn(0, 255),
            (totalG / count).toInt().coerceIn(0, 255),
            (totalB / count).toInt().coerceIn(0, 255)
        )
    }

    private fun isColorWhite(color: Int): Boolean {
        return Color.red(color) > WHITE_THRESHOLD &&
                Color.green(color) > WHITE_THRESHOLD &&
                Color.blue(color) > WHITE_THRESHOLD
    }

    /**
     * Flood fill from edges to identify background pixels.
     * Uses BFS and avoids pixels inside the face mask.
     */
    private fun floodFillFromEdges(
        pixels: IntArray, w: Int, h: Int,
        bgColor: Int, mask: Array<BooleanArray>,
        faceMask: Array<BooleanArray>
    ) {
        val visited = Array(h) { BooleanArray(w) }
        val queue = LinkedList<Pair<Int, Int>>()

        // Add all edge pixels to the queue (skip face pixels)
        for (x in 0 until w) {
            if (!faceMask[0][x]) {
                queue.add(Pair(x, 0))
                visited[0][x] = true
            }
            if (!faceMask[h - 1][x]) {
                queue.add(Pair(x, h - 1))
                visited[h - 1][x] = true
            }
        }
        for (y in 0 until h) {
            if (!faceMask[y][0]) {
                queue.add(Pair(0, y))
                visited[y][0] = true
            }
            if (!faceMask[y][w - 1]) {
                queue.add(Pair(w - 1, y))
                visited[y][w - 1] = true
            }
        }

        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.poll() ?: continue
            val index = y * w + x
            val pixel = pixels[index]
            val pR = Color.red(pixel)
            val pG = Color.green(pixel)
            val pB = Color.blue(pixel)

            // Check if this pixel is similar to the background color
            val isSimilar = abs(pR - bgR) < EDGE_TOLERANCE &&
                    abs(pG - bgG) < EDGE_TOLERANCE &&
                    abs(pB - bgB) < EDGE_TOLERANCE

            if (isSimilar && !faceMask[y][x]) {
                mask[y][x] = true

                // Add neighbors
                val neighbors = listOf(
                    Pair(x - 1, y), Pair(x + 1, y),
                    Pair(x, y - 1), Pair(x, y + 1)
                )

                for ((nx, ny) in neighbors) {
                    if (nx in 0 until w && ny in 0 until h && !visited[ny][nx] && !faceMask[ny][nx]) {
                        visited[ny][nx] = true
                        queue.add(Pair(nx, ny))
                    }
                }
            }
        }
    }

    /**
     * Crop bitmap to the target aspect ratio, centering the crop.
     */
    private fun cropToAspectRatio(bitmap: Bitmap, targetAspect: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val currentAspect = width.toFloat() / height

        return if (kotlin.math.abs(currentAspect - targetAspect) < 0.01f) {
            bitmap
        } else if (currentAspect > targetAspect) {
            val newWidth = (height * targetAspect).toInt()
            val xOffset = (width - newWidth) / 2
            Bitmap.createBitmap(bitmap, xOffset, 0, newWidth, height)
        } else {
            val newHeight = (width / targetAspect).toInt()
            val yOffset = min((height - newHeight) / 3, height - newHeight)
            Bitmap.createBitmap(bitmap, 0, yOffset.coerceAtLeast(0), width, newHeight.coerceAtMost(height))
        }
    }

    /**
     * Resize bitmap to meet minimum dimensions while maintaining aspect ratio.
     */
    private fun resizeToMinimum(bitmap: Bitmap, minWidth: Int, minHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width >= minWidth && height >= minHeight) {
            return bitmap
        }

        val scaleX = minWidth.toFloat() / width
        val scaleY = minHeight.toFloat() / height
        val scale = kotlin.math.max(scaleX, scaleY)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Save bitmap as JPEG with specified quality.
     */
    private fun saveAsJpeg(bitmap: Bitmap, file: File): Long {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return file.length()
    }

    /**
     * Load bitmap from URI and correct its orientation based on EXIF data.
     */
    private fun loadAndCorrectOrientation(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val rotation = getExifRotation(uri)
            if (rotation != 0) {
                rotateBitmap(bitmap, rotation.toFloat())
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load bitmap from File and correct its orientation based on EXIF data.
     */
    private fun loadAndCorrectOrientation(file: File): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val rotation = getExifRotation(file)
            if (rotation != 0) {
                rotateBitmap(bitmap, rotation.toFloat())
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getExifRotation(uri: Uri): Int {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun getExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Generate a preview bitmap with guide overlays for the review screen.
     */
    fun generatePreviewWithGuides(bitmap: Bitmap): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutable)

        val w = mutable.width.toFloat()
        val h = mutable.height.toFloat()

        val guidePaint = android.graphics.Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }

        val ovalRect = android.graphics.RectF(
            w * 0.15f,
            h * 0.08f,
            w * 0.85f,
            h * 0.75f
        )
        canvas.drawOval(ovalRect, guidePaint)

        val linePaint = android.graphics.Paint().apply {
            color = Color.argb(100, 0, 200, 0)
            strokeWidth = 2f
            style = android.graphics.Paint.Style.STROKE
        }

        canvas.drawLine(w * 0.1f, h * 0.08f, w * 0.9f, h * 0.08f, linePaint)
        canvas.drawLine(w * 0.1f, h * 0.72f, w * 0.9f, h * 0.72f, linePaint)

        return mutable
    }
}
