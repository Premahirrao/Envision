package project.prem.smartglasses.ui.screens

import android.util.Log
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import project.prem.smartglasses.ml.OCRModel
import project.prem.smartglasses.utils.CameraManager
import project.prem.smartglasses.utils.TextToSpeech

@Composable
fun OCRScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { CameraManager(context) }
    val ocrModel = remember { OCRModel(context) }
    val tts = remember { TextToSpeech(context) }
    var lastSpokenPrice by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
            ocrModel.shutdown()
            tts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        cameraManager.initialize()
        cameraManager.startCamera(
            previewView = previewView,
            frameInterval = 1000L,
            onFrameAvailable = { bitmap ->
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val prices = ocrModel.detectPrices(bitmap)
                        prices.firstOrNull()?.let { price ->
                            if (price != lastSpokenPrice) {
                                withContext(Dispatchers.Main) {
                                    tts.speak("Price: $price")
                                    lastSpokenPrice = price
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OCR", "Price detection error", e)
                    }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        )

        Text(
            text = if (lastSpokenPrice.isNotEmpty()) "Detected: $lastSpokenPrice" else "Point camera at price tag",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        )

        Button(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Text("Back")
        }
    }
}