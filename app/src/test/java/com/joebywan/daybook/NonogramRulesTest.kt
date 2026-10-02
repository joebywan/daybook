package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Nonogram's rules, its line solver and its generator, checked against [NonogramOracle] (every
 * arrangement of every row) and a brute-force enumerator of one line, neither of which shares
 * anything with the code under test.
 */
class NonogramRulesTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String = "nonogram") = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    private fun pictureOf(s: NonogramState) =
        List(s.height) { r -> List(s.width) { c -> s.solution[r * s.width + c] == '1' } }

    // ---- clues --------------------------------------------------------------------------------

    @Test
    fun `clues are the run lengths of the picture's rows and columns`() {
        val s = NonogramState(5, 4, "11010" + "00000" + "01110" + "10101")
        assertEquals(listOf(listOf(2, 1), emptyList(), listOf(3), listOf(1, 1, 1)), NonogramLogic.rowClues(s))
        assertEquals(
            listOf(listOf(1, 1), listOf(1, 1), listOf(2), listOf(1, 1), listOf(1)),
            NonogramLogic.colClues(s),
        )
    }

    @Test
    fun `every generated board's clues agree with an independent reading of its picture`() {
        for (d in Difficulty.entries) for (seed in seeds(40, d)) {
            val s = NonogramLogic.generateVerified(seed, d)!!
            val p = pictureOf(s)
            assertEquals(p.map { NonogramOracle.runsOf(it) }, NonogramLogic.rowClues(s))
            assertEquals(
                List(s.width) { c -> NonogramOracle.runsOf(p.map { it[c] }) },
                NonogramLogic.colClues(s),
            )
        }
    }

    // ---- the win check ------------------------------------------------------------------------

    private fun withFills(base: NonogramState, filled: Set<Int>) =
        base.copy(cells = String(CharArray(base.width * base.height) { if (it in filled) '#' else '.' }))

    @Test
    fun `solved checks the rules, so a different picture with the same clues wins`() {
        // A diagonal and its mirror: rows 1 1, columns 1 1, two pictures.
        val s = NonogramState(2, 2, "10" + "01")
        assertEquals(2, NonogramOracle.countAnswers(2, 2, NonogramLogic.rowClues(s), NonogramLogic.colClues(s)))
        assertTrue(withFills(s, setOf(0, 3)).solved)
        assertTrue("the other diagonal is a legal answer too", withFills(s, setOf(1, 2)).solved)
        assertFalse(withFills(s, setOf(0, 1)).solved)
        assertFalse(withFills(s, setOf(0)).solved)
        assertFalse("an empty board is not solved", withFills(s, emptySet()).solved)
    }

    @Test
    fun `crosses never count towards the win or against it`() {
        val s = NonogramState(3, 1, "110")
        val done = s.copy(cells = "##x")
        assertTrue(done.solved)
        assertTrue(s.copy(cells = "##.").solved)
        assertFalse(s.copy(cells = "xxx").solved)
        assertFalse("a filled square where the clue has none", s.copy(cells = "###").solved)
    }

    // ---- the line solver ----------------------------------------------------------------------

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

    @Test
    fun `the line solver agrees with brute force on every clue of a short line under random marks`() {
        val rng = com.joebywan.daybook.core.Rng(77)
        var checked = 0
        for (n in 1..9) {
            // Every clue that fits, taken from every mask: all the clues a line of n can have.
            val clues = (0 until (1 shl n)).map { mask -> NonogramOracle.runsOf(List(n) { mask shr it and 1 == 1 }) }.toSet()
            for (clue in clues) {
                repeat(10) {
                    // Marks drawn from a real layout (so they are consistent) and then thinned out, and
                    // now and then one deliberately wrong, so the contradiction path is covered too.
                    val layout = NonogramOracle.layouts(clue, n).let { it[rng.nextInt(it.size)] }
                    val marks = IntArray(n) {
                        when (rng.nextInt(4)) {
                            0 -> if (layout[it]) NonogramLogic.FILL else NonogramLogic.CROSS
                            else -> NonogramLogic.UNKNOWN
                        }
                    }
                    if (rng.nextInt(10) == 0) marks[rng.nextInt(n)] = if (rng.nextBoolean()) NonogramLogic.FILL else NonogramLogic.CROSS
                    val want = bruteForced(clue, marks)
                    val got = NonogramLogic.forced(clue, marks)
                    if (want == null) assertNull("clue $clue marks ${marks.toList()}", got)
                    else assertEquals("clue $clue marks ${marks.toList()}", want.toList(), got!!.toList())
                    checked++
                }
            }
        }
        assertTrue("barely checked anything: $checked", checked > 2000)
    }

    @Test
    fun `a blank line is forced only where every slide of the clue agrees`() {
        // 3 in 5: the middle square, and nothing else.
        assertEquals(listOf(0, 0, 1, 0, 0), NonogramLogic.forcedOnBlank(listOf(3), 5).toList())
        // 2 2 in 5: no slack, every square settled.
        assertEquals(listOf(1, 1, 2, 1, 1), NonogramLogic.forcedOnBlank(listOf(2, 2), 5).toList())
        // 1 in 5: nothing at all.
        assertEquals(listOf(0, 0, 0, 0, 0), NonogramLogic.forcedOnBlank(listOf(1), 5).toList())
        // No clue: every square empty.
        assertEquals(listOf(2, 2, 2), NonogramLogic.forcedOnBlank(emptyList(), 3).toList())
    }

    // ---- the generator ------------------------------------------------------------------------

    @Test
    fun `a year of daily boards on every tier is proved line-solvable, with one answer, and never falls back`() {
        for (d in Difficulty.entries) {
            for (seed in seeds(365, d)) {
                val s = NonogramLogic.generateVerified(seed, d)
                assertNotNull("$d/$seed fell back", s)
                s!!
                assertTrue("$d/$seed is not line-solvable", NonogramLogic.lineSolvable(s.width, s.height, s.solution))
                assertEquals(NonogramLogic.specFor(d).side, s.width)
                assertEquals(NonogramLogic.specFor(d).side, s.height)
            }
        }
    }

    @Test
    fun `an independent solver finds exactly one picture on generated boards`() {
        // The oracle counts answers by plain enumeration, so this is the check that "line-solvable"
        // really did imply "one answer". Thinner on the big boards, which take it longer.
        for ((d, count) in listOf(Difficulty.STANDARD to 365, Difficulty.HARD to 150, Difficulty.EXPERT to 40)) {
            for (seed in seeds(count, d, "nonogram-unique")) {
                val s = NonogramLogic.generateVerified(seed, d)!!
                val n = NonogramOracle.countAnswers(s.width, s.height, NonogramLogic.rowClues(s), NonogramLogic.colClues(s))
                assertEquals("$d/$seed has $n or more answers", 1, n)
            }
        }
    }

    @Test
    fun `the last resort is line-solvable and unique at every tier`() {
        for (d in Difficulty.entries) {
            val s = NonogramLogic.lastResort(d)
            assertTrue("$d", NonogramLogic.lineSolvable(s.width, s.height, s.solution))
            assertEquals("$d", 1, NonogramOracle.countAnswers(s.width, s.height, NonogramLogic.rowClues(s), NonogramLogic.colClues(s)))
            assertFalse("$d starts solved", s.solved)
        }
    }

    @Test
    fun `a board that needs a guess is not line-solvable, and the check can tell`() {
        // The diagonal pair has two answers, so no line can ever settle a square.
        assertFalse(NonogramLogic.lineSolvable(2, 2, "1001"))
        // Three squares in a row, one clue 3: settled by the overlap alone.
        assertTrue(NonogramLogic.lineSolvable(3, 1, "111"))
    }

    @Test
    fun `the tiers get larger in the order they are offered`() {
        val sides = Difficulty.entries.map { NonogramLogic.specFor(it).side }
        assertEquals(listOf(5, 10, 15), sides)
        assertEquals(sides.sorted(), sides)
        // And the work grows with them: more numbers to read on the bigger boards.
        val clueCount = Difficulty.entries.map { d ->
            seeds(30, d).sumOf { seed ->
                val s = NonogramLogic.generateVerified(seed, d)!!
                NonogramLogic.rowClues(s).sumOf { it.size } + NonogramLogic.colClues(s).sumOf { it.size }
            } / 30
        }
        assertTrue("clue counts $clueCount", clueCount[0] < clueCount[1] && clueCount[1] < clueCount[2])
    }

    @Test
    fun `no generated line is blank or full`() {
        for (d in Difficulty.entries) for (seed in seeds(60, d)) {
            val s = NonogramLogic.generateVerified(seed, d)!!
            (NonogramLogic.rowClues(s) + NonogramLogic.colClues(s)).forEach { clue ->
                assertTrue("$d/$seed has a blank line", clue.isNotEmpty())
                assertTrue("$d/$seed has a full line", clue != listOf(s.width))
            }
        }
    }

    // ---- marks --------------------------------------------------------------------------------

    private val blank = NonogramState(4, 1, "1100")

    @Test
    fun `a tap fills, a second tap with the same pen clears, and the other pen replaces`() {
        val filled = blank.tap(0, NonogramLogic.FILLED)
        assertEquals("#...", filled.cells)
        assertEquals(1, filled.moves)
        assertEquals("....", filled.tap(0, NonogramLogic.FILLED).cells)
        assertEquals("x...", filled.tap(0, NonogramLogic.CROSSED).cells)
    }

    @Test
    fun `a sweep is one state, and a pen only writes over untouched squares`() {
        val crossed = blank.copy(cells = ".x..")
        val swept = crossed.sweep(listOf(0, 1, 2), NonogramLogic.FILLED, NonogramLogic.UNMARKED)
        assertEquals("#x#.", swept.cells)
        assertEquals("two squares changed, two moves", 2, swept.moves)
        // An eraser started on a fill takes out fills only.
        val full = blank.copy(cells = "#x##")
        assertEquals(".x.#", full.sweep(listOf(0, 1, 2), NonogramLogic.UNMARKED, NonogramLogic.FILLED).cells)
        // Nothing to change returns the very same board, so the screen pushes no undo entry.
        assertTrue(swept === swept.sweep(listOf(0, 1, 2), NonogramLogic.FILLED, NonogramLogic.UNMARKED))
    }

    @Test
    fun `a sweep that starts on a mark lays down the pen's mark, or erases if the pen is already there`() {
        val s = blank.copy(cells = "#...")
        assertEquals(NonogramLogic.UNMARKED, s.sweepMark(0, NonogramLogic.FILLED))
        assertEquals(NonogramLogic.FILLED, s.sweepMark(1, NonogramLogic.FILLED))
        assertEquals(NonogramLogic.CROSSED, s.sweepMark(0, NonogramLogic.CROSSED))
    }
}
