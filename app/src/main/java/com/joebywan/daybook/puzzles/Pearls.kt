package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.core.union
import com.joebywan.daybook.ui.HintGlow
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.rememberSettled
import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.roundToInt


/**
 * Pearls: draw one closed loop through the middles of squares, passing every pearl. On a black pearl
 * the loop turns and runs straight on through the next square each way; on a white pearl it goes
 * straight, and turns on at least one of the squares beside it.
 *
 * Every board is dealt only if a person's ladder finishes it ([PearlsLogic.accepts]: the pearls' and
 * squares' own rules, on Hard the rule that the loop is one, on Expert a short what-if) and an
 * exhaustive count proves one loop, so the hints teach that reasoning and never run out
 * ([PearlsTeacher]). Tiers: 6x6, 8x8, 10x10, each needing its own rung at least once.
 */
object Pearls : PuzzleType {

    override val id = "pearls"
    override val displayName = "Pearls"
    override val tagline = "One loop through every pearl"
    override val accent = 0xFFC7C785

    override val rules = listOf(
        "Draw one closed loop through the middles of squares, moving up, down, left or right. It never crosses or branches.",
        "The loop passes through every pearl. It need not visit every square.",
        "On a black pearl the loop turns, and goes straight on through the next square on both sides.",
        "On a white pearl the loop goes straight through, and turns on at least one of the squares just before or after it.",
        "Drag through squares to draw; drag back along a line to rub it out. Tap between two squares to cross that edge out (a note).",
    )

    override val keyboardHelp = listOf(
        "Arrows move, Shift+arrow draws or rubs out the line that way, X then an arrow crosses that edge.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = PearlsLogic.generate(seed, difficulty)

    /** The loop rule or a what-if, as the tier demands ([PearlsLogic.depth]); null for the local rules alone. */
    override fun neededTechnique(state: PuzzleState): String? = when (PearlsLogic.depth(state as PearlsState)) {
        PearlsLogic.LOOP -> PearlsTeacher.SMALL_LOOP
        PearlsLogic.WHAT_IF -> PearlsTeacher.WHAT_IF
        else -> null
    }

    // ---- input geometry (pure, tested) ----------------------------------------------------------

    /** The square under ([x], [y]), in square units, clamped onto the board. */
    fun cellAt(n: Int, x: Float, y: Float): Int =
        floor(y).toInt().coerceIn(0, n - 1) * n + floor(x).toInt().coerceIn(0, n - 1)

    /** How near the middle of an edge a tap must land to name it, in squares. */
    const val TAP_REACH = 0.42f

    /**
     * The edge whose middle is nearest ([x], [y]), in square units, if within [TAP_REACH]; else -1. A tap
     * on the middle of a square names no edge (all four are half a square away).
     */
    fun edgeNear(n: Int, x: Float, y: Float): Int {
        val g = PearlsGrid.of(n)
        var best = -1
        var bestD = TAP_REACH * TAP_REACH
        // Horizontal edge (r, c)-(r, c+1) has its middle at (c + 1, r + 0.5); vertical (r, c)-(r+1, c) at (c + 0.5, r + 1).
        val r = floor(y).toInt()
        val k = x.roundToInt()
        if (r in 0 until n && k in 1 until n) {
            val d = (x - k) * (x - k) + (y - r - 0.5f) * (y - r - 0.5f)
            if (d < bestD) { bestD = d; best = r * (n - 1) + (k - 1) }
        }
        val c = floor(x).toInt()
        val j = y.roundToInt()
        if (c in 0 until n && j in 1 until n) {
            val d = (y - j) * (y - j) + (x - c - 0.5f) * (x - c - 0.5f)
            if (d < bestD) { best = g.horizontal + (j - 1) * n + c }
        }
        return best
    }

    /**
     * A drag's trail after the finger reaches [cell]: added when it is a neighbour of the last square, taken
     * back when it is the square before that (backing up along the drag undoes it), else unchanged.
     */
    fun trail(path: List<Int>, cell: Int, n: Int): List<Int> = when {
        path.isEmpty() -> listOf(cell)
        cell == path.last() -> path
        path.size >= 2 && cell == path[path.size - 2] -> path.dropLast(1)
        PearlsGrid.of(n).between(path.last(), cell) >= 0 -> path + cell
        else -> path
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    /**
     * Five by five, one loop (the border), finished by the pearls' and squares' rules alone: black pearls in three
     * corners, a white pearl on the left edge and one on the bottom edge.
     */
    internal const val TUTORIAL_PEARLS = "b...b" + "....." + "....." + "w...." + ".w..b"
    private const val TUTORIAL_N = 5
    internal val TUTORIAL_LOOP = listOf(0, 1, 2, 3, 4, 9, 14, 19, 24, 23, 22, 21, 20, 15, 10, 5)

    private fun tutorialEdges(lines: List<Pair<Int, Int>>, crosses: List<Pair<Int, Int>> = emptyList()): String {
        val g = PearlsGrid.of(TUTORIAL_N)
        val edges = CharArray(g.edgeCount) { PearlsLogic.OPEN_MARK }
        lines.forEach { (a, b) -> edges[g.between(a, b)] = PearlsLogic.LINE_MARK }
        crosses.forEach { (a, b) -> edges[g.between(a, b)] = PearlsLogic.CROSS_MARK }
        return edges.concatToString()
    }

    internal fun tutorialBoard(lines: List<Pair<Int, Int>> = emptyList(), crosses: List<Pair<Int, Int>> = emptyList()): PearlsState {
        val loop = tutorialEdges(TUTORIAL_LOOP.indices.map { TUTORIAL_LOOP[it] to TUTORIAL_LOOP[(it + 1) % TUTORIAL_LOOP.size] })
        return PearlsState(TUTORIAL_N, TUTORIAL_PEARLS, loop.map { if (it == PearlsLogic.LINE_MARK) '1' else '0' }.joinToString(""), tutorialEdges(lines, crosses))
    }

    /** Accepts exactly [expected]'s edges, so a stray stroke cannot slip through. */
    private fun only(expected: PearlsState): (PuzzleState) -> Boolean = { next -> next is PearlsState && next.edges == expected.edges }

    private fun edgeAt(a: Int, b: Int) = TUTORIAL_N * TUTORIAL_N + PearlsGrid.of(TUTORIAL_N).between(a, b)

    override val tutorial: List<TutorialFrame> by lazy {
        val loop = TUTORIAL_LOOP.indices.map { TUTORIAL_LOOP[it] to TUTORIAL_LOOP[(it + 1) % TUTORIAL_LOOP.size] }
        val black = listOf(10 to 5, 5 to 0, 0 to 1, 1 to 2)
        val white = black + listOf(20 to 21, 21 to 22)
        val cross = listOf(1 to 6)
        val slip = white + listOf(15 to 16)
        val solved = tutorialBoard(loop)
        val empty = tutorialBoard()
        val afterBlack = tutorialBoard(black)
        val afterWhite = tutorialBoard(white)
        val afterCross = tutorialBoard(white, cross)
        val afterSlip = tutorialBoard(slip, cross)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Draw one loop through the middles of squares that passes every pearl. On a black pearl it turns; " +
                    "on a white pearl it goes straight.",
                highlight = BoardHighlight(strong = setOf(0, 15)),
            ),
            TutorialFrame(
                state = empty,
                caption = "A black pearl turns, then runs straight on two squares each way. In the corner it can only go " +
                    "down and right: drag from two below it, through it, to two right of it.",
                highlight = BoardHighlight(strong = setOf(0, 10, 2), soft = setOf(5, 1)),
                accepts = only(afterBlack),
                retry = "Drag along the glowing squares in one stroke, through the pearl.",
                done = "Drawn. A black pearl's arms always run two squares straight.",
            ),
            TutorialFrame(
                state = afterBlack,
                caption = "A white pearl goes straight through. This one sits on the bottom edge, so it can't go up and down: " +
                    "drag across it, from its left to its right.",
                highlight = BoardHighlight(strong = setOf(21, 20, 22)),
                accepts = only(afterWhite),
                retry = "Drag along the glowing squares, straight through the white pearl.",
                done = "Straight through. It turns at the corner just before it, as a white pearl must on one side.",
            ),
            TutorialFrame(
                state = afterWhite,
                caption = "The square after a black pearl runs straight on, so no line leaves it sideways. " +
                    "Tap the glowing edge to cross it out.",
                highlight = BoardHighlight(strong = setOf(edgeAt(1, 6)), soft = setOf(0, 1)),
                accepts = only(afterCross),
                retry = "Tap once on the glowing edge, between the two squares.",
                done = "Crossed. A cross is a note: no line here.",
            ),
            TutorialFrame(
                state = afterCross,
                caption = "What if the white pearl on the left turned? Drag from it one square right and see.",
                highlight = BoardHighlight(strong = setOf(15, 16)),
                accepts = only(afterSlip),
                retry = "Drag from the glowing white pearl one square right.",
                done = "It can't: a white pearl goes straight, so it turns red.",
            ),
            TutorialFrame(
                state = afterSlip,
                caption = "Drag back along that line, from the pearl's right to the pearl, to rub it out.",
                highlight = BoardHighlight(strong = setOf(16, 15), warning = true),
                accepts = only(afterCross),
                retry = "Drag back along the red line, in one stroke.",
                done = "Rubbed out. Dragging along a line takes it away.",
            ),
            TutorialFrame(
                state = afterCross,
                caption = "Your turn: finish the loop. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's Pearls.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [PearlsTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as PearlsState
        val step = PearlsTeacher.teach(s) ?: return null
        val before = s.edges
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets(s.size),
            mistake = step.technique == PearlsTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as PearlsState, step) },
            reachedBy = { now -> reached(now as PearlsState, step, before) },
        )
    }

    /** Whether [now] holds [step]'s move: its lines drawn, its crosses made (or implied by a full square), its clears changed. */
    internal fun reached(now: PearlsState, step: PearlsTeacher.Step, before: String): Boolean {
        val view = PearlsTeacher.view(now.size, now.edges)
        return step.lines.all { now.edges[it] == PearlsLogic.LINE_MARK } &&
            step.crosses.all { view[it] == PearlsLogic.CROSS && now.edges[it] != PearlsLogic.LINE_MARK } &&
            step.clears.all { now.edges[it] != before[it] }
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    internal fun applyStep(s: PearlsState, step: PearlsTeacher.Step): PearlsState {
        val next = s.edges.toCharArray()
        var changed = 0
        fun set(e: Int, mark: Char) {
            if (next[e] != mark) {
                next[e] = mark
                changed++
            }
        }
        step.clears.forEach { set(it, PearlsLogic.OPEN_MARK) }
        step.lines.forEach { set(it, PearlsLogic.LINE_MARK) }
        step.crosses.forEach { set(it, PearlsLogic.CROSS_MARK) }
        return if (changed == 0) s else s.copy(edges = next.concatToString(), moves = s.moves + changed)
    }

    // ---- feedback (derived) ---------------------------------------------------------------------

    /** What is broken on the board as drawn, read from the lines alone (crosses are notes): squares, pearls and edges to draw red. */
    internal class Breaks(val squares: Set<Int>, val edges: Set<Int>) {
        val isEmpty get() = squares.isEmpty() && edges.isEmpty()
    }

    /**
     * A square with three or more lines; a pearl whose own lines already break its rule; and every line of a
     * closed loop that is not the whole answer (it leaves out a pearl or another line). Never reads the answer.
     */
    internal fun breaks(s: PearlsState): Breaks {
        val n = s.size
        val g = PearlsGrid.of(n)
        val st = IntArray(g.edgeCount) { if (s.edges[it] == PearlsLogic.LINE_MARK) PearlsLogic.LINE else PearlsLogic.OPEN }
        // Probe-only sets: drawn by membership, never walked into a choice.
        val squares = mutableSetOf<Int>()
        for (q in 0 until g.squares) {
            if ((0 until 4).count { d -> g.edge(q, d).let { it >= 0 && st[it] == PearlsLogic.LINE } } > 2) squares.add(q)
            val f = when (s.pearls[q]) {
                PearlsLogic.BLACK -> PearlsLogic.blackRule(g, s.pearls, st, q)
                PearlsLogic.WHITE -> PearlsLogic.whiteRule(g, st, q)
                else -> null
            }
            if (f != null && f.contradiction) squares.add(q)
        }
        val edges = mutableSetOf<Int>()
        val parts = PearlsLogic.stretches(g, st)
        if (parts.total > 0) {
            val hasEnd = BooleanArray(g.squares)
            for (q in 0 until g.squares) if (parts.degree[q] == 1 || parts.degree[q] > 2) hasEnd[parts.root[q]] = true
            for (q in 0 until g.squares) {
                val r = parts.root[q]
                if (r != q || hasEnd[r]) continue
                val all = parts.edgesIn[r] == parts.total && s.pearls.indices.all { s.pearls[it] == '.' || parts.root[it] == r }
                if (!all) for (e in 0 until g.edgeCount) if (st[e] == PearlsLogic.LINE && parts.root[g.endA[e]] == r) edges.add(e)
            }
        }
        return Breaks(squares, edges)
    }

    // ---- drawing --------------------------------------------------------------------------------

    private val PEARL_BLACK = Color(0xFF15191A)
    private val PEARL_WHITE = Color(0xFFFAF7EF)

    private fun DrawScope.drawBoard(
        s: PearlsState,
        u: Float,
        scheme: ColorScheme,
        broken: Breaks = Breaks(emptySet(), emptySet()),
        overlay: HintGlow = HintGlow(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
        hues: Pair<Float, Float> = BoardHues.pair(s.pearls.hashCode()),
    ) {
        val n = s.size
        val g = PearlsGrid.of(n)
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = hues
        val tint = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.40f else 0.24f)
        val ink = BoardHues.ink(hueB, dark)
        val lineW = u * 0.2f
        fun centre(q: Int) = Offset((q % n + 0.5f) * u, (q / n + 0.5f) * u)
        fun mid(e: Int) = (centre(g.endA[e]) + centre(g.endB[e])) / 2f
        fun named(i: Int) = i in highlight.strong || i in highlight.soft

        // Squares, a light hue-A tint over the surface so their middles read.
        drawRect(scheme.surface, Offset.Zero, Size(n * u, n * u))
        for (q in 0 until g.squares) {
            drawRect(tint, Offset((q % n) * u + u * 0.04f, (q / n) * u + u * 0.04f), Size(u * 0.92f, u * 0.92f))
        }
        // The frame.
        drawRect(scheme.onSurfaceVariant.copy(alpha = 0.7f), Offset.Zero, Size(n * u, n * u), style = Stroke(maxOf(u * 0.04f, 1.5f)))

        // Each square's own half of every line it has, and its pearl: drawn per square so a move's squares settle.
        for (q in 0 until g.squares) {
            val c = centre(q)
            feelSquare(feel, q, Offset((q % n) * u, (q / n) * u), Size(u, u), accent) {
                val bad = q in broken.squares
                for (d in 0 until 4) {
                    val e = g.edge(q, d)
                    if (e < 0 || s.edges[e] != PearlsLogic.LINE_MARK) continue
                    val colour = if (e in broken.edges) scheme.error else ink
                    drawLine(colour, c, mid(e), lineW, StrokeCap.Round)
                }
                // The pearl over its own half-lines, so the line runs into it and never hides it.
                val pearl = s.pearls[q]
                if (pearl != '.') {
                    val r = u * 0.28f
                    // Black and white whatever the scheme: the rules name the colours. Each has a rim of the
                    // other, so a black pearl still reads on a dark board and a white one on a light board.
                    val ring = Stroke(maxOf(u * 0.06f, 1.5f))
                    if (pearl == PearlsLogic.BLACK) {
                        drawCircle(PEARL_BLACK, r, c)
                        if (dark) drawCircle(PEARL_WHITE, r, c, style = ring)
                    } else {
                        drawCircle(PEARL_WHITE, r, c)
                        drawCircle(PEARL_BLACK, r, c, style = ring)
                    }
                }
                if (bad) drawCircle(scheme.error, u * 0.38f, c, style = Stroke(maxOf(u * 0.08f, 2f)))
            }
        }

        // Crosses: a small x on the edge's middle.
        val xs = u * 0.1f
        for (e in 0 until g.edgeCount) {
            if (s.edges[e] != PearlsLogic.CROSS_MARK) continue
            val m = mid(e)
            val w = maxOf(u * 0.05f, 1.2f)
            val colour = scheme.onSurfaceVariant
            drawLine(colour, Offset(m.x - xs, m.y - xs), Offset(m.x + xs, m.y + xs), w, StrokeCap.Round)
            drawLine(colour, Offset(m.x - xs, m.y + xs), Offset(m.x + xs, m.y - xs), w, StrokeCap.Round)
        }

        if (!lit) return

        // Everything the hint does not name steps back, so the glow is findable.
        for (q in 0 until g.squares) {
            if (named(q) || (0 until 4).any { d -> g.edge(q, d).let { it >= 0 && named(n * n + it) } }) continue
            drawRect(scheme.background.copy(alpha = 0.55f), Offset((q % n) * u, (q / n) * u), Size(u, u))
        }
        val corner = androidx.compose.ui.geometry.CornerRadius(u * 0.2f)
        for (i in highlight.soft - highlight.strong) glowAt(i, n, g, u, overlay.glow.copy(alpha = 0.5f), u * 0.05f, corner)
        for (i in highlight.strong) glowAt(i, n, g, u, overlay.glow.copy(alpha = overlay.pulse), u * 0.1f, corner)
    }

    /** The glow for highlight index [i]: a square's rounded outline, or a capsule along an edge. */
    private fun DrawScope.glowAt(i: Int, n: Int, g: PearlsGrid, u: Float, colour: Color, width: Float, corner: androidx.compose.ui.geometry.CornerRadius) {
        if (i < n * n) {
            drawRoundRect(colour, Offset((i % n) * u + width / 2, (i / n) * u + width / 2), Size(u - width, u - width), corner, style = Stroke(width))
            return
        }
        val e = i - n * n
        if (e >= g.edgeCount) return
        val a = g.endA[e]
        val b = g.endB[e]
        val x0 = (minOf(a % n, b % n) + 0.5f) * u
        val y0 = (minOf(a / n, b / n) + 0.5f) * u
        val horizontal = g.isHorizontal(e)
        val half = u * 0.2f
        val topLeft = Offset(x0 - half + (if (horizontal) u * 0.15f else 0f), y0 - half + (if (horizontal) 0f else u * 0.15f))
        val size = if (horizontal) Size(u * 0.7f + 2 * half, 2 * half) else Size(2 * half, u * 0.7f + 2 * half)
        drawRoundRect(colour, topLeft, size, androidx.compose.ui.geometry.CornerRadius(half), style = Stroke(width))
    }

    /** Where highlight index [i] sits, as a rectangle of the board at [unit] px a square. */
    private fun boundsOf(i: Int, n: Int, unit: Float): Rect? {
        val g = PearlsGrid.of(n)
        val squares = if (i < n * n) listOf(i) else (i - n * n).takeIf { it < g.edgeCount }?.let { listOf(g.endA[it], g.endB[it]) } ?: return null
        val rs = squares.map { it / n }
        val cs = squares.map { it % n }
        return Rect(cs.min() * unit, rs.min() * unit, (cs.max() + 1) * unit, (rs.max() + 1) * unit)
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as PearlsState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val density = LocalDensity.current

        // The detectors below outlive the board's states (keyed on the size alone), so they read the live board
        // through this, never a captured one (Snap's lesson).
        val latest by rememberUpdatedState(s)
        val emit by rememberUpdatedState(onState)
        // The drag in progress and the keyboard cursor are conveniences of the screen, not the board: the play
        // screen pushes an undo entry for every state it is handed.
        var path by remember(s.pearls) { mutableStateOf<List<Int>>(emptyList()) }
        var cursor by remember(s.pearls) { mutableStateOf<Int?>(null) }
        var armed by remember(s.pearls) { mutableStateOf(false) }

        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        val shown = if (path.size >= 2) s.drag(path) else s
        val broken = breaks(shown)
        val settled = rememberSettled(broken, s.pearls, shown.edges)
        val red = Breaks(broken.squares.intersect(settled.squares), broken.edges.intersect(settled.edges))
        // A square's look is its pearl and which of its edges are lines.
        val g = PearlsGrid.of(n)
        val feel = rememberMoveFeel(List(g.squares) { q -> (0 until 4).map { d -> g.edge(q, d).let { e -> if (e < 0) ' ' else s.edges[e] } } })

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .boardKeys(interactive, shift = true) { key, shift, repeat ->
                    val action = pearlsKeyAction(key, shift) ?: return@boardKeys false
                    if (!repeat || action is PearlsKeyAction.Move && !action.draw) {
                        val result = s.applyKey(cursor, armed, action)
                        cursor = result.cursor
                        armed = result.armed
                        if (result.board !== s) onState(result.board)
                    }
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            // Sized from both axes (CLAUDE.md), and no larger than a comfortable thumb.
            val unit = minOf(maxWidth / n, maxHeight / n, 64.dp)
            val unitPx = with(density) { unit.toPx() }
            Box(
                Modifier
                    .size(unit * n, unit * n)
                    .reportHighlight { size, h ->
                        (h.strong + h.soft).mapNotNull { boundsOf(it, n, size.width / n.toFloat()) }.reduceOrNull { a, b -> a union b }
                    }
                    .gridCursor(cursor.takeIf { interactive }, n, n, if (armed) scheme.error else scheme.primary)
                    .pointerInput(n, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { at ->
                            val e = edgeNear(n, at.x / unitPx, at.y / unitPx)
                            if (e >= 0) emit(latest.tap(e))
                        }
                    }
                    .pointerInput(n, interactive) {
                        if (!interactive) return@pointerInput
                        var lastX = 0f
                        var lastY = 0f
                        // The overload that hands over the *down* (the web's touch slop is wide): the drag starts where the finger did.
                        detectDragGestures(
                            orientationLock = null,
                            onDragStart = { down, _, _ ->
                                lastX = down.position.x / unitPx
                                lastY = down.position.y / unitPx
                                path = listOf(cellAt(n, lastX, lastY))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x / unitPx
                                val y = change.position.y / unitPx
                                var p = path
                                // Every square a fast swipe crosses, in order, as a slow one would.
                                for (cell in Snap.cellsCrossed(n, n, lastX, lastY, x, y)) p = trail(p, cell, n)
                                lastX = x
                                lastY = y
                                path = p
                            },
                            onDragEnd = {
                                val p = path
                                path = emptyList()
                                val next = latest.drag(p)
                                if (next !== latest) emit(next)
                            },
                            onDragCancel = { path = emptyList() },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawBoard(shown, unitPx, scheme, red, HintGlow(highlight, glow, pulse.value), feel, Color(accent))
                }
            }
        }
    }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed little board for the home grid, drawn by the board's own code: a 4x4 with a black pearl on a
     * corner and two white ones, and a loop that keeps all three. Never calls `generate()`.
     */
    private val motif: PearlsState by lazy {
        val loop = listOf(0, 1, 2, 3, 7, 11, 10, 6, 5, 9, 13, 12, 8, 4)
        val g = PearlsGrid.of(4)
        val edges = CharArray(g.edgeCount) { '.' }
        for (i in loop.indices) edges[g.between(loop[i], loop[(i + 1) % loop.size])] = '-'
        PearlsState(4, "b.w." + "...." + ".w.." + "....", "", edges.concatToString())
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val unitPx = with(LocalDensity.current) { (side / motif.size).toPx() }
            Canvas(Modifier.size(side)) {
                drawBoard(motif, unitPx, scheme, hues = BoardHues.HUE_PAIRS[0])
            }
        }
    }
}
