package com.joebywan.daybook.core

/**
 * Deterministic splitmix64 PRNG.
 *
 * Hand-rolled on purpose: [java.util.Random] and [kotlin.random.Random] are free to change their
 * internals between platform versions, and every daily puzzle in this app is *generated* from a
 * seed rather than downloaded. If the generator drifted, two phones on the same date would see
 * different puzzles. This will not drift.
 */
class Rng(seed: Long) {

    private var state: Long = if (seed == 0L) GOLDEN else seed

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in `[0, bound)`. Rejection-samples so low bits stay unbiased. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        val limit = Int.MAX_VALUE - (Int.MAX_VALUE % bound)
        while (true) {
            val candidate = (nextLong() ushr 33).toInt()
            if (candidate < limit) return candidate % bound
        }
    }

    /** Uniform in `[min, max]`. */
    fun nextInt(min: Int, max: Int): Int = min + nextInt(max - min + 1)

    fun nextBoolean(): Boolean = nextLong() < 0L

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    fun <T> shuffled(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }

    private companion object {
        const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
    }
}
