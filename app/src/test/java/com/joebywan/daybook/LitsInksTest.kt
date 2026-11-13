package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsInks
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LITS's Arcade pieces, as LitsPaletteTest holds Classic's: every letter reads on both stops of its fill, every pair of pieces
 * is CIEDE2000 apart (colour-blind vision included), and every piece is clear of the plain square and the in-progress amber
 * (LITS fills no square red: a mistake is the hint's ring). Walls stand off the squares.
 */
class LitsInksTest {
    private fun mid(a: Color, b: Color) = lerp(a, b, 0.5f)

    @Test fun `letters, pieces and walls read, in both schemes`() {
        for ((name, inks, a) in listOf(Triple("light", LitsInks.Light, ArcadeBoard.Light), Triple("dark", LitsInks.Dark, ArcadeBoard.Dark))) {
            assertEquals(Lits.Piece.entries.toSet(), inks.pieces.keys)
            val fills = inks.pieces.values.toList()
            for ((piece, f) in inks.pieces) for (stop in listOf(f.from, f.to)) {
                assertTrue("$name $piece letter ${contrast(f.ink, stop)}", contrast(f.ink, stop) >= 4.5f)
            }
            val mids = fills.map { mid(it.from, it.to) }
            val closest = mids.indices.flatMap { i -> (i + 1 until mids.size).map { j -> ColourDistance.apart(mids[i], mids[j]) } }.min()
            val names = inks.pieces.keys.toList()
            val pairs = mids.indices.flatMap { i -> (i + 1 until mids.size).map { j -> "${names[i]}${names[j]} %.1f".format(ColourDistance.apart(mids[i], mids[j])) } }
            assertTrue("$name closest pair of pieces: $closest, $pairs", closest >= MIN_APART)
            for (other in listOf(a.square, mid(a.selected.from, a.selected.to))) for (m in mids) {
                assertTrue("$name piece $m against $other: ${ColourDistance.apart(m, other)}", ColourDistance.apart(m, other) >= MIN_CLEAR)
            }
            val wall = inks.wall.compositeOver(a.square)
            assertTrue("$name wall ${contrast(wall, a.square)}", contrast(wall, a.square) >= 3f)
        }
    }

    private companion object {
        const val MIN_APART = 15.0
        const val MIN_CLEAR = 11.0
    }
}
