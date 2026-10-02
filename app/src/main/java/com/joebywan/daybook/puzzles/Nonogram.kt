package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear

/**
 * Nonogram: numbers beside a grid say how long the runs of filled squares are in each row and
 * column; fill the squares that satisfy all of them and a picture appears.
 *
 * Every board is dealt only if it can be finished by looking at one row or column at a time
 * ([NonogramLogic.generateVerified]), so nobody ever has to guess, and the hints teach that
 * line-by-line reasoning ([NonogramTeacher]).
 *
 * Tiers differ in size alone (5x5, 10x10, 15x15): the same line logic, a longer way to carry it.
 */
object Nonogram : PuzzleType {

    override val id = "nonogram"
    override val displayName = "Nonogram"
    override val tagline = "Numbers reveal the picture"
    override val accent = 0xFF3E7CB1

    override val rules = listOf(
        "The numbers beside a row, or above a column, give the lengths of its runs of filled squares, in order.",
        "Two runs always have at least one empty square between them.",
        "Pick Fill or Cross under the board and tap squares. Drag along a row or column to sweep several at once.",
        "A cross just means \"empty\": it is a note to yourself and never counts against you.",
        "Solved when every row and column shows its numbers. Every board can be worked out one line at a time, with no guessing.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        NonogramLogic.generateVerified(seed, difficulty) ?: NonogramLogic.lastResort(difficulty)

    // ---- the walkthrough ----------------------------------------------------------------------

    /** A heart, five wide: two rows are full-width, so the first lesson is a plain sweep. */
    internal const val TUTORIAL_PICTURE =
        "01010" +
            "11111" +
            "11111" +
            "01110" +
            "00100"

    private const val TUTORIAL_N = 5

    private fun tutorialBoard(cells: String = ".".repeat(TUTORIAL_N * TUTORIAL_N)) =
        NonogramState(TUTORIAL_N, TUTORIAL_N, TUTORIAL_PICTURE, cells)

    /** [base] with [marks] (cell to mark) written over it. */
    private fun marked(base: NonogramState, marks: Map<Int, Char>): NonogramState {
        val next = base.cells.toCharArray()
        marks.forEach { (i, c) -> next[i] = c }
        return base.copy(cells = next.concatToString())
    }

    /**
     * Accepts exactly [base] with [changes] made and nothing else. Strict on purpose, as Kings'
     * is: each frame's board is written for the one before it.
     */
    private fun only(base: NonogramState, changes: Map<Int, Char>): (PuzzleState) -> Boolean = { next ->
        next is NonogramState && next.cells == marked(base, changes).cells
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val rowIndex = { r: Int -> NonogramLogic.rowClueIndex(TUTORIAL_N, TUTORIAL_N, r) }
        val colIndex = { c: Int -> NonogramLogic.colClueIndex(TUTORIAL_N, TUTORIAL_N, c) }
        val solved = tutorialBoard(TUTORIAL_PICTURE.map { if (it == '1') NonogramLogic.FILLED else NonogramLogic.UNMARKED }
            .joinToString(""))
        val empty = tutorialBoard()
        val row2 = (5..9).toList()
        val afterRow2 = marked(empty, row2.associateWith { NonogramLogic.FILLED })
        val afterRow3 = marked(afterRow2, (10..14).associateWith { NonogramLogic.FILLED })
        val afterMiddle = marked(afterRow3, mapOf(17 to NonogramLogic.FILLED))
        val afterCross = marked(afterMiddle, mapOf(20 to NonogramLogic.CROSSED))
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Each number is a run of filled squares in that row or column, in order. " +
                    "Row 1 reads \"1 1\": a single square, a gap, then another single square.",
                highlight = BoardHighlight(strong = setOf(1, 3, rowIndex(0))),
            ),
            TutorialFrame(
                state = empty,
                caption = "Row 2 reads 5, and the row is five squares wide, so every square is filled. " +
                    "Drag along it to fill the whole row in one sweep.",
                highlight = BoardHighlight(strong = row2.toSet() + rowIndex(1)),
                accepts = only(empty, row2.associateWith { NonogramLogic.FILLED }),
                retry = "Drag along the whole glowing row, left to right, in one sweep.",
                done = "Filled. A row whose numbers use all its width has nowhere to move.",
            ),
            TutorialFrame(
                state = afterRow2,
                caption = "Row 3 reads 5 too. Sweep it the same way.",
                highlight = BoardHighlight(strong = (10..14).toSet() + rowIndex(2)),
                accepts = only(afterRow2, (10..14).associateWith { NonogramLogic.FILLED }),
                retry = "Drag along the whole glowing row in one sweep.",
                done = "Two rows down.",
            ),
            TutorialFrame(
                state = afterRow3,
                caption = "Row 4 reads 3 in five squares. Slide the 3 to the far left, then the far right: " +
                    "the middle square is covered both times, so it is filled. Tap it.",
                highlight = BoardHighlight(strong = setOf(17, rowIndex(3)), soft = (15..19).toSet() - 17),
                accepts = only(afterRow3, mapOf(17 to NonogramLogic.FILLED)),
                retry = "Tap the glowing square once.",
                done = "Filled. Sliding a run both ways shows which squares it cannot avoid.",
            ),
            TutorialFrame(
                state = afterMiddle,
                caption = "Column 1 reads 2, and rows 2 and 3 already give it that 2, so the rest of the column " +
                    "is empty. Choose Cross under the board, then tap the bottom square.",
                highlight = BoardHighlight(strong = setOf(20, colIndex(0)), soft = setOf(0, 5, 10, 15)),
                accepts = only(afterMiddle, mapOf(20 to NonogramLogic.CROSSED)),
                retry = "Switch to Cross under the board, then tap the glowing square.",
                done = "Crossed. A cross is only a note that the square is empty.",
            ),
            TutorialFrame(
                state = afterCross,
                caption = "Your turn: finish the picture. Stuck? Hint shows you which row or column to look at, and why.",
                freePlay = true,
                done = "Solved. Every board works out one line at a time.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [NonogramTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as NonogramState
        val step = NonogramTeacher.teach(s) ?: return null
        val before = s.cells
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == NonogramTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as NonogramState, step) },
            reachedBy = { now ->
                val cells = (now as NonogramState).cells
                step.fills.all { cells[it] == NonogramLogic.FILLED } &&
                    step.crosses.all { cells[it] == NonogramLogic.CROSSED } &&
                    step.clears.all { cells[it] != before[it] }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyStep(s: NonogramState, step: NonogramTeacher.Step): NonogramState {
        val next = s.cells.toCharArray()
        var changed = 0
        fun set(i: Int, mark: Char) {
            if (next[i] != mark) {
                next[i] = mark
                changed++
            }
        }
        step.clears.forEach { set(it, NonogramLogic.UNMARKED) }
        step.fills.forEach { set(it, NonogramLogic.FILLED) }
        step.crosses.forEach { set(it, NonogramLogic.CROSSED) }
        return if (changed == 0) s else s.copy(cells = next.concatToString(), moves = s.moves + changed)
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** How wide a clue number's slot is, and how tall, as fractions of a square. */
    private const val SLOT_W = 0.62f
    private const val SLOT_H = 0.56f

    /** Margin between the clues and the grid, and around the clue block, in squares. */
    private const val CLUE_GAP = 0.18f

    /**
     * Where everything sits, in squares ([u] pixels each): the clue block to the left and above,
     * then the grid. Shared by the board and the home motif so both draw the same thing.
     */
    private class Geometry(
        val u: Float,
        val width: Int,
        val height: Int,
        val rowSlots: Int,
        val colSlots: Int,
    ) {
        val gridX = (rowSlots * SLOT_W + CLUE_GAP) * u
        val gridY = (colSlots * SLOT_H + CLUE_GAP) * u
        val totalW = gridX + width * u
        val totalH = gridY + height * u
    }

    private fun unitsAcross(s: NonogramState, rowSlots: Int) = s.width + rowSlots * SLOT_W + CLUE_GAP

    private fun unitsDown(s: NonogramState, colSlots: Int) = s.height + colSlots * SLOT_H + CLUE_GAP

    /** The clue numbers of every row (then column) as measured text, one layout per number. */
    private fun measureClues(
        measurer: TextMeasurer,
        clues: List<List<Int>>,
        fontPx: Float,
        density: androidx.compose.ui.unit.Density,
    ): List<List<TextLayoutResult>> {
        val style = TextStyle(fontSize = with(density) { fontPx.toSp() }, fontWeight = FontWeight.SemiBold)
        return clues.map { line -> (if (line.isEmpty()) listOf(0) else line).map { measurer.measure(it.toString(), style) } }
    }

    /** What a [Board] draws besides the board's own marks; all of it optional. */
    private class Overlay(
        val highlight: BoardHighlight = BoardHighlight.None,
        val glow: Color = Color.Unspecified,
        val pulse: Float = 1f,
    )

    private fun DrawScope.drawBoard(
        s: NonogramState,
        g: Geometry,
        rowText: List<List<TextLayoutResult>>,
        colText: List<List<TextLayoutResult>>,
        rowDone: BooleanArray,
        colDone: BooleanArray,
        scheme: androidx.compose.material3.ColorScheme,
        fill: Color,
        overlay: Overlay = Overlay(),
    ) {
        val u = g.u
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        val n = s.width * s.height

        fun named(i: Int) = i in highlight.strong || i in highlight.soft

        // Squares.
        for (r in 0 until s.height) {
            for (c in 0 until s.width) {
                val x = g.gridX + c * u
                val y = g.gridY + r * u
                drawRect(scheme.surface, Offset(x, y), Size(u, u))
                when (s.cells[r * s.width + c]) {
                    NonogramLogic.FILLED -> drawRoundRect(
                        fill, Offset(x + u * 0.07f, y + u * 0.07f), Size(u * 0.86f, u * 0.86f), CornerRadius(u * 0.14f),
                    )
                    NonogramLogic.CROSSED -> {
                        val a = u * 0.30f
                        val stroke = maxOf(u * 0.09f, 1.2f)
                        val ink = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                        drawLine(ink, Offset(x + a, y + a), Offset(x + u - a, y + u - a), stroke, StrokeCap.Round)
                        drawLine(ink, Offset(x + u - a, y + a), Offset(x + a, y + u - a), stroke, StrokeCap.Round)
                    }
                }
            }
        }

        // Grid lines, heavier every five squares so a 15-wide board can be counted.
        val thin = maxOf(u * 0.03f, 1f)
        for (i in 0..s.width) {
            val heavy = i % 5 == 0
            drawLine(
                if (heavy) scheme.onSurfaceVariant.copy(alpha = 0.7f) else scheme.outline,
                Offset(g.gridX + i * u, g.gridY), Offset(g.gridX + i * u, g.gridY + s.height * u),
                if (heavy) thin * 2f else thin,
            )
        }
        for (i in 0..s.height) {
            val heavy = i % 5 == 0
            drawLine(
                if (heavy) scheme.onSurfaceVariant.copy(alpha = 0.7f) else scheme.outline,
                Offset(g.gridX, g.gridY + i * u), Offset(g.gridX + s.width * u, g.gridY + i * u),
                if (heavy) thin * 2f else thin,
            )
        }

        // Clues. A line that already shows its numbers fades, the way a ticked-off list does.
        for (r in 0 until s.height) {
            val lit1 = lit && named(n + r)
            if (lit1) {
                drawRoundRect(
                    overlay.glow.copy(alpha = 0.16f * overlay.pulse),
                    Offset(0f, g.gridY + r * u), Size(g.gridX, u), CornerRadius(u * 0.2f),
                )
            }
            val line = rowText[r]
            for ((k, layout) in line.withIndex()) {
                val cx = g.gridX - CLUE_GAP * u - (line.size - 1 - k) * SLOT_W * u - SLOT_W * u / 2f
                val cy = g.gridY + (r + 0.5f) * u
                val alpha = if (lit && !lit1) 0.35f else if (rowDone[r]) 0.4f else 1f
                drawText(layout, scheme.onSurface.copy(alpha = alpha), Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            }
        }
        for (c in 0 until s.width) {
            val lit1 = lit && named(n + s.height + c)
            if (lit1) {
                drawRoundRect(
                    overlay.glow.copy(alpha = 0.16f * overlay.pulse),
                    Offset(g.gridX + c * u, 0f), Size(u, g.gridY), CornerRadius(u * 0.2f),
                )
            }
            val line = colText[c]
            for ((k, layout) in line.withIndex()) {
                val cx = g.gridX + (c + 0.5f) * u
                val cy = g.gridY - CLUE_GAP * u - (line.size - 1 - k) * SLOT_H * u - SLOT_H * u / 2f
                val alpha = if (lit && !lit1) 0.35f else if (colDone[c]) 0.4f else 1f
                drawText(layout, scheme.onSurface.copy(alpha = alpha), Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            }
        }

        if (!lit) return

        // Everything the hint does not name steps back, so a glowing line is findable on 225 squares.
        for (i in 0 until n) {
            if (named(i)) continue
            drawRect(
                scheme.background.copy(alpha = 0.55f),
                Offset(g.gridX + (i % s.width) * u, g.gridY + (i / s.width) * u),
                Size(u, u),
            )
        }

        // A rim just inside the outline of each set, one stroke per square side facing out of it.
        fun rim(set: Set<Int>, width: Float, colour: Color) {
            val h = width / 2
            for (i in set) {
                if (i >= n) continue
                val r = i / s.width
                val c = i % s.width
                val x0 = g.gridX + c * u
                val y0 = g.gridY + r * u
                val x1 = x0 + u
                val y1 = y0 + u
                if (r == 0 || i - s.width !in set) drawLine(colour, Offset(x0, y0 + h), Offset(x1, y0 + h), width)
                if (r == s.height - 1 || i + s.width !in set) drawLine(colour, Offset(x0, y1 - h), Offset(x1, y1 - h), width)
                if (c == 0 || i - 1 !in set) drawLine(colour, Offset(x0 + h, y0), Offset(x0 + h, y1), width)
                if (c == s.width - 1 || i + 1 !in set) drawLine(colour, Offset(x1 - h, y0), Offset(x1 - h, y1), width)
            }
        }
        rim(highlight.soft - highlight.strong, u * 0.07f, overlay.glow.copy(alpha = 0.5f))
        rim(highlight.strong, u * 0.2f, scheme.background.copy(alpha = 0.9f * overlay.pulse))
        rim(highlight.strong, u * 0.11f, overlay.glow.copy(alpha = overlay.pulse))
    }

    private fun linesDone(s: NonogramState, rows: List<List<Int>>, cols: List<List<Int>>): Pair<BooleanArray, BooleanArray> {
        val rowDone = BooleanArray(s.height) { r ->
            NonogramLogic.runs(BooleanArray(s.width) { c -> s.cells[r * s.width + c] == NonogramLogic.FILLED }) == rows[r]
        }
        val colDone = BooleanArray(s.width) { c ->
            NonogramLogic.runs(BooleanArray(s.height) { r -> s.cells[r * s.width + c] == NonogramLogic.FILLED }) == cols[c]
        }
        return rowDone to colDone
    }

    /** A sweep in progress: where it began, where the finger is now, and what it lays down. */
    private class Sweep(val start: Int, val at: Int, val mark: Char, val startedOn: Char)

    /** The squares a sweep covers: the straight line from where it began toward where the finger is. */
    private fun sweptCells(sweep: Sweep, width: Int): List<Int> {
        val r0 = sweep.start / width
        val c0 = sweep.start % width
        val r1 = sweep.at / width
        val c1 = sweep.at % width
        return if (kotlin.math.abs(c1 - c0) >= kotlin.math.abs(r1 - r0)) {
            (minOf(c0, c1)..maxOf(c0, c1)).map { r0 * width + it }
        } else {
            (minOf(r0, r1)..maxOf(r0, r1)).map { it * width + c0 }
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as NonogramState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // The pen is a convenience of the screen, not part of the board: the play screen pushes an
        // undo entry for every state it is handed, so a pen stored in the state would make
        // "switch to Cross" a step to walk back.
        var pen by rememberSaveable(s.solution) { mutableStateOf(NonogramLogic.FILLED) }
        var sweep by remember(s.solution) { mutableStateOf<Sweep?>(null) }

        val rows = remember(s.solution) { NonogramLogic.rowClues(s) }
        val cols = remember(s.solution) { NonogramLogic.colClues(s) }
        val rowSlots = rows.maxOf { maxOf(it.size, 1) }
        val colSlots = cols.maxOf { maxOf(it.size, 1) }

        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        // A rim that breathes is findable at a glance on a 15x15 board. Only runs while something
        // glows, and is read in the draw phase.
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

        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Sized from both axes (CLAUDE.md): a 15x15 board with its clues is wider than a
                // phone's screen allows by height alone on a short one, and the reverse on a tall one.
                val unit = minOf(maxWidth / unitsAcross(s, rowSlots), maxHeight / unitsDown(s, colSlots))
                val unitPx = with(density) { unit.toPx() }
                val g = Geometry(unitPx, s.width, s.height, rowSlots, colSlots)
                val fontPx = unitPx * 0.52f
                val rowText = remember(s.solution, unitPx) { measureClues(measurer, rows, fontPx, density) }
                val colText = remember(s.solution, unitPx) { measureClues(measurer, cols, fontPx, density) }

                val shown = sweep?.let { w -> s.sweep(sweptCells(w, s.width), w.mark, w.startedOn) } ?: s
                val (rowDone, colDone) = linesDone(shown, rows, cols)
                val fill = Color(accent)

                fun cellAt(offset: Offset): Int {
                    val c = (offset.x / unitPx).toInt().coerceIn(0, s.width - 1)
                    val r = (offset.y / unitPx).toInt().coerceIn(0, s.height - 1)
                    return r * s.width + c
                }

                Box(Modifier.size(g.totalW.asDp(density), g.totalH.asDp(density))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawBoard(
                            shown, g, rowText, colText, rowDone, colDone, scheme, fill,
                            Overlay(highlight, glow, pulse.value),
                        )
                    }
                    // Taps and sweeps are read over the grid alone, in its own coordinates; the
                    // clues beside it are not part of the play area.
                    Box(
                        Modifier
                            .offset(g.gridX.asDp(density), g.gridY.asDp(density))
                            .size((s.width * unitPx).asDp(density), (s.height * unitPx).asDp(density))
                            .highlightGrid(s.width, s.height)
                            .pointerInput(s, interactive, pen) {
                                if (!interactive) return@pointerInput
                                detectTapGestures { offset -> onState(s.tap(cellAt(offset), pen)) }
                            }
                            .pointerInput(s, interactive, pen) {
                                if (!interactive) return@pointerInput
                                // The overload that hands over the *down*: Compose's touch slop on
                                // the web is wider than a 15x15 square, so the plain one would start
                                // every sweep a square or two along from where the finger landed.
                                detectDragGestures(
                                    orientationLock = null,
                                    onDragStart = { down, _, _ ->
                                        val start = cellAt(down.position)
                                        sweep = Sweep(start, start, s.sweepMark(start, pen), s.markAt(start))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        sweep = sweep?.let { Sweep(it.start, cellAt(change.position), it.mark, it.startedOn) }
                                    },
                                    onDragEnd = { _ ->
                                        val w = sweep
                                        sweep = null
                                        if (w != null) {
                                            val next = s.sweep(sweptCells(w, s.width), w.mark, w.startedOn)
                                            if (next !== s) onState(next)
                                        }
                                    },
                                    onDragCancel = { sweep = null },
                                )
                            }
                    )
                }
            }

            PenRow(pen, enabled = interactive, onPen = { pen = it })
        }
    }

    private fun Float.asDp(density: androidx.compose.ui.unit.Density): Dp = with(density) { this@asDp.toDp() }

    /** Fill or Cross. Kept clear of the hint popover: the player reaches for it to make a hinted move. */
    @Composable
    private fun PenRow(pen: Char, enabled: Boolean, onPen: (Char) -> Unit) {
        val scheme = MaterialTheme.colorScheme
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).keepClear(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            for (option in listOf(NonogramLogic.FILLED, NonogramLogic.CROSSED)) {
                val chosen = pen == option
                Row(
                    Modifier
                        .width(124.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (chosen) Color(accent).copy(alpha = 0.22f) else scheme.surface)
                        .border(
                            if (chosen) 2.dp else 1.dp,
                            if (chosen) Color(accent) else scheme.outline,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable(enabled = enabled) { onPen(option) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        if (option == NonogramLogic.FILLED) {
                            drawRoundRect(Color(accent), Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), CornerRadius(size.width * 0.18f))
                        } else {
                            val a = size.width * 0.2f
                            val ink = scheme.onSurface.copy(alpha = 0.85f)
                            drawLine(ink, Offset(a, a), Offset(size.width - a, size.height - a), 3f, StrokeCap.Round)
                            drawLine(ink, Offset(size.width - a, a), Offset(a, size.height - a), 3f, StrokeCap.Round)
                        }
                    }
                    Text(
                        if (option == NonogramLogic.FILLED) "Fill" else "Cross",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                }
            }
        }
    }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed little board for the home grid, drawn by the board's own code so the tile shows the
     * real thing: numbers, filled squares, a cross. The picture is a legal one: its clues are the
     * clues of what is filled in. Never calls `generate()`.
     */
    private val motif = NonogramState(
        4, 4, "0110" + "1111" + "1001" + "0110",
        cells = ".##." + "####" + "#xx#" + "....",
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val rows = remember { NonogramLogic.rowClues(motif) }
        val cols = remember { NonogramLogic.colClues(motif) }
        val rowSlots = rows.maxOf { maxOf(it.size, 1) }
        val colSlots = cols.maxOf { maxOf(it.size, 1) }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth / unitsAcross(motif, rowSlots), maxHeight / unitsDown(motif, colSlots))
            val unitPx = with(density) { unit.toPx() }
            val g = Geometry(unitPx, motif.width, motif.height, rowSlots, colSlots)
            val rowText = remember(unitPx) { measureClues(measurer, rows, unitPx * 0.52f, density) }
            val colText = remember(unitPx) { measureClues(measurer, cols, unitPx * 0.52f, density) }
            val (rowDone, colDone) = remember { linesDone(motif, rows, cols) }
            Canvas(Modifier.size(g.totalW.asDp(density), g.totalH.asDp(density))) {
                drawBoard(motif, g, rowText, colText, rowDone, colDone, scheme, Color(accent))
            }
        }
    }
}
