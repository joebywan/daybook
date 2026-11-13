package com.joebywan.daybook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// The Arcade look's shared colours: boards take them from [ArcadeBoard.current], Home's tiles from [ArcadeBoard.tile].
// Nothing here is stored. Every pair is measured in ArcadeBoardTest, in both schemes. Web-safe: no android.*, no java.*.
// In the Classic look ([classicLook]) [ArcadeBoard.of] hands out [ArcadeBoard.ClassicLight] / [ArcadeBoard.ClassicDark], flat
// fills in the old schemes' own colours; the glow, the hard shadow and the page gradient draw nothing, and Home's tiles are flat.

/** A two-stop diagonal fill and the [ink] that reads on all of it. [glow] is for [arcadeGlow]; null means none in this scheme. */
class ArcadeFill(val from: Color, val to: Color, val ink: Color, val glow: Color? = null) {
    /** For `Modifier.background`: spans whatever shape it fills. */
    val brush: Brush get() = Brush.linearGradient(listOf(from, to))

    /** For a draw lambda: a gradient's origin is the canvas's, not the rect's, so a square needs its own corners. */
    fun brush(topLeft: Offset, size: Size): Brush =
        Brush.linearGradient(listOf(from, to), topLeft, Offset(topLeft.x + size.width, topLeft.y + size.height))
}

class ArcadeBoard private constructor(
    /** Grid lines and the board's outer frame. */
    val frame: Color,
    /** An empty or player's square, with the text on it. */
    val square: Color,
    val squareInk: Color,
    /** A given square, with its digit or letter. */
    val given: Color,
    val givenInk: Color,
    /** The selected square. Amber on a white square is under 3:1, so outline it with [selectedEdge] too. */
    val selected: ArcadeFill,
    val selectedEdge: Color,
    /** A wrong square: draw it with a weight or mark change as well, never the colour alone. */
    val error: Color,
    val errorInk: Color,
    /** A toggled-on control: pen, tab, segmented pill. */
    val control: ArcadeFill,
    /** A letter tile (Lexicon, Word Wheel). */
    val letter: ArcadeFill,
    /**
     * The hint's glow round what it names, and the hint card's edge: Sudoku's magenta in light (the page's indigo ink read as a
     * plain outline there), amber in dark. Classic's is its ink, but Classic's hint draws from its scheme, as before Arcade.
     */
    val hint: Color = squareInk,
) {
    companion object {
        private val AmberLight = ArcadeFill(Color(0xFFFFD23F), Color(0xFFFF9E1B), Color(0xFF311400))
        private val AmberDark = ArcadeFill(Color(0xFFFFD84D), Color(0xFFFF8A00), Color(0xFF311400), Color(0xFFFFB300))

        val Light = ArcadeBoard(
            frame = Color(0xFF21145F),
            square = Color.White,
            squareInk = Color(0xFF21145F),
            given = Color(0xFFE7DFFF),
            givenInk = Color(0xFF3A1FB8),
            selected = AmberLight,
            selectedEdge = Color(0xFF21145F),
            error = Color(0xFFC4122F),
            errorInk = Color.White,
            control = ArcadeFill(Color(0xFF0077C8), Color(0xFF5B3DF5), Color.White),
            letter = AmberLight,
            hint = Color(0xFFE6007E),
        )

        val Dark = ArcadeBoard(
            frame = Color(0x33FFFFFF),
            square = Color(0xFF1B1450),
            squareInk = Color.White,
            given = Color(0xFF33287F),
            givenInk = Color(0xFF9FE9FF),
            selected = AmberDark,
            selectedEdge = Color.White,
            error = Color(0xFFFF5C7A),
            errorInk = Color(0xFF2B0010),
            // The mockup's 7A5CFF misses 4.5:1 with this ink, and white misses on the cyan, so the violet is a step lighter.
            control = ArcadeFill(Color(0xFF00E5FF), Color(0xFF9483FF), Color(0xFF120D33), Color(0xFF00E5FF)),
            letter = AmberDark,
            hint = AmberDark.from,
        )

        // Classic: flat, in the old schemes' colours (ClassicLightScheme / ClassicDarkScheme). The boards that Arcade restyled
        // branch on [classicLook] and draw what they drew before; these are for what is shared (pens, segmented pills, keys).
        val ClassicLight = ArcadeBoard(
            frame = Color(0xFFB9AE99),
            square = Color(0xFFFFFBF2),
            squareInk = Color(0xFF11201C),
            given = Color(0xFFE8E0D0),
            givenInk = Color(0xFF11201C),
            // The old secondary, B06C1E, a step deeper: white on it was 4.2:1, under the 4.5 its label needs.
            selected = ArcadeFill(Color(0xFFA6631A), Color(0xFFA6631A), Color.White),
            selectedEdge = Color(0xFF11201C),
            error = Color(0xFFAD4D39),
            errorInk = Color.White,
            control = ArcadeFill(Color(0xFF1F6F5C), Color(0xFF1F6F5C), Color.White),
            letter = ArcadeFill(Color(0xFFE8E0D0), Color(0xFFE8E0D0), Color(0xFF11201C)),
        )

        val ClassicDark = ArcadeBoard(
            frame = Color(0xFF3C5B53),
            square = Color(0xFF1B2F29),
            squareInk = Color(0xFFF6F1E7),
            given = Color(0xFF24403A),
            givenInk = Color(0xFFF6F1E7),
            selected = ArcadeFill(Color(0xFFD08A33), Color(0xFFD08A33), Color(0xFF11201C)),
            selectedEdge = Color(0xFFF6F1E7),
            error = Color(0xFFD08271),
            errorInk = Color(0xFF11201C),
            control = ArcadeFill(Color(0xFF3F9E7F), Color(0xFF3F9E7F), Color(0xFF11201C)),
            letter = ArcadeFill(Color(0xFF24403A), Color(0xFF24403A), Color(0xFFF6F1E7)),
        )

        fun of(scheme: ColorScheme) = of(BoardHues.isDark(scheme))

        /** The token set of the look showing, light or [dark]. */
        fun of(dark: Boolean) = when {
            classicLook() -> if (dark) ClassicDark else ClassicLight
            else -> if (dark) Dark else Light
        }

        val current: ArcadeBoard
            @Composable @ReadOnlyComposable get() = of(MaterialTheme.colorScheme)

        private val tiles = HashMap<Pair<Color, Boolean>, ArcadeFill>()

        /**
         * A home tile from its puzzle's accent: the accent's hue, saturated, running to a second hue. Blues, violets and pinks
         * (and warm or teal hues in light that can take it) are deep enough for white; what would turn to mud that deep is bright
         * instead, under a dark ink. An accent's own saturation and luminance decide how far it runs, so neighbouring accents
         * do not land on one tile. Classic's tile is the flat surface under its ink ([classicTile]).
         */
        fun tile(accent: Color, dark: Boolean): ArcadeFill = if (classicLook()) classicTile(dark) else arcadeTile(accent, dark)

        /** [tile] in the Arcade look, whichever look is showing (for a swatch of it). */
        fun arcadeTile(accent: Color, dark: Boolean): ArcadeFill = tiles.getOrPut(accent to dark) {
            val h = hueOf(accent)
            val s = saturationOf(accent)
            val spread = ((accent.luminance() - .18f) / .075f).coerceIn(0f, 1f) // where it sat in the accents' luminance band
            val bright = h in 38f..115f || (dark && (h in 20f..38f || h in 150f..205f))
            val run = .7f + (1f - s) // a muted accent runs further, so it stays apart from the saturated one beside it
            val sat = .86f + .14f * s
            val glow = if (dark) Color.White.copy(alpha = .25f) else null
            if (bright) {
                val end = h + run * if (h < 115f) -22f else 14f
                ArcadeFill(
                    Color.hsl(wrap(h), sat, lightnessFor(h, sat, .46f + .14f * spread)),
                    Color.hsl(wrap(end), 1f, lightnessFor(end, 1f, .30f + .08f * spread)),
                    if (dark) Color(0xFF120D33) else Color(0xFF311400),
                    glow,
                )
            } else {
                val start = if (h in 20f..38f) h - 10f else h
                val end = start + run * when {
                    h < 115f -> -14f
                    h < 250f -> 32f
                    else -> -32f
                }
                ArcadeFill(
                    Color.hsl(wrap(start), sat, lightnessFor(start, sat, WHITE_LIMIT - .035f * (1f - spread))),
                    Color.hsl(wrap(end), 1f, lightnessFor(end, 1f, WHITE_LIMIT - .05f * spread)),
                    Color.White,
                    glow,
                )
            }
        }

        /** Classic's home tile: the plain surface, the old schemes' ink; a solved one is tinted by its caller. */
        fun classicTile(dark: Boolean): ArcadeFill {
            val s = lookScheme(dark, classic = true)
            return ArcadeFill(s.surface, s.surface, s.onSurface)
        }

        /** The most relative luminance white text can sit on at 4.5:1 is .183; this leaves a margin. */
        private const val WHITE_LIMIT = .176f

        private fun wrap(h: Float) = (h + 360f) % 360f

        /** The largest HSL lightness up to .7 at which ([hue], [saturation]) stays at or under [luminance]; luminance rises with it. */
        private fun lightnessFor(hue: Float, saturation: Float, luminance: Float): Float =
            (70 downTo 5).first { Color.hsl(wrap(hue), saturation, it / 100f).luminance() <= luminance } / 100f

        private fun saturationOf(c: Color): Float {
            val hi = maxOf(c.red, c.green, c.blue)
            val lo = minOf(c.red, c.green, c.blue)
            return if (hi == lo) 0f else (hi - lo) / (1f - abs(hi + lo - 1f))
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
    }
}

/** A glow without a blur, for a canvas: stacked rings round a rounded rect. A translucent [color] sets its own strength; a solid one glows at half. */
fun DrawScope.arcadeGlow(color: Color, topLeft: Offset, size: Size, corner: Float, reach: Dp = 6.dp) {
    if (classicLook()) return // Classic has no glow.
    val full = if (color.alpha < 1f) color.alpha else .5f
    for ((share, part) in listOf(1f to .2f, .66f to .3f, .33f to .5f)) {
        val g = reach.toPx() * share
        drawRoundRect(color.copy(alpha = full * part), Offset(topLeft.x - g, topLeft.y - g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(corner + g))
    }
}

/** [arcadeGlow] round a composable; put it before the `clip`, or the clip takes it off. */
fun Modifier.arcadeGlow(color: Color, corner: Dp, reach: Dp = 6.dp) =
    drawBehind { arcadeGlow(color, Offset.Zero, size, corner.toPx(), reach) }

/** Light's raised sticker edge, straight down with no blur; before the `clip`. */
fun Modifier.arcadeHardShadow(corner: Dp, drop: Dp = 3.dp) =
    drawBehind { if (!classicLook()) drawRoundRect(HardShadow, Offset(0f, drop.toPx()), size, CornerRadius(corner.toPx())) }

/** What lifts [fill] off the page: its glow in dark, the hard shadow in light. */
fun Modifier.arcadeLift(fill: ArcadeFill, corner: Dp) =
    fill.glow?.let { arcadeGlow(it, corner) } ?: arcadeHardShadow(corner)

private val HardShadow = Color(0x4021145F)

/**
 * The page's gradient at 160 degrees, in pixels from the window's top left; a sticky row paints it too, by the same numbers.
 * Classic's page is flat, its scheme's background.
 */
fun arcadePageBrush(dark: Boolean, window: Size): Brush {
    if (classicLook()) return SolidColor(lookScheme(dark).background)
    val angle = 160f * PI.toFloat() / 180f
    val dx = sin(angle)
    val dy = -cos(angle)
    val half = (abs(window.width * dx) + abs(window.height * dy)) / 2f
    val mid = Offset(window.width / 2f, window.height / 2f)
    val stops = if (dark) listOf(Color(0xFF241A6B), Color(0xFF120D33)) else listOf(Color(0xFFF3EEFF), Color(0xFFFFF1F7))
    return Brush.linearGradient(stops, mid - Offset(dx * half, dy * half), mid + Offset(dx * half, dy * half))
}

/** The page behind a whole screen. */
fun Modifier.arcadePage(dark: Boolean) = drawWithCache {
    val brush = arcadePageBrush(dark, size)
    onDrawBehind { drawRect(brush) }
}
