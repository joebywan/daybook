package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import kotlinx.serialization.Serializable

/** Score for one submitted guess. */
data class Feedback(val exact: Int, val misplaced: Int)

@Serializable
data class TowerState(
    val slots: Int,
    val colours: Int,
    val maxGuesses: Int,
    val secret: List<Int>,
    val guesses: List<List<Int>>,
    val current: List<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = guesses.lastOrNull() == secret
    override val failed: Boolean get() = !solved && guesses.size >= maxGuesses

    val ready: Boolean get() = current.none { it < 0 }

    fun score(guess: List<Int>): Feedback = TowerTeacher.score(guess, secret, colours)

    fun withPeg(slot: Int, colour: Int): TowerState =
        copy(current = current.toMutableList().also { it[slot] = colour }, moves = moves + 1)

    fun submit(): TowerState =
        if (!ready) this
        else copy(
            guesses = guesses + listOf(current),
            current = List(slots) { -1 },
            moves = moves + 1,
        )
}

/**
 * Tower — Mastermind.
 *
 * Break a hidden colour code from scored guesses: a filled pip per peg that is the right colour in
 * the right place, a hollow pip per peg that is the right colour in the wrong place.
 */
object Tower : PuzzleType {

    override val id = "tower"
    override val displayName = "Tower"
    override val tagline = "Break the hidden colour code"
    override val accent = 0xFFD08A33
    override val rules = listOf(
        "A secret row of coloured pegs is hidden at the top.",
        "Tap a colour, then tap a slot to place it. Submit a full row to score it.",
        "A filled pip means one peg is the right colour in the right slot.",
        "A hollow pip means one peg is the right colour in the wrong slot.",
        "Pips are not aligned with the pegs — working out which is which is the puzzle.",
        "Colours may repeat in the secret.",
    )

    val palette = listOf(
        0xFFD9584C, 0xFF4C86D9, 0xFF54B07A, 0xFFE0B23C,
        0xFF9B6FD0, 0xFF48B9C4, 0xFFD97FB0, 0xFF9A8264,
    )

    /**
     * The home tile: two scored guess rows, newest on top, as the board stacks them.
     *
     * Four pegs and their pips is the whole of Tower's shape, and two rows is as many as fit at
     * 80dp while a peg still reads as a peg. The pips go in a 2x2 block rather than the board's
     * single row: four pips strung out beside four pegs leaves each one about 3dp across, where
     * filled and hollow become the same grey dot, and the filled/hollow distinction is the only
     * information a scored row carries.
     */
    private val previewGuesses = listOf(
        listOf(2, 0, 3, 1) to Feedback(exact = 2, misplaced = 1),
        listOf(0, 1, 2, 3) to Feedback(exact = 1, misplaced = 1),
    )

    /**
     * Board shape per tier as `(slots, colours, maxGuesses)`.
     *
     * The budget used to *fall* as the board grew — 10/10/9 over code spaces of 1,296 / 16,807 /
     * 32,768 — so Expert handed you a 25x larger search than Standard and one fewer try to crack
     * it. These numbers are picked so the budget rises with the difficulty, and so each tier is
     * winnable by a person rather than only by a solver.
     *
     * Why these, tier by tier:
     *
     * | tier     | shape | code space | outcomes | floor | budget |
     * |----------|-------|-----------:|---------:|------:|-------:|
     * | STANDARD | 4x5   |        625 |       14 |     3 |     10 |
     * | HARD     | 5x6   |      7,776 |       20 |     3 |     12 |
     * | EXPERT   | 5x8   |     32,768 |       20 |     4 |     14 |
     *
     * "Outcomes" is the number of distinct (exact, misplaced) replies a guess can draw — every
     * pair summing to at most `slots`, less the impossible one where `slots - 1` pegs are exact
     * and the last is misplaced. "Floor" is the information-theoretic bound, `log(space) /
     * log(outcomes)`: no strategy of any kind can average fewer guesses than that. It is a floor,
     * not a target. Nobody reaches it, so it only tells us where the budget must not go.
     *
     * The useful bound is empirical, and it comes in two flavours (both measured, see
     * `TowerBalanceTest`):
     *
     * - A perfect *consistent* solver — one that tracks every prior reply and guesses only codes
     *   still possible — needs up to 7 guesses on 4x5, 8 on 5x6 and 9 on 5x8. Knuth's minimax
     *   result of five guesses for 4x6 is not the relevant number here: it assumes an exhaustive
     *   search over all codes at every turn, which is not what a person on a phone is doing.
     * - A player has bounded working memory. Modelled as a solver that only filters against its
     *   most recent few replies, 95% of games finish within 7 (4x5), 11 (5x6) and 19 (5x8) when
     *   three replies are held in mind, or 5 / 7 / 11 when four are.
     *
     * So each budget clears the perfect solver's worst case by three to five spare guesses, and
     * covers the bounded-memory player around the 95th percentile. Expert deliberately stops short
     * of covering a sloppy player's tail — it is meant to be beatable, not free.
     *
     * Standard dropped from six colours to five. Six was the entry point purely because that is
     * what the boxed game uses; at 1,296 codes it was landing the same ~4.6-guess average as Hard,
     * which is not what a warm-up tier should feel like. Five colours halves the space and the
     * board still plays as Mastermind rather than a toy.
     *
     * Colour counts are capped at [palette]'s eight entries, which is also the practical ceiling
     * for the swatch `Row` on the board: eight swatches share the row's width evenly, leaving
     * roughly 35dp each on a 360dp-wide phone. Any more and they stop being comfortably tappable,
     * so a ninth colour needs a layout change, not just a longer palette.
     */
    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> Triple(4, 5, 10)
        Difficulty.HARD -> Triple(5, 6, 12)
        Difficulty.EXPERT -> Triple(5, 8, 14)
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val rng = Rng(seed)
        val (slots, colours, tries) = shape(difficulty)
        val secret = List(slots) { rng.nextInt(colours) }
        return TowerState(slots, colours, tries, secret, emptyList(), List(slots) { -1 })
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        ) {
            previewGuesses.forEach { (guess, feedback) ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(percent = 50))
                        // A tint of the foreground rather than `surface`, because the tile's own
                        // background is the grid's to choose: a surface plate on a surface tile
                        // would be invisible, and then the pegs float with no row to sit in.
                        .background(scheme.onSurface.copy(alpha = 0.08f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    guess.forEach { colour ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(Color(palette[colour]))
                        )
                    }
                    PreviewPips(Modifier.weight(1f), feedback)
                }
            }
        }
    }

    @Composable
    private fun PreviewPips(modifier: Modifier, feedback: Feedback) {
        val scheme = MaterialTheme.colorScheme
        Column(modifier.aspectRatio(1f).padding(1.5.dp)) {
            repeat(2) { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(2) { column ->
                        val pip = row * 2 + column
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(0.5.dp)
                                .clip(CircleShape)
                                .then(
                                    when {
                                        pip < feedback.exact ->
                                            Modifier.background(scheme.onSurface)
                                        pip < feedback.exact + feedback.misplaced ->
                                            Modifier.border(1.2.dp, scheme.onSurface, CircleShape)
                                        else ->
                                            Modifier.background(scheme.outline.copy(alpha = 0.55f))
                                    }
                                )
                        )
                    }
                }
            }
        }
    }

    // ---- the walkthrough ---------------------------------------------------------------------

    /**
     * The walkthrough's code: three slots, four colours (red, blue, green, yellow), and the code
     * green red blue. Small so every peg is easy to aim at, and so the whole argument fits on one
     * screen:
     *
     * ```
     * guess 1  yellow yellow yellow   nothing        -> yellow is nowhere
     * guess 2  red    red    blue     two filled
     * guess 3  blue   red    blue     two filled     -> made in the walkthrough; only slot 1
     *                                                   changed and the score didn't, so slot 1
     *                                                   is neither red nor blue: green
     * ```
     *
     * After guess 3, guess 2's two filled pips can't include slot 1, so they are its red and blue:
     * the code is green red blue, and TowerTeachingTest proves it is the only one that fits.
     */
    internal val TUTORIAL_CODE = listOf(2, 0, 1)
    internal val TUTORIAL_GUESSES = listOf(listOf(3, 3, 3), listOf(0, 0, 1), listOf(1, 0, 1))
    private const val TUTORIAL_SLOTS = 3
    private const val TUTORIAL_COLOURS = 4
    private const val TUTORIAL_BUDGET = 6

    private fun tutorialBoard(guesses: Int, current: List<Int> = List(TUTORIAL_SLOTS) { -1 }) = TowerState(
        TUTORIAL_SLOTS, TUTORIAL_COLOURS, TUTORIAL_BUDGET, TUTORIAL_CODE,
        TUTORIAL_GUESSES.take(guesses), current,
    )

    /**
     * Accepts exactly [guesses] submitted and [current] in the row, nothing else — each frame's
     * board is written for the one before it.
     */
    private fun only(guesses: Int, current: List<Int>): (PuzzleState) -> Boolean = { next ->
        next is TowerState && next.guesses == TUTORIAL_GUESSES.take(guesses) && next.current == current
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val c = TowerTeacher.CURRENT
        val sw = TowerTeacher.SWATCH
        val p = TowerTeacher.PIPS
        fun row(g: Int) = (0 until TUTORIAL_SLOTS).map { TowerTeacher.peg(TUTORIAL_SLOTS, g, it) }.toSet()
        val copied = listOf(0, 0, 1)
        val changed = listOf(1, 0, 1)
        val green = listOf(2, -1, -1)
        listOf(
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "A code of three pegs is hidden. Each guess scores a filled pip per peg in the " +
                    "right colour and slot, and a hollow pip per right colour in the wrong slot.",
                highlight = BoardHighlight(strong = setOf(p + 0, p + 1)),
            ),
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "Guess 1 was all yellow and scored nothing, so yellow isn't in the code. " +
                    "Guess 2 has two filled pips, but pips never say which pegs earned them.",
                highlight = BoardHighlight(strong = row(0) + (p + 0), soft = setOf(p + 1)),
            ),
            TutorialFrame(
                state = tutorialBoard(2, copied),
                caption = "Your row copies guess 2. Change only slot 1, and the new score tells you about " +
                    "that slot alone. Tap blue, then the glowing slot.",
                highlight = BoardHighlight(strong = setOf(sw + 1, c + 0), soft = row(1)),
                accepts = only(2, changed),
                retry = "Tap blue below first, then the glowing slot.",
                done = "Now your row differs from guess 2 in slot 1 only.",
            ),
            TutorialFrame(
                state = tutorialBoard(2, changed),
                caption = "A full row can be scored. Tap Submit.",
                highlight = BoardHighlight(strong = setOf(TowerTeacher.SUBMIT_BUTTON)),
                accepts = only(3, List(TUTORIAL_SLOTS) { -1 }),
                retry = "Tap Submit, at the end of your row.",
                done = "Scored: two filled pips again.",
            ),
            TutorialFrame(
                state = tutorialBoard(3),
                caption = "Guesses 2 and 3 differ only in slot 1 and scored the same, so slot 1 is neither " +
                    "red nor blue. Yellow is out, so slot 1 is green.",
                highlight = BoardHighlight(
                    strong = setOf(TowerTeacher.peg(TUTORIAL_SLOTS, 1, 0), TowerTeacher.peg(TUTORIAL_SLOTS, 2, 0)),
                    soft = setOf(p + 1, p + 2),
                ),
            ),
            TutorialFrame(
                state = tutorialBoard(3),
                caption = "Build your next guess on what you know. Tap green, then slot 1.",
                highlight = BoardHighlight(strong = setOf(sw + 2, c + 0)),
                accepts = only(3, green),
                retry = "Tap green below first, then the glowing slot.",
                done = "Slot 1 is green.",
            ),
            TutorialFrame(
                state = tutorialBoard(3, green),
                caption = "Your turn: finish the code and submit it. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Cracked. That's all there is to it.",
            ),
        )
    }

    // ---- teaching --------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out — see [TowerTeacher]. Replaces the old
     * `hint()`, which dropped a peg straight out of [TowerState.secret] and taught nothing. The
     * teacher is handed the guesses and their scores, never the code.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as TowerState
        if (s.solved || s.failed) return null
        val feedback = s.guesses.map(s::score)
        val step = TowerTeacher.deduce(s.slots, s.colours, s.guesses, feedback, s.current) ?: return null
        val base = s
        fun fits(code: List<Int>) = TowerTeacher.consistent(code, base.guesses, feedback, base.colours)
        fun newGuesses(now: TowerState) = now.guesses.drop(base.guesses.size)
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == TowerTeacher.MISTAKE,
            fallback = step.technique == TowerTeacher.CONSISTENT || step.technique == TowerTeacher.ONLY_CODE,
            applyTo = { now -> applyMove(now as TowerState, step.move) },
            reachedBy = { now ->
                val t = now as TowerState
                when (val m = step.move) {
                    is TowerTeacher.Move.Place ->
                        t.current[m.slot] == m.colour || newGuesses(t).any { it[m.slot] == m.colour }
                    // Any row that fits the scores does: the suggestion was one of many.
                    is TowerTeacher.Move.Fill ->
                        (t.ready && fits(t.current)) || newGuesses(t).any(::fits)
                    is TowerTeacher.Move.Clear ->
                        m.slots.any { t.current[it] != base.current[it] } || newGuesses(t).isNotEmpty()
                    TowerTeacher.Move.Submit -> newGuesses(t).isNotEmpty()
                }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyMove(s: TowerState, move: TowerTeacher.Move): TowerState = when (move) {
        is TowerTeacher.Move.Place ->
            if (s.current[move.slot] == move.colour) s else s.withPeg(move.slot, move.colour)
        is TowerTeacher.Move.Fill ->
            if (s.current == move.code) s else s.copy(current = move.code, moves = s.moves + 1)
        is TowerTeacher.Move.Clear -> {
            val next = s.current.toMutableList().also { row -> move.slots.forEach { row[it] = -1 } }
            if (next == s.current) s else s.copy(current = next, moves = s.moves + 1)
        }
        TowerTeacher.Move.Submit -> s.submit()
    }

    // ---- drawing -------------------------------------------------------------------------

    /**
     * What [LocalBoardHighlight] asks of one element. [dim] steps everything unnamed back so the
     * named pegs read without hunting; controls (swatches, Submit) glow when named but never dim,
     * because the move a hint asks for is made with them.
     */
    private class Look(val strong: Boolean, val soft: Boolean, val dim: Boolean)

    private fun BoardHighlight.look(index: Int, dims: Boolean = true): Look {
        val strong = index in this.strong
        val soft = !strong && index in this.soft
        return Look(strong, soft, dims && !isEmpty && !strong && !soft)
    }

    /**
     * A ring drawn just outside the element, so the peg's own colour is untouched: breathing in
     * [glow] for strong, a quiet fixed line for soft. Read in the draw phase so the pulse repaints
     * without recomposing. Apply before the element's own `clip`, or the ring is clipped away.
     */
    private fun Modifier.ring(look: Look, glow: Color, pulse: State<Float>, round: Boolean): Modifier =
        if (!look.strong && !look.soft) this
        else drawWithContent {
            drawContent()
            val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
            val gap = 1.dp.toPx()
            val colour = if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f)
            val out = gap + w / 2
            if (round) {
                drawCircle(colour, radius = size.minDimension / 2 + out, style = Stroke(w))
            } else {
                drawRoundRect(
                    colour,
                    topLeft = Offset(-out, -out),
                    size = Size(size.width + 2 * out, size.height + 2 * out),
                    cornerRadius = CornerRadius(8.dp.toPx() + out),
                    style = Stroke(w),
                )
            }
        }

    private fun Modifier.dimmed(look: Look): Modifier = if (look.dim) alpha(0.3f) else this

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as TowerState
        val scheme = MaterialTheme.colorScheme
        var selectedColour by remember(s.secret) { mutableIntStateOf(0) }
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        // Breathes only while something glows, as on Kings.
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

        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {

            Text(
                "${s.maxGuesses - s.guesses.size} guesses left",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = true,
            ) {
                items(s.guesses.indices.reversed().toList()) { g ->
                    GuessRow(g, s.guesses[g], s.score(s.guesses[g]), s.slots, highlight, glow, pulse)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!s.solved && !s.failed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .keepClear()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    s.current.forEachIndexed { slot, colour ->
                        val look = highlight.look(TowerTeacher.CURRENT + slot)
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                .size(34.dp)
                                .highlightAnchor(TowerTeacher.CURRENT + slot)
                                .ring(look, glow, pulse, round = true)
                                .dimmed(look)
                                .clip(CircleShape)
                                .background(
                                    if (colour >= 0) Color(palette[colour]) else scheme.surfaceVariant
                                )
                                .border(1.dp, scheme.outline, CircleShape)
                                .clickable(enabled = interactive) {
                                    onState(s.withPeg(slot, selectedColour))
                                }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Submit",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (s.ready) scheme.primary else scheme.outline,
                        modifier = Modifier
                            .highlightAnchor(TowerTeacher.SUBMIT_BUTTON)
                            .ring(highlight.look(TowerTeacher.SUBMIT_BUTTON, dims = false), glow, pulse, round = false)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = interactive && s.ready) { onState(s.submit()) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth().keepClear(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(s.colours) { colour ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .highlightAnchor(TowerTeacher.SWATCH + colour)
                                .ring(highlight.look(TowerTeacher.SWATCH + colour, dims = false), glow, pulse, round = false)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(palette[colour]))
                                .border(
                                    if (selectedColour == colour) 3.dp else 0.dp,
                                    scheme.onBackground,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = interactive) { selectedColour = colour }
                        )
                    }
                }
            }

            if (s.failed) {
                Text(
                    "Out of guesses. The code was:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                Row {
                    s.secret.forEach { colour ->
                        Box(
                            Modifier.padding(3.dp).size(30.dp).clip(CircleShape)
                                .background(Color(palette[colour]))
                        )
                    }
                }
            }
        }
    }

    /**
     * One scored guess. Numbered, because hints speak of "guess 2" and the number has to be
     * findable on the board, not counted up from the top.
     */
    @Composable
    private fun GuessRow(
        index: Int,
        guess: List<Int>,
        feedback: Feedback,
        slots: Int,
        highlight: BoardHighlight,
        glow: Color,
        pulse: State<Float>,
    ) {
        val scheme = MaterialTheme.colorScheme
        val pegs = guess.indices.map { highlight.look(TowerTeacher.peg(slots, index, it)) }
        val pips = highlight.look(TowerTeacher.PIPS + index)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(18.dp)
                    .then(if (pegs.all { it.dim } && pips.dim) Modifier.alpha(0.3f) else Modifier),
            )
            guess.forEachIndexed { slot, colour ->
                Box(
                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp).size(26.dp)
                        .highlightAnchor(TowerTeacher.peg(slots, index, slot))
                        .ring(pegs[slot], glow, pulse, round = true)
                        .dimmed(pegs[slot])
                        .clip(CircleShape)
                        .background(Color(palette[colour]))
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .highlightAnchor(TowerTeacher.PIPS + index)
                    .ring(pips, glow, pulse, round = false)
                    .dimmed(pips)
                    .width(66.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                repeat(feedback.exact) {
                    Box(
                        Modifier.padding(1.dp).size(9.dp).clip(CircleShape)
                            .background(scheme.onSurface)
                    )
                }
                repeat(feedback.misplaced) {
                    Box(
                        Modifier.padding(1.dp).size(9.dp).clip(CircleShape)
                            .border(1.5.dp, scheme.onSurface, CircleShape)
                    )
                }
                repeat(slots - feedback.exact - feedback.misplaced) {
                    Box(
                        Modifier.padding(1.dp).size(9.dp).clip(CircleShape)
                            .background(scheme.surfaceVariant)
                    )
                }
            }
        }
    }
}
