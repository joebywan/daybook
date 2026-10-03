package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import kotlinx.serialization.Serializable

/**
 * An Inequality square in play. [signs] and [solution] are fixed for the board; [cells] holds 0 for
 * an empty square and is what the player changes. Squares are numbered row by row.
 */
@Serializable
data class InequalityState(
    val size: Int,
    val givens: List<Boolean>,
    val cells: List<Int>,
    val solution: List<Int>,
    val signs: List<Sign>,
    val selected: Int? = null,
    override val moves: Int = 0,
) : PuzzleState {

    /** The rules, not a comparison with [solution]: a full Latin square with every sign true. */
    override val solved: Boolean get() = InequalityLogic.isSolved(size, cells, signs)

    /** Filled squares that repeat a digit in their line, or end a sign that is false. */
    fun conflicts(): Set<Int> =
        InequalityLogic.clashes(size, cells) +
            InequalityLogic.brokenSigns(cells, signs).flatMap { listOf(signs[it].lo, signs[it].hi) }

    /** Sets a digit, or clears the square with 0. `this` when nothing changes (a given, or the same digit). */
    fun withCell(index: Int, value: Int): InequalityState =
        if (givens[index] || cells[index] == value) this
        else copy(cells = cells.toMutableList().also { it[index] = value }, moves = moves + 1)

    fun select(index: Int): InequalityState = copy(selected = index)
}

private val PAD_HEIGHT = 48.dp
private val PAD_GAP = 18.dp

/** A square's side as a share of its slot; the rest is the gap a sign sits in. Tuned by rendering. */
private const val CELL = 0.66f

/** Inequality: a Latin square with `<` and `>` signs between some neighbours. */
object Inequality : PuzzleType {

    override val id = "inequality"
    override val displayName = "Inequality"
    override val tagline = "Each digit once per line, signs point to the smaller"
    override val accent = 0xFF3FA89A
    override val rules = listOf(
        "Fill every square with a digit from 1 to the size of the grid.",
        "No digit may repeat in a row or a column.",
        "A sign between two squares points at the smaller one: the narrow end is the smaller digit.",
        "Tap a square, then tap a digit. Tap the digit again to clear it.",
        "A repeat, or a sign that is false, is shown in red as you go.",
    )

    override val keyboardHelp = listOf("Arrows move, 1-6 place a digit, Backspace clears.")

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: InequalityLogic.lastResort(difficulty)

    /** The board when its answer was proved, null otherwise. What `FallbackTest` asserts on. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): InequalityState? =
        InequalityLogic.generateVerified(seed, difficulty)

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as InequalityState
        val step = InequalityTeacher.teach(s) ?: return null
        val cell = step.cell
        val digit = step.digit
        val mistake = step.technique == InequalityTeacher.MISTAKE
        val wrong = s.cells[cell]
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = setOf(cell),
            mistake = mistake,
            fallback = step.technique == InequalityTeacher.FALLBACK,
            applyTo = { now ->
                val t = now as InequalityState
                if (t.cells[cell] == digit) t else t.withCell(cell, digit).select(cell)
            },
            reachedBy = { now ->
                val t = now as InequalityState
                if (mistake) t.cells[cell] != wrong else t.cells[cell] == digit
            },
        )
    }

    // ---- walkthrough -----------------------------------------------------------------------

    /** A 4x4 square with an intercalate (the top-left 2x2), so only a sign can say which of 1, 2 goes where. */
    internal val TUTORIAL_SOLUTION = listOf(
        1, 2, 3, 4,
        2, 1, 4, 3,
        3, 4, 1, 2,
        4, 3, 2, 1,
    )
    internal val TUTORIAL_OPEN = setOf(0, 1, 4, 5, 14, 15)

    /** The first sign is the one that matters: square 0 is smaller than square 1. */
    internal val TUTORIAL_SIGNS = listOf(Sign(0, 1), Sign(2, 6), Sign(5, 4), Sign(10, 11))

    internal fun tutorialBoard(entered: Map<Int, Int> = emptyMap(), selected: Int? = null): InequalityState =
        InequalityState(
            size = 4,
            givens = TUTORIAL_SOLUTION.indices.map { it !in TUTORIAL_OPEN },
            cells = TUTORIAL_SOLUTION.mapIndexed { i, v -> if (i in TUTORIAL_OPEN) entered[i] ?: 0 else v },
            solution = TUTORIAL_SOLUTION,
            signs = TUTORIAL_SIGNS,
            selected = selected,
        )

    /** Where the walkthrough's wrong digit goes: a 1 beside the 1 already in column 3. */
    private const val TUTORIAL_WRONG = 14

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val placed = mapOf(0 to 1)
        val signAt = 16 // n*n + index 0 in TUTORIAL_SIGNS
        listOf(
            TutorialFrame(
                state = start,
                caption = "Fill the grid with 1 to 4 so no digit repeats in any row or column. The bold digits are given.",
                highlight = BoardHighlight(strong = setOf(8, 9, 10, 11)),
            ),
            TutorialFrame(
                state = start,
                caption = "A sign sits between two squares and points at the smaller one. This one says the left square is smaller than the right.",
                highlight = BoardHighlight(strong = setOf(signAt), soft = setOf(0, 1)),
            ),
            TutorialFrame(
                state = start,
                caption = "These four squares hold only 1s and 2s, and rows and columns alone can't say which goes where. Tap the top-left one.",
                highlight = BoardHighlight(strong = setOf(0), soft = setOf(1, 4, 5)),
                accepts = { next -> next is InequalityState && next.cells == start.cells && next.selected == 0 },
                retry = "Tap the glowing square.",
                done = "Selected.",
            ),
            TutorialFrame(
                state = tutorialBoard(selected = 0),
                caption = "It's the smaller of the pair, so it can't be the 2. Tap 1 below.",
                highlight = BoardHighlight(strong = setOf(0, InequalityTeacher.pad(1)), soft = setOf(signAt, 1)),
                accepts = { next -> next is InequalityState && next.cells == tutorialBoard(placed).cells },
                retry = "Tap the 1 in the row of digits below.",
                done = "The sign settled the rest of that corner.",
            ),
            TutorialFrame(
                state = tutorialBoard(placed + (TUTORIAL_WRONG to 1), selected = TUTORIAL_WRONG),
                caption = "A digit that breaks a rule turns red: this 1 repeats the 1 in its column. It's selected, so tap 1 again to clear it.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_WRONG, InequalityTeacher.pad(1)), soft = setOf(10), warning = true),
                accepts = { next -> next is InequalityState && next.cells == tutorialBoard(placed).cells },
                retry = "Tap the 1 in the row of digits below.",
                done = "Cleared.",
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
        (state as InequalityState).let { if (it.selected == null) it else it.copy(selected = null) }

    // ---- drawing ---------------------------------------------------------------------------

    /** What [LocalBoardHighlight] asks of one square, key or sign. */
    private class Look(val strong: Boolean, val soft: Boolean, val dim: Boolean)

    private fun BoardHighlight.look(index: Int, dims: Boolean = true): Look {
        val strong = index in this.strong
        val soft = !strong && index in this.soft
        return Look(strong, soft, dims && !isEmpty && !strong && !soft)
    }

    /** A ring just inside the square, breathing for strong and quiet for soft; the pulse is read at draw time. */
    private fun Modifier.ring(look: Look, glow: Color, pulse: State<Float>, corner: Float): Modifier =
        if (!look.strong && !look.soft) this
        else drawWithContent {
            drawContent()
            val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
            drawRoundRect(
                if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f),
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner.dp.toPx()),
                style = Stroke(w),
            )
        }

    /**
     * A chevron centred on ([cx], [cy]) whose narrow end is the smaller digit: `<` when the smaller
     * square is on the left, `>` on the right, and the same turned up or down between rows.
     */
    private fun DrawScope.chevron(cx: Float, cy: Float, vertical: Boolean, loFirst: Boolean, slot: Float, color: Color, width: Float) {
        val reach = slot * 0.08f
        val span = slot * 0.15f
        val path = Path()
        if (!vertical) {
            val tip = if (loFirst) cx - reach else cx + reach
            val back = if (loFirst) cx + reach else cx - reach
            path.moveTo(back, cy - span); path.lineTo(tip, cy); path.lineTo(back, cy + span)
        } else {
            val tip = if (loFirst) cy - reach else cy + reach
            val back = if (loFirst) cy + reach else cy - reach
            path.moveTo(cx - span, back); path.lineTo(cx, tip); path.lineTo(cx + span, back)
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** Where sign [s] is drawn on an [n]-wide grid of [slot]-sized slots: centre and orientation. */
    private fun DrawScope.drawSign(n: Int, s: Sign, slot: Float, color: Color, width: Float) {
        val a = minOf(s.lo, s.hi)
        val b = maxOf(s.lo, s.hi)
        val vertical = b - a != 1
        val cx = if (vertical) (a % n + 0.5f) * slot else (b % n) * slot
        val cy = if (vertical) (b / n) * slot else (a / n + 0.5f) * slot
        chevron(cx, cy, vertical, loFirst = s.lo == a, slot = slot, color = color, width = width)
    }

    // ---- home-grid motif -------------------------------------------------------------------

    /** A legal 3x3 corner of a 4x4 square (rows 1 3 4 / 3 4 1 / 4 1 2), with the signs its digits make true. */
    private val PREVIEW_DIGITS = listOf(1, 0, 0, 0, 4, 0, 0, 0, 2)
    private val PREVIEW_GIVEN = listOf(true, false, false, false, true, false, false, false, false)
    private val PREVIEW_SIGNS = listOf(Sign(0, 1), Sign(5, 4), Sign(0, 3), Sign(5, 2))

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier) {
            val slot = minOf(maxWidth, maxHeight) / 3
            val cell = slot * CELL
            val ink = scheme.onSurface.copy(alpha = 0.75f)
            for (i in 0 until 9) {
                Box(
                    Modifier
                        .padding(start = slot * (i % 3) + (slot - cell) / 2, top = slot * (i / 3) + (slot - cell) / 2)
                        .size(cell)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == 8) Color(accent).copy(alpha = 0.40f) else scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (PREVIEW_DIGITS[i] != 0) {
                        Text(
                            PREVIEW_DIGITS[i].toString(),
                            fontSize = (cell.value * 0.6f).sp,
                            fontWeight = if (PREVIEW_GIVEN[i]) FontWeight.Bold else FontWeight.Normal,
                            color = if (PREVIEW_GIVEN[i]) scheme.onSurface else Color(accent),
                        )
                    }
                }
            }
            Canvas(Modifier.size(slot * 3)) {
                for (s in PREVIEW_SIGNS) drawSign(3, s, slot.toPx(), ink, slot.toPx() * 0.06f)
            }
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as InequalityState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val conflicts = s.conflicts()
        val broken = InequalityLogic.brokenSigns(s.cells, s.signs)
        val selectedValue = s.selected?.let { s.cells[it] } ?: 0
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

        // Sized from both axes (CLAUDE.md): the grid leaves room for the digit pad under it.
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .boardKeys(interactive) { key, _, repeat ->
                    val action = inequalityKeyAction(key, n) ?: return@boardKeys false
                    if (!repeat || action is InequalityKeyAction.Move) s.applyKey(action)?.let(onState)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            val side = if (constraints.hasBoundedHeight) {
                minOf(maxWidth, (maxHeight - PAD_GAP - PAD_HEIGHT).coerceAtLeast(0.dp))
            } else {
                maxWidth
            }
            val slot = side / n
            val cell = slot * CELL
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side).highlightGrid(n, n)) {
                    for (i in 0 until n * n) {
                        val look = highlight.look(i)
                        Box(
                            Modifier
                                .padding(start = slot * (i % n) + (slot - cell) / 2, top = slot * (i / n) + (slot - cell) / 2)
                                .size(cell)
                                .then(if (look.dim) Modifier.alpha(0.3f) else Modifier)
                                .ring(look, glow, pulse, corner = 8f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        s.selected == i -> Color(accent).copy(alpha = 0.40f)
                                        selectedValue != 0 && s.cells[i] == selectedValue -> Color(accent).copy(alpha = 0.20f)
                                        else -> scheme.surfaceVariant
                                    },
                                )
                                .clickable(enabled = interactive) { onState(s.select(i)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (s.cells[i] != 0) {
                                Text(
                                    s.cells[i].toString(),
                                    fontSize = (cell.value * 0.6f).sp,
                                    fontWeight = if (s.givens[i]) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        i in conflicts -> scheme.error
                                        s.givens[i] -> scheme.onSurface
                                        else -> Color(accent)
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val px = slot.toPx()
                        for ((k, sign) in s.signs.withIndex()) {
                            val look = highlight.look(n * n + k)
                            val colour = when {
                                k in broken -> scheme.error
                                look.strong -> glow.copy(alpha = pulse.value)
                                look.soft -> glow.copy(alpha = 0.7f)
                                look.dim -> scheme.onSurface.copy(alpha = 0.25f)
                                else -> scheme.onSurface.copy(alpha = 0.8f)
                            }
                            drawSign(n, sign, px, colour, px * (if (look.strong) 0.07f else 0.05f))
                        }
                    }
                }

                Spacer(Modifier.height(PAD_GAP))

                Row(
                    Modifier.width(side).keepClear(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..n).forEach { digit ->
                        val remaining = n - s.cells.count { it == digit }
                        val at = s.selected
                        Box(
                            Modifier
                                .weight(1f)
                                .height(PAD_HEIGHT)
                                .highlightAnchor(InequalityTeacher.pad(digit))
                                .ring(highlight.look(InequalityTeacher.pad(digit), dims = false), glow, pulse, corner = 10f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (remaining == 0) scheme.surfaceVariant else scheme.surface)
                                .clickable(enabled = interactive && at != null) {
                                    val next = s.enter(at ?: return@clickable, digit)
                                    if (next !== s) onState(next)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                digit.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (remaining == 0) scheme.outline else scheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
