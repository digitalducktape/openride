package dev.digitalducktape.openride.core.content

import kotlinx.coroutines.flow.Flow

/**
 * The Classes tab's source list: the seeded catalog plus whatever the rider has added, minus
 * whatever they've hidden.
 */
class ContentSourceRepository(private val dao: ContentSourceDao) {

    /** Populates a fresh install's catalog from [ChannelConfig]. No-op once anything exists. */
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

    fun observeAll(): Flow<List<ContentSource>> = dao.observeAll()

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
