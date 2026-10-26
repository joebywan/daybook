package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.HintLook
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.ring
import kotlinx.coroutines.delay
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.boardSp

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
    override val accent = 0xFF278E8E
    override val rules = listOf(
        "A word is hidden. Type a real word and press Enter to guess it.",
        "Green: the right letter in the right place.",
        "Yellow: the letter is in the word, but somewhere else.",
        "Grey: the word has no (more) of that letter.",
        "A letter is marked only as many times as the word holds it, so a second E can be grey while the first is yellow.",
        "The keys keep the best clue for each letter.",
        "Standard: five letters, six guesses.",
        "Hard: five letters, five guesses.",
        "Expert: four letters, seven guesses. Short words have more look-alikes.",
    )

    override val keyboardHelp = listOf(
        "Type letters, Backspace deletes, Enter submits.",
    )

    override fun generate(seed: Long, difficulty: Difficulty) = LexiconRules.newBoard(seed, difficulty)

    // ---- colours ---------------------------------------------------------------------------------

    internal val correctColour = Color(0xFF4E9F6C)
    internal val presentColour = Color(0xFFD1A32F)

    /** The letter on a green or gold tile: white was 3.2:1 on green and 2.3:1 on gold, so it is picked per fill. */
    internal fun onMark(mark: Int): Color = BoardHues.onFill(if (mark == LexiconMark.CORRECT) correctColour else presentColour)

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
     * guess 2  boat   grey green green green    -> made in the walkthrough: COAT, GOAT or MOAT
     * guess 3  clog   green grey yellow grey    -> made in the walkthrough: it can't be the answer,
     *                                              but C and G tell the three apart
     * ```
     *
     * Guessing COAT, GOAT and MOAT in turn could take three guesses; one guess that cannot win settles
     * it, which is the lesson of four-letter words (`LexiconBalanceTest`). The free-play frame hands
     * the board after CLOG to the hints.
     */
    internal const val TUTORIAL_ANSWER = "coat"
    internal val TUTORIAL_GUESSES = listOf("tape", "boat", "clog")

    private fun tutorialBoard(guesses: Int, current: String = "") = LexiconState(
        length = 4,
        maxGuesses = 6,
        answer = TUTORIAL_ANSWER,
        guesses = TUTORIAL_GUESSES.take(guesses),
        current = current,
    )

    /**
     * Guess [n] typed a letter at a time and then scored, in one frame: any start of the word is
     * progress (a Backspace included), the word scored is the move, and anything else is refused.
     */
    private fun typeGuess(n: Int): (PuzzleState) -> TutorialStep = { next ->
        val word = TUTORIAL_GUESSES[n]
        when {
            next !is LexiconState -> TutorialStep.REJECT
            next.guesses == TUTORIAL_GUESSES.take(n + 1) && next.current.isEmpty() -> TutorialStep.DONE
            next.guesses == TUTORIAL_GUESSES.take(n) && word.startsWith(next.current) -> TutorialStep.PROGRESS
            else -> TutorialStep.REJECT
        }
    }

    /** The next key to tap glows: the next letter of guess [n], then Enter. */
    private fun keyGlow(n: Int): (PuzzleState) -> BoardHighlight = { s ->
        val typed = (s as LexiconState).current
        val word = TUTORIAL_GUESSES[n]
        BoardHighlight(
            strong = setOf(if (typed.length < word.length) LexiconTeacher.key(word[typed.length]) else LexiconTeacher.ENTER),
        )
    }

    override val tutorial: List<TutorialFrame> by lazy {
        fun tiles(row: Int, vararg slots: Int) = slots.map { LexiconTeacher.tile(4, row, it) }.toSet()
        listOf(
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "A word is hidden, here one of four letters. Every guess must be a real " +
                    "word, and each letter comes back marked. Green means right letter, right place.",
                highlight = BoardHighlight(soft = tiles(0, 0, 1, 2, 3)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Yellow means the letter is in the word, but not there: T and A are in it. " +
                    "Grey means the word has none of it: P and E are out.",
                highlight = BoardHighlight(strong = tiles(0, 0, 1), soft = tiles(0, 2, 3)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Use what you know: T and A are in the word, so move them. BOAT puts them in new " +
                    "places. Type BOAT on the keyboard, then tap Enter to score it.",
                glow = keyGlow(1),
                steps = typeGuess(1),
                retry = "Tap the glowing key.",
                done = "Scored: the word ends in OAT.",
            ),
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "COAT, GOAT or MOAT: guess them in turn and it could take three. CLOG can't be the " +
                    "word, but its C and G tell all three apart. Type CLOG and score it.",
                glow = keyGlow(2),
                steps = typeGuess(2),
                retry = "Tap the glowing key.",
                done = "C is green and G grey: only COAT is left.",
            ),
            TutorialFrame(
                state = tutorialBoard(3),
                caption = "Your turn: find the word. Stuck? Hint shows you why.",
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
        val step = LexiconTeacher.teach(s.length, s.guesses, marks, s.current, s.opener) ?: return null
        val base = s
        fun fits(word: String) = LexiconRules.consistent(word, base.guesses, marks)
        fun newGuesses(now: LexiconState) = now.guesses.drop(base.guesses.size)
        return Deduction(
            step,
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
        val (highlight, glow, pulse) = rememberHintPaint()
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

        // Grows past phone size on a tablet or a large window, from both axes (CLAUDE.md): 1 wherever the
        // phone keyboard's 520dp cap does not bind on width or the box is under 640dp tall, so a phone in
        // either orientation keeps its exact look; at most 1.5 (keys about 63dp tall, tiles up to 96dp).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = minOf(maxWidth / MAX_KEYBOARD, maxHeight / SCALE_HEIGHT).coerceIn(1f, MAX_SCALE)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .boardKeys(playable) { key, _, repeat ->
                        // A held Backspace would eat the whole row, a held letter fill it.
                        val action = lexiconKeyAction(key) ?: return@boardKeys false
                        if (!repeat) press(action)
                        true
                    },
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
                // A guessed row settles as its marks land. No units: the one word is the solve.
                val feel = rememberMoveFeel(List(s.maxGuesses * s.length) { s.guesses.getOrNull(it / s.length)?.get(it % s.length) })
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cell = minOf(maxWidth / s.length, maxHeight / s.maxGuesses, MAX_CELL * scale).coerceAtLeast(0.dp)
                    Column(Modifier.size(cell * s.length, cell * s.maxGuesses).highlightGrid(s.length, s.maxGuesses)) {
                        repeat(s.maxGuesses) { row ->
                            Row {
                                repeat(s.length) { slot ->
                                    val guess = s.guesses.getOrNull(row)
                                    val typing = row == s.guesses.size && s.open
                                    val letter = guess?.get(slot) ?: if (typing) s.current.getOrNull(slot) else null
                                    val mark = guess?.let { s.marks(it)[slot] }
                                    val look = highlight.look(LexiconTeacher.tile(s.length, row, slot), dims = tilesNamed)
                                    Tile(letter, mark, typing && letter != null, cell, look, glow, pulse, absent, Modifier.moveFeel(feel, row * s.length + slot, Color(accent)))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val known = keyMarks(s)
                Column(
                    Modifier.widthIn(max = MAX_KEYBOARD * scale).fillMaxWidth().keepClear().padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP * scale),
                ) {
                    keyRows.forEachIndexed { r, letters ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP * scale)) {
                            // The nine-key row is centred under the ten above it.
                            if (r == 1) Spacer(Modifier.weight(0.5f))
                            if (r == 2) {
                                KeyButton("Enter", Modifier.weight(1.5f), playable, LexiconTeacher.ENTER, highlight, glow, pulse,
                                    scheme.surfaceVariant, scheme.onSurfaceVariant, small = true, scale) {
                                    press(LexiconKeyAction.Enter)
                                }
                            }
                            letters.forEach { c ->
                                val mark = known[c - 'a']
                                val fill = when (mark) {
                                    -1 -> scheme.surfaceVariant
                                    // Faded, not recoloured: outline at 30% was the same beige as an untried key in light.
                                    LexiconMark.ABSENT -> scheme.surfaceVariant.copy(alpha = 0.3f)
                                    else -> markColour(mark, absent)
                                }
                                val text = when {
                                    mark > LexiconMark.ABSENT -> onMark(mark)
                                    mark == LexiconMark.ABSENT -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> scheme.onSurfaceVariant
                                }
                                KeyButton(c.uppercase(), Modifier.weight(1f), playable, LexiconTeacher.key(c), highlight, glow, pulse,
                                    fill, text, small = false, scale) {
                                    press(LexiconKeyAction.Letter(c))
                                }
                            }
                            if (r == 1) Spacer(Modifier.weight(0.5f))
                            if (r == 2) {
                                KeyButton("Del", Modifier.weight(1.5f), playable, LexiconTeacher.DELETE, highlight, glow, pulse,
                                    scheme.surfaceVariant, scheme.onSurfaceVariant, small = true, scale) {
                                    press(LexiconKeyAction.Backspace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val MAX_CELL = 64.dp
    private val SCALE_HEIGHT = 640.dp
    private const val MAX_SCALE = 1.5f
    private val MAX_KEYBOARD = 520.dp
    private val KEY_GAP = 5.dp
    private val KEY_HEIGHT = 42.dp

    /** One square of the grid: a typed letter, a marked one, or an empty outline. */
    @Composable
    private fun Tile(
        letter: Char?,
        mark: Int?,
        typed: Boolean,
        cell: Dp,
        look: HintLook,
        glow: Color,
        pulse: State<Float>,
        absent: Color,
        modifier: Modifier = Modifier,
    ) {
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(cell)
                .then(modifier)
                .padding(2.dp)
                .ring(look, glow, pulse, 6.dp, outside = true)
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
                    fontSize = (cell * 0.46f).boardSp(), lineHeight = (cell * 0.46f).boardSp() * 1.2f,
                    fontWeight = FontWeight.Bold,
                    color = if (mark != null && mark > LexiconMark.ABSENT) onMark(mark) else scheme.onSurface,
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
        scale: Float,
        onClick: () -> Unit,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier
                .height(KEY_HEIGHT * scale)
                .highlightAnchor(index)
                .ring(highlight.look(index, dims = false), glow, pulse, 6.dp, outside = true)
                .clip(shape)
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = (if (small) 12.dp else 16.dp).times(scale).boardSp(),
                lineHeight = (if (small) 14.dp else 19.dp).times(scale).boardSp(),
                fontWeight = FontWeight.SemiBold,
                color = text,
                maxLines = 1,
            )
        }
    }
}
