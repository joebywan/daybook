package com.joebywan.daybook.core

import kotlinx.datetime.LocalDate

/**
 * Today's three (`docs/REWARDS.md`): one quick puzzle, one mid-weight, one word or number puzzle, derived from the date
 * alone, so it is the same for everyone and nothing is stored. Each slot steps one place a day through its own list
 * (9, 11 and 5 long today, so a set of three comes round again only after their least common multiple, 495 days).
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
        val mid = ids.filter { it !in QUICK && it !in WORDS }
        return listOf(QUICK.filter { it in ids }, mid, WORDS.filter { it in ids })
            .filter { it.isNotEmpty() }
            .map { it[day.toEpochDays().mod(it.size)] }
    }
}
