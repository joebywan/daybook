package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.Completion
import kotlinx.datetime.LocalDate

/** Where a button on the finished-puzzle frame leads. */
enum class NextKind {
    /** A fresh random board at [NextOption.difficulty]. */
    RANDOM,

    /** The same date's board at [NextOption.difficulty] (a past date's board on an archive day). */
    DAILY,

    /** Back to Home. */
    DONE,
}

/** One button on the finished-puzzle frame. [difficulty] is null only for [NextKind.DONE]. */
data class NextOption(val label: String, val kind: NextKind, val difficulty: Difficulty?)

/**
 * The buttons offered once a puzzle is solved, as a pure function of what was just played and what
 * is already done, so that the player is offered choices rather than what they have finished.
 *
 * A random board offers Another (same tier), Easier and Harder (one tier either way, absent at the
 * ends), Done.
 *
 * A daily offers Easier Daily, Random (same tier), Harder Daily, Done. The two dailies are the same
 * date at another tier and are never offered for a tier already done that day: the nearest undone
 * tier in that direction is offered instead, and the button is dropped if nothing in that direction
 * is undone. [doneTiers] need not include [tier]; the board just solved counts as done regardless,
 * because the store records it asynchronously.
 */
fun nextOptions(daily: Boolean, tier: Difficulty, doneTiers: Set<Difficulty>): List<NextOption> {
    val tiers = Difficulty.entries
    val here = tiers.indexOf(tier)
    return if (daily) {
        val done = doneTiers + tier
        val easier = tiers.take(here).lastOrNull { it !in done }
        val harder = tiers.drop(here + 1).firstOrNull { it !in done }
        buildList {
            if (easier != null) add(NextOption("Easier Daily", NextKind.DAILY, easier))
            add(NextOption("Random", NextKind.RANDOM, tier))
            if (harder != null) add(NextOption("Harder Daily", NextKind.DAILY, harder))
            add(NextOption("Done", NextKind.DONE, null))
        }
    } else {
        buildList {
            add(NextOption("Another", NextKind.RANDOM, tier))
            tiers.getOrNull(here - 1)?.let { add(NextOption("Easier", NextKind.RANDOM, it)) }
            tiers.getOrNull(here + 1)?.let { add(NextOption("Harder", NextKind.RANDOM, it)) }
            add(NextOption("Done", NextKind.DONE, null))
        }
    }
}

/**
 * The tiers of [puzzleId] already completed on [day]. A random game (`day == null`) never counts as
 * a daily one, so a null [day] has none.
 */
fun tiersDoneOn(completions: List<Completion>, puzzleId: String, day: LocalDate?): Set<Difficulty> =
    if (day == null) emptySet()
    else completions.filter { it.puzzleId == puzzleId && it.day == day }.map { it.difficulty }.toSet()
