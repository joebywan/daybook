package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay

/**
 * Tents. The board is one canvas: a row of column counts across the top, a column of row counts down the
 * left, and the grid. A tap cycles a square empty, tent, grass, empty (grass is a note, never counted).
 * Highlight indices follow [TentsState]: squares `0 until n*n`, row clue `n*n + r`, column clue `n*n + n + c`.
 */
object Tents : PuzzleType {

    override val id = "tents"
    override val displayName = "Tents"
    override val tagline = "A tent beside every tree, none touching"
    override val accent = 0xFFC45EC9
    override val rules = listOf(
        "Put one tent beside every tree, up, down, left or right of it. Each tent belongs to one tree.",
        "Tents never touch each other, not even at a corner.",
        "The numbers say how many tents each row and column holds.",
        "Tap a square to place a tent, tap again for grass (a note that a tent can't go there), and again to clear it.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, T or Enter places a tent, X or G grass, Space cycles, Backspace clears.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: TentsLogic.lastResort(difficulty)

    /** The board when its answer was proved, null otherwise. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): TentsState? =
        TentsLogic.generateVerified(seed, difficulty)

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

    /** Accepts exactly [base] with [cell] made a tent and nothing else. */
    private fun onlyTent(base: TentsState, cell: Int): (PuzzleState) -> Boolean = { next ->
        next is TentsState && next.marks.indices.all { i -> next.marks[i] == if (i == cell) TentsLogic.TENT else base.marks[i] }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val n = TUTORIAL_N
        val rowClue0 = n * n
        val solved = tutorialBoard(tents = setOf(1, 3, 10, 17))
        val empty = tutorialBoard()
        val one = tutorialBoard(tents = setOf(1))
        val two = tutorialBoard(tents = setOf(1, 3))
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Every tree gets one tent, right beside it: above, below, left or right. " +
                    "Tents and trees pair off one to one.",
                highlight = BoardHighlight(strong = setOf(2), soft = setOf(1)),
            ),
            TutorialFrame(
                state = solved,
                caption = "Tents never touch, not even corner to corner. None of the eight squares " +
                    "around a tent can hold another.",
                highlight = BoardHighlight(strong = setOf(17), soft = setOf(11, 12, 13, 16, 18, 21, 22, 23)),
            ),
            TutorialFrame(
                state = solved,
                caption = "The number beside a row or column is how many tents it holds. The top row holds two.",
                highlight = BoardHighlight(strong = setOf(rowClue0), soft = (0 until n).toSet()),
            ),
            TutorialFrame(
                state = empty,
                caption = "The top row needs two tents, and only two of its squares sit beside a tree, " +
                    "so both are tents. Tap the glowing square to place one.",
                highlight = BoardHighlight(strong = setOf(1), soft = setOf(rowClue0, 3)),
                accepts = onlyTent(empty, 1),
                retry = "Tap the glowing square once.",
                done = "A tent.",
            ),
            TutorialFrame(
                state = one,
                caption = "The other square in that row is a tent too. Tap it.",
                highlight = BoardHighlight(strong = setOf(3), soft = setOf(rowClue0)),
                accepts = onlyTent(one, 3),
                retry = "Tap the glowing square once.",
                done = "Two tents, and the count is met.",
            ),
            TutorialFrame(
                state = tutorialBoard(tents = setOf(1, 3), grass = setOf(0, 4, 5, 6, 7, 9)),
                caption = "Tap a square twice for grass: a note that no tent can go there. " +
                    "Squares touching a tent, and squares with no tree beside them, are grass. " +
                    "Grass is only for you; it never counts against you.",
                highlight = BoardHighlight(soft = setOf(0, 4, 5, 6, 7, 9)),
            ),
            TutorialFrame(
                state = two,
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- drawing ------------------------------------------------------------------------------

    private const val SETTLE_MILLIS = 1000L

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

        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            val x = gx + c * u
            val y = gy + r * u
            when {
                s.trees[i] -> drawTree(x, y, u, treeInk, scheme.onSurface)
                s.marks[i] == TentsLogic.TENT -> drawTent(x, y, u, if (i in overlay.badTents) scheme.error else tentInk, scheme.surface)
                s.marks[i] == TentsLogic.GRASS -> {
                    drawRect(grassFill, Offset(x, y), Size(u, u))
                    drawGrass(x, y, u, treeInk)
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

        fun rim(indices: Set<Int>, width: Float, color: Color) {
            for (i in indices) {
                val rect = rectOf(i, n, u)
                drawRoundRect(color, rect.topLeft + Offset(width / 2, width / 2), Size(rect.width - width, rect.height - width), CornerRadius(u * 0.15f), style = Stroke(width))
            }
        }
        if (lit) {
            rim(hl.soft - hl.strong, u * 0.07f, overlay.glow.copy(alpha = 0.5f))
            rim(hl.strong, u * 0.2f, scheme.background.copy(alpha = 0.9f * overlay.pulse))
            rim(hl.strong, u * 0.11f, overlay.glow.copy(alpha = overlay.pulse))
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

    private fun Float.asDp(density: Density): Dp = with(density) { this@asDp.toDp() }

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
        val s = state as TentsState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse: State<Float> = if (highlight.strong.isEmpty()) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "hint").animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "hint-pulse",
            )
        }
        val hues = remember(s.solution) { BoardHues.pair(s.solution.hashCode()) }

        // Wrong now, and still wrong a second on: touching tents, a row or column with too many.
        // The next change cancels the wait and starts it over. Derived, never stored.
        val bad = Triple(
            s.touching(),
            (0 until n).filterTo(mutableSetOf()) { s.rowTents(it) > s.rowCounts[it] },
            (0 until n).filterTo(mutableSetOf()) { s.colTents(it) > s.colCounts[it] },
        )
        var settled by remember(s.solution) { mutableStateOf(bad) }
        LaunchedEffect(s.marks) {
            delay(SETTLE_MILLIS)
            settled = bad
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
                            .reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) { drawBoard(s, unitPx, digits, scheme, hues, overlay) }
                // Taps are read over the grid alone; the counts beside it are not part of the play area.
                Box(
                    Modifier
                        .padding(start = unit, top = unit)
                        .size(unit * n)
                        .gridCursor(cursor.takeIf { interactive }, n, n, scheme.primary)
                        .pointerInput(s, interactive) {
                            if (!interactive) return@pointerInput
                            detectTapGestures { offset ->
                                val r = (offset.y / unitPx).toInt().coerceIn(0, n - 1)
                                val c = (offset.x / unitPx).toInt().coerceIn(0, n - 1)
                                cursor = r * n + c
                                val next = s.cycled(r * n + c)
                                if (next !== s) onState(next)
                            }
                        },
                )
            }
        }
    }
}
