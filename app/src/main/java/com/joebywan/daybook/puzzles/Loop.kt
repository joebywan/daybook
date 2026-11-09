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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.dragTrail
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.core.union
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.HintGlow
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.boardStyle
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.gridCellAt
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.rememberSettled
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.vivid

/**
 * Loop: draw one closed loop along the sides of the squares, from dot to dot, so that every number has exactly that many
 * of its four sides on the loop. (The genre is published by Nikoli under its own name, which never appears where a
 * player sees it.)
 *
 * Every board is dealt only if a person's ladder finishes it ([LoopLogic.accepts]: the numbers' and dots' own rules,
 * a number with its corners, on Hard the rule that the loop is one, on Expert a short what-if) and an exhaustive count
 * proves one loop, so the hints teach that reasoning and never run out ([LoopTeacher]). Tiers: 5x5, 7x7, 10x10.
 *
 * The board is drawn on Pearls' lattice one step bigger: the dots are the middles of an `(n + 1) x (n + 1)` grid of
 * squares half a square in from the box's edge, so Pearls' input geometry ([LoopEdges.edgeNear], [dragTrail],
 * [Snap.cellsCrossed], [gridCellAt]) and keys ([loopKey]) work on the dots unchanged.
 */
object Loop : PuzzleType {

    override val id = "loop"
    override val displayName = "Loop"
    override val tagline = "One loop round the numbers"
    override val accent = 0xFFD062BA

    override val rules = listOf(
        "Draw one closed loop along the sides of the squares, from dot to dot. It never crosses or branches.",
        "A number says how many of that square's four sides the loop runs along. Squares without a number can have any.",
        "Drag from dot to dot to draw; drag back along a line to rub it out. Tap an edge to cross it out (a note).",
    )

    override val keyboardHelp = listOf(
        "Arrows move between dots, Shift+arrow draws or rubs out the line that way, X then an arrow crosses that edge.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = LoopLogic.generate(seed, difficulty)

    /** The loop rule or a what-if, as the tier demands ([LoopLogic.depth]); null for the local rules alone. */
    override fun neededTechnique(state: PuzzleState): String? = when (LoopLogic.depth(state as LoopState)) {
        LoopLogic.LOOP -> LoopTeacher.SMALL_LOOP
        LoopLogic.WHAT_IF -> LoopTeacher.WHAT_IF
        else -> null
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    /**
     * Four by four, one loop, finished by the local rules alone: a 3 in the top-left corner, two 0s, and numbers that
     * hold the loop down the left and along the bottom.
     */
    internal const val TUTORIAL_CLUES = "3.0." + ".10." + ".211" + "...."
    internal const val TUTORIAL_LOOP = "1000000000000111111111000110001100010001"
    private const val TUTORIAL_N = 4

    /** Dot `(r, c)` of the walkthrough's board. */
    private fun dot(r: Int, c: Int) = r * (TUTORIAL_N + 1) + c

    internal fun tutorialBoard(lines: List<Pair<Int, Int>> = emptyList(), crosses: List<Pair<Int, Int>> = emptyList()): LoopState {
        val g = LoopLogic.grid(TUTORIAL_N)
        val edges = CharArray(g.edgeCount) { LoopEdges.OPEN_MARK }
        lines.forEach { (a, b) -> edges[g.between(a, b)] = LoopEdges.LINE_MARK }
        crosses.forEach { (a, b) -> edges[g.between(a, b)] = LoopEdges.CROSS_MARK }
        return LoopState(TUTORIAL_N, TUTORIAL_CLUES, TUTORIAL_LOOP, edges.concatToString())
    }

    /** Accepts exactly [expected]'s edges, so a stray stroke cannot slip through. */
    private fun only(expected: LoopState): (PuzzleState) -> Boolean = { next -> next is LoopState && next.edges == expected.edges }

    private fun edgeAt(a: Int, b: Int) = TUTORIAL_N * TUTORIAL_N + LoopLogic.grid(TUTORIAL_N).between(a, b)

    private fun dotAt(d: Int) = LoopTeacher.dotIndex(TUTORIAL_N, d)

    override val tutorial: List<TutorialFrame> by lazy {
        val g = LoopLogic.grid(TUTORIAL_N)
        val answer = (0 until g.edgeCount).filter { TUTORIAL_LOOP[it] == '1' }.map { g.endA[it] to g.endB[it] }
        val corner = listOf(dot(1, 0) to dot(0, 0), dot(0, 0) to dot(0, 1))
        val cross = listOf(dot(0, 2) to dot(0, 3))
        val slip = corner + listOf(dot(1, 2) to dot(1, 3))
        val solved = tutorialBoard(answer)
        val empty = tutorialBoard()
        val afterCorner = tutorialBoard(corner)
        val afterCross = tutorialBoard(corner, cross)
        val afterSlip = tutorialBoard(slip, cross)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Draw one loop along the sides of the squares. A number says how many of its four sides the loop uses: " +
                    "the 3 has three, the 0 none.",
                highlight = BoardHighlight(strong = setOf(0, 2)),
            ),
            TutorialFrame(
                state = empty,
                caption = "The 3 sits in the corner. Its corner dot has no other way out, so the loop turns there: drag from the " +
                    "dot below the corner, round it, to the dot on its right.",
                highlight = BoardHighlight(strong = setOf(dotAt(dot(1, 0)), dotAt(dot(0, 0)), dotAt(dot(0, 1))), soft = setOf(0)),
                accepts = only(afterCorner),
                retry = "Drag through the glowing dots in one stroke, round the corner.",
                done = "Drawn. A 3 in a corner always takes both sides at that corner.",
            ),
            TutorialFrame(
                state = afterCorner,
                caption = "A 0 has no line round it. Tap the glowing edge to cross it out.",
                highlight = BoardHighlight(strong = setOf(edgeAt(dot(0, 2), dot(0, 3))), soft = setOf(2)),
                accepts = only(afterCross),
                retry = "Tap once on the glowing edge, between its two dots.",
                done = "Crossed. A cross is a note: no line here.",
            ),
            TutorialFrame(
                state = afterCross,
                caption = "What if the loop ran between the two 0s? Drag from one glowing dot to the other and see.",
                highlight = BoardHighlight(strong = setOf(dotAt(dot(1, 2)), dotAt(dot(1, 3))), soft = setOf(6)),
                accepts = only(afterSlip),
                retry = "Drag from one glowing dot to the other.",
                done = "It can't: both 0s would have a line, so they turn red.",
            ),
            TutorialFrame(
                state = afterSlip,
                caption = "Drag back along that line to rub it out.",
                highlight = BoardHighlight(strong = setOf(dotAt(dot(1, 3)), dotAt(dot(1, 2))), warning = true),
                accepts = only(afterCross),
                retry = "Drag back along the red line, in one stroke.",
                done = "Rubbed out. Dragging along a line takes it away.",
            ),
            TutorialFrame(
                state = afterCross,
                caption = "Your turn: finish the loop. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's Loop.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [LoopTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as LoopState
        return deduction(s, LoopTeacher.teach(s) ?: return null)
    }

    /** [step] on [s] as the play screen's [Deduction]. */
    internal fun deduction(s: LoopState, step: LoopTeacher.Step): Deduction {
        val before = s.edges
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets(s.size),
            mistake = step.technique == LoopTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as LoopState, step) },
            reachedBy = { now -> reached(now as LoopState, step, before) },
        )
    }

    /** Whether [now] holds [step]'s move: its lines drawn, its crosses made (or implied by a full dot), its clears changed. */
    internal fun reached(now: LoopState, step: LoopTeacher.Step, before: String): Boolean {
        val view = LoopEdges.view(LoopLogic.grid(now.size), now.edges)
        return step.lines.all { now.edges[it] == LoopEdges.LINE_MARK } &&
            step.crosses.all { view[it] == LoopEdges.CROSS && now.edges[it] != LoopEdges.LINE_MARK } &&
            step.clears.all { now.edges[it] != before[it] }
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    internal fun applyStep(s: LoopState, step: LoopTeacher.Step): LoopState =
        s.paint(step.clears, LoopEdges.OPEN_MARK).paint(step.lines, LoopEdges.LINE_MARK).paint(step.crosses, LoopEdges.CROSS_MARK)

    // ---- feedback (derived) ---------------------------------------------------------------------

    /** What is broken on the board as drawn, read from the lines alone (crosses are notes): numbers, dots and edges to draw red. */
    internal class Breaks(val squares: Set<Int>, val dots: Set<Int>, val edges: Set<Int>) {
        val isEmpty get() = squares.isEmpty() && dots.isEmpty() && edges.isEmpty()
    }

    /** How many of square [q]'s sides are lines. */
    internal fun linesRound(s: LoopState, q: Int): Int = LoopLogic.sides(s.size, q).count { s.edges[it] == LoopEdges.LINE_MARK }

    /**
     * A number with more lines than it says; a dot with three or more lines; and every line of a closed loop that is not
     * the whole answer (it leaves out another line or a number short). Never reads the answer.
     */
    internal fun breaks(s: LoopState): Breaks {
        val n = s.size
        val g = LoopLogic.grid(n)
        val st = IntArray(g.edgeCount) { if (s.edges[it] == LoopEdges.LINE_MARK) LoopEdges.LINE else LoopEdges.OPEN }
        // Probe-only sets: drawn by membership, never walked into a choice.
        val squares = (0 until n * n).filterTo(mutableSetOf()) { q -> s.clues[q] != '.' && linesRound(s, q) > s.clues[q] - '0' }
        val dots = (0 until g.nodes).filterTo(mutableSetOf()) { d -> (0 until 4).count { g.edge(d, it).let { e -> e >= 0 && st[e] == LoopEdges.LINE } } > 2 }
        val edges = mutableSetOf<Int>()
        val parts = LoopEdges.stretches(g, st)
        if (parts.total > 0) {
            val hasEnd = BooleanArray(g.nodes)
            for (d in 0 until g.nodes) if (parts.degree[d] == 1 || parts.degree[d] > 2) hasEnd[parts.root[d]] = true
            val rules = LoopLogic.Rules(n, s.clues)
            for (d in 0 until g.nodes) {
                val r = parts.root[d]
                if (r != d || hasEnd[r] || rules.whole(st, parts, r, -1)) continue
                for (e in 0 until g.edgeCount) if (st[e] == LoopEdges.LINE && parts.root[g.endA[e]] == r) edges.add(e)
            }
        }
        return Breaks(squares, dots, edges)
    }

    // ---- drawing --------------------------------------------------------------------------------

    private fun DrawScope.drawBoard(
        s: LoopState,
        u: Float,
        digits: List<TextLayoutResult>,
        scheme: ColorScheme,
        broken: Breaks = Breaks(emptySet(), emptySet(), emptySet()),
        overlay: HintGlow = HintGlow(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
        hues: Pair<Float, Float> = BoardHues.pair(s.clues.hashCode()),
    ) {
        val n = s.size
        val g = LoopLogic.grid(n)
        val highlight = overlay.highlight
        val lit = !highlight.isEmpty
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = hues
        val tint = BoardHues.fill(hueA, dark).copy(alpha = if (dark) 0.40f else 0.24f)
        val ink = BoardHues.ink(hueB, dark)
        val lineW = u * 0.13f
        val half = u * 0.5f
        fun dotAt(d: Int) = Offset((d % (n + 1)) * u + half, (d / (n + 1)) * u + half)
        fun mid(e: Int) = (dotAt(g.endA[e]) + dotAt(g.endB[e])) / 2f
        fun named(i: Int) = i in highlight.strong || i in highlight.soft
        val edgeBase = n * n
        val dotBase = n * n + g.edgeCount

        // The squares, each a light hue-A tint, with its number.
        for (q in 0 until n * n) {
            val topLeft = Offset((q % n) * u + half, (q / n) * u + half)
            feelSquare(feel, q, topLeft, Size(u, u), accent) {
                drawRect(tint, topLeft + Offset(u * 0.05f, u * 0.05f), Size(u * 0.9f, u * 0.9f))
                val clue = s.clues[q]
                if (clue != '.') {
                    val k = clue - '0'
                    val have = linesRound(s, q)
                    val colour = when {
                        q in broken.squares -> scheme.error
                        // A number with its lines all drawn steps back (lighter and smaller), so what is left to do stands out.
                        have == k && k > 0 -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
                        else -> scheme.onSurface
                    }
                    val layout = digits[k + if (have == k && k > 0) 5 else 0]
                    val c = topLeft + Offset(half, half)
                    drawText(layout, colour, Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
                    if (q in broken.squares) drawCircle(scheme.error, u * 0.36f, c, style = Stroke(maxOf(u * 0.06f, 2f)))
                }
            }
        }

        // Lines, dot to dot.
        for (e in 0 until g.edgeCount) {
            if (s.edges[e] != LoopEdges.LINE_MARK) continue
            drawLine(if (e in broken.edges) scheme.error else ink, dotAt(g.endA[e]), dotAt(g.endB[e]), lineW, StrokeCap.Round)
        }
        // Crosses: a small x on the edge's middle.
        val xs = u * 0.09f
        val xw = maxOf(u * 0.045f, 1.2f)
        for (e in 0 until g.edgeCount) {
            if (s.edges[e] != LoopEdges.CROSS_MARK) continue
            val m = mid(e)
            drawLine(scheme.onSurfaceVariant, Offset(m.x - xs, m.y - xs), Offset(m.x + xs, m.y + xs), xw, StrokeCap.Round)
            drawLine(scheme.onSurfaceVariant, Offset(m.x - xs, m.y + xs), Offset(m.x + xs, m.y - xs), xw, StrokeCap.Round)
        }
        // The dots, over the lines' ends.
        val dotR = maxOf(u * 0.07f, 2f)
        for (d in 0 until g.nodes) {
            drawCircle(scheme.onSurface, dotR, dotAt(d))
            if (d in broken.dots) drawCircle(scheme.error, u * 0.2f, dotAt(d), style = Stroke(maxOf(u * 0.05f, 2f)))
        }

        if (!lit) return

        // Everything the hint does not name steps back, so the glow is findable.
        for (q in 0 until n * n) {
            val near = named(q) || LoopLogic.sides(n, q).any { named(edgeBase + it) } || LoopLogic.corners(n, q).any { named(dotBase + it) }
            if (near) continue
            drawRect(scheme.background.copy(alpha = 0.55f), Offset((q % n) * u + half, (q / n) * u + half), Size(u, u))
        }
        for (i in highlight.soft - highlight.strong) glowAt(i, n, u, overlay.glow.copy(alpha = 0.5f), u * 0.05f)
        for (i in highlight.strong) glowAt(i, n, u, overlay.glow.copy(alpha = overlay.pulse), u * 0.09f)
    }

    /** The glow for highlight index [i]: a square's rounded outline, a capsule along an edge, or a ring round a dot. */
    private fun DrawScope.glowAt(i: Int, n: Int, u: Float, colour: Color, width: Float) {
        val g = LoopLogic.grid(n)
        val half = u * 0.5f
        when {
            i < n * n -> drawRoundRect(
                colour, Offset((i % n) * u + half + width / 2, (i / n) * u + half + width / 2), Size(u - width, u - width),
                CornerRadius(u * 0.2f), style = Stroke(width),
            )
            i < n * n + g.edgeCount -> {
                val e = i - n * n
                val a = g.endA[e]
                val x0 = (a % (n + 1)) * u + half
                val y0 = (a / (n + 1)) * u + half
                val r = u * 0.18f
                val horizontal = g.isHorizontal(e)
                val topLeft = Offset(x0 - r + (if (horizontal) u * 0.15f else 0f), y0 - r + (if (horizontal) 0f else u * 0.15f))
                val size = if (horizontal) Size(u * 0.7f + 2 * r, 2 * r) else Size(2 * r, u * 0.7f + 2 * r)
                drawRoundRect(colour, topLeft, size, CornerRadius(r), style = Stroke(width))
            }
            else -> {
                val d = i - n * n - g.edgeCount
                if (d >= g.nodes) return
                drawCircle(colour, u * 0.22f, Offset((d % (n + 1)) * u + half, (d / (n + 1)) * u + half), style = Stroke(width))
            }
        }
    }

    /** Where highlight index [i] sits, as a rectangle of the board at [u] px a step. */
    private fun boundsOf(i: Int, n: Int, u: Float): Rect? {
        val g = LoopLogic.grid(n)
        val m = n + 1
        fun around(d: Int) = Rect((d % m) * u, (d / m) * u, (d % m + 1) * u, (d / m + 1) * u)
        return when {
            i < n * n -> Rect((i % n + 0.5f) * u, (i / n + 0.5f) * u, (i % n + 1.5f) * u, (i / n + 1.5f) * u)
            i < n * n + g.edgeCount -> (i - n * n).let { e -> around(g.endA[e]) union around(g.endB[e]) }
            i < n * n + g.edgeCount + g.nodes -> around(i - n * n - g.edgeCount)
            else -> null
        }
    }

    /** The digits 0-3 as numbers are drawn, then the same lighter, for a number whose lines are all drawn. */
    private fun digitLayouts(measurer: androidx.compose.ui.text.TextMeasurer, unitPx: Float, density: androidx.compose.ui.unit.Density, family: FontFamily?): List<TextLayoutResult> {
        val given = density.boardStyle(BoardGlyph.GIVEN, unitPx).copy(fontFamily = family)
        val met = given.copy(fontWeight = FontWeight.Normal, fontSize = given.fontSize * 0.86f)
        return (0..4).map { measurer.measure(it.toString(), given) } + (0..4).map { measurer.measure(it.toString(), met) }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as LoopState
        val n = s.size
        val m = n + 1
        val scheme = MaterialTheme.colorScheme
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val family = MaterialTheme.typography.bodyLarge.fontFamily

        // The detectors below outlive the board's states (keyed on the size alone), so they read the live board
        // through this, never a captured one (Snap's lesson).
        val latest by rememberUpdatedState(s)
        val emit by rememberUpdatedState(onState)
        // The drag in progress and the keyboard cursor are conveniences of the screen, not the board.
        var path by remember(s.clues) { mutableStateOf<List<Int>>(emptyList()) }
        var cursor by remember(s.clues) { mutableStateOf<Int?>(null) }
        var armed by remember(s.clues) { mutableStateOf(false) }

        val (highlight, glow, pulse) = rememberHintPaint()

        val shown = if (path.size >= 2) s.drag(path) else s
        val broken = breaks(shown)
        val settled = rememberSettled(broken, s.clues, shown.edges)
        val red = Breaks(
            broken.squares.intersect(settled.squares), broken.dots.intersect(settled.dots), broken.edges.intersect(settled.edges),
        )
        // A square's look is its number and which of its sides are lines; a number glows once its lines are all drawn.
        val numbers = remember(s.clues) { s.clues.indices.filter { s.clues[it] != '.' && s.clues[it] != '0' } }
        val feel = rememberMoveFeel(
            List(n * n) { q -> LoopLogic.sides(n, q).map { s.edges[it] } },
            numbers.map { intArrayOf(it) },
        ) { linesRound(s, numbers[it]) == s.clues[numbers[it]] - '0' }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .boardKeys(interactive, shift = true) { key, shift, repeat ->
                    val action = pearlsKeyAction(key, shift) ?: return@boardKeys false
                    if (!repeat || action is PearlsKeyAction.Move && !action.draw) {
                        val (c, a, board) = loopKey(m, cursor, armed, action, s, s::tap, s::drag)
                        cursor = c
                        armed = a
                        if (board !== s) onState(board)
                    }
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            // Sized from both axes (CLAUDE.md): a step between dots, the box one step wider than the dots.
            val unit = minOf(maxWidth / m, maxHeight / m, 64.dp)
            val unitPx = with(density) { unit.toPx() }
            val digits = remember(unitPx, family) { digitLayouts(measurer, unitPx, density, family) }
            Box(
                Modifier
                    .size(unit * m, unit * m)
                    .reportHighlight { size, h ->
                        (h.strong + h.soft).mapNotNull { boundsOf(it, n, size.width / m.toFloat()) }.reduceOrNull { a, b -> a union b }
                    }
                    .gridCursor(cursor.takeIf { interactive }, m, m, if (armed) scheme.error else scheme.primary)
                    .pointerInput(n, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { at ->
                            val e = LoopEdges.edgeNear(m, at.x / unitPx, at.y / unitPx)
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
                                path = listOf(gridCellAt(Offset(lastX, lastY), 1f, m, m))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val x = change.position.x / unitPx
                                val y = change.position.y / unitPx
                                var p = path
                                // Every dot a fast swipe passes, in order, as a slow one would.
                                for (d in Snap.cellsCrossed(m, m, lastX, lastY, x, y)) p = dragTrail(p, d, m)
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
                    drawBoard(shown, unitPx, digits, scheme, red, HintGlow(highlight, glow, pulse.value), feel, vivid(accent))
                }
            }
        }
    }

    // ---- the home motif -------------------------------------------------------------------------

    /** A fixed 3x3 for the home grid, drawn by the board's own code: a bent loop and its numbers. Never calls `generate()`. */
    private val motif: LoopState by lazy {
        val shape = LoopEdges.LoopShape(4)
        shape.trace(booleanArrayOf(true, true, false, false, true, false, false, true, true))
        val loop = CharArray(shape.on.size) { if (shape.on[it]) '1' else '0' }.concatToString()
        val clues = LoopLogic.cluesOf(3, loop)
        LoopState(3, clues, loop, loop.map { if (it == '1') LoopEdges.LINE_MARK else LoopEdges.OPEN_MARK }.joinToString(""))
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val family = MaterialTheme.typography.bodyLarge.fontFamily
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val unitPx = with(density) { (side / (motif.size + 1)).toPx() }
            val digits = remember(unitPx, family) { digitLayouts(measurer, unitPx, density, family) }
            Canvas(Modifier.size(side)) {
                drawBoard(motif, unitPx, digits, scheme, hues = BoardHues.HUE_PAIRS[0])
            }
        }
    }
}
