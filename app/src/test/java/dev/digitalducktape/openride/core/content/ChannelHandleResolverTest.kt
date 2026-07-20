package dev.digitalducktape.openride.core.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelHandleResolverTest {

    private val channelPage = """
        <html><head><title>Kaleigh Cohen Cycling - YouTube</title></head>
        <body><script>{"externalId":"UChY_9WJx0saa0St48lSdytQ","other":1}</script></body></html>
    """.trimIndent()

    /**
     * A playlist page that embeds [playlistId] the way YouTube's real pages do (canonical link,
     * meta tag) *and* has at least one real `lockupViewModel` video tile in `ytInitialData` — the
     * shape of a playlist that actually exists.
     */
    private fun playlistPage(playlistId: String) = """
        <html><head><title>Climb Series - YouTube</title>
        <link rel="canonical" href="https://www.youtube.com/playlist?list=$playlistId"></head>
        <body><script>var ytInitialData = {"contents": {"tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [{"itemSectionRenderer": {"contents": [{"playlistVideoListRenderer": {"contents": [{"playlistVideoRenderer": {"content": {"lockupViewModel": {"contentId": "vidClimb001", "contentType": "LOCKUP_CONTENT_TYPE_VIDEO", "contentImage": {"thumbnailViewModel": {"image": {"sources": [{"url": "https://i.ytimg.com/vi/vidClimb001/hqdefault.jpg", "width": 168}]}, "overlays": []}}, "metadata": {"lockupMetadataViewModel": {"title": {"content": "Alpe d'Huez Climb"}, "metadata": {"contentMetadataViewModel": {"metadataRows": []}}}}}}}}]}}]}}]}}}}]}};</script></body></html>
    """.trimIndent()

    /**
     * The response shape a genuinely nonexistent playlist id returns on real youtube.com: HTTP
     * 200, valid `ytInitialData`, and the requested id echoed back in several template fields
     * (canonical link, meta tag, `ytUrl`/`originalUrl`) — but zero video tiles anywhere in the
     * page. The old "does the id appear in the html" check passes on this fixture; the fix must
     * reject it.
     */
    private fun nonexistentPlaylistPage(playlistId: String) = """
        <html><head><title>YouTube</title>
        <link rel="canonical" href="https://www.youtube.com/playlist?list=$playlistId">
        <meta property="og:url" content="https://www.youtube.com/playlist?list=$playlistId"></head>
        <body><script>var ytInitialData = {"contents": {"tabs": [{"tabRenderer": {"content": {"sectionListRenderer": {"contents": [{"itemSectionRenderer": {"contents": [{"backgroundPromoRenderer": {"title": {"simpleText": "This playlist does not exist."}, "ytUrl": "https://www.youtube.com/playlist?list=$playlistId", "originalUrl": "https://www.youtube.com/playlist?list=$playlistId"}}]}}]}}}}]}};</script></body></html>
    """.trimIndent()

    /** A page with no mention of any playlist at all and no `ytInitialData` — an error/redirect page. */
    private val noPlaylistPage = """
        <html><head><title>YouTube</title></head><body>Something went wrong</body></html>
    """.trimIndent()

    @Test
    fun `resolves a bare handle to its channel id and name`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        val resolved = resolver.resolve("@kaleigh").getOrThrow()

        assertEquals(ContentSourceType.CHANNEL, resolved.sourceType)
        assertEquals("UChY_9WJx0saa0St48lSdytQ", resolved.youtubeId)
        assertEquals("Kaleigh Cohen Cycling", resolved.displayName)
        assertEquals("https://www.youtube.com/@kaleigh", requested)
    }

    @Test
    fun `resolves a handle typed without the at sign`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        resolver.resolve("kaleigh").getOrThrow()

        assertEquals("https://www.youtube.com/@kaleigh", requested)
    }

    @Test
    fun `resolves a full channel URL`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { channelPage.byteInputStream() })

        val resolved = resolver.resolve("https://www.youtube.com/@kaleigh/videos").getOrThrow()

        assertEquals("UChY_9WJx0saa0St48lSdytQ", resolved.youtubeId)
    }

    @Test
    fun `resolves a legacy slash-c URL`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        resolver.resolve("https://www.youtube.com/c/BiketheWorld").getOrThrow()

        assertEquals("https://www.youtube.com/c/BiketheWorld", requested)
    }

    @Test
    fun `resolves a playlist URL without fetching a channel id`() = runTest {
        val playlistId = "PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs"
        val resolver = ChannelHandleResolver(FeedFetcher { playlistPage(playlistId).byteInputStream() })

        val resolved = resolver
            .resolve("https://www.youtube.com/playlist?list=$playlistId")
            .getOrThrow()

        assertEquals(ContentSourceType.PLAYLIST, resolved.sourceType)
        assertEquals(playlistId, resolved.youtubeId)
        assertEquals("Climb Series", resolved.displayName)
    }

    @Test
    fun `bare input that merely starts with PL but is too short resolves as a channel`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        val resolved = resolver.resolve("PLcyclist").getOrThrow()

        assertEquals(ContentSourceType.CHANNEL, resolved.sourceType)
        assertEquals("https://www.youtube.com/@PLcyclist", requested)
    }

    @Test
    fun `bare input shaped like a real playlist id still resolves as a playlist`() = runTest {
        val realPlaylistId = "PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs"
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            playlistPage(realPlaylistId).byteInputStream()
        })

        val resolved = resolver.resolve(realPlaylistId).getOrThrow()

        assertEquals(ContentSourceType.PLAYLIST, resolved.sourceType)
        assertEquals(realPlaylistId, resolved.youtubeId)
        assertEquals("https://www.youtube.com/playlist?list=$realPlaylistId", requested)
    }

    @Test
    fun `short list url still resolves as a playlist regardless of length`() = runTest {
        val shortId = "PL123"
        val resolver = ChannelHandleResolver(FeedFetcher { playlistPage(shortId).byteInputStream() })

        val resolved = resolver.resolve("https://www.youtube.com/playlist?list=$shortId").getOrThrow()

        assertEquals(ContentSourceType.PLAYLIST, resolved.sourceType)
        assertEquals(shortId, resolved.youtubeId)
    }

    @Test
    fun `playlist page with real video tiles resolves as a playlist`() = runTest {
        val playlistId = "PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs"
        val resolver = ChannelHandleResolver(FeedFetcher { playlistPage(playlistId).byteInputStream() })

        val resolved = resolver
            .resolve("https://www.youtube.com/playlist?list=$playlistId")
            .getOrThrow()

        assertEquals(ContentSourceType.PLAYLIST, resolved.sourceType)
        assertEquals(playlistId, resolved.youtubeId)
        assertEquals("Climb Series", resolved.displayName)
    }

    @Test
    fun `nonexistent playlist that echoes the id back but has no video tiles fails`() = runTest {
        // This is the exact shape a real nonexistent-playlist response from youtube.com has:
        // HTTP 200, valid ytInitialData, the requested id templated into the canonical link and
        // meta tag — but zero lockupViewModel video tiles. The old "id appears in html" check
        // passed on this shape; this is the case it got wrong.
        val playlistId = "PLdoesNotExist00000000000000000"
        val resolver = ChannelHandleResolver(FeedFetcher { nonexistentPlaylistPage(playlistId).byteInputStream() })

        val result = resolver.resolve("https://www.youtube.com/playlist?list=$playlistId")

        assertTrue(result.isFailure)
    }

    @Test
    fun `playlist page with no parseable ytInitialData fails`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { noPlaylistPage.byteInputStream() })

        val result = resolver.resolve("https://www.youtube.com/playlist?list=PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs")

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when the page has no channel id`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { "<html>nope</html>".byteInputStream() })

        val result = resolver.resolve("@ghost")

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when the page cannot be fetched`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { throw IOException("offline") })

        assertTrue(resolver.resolve("@kaleigh").isFailure)
    }

    @Test
    fun `fails on blank input without fetching`() = runTest {
        var calls = 0
        val resolver = ChannelHandleResolver(FeedFetcher {
            calls++
            channelPage.byteInputStream()
        })

        assertTrue(resolver.resolve("   ").isFailure)
        assertEquals(0, calls)
    }
}
