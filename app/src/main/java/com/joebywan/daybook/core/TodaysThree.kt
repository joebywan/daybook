package com.joebywan.daybook.core

import kotlinx.datetime.LocalDate

/**
 * Today's three (`docs/REWARDS.md`): one quick puzzle, one mid-weight, one word or number puzzle, derived from the date
 * alone, so it is the same for everyone and nothing is stored. Each slot steps one place a day through its own list
 * (quick 11, words 9: coprime, so those two come round together every 99 days; the mid-weight list takes every other
 * puzzle, 15 now). A mid-weight list coprime with 99 just steps too, and no puzzle waits longer than its list's length;
 * one that shares 3 or 11 with it, as 15 does, steps one more each time the 99-day quick-and-words cycle comes round
 * (`d mod 99 + d / 99`), so no set repeats within the year whatever its length, at the cost of one puzzle's gap doubling
 * at each turn of that cycle. Plain list indexing: no hash order, so the JVM and the web agree.
 *
 * The slots were sorted by what each puzzle asks (2026-10-26): quick is one gesture, or few rules and about a dozen hint
 * steps on Standard (`docs/TEACHING.md`, Tier depth); mid-weight is a longer chain (Mambo's 30 steps, Sudoku's 53) or
 * deeper reading (Mate, Codebreaker, Who's Who); words and numbers is what it says, crosswords included.
 *
 * Changing a list (or adding a puzzle) moves every later day's three, and can make the "Today's three" achievement read
 * unearned for an old day, as adding a puzzle does to the registry-wide achievements.
 *
 * A new player (fewer than [NEWCOMER_SOLVES] solves) gets [starter] instead: the same quick slot, with the mid-weight and
 * word slots drawn from the gentlest of their kind. The achievement accepts either three for a day.
 */
object TodaysThree {
    /** Quick: one gesture, or few rules and about a dozen hint steps on Standard; a Standard board in a few minutes. */
    val QUICK = listOf("pointers", "pipes", "snap", "sand", "sets", "mosaic", "kings", "tents", "shikaku", "atoms", "lamps")

    /** Words and numbers. */
    val WORDS = listOf("words", "wordwheel", "mini", "target", "cipher", "codeword", "tally", "hexwords", "dwindle")

    /** Below this many solves in all, a player is new and gets [starter]. */
    const val NEWCOMER_SOLVES = 5

    /** The gentlest mid-weight puzzles (the first ones `PuzzleOrder.GENTLEST` names that are neither quick nor words). */
    val STARTER_MID = listOf("sudoku", "nonogram", "mines")

    /** The gentlest word and number puzzles, in `PuzzleOrder.GENTLEST`'s order. */
    val STARTER_WORDS = listOf("words", "wordwheel", "mini", "target")

    /** A new player's three for [day]: [of]'s quick slot, then a gentle mid-weight and a gentle word one. Shared by every newcomer. */
    fun starter(day: LocalDate, ids: List<String> = PuzzleRegistry.all.map { it.id }): List<String> {
        val d = day.toEpochDays()
        fun List<String>.at(i: Long) = filter { it in ids }.let { if (it.isEmpty()) null else it[i.mod(it.size)] }
        return listOfNotNull(QUICK.at(d), STARTER_MID.at(d), STARTER_WORDS.at(d))
    }

    /** The three for [day], quick first, from the puzzles [ids] (the registry's, in its order); everything else is mid-weight. */
    fun of(day: LocalDate, ids: List<String> = PuzzleRegistry.all.map { it.id }): List<String> {
        val quick = QUICK.filter { it in ids }
        val words = WORDS.filter { it in ids }
        val mid = ids.filter { it !in QUICK && it !in WORDS }
        val d = day.toEpochDays()
        val cycle = maxOf(1, quick.size * words.size).toLong()
        tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        val m = if (mid.isEmpty() || gcd(cycle, mid.size.toLong()) == 1L) d else d.mod(cycle) + d.floorDiv(cycle)
        fun List<String>.at(i: Long) = if (isEmpty()) null else this[i.mod(size)]
        return listOfNotNull(quick.at(d), mid.at(m), words.at(d))
    }
}
