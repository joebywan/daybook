package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.NonogramTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Nonogram hint solver. Soundness is checked two ways, as for Kings: on real boards every step
 * must agree with the stored picture; and, because on a one-answer board every true fact is
 * derivable, which cannot tell reasoning from peeking, on small boards with *several* answers every
 * step must hold for every answer the visible marks still allow ([NonogramOracle.answers]).
 */
class NonogramTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "nonogram-teach") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    private fun make(width: Int, height: Int, picture: String, cells: String = ".".repeat(width * height)) =
        NonogramState(width, height, picture, cells)

    /** [s] with [step]'s fills and crosses laid on, as Show me does. */
    private fun applied(s: NonogramState, step: NonogramTeacher.Step): NonogramState {
        val next = s.cells.toCharArray()
        step.clears.forEach { next[it] = NonogramLogic.UNMARKED }
        step.fills.forEach { next[it] = NonogramLogic.FILLED }
        step.crosses.forEach { next[it] = NonogramLogic.CROSSED }
        return s.copy(cells = String(next))
    }

    /** Marks taken from [solution]: each true square filled, each false one crossed, with chance [keep]/10. */
    private fun partial(s: NonogramState, rng: Rng, keep: Int): NonogramState =
        s.copy(cells = String(CharArray(s.cells.length) {
            if (rng.nextInt(10) >= keep) NonogramLogic.UNMARKED
            else if (s.solution[it] == '1') NonogramLogic.FILLED else NonogramLogic.CROSSED
        }))

    /** What every layout of [clue] consistent with [marks] agrees on, by trying all 2^n lines. */
    private fun bruteForced(clue: List<Int>, marks: IntArray): IntArray? {
        val n = marks.size
        val fits = (0 until (1 shl n)).filter { mask ->
            val line = List(n) { mask shr it and 1 == 1 }
            NonogramOracle.runsOf(line) == clue && (0 until n).all {
                marks[it] == NonogramLogic.UNKNOWN || (marks[it] == NonogramLogic.FILL) == line[it]
            }
        }
        if (fits.isEmpty()) return null
        return IntArray(n) { x ->
            if (marks[x] != NonogramLogic.UNKNOWN) NonogramLogic.UNKNOWN
            else when {
                fits.all { it shr x and 1 == 1 } -> NonogramLogic.FILL
                fits.none { it shr x and 1 == 1 } -> NonogramLogic.CROSS
                else -> NonogramLogic.UNKNOWN
            }
        }
    }

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the picture and changes something`() {
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(40, d)) {
            var s = NonogramLogic.generateVerified(seed, d)!!
            while (!s.solved) {
                val step = NonogramTeacher.teach(s)
                assertNotNull("$d/$seed: no step on an unsolved board", step)
                step!!
                assertTrue(step.fills.isNotEmpty() || step.crosses.isNotEmpty())
                step.fills.forEach { assertEquals("$d/$seed fill $it", '1', s.solution[it]); assertEquals('.', s.cells[it]) }
                step.crosses.forEach { assertEquals("$d/$seed cross $it", '0', s.solution[it]); assertEquals('.', s.cells[it]) }
                assertEquals(NonogramTeacher.TECHNIQUES.contains(step.technique), true)
                s = applied(s, step)
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 1000)
    }

    @Test
    fun `hints alone solve every board, with no mistake and nothing left to guess`() {
        for (d in Difficulty.entries) for (seed in seeds(365, d, "nonogram-walk")) {
            var s = NonogramLogic.generateVerified(seed, d)!!
            var guard = 0
            while (!s.solved) {
                assertTrue("$d/$seed: walk did not finish", guard++ < 700)
                val step = NonogramTeacher.teach(s)
                assertNotNull("$d/$seed: stuck with no step", step)
                assertFalse(step!!.technique == NonogramTeacher.MISTAKE)
                s = applied(s, step)
            }
        }
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just from the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(25, d, "nonogram-player")) {
            val full = NonogramLogic.generateVerified(seed, d)!!
            repeat(8) {
                val s = partial(full, rng, rng.nextInt(1, 9))
                if (s.solved) return@repeat
                val step = NonogramTeacher.teach(s)
                assertNotNull("$d/$seed: no step from marks ${s.cells}", step)
                step!!
                assertFalse("correct marks are never a mistake", step.technique == NonogramTeacher.MISTAKE)
                step.fills.forEach { assertEquals('1', full.solution[it]) }
                step.crosses.forEach { assertEquals('0', full.solution[it]) }
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 400)
    }

    // ---- soundness when there are several answers -----------------------------------------------

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = Rng(8675309)
        var checked = 0
        var ambiguous = 0
        for (size in listOf(3, 4, 5)) {
            repeat(120) {
                val picture = String(CharArray(size * size) { if (rng.nextInt(100) < 55) '1' else '0' })
                val base = make(size, size, picture)
                val rows = NonogramLogic.rowClues(base)
                val cols = NonogramLogic.colClues(base)
                val all = NonogramOracle.answers(size, size, rows, cols)
                if (all.size > 1) ambiguous++
                // The marks a player might have: some squares of one answer, possibly none.
                repeat(4) {
                    val marked = partial(base, rng, rng.nextInt(0, 7))
                    val possible = all.filter { a ->
                        marked.cells.indices.all { i ->
                            marked.cells[i] == '.' || (marked.cells[i] == '#') == a[i]
                        }
                    }
                    val step = NonogramTeacher.deduce(size, size, rows, cols, marked.cells) ?: return@repeat
                    step.fills.forEach { i -> assertTrue("fill $i on $picture marks ${marked.cells}", possible.all { it[i] }) }
                    step.crosses.forEach { i -> assertTrue("cross $i on $picture marks ${marked.cells}", possible.none { it[i] }) }
                    checked++
                }
            }
        }
        assertTrue("barely checked anything: $checked", checked > 500)
        assertTrue("the sample must include boards with more than one answer: $ambiguous", ambiguous > 10)
    }

    // ---- the techniques, one by one -----------------------------------------------------------------

    @Test
    fun `a line whose clues fill its width exactly is the first thing taught`() {
        // Row 1 reads 2 2 in five squares: no slack at all.
        val s = make(5, 5, "11011" + "11111" + "11111" + "01110" + "00100")
        val step = NonogramTeacher.teach(s)!!
        // Rows 2 and 3 read 5, the same lesson, but row 1 comes first in reading order.
        assertEquals(NonogramTeacher.FULL, step.technique)
        assertEquals(setOf(0, 1, 3, 4), step.fills.toSet())
        assertEquals(listOf(2), step.crosses)
        assertTrue(step.explanation, "Row 1" in step.explanation && "2 + 2 + 1 gap = 5" in step.explanation)
        assertTrue("the row's clue is highlighted", NonogramLogic.rowClueIndex(5, 5, 0) in step.focus)
        assertTrue("and so are its squares", step.focus.containsAll(listOf(0, 1, 2, 3, 4)))
    }

    @Test
    fun `a line that already shows all its clues has the rest crossed`() {
        // A single 2, already filled in row 1; the other squares of the row are empty.
        val s = make(5, 3, "11000" + "01110" + "00100", cells = "##...".padEnd(15, '.'))
        val step = NonogramTeacher.deduce(5, 3, NonogramLogic.rowClues(s), NonogramLogic.colClues(s), s.cells)!!
        assertEquals(NonogramTeacher.FINISHED, step.technique)
        assertEquals(listOf(2, 3, 4), step.crosses)
        assertTrue(step.fills.isEmpty())
        assertTrue(step.explanation, "Row 1" in step.explanation && "already has all of its clue 2" in step.explanation)
    }

    @Test
    fun `overlap fills the squares every slide of the clue covers`() {
        // Row 1 reads 2 1 in five squares: one square of slack, so the 2 can sit two ways and only its
        // second square is certain. No line of this board is as tight as that or tighter.
        val s = make(5, 5, "1101001001011110111110001")
        val step = NonogramTeacher.teach(s)!!
        assertEquals(NonogramTeacher.OVERLAP, step.technique)
        assertEquals(listOf(1), step.fills)
        assertTrue(step.explanation, "row 1" in step.explanation && "square 2" in step.explanation && "covered both times" in step.explanation)
    }

    @Test
    fun `line logic uses what is already marked in the line`() {
        // Row 2 reads 2 1 and already has a filled 2 and a cross: the 1 cannot touch the 2, and that
        // is a fact only the marks, not the clue alone, give.
        val s = make(5, 5, "0010111001101110111010000", cells = ".....##...#x###..#.......")
        val step = NonogramTeacher.teach(s)!!
        assertEquals(NonogramTeacher.LINE, step.technique)
        assertEquals(listOf(7), step.crosses)
        assertTrue(step.explanation, "marks already in row 2" in step.explanation && "square 3" in step.explanation)
    }

    @Test
    fun `every step is exactly what brute force says its line allows`() {
        // The step names no line, so find it again: the one whose squares the focus covers. FULL and
        // LINE steps must be every square that line settles; OVERLAP and FINISHED a part of it.
        for (d in Difficulty.entries) for (seed in seeds(12, d, "nonogram-brute")) {
            var s = NonogramLogic.generateVerified(seed, d)!!
            val rows = NonogramLogic.rowClues(s)
            val cols = NonogramLogic.colClues(s)
            var guard = 0
            while (!s.solved && guard++ < 700) {
                val step = NonogramTeacher.teach(s)!!
                val squares = step.focus.filter { it < s.width * s.height }
                val line = (0 until s.height + s.width).first { l ->
                    NonogramLogic.lineCells(s.width, s.height, l).toSet() == squares.toSet()
                }
                val clue = if (line < s.height) rows[line] else cols[line - s.height]
                val at = NonogramLogic.lineCells(s.width, s.height, line)
                val marks = IntArray(at.size) {
                    when (s.cells[at[it]]) { '#' -> NonogramLogic.FILL; 'x' -> NonogramLogic.CROSS; else -> NonogramLogic.UNKNOWN }
                }
                val want = bruteForced(clue, marks)!!
                val wantFills = at.filterIndexed { x, _ -> want[x] == NonogramLogic.FILL }
                val wantCrosses = at.filterIndexed { x, _ -> want[x] == NonogramLogic.CROSS }
                when (step.technique) {
                    NonogramTeacher.FULL, NonogramTeacher.LINE -> {
                        assertEquals("$d/$seed ${step.technique}", wantFills, step.fills)
                        assertEquals("$d/$seed ${step.technique}", wantCrosses, step.crosses)
                    }
                    NonogramTeacher.OVERLAP -> assertTrue(wantFills.containsAll(step.fills) && step.crosses.isEmpty())
                    NonogramTeacher.FINISHED -> assertEquals(wantCrosses, step.crosses)
                }
                s = applied(s, step)
            }
        }
    }

    @Test
    fun `columns are read too, and named as columns`() {
        // Three rows of five: no row is tight, but column 1 reads 1 1 in three squares, which is.
        val s = make(5, 3, "11000" + "01100" + "10010")
        val step = NonogramTeacher.teach(s)!!
        assertEquals(NonogramTeacher.FULL, step.technique)
        assertTrue(step.explanation, step.explanation.startsWith("Column 1 has 3 squares"))
        assertEquals(listOf(0, 10), step.fills)
        assertEquals(listOf(5), step.crosses)
        assertTrue(NonogramLogic.colClueIndex(5, 3, 0) in step.focus)
    }

    @Test
    fun `a line with no clue is crossed out, in words that make sense`() {
        val s = make(3, 1, "110")
        val blankCol = NonogramTeacher.deduce(3, 1, listOf(listOf(2)), listOf(listOf(1), listOf(1), emptyList()), "...")!!
        // Columns 1 and 2 read 1 in one square (tight); the empty third column waits its turn.
        assertEquals(NonogramTeacher.FULL, blankCol.technique)
        val after = s.copy(cells = "##.")
        val step = NonogramTeacher.deduce(3, 1, listOf(listOf(2)), listOf(listOf(1), listOf(1), emptyList()), after.cells)!!
        assertEquals(NonogramTeacher.FINISHED, step.technique)
        assertEquals(listOf(2), step.crosses)
        assertTrue(step.explanation, "no clue" in step.explanation || "all of its" in step.explanation)
    }

    @Test
    fun `techniques are tried simplest first, and the first line wins a tie`() {
        for (d in Difficulty.entries) for (seed in seeds(20, d, "nonogram-order")) {
            val s = NonogramLogic.generateVerified(seed, d)!!
            val step = NonogramTeacher.teach(s)!!
            // From a blank board nothing is crossed yet, so no line can be FINISHED; if any line is
            // FULL that is what the hint is.
            val anyFull = (NonogramLogic.rowClues(s) + NonogramLogic.colClues(s)).any {
                it.isNotEmpty() && NonogramLogic.minLength(it) == s.width
            }
            assertEquals("$d/$seed", anyFull, step.technique == NonogramTeacher.FULL)
        }
    }

    // ---- mistakes -----------------------------------------------------------------------------------

    @Test
    fun `a wrong fill is addressed first, with its clues cited, and taking it back clears it`() {
        val s = make(5, 5, "11011" + "11111" + "11111" + "01110" + "00100", cells = ".".repeat(24) + "#")
        // r4c4 is empty in the picture.
        val step = NonogramTeacher.teach(s)!!
        assertEquals(NonogramTeacher.MISTAKE, step.technique)
        assertEquals(listOf(24), step.clears)
        assertTrue(step.explanation, "row 5, column 5" in step.explanation && "filled" in step.explanation)
        assertEquals(
            setOf(NonogramLogic.rowClueIndex(5, 5, 4), NonogramLogic.colClueIndex(5, 5, 4)),
            step.cited,
        )
        assertEquals(NonogramTeacher.FULL, NonogramTeacher.teach(applied(s, step))!!.technique)
    }

    @Test
    fun `a wrong cross is a mistake, and a cross on an empty square is not`() {
        val s = make(5, 5, "11011" + "11111" + "11111" + "01110" + "00100", cells = "x" + ".".repeat(24))
        val step = NonogramTeacher.teach(s)!!
        assertEquals(NonogramTeacher.MISTAKE, step.technique)
        assertTrue(step.explanation, "can't be empty" in step.explanation)
        val fine = s.copy(cells = ".".repeat(24) + "x") // r4c4 is empty in the picture
        assertFalse(NonogramTeacher.teach(fine)!!.technique == NonogramTeacher.MISTAKE)
    }

    @Test
    fun `a planted mistake on a real board outranks every step`() {
        val rng = Rng(31337)
        for (d in Difficulty.entries) for (seed in seeds(15, d, "nonogram-mistake")) {
            val full = NonogramLogic.generateVerified(seed, d)!!
            val s0 = partial(full, rng, 5)
            val i = rng.nextInt(s0.cells.length)
            val wrong = if (full.solution[i] == '1') NonogramLogic.CROSSED else NonogramLogic.FILLED
            val cells = s0.cells.toCharArray().also { it[i] = wrong }
            val s = s0.copy(cells = String(cells))
            val step = NonogramTeacher.teach(s)!!
            assertEquals("$d/$seed", NonogramTeacher.MISTAKE, step.technique)
            // The first wrong mark in reading order, which may be earlier than the one planted.
            assertTrue(step.clears.single() <= i)
            // Taking it back leaves the square untouched, and the walk then carries on.
            val fixed = applied(s, step)
            assertEquals(NonogramLogic.UNMARKED, fixed.cells[step.clears.single()])
        }
    }

    // ---- text -----------------------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(2468)
        var checked = 0
        val longest = mutableMapOf<String, Int>()
        for (d in Difficulty.entries) for (seed in seeds(120, d, "nonogram-text")) {
            val full = NonogramLogic.generateVerified(seed, d)!!
            repeat(4) { round ->
                var s = partial(full, rng, rng.nextInt(0, 9))
                if (round == 3) {
                    val i = rng.nextInt(s.cells.length)
                    s = s.copy(cells = String(s.cells.toCharArray().also {
                        it[i] = if (full.solution[i] == '1') NonogramLogic.CROSSED else NonogramLogic.FILLED
                    }))
                }
                val step = NonogramTeacher.teach(s) ?: return@repeat
                assertTrue("nudge too long (${step.nudge.length}): ${step.nudge}", step.nudge.length <= NonogramTeacher.MAX_NUDGE)
                assertTrue(
                    "explanation too long (${step.explanation.length}): ${step.explanation}",
                    step.explanation.length <= NonogramTeacher.MAX_EXPLANATION,
                )
                longest[step.technique] = maxOf(longest[step.technique] ?: 0, step.explanation.length)
                checked++
            }
        }
        println("longest explanation per technique: $longest")
        assertTrue("barely checked anything: $checked", checked > 800)
    }

    // ---- coverage -------------------------------------------------------------------------------------

    /**
     * Not a correctness check but a measurement, printed and written to
     * `app/build/reports/nonogram-teaching-coverage.txt`: which technique a player walking a board
     * by hints alone needs, per tier. Nothing here can fall back: a line-solvable board always has a
     * line with a square to settle, so the assertion is that the walk never runs out of steps.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 200
        val report = StringBuilder()
        report.appendLine("Nonogram teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = NonogramTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = NonogramTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "nonogram-coverage")) {
                var s = NonogramLogic.generateVerified(seed, d)!!
                val used = mutableSetOf<String>()
                while (!s.solved) {
                    val step = NonogramTeacher.teach(s)
                    assertNotNull("$d/$seed: the walk ran out of steps", step)
                    stepCounts[step!!.technique] = stepCounts.getValue(step.technique) + 1
                    used += step.technique
                    total++
                    s = applied(s, step)
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${d.name}: $total steps, ${"%.1f".format(total / perTier.toDouble())} per board")
            for (t in NonogramTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-14s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/nonogram-teaching-coverage.txt").writeText(report.toString())
    }
}
