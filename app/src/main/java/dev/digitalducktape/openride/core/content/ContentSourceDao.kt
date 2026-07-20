package dev.digitalducktape.openride.core.content

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentSourceDao {
    @Query("SELECT * FROM content_sources ORDER BY position ASC")
    fun observeAll(): Flow<List<ContentSource>>

    @Query("SELECT * FROM content_sources WHERE hidden = 0 ORDER BY position ASC")
    fun observeVisible(): Flow<List<ContentSource>>

    @Query("SELECT * FROM content_sources WHERE hidden = 0 ORDER BY position ASC")
    suspend fun visibleOnce(): List<ContentSource>

    @Query("SELECT * FROM content_sources WHERE id = :id")
    suspend fun getById(id: Long): ContentSource?

    @Query("SELECT COUNT(*) FROM content_sources")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM content_sources")
    suspend fun maxPosition(): Int

    /** Ignores a source whose `youtubeId` is already configured — adding a duplicate is a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: ContentSource): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sources: List<ContentSource>)

    @Query("UPDATE content_sources SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Built-ins are protected here rather than in the repository so no caller can bypass it. */
    @Query("DELETE FROM content_sources WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long)

    @Query("SELECT * FROM content_sources WHERE youtubeId = :youtubeId")
    suspend fun findByYoutubeId(youtubeId: String): ContentSource?
}
