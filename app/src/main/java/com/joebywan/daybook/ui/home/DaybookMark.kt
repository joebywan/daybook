package com.joebywan.daybook.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

private val Sun = Color(0xFFD08A33)
private val BookRim = Color(0xFF3F9E7F)
private val BookPage = Color(0xFFF6F1E7)

private const val RIM = "M32 36 C24 32 14 29 6 27 C5 27 4 27.5 4 29 C4.5 38 6 46 8 52 C17 56 25 59.5 29 60 L35 60 " +
    "C39 59.5 47 56 56 52 C58 46 59.5 38 60 29 C60 27.5 59 27 58 27 C50 29 40 32 32 36 Z"
private const val LEFT = "M31 38 C24 35 15 32 8 30.5 C8.6 37 9.8 43 11.2 47.5 C18 50 25 53 31 55 Z"
private const val RIGHT = "M33 38 C40 35 49 32 56 30.5 C55.4 37 54.2 43 52.8 47.5 C46 50 39 53 33 55 Z"

/**
 * The launcher mark — sun over an open book — drawn bare, from the same simplified shapes as the
 * web favicon (`web/.../favicon.svg`), without its dark tile: amber, green and cream all hold on
 * both the light and the dark page, which a dark tile would not (it would vanish into the dark one).
 */
@Composable
internal fun DaybookMark(modifier: Modifier = Modifier) {
    val paths = remember {
        listOf(RIM, LEFT, RIGHT).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier) {
        scale(size.width / 64f, size.width / 64f, pivot = Offset.Zero) {
            drawCircle(Sun, radius = 12f, center = Offset(32f, 17f))
            // The favicon squashes the book to 88% of its height about y=60, then lifts it 2.
            translate(0f, -2f) {
                scale(1f, 0.88f, pivot = Offset(32f, 60f)) {
                    drawPath(paths[0], BookRim)
                    drawPath(paths[1], BookPage)
                    drawPath(paths[2], BookPage)
                }
            }
        }
    }
}
