package dev.digitalducktape.openride

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.digitalducktape.openride.ui.navigation.OpenRideNavHost
import dev.digitalducktape.openride.ui.theme.OpenRideTheme
import kotlinx.coroutines.launch

/**
 * Single activity for the whole app (per project scaffold ticket). All screens are
 * destinations within [OpenRideNavHost] rather than separate Activities.
 */
class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        // PRD P1-4, T17: eagerly construct (the container property is `by lazy`) so the
        // heart-rate manager starts observing the active profile's paired strap from launch,
        // rather than only whenever a screen happens to reference it first.
        appContainer.heartRateManager

        // PRD P0-10: keep the screen on for the duration of an active ride, and let normal
        // display timeout resume the instant it isn't. Driven from the activity (rather than
        // a screen-local effect) so the flag can't be left dangling by screen
        // navigation/recomposition while a ride is still active.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.rideSessionManager.isRideActive.collect { active ->
                    if (active) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        setContent {
            OpenRideTheme {
                OpenRideNavHost(appContainer = appContainer)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Bars can be restored by the system (dialogs, app switches); re-hide whenever the
        // window regains focus so the app stays truly full-screen on the bike.
        if (hasFocus) hideSystemBars()
    }

    /**
     * Full-screen kiosk mode: the tablet's OS navigation bar otherwise overlays the app's
     * own bottom UI (the in-ride metrics bar especially — the app is edge-to-edge and
     * deliberately doesn't inset for a bar the rider never needs mid-ride). Immersive
     * sticky: a swipe from the screen edge shows the system bars transiently, over the
     * app, then they auto-hide again.
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
