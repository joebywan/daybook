package com.joebywan.daybook.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Every sound the game makes, synthesised as an old handheld's sound chip would (no audio files), rendered once and kept, so a
 * play is instant: pulse waves, a stepped triangle, LFSR noise, volume in sixteen hard steps. A tone is an index into the bank
 * (`0 until COUNT`), which is what `rememberTonePlayer` in the platform seam plays. Pure apart from [pick], whose `Random` is the
 * default one: never a generator's `Rng`, so sound cannot move a board. Android and the web play identical samples.
 */
object Tones {
    /** Each kind's size is its variants, except [WHEEL] and [STEP], whose are the steps of their scales, and [WORD], one sound. */
    enum class Kind(val count: Int) { STEP(8), WHEEL(7), WIN(3), BIG_WIN(3), ALL_TIERS(3), WORD(1) }

    const val SAMPLE_RATE = 44_100

    val COUNT: Int = Kind.entries.sumOf { it.count }

    // Notes are MIDI numbers: 60 is C4, each one a semitone.
    private const val D3 = 50
    private const val G3 = 55
    private const val A3 = 57
    private const val C4 = 60
    private const val C5 = 72
    private const val E5 = 76
    private const val G5 = 79
        private const val C6 = 84
        private const val E6 = 88
    private const val F6 = 89
    private const val G6 = 91
    private const val A6 = 93
    private const val B6 = 95
    private const val C7 = 96

    // C major, C5 to G5: a letter added to a word being spelled, one step each, holding on G from the fifth. Seven letters is the biggest wheel.
    private val SCALE = intArrayOf(C5, C5 + 2, E5, E5 + 1, G5, G5, G5)

    // The phrase a board's finished units climb: C major pentatonic below the win, whose first note (G5) is this scale's next.
    private val PHRASE = intArrayOf(C4, C4 + 2, C4 + 4, C4 + 7, C4 + 9, C5, C5 + 2, E5)

    // A win's variants: one tune, its lead a different pulse width and the landing chord broken a different way.
    private val LEAD = doubleArrayOf(0.25, 0.125, 0.5)
    private val BROKEN = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(2, 1, 0))

    private const val ARP_SECONDS = 0.025
    private const val OVERSAMPLE = 4

    private fun index(kind: Kind, n: Int): Int = Kind.entries.takeWhile { it != kind }.sumOf { it.count } + n

    /** A random variant of [kind], different each play. */
    fun pick(kind: Kind): Int = index(kind, Random.nextInt(kind.count))

    /** The tone for the [step]th letter of a word being spelled (0 the first); a word cannot pass the top of the scale. */
    fun wheel(step: Int): Int = index(Kind.WHEEL, step.coerceIn(0, Kind.WHEEL.count - 1))

    /** The da-ding: a found word, above every letter whatever its length so spelling one never ends on a drop, and a confirmation ([com.joebywan.daybook.ui.tutorial.LocalChime]). */
    fun word(): Int = index(Kind.WORD, 0)

    /**
     * Where a board stands in its phrase, from what the rules show alone: [done] of its [total] units finished. The bottom note
     * for the first unit and the top for the last one that does not also solve the board (the win has that one), whatever the
     * count, so it never leaves the scale; a big board repeats notes, an undo steps back down.
     */
    fun phraseStep(done: Int, total: Int): Int {
        val top = Kind.STEP.count - 1
        return if (total <= 2) 0 else ((done - 1).coerceAtLeast(0) * top / (total - 2)).coerceAtMost(top)
    }

    /** The phrase's tone for a [phraseStep]. */
    fun step(step: Int): Int = index(Kind.STEP, step.coerceIn(0, Kind.STEP.count - 1))

    /** Bank entry [i], in -1..1 at [SAMPLE_RATE]. */
    fun samples(i: Int): FloatArray {
        var n = i
        val kind = Kind.entries.first { k -> (n < k.count).also { if (!it) n -= k.count } }
        return when (kind) {
            Kind.STEP -> Chip(0.15).apply {
                // One pitch, wide then thin, sliding up a hair, on a note of the phrase.
                pulse(0.0, 0.04, intArrayOf(PHRASE[n]), 0.5, rms = 0.09, every = 0.02)
                pulse(0.04, 0.11, intArrayOf(PHRASE[n]), 0.25, rms = 0.1, every = 0.008, sweep = 6.0)
            }
            // A plain beep, faded right out by its end.
            Kind.WHEEL -> Chip(0.085).apply { pulse(0.0, 0.085, intArrayOf(SCALE[n]), 0.5, rms = 0.065, every = 0.0056) }
            // Da-ding: up a fifth, the second note thin and ringing.
            Kind.WORD -> Chip(0.34).apply {
                pulse(0.0, 0.07, intArrayOf(C6), 0.5, rms = 0.08, every = 0.012)
                pulse(0.07, 0.27, intArrayOf(G6), 0.25, rms = 0.1, every = 0.018)
            }
            Kind.WIN -> Chip(0.7).apply {
                rise(n)
                triangle(0.05, 0.15, G3)
                drum(0.21)
                land(0.21, 0.49, chord(n, C6, E6, G6), n, every = 0.035)
                triangle(0.21, 0.42, C4)
            }
            Kind.BIG_WIN -> Chip(1.1).apply {
                rise(n)
                run(n, intArrayOf(G6, E6, G6, A6), 0.21)
                triangle(0.05, 0.15, C4)
                triangle(0.21, 0.13, G3)
                triangle(0.35, 0.13, A3)
                drum(0.21)
                drum(0.49)
                land(0.49, 0.61, chord(n, E6, G6, C7), n, every = 0.042)
                triangle(0.49, 0.55, C4)
            }
            Kind.ALL_TIERS -> Chip(1.5).apply {
                rise(n)
                run(n, intArrayOf(G6, E6, G6, A6, B6, C7), 0.21)
                // The same run with a harmony under it, then both land together on the crash.
                for ((k, note) in intArrayOf(E5, G5, C6, E6, C6, E6, F6, G6, A6).withIndex()) {
                    pulse(k * 0.07, 0.065, intArrayOf(note), 0.5, rms = 0.05, every = 0.02)
                }
                triangle(0.05, 0.15, C4)
                triangle(0.21, 0.14, G3)
                triangle(0.35, 0.14, A3)
                triangle(0.49, 0.13, G3)
                drum(0.21)
                drum(0.49)
                noise(0.63, 0.75, clock = 14_000.0, rms = 0.1, every = 0.05)
                land(0.63, 0.75, chord(n, E6, G6, C7), n, every = 0.05, rms = 0.11)
                pulse(0.63, 0.75, intArrayOf(C6), 0.5, rms = 0.05, every = 0.05)
                pulse(0.63, 0.75, intArrayOf(G5), 0.5, rms = 0.04, every = 0.05)
                // Rings on alone a moment after everything else has gone, under the top of the fanfare.
                triangle(0.63, 0.87, C4, every = 0.1)
            }
        }.finished()
    }

    /** [samples] as signed 16-bit PCM, for Android. */
    fun pcm16(i: Int): ShortArray {
        val s = samples(i)
        return ShortArray(s.size) { (s[it] * 32767f).toInt().toShort() }
    }

    private fun chord(variant: Int, a: Int, b: Int, c: Int): IntArray = intArrayOf(a, b, c).let { n -> IntArray(3) { n[BROKEN[variant][it]] } }

    private fun hz(note: Double): Double = 440.0 * 2.0.pow((note - 69) / 12)

    /** The tune every win opens with, on a kick: a bass note falling fast under a click of noise, the board's thump heard. */
    private fun Chip.rise(variant: Int) {
        triangle(0.0, 0.09, D3, every = 0.006, peak = 0.16, sweep = -220.0)
        noise(0.0, 0.012, clock = 6_000.0, rms = 0.03, every = 0.001)
        run(variant, intArrayOf(G5, C6, E6), 0.0)
    }

    private fun Chip.run(variant: Int, notes: IntArray, from: Double) {
        for ((k, note) in notes.withIndex()) pulse(from + k * 0.07, 0.065, intArrayOf(note), LEAD[variant], rms = 0.11, every = 0.02)
    }

    private fun Chip.land(at: Double, seconds: Double, chord: IntArray, variant: Int, every: Double, rms: Double = 0.13) =
        pulse(at, seconds, chord, LEAD[variant], rms = rms, every = every)

    private fun Chip.drum(at: Double) = noise(at, 0.03, clock = 16_000.0, rms = 0.05, every = 0.004)

    /** A buffer the voices add into. Each voice's level is its RMS at full volume, so a thin pulse is as loud as a wide one. */
    private class Chip(seconds: Double) {
        private val out = FloatArray((seconds * SAMPLE_RATE).toInt())

        // Volume is a whole step of 15, falling one every [every] seconds, as the chips' envelopes do: hard steps, no ramp.
        private fun volume(u: Double, every: Double): Double = max(0.0, 15 - floor(u / every)) / 15

        private inline fun voice(at: Double, seconds: Double, sample: (u: Double, i: Int) -> Double) {
            val first = (at * SAMPLE_RATE).toInt()
            for (i in first until min(out.size, ((at + seconds) * SAMPLE_RATE).toInt())) {
                out[i] += sample((i - first).toDouble() / SAMPLE_RATE, i - first).toFloat()
            }
        }

        /**
         * [notes] cycled fast (one note just holds), [sweep] semitones a second. High [duty] of the time, low the rest, with no DC,
         * so a note starting or a volume step is no thump. Averaged over [OVERSAMPLE] so high notes do not alias into wrong pitches.
         */
        fun pulse(at: Double, seconds: Double, notes: IntArray, duty: Double, rms: Double, every: Double, sweep: Double = 0.0) {
            val span = rms / sqrt(duty * (1 - duty))
            var phase = 0.0
            voice(at, seconds) { u, _ ->
                val step = hz(notes[(u / ARP_SECONDS).toInt() % notes.size] + sweep * u) / SAMPLE_RATE / OVERSAMPLE
                var sum = 0.0
                repeat(OVERSAMPLE) {
                    phase = (phase + step) % 1.0
                    sum += if (phase < duty) 1 - duty else -duty
                }
                span * volume(u, every) * sum / OVERSAMPLE
            }
        }

        /** The chips' triangle: 32 steps a cycle through sixteen levels, its volume fixed on the real ones; here it decays as a bass. */
        fun triangle(at: Double, seconds: Double, note: Int, every: Double = 0.05, peak: Double = 0.08, sweep: Double = 0.0) {
            var phase = 0.0
            voice(at, seconds) { u, _ ->
                phase = (phase + hz(note + sweep * u) / SAMPLE_RATE) % 1.0
                val k = (phase * 32).toInt()
                val step = if (k < 16) 15 - k else k - 16
                peak * volume(u, every) * (step / 7.5 - 1)
            }
        }

        /** A 15-bit LFSR clocked [clock] times a second, one bit the output. */
        fun noise(at: Double, seconds: Double, clock: Double, rms: Double, every: Double = 1.0) {
            var lfsr = 1
            var due = 0.0
            voice(at, seconds) { u, _ ->
                due += clock / SAMPLE_RATE
                while (due >= 1) {
                    lfsr = (lfsr shr 1) or (((lfsr xor (lfsr shr 1)) and 1) shl 14)
                    due -= 1
                }
                rms * volume(u, every) * (if (lfsr and 1 == 1) 1 else -1)
            }
        }

        /** The buffer, its first and last milliseconds faded so a hard step cannot click at either end of the file. */
        fun finished(): FloatArray {
            val fade = SAMPLE_RATE / 1000
            for (i in 0 until min(fade, out.size)) {
                val share = i.toFloat() / fade
                out[i] *= share
                out[out.size - 1 - i] *= share
            }
            return out
        }
    }
}
