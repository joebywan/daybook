package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The display-only colouring of Kings regions: a real assignment, and a stable one. */
class KingsPaletteTest {

    private fun boards() = (0 until 60).flatMap { d ->
        Difficulty.entries.map { tier ->
            val seed = daySeed(d, Kings.id, tier)
            Kings.generate(seed, tier) as KingsState
        }
    }

    @Test
    fun `every region gets its own colour, and the same one every time`() {
        for (s in boards()) {
            val colours = Kings.regionPalette(s.size, s.region)
            assertEquals(s.size, colours.size)
            assertEquals("two regions share a colour", s.size, colours.toSet().size)
            assertTrue(colours.all { it in 0 until 9 })
            assertArrayEquals(colours, Kings.regionPalette(s.size, s.region.toList()))
        }
    }

    @Test
    fun `a board whose regions never touch keeps the plain order`() {
        // One region: no touching pairs, so nothing to improve and nothing should move.
        assertArrayEquals(intArrayOf(0), Kings.regionPalette(1, listOf(0)))
    }
}
