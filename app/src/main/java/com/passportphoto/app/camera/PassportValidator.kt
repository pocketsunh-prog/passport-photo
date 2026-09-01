package com.passportphoto.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Validates and processes passport photos against immigration department requirements.
 * 
 * Requirements:
 * - Size: 40mm x 50mm (1.57 x 1.96 inches)
 * - Head Size: Chin to top of head = 32-36mm (64-72% of 50mm)
 * - Background: Plain white
 * - Format: JPEG, max 5MB
 * - Resolution: Min 1200 x 1600 pixels
 * - 600 DPI for scanned copies
 * - Full frontal, neutral expression
 * - No glasses covering eyes
 * - No shadows or glare
 * - No dark/light clothing contrast issues
 */
class PassportValidator(private val context: Context) {

    data class ValidationResult(
        val isValid: Boolean,
        val resolutionOk: Boolean,
        val fileSizeOk: Boolean,
        val backgroundOk: Boolean,
        val brightnessOk: Boolean,
        val contrastOk: Boolean,
        val headSizeOk: Boolean,
        val issues: List<ValidationIssue>,
        val photoInfo: PhotoInfo
    )

    data class PhotoInfo(
        val width: Int,
        val height: Int,
        val fileSizeBytes: Long,
        val estimatedDpi: Int,
        val headSizePercent: Float,
        val backgroundColorVariance: Float,
        val averageBrightness: Float
    )

    enum class ValidationIssue {
        RESOLUTION_TOO_LOW,
        FILE_TOO_LARGE,
        BACKGROUND_NOT_WHITE,
        TOO_DARK,
        TOO_BRIGHT,
        LOW_CONTRAST,
        HEAD_TOO_SMALL,
        HEAD_TOO_LARGE,
        NOT_PORTRAIT_ORIENTATION
    }

    companion object {
        const val MIN_WIDTH = 1200
        const val MIN_HEIGHT = 1600
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5 MB
        const val PASSPORT_WIDTH_MM = 40f
        const val PASSPORT_HEIGHT_MM = 50f
        const val HEAD_MIN_PERCENT = 0.64f // 32/50
        const val HEAD_MAX_PERCENT = 0.72f // 36/50
        const val BACKGROUND_WHITE_THRESHOLD = 200
        const val BACKGROUND_VARIANCE_THRESHOLD = 30f
    }

    fun validatePhoto(imageUri: Uri): ValidationResult {
        val bitmap = loadBitmap(imageUri) ?: return invalidResult("Failed to load image")
        return validateBitmap(bitmap, getFileSize(imageUri))
    }

    fun validatePhoto(file: File): ValidationResult {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return invalidResult("Failed to load image")
        return validateBitmap(bitmap, file.length())
    }

    fun validateBitmap(bitmap: Bitmap, fileSizeBytes: Long): ValidationResult {
        val width = bitmap.width
        val height = bitmap.height

        // Check resolution
        val resolutionOk = width >= MIN_WIDTH && height >= MIN_HEIGHT

        // Check file size
        val fileSizeOk = fileSizeBytes <= MAX_FILE_SIZE

        // Check background (white in corners)
        val backgroundResult = analyzeBackground(bitmap)
        val backgroundOk = backgroundResult.isWhite

        // Check brightness
        val brightnessResult = analyzeBrightness(bitmap)
        val brightnessOk = brightnessResult.isGood
        val contrastOk = brightnessResult.hasGoodContrast

        // Estimate head size from face detection or heuristics
        val headSizePercent = estimateHeadSize(bitmap)
        val headSizeOk = headSizePercent in HEAD_MIN_PERCENT..HEAD_MAX_PERCENT

        // Estimate DPI
        val estimatedDpi = estimateDpi(width, height)

        val issues = mutableListOf<ValidationIssue>()
        if (!resolutionOk) issues.add(ValidationIssue.RESOLUTION_TOO_LOW)
        if (!fileSizeOk) issues.add(ValidationIssue.FILE_TOO_LARGE)
        if (!backgroundOk) issues.add(ValidationIssue.BACKGROUND_NOT_WHITE)
        if (!brightnessOk && brightnessResult.averageBrightness < 80) issues.add(ValidationIssue.TOO_DARK)
        if (!brightnessOk && brightnessResult.averageBrightness > 240) issues.add(ValidationIssue.TOO_BRIGHT)
        if (!contrastOk) issues.add(ValidationIssue.LOW_CONTRAST)
        if (!headSizeOk && headSizePercent < HEAD_MIN_PERCENT) issues.add(ValidationIssue.HEAD_TOO_SMALL)
        if (!headSizeOk && headSizePercent > HEAD_MAX_PERCENT) issues.add(ValidationIssue.HEAD_TOO_LARGE)

        // Portrait orientation check (height should be >= width for passport)
        val isPortrait = height >= width
        if (!isPortrait) issues.add(ValidationIssue.NOT_PORTRAIT_ORIENTATION)

        val isValid = issues.isEmpty()

        return ValidationResult(
            isValid = isValid,
            resolutionOk = resolutionOk,
            fileSizeOk = fileSizeOk,
            backgroundOk = backgroundOk,
            brightnessOk = brightnessOk,
            contrastOk = contrastOk,
            headSizeOk = headSizeOk,
            issues = issues,
            photoInfo = PhotoInfo(
                width = width,
                height = height,
                fileSizeBytes = fileSizeBytes,
                estimatedDpi = estimatedDpi,
                headSizePercent = headSizePercent,
                backgroundColorVariance = backgroundResult.variance,
                averageBrightness = brightnessResult.averageBrightness
            )
        )
    }

    private fun analyzeBackground(bitmap: Bitmap): BackgroundResult {
        val w = bitmap.width
        val h = bitmap.height

        // Sample corners and edges
        val samplePoints = listOf(
            Pair(5, 5), Pair(w - 5, 5),
            Pair(5, h / 4), Pair(w - 5, h / 4),
            Pair(w / 2, 3), Pair(w / 2, h - 3)
        )

        val colors = samplePoints.mapNotNull { (x, y) ->
            val sx = x.coerceIn(0, w - 1)
            val sy = y.coerceIn(0, h - 1)
            bitmap.getPixel(sx, sy)
        }

        if (colors.isEmpty()) return BackgroundResult(false, 999f)

        val avgR = colors.map { Color.red(it) }.average().toFloat()
        val avgG = colors.map { Color.green(it) }.average().toFloat()
        val avgB = colors.map { Color.blue(it) }.average().toFloat()

        val isWhite = avgR > BACKGROUND_WHITE_THRESHOLD &&
                avgG > BACKGROUND_WHITE_THRESHOLD &&
                avgB > BACKGROUND_WHITE_THRESHOLD

        val variance = colors.map { color ->
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val avg = (r + g + b) / 3f
            abs(r - avg) + abs(g - avg) + abs(b - avg)
        }.average().toFloat() / 3f

        return BackgroundResult(isWhite && variance < BACKGROUND_VARIANCE_THRESHOLD, variance)
    }

    private fun analyzeBrightness(bitmap: Bitmap): BrightnessResult {
        val w = bitmap.width
        val h = bitmap.height
        val step = 20 // Sample every 20 pixels for performance

        var totalBrightness = 0f
        var count = 0
        var minBrightness = 255f
        var maxBrightness = 0f

        // Focus on center region (face area)
        val centerXStart = w / 4
        val centerXEnd = 3 * w / 4
        val centerYStart = h / 6
        val centerYEnd = 5 * h / 6

        for (y in centerYStart until centerYEnd step step) {
            for (x in centerXStart until centerXEnd step step) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
                totalBrightness += brightness
                minBrightness = min(minBrightness, brightness)
                maxBrightness = max(maxBrightness, brightness)
                count++
            }
        }

        val avgBrightness = if (count > 0) totalBrightness / count else 0f
        val contrast = maxBrightness - minBrightness

        val isGood = avgBrightness in 80f..230f
        val hasGoodContrast = contrast > 40f

        return BrightnessResult(isGood, hasGoodContrast, avgBrightness)
    }

    private fun estimateHeadSize(bitmap: Bitmap): Float {
        // Estimate by detecting the face region using simple skin color detection
        // This is a heuristic - ML Kit face detection is used for real-time feedback
        val w = bitmap.width
        val h = bitmap.height

        var topY = h
        var bottomY = 0
        var found = false

        // Scan middle vertical strip for skin-colored regions
        val midX = w / 2
        val stripWidth = w / 3
        val startX = midX - stripWidth / 2
        val endX = midX + stripWidth / 2

        for (y in 0 until h step 4) {
            for (x in startX until endX step 4) {
                val pixel = bitmap.getPixel(x, y)
                if (isSkinColor(pixel)) {
                    topY = minOf(topY, y)
                    bottomY = maxOf(bottomY, y)
                    found = true
                }
            }
        }

        return if (found) {
            (bottomY - topY).toFloat() / h
        } else {
            0.5f // Default assumption
        }
    }

    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Simple skin color detection in RGB space
        return r > 95 && g > 40 && b > 20 &&
                r > g && r > b &&
                abs(r - g) > 15 &&
                r - b > 15
    }

    private fun estimateDpi(widthPixels: Int, heightPixels: Int): Int {
        // Standard passport photo at 600 DPI = 945 x 1190 pixels (40x50mm at 600 DPI)
        // But minimum is 1200 x 1600 which implies ~472 DPI
        val widthDpi = (widthPixels / (PASSPORT_WIDTH_MM / 25.4f)).roundToInt()
        return widthDpi
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun invalidResult(reason: String): ValidationResult {
        return ValidationResult(
            isValid = false,
            resolutionOk = false,
            fileSizeOk = false,
            backgroundOk = false,
            brightnessOk = false,
            contrastOk = false,
            headSizeOk = false,
            issues = listOf(ValidationIssue.RESOLUTION_TOO_LOW),
            photoInfo = PhotoInfo(0, 0, 0, 0, 0f, 0f, 0f)
        )
    }

    private data class BackgroundResult(val isWhite: Boolean, val variance: Float)
    private data class BrightnessResult(
        val isGood: Boolean,
        val hasGoodContrast: Boolean,
        val averageBrightness: Float
    )
}
