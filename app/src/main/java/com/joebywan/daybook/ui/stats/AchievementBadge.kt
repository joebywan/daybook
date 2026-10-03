package com.joebywan.daybook.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.AchievementCategory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The centre mark of each badge: text, or "#star" / "#crown" for a drawn shape. One entry per achievement id;
 * `AchievementsTest` fails when a new achievement has none.
 */
internal val BADGE_GLYPHS: Map<String, String> = mapOf(
    "solves1" to "1", "solves100" to "100", "days30" to "30",
    "streak3" to "3", "streak7" to "7", "streak14" to "14", "streak30" to "30",
    "streak60" to "60", "streak100" to "100", "streak365" to "365",
    "puzzleStreak7" to "7", "puzzleStreak30" to "30",
    "firstTop" to "#star", "cleanTop" to "#crown",
    "oneOfEach" to "#star", "fullSet" to "3/3", "allPuzzles" to "All",
)

private fun colourOf(c: AchievementCategory, dark: Boolean) = when (c) {
    AchievementCategory.STREAK -> if (dark) Color(0xFF3F9E7F) else Color(0xFF1F6F5C)
    AchievementCategory.PUZZLE_STREAK -> if (dark) Color(0xFFD08A33) else Color(0xFFB06C1E)
    AchievementCategory.COVERAGE -> Color(0xFFC0563F)
    AchievementCategory.MILESTONE -> if (dark) Color(0xFF5B8FD0) else Color(0xFF3D6FB0)
}

/**
 * A badge: a medal on two ribbon tails, coloured by its achievement's category with the glyph in the middle.
 * Unearned, it is a silhouette: one flat low-contrast fill over the same outline, no glyph.
 */
@Composable
fun AchievementBadge(id: String, earned: Boolean, modifier: Modifier = Modifier) {
    val a = ACHIEVEMENTS.first { it.id == id }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val fill = colourOf(a.category, dark)
    val glyph = BADGE_GLYPHS.getValue(id)
    val ink = if (fill.luminance() > 0.35f) Color(0xFF11201C) else Color.White
    // Opaque, so the ribbon and the disc read as one flat shape where they overlap.
    val sil = scheme.onSurface.copy(alpha = 0.14f).compositeOver(scheme.background)
    BoxWithConstraints(
        modifier.aspectRatio(0.8f).semantics { contentDescription = if (earned) a.title else "Locked achievement" },
        contentAlignment = Alignment.TopCenter,
    ) {
        val bw = maxWidth.value
        Canvas(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
            val w = size.width
            val c = Offset(w / 2, w * 0.5f)
            val r = w * 0.4f
            // Ribbon tails first, so the medal covers where they start.
            val ribbon = if (earned) fill.copy(alpha = 0.75f) else sil
            for (side in listOf(-1f, 1f)) {
                val x0 = c.x + side * r * 0.1f
                val x1 = c.x + side * r * 0.75f
                val xm = c.x + side * r * 0.45f
                val top = c.y + r * 0.3f
                val bot = size.height
                drawPath(
                    Path().apply {
                        moveTo(x0, top); lineTo(x1, top + r * 0.1f)
                        lineTo(x1 + side * r * 0.1f, bot); lineTo(xm + side * r * 0.1f, bot - r * 0.3f)
                        lineTo(x0 + side * r * 0.1f, bot); close()
                    },
                    ribbon,
                )
            }
            if (earned) {
                drawCircle(fill, r, c)
                drawCircle(ink.copy(alpha = 0.45f), (r * 0.82f), c, style = Stroke(w * 0.02f))
                drawCircle(Color.White.copy(alpha = 0.12f), (r * 0.8f), c)
                if (glyph.startsWith("#")) {
                    val g = r * 0.5f
                    if (glyph == "#star") star(c, g, ink) else crown(c, g, ink)
                }
            } else {
                drawCircle(sil, r, c)
            }
        }
        if (earned && !glyph.startsWith("#")) {
            // The medal's centre is the middle of the top w x w square.
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Text(
                    glyph,
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = (bw * if (glyph.length > 2) 0.2f else 0.3f).sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun DrawScope.star(c: Offset, r: Float, colour: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val ang = -PI.toFloat() / 2 + i * PI.toFloat() / 5
        val x = c.x + (rr * cos(ang)).toFloat()
        val y = c.y + (rr * sin(ang)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, colour)
}

private fun DrawScope.crown(c: Offset, r: Float, colour: Color) {
    val l = c.x - r; val rt = c.x + r; val t = c.y - r * 0.8f; val b = c.y + r * 0.7f
    drawPath(
        Path().apply {
            moveTo(l, b); lineTo(l, t); lineTo(c.x - r * 0.5f, c.y); lineTo(c.x, t)
            lineTo(c.x + r * 0.5f, c.y); lineTo(rt, t); lineTo(rt, b); close()
        },
        colour,
    )
}

/** Home-screen button icon: a rosette (flower of petals on a disc) with two ribbon tails, in one [tint]. */
@Composable
fun MedalIcon(modifier: Modifier, tint: Color, description: String) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width
        val c = Offset(w / 2, w * 0.4f)
        val r = w * 0.34f
        for (side in listOf(-1f, 1f)) {
            drawPath(
                Path().apply {
                    moveTo(c.x + side * r * 0.1f, c.y + r * 0.5f); lineTo(c.x + side * r * 0.85f, c.y + r * 0.6f)
                    lineTo(c.x + side * r * 0.6f, size.height); lineTo(c.x + side * r * 0.35f, size.height - r * 0.35f)
                    lineTo(c.x + side * r * 0.05f, size.height); close()
                },
                tint,
            )
        }
        drawCircle(tint, r, c, style = Stroke(w * 0.07f))
        star(c, r * 0.62f, tint)
    }
}
