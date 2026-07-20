package dev.digitalducktape.openride.core.backup

/**
 * Where [AutoBackupManager] keeps the rolling automatic backup. One logical slot: writes
 * overwrite the previous automatic backup, reads return the latest one (or `null` if none —
 * fresh device, or a storage location this install can no longer read).
 *
 * An interface so the manager's logic stays plain-JVM testable; production uses
 * [MediaStoreAutoBackupStore] (shared Downloads storage that survives an uninstall).
 */
interface AutoBackupStore {
    /** Overwrites the automatic backup with [json]. Returns `false` if storage refused. */
    fun write(json: String): Boolean

    /** The latest automatic backup's content, or `null` if none is readable. */
    fun readLatest(): String?
}
