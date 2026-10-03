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
import androidx.compose.ui.unit.dp
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
 * The optional middle of the finished frame. Shows the achievements this solve earned, one at a time (a tap on
 * the block shows the next, wrapping round after the last), and the "only X to go" line. Nothing when there is
 * neither. The buttons are never behind it.
 */
@Composable
fun ResultsBlock(praise: Praise, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val items = praise.achievements
    var index by rememberSaveable { mutableIntStateOf(0) }
    if (items.isEmpty() && praise.toGo == null) return
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AchievementBadge(a.id, true, Modifier.size(44.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
                    }
                }
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
            if (items.isNotEmpty()) Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        }
    }
}
