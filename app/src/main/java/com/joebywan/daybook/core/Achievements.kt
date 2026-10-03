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
/** Which badge shape an achievement wears (`ui/stats/AchievementBadge.kt`). */
enum class AchievementCategory { STREAK, PUZZLE_STREAK, COVERAGE, MILESTONE }

class Achievement(
    val id: String,
    val category: AchievementCategory,
    val title: String,
    val description: String,
    val earned: (history: List<Completion>, today: LocalDate, puzzleIds: List<String>) -> Boolean,
)

private fun playedDays(h: List<Completion>) = h.mapNotNullTo(HashSet()) { it.day }

private fun streak(n: Int, title: String) = Achievement(
    "streak$n", AchievementCategory.STREAK, title, "Play on $n days in one streak.",
) { h, today, _ -> streakOf(playedDays(h), today).best >= n }

private fun puzzleStreak(n: Int, title: String) = Achievement(
    "puzzleStreak$n", AchievementCategory.PUZZLE_STREAK, title, "Reach a $n-day streak in a single puzzle.",
) { h, today, _ ->
    h.groupBy { it.puzzleId }.values.any { c -> streakOf(playedDays(c), today).best >= n }
}

/** The top tier is whatever the last [Difficulty] is. */
private val TOP = Difficulty.entries.last()

val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("solves1", AchievementCategory.MILESTONE, "First solve", "Solve your first puzzle.") { h, _, _ -> h.isNotEmpty() },
    Achievement("solves100", AchievementCategory.MILESTONE, "Hundred solves", "Solve 100 puzzles.") { h, _, _ -> h.size >= 100 },
    streak(3, "Three in a row"),
    streak(7, "A full week"),
    streak(14, "Fortnight"),
    streak(30, "A month of puzzles"),
    streak(60, "Sixty days"),
    streak(100, "One hundred days"),
    streak(365, "A whole year"),
    puzzleStreak(7, "Devoted"),
    puzzleStreak(30, "Specialist"),
    Achievement("days30", AchievementCategory.MILESTONE, "Regular", "Play on 30 different days.") { h, _, _ -> playedDays(h).size >= 30 },
    Achievement("firstTop", AchievementCategory.MILESTONE, "First ${TOP.label}", "Solve a puzzle on ${TOP.label}.") { h, _, _ ->
        h.any { it.difficulty == TOP }
    },
    Achievement("cleanTop", AchievementCategory.MILESTONE, "Unaided ${TOP.label}", "Solve a ${TOP.label} puzzle without hints.") { h, _, _ ->
        h.any { it.difficulty == TOP && it.hints == 0 }
    },
    Achievement("oneOfEach", AchievementCategory.COVERAGE, "One of each", "Solve every kind of puzzle at least once.") { h, _, ids ->
        h.mapTo(HashSet()) { it.puzzleId }.containsAll(ids)
    },
    Achievement("fullSet", AchievementCategory.COVERAGE, "Full set", "Finish every difficulty of one puzzle's daily on the same day.") { h, _, _ ->
        h.filter { it.day != null }.groupBy { it.puzzleId to it.day }
            .any { (_, c) -> c.mapTo(HashSet()) { it.difficulty }.size == Difficulty.entries.size }
    },
    Achievement("allPuzzles", AchievementCategory.COVERAGE, "Clean sweep", "Finish every puzzle's daily on the same day.") { h, _, ids ->
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

/**
 * "Only X to go": one achievement 1 or 2 steps away, for the finished frame. [left] is the number of
 * steps, [text] the whole line. Positive phrasing only, and nothing for a streak that has lapsed.
 */
class ToGo(val id: String, val left: Int, val text: String)

private fun steps(n: Int, one: String, many: String) = if (n == 1) "1 more $one" else "$n more $many"

private fun names(ids: List<String>, nameOf: (String) -> String): String =
    ids.map(nameOf).sorted().joinToString(" and ")

private fun puzzleName(id: String) = PuzzleRegistry.byId(id)?.displayName ?: id

/**
 * How far [id] is from being earned over [history], as a [ToGo], or null when it is earned, far (more than 2),
 * or not a counting kind (first solve, top tier). Pure. A streak counts only while the run is alive.
 */
fun remaining(
    id: String, history: List<Completion>, today: LocalDate,
    puzzleIds: List<String> = registryIds(), nameOf: (String) -> String = ::puzzleName,
): ToGo? {
    val a = ACHIEVEMENTS.firstOrNull { it.id == id } ?: return null
    if (a.earned(history, today, puzzleIds)) return null
    fun go(left: Int, text: String) = if (left in 1..2) ToGo(id, left, text) else null
    fun liveStreak(days: Set<LocalDate>) = streakOf(days, today).takeIf { it.alive }?.current ?: 0
    val dailies = history.filter { it.day == today }
    return when {
        id == "solves100" -> (100 - history.size).let { go(it, "Only ${steps(it, "solve", "solves")} to your hundredth.") }
        id == "days30" -> (30 - playedDays(history).size).let { go(it, "${steps(it, "day", "days")} played to Regular.") }
        id.startsWith("streak") -> {
            val n = id.removePrefix("streak").toInt()
            (n - liveStreak(playedDays(history))).let { go(it, "${steps(it, "day", "days")} to your $n-day streak.") }
        }
        id.startsWith("puzzleStreak") -> {
            val n = id.removePrefix("puzzleStreak").toInt()
            history.groupBy { it.puzzleId }
                .map { (p, c) -> p to n - liveStreak(playedDays(c)) }
                .filter { it.second in 1..2 }.minWithOrNull(compareBy({ it.second }, { it.first }))
                ?.let { (p, left) -> go(left, "${steps(left, "day", "days")} to a $n-day ${nameOf(p)} streak.") }
        }
        id == "oneOfEach" -> {
            val missing = puzzleIds - history.mapTo(HashSet()) { it.puzzleId }.toSet()
            go(missing.size, "Only ${names(missing, nameOf)} left to try them all.")
        }
        id == "fullSet" -> dailies.groupBy { it.puzzleId }
            .map { (p, c) -> p to Difficulty.entries - c.mapTo(HashSet()) { it.difficulty }.toSet() }
            .filter { it.second.size in 1..2 }.minWithOrNull(compareBy({ it.second.size }, { it.first }))
            ?.let { (p, miss) ->
                go(miss.size, "Only ${miss.joinToString(" and ") { it.label }} on ${nameOf(p)} left for all done today!")
            }
        id == "allPuzzles" -> if (dailies.isEmpty() || puzzleIds.isEmpty()) null else {
            val missing = puzzleIds - dailies.mapTo(HashSet()) { it.puzzleId }.toSet()
            go(missing.size, "Only ${names(missing, nameOf)} left for a clean sweep today!")
        }
        else -> null
    }
}

/** The single most useful near goal once [solve] is recorded: fewest steps, then list order. */
fun nearestToGo(
    history: List<Completion>, solve: Completion, today: LocalDate,
    puzzleIds: List<String> = registryIds(), nameOf: (String) -> String = ::puzzleName,
): ToGo? {
    val after = history + solve
    return ACHIEVEMENTS.mapNotNull { remaining(it.id, after, today, puzzleIds, nameOf) }.minByOrNull { it.left }
}
