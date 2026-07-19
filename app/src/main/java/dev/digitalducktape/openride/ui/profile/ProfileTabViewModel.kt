package dev.digitalducktape.openride.ui.profile

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.backup.BackupRepository
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Profile nav tab: shows the active rider and offers "Switch rider" (P0-3: "switch rider"
 * reachable from the Profile tab) and backup/restore (PRD P1-8, T15).
 */
class ProfileTabViewModel(
    private val activeProfileHolder: ActiveProfileHolder,
    profileRepository: ProfileRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {
    val activeProfile: Flow<Profile?> =
        combine(activeProfileHolder.activeProfileId, profileRepository.observeProfiles()) { id, profiles ->
            profiles.firstOrNull { it.id == id }
        }

    /** Clears the active profile so the caller can navigate back to profile select. */
    fun switchRider() {
        activeProfileHolder.clear()
    }

    /** JSON content for a full-database backup file (PRD P1-8). */
    suspend fun createBackupContent(): String = backupRepository.exportJson()

    /**
     * Parses and restores [content] as a backup file, replacing the entire current database.
     * Also clears the active profile — ids may now belong to a different rider than before
     * (e.g. restoring onto a fresh install), so the caller should route back to profile
     * select afterward rather than assume the previously active profile still applies.
     * Returns a [Result] so the caller can show a clear error for a malformed/unreadable file
     * instead of crashing on a bad share/import.
     */
    suspend fun restoreFromContent(content: String): Result<Unit> = runCatching {
        val snapshot = backupRepository.parse(content)
        backupRepository.restore(snapshot)
        activeProfileHolder.clear()
    }
}
