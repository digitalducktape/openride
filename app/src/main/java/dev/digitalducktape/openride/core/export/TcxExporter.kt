package dev.digitalducktape.openride.core.export

import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideSample
import dev.digitalducktape.openride.core.sensor.pelotonSpeedMphFromPower
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generates a Garmin Training Center Database (TCX) v2 XML file for a completed ride —
 * reimplemented directly from the public TCX v2 schema (`TrainingCenterDatabasev2.xsd`) and
 * the Garmin ActivityExtension v2 schema (`ActivityExtensionv2.xsd`), **not** copied from
 * peloton-to-garmin, which is GPL-3.0 (PRD Reference Projects & Reuse Strategy explicitly
 * calls for reimplementing this from the format, not lifting source). Chosen over FIT (the
 * PRD's other option, P1-1) because it's plain, human-verifiable XML — TCX is one of the
 * formats HealthFit-style Apple Health bridge apps import.
 *
 * Includes the full per-second [RideSample] series as `<Trackpoint>` elements (PRD P0-5's
 * time-series architecture requirement) — cadence uses the base schema's own `<Cadence>`
 * element (valid for biking activities per the TCX schema), while power/speed use the
 * standard `TPX`/`LX` extensions Garmin devices themselves emit for bike data. Heart rate
 * (PRD P1-4, T17 — optional, only present when a BLE strap was paired for that ride) uses the
 * base schema's own `<HeartRateBpm>` element and is omitted entirely for samples/rides with
 * none, rather than emitting a fabricated zero.
 *
 * Distance/speed have no dedicated field on [Ride]/[RideSample] (only cadence/resistance/
 * power are persisted) — they're reconstructed here the same way the live ride screen derives
 * them, via [pelotonSpeedMphFromPower], accumulated one second at a time.
 */
object TcxExporter {
    private val timestampFormatter = DateTimeFormatter.ISO_INSTANT
    private const val METERS_PER_MILE = 1609.344

    fun export(ride: Ride, samples: List<RideSample>): String {
        val sorted = samples.sortedBy { it.tSec }
        val startInstant = Instant.ofEpochMilli(ride.startEpochMs)
        val startTime = timestampFormatter.format(startInstant.atZone(ZoneOffset.UTC))

        var cumulativeDistanceMeters = 0.0
        val trackpoints = StringBuilder()
        for (sample in sorted) {
            val speedMph = pelotonSpeedMphFromPower(sample.power.toDouble())
            val speedMetersPerSec = speedMph * METERS_PER_MILE / 3600.0
            cumulativeDistanceMeters += speedMetersPerSec // one sample = one elapsed second
            val pointTime = timestampFormatter.format(
                startInstant.plusSeconds(sample.tSec.toLong() + 1).atZone(ZoneOffset.UTC),
            )
            trackpoints.append(
                trackpointXml(
                    time = pointTime,
                    distanceMeters = cumulativeDistanceMeters,
                    cadence = sample.cadence,
                    power = sample.power,
                    speedMetersPerSec = speedMetersPerSec,
                    heartRateBpm = sample.heartRateBpm,
                ),
            )
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd">
  <Activities>
    <Activity Sport="Biking">
      <Id>$startTime</Id>
      <Lap StartTime="$startTime">
        <TotalTimeSeconds>${ride.durationSec}</TotalTimeSeconds>
        <DistanceMeters>${decimal(cumulativeDistanceMeters)}</DistanceMeters>
        <Calories>${ride.calories ?: 0}</Calories>
        <Intensity>Active</Intensity>
        <TriggerMethod>Manual</TriggerMethod>
        <Track>
${trackpoints}        </Track>
        <Extensions>
          <LX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
            <AvgWatts>${ride.avgPower}</AvgWatts>
            <MaxWatts>${ride.maxPower}</MaxWatts>
          </LX>
        </Extensions>
      </Lap>
      <Creator xsi:type="Device_t">
        <Name>OpenRide</Name>
      </Creator>
    </Activity>
  </Activities>
</TrainingCenterDatabase>
"""
    }

    private fun trackpointXml(
        time: String,
        distanceMeters: Double,
        cadence: Int,
        power: Int,
        speedMetersPerSec: Double,
        heartRateBpm: Int?,
    ): String {
        val heartRateXml = if (heartRateBpm != null) {
            "\n          <HeartRateBpm><Value>$heartRateBpm</Value></HeartRateBpm>"
        } else {
            ""
        }
        return """        <Trackpoint>
          <Time>$time</Time>
          <DistanceMeters>${decimal(distanceMeters)}</DistanceMeters>
          <Cadence>${cadence.coerceIn(0, 254)}</Cadence>$heartRateXml
          <Extensions>
            <TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
              <Watts>$power</Watts>
              <Speed>${decimal(speedMetersPerSec)}</Speed>
            </TPX>
          </Extensions>
        </Trackpoint>
"""
    }

    /** Locale-independent (always `.`-decimal) formatting — the device's locale must never
     * flip this to a comma, which would silently break every TCX/FIT-import bridge's parser. */
    private fun decimal(value: Double): String = String.format(Locale.US, "%.2f", value)
}
