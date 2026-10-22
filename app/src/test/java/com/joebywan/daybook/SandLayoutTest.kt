package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandLogic
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sand's tube sizes. The boxes are the board's own constraints as measured in the web build (Chromium, `?puzzle=sand`):
 * at 375x537 the board gets 351 x 343 dp, at 390x844 366 x 626, less the 30 dp caption line. The owner's baseline for
 * "fiddly" is Nonogram Expert's square on a phone: 16.6 dp at 375x537 and 19.2 dp at 390x844 (measured on the rendered
 * 2026-06-15 board, 6 x 5 clue slots).
 */
class SandLayoutTest {

    private val caption = 30f
    private val nonogramExpertSquare = 16.6f

    @Test
    fun `every tier's tap column is at least 48dp and twice a Nonogram Expert square on the short phone`() {
        for (d in Difficulty.entries) {
            val spec = SandLogic.specFor(d)
            val fit = Sand.layout(spec.colours + 2, spec.height, 351f, 343f - caption)
            println("${d.name} 375x537: ${fit.rows} rows, tube ${fit.tube} dp, tap column ${fit.column} dp")
            assertTrue("${d.name}: column ${fit.column}", fit.column >= 48f)
            assertTrue("${d.name}: column ${fit.column}", fit.column >= 2 * nonogramExpertSquare)
            // The board fits its box.
            assertTrue(fit.column * fit.perRow <= 351f + 0.01f)
        }
    }

    @Test
    fun `the row count is whichever gives the widest tube`() {
        for (d in Difficulty.entries) for ((w, h) in listOf(351f to 313f, 366f to 596f, 896f to 746f, 600f to 300f, 300f to 900f)) {
            val spec = SandLogic.specFor(d)
            val fit = Sand.layout(spec.colours + 2, spec.height, w, h)
            // A brute force over every row count from 1 to the number of tubes: none gives a wider tube by more than a hair,
            // unless it is past the three rows the board allows.
            for (rows in 1..3) {
                val perRow = (spec.colours + 2 + rows - 1) / rows
                val down = rows * (0.45f + 0.2f + spec.height * 0.72f + 0.32f) + (rows - 1) * 0.3f
                val tube = minOf(w / (perRow * 1.45f), h / down, 72f)
                assertTrue("${d.name} ${w}x$h: $rows rows give $tube, chosen ${fit.tube}", tube <= fit.tube + 0.02f)
            }
            assertTrue("every tube has a place", fit.rows * fit.perRow >= spec.colours + 2 && (fit.rows - 1) * fit.perRow < spec.colours + 2)
            assertTrue("a column holds its tube and gap", fit.column >= fit.tube * 1.45f - 0.01f)
        }
    }
}
