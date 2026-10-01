package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import kotlinx.serialization.Serializable

@Serializable
data class SnapState(
    val width: Int,
    val height: Int,
    /** Cell index -> waypoint number, or 0 where there is no number. */
    val waypoints: List<Int>,
    val path: List<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    val cellCount: Int get() = width * height

    override val solved: Boolean
        get() = path.size == cellCount && Snap.respectsOrder(this, path)

    fun reset(): SnapState = copy(path = emptyList(), moves = moves + 1)
}

/**
 * Snap — one unbroken line through every square, touching the numbers in order.
 *
 * The solution is a Hamiltonian path laid down first; numbers are then dropped onto it, one at a
 * time, until no other Hamiltonian path obeys them.
 */
object Snap : PuzzleType {

    override val id = "snap"
    override val displayName = "Snap"
    override val tagline = "One line, every square, numbers in order"
    override val accent = 0xFFD9584C
    override val rules = listOf(
        "Draw one continuous line from 1 to the highest number.",
        "The line must pass through every square exactly once.",
        "It must reach the numbered squares in ascending order.",
        "Drag to draw. Lift off and carry on from the end whenever you like.",
        "Drag back along the line, or start again from a square on it, to rub it out.",
    )

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 5 to 5
        Difficulty.HARD -> 6 to 6
        Difficulty.EXPERT -> 6 to 7
    }

    /**
     * The home tile: a solved 3x3 board, spiralling in from 1 through 2 to 3.
     *
     * Three across rather than a real board's five, because the line is what identifies Snap and a
     * five-wide grid at 80dp makes it a thread. A spiral was chosen over the obvious
     * back-and-forth: a boustrophedon reads as stripes at a glance, whereas a spiral is
     * unmistakably one line that had to find its own way round.
     *
     * The numbers sit at the path's first, fifth and last steps, so the illustration obeys the
     * rule it is advertising — every square visited once, waypoints met in order.
     */
    private const val PREVIEW_SIDE = 3
    private val previewPath = listOf(0, 1, 2, 5, 8, 7, 6, 3, 4)
    private val previewWaypoints = listOf(1, 0, 0, 0, 3, 0, 0, 0, 2)

    /**
     * How many numbers a board may carry. Roughly a third of the squares, which is where a Snap
     * board stops reading as a dot-to-dot and starts asking the player to work the line out.
     *
     * This is a ceiling the generator must come in under, not a target: [trim] takes away every
     * number the rest of the board already implies, and most boards land well below the cap. A
     * seed that cannot be forced within it is abandoned for the next one.
     */
    private fun clueBudget(w: Int, h: Int): Int = maxOf(4, (w * h) / 3)

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (w, h) = shape(difficulty)
        val budget = clueBudget(w, h)

        repeat(160) { attempt ->
            val rng = Rng(seed + attempt)
            val path = hamiltonian(rng, w, h) ?: return@repeat
            val chosen = force(rng, w, h, path, budget) ?: return@repeat
            return SnapState(w, h, labels(w, h, path, chosen), emptyList())
        }

        // Fallback: number every square along a simple boustrophedon path. Trivially the one
        // answer, and trivially no fun — `FallbackTest` asserts the clue budget precisely so that
        // this showing up in a shipped board fails the build rather than reaching a player.
        val marks = MutableList(w * h) { 0 }
        val path = boustrophedon(w, h)
        path.forEachIndexed { rank, cell -> marks[cell] = rank + 1 }
        return SnapState(w, h, marks.toList(), emptyList())
    }

    /** The numbers a board carries: the chosen steps of [path], ranked along it. */
    private fun labels(w: Int, h: Int, path: List<Int>, chosen: List<Int>): List<Int> {
        val marks = MutableList(w * h) { 0 }
        chosen.sorted().forEachIndexed { rank, at -> marks[path[at]] = rank + 1 }
        return marks
    }

    /**
     * Numbers to pin [path] down as the only answer, or null if this path cannot be pinned down
     * inside [budget].
     *
     * Numbers go on at random until the board is forced, and then [trim] takes back every one the
     * others already imply. Adding and then subtracting matters: the order clues arrive in decides
     * which ones end up carrying their weight, and a clue that was essential when it went on is
     * very often redundant by the time five more have joined it. Stopping at the first forced
     * board — which is what this used to do — left every one of those passengers on the grid, and
     * was why boards arrived with two thirds of their squares numbered.
     */
    private fun force(rng: Rng, w: Int, h: Int, path: List<Int>, budget: Int): List<Int>? {
        val chosen = mutableListOf(0, path.lastIndex)
        while (true) {
            when (classify(w, h, labels(w, h, path, chosen))) {
                Verdict.UNIQUE -> return trim(rng, w, h, path, chosen, budget)
                Verdict.MANY -> Unit
                // NONE cannot happen — `path` itself obeys the numbers — and UNPROVED is not a
                // proof of anything. Either way this seed is finished.
                else -> return null
            }
            val candidates = (1 until path.lastIndex).filterNot { it in chosen }
            if (candidates.isEmpty()) return null
            chosen += rng.pick(candidates)
        }
    }

    /**
     * Takes away every number the rest of the board already implies, and reports failure if what
     * is left still exceeds [budget].
     *
     * The two ends stay: a line may only begin at 1, so the board needs somewhere to start, and
     * the highest number is what tells the player where it is meant to finish. Everything between
     * them has to earn its place by being the difference between one answer and several. The order
     * clues are offered up in is shuffled, because dropping them in path order would strip the
     * early ones and leave a board whose numbers all huddle at the end.
     */
    private fun trim(rng: Rng, w: Int, h: Int, path: List<Int>, chosen: List<Int>, budget: Int): List<Int>? {
        var kept = chosen.toList()
        for (at in rng.shuffled(kept.filter { it != 0 && it != path.lastIndex })) {
            val without = kept - at
            if (classify(w, h, labels(w, h, path, without)) == Verdict.UNIQUE) kept = without
        }
        return kept.takeIf { it.size <= budget }
    }

    private fun boustrophedon(w: Int, h: Int): List<Int> = buildList {
        for (r in 0 until h) {
            val cols = if (r % 2 == 0) 0 until w else (w - 1) downTo 0
            for (c in cols) add(r * w + c)
        }
    }

    private fun neighbours(cell: Int, w: Int, h: Int): List<Int> {
        val r = cell / w
        val c = cell % w
        return buildList {
            if (r > 0) add(cell - w)
            if (r < h - 1) add(cell + w)
            if (c > 0) add(cell - 1)
            if (c < w - 1) add(cell + 1)
        }
    }

    /** Randomised search for a Hamiltonian path, starting from a random square. */
    private fun hamiltonian(rng: Rng, w: Int, h: Int): List<Int>? {
        val n = w * h
        val seen = BooleanArray(n)
        val path = ArrayList<Int>(n)
        var nodes = 0

        fun extend(cell: Int): Boolean {
            if (nodes++ > 200_000) return false
            seen[cell] = true
            path += cell
            if (path.size == n) return true
            // Warnsdorff-ish: try the most hemmed-in neighbour first.
            val options = rng.shuffled(neighbours(cell, w, h).filter { !seen[it] })
                .sortedBy { neighbours(it, w, h).count { next -> !seen[next] } }
            for (next in options) {
                if (extend(next)) return true
            }
            seen[cell] = false
            path.removeAt(path.lastIndex)
            return false
        }

        return if (extend(rng.nextInt(n))) path.toList() else null
    }

    fun respectsOrder(s: SnapState, path: List<Int>): Boolean {
        val seen = path.mapNotNull { cell ->
            s.waypoints[cell].takeIf { it > 0 }
        }
        return seen == seen.sorted() && seen == (1..seen.size).toList() &&
            seen.size == s.waypoints.count { it > 0 }
    }

    /**
     * What a bounded search is allowed to say.
     *
     * Only [UNIQUE] is a proof, and only a proof may ship. The count this replaced was an Int that
     * a caller could read as "one solution" when what had actually happened was that the search
     * ran out of budget and returned quietly — the same shape of mistake LITS made, which is why
     * the give-up case is a name here rather than a number.
     */
    private enum class Verdict { NONE, UNIQUE, MANY, UNPROVED }

    /**
     * Generous, because it is only spent when the pruning below has already failed to decide the
     * board, and because a board that cannot be decided is thrown away rather than shipped — so
     * the cost of a budget set too low is worse puzzles, not wrong ones.
     */
    private const val NODE_BUDGET = 400_000

    /** Classifies the Hamiltonian paths obeying [marks], stopping once two are in hand. */
    private fun classify(w: Int, h: Int, marks: List<Int>): Verdict {
        val n = w * h
        val total = marks.count { it > 0 }
        val start = marks.indexOf(1)
        if (start < 0) return Verdict.NONE

        val nbr = Array(n) { neighbours(it, w, h).toIntArray() }
        val seen = BooleanArray(n)
        val stack = IntArray(n)
        val stamp = IntArray(n)
        var era = 0
        var found = 0
        var nodes = 0
        var gaveUp = false

        /**
         * Whether the unvisited squares could still hold the rest of the line: all of them reachable
         * from [head], none of them walled off with no way out, and no more than two of them left
         * with a single way out — a square with one exit can only be an end of the line, and a line
         * has two ends.
         *
         * This is what makes a sparsely numbered board decidable at all. Without it the search is
         * still wandering the same dead end when the budget runs out, which is the whole reason the
         * old generator kept piling numbers on: it was not that the board needed them, it was that
         * nothing could prove otherwise in time.
         */
        fun viable(head: Int, remaining: Int): Boolean {
            era++
            var top = 0
            var reached = 0
            for (x in nbr[head]) if (!seen[x] && stamp[x] != era) {
                stamp[x] = era
                stack[top++] = x
                reached++
            }
            while (top > 0) {
                val cur = stack[--top]
                for (x in nbr[cur]) if (!seen[x] && stamp[x] != era) {
                    stamp[x] = era
                    stack[top++] = x
                    reached++
                }
            }
            if (reached != remaining) return false

            var ends = 0
            for (v in 0 until n) {
                if (seen[v]) continue
                var degree = 0
                for (x in nbr[v]) if (!seen[x] || x == head) degree++
                if (degree == 0) return false
                if (degree == 1 && ++ends > 2) return false
            }
            return true
        }

        fun walk(cell: Int, depth: Int, nextLabel: Int) {
            if (found >= 2) return
            if (nodes++ > NODE_BUDGET) {
                gaveUp = true
                return
            }
            val label = marks[cell]
            var expecting = nextLabel
            if (label > 0) {
                if (label != nextLabel) return
                expecting = nextLabel + 1
            }
            seen[cell] = true
            if (depth == n) {
                if (expecting == total + 1) found++
            } else if (viable(cell, n - depth)) {
                for (next in nbr[cell]) {
                    if (!seen[next]) walk(next, depth + 1, expecting)
                    if (found >= 2 || gaveUp) break
                }
            }
            seen[cell] = false
        }

        walk(start, 1, 1)
        return when {
            // Two answers in hand is a proof however the search ended; one is only a proof if the
            // search finished looking for a second.
            found >= 2 -> Verdict.MANY
            gaveUp -> Verdict.UNPROVED
            found == 1 -> Verdict.UNIQUE
            else -> Verdict.NONE
        }
    }

    // ---- play ---------------------------------------------------------------------------------

    /**
     * No hints, on purpose, and measured rather than assumed.
     *
     * A line only grows from its end, so a hint can only ever be "the next square is this one". A
     * teacher reasoning from the end of the line was written and walked over 100 boards per tier
     * (the corner with two ways in, the only way on, ruling a way out because it steps on a number
     * early, cuts off a pocket or strands a square, and following forced moves ahead): it explained
     * the easy squares and then had nothing to say at the real choices. It fell back to pointing at
     * the answer on 6% of steps on Standard, 13% on Hard and 16% on Expert, and on 89%, 100% and
     * 100% of boards. Following forced moves all the way down instead of eight deep barely moved it
     * (5.6 / 12.4 / 15.4%). The choices those were are exactly what the sparse numbering exists to
     * leave the player, so hints here would mean explaining the obvious squares and handing out
     * the interesting ones — tracing by numbers again. The walkthrough teaches the reasoning instead.
     */
    override val offersHints = false

    // ---- walkthrough --------------------------------------------------------------------------

    /**
     * The walkthrough board, 4x4:
     * ```
     *  .  1  .  .
     *  .  4  .  .
     *  .  2  .  .
     *  .  .  3  .
     * ```
     * It has exactly one answer ([TUTORIAL_SOLUTION]), proved in `SnapTutorialTest` by an
     * enumerator that shares nothing with this file. Every square of it can be reasoned with what
     * the frames teach: corners with two ways in, the only way on, and numbers never met early.
     */
    internal const val TUTORIAL_W = 4
    internal val TUTORIAL_WAYPOINTS = listOf(
        0, 1, 0, 0,
        0, 4, 0, 0,
        0, 2, 0, 0,
        0, 0, 3, 0,
    )
    internal val TUTORIAL_SOLUTION = listOf(1, 0, 4, 8, 12, 13, 9, 10, 14, 15, 11, 7, 3, 2, 6, 5)

    private fun tutorialBoard(path: List<Int>) = SnapState(TUTORIAL_W, TUTORIAL_W, TUTORIAL_WAYPOINTS, path)

    /** The answer's first [n] squares. */
    private fun answerTo(n: Int) = TUTORIAL_SOLUTION.take(n)

    /**
     * Accepts exactly the line [path], however many moves it took. Strict, as Pipes' is: each
     * frame's board is written for the one before it. It also has to be, because the board emits
     * one state per square: a drag that starts on the end of the line first hands in the line
     * unchanged, and every square of a wrong turn is a state of its own. Those are all refused and
     * not applied, while the board keeps following the drag, so the one wanted state still arrives.
     */
    private fun line(path: List<Int>): (PuzzleState) -> Boolean = { next ->
        next is SnapState && next.path == path
    }

    /**
     * Every gesture the board has, each on a move whose reason the caption gives: start on 1, lift
     * and carry on from the end, cut the line back by starting on a square of it, and drag back
     * along it. Each gesture frame wants exactly one emitted state, because the runner applies only
     * the states a frame accepts. The two mistake boards are the line as the frame before left it
     * plus a wrong turn, so the story stays one line.
     */
    override val tutorial: List<TutorialFrame> by lazy {
        val corner = 0
        val below = 4
        val numbered = TUTORIAL_WAYPOINTS.indices.filter { TUTORIAL_WAYPOINTS[it] > 0 }.toSet()
        listOf(
            TutorialFrame(
                state = tutorialBoard(TUTORIAL_SOLUTION),
                caption = "Draw one line from 1 to the highest number. It passes through every " +
                    "square exactly once, and meets the numbers in order: 1, 2, 3, 4.",
                highlight = BoardHighlight(strong = numbered),
            ),
            TutorialFrame(
                state = tutorialBoard(emptyList()),
                caption = "A corner touches only two squares. The line must come in by one and " +
                    "leave by the other, unless it ends there, and only the last number is an end.",
                highlight = BoardHighlight(strong = setOf(corner), soft = setOf(1, below)),
            ),
            TutorialFrame(
                state = tutorialBoard(emptyList()),
                caption = "Lines start on 1. Leave 1 any other way and this corner is left one way " +
                    "in: a dead end. Put your finger on 1 and drag into the corner.",
                highlight = BoardHighlight(strong = setOf(1, corner)),
                accepts = line(answerTo(2)),
                retry = "Start on 1 and drag into the glowing corner.",
                done = "Every line starts on 1.",
            ),
            TutorialFrame(
                state = tutorialBoard(answerTo(2)),
                caption = "Lift your finger whenever you like: the line stays. Put it back on the " +
                    "end of the line and drag down, the corner's only other way out.",
                highlight = BoardHighlight(strong = setOf(below), soft = setOf(corner)),
                accepts = line(answerTo(3)),
                retry = "From the end of the line, drag down one square.",
                done = "Carry on from the end any time.",
            ),
            TutorialFrame(
                state = tutorialBoard(answerTo(3)),
                caption = "The square to the right is 4, but 2 and 3 have to come first. So the " +
                    "line can only go down.",
                highlight = BoardHighlight(strong = setOf(8), soft = setOf(5)),
                accepts = line(answerTo(4)),
                retry = "Drag down one square from the end of the line.",
                done = "Numbers come in order, never early.",
            ),
            TutorialFrame(
                state = tutorialBoard(answerTo(4) + listOf(9, 10)),
                caption = "Say you dashed for 2. Now the bottom corner has one way in: a dead end, " +
                    "but the line must end on 4. Put your finger on the glowing square to cut back to it.",
                highlight = BoardHighlight(strong = setOf(8), soft = setOf(12)),
                accepts = line(answerTo(4)),
                retry = "Start a drag on the glowing square of the line.",
                done = "Everything after it is gone.",
            ),
            TutorialFrame(
                state = tutorialBoard(answerTo(4)),
                caption = "Take the corner now, while it still has two ways in. Leave it a pocket " +
                    "with one way in and the line can't finish. Drag into it.",
                highlight = BoardHighlight(strong = setOf(12), soft = setOf(8, 13)),
                accepts = line(answerTo(5)),
                retry = "Drag down into the corner from the end of the line.",
                done = "The corner's safe.",
            ),
            TutorialFrame(
                state = tutorialBoard(answerTo(6) + 14),
                caption = "This line reached 3 before 2. Drag from 3 back along the line, one " +
                    "square, to rub that step out.",
                highlight = BoardHighlight(strong = setOf(14), soft = setOf(13), warning = true),
                accepts = line(answerTo(6)),
                retry = "Drag from 3 back one square along the line.",
                done = "Rubbed out. Only 2 is left to go to.",
            ),
            TutorialFrame(
                state = tutorialBoard(answerTo(6)),
                caption = "Your turn: finish the line. Every square once, 2 then 3, ending on 4. " +
                    "Watch for squares down to two ways in.",
                freePlay = true,
                done = "Solved. That's Snap.",
            ),
        )
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()

        Canvas(modifier) {
            val side = minOf(size.width, size.height)
            val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            val step = side / PREVIEW_SIDE
            // The board's flat 2px gutter between squares; kept in device pixels so the squares
            // stay squares rather than growing a proportionally fat gap at this scale.
            val gutter = maxOf(step * 0.025f, 1f)

            fun centre(cell: Int) = Offset(
                origin.x + (cell % PREVIEW_SIDE + 0.5f) * step,
                origin.y + (cell / PREVIEW_SIDE + 0.5f) * step,
            )

            for (cell in 0 until PREVIEW_SIDE * PREVIEW_SIDE) {
                drawRoundRect(
                    color = scheme.surfaceVariant,
                    topLeft = Offset(
                        origin.x + (cell % PREVIEW_SIDE) * step + gutter,
                        origin.y + (cell / PREVIEW_SIDE) * step + gutter,
                    ),
                    size = Size(step - 2 * gutter, step - 2 * gutter),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(step * 0.32f),
                )
            }

            val line = androidx.compose.ui.graphics.Path()
            previewPath.forEachIndexed { i, cell ->
                val at = centre(cell)
                if (i == 0) line.moveTo(at.x, at.y) else line.lineTo(at.x, at.y)
            }
            drawPath(
                line,
                Color(accent),
                style = Stroke(width = step * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            previewWaypoints.forEachIndexed { cell, number ->
                if (number == 0) return@forEachIndexed
                val at = centre(cell)
                drawCircle(scheme.onBackground, radius = step * 0.30f, center = at)
                val layout = measurer.measure(
                    number.toString(),
                    TextStyle(
                        color = scheme.background,
                        fontSize = (step * 0.32f).toSp(),
                        fontWeight = FontWeight.Bold,
                    ),
                )
                drawText(
                    layout,
                    topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
                )
            }
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SnapState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()

        // The gesture detector below is keyed on the numbers, which never change while a board is
        // being played, so its coroutine is never restarted and whatever it captured at launch is
        // what it keeps. Capturing `s` there pinned it to the board as first composed — the one
        // with no line on it — so every drag after the first reset to an empty path and wiped the
        // line. Read the live board through this instead.
        val latest by rememberUpdatedState(s)
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        // Breathes, as Kings' and Pipes' do, so a glowing square is findable at a glance. Only runs
        // while something glows, and is read in the draw phase.
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

        // Sized from both axes (CLAUDE.md), so a tall board or a short screen never runs it under
        // the hint slot.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            val step = if (constraints.hasBoundedHeight) minOf(maxWidth / s.width, maxHeight / s.height) else maxWidth / s.width
            val stepPx = with(LocalDensity.current) { step.toPx() }

            fun cellAt(offset: Offset): Int {
                val c = (offset.x / stepPx).toInt().coerceIn(0, s.width - 1)
                val r = (offset.y / stepPx).toInt().coerceIn(0, s.height - 1)
                return r * s.width + c
            }

            /** Appends or rubs out one square, following the drag. */
            fun extend(current: SnapState, cell: Int): SnapState {
                val path = current.path
                if (path.isEmpty()) {
                    // A line may only begin at 1.
                    return if (current.waypoints[cell] == 1) current.copy(path = listOf(cell), moves = current.moves + 1)
                    else current
                }
                if (cell == path.last()) return current
                if (path.size >= 2 && cell == path[path.size - 2]) {
                    return current.copy(path = path.dropLast(1), moves = current.moves + 1)
                }
                if (cell in path) return current
                if (cell !in neighbours(path.last(), current.width, current.height)) return current
                return current.copy(path = path + cell, moves = current.moves + 1)
            }

            Canvas(
                Modifier
                    .width(step * s.width)
                    .height(step * s.height)
                    .pointerInput(s.waypoints, interactive) {
                        if (!interactive) return@pointerInput
                        var working = latest
                        detectDragGestures(
                            onDragStart = { offset ->
                                working = latest
                                val cell = cellAt(offset)
                                // Starting on the line trims it back to that point.
                                working = if (cell in working.path) {
                                    working.copy(path = working.path.take(working.path.indexOf(cell) + 1))
                                } else {
                                    extend(working, cell)
                                }
                                onState(working)
                            },
                            onDrag = { change, _ ->
                                val next = extend(working, cellAt(change.position))
                                if (next !== working) {
                                    working = next
                                    onState(working)
                                }
                            },
                        )
                    }
            ) {
                for (r in 0 until s.height) {
                    for (c in 0 until s.width) {
                        drawRoundRect(
                            color = scheme.surfaceVariant,
                            topLeft = Offset(c * stepPx + 2f, r * stepPx + 2f),
                            size = Size(stepPx - 4f, stepPx - 4f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stepPx * 0.16f),
                        )
                    }
                }

                if (s.path.size >= 2) {
                    val line = androidx.compose.ui.graphics.Path()
                    s.path.forEachIndexed { i, cell ->
                        val x = (cell % s.width + 0.5f) * stepPx
                        val y = (cell / s.width + 0.5f) * stepPx
                        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    drawPath(
                        line,
                        Color(accent),
                        style = Stroke(width = stepPx * 0.26f, cap = StrokeCap.Round),
                    )
                }

                for (cell in 0 until s.cellCount) {
                    val number = s.waypoints[cell]
                    if (number == 0) continue
                    val cx = (cell % s.width + 0.5f) * stepPx
                    val cy = (cell / s.width + 0.5f) * stepPx
                    drawCircle(scheme.onBackground, radius = stepPx * 0.30f, center = Offset(cx, cy))
                    val layout = measurer.measure(
                        number.toString(),
                        TextStyle(
                            color = scheme.background,
                            fontSize = (step.value * 0.30f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
                    )
                }

                if (!highlight.isEmpty) {
                    // Everything the highlight does not name steps back under a veil in the page
                    // colour, line and numbers included, so the named squares read at once.
                    for (cell in 0 until s.cellCount) {
                        if (cell in highlight.strong || cell in highlight.soft) continue
                        drawRect(
                            color = scheme.background.copy(alpha = 0.62f),
                            topLeft = Offset((cell % s.width) * stepPx, (cell / s.width) * stepPx),
                            size = Size(stepPx, stepPx),
                        )
                    }
                    // Outlines last, so a neighbour drawn later never paints over half of one.
                    val strongW = 4.dp.toPx()
                    val softW = 1.5.dp.toPx()
                    val corner = CornerRadius(stepPx * 0.16f)
                    for (cell in 0 until s.cellCount) {
                        val strong = cell in highlight.strong
                        if (!strong && cell !in highlight.soft) continue
                        val w = if (strong) strongW else softW
                        drawRoundRect(
                            color = if (strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f),
                            topLeft = Offset((cell % s.width) * stepPx + w / 2, (cell / s.width) * stepPx + w / 2),
                            size = Size(stepPx - w, stepPx - w),
                            cornerRadius = corner,
                            style = Stroke(w),
                        )
                    }
                }
            }
        }
    }
}
