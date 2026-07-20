package dev.digitalducktape.openride.ui.ride

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.digitalducktape.openride.ui.classes.YouTubeEmbed
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import kotlinx.coroutines.launch

/**
 * The in-app class player (v2 spec, superseding the T10 YouTube-app handoff): a full-bleed
 * WebView streaming the class through YouTube's own embedded player, with live ride metrics
 * overlaid — timer and ride controls along the top scrim, [RideMetricsBar] along the bottom.
 *
 * The overlay is dismissible (tap the video, or bring it back via the "Metrics" pill) so
 * YouTube's native player controls are always reachable and never permanently covered —
 * part of the ToS stance recorded in docs/DECISIONS.md.
 *
 * Ride state comes from the same [InRideViewModel] as the plain in-ride screen, and the
 * video tracks it: pausing the ride (Pause button or freewheel auto-pause) pauses the video
 * through the player API, resuming resumes it, and the video reaching its end finishes the
 * workout automatically.
 */
@Composable
fun VideoRideScreen(
    viewModel: InRideViewModel,
    videoId: String,
    onRideEnded: (rideId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState(initial = InRideUiState())
    val scope = rememberCoroutineScope()
    var overlayVisible by remember { mutableStateOf(true) }
    var showEndConfirmation by remember { mutableStateOf(false) }

    // Shared by the End Ride dialog and the video-completion auto-end. endRide() returns
    // null if the ride is already over, so the two paths can't double-fire navigation.
    val endRideAndShowSummary: () -> Unit = {
        scope.launch {
            val ride = viewModel.endRide()
            if (ride != null) onRideEnded(ride.id)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        ClassPlayer(
            videoId = videoId,
            paused = uiState.isPaused,
            onVideoEnded = endRideAndShowSummary,
            modifier = Modifier.fillMaxSize(),
        )

        if (overlayVisible) {
            // Full-screen tap catcher: any tap on the video area tucks the overlay away so
            // YouTube's own controls become reachable. Sits *under* the overlay rows so the
            // ride controls and metrics stay tappable themselves.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { overlayVisible = false },
            )

            TopScrimRow(
                uiState = uiState,
                onPauseResume = { if (uiState.isPaused) viewModel.resume() else viewModel.pause() },
                onEndRide = { showEndConfirmation = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            RideMetricsBar(
                uiState = uiState,
                translucent = true,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            MetricsPill(
                onClick = { overlayVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    if (showEndConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndConfirmation = false },
            title = { Text("End ride?") },
            text = { Text("This will stop and save your ride.") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirmation = false
                    endRideAndShowSummary()
                }) {
                    Text("End Ride")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * The WebView hosting YouTube's embedded player. Created once per [videoId]. [paused]
 * mirrors the *ride's* paused state into the player (see [YouTubeEmbed] for the page-side
 * functions), and [onVideoEnded] fires — already on the UI thread — when playback completes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ClassPlayer(
    videoId: String,
    paused: Boolean,
    onVideoEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentOnVideoEnded by rememberUpdatedState(onVideoEnded)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                // The class should start without the rider reaching for the screen mid-mount.
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    // JS-interface methods arrive on a WebView-internal thread; hop to the
                    // UI thread before touching Compose/navigation state.
                    VideoEndBridge { post { currentOnVideoEnded() } },
                    YouTubeEmbed.BRIDGE_NAME,
                )
                loadDataWithBaseURL(
                    YouTubeEmbed.BASE_URL,
                    YouTubeEmbed.html(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
                webView = this
            }
        },
    )

    LaunchedEffect(webView, paused) {
        webView?.evaluateJavascript(if (paused) "openridePause()" else "openrideResume()", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                // Stop audio immediately on navigation away (End Ride → summary) rather
                // than whenever the view happens to be garbage collected.
                loadUrl("about:blank")
                destroy()
            }
        }
    }
}

/** Page → app callback surface injected as `window.OpenRideBridge` (see [YouTubeEmbed]). */
private class VideoEndBridge(private val onEnded: () -> Unit) {
    @JavascriptInterface
    fun onVideoEnded() = onEnded()
}

@Composable
private fun TopScrimRow(
    uiState: InRideUiState,
    onPauseResume: () -> Unit,
    onEndRide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Goal progress runs across the very top edge, the way the class player runs the
        // class timeline there.
        uiState.goalProgress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = OpenRideColors.Accent,
                trackColor = OpenRideColors.Divider,
            )
        }
        TopStripContent(
            uiState = uiState,
            onPauseResume = onPauseResume,
            onEndRide = onEndRide,
        )
    }
}

@Composable
private fun TopStripContent(
    uiState: InRideUiState,
    onPauseResume: () -> Unit,
    onEndRide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)),
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = TimeFormat.elapsed(uiState.elapsedSec),
                style = MetricTextStyles.BarValue,
                color = OpenRideColors.OnBackground,
            )
            Text(
                text = "ELAPSED",
                style = MetricTextStyles.UnitLabel,
                color = OpenRideColors.OnSurfaceVariant,
            )
        }

        val statusText = when {
            !uiState.sensorsAvailable -> "Sensors not detected"
            uiState.autoPaused -> "Auto-paused — pedal to resume"
            uiState.isPaused -> "Ride paused"
            else -> null
        }
        if (statusText != null) {
            Text(
                text = statusText,
                color = if (uiState.sensorsAvailable) OpenRideColors.OnBackground else OpenRideColors.Warning,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverlayButton(
                text = if (uiState.isPaused) "Resume" else "Pause",
                onClick = onPauseResume,
            )
            OverlayButton(text = "End Ride", onClick = onEndRide, emphasized = true)
        }
    }
}

@Composable
private fun OverlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(
                color = if (emphasized) OpenRideColors.Accent else OpenRideColors.SurfaceVariant,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = OpenRideColors.OnBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Small affordance to bring the overlay back once it's been tucked away. */
@Composable
private fun MetricsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color = OpenRideColors.ScrimOverlay, shape = RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Metrics",
            color = OpenRideColors.OnBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}
