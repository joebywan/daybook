package com.joebywan.daybook.core

import java.time.LocalDate

/**
 * Turns a date into a puzzle seed.
 *
 * Every daily puzzle is generated locally from these seeds, so the "archive" is simply any date you
 * care to ask for — there is no server, no paywall on past days, and no network permission in the
 * manifest at all.
 */
object DailySeed {

    /** Day zero for the app's own calendar. Daily puzzles exist from here onward. */
    val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)

    fun seedFor(date: LocalDate, puzzleId: String, difficulty: Difficulty): Long {
        var h = date.toEpochDay() * 0x100000001B3L
        h = mix(h xor puzzleId.stableHash())
        h = mix(h xor (difficulty.ordinal + 1).toLong())
        return h
    }

    /** Random games use a fresh seed each time; callers pass a counter or nanotime. */
    fun randomSeed(puzzleId: String, difficulty: Difficulty, nonce: Long): Long =
        mix(nonce xor puzzleId.stableHash() xor (difficulty.ordinal * 0x9E3779B9L))

    /**
     * [String.hashCode] is specified by the JDK, but this keeps seeding self-contained so puzzle
     * ids hash identically regardless of platform.
     */
    private fun String.stableHash(): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in this) {
            h = (h xor c.code.toLong()) * 0x100000001B3L
        }
        return h
    }

    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr 33)) * -0x7ee3623a03d3c83fL
        z = (z xor (z ushr 29)) * -0x3b314601e57a13adL
        return z xor (z ushr 32)
    }
}
