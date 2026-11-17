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
    /** This season's top scorer / assists: "Goals" to "Erling Haaland · 9". */
    val leaders: List<Pair<String, String>> = emptyList(),
)

/** A league table line. [mine]: one of this game's two teams. */
data class TableRow(val rank: Int, val team: String, val played: String, val gd: String, val points: String, val mine: Boolean)

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
    /** The league table (its name, the rows), when it's a league game. */
    val tableName: String? = null,
    val table: List<TableRow> = emptyList(),
    /** Afterwards: the match report's headline and summary. */
    val report: Pair<String, String>? = null,
    /** Live text commentary, newest first: "67'" to "Goal! Celtic 2, St Mirren 1…". */
    val commentary: List<Pair<String, String>> = emptyList(),
    val attendance: Int? = null,
    val city: String? = null,
    /** ESPN headlines mentioning either team. */
    val news: List<Pair<String, String>> = emptyList(),
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
            // Its top scorer and assists ("Matches: 10, Goals: 9" → "Erling Haaland · 9").
            val leaders = b.array("leaders").firstOrNull { it.child("team").str("displayName") == name }.array("leaders").mapNotNull { cat ->
                val label = cat.str("displayName") ?: return@mapNotNull null
                val top = cat.array("leaders").firstOrNull() ?: return@mapNotNull null
                val who = top.child("athlete").str("displayName") ?: return@mapNotNull null
                val n = Regex(Regex.escape(label) + ":\\s*(\\d+)", RegexOption.IGNORE_CASE).find(top.str("displayValue").orEmpty())?.groupValues?.get(1)
                label to (if (n != null) "$who · $n" else who)
            }.take(2)
            return GameSide(name, logo, r.str("formation"), starters, subs, lf, leaders)
        }
        val moments = b.array("keyEvents").mapNotNull { k ->
            val kind = k.child("type").str("text") ?: return@mapNotNull null
            if (kind in NOT_MOMENTS) return@mapNotNull null
            val clock = k.child("clock").str("displayValue").orEmpty()
            val people = k.array("participants").mapNotNull { it.child("athlete").str("displayName") }.joinToString(", ")
            val team = k.child("team").str("displayName")
            val text = listOfNotNull(people.ifEmpty { null }, team?.let { "($it)" }).joinToString(" ").ifEmpty { k.str("text").orEmpty() }
            GameMoment(clock, kind, text)
        }
        val teams = b.child("boxscore").array("teams")
        // By ESPN's stat names (its labels are capitals and vary); home / away by each team's own homeAway.
        fun statsOf(t: JsonElement?) = t.array("statistics").associate { it.str("name").orEmpty() to it.str("displayValue").orEmpty() }
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
        // The league table (cups have none).
        val group = b.child("standings").array("groups").firstOrNull()
        fun stat(e: JsonElement, k: String) = e.array("stats").firstOrNull { it.str("name") == k }?.str("displayValue").orEmpty()
        val table = group.child("standings").array("entries").mapIndexed { i, e ->
            val team = e.str("team").orEmpty()
            TableRow(stat(e, "rank").toIntOrNull() ?: (i + 1), team, stat(e, "gamesPlayed"), stat(e, "pointDifferential"), stat(e, "points"),
                team == g.home || team == g.away)
        }.sortedBy { it.rank }
        val art = b.child("article")
        val report = art.str("headline")?.let { it to art.str("description").orEmpty() }
        val commentary = b.array("commentary").mapNotNull { c ->
            val t = c.str("text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            c.child("time").str("displayValue").orEmpty() to t
        }.reversed().take(30)
        val gi = b.child("gameInfo")
        // Headlines about either team (ESPN's news here is the whole league's).
        fun word(t: String) = t.split(NON_WORD).filter { it.length > 3 && it.lowercase() !in COMMON_NAME_WORDS }.maxByOrNull { it.length } ?: t
        val keys = listOf(word(g.home), word(g.away)).map { it.lowercase() }
        val news = b.child("news").array("articles").mapNotNull { n ->
            val head = n.str("headline") ?: return@mapNotNull null
            val text = (head + " " + n.str("description").orEmpty()).lowercase()
            if (keys.none { text.contains(it) }) null else head to n.str("description").orEmpty()
        }.take(4)
        return GameDetails(side(g.home, g.homeLogo, "home"), side(g.away, g.awayLogo, "away"), venue, tv, series, moments, stats,
            tableName = group?.str("header")?.removeSuffix(" Standings"), table = table, report = report, commentary = commentary,
            attendance = gi.str("attendance")?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }, city = gi.child("venue").child("address").str("city"), news = news)
    }

    private companion object {
        /** Key events that aren't moments worth listing. */
        val NOT_MOMENTS = setOf("Kickoff", "Halftime", "End Regular Time", "Start 2nd Half")
        val NON_WORD = Regex("[^\\p{L}0-9]+")
        /** Words in many clubs' names: not the one to find a team's news by. */
        val COMMON_NAME_WORDS = setOf("city", "united", "town", "athletic", "county", "rovers")
    }
}
