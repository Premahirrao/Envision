//package project.prem.smartglasses.utils
//
//import android.Manifest
//import android.annotation.SuppressLint
//import android.content.Context
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.ImageFormat
//import android.graphics.SurfaceTexture
//import android.hardware.camera2.*
//import android.media.Image
//import android.media.ImageReader
//import android.os.Handler
//import android.os.HandlerThread
//import android.util.Log
//import android.util.Size
//import android.view.Surface
//import android.view.TextureView
//import androidx.camera.view.PreviewView
//import androidx.core.content.ContextCompat
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.withContext
//import kotlin.coroutines.Continuation
//import kotlin.coroutines.resume
//import kotlin.coroutines.resumeWithException
//
//class CameraManager(private val context: Context) {
//    private var cameraDevice: CameraDevice? = null
//    private var imageReader: ImageReader? = null
//    private var captureSession: CameraCaptureSession? = null
//    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
//    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
//    private val backgroundHandler = Handler(backgroundThread.looper)
//    private var latestBitmap: Bitmap? = null
//    private var frameCallback: ((Bitmap) -> Unit)? = null
//    private var frameInterval: Long = 0
//    private var previewSurface: Surface? = null
//    private var textureView: TextureView? = null
//
//    suspend fun initialize() = suspendCancellableCoroutine { continuation ->
//        try {
//            // Find external camera
//            val cameraId = findExternalCamera()
//            if (cameraId == null) {
//                // Fallback to back camera if no external camera found
//                val backCameraId = findBackCamera()
//                if (backCameraId != null) {
//                    openCamera(backCameraId, continuation)
//                } else {
//                    continuation.resumeWithException(Exception("No camera available"))
//                }
//            } else {
//                openCamera(cameraId, continuation)
//            }
//        } catch (e: Exception) {
//            continuation.resumeWithException(e)
//        }
//    }
//
//    private fun findExternalCamera(): String? {
//        return cameraManager.cameraIdList.find { cameraId ->
//            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
//            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
//            val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
//
//            // Check if it's an external camera
//            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
//        }
//    }
//
//    private fun findBackCamera(): String? {
//        return cameraManager.cameraIdList.find { cameraId ->
//            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
//            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun openCamera(cameraId: String, continuation: Continuation<Unit>) {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            continuation.resumeWithException(Exception("Camera permission not granted"))
//            return // Exit if no permission
//        }
//        try {
//            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
//            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
//
//            // Setup image reader for preview
//            val previewSize = map.getOutputSizes(ImageFormat.YUV_420_888).maxByOrNull { it.width * it.height }
//                ?: Size(640, 480)
//
//            imageReader = ImageReader.newInstance(
//                previewSize.width,
//                previewSize.height,
//                ImageFormat.YUV_420_888,
//                2
//            ).apply {
//                setOnImageAvailableListener({ reader ->
//                    val image = reader.acquireLatestImage()
//                    try {
//                        image?.let {
//                            val bitmap = imageToRgbBitmap(it)
//                            latestBitmap = bitmap
//                            frameCallback?.invoke(bitmap)
//                        }
//                    } finally {
//                        image?.close()
//                    }
//                }, backgroundHandler)
//            }
//
//            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
//                override fun onOpened(camera: CameraDevice) {
//                    cameraDevice = camera
//                    continuation.resume(Unit)
//                }
//
//                override fun onDisconnected(camera: CameraDevice) {
//                    camera.close()
//                    cameraDevice = null
//                }
//
//                override fun onError(camera: CameraDevice, error: Int) {
//                    camera.close()
//                    cameraDevice = null
//                    continuation.resumeWithException(Exception("Camera error: $error"))
//                }
//            }, backgroundHandler)
//        } catch (e: Exception) {
//            continuation.resumeWithException(e)
//        }
//    }
//
//    fun setTextureView(view: TextureView) {
//        textureView = view
//        if (view.isAvailable && view.surfaceTexture != null) {
//            setupPreviewSession(view.surfaceTexture)
//        }
//    }
//
//    private fun setupPreviewSession(surfaceTexture: SurfaceTexture?) {
//        try {
//            val characteristics = cameraDevice?.id?.let { cameraManager.getCameraCharacteristics(it) }
//            val map = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
//            val previewSize = map?.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull { it.width * it.height }
//                ?: Size(640, 480)
//
//            surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
//            previewSurface = Surface(surfaceTexture)
//
//            val surfaces = mutableListOf<Surface>()
//            previewSurface?.let { surfaces.add(it) }
//            imageReader?.surface?.let { surfaces.add(it) }
//
//            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
//                override fun onConfigured(session: CameraCaptureSession) {
//                    if (cameraDevice == null) return
//
//                    captureSession = session
//                    try {
//                        val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//                        previewSurface?.let { previewRequestBuilder?.addTarget(it) }
//                        imageReader?.surface?.let { previewRequestBuilder?.addTarget(it) }
//
//                        previewRequestBuilder?.build()?.let { previewRequest ->
//                            session.setRepeatingRequest(previewRequest, null, backgroundHandler)
//                        }
//                    } catch (e: CameraAccessException) {
//                        Log.e("CameraManager", "Failed to start camera preview", e)
//                    }
//                }
//
//                override fun onConfigureFailed(session: CameraCaptureSession) {
//                    Log.e("CameraManager", "Failed to configure camera session")
//                }
//            }, backgroundHandler)
//        } catch (e: Exception) {
//            Log.e("CameraManager", "Error setting up preview session", e)
//        }
//    }
//
//    suspend fun startCamera(previewView: PreviewView, frameInterval: Long,onFrameAvailable: (Bitmap) -> Unit) = withContext(Dispatchers.IO) {
//        this@CameraManager.frameInterval = frameInterval
//        frameCallback = onFrameAvailable
//        textureView?.surfaceTexture?.let { setupPreviewSession(it) }
//    }
//
//    private fun imageToRgbBitmap(image: Image): Bitmap {
//        val width = image.width
//        val height = image.height
//
//        // Get the YUV planes
//        val planes = image.planes
//        val yBuffer = planes[0].buffer
//        val uBuffer = planes[1].buffer
//        val vBuffer = planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
//        val out = java.io.ByteArrayOutputStream()
//        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
//        val imageBytes = out.toByteArray()
//
//        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//    }
//
//    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
//        latestBitmap?.let { bitmap ->
//            bitmap.config?.let { config ->
//                bitmap.copy(config, true)
//            }
//        }
//    }
//
//
//    fun shutdown() {
//        try {
//            captureSession?.close()
//            captureSession = null
//            cameraDevice?.close()
//            cameraDevice = null
//            imageReader?.close()
//            imageReader = null
//            backgroundThread.quitSafely()
//            frameCallback = null
//            latestBitmap?.recycle()
//            latestBitmap = null
//        } catch (e: Exception) {
//            Log.e("CameraManager", "Error shutting down camera", e)
//        }
//    }
//}
package project.prem.smartglasses.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService

class CameraManager(private val context: Context) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var isInitialized = false
    private var latestBitmap: Bitmap? = null

    suspend fun initialize() = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            isInitialized = true
            continuation.resume(Unit)
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalLensFacing::class)
    suspend fun startCamera(
        previewView: PreviewView?,
        frameInterval: Long = 2000L,
        onFrameAvailable: (Bitmap) -> Unit
    ) = withContext(Dispatchers.Main) {
        if (!isInitialized) {
            initialize()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .apply {
                setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        bitmap?.let {
                            latestBitmap = it
                            onFrameAvailable(it)
                        }
                    } finally {
                        imageProxy.close()
                    }
                }
            }

        try {
            cameraProvider.unbindAll()

//            val cameraSelector = CameraSelector.Builder()
//                .requireLensFacing(CameraSelector.LENS_FACING_UNKNOWN)
//                .build()
            camera = cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalyzer
            )

            preview.setSurfaceProvider(previewView?.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
        // First try to get the latest frame from the analyzer
        latestBitmap?.copy(latestBitmap!!.config ?: Bitmap.Config.ARGB_8888, true) ?: run {
            // If no frame is available, take a picture
            try {
                suspendCoroutine { continuation ->
                    val imageCapture = imageCapture ?: run {
                        continuation.resume(null)
                        return@suspendCoroutine
                    }

                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = imageProxyToBitmap(image)
                                image.close()
                                continuation.resume(bitmap)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                                continuation.resume(null)
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate the bitmap if needed
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            bitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        }

        return bitmap
    }

    fun bindPreview(previewView: PreviewView) {
        camera?.cameraControl?.enableTorch(false)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    fun shutdown() {
        imageAnalyzer?.clearAnalyzer()
        cameraExecutor.shutdown()
        latestBitmap?.recycle()
        latestBitmap = null
    }
}