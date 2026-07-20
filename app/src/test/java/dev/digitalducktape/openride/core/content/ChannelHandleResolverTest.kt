package dev.digitalducktape.openride.core.content

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelHandleResolverTest {

    private val channelPage = """
        <html><head><title>Kaleigh Cohen Cycling - YouTube</title></head>
        <body><script>{"externalId":"UChY_9WJx0saa0St48lSdytQ","other":1}</script></body></html>
    """.trimIndent()

    private val playlistPage = """
        <html><head><title>Climb Series - YouTube</title></head><body></body></html>
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
        val resolver = ChannelHandleResolver(FeedFetcher { playlistPage.byteInputStream() })

        val resolved = resolver
            .resolve("https://www.youtube.com/playlist?list=PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs")
            .getOrThrow()

        assertEquals(ContentSourceType.PLAYLIST, resolved.sourceType)
        assertEquals("PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs", resolved.youtubeId)
        assertEquals("Climb Series", resolved.displayName)
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
