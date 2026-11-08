package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.ThermometersLogic
import com.joebywan.daybook.puzzles.ThermometersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.TimeSource

/**
 * Thermometers' rules, ladder, count and generator, checked against [ThermometersOracle] (square by square, no levels,
 * no line logic) and counts read off the answer here, neither of which shares anything with the code under test.
 */
class ThermometersRulesTest {

    /** Counts read independently: rows then columns, the filled squares of the answer. */
    private fun countsOf(s: ThermometersState): Pair<List<Int>, List<Int>> {
        val n = s.size
        val rows = List(n) { r -> (0 until n).count { c -> s.fill[r * n + c] == '1' } }
        val cols = List(n) { c -> (0 until n).count { r -> s.fill[r * n + c] == '1' } }
        return rows to cols
    }

    @Test
    fun `counts are the answer's filled squares in each row and column`() {
        for (d in Difficulty.entries) for (seed in seeds(30, d, "thermometers")) {
            val b = ThermometersLogic.generateVerified(seed, d)!!
            val (rows, cols) = countsOf(b)
            assertEquals(rows + cols, ThermometersLogic.targets(b))
        }
    }

    @Test
    fun `solved checks the rules, so a different legal fill with the same counts wins`() {
        // Four one-square thermometers, every count 1: the two diagonals both fit.
        val s = ThermometersState(2, listOf(listOf(0), listOf(1), listOf(2), listOf(3)), "1001")
        assertEquals(listOf(1, 1, 1, 1), ThermometersLogic.targets(s))
        assertEquals(2, ThermometersOracle.answers(2, s.tubes, listOf(1, 1), listOf(1, 1)).size)
        assertTrue(ThermometersLogic.countSolutions(2, s.tubes, listOf(1, 1, 1, 1)) is Verdict.Several)
        assertTrue(s.copy(marks = "#..#").solved)
        assertTrue("the other diagonal wins too", s.copy(marks = ".##.").solved)
        assertFalse(s.copy(marks = "##..").solved)
        assertFalse("nothing filled is not solved", s.solved)
    }

    @Test
    fun `a gap is never solved, even with every count right`() {
        // Bulbs 0 and 3; filling the two tips instead of the bulbs gives the same counts.
        val s = ThermometersState(2, listOf(listOf(0, 1), listOf(3, 2)), "1001")
        assertTrue(s.copy(marks = "#..#").solved)
        assertFalse(s.copy(marks = ".##.").solved)
        assertTrue(ThermometersLogic.tubeBroken(s.copy(marks = "x#.."), 0))
        assertFalse("untouched under a fill is not yet a break", ThermometersLogic.tubeBroken(s.copy(marks = ".#.."), 0))
    }

    @Test
    fun `a cross counts as empty, exactly like an untouched square`() {
        val s = ThermometersState(2, listOf(listOf(0, 1), listOf(3, 2)), "1001")
        assertTrue(s.copy(marks = "#xx#").solved)
    }

    // ---- input ------------------------------------------------------------------------------------

    @Test
    fun `a fill tap fills the tube from its bulb to the square, as one move`() {
        val s = ThermometersState(3, listOf(listOf(6, 3, 0), listOf(1, 2, 5), listOf(4, 7, 8)), "1".repeat(9))
        val t = s.tap(0, ThermometersLogic.FILLED)
        assertEquals("#..#..#..", t.marks)
        assertEquals(1, t.moves)
        // Tapping lower takes the fill above back to untouched.
        assertEquals("...#..#..", t.tap(3, ThermometersLogic.FILLED).marks)
        // Tapping the top of the fill lowers it below that square.
        assertEquals("...#..#..", t.tap(0, ThermometersLogic.FILLED).marks)
        assertEquals(".........", t.tap(0, ThermometersLogic.FILLED).tap(3, ThermometersLogic.FILLED).tap(6, ThermometersLogic.FILLED).marks)
    }

    @Test
    fun `a cross tap crosses from the square to the tip, and never leaves a fill over a cross`() {
        val s = ThermometersState(3, listOf(listOf(6, 3, 0), listOf(1, 2, 5), listOf(4, 7, 8)), "1".repeat(9))
        val filled = s.tap(0, ThermometersLogic.FILLED)
        val crossed = filled.tap(3, ThermometersLogic.CROSSED)
        assertEquals("x..x..#..", crossed.marks)
        // The lowest cross of a run clears the run.
        assertEquals("......#..", crossed.tap(3, ThermometersLogic.CROSSED).marks)
        // Crossing higher takes the crosses below back.
        assertEquals("x.....#..", crossed.tap(0, ThermometersLogic.CROSSED).marks)
        // Filling over a cross takes the crosses it passes.
        assertEquals("...#..#..", crossed.tap(3, ThermometersLogic.FILLED).let { it.marks })
        assertSame("a tap that changes nothing is the same state", crossed, crossed.paint(listOf(0), ThermometersLogic.CROSSED))
    }

    @Test
    fun `a sweep is one state and marks only the squares it passes`() {
        val s = ThermometersState(3, listOf(listOf(6, 3, 0), listOf(1, 2, 5), listOf(4, 7, 8)), "1".repeat(9))
        val swept = s.sweep(listOf(0, 1, 2), ThermometersLogic.CROSSED, ThermometersLogic.UNMARKED)
        assertEquals("xxx......", swept.marks)
        assertEquals(3, swept.moves)
    }

    // ---- the ladder against the oracle -------------------------------------------------------------

    /** A random small board by the real generator's layout at a made-up spec. */
    private fun smallBoard(rng: Rng, n: Int): ThermometersState? =
        ThermometersLogic.draw(rng, ThermometersLogic.Spec(n, 2, n, 50, ThermometersLogic.SHORTCUTS))

    @Test
    fun `every fact the ladder finds holds in every answer the marks allow`() {
        val rng = Rng(4242)
        var facts = 0
        var whatIfs = 0
        repeat(600) {
            val n = rng.nextInt(3, 4)
            val s = smallBoard(rng, n) ?: return@repeat
            val (rows, cols) = countsOf(s)
            val answers = ThermometersOracle.answers(n, s.tubes, rows, cols)
            // Marks some of a random answer would make, closed by the ladder's own first rung, so lines are asked.
            val answer = answers[rng.nextInt(answers.size)]
            val grid = IntArray(n * n) { i ->
                if (rng.nextInt(4) == 0) (if (answer[i] == '1') ThermometersLogic.FULL else ThermometersLogic.EMPTY) else ThermometersLogic.OPEN
            }
            val l = ThermometersLogic.layout(s)
            val targets = rows + cols
            val closed = ThermometersLogic.solveBy(l, targets, grid, ThermometersLogic.TUBE) ?: error("a true answer's marks never break")
            val fits = answers.filter { a -> closed.indices.all { closed[it] == ThermometersLogic.OPEN || (closed[it] == ThermometersLogic.FULL) == (a[it] == '1') } }
            assertTrue(fits.isNotEmpty())
            for (kind in ThermometersLogic.DONE..ThermometersLogic.AT_LEAST) for (line in 0 until 2 * n) {
                for (f in ThermometersLogic.lineFacts(l, closed, targets, line, kind)) {
                    facts++
                    for (a in fits) {
                        f.full.forEach { assertEquals("fill $it", '1', a[it]) }
                        f.empty.forEach { assertEquals("empty $it", '0', a[it]) }
                    }
                }
            }
            ThermometersLogic.firstWhatIf(l, targets, closed)?.let { w ->
                whatIfs++
                val wrong = if (w.assume == ThermometersLogic.FULL) '1' else '0'
                assertTrue("the supposition is false in every answer", fits.none { it[w.square] == wrong })
            }
        }
        assertTrue("facts: $facts", facts > 500)
        println("what-ifs checked: $whatIfs")
    }

    // ---- the count against the oracle --------------------------------------------------------------

    @Test
    fun `the count agrees with the oracle, including boards with several answers or none`() {
        val rng = Rng(777)
        var several = 0
        var none = 0
        var one = 0
        repeat(1500) {
            val n = rng.nextInt(2, 5)
            val s = smallBoard(rng, n) ?: return@repeat
            val madeUp = rng.nextInt(3) == 0
            val (realRows, realCols) = countsOf(s)
            val rows = if (madeUp) List(n) { rng.nextInt(0, n) } else realRows
            val cols = if (madeUp) List(n) { rng.nextInt(0, n) } else realCols
            val oracle = ThermometersOracle.answers(n, s.tubes, rows, cols)
            when (val v = ThermometersLogic.countSolutions(n, s.tubes, rows + cols)) {
                Verdict.None -> { assertTrue(oracle.isEmpty()); none++ }
                is Verdict.ExactlyOne -> { assertEquals(listOf(v.value), oracle); one++ }
                Verdict.Several -> { assertTrue(oracle.size > 1); several++ }
            }
        }
        assertTrue("the sample must include several-answer boards: $several", several > 20)
        assertTrue("and impossible ones: $none", none > 20)
        assertTrue("and single ones: $one", one > 20)
    }

    // ---- generated boards --------------------------------------------------------------------------------

    @Test
    fun `tubes tile the board, each a run of neighbouring squares the tier allows`() {
        for (d in Difficulty.entries) {
            val spec = ThermometersLogic.specFor(d)
            for (seed in seeds(100, d, "thermometers-tiles")) {
                val s = ThermometersLogic.generateVerified(seed, d)!!
                val n = s.size
                assertEquals((0 until n * n).toList(), s.tubes.flatten().sorted())
                assertEquals("bulbs in reading order", s.tubes.map { it.first() }.sorted(), s.tubes.map { it.first() })
                for (tube in s.tubes) {
                    assertTrue("$d/$seed $tube", tube.size in 2..spec.maxLength)
                    for (k in 1 until tube.size) {
                        val a = tube[k - 1]
                        val b = tube[k]
                        assertEquals("$d/$seed $tube", 1, kotlin.math.abs(a / n - b / n) + kotlin.math.abs(a % n - b % n))
                    }
                }
            }
        }
    }

    @Test
    fun `every generated board has exactly one answer by the oracle, and it is the stored one`() {
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 12 else 80, d, "thermometers-oracle")) {
            val s = ThermometersLogic.generateVerified(seed, d)!!
            val (rows, cols) = countsOf(s)
            assertEquals("$d/$seed", listOf(s.fill), ThermometersOracle.answers(s.size, s.tubes, rows, cols, cap = 2))
        }
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        // Larger, and each needs a deeper rung of the ladder than the one before.
        val sizes = Difficulty.entries.map { ThermometersLogic.specFor(it).side }
        assertEquals(sizes.sorted(), sizes)
        for ((i, d) in Difficulty.entries.withIndex()) {
            assertEquals(i, ThermometersLogic.specFor(d).needs)
            for (seed in seeds(60, d, "thermometers-depth")) {
                val s = ThermometersLogic.generateVerified(seed, d)!!
                assertEquals("$d/$seed", sizes[i], s.size)
                assertEquals("$d/$seed", i, ThermometersLogic.depth(s))
            }
        }
    }

    @Test
    fun `the last resort of every tier is a board that tier would accept, with one answer`() {
        for (d in Difficulty.entries) {
            val s = ThermometersLogic.lastResort(d)
            assertEquals(ThermometersLogic.specFor(d).side, s.size)
            assertTrue(d.name, ThermometersLogic.accepts(s, ThermometersLogic.specFor(d)))
            val (rows, cols) = countsOf(s)
            assertEquals(listOf(s.fill), ThermometersOracle.answers(s.size, s.tubes, rows, cols, cap = 2))
        }
    }

    /** A year of daily boards per tier: draws needed, fill share and generation time, written to a report. */
    @Test
    fun `a year of daily boards - draws, fill and time per tier`() {
        val out = StringBuilder()
        for (d in Difficulty.entries) {
            val draws = ArrayList<Int>()
            val ms = ArrayList<Double>()
            var filled = 0
            var squares = 0
            var tubes = 0
            var whatIfs = 0
            for (seed in (0 until 365).map { daySeed(it, "thermometers", d) }) {
                val start = TimeSource.Monotonic.markNow()
                var a = 0
                var s: ThermometersState? = null
                while (s == null && a < ThermometersLogic.ATTEMPTS) s = ThermometersLogic.attempt(seed, d, a++)
                ms += start.elapsedNow().inWholeMicroseconds / 1000.0
                assertNotNull("$d/$seed", s)
                draws += a
                filled += s!!.fill.count { it == '1' }
                squares += s.fill.length
                tubes += s.tubes.size
                if (d == Difficulty.EXPERT) whatIfs += ThermometersLogic.settle(ThermometersLogic.layout(s), ThermometersLogic.targets(s), IntArray(s.size * s.size)).second
            }
            ms.sort()
            val days = draws.size
            out.appendLine(
                "$d: draws mean %.1f max %d of %d; filled %.0f%%; tubes %.1f a board; ms median %.1f p95 %.1f max %.1f%s".format(
                    draws.average(), draws.max(), ThermometersLogic.ATTEMPTS, 100.0 * filled / squares, tubes.toDouble() / days,
                    ms[days / 2], ms[days * 95 / 100], ms.last(),
                    if (d == Difficulty.EXPERT) "; what-ifs %.2f a board".format(whatIfs.toDouble() / days) else "",
                ),
            )
        }
        println(out)
        writeReport("thermometers-year.txt", out.toString())
    }
}
