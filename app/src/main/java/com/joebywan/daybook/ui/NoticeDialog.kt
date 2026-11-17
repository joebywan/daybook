package com.joebywan.daybook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Notice

/**
 * The developer's notice (`core/Notice.kt`), over whatever screen is showing. Either button, back or a tap outside
 * dismisses it for good; feedback also opens the player's mail app.
 */
@Composable
fun NoticeDialog(message: Notice.Message, onFeedback: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DeveloperAvatar(72.dp) },
        title = { Text(message.title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                // Shown as well as linked: with no mail app the button does nothing, and the address can be copied here.
                SelectionContainer {
                    Text(Notice.FEEDBACK_EMAIL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Got it") } },
        dismissButton = { TextButton(onClick = { onFeedback(); onDismiss() }) { Text("Send feedback") } },
    )
}

/**
 * The developer, drawn: flat shapes on a 200-unit square, clipped to a circle. Fixed colours, the same in both
 * themes, since it is a portrait on its own amber disc. Rendered against a photo before it went in.
 */
@Composable
fun DeveloperAvatar(size: Dp, modifier: Modifier = Modifier) {
    val fills = remember { AVATAR_FILLS.map { (d, c) -> PathParser().parsePathString(d).toPath() to Color(c) } }
    val strokes = remember { AVATAR_STROKES.map { (d, c, w) -> Triple(PathParser().parsePathString(d).toPath(), Color(c), w) } }
    Canvas(modifier.size(size).clip(CircleShape).semantics { contentDescription = "The developer" }) {
        scale(this.size.minDimension / 200f, pivot = Offset.Zero) {
            drawRect(Color(0xFFFFB300), size = Size(200f, 200f))
            fills.forEachIndexed { i, (path, colour) ->
                drawPath(path, colour)
                if (i == EARS_AFTER) {
                    drawOval(SKIN_EAR, Offset(36f, 92f), Size(18f, 24f))
                    drawOval(SKIN_EAR, Offset(146f, 92f), Size(18f, 24f))
                }
            }
            strokes.forEach { (path, colour, width) -> drawPath(path, colour, style = Stroke(width, cap = StrokeCap.Round)) }
            drawOval(EYE, Offset(77.2f, 93.4f), Size(7.6f, 7.2f))
            drawOval(EYE, Offset(115.2f, 93.4f), Size(7.6f, 7.2f))
            lids.forEach { drawPath(it, Color(0xFFC48A70), style = Stroke(2.2f, cap = StrokeCap.Round)) }
            val frame = Stroke(2.2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
            drawRoundRect(GLASSES, Offset(63f, 88f), Size(34f, 17f), CornerRadius(2.5f), style = frame)
            drawRoundRect(GLASSES, Offset(103f, 88f), Size(34f, 17f), CornerRadius(2.5f), style = frame)
            glassesParts.forEach { drawPath(it, GLASSES, style = frame) }
        }
    }
}

private val GLASSES = Color(0xFF151515)
private val SKIN_EAR = Color(0xFFE3A98C)
private val EYE = Color(0xFF2F3328)
private val lids = listOf("M74 94.5 Q81 91.5 88 94.5", "M112 94.5 Q119 91.5 126 94.5").map { PathParser().parsePathString(it).toPath() }
private val glassesParts = listOf("M97 92 L103 92", "M63 91 L48 95", "M137 91 L152 95").map { PathParser().parsePathString(it).toPath() }

/** Index in [AVATAR_FILLS] after which the ears go: behind the head, in front of the tee. */
private const val EARS_AFTER = 1

/** Back to front: the tee and its neckline, then the head, hair, beard and its grey, moustache, lips and nose. */
private val AVATAR_FILLS: List<Pair<String, Long>> = listOf(
    "M-4 200 C0 164 42 148 100 148 C158 148 200 164 204 200 Z" to 0xFF1F1E24,
    "M70 152 C80 168 120 168 130 152 C122 160 78 160 70 152 Z" to 0xFF2E2D35,
    "M46 96 C46 62 68 42 100 42 C132 42 154 62 154 96 C154 132 132 154 100 154 C68 154 46 132 46 96 Z" to 0xFFEEBC9E,
    "M46 94 C44 60 66 38 100 38 C134 38 156 60 154 94 C152 82 149 74 145 68 C140 64 134 62 126 61 C112 59 88 59 74 61 C66 62 60 64 55 68 C51 74 48 82 46 94 Z" to 0xFF3A2C25,
    "M46 98 C44 142 68 178 100 180 C132 178 156 142 154 98 C152 110 147 118 140 122 C130 116 116 114 100 116 C84 114 70 116 60 122 C53 118 48 110 46 98 Z" to 0xFF3B2F29,
    "M74 140 C76 162 90 178 100 179 C110 178 124 162 126 140 C116 150 84 150 74 140 Z" to 0xFF6E6762,
    "M84 144 C86 162 94 174 100 175 C106 174 114 162 116 144 C108 151 92 151 84 144 Z" to 0xFF8F8984,
    "M92 148 C94 162 98 168 100 169 C102 168 106 162 108 148 C104 152 96 152 92 148 Z" to 0xFFA9A49F,
    "M72 130 C80 117 120 117 128 130 C121 127 112 124 100 124 C88 124 79 127 72 130 Z" to 0xFF4A3D36,
    "M72 130 C74 125 78 122 82 121 C80 125 79 128 80 132 Z" to 0xFF8E8780,
    "M128 130 C126 125 122 122 118 121 C120 125 121 128 120 132 Z" to 0xFF8E8780,
    "M89 133 C95 131.5 105 131.5 111 133 C105 135.5 95 135.5 89 133 Z" to 0xFFB57B6E,
    "M92 111 C94 100 106 100 108 111 C106 117 94 117 92 111 Z" to 0xFFE0A285,
)

/** The brows. */
private val AVATAR_STROKES: List<Triple<String, Long, Float>> = listOf(
    Triple("M68 84 Q80 81 92 84", 0xFF3A2C25, 4f),
    Triple("M108 84 Q120 81 132 84", 0xFF3A2C25, 4f),
)
