package dev.digitalducktape.openride.ui.classes

/**
 * Builds the host page for the in-app class player (v2 spec, superseding the T10 intent
 * handoff — see docs/DECISIONS.md): a full-viewport YouTube embed on the privacy-enhanced
 * `youtube-nocookie.com` domain. ToS constraints (PRD Non-Goal #5) shape the parameters:
 * YouTube's own player chrome stays enabled (`controls=1`), and nothing is ever downloaded
 * or cached — the WebView streams from YouTube's player exactly like a browser would.
 */
object YouTubeEmbed {

    /** Base URL handed to `WebView.loadDataWithBaseURL` so the embed runs in a real
     *  https origin (required for YouTube's player to allow playback). */
    const val BASE_URL = "https://www.youtube-nocookie.com"

    /** YouTube video ids are URL-safe by construction; anything else is rejected outright
     *  rather than escaped, since a non-matching id can only be a bug or an injection. */
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{5,20}$")

    fun html(videoId: String): String {
        require(VIDEO_ID.matches(videoId)) { "Not a YouTube video id: $videoId" }
        return """
            <!doctype html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
              iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
            </style>
            </head>
            <body>
            <iframe
              src="$BASE_URL/embed/$videoId?autoplay=1&playsinline=1&controls=1&rel=0&modestbranding=1"
              allow="autoplay; encrypted-media; fullscreen"
              allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()
    }
}
