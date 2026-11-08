package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PenKeyAction
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.marked
import com.joebywan.daybook.core.only
import com.joebywan.daybook.core.penKeyAction
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.HintGlow
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.PenRow
import com.joebywan.daybook.ui.Sweep
import com.joebywan.daybook.ui.boardStyle
import com.joebywan.daybook.ui.drawLineChips
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.lineUnits
import com.joebywan.daybook.ui.penGrid
import com.joebywan.daybook.ui.penKeys
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.rememberSettledFlags
import com.joebywan.daybook.ui.rememberTwinTaps
import com.joebywan.daybook.ui.squareRim
import com.joebywan.daybook.ui.swept
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.vivid
import com.joebywan.daybook.ui.wall

/** F fills, X crosses; the other keys are every pen board's ([penKeyAction]). */
private val THERMOMETERS_KEYS = mapOf(Key.F to ThermometersLogic.FILLED, Key.X to ThermometersLogic.CROSSED)

/** What a physical key asks of a Thermometers board. */
internal fun thermometersKeyAction(key: Key, shift: Boolean): PenKeyAction? = penKeyAction(key, shift, THERMOMETERS_KEYS)

/**
 * Thermometers: straight and bent thermometers tile a square grid. Fill each from its round bulb, with no gap, so that
 * every row and column holds the number of filled squares beside it. Exactly one answer.
 *
 * Every board is dealt only if a person's ladder finishes it ([ThermometersLogic.accepts]) and an exhaustive count
 * proves one answer, so the hints teach that reasoning and never run out ([ThermometersTeacher]).
 *
 * Tiers differ in size (5x5, 6x6, 8x8) and in the deepest rung a board needs: Standard is finished by lines and tips
 * that cannot fill, Hard needs a bulb that must fill, Expert a what-if.
 */
object Thermometers : PuzzleType {

    override val id = "thermometers"
    override val displayName = "Thermometers"
    override val tagline = "Fill each one from the bulb up"
    override val accent = 0xFFD062BA

    override val rules = listOf(
        "Each thermometer fills from its round bulb, with no gaps: a filled square has every square below it filled.",
        "The number beside each row, and under each column, is how many of its squares are filled.",
        "Tap a square to fill its thermometer up to there; tap the top of the fill to lower it. Double-tap or long-press to cross a square out, and everything above it.",
        "Pick Fill or Cross under the board for what a tap does. Drag along a row or column to mark several squares at once.",
        "Solved when every count is met. Every board has one answer, found without guessing.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, F fills to the square, X crosses from it, Space cycles, Backspace clears; Shift+arrow carries a mark along.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        ThermometersLogic.generateVerified(seed, difficulty) ?: ThermometersLogic.lastResort(difficulty)

    /** The deepest technique the board needs from blank ([ThermometersLogic.depth]); null when full and empty lines alone finish it (the walkthrough's board). */
    override fun neededTechnique(state: PuzzleState): String? {
        val s = state as ThermometersState
        return when (ThermometersLogic.depth(s)) {
            ThermometersLogic.TIPS -> {
                val lines = ThermometersLogic.solveBy(ThermometersLogic.layout(s), ThermometersLogic.targets(s), IntArray(s.size * s.size), ThermometersLogic.ROOM)
                if (lines != null && lines.none { it == ThermometersLogic.OPEN }) null else ThermometersTeacher.AT_MOST
            }
            ThermometersLogic.BULBS -> ThermometersTeacher.AT_LEAST
            ThermometersLogic.WHAT_IF -> ThermometersTeacher.WHAT_IF
            else -> null
        }
    }

    // ---- the walkthrough ----------------------------------------------------------------------

    /**
     * Four by four, one answer, finished by full and empty lines alone. Row 4 counts 0; column 1 then has just room for
     * its 3; column 3 likewise; and row 1 then holds its 3, so its last tip stays empty. Tubes bulb first: column 1 from
     * the top, row 1 from the left, row 2 from the right, a short one down column 2, and a bent one from row 3's third
     * square round to row 4.
     */
    internal val TUTORIAL_TUBES = listOf(
        listOf(0, 4, 8, 12),
        listOf(1, 2, 3),
        listOf(7, 6, 5),
        listOf(9, 13),
        listOf(10, 11, 15, 14),
    )
    internal const val TUTORIAL_FILL = "1110" + "1011" + "1110" + "0000"
    private const val TUTORIAL_N = 4

    private fun tutorialBoard(marks: String = ".".repeat(TUTORIAL_N * TUTORIAL_N)) =
        ThermometersState(TUTORIAL_N, TUTORIAL_TUBES, TUTORIAL_FILL, marks)

    override val tutorial: List<TutorialFrame> by lazy {
        val n = TUTORIAL_N
        val row = { r: Int -> ThermometersLogic.rowCountIndex(n, r) }
        val col = { c: Int -> ThermometersLogic.colCountIndex(n, c) }
        val f = ThermometersLogic.FILLED
        val x = ThermometersLogic.CROSSED
        val solved = tutorialBoard(TUTORIAL_FILL.map { if (it == '1') f else ThermometersLogic.UNMARKED }.joinToString(""))
        val empty = tutorialBoard()
        val afterSweep = empty.marked(mapOf(12 to x, 13 to x, 14 to x, 15 to x))
        val afterColumn = afterSweep.marked(mapOf(0 to f, 4 to f, 8 to f))
        val afterRow = afterColumn.marked(mapOf(1 to f, 2 to f))
        val afterTip = afterRow.marked(mapOf(3 to x))
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Each thermometer fills from its round bulb, with no gaps. The numbers count the filled squares in each " +
                    "row and column: row 1 has 3.",
                highlight = BoardHighlight(strong = setOf(0, 1, 2, row(0))),
            ),
            TutorialFrame(
                state = empty,
                caption = "Row 4 counts 0, so nothing in it is filled. Drag along it to cross out all four squares in one sweep.",
                highlight = BoardHighlight(strong = setOf(12, 13, 14, 15, row(3))),
                accepts = empty.only(mapOf(12 to x, 13 to x, 14 to x, 15 to x)),
                retry = "Drag along the glowing row in one sweep.",
                done = "Crossed out. A cross is a note that a square stays empty.",
            ),
            TutorialFrame(
                state = afterSweep,
                caption = "Column 1 needs 3 and has just 3 squares left. Tap its third square: the thermometer fills from its bulb up to there.",
                highlight = BoardHighlight(strong = setOf(8, col(0)), soft = setOf(0, 4)),
                accepts = afterSweep.only(mapOf(0 to f, 4 to f, 8 to f)),
                retry = "Tap the glowing square once, with Fill chosen under the board.",
                done = "Filled from the bulb up to the square you tapped, in one tap.",
            ),
            TutorialFrame(
                state = afterColumn,
                caption = "Column 3 needs 3 too, and has just 3 left, so its top square fills. Tap it: its thermometer fills from the bulb.",
                highlight = BoardHighlight(strong = setOf(2, col(2)), soft = setOf(1, 6, 10)),
                accepts = afterColumn.only(mapOf(1 to f, 2 to f)),
                retry = "Tap the glowing square once.",
                done = "Filled, and the bulb beside it with it: no gaps.",
            ),
            TutorialFrame(
                state = afterRow,
                caption = "Row 1 now holds its 3, so its last square stays empty. Double-tap it, or long-press, to cross it out.",
                highlight = BoardHighlight(strong = setOf(3, row(0))),
                accepts = afterRow.only(mapOf(3 to x)),
                retry = "Double-tap the glowing square.",
                done = "Crossed out. A cross empties its square and everything above it in the thermometer.",
            ),
            TutorialFrame(
                state = afterTip,
                caption = "Your turn: meet every count. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every board works out one line or one thermometer at a time.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [ThermometersTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as ThermometersState
        return deduction(s, ThermometersTeacher.teach(s) ?: return null)
    }

    /** [step] on [s] as the play screen's [Deduction]. */
    internal fun deduction(s: ThermometersState, step: ThermometersTeacher.Step): Deduction {
        val before = s.marks
        return Deduction(
            step,
            mistake = step.technique == ThermometersTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as ThermometersState, step) },
            reachedBy = { now ->
                val marks = (now as ThermometersState).marks
                step.full.all { marks[it] == ThermometersLogic.FILLED } &&
                    step.empty.all { marks[it] == ThermometersLogic.CROSSED } &&
                    step.clears.all { marks[it] != before[it] }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyStep(s: ThermometersState, step: ThermometersTeacher.Step): ThermometersState =
        s.paint(step.clears, ThermometersLogic.UNMARKED).paint(step.full, ThermometersLogic.FILLED).paint(step.empty, ThermometersLogic.CROSSED)

    // ---- drawing --------------------------------------------------------------------------------

    /** The counts' width beside the rows and height under the columns, and the gap before them, in squares. */
    private const val COUNT_W = 1.0f
    private const val COUNT_H = 0.8f
    private const val GAP = 0.16f

    private fun unitsAcross(n: Int) = n + GAP + COUNT_W

    private fun unitsDown(n: Int) = n + GAP + COUNT_H

    private fun measure(measurer: TextMeasurer, targets: List<Int>, unitPx: Float, density: Density): List<TextLayoutResult> {
        val style = density.boardStyle(BoardGlyph.CLUE, unitPx)
        return targets.map { measurer.measure(it.toString(), style) }
    }

    /** The middle of square [i], [u] pixels a square. */
    private fun centre(i: Int, n: Int, u: Float) = Offset((i % n + 0.5f) * u, (i / n + 0.5f) * u)

    /** The tube's width and the bulb's radius, in squares. */
    private const val TUBE_W = 0.44f
    private const val BULB_R = 0.34f

    private fun DrawScope.drawBoard(
        s: ThermometersState,
        u: Float,
        counts: List<TextLayoutResult>,
        done: BooleanArray,
        bad: BooleanArray,
        brokenTubes: BooleanArray,
        scheme: ColorScheme,
        mercury: Color,
        overlay: HintGlow = HintGlow(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val n = s.size
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        val dark = BoardHues.isDark(scheme)
        val (hueA, _) = BoardHues.pair(s.fill.hashCode())
        val band = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.55f else 0.30f)
        val glass = scheme.onSurfaceVariant.copy(alpha = if (dark) 0.55f else 0.45f)
        val border = maxOf(u * 0.04f, 1f)

        fun named(i: Int) = i in highlight.strong || i in highlight.soft

        // Squares.
        for (i in 0 until n * n) drawRect(scheme.surface, Offset((i % n) * u, (i / n) * u), Size(u, u))

        // Grid lines.
        val thin = maxOf(u * 0.03f, 1f)
        for (k in 0..n) {
            val edge = k == 0 || k == n
            val colour = if (edge) scheme.wall else scheme.outline.copy(alpha = 0.6f)
            val w = if (edge) thin * 2f else thin
            drawLine(colour, Offset(k * u, 0f), Offset(k * u, n * u), w)
            drawLine(colour, Offset(0f, k * u), Offset(n * u, k * u), w)
        }

        // The glass of every thermometer: its outline (red once a fill sits over a cross), then the inside.
        s.tubes.forEachIndexed { t, tube ->
            val rim = if (brokenTubes[t]) scheme.error else glass
            for (pass in 0..1) {
                val colour = if (pass == 0) rim else scheme.surface
                val grow = if (pass == 0) border else 0f
                for (k in 1 until tube.size) {
                    drawLine(colour, centre(tube[k - 1], n, u), centre(tube[k], n, u), u * TUBE_W + 2 * grow, StrokeCap.Round)
                }
                if (tube.size == 1) drawCircle(colour, u * TUBE_W / 2 + grow, centre(tube[0], n, u))
                drawCircle(colour, u * BULB_R + grow, centre(tube[0], n, u))
            }
        }

        // The mercury and the crosses, square by square.
        val inner = u * (TUBE_W - 0.16f)
        s.tubes.forEach { tube ->
            tube.forEachIndexed { k, i ->
                val c = centre(i, n, u)
                feelSquare(feel, i, Offset((i % n) * u, (i / n) * u), Size(u, u), accent) {
                    when (s.marks[i]) {
                        ThermometersLogic.FILLED -> {
                            // Halfway to each neighbour, so a run of filled squares reads as one column of mercury.
                            if (k > 0) drawLine(mercury, c, (c + centre(tube[k - 1], n, u)) / 2f, inner, StrokeCap.Butt)
                            if (k < tube.size - 1) drawLine(mercury, c, (c + centre(tube[k + 1], n, u)) / 2f, inner, StrokeCap.Butt)
                            drawCircle(mercury, if (k == 0) u * (BULB_R - 0.08f) else inner / 2, c)
                        }
                        ThermometersLogic.CROSSED -> {
                            val a = u * 0.14f
                            val w = maxOf(u * 0.05f, 1.2f)
                            val ink = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                            drawLine(ink, c + Offset(-a, -a), c + Offset(a, a), w, StrokeCap.Round)
                            drawLine(ink, c + Offset(a, -a), c + Offset(-a, a), w, StrokeCap.Round)
                        }
                        else -> {}
                    }
                }
            }
        }

        drawLineChips(n, u, GAP, COUNT_W, COUNT_H, counts, done, bad, band, scheme, overlay)

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

    /** Which lines (rows then columns) hold their count now. */
    private fun linesDone(s: ThermometersState, targets: List<Int>) = BooleanArray(2 * s.size) { ThermometersLogic.filledIn(s, it) == targets[it] }

    /** Which lines can no longer hold their count. */
    private fun linesImpossible(s: ThermometersState, targets: List<Int>) =
        BooleanArray(2 * s.size) { ThermometersLogic.lineImpossible(s, it, targets[it]) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as ThermometersState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current

        // The pen, the sweep and the keyboard cursor are conveniences of the screen, not the board:
        // the play screen pushes an undo entry for every state it is handed.
        var pen by rememberSaveable(s.fill) { mutableStateOf(ThermometersLogic.FILLED) }
        val sweep = remember(s.fill) { mutableStateOf<Sweep?>(null) }
        val taps = rememberTwinTaps(s, onState)
        val cursor = remember(s.fill) { mutableStateOf<Int?>(null) }
        val targets = remember(s.tubes, s.fill) { ThermometersLogic.targets(s) }
        val mercury = vivid(accent)

        val (highlight, glow, pulse) = rememberHintPaint()

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .penKeys(taps, interactive, THERMOMETERS_KEYS, cursor),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Sized from both axes (CLAUDE.md), and no larger than a comfortable thumb.
                val unit = minOf(maxWidth / unitsAcross(n), maxHeight / unitsDown(n), 72.dp)
                val unitPx = with(density) { unit.toPx() }
                val counts = remember(targets, unitPx) { measure(measurer, targets, unitPx, density) }

                val shown = taps.shown.swept(sweep.value)
                val done = linesDone(shown, targets)
                // Lines that hold their count, on the board as handed (not the sweep still under the finger).
                val units = remember(n) { lineUnits(n, n) }
                val handed = linesDone(s, targets)
                val feel = rememberMoveFeel(s.marks.toList(), units) { handed[it] }

                // A line or a tube must stay broken for a second, and be so now, before it turns red.
                val bad = rememberSettledFlags(linesImpossible(shown, targets), s.fill, shown.marks)
                val broken = rememberSettledFlags(BooleanArray(s.tubes.size) { ThermometersLogic.tubeBroken(shown, it) }, s.fill, shown.marks)

                Box(Modifier.size(unit * unitsAcross(n), unit * unitsDown(n))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawBoard(shown, unitPx, counts, done, bad, broken, scheme, mercury, HintGlow(highlight, glow, pulse.value), feel, mercury)
                    }
                    // Taps and sweeps are read over the grid alone; the counts are not part of the play area.
                    Box(
                        Modifier
                            .size(unit * n)
                            .penGrid(taps, interactive, pen, unitPx, sweep, cursor, scheme.primary),
                    )
                }
            }

            PenRow(s.pens, pen, interactive, vivid(accent), { if (it == ThermometersLogic.FILLED) "Fill" else "Cross" }, { pen = it }) { option ->
                if (option == ThermometersLogic.FILLED) {
                    drawCircle(vivid(accent), size.width * 0.32f, Offset(size.width / 2f, size.height * 0.7f))
                    drawLine(vivid(accent), Offset(size.width / 2f, size.height * 0.7f), Offset(size.width / 2f, size.height * 0.12f), size.width * 0.3f, StrokeCap.Round)
                } else {
                    val a = size.width * 0.22f
                    val ink = scheme.onSurface.copy(alpha = 0.85f)
                    drawLine(ink, Offset(a, a), Offset(size.width - a, size.height - a), 3f, StrokeCap.Round)
                    drawLine(ink, Offset(size.width - a, a), Offset(a, size.height - a), 3f, StrokeCap.Round)
                }
            }
        }
    }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed little board for the home grid, drawn by the board's own code: three thermometers, one bent, partly
     * filled, one square crossed, counts read off its own answer. Never calls `generate()`.
     */
    private val motif = ThermometersState(
        3,
        listOf(listOf(6, 3, 0), listOf(1, 2, 5), listOf(7, 8)),
        "100" + "100" + "111",
        marks = "#.." + "#.x" + "###",
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val targets = remember { ThermometersLogic.targets(motif) }
        val mercury = vivid(accent)
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth / unitsAcross(motif.size), maxHeight / unitsDown(motif.size))
            val unitPx = with(density) { unit.toPx() }
            val counts = remember(unitPx) { measure(measurer, targets, unitPx, density) }
            val done = remember { linesDone(motif, targets) }
            Canvas(Modifier.size(unit * unitsAcross(motif.size), unit * unitsDown(motif.size))) {
                drawBoard(motif, unitPx, counts, done, BooleanArray(2 * motif.size), BooleanArray(motif.tubes.size), scheme, mercury)
            }
        }
    }
}
