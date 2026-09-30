package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The display-only colouring of Kings regions: a real assignment, and a stable one. */
class KingsPaletteTest {

    private fun boards() = (0L until 60L).flatMap { d ->
        Difficulty.entries.map { tier ->
            val seed = DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(d), Kings.id, tier)
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
