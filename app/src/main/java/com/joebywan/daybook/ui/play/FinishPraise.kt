package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.nearestToGo
import com.joebywan.daybook.core.newlyEarned
import com.joebywan.daybook.core.puzzleNote
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.techniqueToPraise
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * What the finished frame says (`docs/REWARDS.md`). [title] is the banner's headline (null: "Congratulations!", for a
 * frame nobody judged); [big] makes it louder (7, 30, 100 days); [lines] is at most one, so the frame stays calm.
 */
@Serializable
data class Praise(
    val title: String? = null,
    val big: Boolean = false,
    val lines: List<String> = emptyList(),
    /** Achievements this solve earned, in list order; the frame rotates through them. */
    val achievements: List<EarnedAchievement> = emptyList(),
    /** "Only X to go": at most one line of progress toward a near achievement. */
    val toGo: String? = null,
    /** What the Share button previews and sends ([shareCard]); null hides the button. */
    val share: ShareCard? = null,
    /** A second Share button, [setShareLabel], for the set this solve finished: a level, or today's three. */
    val setShare: ShareCard? = null,
    val setShareLabel: String = "",
    /**
     * The full show (all the confetti) rather than a light flourish: the first daily solve of today, an Expert solve, a
     * personal record, a streak milestone or an earned achievement. True by default, so a frame nobody judged stays loud.
     */
    val full: Boolean = true,
    /** The streak this solve extended, when [title] is "N day streak": the frame counts up to it. 0 otherwise. */
    val streak: Int = 0,
)

/** What the frame shows for one achievement. */
@Serializable
data class EarnedAchievement(val id: String, val title: String, val description: String)

/** Streak lengths that get the loud treatment. */
private val STREAK_MILESTONES = setOf(7, 30, 100)

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
 * faster than N% of own solves; hints (praise, never a count); no hints ([techniqueLine] for the last two).
 *
 * [taught] is the technique of every hint opened on this board; [needed] is [com.joebywan.daybook.core.PuzzleType.neededTechnique].
 */
fun finishPraise(
    history: List<Completion>,
    solve: Completion,
    today: LocalDate,
    puzzleName: String,
    taught: List<String> = emptyList(),
    needed: String? = null,
): Praise {
    var title: String? = null
    var big = false
    var streak = 0
    val lines = mutableListOf<String>()

    val day = solve.day
    val firstToday = day != null && day == today && history.none { it.day == today }
    if (firstToday) {
        val before = Stats.streak(history, today)
        val after = Stats.streak(history + solve, today)
        val n = after.current
        val savedByWindow = n > 1 && history.none { it.streakDay == LocalDate.fromEpochDays(today.toEpochDays() - 1) }
        when {
            n == 1 && before.lapsed -> { title = "Welcome back"; lines += "A new streak starts today." }
            n == 1 -> { title = "Day one"; lines += "Your streak starts today." }
            else -> {
                title = "$n day streak"
                streak = n
                big = n in STREAK_MILESTONES
                if (savedByWindow) lines += "You're back, and your streak is still going."
            }
        }
    }

    val tier = history.filter { it.puzzleId == solve.puzzleId && it.difficulty == solve.difficulty }
    val s = solve.seconds
    val personal = mutableListOf<String>()
    val record = tier.isNotEmpty() && s < tier.minOf { it.seconds }
    if (record) personal += "Your fastest ${solve.difficulty.label} $puzzleName yet."
    val sorted = tier.map { it.seconds }.sorted()
    val long = s >= LONG_SECONDS || (sorted.size >= MIN_SAMPLES && s >= LONG_FLOOR && s >= 2 * sorted[sorted.size / 2])
    if (long) personal += "That one fought back, and you stuck with it."
    if (sorted.size >= MIN_SAMPLES && !record) {
        val pct = 100 * sorted.count { it > s } / sorted.size
        if (pct >= PERCENT_FLOOR) personal += "Faster than $pct% of your own ${solve.difficulty.label} solves."
    }
    personal += techniqueLine(solve.puzzleId, solve.hints, taught, needed)
        ?: if (solve.hints > 0) "Hints are how a move sticks. Look for that one next time." else puzzleNote(solve.puzzleId, today.toEpochDays()) ?: "No hints needed."
    // Lowest priority: a no-hint solve with no record gets a technique note in place of the bare "No hints needed."

    // One line beneath the title (or beneath "Congratulations!"): praise is loud, not long.
    val earned = newlyEarned(history, solve, today).map { EarnedAchievement(it.id, it.title, it.description) }
    val near = nearestToGo(history, solve, today)
    val full = firstToday || solve.difficulty == Difficulty.EXPERT || record || big || earned.isNotEmpty()
    val firstOfPuzzle = history.none { it.puzzleId == solve.puzzleId }
    val heading = title ?: finishTitle(solve, today, firstOfPuzzle, record)
    return Praise(heading, big, (lines + personal).take(1), earned, near?.text, full = full, streak = streak)
}

/** The longest title, in characters: what fits the banner between its two poppers at 360dp in headlineSmall. */
const val TITLE_MAX = 18

/** Hand-written titles for a solve with nothing rarer to say: unaided ones, and the rest. */
internal val TITLES_UNAIDED = listOf("All your own work.", "Worked it out.", "Clear thinking.")
internal val TITLES_PLAIN = listOf("Nicely done.", "Well solved.", "Lovely work.", "There it is.", "Neatly done.", "That's the one.")
internal const val TITLE_FIRST = "First one done."
internal const val TITLE_RECORD = "A new best."
internal const val TITLE_EXPERT = "Expert, cracked."

/**
 * The title when there is no streak to tell, rarest first: the first solve of this puzzle ever, a personal record at the
 * tier, an Expert solve, then a pool (unaided, or not). The pool line is picked by the day, puzzle and tier, never at
 * random, so the same solve always reads the same and a rotation cannot change it.
 */
internal fun finishTitle(solve: Completion, today: LocalDate, firstOfPuzzle: Boolean, record: Boolean): String = when {
    firstOfPuzzle -> TITLE_FIRST
    record -> TITLE_RECORD
    solve.difficulty == Difficulty.EXPERT -> TITLE_EXPERT
    else -> {
        val pool = if (solve.hints == 0) TITLES_UNAIDED else TITLES_PLAIN
        // Written out rather than String.hashCode, so the JVM and the web are sure to pick alike.
        var h = 0
        for (ch in "${(solve.day ?: today).toEpochDays()}|${solve.puzzleId}|${solve.difficulty.name}") h = h * 31 + ch.code
        pool[(h % pool.size + pool.size) % pool.size]
    }
}

/**
 * The line that names the method, or null for the generic one. With hints: the most worth naming of the techniques the
 * hints showed ([taught]), never a count. Without: what the board itself needed ([needed]), only when that is more than
 * the puzzle's simplest step, because it is true of the board whatever the player did; it never claims the player used it.
 */
fun techniqueLine(puzzleId: String, hints: Int, taught: List<String>, needed: String?): String? {
    if (hints > 0) return techniqueToPraise(puzzleId, taught)?.let { "You learned $it." }
    val names = techniqueNamesOf(puzzleId)
    if (needed == null || needed == names.keys.firstOrNull()) return null
    return names[needed]?.let { "This board needed $it: solved without help." }
}

/** Where a shared result points: the web build, which anyone can open without installing anything. */
const val SHARE_URL = "https://knowhowit.com.au/daybook/"
