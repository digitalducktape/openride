package dev.digitalducktape.openride.core.export

import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideSample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV export: a ride-history export (PRD P1-2) and a per-ride per-second sample export (bonus
 * — handy for power analysis in a spreadsheet, complementing the Apple-Health-focused TCX
 * export). All numeric formatting is explicitly [Locale.US] so a device set to a
 * comma-decimal locale can't silently corrupt the file.
 */
object CsvExporter {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())

    /**
     * One row per ride (PRD P1-2), in whatever order [rides] is given — callers pass the
     * newest-first history list as-is.
     */
    fun exportHistory(rides: List<Ride>): String {
        val header = "date,duration_sec,avg_cadence,max_cadence,avg_power,max_power,avg_resistance,output_kj,calories"
        val rows = rides.joinToString(separator = "\n") { ride ->
            listOf(
                dateFormatter.format(Instant.ofEpochMilli(ride.startEpochMs)),
                ride.durationSec.toString(),
                ride.avgCadence.toString(),
                ride.maxCadence.toString(),
                ride.avgPower.toString(),
                ride.maxPower.toString(),
                ride.avgResistance.toString(),
                String.format(Locale.US, "%.2f", ride.outputKj),
                ride.calories?.toString().orEmpty(),
            ).joinToString(separator = ",")
        }
        return if (rows.isEmpty()) "$header\n" else "$header\n$rows\n"
    }

    /** One row per second of a single ride's recorded sample series, ordered by [RideSample.tSec]. */
    fun exportRideSamples(samples: List<RideSample>): String {
        val header = "t_sec,cadence,resistance,power"
        val rows = samples.sortedBy { it.tSec }.joinToString(separator = "\n") { sample ->
            "${sample.tSec},${sample.cadence},${sample.resistance},${sample.power}"
        }
        return if (rows.isEmpty()) "$header\n" else "$header\n$rows\n"
    }
}
