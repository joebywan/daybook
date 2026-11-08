package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Unblock
import com.joebywan.daybook.puzzles.UnblockLogic
import com.joebywan.daybook.puzzles.UnblockState
import com.joebywan.daybook.puzzles.UnblockTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnblockTeachingTest {

    private fun board(d: Difficulty, day: Int) = Unblock.generate(daySeed(day, Unblock.id, d), d) as UnblockState

    /** Every hint's move takes the board one move nearer the exit, by the oracle's count, so following hints solves in par. */
    @Test
    fun `coverage - hints alone solve every board in exactly its par, each step one nearer by the oracle`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>()
            for (day in 0 until 40) {
                var s = board(d, day)
                val par = s.par
                var left = UnblockOracle.fewest(s.cars)
                var steps = 0
                while (!s.solved) {
                    val dd = Unblock.teach(s)!!
                    assertFalse(dd.mistake)
                    assertFalse(dd.fallback)
                    counts[dd.technique] = (counts[dd.technique] ?: 0) + 1
                    assertTrue("${d.name} day $day: ${dd.technique}", dd.technique in UnblockTeacher.TECHNIQUES)
                    val next = dd.apply(s) as UnblockState
                    assertTrue(dd.isReached(next))
                    assertFalse(dd.isReached(s))
                    s = next
                    val now = UnblockOracle.fewest(s.cars)
                    assertEquals("${d.name} day $day step $steps", left - 1, now)
                    left = now
                    steps++
                }
                assertEquals("${d.name} day $day", par, steps)
            }
            report.append("${d.name}: $counts\n")
        }
        File("build/reports").mkdirs()
        File("build/reports/unblock-teaching-coverage.txt").writeText(report.toString())
        println(report)
    }

    @Test
    fun `from boards a player has wandered on, a hint is still one move nearer and never a mistake`() {
        for (d in Difficulty.entries) for (day in 0 until 15) {
            var s = board(d, day)
            val rng = Rng(day * 31L + d.ordinal)
            repeat(12) {
                val m = rng.pick(UnblockLogic.slides(s.cars))
                s = s.slide(m.car, m.by)!!
            }
            if (s.solved) continue
            val before = UnblockOracle.fewest(s.cars)
            val dd = Unblock.teach(s)!!
            assertFalse(dd.mistake)
            val after = dd.apply(s) as UnblockState
            assertEquals("${d.name} day $day", before - 1, UnblockOracle.fewest(after.cars))
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        for (d in Difficulty.entries) for (day in 0 until 30) {
            var s = board(d, day)
            while (!s.solved) {
                val step = UnblockTeacher.teach(s)!!
                assertTrue(step.nudge, step.nudge.length <= UnblockTeacher.MAX_NUDGE)
                assertTrue(step.explanation, step.explanation.length <= UnblockTeacher.MAX_EXPLANATION)
                s = s.slide(step.move!!.car, step.move!!.by)!!
            }
        }
    }

    @Test
    fun `a solved board has no hint, and the hint names the car it moves`() {
        val s = Unblock.tutorialBoard()
        val step = UnblockTeacher.teach(s)!!
        assertTrue(step.explanation, step.explanation.startsWith("Slide ${UnblockTeacher.name(s.cars, 2)} left"))
        assertTrue(UnblockTeacher.name(s.cars, 2), UnblockTeacher.name(s.cars, 2).endsWith("truck in row 1"))
        var t = s
        while (!t.solved) t = Unblock.teach(t)!!.apply(t) as UnblockState
        assertNull(Unblock.teach(t))
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board's fewest is three, by the oracle`() {
        assertEquals(3, UnblockOracle.fewest(Unblock.tutorialBoard().cars))
        assertEquals(Unblock.tutorialBoard().par, UnblockOracle.fewest(Unblock.tutorialBoard().cars))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val f = Unblock.tutorial
        assertEquals(4, f.size)
        assertNull("frame 1 is read only", f[0].judge)
        val s0 = f[1].state as UnblockState
        // Frame 2: the truck all the way left, and not part way.
        val s1 = s0.slide(2, -3)!!
        assertEquals(TutorialStep.DONE, f[1].judge!!(s1))
        assertEquals(TutorialStep.REJECT, f[1].judge!!(s0.slide(2, -2)!!))
        assertEquals(TutorialStep.REJECT, f[1].judge!!(s0.slide(0, 1)!!))
        assertEquals(s1, f[2].state)
        // Frame 3: the car in the way up.
        val s2 = s1.slide(1, -1)!!
        assertEquals(TutorialStep.DONE, f[2].judge!!(s2))
        assertEquals(TutorialStep.REJECT, f[2].judge!!(s1.slide(0, 1)!!))
        assertEquals(s2, f[3].state)
        // Before the truck goes, the car in the way cannot go up: the caption's order is the rules'.
        assertNull(s0.slide(1, -1))
        // Free play: hints finish it with no fallback.
        assertTrue(f[3].freePlay)
        var s = s2
        while (!s.solved) {
            val d = Unblock.teach(s)!!
            assertFalse(d.fallback)
            s = d.apply(s) as UnblockState
        }
        for (frame in f) assertTrue(frame.caption, frame.caption.length <= 170)
        assertNotEquals(f[1].caption, f[2].caption)
    }
}
