package io.github.petebutty.viewarr.ui.player

import io.github.petebutty.viewarr.ui.common.glass
import androidx.compose.foundation.horizontalScroll
import io.github.petebutty.viewarr.ui.common.popupScroll
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import io.github.petebutty.viewarr.ui.common.ignoreHeldOk
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import io.github.petebutty.viewarr.ui.common.trapFocus
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.petebutty.viewarr.data.local.ChannelListItem
import io.github.petebutty.viewarr.data.local.GuideProgram
import io.github.petebutty.viewarr.playback.AspectMode
import io.github.petebutty.viewarr.playback.PlaybackViewModel
import io.github.petebutty.viewarr.ui.common.MenuItem
import io.github.petebutty.viewarr.ui.guide.displayTitle
import io.github.petebutty.viewarr.ui.guide.formatNumber
import io.github.petebutty.viewarr.ui.theme.AppColors
import io.github.petebutty.viewarr.ui.theme.UiOpts
import io.github.petebutty.viewarr.ui.common.BarLayout
import io.github.petebutty.viewarr.ui.common.AppIcons
import io.github.petebutty.viewarr.playback.SleepTimer
import io.github.petebutty.viewarr.playback.AvSync
import coil.compose.AsyncImage
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.petebutty.viewarr.ui.common.FocusCard
import io.github.petebutty.viewarr.ui.common.FocusCardStatic
import io.github.petebutty.viewarr.ui.common.OptionsPopup
import io.github.petebutty.viewarr.ui.common.PopupOption
import io.github.petebutty.viewarr.ui.common.PopupPanel
import io.github.petebutty.viewarr.ui.common.PopupTitle
import io.github.petebutty.viewarr.ui.common.ReorderAxis
import io.github.petebutty.viewarr.ui.common.RoundIconButton
import io.github.petebutty.viewarr.ui.common.moveOption
import io.github.petebutty.viewarr.ui.common.movingHint
import io.github.petebutty.viewarr.ui.common.rememberCarry
import io.github.petebutty.viewarr.ui.common.reorderable
import io.github.petebutty.viewarr.ui.theme.Tokens
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.petebutty.viewarr.data.settings.LocalRemote
import io.github.petebutty.viewarr.ui.settings.AppearanceViewModel
import io.github.petebutty.viewarr.ui.settings.UiOptsViewModel

private enum class OsdPopup { Audio, Subtitles, Aspect, Source, Info, Sync }

/** A button the home screen adds to live TV's info bar (Movies, Favourite, Last channel…; see BarLayout.extras). */
class BarExtra(val id: String, val icon: ImageVector, val label: String, val onClick: () -> Unit)

private val hhmm get() = io.github.petebutty.viewarr.ui.theme.Dates.time
private fun Long.clockTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(hhmm)

/** classic-style player menu: compact info, catch-up seek bar, and a row of small option pills. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerOsd(
    vm: PlaybackViewModel,
    channel: ChannelListItem,
    current: GuideProgram?,
    now: Long,
    serverUrl: String,
    onGuide: () -> Unit,
    onClose: () -> Unit,
    onActivity: () -> Unit,
    onRecord: (() -> Unit)? = null,
    /** Several channels at once. */
    onMultiView: (() -> Unit)? = null,
    /** Stop live TV (the guide, with nothing playing). */
    onStop: (() -> Unit)? = null,
    /** The settings, over live TV (Back returns here). */
    onSettings: (() -> Unit)? = null,
    /** Search (films, shows, channels, programmes) over live TV. */
    onSearch: (() -> Unit)? = null,
    /** The home screen's extra buttons (shown once added to the bar). */
    extraButtons: List<BarExtra> = emptyList(),
    /** Down from the buttons (no shortcuts row): these rows, e.g. Recent channels, as the film player's next episodes. */
    downRows: List<OsdRow> = emptyList(),
    /** Channel list, guide and wallpaper transparency, adjusted over the picture. */
    onTransparency: (() -> Unit)? = null,
    /** Down from the buttons: your shortcuts (libraries, rows, Music…; Settings → Remote buttons → Live TV shortcuts). */
    shortcuts: List<Pair<LiveShortcut, () -> Unit>> = emptyList(),
    /** Down from the shortcuts: rows of posters (Continue watching, Recent channels…), one row on screen at a time. */
    posterRows: List<OsdRow> = emptyList(),
    /** The rows a shortcut previews underneath while it's highlighted (its network's, its row, its page's); null: [posterRows]. */
    previewFor: (String) -> List<OsdRow>? = { null },
    /** Hold OK on a shortcut: take it off the row. */
    onHideShortcut: (LiveShortcut) -> Unit = {},
    /** Move a shortcut one place along the row (-1 left, 1 right), from the hold-OK menu's Move. */
    onMoveShortcut: (LiveShortcut, Int) -> Unit = { _, _ -> },
    /** Refresh just this shortcut's rows (from the hold-OK menu). */
    onRefreshShortcut: ((LiveShortcut) -> Unit)? = null,
    /** Opened with the Menu button: start on the audio choices. */
    openAudio: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val catchup by vm.catchup.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val audio by vm.audioTracks.collectAsStateWithLifecycle()
    val subtitles by vm.textTracks.collectAsStateWithLifecycle()
    val aspect by vm.aspect.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val sourceNote by vm.sourceNote.collectAsStateWithLifecycle()
    var popup by remember { mutableStateOf<OsdPopup?>(if (openAudio) OsdPopup.Audio else null) }
    // Info bar buttons editor (hold OK on the bar; also Settings → Appearance → Info bar buttons).
    var barEditor by remember { mutableStateOf(false) }
    var barItems by remember { mutableStateOf(emptyList<BarItem>()) }
    // The TV settings panel (frame rate matching, picture size…), from its button.
    var tvOpen by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    // The button that opened a pop-up (Audio, Subtitles, Aspect, Source, Sync): the highlight goes back to it when the
    // pop-up closes (it was lost).
    val btnFocus = remember { HashMap<String, FocusRequester>() }
    var popupFrom by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(popup == null) {
        if (popup != null) return@LaunchedEffect
        val from = popupFrom ?: return@LaunchedEffect
        awaitFrame(); awaitFrame()
        runCatching { btnFocus[from]?.requestFocus() }
        popupFrom = null
    }
    // The progress bar: Left on it while live rewinds (catch-up channels); it stays focused as it turns into the
    // catch-up seek bar, so you can keep scrubbing.
    val barFocus = remember { FocusRequester() }
    var keepBar by remember { mutableStateOf(false) }
    LaunchedEffect(catchup != null) {
        if (!keepBar) return@LaunchedEffect
        awaitFrame(); awaitFrame()
        runCatching { barFocus.requestFocus() }
        keepBar = false
    }
    val seekStep = LocalRemote.current.seek.ms

    BackHandler { if (popup != null) popup = null else onClose() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }

    val barAppearance: AppearanceViewModel = hiltViewModel()
    // The info bar itself, with the buttons inside it: the same bar as when channels change, and as the film player's.
    InfoBarPanel(
        modifier
            .onPreviewKeyEvent { onActivity(); false }
            // Opened by OK: the release of that same press (Fire TV remotes send it a beat later) mustn't press
            // "TV guide", the first button, and throw you out to the guide.
            .ignoreHeldOk(),
    ) {
        if (barEditor) BarButtonsEditor(barItems, barAppearance::setTitlePart, { barAppearance.setBarOrder("all", it) }, onActivity,
            onClose = { barEditor = false; runCatching { firstFocus.requestFocus() } })
        if (popup == OsdPopup.Info) StreamInfoPopup(vm)
        if (tvOpen) TvSettingsPanel(onClose = { tvOpen = false; runCatching { firstFocus.requestFocus() } })
        popup?.takeIf { it != OsdPopup.Info }?.let { p ->
            OptionPopup(
                wide = p == OsdPopup.Source,
                options = when (p) {
                    OsdPopup.Audio -> audio.map { it.label to it.selected }
                    OsdPopup.Subtitles -> subtitles.map { it.label to it.selected }
                    OsdPopup.Aspect -> AspectMode.entries.map { it.label to (it == aspect) }
                    OsdPopup.Sync -> AvSync.steps.map { AvSync.label(it) to (it == AvSync.ms) }
                    // Each source with its provider, Dispatcharr's last check of its quality, and why it may not play now.
                    OsdPopup.Source -> sources.orEmpty().map {
                        (if (it.current) "✓  " else "     ") + listOfNotNull(it.name, it.provider.ifEmpty { null }, it.quality).joinToString("  ·  ") +
                            (it.warning?.let { w -> "  ·  ⚠ $w" } ?: "") to it.current
                    }
                    OsdPopup.Info -> emptyList()
                },
                title = when (p) { OsdPopup.Source -> "Source" + (sourceNote?.let { "  ·  $it" } ?: ""); OsdPopup.Sync -> "Audio sync"; else -> p.name },
                emptyText = when {
                    p == OsdPopup.Subtitles -> "No subtitles on this channel"
                    p == OsdPopup.Source -> sourceNote ?: "Loading sources…"
                    else -> "Nothing to choose"
                },
                onPick = { i ->
                    when (p) {
                        OsdPopup.Audio -> audio.getOrNull(i)?.let { vm.selectTrack(it, C.TRACK_TYPE_AUDIO) }
                        OsdPopup.Subtitles -> subtitles.getOrNull(i)?.let { vm.selectTrack(it, C.TRACK_TYPE_TEXT) }
                        OsdPopup.Aspect -> vm.setAspect(AspectMode.entries[i])
                        OsdPopup.Sync -> AvSync.steps.getOrNull(i)?.let { AvSync.set(it) }
                        OsdPopup.Source -> sources?.getOrNull(i)?.let { if (!it.current) vm.switchSource(it) }
                        OsdPopup.Info -> Unit
                    }
                    popup = null
                },
            )
        }

        val showNumber = AppColors.palette.opt(UiOpts.BANNER_NUMBER) == 0
        InfoBarTop(
            number = if (showNumber) channel.number?.let(::formatNumber).orEmpty() else null,
            logo = {
                io.github.petebutty.viewarr.ui.guide.channelLogo(serverUrl, channel)?.let {
                    AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            },
            smallLine = {
                // (Shrinks with "…" in the narrow info bar styles, so the badges after it aren't cut off.)
                Text(channel.name, fontSize = 14.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                val behind = vm.behindLiveMs()?.takeIf { it > 60_000 }?.let { "${it / 60_000} MIN BEHIND LIVE" }
                if (catchup != null) Badge(listOfNotNull(if (behind != null) null else "CATCH-UP", behind, if (paused) "PAUSED" else null).joinToString(" · "))
                else if (paused) Badge("PAUSED")
                // (The saved picture / sound, with the frame rate as it is now: it can become certain after the info bar was up.)
                QualityBadges(LiveQuality.now?.let { q -> LiveQuality.fps?.let { q.copy(fps = it) } ?: q })
                SourceBadge(vm.streamSource.collectAsState().value)
            },
            title = current?.let { if (AppColors.palette.opt(UiOpts.BANNER_EPISODE) == 0) displayTitle(it) else it.title } ?: "No guide information",
            progress = {
                if (catchup != null) {
                    SeekBar(
                        position = position.first,
                        // The programme's own length: a programme still on air is only as long as what's been broadcast in
                        // the provider's archive, which made the bar look nearly finished.
                        duration = catchup!!.endTime - catchup!!.startTime,
                        // How far through it live is (a programme still on air).
                        live = (now - catchup!!.startTime).takeIf { now < catchup!!.endTime },
                        paused = paused,
                        onSeek = vm::seekBy,
                        onToggle = vm::togglePause,
                        modifier = Modifier.focusRequester(firstFocus).focusRequester(barFocus),
                    )
                } else if (current != null) {
                    if (channel.catchupDays > 0) {
                        var barOn by remember { mutableStateOf(false) }
                        // Left / Right move a marker to where the rewind will jump to; it jumps on OK, or once you stop.
                        var rewindTo by remember { mutableStateOf<Long?>(null) }
                        val earliest = maxOf(current.startTime, now - channel.catchupDays * 86_400_000L)
                        fun jump() { rewindTo?.let { keepBar = true; vm.rewindLiveTo(it) }; rewindTo = null }
                        LaunchedEffect(rewindTo) { if (rewindTo != null) { delay(2_500); jump() } }
                        Column(
                            Modifier.focusRequester(barFocus).onFocusChanged { barOn = it.isFocused; if (!it.isFocused) rewindTo = null }.focusable().onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val step = if (e.nativeKeyEvent.repeatCount >= 4) seekStep * 4 else seekStep
                                when (e.key) {
                                    // The first step back is at least a minute (the archive runs just behind live).
                                    Key.DirectionLeft -> rewindTo = (minOf(rewindTo ?: now, now - 60_000 + step) - step).coerceAtLeast(earliest)
                                    Key.DirectionRight -> rewindTo = rewindTo?.let { (it + step).takeIf { t -> t < now - 60_000 } }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (rewindTo != null) jump() else { keepBar = true; vm.togglePause() }
                                    else -> return@onPreviewKeyEvent false
                                }
                                true
                            },
                        ) {
                            val marker = rewindTo?.let { ((it - current.startTime).toFloat() / (current.endTime - current.startTime)).coerceIn(0f, 1f) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${current.startTime.clockTime()} – ${current.endTime.clockTime()}", fontSize = 14.sp, color = Color.White)
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.weight(1f)) { LiveProgress(current, now, highlighted = barOn, marker = marker) }
                            }
                            val hint = rewindTo?.let { t -> "Back to ${t.clockTime()}  ·  ${((now - t) / 60_000).coerceAtLeast(1)} min ago  ·  OK: jump now" }
                                ?: "◀ rewind  ·  OK: pause"
                            if (barOn) Text(hint, fontSize = 11.sp, color = if (rewindTo != null) Color.White else AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${current.startTime.clockTime()} – ${current.endTime.clockTime()}", fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) { LiveProgress(current, now) }
                    }
                }
            },
        )

        // The same icon buttons as the film / show player's controls (the focused one's name underneath).
        var showShortcuts by remember { mutableStateOf(false) }
        // In the rows under the buttons (Recent channels): which one; -1 on the buttons.
        var downRow by remember { mutableIntStateOf(-1) }
        BackHandler(enabled = downRow >= 0) { downRow = -1; runCatching { firstFocus.requestFocus() } }
        // (The Style button's setting.)
        val barOpts: UiOptsViewModel = hiltViewModel()
        val shortcutFocus = remember { FocusRequester() }
        // Where the buttons start: Go live in catch-up (or paused), else Stop (the TV guide if Stop is hidden). Both
        // when the controls open and when Down comes into the buttons from the seek bar.
        val goLiveFocus = remember { FocusRequester() }
        var stopShown by remember { mutableStateOf(false) }
        Row(
            // Scrolls sideways when there are more buttons than fit (extras added).
            Modifier.padding(top = 6.dp)
                .focusProperties {
                    enter = {
                        when {
                            catchup != null || paused -> goLiveFocus
                            stopShown -> btnFocus.getOrPut("stop") { FocusRequester() }
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()).onPreviewKeyEvent { e ->
                // Down from the buttons: the shortcuts row.
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && shortcuts.isNotEmpty() && popup == null) { showShortcuts = true; true }
                // No shortcuts row: Down opens the rows under the buttons (Recent channels).
                else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && shortcuts.isEmpty() && downRows.isNotEmpty() && popup == null && downRow < 0) { onActivity(); downRow = 0; true }
                else false
            },
            verticalAlignment = Alignment.Top,
        ) {
            if (catchup != null || paused) {
                if (catchup != null) BarButton(AppIcons.Replay, "Start over") { vm.restartProgramme() }
                BarButton(AppIcons.SkipNext, "Go live",
                    // (Paused live TV has no seek bar: the controls open here.)
                    (if (catchup == null) Modifier.focusRequester(firstFocus) else Modifier).focusRequester(goLiveFocus), groupEnd = true) { vm.goLive() }
            }
            // The buttons as arranged (hold OK on one: the Info bar buttons editor; see BarLayout).
            class LiveBtn(val id: String, val icon: ImageVector, val label: String, val onClick: () -> Unit)
            val all = buildList {
                add(LiveBtn("guide", AppIcons.Guide, "TV guide", onGuide))
                onStop?.let { add(LiveBtn("stop", AppIcons.Stop, "Stop", it)) }
                add(LiveBtn("audio", AppIcons.Audio, "Audio") { popup = OsdPopup.Audio })
                add(LiveBtn("subtitles", AppIcons.Subtitles, "Subtitles") { popup = OsdPopup.Subtitles })
                add(LiveBtn("aspect", AppIcons.Aspect, "Aspect: ${aspect.label}") { popup = OsdPopup.Aspect })
                if (vm.dispatcharrLive) add(LiveBtn("source", AppIcons.Layers, "Source") { vm.loadSources(); popup = OsdPopup.Source })
                add(LiveBtn("info", AppIcons.Stats, if (popup == OsdPopup.Info) "Hide stream info" else "Stream info") { popup = if (popup == OsdPopup.Info) null else OsdPopup.Info })
                onRecord?.let { add(LiveBtn("record", AppIcons.Record, "Record", it)) }
                onMultiView?.let { add(LiveBtn("multiview", AppIcons.Grid, "Multi-view", it)) }
                extraButtons.forEach { add(LiveBtn(it.id, it.icon, it.label, it.onClick)) }
                add(LiveBtn("sync", AppIcons.Equalizer, "Audio sync: " + AvSync.label()) { popup = OsdPopup.Sync })
                add(LiveBtn("barstyle", AppIcons.Style, BarLayout.styleLabel()) {
                    BarLayout.nextStyle(barOpts::set)
                })
                add(LiveBtn("tvsettings", AppIcons.Tune, "TV settings") { tvOpen = true })
                add(LiveBtn("sleep", AppIcons.Bedtime, SleepTimer.label(now)) { SleepTimer.cycle(current?.endTime) })
                onSearch?.let { add(LiveBtn("search", AppIcons.Search, "Search", it)) }
                onTransparency?.let { add(LiveBtn("transparency", AppIcons.Opacity, "Transparency", it)) }
                onSettings?.let { add(LiveBtn("settings", AppIcons.Settings, "Settings", it)) }
            }
            val arranged = BarLayout.arrange(all, { it.id }, false, BarLayout.Bar.Live)
            val itemsNow = all.map { BarItem(it.id, it.icon, it.label) }
            SideEffect { barItems = itemsNow }
            val startId = if (arranged.any { it.id == "stop" }) "stop" else "guide"
            // The divider after the locked buttons (BarLayout.arrange puts them first).
            val dividerAfter = BarLayout.lastLocked(arranged.map { it.id }, BarLayout.Bar.Live)
            SideEffect { stopShown = startId == "stop" }
            arranged.forEach { b ->
                androidx.compose.runtime.key(b.id) {
                    BarButton(
                        b.icon,
                        b.label,
                        (if (b.id == startId && catchup == null && !paused) Modifier.focusRequester(firstFocus) else Modifier)
                            .focusRequester(btnFocus.getOrPut(b.id) { FocusRequester() })
                            .onFocusChanged { if (it.isFocused && popup == null) popupFrom = b.id },
                        groupEnd = b.id == dividerAfter && b != arranged.last(),
                        // Hold OK: the Info bar buttons editor (a list with names; it replaced editing on the bar).
                        onLongClick = { onActivity(); barEditor = true },
                    ) {
                        onActivity()
                        b.onClick()
                    }
                }
            }
            sourceNote?.takeIf { popup == null }?.let { Text(it, fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(start = 8.dp, top = 14.dp)) }
        }
        // -1: on the shortcuts (the first row peeks underneath); otherwise the poster row being browsed.
        var posterRow by remember { mutableIntStateOf(-1) }
        // The highlighted shortcut previews its own content underneath; going down keeps what was showing.
        // (Taken a moment after the highlight rests: each preview loads a new set of pictures, and doing that for
        // every shortcut passed on the way along made moving sluggish.)
        var focusedRaw by remember { mutableIntStateOf(0) }
        var focusedSc by remember { mutableIntStateOf(0) }
        LaunchedEffect(focusedRaw) { delay(250); focusedSc = focusedRaw }
        // A shortcut's rows: the ones chosen for it (hold OK → Choose rows), in that order, else all it has.
        fun rowsFor(i: Int): List<OsdRow> {
            val id = shortcuts.getOrNull(i)?.first?.id
            val all = id?.let(previewFor)?.takeIf { it.isNotEmpty() } ?: posterRows
            val picked = id?.let { LiveShortcutsBridge.chosen[it] } ?: return all
            return picked.mapNotNull { t -> all.firstOrNull { it.title == t } }
        }
        val allRows = rowsFor(focusedSc)
        // Settings → Navigation → Rows under the shortcuts: Down reaches this many (the rest are on the shortcut's own
        // page). Not for one whose rows were chosen (all of those), nor Sports (a row per team / league you follow).
        val maxRows = when (AppColors.palette.opt(UiOpts.OSD_ROWS)) { 1 -> 2; 2 -> 5; 3 -> Int.MAX_VALUE; else -> 3 }
        fun capFor(i: Int): Int {
            val id = shortcuts.getOrNull(i)?.first?.id
            return if (id != null && LiveShortcutsBridge.chosen.containsKey(id)) Int.MAX_VALUE else if (id == "page:Sports") maxOf(maxRows, 12) else maxRows
        }
        val shownRows = allRows.take(capFor(focusedSc))
        val moreRows = allRows.size > shownRows.size
        var browsing by remember { mutableStateOf<List<OsdRow>?>(null) }
        // Hold OK on a shortcut takes it off; focus then goes to the one now in its place.
        val scFocus = remember { HashMap<String, FocusRequester>() }
        var hiddenAt by remember { mutableIntStateOf(-1) }
        // Hold OK asks first: "Hide Netflix from your shortcuts?" (Cancel highlighted, so a stray press does nothing).
        var askHide by remember { mutableStateOf<Pair<Int, LiveShortcut>?>(null) }
        var chooseFor by remember { mutableStateOf<LiveShortcut?>(null) }
        // Move (from that menu): the shortcut picked up; ◀ ▶ slide it along, OK or Back put it down (Modifier.reorderable).
        val scCarry = rememberCarry()
        // The row swapped (into Sports, or back out): focus on its first one, no rows from the old row left open.
        val firstId = shortcuts.firstOrNull()?.first?.id
        var lastFirst by remember { mutableStateOf(firstId) }
        LaunchedEffect(firstId) {
            if (firstId == lastFirst) return@LaunchedEffect
            lastFirst = firstId
            if (!showShortcuts || scCarry.active) return@LaunchedEffect
            browsing = null; posterRow = -1
            awaitFrame()
            runCatching { shortcutFocus.requestFocus() }
        }
        LaunchedEffect(shortcuts.map { it.first.id }) {
            val id = scCarry.id ?: return@LaunchedEffect
            // (The one being moved gone from the row: nothing carried any more.)
            if (shortcuts.none { it.first.id == id }) { scCarry.drop(); return@LaunchedEffect }
            awaitFrame()
            runCatching { scFocus[id]?.requestFocus() }
        }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        askHide?.let { (at, sc) ->
            ShortcutOptions(
                sc, onActivity = onActivity,
                onMove = {
                    askHide = null; scCarry.pickUp(sc.id)
                    scope.launch { awaitFrame(); runCatching { scFocus[sc.id]?.requestFocus() } }
                },
                onRefresh = onRefreshShortcut?.let { f -> {
                    askHide = null; f(sc)
                    scope.launch { awaitFrame(); runCatching { scFocus[sc.id]?.requestFocus() } }
                } },
                onHide = { askHide = null; hiddenAt = at; onHideShortcut(sc) },
                onCancel = {
                    askHide = null
                    scope.launch { awaitFrame(); runCatching { scFocus[sc.id]?.requestFocus() } }
                },
                onChooseRows = { askHide = null; chooseFor = sc },
            )
        }
        // Hold OK on a Continue watching card: Go to show / Go to season / Play from the start. Closing it (Cancel,
        // Back) leaves the highlight where it was.
        CardMenuHost(onActivity = onActivity)
        // (Its pop-ups have the remote in their own window: the controls stay up behind them.)
        if (askHide != null || chooseFor != null || CardMenu.open != null) HoldBarOpen()
        chooseFor?.let { sc ->
            ChooseRowsDialog(
                sc, onActivity = onActivity,
                onSave = { LiveShortcutsBridge.chooseRows(sc.id, it) },
                onClose = {
                    chooseFor = null
                    scope.launch { awaitFrame(); runCatching { scFocus[sc.id]?.requestFocus() } }
                },
            )
        }
        LaunchedEffect(shortcuts.size) {
            val at = hiddenAt.takeIf { it >= 0 } ?: return@LaunchedEffect
            hiddenAt = -1
            awaitFrame()
            shortcuts.getOrNull(at.coerceAtMost(shortcuts.lastIndex))?.first?.id?.let { id -> runCatching { scFocus[id]?.requestFocus() } }
        }
        if (showShortcuts && shortcuts.isNotEmpty()) {
            LaunchedEffect(Unit) { awaitFrame(); runCatching { shortcutFocus.requestFocus() } }
            // (Room at the ends and above / below for the highlighted one to grow into, not cut off; shifted back so
            // the row lines up as before.)
            LazyRow(
                Modifier.padding(top = 2.dp).offset(x = (-14).dp).onPreviewKeyEvent { e ->
                    // Moving one: the shortcut itself has the keys (Modifier.reorderable), nothing here.
                    if (scCarry.active) { onActivity(); return@onPreviewKeyEvent false }
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        // Up: back to the buttons, the shortcuts folded away again.
                        Key.DirectionUp -> { showShortcuts = false; posterRow = -1; runCatching { firstFocus.requestFocus() }; true }
                        // Down: into the posters.
                        // (The highlighted one's rows now, not the preview's, which follows the highlight a moment late.)
                        Key.DirectionDown -> {
                            focusedSc = focusedRaw
                            val rows = rowsFor(focusedRaw).take(capFor(focusedRaw))
                            if (rows.isNotEmpty()) { browsing = rows; posterRow = 0 }
                            true
                        }
                        else -> false
                    }
                },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                // Round icons like the buttons above, the highlighted one's name underneath.
                items(shortcuts.size, key = { shortcuts[it].first.id }) { i ->
                    val (sc, go) = shortcuts[i]
                    val fr = scFocus.getOrPut(sc.id) { FocusRequester() }
                    val carried = scCarry.isCarried(sc.id)
                    ShortcutButton(sc, if (carried) "◀  ${sc.label}  ▶" else sc.label,
                        (if (i == 0) Modifier.focusRequester(shortcutFocus) else Modifier).focusRequester(fr)
                            .reorderable(carried = carried, onMove = { dir -> onMoveShortcut(sc, dir) }, onDrop = scCarry::drop, axis = ReorderAxis.Horizontal),
                        onFocus = { focusedRaw = i },
                        onLongClick = if (scCarry.active || sc.id.startsWith("action:") || sc.id == "sport:back" || (sc.id == "page:Live" && shortcuts.firstOrNull()?.first?.id == "sport:back")) null else ({ askHide = i to sc })) { go() }
                }
            }
            val rowsNow = browsing ?: shownRows
            // Settings → Live TV shortcuts → Rows under the shortcuts appear: only once you go down into them
            // (the default, cleaner), or a dimmed preview under the shortcuts all along.
            val peek = AppColors.palette.opt(UiOpts.OSD_PEEK) == 1
            if (rowsNow.isNotEmpty() && (peek || posterRow >= 0)) PosterRows(rowsNow, posterRow, more = moreRows, onRow = { r ->
                posterRow = r
                // Up from the first row: back to the shortcut it came from.
                if (r < 0) { browsing = null; runCatching { (shortcuts.getOrNull(focusedRaw)?.first?.id?.let { scFocus[it] } ?: shortcutFocus).requestFocus() } }
            })
        }
        // Recent channels (and any other rows) under the buttons: Up from the first goes back to the buttons.
        if (shortcuts.isEmpty() && downRows.isNotEmpty() && downRow >= 0) PosterRows(downRows, downRow, onRow = { r ->
            onActivity()
            if (r < 0) { downRow = -1; runCatching { firstFocus.requestFocus() } } else downRow = r.coerceAtMost(downRows.lastIndex)
        })
        if (shortcuts.isEmpty() && downRows.isNotEmpty() && AppColors.palette.opt(UiOpts.BUTTON_HINTS) != 2) Text(
            if (downRow >= 0) "◀ ▶ move  ·  OK: watch  ·  ▲ buttons  ·  Back: buttons" else "▼ ${downRows.first().title.lowercase()}  ·  ◀ ▶ buttons  ·  OK: choose  ·  Back: close",
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
        // What the buttons do where you are (as the film player's line): the shortcuts are found with Down, which
        // nothing said; in them, Down for their rows and hold OK for Move / Refresh / Hide (Settings → Appearance →
        // Button hints off: none).
        if (shortcuts.isNotEmpty() && AppColors.palette.opt(UiOpts.BUTTON_HINTS) != 2) Text(
            when {
                showShortcuts && posterRow >= 0 -> "◀ ▶ move  ·  ▲ ▼ rows  ·  OK: watch or open  ·  ▲ from the top row: shortcuts"
                showShortcuts && scCarry.active -> movingHint(ReorderAxis.Horizontal)
                showShortcuts -> "◀ ▶ shortcuts  ·  ▼ their rows  ·  OK: open  ·  Hold OK: move, refresh or hide  ·  ▲ buttons"
                else -> "▼ shortcuts  ·  ◀ ▶ buttons  ·  OK: choose  ·  Back: close"
            },
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.OnAccent,
        maxLines = 1, softWrap = false,
        modifier = Modifier.padding(start = 8.dp).background(AppColors.Accent, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * One poster row at a time, floating under the shortcuts (no panel: the controls' own fade is behind it). Up / Down
 * change row (Up from the first goes back to the shortcuts); the next row's name shows beside this one's.
 */
@Composable
private fun PosterRows(rows: List<OsdRow>, current: Int, more: Boolean = false, onRow: (Int) -> Unit) {
    val r = current.coerceIn(0, rows.lastIndex)
    val row = rows[r]
    val inside = current >= 0
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(current) { if (current >= 0) { awaitFrame(); runCatching { firstCard.requestFocus() } } }
    Column(
        Modifier
            .padding(top = 6.dp)
            // Dimmed while it's only a preview: a dark tint drawn over it (fading the whole row with alpha made the
            // TV draw it twice, every frame).
            .drawWithContent { drawContent(); if (!inside) drawRect(Color.Black.copy(alpha = 0.5f * AppColors.palette.popupAlpha)) }
            .onPreviewKeyEvent { e ->
                if (!inside || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { onRow(r - 1); true }
                    Key.DirectionDown -> { if (r < rows.lastIndex) onRow(r + 1); true }
                    else -> false
                }
            },
    ) {
        OsdRowHeading(row.title, rows.getOrNull(r + 1)?.title, more, position = if (rows.size > 1) "${r + 1} of ${rows.size}" else null)
        androidx.compose.runtime.key(r) {
            // Room round the cards (the highlight grew and was cut off at the row's left edge; kept since cards stopped growing),
            // pulled back by the same amount so the first card still lines up with the row's name.
            LazyRow(
                Modifier.padding(top = 2.dp).offset(x = (-12).dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(row.items.size, key = { row.items[it].key }) { i ->
                    PosterCard(row.items[i], enabled = inside, modifier = if (i == 0) Modifier.focusRequester(firstCard) else Modifier)
                }
            }
        }
    }
}

/**
 * Holding OK on a shortcut (live TV and the film player show this same one): Move along the row, Choose rows, Refresh
 * just its rows (shortcuts that have rows), Hide; Cancel last (the shared options pop-up: it starts on the first).
 */
@Composable
fun ShortcutOptions(sc: LiveShortcut, onMove: () -> Unit, onRefresh: (() -> Unit)?, onHide: () -> Unit, onCancel: () -> Unit, onActivity: () -> Unit = {}, onChooseRows: (() -> Unit)? = null) {
    // A pinned team / league (Settings → Sports): Unpin puts it back under the Sports shortcut. (Pinned ones sit at the
    // end of the row, in the order pinned: no Move.)
    val sport = sc.id.startsWith("sport:")
    // (Each choice closes the pop-up itself, so keepOpen: closing it otherwise is Cancel / Back, onCancel.)
    fun choice(label: String, run: () -> Unit) = PopupOption(label, keepOpen = true) { onActivity(); run() }
    val options = listOfNotNull(
        if (!sport) choice("Move", onMove) else null,
        // Its rows (Down): which, and in what order, for this shortcut only.
        if (onChooseRows != null && LiveShortcutsBridge.allRowsFor(sc.id).isNotEmpty()) choice("Choose rows", onChooseRows) else null,
        // Shortcuts with rows of their own (networks, rows, Movies / Shows, libraries): fetch just those again.
        if (onRefresh != null && listOf("set:", "row:", "home:", "lib:").any { sc.id.startsWith(it) }) choice("Refresh", onRefresh) else null,
        choice(if (sport) "Unpin" else "Hide", onHide),
    )
    OptionsPopup(
        title = sc.label,
        subtitle = if (sport) "Choose the rows under it, or unpin it (it stays under the Sports shortcut)."
            else "Move it along your row, choose the rows under it, fetch them again, or hide it (add it back in Settings → Remote buttons → Live TV shortcuts).",
        options = options,
        onClose = { onActivity(); onCancel() },
    )
}

/**
 * Hold OK on a row card with options (Continue watching): its menu. [beforeRun]: called first with whether the choice
 * plays (the film player sends itself to the corner only when it doesn't).
 */
@Composable
fun CardMenuHost(onActivity: () -> Unit = {}, beforeRun: (plays: Boolean) -> Unit = {}, onClosed: () -> Unit = {}) {
    val p = CardMenu.open ?: return
    fun close() { CardMenu.open = null; onClosed() }
    // The controls closing under it (a channel change, the film going on): the menu goes with them, not left to pop up
    // the next time they open.
    androidx.compose.runtime.DisposableEffect(p.key) { onDispose { if (CardMenu.open?.key == p.key) CardMenu.open = null } }
    OptionsPopup(
        title = p.title,
        subtitle = p.subtitle,
        // (A choice closes it without onClosed: the highlight goes where the choice takes it, not back to the card.)
        options = p.options.map { o -> PopupOption(o.label, keepOpen = true) { CardMenu.open = null; beforeRun(o.plays); o.run() } },
        onClose = { onActivity(); close() },
    )
}

/**
 * Hold OK on a shortcut → Choose rows: every row it can show, On / Off, in the order they'll come. OK turns one on or
 * off; hold OK: Move / Turn off (Right picks an on one up too); while moving, ▲ ▼ place it and OK, Back or Left put it
 * down. Back keeps the choice; Reset goes back to its usual rows. For this shortcut only (the Movies page itself is
 * untouched). [onSave] null: back to its usual rows.
 */
@Composable
fun ChooseRowsDialog(sc: LiveShortcut, onSave: (List<String>?) -> Unit, onClose: () -> Unit, onActivity: () -> Unit = {}) {
    val all = remember(sc.id) { LiveShortcutsBridge.allRowsFor(sc.id).map { it.title }.distinct() }
    val current = LiveShortcutsBridge.chosen[sc.id]
    // On ones first (chosen order, else its usual first few), then the rest.
    val usual = remember(sc.id) { LiveShortcutsBridge.rowsForId(sc.id).map { it.title } }
    var on by remember(sc.id) { mutableStateOf((current ?: usual).filter { it in all }) }
    val order = on + all.filter { it !in on }
    var changed by remember { mutableStateOf(false) }
    val carry = rememberCarry()
    var menuFor by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { awaitFrame(); runCatching { first.requestFocus() } }
    fun close() { if (changed) onSave(on); onClose() }
    fun toggle(t: String) { on = if (t in on) on - t else on + t; changed = true; onActivity() }
    fun move(t: String, by: Int) {
        val i = on.indexOf(t); val j = i + by
        if (i < 0 || j !in on.indices) return
        on = on.toMutableList().apply { add(j, removeAt(i)) }; changed = true; onActivity()
    }
    PopupPanel(onClose = { if (carry.active) carry.drop() else close() }, modifier = Modifier.onPreviewKeyEvent { onActivity(); false }) {
        PopupTitle("Rows under ${sc.label}", if (carry.active) movingHint(ReorderAxis.Vertical) else "OK: on / off  ·  Hold OK: options  ·  Back: done")
        order.forEachIndexed { i, t ->
            androidx.compose.runtime.key(t) {
                val isOn = t in on
                val carried = carry.isCarried(t)
                MenuItem(
                    text = (if (carried) "↕ " else "") + t, trailing = if (carried) "▲ ▼" else if (isOn) "On" else "Off", dim = !isOn, selected = carried,
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).reorderable(
                        carried = carried, onMove = { move(t, it) }, onDrop = carry::drop, axis = ReorderAxis.Vertical,
                        rightToggles = isOn && on.size > 1, onPickUp = { carry.pickUp(t) },
                    ),
                    onLongClick = if (carry.active) null else ({ menuFor = t }),
                    onClick = { if (!carry.active) toggle(t) },
                )
            }
        }
        MenuItem(text = "Reset to its usual rows", selected = false, onClick = { onSave(null); onClose() }, modifier = Modifier.padding(top = 10.dp))
    }
    menuFor?.let { t ->
        val isOn = t in on
        OptionsPopup(
            title = t,
            options = listOfNotNull(if (isOn && on.size > 1) carry.moveOption(t) else null, PopupOption(if (isOn) "Turn off" else "Turn on") { toggle(t) }),
            onClose = { menuFor = null; onActivity() },
        )
    }
}

/** The picture inside a row card (live TV and the film player draw the same): artwork or logo, else the title; progress. */
@Composable
fun OsdCardContent(p: OsdPoster) {
    Box(Modifier.fillMaxSize()) {
        // No picture, with a look of its own (Live now's sport): its colours and icon (the name is under the card).
        if (p.image == null && p.emoji != null) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient((p.tint ?: listOf(0xFF455A64, 0xFF263238)).map { Color(it) })))
            Text(p.emoji, fontSize = 44.sp, modifier = Modifier.align(Alignment.Center))
        } else if (p.image != null) AsyncImage(
            p.image, null,
            error = p.fallbackImage?.let { coil.compose.rememberAsyncImagePainter(it) },
            contentScale = if (p.logo) ContentScale.Fit else ContentScale.Crop,
            modifier = if (p.logo) Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp) else Modifier.fillMaxSize(),
        ) else Text(p.title, fontSize = 13.sp, color = Color.White, maxLines = 2, modifier = Modifier.align(Alignment.Center).padding(8.dp))
        // Not out yet: darker, and said.
        if (p.unreleased) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            Text("Not out yet", fontSize = 12.sp, color = Color.White, modifier = Modifier.align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        // Inset from the edges: on the edge, the highlight's border and the rounded corners hid it.
        p.progress?.takeIf { it > 0f }?.let { f ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 7.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.45f)),
            ) { Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).background(AppColors.Accent)) }
        }
    }
}

/** A row card's name and subtitle under it (the same on live TV and in the film player). */
@Composable
fun OsdCardCaption(p: OsdPoster, highlighted: Boolean) {
    // Big and bright enough to read from the sofa over the glass (12 / 11 pt in grey was too small and faint).
    val align = if (p.centred) androidx.compose.ui.text.style.TextAlign.Center else null
    val wide = if (p.centred) Modifier.fillMaxWidth() else Modifier
    Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (highlighted) Color.White else Color.White.copy(alpha = 0.88f), maxLines = 1,
        overflow = TextOverflow.Ellipsis, textAlign = align, modifier = wide.padding(top = 6.dp))
    p.subtitle?.let { Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = wide) }
    // A line of its own (an episode's release date): it was cut off at the end of the subtitle.
    p.note?.let { Text(it, fontSize = 13.sp, color = if (p.unreleased) AppColors.Accent else Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align, modifier = wide) }
    // Always room for a second line, so every row is the same height and the info bar doesn't jump when you move
    // between rows (Recently added has only names; Episodes and Continue watching have a second line).
    if (p.subtitle == null && p.note == null) Text(" ", fontSize = 13.sp, maxLines = 1)
}

/** A row's heading: its name, then the next row's (or that there are more on the shortcut's own page). */
@Composable
fun OsdRowHeading(title: String, next: String?, more: Boolean = false, position: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        // Several rows under the info bar: where you are among them ("2 of 4").
        if (position != null) Text("   $position", fontSize = 12.sp, color = AppColors.TextSecondary)
        if (next != null && position == null) Text("   ·   Down: $next", fontSize = 12.sp, color = AppColors.TextSecondary)
        else if (more) Text("   ·   More rows: open the shortcut", fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}

/** Card size and look, shared by live TV's cards and the film player's. */
const val OSD_CARD_W = 176
const val OSD_CARD_H = 99

/**
 * The film player's card: drawn exactly as live TV's (FocusCardStatic: the shared 3 dp ring, no growing),
 * highlighted by the player (it has no focus of its own).
 */
@Composable
fun OsdCardStatic(p: OsdPoster, highlighted: Boolean, widthDp: Int = OSD_CARD_W) {
    // Settings → Appearance → Preview cards.
    @Suppress("NAME_SHADOWING") val widthDp = (widthDp * AppColors.palette.cardScale).toInt()
    Column(Modifier.width(widthDp.dp)) {
        FocusCardStatic(
            highlighted,
            Modifier.size(widthDp.dp, (widthDp * OSD_CARD_H / OSD_CARD_W).dp),
            containerColor = Color.White.copy(alpha = if (highlighted) 0.18f else 0.10f),
        ) { OsdCardContent(p) }
        OsdCardCaption(p, highlighted)
    }
}

/** Live TV's card (FocusCard: the shared 3 dp ring, no growing; it grew 1.05 with a 2 dp ring until the UI review). */
@Composable
private fun PosterCard(p: OsdPoster, enabled: Boolean, modifier: Modifier = Modifier) {
    var on by remember { mutableStateOf(false) }
    // Its hold-OK menu closed without going anywhere (Cancel, Back): the highlight comes back to it.
    val self = remember { FocusRequester() }
    var held by remember { mutableStateOf(false) }
    // (Only the card actually held: the same title can be in two rows.)
    LaunchedEffect(CardMenu.open == null) {
        if (held && CardMenu.open == null) { held = false; awaitFrame(); runCatching { self.requestFocus() } }
    }
    // Settings → Appearance → Preview cards.
    val k = AppColors.palette.cardScale
    Column(Modifier.width((OSD_CARD_W * k).dp)) {
        FocusCard(
            onClick = p.onClick,
            onLongClick = if (p.options.isEmpty()) null else ({ held = true; CardMenu.open = p }),
            enabled = enabled,
            modifier = modifier.focusRequester(self).size((OSD_CARD_W * k).dp, (OSD_CARD_H * k).dp).onFocusChanged { on = it.isFocused },
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
        ) { OsdCardContent(p) }
        OsdCardCaption(p, on)
    }
}

/** One of live TV's bar buttons, sized like the film player's (Settings → Appearance → Info bar & controls → Player controls size). */
@Composable
private fun BarButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, groupEnd: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val k = AppColors.palette.controlsScale
    RoundIconButton(
        icon, label, onClick, modifier, onLongClick = onLongClick, groupEnd = groupEnd,
        width = (48 * k).dp, columnWidth = (72 * k).dp, iconSize = (26 * k).dp, labelSize = (12 * (0.5f + k / 2)).sp, labelGap = (4 * k).dp,
    )
}

/**
 * A shortcut on the shortcuts row: drawn in Settings → Shortcut style, at Settings → Live TV shortcuts → Shortcut size
 * (Small is the first), larger than the buttons so logos read from the sofa. It grows a little when highlighted, so
 * it's clear which one you're on (the buttons don't): at once with List scrolling on Instant (RoundIconButton).
 */
@Composable
private fun ShortcutButton(sc: LiveShortcut, label: String, modifier: Modifier, onFocus: () -> Unit, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val k = AppColors.palette.controlsScale
    val size = floatArrayOf(1f, 1.25f, 1.5f)[AppColors.palette.opt(UiOpts.SHORTCUT_SIZE).coerceIn(0, 2)]
    fun b(v: Int) = ((v * size).toInt() * k).dp
    val style = shortcutStyle()
    val (bw, bh) = shortcutBox(b(64), style)
    RoundIconButton(
        sc.icon, label, onClick, modifier, onLongClick = onLongClick,
        width = bw, height = bh, columnWidth = bw + b(24), shape = shortcutShape(style),
        labelSize = (12 * (0.5f + k / 2)).sp, labelGap = (4 * k).dp, focusedScale = 1.2f, onFocus = onFocus,
    ) { on ->
        Box(Modifier.fillMaxSize().padding(if (on) (3 * k).dp else 0.dp), contentAlignment = Alignment.Center) { ShortcutFace(sc, style, bh) }
    }
}

@Composable
private fun LiveProgress(program: GuideProgram, now: Long, highlighted: Boolean = false, marker: Float? = null) {
    val fraction = ((now - program.startTime).toFloat() / (program.endTime - program.startTime)).coerceIn(0f, 1f)
    // The bar takes all the room, "… min left" right after it at the end, whatever the info bar's width.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The rewind marker: the bar up to it in the highlight colour, from it to live dimmed, and a white knob on it.
        BoxWithConstraints(Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(if (highlighted) 6.dp else 4.dp).background(Color(0x44FFFFFF), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(AppColors.Accent.copy(alpha = if (marker != null) 0.35f else 1f), RoundedCornerShape(2.dp)))
                if (marker != null) Box(Modifier.fillMaxWidth(marker).fillMaxHeight().background(AppColors.Accent, RoundedCornerShape(2.dp)))
            }
            // Highlighted: a knob on the bar (at live until you rewind, then on the marker), so it's clear you're on it.
            if (highlighted || marker != null) SeekKnob(maxWidth * (marker ?: fraction), big = highlighted)
        }
        Spacer(Modifier.width(12.dp))
        Text("${((program.endTime - now) / 60_000).coerceAtLeast(0)} min left", fontSize = 13.sp, color = AppColors.TextSecondary)
    }
}

/** Where you are on a seek bar: a white knob; larger with an accent ring while the bar is highlighted. */
@Composable
private fun SeekKnob(x: androidx.compose.ui.unit.Dp, big: Boolean) {
    val size = if (big) 20.dp else 12.dp
    Box(
        Modifier.padding(start = (x - size / 2).coerceAtLeast(0.dp)).size(size)
            .then(if (big) Modifier.border(3.dp, AppColors.Accent, CircleShape) else Modifier)
            .padding(if (big) 3.dp else 0.dp)
            .background(Color.White, CircleShape)
    )
}

/** Focusable seek bar: Left/Right skip (faster when held), OK plays/pauses. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    live: Long? = null,
    paused: Boolean,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val seekMs = LocalRemote.current.seek.ms
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = if (e.nativeKeyEvent.repeatCount >= 4) seekMs * 4 else seekMs
                when (e.key) {
                    Key.DirectionLeft -> onSeek(-step)
                    Key.DirectionRight -> onSeek(step)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onToggle()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
            val liveFraction = live?.let { (it.toFloat() / duration).coerceIn(0f, 1f) }
            Box(Modifier.fillMaxWidth().height(if (focused) 6.dp else 4.dp).background(Color(0x44FFFFFF), RoundedCornerShape(3.dp))) {
                // Broadcast so far (up to live), then where you're playing.
                liveFraction?.let { Box(Modifier.fillMaxWidth(it).fillMaxHeight().background(AppColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(3.dp))) }
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(AppColors.Accent, RoundedCornerShape(3.dp)))
            }
            // Where you are: a knob. Live: a thin white mark.
            SeekKnob(maxWidth * fraction, big = focused)
            liveFraction?.let { Box(Modifier.padding(start = (maxWidth * it - 1.dp).coerceAtLeast(0.dp)).size(width = 2.dp, height = 14.dp).background(Color(0xCCFFFFFF))) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (focused) {
                Box(Modifier.size(8.dp).background(AppColors.Accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (paused) "Paused  ·  OK: play  ·  ◀ ▶ skip" else "OK: pause  ·  ◀ ▶ skip, hold to go faster",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${clock(position)} / ${clock(duration)}" + (live?.let { l -> if (l > position + 60_000) "  ·  ${(l - position) / 60_000} min behind live" else "" } ?: ""), fontSize = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun OptionPopup(
    wide: Boolean = false,
    title: String,
    options: List<Pair<String, Boolean>>,
    emptyText: String,
    onPick: (Int) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(options.size) {
        awaitFrame()
        runCatching { focus.requestFocus() }
    }
    Column(
        Modifier
            .width(if (wide) 560.dp else 300.dp)
            // (Down past the last choice stayed in it: it went onto the buttons with the pop-up still open.)
            .trapFocus()
            .glass(10.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        if (options.isEmpty()) {
            Text(emptyText, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(12.dp).focusRequester(focus).focusable())
        }
        val selectedIndex = options.indexOfFirst { it.second }.coerceAtLeast(0)
        // (Scrolls inside the pop-up: long lists stay reachable, see popupScroll.)
        Column(Modifier.popupScroll(0.55f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, (label, selected) ->
                MenuItem(
                    text = label,
                    selected = selected,
                    onClick = { onPick(i) },
                    trailing = if (selected) "✓" else null,
                    modifier = if (i == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}

/** What the player on this box is getting, then Dispatcharr's live view of the stream (both refresh every 2 s while open). */
@Composable
private fun StreamInfoPopup(vm: PlaybackViewModel) {
    var lines by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var box by remember { mutableStateOf(vm.playerInfo(LiveQuality.fps)) }
    LaunchedEffect(Unit) {
        while (true) {
            box = vm.playerInfo(LiveQuality.fps)
            lines = if (vm.dispatcharrLive) vm.streamInfo() else emptyList()
            delay(2_000)
        }
    }
    Column(
        Modifier.width(460.dp).glass(10.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("On this box (the player)", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        box.forEach { (k, v) ->
            Row {
                Text(k, fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.width(110.dp))
                Text(v, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val l = lines
        if (l == null || l.isNotEmpty()) Text("From Dispatcharr", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        if (l == null) Text("Loading…", fontSize = 13.sp, color = Color.White)
        else l.forEach { (k, v) ->
            Row {
                Text(k, fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.width(110.dp))
                Text(v, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
