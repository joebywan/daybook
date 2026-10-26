package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesLogic
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.MinesTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinesTeachingTest {

    private fun board(day: Int, d: Difficulty): MinesState =
        Mines.generate(daySeed(day, Mines.id, d), d) as MinesState

    private fun visible(s: MinesState): Pair<IntArray, BooleanArray> {
        val n = s.width * s.height
        return IntArray(n) { if (s.isOpen(it)) s.number(it) else -1 } to BooleanArray(n) { it in s.bumps }
    }

    /** Hints alone, each made by "Show me", until solved; [each] sees every step's technique. */
    private fun walk(start: MinesState, each: (String) -> Unit = {}): MinesState {
        var s = start
        var guard = 0
        while (!s.solved) {
            val d = Mines.teach(s)!!
            each(d.technique)
            assertFalse("a step must change the board", d.isReached(s))
            s = d.apply(s) as MinesState
            assertTrue("Show me reaches the step", d.isReached(s))
            assertTrue(guard++ < 400)
        }
        return s
    }

    /** The hint on [s] holds in every layout the oracle allows: its safe squares are never mines, its mines never safe. */
    private fun sound(s: MinesState, technique: String) {
        val step = MinesTeacher.teach(s)!!
        if (step.technique == MinesTeacher.MISTAKE) return
        val (num, hit) = visible(s)
        val o = MinesOracle.solve(s.width, s.height, num, hit, s.total)
        for (i in step.safe) assertFalse("$technique: $i could be a mine", o.canMine[i])
        for (i in step.mines) assertFalse("$technique: $i could be safe", o.canSafe[i])
    }

    @Test fun `coverage and fallback rate per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { MinesTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            val boards = 200
            for (day in 0 until boards) {
                var used = false
                val levels = HashSet<String>()
                walk(board(day, d)) { t ->
                    counts[t] = counts.getValue(t) + 1; steps++; levels += t
                    if (t == MinesTeacher.FALLBACK) used = true
                    assertTrue("no mistake on a hint-only walk", t != MinesTeacher.MISTAKE)
                }
                if (used) fellBack++
                when (d) {
                    Difficulty.STANDARD -> assertTrue(levels.all { it == MinesTeacher.ALL_FOUND || it == MinesTeacher.ALL_MINES })
                    Difficulty.HARD -> assertTrue("day $day", MinesTeacher.SUBSET in levels || MinesTeacher.PAIR in levels)
                    Difficulty.EXPERT -> assertTrue("day $day", MinesTeacher.EDGE in levels || MinesTeacher.WHAT_IF in levels)
                }
            }
            val line = "${d.name}: boards fell back $fellBack/$boards, steps $steps (${steps / boards.toDouble()}/board), $counts"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
        }
        writeReport("mines-teaching-coverage.txt", report.toString())
    }

    /** Boards a player made: safe squares revealed out of the ladder's order, right flags, a mine hit. */
    @Test fun `steps stay sound from boards a player made`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 30) {
            val s = board(day, d)
            val n = s.width * s.height
            var now = s
            for (i in rng.shuffled((0 until n).filter { !s.isMine(it) }).take(rng.nextInt(8))) now = now.reveal(i) ?: now
            for (i in rng.shuffled((0 until n).filter { s.isMine(it) }).take(rng.nextInt(4))) now = now.flag(i) ?: now
            if (now.solved) continue
            val step = Mines.teach(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a player's board", step.fallback)
            if (d != Difficulty.EXPERT || day < 8) sound(now, step.technique)
            walk(now)
        }
    }

    @Test fun `a wrong flag is addressed first and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val n = s.width * s.height
            val right = (0 until n).filter { s.isMine(it) }
            val half = right.take(right.size / 2).fold(s) { a, i -> a.flag(i)!! }
            assertFalse(Mines.teach(half)!!.mistake)
            val at = rng.pick((0 until n).filter { !s.isMine(it) && !s.isOpen(it) })
            val bad = half.flag(at)!!
            val step = Mines.teach(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong flag", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertFalse(step.isReached(bad))
            assertTrue(step.isReached(bad.flag(at)!!))
            assertFalse((step.apply(bad) as MinesState).isFlag(at))
        }
    }

    @Test fun `every nudge and explanation fits the panel`() {
        val rng = Rng(3)
        for (d in Difficulty.entries) for (day in 0 until 120) {
            var s = board(day, d)
            if (day % 4 == 0) s = s.flag(rng.pick((0 until s.width * s.height).filter { !s.isMine(it) && !s.isOpen(it) }))!!
            while (!s.solved) {
                val step = MinesTeacher.teach(s)!!
                assertTrue(step.nudge, step.nudge.length <= MinesTeacher.MAX_NUDGE)
                assertTrue(step.explanation, step.explanation.length <= MinesTeacher.MAX_EXPLANATION)
                s = MinesTeacher.apply(s, step)
            }
        }
    }

    @Test fun `no hint on a lost or won board`() {
        var s = board(0, Difficulty.STANDARD)
        val mines = (0 until s.width * s.height).filter { s.isMine(it) }
        s = s.reveal(mines.first())!!
        assertTrue(s.failed)
        assertNull(Mines.teach(s))
        assertNull(Mines.teach(walk(board(1, Difficulty.STANDARD))))
    }

    // ---- the walkthrough ------------------------------------------------------------------------------------------

    private val frames = Mines.tutorial

    @Test fun `the walkthrough board has one answer and the ladder clears it with single numbers`() {
        val s = Mines.tutorialBoard()
        assertEquals(1, MinesLogic.walk(5, 5, s.mines, s.opening)!!.depth)
        val (num, hit) = visible(s)
        val o = MinesOracle.solve(5, 5, num, hit, s.total)
        assertTrue(o.layouts > 1) // the opening alone does not decide it; the reveals do
        // Each claim the captions make.
        fun hiddenAround(b: MinesState, i: Int) = MinesLogic.neighbours(5, 5, i).filter { !b.isOpen(it) }
        assertEquals(1, s.number(Mines.FIRST_ONE))
        assertEquals(listOf(Mines.BUMP), hiddenAround(s, Mines.FIRST_ONE))
        assertTrue(s.isMine(Mines.BUMP))
        assertEquals(1, s.number(Mines.SECOND_ONE))
        assertEquals(setOf(Mines.BUMP) + Mines.SAFE, hiddenAround(s, Mines.SECOND_ONE).toSet())
        val s2 = frames[3].state as MinesState
        assertEquals(1, s2.number(Mines.THIRD_ONE))
        assertEquals(listOf(Mines.FLAG_SQUARE), hiddenAround(s2, Mines.THIRD_ONE))
        assertTrue(s2.isMine(Mines.FLAG_SQUARE))
    }

    @Test fun `each frame accepts its move made by the real gestures and refuses a wrong one`() {
        assertEquals(6, frames.size)
        assertNull(frames[0].judge)
        val s0 = frames[1].state as MinesState
        val first = frames[1].judge!!
        assertEquals(TutorialStep.DONE, first(s0.flag(Mines.BUMP)!!))
        assertEquals(TutorialStep.REJECT, first(s0.reveal(Mines.BUMP)!!))
        assertEquals(TutorialStep.REJECT, first(s0.flag(Mines.SAFE[0])!!))
        val s1 = s0.flag(Mines.BUMP)!!
        assertEquals(s1, frames[2].state)

        val reveal = frames[2].judge!!
        var s = s1
        for ((k, i) in Mines.SAFE.withIndex()) {
            assertEquals(setOf(i) + Mines.SAFE.drop(k + 1), frames[2].glow!!(s).strong)
            assertEquals(TutorialStep.REJECT, reveal(s.flag(i)!!))
            assertEquals(TutorialStep.REJECT, reveal(s.reveal(Mines.FLAG_SQUARE)!!))
            s = s.reveal(i)!!
            assertEquals(if (k == Mines.SAFE.lastIndex) TutorialStep.DONE else TutorialStep.PROGRESS, reveal(s))
        }
        assertEquals(s, frames[3].state)

        val flag = frames[3].judge!!
        assertEquals(TutorialStep.DONE, flag(s.flag(Mines.FLAG_SQUARE)!!))
        assertEquals(TutorialStep.REJECT, flag(s.reveal(Mines.FLAG_SQUARE)!!))
        assertEquals(TutorialStep.REJECT, flag(s.flag(4)!!))
        assertEquals(s.flag(Mines.FLAG_SQUARE)!!, frames[4].state)
        assertTrue(frames.last().freePlay)
    }

    @Test fun `the free-play frame is finished by hints alone, with no fallback`() {
        var s = frames.last().state as MinesState
        assertFalse(s.solved)
        while (!s.solved) {
            val d = Mines.teach(s)!!
            assertFalse(d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as MinesState
        }
        assertFalse(s.failed)
    }

    @Test fun `captions fit the walkthrough's slot`() {
        for (f in frames) {
            assertTrue(f.caption, f.caption.length <= 170)
            assertTrue(f.done, f.done.length <= 170)
            assertTrue(f.retry, f.retry.length <= 170)
        }
    }
}
