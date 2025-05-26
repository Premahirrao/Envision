package project.prem.smartglasses.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import project.prem.smartglasses.utils.SpeechRecognizer
import project.prem.smartglasses.utils.TextToSpeech
import project.prem.smartglasses.utils.EmergencyManager

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val speechRecognizer = remember { SpeechRecognizer(context) }
    val tts = remember { TextToSpeech(context) }
    val emergencyManager = remember { EmergencyManager(context) }
    var isListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
            tts.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Smart Assistant",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Choose your preferred functionality or use voice commands",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        ElevatedButton(
            onClick = { navController.navigate("face_recognition") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                "Start Face Recognition",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        ElevatedButton(
            onClick = { navController.navigate("ocr") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                "Start OCR",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        ElevatedButton(
            onClick = { navController.navigate("maps") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                "Start Navigation",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isListening)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Button(
                onClick = {
                    isListening = true
                    speechRecognizer.startListening { command ->
                        when {
                            command.contains("emergency", ignoreCase = true) ||
                                    command.contains("help", ignoreCase = true) -> {
                                tts.speak("Sending emergency alert")
                                emergencyManager.sendEmergencyAlert()
                            }
                            command.contains("face", ignoreCase = true) -> {
                                tts.speak("Opening face recognition")
                                navController.navigate("face_recognition")
                            }
                            command.contains("ocr", ignoreCase = true) ||
                                    command.contains("price", ignoreCase = true) -> {
                                tts.speak("Opening price scanner")
                                navController.navigate("ocr")
                            }
                            command.contains("map", ignoreCase = true) ||
                                    command.contains("navigation", ignoreCase = true) -> {
                                tts.speak("Opening navigation")
                                navController.navigate("maps")
                            }
                        }
                        isListening = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled = !isListening,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (isListening) "Listening..." else "Speak Command",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}