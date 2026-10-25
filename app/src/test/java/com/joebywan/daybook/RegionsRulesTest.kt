package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.RegionsLayout
import com.joebywan.daybook.puzzles.RegionsLogic
import com.joebywan.daybook.puzzles.RegionsState
import com.joebywan.daybook.puzzles.RegionsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegionsRulesTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun seed(day: Int, d: Difficulty) = DailySeed.seedFor(start.plusDays(day.toLong()), Regions.id, d)
    private fun board(day: Int, d: Difficulty) = Regions.generate(seed(day, d), d) as RegionsState

    /** Independent of the code under test: sizes, region digits and touching read straight off the grid. */
    private fun legal(s: RegionsState, cells: List<Int>): Boolean {
        val n = s.size
        for (i in 0 until n * n) {
            val size = s.region.count { it == s.region[i] }
            if (cells[i] !in 1..size) return false
            for (j in i + 1 until n * n) {
                if (cells[i] != cells[j]) continue
                if (s.region[i] == s.region[j]) return false
                if (kotlin.math.abs(i / n - j / n) <= 1 && kotlin.math.abs(i % n - j % n) <= 1) return false
            }
        }
        return true
    }

    /** Whether every region is one edge-connected piece, by flood fill on the raw grid. */
    private fun connected(s: RegionsState): Boolean {
        val n = s.size
        for (r in s.region.toSet()) {
            val cells = s.region.indices.filter { s.region[it] == r }
            val seen = mutableSetOf(cells.first())
            val queue = ArrayDeque(listOf(cells.first()))
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                for (o in listOf(c - n, c + n, if (c % n > 0) c - 1 else -1, if (c % n < n - 1) c + 1 else -1)) {
                    if (o in cells && seen.add(o)) queue.add(o)
                }
            }
            if (seen.size != cells.size) return false
        }
        return true
    }

    @Test
    fun `the tiers get larger in the order they are offered`() {
        assertEquals(listOf(5, 7, 9), Difficulty.entries.map { board(0, it).size })
    }

    @Test
    fun `an independent solver finds exactly one answer, and it is the stored one`() {
        // Naive search is slow on big grids, so the sample thins as the boards grow.
        for ((d, days) in listOf(Difficulty.STANDARD to 40, Difficulty.HARD to 12, Difficulty.EXPERT to 4)) {
            for (day in 0 until days) {
                val s = board(day, d)
                assertEquals("${d.name} day $day", 1L, RegionsOracle.count(s.size, s.region, s.cells))
                assertTrue(legal(s, s.solution))
                assertTrue(connected(s))
                assertTrue(s.region.toSet().all { r -> s.region.count { it == r } in 1..RegionsLogic.MAX_REGION })
                assertTrue(s.cells.indices.all { !s.givens[it] || s.cells[it] == s.solution[it] })
                assertFalse(s.solved)
            }
        }
    }

    @Test
    fun `the last resort is proved on every tier`() {
        for (d in Difficulty.entries) {
            val s = RegionsLogic.lastResort(d)
            assertFalse(s.solved)
            assertEquals(1L, RegionsOracle.count(s.size, s.region, s.cells))
            assertTrue(legal(s, s.solution))
        }
    }

    @Test
    fun `every board is irredundant and gives away a bounded share`() {
        println("tier      givens/squares  singles/regions  (mean over 100 daily boards)")
        val ceiling = mapOf(Difficulty.STANDARD to 0.30, Difficulty.HARD to 0.26, Difficulty.EXPERT to 0.22)
        for (d in Difficulty.entries) {
            var givens = 0.0
            var squares = 0
            var singles = 0
            var regions = 0
            for (day in 0 until 100) {
                val s = board(day, d)
                val n = s.size
                squares = n * n
                givens += s.givens.count { it }
                val layout = RegionsLayout(n, s.region)
                singles += layout.members.count { it.size == 1 }
                regions += layout.count
                // Irredundant: take back any one given and the tier's ladder no longer finishes it.
                for (i in 0 until n * n) if (s.givens[i]) {
                    val fewer = s.cells.toMutableList().also { it[i] = 0 }
                    assertFalse("${d.name} day $day given $i is redundant", RegionsTeacher.solvesByLogic(layout, fewer, RegionsLogic.specFor(d).deepest))
                }
            }
            println("%-9s %5.1f/%d        %d/%d".format(d.name, givens / 100, squares, singles, regions))
            assertTrue("${d.name} gives away too many squares: ${givens / 100}", givens / 100 <= squares * ceiling.getValue(d))
        }
    }

    @Test
    fun `most boards need no redraw, and none fails over a year`() {
        println("tier      boards needing a second attempt / 365")
        for (d in Difficulty.entries) {
            var redraw = 0
            for (day in 0 until 365) {
                if (RegionsLogic.generateVerified(seed(day, d), d, attempts = 1) == null) redraw++
            }
            println("%-9s %d".format(d.name, redraw))
            // Expert's gate (six locked candidates) redraws a fill on about 14% of days (51 of 2026's), Hard's on 1%.
            assertTrue("${d.name} redraws on $redraw of 365 days", redraw <= 365 / 5)
        }
    }

    @Test
    fun `the generator's quick walk agrees with the hints' walk`() {
        // lockedSteps places every single of a pass at once; it must stop where the hints do and take as many locked steps.
        // Checked on shipped boards and on the boards one given short of them, which mostly stick.
        val locked = RegionsTeacher.rung(RegionsTeacher.LOCKED)
        for ((d, days) in listOf(Difficulty.STANDARD to 30, Difficulty.HARD to 15, Difficulty.EXPERT to 6)) for (day in 0 until days) {
            val s = board(day, d)
            val layout = RegionsLayout(s.size, s.region)
            val boards = listOf(s.cells) + s.cells.indices.filter { s.givens[it] }.map { i -> s.cells.toMutableList().also { it[i] = 0 } }
            for (cells in boards) for (deepest in listOf(RegionsTeacher.rung(RegionsTeacher.ONLY_PLACE), locked)) {
                // The hints' walk, a step at a time (simplest first, so within the ladder whenever that finishes), counting locked steps.
                val finished = RegionsTeacher.depth(layout, cells, deepest) != null
                var now = cells
                var steps = 0
                while (finished && now.any { it == 0 }) {
                    val step = RegionsTeacher.deduce(layout, now)!!
                    if (step.technique == RegionsTeacher.LOCKED) steps++
                    now = now.toMutableList().also { it[step.cell] = step.digit }
                }
                assertEquals("${d.name} day $day", if (finished) steps else null, RegionsTeacher.lockedSteps(layout, cells, deepest))
            }
        }
    }

    @Test
    fun `each rule refuses on its own`() {
        // A 4x4 board of four quadrants (the walkthrough board): squares 0, 1, 4, 5 are one region.
        val s = Regions.tutorialBoard()
        fun conflicts(vararg entries: Pair<Int, Int>) =
            entries.fold(s) { acc, (i, d) -> acc.copy(cells = acc.cells.toMutableList().also { it[i] = d }) }.conflicts()
        // A repeat in a region only: squares 0 and 5 are diagonal neighbours, so use 0 and 1? they touch too.
        // The quadrant has four squares, all mutually touching, so a region repeat is also a touching one.
        assertEquals(setOf(0, 1), conflicts(0 to 1, 1 to 1))
        // A touching pair in different regions only: square 1 (region A) and square 2 (region B, given 3) side by side.
        assertEquals(setOf(1, 2), conflicts(1 to 3))
        // A corner touch across regions: square 5 (A) with the given 3s at square 2 (B, up and right) and square 10 (D, down and right).
        assertEquals(setOf(2, 5, 10), conflicts(5 to 3))
        // Equal digits two squares apart do not touch: square 0 and square 2 both 3.
        assertTrue(conflicts(0 to 3).isEmpty())
        // A digit above the region's size: no 5 in a region of four.
        assertEquals(setOf(0), conflicts(0 to 5))
        // The board: a given cannot be changed, and entering the same digit changes nothing.
        assertEquals(s, s.withCell(2, 1))
        assertEquals(s.withCell(0, 1), s.withCell(0, 1).withCell(0, 1))
        assertEquals(1, s.withCell(0, 1).moves)
    }

    @Test
    fun `the win check reads the rules, and near misses lose`() {
        val s = board(3, Difficulty.EXPERT)
        assertTrue(s.copy(cells = s.solution).solved)
        // Swap two digits that sit in different rows: still a full grid, but a region or a touch breaks.
        val swapped = s.solution.toMutableList().also { val t = it[0]; it[0] = it[s.size + 1]; it[s.size + 1] = t }
        assertEquals(legal(s, swapped), s.copy(cells = swapped).solved)
        assertFalse(s.copy(cells = s.solution.toMutableList().also { it[s.size * s.size - 1] = 0 }).solved)
        // Another legal answer to the walkthrough's regions wins even though it is not the stored one.
        val t = Regions.tutorialBoard()
        val other = listOf(1, 3, 1, 3, 4, 2, 4, 2, 1, 3, 1, 3, 2, 4, 2, 4)
        assertTrue(legal(t, other))
        assertTrue(t.copy(cells = other).solved)
        assertFalse(t.copy(cells = other.toMutableList().also { it[0] = 2 }).solved)
    }

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val s = Regions.tutorialBoard()
        assertEquals(1L, RegionsOracle.count(4, s.region, s.cells))
        assertTrue(legal(s, s.solution))
        assertTrue(connected(s))
    }

    @Test
    fun `no two regions that share an edge are drawn in one colour`() {
        for (d in Difficulty.entries) for (day in 0 until 120) {
            val s = board(day, d)
            val colours = Regions.regionColours(s.layout)
            val n = s.size
            for (i in 0 until n * n) {
                for (j in listOf(if (i % n < n - 1) i + 1 else -1, if (i / n < n - 1) i + n else -1)) {
                    if (j >= 0 && s.region[j] != s.region[i]) {
                        assertTrue("${d.name} day $day", colours[s.region[i]] != colours[s.region[j]])
                    }
                }
            }
            assertTrue(colours.all { it in 0..4 })
        }
    }
}
