package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import com.joebywan.daybook.ui.GLOW_ALPHA
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.rememberMoveFeel
import androidx.compose.ui.graphics.drawscope.scale
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.squareRim
import com.joebywan.daybook.ui.rememberSettledFlags

/**
 * Dominoes (`docs/DOMINO_SPEC.md`): an irregular board, some of it cut into coloured regions that each
 * carry a rule, and a tray of distinct dominoes. Lay every domino on two neighbouring squares so that
 * every region keeps its rule. Exactly one answer, proved; `solved` checks the rules
 * ([DominoLogic.isSolved]). The generator ships a board only if [DominoTeacher]'s ladder finishes it,
 * so the hints never fall back on a daily board.
 *
 * Tiers differ in box and tray (5x5 with 6-7 dominoes, 6x6 with 9-10, 7x7 with 12-14), in the rules
 * they use, and in the deepest step of the ladder a board must need.
 *
 * Highlight indices: squares `row * width + col`, tray slot `k` is [DominoTeacher.TRAY] `+ k`, region
 * `r`'s rule badge [DominoTeacher.RULE] `+ r`.
 */
object Dominoes : PuzzleType {

    override val id = "dominoes"
    override val displayName = "Dominoes"
    override val tagline = "Lay every domino so each region keeps its rule"
    override val accent = 0xFF76B43C

    override val rules = listOf(
        "Lay every domino from the tray on two neighbouring squares, either way round.",
        "Each coloured region has a rule. A number is the total of its pips; < and > bound the total.",
        "= means every square of the region shows the same; a crossed-out = means no two match.",
        "Squares outside a region have no rule, and a domino may cross from one region into another.",
        "Drag a domino from the tray onto a square and on into its neighbour: the way you swipe is the way it lies. " +
            "Or tap it (again to turn it round), then two neighbouring squares. Its first half goes on the first square.",
        "Tap a laid domino to lift it; double-tap or long-press it to turn it round. Every board has one answer, found without guessing.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, ] or Tab and [ pick a domino, F or Space turns it, Enter marks a square and lays it, Backspace lifts.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = DominoLogic.generate(seed, difficulty)

    /** The deepest level the generator's ladder needs, each tier exactly one; null below region values. */
    override fun neededTechnique(state: PuzzleState): String? =
        when (DominoTeacher.solveLevel((state as DominoState).layout, DominoTeacher.LEVEL_WHAT_IF)) {
            DominoTeacher.LEVEL_WHAT_IF -> DominoTeacher.WHAT_IF
            DominoTeacher.LEVEL_COUNTING -> DominoTeacher.COUNTING
            DominoTeacher.LEVEL_REGION_VALUE -> DominoTeacher.REGION_VALUE
            else -> null
        }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to lift or a domino to lay; see [DominoTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as DominoState
        val step = DominoTeacher.teach(s) ?: return null
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            // A mistake's targets name only its tray slot; the squares it lies on glow too, so the domino to lift is seen.
            targets = if (step.mistake) step.targets + step.focus else step.targets,
            mistake = step.mistake,
            fallback = step.fallback,
            applyTo = { now -> DominoTeacher.applyTo(now as DominoState, step) },
            reachedBy = { now -> DominoTeacher.reached(now as DominoState, step) },
        )
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    /**
     * Four by two, four dominoes, one answer:
     * ```
     * [12][ 2][ =][  ]
     * [12][  ][ =][<2]
     * ```
     * The 12 is two 6s, the double 6 down the left; the 2 is the 5-2's 2, so the 5-2 goes down beside it;
     * the = squares can only both be 1 (the 4-1 and the 1-0 share nothing else), and < 2 takes the 0,
     * so the 1-0 lies along the bottom and the 4-1 along the top.
     */
    internal const val TUTORIAL_W = 4
    private val tutorialTray = listOf(Domino(0, 1), Domino(1, 4), Domino(2, 5), Domino(6, 6))
    private val tutorialAnswer = listOf(Placement(7, 6), Placement(2, 3), Placement(1, 5), Placement(0, 4))

    internal fun tutorialBoard(): DominoState = DominoState(
        width = TUTORIAL_W,
        height = 2,
        open = List(8) { true },
        regionOf = listOf(0, 1, 2, -1, 0, -1, 2, 3),
        rules = listOf(RegionRule(RuleKind.SUM, 12), RegionRule(RuleKind.SUM, 2), RegionRule(RuleKind.EQUAL), RegionRule(RuleKind.LESS, 2)),
        tray = tutorialTray,
        placed = List(4) { null },
        answer = tutorialAnswer,
    )

    private fun laid(base: DominoState, vararg moves: Pair<Int, Placement?>): DominoState =
        base.copy(placed = base.placed.toMutableList().also { list -> moves.forEach { (d, p) -> list[d] = p } })

    /** Where each domino lies, a double's two squares in either order. */
    private fun shape(s: DominoState) = s.placed.mapIndexed { d, p ->
        if (p != null && s.tray[d].isDouble && p.first > p.second) Placement(p.second, p.first) else p
    }

    /** Accepts exactly [expected]'s dominoes (a double either way round), whatever the move count. */
    private fun only(expected: DominoState): (PuzzleState) -> Boolean = { next -> next is DominoState && shape(next) == shape(expected) }

    override val tutorial: List<TutorialFrame> by lazy {
        val rule = DominoTeacher.RULE
        val tray = DominoTeacher.TRAY
        val empty = tutorialBoard()
        val solved = laid(empty, *tutorialAnswer.mapIndexed { d, p -> d to p }.toTypedArray())
        val six = laid(empty, 3 to tutorialAnswer[3])
        val backwards = laid(six, 2 to Placement(5, 1))
        val five = laid(six, 2 to tutorialAnswer[2])
        val one = laid(five, 0 to tutorialAnswer[0])
        val lifted = laid(one, 2 to null)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Lay every domino from the tray so each coloured region keeps its rule. The 12 region's pips add up to 12: 6 + 6.",
                highlight = BoardHighlight(strong = setOf(0, 4, rule + 0)),
            ),
            TutorialFrame(
                state = empty,
                caption = "Only two 6s make 12, so the double 6 goes there. Tap it in the tray, then tap the two glowing squares.",
                highlight = BoardHighlight(strong = setOf(0, 4, tray + 3), soft = setOf(rule + 0)),
                accepts = only(six),
                retry = "Tap the glowing double 6 in the tray, then the two glowing squares.",
                done = "Laid. A domino covers two neighbouring squares.",
            ),
            TutorialFrame(
                state = six,
                caption = "A one-square region shows its pips: this one needs a 2, the 5-2's 2. Tap the 5-2, then this square, then the one below.",
                highlight = BoardHighlight(strong = setOf(1, tray + 2), soft = setOf(5, rule + 1)),
                accepts = only(backwards),
                retry = "Tap the glowing 5-2 in the tray, then the glowing square, then the one below.",
                done = "The 5 landed on the 2: the half shown first goes on the first square.",
            ),
            TutorialFrame(
                state = backwards,
                caption = "Turn it round where it lies: double-tap the 5-2, or long-press it.",
                highlight = BoardHighlight(strong = setOf(1, 5), soft = setOf(rule + 1)),
                accepts = only(five),
                retry = "Double-tap the glowing domino, or long-press it.",
                done = "Turned. In the tray, a second tap turns a domino before you lay it.",
            ),
            TutorialFrame(
                state = five,
                caption = "= wants its squares the same: only 1 is on both dominoes left, and < 2 takes the 0. Drag the 1-0 from the tray onto the = square, then on to the right.",
                highlight = BoardHighlight(strong = setOf(6, 7, tray + 0), soft = setOf(2, rule + 2, rule + 3)),
                accepts = only(one),
                retry = "Drag the glowing 1-0 onto the glowing = square, then on into the square to its right.",
                done = "Both = squares will show 1, and 0 is under 2.",
            ),
            TutorialFrame(
                state = one,
                caption = "Changed your mind? Tap a laid domino to lift it back to the tray. Lift the 5-2.",
                highlight = BoardHighlight(strong = setOf(1, 5)),
                accepts = only(lifted),
                retry = "Tap the glowing domino once.",
                done = "Back in the tray, and in your hand to lay again.",
            ),
            TutorialFrame(
                state = lifted,
                caption = "Your turn: lay the last two. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every board works out one domino at a time.",
            ),
        )
    }

    // ---- geometry -------------------------------------------------------------------------------

    /** Room around the board, in squares: the badges hang off the top-right corner of a region. */
    private const val MARGIN_START = 0.08f
    private const val MARGIN_END = 0.3f
    private const val MARGIN_TOP = 0.3f
    private const val MARGIN_BOTTOM = 0.08f

    /** A tray slot, in tray units (a domino in the tray is 2 x 1 of them). */
    internal const val SLOT_W = 2.5f
    internal const val SLOT_H = 1.8f
    private const val TRAY_GAP = 0.4f
    internal const val MAX_PER_ROW = 7

    /** Caps, in dp: a square no larger than a comfortable thumb, the tray's domino no larger than the board's. */
    private const val MAX_UNIT = 64f
    private const val MAX_TRAY = 26f
    private const val TRAY_RATIO = 0.8f

    /**
     * Where everything goes in a box [boxW] x [boxH] (px, [dp] px to the dp). [unit] is a board square,
     * [tray] a tray unit; the board's bounding box is columns [c0]..`c0 + cols - 1`, rows [r0]..`r0 + rows - 1`.
     * The tray is [trayRows] rows of at most [perRow] slots (seven), and depends on the board's shape and
     * tray size alone, so nothing moves while the board is played.
     */
    internal class Fit(
        val unit: Float,
        val tray: Float,
        val c0: Int,
        val r0: Int,
        val cols: Int,
        val rows: Int,
        val dominoes: Int,
        val trayRows: Int,
        val perRow: Int,
    ) {
        val boardW get() = (cols + MARGIN_START + MARGIN_END) * unit
        val boardH get() = (rows + MARGIN_TOP + MARGIN_BOTTOM) * unit
        val trayW get() = perRow * SLOT_W * tray
        val trayH get() = if (dominoes == 0) 0f else TRAY_GAP * tray + trayRows * SLOT_H * tray
        val width get() = maxOf(boardW, trayW)
        val height get() = boardH + trayH
        private val boardLeft get() = (width - boardW) / 2

        /** Square at column [c], row [r] of the full box (not the crop). */
        fun square(c: Int, r: Int) = Rect(
            Offset(boardLeft + (MARGIN_START + c - c0) * unit, (MARGIN_TOP + r - r0) * unit),
            Size(unit, unit),
        )

        /** Tray slot [k]: the whole tap target. A short last row is centred. */
        fun slot(k: Int): Rect {
            val row = k / perRow
            val inRow = minOf(perRow, dominoes - row * perRow)
            val x = (width - inRow * SLOT_W * tray) / 2 + (k % perRow) * SLOT_W * tray
            return Rect(Offset(x, boardH + TRAY_GAP * tray + row * SLOT_H * tray), Size(SLOT_W * tray, SLOT_H * tray))
        }

        /** The domino drawn in slot [k]. */
        fun slotDomino(k: Int): Rect {
            val s = slot(k)
            return Rect(Offset(s.center.x - tray, s.center.y - tray / 2), Size(2 * tray, tray))
        }

        /** The square under [at] (the full box's index for [width]), or -1. */
        fun squareAt(at: Offset, width: Int): Int {
            val c = c0 + kotlin.math.floor((at.x - boardLeft) / unit - MARGIN_START).toInt()
            val r = r0 + kotlin.math.floor(at.y / unit - MARGIN_TOP).toInt()
            return if (c in c0 until c0 + cols && r in r0 until r0 + rows) r * width + c else -1
        }

        fun slotAt(at: Offset): Int = (0 until dominoes).firstOrNull { slot(it).contains(at) } ?: -1
    }

    /**
     * The whole box, never cropped to the shape: the hints name squares by row and column of the box, so the
     * board must show the same rows and columns.
     */
    internal fun fit(s: DominoState, boxW: Float, boxH: Float, dp: Float = 1f, tray: Boolean = true): Fit =
        fit(0, 0, s.width, s.height, if (tray) s.tray.size else 0, boxW, boxH, dp)

    /**
     * Sized from both axes (CLAUDE.md): the tray's unit from the width its widest row needs, the board's square from
     * what is left, and the tray never larger than [TRAY_RATIO] of a square, which gives some height back to the board.
     */
    internal fun fit(c0: Int, r0: Int, cols: Int, rows: Int, dominoes: Int, boxW: Float, boxH: Float, dp: Float = 1f): Fit {
        val trayRows = if (dominoes == 0) 0 else ceil(dominoes / MAX_PER_ROW.toFloat()).toInt()
        val perRow = if (dominoes == 0) 0 else ceil(dominoes / trayRows.toFloat()).toInt()
        val t0 = if (dominoes == 0) 0f else minOf(boxW / (perRow * SLOT_W), MAX_TRAY * dp)
        fun square(t: Float): Float {
            val trayH = if (dominoes == 0) 0f else TRAY_GAP * t + trayRows * SLOT_H * t
            return maxOf(1f, minOf(boxW / (cols + MARGIN_START + MARGIN_END), (boxH - trayH) / (rows + MARGIN_TOP + MARGIN_BOTTOM), MAX_UNIT * dp))
        }
        var t = t0
        repeat(6) { t = minOf(t0, square(t) * TRAY_RATIO) }
        // The last square is sized for the last tray; shrinking the tray to it only frees height, so both fit.
        val u = square(t)
        return Fit(u, minOf(t, u * TRAY_RATIO), c0, r0, cols, rows, dominoes, trayRows, perRow)
    }

    // ---- colours --------------------------------------------------------------------------------

    /**
     * Violet, Amber, Teal, Blue: docs/COLOUR.md's content hues less Coral and Green (the badges show right and wrong) and
     * Rose (too near the red of a broken rule); a region graph is planar, so four always do. Gold stands by.
     */
    private val REGION_HUES = listOf(268f, 34f, 175f, 215f, 46f)

    /** A hue index per region, neighbouring regions different: a 4 (else 5) colouring of the region graph, derived, never stored. */
    internal fun regionColours(s: DominoState): IntArray {
        val count = s.rules.size
        val adj = List(count) { BooleanArray(count) }
        for (i in s.regionOf.indices) {
            val a = s.regionOf[i]
            if (a < 0) continue
            val right = if (i % s.width < s.width - 1) s.regionOf[i + 1] else -1
            val down = if (i + s.width < s.regionOf.size) s.regionOf[i + s.width] else -1
            for (b in intArrayOf(right, down)) if (b >= 0 && b != a) { adj[a][b] = true; adj[b][a] = true }
        }
        // Four hues, region r trying hue r first, so a board spreads them rather than leaning on the first three.
        for (k in 4..REGION_HUES.size) {
            val colour = IntArray(count) { -1 }
            var budget = 50_000
            fun go(r: Int): Boolean {
                if (r == count) return true
                if (--budget < 0) return false
                for (j in 0 until k) {
                    val c = (r + j) % k
                    if ((0 until count).any { adj[r][it] && colour[it] == c }) continue
                    colour[r] = c
                    if (go(r + 1)) return true
                }
                colour[r] = -1
                return false
            }
            if (go(0)) return colour
        }
        return IntArray(count) { it % REGION_HUES.size } // not reached on a planar layout; the outlines still show the regions
    }

    private val Ink = Color(0xFF11201C)

    private class Paint(scheme: ColorScheme, val dark: Boolean, hues: IntArray) {
        val surface = scheme.surface
        val line = scheme.outline
        val rim = scheme.onSurfaceVariant.copy(alpha = 0.55f)
        val tint = hues.map { BoardHues.fill(REGION_HUES[it], dark).copy(alpha = if (dark) 0.75f else 0.55f) }
        val edge = hues.map { BoardHues.ink(REGION_HUES[it], dark) }
        val good = BoardHues.mark(145f)
        val error = scheme.error
        val body = if (dark) Color(0xFFDCD4C3) else Color.White
        val pip = Ink
        val bodyEdge = if (dark) Color(0xFF8A8270) else scheme.onSurface.copy(alpha = 0.45f)
        val shadow = Color.Black.copy(alpha = if (dark) 0.35f else 0.16f)
        val background = scheme.background
        val onSurface = scheme.onSurface
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** Pip spots on a half, as (across, along) in -1..1; a six is two files of three along the domino. */
    private fun spots(v: Int): List<Pair<Int, Int>> = when (v) {
        1 -> listOf(0 to 0)
        2 -> listOf(-1 to -1, 1 to 1)
        3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
        4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
        5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
        6 -> listOf(-1 to -1, -1 to 0, -1 to 1, 1 to -1, 1 to 0, 1 to 1)
        else -> emptyList()
    }

    private fun DrawScope.drawPips(v: Int, centre: Offset, half: Float, horizontal: Boolean, colour: Color) {
        val d = half * 0.27f
        for ((across, along) in spots(v)) {
            val x = if (horizontal) along else across
            val y = if (horizontal) across else along
            drawCircle(colour, half * 0.095f, Offset(centre.x + x * d, centre.y + y * d))
        }
    }

    /**
     * A domino over [rect] (two squares, [horizontal] or upright), [lead] on the left or top half and
     * [other] on the right or bottom, [half] the side of one half. [alpha] fades a ghost.
     */
    private fun DrawScope.drawDomino(rect: Rect, lead: Int, other: Int, horizontal: Boolean, paint: Paint, alpha: Float = 1f) {
        val half = if (horizontal) rect.height else rect.width
        val radius = CornerRadius(half * 0.16f)
        if (alpha >= 1f) drawRoundRect(paint.shadow, rect.topLeft + Offset(half * 0.03f, half * 0.06f), rect.size, radius)
        drawRoundRect(paint.body.copy(alpha = alpha), rect.topLeft, rect.size, radius)
        drawRoundRect(paint.bodyEdge.copy(alpha = alpha), rect.topLeft, rect.size, radius, style = Stroke(maxOf(1f, half * 0.035f)))
        val mid = rect.center
        val bar = half * 0.32f
        if (horizontal) {
            drawLine(paint.bodyEdge.copy(alpha = alpha), Offset(mid.x, mid.y - bar), Offset(mid.x, mid.y + bar), maxOf(1f, half * 0.03f), StrokeCap.Round)
        } else {
            drawLine(paint.bodyEdge.copy(alpha = alpha), Offset(mid.x - bar, mid.y), Offset(mid.x + bar, mid.y), maxOf(1f, half * 0.03f), StrokeCap.Round)
        }
        val a = if (horizontal) Offset(rect.left + rect.width / 4, mid.y) else Offset(mid.x, rect.top + rect.height / 4)
        val b = if (horizontal) Offset(rect.right - rect.width / 4, mid.y) else Offset(mid.x, rect.bottom - rect.height / 4)
        drawPips(lead, a, half, horizontal, paint.pip.copy(alpha = alpha))
        drawPips(other, b, half, horizontal, paint.pip.copy(alpha = alpha))
    }

    /** The domino laid at [p] as two squares of [fit], its body inset from the squares' edges. */
    private fun DrawScope.drawLaid(s: DominoState, fit: Fit, d: Int, p: Placement, paint: Paint, alpha: Float = 1f) {
        val first = minOf(p.first, p.second)
        val second = maxOf(p.first, p.second)
        val r1 = fit.square(first % s.width, first / s.width)
        val r2 = fit.square(second % s.width, second / s.width)
        val inset = fit.unit * 0.09f
        val rect = Rect(r1.left + inset, r1.top + inset, r2.right - inset, r2.bottom - inset)
        val dom = s.tray[d]
        val lead = if (first == p.first) dom.a else dom.b
        val other = if (first == p.first) dom.b else dom.a
        drawDomino(rect, lead, other, second - first == 1, paint, alpha)
    }

    /** A region's badge square: its top row's rightmost square. */
    private fun badgeSquare(s: DominoState, r: Int): Int {
        var best = -1
        for (i in s.regionOf.indices) if (s.regionOf[i] == r) {
            if (best < 0 || i / s.width < best / s.width || (i / s.width == best / s.width && i > best)) best = i
        }
        return best
    }

    private fun labelNumber(rule: RegionRule): Int? = when (rule.kind) {
        RuleKind.SUM, RuleKind.LESS, RuleKind.MORE -> rule.n
        else -> null
    }

    /** The numbers the badges show, measured at [unitPx]. */
    private fun measureLabels(measurer: TextMeasurer, s: DominoState, unitPx: Float, density: Density): Map<Int, TextLayoutResult> {
        val style = TextStyle(fontSize = with(density) { (unitPx * 0.27f).toSp() }, fontWeight = FontWeight.Bold)
        return s.rules.mapNotNull { labelNumber(it) }.sorted().associateWith { measurer.measure(it.toString(), style) }
    }

    /** The symbol part of a badge, drawn as strokes (Fredoka has no ≠, and these read the same at any size). */
    private fun DrawScope.drawSymbol(kind: RuleKind, centre: Offset, u: Float, ink: Color) {
        val w = maxOf(1.2f, u * 0.05f)
        val x = u * 0.075f
        when (kind) {
            RuleKind.EQUAL, RuleKind.DIFFERENT -> {
                val y = u * 0.05f
                drawLine(ink, Offset(centre.x - x, centre.y - y), Offset(centre.x + x, centre.y - y), w, StrokeCap.Round)
                drawLine(ink, Offset(centre.x - x, centre.y + y), Offset(centre.x + x, centre.y + y), w, StrokeCap.Round)
                if (kind == RuleKind.DIFFERENT) {
                    drawLine(ink, Offset(centre.x - x * 0.6f, centre.y + u * 0.11f), Offset(centre.x + x * 0.6f, centre.y - u * 0.11f), w, StrokeCap.Round)
                }
            }
            RuleKind.LESS, RuleKind.MORE -> {
                val sign = if (kind == RuleKind.LESS) 1f else -1f
                val y = u * 0.085f
                val tip = Offset(centre.x - sign * x * 0.8f, centre.y)
                drawLine(ink, Offset(centre.x + sign * x * 0.8f, centre.y - y), tip, w, StrokeCap.Round)
                drawLine(ink, tip, Offset(centre.x + sign * x * 0.8f, centre.y + y), w, StrokeCap.Round)
            }
            RuleKind.SUM -> Unit
        }
    }

    /** The badge's rectangle for region [r], hung off the top-right corner of [badgeSquare]. */
    private fun badgeRect(s: DominoState, fit: Fit, r: Int, labels: Map<Int, TextLayoutResult>): Rect {
        val u = fit.unit
        val q = badgeSquare(s, r)
        val sq = fit.square(q % s.width, q / s.width)
        val rule = s.rules[r]
        val text = labelNumber(rule)?.let { labels[it]?.size?.width?.toFloat() } ?: 0f
        val symbol = if (rule.kind == RuleKind.SUM) 0f else u * 0.17f
        val gap = if (text > 0f && symbol > 0f) u * 0.03f else 0f
        val h = u * 0.38f
        val w = maxOf(h, symbol + gap + text + u * 0.18f)
        val centre = Offset(sq.right + u * 0.05f, sq.top - u * 0.05f)
        return Rect(Offset(centre.x - w / 2, centre.y - h / 2), Size(w, h))
    }

    private fun DrawScope.drawBadge(rule: RegionRule, rect: Rect, u: Float, fill: Color, ring: Color, alpha: Float, label: TextLayoutResult?) {
        val radius = CornerRadius(rect.height / 2)
        drawRoundRect(fill.copy(alpha = alpha), rect.topLeft, rect.size, radius)
        drawRoundRect(ring, rect.topLeft, rect.size, radius, style = Stroke(maxOf(1f, u * 0.035f)))
        val ink = BoardHues.onFill(fill).copy(alpha = alpha)
        val symbol = if (rule.kind == RuleKind.SUM) 0f else u * 0.17f
        val text = label?.size?.width?.toFloat() ?: 0f
        val gap = if (text > 0f && symbol > 0f) u * 0.03f else 0f
        var x = rect.center.x - (symbol + gap + text) / 2
        if (symbol > 0f) {
            drawSymbol(rule.kind, Offset(x + symbol / 2, rect.center.y), u, ink)
            x += symbol + gap
        }
        if (label != null) drawText(label, ink, Offset(x, rect.center.y - label.size.height / 2f))
    }

    /** What a [Board] draws beyond the board itself; all of it optional, so the home motif can pass none. */
    private class Overlay(
        val pick: DominoPick = DominoPick(),
        val ghost: Pair<Int, Int>? = null,
        /** The finger, while a domino is carried from the tray and has not yet found two squares. */
        val carry: Offset? = null,
        val cursor: Int? = null,
        val holds: BooleanArray? = null,
        val broken: BooleanArray? = null,
        val highlight: BoardHighlight = BoardHighlight.None,
        val glow: Color = Color.Unspecified,
        val pulse: Float = 1f,
        val select: Color = Color.Unspecified,
        val tray: Boolean = true,
    )

    private fun DrawScope.drawBoard(
        s: DominoState,
        fit: Fit,
        paint: Paint,
        hues: IntArray,
        labels: Map<Int, TextLayoutResult>,
        overlay: Overlay = Overlay(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val u = fit.unit
        val w = s.width
        val n = w * s.height
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        fun named(i: Int) = i in highlight.strong || i in highlight.soft
        fun rect(i: Int) = fit.square(i % w, i / w)

        // Squares: tinted where a region holds them, plain where nothing rules them.
        val thin = maxOf(1f, u * 0.025f)
        for (i in 0 until n) if (s.open[i]) {
            val r = rect(i)
            drawRect(paint.surface, r.topLeft, r.size)
            val g = s.regionOf[i]
            if (g >= 0) drawRect(paint.tint[g], r.topLeft, r.size)
            drawRect(paint.line, r.topLeft, r.size, style = Stroke(thin))
        }
        // The board's own edge, so its shape reads even where no region tints it.
        for (i in 0 until n) if (s.open[i]) {
            val r = rect(i)
            val c = i % w
            fun out(j: Int) = j !in 0 until n || !s.open[j]
            val e = thin * 2.2f
            if (out(i - w)) drawLine(paint.rim, r.topLeft, r.topRight, e, StrokeCap.Square)
            if (out(i + w)) drawLine(paint.rim, r.bottomLeft, r.bottomRight, e, StrokeCap.Square)
            if (c == 0 || out(i - 1)) drawLine(paint.rim, r.topLeft, r.bottomLeft, e, StrokeCap.Square)
            if (c == w - 1 || out(i + 1)) drawLine(paint.rim, r.topRight, r.bottomRight, e, StrokeCap.Square)
        }

        // Laid dominoes, then the region outlines in the gaps around them.
        val pips = DominoLogic.pipsOf(s)
        for (d in s.tray.indices) s.placed[d]?.let { p ->
            scale(feel.scale(p.first), (rect(p.first).center + rect(p.second).center) / 2f) { drawLaid(s, fit, d, p, paint) }
        }
        // A region that has just kept its rule glows once, square by square.
        for (i in 0 until n) feel.glow(i).takeIf { it > 0f }?.let { drawRect(accent.copy(alpha = GLOW_ALPHA * it), rect(i).topLeft, rect(i).size) }
        val edgeW = u * 0.065f
        val inset = u * 0.04f
        for (i in 0 until n) {
            val g = s.regionOf[i]
            if (g < 0) continue
            val r = rect(i)
            val colour = paint.edge[g]
            val c = i % w
            fun other(j: Int) = j !in 0 until n || s.regionOf[j] != g
            val up = i - w < 0 || other(i - w)
            val down = i + w >= n || other(i + w)
            val left = c == 0 || other(i - 1)
            val right = c == w - 1 || other(i + 1)
            val x0 = r.left + inset
            val x1 = r.right - inset
            val y0 = r.top + inset
            val y1 = r.bottom - inset
            // Each side stops at a corner where it meets another outer side; elsewhere it runs on into the neighbour by the
            // inset, which joins a straight edge to its neighbour's and closes an inner corner.
            if (up) drawLine(colour, Offset(if (left) x0 else r.left - inset, y0), Offset(if (right) x1 else r.right + inset, y0), edgeW, StrokeCap.Square)
            if (down) drawLine(colour, Offset(if (left) x0 else r.left - inset, y1), Offset(if (right) x1 else r.right + inset, y1), edgeW, StrokeCap.Square)
            if (left) drawLine(colour, Offset(x0, if (up) y0 else r.top - inset), Offset(x0, if (down) y1 else r.bottom + inset), edgeW, StrokeCap.Square)
            if (right) drawLine(colour, Offset(x1, if (up) y0 else r.top - inset), Offset(x1, if (down) y1 else r.bottom + inset), edgeW, StrokeCap.Square)
        }

        // The first square tapped, with the half that will go there.
        val pick = overlay.pick
        val f = pick.first
        if (f != null && f in 0 until n && pips[f] < 0) {
            val r = rect(f).deflate(u * 0.06f)
            drawRoundRect(overlay.select, r.topLeft, r.size, CornerRadius(u * 0.14f), style = Stroke(u * 0.07f))
            pick.lead(s)?.let { drawPips(it, r.center, u * 0.88f, true, paint.onSurface.copy(alpha = 0.55f)) }
        }
        // A drag in progress: the domino in hand as it would lie.
        overlay.ghost?.let { (from, to) ->
            val d = pick.selected ?: s.placed.indexOfFirst { it == null }
            if (d >= 0) drawLaid(s, fit, d, placementFor(pick.flipped && pick.selected == d, from, to), paint, alpha = 0.6f)
        }

        if (lit) {
            // Everything the hint does not name steps back, so the glow is findable.
            for (i in 0 until n) if (s.open[i] && !named(i)) {
                val r = rect(i)
                drawRect(paint.background.copy(alpha = 0.5f), r.topLeft, r.size)
            }
        }

        // Rule badges, over everything on the board: green once the region is covered and keeps its rule, red once it cannot.
        for (g in s.rules.indices) {
            val fill = when {
                overlay.broken?.get(g) == true -> paint.error
                overlay.holds?.get(g) == true -> paint.good
                else -> paint.edge[g]
            }
            val br = badgeRect(s, fit, g, labels)
            drawBadge(s.rules[g], br, u, fill, paint.surface, if (lit && !named(DominoTeacher.RULE + g)) 0.45f else 1f, labelNumber(s.rules[g])?.let { labels[it] })
        }


        if (lit) {
            for (g in s.rules.indices) {
                val index = DominoTeacher.RULE + g
                if (!named(index)) continue
                val br = badgeRect(s, fit, g, labels).inflate(u * 0.05f)
                val strong = index in highlight.strong
                drawRoundRect(
                    overlay.glow.copy(alpha = if (strong) overlay.pulse else 0.55f), br.topLeft, br.size, CornerRadius(br.height / 2),
                    style = Stroke(u * if (strong) 0.08f else 0.05f),
                )
            }
            hintRims(highlight, u, overlay.glow, overlay.pulse, paint.background) { set, width, colour ->
                squareRim(set, width, colour, w, s.height, { rect(it) }, { s.open[it] })
            }
        }

        // The keyboard cursor.
        overlay.cursor?.let { c ->
            if (c in 0 until n) {
                val r = rect(c).deflate(u * 0.03f)
                drawRect(overlay.select, r.topLeft, r.size, style = Stroke(maxOf(2f, u * 0.08f)))
            }
        }

        if (!overlay.tray) return

        // The tray: every slot keeps its place; a laid domino leaves its slot empty but reserved.
        val t = fit.tray
        for (k in s.tray.indices) {
            val slot = fit.slot(k)
            val body = fit.slotDomino(k)
            val dom = s.tray[k]
            val index = DominoTeacher.TRAY + k
            if (s.placed[k] == null) {
                val chosen = pick.selected == k
                if (chosen) {
                    val back = slot.deflate(t * 0.08f)
                    drawRoundRect(overlay.select.copy(alpha = 0.22f), back.topLeft, back.size, CornerRadius(t * 0.3f))
                    drawRoundRect(overlay.select, back.topLeft, back.size, CornerRadius(t * 0.3f), style = Stroke(maxOf(2f, t * 0.09f)))
                }
                val lead = if (chosen && pick.flipped) dom.a else dom.b
                val other = if (chosen && pick.flipped) dom.b else dom.a
                drawDomino(body, lead, other, true, paint)
            } else {
                drawRoundRect(
                    paint.line, body.topLeft, body.size, CornerRadius(t * 0.16f),
                    style = Stroke(maxOf(1f, t * 0.05f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(t * 0.18f, t * 0.14f))),
                )
            }
            if (lit) {
                if (named(index)) {
                    val g = slot.deflate(t * 0.04f)
                    val strong = index in highlight.strong
                    drawRoundRect(
                        overlay.glow.copy(alpha = if (strong) overlay.pulse else 0.5f), g.topLeft, g.size, CornerRadius(t * 0.3f),
                        style = Stroke(t * if (strong) 0.12f else 0.07f),
                    )
                } else {
                    drawRect(paint.background.copy(alpha = 0.5f), slot.topLeft, slot.size)
                }
            }
        }

        // A domino carried from the tray, its leading half under the finger, until the ghost shows where it would lie.
        val at = overlay.carry
        val held = pick.selected
        if (at != null && overlay.ghost == null && held != null) {
            val dom = s.tray[held]
            val lead = if (pick.flipped) dom.a else dom.b
            val other = if (pick.flipped) dom.b else dom.a
            drawDomino(Rect(Offset(at.x - u / 2, at.y - u / 2), Size(2 * u, u)), lead, other, true, paint, alpha = 0.85f)
        }
    }

    /** Where on the canvas the highlight is, for the hint popover. */
    private fun highlightRect(s: DominoState, fit: Fit, h: BoardHighlight, labels: Map<Int, TextLayoutResult>): Rect? {
        var out: Rect? = null
        for (i in h.strong + h.soft) {
            val r = when {
                i in 0 until s.width * s.height -> fit.square(i % s.width, i / s.width)
                i - DominoTeacher.TRAY in s.tray.indices -> fit.slot(i - DominoTeacher.TRAY)
                i - DominoTeacher.RULE in s.rules.indices -> badgeRect(s, fit, i - DominoTeacher.RULE, labels)
                else -> continue
            }
            out = out?.let { Rect(minOf(it.left, r.left), minOf(it.top, r.top), maxOf(it.right, r.right), maxOf(it.bottom, r.bottom)) } ?: r
        }
        return out
    }

    // ---- the board ------------------------------------------------------------------------------

    private const val DOUBLE_TAP_MS = 300L

    private class Cell<T>(var v: T)

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as DominoState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val dark = BoardHues.isDark(scheme)
        val hues = remember(s.regionOf, s.rules) { regionColours(s) }
        val paint = remember(scheme, hues) { Paint(scheme, dark, hues) }

        // The domino in hand, its turn, the first square, the drag and the cursor are the screen's, not the
        // board's: the play screen pushes an undo entry for every state it is handed.
        var pick by remember(s.open, s.tray) { mutableStateOf(DominoPick()) }
        var ghost by remember(s.open, s.tray) { mutableStateOf<Pair<Int, Int>?>(null) }
        var carry by remember(s.open, s.tray) { mutableStateOf<Offset?>(null) }
        var cursor by remember(s.open, s.tray) { mutableStateOf<Int?>(null) }

        // Gestures read the board as of the last thing they emitted or were handed (Snap's and Tents' lesson),
        // never a value captured when the detector started.
        val onStateNow by rememberUpdatedState(onState)
        val latest = remember { Cell(s) }.also { it.v = s }
        val scope = rememberCoroutineScope()
        val pendingLift = remember { Cell<Job?>(null) }
        val pendingDomino = remember { Cell(-1) }
        fun go(m: DominoMove) {
            pick = m.pick
            m.board?.let {
                latest.v = it
                onStateNow(it)
            }
        }
        fun settlePending() {
            val job = pendingLift.v ?: return
            pendingLift.v = null
            if (job.isActive) {
                job.cancel()
                val d = pendingDomino.v
                val b = latest.v
                b.placed.getOrNull(d)?.let { go(b.tapSquare(pick, it.first)) }
            }
        }

        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        // Green at once when a covered region keeps its rule; red only once it has stayed broken for a second.
        val layout = s.layout
        val pips = DominoLogic.pipsOf(s)
        val holds = BooleanArray(s.rules.size) { DominoLogic.regionHolds(layout, pips, it) }
        val broken = rememberSettledFlags(BooleanArray(s.rules.size) { DominoLogic.regionBroken(layout, pips, it) }, s.open to s.tray, s.placed)
        // A laid domino settles; a region it completes by its rule glows once.
        val feel = rememberMoveFeel(pips.toList(), layout.regionSquares) { holds[it] }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .boardKeys(interactive) { key, _, repeat ->
                    val action = dominoKeyAction(key) ?: return@boardKeys false
                    if (!repeat || action is DominoKeyAction.Move) {
                        settlePending()
                        val r = latest.v.applyKey(cursor, pick, action)
                        cursor = r.cursor
                        go(DominoMove(r.pick, r.board))
                    }
                    true
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = if (constraints.hasBoundedHeight) with(density) { maxHeight.toPx() } else boxW * 1.5f
            val fit = fit(s, boxW, boxH, density.density)
            val fitNow by rememberUpdatedState(fit)
            val labels = remember(s.rules, fit.unit) { measureLabels(measurer, s, fit.unit, density) }
            val shownPick = pick.on(s)

            Box(
                Modifier
                    .size(fit.width.asDp(density), fit.height.asDp(density))
                    .reportHighlight { _, h -> highlightRect(s, fit, h, labels) },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawBoard(
                        s, fit, paint, hues, labels,
                        Overlay(
                            pick = shownPick,
                            ghost = ghost,
                            carry = carry,
                            cursor = cursor.takeIf { interactive },
                            holds = holds,
                            broken = broken,
                            highlight = highlight,
                            glow = glow,
                            pulse = pulse.value,
                            select = Color(accent),
                        ),
                        feel, Color(accent),
                    )
                }
                // The tray is what the player reaches for to make a hinted move: keep the popover off it.
                if (s.tray.isNotEmpty()) {
                    Box(
                        Modifier
                            .offset(y = fit.boardH.asDp(density))
                            .size(fit.width.asDp(density), fit.trayH.asDp(density))
                            .keepClear(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(interactive) {
                            if (!interactive) return@pointerInput
                            detectTapGestures(
                                onLongPress = { at ->
                                    settlePending()
                                    val q = fitNow.squareAt(at, latest.v.width)
                                    if (q >= 0) go(latest.v.flipAt(pick, q))
                                },
                                onTap = { at ->
                                    val b = latest.v
                                    val k = fitNow.slotAt(at)
                                    if (k >= 0) {
                                        settlePending()
                                        go(b.tapTray(pick, k))
                                        return@detectTapGestures
                                    }
                                    val q = fitNow.squareAt(at, b.width)
                                    if (q < 0) return@detectTapGestures
                                    val d = if (b.open[q]) DominoLogic.dominoAt(b, q) else -1
                                    if (d >= 0) {
                                        // A second tap on the same domino inside the window turns it instead of lifting it.
                                        val job = pendingLift.v
                                        if (job != null && job.isActive && pendingDomino.v == d) {
                                            job.cancel()
                                            pendingLift.v = null
                                            go(b.flipAt(pick, q))
                                            return@detectTapGestures
                                        }
                                        settlePending()
                                        pendingDomino.v = d
                                        pendingLift.v = scope.launch {
                                            delay(DOUBLE_TAP_MS)
                                            pendingLift.v = null
                                            val now = latest.v
                                            now.placed.getOrNull(d)?.let { go(now.tapSquare(pick, it.first)) }
                                        }
                                        return@detectTapGestures
                                    }
                                    settlePending()
                                    go(b.tapSquare(pick, q))
                                },
                            )
                        }
                        .pointerInput(interactive) {
                            if (!interactive) return@pointerInput
                            var from = -1
                            var fromTray = false
                            fun free(b: DominoState, q: Int) = q >= 0 && b.open[q] && DominoLogic.dominoAt(b, q) < 0
                            // The overload that hands over the *down* (the web's touch slop is wide).
                            detectDragGestures(
                                orientationLock = null,
                                onDragStart = { down, _, _ ->
                                    settlePending()
                                    val b = latest.v
                                    // From the tray: the domino follows the finger until it settles on a square and
                                    // moves into a neighbour, which sets the way it lies (as a drag on the board does).
                                    val grabbed = b.grabTray(pick, fitNow.slotAt(down.position))
                                    fromTray = grabbed != null
                                    if (grabbed != null) {
                                        pick = grabbed
                                        carry = down.position
                                    }
                                    val q = fitNow.squareAt(down.position, b.width)
                                    from = if (!fromTray && free(b, q)) q else -1
                                    ghost = null
                                },
                                onDrag = { change, _ ->
                                    if (fromTray) {
                                        change.consume()
                                        carry = change.position
                                        val b = latest.v
                                        // It lies across the last two free squares the finger crossed, leading half on
                                        // the earlier one; off the board, or over a laid domino, it is carried again.
                                        val q = fitNow.squareAt(change.position, b.width)
                                        if (q != from) {
                                            ghost = if (from >= 0 && free(b, q) && DominoLogic.adjacent(b.width, from, q)) from to q else null
                                            from = if (free(b, q)) q else -1
                                        }
                                    } else if (from >= 0) {
                                        change.consume()
                                        val b = latest.v
                                        val centre = fitNow.square(from % b.width, from / b.width).center
                                        val dx = change.position.x - centre.x
                                        val dy = change.position.y - centre.y
                                        val to = when {
                                            maxOf(abs(dx), abs(dy)) < fitNow.unit * 0.5f -> -1
                                            abs(dx) >= abs(dy) -> if (dx > 0 && from % b.width < b.width - 1) from + 1 else if (dx < 0 && from % b.width > 0) from - 1 else -1
                                            else -> if (dy > 0) from + b.width else from - b.width
                                        }
                                        val ok = to in b.open.indices && b.open[to] && DominoLogic.dominoAt(b, to) < 0
                                        ghost = if (ok) from to to else null
                                    }
                                },
                                onDragEnd = {
                                    val g = ghost
                                    ghost = null
                                    carry = null
                                    if (g != null) go(latest.v.dragSquares(pick, g.first, g.second))
                                    from = -1
                                },
                                onDragCancel = {
                                    ghost = null
                                    carry = null
                                    from = -1
                                },
                            )
                        },
                )
            }
        }
    }

    private fun Float.asDp(density: Density): Dp = with(density) { this@asDp.toDp() }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed crop of a board for the home grid, drawn by the board's own code: a region of 8 holding the
     * 5-3 upright, an = region holding the double 4, and a one-square 2 still open. A legal crop: its own
     * answer keeps every rule. Never calls `generate()`.
     */
    private val motif = DominoState(
        width = 3,
        height = 2,
        open = List(6) { true },
        regionOf = listOf(0, 1, 1, 0, -1, 2),
        rules = listOf(RegionRule(RuleKind.SUM, 8), RegionRule(RuleKind.EQUAL), RegionRule(RuleKind.SUM, 2)),
        tray = listOf(Domino(0, 2), Domino(3, 5), Domino(4, 4)),
        placed = listOf(null, Placement(3, 0), Placement(1, 2)),
        answer = listOf(Placement(4, 5), Placement(3, 0), Placement(1, 2)),
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val dark = BoardHues.isDark(scheme)
        val hues = remember { regionColours(motif) }
        val paint = remember(scheme) { Paint(scheme, dark, hues) }
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val fit = fit(motif, with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, density.density, tray = false)
            val labels = remember(fit.unit) { measureLabels(measurer, motif, fit.unit, density) }
            Canvas(Modifier.size(fit.width.asDp(density), fit.height.asDp(density))) {
                drawBoard(motif, fit, paint, hues, labels, Overlay(tray = false))
            }
        }
    }
}
