package dev.digitalducktape.openride.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Note: the "insert ride + samples in one transaction" requirement (PRD T4 AC) is
 * implemented in [RideRepository] via `RoomDatabase.withTransaction`, not with a
 * `@Transaction`-annotated default-body method here — Room's Kotlin DAO codegen needs
 * `-Xjvm-default` for default interface methods to work reliably with `@Transaction`,
 * which this project doesn't otherwise need, so the transaction boundary lives one layer
 * up instead.
 */
@Dao
interface RideDao {
    @Insert
    suspend fun insertRide(ride: Ride): Long

    @Insert
    suspend fun insertSamples(samples: List<RideSample>)

    @Query("SELECT * FROM rides WHERE profileId = :profileId ORDER BY startEpochMs DESC")
    fun observeHistory(profileId: Long): Flow<List<Ride>>

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getById(rideId: Long): Ride?

    @Query("SELECT * FROM ride_samples WHERE rideId = :rideId ORDER BY tSec ASC")
    suspend fun getSamples(rideId: Long): List<RideSample>

    // --- Backup & restore (PRD P1-8, T15) ---------------------------------------------------

    @Query("SELECT * FROM rides")
    suspend fun getAllRidesOnce(): List<Ride>

    @Query("SELECT * FROM ride_samples")
    suspend fun getAllSamplesOnce(): List<RideSample>

    /**
     * Bulk-inserts [rides] as-is, preserving each row's existing (non-zero) [Ride.id] — see
     * [ProfileDao.insertAll]'s doc for why this matters for a backup round-trip.
     */
    @Insert
    suspend fun insertRides(rides: List<Ride>)

    @Query("DELETE FROM rides")
    suspend fun deleteAllRides()

    @Query("DELETE FROM ride_samples")
    suspend fun deleteAllSamples()
}
