package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.Cages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/** The dashed outlines must close up: every corner of a cage's outline is met by exactly two pieces. */
class CageOutlineTest {

    private fun key(x: Float, y: Float) = "${Math.round(x * 1000)},${Math.round(y * 1000)}"

    private fun assertClosed(cageOf: List<Int>, n: Int, label: String) {
        val outline = Cages.cageOutline(cageOf, n)
        for (k in cageOf.toSet()) {
            val ends = HashMap<String, Int>()
            for (g in outline.filter { it.cage == k }) {
                ends.merge(key(g.x0, g.y0), 1, Int::plus)
                ends.merge(key(g.x1, g.y1), 1, Int::plus)
            }
            val loose = ends.filter { it.value != 2 }
            assertTrue("$label cage $k has open ends at ${loose.keys}", loose.isEmpty())
        }
    }

    @Test
    fun `a lone square is a small box and a domino a long one`() {
        val inset = 0.09f
        val one = Cages.cageOutline(listOf(0), 1)
        assertEquals(4, one.size)
        assertEquals(4 * (1f - 2 * inset), one.sumOf { (abs(it.x1 - it.x0) + abs(it.y1 - it.y0)).toDouble() }.toFloat(), 1e-4f)
        val two = Cages.cageOutline(listOf(0, 0, 1, 1), 2).filter { it.cage == 0 }
        assertEquals(2 * (2f - 2 * inset) + 2 * (1f - 2 * inset), two.sumOf { (abs(it.x1 - it.x0) + abs(it.y1 - it.y0)).toDouble() }.toFloat(), 1e-4f)
    }

    @Test
    fun `an L, a notch and a ring of cages all close`() {
        // 3x3: an L (0,1,2 across the top and down the right), a notch cage beside it, and the rest.
        assertClosed(listOf(0, 0, 0, 1, 1, 0, 1, 1, 0), 3, "L")
        // A U shape around a square.
        assertClosed(listOf(0, 0, 0, 0, 1, 0, 0, 0, 0), 3, "ring")
        assertClosed(listOf(0, 1, 0, 0, 1, 0, 0, 0, 0), 3, "U")
    }

    @Test
    fun `every generated cage and the walkthrough's close`() {
        assertClosed(CageLogic.dominoes(), 9, "dominoes")
        for (d in Difficulty.entries) for (i in 0 until 30) {
            val s = CageLogic.generateVerified(DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(i.toLong()), Cages.id, d), d)!!
            assertClosed(s.cageOf, 9, "${d.name} day $i")
        }
    }
}
