package com.passportphoto.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Manages CameraX lifecycle, preview, image capture, and real-time face analysis
 * for passport photo compliance. Supports switching between front and back cameras.
 */
class CameraManager(private val context: Context) {

    enum class CameraFacing {
        FRONT,
        BACK
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var currentFacing: CameraFacing = CameraFacing.FRONT
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val isCameraReady = AtomicBoolean(false)
    private val isSwitching = AtomicBoolean(false)

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    interface FaceAnalysisCallback {
        fun onAnalysisResult(result: FaceAnalysisResult)
    }

    data class FaceAnalysisResult(
        val faceDetected: Boolean,
        val faceCount: Int,
        val headPosition: HeadPosition,
        val headSizeRatio: Float,
        val isFrontal: Boolean,
        val isTilted: Boolean,
        val hasGlasses: Boolean,
        val hasShadows: Boolean,
        val notices: List<PhotoNotice>,
        val isCompliant: Boolean
    )

    data class HeadPosition(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    enum class PhotoNotice {
        NO_FACE,
        MULTIPLE_FACES,
        NOT_CENTERED,
        TOO_FAR,
        TOO_CLOSE,
        NOT_FRONTAL,
        TILTED,
        GLASSES_DETECTED,
        SHADOWS_DETECTED,
        GOOD_POSITION
    }

    fun getCurrentFacing(): CameraFacing = currentFacing

    fun isReady(): Boolean = isCameraReady.get()

    @SuppressLint("UnsafeOptInUsageError")
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing = CameraFacing.FRONT,
        analysisCallback: FaceAnalysisCallback
    ) {
        currentFacing = facing
        isCameraReady.set(false)
        isSwitching.set(false)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView, facing, analysisCallback)
                isCameraReady.set(true)
                Log.d("CameraManager", "Camera started: $facing")
            } catch (e: Exception) {
                Log.e("CameraManager", "Camera binding failed", e)
                isCameraReady.set(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing,
        analysisCallback: FaceAnalysisCallback
    ) {
        val provider = cameraProvider ?: return

        // Unbind all previous use cases
        provider.unbindAll()

        // Create new use cases
        preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processImage(imageProxy, analysisCallback)
                }
            }

        val cameraSelector = when (facing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        )
    }

    /**
     * Switch between front and back camera.
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analysisCallback: FaceAnalysisCallback
    ) {
        if (isSwitching.getAndSet(true)) {
            Log.w("CameraManager", "Camera switch already in progress")
            return
        }

        if (!isCameraReady.get()) {
            Log.w("CameraManager", "Camera not ready, cannot switch")
            isSwitching.set(false)
            return
        }

        val newFacing = when (currentFacing) {
            CameraFacing.FRONT -> CameraFacing.BACK
            CameraFacing.BACK -> CameraFacing.FRONT
        }

        Log.d("CameraManager", "Switching camera from $currentFacing to $newFacing")

        // Temporarily mark as not ready
        isCameraReady.set(false)

        try {
            currentFacing = newFacing
            bindCameraUseCases(lifecycleOwner, previewView, newFacing, analysisCallback)
            isCameraReady.set(true)
            Log.d("CameraManager", "Camera switched successfully to $newFacing")
        } catch (e: Exception) {
            Log.e("CameraManager", "Camera switch failed", e)
            // Revert to previous facing
            currentFacing = when (newFacing) {
                CameraFacing.FRONT -> CameraFacing.BACK
                CameraFacing.BACK -> CameraFacing.FRONT
            }
            // Try to rebind with the original facing
            try {
                bindCameraUseCases(lifecycleOwner, previewView, currentFacing, analysisCallback)
                isCameraReady.set(true)
            } catch (e2: Exception) {
                Log.e("CameraManager", "Failed to revert camera", e2)
            }
        } finally {
            isSwitching.set(false)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImage(imageProxy: ImageProxy, callback: FaceAnalysisCallback) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            callback.onAnalysisResult(emptyResult())
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val imageWidth = imageProxy.width.toFloat()
        val imageHeight = imageProxy.height.toFloat()

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val result = analyzeFaces(faces, imageWidth, imageHeight, currentFacing)
                callback.onAnalysisResult(result)
            }
            .addOnFailureListener { e ->
                Log.e("CameraManager", "Face detection failed", e)
                callback.onAnalysisResult(emptyResult())
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun analyzeFaces(
        faces: List<Face>,
        imageWidth: Float,
        imageHeight: Float,
        facing: CameraFacing = CameraFacing.FRONT
    ): FaceAnalysisResult {
        if (faces.isEmpty()) {
            return FaceAnalysisResult(
                faceDetected = false,
                faceCount = 0,
                headPosition = HeadPosition(0f, 0f, 0f, 0f),
                headSizeRatio = 0f,
                isFrontal = false,
                isTilted = false,
                hasGlasses = false,
                hasShadows = false,
                notices = listOf(PhotoNotice.NO_FACE),
                isCompliant = false
            )
        }

        if (faces.size > 1) {
            return FaceAnalysisResult(
                faceDetected = true,
                faceCount = faces.size,
                headPosition = HeadPosition(0f, 0f, 0f, 0f),
                headSizeRatio = 0f,
                isFrontal = false,
                isTilted = false,
                hasGlasses = false,
                hasShadows = false,
                notices = listOf(PhotoNotice.MULTIPLE_FACES),
                isCompliant = false
            )
        }

        val face = faces[0]
        val boundingBox = face.boundingBox

        // Calculate face position relative to image center
        val faceCenterX = (boundingBox.left + boundingBox.right) / 2f
        val faceCenterY = (boundingBox.top + boundingBox.bottom) / 2f
        val imageCenterX = imageWidth / 2f
        val imageCenterY = imageHeight / 2f

        // Head size ratio: face height relative to image height
        // ML Kit face bounding box is ~60-70% of full head height, so multiply by 1.4 to estimate full head
        val faceHeight = boundingBox.height().toFloat()
        val estimatedHeadHeight = faceHeight * 1.4f
        val headSizeRatio = estimatedHeadHeight / imageHeight

        // Check if face is centered (within 15% tolerance)
        val offsetX = abs(faceCenterX - imageCenterX) / imageWidth
        val offsetY = abs(faceCenterY - imageCenterY) / imageHeight
        val isCentered = offsetX < 0.15f && offsetY < 0.2f

        // Check head size (should be 64-72% of image height for passport)
        // Front camera has wider FOV, so allow slightly larger range
        val minSize = 0.55f
        val maxSize = if (facing == CameraFacing.FRONT) 0.85f else 0.75f
        val isGoodSize = headSizeRatio in minSize..maxSize
        val tooFar = headSizeRatio < minSize
        val tooClose = headSizeRatio > maxSize

        // Check if face is frontal (looking straight ahead)
        val rotY = face.headEulerAngleY // left-right rotation
        val rotZ = face.headEulerAngleZ // tilt
        val rotX = face.headEulerAngleX // up-down
        val isFrontal = abs(rotY) < 12f && abs(rotX) < 12f
        val isTilted = abs(rotZ) > 10f

        // Check for glasses (using landmark detection as proxy)
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
        val hasGlasses = leftEye != null && rightEye != null &&
                (face.leftEyeOpenProbability ?: 1f) < 0.2f ||
                detectGlassesReflection(face)

        // Build notices list
        val notices = mutableListOf<PhotoNotice>()

        if (!isCentered) notices.add(PhotoNotice.NOT_CENTERED)
        if (tooFar) notices.add(PhotoNotice.TOO_FAR)
        if (tooClose) notices.add(PhotoNotice.TOO_CLOSE)
        if (!isFrontal) notices.add(PhotoNotice.NOT_FRONTAL)
        if (isTilted) notices.add(PhotoNotice.TILTED)
        if (hasGlasses) notices.add(PhotoNotice.GLASSES_DETECTED)

        val isCompliant = isCentered && isGoodSize && isFrontal && !isTilted && !hasGlasses

        if (isCompliant) {
            notices.add(PhotoNotice.GOOD_POSITION)
        }

        return FaceAnalysisResult(
            faceDetected = true,
            faceCount = 1,
            headPosition = HeadPosition(
                x = boundingBox.left / imageWidth,
                y = boundingBox.top / imageHeight,
                width = boundingBox.width() / imageWidth,
                height = boundingBox.height() / imageHeight
            ),
            headSizeRatio = headSizeRatio,
            isFrontal = isFrontal,
            isTilted = isTilted,
            hasGlasses = hasGlasses,
            hasShadows = false,
            notices = notices,
            isCompliant = isCompliant
        )
    }

    private fun detectGlassesReflection(face: Face): Boolean {
        val leftEyeProb = face.leftEyeOpenProbability ?: return false
        val rightEyeProb = face.rightEyeOpenProbability ?: return false
        return leftEyeProb < 0.15f && rightEyeProb < 0.15f
    }

    private fun emptyResult(): FaceAnalysisResult {
        return FaceAnalysisResult(
            faceDetected = false,
            faceCount = 0,
            headPosition = HeadPosition(0f, 0f, 0f, 0f),
            headSizeRatio = 0f,
            isFrontal = false,
            isTilted = false,
            hasGlasses = false,
            hasShadows = false,
            notices = listOf(PhotoNotice.NO_FACE),
            isCompliant = false
        )
    }

    /**
     * Set zoom level (0.0 to 1.0).
     * 0.0 = no zoom (wide), 1.0 = maximum zoom.
     */
    fun setZoom(zoomLevel: Float) {
        val cam = camera ?: return
        val clampedZoom = zoomLevel.coerceIn(0f, 1f)
        cam.cameraControl.setLinearZoom(clampedZoom)
    }

    /**
     * Get current zoom level (0.0 to 1.0).
     */
    fun getCurrentZoom(): Float {
        val cam = camera ?: return 0f
        val zoomState = cam.cameraInfo.zoomState.value ?: return 0f
        val minZoom = zoomState.minZoomRatio
        val maxZoom = zoomState.maxZoomRatio
        val currentZoom = zoomState.zoomRatio
        return if (maxZoom > minZoom) {
            (currentZoom - minZoom) / (maxZoom - minZoom)
        } else {
            0f
        }
    }

    fun capturePhoto(
        outputDir: File,
        mirrorFrontCamera: Boolean = true,
        callback: (File?, Exception?) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            Log.e("CameraManager", "ImageCapture is null, camera not ready")
            callback(null, IllegalStateException("Camera not initialized. Please wait for camera to be ready."))
            return
        }

        if (!isCameraReady.get()) {
            Log.e("CameraManager", "Camera not ready for capture")
            callback(null, IllegalStateException("Camera is not ready yet. Please wait."))
            return
        }

        val photoFile = File(
            outputDir,
            "passport_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        try {
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d("CameraManager", "Photo saved: ${photoFile.absolutePath}")
                        callback(photoFile, null)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraManager", "Photo capture failed", exception)
                        callback(null, exception)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("CameraManager", "Capture error", e)
            callback(null, e)
        }
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
        faceDetector.close()
        isCameraReady.set(false)
        isSwitching.set(false)
    }
}
