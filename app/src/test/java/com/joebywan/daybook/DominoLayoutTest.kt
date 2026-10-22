package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Dominoes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dominoes' board and tray sizes. The boxes are the board's own constraints in the web build, less the board's
 * padding (12 dp a side, 6 top and bottom): about 351 x 355 dp at 375x537 and 366 x 638 at 390x844. The owner's
 * baseline for "fiddly" is Nonogram Expert's square on a phone, 16.6 dp at 375x537 (`SandLayoutTest`).
 */
class DominoLayoutTest {

    /** Each tier's box and its most dominoes (docs/DOMINO_SPEC.md). */
    private val tiers = listOf(Triple(5, 5, 7), Triple(6, 6, 10), Triple(7, 7, 14))

    @Test
    fun `on the short phone every tier's square is twice a Nonogram Expert square and the tray is tappable`() {
        for ((w, h, d) in tiers) {
            val fit = Dominoes.fit(0, 0, w, h, d, 351f, 355f)
            println("${w}x$h with $d: square ${fit.unit} dp, tray unit ${fit.tray} dp, slot ${fit.tray * Dominoes.SLOT_W} x ${fit.tray * Dominoes.SLOT_H}")
            assertTrue("${w}x$h: square ${fit.unit}", fit.unit >= 2 * 16.6f)
            assertTrue("${w}x$h: slot ${fit.tray * Dominoes.SLOT_W} wide", fit.tray * Dominoes.SLOT_W >= 44f)
            assertTrue("${w}x$h: slot ${fit.tray * Dominoes.SLOT_H} tall", fit.tray * Dominoes.SLOT_H >= 32f)
        }
    }

    @Test
    fun `board and tray fit their box on both axes, and the tray keeps at most seven a row`() {
        for ((w, h, d) in tiers) for ((bw, bh) in listOf(351f to 355f, 366f to 638f, 600f to 300f, 300f to 900f, 900f to 700f)) {
            val fit = Dominoes.fit(0, 0, w, h, d, bw, bh)
            assertTrue("${w}x$h in ${bw}x$bh: width ${fit.width}", fit.width <= bw + 0.01f)
            assertTrue("${w}x$h in ${bw}x$bh: height ${fit.height}", fit.height <= bh + 0.01f)
            assertTrue(fit.perRow <= Dominoes.MAX_PER_ROW)
            assertTrue("every domino has a slot", fit.trayRows * fit.perRow >= d)
            assertTrue("the tray domino is no larger than a square", fit.tray <= fit.unit)
            // Slots never overlap and every one lies in the box.
            for (k in 0 until d) {
                val r = fit.slot(k)
                assertTrue(r.left >= -0.01f && r.right <= fit.width + 0.01f && r.bottom <= fit.height + 0.01f)
                for (j in 0 until k) assertTrue(!r.deflate(0.01f).overlaps(fit.slot(j)))
            }
        }
    }

    @Test
    fun `a tap finds the square and the slot it lands on`() {
        val fit = Dominoes.fit(0, 0, 7, 7, 14, 351f, 355f)
        for (q in 0 until 49) assertEquals(q, fit.squareAt(fit.square(q % 7, q / 7).center, 7))
        for (k in 0 until 14) assertEquals(k, fit.slotAt(fit.slot(k).center))
        assertEquals(-1, fit.slotAt(fit.square(3, 3).center))
    }
}
