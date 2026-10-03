package com.joebywan.daybook.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.joebywan.daybook.ui.stats.AchievementBadge
import kotlin.math.sin
import kotlin.random.Random

/** Render-harness hook (`?finish=` on the web build): when set, the finished frame shows over any board with this praise. */
var finishPreview: Praise? = null

private val CONFETTI = listOf(
    Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFB8C00),
)

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/**
 * About two seconds of confetti falling over the whole screen, drawn on a Canvas. It takes no input, so taps
 * pass through to what is beneath. Plays once: [played] (the caller's saved flag) stops a rotation replaying it.
 */
@Composable
fun Confetti(played: Boolean, onPlayed: () -> Unit) {
    val t = remember { Animatable(if (played) 1f else 0f) }
    val pieces = remember {
        val r = Random(2026)
        List(70) {
            Piece(r.nextFloat(), r.nextFloat() * 0.35f, 0.7f + r.nextFloat() * 0.5f, r.nextFloat() * 24f + 6f,
                r.nextFloat() * 720f - 360f, CONFETTI[r.nextInt(CONFETTI.size)], 5f + r.nextFloat() * 5f, 8f + r.nextFloat() * 6f)
        }
    }
    LaunchedEffect(Unit) {
        if (!played) {
            t.animateTo(1f, tween(2000, easing = LinearEasing))
            onPlayed()
        }
    }
    if (t.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        pieces.forEach { p ->
            val life = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (life <= 0f) return@forEach
            val x = p.x * size.width + sin(life * 9f + p.x * 20f) * p.sway * d
            val y = -20f * d + life * p.speed * (size.height + 40f * d)
            rotate(p.spin * life, Offset(x, y)) {
                drawRect(p.color.copy(alpha = 1f - life * life * life), Offset(x, y), Size(p.w * d, p.h * d))
            }
        }
    }
}

/**
 * A party popper drawn in Canvas (the web build has no emoji font): a striped cone, its mouth at the upper right,
 * with streamers and confetti bursting out. Square; mirror it with `graphicsLayer(scaleX = -1f)`.
 */
@Composable
fun PartyPopper(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 48f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        val cone = Path().apply { moveTo(5 * u, 43 * u); lineTo(18 * u, 14 * u); lineTo(34 * u, 30 * u); close() }
        drawPath(cone, Color(0xFFFFB300))
        drawLine(Color(0xFFE53935), o(11.5f, 28.5f), o(26.5f, 23f), 3f * u)
        drawLine(Color(0xFF1E88E5), o(8.5f, 36f), o(30f, 28f), 3f * u)
        drawLine(Color(0xFF8D5A00), o(18f, 14f), o(34f, 30f), 2.5f * u, StrokeCap.Round)
        fun streamer(c: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
            drawPath(Path().apply { moveTo(26 * u, 22 * u); quadraticTo(x1 * u, y1 * u, x2 * u, y2 * u) }, c, style = Stroke(2f * u, cap = StrokeCap.Round))
        streamer(Color(0xFFE53935), 28f, 6f, 38f, 3f)
        streamer(Color(0xFF43A047), 36f, 20f, 45f, 14f)
        streamer(Color(0xFF8E24AA), 24f, 10f, 20f, 3f)
        drawCircle(Color(0xFF1E88E5), 2.2f * u, o(34f, 9f))
        drawCircle(Color(0xFFE53935), 1.8f * u, o(30f, 2.5f))
        drawRect(Color(0xFF43A047), o(40f, 6f), Size(3.5f * u, 3.5f * u))
    }
}

/**
 * The optional centre of the finished frame, only when this solve earned achievements: one at a time (a tap on
 * the block shows the next, wrapping round after the last), each with its badge, name and description, then the
 * "only X to go" line. Nothing otherwise (the to-go line then sits in the banner). [badge] is the badge size (smaller on a short screen).
 */
@Composable
fun ResultsBlock(praise: Praise, accent: Color, badge: Dp = 84.dp) {
    val scheme = MaterialTheme.colorScheme
    val items = praise.achievements
    var index by rememberSaveable { mutableIntStateOf(0) }
    if (items.isEmpty()) return
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (items.isNotEmpty()) {
            val a = items[index % items.size]
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .then(if (items.size > 1) Modifier.clickable { index = (index + 1) % items.size } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))
                AchievementBadge(a.id, true, Modifier.size(badge))
                Spacer(Modifier.height(8.dp))
                Text(a.title, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(a.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center)
                if (items.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index % items.size + 1} of ${items.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
                        items.indices.forEach { i ->
                            Spacer(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(
                                if (i == index % items.size) accent else scheme.onSurfaceVariant.copy(alpha = 0.35f)))
                        }
                    }
                    Text("Tap to see next achievement", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        praise.toGo?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        }
    }
}
