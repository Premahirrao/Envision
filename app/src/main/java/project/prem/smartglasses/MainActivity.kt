//package project.prem.smartglasses
//
//import android.content.Intent
//import android.os.Bundle
//import android.speech.tts.TextToSpeech
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import java.util.*
//
//class MainActivity : ComponentActivity() {
//    private lateinit var tts: TextToSpeech
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Initialize Text-to-Speech
//        tts = TextToSpeech(this) { status ->
//            if (status == TextToSpeech.SUCCESS) {
//                tts.language = Locale.US
//            }
//        }
//
//        setContent {
//            AccessibilityAppUI(
//                onFaceRecognition = {
//                    tts.speak("Face Recognition started", TextToSpeech.QUEUE_FLUSH, null, null)
//                },
//                onCurrencyRecognition = {
//                    tts.speak("Currency Recognition started", TextToSpeech.QUEUE_FLUSH, null, null)
//                },
//                onTextToSpeech = {
//                    tts.speak("Text to Speech started", TextToSpeech.QUEUE_FLUSH, null, null)
//                }
//            )
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        tts.stop()
//        tts.shutdown()
//    }
//}
//
//@Composable
//fun AccessibilityAppUI(
//    onFaceRecognition: () -> Unit,
//    onCurrencyRecognition: () -> Unit,
//    onTextToSpeech: () -> Unit
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(20.dp),
//        verticalArrangement = Arrangement.Center,
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Text(
//            text = "Envision",
//            fontSize = 24.sp,
//            modifier = Modifier.padding(bottom = 20.dp)
//        )
//
//        FeatureButton("Face Recognition", onFaceRecognition, MaterialTheme.colorScheme.primary)
//        FeatureButton("Currency Recognition", onCurrencyRecognition, MaterialTheme.colorScheme.secondary)
//        FeatureButton("Text to Speech", onTextToSpeech, MaterialTheme.colorScheme.tertiary)
//    }
//}
//
//@Composable
//fun FeatureButton(text: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color) {
//    Button(
//        onClick = onClick,
//        colors = ButtonDefaults.buttonColors(containerColor = color),
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 10.dp),
//        shape = RoundedCornerShape(10.dp)
//    ) {
//        Text(text = text, fontSize = 18.sp)
//    }
//}
package project.prem.smartglasses

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import project.prem.smartglasses.ui.screens.*
import project.prem.smartglasses.ui.theme.SmartGlassesTheme

class MainActivity : ComponentActivity() {
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Some permissions were denied. App functionality may be limited.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            SmartGlassesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController)
                        }
                        composable("face_recognition") {
                            FaceRecognitionScreen(navController)
                        }
                        composable("ocr") {
                            OCRScreen(navController)
                        }
                        composable("maps") {
                            MapsScreen(onNavigateBack = {
                                navController.navigateUp()
                            })
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}
//
//import android.Manifest
//import android.content.Intent
//import android.os.Bundle
//import android.speech.RecognizerIntent
//import android.speech.tts.TextToSpeech
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import java.util.*
//
//class MainActivity : ComponentActivity() {
//    private lateinit var tts: TextToSpeech
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Initialize Text-to-Speech
//        tts = TextToSpeech(this) { status ->
//            if (status == TextToSpeech.SUCCESS) {
//                tts.language = Locale.US
//            }
//        }
//
//        setContent {
//            EnvisionAppUI(
//                onFaceRecognition = { speakText("Face Recognition started") },
//                onCurrencyRecognition = { speakText("Currency Recognition started") },
//                onTextToSpeech = { speakText("Text to Speech started") },
//                onCaptureFace = { speakText("Capture Face started") },
//                onVoiceCommand = { requestVoicePermission() }
//            )
//        }
//    }
//
//    private fun speakText(message: String) {
//        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
//    }
//
//    // Request voice permission
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted ->
//        if (isGranted) {
//            startVoiceRecognition()
//        } else {
//            speakText("Voice permission denied")
//        }
//    }
//
//    private fun requestVoicePermission() {
//        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
//    }
//
//    // Start Speech Recognition
//    private val speechRecognitionLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        val data = result.data
//        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
//        matches?.let {
//            val command = it[0].lowercase(Locale.ROOT)
//            when {
//                "start face recognition" in command -> speakText("Face Recognition started")
//                "start currency recognition" in command -> speakText("Currency Recognition started")
//                "start text to speech" in command -> speakText("Text to Speech started")
//                "start capture face" in command -> speakText("Capture Face started")
//                else -> speakText("Command not recognized")
//            }
//        }
//    }
//
//    private fun startVoiceRecognition() {
//        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
//            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command...")
//        }
//        speechRecognitionLauncher.launch(intent)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        tts.stop()
//        tts.shutdown()
//    }
//}
//@Composable
//fun EnvisionAppUI(
//    onFaceRecognition: () -> Unit,
//    onCurrencyRecognition: () -> Unit,
//    onTextToSpeech: () -> Unit,
//    onCaptureFace: () -> Unit,
//    onVoiceCommand: () -> Unit
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(20.dp),
//        verticalArrangement = Arrangement.Center,
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Text(
//            text = "Envision",
//            fontSize = 30.sp,
//            color = Color.Black,
//            modifier = Modifier.padding(bottom = 20.dp)
//        )
//
//        CustomButton("Face Recognition", onFaceRecognition, Color(0xFF1976D2))
//        CustomButton("Currency Recognition", onCurrencyRecognition, Color(0xFF388E3C))
//        CustomButton("Text to Speech", onTextToSpeech, Color(0xFFF57C00))
//        CustomButton("Capture Face", onCaptureFace, Color(0xFFD32F2F))
//        CustomButton("Voice Command", onVoiceCommand, Color(0xFF7B1FA2))
//    }
//}
//
//@Composable
//fun CustomButton(text: String, onClick: () -> Unit, color: Color) {
//    Button(
//        onClick = onClick,
//        colors = ButtonDefaults.buttonColors(containerColor = color),
//        modifier = Modifier
//            .fillMaxWidth(0.8f)
//            .padding(vertical = 10.dp)
//            .height(60.dp),
//        shape = RoundedCornerShape(20.dp)
//    ) {
//        Text(text = text, fontSize = 20.sp, color = Color.White)
//    }
//}