package com.joebywan.daybook.core

import kotlin.random.Random

/**
 * Every sound the game makes, synthesised from [SolveTone]'s marimba pluck (no audio files), rendered once and kept, so a
 * play is instant. A tone is an index into the bank (`0 until COUNT`), which is what `rememberTonePlayer` in the platform
 * seam plays. Pure apart from [pick], whose `Random` is the default one: never a generator's `Rng`, so sound cannot move a board.
 */
object Tones {
    /** Each kind's size is its variants, except [WHEEL], whose are the steps of the scale. */
    enum class Kind(val count: Int) { TICK(3), UNIT(3), WHEEL(7), WIN(3), BIG_WIN(3), ALL_TIERS(3) }

    val COUNT: Int = Kind.entries.sumOf { it.count }

    private const val RATE = SolveTone.SAMPLE_RATE
    private const val C5 = 523.25
    private const val D5 = 587.33
    private const val E5 = 659.25
    private const val G5 = 783.99
    private const val A5 = 880.00
    private const val C6 = 1046.50
    private const val D6 = 1174.66
    private const val E6 = 1318.51
    private const val G6 = 1567.98
    private const val C7 = 2093.00

    // C major pentatonic: a letter added on the Word Wheel, one step each. Seven letters is the biggest wheel.
    private val SCALE = doubleArrayOf(C5, D5, E5, G5, A5, C6, D6)
    // A win's variants: the owner's chime, then a hair flat and bright, a hair sharp and round. Still the chime.
    private val PITCH = doubleArrayOf(1.0, 0.992, 1.008)
    private val UPPER = doubleArrayOf(0.348, 0.45, 0.25)

    private fun samples(seconds: Double) = (seconds * RATE).toInt()

    private fun index(kind: Kind, n: Int): Int = Kind.entries.takeWhile { it != kind }.sumOf { it.count } + n

    /** A random variant of [kind], different each play. */
    fun pick(kind: Kind): Int = index(kind, Random.nextInt(kind.count))

    /** The Word Wheel's tone for the [step]th letter of a word (0 the first); a word cannot pass the top of the scale. */
    fun wheel(step: Int): Int = index(Kind.WHEEL, step.coerceIn(0, Kind.WHEEL.count - 1))

    /** Bank entry [i], in -1..1 at [SolveTone.SAMPLE_RATE]. WIN's first entry is the owner's chime, sample for sample. */
    fun samples(i: Int): FloatArray {
        var n = i
        val kind = Kind.entries.first { k -> (n < k.count).also { if (!it) n -= k.count } }
        return when (kind) {
            // A dry knock, under a sixth of the chime's level: heard close up, lost under anything else.
            Kind.TICK -> pluck(doubleArrayOf(1760.0, 1975.5, 2217.5)[n], 0.03, 0.05, upper = 0.0)
            Kind.UNIT -> pluck(doubleArrayOf(E6, G6, C7)[n], 0.22, 0.17, upper = 0.25)
            Kind.WHEEL -> pluck(SCALE[n], 0.14, 0.13, upper = 0.3)
            Kind.WIN -> if (n == 0) SolveTone.samples() else SolveTone.render(SolveTone.chime(PITCH[n]), SolveTone.LENGTH, UPPER[n])
            // The chime carries on up the C major arpeggio.
            Kind.BIG_WIN -> SolveTone.render(
                SolveTone.chime(PITCH[n]) + listOf(
                    SolveTone.Note(E6 * PITCH[n], 0.22, 0.60, 0.15),
                    SolveTone.Note(G6 * PITCH[n], 0.33, 0.70, 0.13),
                ),
                samples(1.06), UPPER[n],
            )
            // On up to the top C, landing on a C major chord under it.
            Kind.ALL_TIERS -> SolveTone.render(
                SolveTone.chime(PITCH[n]) + listOf(
                    SolveTone.Note(E6 * PITCH[n], 0.22, 0.60, 0.14),
                    SolveTone.Note(G6 * PITCH[n], 0.33, 0.70, 0.12),
                    SolveTone.Note(C7 * PITCH[n], 0.44, 0.90, 0.11),
                    SolveTone.Note(C5 * PITCH[n], 0.44, 0.90, 0.11),
                    SolveTone.Note(E5 * PITCH[n], 0.44, 0.90, 0.08),
                    SolveTone.Note(G5 * PITCH[n], 0.44, 0.90, 0.08),
                ),
                samples(1.36), UPPER[n],
            )
        }
    }

    /** [samples] as signed 16-bit PCM, for Android. */
    fun pcm16(i: Int): ShortArray {
        val s = samples(i)
        return ShortArray(s.size) { (s[it] * 32767f).toInt().toShort() }
    }

    private fun pluck(freq: Double, seconds: Double, gain: Double, upper: Double): FloatArray =
        SolveTone.render(listOf(SolveTone.Note(freq, 0.0, seconds, gain)), samples(seconds), upper)
}
