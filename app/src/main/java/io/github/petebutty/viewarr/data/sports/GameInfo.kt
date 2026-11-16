package io.github.petebutty.viewarr.data.sports

import io.github.petebutty.viewarr.data.remote.services.HttpJson
import io.github.petebutty.viewarr.data.remote.services.array
import io.github.petebutty.viewarr.data.remote.services.child
import io.github.petebutty.viewarr.data.remote.services.str
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** One side of a game: its name, badge, formation and players (starters first). */
data class GameSide(
    val name: String,
    val logo: String?,
    val formation: String?,
    val starters: List<String>,
    val subs: List<String>,
    /** The last five results: "W 3-1 v Rangers", "L 0-1 @ Hibs"… newest first. */
    val lastFive: List<GameResult>,
)

data class GameResult(val result: String, val score: String, val opponent: String, val home: Boolean)

/** A moment in the game: "23' Goal · Kyogo Furuhashi (Celtic)". */
data class GameMoment(val clock: String, val kind: String, val text: String)

/** Everything the game page shows, from ESPN's match summary. */
data class GameDetails(
    val home: GameSide,
    val away: GameSide,
    val venue: String?,
    val broadcasts: List<String>,
    /** "Celtic lead the series 4-1" (this season's meetings), or null. */
    val series: String?,
    val moments: List<GameMoment>,
    /** Match stats, home value to away value: "Possession" → ("58%", "42%"). */
    val stats: List<Triple<String, String, String>>,
)

/**
 * A game's details from ESPN's public match summary (the same unofficial API the Sports page uses for fixtures and
 * scores): line-ups, each side's last five results, this season's meetings, goals and cards, stats, venue and TV.
 * Only the game asked about is sent. Answers are kept a few minutes (live games: one minute).
 */
@Singleton
class GameInfoSource @Inject constructor(private val http: HttpJson) {
    private val base = "https://site.api.espn.com/apis/site/v2/sports"
    private val cache = HashMap<String, Pair<Long, GameDetails>>()

    suspend fun details(g: SportGame): GameDetails? {
        val key = "${g.league}/${g.id}"
        val keep = if (g.state == "in") 60_000L else 5 * 60_000L
        cache[key]?.takeIf { System.currentTimeMillis() - it.first < keep }?.let { return it.second }
        val body = withTimeoutOrNull(10_000) {
            runCatching { http.get("$base/${g.league}/summary?event=${g.id}") }.getOrNull()
        }?.takeIf { it.ok }?.body ?: return null
        val d = runCatching { parse(body, g) }.getOrNull() ?: return null
        cache[key] = System.currentTimeMillis() to d
        return d
    }

    private fun prim(e: JsonElement?) = (e as? JsonPrimitive)?.content

    private fun parse(b: JsonElement, g: SportGame): GameDetails {
        val rosters = b.array("rosters")
        val five = b.array("lastFiveGames")
        fun side(name: String, logo: String?, homeAway: String): GameSide {
            val r = rosters.firstOrNull { it.str("homeAway") == homeAway }
                ?: rosters.firstOrNull { it.child("team").str("displayName") == name }
            val players = r.array("roster")
            fun who(p: JsonElement) = p.child("athlete").str("displayName")
            val starters = players.filter { prim(it.child("starter")) == "true" }.mapNotNull(::who)
            val subs = players.filter { prim(it.child("starter")) != "true" }.mapNotNull(::who)
            val lf = five.firstOrNull { it.child("team").str("displayName") == name }.array("events").take(5).mapNotNull { e ->
                val res = e.str("gameResult") ?: return@mapNotNull null
                GameResult(res, e.str("score").orEmpty(), e.child("opponent").str("displayName").orEmpty(), e.str("atVs") != "@")
            }
            return GameSide(name, logo, r.str("formation"), starters, subs, lf)
        }
        val moments = b.array("keyEvents").mapNotNull { k ->
            val kind = k.child("type").str("text") ?: return@mapNotNull null
            if (kind in setOf("Kickoff", "Halftime", "End Regular Time", "Start 2nd Half")) return@mapNotNull null
            val clock = k.child("clock").str("displayValue").orEmpty()
            val people = k.array("participants").mapNotNull { it.child("athlete").str("displayName") }.joinToString(", ")
            val team = k.child("team").str("displayName")
            val text = listOfNotNull(people.ifEmpty { null }, team?.let { "($it)" }).joinToString(" ").ifEmpty { k.str("text").orEmpty() }
            GameMoment(clock, kind, text)
        }
        val teams = b.child("boxscore").array("teams")
        // By ESPN's stat names (its labels are capitals and vary); home / away by each team's own homeAway.
        fun statsOf(t: JsonElement?) = t.array("statistics").associate { (it.str("name").orEmpty()) to (it.str("displayValue") ?: "") }
        val h = statsOf(teams.firstOrNull { it.str("homeAway") == "home" } ?: teams.getOrNull(0))
        val a = statsOf(teams.firstOrNull { it.str("homeAway") == "away" } ?: teams.getOrNull(1))
        val wanted = listOf(
            "possessionPct" to "Possession", "totalShots" to "Shots", "shotsOnTarget" to "On target", "wonCorners" to "Corners",
            "foulsCommitted" to "Fouls", "yellowCards" to "Yellow cards", "redCards" to "Red cards", "offsides" to "Offsides", "saves" to "Saves",
        )
        fun show(k: String, v: String?) = v?.takeIf { it.isNotEmpty() }?.let { if (k == "possessionPct" && !it.endsWith("%")) "$it%" else it }.orEmpty()
        val stats = wanted.mapNotNull { (k, label) -> val x = show(k, h[k]); val y = show(k, a[k]); if (x.isEmpty() && y.isEmpty()) null else Triple(label, x, y) }
        val series = b.array("seasonseries").firstOrNull()?.str("summary")
        val venue = b.child("gameInfo").child("venue").str("fullName")
        val tv = b.array("broadcasts").mapNotNull { it.child("media").str("shortName") ?: it.str("station") }.distinct()
        return GameDetails(side(g.home, g.homeLogo, "home"), side(g.away, g.awayLogo, "away"), venue, tv, series, moments, stats)
    }
}
