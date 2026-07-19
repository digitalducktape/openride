package dev.digitalducktape.openride.ui.classes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launches [videoId] in the YouTube app (falling back to a browser if it isn't installed),
 * rather than an in-app WebView IFrame player.
 *
 * This is a v1 tradeoff, not an oversight — see `docs/DECISIONS.md` for the full writeup.
 * Short version: an ACTION_VIEW intent is the simplest implementation, and keeps us
 * unambiguously within YouTube's ToS (Non-Goal #5 in the PRD: never download/rehost/embed
 * content in a way that risks that). The cost is that it hands control to the YouTube app's
 * own UI/back button for the duration of playback, which is a rougher fit with the "feels
 * like one cohesive app" goal (P0-7) than an embedded player would be. Revisit as a P2 if
 * that seam bothers riders in practice.
 */
fun launchVideo(context: Context, videoId: String) {
    val uri = Uri.parse("https://www.youtube.com/watch?v=$videoId")

    val youtubeAppIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.youtube")
    }
    try {
        context.startActivity(youtubeAppIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // Fall through to the browser below — no YouTube app installed.
    }

    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
