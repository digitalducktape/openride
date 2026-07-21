package dev.digitalducktape.openride.core.content

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Whether a source is a whole channel's uploads or one curated playlist. */
enum class ContentSourceType {
    CHANNEL,
    PLAYLIST,
}

/**
 * A pasted URL or handle, resolved to something the content layer can actually fetch — the
 * hand-off between [dev.digitalducktape.openride.core.content.ChannelHandleResolver] and
 * [ContentSourceRepository.add]. Not yet persisted: the rider still picks a category first.
 */
data class ResolvedSource(
    val sourceType: ContentSourceType,
    val youtubeId: String,
    val displayName: String,
)

/**
 * One configured content source — a row on the Classes tab.
 *
 * The catalog lives in the database rather than in [ChannelConfig] because riders can add
 * their own channels and playlists and hide ones they don't want. [ChannelConfig] is now only
 * the seed for a fresh install (see [ContentSourceRepository.seedIfEmpty]).
 *
 * @param builtIn true for a seeded channel. Built-ins can be hidden but never deleted, so a
 *   rider who hides one can always get it back without reinstalling.
 * @param position display order of the rows on the Classes tab; seeded rows keep their
 *   catalog order and custom sources append to the end.
 */
@Entity(
    tableName = "content_sources",
    indices = [Index(value = ["youtubeId"], unique = true)],
)
data class ContentSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: ContentSourceType,
    val youtubeId: String,
    val displayName: String,
    val category: ContentCategory,
    val builtIn: Boolean,
    val hidden: Boolean = false,
    val position: Int,
)
