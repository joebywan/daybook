package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.stepCursor
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class BoardKeysTest {
    @Test
    fun arrowsMapToSteps() {
        assertEquals(-1 to 0, arrowStep(Key.DirectionUp))
        assertEquals(0 to 1, arrowStep(Key.DirectionRight))
        assertNull(arrowStep(Key.A))
    }

    @Test
    fun cursorClampsAtEveryEdgeAndNeverWraps() {
        // 3 rows x 4 cols: cell 0 is top-left, 11 is bottom-right.
        assertEquals(0, stepCursor(0, -1, 0, 3, 4))
        assertEquals(0, stepCursor(0, 0, -1, 3, 4))
        assertEquals(3, stepCursor(3, 0, 1, 3, 4))
        assertEquals(11, stepCursor(11, 1, 0, 3, 4))
        assertEquals(4, stepCursor(0, 1, 0, 3, 4))
        assertEquals(6, stepCursor(5, 0, 1, 3, 4))
    }
}
