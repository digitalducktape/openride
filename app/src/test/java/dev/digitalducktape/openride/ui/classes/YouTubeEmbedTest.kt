package dev.digitalducktape.openride.ui.classes

import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeEmbedTest {

    @Test
    fun `html embeds the privacy-enhanced player URL for the video id`() {
        val html = YouTubeEmbed.html("dQw4w9WgXcQ")

        assertTrue(html.contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?"))
    }

    @Test
    fun `html keeps YouTube's own controls enabled and plays inline`() {
        val html = YouTubeEmbed.html("dQw4w9WgXcQ")

        // ToS stance (spec / DECISIONS.md): never strip YouTube's player UI…
        assertTrue(html.contains("controls=1"))
        // …and play inside our layout rather than forcing the fullscreen activity.
        assertTrue(html.contains("playsinline=1"))
        assertTrue(html.contains("autoplay=1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `html rejects a video id with markup or URL metacharacters`() {
        YouTubeEmbed.html("\"><script>alert(1)</script>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `html rejects an id that would escape the embed path`() {
        YouTubeEmbed.html("../watch?v=x")
    }

    @Test
    fun `underscore and dash ids are accepted`() {
        val html = YouTubeEmbed.html("a-b_c-D_e12")

        assertTrue(html.contains("/embed/a-b_c-D_e12?"))
    }
}
