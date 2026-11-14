package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.InequalityLogic
import com.joebywan.daybook.puzzles.ReckonLogic
import com.joebywan.daybook.puzzles.ReckonState
import com.joebywan.daybook.puzzles.ReckonTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReckonRulesTest {

    private fun board(day: Int, d: Difficulty): ReckonState =
        ReckonLogic.generateVerified(daySeed(day, "reckon", d), d)!!

    /** Independent of the code under test: every line a permutation of 1..n, every cage making its target. */
    private fun legal(s: ReckonState, cells: List<Int>): Boolean {
        val n = s.size
        if (cells.any { it !in 1..n }) return false
        for (k in 0 until n) {
            if (List(n) { cells[k * n + it] }.toSet() != (1..n).toSet()) return false
            if (List(n) { cells[it * n + k] }.toSet() != (1..n).toSet()) return false
        }
        return ReckonOracle.solutions(n, cells, s.cageOf, s.ops, s.targets, 1).isNotEmpty()
    }

    private fun cagesOf(s: ReckonState) = s.cageOf.indices.groupBy { s.cageOf[it] }.toSortedMap().values.toList()

    private fun connected(n: Int, sq: List<Int>): Boolean {
        val seen = mutableSetOf(sq[0])
        val stack = mutableListOf(sq[0])
        while (stack.isNotEmpty()) {
            val i = stack.removeAt(stack.lastIndex)
            for (j in sq) if (j !in seen && ((j / n == i / n && kotlin.math.abs(j - i) == 1) || kotlin.math.abs(j - i) == n)) { seen += j; stack += j }
        }
        return seen.size == sq.size
    }

    @Test
    fun `each rule refuses on its own, and the win check reads the rules`() {
        // A 2x2 with one + cage of all four squares has two answers: both win, whichever is stored.
        val s = ReckonState(2, listOf(0, 0, 0, 0), listOf(1, 2, 2, 1), listOf(0, 0, 0, 0), "+", listOf(6))
        assertTrue(s.copy(cells = listOf(1, 2, 2, 1)).solved)
        assertTrue(s.copy(cells = listOf(2, 1, 1, 2)).solved)
        val b = board(0, Difficulty.HARD)
        assertTrue(b.copy(cells = b.solution).solved)
        // A repeat in a line, with the cages still right where they can be: swap two digits of a row in one cage? Simpler:
        // any change of one square breaks a line, and the conflicts show it.
        val i = b.cells.indexOfFirst { it == 0 }
        val wrong = b.solution.toMutableList().also { it[i] = it[i] % 6 + 1 }
        assertFalse(b.copy(cells = wrong).solved)
        assertTrue(i in ReckonLogic.clashes(6, wrong))
        // A cage that misses its target, lines all fine: a Latin square that is not the answer.
        val shifted = List(36) { b.solution[(it / 6 + 1) % 6 * 6 + it % 6] }
        assertTrue(ReckonLogic.clashes(6, shifted).isEmpty())
        assertFalse(ReckonLogic.isSolved(6, shifted, b.cageOf, b.ops, b.targets))
        assertTrue(ReckonLogic.cageConflicts(6, shifted, b.cageOf, b.ops, b.targets).isNotEmpty())
        // Each operation, read the board's way.
        assertTrue(ReckonLogic.makes('-', 2, listOf(1, 3)) && ReckonLogic.makes('-', 2, listOf(3, 1)))
        assertTrue(ReckonLogic.makes('/', 3, listOf(6, 2)) && !ReckonLogic.makes('/', 2, listOf(5, 2)))
        assertTrue(ReckonLogic.makes('*', 12, listOf(2, 3, 2)) && ReckonLogic.makes('+', 7, listOf(3, 4)))
    }

    @Test
    fun `a cage repeats a digit only in squares that share no line`() {
        // An L of three: 8x as 2·2·2 needs the corner to repeat a neighbour, so only 1·2·4 arrangements remain.
        val l = listOf(0, 1, 6) // (0,0) (0,1) (1,0): the corner is 0
        val ways = ReckonLogic.ways(6, l, '*', 8, IntArray(3) { 0b1111110 })
        assertTrue(ways.isNotEmpty())
        assertTrue(ways.none { w -> w.all { it == 2 } })
        assertTrue(ways.all { it.sorted() == listOf(1, 2, 4) })
        // The two ends of the L share no line, so 2·2·1·... e.g. 4x as 2 (end), 1 (corner), 2 (end) is allowed.
        assertTrue(ReckonLogic.ways(6, l, '*', 4, IntArray(3) { 0b1111110 }).any { it.toList() == listOf(1, 2, 2) })
    }

    @Test
    fun `placing a digit strikes it from the notes of its row and column, and a given never changes`() {
        val b = board(1, Difficulty.STANDARD)
        val given = b.cells.indices.first { b.isGiven(it) }
        assertTrue(b.withCell(given, b.cells[given] % 6 + 1) === b)
        assertTrue(b.toggleNote(given, 1) === b)
        val open = b.cells.indices.filter { !b.isGiven(it) }
        val x = open[0]
        val rowMate = open.first { it != x && it / 6 == x / 6 }
        val other = open.first { it / 6 != x / 6 && it % 6 != x % 6 }
        val noted = b.toggleNote(rowMate, 4).toggleNote(other, 4).toggleNote(x, 2)
        val placed = noted.withCell(x, 4)
        assertFalse(placed.hasNote(rowMate, 4))
        assertTrue(placed.hasNote(other, 4))
        assertEquals(0, placed.notes[x])
        assertEquals(noted.moves + 1, placed.moves)
        assertEquals(0, placed.enter(x, 4, notesMode = false).cells[x])
    }

    @Test
    fun `the count agrees with the oracle, including boards with several answers or none`() {
        var several = 0
        var one = 0
        var none = 0
        for (seed in 1L..300L) {
            val rng = Rng(seed)
            val n = 4
            val sol = InequalityLogic.randomSquare(n, rng)
            val spec = ReckonLogic.Spec(3, 0..2, 0, -1)
            val cageOf = ReckonLogic.carve(n, spec, rng) ?: continue
            val (ops, targets) = ReckonLogic.operations(n, cageOf, sol, rng) ?: continue
            // Every third board is broken on purpose: one target nudged, so it may have no answer.
            val t = if (seed % 3 == 0L) targets.mapIndexed { k, v -> if (k == 0) v + 1 else v } else targets
            val cells = List(n * n) { if (ops[cageOf[it]] == '=') sol[it] else 0 }
            val oracle = ReckonOracle.solutions(n, cells, cageOf, ops, t, 2)
            when (val v = ReckonLogic.solutions(n, cells, cageOf, ops, t)) {
                is Verdict.ExactlyOne -> { assertEquals("$seed", listOf(v.value), oracle); one++ }
                Verdict.Several -> { assertEquals("$seed", 2, oracle.size); several++ }
                Verdict.None -> { assertEquals("$seed", 0, oracle.size); none++ }
            }
        }
        println("4x4 random boards: one $one, several $several, none $none")
        assertTrue(one > 10 && several > 10 && none > 10)
    }

    @Test
    fun `generated boards have one answer by the oracle, and it is the stored one`() {
        for ((d, days) in listOf(Difficulty.STANDARD to 30, Difficulty.HARD to 15, Difficulty.EXPERT to 8)) {
            for (day in 0 until days) {
                val s = board(day, d)
                assertEquals("$d day $day", listOf(s.solution), ReckonOracle.solutions(6, s.cells, s.cageOf, s.ops, s.targets, 2))
                assertTrue(legal(s, s.solution))
            }
        }
    }

    @Test
    fun `the last resort of every tier is a board that tier accepts`() {
        for (d in Difficulty.entries) {
            val s = ReckonLogic.lastResort(d)
            val spec = ReckonLogic.specFor(d)
            val depth = ReckonTeacher.depth(6, s.cells, s.cageOf, s.ops, s.targets, spec.deepest)
            assertNotNull(depth)
            assertTrue(depth!! >= spec.needs)
            assertEquals(listOf(s.solution), ReckonOracle.solutions(6, s.cells, s.cageOf, s.ops, s.targets, 2))
        }
    }

    @Test
    fun `the generator's quick walk agrees with the hints' walk`() {
        for (d in Difficulty.entries) for (day in 0 until 25) {
            val s = board(day, d)
            var cells = s.cells
            var most = -1
            while (cells.any { it == 0 }) {
                val step = ReckonTeacher.deduce(6, cells, s.cageOf, s.ops, s.targets)!!
                most = maxOf(most, ReckonTeacher.rung(step.technique))
                cells = cells.toMutableList().also { it[step.cell] = step.digit }
            }
            val quick = ReckonTeacher.depth(6, s.cells, s.cageOf, s.ops, s.targets)!!
            val cageRung = ReckonTeacher.rung(ReckonTeacher.CAGE_PLACE)
            assertEquals("$d day $day", maxOf(most, cageRung), maxOf(quick, cageRung))
        }
    }

    /**
     * A year of daily boards per tier: every one proved without the last resort, the tier's shape and depth held, and
     * the numbers written to `build/reports/reckon-generation.txt`.
     */
    @Test
    fun `a year of daily boards - proved, shaped and gated per tier, and the tiers get harder`() {
        val report = StringBuilder("tier      draws mean/median/max   JVM ms median/p95/max   cages  biggest  givens  ops + - x /\n")
        for (d in Difficulty.entries) {
            val spec = ReckonLogic.specFor(d)
            val draws = ArrayList<Int>()
            val ms = ArrayList<Double>()
            var cages = 0
            var biggest = 0
            var givens = 0
            val opCount = IntArray(4)
            for (day in 0 until 365) {
                val seed = daySeed(day, "reckon", d)
                val t0 = System.nanoTime()
                val s = ReckonLogic.generateVerified(seed, d)
                ms += (System.nanoTime() - t0) / 1e6
                assertNotNull("$d day $day fell back", s)
                s!!
                draws += (0 until ReckonLogic.ATTEMPTS).first { ReckonLogic.draw(seed + it, 6, spec) != null } + 1
                val depth = ReckonTeacher.depth(6, s.cells, s.cageOf, s.ops, s.targets, spec.deepest)
                assertTrue("$d day $day depth $depth", depth != null && depth >= spec.needs && depth <= spec.deepest)
                val cs = cagesOf(s)
                assertEquals(cs.size, s.ops.length)
                for ((k, sq) in cs.withIndex()) {
                    val op = s.ops[k]
                    assertTrue(connected(6, sq))
                    assertTrue(sq.size <= spec.cap)
                    assertTrue("label ${s.targets[k]}", s.targets[k] in 1..999)
                    when (op) {
                        '*' -> assertTrue(sq.size in 2..4)
                        '-', '/' -> assertEquals(2, sq.size)
                        '+' -> assertTrue(sq.size >= 2)
                        '=' -> assertEquals(1, sq.size)
                    }
                    assertTrue(ReckonOracle.solutions(6, s.solution, s.cageOf, s.ops, s.targets, 1).isNotEmpty())
                }
                assertTrue("$d day $day: every operation", "+-*/".all { it in s.ops })
                val g = s.ops.count { it == '=' }
                assertTrue("$d day $day givens $g", g in spec.givens)
                assertTrue(s.cells.indices.all { (s.cells[it] != 0) == s.isGiven(it) })
                cages += cs.size
                biggest += cs.maxOf { it.size }
                givens += g
                for ((i, c) in "+-*/".withIndex()) opCount[i] += s.ops.count { it == c }
            }
            draws.sort(); ms.sort()
            report.append(
                "%-9s %5.1f / %d / %d          %5.1f / %5.1f / %5.1f     %4.1f   %4.1f   %4.1f    %s\n".format(
                    d.name, draws.average(), draws[182], draws.last(), ms[182], ms[346], ms.last(),
                    cages / 365.0, biggest / 365.0, givens / 365.0, opCount.joinToString(" ") { "%.1f".format(it / 365.0) },
                ),
            )
        }
        println(report)
        writeReport("reckon-generation.txt", report.toString())
    }
}
