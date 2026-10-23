package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.InequalityState
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class InequalityTeachingTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        Inequality.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Inequality.id, d), d) as InequalityState

    /** Follows the teacher's steps from [s] to the end, applying each. Fails on a step that disagrees with the answer. */
    private fun walk(s: InequalityState, onStep: (String) -> Unit = {}): InequalityState {
        var now = s
        var guard = 0
        while (!now.solved) {
            val d = Inequality.teach(now) ?: error("no step on an unsolved board")
            onStep(d.technique)
            val t = d.targets.single()
            val next = d.apply(now) as InequalityState
            assertEquals("${d.technique} put the wrong digit in square $t", s.solution[t], next.cells[t])
            assertNotEquals("a step must change something", now.cells, next.cells)
            assertTrue(d.isReached(next))
            now = next
            assertTrue("walk does not end", ++guard <= 36)
        }
        return now
    }

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { InequalityTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            for (day in 0 until 200) {
                var usedFallback = false
                walk(board(day, d)) { t -> counts[t] = counts.getValue(t) + 1; steps++; if (t == InequalityTeacher.FALLBACK) usedFallback = true }
                if (usedFallback) fellBack++
            }
            val line = "${d.name}: boards fell back $fellBack/200, steps $steps, $counts"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
        }
        File("build/reports").mkdirs()
        File("build/reports/inequality-teaching-coverage.txt").writeText(report.toString())
    }

    @Test
    fun `steps stay sound from boards a player made, not only the solver's path`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = board(day, d)
            // Fill a random share of the empty squares correctly, in any order.
            val open = rng.shuffled(s.cells.indices.filter { s.cells[it] == 0 })
            var now = s
            for (i in open.take(rng.nextInt(open.size + 1))) now = now.withCell(i, s.solution[i])
            if (now.solved) continue
            val step = Inequality.teach(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.fallback)
            walk(now)
        }
    }

    @Test
    fun `a wrong digit is addressed first, and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val n = s.size
            val open = s.cells.indices.filter { s.cells[it] == 0 }
            // Correct digits never trip a mistake.
            val allRight = open.fold(s) { acc, i -> acc.withCell(i, s.solution[i]) }
            if (allRight.solved) assertEquals(null, Inequality.teach(allRight))
            val half = open.take(open.size / 2).fold(s) { acc, i -> acc.withCell(i, s.solution[i]) }
            assertFalse(Inequality.teach(half)?.mistake ?: false)
            // One wrong digit anywhere.
            val at = rng.pick(open)
            val wrong = (1..n).filter { it != s.solution[at] }.let { rng.pick(it) }
            val bad = half.withCell(at, wrong)
            val step = Inequality.teach(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong digit", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertTrue(step.isReached(bad.withCell(at, 0)))
            assertFalse(step.isReached(bad))
            assertEquals(0, (step.apply(bad) as InequalityState).cells[at])
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = board(day, d)
            val now: InequalityState = s
            // Plant a mistake at the start so those texts are measured too.
            val at = s.cells.indices.first { s.cells[it] == 0 }
            for (state in listOf(now, now.withCell(at, s.solution[at] % s.size + 1))) {
                var cur = state
                var guard = 0
                while (!cur.solved && guard++ < 40) {
                    val step = Inequality.teach(cur)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= 70)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= 200)
                    longest = maxOf(longest, step.explanation.length)
                    cur = step.apply(cur) as InequalityState
                }
            }
        }
        println("longest explanation: $longest")
    }

    @Test
    fun `the teacher cannot see the answer`() {
        // The reasoning entry point takes cells and signs only; changing the stored answer changes nothing it says.
        val s = board(5, Difficulty.EXPERT)
        val a = InequalityTeacher.deduce(s.size, s.cells, s.signs)!!
        val b = InequalityTeacher.deduce(s.size, s.cells, s.signs.toList())!!
        assertEquals(a.explanation, b.explanation)
        assertEquals(s.solution[a.cell], a.digit)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Inequality.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(true, true, false, false, false), frames.map { it.judge == null && !it.freePlay })
        val b0 = Inequality.tutorialBoard()
        // Frame 3: tap the top-left square (the 1 glows next), then 1; another square, or the 2 (the
        // wrong end of the sign), is refused.
        val place = frames[2].steps!!
        assertEquals(TutorialStep.REJECT, place(b0))
        val selected = b0.select(0)
        assertEquals(TutorialStep.PROGRESS, place(selected))
        assertEquals(setOf(InequalityTeacher.pad(1)), frames[2].glow!!(selected).strong)
        assertEquals(TutorialStep.REJECT, place(b0.select(1)))
        assertEquals(TutorialStep.REJECT, place(selected.enter(0, 2)))
        val placed = selected.enter(0, 1)
        assertEquals(TutorialStep.DONE, place(placed))
        // Its claim: those four squares hold only 1s and 2s, and without the sign both ways fit.
        assertTrue(listOf(0, 1, 4, 5).all { Inequality.TUTORIAL_SOLUTION[it] in 1..2 })
        // Frame 4: the player puts a 1 under the 1 in column 3 (red, a mistake), then taps 1 again.
        val clash = frames[3].steps!!
        val glow = frames[3].glow!!
        val start = frames[3].state as InequalityState
        assertEquals(placed.cells, start.cells)
        assertEquals(TutorialStep.REJECT, clash(start))
        val wrongAt = glow(start).strong.single()
        val picked = start.select(wrongAt)
        assertEquals(TutorialStep.PROGRESS, clash(picked))
        assertEquals(TutorialStep.REJECT, clash(picked.enter(wrongAt, 2)))
        val wrong = picked.enter(wrongAt, 1)
        assertEquals(TutorialStep.PROGRESS, clash(wrong))
        assertTrue(glow(wrong).warning)
        assertEquals(setOf(10, 14), wrong.conflicts())
        assertEquals(2, wrongAt % 4) // column 3, counting from 1
        assertTrue(Inequality.teach(wrong)!!.mistake)
        val cleared = wrong.enter(wrongAt, 1)
        assertEquals(TutorialStep.DONE, clash(cleared))
        // Frame 5 is free play, and hints alone finish it with no fallback and no mistake.
        assertTrue(frames[4].freePlay)
        var now = frames[4].state as InequalityState
        assertEquals(Inequality.tutorialBoard(mapOf(0 to 1)).cells, now.cells)
        while (!now.solved) {
            val d = Inequality.teach(now)!!
            assertFalse(d.fallback); assertFalse(d.mistake)
            now = d.apply(now) as InequalityState
        }
        assertEquals(Inequality.TUTORIAL_SOLUTION, now.cells)
        for (f in frames) assertTrue("caption ${f.caption.length}", f.caption.length <= 200)
        // Every claim a caption makes: the sign is the first and says square 0 is smaller than square 1.
        assertEquals(com.joebywan.daybook.puzzles.Sign(0, 1), Inequality.TUTORIAL_SIGNS.first())
    }
}
