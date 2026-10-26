package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.theme.BoardHues

/** Text sizes as fractions of a square: the corner number's size and line height, the letter's, and the letter's top padding. */
internal class CrosswordText(val corner: Float, val cornerLine: Float, val letter: Float, val letterLine: Float, val letterTop: Float)

/**
 * A crossword grid (Codeword's and Mini's): black squares, and white ones with a number in the corner ([number], 0 for
 * none) and a letter, once placed, in the middle ([letter], null for none). [tint] colours a white square;
 * [squareModifier] adds to each.
 */
@Composable
internal fun CrosswordGrid(
    size: Int,
    square: Dp,
    text: CrosswordText,
    accent: Color,
    black: (Int) -> Boolean,
    number: (Int) -> Int,
    letter: (Int) -> String?,
    tint: (Int) -> Color,
    letterColour: (Int) -> Color,
    modifier: Modifier = Modifier,
    bold: (Int) -> Boolean = { false },
    feel: MoveFeel = MoveFeel.Still,
    squareModifier: @Composable (index: Int) -> Modifier = { Modifier },
) {
    val scheme = MaterialTheme.colorScheme
    val dark = BoardHues.isDark(scheme)
    // Blocks: near-black on the light card, nearly the background in the dark (the white squares carry the grid).
    val block = if (dark) scheme.background else scheme.onSurface.copy(alpha = 0.85f)
    Box(modifier.size(square * size)) {
        for (i in 0 until size * size) {
            val at = Modifier.padding(start = square * (i % size), top = square * (i / size)).size(square)
            if (black(i)) {
                Box(at.padding(0.5.dp).background(block))
                continue
            }
            Box(
                at
                    .moveFeel(feel, i, accent)
                    .then(squareModifier(i))
                    .padding(0.5.dp)
                    .clip(RoundedCornerShape(minOf(4.dp, square * 0.1f)))
                    .background(tint(i)),
            ) {
                val n = number(i)
                if (n != 0 && square >= 14.dp) Text(
                    n.toString(),
                    Modifier.align(Alignment.TopStart).padding(start = square * 0.07f),
                    fontSize = (square.value * text.corner).sp,
                    lineHeight = (square.value * text.cornerLine).sp,
                    color = scheme.onSurfaceVariant,
                )
                val l = letter(i)
                if (l != null) Text(
                    l,
                    Modifier.align(Alignment.Center).padding(top = square * text.letterTop),
                    fontSize = (square.value * text.letter).sp,
                    lineHeight = (square.value * text.letterLine).sp,
                    fontWeight = if (bold(i)) FontWeight.Bold else FontWeight.Medium,
                    color = letterColour(i),
                )
            }
        }
    }
}
