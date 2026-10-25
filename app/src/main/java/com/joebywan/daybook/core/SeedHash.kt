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
        if (puzzleId == "words" && epochDay >= LEXICON_CYCLE_FROM) {
            return (LEXICON_CYCLE shl 32) or (difficulty.ordinal.toLong() shl 28) or (epochDay - LEXICON_CYCLE_FROM)
        }
        var h = epochDay * 0x100000001B3L
        h = mix(h xor puzzleId.stableHash())
        h = mix(h xor (difficulty.ordinal + 1).toLong())
        return h
    }

    /**
     * Lexicon's daily words go round a cycle from this day on (2026-11-01), so a word cannot come back until its tier
     * has used every other: a hashed seed gave even odds of a repeat within two months. The seed is then not a hash but
     * the day itself, tagged ([lexiconCycleDay] reads it back), and `LexiconRules.newBoard` maps it through a fixed
     * shuffle of the answer list. Days before keep their hashed seeds, so the archive, saved games and completions made
     * before the change are untouched.
     */
    val LEXICON_CYCLE_FROM: Long = epochDay(2026, 11, 1)
    private const val LEXICON_CYCLE = 0x4C784379L // "LxCy"

    /** The cycle's day count (0 on [LEXICON_CYCLE_FROM]) of a Lexicon daily seed, or null for any other seed. */
    fun lexiconCycleDay(seed: Long): Long? =
        if (seed ushr 32 == LEXICON_CYCLE) seed and 0x0FFFFFFFL else null

    fun random(puzzleId: String, difficulty: Difficulty, nonce: Long): Long =
        mix(nonce xor puzzleId.stableHash() xor (difficulty.ordinal * 0x9E3779B9L))

    /**
     * The seed of one board of a level ([Levels]). Started from its own constant and mixed before the
     * puzzle id goes in, so a level board is not a daily board of some day nor a random board.
     */
    fun level(level: Int, puzzleId: String, difficulty: Difficulty): Long {
        var h = mix(level.toLong() * 0x100000001B3L xor LEVEL)
        h = mix(h xor puzzleId.stableHash())
        h = mix(h xor (difficulty.ordinal + 1).toLong())
        return h
    }

    /** The seed of a draw [Levels] makes: [index] is a deck or a level number, [salt] says which draw. */
    fun levelPick(index: Int, salt: Long): Long = mix(mix(index.toLong() xor LEVEL_PICK) xor salt)

    private const val LEVEL = 0x4C6576656C730001L // "Levels" and a version
    private const val LEVEL_PICK = 0x4C65766C50696B01L

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
