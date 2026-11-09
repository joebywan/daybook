package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.joebywan.daybook.core.nearestPiece
import com.joebywan.daybook.ui.CappedFontScale
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.drawHintDim
import com.joebywan.daybook.ui.drawHintRings
import com.joebywan.daybook.ui.fitStep
import com.joebywan.daybook.ui.gridCellAt
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.wall
import kotlin.math.roundToInt

/**
 * Unblock: a 6x6 yard of cars two or three long, each sliding only along its length; slide them until the red car can
 * leave by the exit on the right of its row. See [UnblockLogic] for the rules and the proof of each board's minimum, and
 * [UnblockTeacher] for the hints. Highlight indices are squares, `row * 6 + col`.
 *
 * A drag slides one car, snapped to whole squares, and is one move on lift (the drag in progress is `remember`, never
 * state). Keys: arrows move a cursor from car to car ([nearestPiece], shared with Pointers), Shift+arrow slides the car
 * under it one square, one move a press.
 */
object Unblock : PuzzleType {

    override val id = "unblock"
    override val displayName = "Unblock"
    override val tagline = "Slide the cars aside and drive the red one out"
    override val accent = 0xFF7B6F3D // AccentSpacingTest's printed candidate
    override val rules = listOf(
        "Get the red car out through the exit on the right of its row.",
        "Drag a car to slide it. A car only slides along its length, forwards or back, as far as the way is clear.",
        "Any slide, however far, is one move. There is no move limit, and Undo takes a slide back.",
        "Every board can be done in its par: the fewest moves it needs, which the finished card compares with yours.",
    )

    override val keyboardHelp = listOf(
        "Arrow keys pick a car; Shift with an arrow slides it one square (each press is a move).",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        UnblockLogic.generateVerified(seed, difficulty) ?: UnblockLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = UnblockTeacher.deduction(state as UnblockState)

    // ---- colours ------------------------------------------------------------------------------

    /** The red car's hue: Coral, free here because nothing on this board is ever wrong or right. */
    private const val RED_HUE = 8f

    /** The other cars' hues (docs/COLOUR.md's content hues less Coral, Gold and Rose, too near the red car), with their names. */
    private val HUES = listOf(268f, 34f, 175f, 215f, 145f)
    val hueNames = listOf("purple", "orange", "teal", "blue", "green")

    /**
     * Each car's hue, an index into [HUES] (the red car's is unused): derived from what never moves (which way a car lies
     * and its line), never stored, so a car keeps its colour as it slides. Two cars lying the same way in one line always
     * differ, so a hint's "the teal truck in column 5" names one car; car `i` tries hue `i mod 5` first, to spread them.
     */
    fun hues(cars: List<UnblockCar>): IntArray {
        val out = IntArray(cars.size)
        for (i in 1 until cars.size) {
            val c = cars[i]
            val used = (1 until i).filter { j ->
                cars[j].across == c.across && (if (c.across) cars[j].row == c.row else cars[j].col == c.col)
            }.map { out[it] }.toSet()
            out[i] = (0 until HUES.size).map { (i + it) % HUES.size }.first { it !in used }
        }
        return out
    }

    fun hueOf(cars: List<UnblockCar>, i: Int): Int = hues(cars)[i]

    // ---- drawing ------------------------------------------------------------------------------

    /**
     * One car at ([left], [top]) in squares of [unit] px, [w] x [h] squares: a rounded body in its hue with a darker rim and
     * a windscreen at the end it faces; the red car carries a chevron pointing to the exit, so it is not told by colour alone.
     */
    private fun DrawScope.drawCar(left: Float, top: Float, w: Float, h: Float, unit: Float, hue: Float, red: Boolean, alpha: Float) {
        val inset = unit * 0.08f
        val x = left * unit + inset
        val y = top * unit + inset
        val cw = w * unit - 2 * inset
        val ch = h * unit - 2 * inset
        val body = BoardHues.mark(hue).copy(alpha = alpha)
        val rim = BoardHues.deep(hue).copy(alpha = alpha)
        val r = CornerRadius(unit * 0.22f)
        drawRoundRect(body, Offset(x, y), Size(cw, ch), r)
        drawRoundRect(rim, Offset(x, y), Size(cw, ch), r, style = Stroke(unit * 0.06f))
        // A windscreen across the front end (the right or bottom), a little in from it.
        val glass = Color.White.copy(alpha = 0.55f * alpha)
        val across = w > h
        if (across) drawRoundRect(glass, Offset(x + cw - unit * 0.42f, y + ch * 0.18f), Size(unit * 0.16f, ch * 0.64f), CornerRadius(unit * 0.06f))
        else drawRoundRect(glass, Offset(x + cw * 0.18f, y + ch - unit * 0.42f), Size(cw * 0.64f, unit * 0.16f), CornerRadius(unit * 0.06f))
        if (red) {
            val cx = x + cw * 0.42f
            val cy = y + ch / 2
            val d = unit * 0.16f
            drawPath(
                Path().apply { moveTo(cx - d * 0.6f, cy - d); lineTo(cx + d * 0.6f, cy); lineTo(cx - d * 0.6f, cy + d) },
                Color.White.copy(alpha = 0.9f * alpha),
                style = Stroke(unit * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    /** The yard: a page, a faint dot in every square, and a frame open at the exit. */
    private fun DrawScope.drawYard(unit: Float, page: Color, frame: Color, dots: Color) {
        val n = UnblockLogic.SIZE
        drawRoundRect(page, Offset.Zero, Size(n * unit, n * unit), CornerRadius(unit * 0.18f))
        for (q in 0 until n * n) drawCircle(dots, unit * 0.05f, Offset((q % n + 0.5f) * unit, (q / n + 0.5f) * unit))
        val w = maxOf(2f, unit * 0.07f)
        val e = n * unit
        val gapTop = UnblockLogic.EXIT_ROW * unit
        val gapBottom = gapTop + unit
        drawPath(
            Path().apply {
                moveTo(e, gapTop); lineTo(e, 0f); lineTo(0f, 0f); lineTo(0f, e); lineTo(e, e); lineTo(e, gapBottom)
            },
            frame, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // The exit: a chevron just outside the gap.
        val cy = (gapTop + gapBottom) / 2
        val d = minOf(unit * 0.18f, 8.dp.toPx())
        val cx = e + d * 1.1f
        drawPath(
            Path().apply { moveTo(cx - d * 0.5f, cy - d); lineTo(cx + d * 0.5f, cy); lineTo(cx - d * 0.5f, cy + d) },
            frame, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    /** Every car of [cars], car [moving] shifted [by] squares along its length (a drag in progress). */
    private fun DrawScope.drawCars(cars: List<UnblockCar>, unit: Float, moving: Int, by: Float, feel: MoveFeel?) {
        val hues = hues(cars)
        for ((i, c) in cars.withIndex()) {
            val shift = if (i == moving) by else 0f
            val left = c.col + if (c.across) shift else 0f
            val top = c.row + if (c.across) 0f else shift
            val w = if (c.across) c.length.toFloat() else 1f
            val h = if (c.across) 1f else c.length.toFloat()
            val first = c.row * UnblockLogic.SIZE + c.col
            val alpha = feel?.alpha(first) ?: 1f
            val s = feel?.scale(first) ?: 1f
            if (s == 1f) drawCar(left, top, w, h, unit, if (i == 0) RED_HUE else HUES[hues[i]], i == 0, alpha)
            else {
                val pivot = Offset((left + w / 2) * unit, (top + h / 2) * unit)
                drawContext.transform.scale(s, s, pivot)
                drawCar(left, top, w, h, unit, if (i == 0) RED_HUE else HUES[hues[i]], i == 0, alpha)
                drawContext.transform.scale(1 / s, 1 / s, pivot)
            }
        }
    }

    // ---- the home motif -----------------------------------------------------------------------

    /** A legal yard mid-play: the red car two squares from a truck in its way. */
    private val motif = listOf(
        UnblockCar(2, 0, 2, true), UnblockCar(0, 3, 3, false), UnblockCar(0, 0, 2, true), UnblockCar(3, 1, 2, false),
        UnblockCar(4, 3, 3, true), UnblockCar(1, 5, 2, false), UnblockCar(3, 5, 2, false),
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        Canvas(modifier) {
            val n = UnblockLogic.SIZE
            val unit = minOf(size.width / (n + 0.6f), size.height / (n + 0.2f))
            translate((size.width - n * unit) / 2 - unit * 0.15f, (size.height - n * unit) / 2) {
                drawYard(unit, scheme.surface, scheme.wall, scheme.onSurfaceVariant.copy(alpha = 0.25f))
                drawCars(motif, unit, -1, 0f, null)
            }
        }
    }

    private inline fun DrawScope.translate(x: Float, y: Float, block: DrawScope.() -> Unit) {
        drawContext.transform.translate(x, y)
        block()
        drawContext.transform.translate(-x, -y)
    }

    // ---- the board ----------------------------------------------------------------------------

    /** A drag in progress: car [car], [by] squares from where it stands (clamped to its room). Transient. */
    private class Drag(val car: Int, val by: Float)

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as UnblockState
        val scheme = MaterialTheme.colorScheme
        val (highlight, glow, pulse) = rememberHintPaint()
        val n = UnblockLogic.SIZE
        val live = interactive && !s.solved
        val owners = remember(s.cars) { UnblockLogic.owners(s.cars) }
        val feel = rememberMoveFeel(owners.toList())
        var drag by remember(s.cars) { mutableStateOf<Drag?>(null) }
        // The keyboard cursor: a square, transient.
        var cursor by remember { mutableStateOf<Int?>(null) }

        Column(
            Modifier
                .fillMaxSize()
                .boardKeys(live, shift = true) { key, shift, _ ->
                    val (dr, dc) = arrowStep(key) ?: return@boardKeys false
                    val at = cursor
                    if (at == null) {
                        cursor = s.cars[0].row * n + s.cars[0].col
                        return@boardKeys true
                    }
                    if (!shift) {
                        cursor = nearestPiece(at, dr, dc, n, n) { owners[it] }
                        return@boardKeys true
                    }
                    val car = owners[at]
                    if (car < 0) return@boardKeys true
                    val c = s.cars[car]
                    val by = if (c.across) dc else dr
                    if (by == 0) return@boardKeys true
                    s.slide(car, by)?.let { next ->
                        cursor = at + dr * n + dc
                        onState(next)
                    }
                    true
                }
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The par and the count so far, one line always this height, so nothing moves the yard.
            Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                CappedFontScale {
                    Text(
                        "Moves ${s.moves} · par ${s.par}",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            val step = fitStep(n, n)
            Canvas(
                Modifier
                    .size(step * n)
                    .highlightGrid(n, n)
                    .gridCursor(cursor.takeIf { live }, n, n, scheme.primary)
                    .pointerInput(s, live) {
                        if (!live) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val unit = size.width.toFloat() / n
                            val car = owners[gridCellAt(down.position, unit, n, n)]
                            if (car < 0) return@awaitEachGesture
                            val (lo, hi) = UnblockLogic.room(s.cars, car)
                            val across = s.cars[car].across
                            var by = 0f
                            drag = Drag(car, 0f)
                            while (true) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                val moved = if (across) c.position.x - down.position.x else c.position.y - down.position.y
                                by = (moved / unit).coerceIn(lo.toFloat(), hi.toFloat())
                                c.consume()
                                if (!c.pressed) break
                                drag = Drag(car, by)
                            }
                            drag = null
                            val whole = by.roundToInt()
                            if (whole != 0) s.slide(car, whole)?.let(onState)
                        }
                    },
            ) {
                val unit = size.width / n
                drawYard(unit, scheme.surface, scheme.wall, scheme.onSurfaceVariant.copy(alpha = 0.25f))
                val d = drag
                drawCars(s.cars, unit, d?.car ?: -1, d?.by ?: 0f, feel)
                drawHintDim(highlight, n, n, unit, scheme.background)
                drawHintRings(highlight, n, n, unit, glow, pulse.value)
            }
            }
        }
    }

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * The red car in row 3, a car lying down in column 5 across its way, a truck along the top above that car, a car below
     * it and one more in the corner. The truck goes left, the car in the way goes up, and the red car drives out: three
     * moves, the fewest.
     */
    internal fun tutorialBoard(): UnblockState = UnblockState(
        listOf(
            UnblockCar(2, 0, 2, true), UnblockCar(1, 4, 2, false), UnblockCar(0, 3, 3, true),
            UnblockCar(4, 4, 2, false), UnblockCar(5, 0, 2, true),
        ),
        par = 3,
    )

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.slide(2, -3)!!
        val s2 = s1.slide(1, -1)!!
        val blocker = UnblockTeacher.name(s0.cars, 1)
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Get the red car out through the exit on the right. Each car slides only along its length, as far as the way is clear.",
                highlight = BoardHighlight(strong = s0.cars[0].cells().toSet(), soft = s0.cars.drop(1).flatMap { it.cells() }.toSet()),
            ),
            TutorialFrame(
                state = s0,
                caption = "${blocker.replaceFirstChar { it.uppercase() }} is in the red car's way, and the truck above it is in its way. " +
                    "Drag the truck left, as far as it goes.",
                highlight = BoardHighlight(strong = s0.cars[2].cells().toSet(), soft = s0.cars[1].cells().toSet()),
                accepts = { next -> next is UnblockState && next.cars == s1.cars },
                retry = "Drag the glowing truck all the way left.",
                done = "The truck is out of the way: one slide, one move.",
            ),
            TutorialFrame(
                state = s1,
                caption = "Now drag $blocker up, out of the red car's row.",
                highlight = BoardHighlight(strong = s1.cars[1].cells().toSet()),
                accepts = { next -> next is UnblockState && next.cars == s2.cars },
                retry = "Drag the glowing car up.",
                done = "The red car's way is clear.",
            ),
            TutorialFrame(
                state = s2,
                caption = "Your turn: drive the red car out. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Out in three moves, the fewest this yard allows.",
            ),
        )
    }
}
