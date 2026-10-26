package com.joebywan.daybook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// The board content hues of docs/COLOUR.md: one HSL recipe, so any two hues sit at the same weight.
// Web-safe (no android.*, java.*). Derive colours from these; never store them.
object BoardHues {
    /** Hue in degrees, in table order. */
    val all = listOf(8f, 34f, 46f, 145f, 175f, 215f, 268f, 330f)

    /** Cell or region background; carries `onSurface` text. */
    fun fill(hue: Float, dark: Boolean) = if (dark) Color.hsl(hue, .40f, .30f) else Color.hsl(hue, .62f, .76f)

    /** Text or a thin stroke on the surface. */
    fun ink(hue: Float, dark: Boolean) = Color.hsl(hue, .55f, if (dark) .64f else .38f)

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

    /**
     * Region hues: Violet, Amber, Teal, Blue and, only if a board needs five, Gold. docs/COLOUR.md's content hues less Coral
     * and Green (the boards show right and wrong) and Rose (too near the red of a broken rule).
     */
    val REGION_HUES = listOf(268f, 34f, 175f, 215f, 46f)

    /**
     * An index into [REGION_HUES] per region, no two neighbours alike ([adj] is the region graph): the fewest colours from
     * [minColours] up that a depth-first search finds, region r trying colour r first so a board spreads them rather than
     * leaning on the first. Derived, never stored.
     */
    fun regionColours(adj: List<BooleanArray>, minColours: Int): IntArray {
        val count = adj.size
        for (k in minColours..REGION_HUES.size) {
            val colour = IntArray(count) { -1 }
            var budget = 50_000
            fun go(r: Int): Boolean {
                if (r == count) return true
                if (--budget < 0) return false
                for (j in 0 until k) {
                    val c = (r + j) % k
                    if ((0 until count).any { adj[r][it] && colour[it] == c }) continue
                    colour[r] = c
                    if (go(r + 1)) return true
                }
                colour[r] = -1
                return false
            }
            if (go(0)) return colour
        }
        return IntArray(count) { it % REGION_HUES.size } // not reached on a planar layout; the region outlines still show
    }

    private val TEXT = listOf(Color(0xFF1B2F29), Color.White, Color.Black)
}
