package com.example.screentimeguard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val GridColor = Color(0xFF2A2A2E)
private val TrackColor = Color(0xFF26262A)
private val TickDim = Color(0xFF3A3A3E)
private val TickBright = Color(0xFF6A6A6E)
private val FutureText = Color(0xFF5E5E63)

// Marges du diagramme en barres (partagées entre le dessin et la détection du toucher).
private val ChartLeftPad = 34.dp
private val ChartRightPad = 6.dp
private val ChartTopPad = 10.dp
private val ChartBottomPad = 26.dp

// ---------------------------------------------------------------------------------------------
// Sélecteur Jour / Semaine / Mois
// ---------------------------------------------------------------------------------------------

@Composable
fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .padding(4.dp)
    ) {
        StatsPeriod.values().forEach { period ->
            val isSelected = period == selected
            val t by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(durationMillis = 220),
                label = "periodPill"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Red.copy(alpha = t))
                    .clickable { onSelect(period) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label,
                    color = lerp(AppColors.TextSecondary, Color.White, t),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Carte commune, légende
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChartCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1F1F23), Color(0xFF151518)))
            )
            .border(1.dp, Color(0xFF2A2A2E), shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun AppLegend(
    apps: List<AppUsageSummary>,
    colorOf: (String) -> Color,
    otherCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        apps.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pair.forEach { app ->
                    LegendItem(
                        name = app.appName,
                        color = colorOf(app.packageName),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (otherCount > 0) {
            Text(
                text = "+$otherCount more",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun LegendItem(name: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            color = AppColors.TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat()
    )
}

// ---------------------------------------------------------------------------------------------
// JOUR : cadran d'horloge
// ---------------------------------------------------------------------------------------------

@Composable
fun ClockCard(
    data: StatsData,
    topApps: List<AppUsageSummary>,
    otherCount: Int,
    colorOf: (String) -> Color,
    nowMs: Long
) {
    val nowIsPm = remember(nowMs) {
        Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.AM_PM) == Calendar.PM
    }
    var isPm by remember { mutableStateOf(nowIsPm) }

    ChartCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total time",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Apps placed by the hour they were used",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            AmPmToggle(isPm = isPm, onChange = { isPm = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                ClockDial(
                    arcs = data.arcs,
                    isPm = isPm,
                    nowMs = nowMs,
                    colorOf = colorOf,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", color = AppColors.TextSecondary, fontSize = 12.sp)
                    Text(
                        text = formatDuration(data.totalMs),
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "last 24h",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        AppLegend(apps = topApps, colorOf = colorOf, otherCount = otherCount)
    }
}

@Composable
private fun AmPmToggle(isPm: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Background)
            .padding(3.dp)
    ) {
        listOf(false to "AM", true to "PM").forEach { (value, label) ->
            val selected = value == isPm
            Text(
                text = label,
                color = if (selected) Color.White else AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) AppColors.Red else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ClockDial(
    arcs: List<ClockArc>,
    isPm: Boolean,
    nowMs: Long,
    colorOf: (String) -> Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val brightStyle = TextStyle(
        color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
    val dimStyle = TextStyle(
        color = AppColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium
    )
    val hourLabels = remember(textMeasurer) {
        (1..12).map { h ->
            textMeasurer.measure(h.toString(), if (h % 3 == 0) brightStyle else dimStyle)
        }
    }

    val visibleArcs = remember(arcs, isPm) { arcs.filter { it.isPm == isPm } }

    // Les arcs se dessinent dans le sens des aiguilles d'une montre.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visibleArcs) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    val (nowIsPm, nowHour) = remember(nowMs) {
        val c = Calendar.getInstance().apply { timeInMillis = nowMs }
        (c.get(Calendar.AM_PM) == Calendar.PM) to
                (c.get(Calendar.HOUR) + c.get(Calendar.MINUTE) / 60f)
    }

    Canvas(modifier = modifier) {
        val c = center
        val half = size.minDimension / 2f
        val stroke = 22.dp.toPx()
        val ringRadius = half - 24.dp.toPx() - stroke / 2f
        val inner = ringRadius - stroke / 2f
        val ringTopLeft = Offset(c.x - ringRadius, c.y - ringRadius)
        val ringSize = Size(ringRadius * 2f, ringRadius * 2f)

        // Piste de fond
        drawCircle(
            color = TrackColor,
            radius = ringRadius,
            center = c,
            style = Stroke(width = stroke)
        )

        // Graduations façon montre (une par minute, plus longue à chaque heure)
        for (i in 0 until 60) {
            val isHour = i % 5 == 0
            val angle = i * 6f - 90f
            val r1 = inner - 6.dp.toPx()
            val r2 = r1 - (if (isHour) 6.dp.toPx() else 3.dp.toPx())
            drawLine(
                color = if (isHour) TickBright else TickDim,
                start = polar(c, r1, angle),
                end = polar(c, r2, angle),
                strokeWidth = if (isHour) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Utilisation des applications
        val limit = progress.value * 360f
        visibleArcs.forEach { arc ->
            val startDeg = arc.startHour * 30f
            val sweepDeg = max(arc.sweepHours * 30f, 1.2f)
            val drawn = (limit - startDeg).coerceIn(0f, sweepDeg)
            if (drawn > 0f) {
                drawArc(
                    color = colorOf(arc.packageName),
                    startAngle = startDeg - 90f,
                    sweepAngle = drawn,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
        }

        // Chiffres 1 à 12, droits, autour du cadran
        val labelRadius = half - 11.dp.toPx()
        hourLabels.forEachIndexed { index, layout ->
            val p = polar(c, labelRadius, (index + 1) * 30f - 90f)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    p.x - layout.size.width / 2f,
                    p.y - layout.size.height / 2f
                )
            )
        }

        // Repère « maintenant »
        if (nowIsPm == isPm) {
            val p = polar(c, ringRadius, nowHour * 30f - 90f)
            drawCircle(color = AppColors.Background, radius = 8.dp.toPx(), center = p)
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = p)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SEMAINE / MOIS : barres verticales empilées
// ---------------------------------------------------------------------------------------------

@Composable
fun BarCard(
    period: StatsPeriod,
    data: StatsData,
    topApps: List<AppUsageSummary>,
    otherCount: Int,
    colorOf: (String) -> Color
) {
    val slots = data.bars
    var selected by remember(slots) { mutableStateOf<Int?>(null) }
    val selectedSlot = selected?.let { slots.getOrNull(it) }

    val elapsedDays = slots.count { !it.isFuture }.coerceAtLeast(1)
    val avgMs = data.totalMs / elapsedDays
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    ChartCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedSlot != null) {
                        dateFormat.format(Date(selectedSlot.dayStart))
                    } else if (period == StatsPeriod.WEEK) {
                        "This week"
                    } else {
                        "This month"
                    },
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = formatDuration(selectedSlot?.totalMs ?: data.totalMs),
                    color = AppColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Daily avg", color = AppColors.TextSecondary, fontSize = 12.sp)
                Text(
                    text = formatDuration(avgMs),
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        StackedBarChart(
            slots = slots,
            colorOf = colorOf,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))
        AppLegend(apps = topApps, colorOf = colorOf, otherCount = otherCount)
    }
}

@Composable
private fun StackedBarChart(
    slots: List<BarSlot>,
    colorOf: (String) -> Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Échelle verticale : quelques graduations rondes (15 min, 1 h, 3 h ...)
    val maxMs = slots.maxOfOrNull { it.totalMs } ?: 0L
    val stepMs = remember(maxMs) { axisStepMs(maxMs) }
    val topMs = remember(maxMs, stepMs) { axisTopMs(maxMs, stepMs) }
    val gridCount = (topMs / stepMs).toInt()

    val axisStyle = TextStyle(color = AppColors.TextSecondary, fontSize = 10.sp)
    val axisLabels = remember(topMs, stepMs, textMeasurer) {
        (0..gridCount).map { i ->
            textMeasurer.measure(if (i == 0) "0" else formatAxis(i * stepMs), axisStyle)
        }
    }

    val todayStyle = TextStyle(
        color = AppColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold
    )
    val normalStyle = TextStyle(color = AppColors.TextSecondary, fontSize = 11.sp)
    val futureStyle = TextStyle(color = FutureText, fontSize = 11.sp)
    val xLabels = remember(slots, textMeasurer) {
        slots.map { slot ->
            textMeasurer.measure(
                slot.label,
                when {
                    slot.isToday -> todayStyle
                    slot.isFuture -> futureStyle
                    else -> normalStyle
                }
            )
        }
    }

    // Les barres poussent depuis la ligne de base à chaque changement de données.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slots) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = modifier.pointerInput(slots, selected) {
            detectTapGestures { offset ->
                val left = ChartLeftPad.toPx()
                val width = size.width - left - ChartRightPad.toPx()
                if (slots.isNotEmpty() && width > 0f && offset.x >= left) {
                    val index = ((offset.x - left) / (width / slots.size)).toInt()
                    if (index in slots.indices) {
                        onSelect(if (index == selected) null else index)
                    }
                }
            }
        }
    ) {
        val leftPad = ChartLeftPad.toPx()
        val rightPad = ChartRightPad.toPx()
        val topPad = ChartTopPad.toPx()
        val bottomPad = ChartBottomPad.toPx()

        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        val baseY = topPad + chartH
        val slotW = chartW / slots.size.coerceAtLeast(1)
        val barW = if (slots.size <= 7) min(slotW * 0.5f, 30.dp.toPx()) else slotW * 0.62f
        val gap = 1.5.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))

        // Grille + graduations
        for (i in 0..gridCount) {
            val y = baseY - chartH * (i * stepMs).toFloat() / topMs.toFloat()
            drawLine(
                color = GridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (i == 0) null else dash
            )
            val label = axisLabels[i]
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    leftPad - 8.dp.toPx() - label.size.width,
                    y - label.size.height / 2f
                )
            )
        }

        // Barres
        val scale = chartH / topMs.toFloat() * progress.value
        slots.forEachIndexed { i, slot ->
            val slotLeft = leftPad + i * slotW
            val x = slotLeft + (slotW - barW) / 2f
            val alpha = if (selected != null && selected != i) 0.35f else 1f

            if (selected == i) {
                drawRoundRect(
                    color = Color(0xFF26262B),
                    topLeft = Offset(slotLeft + 1.dp.toPx(), topPad),
                    size = Size(slotW - 2.dp.toPx(), chartH),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }

            if (slot.totalMs <= 0L) {
                // Petit repère sur la ligne de base pour les jours sans usage (ou à venir)
                drawRoundRect(
                    color = if (slot.isFuture) Color(0xFF202024) else Color(0xFF34343A),
                    topLeft = Offset(x, baseY - 3.dp.toPx()),
                    size = Size(barW, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            } else {
                var cumulative = 0L
                slot.segments.forEachIndexed { j, segment ->
                    val yBottom = baseY - cumulative * scale
                    val yTop = baseY - (cumulative + segment.ms) * scale
                    cumulative += segment.ms

                    val drawBottom = if (j == 0) yBottom else yBottom - gap
                    val height = drawBottom - yTop
                    if (height >= 1f) {
                        val color = colorOf(segment.key).copy(alpha = alpha)
                        if (j == slot.segments.lastIndex) {
                            // Dernier segment : coins du haut arrondis
                            val r = min(min(barW / 2f, 6.dp.toPx()), height / 2f)
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = x,
                                        top = yTop,
                                        right = x + barW,
                                        bottom = drawBottom,
                                        topLeftCornerRadius = CornerRadius(r, r),
                                        topRightCornerRadius = CornerRadius(r, r),
                                        bottomRightCornerRadius = CornerRadius.Zero,
                                        bottomLeftCornerRadius = CornerRadius.Zero
                                    )
                                )
                            }
                            drawPath(path = path, color = color)
                        } else {
                            drawRect(
                                color = color,
                                topLeft = Offset(x, yTop),
                                size = Size(barW, height)
                            )
                        }
                    }
                }
            }
        }

        // Étiquettes du bas (jours de la semaine, ou 1 5 10 15 ... pour le mois)
        slots.forEachIndexed { i, slot ->
            val neighborHasLabel = slots.getOrNull(i - 1)?.showLabel == true ||
                    slots.getOrNull(i + 1)?.showLabel == true
            val show = slot.showLabel || (slot.isToday && !neighborHasLabel)
            if (show) {
                val label = xLabels[i]
                val cx = leftPad + i * slotW + slotW / 2f
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(cx - label.size.width / 2f, baseY + 7.dp.toPx())
                )
            }
        }
    }
}
