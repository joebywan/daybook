package com.joebywan.daybook.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.platform.rememberSharer
import com.joebywan.daybook.puzzles.Pointers

/** Render-harness hook (`?share` on the web build): the share preview starts open. */
var sharePreviewOpen = false

/** Lexicon's own right/wrong-place colours (docs/COLOUR.md: the semantic Green and Gold). */
private val GREEN = Color(0xFF4E9F6C)
private val GOLD = Color(0xFFD1A32F)

/**
 * What Share shows before it sends: the graphic drawn natively (the web's font has no emoji, so the squares are shapes,
 * not glyphs), the clock, the key, a nudge toward an all-green result, and the Share button. A dialog, so it covers the
 * play screen without moving anything on it, in either layout.
 */
@Composable
fun SharePreview(card: ShareCard, onClose: () -> Unit) {
    val share = rememberSharer()
    var label by remember { mutableStateOf("Share") }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(card.header.removePrefix("Daybook · "), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val widest = card.lines.maxOfOrNull { it.marks.size + if (it.hinted) 2 else 0 } ?: 1
                    val tail = if (card.lines.any { it.tail.length > 2 }) 120.dp else 24.dp
                    val cell = minOf(24.dp, (maxWidth - tail) / widest.coerceAtLeast(1) - 4.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        card.lines.forEach { line ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                line.marks.forEach { MarkShape(it, cell) }
                                if (line.hinted) {
                                    Spacer(Modifier.width(cell / 2))
                                    MarkShape(Mark.RED, cell)
                                }
                                if (line.tail.isNotEmpty()) {
                                    Text(line.tail.trim(), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                                }
                            }
                        }
                    }
                }
                Text(card.stats, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    card.key.forEach { (mark, words) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MarkShape(mark, 12.dp)
                            Text(words, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(card.nudge, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = {
            TextButton(onClick = { share(card.text)?.let { label = it } }) { Text(label) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** One symbol at [size]: a rounded square, Tower's pip (filled, hollow, or a dot for nothing), or a Pointers heart. */
@Composable
private fun MarkShape(mark: Mark, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    Canvas(Modifier.size(size)) {
        val r = CornerRadius(this.size.minDimension * 0.18f)
        val pip = this.size.minDimension * 0.36f
        when (mark) {
            Mark.GREEN -> drawRoundRect(GREEN, cornerRadius = r)
            Mark.YELLOW -> drawRoundRect(GOLD, cornerRadius = r)
            Mark.RED -> drawRoundRect(scheme.error, cornerRadius = r)
            Mark.WHITE -> drawRoundRect(scheme.outline, cornerRadius = r, style = Stroke(this.size.minDimension * 0.1f))
            Mark.EXACT -> drawCircle(ink, pip)
            Mark.CLOSE -> drawCircle(ink, pip * 0.9f, style = Stroke(this.size.minDimension * 0.1f))
            Mark.NONE -> drawCircle(scheme.outline, this.size.minDimension * 0.08f)
            Mark.HEART -> drawPath(Pointers.heartPath(this.size.minDimension), scheme.error)
            Mark.LOST -> drawPath(Pointers.heartPath(this.size.minDimension), scheme.onSurfaceVariant)
        }
    }
}
