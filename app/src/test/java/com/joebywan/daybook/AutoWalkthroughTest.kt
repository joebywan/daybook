package com.joebywan.daybook

import com.joebywan.daybook.ui.tutorial.autoWalkthrough
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWalkthroughTest {
    @Test
    fun `only a fresh board of a puzzle never solved and never seen starts the walkthrough`() {
        assertTrue(autoWalkthrough(played = false, seen = false, level = false, savedMoves = 0))
    }

    @Test
    fun `each reason on its own keeps it from starting`() {
        assertFalse("solved or saved before", autoWalkthrough(played = true, seen = false, level = false, savedMoves = 0))
        assertFalse("opened or skipped before", autoWalkthrough(played = false, seen = true, level = false, savedMoves = 0))
        assertFalse("a level board", autoWalkthrough(played = false, seen = false, level = true, savedMoves = 0))
        assertFalse("a game under way", autoWalkthrough(played = false, seen = false, level = false, savedMoves = 1))
    }
}
