package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import com.joebywan.daybook.ui.rememberMoveFeel

/** One pour made: [count] layers from tube [from] onto tube [to]. Enough to take it back exactly ([SandState.withoutFrom]). */
@Serializable
data class SandPour(val from: Int, val to: Int, val count: Int)

/**
 * A Sand board: [tubes] bottom to top, colour indices `0 until colours`, each tube holding up to [height] layers.
 * [log] is the pours made, oldest first. It only exists so a mistake can be named and taken back to the last board that
 * could still be sorted (as Target's log): a board alone does not say which pour lost it, since a typical unsortable
 * board could have come from about 14 different sortable ones (measured, `docs/SAND_SPEC.md`). No solution path, no par.
 * Field names and meanings are fixed once shipped (saves).
 */
@Serializable
data class SandState(
    val tubes: List<List<Int>>,
    val height: Int,
    val colours: Int,
    override val moves: Int = 0,
    val log: List<SandPour> = emptyList(),
) : PuzzleState {
    override val solved: Boolean get() = SandLogic.isSolved(tubes, height)

    /** Not solved and no pour left. Derived, never stored. */
    val stuck: Boolean get() = SandLogic.isStuck(tubes, height)

    /** The board after pouring [from] onto [to], one move, or null when the rules refuse it. */
    fun pour(from: Int, to: Int): SandState? {
        if (!SandLogic.canPour(tubes, height, from, to)) return null
        val k = SandLogic.pourSize(tubes, height, from, to)
        return copy(tubes = SandLogic.pour(tubes, height, from, to), moves = moves + 1, log = log + SandPour(from, to, k))
    }

    /** The board with the pour at [index] of the log taken back, and every pour after it: one move. */
    fun withoutFrom(index: Int): SandState {
        var t = tubes
        for (p in log.subList(index, log.size).asReversed()) t = SandLogic.shift(t, p.to, p.from, p.count)
        return copy(tubes = t, moves = moves + 1, log = log.take(index))
    }
}

/**
 * Sand: tubes of layered coloured sand; pour from tube to tube until every tube holds one colour (`docs/SAND_SPEC.md`).
 * Tap a tube to lift it, tap another to pour. Highlight indices: tube `i` is `i`. See [SandLogic] for the proof each
 * board carries and [SandTeacher] for the hints.
 */
object Sand : PuzzleType {

    override val id = "sand"
    override val displayName = "Sand"
    override val tagline = "Pour every colour into a tube of its own"
    override val accent = 0xFFD1617B
    override val rules = listOf(
        "Sort the sand: finish with every tube holding one colour, or nothing.",
        "Tap a tube to lift it, then tap another to pour into it. Tap the lifted tube again to put it down.",
        "A pour moves the colour on top, all of it or as much as fits, onto the same colour or into an empty tube.",
        "Every colour has its own symbol. There is no move limit, and Undo takes a pour back.",
    )

    override val keyboardHelp = listOf(
        "Keys 1 to 9 and 0 pick tubes 1 to 10: the first lifts, the second pours. Escape puts the tube down.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        SandLogic.generateVerified(seed, difficulty) ?: SandLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = SandTeacher.deduction(state as SandState)

    // ---- colours ------------------------------------------------------------------------------

    /**
     * Sand colours, index for index with [colourNames] and [drawSymbol]'s shapes; a board stores the index. Eight exceed
     * the five-hue limit, so these are six hues of docs/COLOUR.md at three steps (pale = light fill, mid = mark, deep =
     * light ink), Coral and Green left out, the same in both schemes. Ordered so each tier's prefix (5, 7, 8) is as far
     * apart as it can be by CIEDE2000 with deuteranopia and protanopia (20.0 / 15.8 / 14.2, `SandPaletteTest`); every
     * layer also carries its colour's symbol, so colour is never the only signal.
     */
    val palette: List<Color> = listOf(
        BoardHues.deep(34f, false), BoardHues.fill(46f, false), BoardHues.fill(268f, false), BoardHues.mark(268f),
        BoardHues.deep(330f, false), BoardHues.fill(175f, false), BoardHues.deep(175f, false), BoardHues.mark(34f),
    )

    /** What the hints call each colour. */
    val colourNames = listOf("brown", "cream", "lilac", "violet", "wine", "mint", "teal", "orange")

    // ---- drawing ------------------------------------------------------------------------------

    /** A small fixed hash, so a layer's grain and edge depend only on where it is. */
    private fun hash(a: Int, b: Int, c: Int): Int {
        var h = a * 73856093 xor b * 19349663 xor c * 83492791
        h = h xor (h ushr 13)
        h *= 0x5bd1e995
        return h xor (h ushr 15)
    }

    private fun unit(a: Int, b: Int, c: Int) = (hash(a, b, c) and 0xFFFF) / 65535f

    /** Colour [c]'s symbol, centred at [centre], about [r] across from the middle. */
    private fun DrawScope.drawSymbol(c: Int, centre: Offset, r: Float, ink: Color) {
        val stroke = Stroke(r * 0.38f, cap = StrokeCap.Round)
        when (c) {
            0 -> drawCircle(ink, r * 0.75f, centre)
            1 -> drawRect(ink, Offset(centre.x - r * 0.68f, centre.y - r * 0.68f), Size(r * 1.36f, r * 1.36f))
            2 -> drawPath(Path().apply {
                moveTo(centre.x, centre.y - r * 0.9f)
                lineTo(centre.x + r * 0.9f, centre.y + r * 0.7f)
                lineTo(centre.x - r * 0.9f, centre.y + r * 0.7f)
                close()
            }, ink)
            3 -> drawPath(Path().apply {
                moveTo(centre.x, centre.y - r)
                lineTo(centre.x + r * 0.8f, centre.y)
                lineTo(centre.x, centre.y + r)
                lineTo(centre.x - r * 0.8f, centre.y)
                close()
            }, ink)
            4 -> drawCircle(ink, r * 0.7f, centre, style = Stroke(r * 0.32f))
            5 -> {
                drawLine(ink, Offset(centre.x - r * 0.8f, centre.y), Offset(centre.x + r * 0.8f, centre.y), stroke.width, StrokeCap.Round)
                drawLine(ink, Offset(centre.x, centre.y - r * 0.8f), Offset(centre.x, centre.y + r * 0.8f), stroke.width, StrokeCap.Round)
            }
            6 -> {
                val d = r * 0.62f
                drawLine(ink, Offset(centre.x - d, centre.y - d), Offset(centre.x + d, centre.y + d), stroke.width, StrokeCap.Round)
                drawLine(ink, Offset(centre.x - d, centre.y + d), Offset(centre.x + d, centre.y - d), stroke.width, StrokeCap.Round)
            }
            else -> {
                val d = r * 0.4f
                drawLine(ink, Offset(centre.x - r * 0.8f, centre.y - d), Offset(centre.x + r * 0.8f, centre.y - d), stroke.width, StrokeCap.Round)
                drawLine(ink, Offset(centre.x - r * 0.8f, centre.y + d), Offset(centre.x + r * 0.8f, centre.y + d), stroke.width, StrokeCap.Round)
            }
        }
    }

    /** How long a refused pour's tint lasts. */
    private const val REFUSAL_MS = 400

    /** Geometry of one tube, in units of its width: the body's height for [h] layers. */
    private const val LAYER = 0.72f
    private const val NECK = 0.32f
    private fun bodyUnits(h: Int) = h * LAYER + NECK

    /** How the tube is outlined. */
    private class Look(val outline: Color, val outlineWidth: Float, val inside: Color, val glow: Color? = null, val glowWidth: Float = 0f)

    /**
     * One tube: the glass from [top] down, [w] wide, its sand bottom to top. Each layer is a flat band with a slightly
     * uneven top and a fixed grain, both from [index] and the layer's place (never random per frame, never stored), and
     * its colour's symbol when there is room. A finished tube (full, one colour) gets a cap.
     */
    private fun DrawScope.drawTube(t: List<Int>, height: Int, index: Int, left: Float, top: Float, w: Float, look: Look, symbols: Boolean) {
        val layerH = w * LAYER
        val bodyH = height * layerH + w * NECK
        val bottom = top + bodyH
        // The glass, grown by [out] on every side (the glow rides outside it).
        fun glass(out: Float) = RoundRect(
            left - out, top - out, left + w + out, bottom + out,
            topLeftCornerRadius = CornerRadius(w * 0.12f + out), topRightCornerRadius = CornerRadius(w * 0.12f + out),
            bottomRightCornerRadius = CornerRadius(w * 0.5f + out), bottomLeftCornerRadius = CornerRadius(w * 0.5f + out),
        )
        val path = Path().apply { addRoundRect(glass(0f)) }
        drawPath(path, look.inside)
        val inset = w * 0.08f
        val floor = bottom - inset * 0.6f
        clipPath(path) {
            // Top layer first: each lower layer then covers what lies under its own uneven top.
            for (j in t.indices.reversed()) {
                val colour = palette[t[j]]
                val surface = floor - (j + 1) * layerH
                val wave = Path().apply {
                    moveTo(left, floor + inset)
                    val steps = 6
                    for (s in 0..steps) {
                        val x = left + w * s / steps
                        val bump = (unit(index, j, s) - 0.5f) * layerH * 0.14f
                        lineTo(x, surface + bump)
                    }
                    lineTo(left + w, floor + inset)
                    close()
                }
                drawPath(wave, colour)
                // Grain: a few darker and lighter specks inside the band.
                for (g in 0 until 9) {
                    val gx = left + inset + unit(index, j, 100 + g) * (w - 2 * inset)
                    val gy = surface + layerH * (0.18f + 0.7f * unit(index, j, 200 + g))
                    val dark = g % 2 == 0
                    drawCircle(if (dark) Color.Black.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.22f), w * 0.028f, Offset(gx, gy))
                }
            }
            if (symbols) for (j in t.indices) {
                val centre = Offset(left + w / 2, floor - (j + 0.5f) * layerH)
                drawSymbol(t[j], centre, minOf(w, layerH) * 0.2f, BoardHues.onFill(palette[t[j]]))
            }
        }
        look.glow?.let { glow ->
            val out = look.glowWidth
            drawPath(Path().apply { addRoundRect(glass(out)) }, glow, style = Stroke(look.glowWidth))
        }
        drawPath(path, look.outline, style = Stroke(look.outlineWidth))
        if (SandLogic.finished(t, height)) {
            // The cap on a finished tube.
            val capH = w * 0.2f
            drawRoundRect(
                palette[t[0]], Offset(left - w * 0.06f, top - capH * 0.7f), Size(w * 1.12f, capH), CornerRadius(capH * 0.4f),
            )
            drawRoundRect(
                look.outline, Offset(left - w * 0.06f, top - capH * 0.7f), Size(w * 1.12f, capH), CornerRadius(capH * 0.4f),
                style = Stroke(look.outlineWidth),
            )
        }
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** Three tubes mid-sort and one finished: a legal crop of a board (each colour four layers). */
    private val motif = listOf(listOf(0, 0, 0, 0), listOf(1, 2, 2), listOf(2, 1, 1, 2), listOf(1))

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        Canvas(modifier) {
            val n = motif.size
            val w = minOf(size.width / (n + (n - 1) * 0.35f + 0.2f), size.height / (bodyUnits(4) + 0.3f))
            val gap = w * 0.35f
            val total = n * w + (n - 1) * gap
            val left = (size.width - total) / 2
            val top = (size.height - w * bodyUnits(4)) / 2 + w * 0.1f
            val look = Look(scheme.outline, maxOf(1f, w * 0.06f), scheme.surface)
            for ((i, t) in motif.withIndex()) drawTube(t, 4, i, left + i * (w + gap), top, w, look, symbols = w > 14f)
        }
    }

    // ---- the board ----------------------------------------------------------------------------

    /**
     * How [n] tubes sit in a box [w] x [h] (dp): [rows] rows of at most [perRow], each tube [tube] dp wide, standing in a
     * column [column] dp wide that is its tap target (gap included, full height). The row count is whichever of 1, 2 or 3
     * gives the widest tube (fewer rows on a tie): the owner's call, after Nonogram Expert's 17-19dp squares were judged
     * fiddly. The column is at least the tube and its gap, and widens towards [MIN_TAP] where the box has room.
     */
    internal class Layout(val rows: Int, val perRow: Int, val tube: Float, val column: Float)

    internal fun layout(n: Int, height: Int, w: Float, h: Float): Layout {
        var best: Layout? = null
        for (rows in 1..3) {
            val perRow = ceil(n / rows.toFloat()).toInt()
            val down = rows * (LIFT + bodyUnits(height) + CAP) + (rows - 1) * ROW_GAP
            val tube = minOf(w / (perRow * (1 + GAP)), h / down, MAX_TUBE)
            val b = best
            if (b == null || tube > b.tube + 0.01f) best = Layout(rows, perRow, tube, minOf(w / perRow, maxOf(tube * (1 + GAP), MIN_TAP)))
        }
        return best!!
    }

    private const val GAP = 0.45f
    private const val LIFT = 0.45f
    private const val CAP = 0.2f
    private const val ROW_GAP = 0.3f
    private const val MAX_TUBE = 72f

    /** The tap column the layout widens towards when the box has room (dp). */
    const val MIN_TAP = 56f

    private class PourAnim(val from: Int, val to: Int, val colour: Int)

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SandState
        val scheme = MaterialTheme.colorScheme
        val highlight = LocalBoardHighlight.current
        val accentColour = Color(accent)
        val glowColour = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        // Transient: the lifted tube starts over whenever the board changes (a pour, undo, a hint).
        var lifted by remember(s.tubes) { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        val shake = remember { Animatable(0f) }
        var shaking by remember { mutableIntStateOf(-1) }
        val stream = remember { Animatable(1f) }
        var pouring by remember { mutableStateOf<PourAnim?>(null) }
        // A refused pour tints the tube it was refused by, fading over 400 ms (alongside the shake).
        val refusal = remember { Animatable(0f) }
        // Reduced motion: no shake and no stream; the tint is held, then cleared; a lifted tube still rises, at once.
        val still = LocalReducedMotion.current
        // A tube finished by the rules (full, one colour) is ringed in the accent once. No settle: the pour is its own landing.
        val units = remember(s.tubes.size) { List(s.tubes.size) { intArrayOf(it) } }
        val feel = rememberMoveFeel(s.tubes, units) { SandLogic.finished(s.tubes[it], s.height) }

        fun go(step: SandStep) {
            lifted = step.lifted
            step.refused?.let { b ->
                shaking = b
                scope.launch {
                    refusal.snapTo(1f)
                    if (still) {
                        delay(REFUSAL_MS.toLong())
                        refusal.snapTo(0f)
                    } else {
                        refusal.animateTo(0f, tween(REFUSAL_MS, easing = LinearEasing))
                    }
                }
                if (!still) scope.launch {
                    shake.snapTo(0f)
                    for (x in listOf(1f, -1f, 0.6f, -0.6f, 0f)) shake.animateTo(x, tween(45))
                }
            }
            step.state?.let { next ->
                val moved = next.tubes.indices.first { next.tubes[it].size > s.tubes[it].size }
                val from = s.tubes.indices.first { next.tubes[it].size < s.tubes[it].size }
                pouring = PourAnim(from, moved, next.tubes[moved].last())
                if (!still) scope.launch {
                    stream.snapTo(0f)
                    stream.animateTo(1f, tween(260))
                }
                onState(next)
            }
        }

        val stuck = s.stuck
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .boardKeys(interactive && !s.solved) { key, _, repeat ->
                    val action = sandKeyAction(key) ?: return@boardKeys false
                    if (action == SandKeyAction.PutDown && lifted == null) return@boardKeys false
                    if (!repeat) go(s.press(lifted, action))
                    true
                }
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val n = s.tubes.size
            val captionH = 30.dp
            val availH = if (constraints.hasBoundedHeight) (maxHeight - captionH).value else Float.MAX_VALUE / 4
            val fit = layout(n, s.height, maxWidth.value, availH)
            val rows = fit.rows
            val perRow = fit.perRow
            val u = fit.tube.dp
            val col = fit.column.dp
            val tubeBoxH = u * (LIFT + CAP + bodyUnits(s.height))
            val rowGap = u * ROW_GAP
            val boardW = col * perRow
            val boardH = tubeBoxH * rows + rowGap * (rows - 1)

            /** The top-left of tube [i]'s column; a short last row is centred. */
            fun tubeOffset(i: Int): Pair<Dp, Dp> {
                val r = i / perRow
                val inRow = minOf(perRow, n - r * perRow)
                val x = (boardW - col * inRow) / 2 + col * (i % perRow)
                return x to (tubeBoxH + rowGap) * r
            }

            Box(Modifier.size(boardW, boardH + captionH)) {
                for (i in 0 until n) {
                    val (x, y) = tubeOffset(i)
                    val strong = i in highlight.strong
                    val soft = !strong && i in highlight.soft
                    val dim = !highlight.isEmpty && !strong && !soft
                    val up by animateFloatAsState(if (lifted == i) 1f else 0f, if (still) snap() else tween(120), label = "lift")
                    val t = s.tubes[i]
                    Box(
                        Modifier
                            .offset(x, y)
                            .size(col, tubeBoxH)
                            .highlightAnchor(i)
                            .graphicsLayer {
                                translationY = -up * LIFT * 0.85f * u.toPx()
                                translationX = if (shaking == i) shake.value * u.toPx() * 0.12f else 0f
                                alpha = if (dim) 0.3f else 1f
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = interactive && !s.solved,
                            ) { go(s.tap(lifted, i)) }
                            .drawBehind {
                                val w = u.toPx()
                                val refused = if (shaking == i) refusal.value else 0f
                                val look = Look(
                                    outline = lerp(if (lifted == i) accentColour else scheme.outline, scheme.error, refused),
                                    outlineWidth = if (lifted == i) 2.5.dp.toPx() else 1.5.dp.toPx(),
                                    inside = lerp(scheme.surface, scheme.error, 0.22f * refused),
                                    glow = when {
                                        strong -> glowColour.copy(alpha = pulse.value)
                                        soft -> glowColour.copy(alpha = 0.5f)
                                        feel.glow(i) > 0f -> accentColour.copy(alpha = feel.glow(i))
                                        else -> null
                                    },
                                    glowWidth = if (strong) 3.dp.toPx() else 2.dp.toPx(),
                                )
                                drawTube(t, s.height, i, (size.width - w) / 2, w * (LIFT + CAP), w, look, symbols = true)
                            },
                    )
                }

                // The pour: a stream of sand from one mouth to the other, decoration only (the board has already changed).
                val p = pouring
                if (p != null && stream.value < 1f) Canvas(Modifier.size(boardW, boardH)) {
                    val (fx, fy) = tubeOffset(p.from)
                    val (tx, ty) = tubeOffset(p.to)
                    val uw = u.toPx()
                    val a = Offset((fx + col / 2).toPx(), (fy + u * (LIFT + CAP)).toPx() - uw * 0.3f)
                    val b = Offset((tx + col / 2).toPx(), (ty + u * (LIFT + CAP)).toPx() + uw * 0.4f)
                    val mid = Offset((a.x + b.x) / 2, minOf(a.y, b.y) - uw * 0.9f)
                    val k = stream.value
                    val colour = palette[p.colour].copy(alpha = 1f - k * k)
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        quadraticTo(mid.x, mid.y, b.x, b.y)
                    }
                    drawPath(path, colour, style = Stroke(uw * 0.16f * (1f - 0.5f * k), cap = StrokeCap.Round))
                    // A few grains falling at the end of the stream.
                    for (g in 0 until 5) {
                        val f = ((k * 3f + g / 5f) % 1f)
                        val angle = (g * 2 * PI / 5).toFloat()
                        drawCircle(colour, uw * 0.035f, Offset(b.x + cos(angle) * uw * 0.1f, b.y - uw * 0.2f * (1f - f) + sin(angle) * uw * 0.04f))
                    }
                }

                // One line under the tubes, always reserved, so the board never moves for it.
                Box(Modifier.offset(y = boardH).width(boardW).height(captionH), contentAlignment = Alignment.Center) {
                    if (stuck) Text(
                        "No pours left: undo a few",
                        color = scheme.error,
                        fontSize = 14.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * Brown (0), cream (1) and lilac (2), three layers each, in five tubes. Cream onto cream, brown into the spare to free a
     * cream, cream onto cream to finish a tube; the rest is the player's. Tubes bottom to top.
     */
    internal fun tutorialBoard(): SandState = SandState(
        listOf(listOf(0, 2, 1), listOf(2, 1), listOf(1, 0, 0), listOf(2), emptyList()),
        height = 3, colours = 3,
    )

    private fun makes(expected: SandState): (PuzzleState) -> Boolean = { next -> next is SandState && next.tubes == expected.tubes }

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.pour(0, 1)!!
        val s2 = s1.pour(2, 4)!!
        val s3 = s2.pour(1, 2)!!
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Sort the sand so each tube holds one colour (each has a symbol too). A pour moves the top colour, " +
                    "as much as fits, onto the same colour or into an empty tube.",
                highlight = BoardHighlight(soft = (0 until 5).toSet()),
            ),
            TutorialFrame(
                state = s0,
                caption = "Tubes 1 and 2 both have cream on top. Tap tube 1 to lift it, then tap tube 2 to pour.",
                highlight = BoardHighlight(strong = setOf(0, 1)),
                accepts = makes(s1),
                retry = "Tap the glowing tube 1, then tube 2.",
                done = "The cream went onto the cream.",
            ),
            TutorialFrame(
                state = s1,
                caption = "Tube 3's two brown sit on a cream. Pour them into the empty tube 5, then pour tube 2's two cream onto tube 3.",
                highlight = BoardHighlight(strong = setOf(2, 4)),
                steps = { next ->
                    when ((next as? SandState)?.tubes) {
                        s2.tubes -> TutorialStep.PROGRESS
                        s3.tubes -> TutorialStep.DONE
                        else -> TutorialStep.REJECT
                    }
                },
                glow = { now -> BoardHighlight(strong = if ((now as? SandState)?.tubes == s2.tubes) setOf(1, 2) else setOf(2, 4)) },
                retry = "Tap the glowing tubes, the one to pour from first.",
                done = "Tube 3 is all cream: finished.",
            ),
            TutorialFrame(
                state = s3,
                caption = "Your turn: sort the rest. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Sorted. That's all there is to it.",
            ),
        )
    }
}
