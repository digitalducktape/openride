package dev.digitalducktape.openride.core.data

import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val profileDao: ProfileDao) {
    fun observeProfiles(): Flow<List<Profile>> = profileDao.observeAll()

    suspend fun getProfile(id: Long): Profile? = profileDao.getById(id)

    /** Returns the id of the created profile. */
    suspend fun createProfile(profile: Profile): Long = profileDao.insert(profile)

    suspend fun updateProfile(profile: Profile) = profileDao.update(profile)

    suspend fun deleteProfile(profile: Profile) = profileDao.delete(profile)
}
