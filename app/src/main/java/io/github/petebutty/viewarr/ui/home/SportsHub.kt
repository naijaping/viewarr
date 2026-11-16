package io.github.petebutty.viewarr.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import io.github.petebutty.viewarr.data.local.ChannelListItem
import io.github.petebutty.viewarr.data.local.GuideProgram
import io.github.petebutty.viewarr.data.media.ServiceLogo
import io.github.petebutty.viewarr.data.sports.SportFollow
import io.github.petebutty.viewarr.data.sports.SportGame
import io.github.petebutty.viewarr.ui.common.AppIcons
import io.github.petebutty.viewarr.ui.dvr.DvrViewModel
import io.github.petebutty.viewarr.ui.guide.GuideViewModel
import io.github.petebutty.viewarr.ui.player.CardOption
import io.github.petebutty.viewarr.ui.player.LiveShortcut
import io.github.petebutty.viewarr.ui.player.OsdPoster
import io.github.petebutty.viewarr.ui.player.OsdRow
import io.github.petebutty.viewarr.ui.player.ShortcutColours
import io.github.petebutty.viewarr.ui.player.ShortcutLogos
import io.github.petebutty.viewarr.ui.settings.SportsViewModel
import io.github.petebutty.viewarr.ui.theme.Dates
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

/**
 * The Sports hub (Settings → Sports) on live TV: your teams' and leagues' games, which of your channels shows each
 * (from the guide; else the UK listings), and the cards, rows and pinned shortcuts built from them.
 *
 * [watch]: play a channel full screen; [toast]: a short message.
 */
internal class SportsHub(
    internal val sportsVm: SportsViewModel,
    private val guide: GuideViewModel,
    internal val dvr: DvrViewModel,
    internal val watch: (ChannelListItem) -> Unit,
    internal val toast: (String) -> Unit,
) {
    /** Each followed team's / league's games, with the channel showing it (from the guide) where one's found. */
    val games = mutableStateMapOf<String, List<Pair<SportGame, Pair<ChannelListItem, GuideProgram>?>>>()
    /** The game whose page is open (OK on a game), with its channel if found; null: none. */
    var openGame by androidx.compose.runtime.mutableStateOf<Pair<SportGame, Pair<ChannelListItem, GuideProgram>?>?>(null)

    /**
     * Reminders / recordings asked for before [g] was in the guide, and "record all of a team's games": done now that
     * its channel is known (each once).
     */
    fun resolvePending(g: SportGame, ch: Pair<ChannelListItem, GuideProgram>) {
        val gw = sportsVm.gameWatch
        if (g.state != "pre" || ch.second.startTime < System.currentTimeMillis()) return
        val remind = gw.wantsReminder(g.id)
        val team = gw.recordsTeam(g.home) || gw.recordsTeam(g.away)
        // Already recording in Dispatcharr (by hand, a series rule, or before): never a second recording.
        val record = (gw.wantsRecording(g.id) || (team && !gw.done(g.id))) && dvr.recordingFor(ch.first.id, ch.second) == null
        if (!remind && !record) { if (gw.wantsRecording(g.id)) gw.resolved(g.id); return }
        if (remind && dvr.reminderFor(ch.first.id, ch.second) == null) { dvr.remind(ch.first, ch.second); toast("Reminder set: ${g.title} on ${ch.first.name}") }
        if (record) { dvr.record(ch.first, ch.second); toast("Recording ${g.title} on ${ch.first.name}") }
        gw.resolved(g.id)
    }

    /** Games your guide doesn't reach yet: the channels showing them (the UK Sport on TV plugin, else TheSportsDB). */
    val tv = mutableStateMapOf<String, List<String>>()

    /** When each team / league was last fetched (opening Sports again within a few minutes uses what's there). */
    private val loadedAt = HashMap<String, Long>()

    /**
     * Fetches every followed team's / league's games and where they're on. The games show as soon as they arrive;
     * their channels (a guide search each, and the UK listings) fill in one by one after.
     */
    /** Games already looked for in the guide / the listings (so going back to Sports carries on, not starts again). */
    private val lookedUp = HashSet<String>()
    /** When the guide was last fetched again for a game with no channel (step 1b). */
    private var lastFreshen = 0L
    private var running = false

    suspend fun load(follows: List<SportFollow>) {
        if (running) return
        running = true
        try {
            val t0 = System.currentTimeMillis()
            // 1: every team's / league's games (small requests to ESPN), shown as soon as they're in.
            coroutineScope {
                follows.map { f ->
                    f.logo?.let { ShortcutLogos.map["sport:${f.shortcutId}"] = ServiceLogo(it, square = true) }
                    async {
                        if (t0 - (loadedAt[f.shortcutId] ?: 0L) < 5 * 60_000L && games.containsKey(f.shortcutId)) return@async
                        val all = runCatching { sportsVm.games(f) }.getOrDefault(emptyList())
                        // On now, the next few (two months), the last few results; one of each game.
                        val shown = (all.filter { it.state == "in" } + all.filter { it.state == "pre" && it.start < t0 + 60 * DAY }.take(10) +
                            all.filter { it.state == "post" }.takeLast(5)).distinctBy { it.id }
                        val before = games[f.shortcutId].orEmpty().associate { it.first.id to it.second }
                        games[f.shortcutId] = shown.map { g -> g to before[g.id] }
                        loadedAt[f.shortcutId] = t0
                    }
                }.forEach { it.await() }
            }
            // 1b: a game on now (or within 3 hours) with no channel found, and the guide on this box older than half an
            // hour: event channels are named after the match only shortly before (the EPG & Sports Editor plugin), after
            // the box last fetched the guide. Fetched again (at most every half hour), and those games looked for again.
            val missing = follows.flatMap { games[it.shortcutId].orEmpty() }
                .filter { (g, ch) -> ch == null && (g.state == "in" || (g.state == "pre" && g.start < t0 + 3 * 3_600_000L)) }
                .map { it.first.id }.toSet()
            if (missing.isNotEmpty() && t0 - lastFreshen > 30 * 60_000L) {
                lastFreshen = t0
                guide.syncIfOlderThan(30 * 60_000L)
                lookedUp.removeAll(missing)
            }
            // Games already matched to a channel: any reminder / recording waiting for them.
            follows.flatMap { games[it.shortcutId].orEmpty() }.forEach { (g, ch) -> if (ch != null) resolvePending(g, ch) }
            // 2: where each is on, one game at a time with a pause between (a guide search each, and the UK listings, all
            // at once made a small box stop responding). Soonest first; games already looked for skipped.
            val todo = follows.flatMap { games[it.shortcutId].orEmpty() }
                .filter { (g, ch) -> ch == null && g.state != "post" && g.id !in lookedUp }
                .map { it.first }.distinctBy { it.id }.sortedBy { it.start }
            for (g in todo) {
                lookedUp += g.id
                if (g.start < t0 + 7 * DAY) {
                    val ch = runCatching { guide.channelForGame(g) }.getOrNull()
                    if (ch != null) {
                        resolvePending(g, ch)
                        games.keys.toList().forEach { k ->
                            if (games[k].orEmpty().any { it.first.id == g.id }) games[k] = games[k].orEmpty().map { if (it.first.id == g.id) g to ch else it }
                        }
                        kotlinx.coroutines.delay(150)
                        continue
                    }
                }
                // Not in the guide (yet): who's showing it (the UK Sport on TV plugin, else TheSportsDB).
                if (g.state == "pre" && g.start < t0 + 60 * DAY && g.id !in tv) runCatching { guide.ukChannels(g) }.getOrNull()?.let { tv[g.id] = it }
                kotlinx.coroutines.delay(150)
            }
        } finally {
            running = false
        }
    }

    /**
     * A game's card (live, coming up, or a result) and what OK does on it; Sports' rows and a team's rows share it.
     * [f]: the team / league whose row it's in (hold OK: pin or unpin it).
     */
    fun card(g: SportGame, ch: Pair<ChannelListItem, GuideProgram>?, f: SportFollow? = null): OsdPoster {
        val tvRaw = tv[g.id].orEmpty()
        // (The UK listings say it isn't on UK TV, e.g. a Saturday 3pm game.)
        val notOnTv = tvRaw == listOf(GuideViewModel.NOT_ON_UK_TV)
        val on = if (notOnTv) emptyList() else tvRaw
        val started = ch != null && (g.state == "in" || ch.second.startTime <= System.currentTimeMillis())
        // Hold OK: a reminder or a recording (once it's in your guide), and pin / unpin the team or league.
        val gw = sportsVm.gameWatch
        val pagesOn = io.github.petebutty.viewarr.ui.theme.AppColors.palette.opt(io.github.petebutty.viewarr.ui.theme.UiOpts.SPORTS_GAME_PAGE) == 0
        val options = listOfNotNull(
            if (pagesOn) CardOption("Game info") { openGame = g to ch } else null,
            // Not in the guide yet: kept, and done once the guide lists it.
            if (ch == null && g.state == "pre") CardOption(if (gw.wantsReminder(g.id)) "Cancel reminder" else "Remind me when it's on") {
                val on = !gw.wantsReminder(g.id); gw.setReminder(g.id, on)
                toast(if (on) "We'll remind you of ${g.title} once it's in your TV guide" else "Reminder cancelled")
            } else null,
            if (ch == null && g.state == "pre") CardOption(if (gw.wantsRecording(g.id)) "Don't record" else "Record when it's in the guide") {
                val on = !gw.wantsRecording(g.id); gw.setRecording(g.id, on)
                toast(if (on) "${g.title} will be recorded once it's in your TV guide" else "Won't record ${g.title}")
            } else null,
            if (ch != null && !started) CardOption("Remind me") {
                dvr.remind(ch.first, ch.second); toast("We'll remind you: ${g.title} on ${ch.first.name}")
            } else null,
            if (ch != null && !started) CardOption("Record") {
                dvr.record(ch.first, ch.second); gw.markDone(g.id); toast("Recording ${g.title} on ${ch.first.name}")
            } else null,
            f?.let { fl ->
                CardOption(if (fl.pinned) "Unpin ${fl.name}" else "Pin ${fl.name}: its own shortcut") {
                    sportsVm.setPinned(fl, !fl.pinned); toast(if (fl.pinned) "${fl.name}: back under Sports" else "${fl.name}: pinned to your shortcuts")
                }
            },
        )
        return OsdPoster(
            key = "sg-${g.id}",
            title = if (g.score != null) "${g.home} ${g.score} ${g.away}" else g.title,
            subtitle = when (g.state) {
                "in" -> "Live" + (g.detail?.let { " $it" } ?: "") + "  ·  " + (ch?.first?.name ?: on.firstOrNull() ?: "not in your guide")
                "post" -> "Full time  ·  " + Dates.dayTime(g.start)
                else -> Dates.dayTime(g.start) + "  ·  " + (ch?.first?.name ?: on.joinToString(", ").ifEmpty { if (notOnTv) "not on UK TV" else "not in your guide yet" })
            },
            image = g.homeLogo, logo = true,
            emoji = if (g.homeLogo == null) sportsVm.league(g.league).emoji else null, tint = ShortcutColours.sports,
            options = options,
        ) {
            when {
                // On now (or its programme has started): watch it.
                started && ch != null -> watch(ch.first)
                // Settings → Sports → Game page: the game's page (line-ups, form, remind / record).
                pagesOn -> openGame = g to ch
                ch != null -> toast("${g.title}: ${Dates.dayTime(g.start)} on ${ch.first.name}  ·  hold OK to be reminded or record it")
                g.state == "post" -> toast("${g.home} ${g.score ?: ""} ${g.away}")
                on.isNotEmpty() -> toast("${g.title}: on ${on.joinToString(", ")}, not in your TV guide yet (it lists a few days ahead)")
                notOnTv -> toast("${g.title}: not shown on UK TV (UK TV listings)")
                else -> toast("${g.title}: not in your TV guide yet (the guide lists a few days ahead)")
            }
        }
    }

    /** A pinned team's / league's own rows: On now, Next games, Results. */
    fun gamesRows(key: String, follows: List<SportFollow>): List<OsdRow> {
        val list = games[key].orEmpty()
        val fol = follows.firstOrNull { it.shortcutId == key }
        return listOf(
            OsdRow("On now", list.filter { it.first.state == "in" }.map { (g, c) -> card(g, c, fol) }),
            OsdRow("Next games", list.filter { it.first.state == "pre" }.map { (g, c) -> card(g, c, fol) }),
            OsdRow("Results", list.filter { it.first.state == "post" }.reversed().map { (g, c) -> card(g, c, fol) }),
        ).filter { it.items.isNotEmpty() }
    }

    /**
     * The Sports shortcut's rows (like a network's): what's on now across everything you follow, then a row per team
     * and league: its games on now and coming up, then its latest results.
     */
    fun rows(follows: List<SportFollow>): List<OsdRow> {
        val onNow = follows.flatMap { games[it.shortcutId].orEmpty() }.filter { it.first.state == "in" }.distinctBy { it.first.id }
        return (listOf(OsdRow("On now", onNow.map { (g, c) -> card(g, c) })) +
            follows.map { f ->
                val list = games[f.shortcutId].orEmpty()
                val ordered = list.filter { it.first.state != "post" } + list.filter { it.first.state == "post" }.reversed().take(3)
                OsdRow(f.name, ordered.map { (g, c) -> card(g, c, f) })
            }).filter { it.items.isNotEmpty() }
    }

    /** A followed team's / league's shortcut: OK watches its game on now, else says when the next one is. */
    fun shortcut(f: SportFollow): Pair<LiveShortcut, () -> Unit> =
        LiveShortcut(
            f.shortcutId, f.name, AppIcons.LiveTv,
            emoji = if (f.logo == null) sportsVm.league(f.league).emoji else null,
            logoName = if (f.logo != null) "sport:${f.shortcutId}" else null,
            colors = ShortcutColours.sports,
        ) to {
            val list = games[f.shortcutId].orEmpty()
            val live = list.firstOrNull { it.first.state == "in" && it.second != null }?.second
            if (live != null) watch(live.first)
            else {
                val next = list.firstOrNull { it.first.state == "pre" }?.first
                toast(next?.let { "Next: ${it.title}, " + Dates.dayTime(it.start) } ?: "No games found for ${f.name} yet")
            }
        }

    private companion object {
        const val DAY = 86_400_000L
    }
}
