package io.github.petebutty.viewarr.ui.home

import io.github.petebutty.viewarr.ui.common.AppPill
import io.github.petebutty.viewarr.ui.common.FocusCard
import io.github.petebutty.viewarr.ui.common.LiveBehind
import io.github.petebutty.viewarr.ui.common.SearchField
import io.github.petebutty.viewarr.ui.common.VoiceButton
import io.github.petebutty.viewarr.ui.theme.Tokens
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import io.github.petebutty.viewarr.ui.guide.GuideViewModel
import io.github.petebutty.viewarr.data.media.MediaType
import io.github.petebutty.viewarr.ui.media.MediaScreen
import io.github.petebutty.viewarr.ui.media.MediaViewModel
import io.github.petebutty.viewarr.data.local.ChannelListItem
import io.github.petebutty.viewarr.data.local.GuideProgram
import io.github.petebutty.viewarr.ui.theme.AppColors
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.petebutty.viewarr.ui.common.RecentSearches
import io.github.petebutty.viewarr.ui.media.SearchFilter
import io.github.petebutty.viewarr.ui.media.VodHost
import io.github.petebutty.viewarr.ui.music.MusicCard
import io.github.petebutty.viewarr.ui.music.MusicSearchResults
import io.github.petebutty.viewarr.ui.music.MusicViewModel
import io.github.petebutty.viewarr.ui.theme.Dates

/** Search: a text box (the TV keyboard), then channel matches and poster rows of results. */
@Composable
internal fun SearchPage(
    guide: GuideViewModel,
    /** Dispatcharr, for the channels' logos. */
    serverUrl: String,
    focusEnabled: Boolean,
    onOpenMenu: () -> Unit,
    onExit: () -> Unit,
    onChannel: (ChannelListItem) -> Unit,
    /** A programme from the guide that hasn't started: its details (with Record). */
    onProgramme: (ChannelListItem, GuideProgram) -> Unit,
    /** Catch-up: a past programme picked from the Catch-up row (plays it from the start). */
    onCatchup: (ChannelListItem, GuideProgram) -> Unit = { _, _ -> },
    /** Popped up with the search button: the keyboard opens straight away, ready to type. */
    openKeyboard: Boolean = false,
    /** Opened on this search (Watch from the start, a recent search). */
    startQuery: String? = null,
    /** Opened from the menu's Voice search: start listening straight away. */
    startVoice: Boolean = false,
    /** Opened with the Catch-up pill picked (the guide's "Search catch-up"). */
    startCatchup: Boolean = false,
    /** A music artist or album picked: open it on the Music page. */
    onMusic: (MusicCard) -> Unit = {},
) {
    var fieldFocused by remember { mutableStateOf(false) }
    // Typing in the box (else read-only: no keyboard just for focus passing through).
    var typing by remember { mutableStateOf(openKeyboard) }
    val searchScope = androidx.compose.runtime.rememberCoroutineScope()
    val media: MediaViewModel = hiltViewModel(key = "media-search")
    // (Keyed on it: a new start query, e.g. a viewarr://search link, while search is already open replaces the old one.)
    var query by rememberSaveable(startQuery) { mutableStateOf(startQuery ?: "") }
    val fieldFocus = remember { FocusRequester() }
    val rowsFocus = remember { FocusRequester() }
    // Down from the box: quick picks first, then the filters, then the results.
    val picksFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val detailOpen by media.detail.collectAsStateWithLifecycle()
    val playingOpen by media.playing.collectAsStateWithLifecycle()
    // The box only takes focus on the search page itself, never behind a title's page or the player
    // (focus passing through it made the keyboard flash up).
    val fieldFree = detailOpen == null && (playingOpen == null || VodHost.mini)
    val channels by androidx.compose.runtime.produceState(emptyList<ChannelListItem>(), query) {
        delay(400)
        value = guide.searchChannels(query)
    }
    // Live TV: programmes in the guide whose title matches, on now and coming up.
    val onTv by androidx.compose.runtime.produceState(emptyList<Pair<ChannelListItem, GuideProgram>>(), query) {
        delay(400)
        value = runCatching { guide.searchPrograms(query) }.getOrDefault(emptyList())
    }
    // Catch-up: programmes that have been on, on channels with catch-up (Dispatcharr's own programme search).
    val catchupHits by androidx.compose.runtime.produceState(emptyList<Pair<ChannelListItem, GuideProgram>>(), query) {
        delay(500)
        value = runCatching { guide.searchCatchup(query) }.getOrDefault(emptyList())
    }
    val filter by media.searchFilter.collectAsStateWithLifecycle()
    // Catch-up: its own pill shows only past programmes; All and Live TV show them alongside the rest.
    val catchupOnly = filter == SearchFilter.Catchup
    val searchFocus by media.searchFocus.collectAsStateWithLifecycle()
    val catchupFirst = searchFocus == SearchFilter.Catchup
    val showTv = filter == SearchFilter.All || filter == SearchFilter.LiveTv || catchupOnly
    val showMusic = filter == SearchFilter.All || filter == SearchFilter.Music
    val musicVm: MusicViewModel = hiltViewModel()
    val musicOn by musicVm.connected.collectAsStateWithLifecycle()
    LaunchedEffect(query) { media.search(query) }
    // Quick picks under the box while typing: tap one instead of typing the rest.
    val picks by androidx.compose.runtime.produceState(emptyList<String>(), query) {
        if (query.trim().length < 2) { value = emptyList(); return@produceState }
        delay(300)
        value = media.suggestions(query)
    }
    // Voice search: Android's speech recogniser listens, the words become the search.
    val context = androidx.compose.ui.platform.LocalContext.current
    // The mic is always there: listening in the app, or the keyboard's mic on a box without a recogniser.
    val canVoice = true
    val voice = io.github.petebutty.viewarr.ui.common.rememberVoiceSearch(
        onResult = { said ->
            query = said
            searchScope.launch { delay(900); runCatching { rowsFocus.requestFocus() } }
        },
        onKeyboard = {
            typing = true
            runCatching { fieldFocus.requestFocus() }
            searchScope.launch { kotlinx.coroutines.android.awaitFrame(); keyboard?.show() }
        },
    )
    fun listen() = voice.toggle()
    LaunchedEffect(Unit) { if (startVoice) { repeat(3) { awaitFrame() }; listen() } }
    // Remembered for the menu's Recent searches when search closes (what you last searched, not every letter typed).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val latestQuery by androidx.compose.runtime.rememberUpdatedState(query)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { RecentSearches.add(ctx, latestQuery) } }
    val micFocus = remember { FocusRequester() }
    // Each search starts showing everything.
    LaunchedEffect(Unit) {
        media.setSearchFilter(if (startCatchup) SearchFilter.Catchup else SearchFilter.All)
        media.setSearchFocus(null)
    }
    // Focus the box when the page opens (opened with a title already in it, e.g. from the guide when no match was
    // found: the box too, so the remote isn't left on nothing).
    LaunchedEffect(Unit) {
        if (query.isNotEmpty()) {
            repeat(3) { awaitFrame() }
            if (focusEnabled) runCatching { fieldFocus.requestFocus() }
            return@LaunchedEffect
        }
        awaitFrame()
        runCatching { fieldFocus.requestFocus() }
        if (openKeyboard) { awaitFrame(); keyboard?.show() }
    }
    // Live TV results (On TV, Channels): at the top when the search is a channel, else after the films and shows.
    // Catch-up results (Search → Catch-up row): first when its pill is looked at.
    val catchupRow: @Composable () -> Unit = {
                // Catch-up: shown and gone, still in its channel's catch-up window. OK plays it from the start.
                if (catchupHits.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Catch-up", fontSize = 16.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.offset(x = (-10).dp)) {
                        items(catchupHits, key = { (c, p) -> "cu:${c.id}:${p.startTime}" }) { (ch, p) ->
                            ResultCard(onClick = { onCatchup(ch, p) }, width = 300.dp, canFocus = fieldFree && focusEnabled) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                                        io.github.petebutty.viewarr.ui.guide.channelLogo(serverUrl, ch)?.let { logo ->
                                            coil.compose.AsyncImage(model = logo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(p.title, fontSize = 14.sp, color = AppColors.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        // Which episode: "S2 · E5 · Feel Her Love". The name is the sub-title, else (Sky's
                                        // guides) the start of the description before its colon.
                                        val epName = p.subTitle ?: p.description?.substringBefore(": ", "")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 }
                                        val epLine = listOfNotNull(p.season?.let { "S$it" }, p.episode?.let { "E$it" }, epName).joinToString(" · ")
                                        if (epLine.isNotEmpty()) Text(epLine, fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            "↺ " + Dates.dayTime(p.startTime) + " on " + ch.name,
                                            fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
    }
    val tvSections: @Composable () -> Unit = {
            if (query.trim().length >= 2 && showTv) {
                if (catchupFirst) catchupRow()
                // On TV: guide programmes that match. On now → watch; later → details and Record.
                if (onTv.isNotEmpty() && !catchupOnly) {
                    Spacer(Modifier.height(12.dp))
                    Text("On TV", fontSize = 16.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                    val nowMs = System.currentTimeMillis()
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.offset(x = (-10).dp)) {
                        items(onTv, key = { (c, p) -> "${c.id}:${p.rowId}" }) { (ch, p) ->
                            val live = p.startTime <= nowMs
                            ResultCard(onClick = { if (live) onChannel(ch) else onProgramme(ch, p) }, width = 250.dp, canFocus = fieldFree && focusEnabled) {
                                // The channel's logo beside the programme, so it's clear it's a showing on that channel.
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                                        io.github.petebutty.viewarr.ui.guide.channelLogo(serverUrl, ch)?.let { logo ->
                                            coil.compose.AsyncImage(model = logo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(p.title, fontSize = 14.sp, color = AppColors.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            (if (live) "● Now on " else whenText(p.startTime, nowMs) + " on ") + ch.name,
                                            fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (!catchupFirst) catchupRow()
                Spacer(Modifier.height(12.dp))
                if (channels.isNotEmpty() && !catchupOnly) {
                    Text("Channels", fontSize = 16.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.offset(x = (-10).dp)) {
                        items(channels, key = { it.id }) { ch ->
                            // The channel's logo, name and number, like the guide.
                            // (Not while a title, its episodes or a player is open over search: focus that fell
                            // here, e.g. while an episode's source list opened, started a live channel.)
                            ResultCard(onClick = { onChannel(ch) }, width = 250.dp, canFocus = fieldFree && focusEnabled) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(width = 64.dp, height = 38.dp), contentAlignment = Alignment.Center) {
                                        io.github.petebutty.viewarr.ui.guide.channelLogo(serverUrl, ch)?.let { logo ->
                                            coil.compose.AsyncImage(model = logo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(ch.name, fontSize = 14.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    ch.number?.let { n -> Text(if (n % 1.0 == 0.0) n.toInt().toString() else n.toString(), fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(start = 6.dp)) }
                                }
                            }
                        }
                    }
                }
            }
    }
    val mediaRowsNow by media.rows.collectAsStateWithLifecycle()
    val q0 = query.trim()
    val titleHit = mediaRowsNow.rows.any { r -> r.items.any { it.title.equals(q0, ignoreCase = true) } }
    val musicFirst = searchFocus == SearchFilter.Music
    // Live TV / Catch-up looked at: the TV results move above the rest (music too, when its pill is looked at).
    val tvFirst = musicFirst || searchFocus == SearchFilter.LiveTv || catchupFirst ||
        (q0.length >= 2 && !titleHit && (channels.any { it.name.startsWith(q0, ignoreCase = true) } || onTv.any { it.second.title.equals(q0, ignoreCase = true) }))
    MediaScreen(
        type = MediaType.Movie,
        focusRequester = rowsFocus,
        focusEnabled = focusEnabled,
        onOpenMenu = onOpenMenu,
        // Back: from the results to the search box; from the box, "press Back again to exit".
        onBack = {
            if (fieldFocused) onExit()
            else {
                runCatching { fieldFocus.requestFocus() }
                // Back to the box without the keyboard popping up (OK on the box opens it).
                searchScope.launch { kotlinx.coroutines.android.awaitFrame(); keyboard?.hide(); kotlinx.coroutines.android.awaitFrame(); keyboard?.hide() }
            }
        },
        searchHeader = {
            // Quick picks only while you're up here typing: down in the results they'd just take room.
            var headerFocused by remember { mutableStateOf(false) }
            // A film minimised to the corner (or live TV in the guide's window): the box and mic beside the window and
            // what follows below it (the header sat under the film: UI review, 2026-11-17).
            val beside = LiveBehind.preview
            Column(Modifier.onFocusChanged { headerFocused = it.hasFocus }.padding(start = Tokens.Gutter, end = Tokens.Gutter, top = if (beside) 26.dp else 18.dp, bottom = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (beside) Modifier.padding(start = Tokens.BesideWindowStart - Tokens.Gutter).heightIn(min = Tokens.BelowWindowTop - 36.dp).wrapContentHeight(Alignment.Top) else Modifier,
                ) {
                // Voice: the shared round mic before the bar, as on Android TV's own search (rings pulse out while
                // listening, swelling with your voice).
                if (canVoice) {
                    VoiceButton(
                        onClick = ::listen,
                        modifier = Modifier.focusRequester(micFocus).focusProperties { canFocus = fieldFree && focusEnabled },
                        listening = voice.listening, level = voice.level, size = 44.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                // The shared slim pill-shaped bar (magnifier, then the text), like Android TV's own search.
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (musicOn == true) "Search films, shows, music, people, channels and the TV guide" else "Search films, shows, people, channels and the TV guide",
                    listening = voice.listening,
                    listeningText = "Listening… say a film, show, person or channel",
                    // The keyboard only when asked for (OK on the box, or search opened with the search button): focus
                    // passing through the box as pages close (a title opened from the guide, then Back) made it flash.
                    // A read-only box never brings it up, so the box is read-only until OK.
                    readOnly = !typing,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        keyboard?.hide()
                        if (!focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)) runCatching { rowsFocus.requestFocus() }
                    }),
                    modifier = Modifier
                        .fillMaxWidth(if (beside) 1f else 0.55f)
                        .height(44.dp)
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = fieldFree && focusEnabled }
                        .onFocusChanged { fieldFocused = it.isFocused; if (!it.isFocused) { typing = false; keyboard?.hide() } }
                        // The text box would keep the remote: Down always leaves it for the results.
                        .onPreviewKeyEvent { e ->
                            // Left in an empty box: the mic if there is one, else the menu (with text, Left moves the cursor).
                            // (Not typing, the cursor can't be seen: Left goes to the mic / the menu with text in the box too.)
                            if (e.key == Key.DirectionLeft && (query.isEmpty() || !typing)) {
                                // (Asked for directly: a box being typed in keeps Left for its cursor.)
                                if (e.type == KeyEventType.KeyDown) { if (canVoice) runCatching { micFocus.requestFocus() } else onOpenMenu() }
                                return@onPreviewKeyEvent true
                            }
                            if (!typing && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                                if (e.type == KeyEventType.KeyUp) {
                                    typing = true
                                    searchScope.launch { kotlinx.coroutines.android.awaitFrame(); keyboard?.show() }
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                                val next = when {
                                    picks.isNotEmpty() -> picksFocus
                                    query.trim().length >= 2 -> filtersFocus
                                    else -> rowsFocus
                                }
                                if (runCatching { next.requestFocus() }.isFailure) runCatching { rowsFocus.requestFocus() }
                                true
                            } else false
                        },
                )
                }
                // Quick picks: the best-known titles and names for what's typed so far.
                if (picks.isNotEmpty() && headerFocused) {
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // (One copy of the picks for the count, keys and cards: they change as you type, and reading
                        // the latest one with an older count went past its end.)
                        val pickList = picks
                        items(pickList.size, key = { "$it:" + pickList.getOrNull(it) }) { i ->
                            val pick = pickList.getOrNull(i) ?: return@items
                            // The shared pill (no growing: it was cut off at the edges of the strip), a thin accent edge.
                            AppPill(
                                text = "⌕  $pick", selected = false, outlined = true,
                                onClick = {
                                    query = pick
                                    searchScope.launch { delay(900); runCatching { rowsFocus.requestFocus() } }
                                },
                                modifier = (if (i == 0) Modifier.focusRequester(picksFocus) else Modifier).focusProperties { canFocus = fieldFree && focusEnabled },
                            )
                        }
                    }
                }
                // Filters: narrow the results to one kind.
                if (query.trim().length >= 2) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchFilter.entries.filter { it != SearchFilter.Music || musicOn == true }.forEach { f ->
                            val on = f == filter
                            AppPill(
                                text = f.label, selected = on,
                                onClick = { media.setSearchFilter(f) },
                                modifier = (if (on) Modifier.focusRequester(filtersFocus) else Modifier).focusProperties { canFocus = fieldFree && focusEnabled }
                                    // Looking at a pill brings its kind of result to the top (OK filters to just that kind).
                                    .onFocusChanged { if (it.isFocused) media.setSearchFocus(f) },
                            )
                        }
                    }
                }
                if (tvFirst) {
                    if (musicFirst && showMusic && query.trim().length >= 2) MusicSearchResults(query, fieldFree && focusEnabled, onMusic)
                    tvSections()
                    if (!musicFirst && showMusic && query.trim().length >= 2) MusicSearchResults(query, fieldFree && focusEnabled, onMusic)
                }
            }
        },
        searchFooter = if (tvFirst) null else ({ Column(Modifier.padding(horizontal = Tokens.Gutter)) { tvSections(); if (showMusic && query.trim().length >= 2) MusicSearchResults(query, fieldFree && focusEnabled, onMusic) } }),
        vm = media,
    )
}


/**
 * A search result card (On TV, Catch-up, Channels): the shared [FocusCard] look, a 3 dp accent ring and no growing
 * (catch-up grew 1.1 with an accent fill, channels didn't: UI review, Tier 3). [canFocus] false while a title or the
 * player is open over search.
 */
@Composable
private fun ResultCard(onClick: () -> Unit, width: androidx.compose.ui.unit.Dp, canFocus: Boolean, content: @Composable () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.width(width).focusProperties { this.canFocus = canFocus }) { content() }
}
