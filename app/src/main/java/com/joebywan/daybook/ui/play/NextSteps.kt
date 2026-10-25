package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LevelBoard
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.Completion
import kotlinx.datetime.LocalDate

/** Where a button on the finished-puzzle frame leads. */
enum class NextKind {
    /** A fresh random board at [NextOption.difficulty]. */
    RANDOM,

    /** The same date's board at [NextOption.difficulty] (a past date's board on an archive day). */
    DAILY,

    /** A board of level [NextOption.level]: [NextOption.puzzleId], or its first unsolved board when that is null. */
    LEVEL,

    /** Back to Home. */
    DONE,
}

/**
 * One button on the finished-puzzle frame. [difficulty] is null for [NextKind.DONE] and for a whole next level;
 * [note] is the button's second line in place of the tier.
 */
data class NextOption(
    val label: String,
    val kind: NextKind,
    val difficulty: Difficulty?,
    val puzzleId: String? = null,
    val level: Int? = null,
    val note: String? = null,
)

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
 * The button the finished frame fills in: going on with the level when there is one, since that is what a level asks for;
 * otherwise "Done"/"Home", which is the safe default on a daily or random board.
 */
fun mainOption(options: List<NextOption>): NextOption? =
    options.firstOrNull { it.kind == NextKind.LEVEL } ?: options.firstOrNull { it.kind == NextKind.DONE }

/**
 * The one way on that the results pill shows beside Done, once the frame is hidden (as it always is on a solved board
 * reopened): a daily tier still to play, harder first, since the pill has no room for the rest of the frame; else the
 * first option that is not Done (Another on a random board, the next board on a level).
 */
fun pillOption(options: List<NextOption>): NextOption? =
    options.lastOrNull { it.kind == NextKind.DAILY } ?: options.firstOrNull { it.kind != NextKind.DONE }

/**
 * The buttons for a solved board of [level]: [plan] is its boards, [solved] the puzzles solved in it, and
 * [justSolved] the board just finished (the store records it a moment after the win). "Next in level N" names
 * the first unsolved board in plan order; once all five are solved it becomes the level to go on with: the one
 * after, or [current] when this was a replay of a level finished long ago. Then "Home".
 */
fun levelNextOptions(level: Int, plan: List<LevelBoard>, solved: Set<String>, justSolved: String, current: Int): List<NextOption> {
    val done = solved + justSolved
    val next = plan.firstNotNullOfOrNull { b -> PuzzleRegistry.byId(b.puzzleId)?.takeIf { b.puzzleId !in done }?.let { b to it.displayName } }
    val go = if (next != null) {
        val (board, name) = next
        NextOption("Next in level $level", NextKind.LEVEL, board.difficulty, board.puzzleId, level, "$name · ${board.difficulty.label}")
    } else {
        val to = maxOf(level + 1, current)
        NextOption("Level $to", NextKind.LEVEL, null, null, to)
    }
    return listOf(go, NextOption("Home", NextKind.DONE, null))
}

/**
 * The tiers of [puzzleId] already completed on [day]. A random game (`day == null`) never counts as
 * a daily one, so a null [day] has none.
 */
fun tiersDoneOn(completions: List<Completion>, puzzleId: String, day: LocalDate?): Set<Difficulty> =
    if (day == null) emptySet()
    else completions.filter { it.puzzleId == puzzleId && it.day == day }.map { it.difficulty }.toSet()
