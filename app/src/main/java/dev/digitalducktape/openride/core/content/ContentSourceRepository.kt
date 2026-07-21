package dev.digitalducktape.openride.core.content

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

/**
 * The Classes tab's source list: the seeded catalog plus whatever the rider has added, minus
 * whatever they've hidden.
 */
class ContentSourceRepository(private val dao: ContentSourceDao) {

    /**
     * Populates a fresh install's catalog from [ChannelConfig]. No-op once anything exists.
     *
     * Called from more than one place ([observeAll] below, and
     * [YouTubeContentRepository.channelSections]) so that whichever screen a rider opens
     * first — Classes or Content Sources — reliably sees the built-in catalog rather than
     * depending on the other screen having run first. That means two callers can race to seed
     * a truly empty database concurrently; that's safe without any extra locking because
     * `youtubeId` has a unique index ([ContentSource]) and [ContentSourceDao.insertAll] uses
     * `OnConflictStrategy.IGNORE` — if both callers lose the `count() > 0` race and both call
     * `insertAll` with the same 12 built-ins, the second batch's rows just get ignored as
     * conflicts rather than inserted as duplicates or throwing.
     */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        dao.insertAll(
            ChannelConfig.ALL.mapIndexed { index, channel ->
                ContentSource(
                    sourceType = ContentSourceType.CHANNEL,
                    youtubeId = channel.id,
                    displayName = channel.displayName,
                    category = channel.category,
                    builtIn = true,
                    hidden = false,
                    position = index,
                )
            },
        )
    }

    /**
     * The full configured list — the Content Sources screen's only way to see the catalog.
     * Unlike [YouTubeContentRepository.channelSections], nothing else guarantees this has been
     * seeded first: a rider who opens Content Sources (Home -> Profile -> Content sources)
     * before ever visiting Classes would otherwise see an empty list on a fresh install, and
     * adding a source there would make `seedIfEmpty`'s `count() > 0` guard true forever with
     * the built-ins never having existed (Finding 2). Seeding on every collection start makes
     * this observation self-sufficient — see [seedIfEmpty]'s KDoc for why racing its other
     * caller is safe.
     */
    fun observeAll(): Flow<List<ContentSource>> = dao.observeAll().onStart { seedIfEmpty() }

    fun observeVisible(): Flow<List<ContentSource>> = dao.observeVisible()

    suspend fun visibleOnce(): List<ContentSource> = dao.visibleOnce()

    suspend fun getById(id: Long): ContentSource? = dao.getById(id)

    /**
     * Adds a resolved source at the end of the list. Adding one that's already configured
     * returns the existing row's id instead of creating a duplicate.
     */
    suspend fun add(resolved: ResolvedSource, category: ContentCategory): Long {
        dao.findByYoutubeId(resolved.youtubeId)?.let { return it.id }
        val inserted = dao.insert(
            ContentSource(
                sourceType = resolved.sourceType,
                youtubeId = resolved.youtubeId,
                displayName = resolved.displayName,
                category = category,
                builtIn = false,
                hidden = false,
                position = dao.maxPosition() + 1,
            ),
        )
        return if (inserted > 0) inserted else dao.findByYoutubeId(resolved.youtubeId)!!.id
    }

    suspend fun setHidden(id: Long, hidden: Boolean) = dao.setHidden(id, hidden)

    /** Deletes a rider-added source. Built-ins are unaffected — hide those instead. */
    suspend fun deleteCustom(id: Long) = dao.deleteCustom(id)
}
