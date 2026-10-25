package com.joebywan.daybook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.puzzles.GridView
import com.joebywan.daybook.puzzles.MAX_ZOOM
import com.joebywan.daybook.puzzles.highlightSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Nonogram's zoom view, checked by what a player would see rather than by its formulas: the grid
 * always covers its box (nothing past an edge shows), the point under the fingers stays under them,
 * and a hint's squares end up on screen.
 */
class NonogramZoomTest {

    private val box = Size(300f, 300f)

    /** The grid, as drawn, still covers the whole box. */
    private fun assertCovers(v: GridView) {
        assertTrue("left edge shows: $v", v.offset.x <= 0.01f)
        assertTrue("top edge shows: $v", v.offset.y <= 0.01f)
        assertTrue("right edge shows: $v", v.offset.x + box.width * v.scale >= box.width - 0.01f)
        assertTrue("bottom edge shows: $v", v.offset.y + box.height * v.scale >= box.height - 0.01f)
    }

    /** Where grid point [p] (unzoomed pixels) is drawn. */
    private fun GridView.drawn(p: Offset) = p * scale + offset

    @Test
    fun `random pinches and pans never show past an edge or zoom beyond its limits`() {
        val rng = Random(7)
        var v = GridView()
        repeat(5000) {
            v = if (rng.nextBoolean()) {
                v.zoomedBy(0.5f + rng.nextFloat() * 1.5f, Offset(rng.nextFloat() * 300, rng.nextFloat() * 300), box)
            } else {
                v.pannedBy(Offset(rng.nextFloat() * 400 - 200, rng.nextFloat() * 400 - 200), box)
            }
            assertTrue(v.scale in 1f..MAX_ZOOM)
            assertCovers(v)
        }
    }

    @Test
    fun `the square under the fingers stays under them while zooming in the middle`() {
        val focus = Offset(150f, 120f)
        val v = GridView().zoomedBy(2f, focus, box)
        // Grid point under the focus before the pinch was the focus itself (scale 1, no offset).
        assertEquals(focus.x, v.drawn(focus).x, 0.01f)
        assertEquals(focus.y, v.drawn(focus).y, 0.01f)
    }

    @Test
    fun `pinching out all the way returns to the whole board`() {
        val v = GridView().zoomedBy(2.5f, Offset(250f, 40f), box).zoomedBy(0.1f, Offset(10f, 10f), box)
        assertEquals(GridView(), v)
        assertFalse(v.zoomed)
    }

    @Test
    fun `a hint's squares are brought into view, and a view already showing them stays put`() {
        val rng = Random(11)
        val u = 20f // 15 squares of 20px fill the 300px box
        repeat(2000) {
            val start = GridView().zoomedBy(1f + rng.nextFloat() * 1.5f, Offset(rng.nextFloat() * 300, rng.nextFloat() * 300), box)
            val c0 = rng.nextInt(15)
            val c1 = (c0 + rng.nextInt(4)).coerceAtMost(14)
            val r0 = rng.nextInt(15)
            val xs = c0 * u..(c1 + 1) * u
            val ys = r0 * u..(r0 + 1) * u
            val v = start.showing(xs, ys, box)
            assertCovers(v)
            val topLeft = v.drawn(Offset(xs.start, ys.start))
            val bottomRight = v.drawn(Offset(xs.endInclusive, ys.endInclusive))
            assertTrue("$start -> $v hides $xs x $ys", topLeft.x >= -0.01f && topLeft.y >= -0.01f)
            assertTrue("$start -> $v hides $xs x $ys", bottomRight.x <= 300.01f && bottomRight.y <= 300.01f)
            val already = start.drawn(Offset(xs.start, ys.start)).let { it.x >= 0 && it.y >= 0 } &&
                start.drawn(Offset(xs.endInclusive, ys.endInclusive)).let { it.x <= 300 && it.y <= 300 }
            if (already) assertEquals(start, v)
        }
    }

    @Test
    fun `a whole row needs only its height in view, a single square both axes, and a clue its line`() {
        val u = 10f
        val row = BoardHighlight(strong = (30 until 45).toSet()) // row 2 of a 15x15
        assertEquals(null to (20f..30f), highlightSpans(row, 15, 15, u))
        val square = BoardHighlight(strong = setOf(16)) // row 1, column 1
        assertEquals((10f..20f) to (10f..20f), highlightSpans(square, 15, 15, u))
        val columnClue = BoardHighlight(strong = setOf(225 + 15 + 4)) // column 4's clue
        assertEquals((40f..50f) to null, highlightSpans(columnClue, 15, 15, u))
        assertNull(highlightSpans(BoardHighlight.None, 15, 15, u))
    }
}
