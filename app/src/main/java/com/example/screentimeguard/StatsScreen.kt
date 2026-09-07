package com.example.screentimeguard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val totalDurationMs: Long,
    val sessionCount: Int
)

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var summaries by remember { mutableStateOf<List<AppUsageSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        val database = AppDatabase.getDatabase(context)
        val allSessions = database.appSessionDao().getAllSessions()

        val grouped = allSessions.groupBy { it.packageName }
        val pm = context.packageManager

        summaries = grouped.map { (packageName, sessions) ->
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
            AppUsageSummary(
                packageName = packageName,
                appName = appName,
                totalDurationMs = sessions.sumOf { it.durationMs },
                sessionCount = sessions.size
            )
        }.sortedByDescending { it.totalDurationMs }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Statistiques d'utilisation",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (summaries.isEmpty()) {
            Text("Aucune donnée pour l'instant. Utilisez une app trackée puis revenez ici.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(summaries) { summary ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = summary.appName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(text = "Temps total : ${formatDuration(summary.totalDurationMs)}")
                            Text(text = "Sessions : ${summary.sessionCount}")
                        }
                    }
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Retour")
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}min"
        minutes > 0 -> "${minutes}min ${seconds}s"
        else -> "${seconds}s"
    }
}