package com.joebywan.daybook.puzzles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/*
 * Chess pieces as vector shapes authored for this project (not a copied set, not font glyphs).
 * Each piece lives in a 100x100 box, base on y=88. Path strings use absolute M L C Z and
 * "O cx cy r" (a circle), space separated. The strings are plain data on purpose: the render
 * harness (outside the repo) parses this very file, so what is checked is what ships.
 * `body` paths are filled and outlined; `detail` paths are open strokes in the outline colour.
 */
private class Art(val body: List<String>, val detail: List<String>)

private val ART: Map<Char, Art> = mapOf(
    'P' to Art(listOf("O 50 30 14", "M 38 46 L 62 46 C 60 58 66 66 70 78 L 74 78 L 74 88 L 26 88 L 26 78 L 30 78 C 34 66 40 58 38 46 Z"), listOf()),
    'R' to Art(listOf("M 26 88 L 26 78 L 32 78 L 35 40 L 28 40 L 28 14 L 40 14 L 40 24 L 46 24 L 46 14 L 54 14 L 54 24 L 60 24 L 60 14 L 72 14 L 72 40 L 65 40 L 68 78 L 74 78 L 74 88 Z"), listOf("M 33 52 L 67 52")),
    'B' to Art(listOf("O 50 15 7", "M 50 22 C 66 32 72 50 60 62 C 64 66 70 70 70 76 L 76 76 L 76 88 L 24 88 L 24 76 L 30 76 C 30 70 36 66 40 62 C 28 50 34 32 50 22 Z"), listOf("M 42 38 L 56 54")),
    'N' to Art(listOf("M 26 88 L 26 78 C 26 60 32 50 42 42 C 36 40 30 44 24 52 L 16 50 C 18 40 26 28 36 22 L 38 10 L 46 18 C 62 18 76 32 76 56 C 76 66 74 74 74 78 L 74 88 Z"), listOf("M 46 31 L 47 32", "M 22 52 L 30 50")),
    'Q' to Art(listOf("O 18 30 5", "O 34 21 5", "O 50 14 5", "O 66 21 5", "O 82 30 5", "M 26 88 L 26 78 L 18 34 L 34 56 L 35 24 L 46 52 L 50 18 L 54 52 L 65 24 L 66 56 L 82 34 L 74 78 L 74 88 Z"), listOf("M 28 70 L 72 70")),
    'K' to Art(listOf("M 24 88 L 24 78 C 18 58 28 46 44 44 L 44 28 L 35 28 L 35 18 L 44 18 L 44 8 L 56 8 L 56 18 L 65 18 L 65 28 L 56 28 L 56 44 C 72 46 82 58 76 78 L 76 88 Z"), listOf("M 28 70 L 72 70")),
)

private fun parse(s: String): Path {
    val p = Path()
    val t = s.split(' ')
    var i = 0
    fun f() = t[i++].toFloat()
    while (i < t.size) {
        when (t[i++]) {
            "M" -> p.moveTo(f(), f())
            "L" -> p.lineTo(f(), f())
            "C" -> p.cubicTo(f(), f(), f(), f(), f(), f())
            "O" -> { val x = f(); val y = f(); val r = f(); p.addOval(Rect(x - r, y - r, x + r, y + r)) }
            "Z" -> p.close()
        }
    }
    return p
}

private class Shapes(val body: List<Path>, val detail: List<Path>)

private val SHAPES: Map<Char, Shapes> by lazy {
    ART.mapValues { (_, a) -> Shapes(a.body.map(::parse), a.detail.map(::parse)) }
}

/**
 * Draws one piece in a square of side [size] at [topLeft]. [kind] is K Q R B N P.
 * White and black differ in fill and outline: pass a light fill with a dark outline for white and a dark
 * fill with a light outline for black. [outline] is in 100ths of the piece, so it thickens with the cell.
 */
internal fun DrawScope.drawChessPiece(
    kind: Char,
    topLeft: Offset,
    size: Float,
    fill: Color,
    line: Color,
    outline: Float = 6f,
) {
    val sh = SHAPES[kind] ?: return
    translate(topLeft.x, topLeft.y) {
        scale(size / 100f, pivot = Offset.Zero) {
            val edge = Stroke(outline, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Fill, outline, then fill again: a ball on a crown keeps the body's outline off its own interior.
            sh.body.forEach { drawPath(it, fill, style = Fill) }
            sh.body.forEach { drawPath(it, line, style = edge) }
            sh.body.forEach { drawPath(it, fill, style = Fill) }
            sh.detail.forEach { drawPath(it, line, style = Stroke(outline * 0.7f, cap = StrokeCap.Round)) }
        }
    }
}

/** The home-grid motif: a fixed back-rank mate crop (5 files x 3 ranks), centred and fitted to [box]. */
internal fun DrawScope.drawChessMotif(
    box: Size,
    light: Color,
    dark: Color,
    whiteFill: Color,
    whiteLine: Color,
    blackFill: Color,
    blackLine: Color,
) {
    val cols = 5
    val rows = 3
    val cell = minOf(box.width / cols, box.height / rows)
    val ox = (box.width - cell * cols) / 2f
    val oy = (box.height - cell * rows) / 2f
    for (r in 0 until rows) for (c in 0 until cols) {
        drawRect(
            if ((r + c) % 2 == 0) light else dark,
            Offset(ox + c * cell, oy + r * cell), Size(cell, cell),
        )
    }
    // Rook mates along the back rank: black king and its own three pawns, white rook, one white knight.
    val pieces = listOf(
        Triple('R', true, 0 to 0), Triple('K', false, 3 to 0),
        Triple('P', false, 2 to 1), Triple('P', false, 3 to 1), Triple('P', false, 4 to 1),
        Triple('N', true, 4 to 2),
    )
    for ((k, white, rc) in pieces) {
        drawChessPiece(
            k, Offset(ox + rc.first * cell, oy + rc.second * cell), cell,
            if (white) whiteFill else blackFill, if (white) whiteLine else blackLine,
        )
    }
}
