package com.joebywan.daybook

import com.joebywan.daybook.core.SolveTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SolveToneTest {
    private val samples = SolveTone.samples()
    private val rate = SolveTone.SAMPLE_RATE

    @Test
    fun isAboutEightTenthsOfASecondOfMono() {
        assertEquals(35_720, samples.size)
        assertEquals(0.81, samples.size.toDouble() / rate, 0.001)
    }

    @Test
    fun staysQuietEnoughNotToStartleButIsAudible() {
        val peak = samples.maxOf { abs(it) }
        assertTrue("peak $peak is too loud", peak <= 0.34f)
        assertTrue("peak $peak is too quiet", peak >= 0.25f)
    }

    @Test
    fun endsInSilenceSoTheTrackStopsWithoutAClick() {
        // The last note stops at 0.72 s; everything after it is exactly zero.
        val from = (0.73 * rate).toInt()
        assertTrue((from until samples.size).all { samples[it] == 0f })
        // And it has faded out before that: the final audible samples are tiny.
        assertTrue(abs(samples[(0.719 * rate).toInt()]) < 0.01f)
        // It starts from silence too.
        assertEquals(0f, samples[0], 0f)
    }

    @Test
    fun theSecondNoteIsHigherAndArrivesAfterTheFirst() {
        // Count zero crossings in a window with only the first note (0.02-0.10 s) and one with only
        // the second (0.55-0.70 s; the first stopped at 0.50 s): G5 is 784 Hz, C6 is 1047 Hz.
        fun hz(from: Double, to: Double): Double {
            val a = (from * rate).toInt()
            val b = (to * rate).toInt()
            var crossings = 0
            for (i in a + 1 until b) if ((samples[i - 1] < 0) != (samples[i] < 0)) crossings++
            return crossings / 2.0 / (to - from)
        }
        assertEquals(784.0, hz(0.02, 0.10), 40.0)
        assertEquals(1046.5, hz(0.55, 0.70), 40.0)
    }

    @Test
    fun pcmIsTheSameSignalInSixteenBits() {
        val pcm = SolveTone.pcm16()
        assertEquals(samples.size, pcm.size)
        for (i in samples.indices step 97) assertEquals(samples[i] * 32767f, pcm[i].toFloat(), 1f)
    }
}
