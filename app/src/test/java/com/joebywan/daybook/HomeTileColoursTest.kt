package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.ui.home.TILE_ACCENTS
import com.joebywan.daybook.ui.home.homeColumns
import com.joebywan.daybook.ui.home.tileAccentAt
import com.joebywan.daybook.ui.theme.ArcadeBoard
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arcade's home tiles are coloured by their place (the owner, 2026-11-13): no two touching tiles share a colour, diagonals
 * included, at every column count the grid can take, and the tiles are far apart (CIEDE2000, normal or twice the worst
 * colour-blind vision, between the middles of their gradients: 11.66 at the closest).
 */
class HomeTileColoursTest {
    @Test fun `no two touching tiles match, at every column count`() {
        // Every count homeColumns gives, from a narrow phone to a wide short desktop window, and then some.
        val counts = (300..2600 step 20).flatMap { w -> (300..1400 step 40).map { h -> homeColumns(w.dp, h.dp) } }.toSet() + (3..20)
        for (columns in counts) {
            // Today's three sit as a row above the first (row -1).
            for (slot in -columns until 60) {
                val r = Math.floorDiv(slot, columns)
                val c = Math.floorMod(slot, columns)
                for ((dr, dc) in listOf(0 to 1, 1 to -1, 1 to 0, 1 to 1)) {
                    val c2 = c + dc
                    if (c2 !in 0 until columns) continue
                    assertNotEquals("$columns columns, ($r, $c) beside (${r + dr}, $c2)", tileAccentAt(r, c), tileAccentAt(r + dr, c2))
                }
            }
        }
    }

    @Test fun `the tile colours are well apart in both schemes`() {
        for (dark in listOf(false, true)) {
            val mids = TILE_ACCENTS.map { ArcadeBoard.arcadeTile(Color(it), dark).let { t -> lerp(t.from, t.to, .5f) } }
            for (i in mids.indices) for (j in i + 1 until mids.size) {
                val d = minOf(ColourDistance.de2000(mids[i], mids[j], null), 2 * ColourDistance.apart(mids[i], mids[j]))
                assertTrue("tiles $i and $j are $d apart, dark=$dark", d >= 11.0)
            }
        }
    }
}
