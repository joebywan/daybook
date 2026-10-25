package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/**
 * [DailySeed.seedFor] for a `java.time.LocalDate`, so the tests can keep walking dates with
 * `plusDays`. The app itself speaks `kotlinx.datetime.LocalDate` now, which the web build can
 * compile; this goes through that very function, so a test seeded this way sees the app's boards.
 */
fun DailySeed.seedFor(date: java.time.LocalDate, puzzleId: String, difficulty: Difficulty): Long =
    seedFor(date.toKotlinLocalDate(), puzzleId, difficulty)

/** The first day the tests' daily walks start from, unless a test names another. */
val TEST_START: LocalDate = LocalDate.of(2026, 1, 1)

/** The seed of the board [day] days after [start], hashed with [puzzleId] (or a test's own salt). */
fun daySeed(day: Int, puzzleId: String, difficulty: Difficulty, start: LocalDate = TEST_START): Long =
    DailySeed.seedFor(start.plusDays(day.toLong()), puzzleId, difficulty)

/** [count] daily seeds from [start], salted with [salt] so each test draws its own sample. */
fun seeds(count: Int, difficulty: Difficulty, salt: String, start: LocalDate = TEST_START): List<Long> =
    (0 until count).map { daySeed(it, salt, difficulty, start) }

/** Writes [text] to `app/build/reports/[name]`, where the measuring tests leave their numbers. */
fun writeReport(name: String, text: String) {
    java.io.File("build/reports").mkdirs()
    java.io.File("build/reports", name).writeText(text)
}
