package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.ui.theme.MetricTextStyles

/**
 * One big Peloton-style metric tile: a large current value with smaller avg/max readouts
 * beneath (PRD P0-7). [currentValue] is "--" (never a fake zero, P0-9) when the sensor feed
 * is down; [avgValue]/[maxValue] are omitted entirely when the metric doesn't track a max
 * (e.g. resistance).
 */
@Composable
fun MetricTile(
    label: String,
    currentValue: String,
    avgValue: String,
    maxValue: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MetricTextStyles.TileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currentValue,
                style = MetricTextStyles.TileValueLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "avg $avgValue",
                    style = MetricTextStyles.TileValueSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (maxValue != null) {
                    Text(
                        text = "  max $maxValue",
                        style = MetricTextStyles.TileValueSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
