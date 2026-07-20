package dev.digitalducktape.openride.core.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightUnitsTest {

    @Test
    fun `pounds convert to kilograms`() {
        assertEquals(80.0, WeightUnits.lbsToKg(176.37), 0.01)
        assertEquals(45.359, WeightUnits.lbsToKg(100.0), 0.001)
    }

    @Test
    fun `kilograms convert to pounds`() {
        assertEquals(176.37, WeightUnits.kgToLbs(80.0), 0.01)
    }

    @Test
    fun `conversion round-trips`() {
        assertEquals(163.5, WeightUnits.kgToLbs(WeightUnits.lbsToKg(163.5)), 0.0001)
    }

    @Test
    fun `formatLbs keeps one decimal and drops a trailing zero`() {
        assertEquals("176.4", WeightUnits.formatLbs(80.0))
        assertEquals("100", WeightUnits.formatLbs(WeightUnits.lbsToKg(100.0)))
        assertEquals("165.5", WeightUnits.formatLbs(WeightUnits.lbsToKg(165.5)))
    }
}
