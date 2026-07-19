package dev.digitalducktape.openride.core.sensor

import android.os.Parcel
import com.onepeloton.affernetservice.BikeData
import com.onepeloton.affernetservice.V3BikeData
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the reconstructed [BikeData] / [V3BikeData] Parcelables marshal in the exact field
 * order OpenRide reverse-engineered from the affernet service (T3/#3). A round-trip through a
 * real [Parcel] is the closest off-device proof that the wire layout OpenRide reads matches
 * the one it writes — the field order is the whole contract, and an off-by-one there would
 * silently corrupt cadence/power/resistance on the bike.
 */
@RunWith(AndroidJUnit4::class)
class BikeDataParcelTest {

    @Test
    fun `BikeData round-trips the metrics OpenRide reads`() {
        val original = BikeData().apply {
            rpm = 87
            power = 21050            // centi-watts -> 210.5 W
            currentResistance = 42
            targetResistance = 45
            calibrationState = 3
            systemState = 1
            powerSource = 1
        }

        val restored = parcelRoundTrip(original)

        assertEquals(87L, restored.rpm)
        assertEquals(21050L, restored.power)
        assertEquals(42, restored.currentResistance)
        assertEquals(45, restored.targetResistance)
        assertNull("V3 payload should be absent when not set", restored.v3BikeData)
    }

    @Test
    fun `BikeData carries a V3 payload without desyncing`() {
        val original = BikeData().apply {
            rpm = 100
            power = 30000
            currentResistance = 55
            v3BikeData = V3BikeData().apply {
                flywheelRpm = 1234.5f
                tick = 7
                motorAppState = 2
            }
        }

        val restored = parcelRoundTrip(original)

        assertEquals(100L, restored.rpm)
        assertEquals(30000L, restored.power)
        assertEquals(55, restored.currentResistance)
        assertNotNull(restored.v3BikeData)
        assertEquals(1234.5f, restored.v3BikeData!!.flywheelRpm, 0.0001f)
        assertEquals(7, restored.v3BikeData!!.tick)
        assertEquals(2.toByte(), restored.v3BikeData!!.motorAppState)
    }

    private fun parcelRoundTrip(data: BikeData): BikeData {
        val parcel = Parcel.obtain()
        try {
            data.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return BikeData.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
