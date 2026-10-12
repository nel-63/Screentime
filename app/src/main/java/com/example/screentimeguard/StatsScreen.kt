package com.example.screentimeguard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val category: AppCategory = categorize(packageName)
)

enum class AppCategory(val label: String, val color: Color) {
    SOCIAL("Social", Color(0xFFB3261E)),
    ENTERTAINMENT("Entertainment", Color(0xFFE94B4B)),
    PRODUCTIVITY("Productivity", Color(0xFFE0E0E0)),
    GAMES("Games", Color(0xFF7A1F1F)),
    OTHER("Other", Color(0xFF4A4A4A))
}

// Heuristique simple par nom de package. À affiner selon les apps réellement trackées.
private fun categorize(packageName: String): AppCategory = when {
    packageName.contains("instagram") || packageName.contains("facebook") ||
            packageName.contains("twitter") || packageName.contains("tiktok") -> AppCategory.SOCIAL
    packageName.contains("youtube") || packageName.contains("netflix") ||
            packageName.contains("spotify") -> AppCategory.ENTERTAINMENT
    packageName.contains("slack") || packageName.contains("gmail") ||
            packageName.contains("office") || packageName.contains("docs") -> AppCategory.PRODUCTIVITY
    packageName.contains("game") || packageName.contains("candy") -> AppCategory.GAMES
    else -> AppCategory.OTHER
}

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

    val totalMs = summaries.sumOf { it.totalDurationMs }
    val byCategory = summaries.groupBy { it.category }
        .mapValues { (_, apps) -> apps.sumOf { it.totalDurationMs } }
        .toList()
        .sortedByDescending { it.second }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .padding(16.dp)
    ) {
        Text(
            text = "Screen Time",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (summaries.isEmpty()) {
            Text(
                text = "Aucune donnée pour l'instant. Utilisez une app trackée puis revenez ici.",
                color = Color.White
            )
        } else {
            TotalTimeCard(totalMs = totalMs, byCategory = byCategory)

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "24h Summary",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(summaries) { summary ->
                    AppUsageRow(summary)
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

@Composable
private fun TotalTimeCard(totalMs: Long, byCategory: List<Pair<AppCategory, Long>>) {
    Surface(
        color = Color(0xFF1A1A1D),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                DonutChart(
                    values = byCategory.map { it.second.toFloat() },
                    colors = byCategory.map { it.first.color },
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Text(
                        text = formatDuration(totalMs),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                byCategory.forEach { (category, ms) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(category.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(category.label, color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            formatDuration(ms),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = values.sum().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        var startAngle = -90f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)

        values.forEachIndexed { index, value ->
            val sweep = 360f * (value / total)
            drawArc(
                color = colors.getOrElse(index) { Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun AppUsageRow(summary: AppUsageSummary) {
    Surface(
        color = Color(0xFF1A1A1D),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(summary.category.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = summary.appName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.appName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "Opened ${summary.sessionCount} times",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }
            Text(
                text = formatDuration(summary.totalDurationMs),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
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