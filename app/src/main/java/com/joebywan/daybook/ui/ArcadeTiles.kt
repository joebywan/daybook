package com.joebywan.daybook.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.joebywan.daybook.ui.theme.ArcadeBoard

/** The gap between two of Arcade's square tiles of side [u] px, where the frame shows as the grid line (Tents'). */
fun arcadeGap(u: Float) = maxOf(2f, u * 0.07f)

/**
 * Arcade's grid, as Tents draws it: the frame under a [cols] x [rows] board of [u] px squares from [origin], showing between
 * them as the grid lines, and every square a plain tile. For a line board (Pearls, Links, Pipes) whose lines run over it.
 */
fun DrawScope.drawArcadeTiles(cols: Int, rows: Int, u: Float, a: ArcadeBoard, origin: Offset = Offset.Zero) {
    val gap = arcadeGap(u)
    drawRoundRect(a.frame, origin, Size(cols * u, rows * u), CornerRadius(u * 0.18f))
    val tile = Size(u - gap, u - gap)
    val corner = CornerRadius(u * 0.14f)
    for (r in 0 until rows) for (c in 0 until cols) {
        drawRoundRect(a.square, origin + Offset(c * u + gap / 2, r * u + gap / 2), tile, corner)
    }
}
