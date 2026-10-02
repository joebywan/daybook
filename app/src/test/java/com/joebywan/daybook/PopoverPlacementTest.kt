package com.joebywan.daybook

import androidx.compose.ui.geometry.Rect
import com.joebywan.daybook.ui.teach.PopoverSpot
import com.joebywan.daybook.ui.teach.placePopover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the hint popover goes, as a pure function of the screen's geometry. The geometries are
 * Sudoku's, measured on the web build at each size (dp, one pixel per dp): a 9x9 board of square cells
 * under the header, the 48dp digit pad below it, the toolbar under that. The pad is what a player
 * reaches for to make the move a hint describes, so no highlight may put the popover on it.
 */
class PopoverPlacementTest {
    private class Screen(val h: Float, val boardTop: Float, val cell: Float, val boardLeft: Float, val padTop: Float, val toolbarTop: Float) {
        val pad get() = Rect(12f, padTop, 348f, padTop + 48f)
        fun cells(vararg i: Int): Rect {
            var out: Rect? = null
            for (k in i) {
                val r = Rect(boardLeft + (k % 9) * cell, boardTop + (k / 9) * cell, boardLeft + (k % 9 + 1) * cell, boardTop + (k / 9 + 1) * cell)
                out = out?.let { Rect(minOf(it.left, r.left), minOf(it.top, r.top), maxOf(it.right, r.right), maxOf(it.bottom, r.bottom)) } ?: r
            }
            return out!!.let { Rect(it.left - 6, it.top - 6, it.right + 6, it.bottom + 6) }
        }
    }

    // 360x640, 390x664, 390x844 and 375x537 (the last with the toolbar squeezed up against the pad).
    private val screens = listOf(
        Screen(640f, 106f, 37.3f, 12f, 460f, 551f),
        Screen(664f, 100f, 41f, 10.5f, 487f, 575f),
        Screen(844f, 191f, 40.8f, 11.5f, 577f, 657f),
        Screen(537f, 87f, 33f, 39f, 402f, 460f),
    )

    private fun place(s: Screen, highlight: Rect?, natural: Float) = placePopover(
        natural = natural, compact = 104f, highlight = highlight, keepClear = listOf(s.pad),
        minTop = 9f, maxBottom = s.toolbarTop - 8f, boardTop = s.boardTop, gap = 8f, windowHeight = s.h,
    )

    private fun covers(spot: PopoverSpot, r: Rect) = spot.y < r.bottom && spot.y + spot.height > r.top

    @Test
    fun topLeftBoxDoesNotPutThePopoverOnThePad() {
        // The reported case: the hint is in the top-left box, the roomy side (below) is the pad.
        for (s in screens) for (natural in listOf(70f, 118f, 150f, 200f)) {
            val spot = place(s, s.cells(0, 1, 2, 9, 10, 11, 18, 19, 20), natural)
            assertFalse("pad covered at ${s.h}, natural $natural: $spot", covers(spot, s.pad))
        }
    }

    @Test
    fun noHighlightPutsThePopoverOnThePad() {
        // Every pair of cells' bounding box, i.e. every highlight a teacher can report on the grid,
        // plus single cells, at every screen and a short and a long explanation.
        for (s in screens) for (natural in listOf(60f, 118f, 190f)) {
            for (a in 0 until 81) for (b in a until 81 step 4) {
                val spot = place(s, s.cells(a, b), natural)
                assertFalse("pad covered at ${s.h}, cells $a/$b, natural $natural: $spot", covers(spot, s.pad))
                assertTrue("popover above the status bar: $spot", spot.y >= 9f)
                assertTrue("popover over the toolbar: $spot", spot.y + spot.height <= s.toolbarTop - 8f + 0.01f)
            }
        }
    }

    @Test
    fun whenTheHighlightIsLowThePopoverStillGoesAbove() {
        for (s in screens) {
            val spot = place(s, s.cells(72, 73, 74, 63, 64, 65), 118f)
            assertEquals("hugs the top of the board area", s.boardTop + 8f, spot.y, 0.01f)
            assertFalse(spot.limited)
        }
    }

    @Test
    fun withNothingToKeepClearBelowIsUnchanged() {
        val s = screens[0]
        val spot = placePopover(
            natural = 118f, compact = 104f, highlight = s.cells(0, 1, 2), keepClear = emptyList(),
            minTop = 9f, maxBottom = s.toolbarTop - 8f, boardTop = s.boardTop, gap = 8f, windowHeight = s.h,
        )
        assertEquals(PopoverSpot(s.toolbarTop - 8f - 118f, 118f, false), spot)
    }

    @Test
    fun anUnknownHighlightMeansBelowButStillClearOfThePad() {
        for (s in screens) {
            val spot = place(s, null, 118f)
            assertFalse("pad covered at ${s.h}: $spot", covers(spot, s.pad))
        }
    }
}
