package com.joebywan.daybook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.joebywan.daybook.ui.dragCountAt
import com.joebywan.daybook.ui.headingDown
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The drag count (Nonogram's sweep, Shikaku's rectangle; ui/DragAim.kt) is never under the finger: wherever the finger is on the board, the pill stays
 * at least the clearance from its centre and never below it unless off to one side (the hand comes from below).
 */
class DragCountTest {
    @Test
    fun `the count is never under the finger or the hand`() {
        val random = Random(7)
        val pill = Size(44f, 30f)
        val clear = 40f
        repeat(5_000) {
            val box = Size(200f + random.nextFloat() * 800f, 200f + random.nextFloat() * 800f)
            val finger = Offset(random.nextFloat() * box.width, random.nextFloat() * box.height)
            val down = random.nextBoolean()
            val rect = Rect(dragCountAt(finger, pill, box, clear, down), pill)
            val dx = maxOf(rect.left - finger.x, 0f, finger.x - rect.right)
            val dy = maxOf(rect.top - finger.y, 0f, finger.y - rect.bottom)
            assertTrue("$finger in $box: $rect", dx * dx + dy * dy >= clear * clear * 0.99f)
            // Lower than the finger only off to one side (at the very top), never over the hand below it.
            assertTrue("$finger in $box: $rect below the finger", rect.bottom <= finger.y + 0.01f || dx >= clear * 0.99f)
            assertTrue("$finger in $box: $rect off the top", rect.top >= 0f)
            // A drag heading down or up's pill keeps off the column, so it never hides the squares ahead.
            if (down) assertTrue("$finger in $box: $rect over the column", dx >= clear * 0.99f)
        }
    }

    @Test
    fun `a drag heads down only when it moves more rows than columns`() {
        val cols = 7
        for (start in 0 until cols * 6) for (at in 0 until cols * 6) {
            val dr = kotlin.math.abs(at / cols - start / cols)
            val dc = kotlin.math.abs(at % cols - start % cols)
            assertTrue("$start to $at", headingDown(start, at, cols) == (dr > dc))
        }
        // A row sweep, a tie (Nonogram's sweep locks a tie to the row) and a tap are not heading down.
        assertFalse(headingDown(0, 6, cols))
        assertFalse(headingDown(0, 8, cols))
        assertFalse(headingDown(3, 3, cols))
        assertTrue(headingDown(3, 3 + 2 * cols, cols))
    }
}
