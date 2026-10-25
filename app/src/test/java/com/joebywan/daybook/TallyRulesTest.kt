package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TallyVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tally's rules, line logic, count and generator, checked against [TallyOracle] (every keep mask of
 * every column, combined with no lookahead) and brute force over single lines, neither of which
 * shares anything with the code under test.
 */
class TallyRulesTest {

    private fun digitsOf(s: TallyState) = List(s.size * s.size) { s.digits[it] - '0' }

    /** Targets read independently: rows then columns, summing the kept digits. */
    private fun targetsOf(s: TallyState): Pair<List<Int>, List<Int>> {
        val n = s.size
        val d = digitsOf(s)
        val rows = List(n) { r -> (0 until n).sumOf { c -> if (s.keep[r * n + c] == '1') d[r * n + c] else 0 } }
        val cols = List(n) { c -> (0 until n).sumOf { r -> if (s.keep[r * n + c] == '1') d[r * n + c] else 0 } }
        return rows to cols
    }

    // ---- targets and the win check -----------------------------------------------------------------

    @Test
    fun `targets are what the answer keeps in each row and column`() {
        val s = TallyState(3, "472" + "935" + "618", "101" + "011" + "110")
        assertEquals(listOf(6, 8, 7), TallyLogic.rowTargets(s))
        assertEquals(listOf(10, 4, 7), TallyLogic.colTargets(s))
        for (d in Difficulty.entries) for (seed in seeds(30, d, "tally")) {
            val b = TallyLogic.generateVerified(seed, d)!!
            val (rows, cols) = targetsOf(b)
            assertEquals(rows + cols, TallyLogic.targets(b))
        }
    }

    @Test
    fun `solved checks the rules, so a different answer with the same targets wins`() {
        // Four 1s, targets 1 everywhere: two answers, the two diagonals.
        val s = TallyState(2, "1111", "1001")
        assertEquals(2, TallyOracle.answers(2, listOf(1, 1, 1, 1), listOf(1, 1), listOf(1, 1)).size)
        assertTrue(s.copy(marks = ".xx.").solved)
        assertTrue("the other diagonal wins too", s.copy(marks = "x..x").solved)
        assertFalse(s.copy(marks = "xx..").solved)
        assertFalse("nothing removed is not solved", s.solved)
        assertTrue(TallyLogic.countSolutions(2, intArrayOf(1, 1, 1, 1), listOf(1, 1, 1, 1)) is TallyVerdict.Several)
    }

    @Test
    fun `a circle counts like any number left in, and only removing takes a number away`() {
        val s = TallyState(2, "1111", "1001")
        assertTrue(s.copy(marks = "oxxo").solved)
        assertTrue(s.copy(marks = "oxx.").solved)
        assertFalse("circling is not removing", s.copy(marks = "oo..").solved)
    }

    // ---- line logic against brute force -----------------------------------------------------------------

    /** Every 0/1 mask of a line that respects [state] and adds up to [target], by brute force. */
    private fun bruteWays(values: IntArray, state: IntArray, target: Int): List<BooleanArray> =
        (0 until (1 shl values.size)).map { m -> BooleanArray(values.size) { m shr it and 1 == 1 } }.filter { keep ->
            values.indices.all { x ->
                when (state[x]) {
                    TallyLogic.STAY -> keep[x]
                    TallyLogic.GO -> !keep[x]
                    else -> true
                }
            } && values.indices.sumOf { if (keep[it]) values[it] else 0 } == target
        }

    @Test
    fun `forced, shortcuts and impossibility agree with brute force on random lines`() {
        val rng = Rng(1234)
        repeat(4000) {
            val n = rng.nextInt(2, 7)
            val values = IntArray(n) { rng.nextInt(1, 9) }
            val state = IntArray(n) { listOf(TallyLogic.OPEN, TallyLogic.OPEN, TallyLogic.STAY, TallyLogic.GO)[rng.nextInt(4)] }
            val target = rng.nextInt(0, values.sum())
            val all = bruteWays(values, state, target)
            val found = TallyLogic.forced(values, state, target)
            val short = TallyLogic.shortcuts(values, state, target)
            if (all.isEmpty()) {
                assertEquals(null, found)
                assertEquals(null, short)
                return@repeat
            }
            for (x in 0 until n) {
                val want = when {
                    state[x] != TallyLogic.OPEN -> TallyLogic.OPEN
                    all.all { it[x] } -> TallyLogic.STAY
                    all.none { it[x] } -> TallyLogic.GO
                    else -> TallyLogic.OPEN
                }
                assertEquals("values ${values.toList()} state ${state.toList()} target $target at $x", want, found!![x])
                // A shortcut is part of what the line forces, never more.
                if (short!![x] != TallyLogic.OPEN) assertEquals(want, short[x])
            }
        }
    }

    @Test
    fun `a line is impossible exactly when no choice of its open numbers makes the target`() {
        val rng = Rng(99)
        repeat(300) {
            val s = TallyState(4, String(CharArray(16) { '0' + rng.nextInt(1, 9) }), "1010" + "0110" + "1001" + "0101")
            val marks = String(CharArray(16) { ".xo"[rng.nextInt(3)] })
            val b = s.copy(marks = marks)
            val targets = TallyLogic.targets(b)
            for (line in 0 until 8) {
                val at = TallyLogic.lineCells(4, line)
                val values = IntArray(4) { b.digitAt(at[it]) }
                val state = IntArray(4) {
                    when (marks[at[it]]) { 'o' -> TallyLogic.STAY; 'x' -> TallyLogic.GO; else -> TallyLogic.OPEN }
                }
                assertEquals(bruteWays(values, state, targets[line]).isEmpty(), TallyLogic.lineImpossible(b, line, targets[line]))
            }
        }
    }

    // ---- the count against the oracle ------------------------------------------------------------------

    @Test
    fun `the count agrees with the oracle, including boards with several answers or none`() {
        val rng = Rng(777)
        var several = 0
        var none = 0
        repeat(1500) {
            val n = rng.nextInt(2, 5)
            val digits = List(n * n) { rng.nextInt(1, 4) }
            // Targets from a random answer (so at least one), or, a third of the time, made up.
            val keep = List(n * n) { rng.nextBoolean() }
            val madeUp = rng.nextInt(3) == 0
            val rows = List(n) { r -> if (madeUp) rng.nextInt(1, 6) else (0 until n).sumOf { if (keep[r * n + it]) digits[r * n + it] else 0 } }
            val cols = List(n) { c -> if (madeUp) rng.nextInt(1, 6) else (0 until n).sumOf { if (keep[it * n + c]) digits[it * n + c] else 0 } }
            val oracle = TallyOracle.answerStrings(n, digits, rows, cols)
            when (val v = TallyLogic.countSolutions(n, digits.toIntArray(), rows + cols)) {
                TallyVerdict.None -> { assertTrue(oracle.isEmpty()); none++ }
                is TallyVerdict.ExactlyOne -> assertEquals(listOf(v.keep), oracle)
                TallyVerdict.Several -> { assertTrue(oracle.size > 1); several++ }
            }
        }
        assertTrue("the sample must include several-answer boards: $several", several > 20)
        assertTrue("and impossible ones: $none", none > 20)
    }

    // ---- generated boards --------------------------------------------------------------------------------

    @Test
    fun `every generated board has exactly one answer by the oracle, and it is the stored one`() {
        for (d in Difficulty.entries) for (seed in seeds(if (d == Difficulty.EXPERT) 60 else 120, d, "tally-oracle")) {
            val s = TallyLogic.generateVerified(seed, d)!!
            val (rows, cols) = targetsOf(s)
            assertEquals("$d/$seed", listOf(s.keep), TallyOracle.answerStrings(s.size, digitsOf(s), rows, cols, cap = 2))
        }
    }

    @Test
    fun `every line keeps at least one number and removes at least one`() {
        var kept = 0
        var squares = 0
        for (d in Difficulty.entries) for (seed in seeds(200, d, "tally-lines")) {
            val s = TallyLogic.generateVerified(seed, d)!!
            for (line in 0 until 2 * s.size) {
                val keeps = TallyLogic.lineCells(s.size, line).count { s.keep[it] == '1' }
                assertTrue("$d/$seed line $line keeps $keeps", keeps in 1 until s.size)
            }
            kept += s.keep.count { it == '1' }
            squares += s.keep.length
        }
        println("share of squares kept: ${100.0 * kept / squares}%")
        assertTrue(kept * 100 / squares in 50..65)
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        // Larger, and each needs a deeper step of the ladder than the one before: Standard is finished
        // by the shortcuts alone, Hard needs the ways of a line, Expert needs a what-if.
        val sizes = Difficulty.entries.map { TallyLogic.specFor(it).side }
        assertEquals(sizes.sorted(), sizes)
        for ((i, d) in Difficulty.entries.withIndex()) {
            assertEquals(i, TallyLogic.specFor(d).needs)
            for (seed in seeds(60, d, "tally-depth")) {
                val s = TallyLogic.generateVerified(seed, d)!!
                assertEquals("$d/$seed", sizes[i], s.size)
                assertEquals("$d/$seed", i, TallyLogic.depth(s))
            }
        }
    }

    @Test
    fun `the last resort of every tier is a board that tier would accept, with one answer`() {
        for (d in Difficulty.entries) {
            val s = TallyLogic.lastResort(d)
            assertEquals(TallyLogic.specFor(d).side, s.size)
            assertTrue(d.name, TallyLogic.accepts(s, TallyLogic.specFor(d)))
            val (rows, cols) = targetsOf(s)
            assertEquals(listOf(s.keep), TallyOracle.answerStrings(s.size, digitsOf(s), rows, cols, cap = 2))
        }
    }

    @Test
    fun `generation is pure`() {
        for (d in Difficulty.entries) for (seed in seeds(5, d, "tally-pure")) {
            assertEquals(TallyLogic.generateVerified(seed, d), TallyLogic.generateVerified(seed, d))
        }
    }

    // ---- marks -----------------------------------------------------------------------------------------

    @Test
    fun `a tap toggles its pen's mark and replaces the other pen's`() {
        val s = TallyState(2, "1111", "1001")
        val removed = s.tap(0, TallyLogic.REMOVED)
        assertEquals("x...", removed.marks)
        assertEquals(1, removed.moves)
        assertEquals("o...", removed.tap(0, TallyLogic.KEPT).marks)
        assertEquals("....", removed.tap(0, TallyLogic.REMOVED).marks)
    }

    @Test
    fun `a sweep is one state, writes only over untouched squares, and an eraser takes only its own mark`() {
        val s = TallyState(4, "1".repeat(16), "1001".repeat(4), marks = ".o.." + "....".repeat(3))
        val swept = s.sweep(listOf(0, 1, 2, 3), TallyLogic.REMOVED, TallyLogic.UNMARKED)
        assertEquals("xoxx", swept.marks.take(4))
        assertEquals("three squares changed, three moves", 3, swept.moves)
        val erased = swept.sweep(listOf(0, 1, 2, 3), TallyLogic.UNMARKED, TallyLogic.REMOVED)
        assertEquals(".o..", erased.marks.take(4))
        assertSame("nothing to change is the same state", erased, erased.sweep(listOf(0), TallyLogic.UNMARKED, TallyLogic.REMOVED))
    }
}
