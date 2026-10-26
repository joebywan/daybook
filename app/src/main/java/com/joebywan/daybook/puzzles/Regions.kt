package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.digitKeyAction
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.placementDeduction
import com.joebywan.daybook.ui.PAD_GAP
import com.joebywan.daybook.ui.DigitPadRow
import com.joebywan.daybook.ui.padBoardSide
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.serialization.Serializable
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel

/**
 * A Regions board in play. [region] (the region id of each square) and [solution] are fixed for the
 * board; [cells] holds 0 for an empty square and is what the player changes. Squares are numbered
 * row by row.
 */
@Serializable
data class RegionsState(
    val size: Int,
    val region: List<Int>,
    val givens: List<Boolean>,
    val cells: List<Int>,
    val solution: List<Int>,
    val selected: Int? = null,
    override val moves: Int = 0,
) : PuzzleState {

    internal val layout: RegionsLayout get() = RegionsLayout(size, region)

    /** The rules, not a comparison with [solution]. */
    override val solved: Boolean get() = RegionsLogic.isSolved(layout, cells)

    /** Filled squares that repeat in their region, touch an equal digit, or exceed their region's size. */
    fun conflicts(): Set<Int> = RegionsLogic.conflicts(layout, cells)

    /** The largest region on the board: the pad's last key. */
    val digits: Int get() = layout.digits

    /** Sets a digit, or clears the square with 0. `this` when nothing changes (a given, or the same digit). */
    fun withCell(index: Int, value: Int): RegionsState =
        if (givens[index] || cells[index] == value) this
        else copy(cells = cells.toMutableList().also { it[index] = value }, moves = moves + 1)

    fun select(index: Int): RegionsState = copy(selected = index)
}

/** Regions: fill each region with 1 up to its size, and never let equal digits touch. */
object Regions : PuzzleType {

    override val id = "regions"
    override val displayName = "Regions"
    override val tagline = "Fill each region, equal digits never touch"
    override val accent = 0xFF675EC9
    override val rules = listOf(
        "A region of N squares holds the digits 1 to N, each once.",
        "Equal digits may not touch, not even at a corner.",
        "Tap a square, then tap a digit. Tap the digit again to clear it.",
        "A repeat, or a touching pair, is shown in red as you go.",
    )

    override val keyboardHelp = listOf("Arrows move, 1-5 place a digit, Backspace clears.")

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        RegionsLogic.generateVerified(seed, difficulty) ?: RegionsLogic.lastResort(difficulty)

    // ---- colour ------------------------------------------------------------------------------

    /** A hue index per region, neighbours different: the fewest of 3 to 5 colours. Pure in the layout. */
    internal fun regionColours(layout: RegionsLayout): IntArray {
        val adj = List(layout.count) { BooleanArray(layout.count) }
        for ((a, b) in layout.adjacentRegions()) { adj[a][b] = true; adj[b][a] = true }
        return BoardHues.regionColours(adj, minColours = 3)
    }

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as RegionsState
        val step = RegionsTeacher.teach(s) ?: return null
        return placementDeduction(
            s, step,
            mistake = step.technique == RegionsTeacher.MISTAKE,
            fallback = step.technique == RegionsTeacher.FALLBACK,
            valueAt = { t, cell -> t.cells[cell] },
            place = { t, cell, digit -> t.withCell(cell, digit).select(cell) },
        )
    }

    // ---- walkthrough -----------------------------------------------------------------------

    /**
     * Four regions of four, the board's quadrants:
     * ```
     * A A B B      3 1 3 1
     * A A B B      2 4 2 4
     * C C D D      3 1 3 1
     * C C D D      4 2 4 2
     * ```
     * Four digits are given. Square 9 touches a 2, a 3 and a 4, which is the first thing the walkthrough teaches.
     */
    internal val TUTORIAL_REGION = listOf(0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 3, 3, 2, 2, 3, 3)
    internal val TUTORIAL_SOLUTION = listOf(3, 1, 3, 1, 2, 4, 2, 4, 3, 1, 3, 1, 4, 2, 4, 2)
    internal val TUTORIAL_GIVEN = setOf(2, 4, 10, 14)

    internal fun tutorialBoard(entered: Map<Int, Int> = emptyMap(), selected: Int? = null): RegionsState =
        RegionsState(
            size = 4,
            region = TUTORIAL_REGION,
            givens = TUTORIAL_SOLUTION.indices.map { it in TUTORIAL_GIVEN },
            cells = TUTORIAL_SOLUTION.mapIndexed { i, v -> if (i in TUTORIAL_GIVEN) v else entered[i] ?: 0 },
            solution = TUTORIAL_SOLUTION,
            selected = selected,
        )

    /** The square the walkthrough fills first (a 1: it touches a 2, a 3 and a 4), and the one it fills wrongly. */
    internal const val TUTORIAL_FIRST = 9
    internal const val TUTORIAL_WRONG = 8

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val placed = mapOf(TUTORIAL_FIRST to 1)
        val done = tutorialBoard(placed)
        val touching = start.layout.around[2].toSet()
        val place = TutorialPath(
            { it is RegionsState && it.cells == start.cells && it.selected == TUTORIAL_FIRST },
            { it is RegionsState && it.cells == done.cells },
        )
        val clash = TutorialPath(
            { it is RegionsState && it.cells == done.cells && it.selected == TUTORIAL_WRONG },
            { it is RegionsState && it.cells == tutorialBoard(placed + (TUTORIAL_WRONG to 1)).cells },
            // Back where it started, but by way of the 1: only a move counted since says so.
            { it is RegionsState && it.cells == done.cells && it.moves > 0 },
        )
        listOf(
            TutorialFrame(
                state = start,
                caption = "Each region holds the digits from 1 up to its size, once each. These have four squares, so 1 to 4. Bold digits are given.",
                highlight = BoardHighlight(soft = setOf(0, 1, 4, 5)),
            ),
            TutorialFrame(
                state = start,
                caption = "Equal digits may never touch, not even at a corner. This 3 rules out a 3 in every square around it.",
                highlight = BoardHighlight(strong = setOf(2), soft = touching),
            ),
            TutorialFrame(
                state = start,
                caption = "This square touches a 2, a 3 and a 4, so none of those can go here, and its region " +
                    "needs 1 to 4. That leaves 1. Tap the square, then 1 below.",
                glow = { s ->
                    val strong = if (place.stage(s) < 0) TUTORIAL_FIRST else RegionsTeacher.pad(1)
                    BoardHighlight(strong = setOf(strong), soft = setOf(4, 10, 14))
                },
                steps = place.steps,
                retry = "Tap the glowing square, then the 1.",
                done = "Touching digits settled it.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_FIRST),
                caption = "A digit that breaks a rule turns red. Put a 1 in the glowing square: it touches the " +
                    "other 1. Then tap 1 again to clear it.",
                glow = { s ->
                    when (clash.stage(s)) {
                        -1 -> BoardHighlight(strong = setOf(TUTORIAL_WRONG), soft = setOf(TUTORIAL_FIRST))
                        0 -> BoardHighlight(strong = setOf(RegionsTeacher.pad(1)), soft = setOf(TUTORIAL_FIRST))
                        else -> BoardHighlight(strong = setOf(TUTORIAL_WRONG, RegionsTeacher.pad(1)), soft = setOf(TUTORIAL_FIRST), warning = true)
                    }
                },
                steps = clash.steps,
                retry = "Tap the glowing square, then the 1, then the 1 again.",
                done = "Cleared. Tapping the digit again takes it out.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG),
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as RegionsState).let { if (it.selected == null) it else it.copy(selected = null) }

    // ---- drawing ---------------------------------------------------------------------------

    /** Region walls over a grid of [slot]-sized squares: thin between squares of a region, thick between regions and round the edge. */
    private fun DrawScope.walls(n: Int, region: List<Int>, slot: Float, thin: Color, thick: Color, thinW: Float, thickW: Float) {
        // Clipped to the grid: a thick wall's square end would otherwise poke out past the edge.
        clipRect(0f, 0f, slot * n, slot * n) {
            for (i in 0 until n * n) {
                val x = (i % n) * slot
                val y = (i / n) * slot
                if (i % n < n - 1 && region[i] == region[i + 1]) drawLine(thin, Offset(x + slot, y), Offset(x + slot, y + slot), thinW)
                if (i / n < n - 1 && region[i] == region[i + n]) drawLine(thin, Offset(x, y + slot), Offset(x + slot, y + slot), thinW)
            }
            for (i in 0 until n * n) {
                val x = (i % n) * slot
                val y = (i / n) * slot
                if (i % n < n - 1 && region[i] != region[i + 1]) drawLine(thick, Offset(x + slot, y), Offset(x + slot, y + slot), thickW, StrokeCap.Square)
                if (i / n < n - 1 && region[i] != region[i + n]) drawLine(thick, Offset(x, y + slot), Offset(x + slot, y + slot), thickW, StrokeCap.Square)
            }
            drawRect(thick, style = Stroke(thickW * 2), topLeft = Offset.Zero, size = Size(slot * n, slot * n))
        }
    }

    // ---- home-grid motif -------------------------------------------------------------------

    /** The walkthrough's 4x4 board, a legal board in itself; the digits shown are a few of its answer. */
    private val PREVIEW_DIGITS = listOf(3, 0, 3, 1, 2, 4, 0, 0, 0, 1, 3, 0, 4, 0, 4, 2)
    private val PREVIEW_GIVEN = setOf(2, 4, 10, 14)

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier) {
            val slot = minOf(maxWidth, maxHeight) / 4
            val dark = BoardHues.isDark(scheme)
            val hues = listOf(268f, 34f, 175f, 215f)
            for (i in 0 until 16) {
                val fill = BoardHues.fill(hues[TUTORIAL_REGION[i]], dark)
                Box(
                    Modifier
                        .padding(start = slot * (i % 4), top = slot * (i / 4))
                        .size(slot)
                        .background(fill),
                    contentAlignment = Alignment.Center,
                ) {
                    if (PREVIEW_DIGITS[i] != 0) {
                        Text(
                            PREVIEW_DIGITS[i].toString(),
                            fontSize = (slot.value * 0.6f).sp,
                            fontWeight = if (i in PREVIEW_GIVEN) FontWeight.Bold else FontWeight.Normal,
                            color = BoardHues.onFill(fill),
                        )
                    }
                }
            }
            Canvas(Modifier.size(slot * 4)) {
                walls(4, TUTORIAL_REGION, slot.toPx(), scheme.onSurface.copy(alpha = 0.2f), scheme.onSurface.copy(alpha = 0.8f), 1.dp.toPx(), 2.5.dp.toPx())
            }
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as RegionsState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val layout = remember(s.region) { s.layout }
        val colours = remember(s.region) { regionColours(layout) }
        val conflicts = s.conflicts()
        val dark = BoardHues.isDark(scheme)
        val (_, hueB) = BoardHues.pair(s.solution.hashCode())
        val ink = BoardHues.ink(hueB, dark)
        val selectedValue = s.selected?.let { s.cells[it] } ?: 0
        val (highlight, glow, pulse) = rememberHintPaint()
        val digits = layout.digits
        // A region is finished when it holds 1..its size, none of them touching an equal digit.
        val units = remember(s.region) { s.region.indices.groupBy { s.region[it] }.values.map { it.toIntArray() } }
        val feel = rememberMoveFeel(s.cells, units) { u ->
            val r = units[u]
            r.map { s.cells[it] }.toSet() == (1..r.size).toSet() && r.none { it in conflicts }
        }

        // Sized from both axes (CLAUDE.md): the grid leaves room for the digit pad under it.
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .boardKeys(interactive) { key, _, repeat ->
                    val action = digitKeyAction(key, digits) ?: return@boardKeys false
                    if (!repeat || action is DigitKeyAction.Move) s.applyKey(action)?.let(onState)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            val side = padBoardSide()
            val slot = side / n
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side).highlightGrid(n, n)) {
                    for (i in 0 until n * n) {
                        val look = highlight.look(i)
                        val regionFill = BoardHues.fill(BoardHues.REGION_HUES[colours[s.region[i]]], dark)
                        // A selected square, and every square holding its digit, go plain so they stand out from the colours.
                        val plain = s.selected == i || (selectedValue != 0 && s.cells[i] == selectedValue)
                        // A square in conflict is filled red (and edged red), so a wrong digit is never only a colour on a colour.
                        val fill = when {
                            i in conflicts -> scheme.error.copy(alpha = 0.8f).compositeOver(if (plain) scheme.surface else regionFill)
                            plain -> scheme.surface
                            else -> regionFill
                        }
                        Box(
                            Modifier
                                .padding(start = slot * (i % n), top = slot * (i / n))
                                .size(slot)
                                .moveFeel(feel, i, Color(accent))
                                .dimmed(look)
                                .ring(look, glow, pulse, corner = 2.dp)
                                .background(fill)
                                .then(if (i in conflicts) Modifier.border(2.dp, scheme.error) else Modifier)
                                .then(if (s.selected == i) Modifier.border(3.dp, scheme.onBackground) else Modifier)
                                .clickable(enabled = interactive) { onState(s.select(i)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (s.cells[i] != 0) {
                                val own = if (BoardHues.contrast(ink, fill) >= 4.5f) ink else BoardHues.onFill(fill)
                                Text(
                                    s.cells[i].toString(),
                                    fontSize = (slot.value * 0.6f).sp,
                                    fontWeight = if (s.givens[i]) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        i in conflicts || s.givens[i] -> BoardHues.onFill(fill)
                                        else -> own
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        walls(n, s.region, slot.toPx(), scheme.onSurface.copy(alpha = 0.2f), scheme.onSurface.copy(alpha = 0.8f), 1.dp.toPx(), 3.dp.toPx())
                    }
                }

                Spacer(Modifier.height(PAD_GAP))

                DigitPadRow(side, digits, RegionsTeacher::pad, highlight, glow, pulse, enabled = interactive && s.selected != null) { digit ->
                    val next = s.enter(s.selected ?: return@DigitPadRow, digit)
                    if (next !== s) onState(next)
                }
            }
        }
    }
}
