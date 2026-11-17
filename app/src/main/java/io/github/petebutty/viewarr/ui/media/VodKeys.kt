package io.github.petebutty.viewarr.ui.media

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import io.github.petebutty.viewarr.ui.common.Press
import io.github.petebutty.viewarr.ui.common.RemoteAction
import io.github.petebutty.viewarr.ui.common.RemoteKeys
import io.github.petebutty.viewarr.ui.common.TapHold
import io.github.petebutty.viewarr.data.settings.BtnAction
import io.github.petebutty.viewarr.data.settings.BtnPress

/** What a film player (the built-in one or mpv) gives the shared remote handling. */
interface VodRemote {
    val positionMs: Long
    val durationMs: Long
    fun seekTo(ms: Long)
    /** Skip from where it is ([ms] negative: back). */
    fun skip(ms: Long)
    fun togglePause()
    fun pause()
    /** Playing now (not paused, not stopped). */
    val playing: Boolean
}

/** The player screen's parts the remote can open or change. */
class VodHooks(
    val buttons: List<PlayerButton>,
    val press: (PlayerButton) -> Unit,
    /** Editing the controls' buttons (hold OK on one): show / hide, and move one place (-1 / 1). */
    /** Settings → Remote → Left / Right. */
    val seekMs: Long,
    val showOsd: () -> Unit,
    /** Audio & subtitles: on the subtitles side, the audio side, or (null) as it was left. */
    val openTracks: (subs: Boolean?) -> Unit,
    val toggleDetails: () -> Unit,
    val showDetails: () -> Unit,
    val stop: () -> Unit,
    /** How Back works: 0 its steps then the corner, 3 its steps then the corner, hold: stop (both players). */
    val backMode: Int = 0,
    /** Back's usual step (what the player's BackHandler does): close what's open, else the last step. */
    val stepBack: () -> Unit = {},
    /**
     * Settings → Remote buttons for films: what a button that works anywhere (colours, Info, Play / pause, Stop,
     * Rewind / Fast forward) has been changed to (null: as standard, or not one of those).
     */
    val tableAction: (KeyEvent) -> BtnAction? = { null },
    /** What OK, hold OK, the arrows or Menu have been changed to, with nothing open on the player (null: as standard). */
    val mapped: (BtnPress) -> BtnAction? = { null },
    /** The TV guide (the film stops, its place kept), and search over the page (the film goes to the corner). */
    val openGuide: () -> Unit = {},
    val search: () -> Unit = {},
    /** Skip intro / credits showing (or an intro just skipped by itself): OK skips it (or goes back). True if used. */
    val skipOk: () -> Boolean = { false },
)

/**
 * The remote during a film or episode: one copy for both players, so they can't drift apart. Back is handled by
 * each player's BackHandler (it steps out of whatever is open).
 */
class VodKeys(private val scrub: Scrubber, private val menu: PlayerMenu) {
    private val ok = TapHold()
    private val menuKey = TapHold()
    // A tap of Back takes its usual step (on release); a hold closes the player.
    private var backDown = false
    private var lastKey = 0

    private val leaves = setOf(BtnAction.Guide, BtnAction.Search,
        BtnAction.MainMenu, BtnAction.Settings,
        BtnAction.Stop)

    /** True when the event was used. */
    fun handle(e: KeyEvent, p: VodRemote, h: VodHooks): Boolean {
        lastKey = e.nativeKeyEvent.keyCode
        val a = RemoteKeys.action(e)
        val down = e.type == KeyEventType.KeyDown
        val repeat = e.nativeKeyEvent.repeatCount
        // Gliding into the corner: the film has let go (OK would open its controls in the corner, Right skip it).
        if (VodHost.leaving) return a != RemoteAction.Back
        if (a == RemoteAction.Back && h.backMode == 3) {
            // Since 0.28.3, the same for films and live TV: a tap takes Back's step (what's open closes, then the
            // corner); a hold closes the player.
            when {
                down && repeat == 0 -> backDown = true
                down && backDown -> {
                    backDown = false
                    VodHost.swallowBack = true
                    h.stop()
                }
                !down && backDown -> { backDown = false; h.stepBack() }
                !down -> VodHost.swallowBack = false
            }
            return true
        }
        // The button table (Settings → Remote buttons → Films & shows): buttons that work whatever is open.
        h.tableAction(e)?.let { act ->
            if (down && repeat == 0) perform(act, p, h)
            return true
        }
        // On the seek bar: Left / Right move the marker, OK jumps there, Up leaves.
        if (scrub.active) {
            when (a) {
                RemoteAction.Ok -> {
                    if (down && repeat == 0) { scrub.commit()?.let(p::seekTo); ok.reset(); h.showOsd() }
                    return true
                }
                RemoteAction.Left, RemoteAction.Rewind -> { if (down) { scrub.step(-1, repeat, p.durationMs); h.showOsd() }; return true }
                RemoteAction.Right, RemoteAction.FastForward -> { if (down) { scrub.step(1, repeat, p.durationMs); h.showOsd() }; return true }
                RemoteAction.Up -> { if (down) { scrub.cancel(); h.showOsd() }; return true }
                RemoteAction.Down -> { if (down && menu.open) { scrub.cancel(); menu.onBar = false }; return true }
                else -> Unit
            }
        }
        // The controls (opened with OK): seek bar and buttons.
        if (menu.open && menu.handle(
                e, h.buttons,
                beginScrub = { dir, rep -> scrub.begin(p.positionMs); scrub.step(dir, rep, p.durationMs); h.showOsd() },
                press = h.press,
                
                // (Up from the seek bar used to pause and show the synopsis: Play / Pause does that now, so Up does
                // nothing there.)
                upFromBar = {},
                leftFromFirst = { VodHost.openMenu() },
                downSizes = FilmShortcuts.downSizes(menu),
                pickDown = { r, c -> FilmShortcuts.pickDown(menu, r, c) },
            )
        ) return true
        // Tap OK: the controls (Play/Pause on the remote pauses). Hold OK: nothing unless set. (Either as changed in
        // Settings → Remote buttons → Films & shows.)
        if (a == RemoteAction.Ok) {
            when (ok.feed(e)) {
                // Long OK: only what it's set to in Settings → Remote buttons (its own audio / subtitles pop-up is gone: the info bar has them).
                Press.Hold -> { h.mapped(BtnPress.OkHold)?.let { perform(it, p, h) } }
                Press.Tap -> if (!h.skipOk()) h.mapped(BtnPress.Ok)?.let { perform(it, p, h) } ?: run { menu.show(); h.showOsd() }
                null -> Unit
            }
            return true
        }
        // Menu: audio & subtitles on a tap, when it's let go (holding Menu is search, handled above the player).
        if (a == RemoteAction.Menu) {
            // (The controls don't use Menu: a changed Menu stays off while they're open, as the other moving keys do;
            // its own audio & subtitles still opens.)
            if (menu.open && h.mapped(BtnPress.Menu) != null) return true
            if (menuKey.feed(e) == Press.Tap) h.mapped(BtnPress.Menu)?.let { perform(it, p, h) } ?: h.openTracks(false)
            return true
        }
        // The arrows, changed (nothing open on the player here): their new action, once per press.
        val arrow = when (a) {
            RemoteAction.Up -> BtnPress.Up
            RemoteAction.Down -> BtnPress.Down
            RemoteAction.Left -> BtnPress.Left
            RemoteAction.Right -> BtnPress.Right
            else -> null
        }
        arrow?.let(h.mapped)?.let { act ->
            if (down && repeat == 0) perform(act, p, h)
            return true
        }
        if (!down) return false
        when (a) {
            RemoteAction.Left, RemoteAction.Rewind -> { p.skip(-h.seekMs); h.showOsd() }
            RemoteAction.Right, RemoteAction.FastForward -> { p.skip(h.seekMs); h.showOsd() }
            RemoteAction.Down -> { scrub.begin(p.positionMs); h.showOsd() }
            RemoteAction.Up -> h.showOsd()
            RemoteAction.Info -> h.toggleDetails()
            RemoteAction.PlayPause -> { p.togglePause(); h.showOsd() }
            RemoteAction.Stop -> h.stop()
            else -> return false
        }
        return true
    }

    /** A button's action from the table, on the film. */
    private fun perform(act: BtnAction, p: VodRemote, h: VodHooks) {
        // Leaving the player: the rest of this press (repeats, its release) is eaten at the root, not handed to the page.
        if (act in leaves) VodHost.eatKey = lastKey
        when (act) {
            BtnAction.Controls -> { menu.show(); h.showOsd() }
            BtnAction.AudioSubs -> h.openTracks(null)
            BtnAction.Details -> h.toggleDetails()
            BtnAction.PlayPause -> { p.togglePause(); h.showOsd() }
            BtnAction.SkipBack -> { p.skip(-h.seekMs); h.showOsd() }
            BtnAction.SkipForward -> { p.skip(h.seekMs); h.showOsd() }
            BtnAction.Stop -> h.stop()
            BtnAction.Guide -> h.openGuide()
            BtnAction.Search -> h.search()
            BtnAction.MainMenu -> VodHost.openMenu()
            BtnAction.MediaInfo -> h.press(PlayerButton.Info)
            BtnAction.SwitchPlayer -> h.press(PlayerButton.SwitchPlayer)
            BtnAction.Settings -> h.press(PlayerButton.Settings)
            BtnAction.Transparency -> h.press(PlayerButton.Transparency)
            BtnAction.TimeTitle -> h.showOsd()
            BtnAction.SeekBar -> { scrub.begin(p.positionMs); h.showOsd() }
            // An episode with more after it: the bar with the next episodes; otherwise (a film) the seek bar.
            BtnAction.Episodes ->
                if (NextUp.upcoming.isNotEmpty()) { menu.show(); menu.onBar = false; FilmShortcuts.openEpisodes(menu, only = true); h.showOsd() }
                else { scrub.begin(p.positionMs); h.showOsd() }
            else -> Unit
        }
    }

}
