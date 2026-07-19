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
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.data.RideSample
import dev.digitalducktape.openride.ui.theme.OpenRideColors

/**
 * Simple power-over-time line graph for the ride summary (PRD P0-5), drawn with plain
 * [Canvas] rather than a charting library. [samples] are assumed already ordered by `tSec`
 * (which is how [dev.digitalducktape.openride.core.data.RideDao.getSamples] returns them).
 */
@Composable
fun PowerGraph(samples: List<RideSample>, modifier: Modifier = Modifier) {
    if (samples.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Not enough data for a graph",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxPower = (samples.maxOf { it.power }).coerceAtLeast(1)
    val minTSec = samples.first().tSec
    val maxTSec = samples.last().tSec.coerceAtLeast(minTSec + 1)
    val lineColor = OpenRideColors.Accent

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val width = size.width
        val height = size.height

        fun xFor(tSec: Int) = width * (tSec - minTSec).toFloat() / (maxTSec - minTSec).toFloat()
        fun yFor(power: Int) = height - (height * power.toFloat() / maxPower.toFloat())

        val path = Path().apply {
            moveTo(xFor(samples.first().tSec), yFor(samples.first().power))
            for (sample in samples.drop(1)) {
                lineTo(xFor(sample.tSec), yFor(sample.power))
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )
    }
}
