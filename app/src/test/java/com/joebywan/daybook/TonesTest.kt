package com.joebywan.daybook

import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.puzzles.wheelToneStep
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** All measured off the rendered samples, none of it asking the synth what it meant to do. */
class TonesTest {
    private val rate = Tones.SAMPLE_RATE

    @Test
    fun `a hinted word is one da-ding, and only the da-ding is held back`() {
        val played = mutableListOf<Int>()
        var now = 0L
        val tones = Tones.oneDaDing(played::add) { now }
        val letter = Tones.wheel(0)

        tones(Tones.word()) // the very first one is not swallowed
        now = 16
        tones(Tones.word()) // the same move's other confirmation
        tones(letter)
        tones(letter)
        assertEquals(listOf(Tones.word(), letter, letter), played)

        now = Tones.DA_DING_GAP_MS
        tones(Tones.word()) // the next word
        assertEquals(listOf(Tones.word(), letter, letter, Tones.word()), played)
    }

    // Bank entries of a kind, from the same public index pick() draws from: every index it can return, found by asking.
    private fun of(kind: Tones.Kind): List<Int> = (1..400).mapTo(HashSet()) { Tones.pick(kind) }.sorted()
    private fun hz(s: FloatArray): Double {
        val n = (0.1 * rate).toInt().coerceAtMost(s.size)
        val crossings = (1 until n).count { (s[it - 1] < 0) != (s[it] < 0) }
        return crossings / 2.0 / (n.toDouble() / rate)
    }
    private fun FloatArray.window(from: Double, to: Double) = copyOfRange((from * rate).toInt(), (to * rate).toInt())
    private fun rms(s: FloatArray) = sqrt(s.sumOf { it.toDouble() * it } / s.size)
    private fun loudest(s: FloatArray) = (0 until s.size - 882 step 441).maxOf { rms(s.copyOfRange(it, it + 882)) }

    // Goertzel: the strength of one frequency in a window.
    private fun strength(s: FloatArray, freq: Double): Double {
        val c = 2 * cos(2 * PI * freq / rate)
        var a = 0.0
        var b = 0.0
        for (x in s) { val next = x + c * a - b; b = a; a = next }
        return a * a + b * b - c * a * b
    }
    private fun noteHz(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    @Test fun everyToneIsPureAndStartsAndEndsWithoutAClick() {
        for (i in 0 until Tones.COUNT) {
            val s = Tones.samples(i)
            assertArrayEquals(s, Tones.samples(i), 0f)
            assertEquals(0f, s[0], 0f)
            assertTrue("tone $i ends at ${s.last()}", abs(s.last()) < 0.002f)
            assertTrue("tone $i clips", s.all { abs(it) < 1f })
            assertEquals("tone $i in 16 bits", s.size, Tones.pcm16(i).size)
        }
    }

    @Test fun lengthsAndLevelsKeepEachKindInItsPlace() {
        fun peak(k: Tones.Kind) = of(k).maxOf { i -> Tones.samples(i).maxOf { abs(it) } }
        fun seconds(k: Tones.Kind) = Tones.samples(of(k).first()).size / rate.toDouble()
        fun loud(k: Tones.Kind) = of(k).maxOf { loudest(Tones.samples(it)) }
        assertTrue(seconds(Tones.Kind.WORD) <= 0.4)
        assertTrue(peak(Tones.Kind.WORD) <= 0.25f)
        assertEquals(0.7, seconds(Tones.Kind.WIN), 0.05)
        assertEquals(1.1, seconds(Tones.Kind.BIG_WIN), 0.05)
        assertEquals(1.5, seconds(Tones.Kind.ALL_TIERS), 0.05)
        assertTrue("no win startles", peak(Tones.Kind.WIN) <= 0.5f && peak(Tones.Kind.BIG_WIN) <= 0.5f && peak(Tones.Kind.ALL_TIERS) <= 0.6f)
        assertTrue("a win is heard", peak(Tones.Kind.WIN) >= 0.2f)
        // A square wave is far louder than a plucked sine of the same peak, so loudness, not peak, keeps the order.
        assertTrue(seconds(Tones.Kind.STEP) <= 0.2)
        assertTrue(loud(Tones.Kind.STEP) < loud(Tones.Kind.WIN))
        assertTrue(loud(Tones.Kind.WHEEL) < loud(Tones.Kind.WIN))
        assertTrue(loud(Tones.Kind.WORD) < loud(Tones.Kind.WIN))
        assertTrue(loud(Tones.Kind.WIN) <= loud(Tones.Kind.ALL_TIERS))
    }

    @Test fun variantsDifferButATierIsOneTune() {
        for (k in Tones.Kind.entries.filter { it != Tones.Kind.WHEEL && it != Tones.Kind.STEP && it != Tones.Kind.WORD }) {
            assertEquals(k.name, 3, of(k).map { Tones.samples(it).toList() }.toSet().size)
        }
        // In each win's first three notes, every variant's strongest note is the same one: G5, C6, E6.
        val candidates = listOf(79, 81, 83, 84, 86, 88, 89).map(::noteHz)
        for (k in listOf(Tones.Kind.WIN, Tones.Kind.BIG_WIN, Tones.Kind.ALL_TIERS)) for (i in of(k)) {
            val s = Tones.samples(i)
            val heard = listOf(0.005, 0.075, 0.145).map { from ->
                candidates.maxByOrNull { strength(s.window(from, from + 0.055), it) }
            }
            assertEquals("$k entry $i", listOf(79, 84, 88).map(::noteHz), heard)
        }
    }

    @Test fun theFanfaresGrow() {
        // A bigger win's opening is the smaller's, and it goes higher: the top C only arrives on all tiers' run.
        val win = Tones.samples(of(Tones.Kind.WIN).first())
        val all = Tones.samples(of(Tones.Kind.ALL_TIERS).first())
        assertTrue(strength(all.window(0.565, 0.62), noteHz(96)) > 20 * strength(win.window(0.565, 0.62), noteHz(96)))
    }

    @Test fun aNoteReallyIsAPulseWave() {
        // In one volume step of the da-ding's ring the samples sit on two values a quarter and three quarters of the time.
        val note = Tones.samples(Tones.word()).window(0.072, 0.087)
        val high = note.max()
        val low = note.min()
        val atHigh = note.count { abs(it - high) < 0.001f }
        val atLow = note.count { abs(it - low) < 0.001f }
        assertTrue("$atHigh + $atLow of ${note.size} on two levels", atHigh + atLow >= note.size * 0.9)
        assertEquals(0.25, atHigh.toDouble() / (atHigh + atLow), 0.04)
        // Zero mean: the levels are three to one, so a note starting makes no thump.
        assertEquals(3.0, abs(high / low).toDouble(), 0.1)
    }

    @Test fun volumeFallsInHardSteps() {
        // The note's top level, read every 2 ms over the note: few distinct values, and it holds between them.
        val note = Tones.samples(Tones.wheel(0))
        val tops = (0 until 40).map { k -> note.window(0.002 + k * 0.002, 0.002 + (k + 1) * 0.002 + 0.002).max() }
        val values = tops.map { (it * 2000).toInt() }.distinct()
        assertTrue("${values.size} levels in ${tops.size} readings", values.size in 3..15)
        assertEquals(values, values.sortedDescending())
    }

    @Test fun theBassIsAStairCaseAndTheCrashIsNoise() {
        val all = Tones.samples(of(Tones.Kind.ALL_TIERS).first())
        // After 1.38 s only the triangle rings, one volume step from 1.43 s. Sixteen levels: the wave holds still between
        // steps, which no sine does.
        val bass = all.window(1.435, 1.495)
        val held = (1 until bass.size).count { bass[it] == bass[it - 1] }
        assertTrue("$held of ${bass.size} samples hold", held >= bass.size / 2)
        assertTrue(bass.distinct().size <= 17)
        assertEquals(noteHz(60), hz(bass), 12.0)
        // The crash: sample-to-sample movement jumps where it lands, and it has no pitch to find.
        fun movement(s: FloatArray) = (1 until s.size).sumOf { abs(s[it] - s[it - 1]).toDouble() } / s.size
        assertTrue(movement(all.window(0.63, 0.70)) > 1.5 * movement(all.window(0.50, 0.56)))
    }

    @Test fun theWheelRisesAStepPerLetterThenHolds() {
        val pitches = (0 until Tones.Kind.WHEEL.count).map { hz(Tones.samples(Tones.wheel(it))) }
        assertEquals(pitches, pitches.sorted())
        assertEquals(5, pitches.toSet().size)
        assertEquals(pitches[4], pitches[6], 0.0)
        assertEquals(523.25, pitches[0], 25.0)
        assertEquals(Tones.wheel(6), Tones.wheel(12))
        assertEquals(noteHz(79), pitches.last(), 25.0)
    }

    @Test fun aFoundWordRisesAndLandsAboveEveryLetter() {
        val s = Tones.samples(Tones.word())
        val top = hz(Tones.samples(Tones.wheel(6)))
        val candidates = listOf(72, 79, 84, 91, 96).map(::noteHz)
        val da = candidates.maxByOrNull { strength(s.window(0.005, 0.065), it) }!!
        val ding = candidates.maxByOrNull { strength(s.window(0.075, 0.2), it) }!!
        assertEquals(noteHz(84), da, 0.01)
        assertEquals(noteHz(91), ding, 0.01)
        assertTrue(da > top)
    }

    @Test fun thePhraseRisesToTheWinsFirstNote() {
        val pitches = (0 until Tones.Kind.STEP.count).map { hz(Tones.samples(Tones.step(it))) }
        assertEquals(pitches, pitches.sorted())
        assertEquals(pitches.size, pitches.map { it.toInt() / 5 }.toSet().size)
        // Top note E5, under the win's G5, so the win is the phrase's next note up.
        assertTrue(pitches.last() in noteHz(75)..noteHz(77))
        assertEquals(Tones.step(7), Tones.step(40))
    }

    @Test fun phraseStepWalksEveryBoardFromBottomToTopAndNeverLeaves() {
        for (total in 1..120) {
            val steps = (0..total + 1).map { Tones.phraseStep(it, total) }
            assertTrue("$total in range", steps.all { it in 0 until Tones.Kind.STEP.count })
            assertEquals("$total rises", steps, steps.sorted())
            assertEquals("$total starts low", 0, Tones.phraseStep(1, total))
            // The last unit that does not solve the board is at the top, whatever the size.
            if (total >= 3) assertEquals("$total ends high", 7, Tones.phraseStep(total - 1, total))
        }
        // A small board moves every unit; a Sudoku's 27 share eight notes.
        assertEquals(listOf(0, 1, 3, 5, 7), (1..5).map { Tones.phraseStep(it, 6) })
        assertEquals(8, (1..26).map { Tones.phraseStep(it, 27) }.toSet().size)
    }

    @Test fun everyWinOpensOnAKick() {
        // Averaged over 4 ms, which leaves the bass and takes out the pulses (G5 is 1.3 ms a cycle).
        fun bass(s: FloatArray): Double {
            val w = (0.004 * rate).toInt()
            return rms(FloatArray(s.size - w) { i -> (0 until w).sumOf { s[i + it].toDouble() }.toFloat() / w })
        }
        for (k in listOf(Tones.Kind.WIN, Tones.Kind.BIG_WIN, Tones.Kind.ALL_TIERS)) for (i in of(k)) {
            val s = Tones.samples(i)
            assertTrue("$k entry $i", bass(s.window(0.0, 0.04)) > 1.5 * bass(s.window(0.1, 0.14)))
        }
    }

    @Test fun wheelStepFollowsTheWordsLength() {
        assertEquals(0, wheelToneStep(0, 1))
        assertEquals(1, wheelToneStep(1, 2))
        assertEquals(0, wheelToneStep(2, 1))   // backtracking steps down
        assertNull(wheelToneStep(3, 0))        // a word tried or cleared has its own feedback
        assertNull(wheelToneStep(2, 2))
        assertFalse(wheelToneStep(1, 2) == wheelToneStep(2, 1))
    }

    /** `DAYBOOK_TONES_WAV=<dir> ./gradlew :app:testDebugUnitTest --tests '*TonesTest*' --rerun` writes the bank out to listen to. */
    @Test fun writeTheBankForListening() {
        val dir = System.getenv("DAYBOOK_TONES_WAV")
        assumeTrue(dir != null)
        File(dir!!).mkdirs()
        val gap = ShortArray(rate / 2)
        val everything = ByteArrayOutputStream()
        var i = 0
        for (k in Tones.Kind.entries) for (n in 1..k.count) {
            val pcm = Tones.pcm16(i++)
            File(dir, "${k.name.lowercase().replace('_', '-')}-$n.wav").writeBytes(wav(pcm(pcm)))
            everything.write(pcm(pcm))
            everything.write(pcm(gap))
        }
        File(dir, "all-sounds.wav").writeBytes(wav(everything.toByteArray()))
        // A twelve-unit board's whole phrase, a unit every 0.6 s, then the win on the solving move.
        val phrase = ByteArrayOutputStream()
        for (done in 1..11) {
            val note = Tones.pcm16(Tones.step(Tones.phraseStep(done, 12)))
            phrase.write(pcm(note + ShortArray((0.6 * rate).toInt() - note.size)))
        }
        phrase.write(pcm(Tones.pcm16(of(Tones.Kind.WIN).first())))
        File(dir, "phrase.wav").writeBytes(wav(phrase.toByteArray()))
    }

    private fun pcm(s: ShortArray): ByteArray =
        ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { s.forEach { putShort(it) } }.array()

    private fun wav(data: ByteArray): ByteArray =
        ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".encodeToByteArray()); putInt(36 + data.size); put("WAVEfmt ".encodeToByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".encodeToByteArray()); putInt(data.size); put(data)
        }.array()
}
