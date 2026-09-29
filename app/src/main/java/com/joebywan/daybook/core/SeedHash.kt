package com.joebywan.daybook.core

/**
 * The arithmetic behind [DailySeed], with no platform types in it.
 *
 * Split out of [DailySeed] so that the web build compiles this very file rather than a copy of it:
 * [DailySeed] speaks `java.time.LocalDate`, which Kotlin/Wasm does not have, while everything that
 * decides which board a date gets lives here and takes the date as an epoch day. Two copies of this
 * code would be two chances for a phone and a browser to disagree about today's puzzle.
 */
object SeedHash {

    fun daily(epochDay: Long, puzzleId: String, difficulty: Difficulty): Long {
        var h = epochDay * 0x100000001B3L
        h = mix(h xor puzzleId.stableHash())
        h = mix(h xor (difficulty.ordinal + 1).toLong())
        return h
    }

    fun random(puzzleId: String, difficulty: Difficulty, nonce: Long): Long =
        mix(nonce xor puzzleId.stableHash() xor (difficulty.ordinal * 0x9E3779B9L))

    /**
     * Days since 1970-01-01 in the proleptic Gregorian calendar — the same number
     * `LocalDate.of(year, month, day).toEpochDay()` gives, which a JVM test checks day by day.
     * Howard Hinnant's days-from-civil; [month] is 1-12.
     */
    fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = y.floorDiv(400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

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
