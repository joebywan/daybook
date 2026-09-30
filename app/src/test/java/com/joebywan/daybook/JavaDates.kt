package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import kotlinx.datetime.toKotlinLocalDate

/**
 * [DailySeed.seedFor] for a `java.time.LocalDate`, so the tests can keep walking dates with
 * `plusDays`. The app itself speaks `kotlinx.datetime.LocalDate` now, which the web build can
 * compile; this goes through that very function, so a test seeded this way sees the app's boards.
 */
fun DailySeed.seedFor(date: java.time.LocalDate, puzzleId: String, difficulty: Difficulty): Long =
    seedFor(date.toKotlinLocalDate(), puzzleId, difficulty)
