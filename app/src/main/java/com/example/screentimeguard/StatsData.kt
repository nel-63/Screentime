package com.example.screentimeguard

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Calculs de la page Statistiques (sans aucune partie visuelle).
 * Tout est découpé en « tranches » de temps : on ne compte que la part d'une session
 * qui tombe réellement dans la période choisie (24 h, semaine ou mois).
 */

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val totalDurationMs: Long,
    val sessionCount: Int
)

enum class StatsPeriod(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

/** Intervalle [start, end[ en millisecondes. */
data class TimeRange(val start: Long, val end: Long)

/** Clé utilisée pour regrouper toutes les applications hors « top » dans une même couleur. */
const val OTHERS_KEY = "__others__"

data class BarSegment(val key: String, val ms: Long)

/** Une barre du diagramme (un jour). Les segments vont du bas vers le haut. */
data class BarSlot(
    val label: String,
    val showLabel: Boolean,
    val dayStart: Long,
    val segments: List<BarSegment>,
    val totalMs: Long,
    val isToday: Boolean,
    val isFuture: Boolean
)

/** Un morceau de cadran : de [startHour] sur 12 h, pendant [sweepHours] heures. */
data class ClockArc(
    val isPm: Boolean,
    val startHour: Float,
    val sweepHours: Float,
    val packageName: String
)

data class StatsData(
    val range: TimeRange,
    val summaries: List<AppUsageSummary>,
    val topPackages: List<String>,
    val totalMs: Long,
    val bars: List<BarSlot>,
    val arcs: List<ClockArc>
)

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

private fun startOfDay(timeMs: Long): Calendar =
    Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

/*
 * DAY: les 24 dernières heures (glissantes).
 * WEEK: la semaine en cours, du lundi 00:00 au lundi suivant.
 * MONTH : le mois en cours, du 1er 00:00 au 1er du mois suivant.
 */
fun periodRange(period: StatsPeriod, now: Long): TimeRange = when (period) {
    StatsPeriod.DAY -> TimeRange(now - DAY_MS, now)
    StatsPeriod.WEEK -> {
        val cal = startOfDay(now)
        // Calendar : dimanche = 1, lundi = 2 ... samedi = 7jours écoulés depuis lundi
        val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        TimeRange(start, cal.timeInMillis)
    }
    StatsPeriod.MONTH -> {
        val cal = startOfDay(now)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        TimeRange(start, cal.timeInMillis)
    }
}

/** Durée (ms) de la session qui tombe dans [from, to[. */
fun overlapMs(session: AppSession, from: Long, to: Long): Long {
    val a = maxOf(session.startTime, from)
    val b = minOf(session.endTime, to)
    return if (b > a) b - a else 0L
}

fun buildBarSlots(
    sessions: List<AppSession>,
    period: StatsPeriod,
    range: TimeRange,
    topPackages: List<String>,
    now: Long
): List<BarSlot> {
    val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val rank = topPackages.withIndex().associate { it.value to it.index }
    val slots = mutableListOf<BarSlot>()

    val cal = Calendar.getInstance().apply { timeInMillis = range.start }
    while (cal.timeInMillis < range.end) {
        val slotStart = cal.timeInMillis
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val label = if (period == StatsPeriod.WEEK) {
            weekdayFormat.format(cal.time).take(3)
        } else {
            dayOfMonth.toString()
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val slotEnd = cal.timeInMillis

        val perKey = HashMap<String, Long>()
        for (s in sessions) {
            val ms = overlapMs(s, slotStart, slotEnd)
            if (ms > 0) {
                val key = if (rank.containsKey(s.packageName)) s.packageName else OTHERS_KEY
                perKey[key] = (perKey[key] ?: 0L) + ms
            }
        }
        // Les applications les plus utilisées en bas, « autres » tout en haut.
        val segments = perKey.entries
            .sortedBy { if (it.key == OTHERS_KEY) Int.MAX_VALUE else rank.getValue(it.key) }
            .map { BarSegment(it.key, it.value) }

        slots += BarSlot(
            label = label,
            showLabel = period == StatsPeriod.WEEK || dayOfMonth == 1 || dayOfMonth % 5 == 0,
            dayStart = slotStart,
            segments = segments,
            totalMs = segments.sumOf { it.ms },
            isToday = now >= slotStart && now < slotEnd,
            isFuture = slotStart > now
        )
    }
    return slots
}

/** Prochain passage à 00:00 ou 12:00 strictement après [timeMs]. */
private fun nextHalfDayBoundary(timeMs: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (hour < 12) {
        cal.set(Calendar.HOUR_OF_DAY, 12)
    } else {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}

/**
 * Place chaque session sur un cadran de 12 h. Une session qui traverse midi ou minuit
 * est coupée en deux morceaux (un sur AM, un sur PM).
 */
fun buildClockArcs(sessions: List<AppSession>, range: TimeRange): List<ClockArc> {
    val arcs = mutableListOf<ClockArc>()
    val cal = Calendar.getInstance()
    for (s in sessions) {
        var cur = maxOf(s.startTime, range.start)
        val end = minOf(s.endTime, range.end)
        while (cur < end) {
            val pieceEnd = minOf(end, nextHalfDayBoundary(cur))
            cal.timeInMillis = cur
            // Calendar.HOUR va de 0 à 11 : 0 correspond au « 12 » en haut du cadran.
            val startHour = cal.get(Calendar.HOUR) +
                    cal.get(Calendar.MINUTE) / 60f +
                    cal.get(Calendar.SECOND) / 3600f +
                    cal.get(Calendar.MILLISECOND) / 3_600_000f
            arcs += ClockArc(
                isPm = cal.get(Calendar.AM_PM) == Calendar.PM,
                startHour = startHour,
                sweepHours = (pieceEnd - cur) / HOUR_MS.toFloat(),
                packageName = s.packageName
            )
            cur = pieceEnd
        }
    }
    return arcs
}

fun buildStatsData(
    sessions: List<AppSession>,
    nameOf: (String) -> String,
    period: StatsPeriod,
    now: Long,
    maxTopApps: Int = 5
): StatsData {
    val range = periodRange(period, now)
    val inRange = sessions.filter { overlapMs(it, range.start, range.end) > 0 }

    val summaries = inRange.groupBy { it.packageName }
        .map { (pkg, list) ->
            AppUsageSummary(
                packageName = pkg,
                appName = nameOf(pkg),
                totalDurationMs = list.sumOf { overlapMs(it, range.start, range.end) },
                sessionCount = list.size
            )
        }
        .sortedByDescending { it.totalDurationMs }

    val topPackages = summaries.take(maxTopApps).map { it.packageName }

    return StatsData(
        range = range,
        summaries = summaries,
        topPackages = topPackages,
        totalMs = summaries.sumOf { it.totalDurationMs },
        bars = if (period == StatsPeriod.DAY) emptyList()
        else buildBarSlots(inRange, period, range, topPackages, now),
        arcs = if (period == StatsPeriod.DAY) buildClockArcs(inRange, range) else emptyList()
    )
}

/* Pas de la grille verticale : on vise 4 graduations au maximum. */
fun axisStepMs(maxMs: Long): Long {
    if (maxMs <= 0L) return HOUR_MS
    val candidatesMin = listOf(5, 10, 15, 20, 30, 60, 120, 180, 240, 360, 480, 720)
    for (m in candidatesMin) {
        if (maxMs <= m * 60_000L * 4) return m * 60_000L
    }
    return 720 * 60_000L
}

/*Sommet de l'axe : le plus petit multiple du pas qui contient la plus grande barre. */
fun axisTopMs(maxMs: Long, stepMs: Long): Long {
    val steps = (maxMs + stepMs - 1) / stepMs
    return maxOf(1L, steps) * stepMs
}

fun formatAxis(ms: Long): String {
    val h = ms / HOUR_MS
    val m = (ms % HOUR_MS) / 60_000L
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h${m}"
    }
}
