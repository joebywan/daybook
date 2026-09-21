package com.joebywan.daybook.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joebywan.daybook.core.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daybook")

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

    private companion object {
        val KEY_COMPLETIONS = stringSetPreferencesKey("completions")
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
