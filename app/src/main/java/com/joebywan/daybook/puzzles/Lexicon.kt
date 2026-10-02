package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import kotlinx.coroutines.delay

/**
 * Lexicon: Mastermind for words.
 *
 * Break a hidden word from scored guesses. A guess must be a real word; each letter comes back
 * green (right letter, right place), yellow (in the word, elsewhere) or grey (not in it, or no more
 * of it). The keys of the on-screen keyboard keep the best thing known about each letter.
 *
 * Like Tower there is nothing to prove about a board: the word is one pick from a fixed list, and a
 * guess that cannot be it is still a fair probe. The rules are in [LexiconRules], the lists in
 * [WordList], the hints in [LexiconTeacher]; this file is the board.
 */
object Lexicon : PuzzleType {

    override val id = "words"
    override val displayName = "Lexicon"
    override val tagline = "Find the hidden word"
    override val accent = 0xFF9CB83E
    override val rules = listOf(
        "A word is hidden. Type a real word and press Enter to guess it.",
        "Green: the right letter in the right place.",
        "Yellow: the letter is in the word, but somewhere else.",
        "Grey: the word has no (more) of that letter.",
        "A letter is marked only as many times as the word holds it, so a second E can be grey while the first is yellow.",
        "The keys keep the best clue for each letter.",
        "Standard: five letters, six guesses.",
        "Hard: the same, but every clue you have must be used in your next guess.",
        "Expert: four letters, eight guesses, and the same rule. Short words have more look-alikes.",
    )

    override fun generate(seed: Long, difficulty: Difficulty) = LexiconRules.newBoard(seed, difficulty)

    // ---- colours ---------------------------------------------------------------------------------

    private val correctColour = Color(0xFF4E9F6C)
    private val presentColour = Color(0xFFD1A32F)
    private val onMark = Color.White

    private fun markColour(mark: Int, absent: Color): Color = when (mark) {
        LexiconMark.CORRECT -> correctColour
        LexiconMark.PRESENT -> presentColour
        else -> absent
    }

    // ---- the home tile ----------------------------------------------------------------------------

    /** Three scored rows, letters left out: the colours are the whole of what reads at 80dp. */
    private val previewRows = listOf(
        listOf(LexiconMark.ABSENT, LexiconMark.PRESENT, LexiconMark.ABSENT, LexiconMark.ABSENT, LexiconMark.PRESENT),
        listOf(LexiconMark.CORRECT, LexiconMark.ABSENT, LexiconMark.PRESENT, LexiconMark.CORRECT, LexiconMark.ABSENT),
        listOf(LexiconMark.CORRECT, LexiconMark.CORRECT, LexiconMark.CORRECT, LexiconMark.CORRECT, LexiconMark.CORRECT),
    )

    @Composable
    override fun Preview(modifier: Modifier) {
        val absent = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        // Sized from both axes, like every board: the tile is square-ish and its box is not.
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val gap = 3.dp
            val cell = minOf((maxWidth - gap * 4) / 5, (maxHeight - gap * 2) / 3).coerceAtLeast(0.dp)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                previewRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { mark ->
                            Box(
                                Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(markColour(mark, absent))
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- the walkthrough ---------------------------------------------------------------------------

    /**
     * The walkthrough's word: COAT, four letters. One guess is already on the board.
     *
     * ```
     * guess 1  tape   yellow yellow grey grey   -> t and a are in it, not there; p and e are out
     * guess 2  boat   grey green green green    -> made in the walkthrough, a letter at a time
     * ```
     *
     * After guess 2 the word is ?OAT: COAT, GOAT or MOAT fit, and no guess can tell all three apart,
     * which is the lesson of four-letter words. The free-play frame hands that board to the hints.
     */
    internal const val TUTORIAL_ANSWER = "coat"
    internal val TUTORIAL_GUESSES = listOf("tape", "boat")

    private fun tutorialBoard(guesses: Int, current: String = "") = LexiconState(
        length = 4,
        maxGuesses = 6,
        hard = false,
        answer = TUTORIAL_ANSWER,
        guesses = TUTORIAL_GUESSES.take(guesses),
        current = current,
    )

    /** Accepts exactly [guesses] submitted and [current] typed, nothing else: each frame's board is written for the one before. */
    private fun only(guesses: Int, current: String): (PuzzleState) -> Boolean = { next ->
        next is LexiconState && next.guesses == TUTORIAL_GUESSES.take(guesses) && next.current == current
    }

    override val tutorial: List<TutorialFrame> by lazy {
        fun tiles(row: Int, vararg slots: Int) = slots.map { LexiconTeacher.tile(4, row, it) }.toSet()
        fun typeFrame(typed: String, caption: String) = TutorialFrame(
            state = tutorialBoard(1, typed.dropLast(1)),
            caption = caption,
            highlight = BoardHighlight(strong = setOf(LexiconTeacher.key(typed.last()))),
            accepts = only(1, typed),
            retry = "Tap the glowing key.",
            done = "${typed.last().uppercaseChar()} goes in slot ${typed.length}.",
        )
        listOf(
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "A four-letter word is hidden, and you have six guesses. Every guess must be a real " +
                    "word, and each letter comes back marked. Green means right letter, right place.",
                highlight = BoardHighlight(soft = tiles(0, 0, 1, 2, 3)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Yellow means the letter is in the word, but not there: T and A are in it. " +
                    "Grey means the word has none of it: P and E are out.",
                highlight = BoardHighlight(strong = tiles(0, 0, 1), soft = tiles(0, 2, 3)),
            ),
            typeFrame(
                "b",
                "Use what you know: T and A are in the word, so move them. BOAT puts them in new places. " +
                    "Tap B on the keyboard.",
            ),
            typeFrame("bo", "Now O."),
            typeFrame("boa", "Then A."),
            typeFrame("boat", "And T."),
            TutorialFrame(
                state = tutorialBoard(1, "boat"),
                caption = "A full row can be scored. Tap Enter.",
                highlight = BoardHighlight(strong = setOf(LexiconTeacher.ENTER)),
                accepts = only(2, ""),
                retry = "Tap Enter, at the end of the keyboard.",
                done = "Scored.",
            ),
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "O, A and T are green and B is grey, so the word ends in OAT. COAT, GOAT and MOAT " +
                    "all fit, and no guess can tell all three apart. The keys keep score too.",
                highlight = BoardHighlight(strong = tiles(1, 1, 2, 3), soft = tiles(1, 0)),
            ),
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "Your turn: find the word. Stuck? Hint shows you how to narrow it down.",
                freePlay = true,
                done = "Found it. That's all there is to it.",
            ),
        )
    }

    // ---- teaching ---------------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out; see [LexiconTeacher]. The teacher is handed
     * the guesses and their marks, never the word.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as LexiconState
        if (!s.open) return null
        val marks = s.allMarks
        val step = LexiconTeacher.teach(s.length, s.hard, s.guesses, marks, s.current) ?: return null
        val base = s
        fun fits(word: String) = LexiconRules.consistent(word, base.guesses, marks)
        fun newGuesses(now: LexiconState) = now.guesses.drop(base.guesses.size)
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == LexiconTeacher.MISTAKE,
            fallback = step.technique in LexiconTeacher.FALLBACKS,
            applyTo = { now -> applyMove(now as LexiconState, step.move) },
            reachedBy = { now ->
                val t = now as LexiconState
                when (val m = step.move) {
                    is LexiconTeacher.Move.Retype ->
                        t.current.length <= m.keep || newGuesses(t).isNotEmpty()
                    is LexiconTeacher.Move.Open ->
                        t.current.length == t.length || newGuesses(t).isNotEmpty()
                    is LexiconTeacher.Move.Pin ->
                        (t.current.length > m.slot && t.current[m.slot] == m.letter) ||
                            newGuesses(t).any { it[m.slot] == m.letter }
                    // Any whole row that fits the marks does: the suggestion was one of several.
                    is LexiconTeacher.Move.Fill ->
                        (t.current.length == t.length && fits(t.current)) || newGuesses(t).any(::fits)
                    LexiconTeacher.Move.Submit -> newGuesses(t).isNotEmpty()
                }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyMove(s: LexiconState, move: LexiconTeacher.Move): LexiconState {
        fun typed(word: String) = if (s.current == word) s else s.copy(current = word, moves = s.moves + 1)
        return when (move) {
            is LexiconTeacher.Move.Retype ->
                if (s.current.length <= move.keep) s else s.copy(current = s.current.take(move.keep), moves = s.moves + 1)
            is LexiconTeacher.Move.Open -> typed(move.word)
            is LexiconTeacher.Move.Pin -> typed(move.word)
            is LexiconTeacher.Move.Fill -> typed(move.word)
            LexiconTeacher.Move.Submit -> s.submit()
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------

    /**
     * What [LocalBoardHighlight] asks of one element. [dim] steps everything unnamed back so the
     * named tiles read without hunting; keys glow when named but never dim, because the move a hint
     * asks for is made with them.
     */
    private class Look(val strong: Boolean, val soft: Boolean, val dim: Boolean)

    private fun BoardHighlight.look(index: Int, dims: Boolean): Look {
        val strong = index in this.strong
        val soft = !strong && index in this.soft
        return Look(strong, soft, dims && !strong && !soft)
    }

    /**
     * A ring drawn just outside the element, so its own colour is untouched: breathing in [glow]
     * for strong, a quiet fixed line for soft. Read in the draw phase so the pulse repaints without
     * recomposing. Apply before the element's own `clip`, or the ring is clipped away.
     */
    private fun Modifier.ring(look: Look, glow: Color, pulse: State<Float>): Modifier =
        if (!look.strong && !look.soft) this
        else drawWithContent {
            drawContent()
            val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
            val gap = 1.dp.toPx()
            val colour = if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f)
            val out = gap + w / 2
            drawRoundRect(
                colour,
                topLeft = Offset(-out, -out),
                size = Size(size.width + 2 * out, size.height + 2 * out),
                cornerRadius = CornerRadius(6.dp.toPx() + out),
                style = Stroke(w),
            )
        }

    private fun Modifier.dimmed(look: Look): Modifier = if (look.dim) alpha(0.3f) else this

    /** The best clue the board holds for each letter, a..z: a [LexiconMark], or -1 for a letter not yet tried. */
    private fun keyMarks(s: LexiconState): IntArray {
        val best = IntArray(26) { -1 }
        s.guesses.forEach { guess ->
            val marks = s.marks(guess)
            guess.forEachIndexed { i, c -> if (marks[i] > best[c - 'a']) best[c - 'a'] = marks[i] }
        }
        return best
    }

    private val keyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as LexiconState
        val scheme = MaterialTheme.colorScheme
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val tilesNamed = highlight.strong.any { it < LexiconTeacher.KEY } || highlight.soft.any { it < LexiconTeacher.KEY }
        val absent = scheme.outline.copy(alpha = 0.45f)
        val playable = interactive && s.open

        // Why Enter was refused. Transient UI state, so it lives here and not in the state:
        // PlayScreen would otherwise make every refusal an undo step. It is shown in the header line,
        // whose room is always reserved, so a refusal never moves the board.
        var notice by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(1600)
                notice = null
            }
        }
        LaunchedEffect(s.current, s.guesses.size) { notice = null }

        val focus = remember { FocusRequester() }
        // A held key repeats KeyDown: a held Backspace would eat the whole row, a held letter fill it.
        var heldKey by remember { mutableStateOf<Key?>(null) }
        // Claimed on arrival, and again whenever a hint lights something up: the Hint button took the
        // focus when it was clicked, and the move it asks for should be makeable from the keyboard.
        LaunchedEffect(interactive, highlight.strong) {
            if (interactive) focus.requestFocus()
        }

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

        fun press(action: LexiconKeyAction) {
            if (!playable) return
            when (action) {
                is LexiconKeyAction.Letter -> s.withLetter(action.letter).takeIf { it != s }?.let(onState)
                LexiconKeyAction.Backspace -> s.withoutLetter().takeIf { it != s }?.let(onState)
                LexiconKeyAction.Enter -> {
                    val why = LexiconRules.problem(s)
                    if (why != null) notice = why else onState(s.submit())
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .focusRequester(focus)
                .onKeyEvent { event ->
                    if (!playable) return@onKeyEvent false
                    // Chords belong to the browser (Ctrl+R, Cmd+L...) and to the system.
                    if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed || event.isShiftPressed) {
                        return@onKeyEvent false
                    }
                    val action = lexiconKeyAction(event.key) ?: return@onKeyEvent false
                    if (event.type == KeyEventType.KeyUp) {
                        if (heldKey == event.key) heldKey = null
                        return@onKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val repeat = heldKey == event.key
                    heldKey = event.key
                    if (!repeat) press(action)
                    true
                }
                .focusable(interactive),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // One line, always laid out: the guesses left, or why Enter refused, or the word.
            val line = when {
                notice != null -> notice!!
                s.failed -> "Out of guesses. The word was ${s.answer.uppercase()}."
                else -> "${s.maxGuesses - s.guesses.size} guesses left"
            }
            Text(
                line,
                style = MaterialTheme.typography.labelLarge,
                color = if (notice != null || s.failed) scheme.error else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                minLines = 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Sized from both axes (CLAUDE.md): the grid is the width, or the height left once the
            // keyboard has its room, whichever binds. It never changes size, so a hint opening or a
            // solve cannot move it.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val cell = minOf(maxWidth / s.length, maxHeight / s.maxGuesses, MAX_CELL).coerceAtLeast(0.dp)
                Column(Modifier.size(cell * s.length, cell * s.maxGuesses).highlightGrid(s.length, s.maxGuesses)) {
                    repeat(s.maxGuesses) { row ->
                        Row {
                            repeat(s.length) { slot ->
                                val guess = s.guesses.getOrNull(row)
                                val typing = row == s.guesses.size && s.open
                                val letter = guess?.get(slot) ?: if (typing) s.current.getOrNull(slot) else null
                                val mark = guess?.let { s.marks(it)[slot] }
                                val look = highlight.look(LexiconTeacher.tile(s.length, row, slot), dims = tilesNamed)
                                Tile(letter, mark, typing && letter != null, cell, look, glow, pulse, absent)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val known = keyMarks(s)
            Column(
                Modifier.fillMaxWidth().keepClear().padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(KEY_GAP),
            ) {
                keyRows.forEachIndexed { r, letters ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        // The nine-key row is centred under the ten above it.
                        if (r == 1) Spacer(Modifier.weight(0.5f))
                        if (r == 2) {
                            KeyButton("Enter", Modifier.weight(1.5f), playable, LexiconTeacher.ENTER, highlight, glow, pulse,
                                scheme.surfaceVariant, scheme.onSurfaceVariant, small = true) {
                                press(LexiconKeyAction.Enter)
                            }
                        }
                        letters.forEach { c ->
                            val mark = known[c - 'a']
                            val fill = when (mark) {
                                -1 -> scheme.surfaceVariant
                                LexiconMark.ABSENT -> scheme.outline.copy(alpha = 0.3f)
                                else -> markColour(mark, absent)
                            }
                            val text = if (mark > LexiconMark.ABSENT) onMark else scheme.onSurfaceVariant
                            KeyButton(c.uppercase(), Modifier.weight(1f), playable, LexiconTeacher.key(c), highlight, glow, pulse,
                                fill, text, small = false) {
                                press(LexiconKeyAction.Letter(c))
                            }
                        }
                        if (r == 1) Spacer(Modifier.weight(0.5f))
                        if (r == 2) {
                            KeyButton("Del", Modifier.weight(1.5f), playable, LexiconTeacher.DELETE, highlight, glow, pulse,
                                scheme.surfaceVariant, scheme.onSurfaceVariant, small = true) {
                                press(LexiconKeyAction.Backspace)
                            }
                        }
                    }
                }
            }
        }
    }

    private val MAX_CELL = 64.dp
    private val KEY_GAP = 5.dp
    private val KEY_HEIGHT = 42.dp

    /** One square of the grid: a typed letter, a marked one, or an empty outline. */
    @Composable
    private fun Tile(
        letter: Char?,
        mark: Int?,
        typed: Boolean,
        cell: Dp,
        look: Look,
        glow: Color,
        pulse: State<Float>,
        absent: Color,
    ) {
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(cell)
                .padding(2.dp)
                .ring(look, glow, pulse)
                .dimmed(look)
                .clip(shape)
                .then(
                    if (mark != null) Modifier.background(markColour(mark, absent))
                    else Modifier.border(if (typed) 2.dp else 1.dp, if (typed) scheme.onSurfaceVariant else scheme.outline, shape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (letter != null) {
                Text(
                    letter.uppercase(),
                    fontSize = (cell.value * 0.46f).sp,
                    fontWeight = FontWeight.Bold,
                    color = if (mark != null && mark > LexiconMark.ABSENT) onMark else scheme.onSurface,
                )
            }
        }
    }

    /** One key of the on-screen keyboard. [index] is what a hint names it by. */
    @Composable
    private fun KeyButton(
        label: String,
        modifier: Modifier,
        enabled: Boolean,
        index: Int,
        highlight: BoardHighlight,
        glow: Color,
        pulse: State<Float>,
        fill: Color,
        text: Color,
        small: Boolean,
        onClick: () -> Unit,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier
                .height(KEY_HEIGHT)
                .highlightAnchor(index)
                .ring(highlight.look(index, dims = false), glow, pulse)
                .clip(shape)
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (small) 12.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = text,
                maxLines = 1,
            )
        }
    }
}
