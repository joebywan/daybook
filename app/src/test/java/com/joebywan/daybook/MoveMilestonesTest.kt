package com.joebywan.daybook

import com.joebywan.daybook.puzzles.CipherLogic
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY
import com.joebywan.daybook.puzzles.CipherSum
import com.joebywan.daybook.puzzles.LexiconMark
import com.joebywan.daybook.puzzles.TargetLogic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small milestones that glow and play a note mid-game, each worked out here by hand, not by the code's own rule. */
class MoveMilestonesTest {

    // SEND + MORE = MONEY, 9567 + 1085 = 10652. Letters sorted: d e m n o r s y.
    private val sum = CipherSum(listOf("send", "more", "money"))

    private fun digits(vararg placed: Pair<Char, Int>): List<Int> =
        sum.letters.map { l -> placed.firstOrNull { it.first == l }?.second ?: EMPTY }

    private fun addsUp(c: Int, vararg placed: Pair<Char, Int>) = CipherLogic.columnAddsUp(sum, digits(*placed), c)

    @Test
    fun `a cipher column glows once its own sum works`() {
        val all = arrayOf('s' to 9, 'e' to 5, 'n' to 6, 'd' to 7, 'm' to 1, 'o' to 0, 'r' to 8, 'y' to 2)
        for (c in 0 until 5) assertTrue("column $c", addsUp(c, *all))
        assertFalse(addsUp(0))
        // Units: 7 + 5 = 12.
        assertTrue(addsUp(0, 'd' to 7, 'e' to 5, 'y' to 2))
        assertFalse(addsUp(0, 'd' to 7, 'e' to 5, 'y' to 3))
        // Two letters on one digit never count, even when the sum works: 5 + 5 = 10.
        assertFalse(addsUp(0, 'd' to 5, 'e' to 5, 'y' to 0))
        // Tens alone, units open: 6 + 8 is 14, or 15 with a carry, so E = 5 can work.
        assertTrue(addsUp(1, 'n' to 6, 'r' to 8, 'e' to 5))
        // Once the units are placed the carry is known: 2 + 5 = 7 passes nothing on, and 6 + 8 is not 5.
        assertFalse(addsUp(1, 'd' to 2, 'e' to 5, 'y' to 7, 'n' to 6, 'r' to 8))
        // The top column is only a carry: M is 1, never 0 (a leading zero) or 2.
        assertTrue(addsUp(4, 'm' to 1))
        assertFalse(addsUp(4, 'm' to 0))
        assertFalse(addsUp(4, 'm' to 2))
    }

    @Test
    fun `a target band holds the tiles that near, never the target itself`() {
        val slots = listOf(100, 3, 0, 117, 0, 120)
        // 120 is the target (the solve), 117 within 4% (4.8), 100 within a quarter (30), 3 nowhere near.
        assertArrayEquals(intArrayOf(0, 3), TargetLogic.within(slots, 120, 0.5))
        assertArrayEquals(intArrayOf(0, 3), TargetLogic.within(slots, 120, 0.25))
        assertArrayEquals(intArrayOf(3), TargetLogic.within(slots, 120, 0.1))
        assertArrayEquals(intArrayOf(3), TargetLogic.within(slots, 120, 0.04))
        assertArrayEquals(intArrayOf(), TargetLogic.within(listOf(0, 0, 0, 0, 0, 0), 120, 0.5))
    }

    @Test
    fun `a marked row sounds higher the more it landed`() {
        // Five letters: in place counts 2, elsewhere 1, out of 10, onto the phrase's 0..7.
        assertEquals(0, LexiconMark.step(0, 5))
        assertEquals(3, LexiconMark.step(5, 5))
        assertEquals(7, LexiconMark.step(10, 5))
        // Codebreaker's four pegs: two exact and one misplaced is 5 of 8.
        assertEquals(4, LexiconMark.step(2 * 2 + 1, 4))
        assertTrue((0..8).map { LexiconMark.step(it, 4) }.zipWithNext().all { (a, b) -> a <= b })
    }
}
