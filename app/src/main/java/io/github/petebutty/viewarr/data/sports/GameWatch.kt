package io.github.petebutty.viewarr.data.sports

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders and recordings asked for on games the TV guide doesn't list yet (it reaches a few days ahead), and
 * "record all of a team's games". Kept on this TV; the Sports page turns each into a real reminder / Dispatcharr
 * recording as soon as it finds the game's channel in the guide (so a Viewarr TV has to be on once the game is listed).
 */
@Singleton
class GameWatch @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("game_watch", Context.MODE_PRIVATE)

    /** Bumped on every change, for screens to redraw ticks. */
    val version = mutableStateOf(0)

    private fun set(name: String) = prefs.getStringSet(name, emptySet()).orEmpty()
    private fun put(name: String, v: Set<String>) { prefs.edit().putStringSet(name, v).apply(); version.value++ }

    fun wantsReminder(gameId: String) = gameId in set(REMIND)
    fun wantsRecording(gameId: String) = gameId in set(RECORD)
    fun recordsTeam(team: String) = team.lowercase() in set(TEAMS)

    fun setReminder(gameId: String, on: Boolean) = put(REMIND, if (on) set(REMIND) + gameId else set(REMIND) - gameId)
    fun setRecording(gameId: String, on: Boolean) = put(RECORD, if (on) set(RECORD) + gameId else set(RECORD) - gameId)
    fun setTeam(team: String, on: Boolean) = put(TEAMS, if (on) set(TEAMS) + team.lowercase() else set(TEAMS) - team.lowercase())

    /** Done (turned into the real thing): forgotten here so it isn't done twice. */
    fun resolved(gameId: String) {
        val r = set(REMIND); val c = set(RECORD)
        if (gameId in r || gameId in c) prefs.edit().putStringSet(REMIND, r - gameId).putStringSet(RECORD, c - gameId).apply()
        markDone(gameId)
    }

    /** Games already recorded (by a team rule or by hand): not recorded again. */
    fun done(gameId: String) = doneMap().containsKey(gameId)
    /** Remembered as recorded, newest kept (the oldest dropped after 300). */
    fun markDone(gameId: String) {
        val m = doneMap(); if (m.containsKey(gameId)) return
        m[gameId] = System.currentTimeMillis()
        val kept = m.entries.sortedByDescending { it.value }.take(300)
        prefs.edit().putString(DONE_AT, kept.joinToString(";") { "${it.key}=${it.value}" }).apply(); version.value++
    }
    private fun doneMap(): HashMap<String, Long> = HashMap(prefs.getString(DONE_AT, "").orEmpty().split(';').mapNotNull {
        val k = it.substringBefore('=', ""); val v = it.substringAfter('=', "").toLongOrNull(); if (k.isEmpty() || v == null) null else k to v
    }.toMap())

    private companion object {
        const val REMIND = "remind"
        const val RECORD = "record"
        const val TEAMS = "teams"
        const val DONE_AT = "done_at"
    }
}
