package dev.digitalducktape.openride.core.profile

import kotlin.math.roundToInt

/**
 * Weight unit conversion for the profile form and display. The rider enters and reads
 * pounds (matching the app's miles-first display units); the database keeps kilograms
 * canonical because exports (TCX) and calorie math expect metric.
 */
object WeightUnits {
    const val LBS_PER_KG = 2.2046226218

    fun lbsToKg(lbs: Double): Double = lbs / LBS_PER_KG

    fun kgToLbs(kg: Double): Double = kg * LBS_PER_KG

    /** Pounds for display/prefill, to a tenth, without a trailing ".0" (80 kg → "176.4", 45.359 kg → "100"). */
    fun formatLbs(kg: Double): String {
        val tenths = (kgToLbs(kg) * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
