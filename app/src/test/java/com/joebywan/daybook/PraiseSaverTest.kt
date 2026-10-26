package com.joebywan.daybook

import androidx.compose.runtime.saveable.SaverScope
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.SavedGames
import com.joebywan.daybook.ui.play.EarnedAchievement
import com.joebywan.daybook.ui.play.Mark
import com.joebywan.daybook.ui.play.PraiseSaver
import com.joebywan.daybook.ui.play.Praise
import com.joebywan.daybook.ui.play.ShareCard
import com.joebywan.daybook.ui.play.ShareLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PraiseSaverTest {

    @Test
    fun `the finish frame survives a rotation whole`() {
        val card = ShareCard(
            "Daybook · Sudoku · Standard", listOf(ShareLine(listOf(Mark.GREEN, Mark.YELLOW), hinted = true, tail = " x")),
            "1:00", listOf(Mark.GREEN to "own move"), "nudge",
        )
        val praise = Praise(
            title = "7 day streak", big = true, lines = listOf("Fastest yet"),
            achievements = listOf(EarnedAchievement("a", "A", "did a")), toGo = "Only 2 to go",
            share = card, setShare = card, setShareLabel = "Share level", full = false, streak = 7,
        )
        val saved = with(PraiseSaver) { SaverScope { true }.save(praise) }!!
        assertEquals(praise, PraiseSaver.restore(saved))
    }

    @Test
    fun `every puzzle's random game on every tier fits the saved-game limit`() {
        assertTrue(SavedGames.KEEP >= PuzzleRegistry.all.size * Difficulty.entries.size)
    }
}
