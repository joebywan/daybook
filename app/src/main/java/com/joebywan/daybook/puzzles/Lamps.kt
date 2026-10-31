package com.joebywan.daybook.puzzles

import com.joebywan.daybook.ui.theme.vivid
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.core.orthogonalReading
import com.joebywan.daybook.ui.Cell
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.fitStep
import com.joebywan.daybook.ui.gridCellAt
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.rememberSettled
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Lamps. The board is one canvas of squares: walls (numbered or blank) and white squares, lit squares washed in the
 * lamps' light. A tap marks a cross (a note, never counted), a double tap places a lamp, a tap on a lamp takes it away,
 * a drag lays crosses (Kings' and Tents' model). Highlight indices are squares, `row * size + col`.
 */
object Lamps : PuzzleType {

    private const val DOUBLE_TAP_MS = 300L
    private val tapClock = TimeSource.Monotonic.markNow()

    private data class Pending(val cell: Int, val base: LampsState, val after: LampsState)

    override val id = "lamps"
    override val displayName = "Lamps"
    override val tagline = "Light every square, no lamp seeing another"
    override val accent = 0xFF737B39
    override val rules = listOf(
        "A lamp lights its own square and its whole row and column, up to a wall or the edge.",
        "Light every white square. No lamp may light another lamp.",
        "A number on a wall is how many lamps sit right beside it: above, below, left or right.",
        "Tap a square for a cross (a note that no lamp goes there), tap again to clear it. Drag to lay crosses across squares. Double-tap or long-press for a lamp; tap a lamp to remove it.",
    )

    override val keyboardHelp = listOf(
        "Arrows move, L or Enter places a lamp, X or Space a cross, Backspace clears.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = LampsLogic.generate(seed, difficulty)

    override fun teach(state: PuzzleState): Deduction? = LampsTeacher.deduction(state as LampsState)

    /** A look-ahead (whichever the walk from blank went deepest to) on Hard, a what-if on Expert; null for the direct facts. */
    override fun neededTechnique(state: PuzzleState): String? = LampsLogic.deepest(state as LampsState)

    // ---- walkthrough --------------------------------------------------------------------------

    private const val TUTORIAL_N = 5

    /**
     * A 2 in the top-left corner, a 0 at row 2 column 4, a 1 in the middle and a blank wall in the bottom-right corner.
     * The one answer (a test enumerates it) has lamps at 1, 5, 14, 17 and 23, and the direct facts alone find it.
     */
    internal const val TUTORIAL_WALLS = "2...." + "...0." + "..1.." + "....." + "....#"
    internal const val TUTORIAL_SOLUTION = "01000" + "10000" + "00001" + "00100" + "00010"

    internal fun tutorialBoard(marks: String = ".".repeat(TUTORIAL_N * TUTORIAL_N)) =
        LampsState(TUTORIAL_N, TUTORIAL_WALLS, TUTORIAL_SOLUTION, marks)

    override val tutorial: List<TutorialFrame> by lazy {
        val solved = tutorialBoard(TUTORIAL_SOLUTION.map { if (it == '1') LampsLogic.LAMP_MARK else LampsLogic.NONE }.joinToString(""))
        val empty = tutorialBoard()
        val crossed = empty.tapped(13)
        val swept = crossed.crossed(setOf(7, 8, 9))
        val pair = setOf(1, 5)
        val lamps = swept.withLamp(1).withLamp(5)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "A lamp lights its own square and its whole row and column, up to a wall. Light every white " +
                    "square, and never let one lamp light another.",
                highlight = BoardHighlight(strong = setOf(17), soft = setOf(15, 16, 18, 19, 22)),
            ),
            TutorialFrame(
                state = solved,
                caption = "A number on a wall is how many lamps sit right beside it: above, below, left or right. " +
                    "This 2 has two.",
                highlight = BoardHighlight(strong = setOf(0), soft = pair),
            ),
            TutorialFrame(
                state = empty,
                caption = "The 0 has no lamps beside it. Tap the glowing square below it to cross it out: a note that no " +
                    "lamp goes there.",
                highlight = BoardHighlight(strong = setOf(13), soft = setOf(8)),
                accepts = { next -> next is LampsState && next.marks == crossed.marks },
                retry = "Tap the glowing square once.",
                done = "Crossed out. No lamp can go there.",
            ),
            TutorialFrame(
                state = crossed,
                caption = "Its squares to the left and right are the same. Drag across the 0 from one glowing square to " +
                    "the other to cross out both in one sweep.",
                highlight = BoardHighlight(strong = setOf(7, 9), soft = setOf(8)),
                accepts = { next -> next is LampsState && next.marks == swept.marks },
                retry = "Drag across the 0, from one glowing square to the other.",
                done = "Swept: one drag crosses out a whole run.",
            ),
            TutorialFrame(
                state = swept,
                caption = "The 2 in the corner has only two squares beside it, so both hold lamps. Double-tap or long-press each " +
                    "glowing square to place one.",
                glow = { s ->
                    BoardHighlight(strong = pair.filter { (s as LampsState).marks[it] != LampsLogic.LAMP_MARK }.toSet(), soft = setOf(0))
                },
                // Either order; taking one back on the way is fine too.
                steps = { next ->
                    when {
                        next !is LampsState -> TutorialStep.REJECT
                        next.marks == lamps.marks -> TutorialStep.DONE
                        next.marks in pair.map { swept.withLamp(it).marks } + listOf(swept.marks) -> TutorialStep.PROGRESS
                        else -> TutorialStep.REJECT
                    }
                },
                retry = "Double-tap the glowing square (two quick taps), or press and hold it.",
                done = "Two lamps, and their light fills their rows and columns.",
            ),
            TutorialFrame(
                state = lamps,
                caption = "Your turn: light every square. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every square lit, and no lamp sees another.",
            ),
        )
    }

    // ---- drawing ------------------------------------------------------------------------------

    /** The light, the bulbs: Amber. The crosses: Blue. Fixed, so a lamp always reads as a lamp. */
    private const val HUE_LIGHT = 34f
    private const val HUE_CROSS = 215f

    /** What the board draws on top: the highlight, and what is bad (already debounced). */
    private class Overlay(
        val highlight: BoardHighlight = BoardHighlight.None,
        val glow: Color = Color.Unspecified,
        val pulse: Float = 1f,
        val badLamps: Set<Int> = emptySet(),
        val badWalls: Set<Int> = emptySet(),
    )

    /** Numbered walls that already have too many lamps beside them, or can no longer get enough (crosses ignored). */
    internal fun badNumbers(s: LampsState, lit: BooleanArray = s.lit()): Set<Int> = (0 until s.size * s.size).filterTo(mutableSetOf()) { w ->
        val want = s.numberAt(w)
        if (want < 0) return@filterTo false
        val beside = orthogonalReading(w, s.size).filter { !s.isWall(it) }
        val on = beside.count { s.marks[it] == LampsLogic.LAMP_MARK }
        val room = beside.count { s.marks[it] != LampsLogic.LAMP_MARK && !lit[it] }
        on > want || on + room < want
    }

    private fun DrawScope.drawBoard(
        s: LampsState,
        u: Float,
        digits: List<TextLayoutResult>,
        scheme: ColorScheme,
        overlay: Overlay,
        feel: MoveFeel = MoveFeel.Still,
        accent: Color = Color.Unspecified,
    ) {
        val n = s.size
        val dark = BoardHues.isDark(scheme)
        val light = BoardHues.fill(HUE_LIGHT, dark).copy(alpha = if (dark) 0.75f else 0.6f)
        val bulb = BoardHues.mark(HUE_LIGHT)
        val rimInk = BoardHues.ink(HUE_LIGHT, dark)
        val crossInk = BoardHues.ink(HUE_CROSS, dark)
        val hl = overlay.highlight
        val dim = !hl.isEmpty
        fun named(i: Int) = i in hl.strong || i in hl.soft
        val lit = s.lit()

        // Walls are the darkest thing on the board in both schemes: ink on the light card; in dark, the background's
        // ink under squares raised to surfaceVariant, so a wall never reads as a white square.
        val square = if (dark) scheme.surfaceVariant else scheme.surface
        val wall = if (dark) scheme.background else scheme.onSurface
        val wallText = if (dark) scheme.onSurface else scheme.surface
        drawRoundRect(square, Offset.Zero, Size(n * u, n * u), CornerRadius(u * 0.12f))
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            val x = c * u
            val y = r * u
            feelSquare(feel, i, Offset(x, y), Size(u, u), accent) {
                val fade = if (dim && !named(i)) 0.45f else 1f
                if (s.isWall(i)) {
                    val want = s.numberAt(i)
                    // A 0 is met from the start, so only a number with lamps to find turns green when it has them.
                    val met = want > 0 && s.lampsBeside(i) == want
                    val block = when {
                        i in overlay.badWalls -> scheme.error
                        met -> scheme.primary
                        else -> wall
                    }
                    drawRect(block.copy(alpha = fade), Offset(x, y), Size(u, u))
                    if (want >= 0) {
                        val layout = digits[want]
                        val ink = when {
                            i in overlay.badWalls -> scheme.onError
                            met -> scheme.onPrimary
                            else -> wallText
                        }
                        drawText(layout, ink.copy(alpha = fade), Offset(x + u / 2 - layout.size.width / 2f, y + u / 2 - layout.size.height / 2f))
                    }
                } else {
                    if (lit[i]) drawRect(light.copy(alpha = light.alpha * fade), Offset(x, y), Size(u, u))
                    when (s.marks[i]) {
                        LampsLogic.LAMP_MARK -> drawLamp(x, y, u, if (i in overlay.badLamps) scheme.error else bulb, rimInk, fade)
                        LampsLogic.CROSS -> drawCross(x, y, u, crossInk.copy(alpha = fade))
                    }
                }
            }
        }
        val thin = maxOf(1f, u * 0.025f)
        for (i in 0..n) {
            drawLine(scheme.outline, Offset(i * u, 0f), Offset(i * u, n * u), thin)
            drawLine(scheme.outline, Offset(0f, i * u), Offset(n * u, i * u), thin)
        }
        if (dim) {
            hintRims(hl, u, overlay.glow, overlay.pulse, scheme.background) { indices, width, color ->
                for (i in indices) {
                    val tl = Offset((i % n) * u + width / 2, (i / n) * u + width / 2)
                    drawRoundRect(color, tl, Size(u - width, u - width), CornerRadius(u * 0.15f), style = Stroke(width))
                }
            }
        }
    }

    /** A bulb: a round glass on a short base, with rays. */
    private fun DrawScope.drawLamp(x: Float, y: Float, u: Float, glass: Color, rim: Color, alpha: Float) {
        val cx = x + u / 2
        val cy = y + u * 0.44f
        val ray = maxOf(1.5f, u * 0.05f)
        for (k in 0 until 8) {
            val a = k * kotlin.math.PI / 4
            val dx = kotlin.math.cos(a).toFloat()
            val dy = kotlin.math.sin(a).toFloat()
            drawLine(glass.copy(alpha = 0.7f * alpha), Offset(cx + dx * u * 0.29f, cy + dy * u * 0.29f), Offset(cx + dx * u * 0.4f, cy + dy * u * 0.4f), ray, StrokeCap.Round)
        }
        drawCircle(glass.copy(alpha = alpha), u * 0.21f, Offset(cx, cy))
        drawCircle(rim.copy(alpha = alpha), u * 0.21f, Offset(cx, cy), style = Stroke(maxOf(1.5f, u * 0.045f)))
        drawRoundRect(rim.copy(alpha = alpha), Offset(cx - u * 0.09f, cy + u * 0.2f), Size(u * 0.18f, u * 0.13f), CornerRadius(u * 0.03f))
    }

    private fun DrawScope.drawCross(x: Float, y: Float, u: Float, ink: Color) {
        val w = maxOf(2f, u * 0.07f)
        val a = u * 0.36f
        val b = u * 0.64f
        drawLine(ink, Offset(x + a, y + a), Offset(x + b, y + b), w, StrokeCap.Round)
        drawLine(ink, Offset(x + b, y + a), Offset(x + a, y + b), w, StrokeCap.Round)
    }

    private fun digitLayouts(measurer: androidx.compose.ui.text.TextMeasurer, fontPx: Float, density: Density): List<TextLayoutResult> {
        val style = TextStyle(fontSize = with(density) { fontPx.toSp() }, fontWeight = FontWeight.SemiBold)
        return (0..4).map { measurer.measure(it.toString(), style) }
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** A 2 with its two lamps, and two more lamps lighting the rest: a legal 4x4, never generated. */
    private val motif = LampsState(
        size = 4,
        walls = "....." + "2" + "..........",
        solution = "0100" + "1000" + "0010" + "0001",
        marks = ".L.." + "L..." + "..L." + "...L",
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val unit = minOf(maxWidth, maxHeight) / motif.size
            val unitPx = with(density) { unit.toPx() }
            val digits = remember(unitPx) { digitLayouts(measurer, unitPx * 0.55f, density) }
            Canvas(Modifier.size(unit * motif.size)) {
                drawBoard(motif, unitPx, digits, scheme, Overlay())
            }
        }
    }

    // ---- the board ----------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val base = state as LampsState
        val n = base.size
        // A single tap (a cross) is drawn at once but handed to the screen only after the double-tap window, so a double
        // tap becomes one lamp move, not a cross under it (as Kings' crowns and Tents' tents). [Pending.base] ties it to
        // the board it was made on: undo or a hint drops it.
        var pending by remember { mutableStateOf<Pending?>(null) }
        var lastCell by remember { mutableIntStateOf(-1) }
        var lastTapAt by remember { mutableLongStateOf(0L) }
        val held = pending?.takeIf { it.base === base }
        // A sweep crosses every empty square the finger passes, drawn as it goes and emitted once on lift.
        var sweepBase by remember { mutableStateOf<LampsState?>(null) }
        var swept by remember { mutableStateOf(emptySet<Int>()) }
        val s = sweepBase?.crossed(swept) ?: held?.after ?: base
        val onStateNow by rememberUpdatedState(onState)
        val scope = rememberCoroutineScope()
        val latest = remember { Cell(base) }.also { it.v = base }
        val commit = remember { Cell<Job?>(null) }
        fun emit(next: LampsState) {
            latest.v = next
            onStateNow(next)
        }

        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val (highlight, glow, pulse) = rememberHintPaint()

        // Wrong now, and still wrong a second on: lamps lighting each other, numbers over or out of reach. Derived.
        val bad = s.clashes() to badNumbers(s)
        val settled = rememberSettled(bad, s.walls, s.marks)

        // A numbered wall's squares glow once when its count is met.
        val units = remember(base.walls) {
            (0 until n * n).filter { base.numberAt(it) > 0 }.map { w ->
                (listOf(w) + orthogonalReading(w, n).filter { !base.isWall(it) }).toIntArray()
            }
        }
        val feel = rememberMoveFeel(base.marks.toList(), units) { u ->
            val w = units[u][0]
            base.lampsBeside(w) == base.numberAt(w)
        }

        var cursor by remember(s.walls) { mutableStateOf<Int?>(null) }

        BoxWithConstraints(
            Modifier.fillMaxWidth().boardKeys(interactive) { key, _, repeat ->
                movedCursor(cursor, key, n, n)?.let { cursor = it; return@boardKeys true }
                val at = cursor ?: return@boardKeys false
                val action = lampsKeyAction(key) ?: return@boardKeys false
                if (!repeat) s.applyKey(at, action)?.let(onState)
                true
            }.padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            val unit = fitStep(n, n)
            val unitPx = with(density) { unit.toPx() }
            val digits = remember(unitPx) { digitLayouts(measurer, unitPx * 0.55f, density) }
            val overlay = Overlay(highlight, glow, pulse.value, bad.first.intersect(settled.first), bad.second.intersect(settled.second))

            Box(
                Modifier
                    .size(unit * n)
                    .highlightGrid(n, n)
                    .gridCursor(cursor.takeIf { interactive }, n, n, scheme.primary)
                    .pointerInput(interactive) {
                        if (!interactive) return@pointerInput
                        fun cellAt(o: Offset) = gridCellAt(o, unitPx, n, n)
                        detectDragGestures(
                            onDragStart = { o ->
                                // Any tap still held rides along as the sweep's base.
                                commit.v?.cancel()
                                sweepBase = pending?.takeIf { it.base === latest.v }?.after ?: latest.v
                                pending = null
                                lastCell = -1
                                swept = setOf(cellAt(o))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val i = cellAt(change.position)
                                if (i !in swept) swept = swept + i
                            },
                            onDragEnd = {
                                val next = sweepBase!!.crossed(swept)
                                sweepBase = null
                                swept = emptySet()
                                if (next !== latest.v) emit(next)
                            },
                            onDragCancel = { sweepBase = null; swept = emptySet() },
                        )
                    }
                    .pointerInput(interactive, unitPx) {
                        if (!interactive) return@pointerInput
                        // A long press is the double tap's twin, as in Tents and Kings: a lamp, or one taken back.
                        // `onLongPress` never delays a tap (only `onDoubleTap` would).
                        detectTapGestures(
                            onLongPress = { offset ->
                                val i = gridCellAt(offset, unitPx, n, n)
                                val held = pending?.takeIf { it.base === latest.v }
                                commit.v?.cancel()
                                lastCell = -1
                                pending = null
                                val from = if (held != null && held.cell != i) held.after else latest.v
                                val next = from.withLamp(i)
                                if (next !== latest.v) emit(next)
                            },
                        ) { offset ->
                            val i = gridCellAt(offset, unitPx, n, n)
                            val held = pending?.takeIf { it.base === latest.v }
                            commit.v?.cancel()
                            if ((held?.after ?: latest.v).marks[i] == LampsLogic.LAMP_MARK) { // taking a lamp back is one tap
                                lastCell = -1
                                pending = null
                                emit((held?.after ?: latest.v).withLamp(i))
                                return@detectTapGestures
                            }
                            val now = tapClock.elapsedNow().inWholeMilliseconds
                            if (i == lastCell && now - lastTapAt < DOUBLE_TAP_MS) {
                                lastCell = -1
                                pending = null
                                val next = latest.v.withLamp(i)
                                if (next !== latest.v) emit(next)
                                return@detectTapGestures
                            }
                            lastCell = i
                            lastTapAt = now
                            // A tap on another square settles the one held before it.
                            if (held != null && held.cell != i) emit(held.after)
                            val from = latest.v
                            val next = from.tapped(i)
                            if (next === from) {
                                pending = null
                            } else {
                                val p = Pending(i, from, next)
                                pending = p
                                commit.v = scope.launch {
                                    delay(DOUBLE_TAP_MS)
                                    if (pending === p && p.base === latest.v) {
                                        pending = null
                                        emit(p.after)
                                    }
                                }
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) { drawBoard(s, unitPx, digits, scheme, overlay, feel, vivid(accent)) }
            }
        }
    }
}
