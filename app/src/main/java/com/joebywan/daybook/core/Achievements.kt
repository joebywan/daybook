package com.joebywan.daybook.core

import com.joebywan.daybook.data.Completion
import kotlinx.datetime.LocalDate

/**
 * Achievements (`docs/REWARDS.md`). Nothing is stored: [Achievement.earned] is a predicate over the whole
 * completion history, and a solve "earns" one when it is false over the history before the solve and true
 * after it ([newlyEarned]). Every predicate is monotone (more history never un-earns it), so an old player
 * whose history already satisfies one simply sees it earned and is never told about it: only the solve that
 * flips it announces it. The exceptions are the two registry-wide ones: add a puzzle and they need that
 * puzzle too, so they read as unearned again until it is played.
 *
 * Pure and platform-free (the web compiles it). `puzzleIds` is a parameter so tests can fake the registry.
 */
class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val earned: (history: List<Completion>, today: LocalDate, puzzleIds: List<String>) -> Boolean,
)

private fun playedDays(h: List<Completion>) = h.mapNotNullTo(HashSet()) { it.day }

private fun streak(n: Int, title: String) = Achievement(
    "streak$n", title, "Play on $n days in one streak.",
) { h, today, _ -> streakOf(playedDays(h), today).best >= n }

private fun puzzleStreak(n: Int, title: String) = Achievement(
    "puzzleStreak$n", title, "Reach a $n-day streak in a single puzzle.",
) { h, today, _ ->
    h.groupBy { it.puzzleId }.values.any { c -> streakOf(playedDays(c), today).best >= n }
}

/** The top tier is whatever the last [Difficulty] is. */
private val TOP = Difficulty.entries.last()

val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("solves1", "First solve", "Solve your first puzzle.") { h, _, _ -> h.isNotEmpty() },
    Achievement("solves100", "Hundred solves", "Solve 100 puzzles.") { h, _, _ -> h.size >= 100 },
    streak(3, "Three in a row"),
    streak(7, "A full week"),
    streak(14, "Fortnight"),
    streak(30, "A month of puzzles"),
    streak(60, "Sixty days"),
    streak(100, "One hundred days"),
    streak(365, "A whole year"),
    puzzleStreak(7, "Devoted"),
    puzzleStreak(30, "Specialist"),
    Achievement("days30", "Regular", "Play on 30 different days.") { h, _, _ -> playedDays(h).size >= 30 },
    Achievement("firstTop", "First ${TOP.label}", "Solve a puzzle on ${TOP.label}.") { h, _, _ ->
        h.any { it.difficulty == TOP }
    },
    Achievement("cleanTop", "Unaided ${TOP.label}", "Solve a ${TOP.label} puzzle without hints.") { h, _, _ ->
        h.any { it.difficulty == TOP && it.hints == 0 }
    },
    Achievement("oneOfEach", "One of each", "Solve every kind of puzzle at least once.") { h, _, ids ->
        h.mapTo(HashSet()) { it.puzzleId }.containsAll(ids)
    },
    Achievement("fullSet", "Full set", "Finish every difficulty of one puzzle's daily on the same day.") { h, _, _ ->
        h.filter { it.day != null }.groupBy { it.puzzleId to it.day }
            .any { (_, c) -> c.mapTo(HashSet()) { it.difficulty }.size == Difficulty.entries.size }
    },
    Achievement("allPuzzles", "Clean sweep", "Finish every puzzle's daily on the same day.") { h, _, ids ->
        ids.isNotEmpty() && h.filter { it.day != null }.groupBy { it.day }
            .any { (_, c) -> c.mapTo(HashSet()) { it.puzzleId }.containsAll(ids) }
    },
)

private fun registryIds() = PuzzleRegistry.all.map { it.id }

/** Every achievement [history] has earned. */
fun earnedAchievements(
    history: List<Completion>, today: LocalDate, puzzleIds: List<String> = registryIds(),
): List<Achievement> = ACHIEVEMENTS.filter { it.earned(history, today, puzzleIds) }

/** Earned once [solve] is recorded. */
fun earnedAfter(
    history: List<Completion>, solve: Completion, today: LocalDate, puzzleIds: List<String> = registryIds(),
): List<Achievement> = earnedAchievements(history + solve, today, puzzleIds)

/** What [solve] earns: true after it, false before. History that already satisfied one never re-fires it. */
fun newlyEarned(
    history: List<Completion>, solve: Completion, today: LocalDate, puzzleIds: List<String> = registryIds(),
): List<Achievement> {
    val before = earnedAchievements(history, today, puzzleIds).map { it.id }.toSet()
    return earnedAfter(history, solve, today, puzzleIds).filter { it.id !in before }
}
