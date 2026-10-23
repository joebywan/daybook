package com.joebywan.daybook.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The system's "reduce motion" (Android: Remove animations; the web: `prefers-reduced-motion`), read once at
 * the root by `rememberReducedMotion` in the platform seam. While it is on, decoration that moves (confetti,
 * the sparkles, a slide, a shake, the hint glow's breathing) is drawn still or skipped; what carries meaning
 * (a bump's red flash, a lifted tube, the glow itself) stays, as an instant change.
 */
val LocalReducedMotion = compositionLocalOf { false }

/** The three buzzes a game gives, from `rememberHaptics` in the platform seam. Each does nothing when vibration is off. */
class Haptics(val tick: () -> Unit, val bump: () -> Unit, val success: () -> Unit) {
    companion object {
        val None = Haptics({}, {}, {})
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics.None }

/**
 * The hint glow's alpha: breathing between [low] and [high] while [glowing], steady at 1 otherwise. Under
 * reduced motion it holds at [high], so the glow still shows. Read it in the draw phase, so the board is not
 * recomposed every frame.
 */
@Composable
fun rememberHintPulse(glowing: Boolean, low: Float = 0.45f, high: Float = 1f): State<Float> =
    when {
        !glowing -> remember { mutableFloatStateOf(1f) }
        LocalReducedMotion.current -> remember(high) { mutableFloatStateOf(high) }
        else -> rememberInfiniteTransition(label = "hint").animateFloat(
            initialValue = low,
            targetValue = high,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "hint-pulse",
        )
    }
