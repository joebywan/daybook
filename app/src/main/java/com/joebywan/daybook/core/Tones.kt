package com.joebywan.daybook.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Every sound the game makes, synthesised (no audio files), rendered once and kept, so a play is instant: a win is a marimba
 * chime that grows with the show, a progression (letters, finished units) climbs on the same marimba, and the da-ding is an old handheld's pulse wave with volume in sixteen hard steps. A tone is an index into the bank
 * (`0 until COUNT`), which is what `rememberTonePlayer` in the platform seam plays. Pure apart from [pick], whose `Random` is the
 * default one: never a generator's `Rng`, so sound cannot move a board. Android and the web play identical samples.
 */
object Tones {
    /** Each kind's size is its variants, except [WHEEL] and [STEP], whose are the steps of their scales, and [WORD], one sound. */
    enum class Kind(val count: Int) { STEP(8), WHEEL(7), WIN(3), BIG_WIN(3), ALL_TIERS(3), WORD(1) }

    const val SAMPLE_RATE = 44_100
    const val DA_DING_GAP_MS = 200L

    val COUNT: Int = Kind.entries.sumOf { it.count }

    // Notes are MIDI numbers: 60 is C4, each one a semitone.
    private const val C4 = 60
    private const val C5 = 72
    private const val E5 = 76
    private const val G5 = 79
    private const val C6 = 84
    private const val G6 = 91

    // C major, C5 to G5: a letter added to a word being spelled, one step each, holding on G from the fifth. Seven letters is the biggest wheel.
    private val SCALE = intArrayOf(C5, C5 + 2, E5, E5 + 1, G5, G5, G5)

    // The phrase a board's finished units climb: C major pentatonic below the win, whose first note (G5) is this scale's next.
    private val PHRASE = intArrayOf(C4, C4 + 2, C4 + 4, C4 + 7, C4 + 9, C5, C5 + 2, E5)

    // A win's variants: the chime as first chosen, then a hair flat and bright, a hair sharp and round. Still the chime.
    private val PITCH = doubleArrayOf(1.0, 0.992, 1.008)
    private val UPPER = doubleArrayOf(0.348, 0.45, 0.25)

    private const val OVERSAMPLE = 4

    private fun index(kind: Kind, n: Int): Int = Kind.entries.takeWhile { it != kind }.sumOf { it.count } + n

    /** A random variant of [kind], different each play. */
    fun pick(kind: Kind): Int = index(kind, Random.nextInt(kind.count))

    /** The tone for the [step]th letter of a word being spelled (0 the first); a word cannot pass the top of the scale. */
    fun wheel(step: Int): Int = index(Kind.WHEEL, step.coerceIn(0, Kind.WHEEL.count - 1))

    /** The da-ding: a found word, above every letter whatever its length so spelling one never ends on a drop, and a confirmation ([com.joebywan.daybook.ui.tutorial.LocalChime]). */
    fun word(): Int = index(Kind.WORD, 0)

    /**
     * [play] with a da-ding dropped if one started within [DA_DING_GAP_MS]: a found word a hint named is confirmed by the board and
     * by the hint on the same move, and that is one da-ding, not two doubled. No two words can really be found that close together.
     */
    fun oneDaDing(play: (Int) -> Unit, nowMs: () -> Long = TimeSource.Monotonic.markNow().let { start -> { start.elapsedNow().inWholeMilliseconds } }): (Int) -> Unit {
        var last = -DA_DING_GAP_MS
        return { tone ->
            if (tone != word()) play(tone)
            else nowMs().let { now -> if (now - last >= DA_DING_GAP_MS) { last = now; play(tone) } }
        }
    }

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
            Kind.STEP -> return Marimba.pluck(hz(PHRASE[n].toDouble()), 0.15)
            // Quieter than the phrase: a word has many letters, and they come quickly.
            Kind.WHEEL -> return Marimba.pluck(hz(SCALE[n].toDouble()), 0.12)
            // Da-ding: up a fifth, the second note thin and ringing.
            Kind.WORD -> Chip(0.34).apply {
                pulse(0.0, 0.07, C6, 0.5, rms = 0.08, every = 0.012)
                pulse(0.07, 0.27, G6, 0.25, rms = 0.1, every = 0.018)
            }
            Kind.WIN -> return Marimba.render(Marimba.chime(PITCH[n]), Marimba.LENGTH, UPPER[n])
            // The chime carries on up the C major arpeggio.
            Kind.BIG_WIN -> return Marimba.render(
                Marimba.chime(PITCH[n]) + listOf(
                    Marimba.Note(Marimba.E6 * PITCH[n], 0.22, 0.60, 0.15),
                    Marimba.Note(Marimba.G6 * PITCH[n], 0.33, 0.70, 0.13),
                ),
                (1.06 * SAMPLE_RATE).toInt(), UPPER[n],
            )
            // On up to the top C, landing on a C major chord under it.
            Kind.ALL_TIERS -> return Marimba.render(
                Marimba.chime(PITCH[n]) + listOf(
                    Marimba.Note(Marimba.E6 * PITCH[n], 0.22, 0.60, 0.14),
                    Marimba.Note(Marimba.G6 * PITCH[n], 0.33, 0.70, 0.12),
                    Marimba.Note(Marimba.C7 * PITCH[n], 0.44, 0.90, 0.11),
                    Marimba.Note(Marimba.C5 * PITCH[n], 0.44, 0.90, 0.11),
                    Marimba.Note(Marimba.E5 * PITCH[n], 0.44, 0.90, 0.08),
                    Marimba.Note(Marimba.G5 * PITCH[n], 0.44, 0.90, 0.08),
                ),
                (1.36 * SAMPLE_RATE).toInt(), UPPER[n],
            )
        }.finished()
    }

    /** [samples] as signed 16-bit PCM, for Android. */
    fun pcm16(i: Int): ShortArray {
        val s = samples(i)
        return ShortArray(s.size) { (s[it] * 32767f).toInt().toShort() }
    }

    private fun hz(note: Double): Double = 440.0 * 2.0.pow((note - 69) / 12)

    /** A win or a progression's note: plucked bars, a fundamental plus a partial at four times it, both decaying, the upper about three times faster. */
    private object Marimba {
        const val LENGTH = 35_720
        const val C5 = 523.25
        const val E5 = 659.25
        const val G5 = 783.99
        const val C6 = 1046.50
        const val E6 = 1318.51
        const val G6 = 1567.98
        const val C7 = 2093.00

        private const val PLUCK = 0.4
        private const val DECAY = 7.05
        private const val UPPER_DECAY = 22.0
        private const val ATTACK = 0.002
        private const val RELEASE = 0.002

        class Note(val freq: Double, val start: Double, val length: Double, val gain: Double)

        fun chime(pitch: Double): List<Note> = listOf(Note(G5 * pitch, 0.0, 0.50, 0.218), Note(C6 * pitch, 0.11, 0.61, 0.184))

        /** One bar struck, for a progression. */
        fun pluck(freq: Double, gain: Double): FloatArray =
            render(listOf(Note(freq, 0.0, PLUCK, gain)), (PLUCK * SAMPLE_RATE).toInt(), UPPER[0])

        fun render(notes: List<Note>, length: Int, upper: Double): FloatArray {
            val out = FloatArray(length)
            for (n in notes) {
                val first = (n.start * SAMPLE_RATE).toInt()
                val last = min(length, ((n.start + n.length) * SAMPLE_RATE).toInt())
                for (i in first until last) {
                    val u = i.toDouble() / SAMPLE_RATE - n.start
                    val body = exp(-DECAY * u) * sin(2 * PI * n.freq * u) +
                        upper * exp(-UPPER_DECAY * u) * sin(2 * PI * n.freq * 4 * u)
                    val fadeIn = min(u / ATTACK, 1.0)
                    val fadeOut = min((n.length - u) / RELEASE, 1.0)
                    out[i] += (n.gain * fadeIn * fadeOut * body).toFloat()
                }
            }
            return out
        }
    }

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
         * [note]. High [duty] of the time, low the rest, with no DC,
         * so a note starting or a volume step is no thump. Averaged over [OVERSAMPLE] so high notes do not alias into wrong pitches.
         */
        fun pulse(at: Double, seconds: Double, note: Int, duty: Double, rms: Double, every: Double) {
            val span = rms / sqrt(duty * (1 - duty))
            var phase = 0.0
            val step = hz(note.toDouble()) / SAMPLE_RATE / OVERSAMPLE
            voice(at, seconds) { u, _ ->
                var sum = 0.0
                repeat(OVERSAMPLE) {
                    phase = (phase + step) % 1.0
                    sum += if (phase < duty) 1 - duty else -duty
                }
                span * volume(u, every) * sum / OVERSAMPLE
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
