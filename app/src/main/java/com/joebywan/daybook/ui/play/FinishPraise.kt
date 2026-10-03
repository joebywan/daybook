package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.newlyEarned
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import kotlinx.datetime.LocalDate

/**
 * What the finished frame says besides "Congratulations!" (`docs/REWARDS.md`). [title] replaces the
 * congratulation when there is a streak to announce; [big] makes it louder (7, 30, 100 days);
 * [lines] is at most one, so the frame stays calm and no taller than before.
 */
data class Praise(
    val title: String? = null,
    val big: Boolean = false,
    val lines: List<String> = emptyList(),
    /** Titles of achievements this solve earned. When any, [lines] is empty: the achievement takes the one line. */
    val achievements: List<String> = emptyList(),
) {
    /** One line however many: "Achievement: A", or "Achievement: A +2" (the list screen has the rest). */
    val achievementLine: String?
        get() = when (achievements.size) {
            0 -> null
            1 -> "Achievement: ${achievements[0]}"
            else -> "Achievement: ${achievements[0]} +${achievements.size - 1}"
        }
}

/** Streak lengths that get the loud treatment. */
val STREAK_MILESTONES = setOf(7, 30, 100)

private const val MIN_SAMPLES = 5      // own solves at the tier before a percentile or "long" is meaningful
private const val PERCENT_FLOOR = 75   // "faster than N%" shown only from here up
private const val LONG_SECONDS = 600   // a solve this long always counts as one that was stuck with
private const val LONG_FLOOR = 240     // ...or twice the tier's median, but never under four minutes

/**
 * Pure. [history] is every completion recorded BEFORE this solve; [solve] is the one just made.
 * Everything is derived from [history], and every claim is true of it: records and percentiles are
 * against the player's own solves only (there is no population data), a tie is not a record, and a
 * percentile is rounded down.
 *
 * Streak (only for the daily played on [today]'s own date, and only on the first solve of that date,
 * which is the one that adds a day): "N day streak"; the window-saved wording when yesterday was missed
 * and the run goes on; a fresh start after a lapse says welcome back.
 *
 * Personal lines, in priority order, fastest at this tier; perseverance;
 * faster than N% of own solves; hints (praise, never a count); no hints.
 */
fun finishPraise(history: List<Completion>, solve: Completion, today: LocalDate, puzzleName: String): Praise {
    var title: String? = null
    var big = false
    val lines = mutableListOf<String>()

    val day = solve.day
    if (day != null && day == today && history.none { it.day == today }) {
        val before = Stats.streak(history, today)
        val after = Stats.streak(history + solve, today)
        val n = after.current
        val savedByWindow = n > 1 && history.none { it.day == LocalDate.fromEpochDays(today.toEpochDays() - 1) }
        when {
            n == 1 && before.lapsed -> { title = "Welcome back"; lines += "A new streak starts today." }
            n == 1 -> { title = "Day one"; lines += "Your streak starts today." }
            else -> {
                title = "$n day streak"
                big = n in STREAK_MILESTONES
                if (savedByWindow) lines += "You're back, and your streak is still going."
            }
        }
    }

    val tier = history.filter { it.puzzleId == solve.puzzleId && it.difficulty == solve.difficulty }
    val s = solve.seconds
    val personal = mutableListOf<String>()
    if (tier.isNotEmpty() && s < tier.minOf { it.seconds }) {
        personal += "Your fastest ${solve.difficulty.label} $puzzleName yet."
    }
    val sorted = tier.map { it.seconds }.sorted()
    val long = s >= LONG_SECONDS || (sorted.size >= MIN_SAMPLES && s >= LONG_FLOOR && s >= 2 * sorted[sorted.size / 2])
    if (long) personal += "That one fought back, and you stuck with it."
    if (sorted.size >= MIN_SAMPLES && personal.none { it.startsWith("Your fastest") }) {
        val pct = 100 * sorted.count { it > s } / sorted.size
        if (pct >= PERCENT_FLOOR) personal += "Faster than $pct% of your own ${solve.difficulty.label} solves."
    }
    personal += if (solve.hints > 0) "Hints are how a move sticks. Look for that one next time." else "No hints needed."

    // One line beneath the title (or beneath "Congratulations!"): praise is loud, not long.
    // An achievement takes that one line (loud beats a personal best), so the frame never grows.
    val earned = newlyEarned(history, solve, today).map { it.title }
    return Praise(title, big, if (earned.isEmpty()) (lines + personal).take(1) else emptyList(), earned)
}
