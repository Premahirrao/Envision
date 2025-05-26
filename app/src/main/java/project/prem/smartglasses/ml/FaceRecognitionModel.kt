package project.prem.smartglasses.ml


import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import java.io.FileInputStream
import java.nio.channels.FileChannel

class FaceRecognitionModel(private val context: Context) {
    private val faceDetector: FaceDetector
    private val faceNetInterpreter: Interpreter
    private val registeredFaces = mutableMapOf<String, MutableList<FloatArray>>()
    private val embeddingDim = 128
    private val inputSize = 160
    private val storageFile = File(context.filesDir, "registered_faces.json")

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()

        faceDetector = FaceDetection.getClient(options)
        faceNetInterpreter = Interpreter(loadModelFile(), Interpreter.Options().apply {
            setNumThreads(4)
            setUseNNAPI(true)
        })
        loadRegisteredFaces()
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd("faceNet.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        ).apply {
            assetFileDescriptor.close()
        }
    }

    private fun loadRegisteredFaces() {
        try {
            if (storageFile.exists()) {
                val jsonString = storageFile.readText()
                val jsonObject = JSONObject(jsonString)

                jsonObject.keys().forEach { name ->
                    val embeddingsArray = jsonObject.getJSONArray(name)
                    val embeddings = mutableListOf<FloatArray>()

                    for (i in 0 until embeddingsArray.length()) {
                        val embeddingArray = embeddingsArray.getJSONArray(i)
                        val embedding = FloatArray(embeddingDim)
                        for (j in 0 until embeddingDim) {
                            embedding[j] = embeddingArray.getDouble(j).toFloat()
                        }
                        embeddings.add(embedding)
                    }
                    registeredFaces[name] = embeddings
                }
                Log.d("FaceRecognition", "Loaded ${registeredFaces.size} people with multiple faces")
            }
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Error loading registered faces", e)
        }
    }

    private fun saveRegisteredFaces() {
        try {
            val jsonObject = JSONObject()
            registeredFaces.forEach { (name, embeddings) ->
                val personEmbeddings = JSONArray()
                embeddings.forEach { embedding ->
                    val embeddingArray = JSONArray()
                    embedding.forEach { embeddingArray.put(it.toDouble()) }
                    personEmbeddings.put(embeddingArray)
                }
                jsonObject.put(name, personEmbeddings)
            }

            storageFile.writeText(jsonObject.toString())
            Log.d("FaceRecognition", "Saved ${registeredFaces.size} people with multiple faces")
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Error saving registered faces", e)
        }
    }

    suspend fun detectFaces(image: Bitmap): List<DetectedFace> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(image, 0)
            val faces = faceDetector.process(inputImage).await()

            faces.mapNotNull { face ->
                try {
                    val faceBitmap = extractFaceBitmap(image, face.boundingBox)
                    val embedding = getFaceEmbedding(faceBitmap)
                    val (name, confidence) = findMatchingFace(embedding)

                    DetectedFace(
                        name = name,
                        confidence = confidence,
                        boundingBox = RectF(face.boundingBox)
                    )
                } catch (e: Exception) {
                    Log.e("FaceRecognition", "Error processing face", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Error detecting faces", e)
            emptyList()
        }
    }

    suspend fun registerFace(image: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(image, 0)
            val faces = faceDetector.process(inputImage).await()

            if (faces.size != 1) {
                Log.w("FaceRecognition", "Expected 1 face, found ${faces.size}")
                return@withContext false
            }

            val face = faces[0]
            val faceBitmap = extractFaceBitmap(image, face.boundingBox)
            val embedding = getFaceEmbedding(faceBitmap)

            // Add new embedding to existing list or create new list
            registeredFaces.getOrPut(name) { mutableListOf() }.add(embedding)
            saveRegisteredFaces()
            Log.d("FaceRecognition", "Successfully registered new face for $name")
            true
        } catch (e: Exception) {
            Log.e("FaceRecognition", "Error registering face", e)
            false
        }
    }

    private fun extractFaceBitmap(image: Bitmap, boundingBox: android.graphics.Rect): Bitmap {
        val padding = (boundingBox.width() * 0.2).toInt() // Increased padding for better recognition
        val left = (boundingBox.left - padding).coerceAtLeast(0)
        val top = (boundingBox.top - padding).coerceAtLeast(0)
        val width = (boundingBox.width() + 2 * padding).coerceAtMost(image.width - left)
        val height = (boundingBox.height() + 2 * padding).coerceAtMost(image.height - top)

        return Bitmap.createBitmap(
            image,
            left,
            top,
            width,
            height
        ).let { faceBitmap ->
            Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        }
    }

    private fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val inputArray = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }

        for (x in 0 until inputSize) {
            for (y in 0 until inputSize) {
                val pixel = faceBitmap.getPixel(x, y)
                inputArray[0][x][y][0] = (((pixel shr 16) and 0xFF) / 127.5f) - 1f
                inputArray[0][x][y][1] = (((pixel shr 8) and 0xFF) / 127.5f) - 1f
                inputArray[0][x][y][2] = ((pixel and 0xFF) / 127.5f) - 1f
            }
        }

        val outputArray = Array(1) { FloatArray(embeddingDim) }
        faceNetInterpreter.run(inputArray, outputArray)

        val embedding = outputArray[0]
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)
        for (i in embedding.indices) {
            embedding[i] /= norm
        }

        return embedding
    }

    private fun findMatchingFace(embedding: FloatArray): Pair<String, Float> {
        var bestMatch = "Unknown"
        var bestConfidence = 0f

        registeredFaces.forEach { (name, embeddings) ->
            embeddings.forEach { registeredEmbedding ->
                val confidence = calculateSimilarity(embedding, registeredEmbedding)
                if (confidence > bestConfidence && confidence > RECOGNITION_THRESHOLD) {
                    bestMatch = name
                    bestConfidence = confidence
                }
            }
        }

        return Pair(bestMatch, bestConfidence)
    }

    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }

    fun shutdown() {
        faceDetector.close()
        faceNetInterpreter.close()
    }

    data class DetectedFace(
        val name: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    companion object {
        private const val RECOGNITION_THRESHOLD = 0.9f // Increased threshold for better accuracy
    }
}
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.RectF
//import android.util.Log
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.face.FaceDetection
//import com.google.mlkit.vision.face.FaceDetector
//import com.google.mlkit.vision.face.FaceDetectorOptions
//import org.tensorflow.lite.Interpreter
//import org.tensorflow.lite.support.common.FileUtil
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.tasks.await
//import kotlinx.coroutines.withContext
//import java.io.File
//import org.json.JSONObject
//import org.json.JSONArray
//import java.io.FileInputStream
//import java.nio.channels.FileChannel
//
//class FaceRecognitionModel(private val context: Context) {
//    private val faceDetector: FaceDetector
//    private val faceNetInterpreter: Interpreter
//    private val registeredFaces = mutableMapOf<String, FloatArray>()
//    private val embeddingDim = 128
//    private val inputSize = 160
//    private val storageFile = File(context.filesDir, "registered_faces.json")
//
//    init {
//        val options = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
//            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
//            .setMinFaceSize(0.15f)
//            .enableTracking()
//            .build()
//
//        faceDetector = FaceDetection.getClient(options)
//        faceNetInterpreter = Interpreter(loadModelFile(), Interpreter.Options().apply {
//            setNumThreads(4)
//            setUseNNAPI(true)
//        })
//        loadRegisteredFaces()
//    }
//
//    private fun loadModelFile(): ByteBuffer {
//        val assetFileDescriptor = context.assets.openFd("faceNet.tflite")
//        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        return fileChannel.map(
//            FileChannel.MapMode.READ_ONLY,
//            assetFileDescriptor.startOffset,
//            assetFileDescriptor.declaredLength
//        ).apply {
//            assetFileDescriptor.close()
//        }
//    }
//
//
//    private fun loadRegisteredFaces() {
//        try {
//            if (storageFile.exists()) {
//                val jsonString = storageFile.readText()
//                val jsonObject = JSONObject(jsonString)
//
//                jsonObject.keys().forEach { name ->
//                    val embeddingArray = jsonObject.getJSONArray(name)
//                    val embedding = FloatArray(embeddingDim)
//                    for (i in 0 until embeddingDim) {
//                        embedding[i] = embeddingArray.getDouble(i).toFloat()
//                    }
//                    registeredFaces[name] = embedding
//                }
//                Log.d("FaceRecognition", "Loaded ${registeredFaces.size} faces from storage")
//            }
//        } catch (e: Exception) {
//            Log.e("FaceRecognition", "Error loading registered faces", e)
//        }
//    }
//
//    private fun saveRegisteredFaces() {
//        try {
//            val jsonObject = JSONObject()
//            registeredFaces.forEach { (name, embedding) ->
//                val embeddingArray = JSONArray()
//                embedding.forEach { embeddingArray.put(it.toDouble()) }
//                jsonObject.put(name, embeddingArray)
//            }
//
//            storageFile.writeText(jsonObject.toString())
//            Log.d("FaceRecognition", "Saved ${registeredFaces.size} faces to storage")
//        } catch (e: Exception) {
//            Log.e("FaceRecognition", "Error saving registered faces", e)
//        }
//    }
//
//    suspend fun detectFaces(image: Bitmap): List<DetectedFace> = withContext(Dispatchers.IO) {
//        try {
//            val inputImage = InputImage.fromBitmap(image, 0)
//            val faces = faceDetector.process(inputImage).await()
//
//            faces.mapNotNull { face ->
//                try {
//                    val faceBitmap = extractFaceBitmap(image, face.boundingBox)
//                    val embedding = getFaceEmbedding(faceBitmap)
//                    val (name, confidence) = findMatchingFace(embedding)
//
//                    DetectedFace(
//                        name = name,
//                        confidence = confidence,
//                        boundingBox = RectF(face.boundingBox)
//                    )
//                } catch (e: Exception) {
//                    Log.e("FaceRecognition", "Error processing face", e)
//                    null
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("FaceRecognition", "Error detecting faces", e)
//            emptyList()
//        }
//    }
//
//    suspend fun registerFace(image: Bitmap, name: String): Boolean = withContext(Dispatchers.IO) {
//        try {
//            val inputImage = InputImage.fromBitmap(image, 0)
//            val faces = faceDetector.process(inputImage).await()
//
//            if (faces.size != 1) {
//                Log.w("FaceRecognition", "Expected 1 face, found ${faces.size}")
//                return@withContext false
//            }
//
//            val face = faces[0]
//            val faceBitmap = extractFaceBitmap(image, face.boundingBox)
//            val embedding = getFaceEmbedding(faceBitmap)
//
//            registeredFaces[name] = embedding
//            saveRegisteredFaces()
//            Log.d("FaceRecognition", "Successfully registered face for $name")
//            true
//        } catch (e: Exception) {
//            Log.e("FaceRecognition", "Error registering face", e)
//            false
//        }
//    }
//
//    private fun extractFaceBitmap(image: Bitmap, boundingBox: android.graphics.Rect): Bitmap {
//        // Add padding to the face region for better recognition
//        val padding = (boundingBox.width() * 0.1).toInt()
//        val left = (boundingBox.left - padding).coerceAtLeast(0)
//        val top = (boundingBox.top - padding).coerceAtLeast(0)
//        val width = (boundingBox.width() + 2 * padding).coerceAtMost(image.width - left)
//        val height = (boundingBox.height() + 2 * padding).coerceAtMost(image.height - top)
//
//        return Bitmap.createBitmap(
//            image,
//            left,
//            top,
//            width,
//            height
//        ).let { faceBitmap ->
//            Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
//        }
//    }
//
//    private fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
//        val inputArray = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }
//
//        // Normalize pixel values to [-1, 1]
//        for (x in 0 until inputSize) {
//            for (y in 0 until inputSize) {
//                val pixel = faceBitmap.getPixel(x, y)
//                inputArray[0][x][y][0] = (((pixel shr 16) and 0xFF) / 127.5f) - 1f
//                inputArray[0][x][y][1] = (((pixel shr 8) and 0xFF) / 127.5f) - 1f
//                inputArray[0][x][y][2] = ((pixel and 0xFF) / 127.5f) - 1f
//            }
//        }
//
//        val outputArray = Array(1) { FloatArray(embeddingDim) }
//        faceNetInterpreter.run(inputArray, outputArray)
//
//        // Normalize the embedding
//        val embedding = outputArray[0]
//        var norm = 0f
//        for (value in embedding) {
//            norm += value * value
//        }
//        norm = kotlin.math.sqrt(norm)
//        for (i in embedding.indices) {
//            embedding[i] /= norm
//        }
//
//        return embedding
//    }
//
//    private fun findMatchingFace(embedding: FloatArray): Pair<String, Float> {
//        var bestMatch = "Unknown"
//        var bestConfidence = 0f
//
//        registeredFaces.forEach { (name, registeredEmbedding) ->
//            val confidence = calculateSimilarity(embedding, registeredEmbedding)
//            if (confidence > bestConfidence && confidence > RECOGNITION_THRESHOLD) {
//                bestMatch = name
//                bestConfidence = confidence
//            }
//        }
//
//        return Pair(bestMatch, bestConfidence)
//    }
//
//    private fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
//        var dotProduct = 0f
//        var norm1 = 0f
//        var norm2 = 0f
//
//        for (i in embedding1.indices) {
//            dotProduct += embedding1[i] * embedding2[i]
//            norm1 += embedding1[i] * embedding1[i]
//            norm2 += embedding2[i] * embedding2[i]
//        }
//
//        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
//    }
//
//    fun shutdown() {
//        faceDetector.close()
//        faceNetInterpreter.close()
//    }
//
//    data class DetectedFace(
//        val name: String,
//        val confidence: Float,
//        val boundingBox: RectF
//    )
//
//    companion object {
//        private const val RECOGNITION_THRESHOLD = 0.7f
//    }
//}