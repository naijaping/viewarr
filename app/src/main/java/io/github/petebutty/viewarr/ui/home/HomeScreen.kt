package io.github.petebutty.viewarr.ui.home

import io.github.petebutty.viewarr.R
import androidx.compose.runtime.mutableStateMapOf
import io.github.petebutty.viewarr.ui.theme.appBackground

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.petebutty.viewarr.ui.common.glass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.petebutty.viewarr.playback.PlaybackState
import io.github.petebutty.viewarr.playback.PlaybackViewModel
import io.github.petebutty.viewarr.ui.player.PlayerLayer
import io.github.petebutty.viewarr.ui.player.PlayerOsd
import io.github.petebutty.viewarr.ui.player.MultiView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import io.github.petebutty.viewarr.data.session.Session
import io.github.petebutty.viewarr.ui.common.MenuItem
import io.github.petebutty.viewarr.ui.common.EdgeBlocked
import io.github.petebutty.viewarr.ui.common.trapFocus
import io.github.petebutty.viewarr.ui.guide.GuideScreen
import io.github.petebutty.viewarr.ui.setup.SetupMode
import io.github.petebutty.viewarr.ui.setup.SetupWizard
import io.github.petebutty.viewarr.ui.guide.GuideViewModel
import io.github.petebutty.viewarr.data.media.MediaType
import io.github.petebutty.viewarr.ui.media.MediaScreen
import io.github.petebutty.viewarr.ui.media.MediaViewModel
import io.github.petebutty.viewarr.ui.dvr.DvrViewModel
import io.github.petebutty.viewarr.ui.dvr.RecordDialog
import io.github.petebutty.viewarr.ui.dvr.ProgramInfoDialog
import io.github.petebutty.viewarr.ui.dvr.RecordingsScreen
import io.github.petebutty.viewarr.data.media.VodStream
import io.github.petebutty.viewarr.data.local.ChannelListItem
import io.github.petebutty.viewarr.data.local.GuideProgram
import io.github.petebutty.viewarr.data.settings.GuideBack
import io.github.petebutty.viewarr.data.settings.LocalRemote
import io.github.petebutty.viewarr.data.settings.MediaBack
import io.github.petebutty.viewarr.data.settings.PlayerOk
import io.github.petebutty.viewarr.data.settings.ZapDirection
import io.github.petebutty.viewarr.data.settings.StartPage
import io.github.petebutty.viewarr.ui.theme.AppColors
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.mutableIntStateOf
import io.github.petebutty.viewarr.data.dvr.DvrRecording
import io.github.petebutty.viewarr.data.dvr.DvrState
import io.github.petebutty.viewarr.data.dvr.Reminder
import io.github.petebutty.viewarr.data.media.DeepLink
import io.github.petebutty.viewarr.data.media.MediaItem
import io.github.petebutty.viewarr.data.profiles.Access
import io.github.petebutty.viewarr.data.settings.BtnAction
import io.github.petebutty.viewarr.data.settings.BtnPress
import io.github.petebutty.viewarr.data.settings.BtnScreen
import io.github.petebutty.viewarr.data.settings.ButtonMap
import io.github.petebutty.viewarr.data.settings.GuideLeft
import io.github.petebutty.viewarr.data.settings.LiveSides
import io.github.petebutty.viewarr.playback.MusicPlayer
import io.github.petebutty.viewarr.ui.common.AppIcons
import io.github.petebutty.viewarr.ui.common.ButtonHint
import io.github.petebutty.viewarr.ui.common.LocalQuickSearchOpen
import io.github.petebutty.viewarr.ui.common.Press
import io.github.petebutty.viewarr.ui.common.RemoteAction
import io.github.petebutty.viewarr.ui.common.RemoteKeys
import io.github.petebutty.viewarr.ui.common.SearchKey
import io.github.petebutty.viewarr.ui.common.TapHold
import io.github.petebutty.viewarr.ui.dvr.GuideMatch
import io.github.petebutty.viewarr.ui.dvr.SeasonPicker
import io.github.petebutty.viewarr.ui.guide.channelLogo
import io.github.petebutty.viewarr.ui.guide.displayTitle
import io.github.petebutty.viewarr.ui.media.FullTrailer
import io.github.petebutty.viewarr.ui.media.VodHost
import io.github.petebutty.viewarr.ui.media.VodSource
import io.github.petebutty.viewarr.ui.music.MusicBrowseKind
import io.github.petebutty.viewarr.ui.music.MusicScreen
import io.github.petebutty.viewarr.ui.music.MusicViewModel
import io.github.petebutty.viewarr.ui.player.CardOption
import io.github.petebutty.viewarr.ui.player.LiveShortcut
import io.github.petebutty.viewarr.ui.player.LiveShortcuts
import io.github.petebutty.viewarr.ui.player.LiveShortcutsBridge
import io.github.petebutty.viewarr.ui.player.OsdPoster
import io.github.petebutty.viewarr.ui.common.InfoRows.Kind as RowKind
import io.github.petebutty.viewarr.ui.player.OsdRow
import io.github.petebutty.viewarr.ui.player.RowSets
import io.github.petebutty.viewarr.ui.player.ShortcutColours
import io.github.petebutty.viewarr.ui.player.ShortcutLogos
import io.github.petebutty.viewarr.ui.player.TransparencyPanel
import io.github.petebutty.viewarr.ui.settings.RemoteViewModel
import io.github.petebutty.viewarr.ui.settings.SportsViewModel
import io.github.petebutty.viewarr.ui.theme.CornerPicture
import io.github.petebutty.viewarr.ui.theme.Dates
import io.github.petebutty.viewarr.ui.theme.UiOpts

internal const val SEEK_MS = 30_000L

internal enum class Section(val label: String, val available: Boolean) {
    Tv("Live TV", true),
    Movies("Movies", true),
    Shows("Shows", true),
    /** Every library on your servers (Plex, Jellyfin, Emby, IPTV) and your music, in one list. */
    Library("My Library", true),
    Music("Music", true),
    Recordings("Recordings", true),
    Search("Search", true),
    Settings("Settings", true),
}


/** What fills the screen behind the menu. */
enum class Page { Tv, Movies, Shows, Music, Recordings, Search }

@Composable
fun HomeScreen(
    session: Session?,
    onSignOut: () -> Unit,
    vm: GuideViewModel = hiltViewModel(),
    playback: PlaybackViewModel = hiltViewModel(),
    dvr: DvrViewModel = hiltViewModel(),
) {
    // Record options for a programme (from the guide or the player's Record button).
    // Everything the screen keeps track of (HomeState.kt).
    val hs = rememberHomeState()
    hs.vm = vm; hs.playback = playback; hs.dvr = dvr; hs.session = session
    // Seerr: choosing which series of a guide programme's show to request.
    // Recordings play in the same player as films (VodHost, above every page), so they can shrink to a corner too.
    val recordingFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<VodStream?>(null) }
    val recordingSource = remember { VodSource(recordingFlow, kotlinx.coroutines.flow.MutableStateFlow(null), onClose = { recordingFlow.value = null }) }
    LaunchedEffect(hs.recordingPlayback) {
        hs.recordingPlayback?.let { recordingFlow.value = it; VodHost.show(recordingSource); hs.recordingPlayback = null }
    }
    // Search popped up over the current screen (Settings → Remote → Search button); Back closes it.
    // A changed button from Settings → Remote buttons: the rest of its press (repeats, release) is swallowed.
    // A button with a long press as well (Settings → Remote buttons): its tap waits for the release, its hold for a timer.
    // Buttons that open a page or leave the app (done once those are in reach, below).
    // "Remind me": the programme starting now pops up in the corner; OK watches it, Back dismisses.
    LaunchedEffect(Unit) {
        dvr.pruneReminders()
        while (true) {
            val now = System.currentTimeMillis()
            val due = dvr.reminders.value.firstOrNull { it.start <= now + 30_000 && it.end > now }
            if (due != null && hs.dueReminder?.id != due.id) {
                hs.dueReminder = due
                dvr.forget(due.id)
            }
            delay(15_000)
        }
    }
    LaunchedEffect(hs.dueReminder) { if (hs.dueReminder != null) { delay(90_000); hs.dueReminder = null } }
    // Recordings: a small card when one starts, finishes or fails (by the schedule, so it's on time).
    LaunchedEffect(Unit) {
        val told = HashSet<String>()
        var first = true
        // The recordings arrive a moment after start: what's already there then isn't news (the first look used to see
        // an empty list, so every old failed recording popped up 15 s later, one after another).
        kotlinx.coroutines.withTimeoutOrNull(60_000) { dvr.recordings.first { it.isNotEmpty() } }
        while (true) {
            val now = System.currentTimeMillis()
            val names = dvr.channels.value
            for (r in dvr.recordings.value) {
                val ch = names[r.channelId]?.name?.let { " · $it" }.orEmpty()
                val note = when {
                    r.state == DvrState.Failed && r.end > now - 12 * 3_600_000L -> "⚠  Recording failed: ${r.title}$ch"
                    r.end in (now - 120_000)..now -> "✓  Recorded: ${r.title}$ch"
                    r.start in (now - 120_000)..now && r.end > now -> "●  Recording now: ${r.title}$ch"
                    else -> null
                } ?: continue
                val id = "${r.id}:${note.take(3)}"
                if (told.add(id) && !first) { hs.dvrNote = note; delay(6_000); hs.dvrNote = null }
            }
            first = false // what was already true when the app opened isn't news
            delay(15_000)
        }
    }
    // Live TV: the channel's providers have no free connection (see PlaybackViewModel.checkConnections).
    val connectionNote by playback.connectionNote.collectAsStateWithLifecycle()
    LaunchedEffect(connectionNote) { if (connectionNote != null) { delay(12_000); playback.clearConnectionNote() } }
    val dvrRecordings by dvr.recordings.collectAsStateWithLifecycle()
    val remote = LocalRemote.current
    hs.remote = remote
    // Movies & Shows switched off (live TV only): don't leave a hidden page on screen.
    LaunchedEffect(AppColors.palette.hideMedia) {
        if (AppColors.palette.hideMedia && (hs.page == Page.Movies || hs.page == Page.Shows)) hs.page = Page.Tv
    }
    val mediaFocus = remember { FocusRequester() }
    val musicVm: MusicViewModel = hiltViewModel()
    hs.musicVm = musicVm
    // Live TV hub: the channel carries on in a corner over another page (Settings → Playback → Live TV).
    // Movies or Shows opened from the menu over the guide: the channel carries on in the guide's preview place, the
    // page's content starting where the guide's did.
    // The Sports page (Sports in the menu): over whatever page is open, live TV staying in the top-left window.
    val sportsFocus = remember { FocusRequester() }
    // (A film as the mini player counts too: the pages make room for it the same way.)
    SideEffect { io.github.petebutty.viewarr.ui.common.LiveBehind.preview = hs.livePreview || (VodHost.active && VodHost.mini) }
    // Movies / Shows opened from the menu over full-screen live TV: the channel carries on full screen behind the page.
    SideEffect { io.github.petebutty.viewarr.ui.common.LiveBehind.on = hs.liveBehind }
    // The film player's Settings button opened the menu (its film is in the corner meanwhile).
    // Full-screen live TV: Left asks "Rewind?" first (a second Left, or holding it, rewinds), so a stray press
    // doesn't drop you out of live. When it was asked; 0: not asking.
    // A hold that already rewound: the rest of that press is ignored (each repeat would rewind again).
    // A channel browsed to in the info bar, not changed to yet (browse()).
    // Several channels on screen at once (from the player menu).
    // A Menu press that's still a tap (not held for search).
    // Menu on full-screen live TV: a tap (on release) opens audio & subtitles; holding it is search.
    val liveMenuKey = remember { TapHold() }
    // "Watch from the start" from the guide: search opens on the programme's title.
    // Search opened from the guide's "Search catch-up": with the Catch-up pill picked.
    LaunchedEffect(hs.quickSearch) { if (!hs.quickSearch) hs.searchCatchup = false }
    // Sign out of everything: asked first.
    // Live TV controls → Transparency: the three sliders over the picture.
    // A film brought back full screen (held Back, handled in MainActivity): the menu or search under it closes,
    // or it would take the next Back presses.
    LaunchedEffect(VodHost.mini) {
        if (!VodHost.mini && VodHost.active) { hs.menuOpen = false; hs.quickSearch = false }
    }
    // Held Back with nothing playing: the menu opens on its main list (not the page's own list).
    // A library (from My Library) on screen: Left opens the menu on My Library.
    // However the menu closed (a page or film picked in it, not only Back), the next one opens the normal way: on the
    // page's own list (Left from the guide: the channel groups), not on the main list a held Back / film menu asked for.
    // (Every "open the menu on…" is for that one opening: Settings stayed set when a page closed the menu, so Left
    // from any page then opened the Settings list.)
    LaunchedEffect(hs.menuOpen) { if (!hs.menuOpen) { hs.menuOnMain = false; hs.menuOnLibrary = false; hs.menuOnSettings = false; hs.menuOnGroups = false } }
    val searchMedia: MediaViewModel = hiltViewModel(key = "media-search")
    val fromStartScope = rememberCoroutineScope()
    hs.fromStartScope = fromStartScope
    // Leaving full screen (guide, another page) ends multi-view; it mustn't reappear next time.
    LaunchedEffect(hs.fullscreen) { if (!hs.fullscreen) hs.multiView = false }
    // When the live controls were last used: a plain holder, not state. As state, every press inside the controls
    // rebuilt the whole home screen (guide, shortcuts, poster rows), which made them sluggish.
    val guideFocus = remember { FocusRequester() }
    val playerFocus = remember { FocusRequester() }
    val playing by playback.channel.collectAsStateWithLifecycle()
    val playState by playback.state.collectAsStateWithLifecycle()
    val catchup by playback.catchup.collectAsStateWithLifecycle()
    // The guide group a channel was started from (browsing another group later doesn't change it).
    LaunchedEffect(playing?.id) { if (playing != null) hs.playingGroup = vm.groupId.value }
    // Settings → Live TV → "All channels" group hidden: the guide shows the first group instead of everything
    // (also after switching source, which starts on All channels).
    val groupsForAll by vm.groups.collectAsStateWithLifecycle()
    val groupForAll by vm.groupId.collectAsStateWithLifecycle()
    LaunchedEffect(AppColors.palette.hideAllChannels, groupForAll, groupsForAll.isNotEmpty()) {
        if (AppColors.palette.hideAllChannels && groupForAll == null) groupsForAll.firstOrNull { it.name !in vm.menuHiddenGroups.value }?.let { vm.selectGroup(it.id) }
    }
    // The last favourite removed while on ★ Favourites: back to all channels (or the first group, as above).
    val favsNow by vm.favourites.collectAsStateWithLifecycle()
    LaunchedEffect(favsNow.isEmpty(), groupForAll) {
        if (favsNow.isEmpty() && groupForAll == io.github.petebutty.viewarr.ui.guide.GuideViewModel.FAVOURITES) vm.selectGroup(null)
    }
    // Back from full screen: the guide opens on the channel playing, at now, in its group (not wherever the guide was
    // last scrolled to, nor the group browsed since).
    LaunchedEffect(hs.fullscreen) {
        if (!hs.fullscreen && hs.page == Page.Tv) playing?.let { vm.returnTo(it.id, hs.playingGroup) }
        // Into full screen with the guide on another group (one picked from the menu): back to the playing channel's,
        // so Up / Down (and the channel list) step from it, not from the first channel of the group being looked at.
        if (hs.fullscreen) playing?.let { p -> if (vm.channels.value.none { it.id == p.id }) vm.returnTo(p.id, hs.playingGroup) }
    }
    val programs by vm.programs.collectAsStateWithLifecycle()
    // The guide only keeps programmes for the channels near where it's scrolled to. The channel you're watching
    // (and multi-view's) are looked up on their own, or the banner said "No guide information" once you'd scrolled
    // away from it in the guide.
    val ownScope = rememberCoroutineScope()
    hs.ownScope = ownScope
    val now by vm.now.collectAsStateWithLifecycle()


    // Live TV moved to another source: what was playing belongs to the old one (its picture froze), so stop it.




    LaunchedEffect(hs.bannerVisible) { if (!hs.bannerVisible && hs.browseChannel != null) hs.cancelBrowse() }




    // Held up while the Transparency panel is on its glass colour / info bar rows (the bar is what's being changed).
    LaunchedEffect(hs.bannerToken, hs.bannerHeld) {
        if (hs.bannerHeld) { hs.bannerVisible = true; return@LaunchedEffect }
        if (hs.bannerVisible) {
            delay(5_000)
            hs.bannerVisible = false
        }
    }
    // The bar gone (timed out, or anything else closed it) with a channel browsed to but not changed to: let it go.
    LaunchedEffect(hs.bannerVisible, hs.fullscreen) { if (!hs.bannerVisible || !hs.fullscreen) hs.cancelBrowse() }
    // The live controls close 8 s after they were last used.
    LaunchedEffect(hs.osdVisible) {
        if (!hs.osdVisible) return@LaunchedEffect
        while (true) {
            val left = 8_000 - (System.currentTimeMillis() - hs.osdUsedAt[0])
            if (left <= 0) { hs.osdVisible = false; break }
            delay(left)
        }
    }
    LaunchedEffect(remote.loaded) {
        if (!remote.loaded || hs.startApplied) return@LaunchedEffect
        hs.startApplied = true
        // Live TV hub: always opens on the last channel, full screen.
        val start = remote.startPage
        when (start) {
            StartPage.Guide -> Unit
            StartPage.Movies -> hs.page = Page.Movies
            StartPage.Shows -> hs.page = Page.Shows
            StartPage.Search -> hs.quickSearch = true
            StartPage.Recordings -> hs.page = Page.Recordings
            StartPage.LastChannel -> {
                val ch = remote.lastChannelId?.let { id -> vm.channelById(id) }
                if (ch != null && session != null) hs.watchChannel(ch)
            }
        }
    }
    LaunchedEffect(hs.sportsOpen, hs.menuOpen, hs.fullscreen, hs.osdVisible, hs.transparencyOpen, hs.showAccounts, hs.page, hs.recordTarget == null, hs.seasonPick == null, VodHost.covering, VodHost.gliding, hs.infoTarget == null, hs.channelListOpen, hs.quickSearch, MusicPlayer.nowPlayingOpen) {
        if (hs.channelListOpen || hs.quickSearch || hs.transparencyOpen || MusicPlayer.nowPlayingOpen) return@LaunchedEffect
        if (hs.recordTarget != null || hs.seasonPick != null || VodHost.covering || hs.infoTarget != null) return@LaunchedEffect
        // A film gliding into the corner: the page takes the remote once it's there (its highlight starting mid-glide
        // made the glide stutter).
        if (VodHost.gliding) return@LaunchedEffect
        // Just landed in the corner: a moment before the page takes the remote, so its highlight and the player
        // letting go aren't drawn in the same frame (that frame was slow).
        if (VodHost.mini) delay(150)
        awaitFrame()
        runCatching {
            when {
                hs.fullscreen && !hs.osdVisible -> playerFocus.requestFocus()
                !hs.fullscreen && !hs.menuOpen && !hs.showAccounts && !hs.quickSearch -> if (hs.sportsOpen) sportsFocus.requestFocus() else if (hs.page == Page.Tv) guideFocus.requestFocus() else mediaFocus.requestFocus()
            }
        }
    }
    // A film or episode playing over Live TV (Watch from the start, search, a recording): the channel is stopped and
    // its player let go (it carried on underneath, sound and all). Picking a channel afterwards starts it afresh.
    val vodOver = VodHost.active
    LaunchedEffect(vodOver) {
        if (vodOver) { hs.fullscreen = false; hs.livePreview = false; playback.close() }
    }
    // …and a channel started while a film is in the corner ends the film (one stream, one sound).
    LaunchedEffect(playing?.id) { if (playing != null) { VodHost.close(); MusicPlayer.stop() } }
    // Music starting ends Live TV (as a film does).
    val musicOn = MusicPlayer.active
    LaunchedEffect(musicOn) { if (musicOn) { hs.fullscreen = false; hs.livePreview = false; playback.close() } }
    // A full-screen trailer (title page → Trailer) ends live TV too; it was silent with the channel still playing.
    val trailerOn = FullTrailer.active
    LaunchedEffect(trailerOn) { if (trailerOn) { hs.fullscreen = false; hs.livePreview = false; playback.close() } }
    // Back on the guide (or the channel stopped): nothing in the corner any more.
    LaunchedEffect(hs.page, playing == null) { if (hs.page == Page.Tv || playing == null) { hs.livePreview = false } }
    // A picture in the corner: a film in the mini player. (Live TV over another page plays in the guide's preview
    // place instead, since 0.26.0.)
    val pictureInCorner = VodHost.active && VodHost.mini
    // Settings → Playback → Still watching?: after hours without a button press on live TV, ask, then stop (a TV left
    // on otherwise holds a provider connection all night).
    val stillHours = AppColors.palette.stillWatchingHours
    LaunchedEffect(stillHours, playing?.id) {
        if (stillHours == 0) return@LaunchedEffect
        while (true) {
            delay(60_000)
            if (playing != null && !hs.stillAsk && System.currentTimeMillis() - hs.lastKeyAt > stillHours * 3_600_000L) hs.stillAsk = true
        }
    }
    LaunchedEffect(hs.stillAsk) {
        if (!hs.stillAsk) return@LaunchedEffect
        delay(60_000)
        if (hs.stillAsk) { hs.stillAsk = false; hs.fullscreen = false; playback.stop() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { playback.resume() }

    // Back on a page (guide, Movies, Shows, Recordings) asks before leaving: a second Back within a few
    // seconds exits. The menu handles its own Back (list → main menu → closed).
    val activity = LocalContext.current as? Activity
    hs.activity = activity
    /**
     * The menu was opened by Back in the guide (TV guide → Back: Open the menu, as TiviMate): Back out of it asks to
     * exit, and Back once more exits (it used to close the menu, and Back opened it again: no way out).
     */
    LaunchedEffect(hs.menuOpen) { if (!hs.menuOpen) { hs.menuFromGuideBack = false; hs.menuFullPage = false; VodHost.behind = false } }
    // Left from the film controls' first button: the menu over the film, which plays on full screen underneath.
    SideEffect { VodHost.openMenu = { VodHost.behind = true; hs.menuOnMain = true; hs.menuOpen = true } }
    /** When Back last took full screen down to the guide (a quick second Back then asks to exit). */
    /** The guide's Back (Settings → Remote & navigation → TV guide). */
    val guideBack = remote.guideBack

    LaunchedEffect(hs.exitArmed) {
        if (hs.exitArmed != 0L) { delay(2_500); hs.exitArmed = 0L }
    }
    // Back returns to live TV first when browsing the past/future, then asks to exit (or opens the menu).
    BackHandler(enabled = hs.page == Page.Tv && !hs.menuOpen && !hs.fullscreen && !hs.showAccounts && !hs.quickSearch) {
        when {
            // A channel playing in the guide's window: Back brings it full screen (Back there shrinks it here again;
            // hold Back closes it). The same for films and live TV since 0.28.3, whatever Guide Back says.
            playing != null -> { if (!vm.isAtNow()) vm.goToNow(); hs.fullscreen = true; hs.showBanner() }
            !vm.isAtNow() -> vm.goToNow()
            // "Press Back again to exit" is up: this Back exits.
            hs.exitArmed != 0L -> hs.backOnPage()
            guideBack == GuideBack.Menu -> { hs.menuOpen = true; hs.menuFromGuideBack = true }
            else -> hs.backOnPage()
        }
    }
    BackHandler(enabled = hs.fullscreen && !hs.osdVisible) {
        ButtonMap.changed(remote, BtnScreen.Live, BtnPress.Back)?.let { hs.doButton(BtnScreen.Live, it); return@BackHandler }
        hs.fullscreen = false
        hs.bannerVisible = false
        hs.guideFromFullAt = System.currentTimeMillis()
    }


    LaunchedEffect(hs.digitsToken) {
        if (hs.digits.isEmpty() && hs.digitsMessage == null) return@LaunchedEffect
        delay(if (hs.digitsMessage != null) 3_000 else 1_800)
        // (A note that came up while a number was being typed: cleared, and the number still tuned, not left on screen.)
        if (hs.digitsMessage != null) { hs.digitsMessage = null; if (hs.digits.isNotEmpty()) hs.tuneTyped() } else hs.tuneTyped()
    }

    // Watch Next cards on the TV home screen: brought up to date a little after start-up (servers answer slowly
    // while everything else loads), and a card picked there opens its title's page here.
    val moviesVm: MediaViewModel = hiltViewModel(key = "media-Movie")
    val showsVm: MediaViewModel = hiltViewModel(key = "media-Show")
    hs.moviesVm = moviesVm; hs.showsVm = showsVm
    // Into a title (a poster on a library or catalogue page) while the channel's in the corner: it stops. Browsing a
    // shortcut's posters keeps it; choosing one is watching something else.
    val titleOpen = moviesVm.detail.collectAsStateWithLifecycle().value != null || showsVm.detail.collectAsStateWithLifecycle().value != null
    LaunchedEffect(titleOpen) {
        // (The channel in the preview place stays: the title page makes room for it.)
        if (titleOpen && hs.liveBehind) { hs.liveBehind = false; playback.stop() }
    }
    // Over live TV only on Movies / Shows; nothing playing, nothing behind.
    HomeLinkEffects(hs)
    // Live TV controls → Down: your shortcuts (Settings → Remote & navigation → Live TV shortcuts).
    val movieLibs by moviesVm.libraries.collectAsStateWithLifecycle()
    val showLibs by showsVm.libraries.collectAsStateWithLifecycle()
    val movieMenuRows by moviesVm.menuRows.collectAsStateWithLifecycle()
    val showMenuRows by showsVm.menuRows.collectAsStateWithLifecycle()
    // Streaming services down the main menu (AIOMetadata's "Netflix · Popular", "Netflix · New"… rows), each with its
    // rows' names: its show rows then its film rows on one page.
    val menuServices = remember(movieMenuRows.rows, showMenuRows.rows) {
        val all = showMenuRows.rows + movieMenuRows.rows
        (RowSets.sets(showMenuRows.rows) + RowSets.sets(movieMenuRows.rows)).filter { it.startsWith("name:") }.distinct().map { key ->
            key.removePrefix("name:") to all.filter { RowSets.matches(key, it) }.map { it.id to RowSets.rowTitle(it) }.distinctBy { it.first }
        }
    }
    // The other viewarr:// controls (home automation): each does what the remote's own button would.
    HomeCommandEffects(hs)
    val musicOnNow by musicVm.enabled.collectAsStateWithLifecycle()
    // Settings → Remote & navigation → Live TV shortcuts → Poster rows (off in Low memory mode: no pictures).
    val postersOn = AppColors.palette.opt(UiOpts.OSD_POSTERS) == 0 && !AppColors.palette.lowMemory
    // The shortcuts' rows, logos, Live now and Sports: loaded after the app starts (once connected and the start page is
    // up), not the first time the controls open, so they're ready then. One at a time, a few seconds apart, so a slow
    // box isn't asked for everything at once while you start using it. (Refreshed again as the controls open.)
    val ctxForPreload = LocalContext.current
    LaunchedEffect(session?.id) {
        if (session == null) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { io.github.petebutty.viewarr.ui.common.StartupGate.ready }.first { it }
        val am = ctxForPreload.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val gap = if (am?.isLowRamDevice == true || AppColors.palette.lowMemory) 5_000L else 2_500L
        delay(3_000)
        for (stage in 1..5) { hs.preloadStage = stage; delay(gap) }
        // On for long enough to load, then off, so the controls closing later doesn't load it all again.
        delay(60_000)
        hs.preloadStage = 0
    }
    // 1: libraries and the pages' rows (row and network shortcuts come from them: Plex rows, AIOMetadata's networks).
    val preloadRows = hs.preloadStage >= 1
    LaunchedEffect(hs.osdVisible, preloadRows) {
        if (!hs.osdVisible && !preloadRows) return@LaunchedEffect
        moviesVm.loadLibraries(MediaType.Movie); showsVm.loadLibraries(MediaType.Show)
        moviesVm.load(MediaType.Movie); showsVm.load(MediaType.Show)
    }
    // 2: Continue watching and Recently added (the poster rows under the shortcuts).
    val preloadPosters = hs.preloadStage >= 2
    LaunchedEffect(hs.osdVisible, preloadPosters) {
        if ((!hs.osdVisible && !preloadPosters) || !postersOn) return@LaunchedEffect
        moviesVm.loadContinue(MediaType.Movie); showsVm.loadContinue(MediaType.Show)
        moviesVm.loadRecent(MediaType.Movie); showsVm.loadRecent(MediaType.Show)
    }
    val preloadEvents = hs.preloadStage >= 3
    val preloadExtras = hs.preloadStage >= 4
    val preloadMusic = hs.preloadStage >= 5
    // The music service in use (its logo on the music shortcuts).
    val musicSources by musicVm.sources.collectAsStateWithLifecycle()
    val musicSourceName = musicSources.firstOrNull { it.on }?.name
    // Live now (the hub's shortcut): the event channels' programmes, looked at while the live TV controls are up.
    // (Also while the film player's controls are up: its shortcuts show the same rows.)
    val rowsWanted = hs.osdVisible || LiveShortcutsBridge.filmMenuOpen
    // (Once at the start; then every minute while the controls are up.)
    // (3)
    LaunchedEffect(rowsWanted, preloadEvents) {
        if (!rowsWanted && !preloadEvents) return@LaunchedEffect
        while (true) {
            hs.liveEvents = runCatching { vm.liveEvents() }.getOrNull() ?: hs.liveEvents
            if (!rowsWanted) break
            delay(60_000)
        }
    }

    // ---- Sports hub (Settings → Sports): your teams and leagues, each a shortcut with its games.
    val sportsVm: SportsViewModel = hiltViewModel()
    hs.sportsVm = sportsVm
    val sportFollows by sportsVm.follows.collectAsStateWithLifecycle()
    // (SportsHub.kt: the games, where they're on, and the cards, rows and pinned shortcuts made from them.)
    val sessionRef = rememberUpdatedState(session)
    val sportsHub = remember {
        SportsHub(sportsVm, vm, dvr,
            watch = { ch -> hs.osdVisible = false; playback.play(ch, sessionRef.value); vm.selectChannel(ch.id); hs.fullscreen = true; hs.showBanner() },
            toast = { hs.toast(it) })
    }
    // (4)
    LaunchedEffect(rowsWanted, preloadExtras, sportFollows) {
        if (!rowsWanted && !preloadExtras) return@LaunchedEffect
        sportsHub.load(sportFollows)
    }
    // The Sports page: its games and channels carry on loading after you leave it (a quick look and back used to
    // stop it part-way, and the channels never came).
    LaunchedEffect(hs.sportsOpen, sportFollows) { if (hs.sportsOpen) fromStartScope.launch { sportsHub.load(sportFollows) } }

    // (Built again only when what it's made from changes: it was rebuilt on every redraw of this screen. What each
    // shortcut does reads the rest when it's pressed.)
    val mainShortcuts: List<Pair<LiveShortcut, () -> Unit>> = remember(movieLibs, showLibs, movieMenuRows, showMenuRows, musicOnNow, musicSourceName, remote.liveShortcuts) {
        val all = LiveShortcuts.candidates(
            movieLibs.orEmpty(), showLibs.orEmpty(), movieMenuRows.rows, showMenuRows.rows, musicOnNow, musicSourceName,
        ).associateBy { it.id }
        val rowsForIds = movieMenuRows.rows + showMenuRows.rows
        val saved = (remote.liveShortcuts ?: LiveShortcuts.defaults)
            .mapNotNull { LiveShortcuts.resolve(it, all, rowsForIds) }.distinctBy { it.id }
        // Nothing usable saved (everything gone, or not loaded yet): the defaults rather than an empty row.
        val use = saved.ifEmpty { LiveShortcuts.defaults.mapNotNull { all[it] } }
        use.map { it.id }.mapNotNull { id ->
            val sc = all[id] ?: return@mapNotNull null
            sc to sc@{
                // Sports: like Live now, OK opens the guide on the sports channels (its rows, Down, are your teams).
                if (id == "page:Sports") { hs.openLiveEvents(); return@sc }
                hs.osdVisible = false
                val parts = id.split(":", limit = 3)
                val type = if (parts.getOrNull(1) == "Show") MediaType.Show else MediaType.Movie
                val mvm = if (type == MediaType.Show) showsVm else moviesVm
                val target = if (type == MediaType.Show) Page.Shows else Page.Movies
                when (parts[0]) {
                    "home" -> { mvm.openLibrary(null, type); mvm.jumpTo(null); hs.openPage(target, keepLive = true) }
                    "lib" -> { (if (type == MediaType.Show) showLibs else movieLibs).orEmpty().firstOrNull { it.key == parts[2] }?.let { mvm.openLibrary(it, type) }; hs.openPage(target, keepLive = true) }
                    "row" -> { mvm.openLibrary(null, type); mvm.jumpTo(parts[2]); hs.openPage(target, keepLive = true) }
                    "set" -> {
                        mvm.openLibrary(null, type); mvm.jumpTo(null)
                        // A network: its film rows follow its show rows on the same page (named "… (films)").
                        val other = if (type == MediaType.Show) movieMenuRows.rows else showMenuRows.rows
                        val extra = if (!parts[2].startsWith("name:")) emptyList() else other
                            .filter { RowSets.matches(parts[2], it) }
                            .map { it.copy(title = it.title + if (type == MediaType.Show) " (films)" else " (shows)") }
                        mvm.showRowSet(parts[2], extra)
                        hs.openPage(target, keepLive = true)
                    }
                    // Music: straight into Albums, Artists, Playlists… (the channel carries on in the corner).
                    "music" -> {
                        runCatching { MusicBrowseKind.valueOf(parts[1]) }.getOrNull()?.let { musicVm.openBrowse(it) }
                        hs.openPage(Page.Music, keepLive = true)
                    }
                    "page" -> when (parts[1]) {
                        "Guide" -> hs.fullscreen = false
                        "Live" -> hs.openLiveEvents()
                        "Music" -> hs.openPage(Page.Music, keepLive = true)
                        "Recordings" -> hs.openPage(Page.Recordings, keepLive = true)
                        "Settings" -> { hs.menuOnSettings = true; hs.menuOpen = true }
                        else -> { hs.searchStart = null; hs.quickSearch = true }
                    }
                }
            }
        } + listOf(
            // Last in the row: fetch every row again from your addons (AIOMetadata, AIOStreams…) and servers, so new
            // networks and fresh catalogues turn up without restarting Viewarr. (Not movable or hideable.)
            LiveShortcut(REFRESH_SHORTCUT, "Refresh", AppIcons.Refresh,
                colors = ShortcutColours.settings) to { hs.refreshShortcuts() },
        )
    }
    // The row on live TV: the main row with the teams / leagues you pinned (Settings → Sports) before Refresh.
    val liveShortcuts: List<Pair<LiveShortcut, () -> Unit>> = remember(mainShortcuts, sportFollows) {
        mainShortcuts.dropLast(1) + sportFollows.filter { it.pinned && mainShortcuts.none { m -> m.first.id == it.shortcutId } }.map { sportsHub.shortcut(it) } + mainShortcuts.last()
    }
    hs.liveShortcuts = liveShortcuts

    // Network / service shortcuts: their logos (TMDB), looked up at the start (and for any shortcut added since).
    LaunchedEffect(hs.osdVisible, preloadExtras, liveShortcuts.map { it.first.id }) {
        if (!hs.osdVisible && !preloadExtras) return@LaunchedEffect
        val names = liveShortcuts.mapNotNull { (sc, _) -> sc.logoName?.takeIf { !it.startsWith("sport:") }?.let { n -> n to sc } }
            .filter { (n, _) -> n !in ShortcutLogos.map }
        if (names.isEmpty()) return@LaunchedEffect
        // A show from its rows tells TMDB which network it is (networks aren't watch providers).
        moviesVm.loadShortcutLogos(names.associate { (n, _) ->
            n to showMenuRows.rows.firstOrNull { RowSets.name(it) == n }?.items?.firstOrNull()
        })
    }
    // The same shortcuts in the film and show players' controls.
    SideEffect {
        LiveShortcutsBridge.items = liveShortcuts
        LiveShortcutsBridge.openSettings = { hs.settingsFromFilm = true; hs.menuOnSettings = true; hs.menuOpen = true }
        LiveShortcutsBridge.openPage = { name -> hs.buttonRequest = when (name) { "Movies" -> BtnAction.Movies; "Shows" -> BtnAction.Shows; else -> BtnAction.Recordings } }
        LiveShortcutsBridge.openGuide = { hs.menuOpen = false; hs.quickSearch = false; if (hs.page != Page.Tv) hs.openPage(Page.Tv) else hs.fullscreen = false }
    }
    // Settings opened from a film: closing it brings the film back full screen.
    LaunchedEffect(hs.menuOpen) {
        if (!hs.menuOpen && hs.settingsFromFilm) {
            hs.settingsFromFilm = false
            if (VodHost.active && VodHost.mini) VodHost.expand()
        }
    }
    // Hold OK on a shortcut: off the row (saved ids that stand for it too), with a note on getting it back.
    val remoteVm: RemoteViewModel = hiltViewModel()
    hs.remoteVm = remoteVm
    // Choose rows: only when the saved choice changes (each redraw would put back the saved one over a choice just made
    // and not yet written).
    LaunchedEffect(remote.shortcutRows) { LiveShortcutsBridge.chosen = remote.shortcutRows }
    // The film player's shortcuts use these same three (hold OK on one): one row, changed from either player.
    SideEffect {
        LiveShortcutsBridge.hide = { sc -> hs.hideShortcut(sc) }
        // The search button in the film player: search over the page (the film goes to the corner first).
        VodHost.openSearch = { hs.menuOpen = false; hs.channelListOpen = false; hs.searchStart = null; hs.quickSearch = true }
        // Settings → Playback → After a film or episode → Back to live TV: the channel you were watching, full screen.
        VodHost.afterStop = { if (AppColors.palette.opt(io.github.petebutty.viewarr.ui.theme.UiOpts.AFTER_FILM) == 1) hs.goLive() }
        LiveShortcutsBridge.setRows = { id, rows -> remoteVm.setShortcutRows(id, rows) }
        LiveShortcutsBridge.move = { sc, d -> hs.moveShortcut(sc, d) }
        LiveShortcutsBridge.refresh = { sc -> hs.refreshOne(sc) }
    }
    // Live TV controls → Down → Down: Continue watching, Recent channels, Recently added (Live TV hub, step 3).
    // The rows under the info bar (live TV) and their preview cards: built in their own function, as HomeScreen
    // was over Android's size limit for optimising a function.
    val infoBarRows = rememberInfoBarRows(hs, postersOn, preloadMusic, sportsHub)
    val liveInfoRows = infoBarRows.live
    val previewFor = infoBarRows.previewFor
    val posterRows = infoBarRows.posters

    // ---- The corner picture, highlighted (Settings → Live TV → Highlight the corner picture): at the page's edge,
    // pressing towards it moves onto it; then Full screen or Close (Left / Right, OK).
    val cornerFocus = remember { FocusRequester() }
    // Set just before moving onto the picture on purpose: otherwise it can't take the highlight (when a page briefly
    // has nothing to highlight, e.g. opening a title, Android handed it to the picture, and it stuck there). A plain
    // holder, read when the focus system asks, not a state (it must count in the same moment it's set).
    val cornerArm = remember { booleanArrayOf(false) }
    val rootFocus = LocalFocusManager.current
    // A film's picture is drawn over everything (search and the TV guide too); live TV's only over another page, and
    // search covers it.
    val filmInCorner = VodHost.active && VodHost.mini
    val cornerReachable = pictureInCorner && (hs.page != Page.Tv || filmInCorner) &&
        !hs.menuOpen && !hs.showAccounts && !hs.pipMenu &&
        AppColors.palette.opt(UiOpts.CORNER_FOCUS) == 0
    LaunchedEffect(pictureInCorner) { if (!pictureInCorner) hs.cornerFocused = false }

    // The film and show players' rows under their shortcuts: the same as live TV's.
    SideEffect {
        LiveShortcutsBridge.previewFor = previewFor
        LiveShortcutsBridge.defaultRows = posterRows
    }
    Box(
        Modifier
            .fillMaxSize()
            // Towards the corner picture with nowhere further to go on the page (the end of a row, the bottom row):
            // onto the picture. Every other move is the page's own, as before.
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !cornerReachable || hs.cornerFocused) return@onKeyEvent false
                val at = AppColors.palette.opt(UiOpts.MINI_CORNER) // 0 BR, 1 BL, 2 TR, 3 TL
                val dir = when (e.key) {
                    Key.DirectionRight -> if (at == 0 || at == 2) FocusDirection.Right else null
                    // (Top left: only Up reaches it. Left at a page's edge is the menu, everywhere; it went onto the
                    // picture with no way on, so Left on a film's page did nothing.)
                    Key.DirectionLeft -> if (at == 1) FocusDirection.Left else null
                    Key.DirectionDown -> if (at < 2) FocusDirection.Down else null
                    Key.DirectionUp -> if (at >= 2) FocusDirection.Up else null
                    else -> null
                } ?: return@onKeyEvent false
                // (A page that keeps the highlight inside itself, a title page or search, blocks the move at its edge;
                // that counts as handled, so it's asked about too.)
                EdgeBlocked.hit = false
                if (rootFocus.moveFocus(dir) && !EdgeBlocked.hit) return@onKeyEvent true
                cornerArm[0] = true
                EdgeBlocked.lettingOut { runCatching { cornerFocus.requestFocus() }.isSuccess }.also { if (!it) cornerArm[0] = false }
            }
            // Every button press first (HomeState.onRootKey: holds, reminders, the corner picture, the button table…).
            .onPreviewKeyEvent { e -> hs.onRootKey(e) },
    ) {
        // The menu over a page (as TiviMate): the page and the preview pushed right beside the see-through strip,
        // sliding along together as it opens and grows (Left: the main list too).
        val guidePush = animateIntAsState(
            if (hs.menuOpen && !hs.fullscreen && !hs.liveBehind && !hs.menuFullPage && !VodHost.behind) hs.menuWidth else 0,
            tween(160, easing = FastOutSlowInEasing),
            label = "guidePush",
        )
        // (The menu over a full-screen film: only the menu shows, the page stays out of sight underneath.)
        PagesLayer(hs, cornerArm, cornerFocus, cornerReachable, guideFocus, guidePush, mediaFocus, sportsFocus, sportsHub)

        val channel = playing.takeIf { hs.page == Page.Tv || hs.liveBehind || hs.livePreview }
        // The live channel list: on its own over full-screen live TV, or as the menu strip's last column.
        val channelPanel: @Composable (Boolean, () -> Unit) -> Unit = { embedded, onLeft ->
            channel?.let { ch0 ->
                ChannelListOverlay(
                    vm = vm,
                    serverUrl = session?.serverUrl.orEmpty(),
                    current = ch0,
                    now = now,
                    onPick = { ch ->
                        hs.channelListOpen = false; hs.menuOpen = false
                        // Another channel, or the same number on another source (after switching source).
                        if (ch.id != ch0.id || ch.uuid != ch0.uuid) {
                            playback.play(ch, session)
                            vm.selectChannel(ch.id)
                        }
                        hs.showBanner()
                    },
                    onClose = { hs.channelListOpen = false },
                    onCatchup = { ch, prog ->
                        hs.channelListOpen = false; hs.menuOpen = false
                        playback.playCatchup(ch, prog, session)
                        vm.selectChannel(ch.id)
                        hs.showBanner()
                    },
                    onFuture = { ch, prog -> hs.channelListOpen = false; hs.menuOpen = false; hs.recordTarget = ch to prog },
                    onLeft = onLeft,
                    embedded = embedded,
                )
            }
        }
        LiveTvLayer(hs, channel, channelPanel, guidePush, playerFocus, liveMenuKey, liveInfoRows, previewFor)

        // "Rewind?" after Left on full-screen live TV: gone after 4 s, or as soon as anything else happens.
        LaunchedEffect(hs.rewindAsk) { if (hs.rewindAsk != 0L) { delay(4_000); hs.rewindAsk = 0L } }
        LaunchedEffect(hs.fullscreen, hs.osdVisible, playing?.id, hs.page) { hs.rewindAsk = 0L }
        AnimatedVisibility(
            hs.rewindAsk != 0L && hs.fullscreen && hs.page == Page.Tv,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            enter = androidx.compose.animation.EnterTransition.None, exit = androidx.compose.animation.ExitTransition.None,
        ) {
            Row(
                Modifier.background(AppColors.Panel.copy(alpha = 0.94f), RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Replay, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
                Text("Rewind?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, modifier = Modifier.padding(start = 10.dp))
                Text("   Press ◀ again, or hold ◀", fontSize = 13.sp, color = AppColors.TextSecondary)
            }
        }
        // What the buttons do here (Settings → Appearance → Menu → Button hints), following the button settings.
        if (!hs.menuOpen && !hs.quickSearch && !hs.showAccounts && hs.recordTarget == null && hs.infoTarget == null && !VodHost.active) {
            when {
                hs.page == Page.Tv && hs.fullscreen && !hs.osdVisible && !hs.channelListOpen && !hs.multiView -> ButtonHint(
                    "live-back", "OK: controls  ·  ▲ ▼ channels  ·  Back: mini player  ·  hold Back: close the player")
                hs.page == Page.Tv && !hs.fullscreen && session != null -> ButtonHint(
                    if (playing != null) "guide-back" else "guide", "OK: watch  ·  ◀ menu  ·  hold ◀ catch-up  ·  Back: " + (if (playing != null) "full screen  ·  hold Back: close the player" else when (guideBack) { GuideBack.Menu -> "menu"; else -> "exit" }))
                hs.page == Page.Movies || hs.page == Page.Shows -> ButtonHint(
                    "media", "OK: open  ·  hold OK: options  ·  ◀ menu  ·  hold Back: " + ButtonMap.action(remote, BtnScreen.Pages, BtnPress.BackHold).label.lowercase())
                else -> Unit
            }
        }
        // A film or episode in the corner: Back brings it full screen, holding Back closes it. (Full screen's hint is
        // drawn over the player, in AppRoot.)
        if (VodHost.active && VodHost.mini && !hs.menuOpen && !hs.quickSearch) ButtonHint("film-mini", "Back: full screen  ·  hold Back: close the player")
        // Guide and wallpaper dimming only show on the guide: the panel switches the view as you move to them.
        if (hs.transparencyOpen && hs.page == Page.Tv) TransparencyPanel(
            onClose = { hs.transparencyOpen = false; if (playing != null) hs.fullscreen = true },
            onRow = { r -> if (playing != null) hs.fullscreen = r == 0 },
            onInfoBar = { show -> hs.bannerHeld = show && playing != null; if (hs.bannerHeld) { hs.fullscreen = true; hs.showBanner() } },
        )
        // Going right into a page (a library, a service, a channel group's guide): the menu's glass slides back out to the
        // left with the page and the preview moving back, rather than vanishing while they move.
        if (!hs.menuOpen && guidePush.value > 0 && !hs.fullscreen && !hs.liveBehind) Box(
            Modifier.fillMaxHeight()
                .offset { IntOffset(guidePush.value - hs.menuWidth, 0) }
                .layout { m, c -> val pl = m.measure(Constraints.fixed(hs.menuWidth, c.maxHeight)); layout(hs.menuWidth, c.maxHeight) { pl.place(0, 0) } }
                .background(AppColors.MenuPanel),
        )
        MenuLayer(hs, channel, channelPanel, guidePush, menuServices)

        SearchLayer(hs, filmInCorner)

        DialogsLayer(hs, cornerArm, cornerFocus, cornerReachable, filmInCorner, pictureInCorner, guideFocus, mediaFocus, searchMedia, onSignOut)
    }
}

@Composable
internal fun NoLiveTv(focusRequester: FocusRequester, focusEnabled: Boolean, onSetUp: () -> Unit, onOpenMenu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Live TV isn't set up", fontSize = 26.sp, color = AppColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text("Connect your Dispatcharr server to watch channels and use the TV guide.", fontSize = 15.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(220.dp).focusRequester(focusRequester)) {
                if (focusEnabled) MenuItem(text = "Set up Live TV", selected = false, onClick = onSetUp)
            }
            Box(Modifier.width(160.dp)) { MenuItem(text = "Menu", selected = false, onClick = onOpenMenu) }
        }
    }
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.LiveTvLayer(
    hs: HomeState,
    channel: io.github.petebutty.viewarr.data.local.ChannelListItem?,
    channelPanel: @Composable (Boolean, () -> Unit) -> Unit,
    guidePush: androidx.compose.runtime.State<Int>,
    playerFocus: FocusRequester,
    liveMenuKey: TapHold,
    liveInfoRows: List<OsdRow>,
    previewFor: (String) -> List<OsdRow>?,
) {
    // (Read here as HomeScreen reads them, so this part redraws on its own when they change.)
    val vm = hs.vm; val playback = hs.playback; val session = hs.session; val remote = hs.remote
    val playing by playback.channel.collectAsStateWithLifecycle()
    val playState by playback.state.collectAsStateWithLifecycle()
    val catchup by playback.catchup.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val favsNow by vm.favourites.collectAsStateWithLifecycle()
    // Live TV into the guide's preview and back: a glide (the window's real place and size; the picture is a
    // SurfaceView, scaled by the box's display hardware). Settings → Playback → Picture-in-picture → Live TV animation.
    val liveShrink = animateFloatAsState(
        if (hs.fullscreen || hs.liveBehind) 0f else 1f,
        tween(300, easing = FastOutSlowInEasing),
        label = "live",
    )
    val list = channel?.let { hs.programsOf(it) }.orEmpty()
    val current = catchup ?: GuideViewModel.programAt(list, now)
    if (channel != null && !(hs.multiView && hs.fullscreen)) {
        val next = if (catchup != null) null else current?.let { c -> list.firstOrNull { it.startTime >= c.endTime } }
        val browsed = hs.browseChannel
        val browsedList = browsed?.let { hs.programsOf(it) }.orEmpty()
        val browsedNow = browsed?.let { GuideViewModel.programAt(browsedList, now) }
        PlayerLayer(
            vm = playback,
            fullscreen = hs.fullscreen,
            browse = browsed?.let { Triple(it, browsedNow, browsedNow?.let { c -> browsedList.firstOrNull { p -> p.startTime >= c.endTime } }) },
            bannerVisible = hs.bannerVisible && !hs.osdVisible && !hs.liveBehind && !hs.livePreview,
            current = current,
            next = next,
            now = now,
            serverUrl = session?.serverUrl.orEmpty(),
            // Read in the layout and draw steps only, so the glide doesn't rebuild the screen every frame.
            // (A library over live TV: the picture drawn first, under the page, so the see-through page is on top.)
            modifier = Modifier.zIndex(if (hs.liveBehind) -1f else 0f).fillMaxSize()
                .layout { measurable, constraints ->
                    val p = liveShrink.value
                    val fw = constraints.maxWidth
                    val fh = constraints.maxHeight
                    // The small window: the guide's preview place (320 x 180, top left), and in between on the way.
                    val sw = 320.dp.roundToPx()
                    val sh = 180.dp.roundToPx()
                    val sx = 12.dp.roundToPx()
                    val sy = 20.dp.roundToPx()
                    val cw = (fw + (sw - fw) * p).toInt().coerceAtLeast(1)
                    val ch = (fh + (sh - fh) * p).toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(cw, ch))
                    layout(fw, fh) { placeable.place((sx * p).toInt() + (guidePush.value * p).toInt(), (sy * p).toInt()) }
                }
                .graphicsLayer { clip = liveShrink.value > 0.99f; shape = RoundedCornerShape(8.dp) },
        )
    }

    if (hs.fullscreen && hs.multiView && channel != null) {
        MultiView(
            vm = playback,
            programFor = { ch -> GuideViewModel.programAt(hs.programsOf(ch), now) },
            now = now,
            onExit = { ch ->
                hs.multiView = false
                if (ch.id != playback.channel.value?.id) {
                    playback.play(ch, session)
                    vm.selectChannel(ch.id)
                }
                hs.showBanner()
            },
            channelPicker = { cur, onPick, onClose ->
                Box(Modifier.fillMaxSize()) {
                    ChannelListOverlay(
                        vm = vm,
                        serverUrl = session?.serverUrl.orEmpty(),
                        current = cur,
                        now = now,
                        onPick = onPick,
                        onClose = onClose,
                        // (Left closes the picker: it was swallowed, nothing happening.)
                        onLeft = onClose,
                        // A programme from the list's programme panel: that channel, live.
                        onCatchup = { ch, _ -> onPick(ch) },
                    )
                }
            },
        )
    } else if (hs.fullscreen) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(playerFocus)
                .focusable(enabled = !hs.osdVisible && !hs.channelListOpen && !hs.transparencyOpen)
                .onPreviewKeyEvent { e ->
                    if (hs.osdVisible || hs.channelListOpen) return@onPreviewKeyEvent false
                    // Menu: a tap opens audio & subtitles when it's let go. Holding it is search (handled above),
                    // so acting on the press opened audio as well.
                    val action = RemoteKeys.action(e)
                    if (action == RemoteAction.Menu) {
                        if (liveMenuKey.feed(e) == Press.Tap) {
                            if (playState is PlaybackState.Error) playback.retry() else hs.showOsd(audio = true)
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown) {
                        if (action == RemoteAction.Left) hs.rewindHeld = false
                        return@onPreviewKeyEvent false
                    }
                    if (action != RemoteAction.Left) hs.rewindAsk = 0L
                    // Settings → Remote buttons → Live TV → Left / Right, as TiviMate: Left the channel list, Right
                    // back to the channel watched before (catch-up still moves through the programme).
                    if (remote.liveSides == LiveSides.ListAndLast && catchup == null && !hs.multiView) {
                        if (action == RemoteAction.Left) {
                            if (e.nativeKeyEvent.repeatCount == 0) hs.channelListOpen = true
                            return@onPreviewKeyEvent true
                        }
                        if (action == RemoteAction.Right) {
                            if (e.nativeKeyEvent.repeatCount == 0) hs.lastChannel()
                            return@onPreviewKeyEvent true
                        }
                    }
                    when (action) {
                        // Live: ask first. In catch-up (already behind live) Left steps back straight away.
                        RemoteAction.Left -> if (catchup == null) {
                            val ch = playing
                            val again = hs.rewindAsk != 0L && System.currentTimeMillis() - hs.rewindAsk < 4_000
                            when {
                                hs.rewindHeld -> Unit
                                ch == null || ch.catchupDays <= 0 -> { hs.toast("This channel has no catch-up to rewind") }
                                again || e.nativeKeyEvent.repeatCount > 0 -> {
                                    hs.rewindAsk = 0L
                                    hs.rewindHeld = e.nativeKeyEvent.repeatCount > 0
                                    playback.seekBy(-remote.seek.ms)
                                    hs.showBanner()
                                }
                                else -> hs.rewindAsk = System.currentTimeMillis()
                            }
                        } else {
                            playback.seekBy(-remote.seek.ms)
                            hs.showBanner()
                        }
                        RemoteAction.Up, RemoteAction.Down -> when {
                            // Browsing in the info bar (Settings → Live TV → Up / Down while watching) wins over the list.
                            AppColors.palette.opt(io.github.petebutty.viewarr.ui.theme.UiOpts.LIVE_UPDOWN) != 0 ->
                                hs.upDown(if ((action == RemoteAction.Up) != (remote.zap == ZapDirection.UpPrevious)) 1 else -1)
                            remote.zap == ZapDirection.ChannelList -> hs.channelListOpen = true
                            remote.zap == ZapDirection.UpNext -> hs.zap(if (action == RemoteAction.Up) 1 else -1)
                            else -> hs.zap(if (action == RemoteAction.Up) -1 else 1)
                        }
                        RemoteAction.ChannelDown -> hs.upDown(-1)
                        RemoteAction.ChannelUp -> hs.upDown(1)
                        RemoteAction.Ok -> when {
                            playState is PlaybackState.Error -> playback.retry()
                            remote.playerOk == PlayerOk.Banner -> hs.showBanner()
                            remote.playerOk == PlayerOk.ChannelList && !hs.multiView -> hs.channelListOpen = true
                            else -> hs.showOsd()
                        }
                        RemoteAction.PlayPause -> {
                            playback.togglePause()
                            hs.showBanner()
                        }
                        // The remote's own rewind button means it: no question.
                        RemoteAction.Rewind -> {
                            hs.rewindAsk = 0L
                            playback.seekBy(-remote.seek.ms)
                            hs.showBanner()
                        }
                        RemoteAction.Right, RemoteAction.FastForward -> {
                            playback.seekBy(remote.seek.ms)
                            hs.showBanner()
                        }
                        RemoteAction.Info -> hs.showBanner()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        )
        // The channel list on its own; Left opens the menu strip beside it (groups, then the main list).
        if (hs.channelListOpen && channel != null && !hs.menuOpen) Box(Modifier.fillMaxSize()) {
            channelPanel(false) { hs.menuOpen = true; hs.menuOnMain = false }
        }
        if (hs.osdVisible && channel != null) {
            PlayerOsd(
                vm = playback,
                channel = channel,
                current = current,
                now = now,
                serverUrl = session?.serverUrl.orEmpty(),
                onGuide = {
                    hs.osdVisible = false
                    hs.fullscreen = false
                },
                onClose = { hs.osdVisible = false },
                onActivity = { hs.osdUsedAt[0] = System.currentTimeMillis() },
                // Low-memory mode: no multi-view (up to four players at once).
                // Settings over live TV: the menu opens on Settings; Back closes it, back to the channel.
                onSettings = { hs.osdVisible = false; hs.menuOnSettings = true; hs.menuOpen = true },
                onTransparency = { hs.osdVisible = false; hs.transparencyOpen = true },
                onSearch = { hs.osdVisible = false; hs.doButton(BtnScreen.Live, BtnAction.Search) },
                // Extra buttons (once added to the bar: hold OK on a button → OK on one marked "not added").
                extraButtons = listOf(
                    io.github.petebutty.viewarr.ui.player.BarExtra("favourite",
                        if (channel.uuid in favsNow) io.github.petebutty.viewarr.ui.common.AppIcons.Star else io.github.petebutty.viewarr.ui.common.AppIcons.StarOutline,
                        if (channel.uuid in favsNow) "Remove from favourites" else "Add to favourites") {
                        hs.toast(if (vm.toggleFavourite(channel)) "${channel.name} added to favourites" else "${channel.name} removed from favourites")
                    },
                    io.github.petebutty.viewarr.ui.player.BarExtra("lastchannel", io.github.petebutty.viewarr.ui.common.AppIcons.History, "Last channel") { hs.osdVisible = false; hs.doButton(BtnScreen.Live, BtnAction.LastChannel) },
                    io.github.petebutty.viewarr.ui.player.BarExtra("movies", io.github.petebutty.viewarr.ui.common.AppIcons.Movie, "Movies") { hs.osdVisible = false; hs.doButton(BtnScreen.Live, BtnAction.Movies) },
                    io.github.petebutty.viewarr.ui.player.BarExtra("shows", io.github.petebutty.viewarr.ui.common.AppIcons.Tv, "Shows") { hs.osdVisible = false; hs.doButton(BtnScreen.Live, BtnAction.Shows) },
                    io.github.petebutty.viewarr.ui.player.BarExtra("recordings", io.github.petebutty.viewarr.ui.common.AppIcons.Recordings, "Recordings") { hs.osdVisible = false; hs.doButton(BtnScreen.Live, BtnAction.Recordings) },
                ),
                // No shortcut row under the buttons any more: every library and service is in the menu on the left.
                shortcuts = emptyList(),
                posterRows = emptyList(),
                // (Settings → Appearance → Info bar & controls → Info bar: recent channels.)
                downRows = liveInfoRows,
                previewFor = previewFor,
                onHideShortcut = { sc -> hs.hideShortcut(sc) },
                onMoveShortcut = { sc, d -> hs.moveShortcut(sc, d) },
                onRefreshShortcut = { sc -> hs.refreshOne(sc) },
                onStop = { hs.doButton(BtnScreen.Live, BtnAction.Stop) },
                onMultiView = if (!AppColors.palette.heavyExtras) null else ({
                    hs.osdVisible = false
                    if (catchup != null) playback.goLive()
                    hs.multiView = true
                }),
                openAudio = hs.osdAudio,
                // Recording: only for people allowed to record (Dispatcharr dvr_access; admins always).
                onRecord = current?.takeIf { catchup == null && Access.record }?.let { prog ->
                    {
                        hs.osdVisible = false
                        hs.recordTarget = channel to prog
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.DialogsLayer(
    hs: HomeState,
    cornerArm: BooleanArray,
    cornerFocus: FocusRequester,
    cornerReachable: Boolean,
    filmInCorner: Boolean,
    pictureInCorner: Boolean,
    guideFocus: FocusRequester,
    mediaFocus: FocusRequester,
    searchMedia: MediaViewModel,
    onSignOut: () -> Unit,
) {
    // (Read here as HomeScreen reads them, so this part redraws on its own when they change.)
    val vm = hs.vm; val playback = hs.playback; val session = hs.session; val dvr = hs.dvr; val fromStartScope = hs.fromStartScope
    val connectionNote by playback.connectionNote.collectAsStateWithLifecycle()
    val dvrRecordings by dvr.recordings.collectAsStateWithLifecycle()
    // The picture in the corner: its highlight (Full screen / Close) and, holding Back, its menu (CornerPicture.kt).
    // A film playing at all (not only while it's in the mini player): a held Back can bring it full screen just before
    // the pop-up's answer, and live TV then started in its place.
    fun cornerFull() { if (VodHost.active) VodHost.expand() else hs.goLive() }
    fun cornerClose() { if (VodHost.active) VodHost.close() else playback.stop() }
    if (pictureInCorner && (hs.page != Page.Tv || filmInCorner)) CornerHighlight(
        film = filmInCorner, focusRequester = cornerFocus, arm = cornerArm, reachable = cornerReachable, focused = hs.cornerFocused,
        onFocused = { hs.cornerFocused = it }, onFullScreen = ::cornerFull, onClose = ::cornerClose,
        // (Nothing above it to land on: the page's own starting point, or search's.)
        onLeave = { runCatching { if (hs.page == Page.Tv) guideFocus.requestFocus() else mediaFocus.requestFocus() } },
    )
    if (hs.pipMenu) CornerPictureMenu(
        film = VodHost.active, onFullScreen = { hs.pipMenu = false; cornerFull() }, onClosePicture = { hs.pipMenu = false; cornerClose() },
        onDismiss = { hs.pipMenu = false },
    )

    hs.infoTarget?.let { (ch, prog) ->
        ProgramInfoDialog(ch, prog, dvr, onRecord = { hs.infoTarget = null; hs.recordTarget = ch to prog }, onClose = { hs.infoTarget = null })
    }
    // Recording started / finished / failed: bottom-right, a few seconds, no buttons.
    hs.dvrNote?.let { text ->
        Text(
            text, fontSize = 15.sp, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomEnd).padding(28.dp).width(420.dp)
                .glass(10.dp, if (text.startsWith("⚠")) Color(0xFFE5534B) else null)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    // No free provider connection for the channel just tuned: say what's using them.
    if (hs.page == Page.Tv) connectionNote?.let { text ->
        val recordingNow = dvrRecordings.firstOrNull { it.state == DvrState.Recording }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 28.dp).width(640.dp)
                .glass(10.dp, Color(0xFFE5A23B))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text("All connections in use", fontSize = 13.sp, color = Color(0xFFE5A23B))
            Text(text, fontSize = 15.sp, color = AppColors.TextPrimary)
            recordingNow?.let { Text("Recording now: ${it.title}", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
    hs.dueReminder?.let { r ->
        Column(
            Modifier.align(Alignment.TopEnd).padding(28.dp).width(380.dp)
                .glass(12.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("⏰  Starting now", fontSize = 13.sp, color = AppColors.Accent)
            Text(r.title, fontSize = 18.sp, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(r.channelName, fontSize = 14.sp, color = AppColors.TextSecondary)
            Text("OK: watch  ·  Back: dismiss", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
    // Search opens on the title (so there's something to pick if it isn't matched), then the title's page opens:
    // playing it from the start, or (Go to show) on its episodes where you're up to.
    fun openTitle(ch: ChannelListItem, prog: GuideProgram, play: Boolean) {
        hs.searchStart = prog.title.removePrefix("New: ")
        hs.fullscreen = false
        hs.quickSearch = true
        // Films in the guide usually end their description with the year, e.g. "(2014)".
        val year = prog.description?.let { Regex("\\((19|20)\\d{2}\\)").findAll(it).lastOrNull()?.value?.trim('(', ')')?.toIntOrNull() }
        fromStartScope.launch {
            if (play) searchMedia.watchFromStart(prog.title, prog.season, prog.episode, year)
            else searchMedia.goToTitle(prog.title, prog.season, prog.episode, year)
        }
    }
    hs.seasonPick?.let { m -> SeasonPicker(m, onClose = { hs.seasonPick = null }) }
if (hs.askSignOut) io.github.petebutty.viewarr.ui.dvr.ChoicePanel(
    title = "Sign out of everything?",
    subtitle = "This TV forgets all your accounts and Live TV sources (they stay on your servers). You'll need to set Viewarr up again.",
    choices = listOf(
        "Cancel" to { hs.askSignOut = false },
        "Sign out" to { hs.askSignOut = false; playback.stop(); onSignOut() },
    ),
    message = null,
    onClose = { hs.askSignOut = false },
)
    if (hs.seasonPick == null) hs.recordTarget?.let { (ch, prog) ->
        RecordDialog(ch, prog, dvr, onDetails = { hs.recordTarget = null; hs.infoTarget = ch to prog },
            favourite = (if (ch.uuid in vm.favourites.value) "★  Remove from favourites" else "★  Add to favourites") to {
                hs.recordTarget = null
                hs.toast(if (vm.toggleFavourite(ch)) "${ch.name} added to favourites" else "${ch.name} removed from favourites")
            },
            onChooseSeasons = { m -> hs.recordTarget = null; hs.seasonPick = m },
            onWatchFromStart = {
                hs.recordTarget = null
                val t = System.currentTimeMillis()
                // Catch-up first: the channel's own replay of this very showing.
                if (ch.catchupDays > 0 && prog.startTime < t && prog.startTime >= t - ch.catchupDays * 86_400_000L) {
                    playback.playCatchup(ch, prog, session)
                    vm.selectChannel(ch.id)
                } else openTitle(ch, prog, play = true)
            },
            onGoTo = { hs.recordTarget = null; openTitle(ch, prog, play = false) },
            // Only when some channel has catch-up (nothing to find otherwise).
            onSearchCatchup = if (vm.channels.value.none { it.catchupDays > 0 }) null else ({
                hs.recordTarget = null
                hs.searchStart = prog.title
                hs.searchVoice = false
                hs.searchCatchup = true
                hs.quickSearch = true
            }),
        ) { hs.recordTarget = null }
    }

    if (hs.stillAsk) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).glass(14.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Still watching?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Text("Press any button to carry on. Live TV stops in a minute otherwise.", fontSize = 15.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }

    if (hs.exitArmed != 0L && !hs.menuOpen) {
        Text(
            "Press Back again to exit",
            fontSize = 18.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .glass(24.dp)
                .padding(horizontal = 26.dp, vertical = 12.dp),
        )
    }

    (hs.digits.takeIf { it.isNotEmpty() } ?: hs.digitsMessage)?.let { text ->
        Text(
            text,
            fontSize = if (hs.digits.isNotEmpty()) 44.sp else 22.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 40.dp)
                .glass(10.dp)
                .padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SearchLayer(
    hs: HomeState,
    filmInCorner: Boolean,
) {
    // (Read here as HomeScreen reads them, so this part redraws on its own when they change.)
    val vm = hs.vm; val session = hs.session; val musicVm = hs.musicVm
    if (hs.showAccounts) {
        // Back from Accounts & sources (opened from Settings): back on Settings in the menu, like the other settings pages.
        SetupWizard(mode = SetupMode.Manage, onClose = {
            hs.showAccounts = false
            if (hs.accountsFromMenu) { hs.accountsFromMenu = false; hs.menuOnSettings = true; hs.menuOpen = true }
        })
    }

    // Search popped up from any screen: the same search page, on top; Back closes it.
    if (hs.quickSearch) {
        BackHandler { hs.quickSearch = false; hs.searchStart = null }
        // Solid: the screen underneath showing through between results (guide text, times) looked like a glitch.
        // The remote stays inside search until it closes.
        // Live TV in the corner: a window cut in search where its picture is, so it shows (the video's drawn
        // behind the app; a film's picture is drawn over everything anyway).
        Box(Modifier.fillMaxSize().appBackground().then(if (hs.infoTarget == null && hs.recordTarget == null) Modifier.trapFocus() else Modifier)) {
            SearchPage(
                guide = vm,
                serverUrl = session?.serverUrl.orEmpty(),
                // (Not switched off while the corner picture has the highlight: search's box, results and buttons
                // would all stop being reachable, and coming back off the picture had nowhere to land. It never
                // pulls the highlight back on its own, so it needn't stand aside.)
                focusEnabled = hs.infoTarget == null && hs.recordTarget == null && hs.seasonPick == null,
                openKeyboard = hs.searchStart == null && !hs.searchVoice,
                startQuery = hs.searchStart,
                startVoice = hs.searchVoice,
                startCatchup = hs.searchCatchup,
                onProgramme = { ch, prog -> hs.recordTarget = ch to prog },
                onCatchup = { ch, prog ->
                    hs.quickSearch = false
                    hs.searchStart = null
                    hs.watchChannel(ch, catchup = prog, endMultiView = true)
                },
                // Left at the edge stays in search (it used to close it and open the menu); Back is the way out.
                onOpenMenu = {},
                onExit = { hs.quickSearch = false; hs.searchStart = null; hs.searchCatchup = false },
                onMusic = { card -> hs.quickSearch = false; hs.searchStart = null; musicVm.openFromSearch(card); hs.openPage(Page.Music) },
                onChannel = { ch ->
                    hs.quickSearch = false
                    hs.watchChannel(ch, endMultiView = true)
                },
            )
        }
    }
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MenuLayer(
    hs: HomeState,
    channel: io.github.petebutty.viewarr.data.local.ChannelListItem?,
    channelPanel: @Composable (Boolean, () -> Unit) -> Unit,
    guidePush: androidx.compose.runtime.State<Int>,
    menuServices: List<Pair<String, List<Pair<String, String>>>>,
) {
    // (Read here as HomeScreen reads them, so this part redraws on its own when they change.)
    val vm = hs.vm; val session = hs.session; val moviesVm = hs.moviesVm; val showsVm = hs.showsVm
    val playing by hs.playback.channel.collectAsStateWithLifecycle()
    if (hs.menuOpen) Box(Modifier.fillMaxSize().offset { IntOffset(if (!hs.fullscreen && !hs.liveBehind && !hs.menuFullPage && !VodHost.behind) guidePush.value - hs.menuWidth else 0, 0) }) {
        MenuOverlay(
            onPlayRecording = { hs.menuOpen = false; hs.recordingPlayback = it },
            // See-through everywhere: the page is pushed aside beside it (or video shows through).
            seeThrough = true,
            vm = vm,
            session = session,
            page = hs.page,
            onOpenPage = hs::openPage,
            onAccounts = {
                hs.menuOpen = false
                hs.showAccounts = true
                hs.accountsFromMenu = true
            },
            onSettings = hs.menuOnSettings,
            onMain = hs.menuOnMain,
            onGroups = hs.menuOnGroups,
            onLibrary = hs.menuOnLibrary,
            onSearchFor = { q -> hs.searchStart = q; hs.searchVoice = false; hs.openPage(Page.Search) },
            onVoiceSearch = { hs.searchStart = null; hs.searchVoice = true; hs.openPage(Page.Search) },
            onClose = { hs.menuOpen = false; hs.menuOnSettings = false; hs.menuOnMain = false },
            backOut = hs.menuFromGuideBack,
            onBackOut = {
                hs.menuOpen = false; hs.menuOnSettings = false; hs.menuOnMain = false
                // (The guide, and Music / Recordings / Search whose Back opened the menu: Back again exits.)
                if (!hs.fullscreen && hs.page in setOf(Page.Tv, Page.Music, Page.Recordings, Page.Search)) hs.exitArmed = System.currentTimeMillis()
            },
            // An "are you sure?" first (it signs out of every account and source on this TV).
            onSignOut = { hs.menuOpen = false; hs.askSignOut = true },
            channelsPanel = if (hs.fullscreen && hs.channelListOpen && channel != null) ({ onLeft -> channelPanel(true, onLeft) }) else null,
            // A group picked: back to its channels, the channel list on its own.
            onGroupPicked = { hs.menuOpen = false; hs.menuOnMain = false },
            onWidth = { hs.menuWidth = it },
            // (A film in the mini player counts too: it's drawn over everything, so a settings page must leave its place.)
            previewOn = (playing != null && !hs.fullscreen && !hs.liveBehind && (hs.page == Page.Tv || hs.livePreview)) || (VodHost.active && VodHost.mini),
            onFullPage = { hs.menuFullPage = it },
            // Live TV, Movies, Shows, Music, Recordings: the page behind changes as you move onto it (not from over
            // full-screen video).
            // (Moving through the menu changes the page behind it, except over full-screen video. A film in the mini
            // player doesn't count: it keeps playing in the corner while the page changes, as when browsing.)
            onHoverPage = { p ->
                if (!hs.fullscreen && !hs.liveBehind && !filmFullScreen()) {
                    hs.sportsOpen = false
                    // Back on Movies / Shows from a service: the page's own rows again, not the service's.
                    // (A library left open from My Library is closed by the menu's Movies / Shows entries instead:
                    // My Library's own hover opens a library and comes through here.)
                    if (p == Page.Movies && moviesVm.currentRowSet.value != null) { moviesVm.openLibrary(null, MediaType.Movie); moviesVm.jumpTo(null) }
                    if (p == Page.Shows && showsVm.currentRowSet.value != null) { showsVm.openLibrary(null, MediaType.Show); showsVm.jumpTo(null) }
                    if (p != hs.page) hs.openPage(p, closeMenu = false)
                }
            },
            services = menuServices,
            liveEntries = listOf("Live now", "Sports"),
            openEntry = when {
                hs.sportsOpen -> "Sports"
                hs.page == Page.Shows -> showsVm.currentRowSet.value?.takeIf { it.startsWith("name:") }?.let { "svc:" + it.removePrefix("name:") }
                hs.page == Page.Movies -> moviesVm.currentRowSet.value?.takeIf { it.startsWith("name:") }?.let { "svc:" + it.removePrefix("name:") }
                else -> null
            },
            onLiveEntry = { label, into -> if (into || (!hs.fullscreen && !hs.liveBehind && !filmFullScreen())) hs.openLiveEntry(label, closeMenu = into) },
            // Moving onto a service: its page behind (not over full-screen video); Right / OK: into it.
            onService = { name, rowId, into -> if (into || (!hs.fullscreen && !hs.liveBehind && !filmFullScreen())) { hs.sportsOpen = false; hs.openService(name, closeMenu = into, rowId = rowId) } },
        )
    }
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PagesLayer(
    hs: HomeState,
    cornerArm: BooleanArray,
    cornerFocus: FocusRequester,
    cornerReachable: Boolean,
    guideFocus: FocusRequester,
    guidePush: androidx.compose.runtime.State<Int>,
    mediaFocus: FocusRequester,
    sportsFocus: FocusRequester,
    sportsHub: SportsHub,
) {
    // (Read here as HomeScreen reads them, so this part redraws on its own when they change.)
    val vm = hs.vm; val playback = hs.playback; val session = hs.session; val remote = hs.remote; val dvr = hs.dvr
    val moviesVm = hs.moviesVm; val showsVm = hs.showsVm; val musicVm = hs.musicVm; val fromStartScope = hs.fromStartScope
    val playing by playback.channel.collectAsStateWithLifecycle()
    val playState by playback.state.collectAsStateWithLifecycle()
    val catchup by playback.catchup.collectAsStateWithLifecycle()
    val dvrRecordings by dvr.recordings.collectAsStateWithLifecycle()
    val sportFollows by hs.sportsVm.follows.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().offset { IntOffset(guidePush.value, 0) }.graphicsLayer { alpha = if (VodHost.behind) 0f else 1f }) {
    SideEffect { io.github.petebutty.viewarr.ui.theme.CornerPicture.pushPx = { guidePush.value } }
    if (hs.page == Page.Search) {
        SearchPage(
            guide = vm,
            serverUrl = session?.serverUrl.orEmpty(),
            focusEnabled = !hs.menuOpen && !hs.cornerFocused && !hs.showAccounts && !hs.quickSearch && hs.infoTarget == null && hs.recordTarget == null && hs.seasonPick == null && !hs.sportsOpen,
            onProgramme = { ch, prog -> hs.recordTarget = ch to prog },
            onCatchup = { ch, prog ->
                hs.watchChannel(ch, catchup = prog)
            },
            onOpenMenu = { hs.menuOpen = true },
            onExit = hs::backFromPage,
            onMusic = { card -> musicVm.openFromSearch(card); hs.openPage(Page.Music) },
            onChannel = { ch ->
                hs.watchChannel(ch)
            },
        )
    } else if (hs.page == Page.Music) {
        MusicScreen(
            focusRequester = mediaFocus,
            focusEnabled = !hs.menuOpen && !hs.cornerFocused && !hs.showAccounts && !hs.quickSearch && !VodHost.covering && !MusicPlayer.nowPlayingOpen && !hs.sportsOpen,
            onOpenMenu = { hs.menuOpen = true },
            onBack = hs::backFromPage,
            onSetUp = { hs.showAccounts = true },
        )
    } else if (hs.page == Page.Recordings) {
        RecordingsScreen(
            vm = dvr,
            focusRequester = mediaFocus,
            focusEnabled = !hs.menuOpen && !hs.cornerFocused && !hs.showAccounts && !hs.quickSearch && !VodHost.covering && !hs.sportsOpen,
            onBack = hs::backFromPage,
            onPlay = { hs.recordingPlayback = it },
            onOpenMenu = { hs.menuOpen = true },
        )
    } else if (hs.page != Page.Tv) CompositionLocalProvider(LocalQuickSearchOpen provides hs.quickSearch) {
        MediaScreen(
            type = if (hs.page == Page.Movies) MediaType.Movie else MediaType.Show,
            cornerHasFocus = hs.cornerFocused,
            focusRequester = mediaFocus,
            focusEnabled = !hs.menuOpen && !hs.cornerFocused && !hs.showAccounts && !hs.quickSearch && !hs.sportsOpen,
            // Left from Movies / Shows: straight to the main menu (their own list is only "home" and "Jump to a
            // row" now). A library open (from My Library): the menu on My Library. A service's page: its rows.
            onOpenMenu = {
                val mvm = if (hs.page == Page.Movies) moviesVm else showsVm
                when {
                    mvm.currentRowSet.value != null -> Unit
                    mvm.browse.value != null -> { hs.menuOnLibrary = true; hs.menuOnMain = true }
                    else -> hs.menuOnMain = true
                }
                hs.menuOpen = true
            },
            onBack = {
                if (VodHost.active && VodHost.mini) { VodHost.expand(); return@MediaScreen }
                // Otherwise your Back setting (not always back to live TV).
                // Over live TV: back to the channel full screen.
                if (hs.liveBehind && playing != null) { hs.backOnPage(); return@MediaScreen }
                // The channel in the preview place: back to the guide.
                if (hs.livePreview && playing != null) { hs.backOnPage(); return@MediaScreen }
                when (remote.mediaBack) {
                    MediaBack.Exit -> hs.backOnPage()
                    MediaBack.Menu -> hs.menuOpen = true
                    MediaBack.Guide -> hs.openPage(Page.Tv)
                }
            },
        )
    } else if (session == null) {
        NoLiveTv(
            focusRequester = guideFocus,
            focusEnabled = !hs.menuOpen && !hs.cornerFocused && !hs.showAccounts && !hs.quickSearch && !hs.sportsOpen,
            onSetUp = { hs.showAccounts = true },
            onOpenMenu = { hs.menuOpen = true },
        )
    } else {
        GuideScreen(
            // A film in the corner: past the last (or first) channel, onto it (as at a page's edge).
            onPastEnd = { up ->
                val at = AppColors.palette.opt(UiOpts.MINI_CORNER)
                if (!cornerReachable || up != (at >= 2)) false else {
                    cornerArm[0] = true
                    EdgeBlocked.lettingOut { runCatching { cornerFocus.requestFocus() }.isSuccess }.also { if (!it) cornerArm[0] = false }
                }
            },
            vm = vm,
            recordings = dvrRecordings,
            playingChannelId = playing?.id,
            playingCatchupRowId = catchup?.rowId,
            serverUrl = session?.serverUrl.orEmpty(),
            focusRequester = guideFocus,
            focusEnabled = !hs.menuOpen && !hs.cornerFocused && !hs.fullscreen && !hs.showAccounts && !hs.quickSearch && !hs.sportsOpen,
            onOpenMenu = { hs.menuOpen = true },
            onSelect = { channel, program ->
                // Settings → Remote buttons → TV guide → OK: "Watch in the preview" stays in the guide (OK again on
                // what's playing: full screen).
                val inGuide = ButtonMap.action(remote, BtnScreen.Guide, BtnPress.Ok) == BtnAction.WatchInGuide
                if (!inGuide) ButtonMap.changed(remote, BtnScreen.Guide, BtnPress.Ok)?.let { hs.doButton(BtnScreen.Guide, it); return@GuideScreen }
                // Recorded on Dispatcharr (the guide shows it as recorded): OK plays the recording.
                val rec = program?.let { dvr.recordingFor(channel.id, it) }
                if (rec != null && rec.state == DvrState.Recorded && rec.hasFile) {
                    fromStartScope.launch { dvr.playback(rec)?.let { hs.recordingPlayback = it } ?: hs.toast("That recording couldn't be played") }
                    return@GuideScreen
                }
                val t = System.currentTimeMillis()
                val live = program == null || (program.startTime <= t && program.endTime > t)
                val replayable = program != null && program.endTime <= t && channel.catchupDays > 0 &&
                    program.startTime >= t - channel.catchupDays * 86_400_000L
                when {
                    live -> {
                        val alreadyLive = playing?.id == channel.id && catchup == null && playState !is PlaybackState.Error
                        if (!alreadyLive) playback.play(channel, session)
                        if (!inGuide || alreadyLive) { hs.fullscreen = true; hs.showBanner() }
                    }
                    replayable -> {
                        val already = catchup?.rowId == program!!.rowId && playing?.id == channel.id
                        if (!already) playback.playCatchup(channel, program, session)
                        if (!inGuide || already) { hs.fullscreen = true; hs.showBanner() }
                    }
                    // A programme still to come: show its details (Record options from there).
                    program != null && program.startTime > t ->
                        // Already set to record: straight to cancelling it. Otherwise the details, with record / remind.
                        if (dvr.recordingFor(channel.id, program) != null) hs.recordTarget = channel to program
                        else hs.infoTarget = channel to program
                    // Aired, with no catch-up for it: its details, rather than nothing happening.
                    program != null -> hs.infoTarget = channel to program
                }
            },
            // Holding OK: the programme's options (record / remind, watch it from your library or addons), whether
            // it's on now, still to come or already aired.
            onLongSelect = { channel, program ->
                ButtonMap.changed(remote, BtnScreen.Guide, BtnPress.OkHold)?.let { hs.doButton(BtnScreen.Guide, it); return@GuideScreen }
                if (program != null) hs.recordTarget = channel to program
                // No programme listed (nothing to record): hold OK just adds / removes the favourite.
                else hs.toast(if (vm.toggleFavourite(channel)) "${channel.name} added to favourites" else "${channel.name} removed from favourites")
            },
        )
    }
    if (hs.sportsOpen) SportsPage(
        hub = sportsHub,
        follows = sportFollows,
        // (Every channel, not just the guide's group on show: the plugin may name one in another group.)
        channels = dvr.channels.collectAsStateWithLifecycle().value.values.toList(),
        besidePreview = playing != null && !hs.fullscreen,
        focusRequester = sportsFocus,
        focusEnabled = !hs.menuOpen && !hs.showAccounts && !hs.quickSearch && !hs.fullscreen && hs.infoTarget == null && hs.recordTarget == null,
        onWatch = { ch -> hs.sportsOpen = false; hs.watchChannel(ch) },
        onToast = { hs.toast(it) },
        onOpenMenu = { hs.menuOpen = true },
        onBack = { hs.sportsOpen = false },
    )
    }
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun HomeCommandEffects(hs: HomeState) {
    // (Read here as HomeScreen reads them, so these effects see the latest values.)
    val vm = hs.vm; val playback = hs.playback; val dvr = hs.dvr; val fromStartScope = hs.fromStartScope
    val playing by playback.channel.collectAsStateWithLifecycle()
val commandLink by DeepLink.pendingCommand.collectAsStateWithLifecycle()
LaunchedEffect(commandLink) {
    val c = commandLink ?: return@LaunchedEffect
    DeepLink.pendingCommand.value = null
    fromStartScope.launch {
    when (c.kind) {
        // Channel up / down: from the channel playing (else the first), full screen.
        "step" -> {
            kotlinx.coroutines.withTimeoutOrNull(60_000) { vm.channels.first { it.isNotEmpty() } }
            if (playing == null) vm.channels.value.firstOrNull()?.let { hs.watchChannel(it) }
            else { hs.zap(if (c.arg == "up") 1 else -1); if (hs.page == Page.Tv) hs.fullscreen = true }
        }
        // Stop whatever's playing: live TV, a film or episode, music.
        "stop" -> { playback.stop(); VodHost.close(); io.github.petebutty.viewarr.playback.MusicPlayer.stop(); hs.fullscreen = false }
        "guide" -> { hs.menuOpen = false; hs.quickSearch = false; VodHost.minimise(); hs.openPage(Page.Tv); hs.fullscreen = false }
        "page" -> {
            hs.menuOpen = false; hs.quickSearch = false
            when (c.arg) {
                "movies" -> hs.openPage(Page.Movies); "shows" -> hs.openPage(Page.Shows); "music" -> hs.openPage(Page.Music)
                "recordings" -> hs.openPage(Page.Recordings); "guide" -> { hs.openPage(Page.Tv); hs.fullscreen = false }
                "sports" -> { if (hs.fullscreen) hs.fullscreen = false; hs.sportsOpen = true }
            }
        }
        // Record what's on now on that channel (or the one playing), as one press of Record.
        "record" -> {
            val all = kotlinx.coroutines.withTimeoutOrNull(60_000) { dvr.channels.first { it.isNotEmpty() } }?.values.orEmpty()
            val ch = c.uuid?.let { u -> all.firstOrNull { it.uuid.equals(u, ignoreCase = true) } } ?: c.number?.let { n -> all.firstOrNull { it.number == n } } ?: playing
            val prog = ch?.let { GuideViewModel.programAt(hs.programsOf(it), System.currentTimeMillis()) }
            when {
                ch == null || prog == null -> hs.toast("Nothing to record there")
                !Access.record -> hs.toast("Recording isn't allowed for this profile")
                dvr.recordingFor(ch.id, prog) != null -> hs.toast("Already recording: ${prog.title}")
                else -> { dvr.record(ch, prog); hs.toast("⏺ Recording from now: ${prog.title}") }
            }
        }
        "search" -> { hs.menuOpen = false; VodHost.minimise(); hs.searchStart = c.arg; hs.searchVoice = false; hs.quickSearch = true }
        // The channel list again (quick), or with guide=1 the whole guide.
        "refresh" -> { if (c.arg == "guide") vm.refreshGuide() else vm.refreshChannels() }
    }
    }
}
}

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun HomeLinkEffects(hs: HomeState) {
    // (Read here as HomeScreen reads them, so these effects see the latest values.)
    val vm = hs.vm; val playback = hs.playback; val dvr = hs.dvr; val fromStartScope = hs.fromStartScope
    val moviesVm = hs.moviesVm; val showsVm = hs.showsVm; val activity = hs.activity
    val playing by playback.channel.collectAsStateWithLifecycle()
    val catchup by playback.catchup.collectAsStateWithLifecycle()
LaunchedEffect(hs.page, playing == null) { if ((hs.page != Page.Movies && hs.page != Page.Shows) || playing == null) hs.liveBehind = false }
LaunchedEffect(Unit) { moviesVm.refreshPlexServers(); delay(20_000); moviesVm.refreshWatchNext() }
// The splash can go once the guide has its channels (the splash's own limit covers a guide that doesn't come).
LaunchedEffect(Unit) {
    vm.channels.first { it.isNotEmpty() }
    io.github.petebutty.viewarr.ui.common.StartupGate.ready = true
}
// viewarr://channel?uuid=… / ?number=…: that live channel, full screen. On a cold start it waits for the guide's
// channels first; the channel already playing is reconnected (a frozen stream).
val channelLink by DeepLink.pendingChannel.collectAsStateWithLifecycle()
LaunchedEffect(channelLink) {
    val req = channelLink ?: return@LaunchedEffect
    DeepLink.pendingChannel.value = null
    // (Run apart from this effect: clearing the request above restarts it, which cancelled a channel refresh
    // half-way through.)
    fromStartScope.launch {
    var all = kotlinx.coroutines.withTimeoutOrNull(60_000) { dvr.channels.first { it.isNotEmpty() } }?.values.orEmpty()
    fun find() = req.uuid?.let { u -> all.firstOrNull { it.uuid.equals(u, ignoreCase = true) } }
        ?: req.number?.let { n -> all.firstOrNull { it.number == n } }
    var ch = find()
    // Not known yet (added on Dispatcharr since the last fetch): the channel list again (quick, not the guide),
    // then look once more.
    if (ch == null) {
        hs.toast("Looking for that channel…")
        vm.refreshChannels()
        all = kotlinx.coroutines.withTimeoutOrNull(5_000) { dvr.channels.first { m -> m.values.any { c -> (req.uuid != null && c.uuid.equals(req.uuid, true)) || (req.number != null && c.number == req.number) } } }?.values ?: dvr.channels.first().values
        ch = find()
    }
    if (ch == null) { hs.toast("That channel isn't in Viewarr's guide"); return@launch }
    hs.quickSearch = false; hs.menuOpen = false; hs.sportsOpen = false; hs.channelListOpen = false; hs.osdVisible = false
    VodHost.close()
    hs.watchChannel(ch, endMultiView = true)
    }
}
val deepLink by DeepLink.pending.collectAsStateWithLifecycle()
LaunchedEffect(deepLink) {
    val item = deepLink ?: return@LaunchedEffect
    DeepLink.pending.value = null
    hs.quickSearch = false
    hs.menuOpen = false
    val show = item.type == MediaType.Show
    if (hs.page == Page.Tv) { playback.stop(); hs.fullscreen = false }
    hs.page = if (show) Page.Shows else Page.Movies
    (if (show) showsVm else moviesVm).open(item)
}


// The info bars' sleep timer: when it's up, everything playing stops (music has its own timer).
LaunchedEffect(io.github.petebutty.viewarr.playback.SleepTimer.endsAt) {
    val t = io.github.petebutty.viewarr.playback.SleepTimer.endsAt ?: return@LaunchedEffect
    delay((t - System.currentTimeMillis()).coerceAtLeast(0))
    io.github.petebutty.viewarr.playback.SleepTimer.clear()
    if (playing != null || catchup != null) hs.doButton(BtnScreen.Live, BtnAction.Stop)
    VodHost.close()
    hs.toast("Sleep timer: stopped")
}

// A button's page (Movies, Recordings…), the TV guide from a page, or leaving the app.
LaunchedEffect(hs.buttonRequest) {
    val r = hs.buttonRequest ?: return@LaunchedEffect
    hs.buttonRequest = null
    when (r) {
        BtnAction.Movies -> hs.openPage(Page.Movies)
        BtnAction.Shows -> hs.openPage(Page.Shows)
        BtnAction.Recordings -> hs.openPage(Page.Recordings)
        BtnAction.Music -> hs.openPage(Page.Music)
        BtnAction.Guide -> { hs.menuOpen = false; hs.openPage(Page.Tv) }
        BtnAction.Exit -> activity?.finish()
        else -> Unit
    }
}
}

/** The live info bar's rows (Down from its buttons) and the preview cards' rows, from HomeScreen. */
internal class InfoBarRows(val live: List<OsdRow>, val previewFor: (String) -> List<OsdRow>?, val posters: List<OsdRow>)

/** Split out of HomeScreen (its body was over Android's size limit for optimising a function). */
@Composable
private fun rememberInfoBarRows(hs: HomeState, postersOn: Boolean, preloadMusic: Boolean, sportsHub: SportsHub): InfoBarRows {
    // (Read here as HomeScreen reads them, so the rows rebuild when they change.)
    val vm = hs.vm; val playback = hs.playback; val session = hs.session; val remote = hs.remote; val dvr = hs.dvr
    val moviesVm = hs.moviesVm; val showsVm = hs.showsVm; val musicVm = hs.musicVm
    val sessionRef = rememberUpdatedState(session)
    val playing by playback.channel.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val favsNow by vm.favourites.collectAsStateWithLifecycle()
    val dvrRecordings by dvr.recordings.collectAsStateWithLifecycle()
    val musicOnNow by musicVm.enabled.collectAsStateWithLifecycle()
    val sportFollows by hs.sportsVm.follows.collectAsStateWithLifecycle()
    val movieMenuRows by moviesVm.menuRows.collectAsStateWithLifecycle()
    val showMenuRows by showsVm.menuRows.collectAsStateWithLifecycle()
val continueMovies by moviesVm.continueItems.collectAsStateWithLifecycle()
val continueShows by showsVm.continueItems.collectAsStateWithLifecycle()
val recentMovies by moviesVm.recent.collectAsStateWithLifecycle()
val recentShows by showsVm.recent.collectAsStateWithLifecycle()
val allChannels by dvr.channels.collectAsStateWithLifecycle()
// A film or show as a card under the live controls: OK opens its page (the channel carries on in the corner).
// Continue watching ([resume]): OK carries on (the episode you're up to, or the film where you left it); hold OK
// for Go to show / Go to season / Play from the start. Elsewhere OK opens its page.
fun titleCard(item: MediaItem, resume: Boolean = false): OsdPoster {
    val show = item.type == MediaType.Show
    val mvm = if (show) showsVm else moviesVm
    val page = if (show) Page.Shows else Page.Movies
    // (A card on a row: into the title itself, so the channel stops. The shortcuts keep it in the corner.)
    fun open() { hs.osdVisible = false; mvm.open(item); hs.openPage(page, keepLive = false) }
    fun play(how: (MediaItem) -> Unit) { hs.osdVisible = false; how(item); hs.openPage(page, keepLive = false) }
    val carryOn = resume && (item.progress != null || item.nextSeason != null)
    val options = if (!carryOn) emptyList() else listOfNotNull(
        CardOption(if (show) "Go to show" else "Go to film") { open() },
        if (show) CardOption("Go to season" + (item.nextSeason?.let { " $it" } ?: "")) {
            hs.osdVisible = false; showsVm.quickEpisodes(item); hs.openPage(page, keepLive = false)
        } else null,
        CardOption(if (show) "Play this episode from the start" else "Play from the start", plays = true) { play(mvm::quickFromStart) },
    )
    return OsdPoster(
        key = item.key, title = item.title, subtitle = item.subtitle,
        image = item.backdrop ?: item.poster, progress = item.progress,
        options = options, plays = carryOn,
    ) { if (carryOn) play(if (show) showsVm::quickNextUp else moviesVm::quickPlay) else open() }
}
// A highlighted shortcut's own content, previewed under it: a network's rows, its row, its page's first rows.
val movieHomeRows by moviesVm.homeRows.collectAsStateWithLifecycle()
val showHomeRows by showsVm.homeRows.collectAsStateWithLifecycle()
// Music and Recordings preview their own: your music's home rows, your recordings.
val musicHome by musicVm.home.collectAsStateWithLifecycle()
LaunchedEffect(hs.osdVisible, preloadMusic, musicOnNow) { if ((hs.osdVisible || preloadMusic) && musicOnNow && musicHome.rows.isEmpty()) musicVm.load() }
// Live now's rows: On now (the event, its channel, until when, how far through) and Starting soon.
fun liveEventRows(): List<OsdRow> {
    val ev = hs.liveEvents ?: return emptyList()
    val t = System.currentTimeMillis()
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE ", java.util.Locale.UK)
    fun at(ms: Long): String {
        val z = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        val today = java.time.LocalDate.now()
        return (if (z.toLocalDate() == today) "" else z.format(dayFmt)) + z.format(Dates.time)
    }
    // The event's own picture (the match's, or the guide's), else its sport's icon on its colours; the channel and
    // time under it. (The channel's logo said nothing: every Sky Sports+ feed has the same one.)
    fun card(e: GuideViewModel.LiveEvent, now: Boolean) = OsdPoster(
        key = "ev-${e.channel.id}-${e.program.startTime}", title = e.title,
        subtitle = (if (now) "Until " + at(e.program.endTime) else at(e.program.startTime)) + "  ·  " + e.channel.name,
        image = e.picture ?: if (e.sport == null) channelLogo(session?.serverUrl.orEmpty(), e.channel) else null,
        logo = e.picture == null || e.picture.contains("/badge/"),
        progress = if (now) ((t - e.program.startTime).toFloat() / (e.program.endTime - e.program.startTime).coerceAtLeast(1)).coerceIn(0f, 1f) else null,
        emoji = e.sport?.emoji, tint = e.sport?.colors,
    ) {
        val ch = e.channel
        hs.osdVisible = false
        playback.play(ch, session)
        vm.selectChannel(ch.id)
        hs.fullscreen = true
        hs.showBanner()
    }
    return listOf(
        OsdRow("On now", ev.onNow.map { card(it, true) }),
        OsdRow("Starting soon", ev.soon.map { card(it, false) }),
    ).filter { it.items.isNotEmpty() }
}
val computePreview: (String) -> List<OsdRow>? = { id ->
    if (id == "page:Live") liveEventRows() else if (id == "page:Sports") sportsHub.rows(sportFollows) else if (id.startsWith("sport:team:") || id.startsWith("sport:league:")) sportsHub.gamesRows(id, sportFollows) else if (!postersOn) null else if (id == "page:Music" || id.startsWith("music:")) musicHome.rows.map { r ->
        OsdRow(r.title, r.cards.take(20).map { card ->
            OsdPoster(key = "m:" + card.id, title = card.title, subtitle = card.sub, image = card.art?.small) {
                hs.osdVisible = false
                musicVm.open(card)
                hs.openPage(Page.Music, keepLive = false)
            }
        })
    }.filter { it.items.isNotEmpty() } else if (id == "page:Recordings") {
        val chans = dvr.channels.value
        fun card(rec: DvrRecording) = OsdPoster(
            key = "r:${rec.id}", title = rec.title,
            subtitle = listOfNotNull(rec.season?.let { "S$it" }, rec.episode?.let { "E$it" }, Dates.dayTime(rec.start)).joinToString(" · "),
            image = chans[rec.channelId]?.let { channelLogo(session?.serverUrl.orEmpty(), it) }, logo = true,
        ) { hs.osdVisible = false; hs.openPage(Page.Recordings, keepLive = false) }
        listOf(
            OsdRow("Recordings", dvrRecordings
                .filter { it.state == DvrState.Recorded || it.state == DvrState.Recording }
                .sortedByDescending { it.start }.take(20).map(::card)),
            OsdRow("Coming up", dvrRecordings
                .filter { it.state == DvrState.Scheduled }.sortedBy { it.start }.take(20).map(::card)),
        ).filter { it.items.isNotEmpty() }
    } else {
        val parts = id.split(":", limit = 3)
        val show = parts.getOrNull(1) == "Show"
        val menu = if (show) showMenuRows.rows else movieMenuRows.rows
        when (parts[0]) {
            "set" -> menu.filter { RowSets.matches(parts[2], it) } +
                (if (parts[2].startsWith("name:")) (if (show) movieMenuRows.rows else showMenuRows.rows)
                    .filter { RowSets.matches(parts[2], it) }
                    .map { it.copy(title = it.title + if (show) " (films)" else " (shows)") } else emptyList())
            "row" -> menu.filter { it.id == parts[2] }
            "home" -> (if (show) showHomeRows.rows else movieHomeRows.rows)
            else -> null
        }?.map { r -> OsdRow(r.title, r.items.take(20).map { titleCard(it, resume = r.group == "deck") }) }?.filter { it.items.isNotEmpty() }
    }
}
// Each shortcut's rows, built once and kept until something they show changes (they were built again on every
// redraw of the controls, and in the film player on every key press: hundreds of cards for Sports or Music).
val previewCache = remember(
    hs.liveEvents, sportFollows, sportsHub.games.toMap(), sportsHub.tv.toMap(), musicHome, dvrRecordings, movieMenuRows, showMenuRows,
    movieHomeRows, showHomeRows, postersOn, session?.serverUrl, now, allChannels,
) { HashMap<String, List<OsdRow>?>() }
val previewFor: (String) -> List<OsdRow>? = { id ->
    if (previewCache.containsKey(id)) previewCache[id] else computePreview(id).also { previewCache[id] = it }
}

// Built again only when what's in them changes, not on every redraw of the live TV screen. (What the cards' OK does
// reads the session as it is when pressed.)
val sessionNow by sessionRef
val ownStamp = hs.ownPrograms.entries.sumOf { it.value.first }
val posterRows: List<OsdRow> = remember(
    postersOn, continueMovies, continueShows, recentMovies, recentShows, remote.recentChannels, playing?.id, allChannels, programs, now, ownStamp, session?.serverUrl,
) { if (!postersOn) emptyList() else run {
    // Films and shows taken in turn, so neither crowds out the other.
    fun mix(a: List<MediaItem>, b: List<MediaItem>) =
        (0 until maxOf(a.size, b.size)).flatMap { i -> listOfNotNull(a.getOrNull(i), b.getOrNull(i)) }.distinctBy { it.key }.take(20)
    val chNow = System.currentTimeMillis()
    listOf(
        OsdRow("Continue watching", mix(continueMovies.orEmpty(), continueShows.orEmpty()).map { titleCard(it, resume = true) }),
        OsdRow("Recent channels", remote.recentChannels.filter { it != playing?.id }.mapNotNull { allChannels[it] }.map { ch ->
            OsdPoster(
                key = "ch-${ch.id}", title = ch.name,
                subtitle = GuideViewModel.programAt(hs.programsOf(ch), chNow)?.let { displayTitle(it) },
                image = channelLogo(session?.serverUrl.orEmpty(), ch), logo = true,
            ) {
                hs.osdVisible = false
                playback.play(ch, sessionNow)
                vm.selectChannel(ch.id)
                hs.showBanner()
            }
        }),
        OsdRow("Recently added", mix(recentMovies.orEmpty(), recentShows.orEmpty()).map { titleCard(it) }),
    ).filter { it.items.isNotEmpty() }
} }

// ---- The rows under the info bars (Settings → Appearance → Info bar & controls → Rows under the info bar): built
// here for live TV, and for the film players (shared through LiveShortcutsBridge.filmRows). Down from the buttons.
val localWatchEntries by moviesVm.localWatch.rowEntries.collectAsStateWithLifecycle()
val traktMovies by moviesVm.traktContinue.collectAsStateWithLifecycle()
val traktShows by showsVm.traktContinue.collectAsStateWithLifecycle()
val groupChannels by vm.channels.collectAsStateWithLifecycle()
// The film / episode playing: left out of the Continue watching rows (it was the first card of its own row).
val playingMovie by moviesVm.playing.collectAsStateWithLifecycle()
val playingShow by showsVm.playing.collectAsStateWithLifecycle()
val playingWatch = (playingShow ?: playingMovie)?.watch?.takeIf { VodHost.active }
val liveKinds = io.github.petebutty.viewarr.ui.common.InfoRows.active(io.github.petebutty.viewarr.ui.common.InfoRows.LIVE)
val filmKinds = io.github.petebutty.viewarr.ui.common.InfoRows.active(io.github.petebutty.viewarr.ui.common.InfoRows.FILM)
val wantedKinds = (liveKinds + filmKinds).toSet()
// What the chosen rows need, fetched once the controls are up (or a film is playing).
LaunchedEffect(hs.osdVisible, VodHost.active, wantedKinds) {
    if (!hs.osdVisible && !VodHost.active) return@LaunchedEffect
    if (wantedKinds.any { it == RowKind.CONTINUE || it == RowKind.CONTINUE_SERVERS }) { moviesVm.loadContinue(MediaType.Movie); showsVm.loadContinue(MediaType.Show) }
    if (wantedKinds.any { it == RowKind.CONTINUE || it == RowKind.CONTINUE_TRAKT }) showsVm.loadTraktContinue(MediaType.Show) // (shows only: see traktContinue)
    if (RowKind.RECENTLY_ADDED in wantedKinds) { moviesVm.loadRecent(MediaType.Movie); showsVm.loadRecent(MediaType.Show) }
    if (RowKind.WATCHLIST in wantedKinds) { moviesVm.load(MediaType.Movie); showsVm.load(MediaType.Show) }
}
// A channel as a card (logo, what's on now); OK watches it ([plays]: from a film, which then makes way).
fun channelCard(ch: ChannelListItem, prefix: String, chNow: Long, plays: Boolean = false) = OsdPoster(
    key = "$prefix-${ch.id}", title = ch.name,
    subtitle = GuideViewModel.programAt(hs.programsOf(ch), chNow)?.let { displayTitle(it) },
    image = channelLogo(session?.serverUrl.orEmpty(), ch), logo = true,
    progress = GuideViewModel.programAt(hs.programsOf(ch), chNow)?.let { p -> ((chNow - p.startTime).toFloat() / (p.endTime - p.startTime).coerceAtLeast(1)).coerceIn(0f, 1f) },
) {
    hs.osdVisible = false
    playback.play(ch, sessionNow)
    vm.selectChannel(ch.id)
    if (plays) hs.fullscreen = true
    hs.showBanner()
}
fun mixTitles(a: List<MediaItem>, b: List<MediaItem>) =
    (0 until maxOf(a.size, b.size)).flatMap { i -> listOfNotNull(a.getOrNull(i), b.getOrNull(i)) }
        .distinctBy { it.tmdbId?.let { id -> "${it.type}:$id" } ?: it.key }.take(20)
/** One row of [kind], for [player] (null: nothing in it, or the player builds it itself: next episodes). */
fun infoRow(kind: io.github.petebutty.viewarr.ui.common.InfoRows.Kind, player: String): OsdRow? {
    val film = player == io.github.petebutty.viewarr.ui.common.InfoRows.FILM
    val chNow = System.currentTimeMillis()
    fun notPlaying(l: List<MediaItem>) = playingWatch?.let { w ->
        l.filterNot { it.type == w.type && ((w.tmdbId != null && it.tmdbId == w.tmdbId) || (w.imdbId != null && it.imdbId == w.imdbId)) }
    } ?: l
    val iptv = notPlaying(mixTitles(moviesVm.localWatch.continueItems(MediaType.Movie, localWatchEntries), moviesVm.localWatch.continueItems(MediaType.Show, localWatchEntries)))
    val servers = notPlaying(mixTitles(continueMovies.orEmpty(), continueShows.orEmpty()))
    val traktAll = notPlaying(mixTitles(traktMovies.orEmpty(), traktShows.orEmpty()))
    val row = when (kind) {
        RowKind.NEXT -> return null
        // The film / show playing's page: its recommendations.
        RowKind.SIMILAR -> OsdRow("More like this", (if (showsVm.playing.value != null) showsVm else moviesVm).similarToPlaying().take(20).map { titleCard(it) })
        RowKind.CONTINUE -> OsdRow("Continue watching", (servers + iptv + traktAll).distinctBy { it.tmdbId?.let { id -> "${it.type}:$id" } ?: it.key }.take(25).map { titleCard(it, resume = true) })
        RowKind.CONTINUE_IPTV -> OsdRow("Continue watching on IPTV", iptv.map { titleCard(it, resume = true) })
        RowKind.CONTINUE_TRAKT -> OsdRow("Continue watching on Trakt", traktAll.map { titleCard(it, resume = true) })
        RowKind.CONTINUE_SERVERS -> OsdRow("Continue watching on your servers", servers.map { titleCard(it, resume = true) })
        RowKind.RECENT_CHANNELS -> OsdRow("Recent channels", remote.recentChannels.filter { film || it != playing?.id }.mapNotNull { allChannels[it] }.take(15).map { channelCard(it, "rc", chNow, film) })
        RowKind.FAVOURITES -> OsdRow("Favourite channels", allChannels.values.filter { it.uuid in favsNow }.sortedBy { it.number ?: Double.MAX_VALUE }.take(40).map { channelCard(it, "fav", chNow, film) })
        RowKind.ON_NOW -> OsdRow("On now" + (playing?.groupName?.let { " in $it" } ?: ""), groupChannels.filter { it.id != playing?.id }.take(40).map { channelCard(it, "on", chNow) })
        RowKind.COMING_UP -> {
            val ch = playing
            OsdRow("Coming up on ${ch?.name ?: "this channel"}", ch?.let { c ->
                hs.programsOf(c).filter { it.startTime > chNow }.sortedBy { it.startTime }.take(12).map { p ->
                    OsdPoster(key = "up-${c.id}-${p.startTime}", title = displayTitle(p), subtitle = Dates.time(p.startTime) + "–" + Dates.time(p.endTime),
                        image = channelLogo(session?.serverUrl.orEmpty(), c), logo = true) {
                        hs.osdVisible = false; LiveShortcutsBridge.openGuide()
                    }
                }
            }.orEmpty())
        }
        RowKind.RECORDINGS -> {
            val chans = dvr.channels.value
            OsdRow("Recordings", dvrRecordings.filter { it.state == DvrState.Recorded || it.state == DvrState.Recording }.sortedByDescending { it.start }.take(20).map { rec ->
                OsdPoster(key = "r:${rec.id}", title = rec.title,
                    subtitle = listOfNotNull(rec.season?.let { "S$it" }, rec.episode?.let { "E$it" }, Dates.dayTime(rec.start)).joinToString(" · "),
                    image = chans[rec.channelId]?.let { channelLogo(session?.serverUrl.orEmpty(), it) }, logo = true,
                ) { hs.osdVisible = false; hs.openPage(Page.Recordings, keepLive = false) }
            })
        }
        RowKind.RECENTLY_ADDED -> OsdRow("Recently added", mixTitles(recentMovies.orEmpty(), recentShows.orEmpty()).map { titleCard(it) })
        RowKind.WATCHLIST -> OsdRow("Your watchlist", mixTitles(
            movieMenuRows.rows.firstOrNull { it.id.startsWith("trakt-watchlist") }?.items.orEmpty(),
            showMenuRows.rows.firstOrNull { it.id.startsWith("trakt-watchlist") }?.items.orEmpty(),
        ).map { titleCard(it) })
    }
    return row.takeIf { it.items.isNotEmpty() }
}
val infoRowsStamp = listOf(liveKinds, filmKinds, remote.recentChannels, playing?.id, allChannels, programs, ownStamp, favsNow, groupChannels,
    continueMovies, continueShows, recentMovies, recentShows, traktMovies, traktShows, localWatchEntries, dvrRecordings, movieMenuRows, showMenuRows, now, session?.serverUrl, VodHost.active, playingWatch)
// Built only while they can be seen (the live controls up, or a film playing): every channel card looks up what's on,
// which was being done every 30 s for rows nobody could see.
val liveRowsWanted = hs.osdVisible
val filmRowsWanted = VodHost.active
val liveInfoRows: List<OsdRow> = remember(infoRowsStamp, liveRowsWanted) {
    if (!liveRowsWanted) emptyList() else liveKinds.mapNotNull { infoRow(it, io.github.petebutty.viewarr.ui.common.InfoRows.LIVE) }
}
val filmInfoRows: List<Pair<String, OsdRow>> = remember(infoRowsStamp, filmRowsWanted) {
    if (!filmRowsWanted) emptyList() else filmKinds.mapNotNull { k -> infoRow(k, io.github.petebutty.viewarr.ui.common.InfoRows.FILM)?.let { k.id to it } }
}
SideEffect { LiveShortcutsBridge.filmRows = filmInfoRows }
    return InfoBarRows(liveInfoRows, previewFor, posterRows)
}

/** A film or episode on full screen (or with the menu open over it): the menu mustn't change the page underneath. */
private fun filmFullScreen() = VodHost.active && !VodHost.mini
