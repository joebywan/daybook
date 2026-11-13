package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.stepCursor
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.HintGlow
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.boardStyle
import com.joebywan.daybook.ui.drawHintDim
import com.joebywan.daybook.ui.drawHintRings
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.squareDrag
import com.joebywan.daybook.ui.arcadeGap
import com.joebywan.daybook.ui.drawArcadeTiles
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.theme.vivid
import com.joebywan.daybook.ui.wall

/**
 * Links: numbered pairs of squares; join each pair with one path through side-touching squares, paths never meet, and
 * every square is used.
 *
 * Every board is dealt only if a person's ladder finishes it ([LinksLogic.accepts]: a line's end with one way on, a
 * square with two ways in, which paths can reach a square, what a way would cut off, on Expert a short what-if) and an
 * exhaustive count proves one answer, so the hints teach that reasoning and never run out ([LinksTeacher]). Tiers:
 * 6x6, 8x8, 10x10, each needing its own rung.
 */
object Links : PuzzleType {

    override val id = "links"
    override val displayName = "Links"
    override val tagline = "Join every pair, fill every square"
    override val accent = 0xFF967028 // Pearls': the accent set is full, and both draw paths (AccentSpacingTest.SHARED)

    override val rules = listOf(
        "Join each pair of matching numbers with one path through squares that share a side.",
        "Paths never cross or touch the same square, and every square is on a path.",
        "Drag from a number, or from the end of a line, to draw. A line can start on any square, and takes a pair's colour when it reaches one.",
        "Drag back along a line to rub it out. Drawing across another path cuts it there.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, Shift+arrow draws from the square you are on (or rubs out the line that way).",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = LinksLogic.generate(seed, difficulty)

    /** A cut or a what-if, as the tier demands ([LinksLogic.depth]); null for the local rules alone. */
    override fun neededTechnique(state: PuzzleState): String? {
        val s = state as LinksState
        return when (LinksLogic.depth(LinksLogic.Board.of(s.size, s.ends))) {
            LinksLogic.CUT -> LinksTeacher.CUT
            LinksLogic.WHAT_IF -> LinksTeacher.WHAT_IF
            else -> null
        }
    }

    // ---- colours --------------------------------------------------------------------------------

    /**
     * A colour per pair, the same in both schemes: the hue family's three fixed steps (pale, mid, deep), each next colour
     * the one furthest from all before it by CIEDE2000, deuteranopia and protanopia included (`LinksPaletteTest`: the
     * closest pair is 14.6 apart in Standard's seven, 13.9 in Hard's nine, 8.3 in Expert's twelve). Coral and Green are
     * free here: the board shows no right or wrong. Every end also carries its number, which carries the rest.
     */
    val palette: List<Color> = listOf(
        BoardHues.pinnedMark(215f), BoardHues.pinnedMark(46f), BoardHues.deep(8f, false), BoardHues.pinnedFill(175f, false),
        BoardHues.deep(268f, false), BoardHues.deep(175f, false), BoardHues.deep(46f, false), BoardHues.pinnedFill(215f, false),
        BoardHues.pinnedFill(145f, false), BoardHues.deep(330f, false), BoardHues.deep(145f, false), BoardHues.pinnedMark(145f),
    )

    private fun colourOf(p: Int): Color = palette[p % palette.size]

    // ---- teaching -------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [LinksTeacher]. */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as LinksState
        return deduction(LinksTeacher.teach(s) ?: return null)
    }

    /** [step] as the play screen's [Deduction] (Training builds its practices' steps with it too). */
    internal fun deduction(step: LinksTeacher.Step): Deduction {
        return Deduction(
            step,
            mistake = step.technique == LinksTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as LinksState, step) },
            reachedBy = { now -> reached(now as LinksState, step) },
        )
    }

    /** Whether [now] holds [step]'s move: its lines drawn, its wrong line gone. */
    internal fun reached(now: LinksState, step: LinksTeacher.Step): Boolean {
        val m = LinksLogic.masksOf(now.links)
        return step.lines.all { (a, b) -> LinksLogic.hasLine(now.size, m, a, b) } &&
            step.clears.none { (a, b) -> LinksLogic.hasLine(now.size, m, a, b) }
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    internal fun applyStep(s: LinksState, step: LinksTeacher.Step): LinksState {
        val m = LinksLogic.masksOf(s.links)
        step.clears.forEach { (a, b) -> LinksLogic.setLine(s.size, m, a, b, false) }
        step.lines.forEach { (a, b) -> LinksLogic.setLine(s.size, m, a, b, true) }
        val text = LinksLogic.maskString(m)
        return if (text == s.links) s else s.copy(links = text, moves = s.moves + 1)
    }

    // ---- keys ------------------------------------------------------------------------------------

    /**
     * An arrow at [cursor]: the cursor moves; with Shift the board also takes a two-square drag from the cursor that way
     * (drawing, or rubbing out along a line), through the same [LinksState.drag] a finger uses. A null cursor lands on
     * the first square. Returns the new cursor and the board (the same instance when nothing changed).
     */
    fun keyMove(s: LinksState, cursor: Int?, key: Key, shift: Boolean): Pair<Int?, LinksState>? {
        val (dr, dc) = arrowStep(key) ?: return null
        if (cursor == null) return 0 to s
        val to = stepCursor(cursor, dr, dc, s.size, s.size)
        if (!shift || to == cursor) return to to s
        return to to s.drag(listOf(cursor, to))
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    /** Five by five, five pairs, one answer, finished by the local rules alone (`LinksTutorialTest` proves both). */
    internal const val TUTORIAL_ENDS = "a.b.c" + "..d.e" + "....." + ".b.c." + ".ade."
    private const val TUTORIAL_N = 5

    /** The answer's paths, each from its first end. */
    internal val TUTORIAL_PATHS = listOf(
        listOf(0, 5, 10, 15, 20, 21),
        listOf(2, 1, 6, 11, 16),
        listOf(4, 3, 8, 13, 18),
        listOf(7, 12, 17, 22),
        listOf(9, 14, 19, 24, 23),
    )

    internal fun tutorialBoard(lines: List<Pair<Int, Int>> = emptyList()): LinksState {
        val n = TUTORIAL_N
        val answer = IntArray(n * n)
        for (p in TUTORIAL_PATHS) for (i in 1 until p.size) LinksLogic.setLine(n, answer, p[i - 1], p[i], true)
        val m = IntArray(n * n)
        for ((a, b) in lines) LinksLogic.setLine(n, m, a, b, true)
        return LinksState(n, TUTORIAL_ENDS, LinksLogic.maskString(answer), LinksLogic.maskString(m))
    }

    private fun along(vararg squares: Int) = (1 until squares.size).map { squares[it - 1] to squares[it] }

    /** Accepts exactly [expected]'s lines, so a stray stroke cannot slip through. */
    private fun only(expected: LinksState): (PuzzleState) -> Boolean = { next -> next is LinksState && next.links == expected.links }

    override val tutorial: List<TutorialFrame> by lazy {
        val corner = along(21, 20, 15)
        val one = corner + along(15, 10, 5, 0)
        val two = one + along(19, 24, 23)
        val slip = two + along(7, 8)
        val solved = tutorialBoard(TUTORIAL_PATHS.flatMap { p -> (1 until p.size).map { p[it - 1] to p[it] } })
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Join each pair of matching numbers with one path through squares that share a side. " +
                    "Paths never cross, and every square is used.",
                highlight = BoardHighlight(strong = setOf(0, 21), soft = setOf(5, 10, 15, 20)),
            ),
            TutorialFrame(
                state = tutorialBoard(),
                caption = "Every square is on a path. The bottom-left corner touches only two squares, so its path runs " +
                    "through both: drag from the 1 at the bottom, through the corner, up one.",
                highlight = BoardHighlight(strong = setOf(21, 20, 15)),
                accepts = only(tutorialBoard(corner)),
                retry = "Drag along the glowing squares in one stroke, from the 1.",
                done = "Drawn. A corner always has a path turning through it.",
            ),
            TutorialFrame(
                state = tutorialBoard(corner),
                caption = "Drag on from the end of the line, straight up to the other 1.",
                highlight = BoardHighlight(strong = setOf(15, 10, 5, 0)),
                accepts = only(tutorialBoard(one)),
                retry = "Drag from the end of the line up the glowing squares to the 1.",
                done = "Joined: path 1 is finished.",
            ),
            TutorialFrame(
                state = tutorialBoard(one),
                caption = "A line can start on any square. The bottom-right corner touches only two squares: drag from " +
                    "the one above it, through the corner, onto the 5.",
                highlight = BoardHighlight(strong = setOf(19, 24, 23)),
                accepts = only(tutorialBoard(two)),
                retry = "Drag from the glowing square above the corner, through it, onto the 5.",
                done = "Drawn, and it takes the 5's colour.",
            ),
            TutorialFrame(
                state = tutorialBoard(two),
                caption = "What if the 4 went right? Drag from it one square right and see.",
                highlight = BoardHighlight(strong = setOf(7, 8)),
                accepts = only(tutorialBoard(slip)),
                retry = "Drag from the glowing 4 one square right.",
                done = "Now the 3 in the top corner can't reach its other 3, so that line is wrong.",
            ),
            TutorialFrame(
                state = tutorialBoard(slip),
                caption = "Drag back along that line, from its end to the 4, to rub it out.",
                highlight = BoardHighlight(strong = setOf(8, 7), warning = true),
                accepts = only(tutorialBoard(two)),
                retry = "Drag back along the glowing line, in one stroke.",
                done = "Rubbed out. Dragging along a line takes it away.",
            ),
            TutorialFrame(
                state = tutorialBoard(two),
                caption = "Your turn: join every pair and fill every square. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's Links.",
            ),
        )
    }

    // ---- drawing --------------------------------------------------------------------------------

    private fun numberLayouts(measurer: TextMeasurer, unitPx: Float, density: Density, pairs: Int): List<TextLayoutResult> {
        val style = density.boardStyle(BoardGlyph.CLUE, unitPx)
        return (1..maxOf(pairs, 1)).map { measurer.measure(it.toString(), style) }
    }

    private fun DrawScope.drawBoard(
        s: LinksState,
        u: Float,
        scheme: ColorScheme,
        numbers: List<TextLayoutResult>,
        overlay: HintGlow = HintGlow(),
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val n = s.size
        val b = LinksLogic.Board.of(n, s.ends)
        val m = LinksLogic.masksOf(s.links)
        val v = LinksLogic.View(b, m)
        val dark = BoardHues.isDark(scheme)
        // Arcade draws on ArcadeBoard's tiles; Classic (classicLook) draws exactly what it drew before.
        val arcade = !classicLook()
        val a = ArcadeBoard.of(scheme)
        val loose = if (arcade) a.squareInk.copy(alpha = 0.5f) else scheme.onSurfaceVariant
        fun centre(q: Int) = Offset((q % n + 0.5f) * u, (q / n + 0.5f) * u)

        if (arcade) drawArcadeTiles(n, n, u, a) else drawRect(scheme.surface, Offset.Zero, Size(n * u, n * u))
        // Each square on a pair's line takes a wash of its colour, so a filled board reads at a glance.
        val gap = arcadeGap(u)
        for (q in 0 until b.squares) {
            val c = v.colour[q]
            if (c < 0 || v.deg[q] == 0 && b.pair[q] < 0) continue
            val wash = colourOf(c).copy(alpha = if (dark) 0.30f else 0.22f)
            if (arcade) {
                drawRoundRect(wash, Offset((q % n) * u + gap / 2, (q / n) * u + gap / 2), Size(u - gap, u - gap), CornerRadius(u * 0.14f))
            } else {
                drawRect(wash, Offset((q % n) * u, (q / n) * u), Size(u, u))
            }
        }
        if (!arcade) {
            // The grid: thin lines between squares, a frame round them.
            val grid = scheme.onSurfaceVariant.copy(alpha = 0.22f)
            val thin = maxOf(u * 0.02f, 1f)
            for (k in 1 until n) {
                drawLine(grid, Offset(k * u, 0f), Offset(k * u, n * u), thin)
                drawLine(grid, Offset(0f, k * u), Offset(n * u, k * u), thin)
            }
            drawRect(scheme.wall, Offset.Zero, Size(n * u, n * u), style = Stroke(maxOf(u * 0.04f, 1.5f)))
        }

        // Each square's own half of every line it has, and its end: drawn per square so a move's squares settle.
        val lineW = u * 0.3f
        for (q in 0 until b.squares) {
            val c = centre(q)
            feelSquare(feel, q, Offset((q % n) * u, (q / n) * u), Size(u, u), accent) {
                val ink = if (v.colour[q] >= 0) colourOf(v.colour[q]) else loose
                for (d in 0 until 4) {
                    if (m[q] and (1 shl d) == 0) continue
                    val to = when (d) {
                        LinksLogic.UP -> Offset(c.x, c.y - u / 2)
                        LinksLogic.RIGHT -> Offset(c.x + u / 2, c.y)
                        LinksLogic.DOWN -> Offset(c.x, c.y + u / 2)
                        else -> Offset(c.x - u / 2, c.y)
                    }
                    drawLine(ink, c, to, lineW, StrokeCap.Round)
                }
                val p = b.pair[q]
                if (p >= 0) {
                    val fill = colourOf(p)
                    drawCircle(fill, u * 0.36f, c)
                    // A rim of the surface's opposite, so a pale end still reads on a light board and a deep one on a dark.
                    drawCircle((if (arcade) a.squareInk else scheme.onSurface).copy(alpha = 0.35f), u * 0.36f, c, style = Stroke(maxOf(u * 0.03f, 1f)))
                    val label = numbers.getOrNull(p)
                    if (label != null) {
                        drawText(label, BoardHues.onFill(fill), Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
                    }
                }
            }
        }

        val highlight = overlay.highlight
        if (highlight.isEmpty) return
        drawHintDim(highlight, n, n, u, scheme.background)
        drawHintRings(highlight, n, n, u, overlay.glow, overlay.pulse)
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as LinksState
        val n = s.size
        val scheme = MaterialTheme.colorScheme
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()

        // The detector outlives the board's states (keyed on the size), so it reads the live board through this.
        val latest by rememberUpdatedState(s)
        val emit by rememberUpdatedState(onState)
        // The drag in progress and the keyboard cursor belong to the screen, not the board: every state handed to the
        // play screen is an undo entry.
        var path by remember(s.ends) { mutableStateOf<List<Int>>(emptyList()) }
        var cursor by remember(s.ends) { mutableStateOf<Int?>(null) }

        val (highlight, glow, pulse) = rememberHintPaint()
        val shown = if (path.size >= 2) s.drag(path) else s

        // A pair joined glows along its path and plays its note; squares settle as their lines change.
        val b = LinksLogic.Board.of(n, s.ends)
        val v = LinksLogic.View(b, LinksLogic.masksOf(s.links))
        val units = (0 until b.pairs).map { p -> if (v.complete(p)) pathOf(v, b.endA[p]) else intArrayOf(b.endA[p]) }
        val feel = rememberMoveFeel(List(b.squares) { s.links[it] }, units) { v.complete(it) }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .boardKeys(interactive, shift = true) { key, shift, repeat ->
                    if (repeat && shift) return@boardKeys true
                    val (to, next) = keyMove(s, cursor, key, shift) ?: return@boardKeys false
                    cursor = to
                    if (next !== s) onState(next)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            // Sized from both axes (CLAUDE.md), and no larger than a comfortable thumb.
            val unit = minOf(maxWidth / n, maxHeight / n, 64.dp)
            val unitPx = with(density) { unit.toPx() }
            val numbers = remember(unitPx, b.pairs) { numberLayouts(measurer, unitPx, density, b.pairs) }
            Box(
                Modifier
                    .size(unit * n, unit * n)
                    .highlightGrid(n, n)
                    .gridCursor(cursor.takeIf { interactive }, n, n, scheme.primary)
                    // A drag draws (or rubs out) through squares, one state on lift; backing up along it undoes it.
                    .squareDrag(n, n, unitPx, interactive, onTrail = { path = it }) { p ->
                        val next = latest.drag(p)
                        if (next !== latest) emit(next)
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawBoard(shown, unitPx, scheme, numbers, HintGlow(highlight, glow, pulse.value), feel, vivid(accent))
                }
            }
        }
    }

    /** The squares of the path from end [from] along its lines, in order. */
    internal fun pathOf(v: LinksLogic.View, from: Int): IntArray {
        val out = mutableListOf(from)
        var prev = -1
        var cur = from
        while (out.size <= v.b.squares) {
            val nxt = (0 until 4).map { LinksLogic.next(v.n, cur, it) }
                .firstOrNull { it >= 0 && it != prev && v.m[cur] and (1 shl LinksLogic.dirTo(v.n, cur, it)) != 0 } ?: break
            prev = cur
            cur = nxt
            out += cur
        }
        return out.toIntArray()
    }

    // ---- the home motif -------------------------------------------------------------------------

    /** A fixed, finished 4x4 for the home grid, drawn by the board's own code. Never calls `generate()`. */
    private val motif: LinksState by lazy {
        val paths = listOf(listOf(0, 1, 2, 6), listOf(3, 7, 11, 15), listOf(5, 4, 8, 12, 13), listOf(9, 10, 14))
        val m = IntArray(16)
        val ends = CharArray(16) { '.' }
        paths.forEachIndexed { p, path ->
            ends[path.first()] = 'a' + p
            ends[path.last()] = 'a' + p
            for (i in 1 until path.size) LinksLogic.setLine(4, m, path[i - 1], path[i], true)
        }
        val text = LinksLogic.maskString(m)
        LinksState(4, ends.concatToString(), text, text)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val unitPx = with(density) { (side / motif.size).toPx() }
            val numbers = remember(unitPx) { numberLayouts(measurer, unitPx, density, 4) }
            Canvas(Modifier.size(side)) {
                drawBoard(motif, unitPx, scheme, numbers)
            }
        }
    }
}
