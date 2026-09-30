package com.joebywan.daybook.core

import kotlinx.datetime.LocalDate

/**
 * Turns a date into a puzzle seed.
 *
 * Every daily puzzle is generated locally from these seeds, so the "archive" is simply any date you
 * care to ask for — there is no server, no paywall on past days, and no network permission in the
 * manifest at all.
 *
 * `kotlinx.datetime.LocalDate` rather than `java.time`'s so the web build compiles this same file;
 * its epoch-day count is the same number, which `CompletionFormatTest` checks day by day.
 */
object DailySeed {

    /** Day zero for the app's own calendar. Daily puzzles exist from here onward. */
    val EPOCH: LocalDate = LocalDate(2026, 1, 1)

    fun seedFor(date: LocalDate, puzzleId: String, difficulty: Difficulty): Long =
        SeedHash.daily(date.toEpochDays(), puzzleId, difficulty)

    /** Random games use a fresh seed each time; callers pass a counter or nanotime. */
    fun randomSeed(puzzleId: String, difficulty: Difficulty, nonce: Long): Long =
        SeedHash.random(puzzleId, difficulty, nonce)
}
