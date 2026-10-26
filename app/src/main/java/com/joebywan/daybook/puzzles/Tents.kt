package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.core.union
import com.joebywan.daybook.ui.Cell
import com.joebywan.daybook.ui.gridCellAt
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import com.joebywan.daybook.ui.lineUnits
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.rememberSettled

/**
 * Tents. The board is one canvas: a row of column counts across the top, a column of row counts down the
 * left, and the grid. A tap marks grass (a note, never counted), a double tap or long press places a tent and sprouts grass around it.
 * Highlight indices follow [TentsState]: squares `0 until n*n`, row clue `n*n + r`, column clue `n*n + n + c`.
 */
object Tents : PuzzleType {

    private const val DOUBLE_TAP_MS = 300L
    private val tapClock = TimeSource.Monotonic.markNow()

    private data class Pending(val cell: Int, val base: TentsState, val after: TentsState)

    override val id = "tents"
    override val displayName = "Tents"
    override val tagline = "A tent beside every tree, none touching"
    override val accent = 0xFFC45EC9
    override val rules = listOf(
        "Put one tent beside every tree, up, down, left or right of it. Each tent belongs to one tree.",
        "Tents never touch each other, not even at a corner.",
        "The numbers say how many tents each row and column holds.",
        "Tap a square for grass (a note that a tent can't go there), tap again to clear it. Drag to lay grass across squares. Double-tap or long-press for a tent (grass sprouts around it); tap a tent to remove it.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, T or Enter places a tent, X, G or Space grass, Backspace clears.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        TentsLogic.generateVerified(seed, difficulty) ?: TentsLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = TentsTeacher.deduction(state as TentsState)

    // ---- walkthrough --------------------------------------------------------------------------

    private const val TUTORIAL_N = 5

    /** Trees at 2, 8, 15, 18; the one answer has tents at 1, 3, 10, 17. */
    internal fun tutorialBoard(tents: Set<Int> = emptySet(), grass: Set<Int> = emptySet()): TentsState {
        val trees = setOf(2, 8, 15, 18)
        val answer = setOf(1, 3, 10, 17)
        return TentsState(
            size = TUTORIAL_N,
            trees = List(TUTORIAL_N * TUTORIAL_N) { it in trees },
            rowCounts = listOf(2, 0, 1, 1, 0),
            colCounts = listOf(1, 1, 1, 1, 0),
            solution = List(TUTORIAL_N * TUTORIAL_N) { it in answer },
            marks = List(TUTORIAL_N * TUTORIAL_N) {
                when (it) {
                    in tents -> TentsLogic.TENT
                    in grass -> TentsLogic.GRASS
                    else -> 0
                }
            },
        )
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val n = TUTORIAL_N
        val rowClue0 = n * n
        val solved = tutorialBoard(tents = setOf(1, 3, 10, 17))
        val empty = tutorialBoard()
        val grass = empty.tapped(0)
        val pair = setOf(1, 3)
        val two = grass.withTent(1).withTent(3)
        val bottom = (20 until 25).toSet()
        val swept = two.grassed(bottom)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Every tree gets one tent, right beside it: above, below, left or right. The number " +
                    "beside a row or column is how many tents it holds.",
                highlight = BoardHighlight(strong = setOf(2, rowClue0), soft = setOf(1, 3)),
            ),
            TutorialFrame(
                state = solved,
                caption = "Tents never touch, not even corner to corner. None of the eight squares " +
                    "around a tent can hold another.",
                highlight = BoardHighlight(strong = setOf(17), soft = setOf(11, 12, 13, 16, 18, 21, 22, 23)),
            ),
            TutorialFrame(
                state = empty,
                caption = "The glowing corner has no tree beside it, so no tent can go there. Tap it to " +
                    "mark it grass.",
                highlight = BoardHighlight(strong = setOf(0), soft = setOf(1, 5)),
                accepts = { next -> next is TentsState && next.marks == grass.marks },
                retry = "Tap the glowing square once.",
                done = "Grass. That square can never hold a tent.",
            ),
            TutorialFrame(
                state = grass,
                caption = "The top row needs two tents, and only two of its squares sit beside a tree, " +
                    "so both are tents. Double-tap or long-press each glowing square to place one.",
                // Each square glows until it holds its tent.
                glow = { s ->
                    BoardHighlight(strong = pair.filter { (s as TentsState).marks[it] != TentsLogic.TENT }.toSet(), soft = setOf(rowClue0))
                },
                // Either order; taking one back on the way is fine too.
                steps = { next ->
                    when {
                        next !is TentsState -> TutorialStep.REJECT
                        next.marks == two.marks -> TutorialStep.DONE
                        next.marks in pair.map { grass.withTent(it).marks } + listOf(grass.marks) -> TutorialStep.PROGRESS
                        else -> TutorialStep.REJECT
                    }
                },
                retry = "Double-tap the glowing square (two quick taps), or press and hold it.",
                done = "Two tents, and grass sprouts around them.",
            ),
            TutorialFrame(
                state = two,
                caption = "The bottom row holds no tents. Drag along the glowing squares to mark them all " +
                    "grass in one sweep.",
                highlight = BoardHighlight(strong = bottom, soft = setOf(rowClue0 + 4)),
                accepts = { next -> next is TentsState && next.marks == swept.marks },
                retry = "Drag along all five glowing squares in one sweep.",
                done = "Swept: one drag marks a whole run.",
            ),
            TutorialFrame(
                state = swept,
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- drawing ------------------------------------------------------------------------------

    /** What the board draws on top: the highlight, and who is bad (already debounced). */
    private class Overlay(
        val highlight: BoardHighlight = BoardHighlight.None,
        val glow: Color = Color.Unspecified,
        val pulse: Float = 1f,
        val badTents: Set<Int> = emptySet(),
        val badRows: Set<Int> = emptySet(),
        val badCols: Set<Int> = emptySet(),
    )

    /** The square a highlight index names, in the canvas of [n] + 1 units across (clues take the first). */
    private fun rectOf(index: Int, n: Int, u: Float): Rect = when {
        index < n * n -> Rect(Offset((1 + index % n) * u, (1 + index / n) * u), Size(u, u))
        index < n * n + n -> Rect(Offset(0f, (1 + index - n * n) * u), Size(u, u))
        else -> Rect(Offset((1 + index - n * n - n) * u, 0f), Size(u, u))
    }

    private fun DrawScope.drawBoard(
        s: TentsState,
        u: Float,
        digits: List<TextLayoutResult>,
        scheme: ColorScheme,
        hues: Pair<Float, Float>,
        overlay: Overlay,
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val n = s.size
        val dark = BoardHues.isDark(scheme)
        val (hueTree, hueTent) = hues
        val treeInk = BoardHues.ink(hueTree, dark)
        val tentInk = BoardHues.ink(hueTent, dark)
        val grassFill = BoardHues.fill(hueTree, dark).copy(alpha = if (dark) 0.5f else 0.4f)
        val gx = u
        val gy = u
        val hl = overlay.highlight
        val lit = !hl.isEmpty
        fun named(i: Int) = i in hl.strong || i in hl.soft

        drawRoundRect(scheme.surface, Offset(gx, gy), Size(n * u, n * u), CornerRadius(u * 0.12f))

        val seen = s.seen
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            val x = gx + c * u
            val y = gy + r * u
            feelSquare(feel, i, Offset(x, y), Size(u, u), accent) {
                when {
                    s.trees[i] -> drawTree(x, y, u, treeInk, scheme.onSurface)
                    s.marks[i] == TentsLogic.TENT -> drawTent(x, y, u, if (i in overlay.badTents) scheme.error else tentInk, scheme.surface)
                    seen[i] == TentsLogic.GRASS -> {
                        drawRect(grassFill, Offset(x, y), Size(u, u))
                        drawGrass(x, y, u, treeInk)
                    }
                }
            }
        }
        val thin = maxOf(1f, u * 0.025f)
        for (i in 0..n) {
            drawLine(scheme.outline, Offset(gx + i * u, gy), Offset(gx + i * u, gy + n * u), thin)
            drawLine(scheme.outline, Offset(gx, gy + i * u), Offset(gx + n * u, gy + i * u), thin)
        }

        // Counts: green when met, red when over (once the debounce lets it show).
        fun clue(want: Int, have: Int, bad: Boolean, idx: Int, rect: Rect) {
            val tint = if (bad) scheme.error else if (have == want) scheme.primary else Color.Unspecified
            if (idx in hl.strong) drawRoundRect(overlay.glow.copy(alpha = 0.16f * overlay.pulse), rect.topLeft, rect.size, CornerRadius(u * 0.2f))
            if (tint != Color.Unspecified) drawRoundRect(tint.copy(alpha = 0.16f), rect.topLeft, rect.size, CornerRadius(u * 0.2f))
            val layout = digits[want]
            val alpha = if (lit && !named(idx)) 0.35f else 1f
            val color = (if (tint != Color.Unspecified) tint else scheme.onSurface).copy(alpha = alpha)
            drawText(layout, color, Offset(rect.center.x - layout.size.width / 2f, rect.center.y - layout.size.height / 2f))
        }
        for (r in 0 until n) clue(s.rowCounts[r], s.rowTents(r), r in overlay.badRows, n * n + r, rectOf(n * n + r, n, u))
        for (c in 0 until n) clue(s.colCounts[c], s.colTents(c), c in overlay.badCols, n * n + n + c, rectOf(n * n + n + c, n, u))

        if (lit) {
            hintRims(hl, u, overlay.glow, overlay.pulse, scheme.background) { indices, width, color ->
                for (i in indices) {
                    val rect = rectOf(i, n, u)
                    drawRoundRect(color, rect.topLeft + Offset(width / 2, width / 2), Size(rect.width - width, rect.height - width), CornerRadius(u * 0.15f), style = Stroke(width))
                }
            }
        }
    }

    /** A round tree on a trunk. */
    private fun DrawScope.drawTree(x: Float, y: Float, u: Float, canopy: Color, trunk: Color) {
        drawRoundRect(trunk.copy(alpha = 0.65f), Offset(x + u * 0.43f, y + u * 0.55f), Size(u * 0.14f, u * 0.32f), CornerRadius(u * 0.04f))
        drawCircle(canopy, u * 0.3f, Offset(x + u * 0.5f, y + u * 0.4f))
        drawCircle(canopy, u * 0.19f, Offset(x + u * 0.3f, y + u * 0.54f))
        drawCircle(canopy, u * 0.19f, Offset(x + u * 0.7f, y + u * 0.54f))
    }

    /** A tent: a filled triangle with a doorway cut into it. */
    private fun DrawScope.drawTent(x: Float, y: Float, u: Float, body: Color, door: Color) {
        drawPath(Path().apply {
            moveTo(x + u * 0.5f, y + u * 0.16f)
            lineTo(x + u * 0.92f, y + u * 0.84f)
            lineTo(x + u * 0.08f, y + u * 0.84f)
            close()
        }, body)
        drawPath(Path().apply {
            moveTo(x + u * 0.5f, y + u * 0.46f)
            lineTo(x + u * 0.65f, y + u * 0.84f)
            lineTo(x + u * 0.35f, y + u * 0.84f)
            close()
        }, door.copy(alpha = 0.85f))
    }

    /** Three blades of grass. */
    private fun DrawScope.drawGrass(x: Float, y: Float, u: Float, ink: Color) {
        val w = maxOf(2f, u * 0.07f)
        for ((dx, lean) in listOf(0.3f to -0.07f, 0.5f to 0f, 0.7f to 0.07f)) {
            val top = if (dx == 0.5f) 0.34f else 0.44f
            drawLine(ink.copy(alpha = 0.85f), Offset(x + u * dx, y + u * 0.78f), Offset(x + u * (dx + lean), y + u * top), w, StrokeCap.Round)
        }
    }

    private fun digitLayouts(measurer: androidx.compose.ui.text.TextMeasurer, n: Int, fontPx: Float, density: Density): List<TextLayoutResult> {
        val style = TextStyle(fontSize = with(density) { fontPx.toSp() }, fontWeight = FontWeight.SemiBold)
        return (0..n).map { measurer.measure(it.toString(), style) }
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** Two trees, two tents beside them, one grass: a legal 3x3, never generated. */
    private val motif = TentsState(
        size = 3,
        trees = List(9) { it == 0 || it == 8 },
        rowCounts = listOf(1, 0, 1),
        colCounts = listOf(0, 2, 0),
        solution = List(9) { it == 1 || it == 7 },
        marks = List(9) { if (it == 1 || it == 7) TentsLogic.TENT else if (it == 4) TentsLogic.GRASS else 0 },
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth, maxHeight) / (motif.size + 1)
            val unitPx = with(density) { unit.toPx() }
            val digits = remember(unitPx) { digitLayouts(measurer, motif.size, unitPx * 0.55f, density) }
            Canvas(Modifier.size(unit * (motif.size + 1))) {
                drawBoard(motif, unitPx, digits, scheme, BoardHues.HUE_PAIRS[0], Overlay())
            }
        }
    }

    // ---- the board ----------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val base = state as TentsState
        val n = base.size
        // A single tap (grass) is drawn at once but handed to the screen only after the double-tap
        // window, so a double tap becomes one tent move, not a grass entry under it (as Kings' crowns).
        // [Pending.base] ties it to the board it was made on: undo or a hint drops it.
        var pending by remember { mutableStateOf<Pending?>(null) }
        var lastCell by remember { mutableIntStateOf(-1) }
        var lastTapAt by remember { mutableLongStateOf(0L) }
        val held = pending?.takeIf { it.base === base }
        // A sweep lays grass on every empty square the finger crosses, drawn as it goes and emitted
        // once on lift (one move, one undo). Transient, so not in the state.
        var sweepBase by remember { mutableStateOf<TentsState?>(null) }
        var swept by remember { mutableStateOf(emptySet<Int>()) }
        val s = sweepBase?.grassed(swept) ?: held?.after ?: base
        val onStateNow by rememberUpdatedState(onState)
        // The taps are driven by events and a timer of their own, never by recomposition (a frame can lag
        // behind a quick tap): [latest] is the board as of the last thing this handler emitted or was handed.
        val scope = rememberCoroutineScope()
        val latest = remember { Cell(base) }.also { it.v = base }
        val commit = remember { Cell<Job?>(null) }
        fun emit(next: TentsState) {
            latest.v = next
            onStateNow(next)
        }

        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val (highlight, glow, pulse) = rememberHintPaint()
        val hues = remember(s.solution) { BoardHues.pair(s.solution.hashCode()) }

        // Wrong now, and still wrong a second on: touching tents, a row or column with too many.
        // The next change cancels the wait and starts it over. Derived, never stored.
        val bad = Triple(
            s.touching(),
            (0 until n).filterTo(mutableSetOf()) { s.rowTents(it) > s.rowCounts[it] },
            (0 until n).filterTo(mutableSetOf()) { s.colTents(it) > s.colCounts[it] },
        )
        val settled = rememberSettled(bad, s.solution, s.marks)

        // A row or column is finished when it has its count of tents, none touching another.
        val units = remember(n) { lineUnits(n, n) }
        val touching = base.touching()
        val feel = rememberMoveFeel(base.marks, units) { u ->
            val line = units[u]
            line.count { base.marks[it] == TentsLogic.TENT } == (if (u < n) base.rowCounts[u] else base.colCounts[u - n]) &&
                line.none { it in touching }
        }

        // The keyboard cursor: transient, so not in the state.
        var cursor by remember(s.solution) { mutableStateOf<Int?>(null) }

        BoxWithConstraints(
            Modifier.fillMaxWidth().boardKeys(interactive) { key, _, repeat ->
                movedCursor(cursor, key, n, n)?.let { cursor = it; return@boardKeys true }
                val at = cursor ?: return@boardKeys false
                val action = tentsKeyAction(key) ?: return@boardKeys false
                if (!repeat) s.applyKey(at, action)?.let(onState)
                true
            }.padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Both axes: the clue gutter adds a unit each way.
            val unit = if (constraints.hasBoundedHeight) minOf(maxWidth / (n + 1), maxHeight / (n + 1)) else maxWidth / (n + 1)
            val unitPx = with(density) { unit.toPx() }
            val digits = remember(unitPx, n) { digitLayouts(measurer, n, unitPx * 0.55f, density) }
            val overlay = Overlay(
                highlight, glow, pulse.value,
                bad.first.intersect(settled.first), bad.second.intersect(settled.second), bad.third.intersect(settled.third),
            )

            Box(
                Modifier
                    .size(unit * (n + 1))
                    .reportHighlight { _, h ->
                        (h.strong + h.soft).map { rectOf(it, n, unitPx) }
                            .reduceOrNull(Rect::union)
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) { drawBoard(s, unitPx, digits, scheme, hues, overlay, feel, Color(accent)) }
                // Taps are read over the grid alone; the counts beside it are not part of the play area.
                Box(
                    Modifier
                        .padding(start = unit, top = unit)
                        .size(unit * n)
                        .gridCursor(cursor.takeIf { interactive }, n, n, scheme.primary)
                        .pointerInput(interactive) {
                            if (!interactive) return@pointerInput
                            fun cellAt(o: Offset) = gridCellAt(o, unitPx, n, n)
                            detectDragGestures(
                                onDragStart = { o ->
                                    // Any tap still held rides along as the sweep's base.
                                    commit.v?.cancel()
                                    sweepBase = pending?.takeIf { it.base === latest.v }?.after ?: latest.v
                                    pending = null
                                    lastCell = -1
                                    swept = setOf(cellAt(o))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val i = cellAt(change.position)
                                    if (i !in swept) swept = swept + i
                                },
                                onDragEnd = {
                                    val next = sweepBase!!.grassed(swept)
                                    sweepBase = null
                                    swept = emptySet()
                                    if (next !== latest.v) emit(next)
                                },
                                onDragCancel = { sweepBase = null; swept = emptySet() },
                            )
                        }
                        .pointerInput(interactive, unitPx) {
                            if (!interactive) return@pointerInput
                            // A long press is the double tap's twin: a tent, or one taken back. `onLongPress`
                            // never delays a tap (only `onDoubleTap` would).
                            detectTapGestures(
                                onLongPress = { offset ->
                                    val i = gridCellAt(offset, unitPx, n, n)
                                    val held = pending?.takeIf { it.base === latest.v }
                                    commit.v?.cancel()
                                    lastCell = -1
                                    pending = null
                                    val from = if (held != null && held.cell != i) held.after else latest.v
                                    val next = from.withTent(i)
                                    if (next !== latest.v) emit(next)
                                },
                            ) { offset ->
                                val i = gridCellAt(offset, unitPx, n, n)
                                val held = pending?.takeIf { it.base === latest.v }
                                commit.v?.cancel()
                                if ((held?.after ?: latest.v).marks[i] == TentsLogic.TENT) { // taking a tent back is one tap
                                    lastCell = -1
                                    pending = null
                                    emit((held?.after ?: latest.v).withTent(i))
                                    return@detectTapGestures
                                }
                                val now = tapClock.elapsedNow().inWholeMilliseconds
                                if (i == lastCell && now - lastTapAt < DOUBLE_TAP_MS) {
                                    lastCell = -1
                                    pending = null
                                    val next = latest.v.withTent(i)
                                    if (next !== latest.v) emit(next)
                                    return@detectTapGestures
                                }
                                lastCell = i
                                lastTapAt = now
                                // A tap on another square settles the one held before it.
                                if (held != null && held.cell != i) emit(held.after)
                                val from = latest.v
                                val next = from.tapped(i)
                                if (next === from) {
                                    pending = null
                                } else {
                                    val p = Pending(i, from, next)
                                    pending = p
                                    commit.v = scope.launch {
                                        delay(DOUBLE_TAP_MS)
                                        if (pending === p && p.base === latest.v) {
                                            pending = null
                                            emit(p.after)
                                        }
                                    }
                                }
                            }
                        },
                )
            }
        }
    }
}
