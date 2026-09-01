package com.luke.workouttracker.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val TraceWidth = 2.5.dp

/**
 * Set progress as a single line: accent for completed, track for
 * remaining, with a dot at the current position.
 */
@Composable
fun TraceProgress(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "traceProgress",
    )
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val y = size.height / 2f
        val w = TraceWidth.toPx()
        drawLine(track, Offset(w, y), Offset(size.width - w, y), w, StrokeCap.Round)
        if (animated > 0f) {
            val end = w + (size.width - 2 * w) * animated
            drawLine(accent, Offset(w, y), Offset(end, y), w, StrokeCap.Round)
            drawCircle(accent, radius = w * 1.6f, center = Offset(end, y))
        }
    }
}

/**
 * The same line closed into a ring, for the rest timer. [progress] is a
 * 0..1 fraction and is coerced, so overrunning the nominal duration
 * simply leaves the ring full.
 */
@Composable
fun TraceRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = TraceWidth.toPx() * 1.4f
            val inset = w / 2f
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Weekly volume as a polyline.
 *
 * [highlighted] draws in accent, otherwise track — so one exercise can be
 * the subject of the screen. [hollowAt] marks indices whose week was
 * performed as a swapped exercise.
 *
 * Edge cases, all of which occur in real data:
 *  - empty     -> draw nothing
 *  - one point -> a single dot, centred
 *  - all equal -> a flat line at mid height (no divide by zero)
 *  - all zero  -> the same flat line; never scales by zero
 */
@Composable
fun TraceChart(
    values: List<Double>,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    hollowAt: Set<Int> = emptySet(),
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val stroke = if (highlighted) accent else track

    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        if (values.isEmpty()) return@Canvas
        val w = TraceWidth.toPx()
        val pad = w * 3f
        val usableW = size.width - pad * 2
        val usableH = size.height - pad * 2

        if (values.size == 1) {
            drawCircle(stroke, w * 1.8f, Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }

        val max = values.max()
        val min = values.min()
        val span = max - min

        fun yFor(v: Double): Float =
            if (span <= 0.0) size.height / 2f
            else pad + usableH * (1f - ((v - min) / span).toFloat())

        fun xFor(i: Int): Float = pad + usableW * (i.toFloat() / (values.size - 1))

        val path = Path().apply {
            moveTo(xFor(0), yFor(values[0]))
            for (i in 1 until values.size) lineTo(xFor(i), yFor(values[i]))
        }
        drawPath(path, stroke, style = Stroke(width = w, cap = StrokeCap.Round))

        values.indices.forEach { i ->
            val c = Offset(xFor(i), yFor(values[i]))
            when {
                i in hollowAt -> {
                    drawCircle(surface, w * 1.9f, c)
                    drawCircle(stroke, w * 1.9f, c, style = Stroke(width = w * 0.8f))
                }
                i == values.lastIndex -> drawCircle(stroke, w * 1.8f, c)
            }
        }
    }
}
