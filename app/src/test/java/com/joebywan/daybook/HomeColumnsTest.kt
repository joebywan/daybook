package com.joebywan.daybook

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.ui.home.homeColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeColumnsTest {
    /** The grid is the window less 36dp of side padding; the height is what is left under the header. */
    private fun columns(width: Int, height: Int) = homeColumns((width - 36).dp, height.dp)

    private fun tile(width: Int, columns: Int): Dp = ((width - 36).dp - 8.dp * (columns - 1)) / columns

    @Test
    fun `a phone in portrait and a tablet keep the width rule`() {
        assertEquals(3, columns(390, 600))
        assertEquals(3, columns(375, 300))
        assertEquals(3, columns(360, 400))
        assertEquals(5, columns(800, 1000))
        assertEquals(7, columns(1280, 560))
        assertEquals(7, columns(2560, 1300))
    }

    @Test
    fun `a phone on its side takes more columns, never a tile under 110dp`() {
        for ((w, h) in listOf(844 to 170, 640 to 150, 914 to 190)) {
            val c = columns(w, h)
            assertTrue("$w x $h: $c", c > ((w - 36).dp / 150.dp).toInt().coerceIn(3, 7))
            assertTrue("$w x $h: ${tile(w, c)}", tile(w, c) >= 110.dp)
        }
    }
}
