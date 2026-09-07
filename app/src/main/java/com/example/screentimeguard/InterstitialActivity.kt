package com.example.screentimeguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.screentimeguard.ui.theme.ScreenTimeGuardTheme
import kotlinx.coroutines.delay

class InterstitialActivity : ComponentActivity() {

    private var trackedPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra("appName") ?: "cette application"
        trackedPackageName = intent.getStringExtra("packageName")

        onBackPressedDispatcher.addCallback(this) {
            goToHomeScreen()
        }

        setContent {
            ScreenTimeGuardTheme {
                InterstitialScreen(
                    appName = appName,
                    waitSeconds = 10,
                    onContinue = { finish() },
                    onCancel = { goToHomeScreen() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // On ne libère l'overlay que si l'activité est VRAIMENT terminée
        // (pas juste recréée suite à une rotation d'écran par exemple),
        // et seulement si c'est bien notre app suivie qui est concernée.
        if (!isChangingConfigurations && trackedPackageName != null &&
            OverlayState.activeFor == trackedPackageName
        ) {
            OverlayState.activeFor = null
        }
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

// Les 3 étapes possibles de l'écran
private enum class InterstitialStep {
    QUESTION,   // l'utilisateur doit répondre avant de pouvoir continuer
    WAITING,    // compte à rebours en cours
    READY       // peut continuer
}

@Composable
fun InterstitialScreen(
    appName: String,
    waitSeconds: Int,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(InterstitialStep.QUESTION) }
    var answer by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf(waitSeconds) }

    // Le compte à rebours ne démarre QUE quand on passe à l'étape WAITING
    LaunchedEffect(step) {
        if (step == InterstitialStep.WAITING) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            step = InterstitialStep.READY
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            InterstitialStep.QUESTION -> {
                Text(
                    text = "🤔",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Pourquoi voulez-vous ouvrir $appName ?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    placeholder = { Text("Votre réponse...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { step = InterstitialStep.WAITING },
                    enabled = answer.trim().length >= 3 // réponse minimale requise
                ) {
                    Text("Valider")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onCancel) {
                    Text("Annuler, retour à l'accueil")
                }
            }

            InterstitialStep.WAITING -> {
                Text(
                    text = "⏳",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Patiente $secondsLeft s...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCancel) {
                    Text("Annuler, retour à l'accueil")
                }
            }

            InterstitialStep.READY -> {
                Text(
                    text = "✅",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Tu peux continuer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onContinue) {
                    Text("Continuer")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onCancel) {
                    Text("Annuler, retour à l'accueil")
                }
            }
        }
    }
}