package com.joebywan.daybook.ui

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight

/**
 * What [com.joebywan.daybook.core.LocalBoardHighlight] asks of one element. Elements not named step back
 * ([dim]) while anything is highlighted, so the named ones read on a busy board; keys and controls pass
 * `dims = false`, glowing when named but never dimming, because the move a hint asks for is made with them.
 */
class HintLook(val strong: Boolean, val soft: Boolean, val dim: Boolean)

fun BoardHighlight.look(index: Int, dims: Boolean = true): HintLook {
    val strong = index in this.strong
    val soft = !strong && index in this.soft
    return HintLook(strong, soft, dims && !isEmpty && !strong && !soft)
}

/**
 * The hint ring: breathing in [glow] for strong, a quiet fixed line for soft; [pulse] is read in the draw
 * phase so it repaints without recomposing. Drawn just inside the element by default (squares that sit a
 * dp apart), or 1dp [outside] it so the element's own colour is untouched (apply before the element's own
 * `clip`, or the ring is clipped away); [round] draws an outside circle instead of a rounded rectangle.
 */
fun Modifier.ring(
    look: HintLook,
    glow: Color,
    pulse: State<Float>,
    corner: Dp,
    outside: Boolean = false,
    round: Boolean = false,
): Modifier =
    if (!look.strong && !look.soft) this
    else drawWithContent {
        drawContent()
        val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
        val colour = if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f)
        val out = 1.dp.toPx() + w / 2
        when {
            round -> drawCircle(colour, radius = size.minDimension / 2 + out, style = Stroke(w))
            outside -> drawRoundRect(
                colour,
                topLeft = Offset(-out, -out),
                size = Size(size.width + 2 * out, size.height + 2 * out),
                cornerRadius = CornerRadius(corner.toPx() + out),
                style = Stroke(w),
            )
            else -> drawRoundRect(
                colour,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner.toPx()),
                style = Stroke(w),
            )
        }
    }

fun Modifier.dimmed(look: HintLook): Modifier = if (look.dim) alpha(0.3f) else this
