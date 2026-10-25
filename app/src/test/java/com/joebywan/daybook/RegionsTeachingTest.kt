package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.RegionsState
import com.joebywan.daybook.puzzles.RegionsTeacher
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class RegionsTeachingTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        Regions.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Regions.id, d), d) as RegionsState

    /** Follows the teacher's steps from [s] to the end, applying each. Fails on a step that disagrees with the answer. */
    private fun walk(s: RegionsState, onStep: (String) -> Unit = {}): RegionsState {
        var now = s
        var guard = 0
        while (!now.solved) {
            val d = Regions.teach(now) ?: error("no step on an unsolved board")
            onStep(d.technique)
            val t = d.targets.single()
            val next = d.apply(now) as RegionsState
            assertEquals("${d.technique} put the wrong digit in square $t", s.solution[t], next.cells[t])
            assertNotEquals("a step must change something", now.cells, next.cells)
            assertTrue(d.isReached(next))
            now = next
            assertTrue("walk does not end", ++guard <= 81)
        }
        return now
    }

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { RegionsTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            for (day in 0 until 200) {
                var usedFallback = false
                walk(board(day, d)) { t -> counts[t] = counts.getValue(t) + 1; steps++; if (t == RegionsTeacher.FALLBACK) usedFallback = true }
                if (usedFallback) fellBack++
            }
            val line = "${d.name}: boards fell back $fellBack/200, steps ${"%.1f".format(steps / 200.0)} per board, $counts"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
            // The tier gate, seen from the hints: Standard never needs a locked candidate; Hard two a board, Expert six.
            val locked = counts.getValue(RegionsTeacher.LOCKED)
            when (d) {
                Difficulty.STANDARD -> assertEquals(0, locked)
                Difficulty.HARD -> assertTrue("$locked", locked >= 2 * 200)
                Difficulty.EXPERT -> assertTrue("$locked", locked >= 6 * 200)
            }
        }
        File("build/reports").mkdirs()
        File("build/reports/regions-teaching-coverage.txt").writeText(report.toString())
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
            val step = Regions.teach(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.fallback)
            walk(now)
        }
    }

    @Test
    fun `on a board with several answers every step holds for every answer still possible`() {
        // Soundness on a unique board cannot tell reasoning from peeking, so take the givens away until
        // an independent search (RegionsOracle) finds several answers, and hold each step to all of them.
        val rng = Rng(3)
        var checked = 0
        for (day in 0 until 60) {
            val s = board(day, Difficulty.STANDARD)
            val cells = s.cells.toMutableList()
            val givens = rng.shuffled(cells.indices.filter { cells[it] != 0 })
            for (g in givens.take(rng.nextInt(1, givens.size))) cells[g] = 0
            val answers = RegionsOracle.solutions(s.size, s.region, cells)
            if (answers.size < 2 || answers.size > 20000) continue
            val step = RegionsTeacher.deduce(s.layout, cells) ?: continue
            assertTrue(
                "day $day: ${step.technique} puts ${step.digit} in ${step.cell} but ${answers.count { it[step.cell] != step.digit }} of ${answers.size} answers disagree",
                answers.all { it[step.cell] == step.digit },
            )
            checked++
        }
        assertTrue("only $checked boards with several answers were checked", checked >= 15)
    }

    @Test
    fun `a wrong digit is addressed first, and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val open = s.cells.indices.filter { s.cells[it] == 0 }
            // Correct digits never trip a mistake.
            val allRight = open.fold(s) { acc, i -> acc.withCell(i, s.solution[i]) }
            if (allRight.solved) assertEquals(null, Regions.teach(allRight))
            val half = open.take(open.size / 2).fold(s) { acc, i -> acc.withCell(i, s.solution[i]) }
            assertFalse(Regions.teach(half)?.mistake ?: false)
            // One wrong digit anywhere, including one too big for its region.
            val at = rng.pick(open)
            val wrong = (1..s.digits).filter { it != s.solution[at] }.let { rng.pick(it) }
            val bad = half.withCell(at, wrong)
            val step = Regions.teach(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong digit", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertTrue(step.isReached(bad.withCell(at, 0)))
            assertFalse(step.isReached(bad))
            assertEquals(0, (step.apply(bad) as RegionsState).cells[at])
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = board(day, d)
            val at = s.cells.indices.first { s.cells[it] == 0 }
            for (state in listOf(s, s.withCell(at, s.solution[at] % s.digits + 1))) {
                var cur = state
                var guard = 0
                while (!cur.solved && guard++ < 90) {
                    val step = Regions.teach(cur)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= 70)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= RegionsTeacher.MAX_EXPLANATION)
                    longest = maxOf(longest, step.explanation.length)
                    cur = step.apply(cur) as RegionsState
                }
            }
        }
        println("longest explanation: $longest")
    }

    @Test
    fun `the teacher cannot see the answer`() {
        // The reasoning entry point takes the layout and cells only; the step it finds is right for the stored answer.
        val s = board(5, Difficulty.EXPERT)
        val a = RegionsTeacher.deduce(s.layout, s.cells)!!
        val b = RegionsTeacher.deduce(s.copy(solution = List(s.size * s.size) { 0 }).layout, s.cells)!!
        assertEquals(a.explanation, b.explanation)
        assertEquals(s.solution[a.cell], a.digit)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Regions.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(true, true, false, false, false), frames.map { it.judge == null && !it.freePlay })
        val b0 = Regions.tutorialBoard()
        val first = Regions.TUTORIAL_FIRST
        // Frame 3: tap square 9 (the 1 glows next), then 1; another square or digit is refused.
        val place = frames[2].steps!!
        assertEquals(TutorialStep.REJECT, place(b0))
        val selected = b0.select(first)
        assertEquals(TutorialStep.PROGRESS, place(selected))
        assertEquals(setOf(RegionsTeacher.pad(1)), frames[2].glow!!(selected).strong)
        assertEquals(TutorialStep.REJECT, place(b0.select(8)))
        assertEquals(TutorialStep.REJECT, place(selected.enter(first, 2)))
        val placed = selected.enter(first, 1)
        assertEquals(TutorialStep.DONE, place(placed))
        // Frame 4: the player puts a 1 beside it (red, a mistake), then taps 1 again to clear it.
        val clash = frames[3].steps!!
        val glow = frames[3].glow!!
        val start = frames[3].state as RegionsState
        assertEquals(placed.cells, start.cells)
        assertEquals(TutorialStep.REJECT, clash(start))
        assertEquals(setOf(Regions.TUTORIAL_WRONG), glow(start).strong)
        val picked = start.select(Regions.TUTORIAL_WRONG)
        assertEquals(TutorialStep.PROGRESS, clash(picked))
        assertEquals(TutorialStep.REJECT, clash(picked.enter(Regions.TUTORIAL_WRONG, 2)))
        val wrong = picked.enter(Regions.TUTORIAL_WRONG, 1)
        assertEquals(TutorialStep.PROGRESS, clash(wrong))
        assertTrue(glow(wrong).warning)
        assertEquals(setOf(8, 9), wrong.conflicts())
        assertTrue(first in b0.layout.around[Regions.TUTORIAL_WRONG])
        assertTrue(Regions.teach(wrong)!!.mistake)
        assertEquals(TutorialStep.DONE, clash(wrong.enter(Regions.TUTORIAL_WRONG, 1)))
        // Frame 5 is free play, and hints alone finish it with no fallback and no mistake.
        assertTrue(frames[4].freePlay)
        var now = frames[4].state as RegionsState
        assertEquals(Regions.tutorialBoard(mapOf(first to 1)).cells, now.cells)
        while (!now.solved) {
            val d = Regions.teach(now)!!
            assertFalse(d.fallback); assertFalse(d.mistake)
            now = d.apply(now) as RegionsState
        }
        assertEquals(Regions.TUTORIAL_SOLUTION, now.cells)
        for (f in frames) assertTrue("caption ${f.caption.length}", f.caption.length <= 200)
        // Every claim a caption makes: square 9 touches a 2, a 3 and a 4 and the others around it are empty or 1-free.
        val around = b0.layout.around[first].map { b0.cells[it] }.filter { it != 0 }.toSet()
        assertEquals(setOf(2, 3, 4), around)
        assertEquals(3, b0.cells[2])
        assertEquals(1, RegionsTeacher.deduce(b0.layout, b0.cells)!!.digit)
        assertEquals(first, RegionsTeacher.deduce(b0.layout, b0.cells)!!.cell)
    }
}
