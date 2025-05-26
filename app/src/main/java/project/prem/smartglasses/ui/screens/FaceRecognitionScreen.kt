package project.prem.smartglasses.ui.screens

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import project.prem.smartglasses.ml.FaceRecognitionModel
import project.prem.smartglasses.utils.CameraManager
import project.prem.smartglasses.utils.TextToSpeech

@Composable
fun FaceRecognitionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }

    // Model and Utilities
    val cameraManager = remember { CameraManager(context) }
    val faceModel = remember { FaceRecognitionModel(context) }
    val tts = remember { TextToSpeech(context) }

    // State Management
    var detectedFaces by remember { mutableStateOf<List<FaceRecognitionModel.DetectedFace>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showRegistration by remember { mutableStateOf(false) }
    var regName by remember { mutableStateOf("") }
    var regStatus by remember { mutableStateOf<String?>(null) }
    var lastSpoken by remember { mutableStateOf("") }

    // Lifecycle Management
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
            faceModel.shutdown()
            tts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        cameraManager.initialize()
        cameraManager.startCamera(
            previewView = previewView,
            frameInterval = 2000L
        ) { bitmap ->
            if (!isProcessing) {
                isProcessing = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val start = System.currentTimeMillis()

                        val faces = faceModel.detectFaces(bitmap)
                        val end = System.currentTimeMillis()
                        Log.d("Time", "Inference took ${end - start}ms")

                        withContext(Dispatchers.Main) {
                            detectedFaces = faces
                            faces.firstOrNull()?.let { face ->
                                if (face.name != "Unknown" && face.name != lastSpoken) {
                                    tts.speak("Detected ${face.name}")
                                    lastSpoken = face.name
                                }
                            }
                        }
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    // Registration Dialog
    if (showRegistration) {
        AlertDialog(
            onDismissRequest = { showRegistration = false },
            title = { Text("Register New Face") },
            text = {
                Column {
                    regStatus?.let {
                        Text(
                            text = it,
                            color = if (it.startsWith("Success")) Color.Green else Color.Red
                        )
                    }
                    TextField(
                        value = regName,
                        onValueChange = { regName = it },
                        label = { Text("Enter Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            cameraManager.captureFrame()?.let { frame ->
                                val success = faceModel.registerFace(frame, regName)
                                regStatus = if (success) {
                                    "Successfully registered $regName"
                                } else {
                                    "Failed to register face"
                                }
                                if (success) {
                                    regName = ""
                                    showRegistration = false
                                }
                            } ?: run {
                                regStatus = "Failed to capture image"
                            }
                        }
                    },
                    enabled = regName.isNotEmpty()
                ) {
                    Text("Register")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showRegistration = false
                    regName = ""
                    regStatus = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Main UI
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        ) { view ->
            view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            view.scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        // Face Bounding Boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedFaces.forEach { face ->
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(
                        face.boundingBox.left * size.width,
                        face.boundingBox.top * size.height
                    ),
                    size = Size(
                        face.boundingBox.width() * size.width,
                        face.boundingBox.height() * size.height
                    ),
                    style = Stroke(width = 4f)
                )
            }
        }

        // Recognition Results
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Text(
                text = "Face Recognition",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )

            detectedFaces.forEach { face ->
                Text(
                    text = "${face.name} (${(face.confidence * 100).toInt()}%)",
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showRegistration = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Register Face")
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
        }
    }
}