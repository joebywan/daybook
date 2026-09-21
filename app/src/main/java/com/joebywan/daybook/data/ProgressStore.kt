package com.joebywan.daybook.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.PuzzleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daybook")

/**
 * Lenient on purpose. A board saved by an older build whose state class has since gained or lost a
 * field must degrade to "no saved game", never to a crash on the way into the puzzle.
 */
private val SavedJson = Json { ignoreUnknownKeys = true }

/**
 * One finished puzzle.
 *
 * [day] is null for practice games, which count toward totals but never toward streaks.
 */
data class Completion(
    val puzzleId: String,
    val difficulty: Difficulty,
    val day: LocalDate?,
    val seconds: Int,
    val hints: Int,
) {
    fun encode(): String =
        listOf(puzzleId, difficulty.name, day?.toEpochDay()?.toString() ?: "-", seconds, hints)
            .joinToString("|")

    companion object {
        fun decode(raw: String): Completion? {
            val parts = raw.split("|")
            if (parts.size != 5) return null
            return Completion(
                puzzleId = parts[0],
                difficulty = Difficulty.fromKey(parts[1]),
                day = parts[2].toLongOrNull()?.let(LocalDate::ofEpochDay),
                seconds = parts[3].toIntOrNull() ?: return null,
                hints = parts[4].toIntOrNull() ?: return null,
            )
        }
    }
}

/**
 * A game left part-finished: the board plus everything the play screen would otherwise lose.
 *
 * The undo stack is part of it because a restored game that cannot be undone is only half restored.
 * Doubles as the payload for the screen's own `rememberSaveable`, so the rotation path and the
 * process-death path can never drift apart in what they preserve.
 */
@Serializable
data class SavedGame(
    val state: PuzzleState,
    val history: List<PuzzleState> = emptyList(),
    val hints: Int = 0,
    val seconds: Int = 0,
) {
    /**
     * Keeps only the tail of the undo stack. Every entry is a whole board, so a Mosaic Expert game
     * a few hundred taps deep would run past half a megabyte — more than Android will carry in a
     * savedInstanceState Bundle, which is a crash rather than a lost undo. The live stack on the
     * play screen stays whole; it is only leaving the screen that costs the oldest steps.
     */
    fun trimmed(): SavedGame = copy(history = history.takeLast(UNDO_DEPTH))

    fun encode(): String = SavedJson.encodeToString(trimmed())

    companion object {
        /** Far deeper than anyone reaches back for, and small enough to be cheap to carry. */
        const val UNDO_DEPTH = 24

        fun decode(raw: String): SavedGame? =
            runCatching { SavedJson.decodeFromString<SavedGame>(raw) }.getOrNull()
    }
}

/** A [SavedGame] tagged with the board it belongs to, as it is held on disk. */
@Serializable
data class StoredGame(val key: String, val savedAt: Long, val game: SavedGame) {
    fun encode(): String = SavedJson.encodeToString(copy(game = game.trimmed()))

    companion object {
        fun decode(raw: String): StoredGame? =
            runCatching { SavedJson.decodeFromString<StoredGame>(raw) }.getOrNull()
    }
}

/**
 * Identifies one board exactly. A daily puzzle and a practice game of the same genre and
 * difficulty differ by seed, which is what stops yesterday's Sudoku reopening as today's.
 */
fun savedGameKey(puzzleId: String, difficulty: Difficulty, seed: Long): String =
    listOf(puzzleId, difficulty.name, seed).joinToString("|")

/** Which saved games survive a write. Pure, so the eviction rule is testable on its own. */
object SavedGames {

    /**
     * Practice games mint a fresh seed every time they are started, so without a ceiling the store
     * would grow a board for every game ever abandoned. Enough to cover a morning's dabbling.
     */
    const val KEEP = 12

    fun find(all: List<StoredGame>, key: String): SavedGame? =
        all.firstOrNull { it.key == key }?.game

    fun without(all: List<StoredGame>, key: String): List<StoredGame> =
        all.filterNot { it.key == key }

    /** One entry per board: re-saving replaces, and the stalest games fall off the end. */
    fun upsert(all: List<StoredGame>, entry: StoredGame): List<StoredGame> =
        (listOf(entry) + without(all, entry.key)).sortedByDescending { it.savedAt }.take(KEEP)
}

/**
 * All saved progress. Local only — there is no account, no sync and no network permission.
 */
class ProgressStore(private val context: Context) {

    val completions: Flow<List<Completion>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_COMPLETIONS].orEmpty().mapNotNull(Completion::decode)
        }

    suspend fun record(completion: Completion) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_COMPLETIONS].orEmpty()
            // A given daily puzzle is only ever recorded once; re-solves do not inflate stats.
            val isDuplicate = completion.day != null && existing.any { raw ->
                val other = Completion.decode(raw)
                other?.puzzleId == completion.puzzleId &&
                    other.difficulty == completion.difficulty &&
                    other.day == completion.day
            }
            if (!isDuplicate) {
                prefs[KEY_COMPLETIONS] = existing + completion.encode()
            }
        }
    }

    /** The game in progress on this board, if one was left behind. */
    suspend fun savedGame(key: String): SavedGame? =
        SavedGames.find(context.dataStore.data.first().savedGames(), key)

    suspend fun saveGame(key: String, game: SavedGame) {
        context.dataStore.edit { prefs ->
            val entry = StoredGame(key, System.currentTimeMillis(), game)
            prefs[KEY_SAVED] = SavedGames.upsert(prefs.savedGames(), entry).encodeAll()
        }
    }

    suspend fun clearSavedGame(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAVED] = SavedGames.without(prefs.savedGames(), key).encodeAll()
        }
    }

    private fun Preferences.savedGames(): List<StoredGame> =
        this[KEY_SAVED].orEmpty().mapNotNull(StoredGame::decode)

    private fun List<StoredGame>.encodeAll(): Set<String> = map(StoredGame::encode).toSet()

    private companion object {
        val KEY_COMPLETIONS = stringSetPreferencesKey("completions")
        val KEY_SAVED = stringSetPreferencesKey("saved_games")
    }
}

/** Derived numbers for the stats screen. Pure, so it is trivially testable. */
object Stats {

    fun totalSolved(all: List<Completion>): Int = all.size

    fun solvedToday(all: List<Completion>, today: LocalDate): Int =
        all.count { it.day == today }

    /**
     * Consecutive days, ending today or yesterday, on which at least one daily puzzle was solved.
     * Allowing the streak to end yesterday means it survives until the day is actually missed.
     */
    fun currentStreak(all: List<Completion>, today: LocalDate): Int {
        val days = all.mapNotNull { it.day }.toSet()
        if (days.isEmpty()) return 0
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun bestStreak(all: List<Completion>): Int {
        val days = all.mapNotNull { it.day }.distinct().sorted()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1..days.lastIndex) {
            run = if (days[i - 1].plusDays(1) == days[i]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    fun bestTime(all: List<Completion>, puzzleId: String, difficulty: Difficulty): Int? =
        all.filter { it.puzzleId == puzzleId && it.difficulty == difficulty }
            .minOfOrNull { it.seconds }
}
