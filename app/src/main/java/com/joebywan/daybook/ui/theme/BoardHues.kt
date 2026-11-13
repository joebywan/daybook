package com.joebywan.daybook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

// A global dark flag, so `vivid(accent)` works in draw lambdas as well as compositions; DaybookTheme sets it.
private var vividIsDark by mutableStateOf(false)
private val vividCache = HashMap<Long, Color>()

internal fun useVividScheme(dark: Boolean) { if (vividIsDark != dark) vividIsDark = dark }

private val classicCache = HashMap<Long, Color>()

/**
 * The Classic look's accent (issue #418): the raw accent, as before Arcade, wherever it reaches 3:1 on the old scheme's surface
 * and background, which every accent from before Arcade does. One chosen since (Dwindle's, 2.8:1 on the old dark surface) is
 * mixed towards white in dark (black in light), a fiftieth at a time, until it does.
 */
fun classicAccent(accent: Long, dark: Boolean): Color {
    val s = lookScheme(dark, classic = true)
    val toward = if (dark) Color.White else Color.Black
    for (step in 0..50) {
        val c = androidx.compose.ui.graphics.lerp(Color(accent), toward, step / 50f)
        if (BoardHues.contrast(c, s.surface) >= 3f && BoardHues.contrast(c, s.background) >= 3f) return c
    }
    return toward
}

/**
 * A puzzle's accent in the scheme now showing: Arcade's pair for it ([arcadeAccent]), or in the Classic look [classicAccent].
 * Boards keep passing their raw `accent`; it is the key.
 */
fun vivid(accent: Long): Color {
    val dark = vividIsDark
    if (classicLook()) return classicCache.getOrPut(if (dark) accent or (1L shl 40) else accent) { classicAccent(accent, dark) }
    return vividCache.getOrPut(if (dark) accent or (1L shl 40) else accent) { arcadeAccent(accent, dark) }
}

/** Arcade's accent for the raw [accent] in light or [dark]: its pair from [ARCADE_ACCENTS], or [vividFor] for one not in it. */
fun arcadeAccent(accent: Long, dark: Boolean): Color =
    ARCADE_ACCENTS[accent]?.let { (light, night) -> Color(if (dark) night else light) } ?: vividFor(accent, dark)

/**
 * Arcade's accents, a light and a dark per raw accent (docs/COLOUR.md, Arcade's drawn accents), solved as a set per scheme:
 * from [vividFor]'s colour, each moved within 24 degrees of its hue, saturation .45 to 1 and lightness, to keep 3:1 on the
 * scheme's surface and background (no more than 5.5:1 on light's surface, 7.5:1 on dark's background: vivid, not mud or
 * pastel), out of the reserved hues and well clear of the error red and the success green, spreading the closest pair
 * (CIEDE2000, normal or twice the worst colour-blind vision) by local search with random kicks. Closest pair: light 4.56,
 * dark 8.14, against [vividFor]'s 2.34 / 1.94. `ArcadeAccentTest` pins it. A new puzzle shares an accent (the set is full).
 */
val ARCADE_ACCENTS: Map<Long, Pair<Long, Long>> = mapOf(
    0xFF6F7DF7 to (0xFF3D51FF to 0xFF4065DD),
    0xFF94669F to (0xFF9D1BF3 to 0xFFBA00E6),
    0xFF8C79E0 to (0xFF7A5FF7 to 0xFF858BD1),
    0xFF1C7EAB to (0xFF0870A0 to 0xFF72A8CA),
    0xFF8D813D to (0xFFB86D05 to 0xFFC18A5C),
    0xFFBC4C9C to (0xFFAE4281 to 0xFFCD7AA4),
    0xFF997EB8 to (0xFF9E6FFB to 0xFFBD91E3),
    0xFFB2659A to (0xFFDB1FA1 to 0xFFC0598B),
    0xFFCA4383 to (0xFFC86F99 to 0xFFDA52C0),
    0xFF7A9052 to (0xFF7F9504 to 0xFF5FA008),
    0xFFAF7D4C to (0xFFC27C05 to 0xFFAA9841),
    0xFF278E8E to (0xFF0A7B7B to 0xFF20B6B6),
    0xFFA258C1 to (0xFFC200D6 to 0xFFD48CCC),
    0xFF1E76D8 to (0xFF6478C4 to 0xFF419DAA),
    0xFFBE7C1C to (0xFFAE7442 to 0xFF896C34),
    0xFFBE6B0B to (0xFFA67830 to 0xFF7E6E07),
    0xFF5C72BE to (0xFF4491C1 to 0xFF1FA9FF),
    0xFF528DC9 to (0xFF2689ED to 0xFF648FF7),
    0xFFB47699 to (0xFFF20D8E to 0xFFFF61B9),
    0xFFA879AA to (0xFFC059B6 to 0xFFB051FB),
    0xFF2891AC to (0xFF0988AA to 0xFF089189),
    0xFFA26482 to (0xFFCB1077 to 0xFFD81881),
    0xFF8A8C34 to (0xFF8E7F0B to 0xFF778112),
    0xFFB96BCB to (0xFFB526AA to 0xFFFF4CFD),
    0xFF8F5DD6 to (0xFFB76CDA to 0xFF8342FF),
    0xFFD45C91 to (0xFFE156A6 to 0xFFFF0F9A),
    0xFF737B39 to (0xFF6E8934 to 0xFF96AF2C),
    0xFF0F9B89 to (0xFF0D9C89 to 0xFF297A75),
    0xFF967028 to (0xFFAA8541 to 0xFFAA8541),
    0xFF846CAD to (0xFF9551BD to 0xFF9375D7),
    0xFF098777 to (0xFF197669 to 0xFF1D80A5),
    0xFF96754A to (0xFFA46C23 to 0xFFA6733F),
    0xFFA55C09 to (0xFF9A8C2D to 0xFFD17D00),
    0xFFD062BA to (0xFFEE2FC8 to 0xFFAF51BD),
    0xFF9F568E to (0xFFC44F82 to 0xFFC2299E),
    0xFF7B6F3D to (0xFFAE6004 to 0xFFC89D5B),
)

/**
 * The accent's hue, its saturation raised to .85 give or take how saturated the accent was, its lightness stepped until
 * it reaches 3:1 on [dark]'s surface and background, then moved on past that by where the accent sat in the set's luminance
 * band. Without the spread the set collapses: same-hue pairs measured 0.00 apart ([flatVivid], `ArcadeReportTest`).
 */
fun vividFor(accent: Long, dark: Boolean): Color {
    val c = Color(accent)
    val saturation = (.85f + (saturationOf(c) - .55f) * .5f).coerceIn(.6f, 1f)
    val base = lightnessAt3(hueOf(c), saturation, dark)
    val spread = (if (dark) c.luminance() - .18f else .255f - c.luminance()) * 1.6f
    return Color.hsl(hueOf(c), saturation, (if (dark) base + spread else base - spread).coerceIn(.05f, .95f))
}

/** The brief's derivation, for the report: every accent at .85 and exactly the first lightness that reaches 3:1. */
fun flatVivid(accent: Long, dark: Boolean) = Color.hsl(hueOf(Color(accent)), .85f, lightnessAt3(hueOf(Color(accent)), .85f, dark))

private fun lightnessAt3(hue: Float, saturation: Float, dark: Boolean): Float {
    val scheme = if (dark) DarkScheme else LightScheme
    val steps = if (dark) 48..95 else 56 downTo 5
    return steps.first {
        val c = Color.hsl(hue, saturation, it / 100f)
        BoardHues.contrast(c, scheme.surface) >= 3f && BoardHues.contrast(c, scheme.background) >= 3f
    } / 100f
}

private fun saturationOf(c: Color): Float {
    val hi = maxOf(c.red, c.green, c.blue)
    val lo = minOf(c.red, c.green, c.blue)
    val l = (hi + lo) / 2f
    return if (hi == lo) 0f else (hi - lo) / (1f - kotlin.math.abs(2f * l - 1f))
}

private fun hueOf(c: Color): Float {
    val hi = maxOf(c.red, c.green, c.blue)
    val d = hi - minOf(c.red, c.green, c.blue)
    if (d == 0f) return 0f
    val h = when (hi) {
        c.red -> (c.green - c.blue) / d
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    }
    return (h * 60f + 360f) % 360f
}

// The board content hues of docs/COLOUR.md: one HSL recipe, so any two hues sit at the same weight.
// Web-safe (no android.*, java.*). Derive colours from these; never store them.
object BoardHues {
    /** Hue in degrees, in table order. */
    val all = listOf(8f, 34f, 46f, 145f, 175f, 215f, 268f, 330f)

    /** Cell or region background; carries `onSurface` text. Classic keeps the old recipe, [pinnedFill]. */
    fun fill(hue: Float, dark: Boolean): Color {
        if (classicLook()) return pinnedFill(hue, dark)
        // Green and Teal at .85 are fluorescent at this lightness (Regions read as highlighter), so they stop at .65.
        if (!dark) return Color.hsl(hue, if (hue in 130f..190f) .65f else .85f, .76f)
        // hsl(h, .62, .34), a hundredth darker at a time where onSurface would miss 4.5:1 on it (Gold, Green, Teal).
        var l = 34
        while (l > 20 && contrast(DarkScheme.onSurface, Color.hsl(hue, .62f, l / 100f)) < 4.5f) l--
        return Color.hsl(hue, .62f, l / 100f)
    }

    /**
     * Text or a thin stroke on the surface: lightness .38 (light) or .64 (dark), stepped darker or lighter, a hundredth
     * at a time, until it reaches 4.5:1 against both the scheme's surface and its background. Amber, Gold, Green and Teal step
     * in light, Violet in dark; the rest keep the table's value. Saturation .8 in Arcade, .55 in Classic (the old recipe).
     */
    fun ink(hue: Float, dark: Boolean, classic: Boolean = classicLook()): Color {
        val scheme = lookScheme(dark, classic)
        val saturation = if (classic) .55f else .8f
        val steps = if (dark) 64..100 else 38 downTo 0
        return steps.map { Color.hsl(hue, saturation, it / 100f) }
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

    /** A solid shape (token, path, dot), either scheme. Classic keeps the old recipe, [pinnedMark]. */
    fun mark(hue: Float) = if (classicLook()) pinnedMark(hue) else Color.hsl(hue, .8f, .56f)

    // The old recipes, for the token palettes pinned by CIEDE2000 (Sand, Tower, Lits, Mosaic), left as they ship.
    fun pinnedFill(hue: Float, dark: Boolean) = if (dark) Color.hsl(hue, .40f, .30f) else Color.hsl(hue, .62f, .76f)
    fun pinnedMark(hue: Float) = Color.hsl(hue, .55f, .56f)

    /** The selected square's ring, because amber on white alone is 1.4:1: [ink]'s amber, 4.5:1 on the surface. */
    fun selectedRing(dark: Boolean) = if (dark) Color.White else ink(34f, false)

    /** The selected square: warm amber in both schemes, a digit on it through [onFill]. */
    fun selected(dark: Boolean) = if (dark) Color.hsl(42f, .95f, .58f) else Color.hsl(46f, 1f, .62f)

    /**
     * Text colour for a digit on [fill]: the theme's dark ink, else white, else black, the first with WCAG contrast of at
     * least 4.5:1 (else the best of the three). Computed per fill, never one colour for all (a mid-tone can fail both
     * ink and white). Draw it at full alpha: transparency lowers the ratio this guarantees.
     */
    fun onFill(fill: Color): Color {
        val text = if (classicLook()) CLASSIC_TEXT else TEXT
        return text.firstOrNull { contrast(it, fill) >= 4.5f } ?: text.maxBy { contrast(it, fill) }
    }

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

    private val TEXT = listOf(Color(0xFF21145F), Color.White, Color.Black)
    private val CLASSIC_TEXT = listOf(Color(0xFF1B2F29), Color.White, Color.Black)
}
