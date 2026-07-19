package dev.digitalducktape.openride.core.ride

/**
 * Standard 7-zone FTP-based power zone model (PRD P1-3), per the power-zone gist the PRD
 * references (Coggan's classic %FTP boundaries — a widely-published formula, not anything
 * proprietary to a single project). FTP itself is derived elsewhere (see [FtpEstimator]) as
 * 95% of a rider's best 20-minute average power.
 *
 * Boundaries are the upper edge of each zone as a fraction of FTP; [NEUROMUSCULAR] has no
 * upper bound.
 */
enum class PowerZone(val number: Int, val label: String, val maxFraction: Double?) {
    ACTIVE_RECOVERY(1, "Active Recovery", 0.55),
    ENDURANCE(2, "Endurance", 0.75),
    TEMPO(3, "Tempo", 0.90),
    THRESHOLD(4, "Threshold", 1.05),
    VO2_MAX(5, "VO2 Max", 1.20),
    ANAEROBIC(6, "Anaerobic", 1.50),
    NEUROMUSCULAR(7, "Neuromuscular", null),
    ;

    companion object {
        /**
         * Returns the zone [powerWatts] falls into for the given [ftpWatts], or `null` if
         * [ftpWatts] isn't a usable positive value (e.g. profile has no FTP set — PRD P1-3
         * only shows a live zone when FTP is known).
         */
        fun forPower(powerWatts: Int, ftpWatts: Int?): PowerZone? {
            if (ftpWatts == null || ftpWatts <= 0) return null
            val fraction = powerWatts.toDouble() / ftpWatts
            return entries.firstOrNull { zone -> zone.maxFraction == null || fraction < zone.maxFraction }
        }
    }
}
