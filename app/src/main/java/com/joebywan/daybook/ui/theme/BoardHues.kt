package com.joebywan.daybook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

// The board content hues of docs/COLOUR.md: one HSL recipe, so any two hues sit at the same weight.
// Web-safe (no android.*, java.*). Derive colours from these; never store them.
object BoardHues {
    /** Hue in degrees, in table order. */
    val all = listOf(8f, 34f, 46f, 145f, 175f, 215f, 268f, 330f)

    /** Cell or region background; carries `onSurface` text. */
    fun fill(hue: Float, dark: Boolean) = if (dark) Color.hsl(hue, .40f, .30f) else Color.hsl(hue, .62f, .76f)

    /**
     * Text or a thin stroke on the surface: lightness .38 (light) or .64 (dark), stepped darker or lighter, a hundredth
     * at a time, until it reaches 4.5:1 against both the scheme's surface and its background. Amber, Gold, Green and Teal step
     * in light, Violet in dark; the rest keep the table's value.
     */
    fun ink(hue: Float, dark: Boolean): Color {
        val scheme = if (dark) DarkScheme else LightScheme
        val steps = if (dark) 64..100 else 38 downTo 0
        return steps.map { Color.hsl(hue, .55f, it / 100f) }
            .firstOrNull { contrast(it, scheme.surface) >= 4.5f && contrast(it, scheme.background) >= 4.5f }
            ?: if (dark) Color.White else Color.Black
    }

    /**
     * The deep step of a token palette (Sand's grains, Tower's pegs, Mosaic's and LITS's pieces, Sets' shapes): the fixed
     * recipe [ink] started from, lightness .38 (light) or .64 (dark). Those palettes are chosen and pinned by CIEDE2000
     * spacing, so they keep it; a token carries its own digit or shape, not text on the surface.
     */
    fun deep(hue: Float, dark: Boolean = false) = Color.hsl(hue, .55f, if (dark) .64f else .38f)

    /**
     * [colour] made readable as text on [background]: the same hue, mixed towards black on a light background or white on
     * a dark one, a fiftieth at a time, until it reaches [ratio] (4.5:1 for text, 3:1 for a dot or icon). A colour that
     * already reaches it is returned as it is. For accents drawn as text; fills and rings keep the raw accent.
     */
    fun textOn(colour: Color, background: Color, ratio: Float = 4.5f): Color {
        val toward = if (background.luminance() < 0.5f) Color.White else Color.Black
        for (step in 0..50) {
            val t = step / 50f
            val c = Color(
                colour.red + (toward.red - colour.red) * t,
                colour.green + (toward.green - colour.green) * t,
                colour.blue + (toward.blue - colour.blue) * t,
            )
            if (contrast(c, background) >= ratio) return c
        }
        return toward
    }

    /**
     * [accent] made readable on its own tint: the ground a solved tile, chip, archive cell or finish card paints, [accent]
     * at [alpha] over [ground]. 4.5:1 for text; pass 3f for a tick or a border. A raw accent reaches 3:1 on the plain
     * surface and background (`docs/COLOUR.md`), not on its tint, where it measures as little as 2:1.
     */
    fun onTint(accent: Color, ground: Color, ratio: Float = 4.5f, alpha: Float = 0.22f) =
        textOn(accent, accent.copy(alpha = alpha).compositeOver(ground), ratio)

    /** A solid shape (token, path, dot), either scheme. */
    fun mark(hue: Float) = Color.hsl(hue, .55f, .56f)

    /**
     * Text colour for a digit on [fill]: the theme's dark ink, else white, else black, the first with WCAG contrast of at
     * least 4.5:1 (else the best of the three). Computed per fill, never one colour for all (a mid-tone can fail both
     * ink and white). Draw it at full alpha: transparency lowers the ratio this guarantees.
     */
    fun onFill(fill: Color): Color = TEXT.firstOrNull { contrast(it, fill) >= 4.5f } ?: TEXT.maxBy { contrast(it, fill) }

    /** WCAG contrast ratio of two opaque colours. */
    fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + .05f) / (lo + .05f)
    }

    /** Whether [scheme] is the dark one; the `dark` argument of [fill] and [ink]. */
    fun isDark(scheme: ColorScheme) = scheme.background.luminance() < 0.5f

    /**
     * Two-hue pairs for boards, none using Coral or Green (right/wrong). Order is part of the contract: it fixes every
     * board's colours. Destructure as `(hueA, hueB)`; the roles are in docs/COLOUR.md: A is structure (signs, lines,
     * the selected thing), B is what the player placed and its echoes.
     */
    val HUE_PAIRS = listOf(215f to 34f, 175f to 330f, 268f to 46f, 34f to 215f, 330f to 175f, 46f to 268f)

    /** The pair for a board: [seed] is a hash of its solution or picture, never stored. */
    fun pair(seed: Int): Pair<Float, Float> = HUE_PAIRS[(seed and Int.MAX_VALUE) % HUE_PAIRS.size]

    private val TEXT = listOf(Color(0xFF1B2F29), Color.White, Color.Black)
}
