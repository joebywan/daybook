package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.BLACK_FILL
import com.joebywan.daybook.puzzles.BLACK_LINE
import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.WHITE_FILL
import com.joebywan.daybook.puzzles.WHITE_LINE
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mate's pieces read on both squares in both looks and schemes: a piece's fill or its outline parts it from the square at 3:1. */
class ChessSquaresTest {
    @Test fun `every piece reads on every square`() = forEachLook { look ->
        for ((name, scheme) in lookSchemes(look)) {
            val (light, dark) = Chess.squareColours(scheme)
            assertTrue("$name: the light square is the lighter", light != dark)
            for (square in listOf(light, dark)) {
                for ((piece, fill, line) in listOf(Triple("white", WHITE_FILL, WHITE_LINE), Triple("black", BLACK_FILL, BLACK_LINE))) {
                    val best = maxOf(contrast(fill, square), contrast(line, square))
                    assertTrue("$name: the $piece piece on ${hex(square)} is $best:1", best >= 3f)
                }
            }
        }
    }

    private fun hex(c: Color) = "%08X".format(c.value.toLong() ushr 32)
}
