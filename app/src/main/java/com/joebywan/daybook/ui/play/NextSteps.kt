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

    /** Today's daily of another puzzle, [NextOption.puzzleId], at [NextOption.difficulty] ([nextToday]). */
    TODAY,

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
 * A daily offers Easier daily, Random (same tier), Harder daily, Done. The two dailies are the same
 * date at another tier and are never offered for a tier already done that day: the nearest undone
 * tier in that direction is offered instead, and the button is dropped if nothing in that direction
 * is undone. [doneTiers] need not include [tier]; the board just solved counts as done regardless,
 * because the store records it asynchronously. [today] (offered on today's own daily only: [nextToday]) leads the list
 * as "Next puzzle" (named under it), the frame's main button.
 */
fun nextOptions(daily: Boolean, tier: Difficulty, doneTiers: Set<Difficulty>, today: String? = null): List<NextOption> {
    val tiers = Difficulty.entries
    val here = tiers.indexOf(tier)
    return if (daily) {
        val done = doneTiers + tier
        val easier = tiers.take(here).lastOrNull { it !in done }
        val harder = tiers.drop(here + 1).firstOrNull { it !in done }
        buildList {
            today?.let { id ->
                val name = PuzzleRegistry.byId(id)?.displayName ?: id
                // The tier is the one just played; the pill has room for the name alone.
                add(NextOption("Next puzzle", NextKind.TODAY, tier, id, note = name))
            }
            if (easier != null) add(NextOption("Easier daily", NextKind.DAILY, easier))
            add(NextOption("Random", NextKind.RANDOM, tier))
            if (harder != null) add(NextOption("Harder daily", NextKind.DAILY, harder))
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
 * The next of today's dailies at [tier] after solving [justSolved]: the first of [three] (today's three) not yet done, else
 * the first of [order] (the home grid's) not yet done; null once every one is. [done] is today's (puzzle, tier) solves.
 */
fun nextToday(three: List<String>, order: List<String>, tier: Difficulty, done: Set<Pair<String, Difficulty>>, justSolved: String): String? =
    (three + order).firstOrNull { it != justSolved && (it to tier) !in done }

/**
 * The button the finished frame fills in: going on, with the level or with today's next puzzle, since that is what the
 * player came for; otherwise "Done"/"Home", which is the safe default.
 */
fun mainOption(options: List<NextOption>): NextOption? =
    options.firstOrNull { it.kind == NextKind.LEVEL || it.kind == NextKind.TODAY } ?: options.firstOrNull { it.kind == NextKind.DONE }

/**
 * The one way on that the results pill shows beside Done, once the frame is hidden (as it always is on a solved board
 * reopened): today's next puzzle; else a daily tier still to play, harder first, since the pill has no room for the rest
 * of the frame; else the first option that is not Done (Another on a random board, the next board on a level).
 */
fun pillOption(options: List<NextOption>): NextOption? =
    options.firstOrNull { it.kind == NextKind.TODAY } ?: options.lastOrNull { it.kind == NextKind.DAILY }
        ?: options.firstOrNull { it.kind != NextKind.DONE }

/**
 * The tiles beside "Show results". A daily's are the way on and Random, so a solved daily reopened always offers another game;
 * there is no room for Done as well, and the back arrow is the same thing. Every other board keeps its way on and its Done.
 */
fun pillOptions(options: List<NextOption>): List<NextOption> {
    val next = pillOption(options)
    val random = options.firstOrNull { it.label == "Random" }
    return if (random != null && next != random) listOfNotNull(next, random) else listOfNotNull(next, options.last())
}

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
