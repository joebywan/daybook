package com.joebywan.daybook

import com.joebywan.daybook.core.SolveTone
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.puzzles.wheelToneStep
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TonesTest {
    private val rate = SolveTone.SAMPLE_RATE

    // Bank entries of a kind, from the same public index pick() draws from: every index it can return, found by asking.
    private fun of(kind: Tones.Kind): Set<Int> = (1..400).mapTo(HashSet()) { Tones.pick(kind) }
    private fun hz(s: FloatArray): Double {
        val n = (0.1 * rate).toInt().coerceAtMost(s.size)
        val crossings = (1 until n).count { (s[it - 1] < 0) != (s[it] < 0) }
        return crossings / 2.0 / (n.toDouble() / rate)
    }

    @Test fun everyToneIsPureAndStartsAndEndsWithoutAClick() {
        for (i in 0 until Tones.COUNT) {
            val s = Tones.samples(i)
            assertArrayEquals(s, Tones.samples(i), 0f)
            assertEquals(0f, s[0], 0f)
            assertTrue("tone $i ends at ${s.last()}", abs(s.last()) < 0.002f)
            assertTrue("tone $i clips", s.all { abs(it) < 1f })
        }
    }

    @Test fun lengthsAndLevelsKeepEachKindInItsPlace() {
        fun peak(k: Tones.Kind) = of(k).maxOf { i -> Tones.samples(i).maxOf { abs(it) } }
        fun longest(k: Tones.Kind) = of(k).maxOf { Tones.samples(it).size } / rate.toDouble()
        assertTrue("a tick is very quiet", peak(Tones.Kind.TICK) <= 0.06f)
        assertTrue("a tick is short", longest(Tones.Kind.TICK) <= 0.04)
        assertTrue(longest(Tones.Kind.UNIT) <= 0.25)
        assertTrue(peak(Tones.Kind.UNIT) <= 0.2f)
        assertTrue(longest(Tones.Kind.BIG_WIN) in 0.9..1.2)
        assertTrue(longest(Tones.Kind.ALL_TIERS) > longest(Tones.Kind.BIG_WIN))
        assertTrue("no win startles", peak(Tones.Kind.ALL_TIERS) <= 0.6f && peak(Tones.Kind.BIG_WIN) <= 0.5f)
    }

    @Test fun theChimeIsStillTheOwnersAndVariantsDiffer() {
        val wins = of(Tones.Kind.WIN).sorted()
        assertEquals(3, wins.size)
        assertArrayEquals(SolveTone.samples(), Tones.samples(wins[0]), 0f)
        for (k in Tones.Kind.entries.filter { it != Tones.Kind.WHEEL }) {
            val bank = of(k).map { Tones.samples(it).toList() }
            assertEquals(k.name, 3, bank.toSet().size)
        }
    }

    @Test fun theWheelRisesAStepPerLetterAndStopsAtTheTop() {
        val pitches = (0 until Tones.Kind.WHEEL.count).map { hz(Tones.samples(Tones.wheel(it))) }
        assertEquals(pitches, pitches.sorted())
        assertEquals(pitches.size, pitches.toSet().size)
        assertEquals(Tones.wheel(6), Tones.wheel(12))
    }

    @Test fun wheelStepFollowsTheWordsLength() {
        assertEquals(0, wheelToneStep(0, 1))
        assertEquals(1, wheelToneStep(1, 2))
        assertEquals(0, wheelToneStep(2, 1))   // backtracking steps down
        assertNull(wheelToneStep(3, 0))        // a word tried or cleared has its own feedback
        assertNull(wheelToneStep(2, 2))
        assertFalse(wheelToneStep(1, 2) == wheelToneStep(2, 1))
    }
}
