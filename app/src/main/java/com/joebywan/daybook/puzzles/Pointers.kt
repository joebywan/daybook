package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pointers: the board is covered in arrows. Tap one and it slides off the way its head points, if nothing is in its
 * way to the edge; if something is, it bumps and a life goes. Clear them all; lose three lives and the board is lost.
 * Rules and generator: [PointersLogic]. Highlight indices (the walkthrough's only) are arrow indices.
 */
object Pointers : PuzzleType {

    override val id = "pointers"
    override val displayName = "Pointers"
    override val tagline = "Clear every arrow, without a bump"
    override val accent = 0xFFC95E77
    override val rules = listOf(
        "Tap an arrow and it slides off the board the way its head points, if nothing is in its way to the edge.",
        "If another arrow is in the way, it bumps and you lose a life. Lose all three and the board is lost: try it again from the start.",
        "Clear every arrow to win. Taking one away only ever opens the way for others, so look before you tap.",
    )

    override val keyboardHelp = listOf("Arrow keys move between arrows; Enter or Space taps the outlined one.")

    /**
     * No hints, on purpose (the owner's call). Every arrow whose way is clear can be seen to be free, and taking it
     * can never hurt (removal only opens lines), so a hint could only ever say "this one is free": nothing to
     * teach. The challenge is care, not deduction; the walkthrough teaches the one idea (clear the blocker first).
     */
    override val offersHints = false

    /**
     * No undo, on purpose (the owner's call). A cleared arrow never needs to come back, since removing it only ever
     * frees others, so undo has no use here except to hand back a life after a bump. A lost board offers Try again.
     */
    override val offersUndo = false

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = PointersLogic.generate(seed, difficulty)

    /** The board when it covered the grid to the tier's density and cleared, null otherwise. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): PointersState? =
        PointersLogic.generateVerified(seed, difficulty)

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * A 4x4 board, every square covered: [TOP] (top row, pointing left: free), [ROW] (second row, pointing right:
     * [SIDE] is in its way), [SIDE] (the right-hand column, pointing down: free), [HOOK] (bent along the bottom,
     * pointing right: [SIDE] in its way) and [SHORT] (pointing left: [HOOK] in its way).
     */
    internal val TUTORIAL_ARROWS = listOf(
        listOf(2, 1, 0),
        listOf(4, 5, 6),
        listOf(3, 7, 11, 15),
        listOf(8, 12, 13, 14),
        listOf(10, 9),
    )
    internal const val TOP = 0
    internal const val ROW = 1
    internal const val SIDE = 2
    internal const val HOOK = 3
    internal const val SHORT = 4

    internal fun tutorialBoard() = PointersState(4, 4, TUTORIAL_ARROWS)

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.tap(TUTORIAL_ARROWS[TOP][0])!!
        val s2 = s1.tap(TUTORIAL_ARROWS[ROW][0])!!
        val s3 = s2.tap(TUTORIAL_ARROWS[SIDE][0])!!
        val s4 = s3.tap(TUTORIAL_ARROWS[ROW][0])!!
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Every arrow slides off the board the way its head points, but only if nothing is in its way to the edge.",
                highlight = BoardHighlight(soft = TUTORIAL_ARROWS.indices.toSet()),
            ),
            TutorialFrame(
                state = s0,
                caption = "The top arrow points left, and nothing lies between its head and the edge. Tap it.",
                highlight = BoardHighlight(strong = setOf(TOP)),
                accepts = { it is PointersState && it.removed == listOf(TOP) && it.bumps.isEmpty() },
                retry = "Tap the glowing arrow along the top.",
                done = "Gone. A clear way out means it can always go.",
            ),
            TutorialFrame(
                state = s1,
                caption = "The arrow below points right, into the long one down the side. Tap it anyway, to see.",
                highlight = BoardHighlight(strong = setOf(ROW), soft = setOf(SIDE)),
                accepts = { it is PointersState && it.removed == listOf(TOP) && it.bumps == listOf(ROW) },
                retry = "Tap the glowing arrow in the second row.",
                done = "Bump. That cost a life: lose all three and the board is lost.",
            ),
            TutorialFrame(
                state = s2,
                caption = "Clear what is in the way first: tap the side arrow, whose way down is clear, then the second-row arrow again.",
                highlight = BoardHighlight(strong = setOf(SIDE)),
                steps = { next ->
                    when ((next as? PointersState)?.takeIf { it.bumps == listOf(ROW) }?.removed) {
                        listOf(TOP, SIDE) -> TutorialStep.PROGRESS
                        listOf(TOP, SIDE, ROW) -> TutorialStep.DONE
                        else -> TutorialStep.REJECT
                    }
                },
                glow = { now -> BoardHighlight(strong = setOf(if (SIDE in (now as PointersState).removed) ROW else SIDE)) },
                retry = "Tap the glowing arrow.",
                done = "Taking an arrow away only ever opens the way for others.",
            ),
            TutorialFrame(
                state = s4,
                caption = "Your turn: clear the last two. Which one is in the other's way?",
                freePlay = true,
                done = "Cleared. That's all there is to it.",
            ),
        )
    }

    // ---- hit testing ----------------------------------------------------------------------------

    /** How far from an arrow's line, in squares, a tap on a bare square still takes it. */
    private const val REACH = 0.75f

    /**
     * The square to tap for a touch at ([x], [y]) in square units (0..width, 0..height): the square itself when an
     * arrow is on it, else a square of the arrow whose line passes nearest, within [REACH]; -1 for none. Generous on
     * purpose: the line is thin, the square it runs through is not.
     */
    internal fun hit(s: PointersState, x: Float, y: Float): Int {
        val c = floor(x).toInt()
        val r = floor(y).toInt()
        if (r in 0 until s.height && c in 0 until s.width && s.arrowAt(r * s.width + c) >= 0) return r * s.width + c
        var best = -1
        var bestD = REACH
        for (i in s.arrows.indices) {
            if (i in s.removed) continue
            val a = s.arrows[i]
            for (k in 0 until a.size - 1) {
                val d = segmentDistance(x, y, centreX(a[k], s.width), centreY(a[k], s.width), centreX(a[k + 1], s.width), centreY(a[k + 1], s.width))
                if (d < bestD) {
                    bestD = d
                    best = a[k]
                }
            }
        }
        return best
    }

    private fun centreX(cell: Int, w: Int) = cell % w + 0.5f
    private fun centreY(cell: Int, w: Int) = cell / w + 0.5f

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val ex = ax + t * dx - px
        val ey = ay + t * dy - py
        return sqrt(ex * ex + ey * ey)
    }

    // ---- colour -----------------------------------------------------------------------------------

    /** Blue, Amber, Violet, Teal: no Coral or Green (a bump is drawn in the error colour), no Rose (near the error). */
    private val HUES = listOf(215f, 34f, 268f, 175f)

    /**
     * A hue index per arrow, so arrows that touch differ where four hues allow: each takes the first not already used
     * by a touching arrow before it. Derived from the arrows, never stored. Colour is not the only signal: each arrow
     * is one unbroken line with one head.
     */
    internal fun colouring(s: PointersState): IntArray {
        val owner = IntArray(s.width * s.height) { -1 }
        s.arrows.forEachIndexed { i, a -> a.forEach { owner[it] = i } }
        val out = IntArray(s.arrows.size)
        for (i in s.arrows.indices) {
            val used = BooleanArray(HUES.size)
            for (cell in s.arrows[i]) {
                val r = cell / s.width
                val c = cell % s.width
                for (d in 0 until 4) {
                    val nr = r + PointersLogic.rowStep(d)
                    val nc = c + PointersLogic.colStep(d)
                    if (nr !in 0 until s.height || nc !in 0 until s.width) continue
                    val o = owner[nr * s.width + nc]
                    if (o in 0 until i) used[out[o]] = true
                }
            }
            out[i] = used.indexOfFirst { !it }.takeIf { it >= 0 } ?: (i % HUES.size)
        }
        return out
    }

    // ---- drawing -----------------------------------------------------------------------------------

    /**
     * Points of [arrow]'s line in square units, its squares' centres and then on past the head along its way for
     * [beyond] more squares: the track a sliding arrow follows.
     */
    private fun track(arrow: List<Int>, w: Int, beyond: Int): List<Offset> {
        val d = PointersLogic.dirOf(arrow, w)
        val out = arrow.map { Offset(centreX(it, w), centreY(it, w)) }.toMutableList()
        val head = out.last()
        for (k in 1..beyond) out += Offset(head.x + k * PointersLogic.colStep(d), head.y + k * PointersLogic.rowStep(d))
        return out
    }

    private fun at(track: List<Offset>, t: Float): Offset {
        val i = floor(t).toInt().coerceIn(0, track.size - 2)
        val f = t - i
        return track[i] + (track[i + 1] - track[i]) * f
    }

    /**
     * Draws [arrow] moved [shift] squares along its own track (0 = in place): a line through its squares' centres
     * with round ends and a head. [unit] is the square's size in px, [origin] the board's top left.
     */
    private fun DrawScope.drawArrow(arrow: List<Int>, w: Int, unit: Float, colour: Color, shift: Float = 0f, width: Float = 0.2f, head: Boolean = true) {
        val beyond = (shift + 2).toInt() + 1
        val tr = track(arrow, w, beyond)
        val len = (arrow.size - 1).toFloat()
        val path = Path()
        val start = at(tr, shift) * unit
        path.moveTo(start.x, start.y)
        var k = floor(shift).toInt() + 1
        while (k < shift + len) {
            val p = tr[k] * unit
            path.lineTo(p.x, p.y)
            k++
        }
        val end = at(tr, shift + len) * unit
        path.lineTo(end.x, end.y)
        drawPath(path, colour, style = Stroke(width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (!head) return
        val d = PointersLogic.dirOf(arrow, w)
        val fx = PointersLogic.colStep(d).toFloat()
        val fy = PointersLogic.rowStep(d).toFloat()
        val tip = end + Offset(fx, fy) * (0.36f * unit)
        val base = end - Offset(fx, fy) * (0.1f * unit)
        val side = Offset(-fy, fx) * (0.27f * unit)
        val tri = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + side.x, base.y + side.y)
            lineTo(base.x - side.x, base.y - side.y)
            close()
        }
        drawPath(tri, colour)
        drawPath(tri, colour, style = Stroke(0.06f * unit, join = StrokeJoin.Round))
    }

    /** Faint dots on every square, so a line's way to the edge can be read across bare squares. */
    private fun DrawScope.drawDots(w: Int, h: Int, unit: Float, colour: Color) {
        for (r in 0 until h) for (c in 0 until w) drawCircle(colour, 0.05f * unit, Offset((c + 0.5f) * unit, (r + 0.5f) * unit))
    }

    /** A heart in a [size] square: the lives row, and the share card's. */
    internal fun heartPath(size: Float): Path = Path().apply {
        val s = size
        moveTo(s * 0.5f, s * 0.9f)
        cubicTo(s * 0.1f, s * 0.62f, s * 0.0f, s * 0.38f, s * 0.08f, s * 0.22f)
        cubicTo(s * 0.18f, s * 0.04f, s * 0.42f, s * 0.04f, s * 0.5f, s * 0.24f)
        cubicTo(s * 0.58f, s * 0.04f, s * 0.82f, s * 0.04f, s * 0.92f, s * 0.22f)
        cubicTo(s * 1.0f, s * 0.38f, s * 0.9f, s * 0.62f, s * 0.5f, s * 0.9f)
        close()
    }

    @Composable
    private fun Hearts(lives: Int, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(PointersLogic.LIVES) { k ->
                Canvas(Modifier.size(22.dp)) {
                    val p = heartPath(size.minDimension)
                    if (k < lives) drawPath(p, scheme.error)
                    else drawPath(p, scheme.onSurfaceVariant.copy(alpha = 0.6f), style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
                }
            }
        }
    }

    // ---- the home motif -----------------------------------------------------------------------------

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val board = tutorialBoard()
        val hues = colouring(board)
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(Modifier.size(side)) {
                val unit = size.minDimension / 4f
                board.arrows.forEachIndexed { i, a -> drawArrow(a, 4, unit, BoardHues.ink(HUES[hues[i]], dark), width = 0.22f) }
            }
        }
    }

    // ---- the board ------------------------------------------------------------------------------

    private const val SLIDE_MS = 420
    private const val BUMP_MS = 600

    private class Slide(val arrow: Int, val progress: Animatable<Float, *>)
    private class Bump(val arrow: Int, val blocker: Int, val progress: Animatable<Float, *>)

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as PointersState
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val hues = remember(s.arrows) { colouring(s) }
        val highlight = LocalBoardHighlight.current
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty(), 0.35f, 0.8f)
        // Animations are transient: they live here, in a scope that outlives the state that started them.
        val scope = rememberCoroutineScope()
        val slides = remember(s.arrows) { mutableStateListOf<Slide>() }
        var bump by remember(s.arrows) { mutableStateOf<Bump?>(null) }
        var cursor by remember(s.arrows) { mutableStateOf<Int?>(null) }
        // Reduced motion: a cleared arrow simply goes and a bump flashes without the shake.
        val still = LocalReducedMotion.current
        val live = interactive && !s.solved && !s.failed

        fun tapAt(cell: Int) {
            val i = s.arrowAt(cell)
            val next = s.tap(cell) ?: return
            if (next.lives < s.lives) {
                val b = Bump(i, s.blocker(i), Animatable(0f))
                bump = b
                scope.launch {
                    b.progress.animateTo(1f, tween(BUMP_MS, easing = LinearEasing))
                    if (bump === b) bump = null
                }
            } else if (!still) {
                val slide = Slide(i, Animatable(0f))
                slides += slide
                scope.launch {
                    slide.progress.animateTo(1f, tween(SLIDE_MS, easing = FastOutLinearInEasing))
                    slides.remove(slide)
                }
            }
            onState(next)
        }

        Column(
            Modifier
                .fillMaxSize()
                .boardKeys(live) { key, _, repeat ->
                    when (val action = pointersKeyAction(key) ?: return@boardKeys false) {
                        is PointersKeyAction.Move -> cursor = s.cursorAfter(cursor, action.dRow, action.dCol)
                        PointersKeyAction.Tap -> if (!repeat) cursor?.let { tapAt(it) }
                    }
                    true
                }
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Lives and the one line of news, always this height, so a bump or a loss never moves the board.
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Hearts(s.lives, Modifier)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (s.failed) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Out of lives", style = MaterialTheme.typography.bodyMedium, color = scheme.error, maxLines = 1)
                            FilledTonalButton(onClick = { if (interactive) onState(s.retry()) }) { Text("Try again", maxLines = 1) }
                        }
                    } else {
                        val left = s.arrows.size - s.removed.size
                        Text(
                            if (s.solved) "All clear" else "$left arrow${if (left == 1) "" else "s"} left",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Both axes, as Mosaic: whichever runs out first sets the square.
                val step = minOf(maxWidth / s.width, maxHeight / s.height)
                Canvas(
                    Modifier
                        .size(step * s.width, step * s.height)
                        .pointerInput(s, live) {
                            if (!live) return@pointerInput
                            detectTapGestures { p ->
                                val unit = size.width.toFloat() / s.width
                                val cell = hit(s, p.x / unit, p.y / unit)
                                if (cell >= 0) tapAt(cell)
                            }
                        },
                ) {
                    val unit = size.width / s.width
                    drawRoundRect(scheme.surface, cornerRadius = CornerRadius(0.25f * unit))
                    drawDots(s.width, s.height, unit, scheme.onSurfaceVariant.copy(alpha = 0.35f))
                    val b = bump
                    val flash = if (b == null) 0f else minOf(1f, 2f * (1f - b.progress.value)) // full warning for the first half, then fading
                    // The keyboard cursor: a soft band under the arrow it is on, or a ring on a bare square.
                    cursor?.let { at ->
                        val a = s.arrowAt(at)
                        val band = Color(accent).copy(alpha = 0.45f)
                        if (a >= 0) drawArrow(s.arrows[a], s.width, unit, band, width = 0.75f, head = false)
                        else drawCircle(band, 0.35f * unit, Offset((at % s.width + 0.5f) * unit, (at / s.width + 0.5f) * unit), style = Stroke(0.08f * unit))
                    }
                    for (i in s.arrows.indices) {
                        if (i in s.removed) continue
                        var colour = BoardHues.ink(HUES[hues[i]], dark)
                        if (!highlight.isEmpty && i !in highlight.strong && i !in highlight.soft) colour = colour.copy(alpha = 0.3f)
                        if (i in highlight.strong) drawArrow(s.arrows[i], s.width, unit, scheme.onBackground.copy(alpha = pulse.value * 0.5f), width = 0.6f, head = false)
                        var shift = 0f
                        if (b != null && (i == b.arrow || i == b.blocker)) colour = lerp(colour, scheme.error, flash)
                        if (b != null && i == b.arrow && !still) {
                            val t = b.progress.value
                            shift = 0.3f * sin(PI.toFloat() * t) + 0.06f * sin(6 * PI.toFloat() * t) * (1 - t)
                        }
                        drawArrow(s.arrows[i], s.width, unit, colour, shift)
                    }
                    clipRect {
                        for (slide in slides) {
                            if (slide.arrow !in s.removed) continue // a move the walkthrough refused: it stays
                            val a = s.arrows[slide.arrow]
                            val dist = PointersLogic.ray(a, s.width, s.height).size + a.size + 1f
                            val p = slide.progress.value
                            drawArrow(a, s.width, unit, BoardHues.ink(HUES[hues[slide.arrow]], dark).copy(alpha = 1f - p * p), p * dist)
                        }
                    }
                }
            }
        }
    }
}
