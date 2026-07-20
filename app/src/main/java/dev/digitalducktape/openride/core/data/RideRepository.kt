package dev.digitalducktape.openride.core.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Persists finished rides and serves per-profile history.
 *
 * @param database the app's [RoomDatabase] instance, used only to open the transaction
 *   that [saveRide] needs to write the ride's aggregate row and its full sample series
 *   atomically (PRD T4 AC).
 */
class RideRepository(
    private val database: RoomDatabase,
    private val rideDao: RideDao,
) {
    /**
     * Writes [ride] and [samples] in a single transaction: either both land, or neither
     * does. [samples] are inserted with the ride's freshly generated id. Returns the
     * generated ride id.
     */
    suspend fun saveRide(ride: Ride, samples: List<RideSample>): Long =
        database.withTransaction {
            val rideId = rideDao.insertRide(ride)
            if (samples.isNotEmpty()) {
                rideDao.insertSamples(samples.map { it.copy(rideId = rideId) })
            }
            rideId
        }

    /** Newest-first ride history, scoped to a single profile. */
    fun observeHistory(profileId: Long): Flow<List<Ride>> = rideDao.observeHistory(profileId)

    /** Most recent take of each class this profile has ridden (v2 "taken" badges). */
    fun observeTakenVideos(profileId: Long): Flow<List<TakenVideo>> =
        rideDao.observeTakenVideos(profileId)

    suspend fun getRide(rideId: Long): Ride? = rideDao.getById(rideId)

    suspend fun getSamples(rideId: Long): List<RideSample> = rideDao.getSamples(rideId)
}
