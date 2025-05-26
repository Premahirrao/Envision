package project.prem.smartglasses.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import android.content.res.AssetManager
import android.content.res.AssetFileDescriptor
import kotlin.math.atan2

class ObjectDetectionModel(private val context: Context) {
    private val options = ObjectDetector.ObjectDetectorOptions.builder()
        .setMaxResults(5)
        .setScoreThreshold(0.5f)
        .build()

    private val detector: ObjectDetector? = try {
        val modelPath = "object_detection.tflite"
        val model = loadModelFile(context.assets, modelPath)
        if (model != null) {
            ObjectDetector.createFromBufferAndOptions(model, options)
        } else {
            Log.e(TAG, "Failed to load model from buffer.")
            null
        }
    } catch (e: IllegalStateException) {
        Log.e(TAG, "Error creating ObjectDetector: ${e.message}")
        null
    }

    fun detectObjects(image: Bitmap): List<DetectedObject> {
        val detector = detector ?: run {
            Log.e(TAG, "ObjectDetector is null. Cannot perform object detection.")
            return emptyList()
        }
        val tensorImage = TensorImage.fromBitmap(image)
        val results = detector.detect(tensorImage)

        return results.map { detection ->
            val box = detection.boundingBox
            val centerX = box.centerX()
            val centerY = box.centerY()

            // Calculate angle from center of image
            val imageWidth = image.width.toFloat()
            val imageHeight = image.height.toFloat()
            val relativeX = centerX - (imageWidth / 2)
            val relativeY = (imageHeight / 2) - centerY

            val angle = Math.toDegrees(atan2(relativeY, relativeX).toDouble())

            DetectedObject(
                label = detection.categories[0].label,
                confidence = detection.categories[0].score,
                angle = angle
            )
        }
    }

    data class DetectedObject(
        val label: String,
        val confidence: Float,
        val angle: Double
    )

    fun shutdown() {
        detector?.close()
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer? {
        val fileDescriptor: AssetFileDescriptor?
        try {
            fileDescriptor = assetManager.openFd(modelPath)
        } catch (e: IOException) {
            Log.e(TAG, "File not found: ${e.message}")
            return null
        }

        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    companion object {
        private const val TAG = "ObjectDetectionModel"
    }
}