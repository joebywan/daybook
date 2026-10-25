package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay
import com.joebywan.daybook.ui.lineUnits
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberMoveFeel
import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.PenKeyAction
import com.joebywan.daybook.core.penKeyAction
import com.joebywan.daybook.ui.Sweep
import com.joebywan.daybook.ui.swept
import com.joebywan.daybook.ui.penKeys
import com.joebywan.daybook.ui.penGrid
import com.joebywan.daybook.ui.PenRow
import androidx.compose.ui.geometry.Rect
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.squareRim

/** How long a line must stay impossible before its target turns red, so a sweep passing through is not shouted at. */
private const val SETTLE_MILLIS = 1000L

/** X or R removes, K or O keeps; the other keys are every pen board's ([penKeyAction]). */
private val TALLY_KEYS = mapOf(Key.X to TallyLogic.REMOVED, Key.R to TallyLogic.REMOVED, Key.K to TallyLogic.KEPT, Key.O to TallyLogic.KEPT)

/** What a physical key asks of a Tally board. */
internal fun tallyKeyAction(key: Key, shift: Boolean): PenKeyAction? = penKeyAction(key, shift, TALLY_KEYS)

/**
 * Tally: a square of digits with a target at the end of every row and under every column. Remove
 * numbers until what is left in every line adds up to its target. Exactly one answer.
 *
 * Every board is dealt only if a person's ladder finishes it ([TallyLogic.accepts]: the ways one
 * line can make its target, and on Expert a short what-if) and an exhaustive count proves one
 * answer, so the hints teach that reasoning and never run out ([TallyTeacher]).
 *
 * Tiers differ in size (5x5, 6x6, 7x7), and Expert boards also need at least one what-if.
 */
object Tally : PuzzleType {

    override val id = "tally"
    override val displayName = "Tally"
    override val tagline = "Keep the numbers that make every sum"
    override val accent = 0xFF43B14E

    override val rules = listOf(
        "The number beside each row, and under each column, is its target.",
        "Remove numbers so that what is left in every row and every column adds up to its target.",
        "Pick Remove or Keep under the board and tap numbers. Drag along a row or column to sweep several at once.",
        "Keep circles a number you are sure of. It is only a note: a circled number counts exactly like any other left in.",
        "Solved when every row and column adds up. Every board has one answer, found without guessing.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, X removes, K circles, Space cycles, Backspace clears; Shift+arrow carries a mark along.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: TallyLogic.lastResort(difficulty)

    internal fun generateVerified(seed: Long, difficulty: Difficulty): TallyState? =
        TallyLogic.generateVerified(seed, difficulty)

    /** The ways of a line or a what-if, as the tier demands ([TallyLogic.depth]); null for the shortcuts alone. */
    override fun neededTechnique(state: PuzzleState): String? = when (TallyLogic.depth(state as TallyState)) {
        TallyLogic.WAYS -> TallyTeacher.WAYS
        TallyLogic.WHAT_IF -> TallyTeacher.WHAT_IF
        else -> null
    }

    // ---- the walkthrough ----------------------------------------------------------------------

    /**
     * Four by four, one answer, finished by the lines alone. Row 1 needs 7 and holds a 9; column 4
     * needs 3 and holds two 9s, one above the other; once those go, row 2 is 4 over its 6 and its 5
     * cannot be the one removed.
     */
    internal const val TUTORIAL_DIGITS = "1962" + "4519" + "3449" + "7633"
    internal const val TUTORIAL_KEEP = "1010" + "0110" + "1110" + "1101"
    private const val TUTORIAL_N = 4

    private fun tutorialBoard(marks: String = ".".repeat(TUTORIAL_N * TUTORIAL_N)) =
        TallyState(TUTORIAL_N, TUTORIAL_DIGITS, TUTORIAL_KEEP, marks)

    private fun marked(base: TallyState, changes: Map<Int, Char>): TallyState {
        val next = base.marks.toCharArray()
        changes.forEach { (i, c) -> next[i] = c }
        return base.copy(marks = next.concatToString())
    }

    /** Accepts exactly [base] with [changes] made and nothing else (Nonogram's rule). */
    private fun only(base: TallyState, changes: Map<Int, Char>): (PuzzleState) -> Boolean = { next ->
        next is TallyState && next.marks == marked(base, changes).marks
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val n = TUTORIAL_N
        val row = { r: Int -> TallyLogic.rowTargetIndex(n, r) }
        val col = { c: Int -> TallyLogic.colTargetIndex(n, c) }
        val solved = tutorialBoard(TUTORIAL_KEEP.map { if (it == '1') TallyLogic.UNMARKED else TallyLogic.REMOVED }.joinToString(""))
        val empty = tutorialBoard()
        val afterNine = marked(empty, mapOf(1 to TallyLogic.REMOVED))
        val afterSweep = marked(afterNine, mapOf(7 to TallyLogic.REMOVED, 11 to TallyLogic.REMOVED))
        val afterSlip = marked(afterSweep, mapOf(5 to TallyLogic.REMOVED))
        val afterKeep = marked(afterSweep, mapOf(5 to TallyLogic.KEPT))
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Each number on the edge is a target. Remove numbers until what is left in every row and column " +
                    "adds up to it: row 1 keeps 1 + 6 = 7.",
                highlight = BoardHighlight(strong = setOf(0, 2, row(0))),
            ),
            TutorialFrame(
                state = empty,
                caption = "Row 1 needs 7, and its 9 is bigger than 7 on its own, so the 9 can't stay. Tap it to remove it.",
                highlight = BoardHighlight(strong = setOf(1, row(0)), soft = setOf(0, 2, 3)),
                accepts = only(empty, mapOf(1 to TallyLogic.REMOVED)),
                retry = "Tap the glowing 9 once, with Remove chosen under the board.",
                done = "Removed. A number bigger than what its line needs always goes.",
            ),
            TutorialFrame(
                state = afterNine,
                caption = "Column 4 needs 3, so both of its 9s go. Drag down across the two of them to remove both in one sweep.",
                highlight = BoardHighlight(strong = setOf(7, 11, col(3)), soft = setOf(3, 15)),
                accepts = only(afterNine, mapOf(7 to TallyLogic.REMOVED, 11 to TallyLogic.REMOVED)),
                retry = "Drag along the glowing 9s in one sweep.",
                done = "Both gone. A sweep marks a run of squares along one row or column.",
            ),
            TutorialFrame(
                state = afterSweep,
                caption = "Row 2 now adds up to 10, 4 over its 6. Remove its 5 and see what happens.",
                highlight = BoardHighlight(strong = setOf(5, row(1)), soft = setOf(4, 6)),
                accepts = only(afterSweep, mapOf(5 to TallyLogic.REMOVED)),
                retry = "Tap the glowing 5 once.",
                done = "Too much: 4 + 1 can never make 6, so row 2 turns red, and column 2 with it.",
            ),
            TutorialFrame(
                state = afterSlip,
                caption = "So the 5 stays. Choose Keep under the board, then tap the 5 to circle it.",
                highlight = BoardHighlight(strong = setOf(5, row(1))),
                accepts = only(afterSweep, mapOf(5 to TallyLogic.KEPT)),
                retry = "Choose Keep under the board, then tap the glowing 5.",
                done = "Circled: a note that you are sure. It counts like any number left in.",
            ),
            TutorialFrame(
                state = afterKeep,
                caption = "Your turn: make every row and column add up. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every board works out one line at a time.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [TallyTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as TallyState
        val step = TallyTeacher.teach(s) ?: return null
        val before = s.marks
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == TallyTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as TallyState, step) },
            reachedBy = { now ->
                val marks = (now as TallyState).marks
                step.stays.all { marks[it] == TallyLogic.KEPT } &&
                    step.goes.all { marks[it] == TallyLogic.REMOVED } &&
                    step.clears.all { marks[it] != before[it] }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyStep(s: TallyState, step: TallyTeacher.Step): TallyState =
        s.paint(step.clears, TallyLogic.UNMARKED).paint(step.stays, TallyLogic.KEPT).paint(step.goes, TallyLogic.REMOVED)

    // ---- drawing --------------------------------------------------------------------------------

    /** The targets' width beside the rows and height under the columns, and the gap before them, in squares. */
    private const val TARGET_W = 1.0f
    private const val TARGET_H = 0.8f
    private const val GAP = 0.16f

    private fun unitsAcross(n: Int) = n + GAP + TARGET_W

    private fun unitsDown(n: Int) = n + GAP + TARGET_H

    /** Text for the digits 1..9 (index 0 unused) and every target, measured at the board's size. */
    private class Texts(val digits: List<TextLayoutResult>, val targets: List<TextLayoutResult>)

    private fun measure(measurer: TextMeasurer, s: TallyState, unitPx: Float, density: Density): Texts {
        fun style(f: Float) = TextStyle(fontSize = with(density) { (unitPx * f).toSp() }, fontWeight = FontWeight.SemiBold)
        return Texts(
            digits = (0..9).map { measurer.measure(it.toString(), style(0.5f)) },
            targets = TallyLogic.targets(s).map { measurer.measure(it.toString(), style(0.42f)) },
        )
    }

    /** What a [Board] draws besides the board's own marks; all of it optional. */
    private class Overlay(
        val highlight: BoardHighlight = BoardHighlight.None,
        val glow: Color = Color.Unspecified,
        val pulse: Float = 1f,
    )

    private fun DrawScope.drawBoard(
        s: TallyState,
        u: Float,
        texts: Texts,
        done: BooleanArray,
        bad: BooleanArray,
        scheme: ColorScheme,
        overlay: Overlay = Overlay(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val n = s.size
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.pair(s.digits.hashCode())
        val circle = BoardHues.ink(hueB, dark)
        val band = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.55f else 0.30f)
        val good = BoardHues.mark(145f)

        fun named(i: Int) = i in highlight.strong || i in highlight.soft

        // Squares, with their digits and marks.
        for (i in 0 until n * n) {
            val x = (i % n) * u
            val y = (i / n) * u
            drawRect(scheme.surface, Offset(x, y), Size(u, u))
            val layout = texts.digits[s.digitAt(i)]
            val at = Offset(x + (u - layout.size.width) / 2f, y + (u - layout.size.height) / 2f)
            feelSquare(feel, i, Offset(x, y), Size(u, u), accent) {
                when (s.marks[i]) {
                    TallyLogic.REMOVED -> {
                        drawText(layout, scheme.onSurface.copy(alpha = 0.22f), at)
                        val a = u * 0.28f
                        drawLine(
                            scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            Offset(x + a, y + u - a), Offset(x + u - a, y + a), maxOf(u * 0.05f, 1f), StrokeCap.Round,
                        )
                    }
                    TallyLogic.KEPT -> {
                        drawText(layout, scheme.onSurface, at)
                        drawCircle(circle, u * 0.34f, Offset(x + u / 2f, y + u / 2f), style = Stroke(maxOf(u * 0.07f, 1.5f)))
                    }
                    else -> drawText(layout, scheme.onSurface, at)
                }
            }
        }

        // Grid lines.
        val thin = maxOf(u * 0.03f, 1f)
        for (k in 0..n) {
            val edge = k == 0 || k == n
            val colour = if (edge) scheme.onSurfaceVariant.copy(alpha = 0.7f) else scheme.outline
            val w = if (edge) thin * 2f else thin
            drawLine(colour, Offset(k * u, 0f), Offset(k * u, n * u), w)
            drawLine(colour, Offset(0f, k * u), Offset(n * u, k * u), w)
        }

        // Targets: a chip beside each row and under each column, green once the line adds up, red
        // once it can no longer.
        for (line in 0 until 2 * n) {
            val index = n * n + line
            val (topLeft, size) = if (line < n) {
                Offset(n * u + GAP * u, line * u + u * 0.08f) to Size(TARGET_W * u, u * 0.84f)
            } else {
                Offset((line - n) * u + u * 0.08f, n * u + GAP * u) to Size(u * 0.84f, TARGET_H * u)
            }
            val fill = when {
                bad[line] -> scheme.error
                done[line] -> good
                else -> band
            }
            drawRoundRect(fill, topLeft, size, CornerRadius(u * 0.18f))
            if (lit && named(index)) {
                drawRoundRect(overlay.glow.copy(alpha = overlay.pulse), topLeft, size, CornerRadius(u * 0.18f), style = Stroke(u * 0.08f))
            }
            val ink = when {
                bad[line] -> BoardHues.onFill(scheme.error)
                done[line] -> BoardHues.onFill(good)
                else -> scheme.onSurface
            }
            val alpha = if (lit && !named(index)) 0.4f else 1f
            val layout = texts.targets[line]
            drawText(
                layout, ink.copy(alpha = alpha),
                Offset(topLeft.x + (size.width - layout.size.width) / 2f, topLeft.y + (size.height - layout.size.height) / 2f),
            )
        }

        if (!lit) return

        // Everything the hint does not name steps back, so the glow is findable.
        for (i in 0 until n * n) {
            if (named(i)) continue
            drawRect(scheme.background.copy(alpha = 0.55f), Offset((i % n) * u, (i / n) * u), Size(u, u))
        }

        hintRims(highlight, u, overlay.glow, overlay.pulse, scheme.background) { set, width, colour ->
            squareRim(set, width, colour, n, n, { i -> Rect(Offset((i % n) * u, (i / n) * u), Size(u, u)) })
        }
    }

    /** Which lines (rows then columns) add up to their target now. */
    private fun linesDone(s: TallyState, targets: List<Int>) = BooleanArray(2 * s.size) { TallyLogic.leftIn(s, it) == targets[it] }

    /** Which lines no choice of their open squares can bring to the target any more. */
    private fun linesImpossible(s: TallyState, targets: List<Int>) =
        BooleanArray(2 * s.size) { TallyLogic.lineImpossible(s, it, targets[it]) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as TallyState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // The pen, the sweep and the keyboard cursor are conveniences of the screen, not the board:
        // the play screen pushes an undo entry for every state it is handed.
        var pen by rememberSaveable(s.digits) { mutableStateOf(TallyLogic.REMOVED) }
        val sweep = remember(s.digits) { mutableStateOf<Sweep?>(null) }
        val cursor = remember(s.digits) { mutableStateOf<Int?>(null) }
        val targets = remember(s.digits, s.keep) { TallyLogic.targets(s) }

        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .penKeys(s, interactive, TALLY_KEYS, cursor, onState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Sized from both axes (CLAUDE.md), and no larger than a comfortable thumb.
                val unit = minOf(maxWidth / unitsAcross(n), maxHeight / unitsDown(n), 72.dp)
                val unitPx = with(density) { unit.toPx() }
                val texts = remember(s.digits, s.keep, unitPx) { measure(measurer, s, unitPx, density) }

                val shown = s.swept(sweep.value)
                val done = linesDone(shown, targets)
                // Lines that add up, on the board as handed (not the sweep still under the finger).
                val units = remember(n) { lineUnits(n, n) }
                val handed = linesDone(s, targets)
                val feel = rememberMoveFeel(s.marks.toList(), units) { handed[it] }

                // A line must stay impossible for a second, and be so now, before it turns red.
                val impossible = linesImpossible(shown, targets)
                var settled by remember(s.digits) { mutableStateOf(impossible) }
                LaunchedEffect(shown.marks) {
                    delay(SETTLE_MILLIS)
                    settled = impossible
                }
                val bad = BooleanArray(2 * n) { impossible[it] && settled[it] }

                Box(Modifier.size((unitsAcross(n) * unitPx).asDp(density), (unitsDown(n) * unitPx).asDp(density))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawBoard(shown, unitPx, texts, done, bad, scheme, Overlay(highlight, glow, pulse.value), feel, Color(accent))
                    }
                    // Taps and sweeps are read over the grid alone; the targets are not part of the play area.
                    Box(
                        Modifier
                            .size((n * unitPx).asDp(density), (n * unitPx).asDp(density))
                            .penGrid(s, interactive, pen, unitPx, sweep, cursor, scheme.primary, onState),
                    )
                }
            }

            PenRow(s.pens, pen, interactive, Color(accent), { if (it == TallyLogic.REMOVED) "Remove" else "Keep" }, { pen = it }) { option ->
                if (option == TallyLogic.REMOVED) {
                    val a = size.width * 0.2f
                    drawLine(scheme.onSurface.copy(alpha = 0.85f), Offset(a, size.height - a), Offset(size.width - a, a), 3f, StrokeCap.Round)
                } else {
                    drawCircle(Color(accent), size.width * 0.4f, style = Stroke(3f))
                }
            }
        }
    }

    private fun Float.asDp(density: Density): Dp = with(density) { this@asDp.toDp() }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed little board for the home grid, drawn by the board's own code: digits, two removed, one
     * circled, targets, the top rows already adding up. A legal crop: its targets are those of [motif]'s
     * own answer. Never calls `generate()`.
     */
    private val motif = TallyState(
        3, "472" + "935" + "618", "101" + "011" + "110",
        marks = ".x." + "xo." + "...",
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val targets = remember { TallyLogic.targets(motif) }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth / unitsAcross(motif.size), maxHeight / unitsDown(motif.size))
            val unitPx = with(density) { unit.toPx() }
            val texts = remember(unitPx) { measure(measurer, motif, unitPx, density) }
            val done = remember { linesDone(motif, targets) }
            Canvas(Modifier.size((unitsAcross(motif.size) * unitPx).asDp(density), (unitsDown(motif.size) * unitPx).asDp(density))) {
                drawBoard(motif, unitPx, texts, done, BooleanArray(2 * motif.size), scheme)
            }
        }
    }
}
