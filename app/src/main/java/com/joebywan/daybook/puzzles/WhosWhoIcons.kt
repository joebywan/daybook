package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joebywan.daybook.ui.theme.BoardHues

/** A category of test:Who's who drawn as a mark: [PERSON], or an index into [WhosWhoWords.HEADERS]. */
internal object WhosWhoIcons {

    const val PERSON = -1

    /** Red, gold, green and blue for Colour's four squares; the ink step of each, so each holds on the surface. */
    private val RAINBOW = listOf(8f, 46f, 145f, 215f)

    /** The mark for [kind], inked [ink], labelled [label] for a screen reader. */
    @Composable
    fun Icon(kind: Int, ink: Color, dark: Boolean, label: String, modifier: Modifier = Modifier) {
        Canvas(modifier.semantics { contentDescription = label }) {
            // Drawn on a 24-unit square, centred in whatever box it is given.
            val side = minOf(size.width, size.height)
            translate((size.width - side) / 2, (size.height - side) / 2) {
                scale(side / 24f, side / 24f, pivot = Offset.Zero) { draw(kind, ink, dark) }
            }
        }
    }

    private fun DrawScope.draw(kind: Int, ink: Color, dark: Boolean) {
        when (kind) {
            PERSON -> {
                drawCircle(ink, 4.6f, Offset(12f, 7.5f))
                drawArc(ink, 180f, 180f, useCenter = true, topLeft = Offset(3.5f, 14f), size = Size(17f, 16f))
            }
            0 -> { // House: a roof, walls and a door cut out of them.
                val p = Path().apply {
                    fillType = PathFillType.EvenOdd
                    moveTo(12f, 2.5f)
                    lineTo(22f, 11f)
                    lineTo(19f, 11f)
                    lineTo(19f, 21f)
                    lineTo(5f, 21f)
                    lineTo(5f, 11f)
                    lineTo(2f, 11f)
                    close()
                    addRect(Rect(10f, 14f, 14f, 21f))
                }
                drawPath(p, ink)
            }
            1 -> { // Colour: four coloured squares.
                for ((k, hue) in RAINBOW.withIndex()) {
                    val x = 3f + (k % 2) * 9.5f
                    val y = 3f + (k / 2) * 9.5f
                    drawRoundRect(BoardHues.ink(hue, dark), Offset(x, y), Size(8.5f, 8.5f), CornerRadius(1.8f))
                }
            }
            2 -> { // Pet: a paw print.
                drawOval(ink, Offset(6.5f, 12.5f), Size(11f, 9f))
                for ((x, y) in listOf(4.5f to 10.5f, 9f to 5.5f, 15f to 5.5f, 19.5f to 10.5f)) {
                    drawOval(ink, Offset(x - 2.3f, y - 2.8f), Size(4.6f, 5.6f))
                }
            }
            3 -> { // Job: a briefcase with its handle and a clasp line.
                drawRoundRect(ink, Offset(8.5f, 3.5f), Size(7f, 6f), CornerRadius(1.5f), style = Stroke(2f))
                val p = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(RoundRect(Rect(2.5f, 8f, 21.5f, 20.5f), CornerRadius(2.2f)))
                    addRect(Rect(2.5f, 12.6f, 21.5f, 13.8f))
                }
                drawPath(p, ink)
            }
            4 -> { // Drink: a cup with a handle.
                val p = Path().apply {
                    moveTo(3.5f, 7f)
                    lineTo(17f, 7f)
                    lineTo(17f, 15f)
                    quadraticTo(17f, 20.5f, 11.5f, 20.5f)
                    lineTo(9f, 20.5f)
                    quadraticTo(3.5f, 20.5f, 3.5f, 15f)
                    close()
                }
                drawPath(p, ink)
                drawArc(ink, -90f, 180f, useCenter = false, topLeft = Offset(14.5f, 8.5f), size = Size(7f, 7f), style = Stroke(2f))
            }
            else -> { // Music: a note with a flag.
                rotate(-20f, Offset(8f, 18f)) { drawOval(ink, Offset(3.5f, 15f), Size(9f, 6.5f)) }
                drawRect(ink, Offset(10.6f, 3f), Size(2f, 15.5f))
                val flag = Path().apply {
                    moveTo(10.6f, 3f)
                    cubicTo(12f, 7f, 19f, 7.5f, 18f, 14f)
                    cubicTo(17f, 10.5f, 14f, 9.5f, 12.6f, 9.5f)
                    close()
                }
                drawPath(flag, ink)
            }
        }
    }
}
