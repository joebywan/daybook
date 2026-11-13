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
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.boardSp
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.classicLook

/** Text sizes as fractions of a square: the corner number's size and line height, the letter's, and the letter's top padding. */
internal class CrosswordText(val corner: Float, val cornerLine: Float, val letter: Float, val letterLine: Float, val letterTop: Float)

/**
 * A crossword grid (Codeword's and Mini's): black squares, and white ones with a number in the corner ([number], 0 for
 * none) and a letter, once placed, in the middle ([letter], null for none). [tint] fills a white square; [squareModifier]
 * adds to each. In Classic a tint is flat, black squares are drawn and the corner number is the scheme's; in Arcade the
 * grid sits on [ArcadeBoard.frame], which shows between the squares and as the black ones, and a number takes its tint's ink.
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
    tint: (Int) -> ArcadeFill,
    letterColour: (Int) -> Color,
    modifier: Modifier = Modifier,
    bold: (Int) -> Boolean = { false },
    feel: MoveFeel = MoveFeel.Still,
    squareModifier: @Composable (index: Int) -> Modifier = { Modifier },
) {
    val scheme = MaterialTheme.colorScheme
    val dark = BoardHues.isDark(scheme)
    val classic = classicLook()
    // Blocks: near-black on the light card, nearly the background in the dark (the white squares carry the grid).
    val block = if (dark) scheme.background else scheme.onSurface.copy(alpha = 0.85f)
    val frame = ArcadeBoard.of(scheme).frame
    // Arcade's frame shows between the squares: a gap round each, the frame a half gap past the outer ones.
    val gap = maxOf(1.5.dp, square * 0.06f)
    val corner = if (classic) minOf(4.dp, square * 0.1f) else minOf(6.dp, square * 0.14f)
    Box(modifier.size(square * size).then(if (classic) Modifier else Modifier.background(frame, RoundedCornerShape(corner + gap / 2)))) {
        for (i in 0 until size * size) {
            val at = Modifier.padding(start = square * (i % size), top = square * (i / size)).size(square)
            if (black(i)) {
                if (classic) Box(at.padding(0.5.dp).background(block))
                continue
            }
            val fill = tint(i)
            Box(
                at
                    .moveFeel(feel, i, accent)
                    .then(squareModifier(i))
                    .padding(if (classic) 0.5.dp else gap / 2)
                    .clip(RoundedCornerShape(corner))
                    .background(fill.brush),
            ) {
                val n = number(i)
                if (n != 0 && square >= 14.dp) Text(
                    n.toString(),
                    Modifier.align(Alignment.TopStart).padding(start = square * 0.07f),
                    fontSize = (square * text.corner).boardSp(),
                    lineHeight = (square * text.cornerLine).boardSp(),
                    color = if (classic) scheme.onSurfaceVariant else fill.ink.copy(alpha = 0.75f),
                )
                val l = letter(i)
                if (l != null) Text(
                    l,
                    Modifier.align(Alignment.Center).padding(top = square * text.letterTop),
                    fontSize = (square * text.letter).boardSp(),
                    lineHeight = (square * text.letterLine).boardSp(),
                    fontWeight = if (bold(i)) FontWeight.Bold else FontWeight.Medium,
                    color = letterColour(i),
                )
            }
        }
    }
}
