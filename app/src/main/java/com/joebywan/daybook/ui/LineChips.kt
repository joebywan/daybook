package com.joebywan.daybook.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues

/** Arcade's chip for a line that holds its number: Tents' met green, light and dark (`ArcadeNumberGridsTest`). */
internal val ArcadeMetLight = ArcadeFill(Color(0xFF087F5B), Color(0xFF046C4E), Color.White)
internal val ArcadeMetDark = ArcadeFill(Color(0xFF00E6A0), Color(0xFF00A67E), Color(0xFF05281F))

/**
 * The number chips of a square board's lines (Tally's targets, Thermometers' counts): one beside each row and one under
 * each column of an [n] x [n] grid of [u]-pixel squares, [gap] squares out, a row's [across] squares wide and a column's
 * [down] squares tall. A chip is [band] while open, green once its line holds its number ([done]) and solid
 * `scheme.error` with a dashed ring once it no longer can ([bad]; the ring so red and green differ by more than hue).
 * Row `r` is highlight index `n * n + r`, column `c` is `n * n + n + c`; while a hint names anything, the chips it does
 * not name fade. [texts] are the numbers, rows then columns. Given [arcade] (the Arcade look), an open chip is the given
 * fill, a met one Tents' green and a bad one the token red, each under its own ink.
 */
fun DrawScope.drawLineChips(
    n: Int,
    u: Float,
    gap: Float,
    across: Float,
    down: Float,
    texts: List<TextLayoutResult>,
    done: BooleanArray,
    bad: BooleanArray,
    band: Color,
    scheme: ColorScheme,
    overlay: HintGlow,
    arcade: ArcadeBoard? = null,
) {
    val met = if (BoardHues.isDark(scheme)) ArcadeMetDark else ArcadeMetLight
    val highlight = overlay.highlight
    val lit = !highlight.isEmpty
    val good = BoardHues.mark(145f)
    for (line in 0 until 2 * n) {
        val index = n * n + line
        val named = index in highlight.strong || index in highlight.soft
        val (topLeft, size) = if (line < n) {
            Offset(n * u + gap * u, line * u + u * 0.08f) to Size(across * u, u * 0.84f)
        } else {
            Offset((line - n) * u + u * 0.08f, n * u + gap * u) to Size(u * 0.84f, down * u)
        }
        val fill = when {
            bad[line] -> arcade?.error ?: scheme.error
            done[line] -> good
            else -> arcade?.given ?: band
        }
        if (arcade != null && done[line] && !bad[line]) drawRoundRect(met.brush(topLeft, size), topLeft, size, CornerRadius(u * 0.18f))
        else drawRoundRect(fill, topLeft, size, CornerRadius(u * 0.18f))
        if (bad[line]) {
            val w = maxOf(u * 0.05f, 1.5f)
            val inset = u * 0.07f
            drawRoundRect(
                arcade?.errorInk ?: BoardHues.onFill(scheme.error), topLeft + Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
                CornerRadius(u * 0.12f), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 2f, w * 1.5f))),
            )
        }
        if (lit && named) {
            drawRoundRect(overlay.glow.copy(alpha = overlay.pulse), topLeft, size, CornerRadius(u * 0.18f), style = Stroke(u * 0.08f))
        }
        val ink = when {
            arcade != null -> if (bad[line]) arcade.errorInk else if (done[line]) met.ink else arcade.givenInk
            bad[line] -> BoardHues.onFill(scheme.error)
            done[line] -> BoardHues.onFill(good)
            else -> scheme.onSurface
        }
        val alpha = if (lit && !named) 0.4f else 1f
        val layout = texts[line]
        drawText(
            layout, ink.copy(alpha = alpha),
            Offset(topLeft.x + (size.width - layout.size.width) / 2f, topLeft.y + (size.height - layout.size.height) / 2f),
        )
    }
}
