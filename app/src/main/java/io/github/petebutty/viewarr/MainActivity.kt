package io.github.petebutty.viewarr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import io.github.petebutty.viewarr.data.settings.ThemeStore
import io.github.petebutty.viewarr.ui.AppRoot
import io.github.petebutty.viewarr.ui.theme.AppColors
import io.github.petebutty.viewarr.ui.theme.ViewarrTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeStore: ThemeStore

    /** The rest of the held Back that brought a film back full screen. */
    private var pipHold = false

    /**
     * Holding Back with a film in the corner brings it back full screen, handled here, before any screen sees the
     * press: right after the film shrank nothing on screen had the remote yet, so the first hold went nowhere.
     */
    /** OK is being held (a repeat came): its release isn't a press (see dispatchKeyEvent). */
    private var okHeld = false

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Holding OK opens things (record options, an episode's menu): the hold is decided on its first repeat, so the
        // release that ends it means nothing. Dropped here, app-wide: with pop-ups in their own windows it reached the
        // page behind (still focused for a moment) as a plain OK, pressing Play on the episode, or Watch in the guide.
        if (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
            when {
                event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> okHeld = false
                event.action == android.view.KeyEvent.ACTION_DOWN -> okHeld = true
                event.action == android.view.KeyEvent.ACTION_UP && okHeld -> { okHeld = false; return true }
            }
        }
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            val host = io.github.petebutty.viewarr.ui.media.VodHost
            // The rest of a hold that just sent the film to the corner.
            if (host.swallowBack) {
                if (event.action == android.view.KeyEvent.ACTION_UP) host.swallowBack = false
                return true
            }
            // (Holding Back with the film in the corner used to bring it full screen here. Since 0.28.3 a hold closes it
            // and a tap enlarges it, timed in HomeState.onRootKey: this fought that, flashing it full screen first.)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // (Only a fresh start: Android hands a restored app its original link again, which replayed it on every restart.)
        if (savedInstanceState == null) io.github.petebutty.viewarr.data.media.DeepLink.handle(intent?.data)
        lifecycleScope.launch { themeStore.palette.collect { AppColors.palette = it } }
        setContent {
            ViewarrTheme { AppRoot() }
        }
        // No screen reader on: skip Compose's screen-reader bookkeeping (a launcher's button-catching accessibility
        // service switched it on for everything). See A11yTrim.
        io.github.petebutty.viewarr.ui.common.A11yTrim.install(this)
    }

    // Compose refreshes its accessibility state when its view attaches and the window gains focus: apply again after.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) io.github.petebutty.viewarr.ui.common.A11yTrim.refresh(this)
    }

    /** Already running: a Watch Next card opens its title here; a viewarr://channel link changes channel. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        io.github.petebutty.viewarr.data.media.DeepLink.handle(intent.data)
    }
}
