package project.prem.smartglasses.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.core.graphics.scale

class OCRModel(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Enhanced price pattern with international currency support
    private val pricePattern = Regex("(Rs\\.\\s?\\d+(\\.\\d{2})?)")

    @WorkerThread
    suspend fun detectPrices(image: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
        try {
            val processedImage = preprocessImage(image)
            val inputImage = InputImage.fromBitmap(processedImage, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val prices = processTextResults(visionText.text)
                    continuation.resume(prices)
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "Recognition error: ${e.message}")
                    continuation.resume(emptyList())
                }
        } catch (e: Exception) {
            Log.e("OCR", "Image processing error: ${e.message}")
            continuation.resume(emptyList())
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Resize while maintaining aspect ratio
        val maxWidth = 1280
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth = maxWidth.coerceAtMost(bitmap.width)
        val newHeight = (newWidth / aspectRatio).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    private fun processTextResults(text: String): List<String> {
        return text.split("\n")
            .flatMap { line ->
                pricePattern.findAll(line)
                    .map { it.value }
                    .filter { it.length in 3..20 }
                    .map {
                        it.replace(Regex("""[^\d.,$€£¥₹]"""), "")
                            .replace(",", ".")
                    }
            }
            .distinct()
            .sortedBy { it.length }
    }

    fun shutdown() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.e("OCR", "Error closing recognizer", e)
        }
    }
}