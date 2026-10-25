package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CageTeachingTest {

    private fun board(day: Int, d: Difficulty) =
        CageLogic.generateVerified(daySeed(day, Cages.id, d), d)
            ?: error("no proved board for day $day ${d.name}")

    /** Applies each hint until solved; every step must agree with the (unique) answer and change something. */
    private fun walk(s: CageState, onStep: (String) -> Unit = {}): CageState {
        var now = s
        var guard = 0
        while (!now.solved) {
            val d = Cages.teach(now) ?: error("no step on an unsolved board")
            assertFalse("unexpected mistake: ${d.explanation}", d.mistake)
            onStep(d.technique)
            val next = d.apply(now) as CageState
            for (t in d.targets) assertEquals("${d.technique} is wrong at $t", s.grid.solution[t], next.cells[t])
            assertNotEquals("a step must change something", now.cells, next.cells)
            assertTrue(d.isReached(next))
            now = next
            assertTrue("walk does not end", ++guard <= 90)
        }
        return now
    }

    @Test
    fun `coverage and fallback rate per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { CageTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            val boards = 200
            for (day in 0 until boards) {
                var used = false
                walk(board(day, d)) { t -> counts[t] = counts.getValue(t) + 1; steps++; if (t == CageTeacher.FALLBACK) used = true }
                if (used) fellBack++
            }
            val line = "${d.name}: boards fell back $fellBack/$boards, steps $steps (${steps / boards.toDouble()}/board), $counts"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
            assertEquals(0, counts.getValue(CageTeacher.FALLBACK))
        }
        File("build/reports").mkdirs()
        File("build/reports/cages-teaching-coverage.txt").writeText(report.toString())
    }

    @Test
    fun `a year of daily boards on every tier is finished by hints alone`() {
        for (d in Difficulty.entries) for (day in 0 until 365) {
            walk(board(day, d)) { t -> assertNotEquals("${d.name} day $day", CageTeacher.FALLBACK, t) }
        }
    }

    @Test
    fun `steps stay sound from boards a player made`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = board(day, d)
            val open = s.grid.givens.indices.filter { !s.grid.givens[it] }
            var now = s
            // Some correct digits, in any order, and some arbitrary pencil marks.
            for (i in rng.shuffled(open).take(rng.nextInt(open.size + 1))) now = now.withCell(i, s.grid.solution[i])
            for (i in rng.shuffled(open).take(10)) now = now.toggleNote(i, rng.nextInt(1, 9))
            if (now.solved) continue
            val step = Cages.teach(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.fallback)
            walk(now)
        }
    }

    @Test
    fun `a wrong digit is addressed first and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val open = s.grid.givens.indices.filter { !s.grid.givens[it] }
            val half = open.take(open.size / 2).fold(s) { a, i -> a.withCell(i, s.grid.solution[i]) }
            assertFalse(Cages.teach(half)?.mistake ?: false)
            val at = rng.pick(open.filter { half.cells[it] == 0 })
            val wrongDigit = (1..9).filter { it != s.grid.solution[at] }.let { rng.pick(it) }
            val bad = half.withCell(at, wrongDigit)
            val step = Cages.teach(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong digit", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertFalse(step.isReached(bad))
            assertTrue(step.isReached(bad.withCell(at, 0)))
            assertEquals(0, (step.apply(bad) as CageState).cells[at])
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        var nudge = 0
        for (d in Difficulty.entries) for (day in 0 until 150) {
            val s = board(day, d)
            val at = s.grid.givens.indices.first { !s.grid.givens[it] }
            val wrong = (1..9).first { it != s.grid.solution[at] }
            for (state in listOf(s, s.withCell(at, wrong))) {
                var cur = state
                var guard = 0
                while (!cur.solved && guard++ < 90) {
                    val step = CageTeacher.teach(cur)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= 70)
                    assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= CageTeacher.MAX_EXPLANATION)
                    longest = maxOf(longest, step.explanation.length)
                    nudge = maxOf(nudge, step.nudge.length)
                    cur = Cages.teach(cur)!!.apply(cur) as CageState
                }
            }
        }
        println("longest explanation: $longest, longest nudge: $nudge")
    }

    @Test
    fun `the reasoning cannot see the answer`() {
        for (d in Difficulty.entries) for (day in 0 until 12) {
            val s = board(day, d)
            // The answer blanked wherever the board is still open; the player's own digits stay, so nothing is a mistake.
            val blind = s.copy(grid = s.grid.copy(solution = s.grid.cells))
            val a = CageTeacher.teach(s)!!
            val b = CageTeacher.teach(blind)!!
            assertEquals(a.explanation, b.explanation)
            assertEquals(a.cell, b.cell)
            assertEquals(a.digit, b.digit)
        }
    }

    /** Boards the generator did not make, with several answers: every non-fallback step holds in every answer. */
    @Test
    fun `steps hold in every answer of boards that have several`() {
        val rng = Rng(3)
        val spec = CageLogic.specFor(Difficulty.STANDARD)
        var boards = 0
        var steps = 0
        var attempts = 0
        while (boards < 12 && attempts++ < 400) {
            val solution = Sudoku.fullGrid(rng)
            val cageOf = CageLogic.partition(rng, solution, spec)
            val sums = CageLogic.sumsOf(cageOf, solution)
            val givens = rng.shuffled((0 until 81).toList()).take(rng.nextInt(18, 24)).toSet()
            val cells = List(81) { if (it in givens) solution[it] else 0 }
            val answers = CageOracle.all(cageOf, sums, cells, limit = 30)
            if (answers.size < 2 || answers.size >= 30) continue
            boards++
            var now = CageState(
                SudokuState(givens = List(81) { it in givens }, cells = cells, solution = solution),
                cageOf, sums,
            )
            var guard = 0
            while (!now.solved && guard++ < 90) {
                val step = Cages.teach(now) ?: break
                if (step.fallback) break
                for (t in step.targets) {
                    val digit = (step.apply(now) as CageState).cells[t]
                    for (a in answers) assertEquals("${step.technique} at $t", a[t], digit)
                }
                steps++
                now = step.apply(now) as CageState
            }
        }
        println("multi-answer check: $boards boards, $steps steps")
        assertTrue("found $boards boards", boards >= 6)
        assertTrue(steps > 40)
    }

    // ---- the walkthrough -----------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val b = Cages.tutorialBoard()
        assertEquals(1, CageOracle.count(b.cageOf, b.sums, b.cells))
        val all = CageOracle.all(b.cageOf, b.sums, b.cells, 2)
        assertEquals(b.grid.solution, all.single())
    }

    @Test
    fun `each walkthrough frame accepts its move and rejects a near miss`() {
        val frames = Cages.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(false, true, true, true, false), frames.map { it.judge != null })
        assertTrue(frames.last().freePlay)

        val first = Cages.TUTORIAL_FIRST
        val wrong = Cages.TUTORIAL_WRONG
        // The captions' claims, from the board itself.
        val b = Cages.tutorialBoard()
        val cageFirst = CageLogic.cageCells(b.cageOf)[b.cageOf[first]]
        assertEquals(2, cageFirst.size)
        assertEquals(11, b.sums[b.cageOf[first]])
        assertEquals(7, b.cells[cageFirst.first { it != first }])
        assertEquals(4, b.grid.solution[first])
        val cageWrong = CageLogic.cageCells(b.cageOf)[b.cageOf[wrong]]
        assertEquals(11, b.sums[b.cageOf[wrong]])
        assertEquals(2, b.cells[cageWrong.first { it != wrong }])
        assertEquals(9, b.grid.solution[wrong])

        // 1: tap the first gap (the 4 glows next), then the 4; another square or digit is refused.
        val place = frames[1].steps!!
        assertEquals(TutorialStep.REJECT, place(b))
        val chosen = b.select(first)
        assertEquals(TutorialStep.PROGRESS, place(chosen))
        assertEquals(setOf(SudokuTeacher.pad(4)), frames[1].glow!!(chosen).strong)
        assertEquals(TutorialStep.REJECT, place(b.select(wrong)))
        assertEquals(TutorialStep.REJECT, place(chosen.enter(first, 5, notesMode = false)))
        val placed = chosen.enter(first, 4, notesMode = false)
        assertEquals(TutorialStep.DONE, place(placed))
        // 2: the player puts the 5 in (its cage red, and a repeat in its column), then taps 5 again.
        val clash = frames[2].steps!!
        val start = frames[2].state as CageState
        assertEquals(placed.cells, start.cells)
        assertEquals(TutorialStep.REJECT, clash(start))
        val picked = start.select(wrong)
        assertEquals(TutorialStep.PROGRESS, clash(picked))
        assertEquals(TutorialStep.REJECT, clash(picked.enter(wrong, 9, notesMode = false)))
        val shown = picked.enter(wrong, 5, notesMode = false)
        assertEquals(TutorialStep.PROGRESS, clash(shown))
        assertTrue(frames[2].glow!!(shown).warning)
        assertTrue("the cage is shown wrong", CageLogic.cageConflicts(shown.cells, shown.cageOf, shown.sums).containsAll(cageWrong.toList()))
        assertTrue("and the 5 repeats in its column", (0 until 9).any { it * 9 + wrong % 9 != wrong && shown.cells[it * 9 + wrong % 9] == 5 })
        assertTrue(wrong in shown.grid.conflicts())
        assertEquals(TutorialStep.DONE, clash(shown.enter(wrong, 5, notesMode = false)))
        // 3: pencil the 9.
        val cleared = frames[3].state as CageState
        assertEquals(0, cleared.cells[wrong])
        assertTrue(frames[3].accepts!!(cleared.toggleNote(wrong, 9)))
        assertFalse(frames[3].accepts!!(cleared.toggleNote(wrong, 8)))
        assertFalse(frames[3].accepts!!(cleared.withCell(wrong, 9)))
        // Each frame's board is the previous one's result.
        assertEquals(Cages.tutorialBoard(mapOf(first to 4), selected = wrong), frames[3].state)
        assertEquals(cleared.toggleNote(wrong, 9).grid.notes, (frames[4].state as CageState).grid.notes)
        for (f in frames) assertTrue(f.caption.length <= 170)
    }

    @Test
    fun `from the free-play frame hints alone finish the walkthrough with no fallback and no mistake`() {
        walk(Cages.tutorial.last().state as CageState) { t -> assertNotEquals(CageTeacher.FALLBACK, t) }
    }
}
