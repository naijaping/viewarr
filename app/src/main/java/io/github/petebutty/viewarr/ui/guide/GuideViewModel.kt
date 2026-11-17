package io.github.petebutty.viewarr.ui.guide

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.petebutty.viewarr.data.dvr.PluginsRepository
import io.github.petebutty.viewarr.data.guide.LiveSports
import io.github.petebutty.viewarr.data.guide.SportsEvents
import io.github.petebutty.viewarr.data.local.ChannelListItem
import io.github.petebutty.viewarr.data.local.GroupWithCount
import io.github.petebutty.viewarr.data.local.GuideProgram
import io.github.petebutty.viewarr.data.media.ProgramArt
import io.github.petebutty.viewarr.data.repository.GuideRepository
import io.github.petebutty.viewarr.data.session.SessionStore
import io.github.petebutty.viewarr.data.sports.GameMatch
import io.github.petebutty.viewarr.data.sports.SkySchedule
import io.github.petebutty.viewarr.data.sports.SportGame
import io.github.petebutty.viewarr.ui.theme.AppColors
import io.github.petebutty.viewarr.ui.theme.Dates
import io.github.petebutty.viewarr.ui.theme.UiOpts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.abs

/** row = selected channel index; focusTime = time anchor (classic IPTV apps keeps the column time when moving up/down). */
data class GuideCursor(val row: Int, val focusTime: Long, val windowStart: Long)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val repo: GuideRepository,
    private val sessions: SessionStore,
    private val programArt: ProgramArt,
    private val plugins: PluginsRepository,
    private val sports: SportsEvents,
    private val sky: SkySchedule,
) : ViewModel() {
    /** One event for Live now: its channel and guide entry, its tidied name, sport and picture (if found). */
    data class LiveEvent(
        val channel: ChannelListItem,
        val program: GuideProgram,
        val title: String,
        val sport: SportsEvents.Sport?,
        val picture: String?,
    )

    /** A game from your Sports schedule on now or soon, on the channel the Sports page found for it (its guide entry, if any). */
    data class LiveGame(val game: SportGame, val channel: ChannelListItem, val program: GuideProgram?)

    /** Live now (the Live TV hub's shortcut): event channels with something on, and what's starting soon. */
    data class LiveEvents(
        val onNow: List<LiveEvent>,
        val soon: List<LiveEvent>,
        /** The group most of what's on now is in (OK on the shortcut opens the guide there). */
        val mainGroup: String?,
    )

    // The event / sports groups: the ones Event Channel Managarr (a Dispatcharr plugin) looks after, read once. Without
    // it (or for users Dispatcharr doesn't show plugins to), groups whose names look like sport or events.
    private var eventGroupNames: Set<String>? = null
    private suspend fun eventGroups(): Set<String> {
        eventGroupNames?.let { return it }
        // (Only a real answer is kept: a failed lookup is asked again next time, not stuck on the fallback all session.)
        val installed = runCatching { plugins.installed() }.getOrNull() ?: return emptySet()
        val fromPlugin = installed
            .firstOrNull { it.key == "event_channel_managarr" && it.enabled }
            ?.settings?.get("channel_groups")?.let { (it as? JsonPrimitive)?.content }
            ?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        return fromPlugin.also { eventGroupNames = it }
    }
    /**
     * A guide entry that only repeats its channel's name ("NBA B 09:", "UEFA 05: 14:45 Belgium vs Italy" all day long:
     * a filler source such as EPG & Sports Editor's "Fill") or an empty slot's label ("F1 TV 70:"): not an event on now.
     * (2,120 of 2,238 event channels "on now" were these, mostly empty or long finished. Peter, 2026-11-17.)
     */
    private fun fillerFor(channelBare: String, p: GuideProgram): Boolean {
        val t = p.title.trim()
        if (t.endsWith(":") || bare(t) == channelBare) return true
        // A stream's own name standing in for a programme ("US NFL GAME (UHD/4K)" all afternoon: a filler source copies
        // the provider's stream names, not the channel's): its quality mark gives it away, as does an all-capitals name
        // spanning 5 hours or more ("FORMULA 1 ALB-ALBON-WILLIAMS", 6 h; not "WWE RAW", 3 h). Real listings are mixed case. (Peter, 2026-11-17.)
        if (QUALITY_MARK.containsMatchIn(t)) return true
        val letters = t.filter { it.isLetter() }
        return letters.length >= 4 && letters == letters.uppercase() && p.endTime - p.startTime >= 5 * 3_600_000L
    }
    private val QUALITY_MARK = Regex("(?i)\\((?:[^)]*\\b)?(uhd|4k|fhd|hd|sd|hevc|raw)\\b[^)]*\\)|\\b(uhd|fhd)\\b|\\|")
    /**
     * Whether a feed whose name gives its event's time ("… 19:00 …", maybe a date "17/11" / "17-11") is on around [now]:
     * 15 minutes before to 5 hours after. No time in the name: true (its guide entries decide).
     */
    private fun feedOnAround(name: String, now: Long): Boolean {
        val m = FEED_TIME.find(name) ?: return true
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        FEED_DATE.find(name)?.let { d ->
            val day = d.groupValues[1].toInt(); val month = d.groupValues[2].toInt()
            if (day != cal.get(java.util.Calendar.DAY_OF_MONTH) || month != cal.get(java.util.Calendar.MONTH) + 1) return false
        }
        val start = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, m.groupValues[1].toInt()); set(java.util.Calendar.MINUTE, m.groupValues[2].toInt())
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        // (Just after midnight, an event that started last night is still on.)
        return listOf(start, start - 86_400_000L).any { now in (it - 15 * 60_000L)..(it + 5 * 3_600_000L) }
    }
    // A clock time on its own ("19:00", not the feed's number "01:"), and a day/month ("17/11", "17-11", "17.11").
    private val FEED_TIME = Regex("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d])")
    private val FEED_DATE = Regex("(?<!\\d)(0?[1-9]|[12]\\d|3[01])[/.-](0?[1-9]|1[0-2])(?![\\d/.-])")

    /** An event's slot, not a filler block: under 9 hours (golf, cricket and a race weekend's sessions fit). */
    private fun realSlot(p: GuideProgram) = p.endTime - p.startTime in 1..9 * 3_600_000L
    /** The guide says it's live: its flag, or "Live" in the title, subtitle or the synopsis's start. */
    private fun saysLive(p: GuideProgram) = p.isLive || LIVE_MARK.containsMatchIn(p.title) || p.subTitle?.let { LIVE_MARK.containsMatchIn(it) } == true ||
        p.description?.take(80)?.let { LIVE_START.containsMatchIn(it) } == true
    private val LIVE_MARK = Regex("(?i)\\blive\\b|ᴸᶦᵛᵉ|🔴")
    private val LIVE_START = Regex("(?i)^\\W*live\\b|\\blive (coverage|action|from)\\b")
    /** A name for [fillerFor]'s comparison: lower case, no date ending ("… 17-11"), letters and digits only. */
    private fun bare(t: String) = t.lowercase().replace(DATE_SUFFIX, "").replace(NON_WORD, "")
    // (Built once: compiled for each of thousands of guide entries, Live now's filling held up the remote.)
    private val DATE_SUFFIX = Regex("\\s+\\d{1,2}-\\d{1,2}$")
    private val NON_WORD = Regex("[^\\p{L}0-9]+")

    /** A provider's numbered event feed's name: "UEFA 05:", "NFL B 13:", "AMAZON UK EVENT 3", "PPV 12". */
    private val EVENT_FEED = Regex("\\d{1,3}\\s*:|\\bevents?\\s*\\d+|\\bppv\\s*\\d+", RegexOption.IGNORE_CASE)
    private val eventWords = Regex("\\b(sports?|events?|ppv|pay per view|box office|football|soccer|rugby|ufc|boxing|wwe|nfl|nba|nhl|mlb|f1|motogp|cricket|golf|tennis|darts|racing|premier league|efl|epl|spfl|uefa|matchroom|dazn)\\b", RegexOption.IGNORE_CASE)

    /**
     * What's on the event channels now, and starting in the next three hours (most recent / soonest first).
     * [games]: your Sports schedule's games on now or within three hours, each on its channel (shown first, with the
     * score); [known]: every game it knows, finished ones too (a guide entry for a finished game is a dead channel, and
     * one for a game in [games] is already shown). (Live now trusted the guide alone and filled with finished events and
     * empty slots. Peter, 2026-11-17.)
     */
    suspend fun liveEvents(games: List<LiveGame> = emptyList(), known: List<SportGame> = emptyList()): LiveEvents =
        // Off the screen's thread: thousands of guide entries sorted and matched (on it, the remote stalled while Live
        // now filled. Peter, 2026-11-17).
        withContext(Dispatchers.Default) { liveEventsNow(games, known) }

    private suspend fun liveEventsNow(games: List<LiveGame>, known: List<SportGame>): LiveEvents {
        val named = eventGroups()
        val all = allChannelsList()
        // Every event channel (not the first 400 by number: a "4K / UHD" group early in the numbers filled the list, and
        // the groups after it, UEFA, NFL, UFC…, were never looked at. Peter, 2026-11-17).
        val chans = all.filter { c -> c.groupName?.let { g -> if (named.isNotEmpty()) g.trim().lowercase() in named else eventWords.containsMatchIn(g) } == true }
        val now = System.currentTimeMillis()
        val onNow = ArrayList<Pair<ChannelListItem, GuideProgram>>()
        val soon = ArrayList<Pair<ChannelListItem, GuideProgram>>()
        // Placeholders ("Ended: …", "No event today") left out: only real events. Every event channel's next three
        // hours in one read (one channel at a time was hundreds of reads a minute).
        val byId = chans.associateBy { it.id }
        val all3h = runCatching { repo.programsForChannels(chans.map { it.id }, now - 1, now + 3 * 3_600_000L) }.getOrNull().orEmpty()
        for ((id, ps0) in all3h.groupBy { it.channelId }) {
            val c = byId[id] ?: continue
            // Real events only: on a provider's event feed ("UEFA 05:", "AMAZON UK EVENT 3") any programme that isn't a
            // placeholder; on an ordinary channel in those groups (US CBS (UHD/4K), Sky Sports+…) only one that's a
            // sport by its title (a match, a race, a fight), not its everyday shows.
            val feed = EVENT_FEED.containsMatchIn(c.name)
            // An event feed with its event's time in its name ("PDC BOARD B 01: 19:00 Grand Slam…"): only at that time
            // (from 15 minutes before to 5 hours after), never for the rest of the day. (Peter, 2026-11-17.)
            if (feed && !feedOnAround(c.name, now)) continue
            val channelBare = bare(c.name)
            // Real guide data that says it's live (Peter, 2026-11-17: "look in the EPG for live and actual EPG data"):
            // the live flag, or "Live" in its title / subtitle / synopsis ("Live PL: …", "MNF … ᴸᶦᵛᵉ"). A feed's or
            // channel's name standing in, placeholders ("No Fixtures Today") and all-day blocks never count. Games your
            // Sports schedule knows come in from it on their own (below).
            val ps = ps0.filter { p -> sports.clean(p.title) != null && !fillerFor(channelBare, p) && realSlot(p) && saysLive(p) && (feed || sports.sport(p.title) != null || p.isLive) }
            ps.firstOrNull { it.startTime <= now && it.endTime > now }?.let { onNow += c to it }
            ps.firstOrNull { it.startTime > now }?.let { soon += c to it }
        }
        // The same event on several channels (feeds, backups): once, on the first. A game your Sports schedule knows: left
        // out here (finished: a dead channel; on now or soon: shown from the schedule instead).
        fun scheduled(p: GuideProgram) = known.isNotEmpty() && (" " + GameMatch.norm(p.title) + " ").let { t -> known.any { GameMatch.normTextIsGame(t, it) == GameMatch.SURE } }
        fun distinct(l: List<Pair<ChannelListItem, GuideProgram>>) = l.filter { !scheduled(it.second) }
            .distinctBy { (_, p) -> (sports.clean(p.title) ?: p.title).trim().lowercase() + "|" + p.startTime }
        val nowPairs = distinct(onNow.sortedByDescending { it.second.startTime }).take(30)
        val soonPairs = distinct(soon.sortedBy { it.second.startTime }).take(30)
        // The schedule's games: live ones (score and minute) and the next three hours' kick-offs, once each.
        fun fromSchedule(l: List<LiveGame>) = l.distinctBy { it.game.id }.map { lg ->
            val g = lg.game
            val p = lg.program ?: GuideProgram(
                channelId = lg.channel.id, rowId = -1L - (g.id.hashCode().toLong() and 0x7fffffff), title = g.title, subTitle = g.leagueName,
                packedDescription = null, startTime = g.start, endTime = g.start + 3 * 3_600_000L, season = null, episode = null,
                isNew = false, isLive = true, isPremiere = false,
            )
            val title = if (g.score != null && !g.isEvent) "${g.home} ${g.score} ${g.away}" else g.title
            LiveEvent(lg.channel, p, title, sports.sport("${g.leagueName} ${g.league}"), g.homeLogo)
        }
        val schedNow = fromSchedule(games.filter { it.game.state == "in" })
        val schedSoon = fromSchedule(games.filter { it.game.state == "pre" && it.game.start in now..now + 3 * 3_600_000L }.sortedBy { it.game.start })
        // Each one's sport and picture: the guide's own picture, else the match's from TheSportsDB (a few at a time).
        val gate = Semaphore(6)
        suspend fun events(l: List<Pair<ChannelListItem, GuideProgram>>) = coroutineScope {
            l.mapIndexed { i, (c, p) ->
                async {
                    val title = sports.clean(p.title) ?: p.title
                    // (Pictures from TheSportsDB for the first dozen only: it allows very few requests.)
                    val pic = p.image ?: if (i < 12) gate.withPermit { runCatching { sports.picture(title) }.getOrNull() } else null
                    LiveEvent(c, p, title, sports.sport("$title ${c.name} ${c.groupName.orEmpty()}"), pic)
                }
            }.map { it.await() }
        }
        // Sports you've switched off (Settings → Playback → Live now sports) left out.
        fun wanted(l: List<LiveEvent>) = l.filter { LiveSports.wanted(it.sport?.name) }
        val onNowList = wanted(schedNow + events(nowPairs)).take(30)
        val soonList = wanted(schedSoon + events(soonPairs)).take(30)
        // The Live now group in the guide: only channels whose guide has the event (a team or event channel found from
        // the schedule often has no guide at all, an empty row there. Peter, 2026-11-17); those still show in the
        // shortcut row's cards.
        _liveUuids.value = (onNowList + soonList).filter { it.program.rowId >= 0 }.map { it.channel.uuid }.distinct()
        return LiveEvents(
            onNow = onNowList,
            soon = soonList,
            mainGroup = nowPairs.mapNotNull { it.first.groupName }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
        )
    }
    /** The highlighted programme's artwork (TMDB), or null to show the channel logo. */
    suspend fun artFor(title: String): String? = programArt.imageFor(title)

    /** The programme's title logo from TMDB (same close-name match as its picture), or null. */
    suspend fun logoFor(title: String): String? = programArt.logoFor(title)

    /** The programme's own picture from Dispatcharr (its guide grid sends none), or null. */
    suspend fun pictureFor(p: GuideProgram): String? = sessions.session.first()?.let { repo.lookupImage(it, p) }

    val sync = repo.sync
    /**
     * The Live now group ([LIVE_NOW]): the channels with something live or starting soon (uuids, from [liveEvents]: the
     * Sports schedule, ESPN and the real guide events; filler and finished ones left out). The menu's Live now opens the
     * guide on it. (It opened a whole channel group, empty event slots and all. Peter, 2026-11-17.)
     */
    private val _liveUuids = MutableStateFlow<List<String>>(emptyList())
    // In your order (hold OK on a group in the menu → move); groups not placed yet keep the guide's order after them.
    // (📡 Live now is one of the groups, while anything's on: moved, hidden and shown again like the others, your
    // choice. Peter, 2026-11-17. Out of the box it comes first.)
    val groups = combine(repo.observeGroups(), repo.groupOrder, _liveUuids) { g0, order, live ->
        val g = (if (live.isEmpty()) emptyList() else listOf(GroupWithCount(LIVE_NOW, LIVE_NAME, live.size))) + g0
        if (order.isEmpty()) g else g.withIndex().sortedBy { (i, x) ->
            order.indexOf(x.name).let { if (it >= 0) it else if (x.id == LIVE_NOW) -1 else order.size + i }
        }.map { it.value }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The group to show [ch] in when it isn't in the one on screen: All channels, or (with All channels hidden) its own
     * group. (Switching to All channels while it was hidden was undone at once by the home screen, so typed numbers,
     * search and reminders went nowhere.)
     */
    private fun groupToShow(ch: ChannelListItem): Int? =
        if (AppColors.palette.hideAllChannels) groups.value.firstOrNull { it.name == ch.groupName }?.id else null

    /** Groups hidden from the channel groups list (their channels stay). */
    val menuHiddenGroups = repo.menuHiddenGroups
    fun setGroupMenuHidden(name: String, hidden: Boolean) {
        repo.setGroupMenuHidden(name, hidden)
        // The group on show hidden: back to all channels.
        if (hidden && groups.value.firstOrNull { it.id == _groupId.value }?.name == name) selectGroup(null)
    }

    /** Moves a channel group one place up ([dir] -1) or down (1) in the menu. */
    fun moveGroup(id: Int, dir: Int) {
        // From the order as just changed (not the shown list, a moment behind: a quick second press then moved it
        // back, so the group flicked about).
        val order = repo.groupOrder.value
        val shown = groups.value
        val sorted = if (order.isEmpty()) shown else shown.withIndex().sortedBy { (i, g) -> order.indexOf(g.name).let { if (it < 0) order.size + i else it } }.map { it.value }
        val names = sorted.map { it.name }.toMutableList()
        // Past groups hidden from the list (moving over one did nothing you could see).
        val hiddenNow = repo.menuHiddenGroups.value
        val i = sorted.indexOfFirst { it.id == id }
        var j = i + dir
        while (j in sorted.indices && sorted[j].name in hiddenNow) j += dir
        if (i < 0 || j !in names.indices) return
        Collections.swap(names, i, j)
        repo.setGroupOrder(names)
    }

    private val _groupId = MutableStateFlow<Int?>(null)
    val groupId = _groupId.asStateFlow()

    /** Favourite channels (uuids); the ★ Favourites group is [FAVOURITES]. */
    val favourites = repo.favourites
    fun toggleFavourite(ch: ChannelListItem): Boolean = repo.toggleFavourite(ch.uuid)

    // Guide time offset (see GuideRepository): the source's, and a channel's own (by its guide).
    val shiftVersion = repo.shiftVersion
    fun sourceShiftMin(): Int = repo.sourceShiftMin()
    // (Slides the saved programmes: no guide download. Peter, 2026-11-17.)
    fun setSourceShiftMin(min: Int) { viewModelScope.launch { repo.setSourceShiftMin(min) } }
    fun channelShiftMin(ch: ChannelListItem): Int = repo.channelShiftMin(ch.guideKey)
    fun setChannelShiftMin(ch: ChannelListItem, min: Int) { viewModelScope.launch { repo.setChannelShiftMin(ch.guideKey, min) } }
    /** The channel's whole offset (source + its own + its playlist's tvg-shift), for the mark in the channel list. */
    fun totalShiftMin(ch: ChannelListItem): Int = repo.totalShiftMin(ch.guideKey)

    /** How many channels the Live now group has (none: it isn't in the groups). */
    val liveNowCount get() = _liveUuids.value.size
    /** A list-made group's channels (Favourites, Live now), else null (a real group). */
    private val listGroups = combine(repo.favourites, _liveUuids) { f, l -> f.toList() to l }
    private fun listFor(g: Int?, lists: Pair<List<String>, List<String>>) = when (g) { FAVOURITES -> lists.first; LIVE_NOW -> lists.second; else -> null }

    val channels: StateFlow<List<ChannelListItem>> = combine(_groupId, listGroups) { g, l -> g to l }
        .flatMapLatest { (g, l) -> listFor(g, l)?.let { repo.observeChannelsIn(it) } ?: repo.observeChannels(g) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now = _now.asStateFlow()

    private val _cursor = MutableStateFlow(initialCursor(_now.value))
    val cursor = _cursor.asStateFlow()

    val programs: StateFlow<Map<Int, List<GuideProgram>>> = combine(
        _groupId,
        _cursor.map { it.row / BLOCK }.distinctUntilChanged(),
        _cursor.map { it.windowStart }.distinctUntilChanged(),
        listGroups,
        // (A longer timeline in Settings: the programmes for the whole of it.)
        snapshotFlow { VISIBLE_MS }.distinctUntilChanged(),
    ) { g, block, ws, lists, _ -> listOf(g, block, ws, lists) }
        .flatMapLatest { l ->
            val g = l[0] as Int?; val block = l[1] as Int; val ws = l[2] as Long
            @Suppress("UNCHECKED_CAST") val inList = listFor(g, l[3] as Pair<List<String>, List<String>>)
            (if (inList != null) repo.observeProgramBlockIn(inList, blockOffset(block), BLOCK * 3, ws - BUFFER_MS, ws + VISIBLE_MS + BUFFER_MS)
            else repo.observeProgramBlock(
                groupId = g,
                offset = blockOffset(block),
                count = BLOCK * 3,
                start = ws - BUFFER_MS,
                end = ws + VISIBLE_MS + BUFFER_MS,
            )).map { rows -> rows.groupBy { it.channelId } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val fetchedPast = HashSet<String>()

    init {
        // A shorter list (the last favourite removed, a smaller group): the highlight stays on its last channel rather
        // than past the end (nothing highlighted, a blank info panel).
        viewModelScope.launch {
            channels.collect { list -> if (list.isNotEmpty() && _cursor.value.row > list.lastIndex) _cursor.update { it.copy(row = list.lastIndex) } }
        }
        viewModelScope.launch {
            // collectLatest: a new source (another server, a playlist) cancels the old one's sync, which otherwise
            // held the guide until it finished (its 45 MB guide) and the new one never showed until a rescan.
            // Then checked every 15 minutes while Viewarr is open, so a guide left running is fetched again on time
            // (Settings → Live TV → TV guide refresh).
            sessions.session.filterNotNull().distinctUntilChanged().collectLatest { s ->
                while (true) {
                    repo.syncIfStale(s, AppColors.palette.guideRefreshMs)
                    delay(15 * 60_000L)
                }
            }
        }
        viewModelScope.launch {
            repo.generation.collect {
                fetchedPast.clear()
                ensurePast()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val previous = _now.value
                val t = System.currentTimeMillis()
                _now.value = t
                // Follow live TV only if the user was looking at "now"; leave past/future browsing alone.
                _cursor.update { c ->
                    if (c.focusTime in previous..t) c.copy(focusTime = t, windowStart = maxOf(c.windowStart, floorToStep(t - STEP_MS)))
                    else c
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { sessions.session.first()?.let { repo.sync(it) } }
    }

    fun selectGroup(id: Int?) {
        _groupId.value = id
        _cursor.value = initialCursor(System.currentTimeMillis())
    }

    /**
     * Up / Down a row ([delta] rows; a page with the channel buttons). Past the last channel or above the first
     * (Settings → Live TV → TV guide → End of a list): [ends] 0 loops on round, 1 jumps to the other end on a [fresh] press
     * (holding the button stops at the end), 2 stops.
     */
    fun moveVertical(delta: Int, ends: Int = 2, fresh: Boolean = true): Boolean {
        val count = channels.value.size
        if (count == 0) return false
        val before = _cursor.value.row
        _cursor.update { c ->
            val to = c.row + delta
            val row = when {
                to in 0 until count -> to
                ends == 0 -> ((to % count) + count) % count
                ends == 1 && fresh && (c.row == 0 || c.row == count - 1) -> if (to < 0) count - 1 else 0
                else -> to.coerceIn(0, count - 1)
            }
            c.copy(row = row)
        }
        ensurePast()
        return _cursor.value.row != before
    }

    fun moveRight() {
        val c = _cursor.value
        val list = rowPrograms(c.row)
        val current = programAt(list, c.focusTime)
        val next = if (current != null) list.firstOrNull { it.startTime >= current.endTime }
        else list.firstOrNull { it.startTime > c.focusTime }
        val target = next?.startTime ?: (c.focusTime + STEP_MS)
        if (target > _now.value + maxForwardMs()) return
        var ws = c.windowStart
        while (target >= ws + VISIBLE_MS - STEP_MS) ws += STEP_MS
        _cursor.value = c.copy(focusTime = target, windowStart = ws)
    }

    /** Back one programme. False at the start: the channel's catch-up limit, or its earliest programme without it. */
    fun moveLeft(): Boolean {
        val c = _cursor.value
        val list = rowPrograms(c.row)
        val current = programAt(list, c.focusTime)
        val prev = if (current != null) list.lastOrNull { it.endTime <= current.startTime }
        else list.lastOrNull { it.endTime <= c.focusTime }
        val now = _now.value
        val days = channels.value.getOrNull(c.row)?.catchupDays ?: 0
        val earliest = if (days > 0) maxOf(now - days * 86_400_000L, now - MAX_PAST_MS)
        else minOf(list.firstOrNull()?.startTime ?: now, now).coerceAtLeast(now - MAX_PAST_MS)
        val target = maxOf(prev?.startTime ?: (c.focusTime - STEP_MS), earliest)
        if (target >= c.focusTime) return false
        var ws = c.windowStart
        while (target < ws) ws -= STEP_MS
        _cursor.value = c.copy(focusTime = target, windowStart = ws)
        ensurePast()
        return true
    }

    /** Jumps the guide [deltaMs] (a day: the colour buttons), keeping the channel; stops at the guide's limits. */
    fun jumpBy(deltaMs: Long) {
        val c = _cursor.value
        val now = _now.value
        // A whole day: the same time of day on the next / previous date (24 h is an hour off on clock-change days).
        val target = if (deltaMs == DAY_MS || deltaMs == -DAY_MS) {
            val z = Instant.ofEpochMilli(c.focusTime).atZone(ZoneId.systemDefault())
            (if (deltaMs > 0) z.plusDays(1) else z.minusDays(1)).toInstant().toEpochMilli()
        } else c.focusTime + deltaMs
        val t = target.coerceIn(now - MAX_PAST_MS, now + maxForwardMs())
        if (t == c.focusTime) return
        _cursor.value = c.copy(focusTime = t, windowStart = floorToStep(t))
        ensurePast()
    }

    fun isAtNow(): Boolean {
        val c = _cursor.value
        return c.focusTime in _now.value - 60_000.._now.value + 60_000 || programAt(rowPrograms(c.row), c.focusTime)?.let {
            it.startTime <= _now.value && it.endTime > _now.value
        } == true
    }

    /** Moves the guide to a channel (used when zapping in the player), at live time. */
    fun selectChannel(channelId: Int) {
        val index = channels.value.indexOfFirst { it.id == channelId }
        if (index < 0) return
        val t = System.currentTimeMillis()
        _cursor.value = GuideCursor(row = index, focusTime = t, windowStart = floorToStep(t))
    }

    /**
     * Finds a channel by its number (typed on the remote). Looks in the current category first, then in all
     * channels; if it's in another category the guide switches to All channels so it can be shown.
     */
    fun findByNumber(number: Int, onResult: (ChannelListItem?) -> Unit) {
        viewModelScope.launch {
            val wanted = number.toDouble()
            channels.value.firstOrNull { it.number == wanted }?.let { return@launch onResult(it) }
            val hit = repo.observeChannels(null).first().firstOrNull { it.number == wanted }
            if (hit != null && channels.value.none { it.id == hit.id }) {
                _groupId.value = groupToShow(hit)
                withTimeoutOrNull(3_000) { channels.first { list -> list.any { it.id == hit.id } } }
            }
            onResult(hit)
        }
    }

    suspend fun channelById(id: Int): ChannelListItem? = allChannelsById()[id]

    /** A channel's programmes: back as far as its catch-up goes (else just earlier today), and the next day ahead. */
    suspend fun programsFor(ch: ChannelListItem): List<GuideProgram> {
        val now = System.currentTimeMillis()
        // Earlier today at least (from midnight, or 6 hours if that's longer), more for catch-up channels.
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val back = maxOf(now - midnight, 6 * 3_600_000L, ch.catchupDays.coerceAtMost(2) * 86_400_000L)
        val from = now - back
        // Past programmes before the last full sync aren't stored until asked for: fetch them first.
        val syncedFrom = repo.syncedFrom()
        if (from < syncedFrom && ch.guideKey != ch.uuid) {
            sessions.session.first()?.let { s ->
                // Don't keep the panel on "Loading…" if the server is slow: show what's stored after a few seconds.
                // (The fetches carry on in the background, so the guide gets them even if the panel stops waiting.)
                val jobs = ArrayList<Job>()
                var chunk = Math.floorDiv(from, CHUNK_MS) * CHUNK_MS
                while (chunk < syncedFrom) {
                    val c = chunk
                    if (fetchedPast.add("${ch.guideKey}|$c")) jobs += viewModelScope.launch { repo.loadPastChunk(s, ch.guideKey, c, c + CHUNK_MS) }
                    chunk += CHUNK_MS
                }
                withTimeoutOrNull(8_000) { jobs.forEach { it.join() } }
            }
        }
        return repo.programsFor(ch.id, ch.guideKey, from, now + 30 * 3_600_000L)
    }

    // A game's channel, remembered for ten minutes (the Sports rows ask each time the live TV controls open).
    private val gameChannels = ConcurrentHashMap<String, Pair<Long, Pair<ChannelListItem, GuideProgram>?>>()
    // Every channel (all groups): one copy, kept up to date by the database (a guide sync, another Live TV source), for
    // the things that look across all of them: search as you type, Live now each minute, a channel by its id. Each
    // used to load the whole list again every time.
    private val allChannels = repo.observeChannels(null).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private suspend fun allChannelsList(): List<ChannelListItem> = allChannels.value.ifEmpty { repo.observeChannels(null).first() }
    private var byIdOf: Pair<List<ChannelListItem>, Map<Int, ChannelListItem>>? = null
    private suspend fun allChannelsById(): Map<Int, ChannelListItem> {
        val list = allChannelsList()
        byIdOf?.takeIf { it.first === list }?.let { return it.second }
        return list.associateBy { it.id }.also { byIdOf = list to it }
    }

    /** A televised UK match from the UK Sport on TV plugin (Dispatcharr): when, who, and which channels. */
    private data class UkMatch(val start: Long, val home: String, val away: String, val channels: List<String>)
    private var ukList: Pair<Long, List<UkMatch>?>? = null

    /**
     * The UK Sport on TV plugin's listings, if it's installed and on (read every 30 minutes). Null: not there (or this
     * profile can't read plugins: only Dispatcharr admins can).
     */
    private suspend fun ukListings(): List<UkMatch>? {
        ukList?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.let { return it.second }
        val list = runCatching {
            val p = plugins.installed()?.firstOrNull { it.key == "uk_sport_on_tv" && it.enabled } ?: return@runCatching null
            val raw = (p.settings["listings"] as? JsonPrimitive)?.content ?: return@runCatching null
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
                val a = e.jsonArray
                UkMatch(
                    a[0].jsonPrimitive.long, a[1].jsonPrimitive.content, a[2].jsonPrimitive.content,
                    a[4].jsonArray.map { it.jsonPrimitive.content },
                )
            }
        }.getOrNull()
        ukList = System.currentTimeMillis() to list
        return list
    }

    /**
     * What the UK listings say about a game: the channels, how surely the listing is this game ([GameMatch.SURE]:
     * both teams; POSSIBLE: one by name), and the listing itself ("Celtic v St Mirren, Scottish Premiership").
     */
    data class UkResult(val channels: List<String>, val sureness: Int, val listing: String?, val source: String = "UK TV listings")

    /**
     * Where a game your guide doesn't list is on: Sky's TV guide first (about a week ahead), then the UK Sport on TV
     * plugin's listings (football, weeks ahead), else TheSportsDB. Null: no answer yet.
     */
    suspend fun ukMatch(game: SportGame): UkResult? {
        // 1: Sky's own TV guide (Viewarr's: no plugin needed), about a week ahead: both teams in a programme around
        // kick-off.
        val skyFound = runCatching { sky.find(game) }.getOrNull()
        if (skyFound != null && skyFound.channels.isNotEmpty())
            return UkResult(skyFound.channels, GameMatch.SURE, "${skyFound.title} (${Dates.time(skyFound.start)})", source = "Sky TV guide")
        // 2: the UK Sport on TV plugin's listings, if it's there (weeks ahead, Amazon, club TV).
        ukListings()?.let { list ->
            // Same kick-off (within half an hour), then the best by GameMatch (both teams, either way round).
            val m = list.filter { abs(it.start - game.start) <= 30 * 60_000L }
                .map { it to GameMatch.teamsAreGame(it.home, it.away, game) }
                .filter { it.second != GameMatch.NO }.maxByOrNull { it.second }
            if (m != null) return UkResult(m.first.channels, m.second, "${m.first.home} v ${m.first.away}", source = "UK TV listings")
            // Listed, and not there: not on UK TV (a Saturday 3pm game, say). The plugin's listings cover football only,
            // the coming weeks only.
            if (game.league.startsWith("soccer/") && list.any { it.start >= game.start }) return UkResult(listOf(NOT_ON_UK_TV), GameMatch.SURE, null, source = "UK TV listings")
        }
        // In Sky's range and not there: an English / Scottish league game at 3pm on a Saturday is the blackout (never on
        // UK TV); anything else may be on a channel Sky's guide doesn't carry (Amazon, club TV).
        if (skyFound != null && blackout(game)) return UkResult(listOf(NOT_ON_UK_TV), GameMatch.SURE, null, source = "Sky TV guide")
        // 3: TheSportsDB.
        return runCatching { sports.ukChannels(game.home, game.away, game.start) }.getOrNull()?.let { UkResult(it, GameMatch.SURE, "TheSportsDB", source = "TheSportsDB") }
    }

    /** An English / Scottish league game kicking off between 14:45 and 15:15 UK time on a Saturday (not shown on UK TV). */
    private fun blackout(g: SportGame): Boolean {
        val l = g.league
        val league = l.startsWith("soccer/eng.") && l.removePrefix("soccer/eng.").firstOrNull()?.isDigit() == true ||
            l.startsWith("soccer/sco.") && l.removePrefix("soccer/sco.").firstOrNull()?.isDigit() == true
        if (!league) return false
        val t = Instant.ofEpochMilli(g.start).atZone(ZoneId.of("Europe/London"))
        val mins = t.hour * 60 + t.minute
        return t.dayOfWeek == DayOfWeek.SATURDAY && mins in (14 * 60 + 45)..(15 * 60 + 15)
    }

    /** Just the channel list again (viewarr://refresh, or a link to a channel not known yet). */
    suspend fun refreshChannels() { sessions.session.first()?.let { runCatching { repo.syncChannels(it) } } }
    /** The whole guide again (viewarr://refresh?guide=1). */
    suspend fun refreshGuide() { sessions.session.first()?.let { runCatching { repo.sync(it) } } }

    /** The guide fetched again if it's older than [maxAgeMs] (Sports: a game on now whose event channel is newer). */
    suspend fun syncIfOlderThan(maxAgeMs: Long) {
        sessions.session.first()?.let { runCatching { repo.syncIfStale(it, maxAgeMs) } }
        gameChannels.clear()
    }

    /**
     * Sports hub: the channel showing [game], from the guide: a programme naming both sides (GameMatch), from 3 hours
     * before kick-off to an hour after. Null if none is listed yet.
     */
    suspend fun channelForGame(game: SportGame): Pair<ChannelListItem, GuideProgram>? {
        val t = System.currentTimeMillis()
        gameChannels[game.id + "|" + game.start]?.takeIf { t - it.first < 10 * 60_000L }?.let { return it.second }
        // The shows around kick-off (starting up to 3 h before to 1 h after; so ending by 7 h after) with either team's
        // telling word, then only those naming both teams (GameMatch: ESPN's names for them, whole words). One team
        // alone isn't enough here: a club's channel shows its name all day.
        val channels = allChannelsById()
        val found = GameMatch.searchWords(game).flatMap { w ->
            runCatching { repo.searchProgramsEnding(w, game.start - 3 * 3_600_000L, game.start + 7 * 3_600_000L) }.getOrNull().orEmpty()
        }.distinctBy { it.channelId to it.startTime }.mapNotNull { p -> channels[p.channelId]?.let { it to p } }
        val best = found.filter { (_, p) ->
            p.startTime in (game.start - 3 * 3_600_000L)..(game.start + 3_600_000L) &&
                GameMatch.textIsGame(p.title + " " + p.subTitle.orEmpty(), game) == GameMatch.SURE // (a few dozen at most)
        }.minByOrNull { (_, p) -> abs(p.startTime - game.start) }
        gameChannels[game.id + "|" + game.start] = t to best
        return best
    }

    /** Search: what's on now and coming up whose title matches, with its channel (one per showing). */
    suspend fun searchPrograms(q: String): List<Pair<ChannelListItem, GuideProgram>> {
        val t = q.trim()
        if (t.length < 2) return emptyList()
        val channels = allChannelsById()
        return repo.searchPrograms(t, System.currentTimeMillis(), 120)
            .mapNotNull { p -> channels[p.channelId]?.let { it to p } }
            // The same showing on two copies of a channel: keep one.
            .distinctBy { (c, p) -> Triple(c.name.lowercase(), p.title, p.startTime) }
            .take(40)
    }

    /**
     * Search → Catch-up: past programmes whose title matches, on channels with catch-up and still inside their
     * catch-up window, newest first (one per showing).
     */
    suspend fun searchCatchup(q: String): List<Pair<ChannelListItem, GuideProgram>> {
        val t = q.trim()
        if (t.length < 2) return emptyList()
        val session = sessions.session.first() ?: return emptyList()
        val all = allChannelsList().filter { it.catchupDays > 0 }
        if (all.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val from = now - all.maxOf { it.catchupDays } * 86_400_000L
        val byKey = all.groupBy { it.guideKey }
        val byId = all.associateBy { it.id }
        return repo.searchPast(session, t, from, now)
            .flatMap { (key, p) ->
                val chs = if (key != null) byKey[key].orEmpty() else listOfNotNull(byId[p.channelId])
                chs.filter { p.startTime >= now - it.catchupDays * 86_400_000L }.map { it to p.copy(channelId = it.id) }
            }
            // The same showing on two copies of a channel: keep one.
            .distinctBy { (c, p) -> Triple(c.name.lowercase(), p.title, p.startTime) }
            .take(40)
    }

    /** Channels whose name or number matches a search, across all categories. */
    suspend fun searchChannels(q: String): List<ChannelListItem> {
        val t = q.trim()
        if (t.length < 2) return emptyList()
        return allChannelsList().filter { c ->
            c.name.contains(t, ignoreCase = true) || c.number?.let { n -> (if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()) == t } == true
        }.take(30)
    }

    /**
     * Back to the guide from full screen: the channel playing, at now, in the group it was started from ([groupId];
     * All channels when it isn't there), whatever group was browsed since.
     */
    fun returnTo(channelId: Int, groupId: Int?) {
        viewModelScope.launch {
            if (_groupId.value != groupId) {
                _groupId.value = groupId
                withTimeoutOrNull(3_000) { channels.first { list -> list.any { it.id == channelId } } }
            }
            if (channels.value.none { it.id == channelId }) {
                _groupId.value = repo.observeChannels(null).first().firstOrNull { it.id == channelId }?.let(::groupToShow)
                withTimeoutOrNull(3_000) { channels.first { list -> list.any { it.id == channelId } } }
            }
            selectChannel(channelId)
        }
    }

    /** Makes a channel tunable from anywhere: switches to All channels (or its group) if it isn't in the current one. */
    fun reveal(channel: ChannelListItem, onReady: () -> Unit) {
        viewModelScope.launch {
            if (channels.value.none { it.id == channel.id }) {
                _groupId.value = groupToShow(channel)
                withTimeoutOrNull(3_000) { channels.first { list -> list.any { it.id == channel.id } } }
            }
            onReady()
        }
    }

    /** Jump back to live TV on the current channel. */
    fun goToNow() {
        val t = System.currentTimeMillis()
        _cursor.update { it.copy(focusTime = t, windowStart = floorToStep(t)) }
    }

    private fun ensurePast() {
        val c = _cursor.value
        val syncedFrom = repo.syncedFrom()
        val wantFrom = c.windowStart - BUFFER_MS
        if (wantFrom >= syncedFrom) return
        viewModelScope.launch {
            val s = sessions.session.first() ?: return@launch
            val all = channels.value
            // (The cursor can point past a shorter list after a group change: kept inside it.)
            val from = (c.row - BLOCK).coerceIn(0, all.size)
            val keys = all.subList(from, minOf(all.size, from + BLOCK * 3))
                .filter { it.guideKey != it.uuid }
                .map { it.guideKey }
                .toSet()
            var chunk = Math.floorDiv(wantFrom, CHUNK_MS) * CHUNK_MS
            while (chunk < syncedFrom) {
                val chunkStart = chunk
                for (key in keys) {
                    if (!fetchedPast.add("$key|$chunkStart")) continue
                    launch { repo.loadPastChunk(s, key, chunkStart, chunkStart + CHUNK_MS) }
                }
                chunk += CHUNK_MS
            }
        }
    }

    private fun rowPrograms(row: Int): List<GuideProgram> {
        val channel = channels.value.getOrNull(row) ?: return emptyList()
        return programs.value[channel.id].orEmpty()
    }

    companion object {
        /** [ukMatch]: in the UK listings' range, and not in them (not on UK TV). */
        const val NOT_ON_UK_TV = "\u0000not on UK TV"
        const val STEP_MS = 30 * 60_000L
        /** How much of the timeline is on screen: Settings → Appearance → TV guide timeline (2½ hours as standard). */
        val VISIBLE_MS: Long get() = when (AppColors.palette.opt(UiOpts.GUIDE_SPAN)) {
            1 -> 90; 2 -> 120; 3 -> 180; 4 -> 240; 5 -> 360; else -> 150
        } * 60_000L
        private const val BUFFER_MS = 90 * 60_000L
        /** A couple of hours short of what's fetched, so the last programmes aren't cut off. */
        private fun maxForwardMs() = AppColors.palette.guideForwardMs - 2 * 3_600_000L
        /** The ★ Favourites group's id (real groups are positive). */
        const val FAVOURITES = -2
        /** The Live now group: the channels with something live now or soon (see [liveEvents]). */
        const val LIVE_NOW = -3
        const val LIVE_NAME = "📡  Live now"
        private const val MAX_PAST_MS = 3 * 24 * 3_600_000L
        const val DAY_MS = 86_400_000L
        private const val CHUNK_MS = 6 * 3_600_000L
        private const val BLOCK = 10

        fun floorToStep(t: Long) = t - Math.floorMod(t, STEP_MS)

        private fun blockOffset(block: Int) = maxOf(0, (block - 1) * BLOCK)

        private fun initialCursor(now: Long) = GuideCursor(row = 0, focusTime = now, windowStart = floorToStep(now))

        fun programAt(list: List<GuideProgram>, time: Long): GuideProgram? =
            list.firstOrNull { it.startTime <= time && it.endTime > time }
    }
}
