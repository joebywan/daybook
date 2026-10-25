package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.rememberHintPulse
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.joebywan.daybook.ui.drawHintRings
import com.joebywan.daybook.ui.fitStep
import com.joebywan.daybook.ui.gridCellAt
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pipe shapes are direction bitmasks: bit 0 up, 1 right, 2 down, 3 left. Rotating a piece is a
 * rotation of those four bits, which is why the whole puzzle needs no shape table at all.
 */
private const val UP = 1
private const val RIGHT = 2
private const val DOWN = 4
private const val LEFT = 8

/**
 * Long enough to read as a turn rather than a flicker, short enough that a player tapping at speed
 * is never waiting on it. Taps apply to the state immediately and only the drawn angle lags, so
 * this is a ceiling on visual lag, not on input.
 */
private const val SPIN_MILLIS = 140

/** Degrees in the quarter turn a tap performs; the tile is drawn wound back by this and unwinds. */
private const val QUARTER = 90f

/** Pipe width as a fraction of the cell. Everything else on a tile is sized from the result. */
private const val PIPE_WIDTH = 0.18f

/**
 * How far an unfilled pipe is tinted from the tile it sits on towards the foreground colour.
 *
 * Applied as an opaque blend rather than as stroke alpha: two translucent strokes that overlap
 * composite darker, which turned every join and every hub into a blob and made a joined run read
 * as a chain of separate lozenges. Blended once and drawn solid, an overlap cannot show at all.
 */
private const val DRY_TINT = 0.38f

/**
 * How far past the cell edge an arm reaches, in device pixels.
 *
 * Two butt-capped strokes that stop on exactly the same coordinate share one antialiased edge and
 * between them cover it only partly, so a hairline of background shows straight across the join —
 * the very thing this drawing is trying to get rid of. Half a pixel of overlap closes that, and
 * cannot itself be seen now that the pipe colours are opaque. It also stays inside the one-pixel
 * gutter between neighbouring tile backgrounds, so a later tile's fill never clips it back off.
 */
private const val JOIN_BLEED_PX = 0.5f

@Serializable
data class PipesState(
    val width: Int,
    val height: Int,
    val cells: List<Int>,
    /**
     * Where the water comes in. Part of the generated state rather than a constant so that a
     * restored board fills from the same tile it filled from before it was put down.
     */
    val source: Int,
    override val moves: Int = 0,
    /**
     * Tiles the player has turned. Every tile starts scrambled, so a tile sitting the wrong way is
     * only the player's mistake if they turned it; the hint reads this to tell the two apart.
     * Defaulted, so games saved before it existed still load.
     */
    val turned: Set<Int> = emptySet(),
) : PuzzleState {

    override val solved: Boolean get() = Pipes.fullyJoined(this) && Pipes.connected(this)

    fun rotate(index: Int): PipesState =
        copy(
            cells = cells.toMutableList().also { it[index] = Pipes.rotateCw(it[index]) },
            moves = moves + 1,
            turned = turned + index,
        )
}

/**
 * Pipes — the "Net" rotation puzzle.
 *
 * The solved board is a random spanning tree of the grid, so there is always a way to join every
 * pipe into one network with no loose ends and no loops.
 */
object Pipes : PuzzleType {

    override val id = "pipes"
    override val displayName = "Pipes"
    override val tagline = "Rotate until every pipe joins up"
    override val accent = 0xFF48B9C4
    override val rules = listOf(
        "Tap a tile to rotate it a quarter turn.",
        "Water enters at the ringed tile; pipes joined back to it run full.",
        "Finish with no loose ends: every pipe opening must meet another.",
        "All the pipework must form one single connected network.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, Space or R turns the tile.",
    )

    fun rotateCw(mask: Int): Int {
        var out = 0
        if (mask and UP != 0) out = out or RIGHT
        if (mask and RIGHT != 0) out = out or DOWN
        if (mask and DOWN != 0) out = out or LEFT
        if (mask and LEFT != 0) out = out or UP
        return out
    }

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 5 to 7
        Difficulty.HARD -> 6 to 9
        Difficulty.EXPERT -> 8 to 11
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /** Trees tried per seed before [generateVerified] gives up. */
    internal const val ATTEMPTS = 40

    /**
     * A board the hints' reasoning settles tile by tile ([PipesTeacher.settles], which also proves it
     * has one answer), or null when none of [ATTEMPTS] trees was. Attempt `a` uses `Rng(seed + a)`,
     * so a board depends on its seed alone.
     */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): PipesState? {
        val (w, h) = shape(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            // The mains inlet: in a tree every other tile is downstream of it, wherever it is.
            val source = rng.nextInt(w * h)
            val solved = spanningTree(rng, w, h)
            if (!PipesTeacher.settles(w, h, solved)) continue
            // Scramble. Re-scramble in the unlikely event the board lands already solved.
            var cells: List<Int>
            do {
                cells = solved.map { mask ->
                    var m = mask
                    repeat(rng.nextInt(4)) { m = rotateCw(m) }
                    m
                }
            } while (cells == solved)
            return PipesState(w, h, cells, source)
        }
        return null
    }

    /** Only when no tree of [ATTEMPTS] was settled, which a year per tier never reaches (`FallbackTest`): seed 1's board, proved by the same test. */
    internal fun lastResort(difficulty: Difficulty): PipesState = generateVerified(1L, difficulty)!!

    /**
     * Randomised Kruskal: every edge of the grid in a shuffled order, each kept when it joins two
     * pieces not yet joined. Each cell records the edges it keeps. A depth-first maze (the first
     * generator) made long corridors with few junctions; this branches everywhere, so boards have
     * about twice the T-junctions and some crosses.
     */
    private fun spanningTree(rng: Rng, w: Int, h: Int): List<Int> {
        val masks = MutableList(w * h) { 0 }
        val parent = IntArray(w * h) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        val edges = buildList {
            for (i in 0 until w * h) {
                if (i % w < w - 1) add(i to RIGHT)
                if (i / w < h - 1) add(i to DOWN)
            }
        }
        for ((i, direction) in rng.shuffled(edges)) {
            val j = if (direction == RIGHT) i + 1 else i + w
            val a = root(i)
            val b = root(j)
            if (a == b) continue
            parent[a] = b
            masks[i] = masks[i] or direction
            masks[j] = masks[j] or opposite(direction)
        }
        return masks
    }

    private fun opposite(direction: Int) = when (direction) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        else -> LEFT
    }

    /** True when no opening points at a wall or at a tile that does not open back. */
    fun fullyJoined(s: PipesState): Boolean {
        for (i in s.cells.indices) {
            val r = i / s.width
            val c = i % s.width
            val mask = s.cells[i]
            if (mask and UP != 0 && (r == 0 || s.cells[i - s.width] and DOWN == 0)) return false
            if (mask and DOWN != 0 && (r == s.height - 1 || s.cells[i + s.width] and UP == 0)) return false
            if (mask and LEFT != 0 && (c == 0 || s.cells[i - 1] and RIGHT == 0)) return false
            if (mask and RIGHT != 0 && (c == s.width - 1 || s.cells[i + 1] and LEFT == 0)) return false
        }
        return true
    }

    /**
     * The tiles the water has actually reached: those joined back to [PipesState.source] through
     * openings that meet on both sides.
     *
     * The board is coloured by this and the win condition is decided by it, so what the player
     * sees filling up and what counts as finished cannot drift apart.
     */
    fun filled(s: PipesState): Set<Int> {
        val start = s.source
        if (start !in s.cells.indices) return emptySet()

        val stack = ArrayDeque<Int>()
        stack.addLast(start)
        val reached = mutableSetOf(start)

        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val mask = s.cells[i]
            val r = i / s.width
            val c = i % s.width

            fun flowTo(opening: Int, inBounds: Boolean, j: Int) {
                if (!inBounds || mask and opening == 0) return
                if (s.cells[j] and opposite(opening) == 0 || !reached.add(j)) return
                stack.addLast(j)
            }

            flowTo(UP, r > 0, i - s.width)
            flowTo(DOWN, r < s.height - 1, i + s.width)
            flowTo(LEFT, c > 0, i - 1)
            flowTo(RIGHT, c < s.width - 1, i + 1)
        }
        return reached
    }

    /**
     * A fully joined board has exactly as many edges as the spanning tree it came from, so it is
     * either one tree or a cycle plus leftovers. This is the check that rules the latter out.
     */
    fun connected(s: PipesState): Boolean = filled(s).size == s.cells.size

    // ---- the walkthrough ---------------------------------------------------------------------

    /**
     * The walkthrough's board, 3x3 so every tile is big enough to aim at while learning:
     *
     * ```
     * ┌  ┬  ╴
     * │  ├  ╴
     * ╵  └  ╴
     * ```
     *
     * Hand-built so that the tiles the frames teach are settled by the border alone — the straight
     * on the left edge, the corner bend, the T on the top edge — and then by a neighbour: the end in
     * the bottom corner that the straight now points into. Each starts exactly one tap short, so each
     * tile the walkthrough asks for is one tap (a tap past its answer is refused, and a tile could
     * never come back round). PipesTeachingTest proves the board has exactly one answer and that hints alone finish
     * it from the "your turn" frame, with no mistake and no fallback.
     */
    internal val TUTORIAL_SOLUTION = listOf(
        RIGHT or DOWN, LEFT or RIGHT or DOWN, LEFT,
        UP or DOWN, UP or RIGHT or DOWN, LEFT,
        UP, UP or RIGHT, LEFT,
    )

    /** The scramble the walkthrough starts from: every tile the frames teach is one tap short. */
    internal val TUTORIAL_START = listOf(
        UP or RIGHT, UP or RIGHT or DOWN, DOWN,
        LEFT or RIGHT, LEFT or RIGHT or DOWN, UP,
        LEFT, RIGHT or DOWN, UP,
    )
    private const val TUTORIAL_W = 3
    private const val TUTORIAL_SOURCE = 4

    private fun tutorialBoard(cells: List<Int>) = PipesState(TUTORIAL_W, TUTORIAL_W, cells, TUTORIAL_SOURCE)

    /** [TUTORIAL_START] with the listed tiles already turned to their answer. */
    private fun tutorialAfter(vararg settled: Int) =
        tutorialBoard(TUTORIAL_START.mapIndexed { i, m -> if (i in settled) TUTORIAL_SOLUTION[i] else m })

    /**
     * Accepts exactly [base] with [cell] turned to its answer and nothing else. Strict on purpose:
     * each frame's board is written for the one before it, so a frame that let a stray turn through
     * would hand the next frame a board its caption does not describe.
     */
    private fun only(base: PipesState, cell: Int): (PuzzleState) -> Boolean = { next ->
        next is PipesState && next.cells.indices.all { i ->
            next.cells[i] == if (i == cell) TUTORIAL_SOLUTION[i] else base.cells[i]
        }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val solved = tutorialBoard(TUTORIAL_SOLUTION)
        val start = tutorialAfter()
        // The three tiles the border alone settles: the straight, the corner bend and the T.
        val border = setOf(3, 0, 1)
        val tee = tutorialAfter(3, 0, 1)
        val end = tutorialAfter(3, 0, 1, 6)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Each tile's pipes are fixed; only the way it faces can change. Turn the " +
                    "tiles until every pipe joins into one network, with no loose ends and no loops.",
            ),
            TutorialFrame(
                state = solved,
                caption = "Water comes in at the ringed tile and fills every pipe joined back to it. " +
                    "When the whole board runs full, it's solved.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_SOURCE)),
            ),
            TutorialFrame(
                state = start,
                caption = "No pipe may point off the board: an edge straight runs along it, a corner bend faces " +
                    "in, a T's flat side faces out. A tap turns a tile clockwise. Tap each glowing tile.",
                // Each tile glows until it faces its one way.
                glow = { s -> BoardHighlight(strong = border.filter { (s as PipesState).cells[it] != TUTORIAL_SOLUTION[it] }.toSet()) },
                // Any order, one tap each; a tile turned past its way is refused.
                steps = { next ->
                    when {
                        next !is PipesState -> TutorialStep.REJECT
                        next.cells == tee.cells -> TutorialStep.DONE
                        next.cells.indices.all { i -> next.cells[i] == start.cells[i] || (i in border && next.cells[i] == TUTORIAL_SOLUTION[i]) } ->
                            TutorialStep.PROGRESS
                        else -> TutorialStep.REJECT
                    }
                },
                retry = "Tap the glowing tile once.",
                done = "Each fits the border the only way it can.",
            ),
            TutorialFrame(
                state = tee,
                caption = "The straight above now points down into this end, and every opening has " +
                    "to meet another. So this end must point up. Tap it.",
                highlight = BoardHighlight(strong = setOf(6), soft = setOf(3)),
                accepts = only(tee, 6),
                retry = "Tap the glowing tile once.",
                done = "Joined. A set tile tells you about the tiles beside it.",
            ),
            TutorialFrame(
                state = end,
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- teaching --------------------------------------------------------------------------

    /**
     * A mistake to take back or a tile to reason out — see [PipesTeacher]. Pipes had no hints at all
     * before this: a tile's turn cannot be read off the tile alone, but the border and the tiles
     * already set pin every tile of every generated board, one at a time.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as PipesState
        val step = PipesTeacher.teach(s) ?: return null
        val wrong = step.wrong
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = setOf(step.cell),
            mistake = step.technique == PipesTeacher.MISTAKE,
            fallback = step.technique == PipesTeacher.FALLBACK,
            applyTo = { now -> turnTo(now as PipesState, step.cell, step.mask) },
            reachedBy = { now ->
                val p = now as PipesState
                // A mistake is taken back by turning the tile off the wrong way; the next hint looks
                // again if the new way is wrong too.
                if (wrong != null) p.cells[step.cell] != wrong else p.cells[step.cell] == step.mask
            },
        )
    }

    /** "Show me": [cell] turned clockwise until it reads [mask]. One state, so one undo entry. */
    private fun turnTo(s: PipesState, cell: Int, mask: Int): PipesState {
        var m = s.cells[cell]
        var taps = 0
        while (m != mask && taps < 4) {
            m = rotateCw(m)
            taps++
        }
        if (m != mask || taps == 0) return s
        return s.copy(
            cells = s.cells.toMutableList().also { it[cell] = mask },
            moves = s.moves + taps,
            turned = s.turned + cell,
        )
    }

    // ---- home-grid motif ----------------------------------------------------------------------

    /**
     * A hand-picked 3x3 corner: a corner, a T, a straight and two endpoints, arranged so the run
     * back to the inlet is a shape the eye can follow.
     *
     * Not a generated board, because the tile has to look the same on every device and every day.
     * The bottom-right pair is deliberately left pointing at nothing: half the puzzle is the
     * difference between a pipe that has joined up and one that has not, and a motif where
     * everything already met would only show the easy half.
     */
    private val PREVIEW_BOARD = PipesState(
        width = 3,
        height = 3,
        cells = listOf(
            DOWN or RIGHT, LEFT or RIGHT or DOWN, LEFT,
            UP, UP or DOWN, UP or LEFT,
            UP or RIGHT, UP, UP or LEFT,
        ),
        source = 0,
    )

    /**
     * Read off [PREVIEW_BOARD] rather than listed separately, so the wet tiles cannot drift away
     * from the tiles that are actually joined to the inlet. Nine cells, computed once.
     */
    private val PREVIEW_FILLED = filled(PREVIEW_BOARD)

    /**
     * Pipework at a size where the pipework is legible, rather than a whole board at a size where
     * it is a texture. Wet and dry both appear, since the fill is what the puzzle is about.
     */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val wet = Color(accent)
        val dry = scheme.onSurfaceVariant.copy(alpha = DRY_TINT).compositeOver(scheme.surface)

        Canvas(modifier) {
            val cellPx = size.minDimension / PREVIEW_BOARD.width
            for (i in PREVIEW_BOARD.cells.indices) {
                val isSource = i == PREVIEW_BOARD.source
                drawTile(i, PREVIEW_BOARD.width, cellPx, PREVIEW_BOARD.cells[i], isSource, if (i in PREVIEW_FILLED) wet else dry, wet, scheme.surface)
            }
        }
    }

    /**
     * Tile [i] of a grid [width] tiles across: its ground, its pipes turned by [spin] degrees and, if it is
     * the inlet, the inlet. The board and the home-grid motif both draw through this.
     */
    private fun DrawScope.drawTile(
        i: Int,
        width: Int,
        cellPx: Float,
        mask: Int,
        isSource: Boolean,
        pipeColour: Color,
        wet: Color,
        ground: Color,
        spin: Float = 0f,
    ) {
        val r = i / width
        val c = i % width
        val cx = (c + 0.5f) * cellPx
        val cy = (r + 0.5f) * cellPx
        val stroke = cellPx * PIPE_WIDTH
        val reach = cellPx * 0.5f + JOIN_BLEED_PX

        drawRect(
            color = if (isSource) wet.copy(alpha = 0.16f) else ground,
            topLeft = Offset(c * cellPx + 1f, r * cellPx + 1f),
            size = Size(cellPx - 2f, cellPx - 2f),
        )

        if (mask != 0) {
            // A single-ended pipe is an endpoint: the run stops here, and the knob is
            // what says so.
            val endpoint = mask.countOneBits() == 1
            rotate(degrees = spin, pivot = Offset(cx, cy)) {
                // Butt caps stop the stroke dead on the cell edge. A round cap would
                // instead push half a pipe width past it and into the neighbour, so
                // two joined arms overlapped by a full width — the "overlapping rather
                // than joining" the board reads as.
                fun arm(dx: Float, dy: Float) {
                    drawLine(
                        color = pipeColour,
                        start = Offset(cx, cy),
                        end = Offset(cx + dx * reach, cy + dy * reach),
                        strokeWidth = stroke,
                        cap = StrokeCap.Butt,
                    )
                }
                if (mask and UP != 0) arm(0f, -1f)
                if (mask and DOWN != 0) arm(0f, 1f)
                if (mask and LEFT != 0) arm(-1f, 0f)
                if (mask and RIGHT != 0) arm(1f, 0f)

                // Square-ended arms leave a notch on the outside of a bend, so the hub
                // stands in for the round join the stroke no longer draws itself: at
                // exactly half the pipe width it rounds the corner off without the
                // junction swelling wider than the run passing through it.
                drawCircle(
                    color = pipeColour,
                    radius = if (endpoint) cellPx * 0.20f else stroke * 0.5f,
                    center = Offset(cx, cy),
                )
            }
        }

        // The inlet does not turn with its tile: it marks a fixed point on the board,
        // and a ring that spun would read as just another pipe.
        if (isSource) {
            drawCircle(color = wet, radius = cellPx * 0.17f, center = Offset(cx, cy))
            drawCircle(
                color = wet,
                radius = cellPx * 0.31f,
                center = Offset(cx, cy),
                style = Stroke(width = stroke * 0.5f),
            )
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as PipesState
        val scheme = MaterialTheme.colorScheme
        val wet = Color(accent)
        // Flattened against the tile background it will be drawn on, so it lands on the same tone
        // the translucent version used to show while being opaque everywhere it overlaps itself.
        val dry = scheme.onSurfaceVariant.copy(alpha = DRY_TINT).compositeOver(scheme.surface)
        val wetCells = remember(s.cells, s.source) { filled(s) }
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        // Breathes, as Kings' does, so a glowing tile is findable at a glance on an 8x11 board. Only
        // runs while something glows, and is read in the draw phase.
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        // One angle per tile, held outside the state so a rotation can be shown turning while the
        // board itself has already moved on. Only the grid shape resets them.
        val spins = remember(s.width, s.height) { List(s.width * s.height) { Animatable(0f) } }
        val shown = remember(s.width, s.height) { s.cells.toMutableList() }
        // The turns run here rather than in the effect below. Every tap hands in a new state, which
        // restarts that effect and cancels its children: a spin launched there was cut off by the
        // next tap on any *other* tile, leaving the first frozen at a slant. Only the same tile's
        // own next turn (snapTo, below) may interrupt a spin, and it carries the angle on.
        val spinScope = rememberCoroutineScope()
        // Reduced motion: a tile simply shows its new turn.
        val still = LocalReducedMotion.current

        LaunchedEffect(s.cells) {
            val before = shown.toList()
            shown.clear()
            shown.addAll(s.cells)
            if (before.size != s.cells.size) return@LaunchedEffect

            for (i in s.cells.indices) {
                if (before[i] == s.cells[i]) continue
                // An undo turns the other way. Anything else — a restored board, a new puzzle —
                // is not a turn at all and should simply appear.
                val wound = when {
                    still -> 0f
                    rotateCw(before[i]) == s.cells[i] -> -QUARTER
                    rotateCw(s.cells[i]) == before[i] -> QUARTER
                    else -> 0f
                }
                val spin = spins[i]
                spinScope.launch {
                    if (wound == 0f) {
                        spin.snapTo(0f)
                    } else {
                        // Winding back from wherever the tile currently is, rather than from
                        // square on, means a second tap during the first turn keeps going round
                        // instead of jumping back a quarter.
                        spin.snapTo(spin.value + wound)
                        spin.animateTo(0f, tween(SPIN_MILLIS, easing = FastOutSlowInEasing))
                    }
                }
            }
        }

        // Sized from both axes (CLAUDE.md): sized from width alone, a 5x7 board ran a row under
        // the hint panel at 390dp, and an 8x11 one further.
        // The keyboard cursor (see PipesKeys): transient, so not in the state.
        var cursor by remember(s.width, s.height) { mutableStateOf<Int?>(null) }
        BoxWithConstraints(
            Modifier.fillMaxWidth().boardKeys(interactive) { key, _, repeat ->
                movedCursor(cursor, key, s.height, s.width)?.let { cursor = it; return@boardKeys true }
                val at = cursor ?: return@boardKeys false
                if (!pipesRotateKey(key)) return@boardKeys false
                if (!repeat) onState(s.rotate(at))
                true
            }.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cell = fitStep(s.width, s.height)
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            Canvas(
                Modifier
                    .width(cell * s.width)
                    .height(cell * s.height)
                    .highlightGrid(s.width, s.height)
                    .gridCursor(cursor.takeIf { interactive }, s.width, s.height, scheme.primary)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset: Offset ->
                            val i = gridCellAt(offset, cellPx, s.width, s.height)
                            cursor = i
                            onState(s.rotate(i))
                        }
                    }
            ) {
                for (i in s.cells.indices) {
                    drawTile(i, s.width, cellPx, s.cells[i], i == s.source, if (i in wetCells) wet else dry, wet, scheme.surface, spins[i].value)

                    // Everything a hint does not name steps back, so the named tiles read without
                    // hunting for their outlines. A veil in the page colour rather than a fainter
                    // pipe, so wet and dry stay tellable apart underneath.
                    if (!highlight.isEmpty && i !in highlight.strong && i !in highlight.soft) {
                        drawRect(
                            color = scheme.background.copy(alpha = 0.62f),
                            topLeft = Offset((i % s.width) * cellPx, (i / s.width) * cellPx),
                            size = Size(cellPx, cellPx),
                        )
                    }
                }

                // Outlines last, so a neighbour drawn later never paints over half of one.
                drawHintRings(highlight, s.width, s.height, cellPx, glow, pulse.value)
            }
        }
    }
}
