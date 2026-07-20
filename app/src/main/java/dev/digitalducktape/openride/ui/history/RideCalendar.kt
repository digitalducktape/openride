package dev.digitalducktape.openride.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Month calendar with ride days marked (v2 metrics spec, after the profile overview's
 * activity calendar): Monday-first grid, accent-filled circles on days with a ride, an
 * outline on today, ‹ › month navigation in the header.
 */
@Composable
fun RideCalendar(
    month: YearMonth,
    rideDates: Set<LocalDate>,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onMonthChange(month.minusMonths(1)) }
                    .padding(horizontal = 16.dp),
            )
            Text(
                text = month.format(MonthFormatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onMonthChange(month.plusMonths(1)) }
                    .padding(horizontal = 16.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            for (label in DayLabels) {
                Text(
                    text = label,
                    style = MetricTextStyles.UnitLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // Monday-first leading blanks, then the month's days in 7-column rows.
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> =
            List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        hasRide = day != null && day in rideDates,
                        isToday = day == today,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DayLabels = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

@Composable
private fun DayCell(day: LocalDate?, hasRide: Boolean, isToday: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (day != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = if (hasRide) OpenRideColors.Accent else androidx.compose.ui.graphics.Color.Transparent,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${day.dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (hasRide || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        hasRide -> OpenRideColors.OnBackground
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
