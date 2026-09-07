package com.example.screentimeguard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ThresholdSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val trackedPackages = remember { TrackedAppsRepository.getTrackedApps(context).toList() }

    // État local : packageName -> texte saisi (en minutes)
    val thresholds = remember {
        mutableStateMapOf<String, String>().apply {
            trackedPackages.forEach { pkg ->
                val current = ThresholdsRepository.getThresholdMinutes(context, pkg)
                put(pkg, if (current > 0) current.toString() else "")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Définir les limites (en minutes/jour)",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(trackedPackages) { pkg ->
                val appName = try {
                    context.packageManager
                        .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                        .toString()
                } catch (e: Exception) {
                    pkg
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Text(text = appName, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = thresholds[pkg] ?: "",
                        onValueChange = { thresholds[pkg] = it.filter { c -> c.isDigit() } },
                        label = { Text("min") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true
                    )
                }
            }
        }

        Button(
            onClick = {
                thresholds.forEach { (pkg, value) ->
                    val minutes = value.toIntOrNull() ?: 0
                    ThresholdsRepository.setThresholdMinutes(context, pkg, minutes)
                }
                onBack()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Enregistrer les limites")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Retour")
        }
    }
}