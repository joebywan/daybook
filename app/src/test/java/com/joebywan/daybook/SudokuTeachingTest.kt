package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sudoku hint solver, checked against an enumerator written out again here on a different
 * principle from the generator's (most-constrained cell first, through the shared peer table): plain
 * row-major backtracking that tests each digit by scanning its row, column and box directly.
 *
 * As for Kings, soundness on a unique board cannot tell reasoning from peeking, so the solver is also
 * walked over boards with several answers, where a digit is only sound if every answer still
 * standing has it there.
 */
class SudokuTeachingTest {

    // ---- independent rules --------------------------------------------------------------------

    private fun fits(grid: IntArray, cell: Int, d: Int): Boolean {
        val r = cell / 9
        val c = cell % 9
        for (k in 0 until 9) {
            if (grid[r * 9 + k] == d || grid[k * 9 + c] == d) return false
        }
        val br = r - r % 3
        val bc = c - c % 3
        for (dr in 0 until 3) for (dc in 0 until 3) if (grid[(br + dr) * 9 + bc + dc] == d) return false
        return true
    }

    /** Every completion of [cells], row-major, up to [cap]. Null when there are more than [cap]. */
    private fun allSolutions(cells: List<Int>, cap: Int): List<List<Int>>? {
        val grid = cells.toIntArray()
        val out = mutableListOf<List<Int>>()
        var over = false
        fun go(i: Int) {
            if (over) return
            if (i == 81) {
                if (out.size == cap) over = true else out += grid.toList()
                return
            }
            if (grid[i] != 0) return go(i + 1)
            for (d in 1..9) {
                if (!fits(grid, i, d)) continue
                grid[i] = d
                go(i + 1)
                grid[i] = 0
                if (over) return
            }
        }
        go(0)
        return if (over) null else out
    }

    /** A completed grid obeys the rules: every row, column and box holds 1..9. */
    private fun valid(g: List<Int>): Boolean {
        val want = (1..9).toSet()
        for (k in 0 until 9) {
            if ((0 until 9).map { g[k * 9 + it] }.toSet() != want) return false
            if ((0 until 9).map { g[it * 9 + k] }.toSet() != want) return false
            val br = k / 3 * 3
            val bc = k % 3 * 3
            if ((0 until 9).map { g[(br + it / 3) * 9 + bc + it % 3] }.toSet() != want) return false
        }
        return true
    }

    // ---- soundness ------------------------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and places a digit`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "sudoku-teach")) {
                var s = Sudoku.generate(seed, difficulty) as SudokuState
                assertEquals(listOf(s.solution), allSolutions(s.cells, 2))
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 81)
                    val d = Sudoku.teach(s)
                    assertNotNull(d)
                    d!!
                    assertFalse("$difficulty/$seed: a hint-only walk made a mistake", d.mistake)
                    assertEquals(1, d.targets.size)
                    val cell = d.targets.single()
                    assertEquals("$difficulty/$seed: ${d.technique} on an empty cell", 0, s.cells[cell])
                    val next = d.apply(s) as SudokuState
                    assertEquals("$difficulty/$seed: ${d.technique}: ${d.explanation}", s.solution[cell], next.cells[cell])
                    assertTrue(d.isReached(next))
                    assertFalse(d.isReached(s))
                    assertTrue("${d.explanation.length}: ${d.explanation}", d.explanation.length <= SudokuTeacher.MAX_EXPLANATION)
                    assertTrue("nudge too long: ${d.nudge}", d.nudge.length <= 60)
                    s = next
                }
                assertTrue(valid(s.cells))
            }
        }
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = java.util.Random(7)
        var boards = 0
        var steps = 0
        var tries = 0
        while (boards < 150) {
            assertTrue("could not make enough multi-answer boards", tries++ < 2000)
            val base = Sudoku.generate(rng.nextLong(), Difficulty.entries[rng.nextInt(3)]) as SudokuState
            // Knock out a few more givens, unchecked, so several answers stand.
            val cells = base.cells.toMutableList()
            val filled = cells.indices.filter { cells[it] != 0 }.shuffled(rng)
            filled.take(3 + rng.nextInt(6)).forEach { cells[it] = 0 }
            var answers = allSolutions(cells, 300) ?: continue
            if (answers.size < 2) continue
            boards++
            while (true) {
                val step = SudokuTeacher.deduce(cells) ?: break
                assertTrue(
                    "${step.technique}: ${step.explanation}",
                    answers.all { it[step.cell] == step.digit },
                )
                cells[step.cell] = step.digit
                answers = answers.filter { it[step.cell] == step.digit }
                steps++
            }
        }
        assertTrue("too few steps exercised: $steps", steps > 1000)
    }

    /**
     * `app/build/reports/sudoku-teaching-coverage.txt`: how often each technique is what a player
     * needs next, and how often the fallback is reached, per tier.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 300
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        report.appendLine("Sudoku teaching coverage: $perTier boards per tier, walked from the start by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = SudokuTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = SudokuTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var longest = ""
            for (seed in seeds(perTier, difficulty, "sudoku-coverage")) {
                var s = Sudoku.generate(seed, difficulty) as SudokuState
                val used = mutableSetOf<String>()
                while (!s.solved) {
                    val d = Sudoku.teach(s)!!
                    if (d.explanation.length > longest.length) longest = d.explanation
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as SudokuState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${difficulty.name}: $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board")
            for (t in SudokuTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-20s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine("  longest explanation, ${longest.length} characters: $longest")
            // Every tier is dug only while the teacher's ladder finishes it (Sudoku.generateVerified), so no
            // generated board reaches the fallback. Before that gate: Hard 15/300, Expert 114/300.
            val fallbackBoards = boardCounts.getValue(SudokuTeacher.FALLBACK)
            val fallbackSteps = stepCounts.getValue(SudokuTeacher.FALLBACK)
            val boardLimit = 0
            failures += listOfNotNull(
                "$difficulty: an explanation too long for the panel: $longest"
                    .takeIf { longest.length > SudokuTeacher.MAX_EXPLANATION },
                "$difficulty: the fallback is reached on $fallbackBoards/$perTier boards"
                    .takeIf { fallbackBoards > boardLimit },
                "$difficulty: the fallback is $fallbackSteps of $totalSteps steps"
                    .takeIf { fallbackSteps * 50 > totalSteps },
            )
        }
        println(report)
        writeReport("sudoku-teaching-coverage.txt", report.toString())
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun board(seed: Long = 42L): SudokuState = Sudoku.generate(seed, Difficulty.HARD) as SudokuState

    @Test
    fun `a wrong digit outranks every step, names its clash, and taking it back clears it`() {
        val s = board()
        // A wrong digit that clashes: an empty cell given a digit one of its peers already holds.
        val cell = s.cells.indices.first { s.cells[it] == 0 }
        val peerDigit = Sudoku.peers(cell).map { s.cells[it] }.first { it != 0 && it != s.solution[cell] }
        val wrong = s.withCell(cell, peerDigit)
        val d = Sudoku.teach(wrong)!!
        assertTrue(d.mistake)
        assertEquals(setOf(cell), d.targets)
        assertTrue(d.explanation, d.explanation.contains("clashes with the $peerDigit"))
        assertTrue(d.cited.all { wrong.cells[it] == peerDigit })
        assertFalse(d.isReached(wrong))
        val cleared = wrong.withCell(cell, 0)
        assertTrue(d.isReached(cleared))
        assertFalse(Sudoku.teach(cleared)!!.mistake)
        assertEquals(0, (d.apply(wrong) as SudokuState).cells[cell])
    }

    @Test
    fun `a wrong digit with no clash is still caught`() {
        var found = 0
        for (seed in 1L..40L) {
            val s = board(seed)
            for (cell in s.cells.indices) {
                if (s.cells[cell] != 0) continue
                val quiet = (1..9).firstOrNull { d ->
                    d != s.solution[cell] && Sudoku.peers(cell).none { s.cells[it] == d }
                } ?: continue
                val wrong = s.withCell(cell, quiet)
                val d = Sudoku.teach(wrong)!!
                assertTrue(d.mistake)
                assertEquals(setOf(cell), d.targets)
                assertTrue(d.explanation.length <= SudokuTeacher.MAX_EXPLANATION)
                found++
                break
            }
        }
        assertTrue(found > 30)
    }

    @Test
    fun `a digit the answer agrees with is never called a mistake`() {
        for (seed in 1L..20L) {
            var s = board(seed)
            for (cell in s.cells.indices.filter { s.cells[it] == 0 }.take(10)) {
                s = s.withCell(cell, s.solution[cell])
                assertFalse(Sudoku.teach(s)!!.mistake)
            }
        }
    }

    @Test
    fun `the fallback says so, and points at the answer`() {
        // An empty board: nothing is forced, so only the fallback can speak.
        val s = board()
        val empty = s.copy(givens = List(81) { false }, cells = List(81) { 0 })
        val d = Sudoku.teach(empty)!!
        assertTrue(d.fallback)
        assertTrue(d.explanation.contains("from the answer"))
        val cell = d.targets.single()
        assertEquals(s.solution[cell], (d.apply(empty) as SudokuState).cells[cell])
    }

    // ---- notes ------------------------------------------------------------------------------------

    @Test
    fun `pencil marks never change what the teacher suggests or calls a mistake`() {
        // Notes of every kind: the answer, wrong digits, digits a peer rules out, whole cells of them.
        var x = 12345L
        fun next(): Int { x = x * 6364136223846793005L + 1442695040888963407L; return ((x ushr 33) % 512).toInt() }
        var compared = 0
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..25L) {
                val plain = Sudoku.generate(seed, difficulty) as SudokuState
                // Both a clean board and one carrying a wrong digit, so mistakes are compared too.
                val open = plain.cells.indices.first { plain.cells[it] == 0 }
                val boards = listOf(plain, plain.withCell(open, plain.solution[open] % 9 + 1))
                for (b in boards) {
                    val noted = b.copy(notes = List(81) { i -> if (b.cells[i] == 0) next() else 0 })
                    assertTrue(noted.notes.any { it != 0 })
                    val a = Sudoku.teach(b)
                    val n = Sudoku.teach(noted)
                    assertEquals(a == null, n == null)
                    if (a != null && n != null) {
                        assertEquals(a.technique, n.technique)
                        assertEquals(a.nudge, n.nudge)
                        assertEquals(a.explanation, n.explanation)
                        assertEquals(a.focus, n.focus)
                        assertEquals(a.cited, n.cited)
                        assertEquals(a.targets, n.targets)
                        assertEquals(a.mistake, n.mistake)
                        assertEquals(a.fallback, n.fallback)
                        // Applying the step places its digit and leaves the notes elsewhere alone.
                        val applied = n.apply(noted) as SudokuState
                        val cell = n.targets.single()
                        assertEquals(a.apply(b).let { (it as SudokuState).cells[cell] }, applied.cells[cell])
                        assertEquals(0, applied.notes[cell])
                        compared++
                    }
                }
            }
        }
        assertTrue(compared > 100)
    }

    // ---- walkthrough ------------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        assertTrue(valid(Sudoku.TUTORIAL_SOLUTION))
        val open = Sudoku.TUTORIAL_SOLUTION.mapIndexed { i, v -> if (i in Sudoku.TUTORIAL_OPEN) 0 else v }
        assertEquals(listOf(Sudoku.TUTORIAL_SOLUTION), allSolutions(open, 2))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Sudoku.tutorial
        assertEquals(5, frames.size)
        fun at(i: Int) = frames[i].state as SudokuState
        assertNull(frames[0].judge)

        // 2: one frame, two taps. Tapping the glowing cell selects it (progress, and the 6 glows
        // next), then 6 on the keys, as the Board does, places it; another cell or digit does not.
        val move = frames[1].steps!!
        val first = frames[1].glow!!(at(1)).strong.single()
        assertEquals(1, at(1).cells.take(9).count { it == 0 })
        assertEquals(0, at(1).cells[first])
        val selected = at(1).select(first)
        assertEquals(TutorialStep.PROGRESS, move(selected))
        assertEquals(setOf(SudokuTeacher.pad(6)), frames[1].glow!!(selected).strong)
        assertEquals(TutorialStep.REJECT, move(at(1).select(first + 9)))
        assertEquals(TutorialStep.REJECT, move(selected.withCell(first, 5)))
        assertEquals(6, Sudoku.TUTORIAL_SOLUTION[first])
        assertEquals(TutorialStep.DONE, move(selected.withCell(first, 6)))
        assertEquals(at(2).cells, selected.withCell(first, 6).cells)

        // 3: the player makes the clash. Tap the glowing square, then 9 (it is red and a mistake),
        // then 9 again, which the pad reads as clearing it; the glow turns red when the 9 lands.
        val clash = frames[2].steps!!
        val glow = frames[2].glow!!
        assertEquals(TutorialStep.REJECT, clash(at(2)))
        val wrong = glow(at(2)).strong.single()
        assertEquals(0, at(2).cells[wrong])
        val picked = at(2).select(wrong)
        assertEquals(TutorialStep.PROGRESS, clash(picked))
        assertEquals(setOf(SudokuTeacher.pad(9)), glow(picked).strong)
        assertEquals(TutorialStep.REJECT, clash(picked.enter(wrong, 5, notesMode = false)))
        assertEquals(TutorialStep.REJECT, clash(at(2).select(0)))
        val nine = picked.enter(wrong, 9, notesMode = false)
        assertEquals(TutorialStep.PROGRESS, clash(nine))
        assertTrue(glow(nine).warning)
        assertTrue(wrong in nine.conflicts())
        assertTrue(Sudoku.teach(nine)!!.mistake)
        // The claim: the 9s it clashes with are one in its column and one in its box (not its row).
        val soft = glow(nine).soft
        assertEquals(2, soft.size)
        assertTrue(soft.all { nine.cells[it] == 9 && it in nine.conflicts() })
        assertTrue(soft.any { it % 9 == wrong % 9 })
        assertTrue(soft.any { it % 9 != wrong % 9 && it / 9 != wrong / 9 })
        assertTrue(soft.none { it / 9 == wrong / 9 })
        val cleared = nine.enter(wrong, 9, notesMode = false)
        assertEquals(TutorialStep.DONE, clash(cleared))
        assertEquals(at(3).cells, cleared.cells)

        // 4: pencilling a note is SudokuNotesTest's. 5: free play, finishable by hints without the
        // fallback or a mistake, from a board that carries the note the last frame asked for.
        assertTrue(frames[4].freePlay)
        assertTrue(at(4).notes.any { it != 0 })
        var s = at(4)
        while (!s.solved) {
            val d = Sudoku.teach(s)!!
            assertFalse(d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as SudokuState
        }
        assertEquals(Sudoku.TUTORIAL_SOLUTION, s.cells)

        frames.forEachIndexed { i, f ->
            assertTrue("frame ${i + 1} caption is ${f.caption.length} chars", f.caption.length <= 170)
        }
    }
}
