package com.joebywan.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.highlightAnchor

/** The digit pad under a grid (Sudoku, Cages, Inequality, Regions, Cipher), which the grid's size has to leave room for. */
val PAD_HEIGHT = 48.dp

/** The space between a grid and its pad. */
val PAD_GAP = 18.dp

/**
 * One key of a pad row: an equal share of the row, [PAD_HEIGHT] tall, filled with [fill], reporting
 * highlight index [anchor] and glowing (never dimming) when a hint names it. [modifier] goes on before
 * the height, so an alpha there fades the ring as well.
 */
@Composable
fun RowScope.PadKey(
    anchor: Int,
    highlight: BoardHighlight,
    glow: Color,
    pulse: State<Float>,
    fill: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .then(modifier)
            .height(PAD_HEIGHT)
            .highlightAnchor(anchor)
            .ring(highlight.look(anchor, dims = false), glow, pulse, corner)
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
