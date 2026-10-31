package com.example.screentimeguard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Nombre d'applications nommées (légende + couleurs) dans les graphiques.
// Les suivantes sont regroupées dans une couleur grise : « +N more ».
private const val MAX_CHART_APPS = 5

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf<List<AppSession>>(emptyList()) }
    var appNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(StatsPeriod.DAY) }

    LaunchedEffect(Unit) {
        val database = AppDatabase.getDatabase(context)
        val all = database.appSessionDao().getAllSessions()
        val pm = context.packageManager

        appNames = all.map { it.packageName }.distinct().associateWith { pkg ->
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }
        sessions = all
        loaded = true
    }

    val now = remember(sessions) { System.currentTimeMillis() }
    val data = remember(sessions, appNames, period, now) {
        buildStatsData(
            sessions = sessions,
            nameOf = { pkg -> appNames[pkg] ?: pkg },
            period = period,
            now = now,
            maxTopApps = MAX_CHART_APPS
        )
    }

    // Une couleur par application, identique dans le cadran, les barres et la légende.
    val colorMap = remember(data) {
        val palette = AppColors.ChartPalette
        data.topPackages
            .mapIndexed { index, pkg -> pkg to palette[index % palette.size] }
            .toMap()
    }
    val colorOf: (String) -> Color = remember(colorMap) {
        val lookup: (String) -> Color = { key -> colorMap[key] ?: AppColors.ChartRemainder }
        lookup
    }
    val topApps = data.summaries.take(MAX_CHART_APPS)
    val otherCount = (data.summaries.size - MAX_CHART_APPS).coerceAtLeast(0)

    val subtitle = remember(period, data.range) {
        when (period) {
            StatsPeriod.DAY -> "Last 24 hours"
            StatsPeriod.WEEK -> {
                val f = SimpleDateFormat("MMM d", Locale.getDefault())
                "${f.format(Date(data.range.start))} – ${f.format(Date(data.range.end - 1))}"
            }
            StatsPeriod.MONTH ->
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(data.range.start))
        }
    }
    val summaryTitle = when (period) {
        StatsPeriod.DAY -> "24h Summary"
        StatsPeriod.WEEK -> "Week Summary"
        StatsPeriod.MONTH -> "Month Summary"
    }

    ScreenScaffold(
        title = "Screen Time",
        subtitle = subtitle,
        onBack = onBack
    ) {
        if (loaded && sessions.isEmpty()) {
            Text(
                text = "Aucune donnée pour l'instant. Utilisez une app trackée puis revenez ici.",
                color = AppColors.TextPrimary
            )
        } else if (loaded) {
            PeriodSelector(selected = period, onSelect = { period = it })
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "chart") {
                    if (period == StatsPeriod.DAY) {
                        ClockCard(
                            data = data,
                            topApps = topApps,
                            otherCount = otherCount,
                            colorOf = colorOf,
                            nowMs = now
                        )
                    } else {
                        BarCard(
                            period = period,
                            data = data,
                            topApps = topApps,
                            otherCount = otherCount,
                            colorOf = colorOf
                        )
                    }
                }

                item(key = "summary-title") {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = summaryTitle,
                            color = AppColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (data.summaries.isEmpty()) {
                    item(key = "summary-empty") {
                        Text(
                            text = "No usage recorded for this period.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(data.summaries, key = { it.packageName }) { summary ->
                        AppUsageRow(summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(summary: AppUsageSummary) {
    AppCard(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = summary.packageName,
                appName = summary.appName,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.appName,
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Opened ${summary.sessionCount} times",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = formatDuration(summary.totalDurationMs),
                color = AppColors.TextPrimary,
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
