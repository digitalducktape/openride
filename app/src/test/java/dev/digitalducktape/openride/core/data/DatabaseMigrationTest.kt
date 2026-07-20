package dev.digitalducktape.openride.core.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [MIGRATION_1_2] (T17/#17 — BLE heart rate: adds `profiles.pairedHrDeviceAddress`
 * and `ride_samples.heartRateBpm`).
 *
 * Drives the migration directly against a real SQLite database built at the version-1 schema,
 * rather than Room's [androidx.room.testing.MigrationTestHelper]. MigrationTestHelper loads the
 * exported schema JSON from *assets*, which Robolectric unit tests (src/test, no device) do not
 * reliably serve; building the v1 tables here and calling `MIGRATION_1_2.migrate` exercises the
 * exact same migration SQL without that packaging dependency.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var helper: SupportSQLiteOpenHelper? = null

    /** Opens a fresh in-memory database created at the version-1 schema (verbatim from 1.json). */
    private fun openV1Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                        "NOT NULL, `name` TEXT NOT NULL, `avatarEmoji` TEXT NOT NULL, " +
                        "`avatarColor` INTEGER NOT NULL, `weightKg` REAL, `ftp` INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rides` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                        "NOT NULL, `profileId` INTEGER NOT NULL, `startEpochMs` INTEGER NOT NULL, " +
                        "`durationSec` INTEGER NOT NULL, `avgCadence` INTEGER NOT NULL, " +
                        "`maxCadence` INTEGER NOT NULL, `avgPower` INTEGER NOT NULL, " +
                        "`maxPower` INTEGER NOT NULL, `avgResistance` INTEGER NOT NULL, " +
                        "`outputKj` REAL NOT NULL, `calories` INTEGER, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ride_samples` (`rideId` INTEGER NOT NULL, " +
                        "`tSec` INTEGER NOT NULL, `cadence` INTEGER NOT NULL, " +
                        "`resistance` INTEGER NOT NULL, `power` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`rideId`, `tSec`), FOREIGN KEY(`rideId`) REFERENCES " +
                        "`rides`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // no-op: the test applies MIGRATION_1_2 explicitly
            }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
            .also { helper = it }
            .writableDatabase
    }

    @After
    fun tearDown() {
        helper?.close()
    }

    @Test
    fun `migrate 1 to 2 preserves existing rows and adds the new nullable columns`() {
        val db = openV1Database()
        db.execSQL(
            "INSERT INTO profiles (id, name, avatarEmoji, avatarColor, weightKg, ftp) " +
                "VALUES (1, 'Ed', '🚴', -16711936, 80.0, 220)",
        )
        db.execSQL(
            "INSERT INTO rides (id, profileId, startEpochMs, durationSec, avgCadence, maxCadence, " +
                "avgPower, maxPower, avgResistance, outputKj, calories) " +
                "VALUES (1, 1, 1700000000000, 1800, 85, 100, 150, 300, 45, 270.0, 260)",
        )
        db.execSQL(
            "INSERT INTO ride_samples (rideId, tSec, cadence, resistance, power) " +
                "VALUES (1, 0, 85, 45, 150)",
        )

        MIGRATION_1_2.migrate(db)

        db.query("SELECT name, pairedHrDeviceAddress FROM profiles WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("Ed", it.getString(it.getColumnIndexOrThrow("name")))
            assertEquals(true, it.isNull(it.getColumnIndexOrThrow("pairedHrDeviceAddress")))
        }
        db.query("SELECT power, heartRateBpm FROM ride_samples WHERE rideId = 1 AND tSec = 0").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(150, it.getInt(it.getColumnIndexOrThrow("power")))
            assertEquals(true, it.isNull(it.getColumnIndexOrThrow("heartRateBpm")))
        }
    }

    @Test
    fun `migrate 2 to 3 preserves rides and adds a null videoId`() {
        val db = openV1Database()
        MIGRATION_1_2.migrate(db)
        db.execSQL(
            "INSERT INTO profiles (id, name, avatarEmoji, avatarColor, weightKg, ftp) " +
                "VALUES (1, 'Ed', '🚴', -16711936, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO rides (id, profileId, startEpochMs, durationSec, avgCadence, maxCadence, " +
                "avgPower, maxPower, avgResistance, outputKj, calories) " +
                "VALUES (1, 1, 1700000000000, 1800, 85, 100, 150, 300, 45, 270.0, 260)",
        )

        MIGRATION_2_3.migrate(db)

        db.query("SELECT outputKj, videoId FROM rides WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(270.0, it.getDouble(it.getColumnIndexOrThrow("outputKj")), 0.001)
            assertEquals(true, it.isNull(it.getColumnIndexOrThrow("videoId")))
        }

        db.execSQL(
            "INSERT INTO rides (id, profileId, startEpochMs, durationSec, avgCadence, maxCadence, " +
                "avgPower, maxPower, avgResistance, outputKj, calories, videoId) " +
                "VALUES (2, 1, 1700000100000, 600, 70, 80, 100, 120, 30, 60.0, 58, 'dQw4w9WgXcQ')",
        )
        db.query("SELECT videoId FROM rides WHERE id = 2").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("dQw4w9WgXcQ", it.getString(it.getColumnIndexOrThrow("videoId")))
        }
    }

    @Test
    fun `migrate 1 to 2 lets new rows populate both new columns`() {
        val db = openV1Database()

        MIGRATION_1_2.migrate(db)

        db.execSQL(
            "INSERT INTO profiles (id, name, avatarEmoji, avatarColor, weightKg, ftp, " +
                "pairedHrDeviceAddress) VALUES (1, 'Ed', '🚴', -16711936, NULL, NULL, " +
                "'AA:BB:CC:DD:EE:FF')",
        )
        db.execSQL(
            "INSERT INTO rides (id, profileId, startEpochMs, durationSec, avgCadence, maxCadence, " +
                "avgPower, maxPower, avgResistance, outputKj, calories) " +
                "VALUES (1, 1, 0, 60, 90, 90, 150, 150, 50, 9.0, 9)",
        )
        db.execSQL(
            "INSERT INTO ride_samples (rideId, tSec, cadence, resistance, power, heartRateBpm) " +
                "VALUES (1, 0, 90, 50, 150, 142)",
        )

        db.query("SELECT pairedHrDeviceAddress FROM profiles WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("AA:BB:CC:DD:EE:FF", it.getString(it.getColumnIndexOrThrow("pairedHrDeviceAddress")))
        }
        db.query("SELECT heartRateBpm FROM ride_samples WHERE rideId = 1 AND tSec = 0").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(142, it.getInt(it.getColumnIndexOrThrow("heartRateBpm")))
        }
    }
}
