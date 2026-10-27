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

    /**
     * Every epoch day the app accepts from outside: a stored solve, a backup, a `?date=` link. Fixed bounds rather than
     * "today" keep decoding pure, and they are wide (tests and old records hold days before [EPOCH]): what matters is
     * that they are bounded, so a crafted day can neither throw (`fromEpochDays` of a huge number does) nor send the
     * streak walk across billions of days.
     */
    val DAYS: LongRange = LocalDate(2000, 1, 1).toEpochDays()..LocalDate(2200, 1, 1).toEpochDays()

    /** A date from a link (`?date=`, `?day=`): null when it does not parse or lies outside [DAYS]. */
    fun fromLink(raw: String?): LocalDate? =
        raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.toEpochDays() in DAYS }

    fun seedFor(date: LocalDate, puzzleId: String, difficulty: Difficulty): Long =
        SeedHash.daily(date.toEpochDays(), puzzleId, difficulty)
}
