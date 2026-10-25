package com.joebywan.daybook.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.ui.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/**
 * The tier a sideways swipe on Home asks for, or null: a swipe left is the next harder tier, right the next easier, nothing
 * past either end and nothing in Levels (the level sets the tier). A swipe counts only once it has gone [threshold] sideways
 * and at least twice as far sideways as up or down, so a slightly diagonal scroll stays a scroll, and only if it began more
 * than [edge] in from either side, where Android's back gesture lives. Positions in pixels across a box [width] wide.
 */
internal fun swipedTier(
    startX: Float, dx: Float, dy: Float, width: Float, edge: Float, threshold: Float, mode: LaunchMode, tier: Difficulty,
): Difficulty? {
    if (mode == LaunchMode.LEVELS || startX < edge || startX > width - edge) return null
    if (abs(dx) < threshold || abs(dx) < 2 * abs(dy)) return null
    return Difficulty.entries.getOrNull(tier.ordinal + if (dx < 0) 1 else -1)
}

private val SwipeEdge = 28.dp
private val SwipeDistance = 56.dp

/**
 * Sideways swipes anywhere on Home change the tier ([swipedTier]). It watches on the Initial pass, ahead of the tiles and the
 * list, and consumes nothing until a move is plainly sideways (past the touch slop, twice as far across as down, before the
 * long-press timeout, and towards a tier that exists); from then on it consumes the gesture, so the tile under the finger
 * does not open and the list does not scroll. A move that goes vertical first is left to the list, and a finger held past
 * the long-press timeout is left to the tile drag. [onSwipe] fires once per gesture.
 */
internal fun Modifier.tierSwipe(mode: () -> LaunchMode, tier: () -> Difficulty, onSwipe: (Difficulty) -> Unit) =
    pointerInput(Unit) {
        val edge = SwipeEdge.toPx()
        val threshold = SwipeDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val longPress = viewConfiguration.longPressTimeoutMillis
            fun target(dx: Float, dy: Float) =
                swipedTier(down.position.x, dx, dy, size.width.toFloat(), edge, threshold, mode(), tier())
            var claimed = false
            var fired = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val d = change.position - down.position
                if (!claimed) {
                    if (change.uptimeMillis - down.uptimeMillis > longPress) break
                    if (abs(d.y) > slop && 2 * abs(d.y) > abs(d.x)) break
                    if (abs(d.x) <= slop || abs(d.x) < 2 * abs(d.y)) continue
                    if (target(sign(d.x) * threshold, 0f) == null) break
                    claimed = true
                }
                change.consume()
                if (!fired) target(d.x, d.y)?.let { fired = true; onSwipe(it) }
            }
        }
    }

/**
 * The new tier's name, in a solid pill over the grid for a moment after a swipe (it was once faint text, and went unseen); [flash] is the tier and a count, so the same tier
 * twice still shows. Drawn over everything and takes no touches, so it moves nothing. Under reduced motion it shows and
 * goes without the fade.
 */
@Composable
internal fun TierFlash(flash: Pair<Difficulty, Int>?, modifier: Modifier) {
    val still = LocalReducedMotion.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash == null) return@LaunchedEffect
        alpha.snapTo(FLASH_ALPHA)
        if (still) {
            delay((FLASH_HOLD_MS + FLASH_MS).toLong())
            alpha.snapTo(0f)
        } else {
            alpha.animateTo(0f, tween(FLASH_MS, delayMillis = FLASH_HOLD_MS))
        }
    }
    if (flash != null) Surface(
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
    ) {
        Text(
            flash.first.label,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        )
    }
}

private const val FLASH_ALPHA = 0.95f
private const val FLASH_HOLD_MS = 700
private const val FLASH_MS = 500
