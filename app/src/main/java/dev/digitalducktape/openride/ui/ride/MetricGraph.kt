package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.OpenRideColors

/**
 * Metric-over-time line graph for the ride detail screen (v2 metrics spec, after the
 * original bike UI's per-ride chart): horizontal gridlines with value labels on the right,
 * minute labels along the bottom, and the series drawn in the accent color. Plain [Canvas],
 * no charting library. [points] are (tSec, value) pairs ordered by tSec — exactly what
 * [RideMetricStats.points] produces.
 */
@Composable
fun MetricGraph(points: List<Pair<Int, Int>>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(GraphHeight), contentAlignment = Alignment.Center) {
            Text(
                text = "Not enough data for a graph",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxValue = niceCeiling(points.maxOf { it.second })
    val minTSec = points.first().first
    val maxTSec = points.last().first.coerceAtLeast(minTSec + 1)
    val lineColor = OpenRideColors.Accent
    val gridColor = OpenRideColors.Divider
    val labelColor = OpenRideColors.OnSurfaceVariant

    Canvas(modifier = modifier.fillMaxWidth().height(GraphHeight)) {
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt(),
            )
            textSize = 11.dp.toPx()
            isAntiAlias = true
        }
        val bottomInset = 18.dp.toPx()
        val width = size.width
        val plotHeight = size.height - bottomInset

        fun xFor(tSec: Int) = width * (tSec - minTSec).toFloat() / (maxTSec - minTSec).toFloat()
        fun yFor(value: Int) = plotHeight - (plotHeight * value.toFloat() / maxValue.toFloat())

        // Horizontal gridlines at quarters of the (nice-rounded) max, value labels right.
        for (step in 0..4) {
            val value = maxValue * step / 4
            val y = yFor(value)
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = 1f,
            )
            if (step > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    "$value",
                    width - labelPaint.measureText("$value") - 4.dp.toPx(),
                    y - 4.dp.toPx(),
                    labelPaint,
                )
            }
        }

        // Elapsed-time labels along the bottom, roughly quartering the ride.
        for (step in 0..4) {
            val tSec = minTSec + (maxTSec - minTSec) * step / 4
            val label = TimeFormat.elapsed(tSec)
            val x = when (step) {
                0 -> 0f
                4 -> width - labelPaint.measureText(label)
                else -> xFor(tSec) - labelPaint.measureText(label) / 2
            }
            drawContext.canvas.nativeCanvas.drawText(label, x, size.height, labelPaint)
        }

        val path = Path().apply {
            moveTo(xFor(points.first().first), yFor(points.first().second))
            for ((tSec, value) in points.drop(1)) {
                lineTo(xFor(tSec), yFor(value))
            }
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

private val GraphHeight = 220.dp

/** Rounds up to a friendly axis maximum (e.g. 437 → 500), so gridline labels stay clean. */
private fun niceCeiling(value: Int): Int {
    if (value <= 0) return 4
    val candidates = sequenceOf(4, 8, 20, 40, 80, 100, 120, 160, 200, 240, 300, 400, 500, 600, 800, 1000)
    candidates.firstOrNull { it >= value }?.let { return it }
    // Beyond the table: round up to the next multiple of 200.
    return ((value + 199) / 200) * 200
}

