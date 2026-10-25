package com.joebywan.daybook.core

import kotlinx.datetime.LocalDate

/**
 * Today's three (`docs/REWARDS.md`): one quick puzzle, one mid-weight, one word or number puzzle, derived from the date
 * alone, so it is the same for everyone and nothing is stored. The quick and word slots step one place a day through their
 * lists (9 and 5 long, coprime, so they pair off afresh for 45 days); the mid-weight slot steps one place a day too, and
 * one more each time that 45-day cycle comes round (`d mod 45 + d / 45`). So a set of three comes round again only after
 * 45 times the mid-weight list's length, over a year for any list of 9 or more, whatever that length's factors (plain
 * `d mod n` in every slot repeated within the year once a new puzzle gave the mid-weight list a factor of 3 or 5).
 * Plain list indexing: no hash order, so the JVM and the web agree.
 *
 * Changing a list (or adding a puzzle, which joins the mid-weight list) moves every later day's three, and can make the
 * "Today's three" achievement read unearned for an old day, as adding a puzzle does to the registry-wide achievements.
 */
object TodaysThree {
    /** Quick: one gesture or few rules, a Standard board in a few minutes. */
    val QUICK = listOf("pointers", "pipes", "snap", "sand", "kings", "mambo", "sets", "mosaic", "tents")

    /** Words and numbers. */
    val WORDS = listOf("words", "wordwheel", "target", "cipher", "tally")

    /** The three for [day], quick first, from the puzzles [ids] (the registry's, in its order); everything else is mid-weight. */
    fun of(day: LocalDate, ids: List<String> = PuzzleRegistry.all.map { it.id }): List<String> {
        val quick = QUICK.filter { it in ids }
        val words = WORDS.filter { it in ids }
        val mid = ids.filter { it !in QUICK && it !in WORDS }
        val d = day.toEpochDays().toLong()
        val cycle = maxOf(1, quick.size * words.size).toLong()
        fun List<String>.at(i: Long) = if (isEmpty()) null else this[i.mod(size)]
        return listOfNotNull(quick.at(d), mid.at(d.mod(cycle) + d.floorDiv(cycle)), words.at(d))
    }
}
