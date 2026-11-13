package com.joebywan.daybook

import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.puzzles.shikakuSticker
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shikaku's Arcade stickers: the number reads on every one, and every one stands off the frame that seams it. */
class ShikakuInksTest {
    @Test fun `every sticker carries its number and stands off the frame, in both schemes`() {
        for (dark in listOf(false, true)) {
            val a = if (dark) ArcadeBoard.Dark else ArcadeBoard.Light
            val frame = a.frame.compositeOver(a.square)
            for (k in 0 until 8) {
                val hue = k * 45f + 2.5f // Block.hue()'s eight slots
                val fill = shikakuSticker(hue, dark)
                for (stop in listOf(fill.from, fill.to)) {
                    val ink = contrast(fill.ink, stop)
                    assertTrue("hue $hue dark=$dark: number at $ink:1", ink >= 4.5f)
                    val seam = contrast(stop, frame)
                    assertTrue("hue $hue dark=$dark: against the frame $seam:1", seam >= 3f)
                }
            }
        }
    }
}
