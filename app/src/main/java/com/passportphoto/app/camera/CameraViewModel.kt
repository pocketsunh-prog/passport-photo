package com.passportphoto.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.LinkedList
import kotlin.math.abs

/**
 * ViewModel managing camera state, face analysis, and photo processing.
 * Supports switching between front and back cameras.
 */
class CameraViewModel : ViewModel() {

    private val _analysisResult = MutableStateFlow(
        CameraManager.FaceAnalysisResult(
            faceDetected = false,
            faceCount = 0,
            headPosition = CameraManager.HeadPosition(0f, 0f, 0f, 0f),
            headSizeRatio = 0f,
            isFrontal = false,
            isTilted = false,
            hasGlasses = false,
            hasShadows = false,
            notices = listOf(CameraManager.PhotoNotice.NO_FACE),
            isCompliant = false
        )
    )
    val analysisResult: StateFlow<CameraManager.FaceAnalysisResult> = _analysisResult.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _captureError = MutableStateFlow<String?>(null)
    val captureError: StateFlow<String?> = _captureError.asStateFlow()

    private val _capturedFile = MutableStateFlow<File?>(null)
    val capturedFile: StateFlow<File?> = _capturedFile.asStateFlow()

    private val _currentFacing = MutableStateFlow(CameraManager.CameraFacing.FRONT)
    val currentFacing: StateFlow<CameraManager.CameraFacing> = _currentFacing.asStateFlow()

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    private val _validationResult = MutableStateFlow<PassportValidator.ValidationResult?>(null)
    val validationResult: StateFlow<PassportValidator.ValidationResult?> = _validationResult.asStateFlow()

    private val _processedImage = MutableStateFlow<ImageProcessor.ProcessedImage?>(null)
    val processedImage: StateFlow<ImageProcessor.ProcessedImage?> = _processedImage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentNotice = MutableStateFlow(CameraManager.PhotoNotice.NO_FACE)
    val currentNotice: StateFlow<CameraManager.PhotoNotice> = _currentNotice.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var passportValidator: PassportValidator? = null
    private var imageProcessor: ImageProcessor? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var lastCallback: CameraManager.FaceAnalysisCallback? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            cameraManager = CameraManager(context.applicationContext)
            passportValidator = PassportValidator(context.applicationContext)
            imageProcessor = ImageProcessor(context.applicationContext)
            isInitialized = true
        }
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraManager.CameraFacing = CameraManager.CameraFacing.FRONT
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        this.lastCallback = createCallback()

        _currentFacing.value = facing
        _isCameraReady.value = false

        cameraManager?.startCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            facing = facing,
            analysisCallback = lastCallback!!
        )
    }

    private fun createCallback(): CameraManager.FaceAnalysisCallback {
        return object : CameraManager.FaceAnalysisCallback {
            override fun onAnalysisResult(result: CameraManager.FaceAnalysisResult) {
                _analysisResult.value = result
                _currentNotice.value = result.notices.firstOrNull() ?: CameraManager.PhotoNotice.NO_FACE
                _isCameraReady.value = true
            }
        }
    }

    fun switchCamera() {
        val owner = lifecycleOwner ?: return
        val view = previewView ?: return
        val callback = lastCallback ?: return

        _isCameraReady.value = false

        cameraManager?.switchCamera(
            lifecycleOwner = owner,
            previewView = view,
            analysisCallback = callback
        )

        // Update facing state
        _currentFacing.value = cameraManager?.getCurrentFacing() ?: CameraManager.CameraFacing.FRONT
    }

    fun capturePhoto(context: Context) {
        if (_isCapturing.value) return
        _captureError.value = null
        _isCapturing.value = true

        val outputDir = File(context.cacheDir, "photos").apply { mkdirs() }

        // Determine if we should mirror (front camera photos are typically mirrored)
        val mirrorFront = _currentFacing.value == CameraManager.CameraFacing.FRONT

        cameraManager?.capturePhoto(outputDir, mirrorFront) { file, error ->
            if (file != null) {
                _capturedFile.value = file
            } else {
                _captureError.value = error?.message ?: "Failed to capture photo"
            }
            _isCapturing.value = false
        }
    }

    fun clearCaptureError() {
        _captureError.value = null
    }

    fun processCapturedImage(context: Context, file: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            val outputDir = File(context.cacheDir, "processed").apply { mkdirs() }
            val processed = imageProcessor?.processImage(file, outputDir, replaceBackground = false)
            _processedImage.value = processed

            processed?.let { result ->
                val validation = passportValidator?.validatePhoto(result.file)
                _validationResult.value = validation
            }
            _isProcessing.value = false
        }
    }

    fun validateUploadedImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            val validation = passportValidator?.validatePhoto(uri)
            _validationResult.value = validation
            _isProcessing.value = false
        }
    }

    fun processUploadedImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            val outputDir = File(context.cacheDir, "processed").apply { mkdirs() }
            val processed = imageProcessor?.processImage(uri, outputDir, replaceBackground = false)
            _processedImage.value = processed

            processed?.let { result ->
                val validation = passportValidator?.validatePhoto(result.file)
                _validationResult.value = validation
            }
            _isProcessing.value = false
        }
    }

    fun saveToGallery(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - use MediaStore
                saveToGalleryApi29Plus(context, file)
            } else {
                // Android 9 and below - use direct file access
                saveToGalleryLegacy(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveToGalleryApi29Plus(context: Context, file: File): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PassportPhotos")
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            outputStream?.use { os ->
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveToGalleryLegacy(file: File): Boolean {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "PassportPhotos"
        ).apply { mkdirs() }

        val destFile = File(picturesDir, file.name)
        return try {
            FileInputStream(file).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Save photo with transparent background.
     * Removes the background and saves as PNG for transparency support.
     */
    fun saveToGalleryWithTransparentBackground(context: Context, file: File): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
            val transparentBitmap = removeBackground(bitmap)
            bitmap.recycle()

            if (transparentBitmap == null) return false

            val fileName = file.nameWithoutExtension + "_transparent.png"
            saveBitmapToGallery(context, transparentBitmap, fileName, "image/png")
            transparentBitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun removeBackground(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val bgColor = getEdgeColor(bitmap)

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        val skinMask = Array(h) { BooleanArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (isSkinColor(pixels[y * w + x])) {
                    skinMask[y][x] = true
                }
            }
        }
        val protectedMask = expandMask(skinMask, w, h, radius = 4)

        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)
        val tolerance = 35

        val visited = Array(h) { BooleanArray(w) }
        val queue = LinkedList<Pair<Int, Int>>()

        for (x in 0 until w) {
            if (!protectedMask[0][x]) {
                queue.add(x to 0)
                visited[0][x] = true
            }
            if (!protectedMask[h - 1][x]) {
                queue.add(x to h - 1)
                visited[h - 1][x] = true
            }
        }
        for (y in 0 until h) {
            if (!protectedMask[y][0]) {
                queue.add(0 to y)
                visited[y][0] = true
            }
            if (!protectedMask[y][w - 1]) {
                queue.add(w - 1 to y)
                visited[y][w - 1] = true
            }
        }

        while (queue.isNotEmpty()) {
            val (x, y) = queue.poll() ?: continue
            val pixel = pixels[y * w + x]
            val pR = Color.red(pixel)
            val pG = Color.green(pixel)
            val pB = Color.blue(pixel)

            val isSimilar = abs(pR - bgR) < tolerance &&
                    abs(pG - bgG) < tolerance &&
                    abs(pB - bgB) < tolerance

            if (isSimilar) {
                pixels[y * w + x] = Color.TRANSPARENT

                val neighbors = listOf(
                    x - 1 to y, x + 1 to y,
                    x to y - 1, x to y + 1
                )
                for ((nx, ny) in neighbors) {
                    if (nx in 0 until w && ny in 0 until h && !visited[ny][nx]) {
                        visited[ny][nx] = true
                        queue.add(nx to ny)
                    }
                }
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun getEdgeColor(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

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

    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        val cb = (128 - 0.168736 * r - 0.331264 * g + 0.5 * b).toInt()
        val cr = (128 + 0.5 * r - 0.418688 * g - 0.081312 * b).toInt()

        return y > 80 && cb in 77..127 && cr in 133..173
    }

    private fun expandMask(mask: Array<BooleanArray>, w: Int, h: Int, radius: Int): Array<BooleanArray> {
        val expanded = Array(h) { BooleanArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y][x]) {
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

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String, mimeType: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PassportPhotos")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false
                val outputStream = context.contentResolver.openOutputStream(uri) ?: return false
                outputStream.use { os ->
                    val format = if (mimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    bitmap.compress(format, 100, os)
                }
                true
            } else {
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "PassportPhotos"
                ).apply { mkdirs() }
                val destFile = File(picturesDir, fileName)
                FileOutputStream(destFile).use { output ->
                    val format = if (mimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    bitmap.compress(format, 100, output)
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun reset() {
        _analysisResult.value = CameraManager.FaceAnalysisResult(
            faceDetected = false,
            faceCount = 0,
            headPosition = CameraManager.HeadPosition(0f, 0f, 0f, 0f),
            headSizeRatio = 0f,
            isFrontal = false,
            isTilted = false,
            hasGlasses = false,
            hasShadows = false,
            notices = listOf(CameraManager.PhotoNotice.NO_FACE),
            isCompliant = false
        )
        _capturedFile.value = null
        _validationResult.value = null
        _processedImage.value = null
        _currentNotice.value = CameraManager.PhotoNotice.NO_FACE
        _captureError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager?.shutdown()
    }
}
