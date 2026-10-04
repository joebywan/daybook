package com.joebywan.daybook.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.ui.theme.BoardHues
import kotlin.math.PI
import kotlin.math.cos

// Today's pick marker (docs/REWARDS.md): a twinkling gold sparkle frame on the tile and "Try me!" across the
// thumbnail. Only drawn while the pick is unsolved, so nothing animates once it is done. Neither changes layout.

private val SparkleGold = Color(0xFFF5C542)
private val RingGold = Color(0xFFE8A317).copy(alpha = 0.7f)
private const val SPARKLES = 30

/** Gold ring plus sparkles around the edge of the tile, drawn inside its bounds. The one phase is read only while drawing. */
@Composable
fun Modifier.sparkleFrame(corner: Dp = 18.dp): Modifier {
    val phase by rememberInfiniteTransition(label = "sparkle").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    return drawWithContent {
        drawContent()
        val ring = 1.5.dp.toPx()
        drawRoundRect(
            RingGold, topLeft = Offset(ring / 2, ring / 2),
            size = Size(size.width - ring, size.height - ring),
            cornerRadius = CornerRadius(corner.toPx()), style = Stroke(ring),
        )
        val inset = 7.dp.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        val per = 2 * (w + h)
        for (i in 0 until SPARKLES) {
            // Even spacing along the perimeter with a small deterministic wobble in and out.
            var d = (i + 0.5f) / SPARKLES * per
            val wobble = (((i * 5) % 7) - 3) * 0.7.dp.toPx()
            val at = when {
                d < w -> Offset(inset + d, inset + wobble)
                d - w < h -> Offset(inset + w - wobble, inset + d - w)
                d - w - h < w -> Offset(inset + w - (d - w - h), inset + h - wobble)
                else -> { d -= 2 * w + h; Offset(inset + wobble, inset + h - d) }
            }
            val full = (4 + (i * 3) % 5).dp.toPx() // 4..8dp across
            val p = (phase + (i * 0.37f) % 1f) % 1f
            val wave = 0.5f - 0.5f * cos(2 * PI.toFloat() * p)
            rotate(wave * 45f, at) { sparkle(at, full / 2 * (0.3f + 0.7f * wave), 0.1f + 0.9f * wave) }
        }
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, alpha: Float) {
    val k = r * 0.28f
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + k, c.y - k); lineTo(c.x + r, c.y); lineTo(c.x + k, c.y + k)
        lineTo(c.x, c.y + r); lineTo(c.x - k, c.y + k); lineTo(c.x - r, c.y)
        lineTo(c.x - k, c.y - k); close()
    }
    drawPath(path, SparkleGold, alpha)
}

/** "Try / me!" tilted across the whole thumbnail, with a halo so it reads over any puzzle's icon. Overlay: takes no space. */
@Composable
fun TryMeLabel(previewSize: Dp, modifier: Modifier = Modifier) {
    val dark = BoardHues.isDark(MaterialTheme.colorScheme)
    val ink = if (dark) Color(0xFFFFE9A8) else Color(0xFF2B1B00)
    val halo = if (dark) Color(0xFF2A1A00) else Color(0xFFFFF4D6)
    val size = with(LocalDensity.current) { (previewSize * 0.31f).toSp() } // Dp.toSp: font scale does not enlarge it
    val base = MaterialTheme.typography.titleLarge.copy(
        fontSize = size, lineHeight = size, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
    )
    val stroke = with(LocalDensity.current) { Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round) } // 2dp each side
    Box(modifier.rotate(-30f).semantics { contentDescription = "Today's pick" }) {
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Text("Try\nme!", style = base.copy(color = halo, drawStyle = stroke))
            Text("Try\nme!", style = base.copy(color = ink))
        }
    }
}
