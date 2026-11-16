package io.github.petebutty.viewarr.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import io.github.petebutty.viewarr.ui.common.trapFocus
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.petebutty.viewarr.data.sports.GameDetails
import io.github.petebutty.viewarr.data.sports.GameSide
import io.github.petebutty.viewarr.ui.common.MenuItem
import io.github.petebutty.viewarr.ui.theme.AppColors
import io.github.petebutty.viewarr.ui.theme.Dates
import kotlinx.coroutines.launch

/**
 * A game's page (OK on a game in Sports; Settings → Sports → Game page): the teams and score, where it's on, buttons
 * to watch / be reminded / record (also before it's in the guide, and every game of a team), then line-ups, each
 * side's last five results, this season's meetings, goals and cards, and the stats. Down goes into the details and
 * scrolls them; Up at their top goes back to the buttons; Back closes.
 */
@Composable
internal fun GamePageHost(hub: SportsHub) {
    val open = hub.openGame ?: return
    val (g, ch) = open
    val gw = hub.sportsVm.gameWatch
    val version by gw.version
    BackHandler { hub.openGame = null }
    // Live: fetched again every minute while the page is open (score, goals, cards).
    val details by produceState<GameDetails?>(null, g.id) {
        while (true) { value = hub.sportsVm.gameInfo.details(g) ?: value; if (g.state != "in") break; kotlinx.coroutines.delay(60_000) }
    }
    val first = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var inside by remember { mutableStateOf(false) }
    var detailsFocused by remember { mutableStateOf(false) }
    LaunchedEffect(g.id) { repeat(20) { if (inside) return@LaunchedEffect; runCatching { first.requestFocus() }; kotlinx.coroutines.delay(60) } }
    val now = System.currentTimeMillis()
    val started = ch != null && (g.state == "in" || ch.second.startTime <= now)
    val onTv = hub.tv[g.id].orEmpty().filter { it != io.github.petebutty.viewarr.ui.guide.GuideViewModel.NOT_ON_UK_TV }

    Box(
        // (Focus stays in the page: nothing behind it can be reached while it's open.)
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).onFocusChanged { inside = it.hasFocus }
            .trapFocus(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.92f).background(AppColors.Panel, RoundedCornerShape(16.dp)).padding(28.dp)) {
            // The teams and the score (or kick-off).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Team(g.home, g.homeLogo, Modifier.weight(1f), alignEnd = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                    Text(g.score ?: Dates.time(g.start), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Text(
                        when (g.state) { "in" -> "Live" + (g.detail?.let { " · $it" } ?: ""); "post" -> "Full time"; else -> Dates.dayTime(g.start) },
                        fontSize = 14.sp, color = if (g.state == "in") AppColors.Accent else AppColors.TextSecondary,
                    )
                }
                Team(g.away, g.awayLogo, Modifier.weight(1f), alignEnd = false)
            }
            Text(
                listOfNotNull(g.leagueName, details?.venue, ch?.first?.name?.let { "On $it" } ?: onTv.joinToString(", ").ifEmpty { null }?.let { "On $it" }
                    ?: details?.broadcasts?.joinToString(", ")?.ifEmpty { null }?.let { "TV: $it" }).joinToString("  ·  "),
                fontSize = 14.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
            )
            // What you can do with it.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) { runCatching { detailsFocus.requestFocus() }; true } else false
            }) {
                val buttons = buildList<Pair<String, () -> Unit>> {
                    if (started && ch != null) add("Watch" to { hub.openGame = null; hub.watch(ch.first) })
                    if (g.state == "pre" && !started) {
                        if (ch != null) {
                            add("Remind me" to { hub.dvr.remind(ch.first, ch.second); hub.toast("We'll remind you: ${g.title} on ${ch.first.name}") })
                            add("Record" to { hub.dvr.record(ch.first, ch.second); gw.markDone(g.id); hub.toast("Recording ${g.title} on ${ch.first.name}") })
                        } else {
                            add((if (gw.wantsReminder(g.id)) "✓ Reminder" else "Remind me") to { gw.setReminder(g.id, !gw.wantsReminder(g.id)) })
                            add((if (gw.wantsRecording(g.id)) "✓ Recording" else "Record") to { gw.setRecording(g.id, !gw.wantsRecording(g.id)) })
                        }
                    }
                    listOf(g.home, g.away).forEach { team ->
                        add((if (gw.recordsTeam(team)) "✓ Record all $team" else "Record all $team") to { gw.setTeam(team, !gw.recordsTeam(team)) })
                    }
                    add("Close" to { hub.openGame = null })
                }
                version // (redraw ticks when the choices change)
                buttons.forEachIndexed { i, (label, act) ->
                    Box(Modifier.width(if (label.length > 14) 240.dp else 150.dp)) {
                        MenuItem(text = label, selected = label.startsWith("✓"), onClick = act, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
            if (g.state == "pre" && ch == null) Text(
                "Not in your TV guide yet: a reminder or recording asked for here is set as soon as the guide lists it (a Viewarr TV needs to be on then).",
                fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            // The details: Down into them, Up / Down scroll, Up at the top back to the buttons.
            Box(
                Modifier.fillMaxWidth().weight(1f).focusRequester(detailsFocus).onFocusChanged { detailsFocused = it.isFocused }.focusable()
                    // (Outlined while you're in the details, so you can see where you are.)
                    .then(if (detailsFocused) Modifier.border(2.dp, AppColors.Accent, RoundedCornerShape(10.dp)).padding(10.dp) else Modifier.padding(10.dp))
                    .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(220f) }; true }
                        Key.DirectionUp -> { if (scroll.value == 0) runCatching { first.requestFocus() } else scope.launch { scroll.animateScrollBy(-220f) }; true }
                        else -> false
                    }
                },
            ) {
                val d = details
                if (d == null) Text("Getting the line-ups and form…", fontSize = 14.sp, color = AppColors.TextSecondary)
                else Column(Modifier.verticalScroll(scroll)) {
                    Row(Modifier.fillMaxWidth()) {
                        SideColumn(d.home, Modifier.weight(1f))
                        Spacer(Modifier.width(28.dp))
                        SideColumn(d.away, Modifier.weight(1f))
                    }
                    d.series?.let { Heading("This season"); Text(it, fontSize = 15.sp, color = AppColors.TextPrimary) }
                    if (d.moments.isNotEmpty()) {
                        Heading("Goals, cards and changes")
                        d.moments.forEach { m -> Text("${m.clock}  ${m.kind}  ·  ${m.text}", fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 1.dp)) }
                    }
                    if (d.stats.isNotEmpty()) {
                        Heading("Stats")
                        d.stats.forEach { (k, h, a) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                Text(h, fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.width(80.dp))
                                Text(k, fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
                                Text(a, fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.width(80.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Team(name: String, logo: String?, modifier: Modifier, alignEnd: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        if (!alignEnd) logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(56.dp)); Spacer(Modifier.width(12.dp)) }
        Text(name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (alignEnd) logo?.let { Spacer(Modifier.width(12.dp)); AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(56.dp)) }
    }
}

@Composable
private fun Heading(text: String) = Text(text, fontSize = 15.sp, color = AppColors.Accent, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

/** One side: its line-up (formation, starters, subs) and its last five results. */
@Composable
private fun SideColumn(s: GameSide, modifier: Modifier) = Column(modifier) {
    Heading("${s.name} · line-up" + (s.formation?.let { " ($it)" } ?: ""))
    if (s.starters.isEmpty()) Text("Not announced yet (usually an hour before kick-off).", fontSize = 13.sp, color = AppColors.TextSecondary)
    s.starters.forEachIndexed { i, p -> Text("${i + 1}. $p", fontSize = 14.sp, color = AppColors.TextPrimary) }
    if (s.subs.isNotEmpty()) Text("Subs: " + s.subs.joinToString(", "), fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
    if (s.lastFive.isNotEmpty()) {
        Heading("Last five")
        s.lastFive.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                val c = when (r.result) { "W" -> Color(0xFF2E7D32); "L" -> Color(0xFFC62828); else -> Color(0xFF616161) }
                Text(r.result, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(c, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                Text("  ${r.score}  ${if (r.home) "v" else "@"} ${r.opponent}", fontSize = 14.sp, color = AppColors.TextPrimary)
            }
        }
    }
}
