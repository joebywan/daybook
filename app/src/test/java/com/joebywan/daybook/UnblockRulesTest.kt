package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Reached
import com.joebywan.daybook.core.Search
import com.joebywan.daybook.core.SearchVerdict
import com.joebywan.daybook.puzzles.Unblock
import com.joebywan.daybook.puzzles.UnblockCar
import com.joebywan.daybook.puzzles.UnblockLogic
import com.joebywan.daybook.puzzles.UnblockState
import com.joebywan.daybook.puzzles.UnblockTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnblockRulesTest {

    // Red car at the left of row 3; a car down column 5 across its way; a truck along the top.
    private val yard = listOf(
        UnblockCar(2, 0, 2, true), UnblockCar(1, 4, 2, false), UnblockCar(0, 3, 3, true),
        UnblockCar(4, 4, 2, false), UnblockCar(5, 0, 2, true),
    )

    // ---- the rules ----------------------------------------------------------------------------

    @Test
    fun `a car slides only along its length, over empty squares, inside the yard`() {
        val s = UnblockState(yard, 3)
        assertNull("into another car", s.slide(0, 4))
        assertNull("off the yard", s.slide(0, -1))
        assertNull("no slide at all", s.slide(0, 0))
        assertNull("blocked both ways", s.slide(3, 1))
        assertNull("through a car", s.slide(2, 1))
        assertEquals(UnblockCar(2, 2, 2, true), s.slide(0, 2)!!.cars[0])
        assertEquals(UnblockCar(0, 0, 3, true), s.slide(2, -3)!!.cars[2])
        assertNull("past the edge", s.slide(2, -4))
        // A down car moves rows, never columns.
        assertEquals(UnblockCar(2, 4, 2, false), s.slide(1, 1)!!.cars[1])
        assertEquals(1, s.slide(0, 2)!!.moves)
        // Every slide the rules list is one the state takes, and agrees with the oracle's picture.
        for (m in UnblockLogic.slides(yard)) {
            val next = s.slide(m.car, m.by)!!
            assertTrue(UnblockLogic.isLegal(next.cars))
            UnblockOracle.picture(next.cars)
        }
    }

    @Test
    fun `solved is the red car at the exit, whatever else stands where`() {
        assertFalse(UnblockState(yard, 3).solved)
        val out = listOf(UnblockCar(2, 4, 2, true), UnblockCar(0, 0, 3, false))
        assertTrue(UnblockState(out, 0).solved)
        assertFalse("one short of the exit", UnblockState(listOf(UnblockCar(2, 3, 2, true)), 0).solved)
        // A car at the right edge of another row is not out.
        assertFalse(UnblockLogic.isSolved(listOf(UnblockCar(1, 4, 2, true))))
    }

    @Test
    fun `the search's fewest moves agree with the oracle, and its road replays to solved by the rules`() {
        val v = UnblockLogic.solve(yard) as SearchVerdict.Solvable
        assertEquals(3, v.path.size)
        assertEquals(3, UnblockOracle.fewest(yard))
        var s = UnblockState(yard, 3)
        for (m in v.path) s = s.slide(m.car, m.by)!!
        assertTrue(s.solved)
    }

    @Test
    fun `a yard whose red car can never leave is Unsolvable, and a small budget is never read as that`() {
        // A truck lying across row 3 to the right of the red car can never move out of its row.
        val stuck = listOf(UnblockCar(2, 0, 2, true), UnblockCar(2, 3, 3, true))
        assertEquals(SearchVerdict.Unsolvable, UnblockLogic.solve(stuck))
        assertEquals(-1, UnblockOracle.fewest(stuck))
        val hard = UnblockLogic.lastResort(Difficulty.EXPERT)
        assertEquals(SearchVerdict.Truncated, UnblockLogic.solve(hard.cars, 5))
    }

    @Test
    fun `the shared search - shortest, unsolvable and truncated on a tiny graph`() {
        // Numbers, +1 or *2, from 1: the fewest steps to 10 is 1, 2, 4, 5, 10.
        val step: (Int, (String, Int) -> Unit) -> Unit = { n, emit -> if (n < 40) { emit("+1", n + 1); emit("*2", n * 2) } }
        val v = Search.shortest(1, 1000, { it }, step) { if (it == 10) Reached.GOAL else Reached.EXPAND }
        assertEquals(listOf("+1", "*2", "+1", "*2"), (v as SearchVerdict.Solvable).path)
        assertEquals(SearchVerdict.Unsolvable, Search.shortest(1, 1000, { it }, step) { if (it == 99) Reached.GOAL else Reached.EXPAND })
        assertEquals(SearchVerdict.Truncated, Search.shortest(1, 3, { it }, step) { if (it == 10) Reached.GOAL else Reached.EXPAND })
        // A dropped position is never searched past: with 2 barred (1's only children are 2), 10 is out of reach.
        assertEquals(SearchVerdict.Unsolvable, Search.shortest(1, 1000, { it }, step) {
            when (it) { 10 -> Reached.GOAL; 2 -> Reached.DROP; else -> Reached.EXPAND }
        })
    }

    @Test
    fun `the yard's space gives every position its fewest moves, as a fresh search from it finds`() {
        val s = UnblockLogic.space(yard)!!
        val y = s.yard
        for (i in s.positions.indices step 7) {
            val v = UnblockLogic.solve(y.cars(s.positions[i])) as SearchVerdict.Solvable
            assertEquals(s.distance[i], v.path.size)
        }
        assertNull("over the cap", UnblockLogic.space(yard, cap = 5))
        assertNull("no way out", UnblockLogic.space(listOf(UnblockCar(2, 0, 2, true), UnblockCar(2, 3, 3, true))))
    }

    // ---- generated boards -----------------------------------------------------------------------

    @Test
    fun `daily boards are legal, unsolved, and their par is the oracle's fewest moves, inside the tier`() {
        for (d in Difficulty.entries) for (day in 0 until 25) {
            val s = Unblock.generate(daySeed(day, Unblock.id, d), d) as UnblockState
            val spec = UnblockLogic.specFor(d)
            assertTrue(UnblockLogic.isLegal(s.cars))
            assertFalse(s.solved)
            assertTrue("${d.name} day $day: ${s.cars.size} cars", s.cars.size in spec.cars)
            assertTrue("${d.name} day $day par ${s.par}", s.par in spec.least..spec.most)
            assertEquals("${d.name} day $day", s.par, UnblockOracle.fewest(s.cars))
        }
    }

    @Test
    fun `every last resort is legal and its par is proved and in its tier`() {
        for (d in Difficulty.entries) {
            val s = UnblockLogic.lastResort(d)
            val spec = UnblockLogic.specFor(d)
            assertTrue(UnblockLogic.isLegal(s.cars))
            assertEquals(d.name, s.par, UnblockOracle.fewest(s.cars))
            assertTrue(d.name, s.par in spec.least..spec.most)
            assertTrue(d.name, s.cars.size in spec.cars)
        }
    }

    @Test
    fun `every car of a board has a name no other car shares`() {
        for (d in Difficulty.entries) for (day in 0 until 120) {
            val s = Unblock.generate(daySeed(day, Unblock.id, d), d) as UnblockState
            val names = s.cars.indices.map { UnblockTeacher.name(s.cars, it) }
            assertEquals("${d.name} day $day: $names", names.size, names.toSet().size)
        }
    }

    @Test
    fun `a year of daily boards - the tiers get harder in the order they are offered`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val pars = mutableListOf<Int>()
            val cars = mutableListOf<Int>()
            val ms = mutableListOf<Double>()
            val draws = mutableListOf<Int>()
            var failed = 0
            for (day in 0 until 365) {
                val seed = daySeed(day, Unblock.id, d)
                val t0 = System.nanoTime()
                val k = (0 until UnblockLogic.ATTEMPTS).firstOrNull { UnblockLogic.attempt(seed, d, it) != null }
                ms += (System.nanoTime() - t0) / 1e6
                if (k == null) { failed++; continue }
                draws += k + 1
                val s = UnblockLogic.attempt(seed, d, k)!!
                pars += s.par
                cars += s.cars.size
            }
            ms.sort()
            draws.sort()
            val mean = pars.average()
            report.append(
                "${d.name}: failed $failed; draws median ${draws[draws.size / 2]} max ${draws.last()}; " +
                    "par min ${pars.min()} mean ${"%.1f".format(mean)} max ${pars.max()}; cars mean ${"%.1f".format(cars.average())}; " +
                    "ms median ${"%.1f".format(ms[ms.size / 2])} p95 ${"%.1f".format(ms[ms.size * 95 / 100])} max ${"%.1f".format(ms.last())}\n",
            )
        }
        File("build/reports").mkdirs()
        File("build/reports/unblock-tiers.txt").writeText(report.toString())
        println(report)
        assertTrue(report.toString(), !report.contains(Regex("failed [1-9]")))
        assertTrue(report.toString(), report.lines().filter { it.isNotBlank() }.map { Regex("mean ([0-9.]+) max").find(it)!!.groupValues[1].toDouble() }.zipWithNext().all { (a, b) -> b > a })
    }
}
