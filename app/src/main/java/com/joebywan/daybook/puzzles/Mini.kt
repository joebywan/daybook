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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.listed
import com.joebywan.daybook.puzzles.MiniLogic.BLACK
import com.joebywan.daybook.puzzles.MiniLogic.OPEN
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.rememberSettled
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.serialization.Serializable

/**
 * A Mini crossword in play. [answer] (the filled grid in reading order, [BLACK] for a black square) and [clues] (one
 * per slot, across then down, in [MiniLogic.slots] order) are fixed for the board; [letters] is what the player
 * types, [OPEN] for a square not filled. [cursor] is the selected square and [across] the way it reads.
 */
@Serializable
data class MiniState(
    val size: Int,
    val answer: String,
    val clues: List<String>,
    val letters: String,
    val cursor: Int? = null,
    val across: Boolean = true,
    override val moves: Int = 0,
) : PuzzleState {

    /** The rules, not a comparison with [answer]: see [MiniLogic.isSolved]. */
    override val solved: Boolean get() = MiniLogic.isSolved(size, letters, clues)

    private fun white(r: Int, c: Int) = r in 0 until size && c in 0 until size && letters[r * size + c] != BLACK

    /** Whether a word runs through [square] across ([acrossWay]) or down. */
    fun hasSlot(square: Int, acrossWay: Boolean): Boolean {
        val r = square / size
        val c = square % size
        return if (acrossWay) white(r, c - 1) || white(r, c + 1) else white(r - 1, c) || white(r + 1, c)
    }

    /** The slot the cursor is in, the way it reads; null with nothing selected. */
    fun slotIndex(): Int? {
        val at = cursor ?: return null
        return MiniLogic.slots(size, letters).indexOfFirst { it.across == across && at in it.squares }.takeIf { it >= 0 }
    }

    /** Selects [square]; the selected square again turns the way it reads, where a word runs the other way. */
    fun select(square: Int): MiniState {
        if (letters[square] == BLACK) return this
        if (square == cursor) return turn()
        return copy(cursor = square, across = if (hasSlot(square, across)) across else !across)
    }

    /** Turns the way the cursor reads, where a word runs the other way. */
    fun turn(): MiniState = cursor?.let { if (hasSlot(it, !across)) copy(across = !across) else this } ?: this

    /** Selects slot [index] ([MiniLogic.slots] order), at its first open square, else its first. */
    fun selectSlot(index: Int): MiniState {
        val slot = MiniLogic.slots(size, letters)[index]
        val at = slot.squares.firstOrNull { letters[it] == OPEN } ?: slot.squares[0]
        return copy(cursor = at, across = slot.across)
    }

    /** Square [square] takes [letter] ([OPEN] clears it). `this` when nothing changes. */
    fun withLetter(square: Int, letter: Char): MiniState =
        if (letters[square] == BLACK || letters[square] == letter) this
        else copy(letters = letters.substring(0, square) + letter + letters.substring(square + 1), moves = moves + 1)

    /**
     * Types [letter] at the cursor, then moves on along the slot: to the next open square after it, else the next square.
     * One state, so one undo takes the letter back.
     */
    fun type(letter: Char): MiniState {
        val at = cursor ?: return this
        val placed = withLetter(at, letter)
        val slot = slotIndex()?.let { MiniLogic.slots(size, letters)[it] } ?: return placed
        val i = slot.squares.indexOf(at)
        val next = slot.squares.drop(i + 1).firstOrNull { placed.letters[it] == OPEN } ?: slot.squares.getOrNull(i + 1) ?: at
        return placed.copy(cursor = next)
    }

    /** Backspace: clears the cursor's letter, or with none there steps back along the slot and clears that one. */
    fun erase(): MiniState {
        val at = cursor ?: return this
        if (letters[at] != OPEN) return withLetter(at, OPEN)
        val slot = slotIndex()?.let { MiniLogic.slots(size, letters)[it] } ?: return this
        val back = slot.squares.getOrNull(slot.squares.indexOf(at) - 1) ?: return this
        return withLetter(back, OPEN).copy(cursor = back)
    }

    /** [word] typed into slot [index], the cursor left on its first square. One state. */
    fun withWord(index: Int, word: String): MiniState {
        val slot = MiniLogic.slots(size, letters)[index]
        val next = letters.toCharArray()
        slot.squares.forEachIndexed { i, sq -> next[sq] = word[i] }
        val typed = next.concatToString()
        return copy(letters = typed, cursor = slot.squares[0], across = slot.across, moves = if (typed == letters) moves else moves + 1)
    }
}

private val KEY_HEIGHT = 40.dp
private val KEY_GAP = 4.dp
private val CLUE_HEIGHT = 44.dp
private val GAP = 6.dp
private val MAX_SQUARE = 64.dp
private val MAX_KEYBOARD = 520.dp

/** The Mini crossword: a small grid, a clue for every word across and down. */
object Mini : PuzzleType {

    override val id = "mini"
    override val displayName = "Crossword"
    override val tagline = "A mini crossword. Solve the clues."
    override val accent = 0xFF6A30DF
    override val rules = listOf(
        "Every row and column of white squares is a word, and each word has a clue: 1 Across, 2 Down and so on.",
        "Tap a square, then type. Tap it again to turn between Across and Down, or tap the clue.",
        "Letters cross: a letter in one word is a letter of the word across it.",
        "A word that is no word in the list turns red. Each clue has exactly one answer.",
    )

    override val keyboardHelp = listOf("Type letters; Backspace clears. Arrows move (one the other way turns first), Space turns.")

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = MiniLogic.generate(seed, difficulty)

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as MiniState).let { if (it.cursor == null && it.across) it else it.copy(cursor = null, across = true) }

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as MiniState
        val step = MiniTeacher.teach(s) ?: return null
        val mistake = step.technique == MiniTeacher.MISTAKE
        val slot = MiniLogic.slots(s.size, s.letters)[step.slot]
        val word = step.word
        if (word != null) {
            val firstOpen = slot.squares.indices.firstOrNull { s.letters[slot.squares[it]] == OPEN }
            return Deduction(
                technique = step.technique,
                nudge = step.nudge,
                explanation = step.explanation,
                focus = step.focus,
                cited = step.cited,
                targets = slot.squares.toSet() + listOfNotNull(firstOpen?.let { MiniLogic.letterKey(word[it] - 'a') }),
                applyTo = { now -> (now as MiniState).let { t -> if (MiniLogic.spelled(t.letters, slot) == word) t else t.withWord(step.slot, word) } },
                reachedBy = { now -> MiniLogic.spelled((now as MiniState).letters, slot) == word },
            )
        }
        val sq = step.square
        val wrong = s.letters[sq]
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = setOf(sq) + if (mistake) emptySet() else setOf(MiniLogic.letterKey(step.letter - 'a')),
            mistake = mistake,
            fallback = step.technique == MiniTeacher.CLUE,
            applyTo = { now ->
                val t = now as MiniState
                if (t.letters[sq] == step.letter) t else t.withLetter(sq, step.letter).copy(cursor = sq, across = slot.across)
            },
            reachedBy = { now -> val t = now as MiniState; if (mistake) t.letters[sq] != wrong else t.letters[sq] == step.letter },
        )
    }

    // ---- drawing ---------------------------------------------------------------------------

    private val TEXT = CrosswordText(corner = 0.25f, cornerLine = 0.28f, letter = 0.52f, letterLine = 0.56f, letterTop = 0.1f)

    /** [CrosswordGrid] read from the board's string, numbered by [MiniLogic.numbers]. */
    @Composable
    private fun Grid(
        size: Int,
        letters: String,
        square: Dp,
        tint: (Int) -> Color,
        letterColour: (Int) -> Color,
        modifier: Modifier = Modifier,
        feel: MoveFeel = MoveFeel.Still,
        squareModifier: @Composable (index: Int) -> Modifier = { Modifier },
    ) {
        val numbers = remember(size, letters.map { it == BLACK }) { MiniLogic.numbers(size, letters) }
        CrosswordGrid(
            size, square, TEXT, Color(accent),
            black = { letters[it] == BLACK },
            number = { numbers[it] },
            letter = { i -> letters[i].takeIf { it != OPEN }?.uppercase() },
            tint = tint,
            letterColour = letterColour,
            modifier = modifier,
            feel = feel,
            squareModifier = squareModifier,
        )
    }

    // ---- home-grid motif -------------------------------------------------------------------

    /** The walkthrough's grid with its first across word in and the cursor on it: a legal moment of a real board. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val s = PREVIEW
            val square = minOf(maxWidth, maxHeight) / s.size
            val dark = BoardHues.isDark(scheme)
            val (hueA, hueB) = BoardHues.HUE_PAIRS[2]
            val slot = MiniLogic.slots(s.size, s.letters)[0].squares
            Grid(
                s.size, s.letters, square,
                tint = { i -> if (i in slot) BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.4f).compositeOver(scheme.surfaceVariant) else scheme.surfaceVariant },
                letterColour = { BoardHues.ink(hueB, dark) },
            )
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    private val keyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MiniState
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.pair(s.answer.hashCode())
        val word = BoardHues.mark(hueA).copy(alpha = if (dark) 0.3f else 0.22f).compositeOver(scheme.surface)
        val here = BoardHues.mark(hueA).copy(alpha = if (dark) 0.7f else 0.55f).compositeOver(scheme.surface)
        val ink = BoardHues.ink(hueB, dark)
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        val slots = remember(s.size, s.answer) { MiniLogic.slots(s.size, s.answer) }
        val units = remember(slots) { slots.map { it.squares } }
        // A word no word of the list can be shows after a pause and goes at once when mended (held here, not in the state).
        val live = remember(s.letters) { MiniLogic.deadSlots(s.size, s.letters) }
        val settled = rememberSettled(live, reset = s.answer, change = s.letters)
        val red = (live intersect settled).flatMap { slots[it].squares.toList() }.toSet()
        // A letter settles; a word the list holds glows once (a word, not the answer: that would be a check).
        val feel = rememberMoveFeel(s.letters.map { it }, units) { r -> WheelWords.isGridWord(MiniLogic.spelled(s.letters, slots[r])) }
        val current = s.slotIndex()
        val inWord = current?.let { slots[it].squares.toSet() } ?: emptySet()

        fun press(action: MiniKeyAction) {
            if (!interactive) return
            s.applyKey(action)?.let(onState)
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .boardKeys(interactive) { key, _, repeat ->
                    val action = miniKeyAction(key) ?: return@boardKeys false
                    // With nothing picked a letter is left to the play screen, so H still asks for a hint there.
                    if (action is MiniKeyAction.Letter && s.cursor == null) return@boardKeys false
                    if (!repeat || action is MiniKeyAction.Move) press(action)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            val keyboard = KEY_HEIGHT * keyRows.size + KEY_GAP * (keyRows.size - 1)
            val controls = CLUE_HEIGHT + keyboard + GAP * 2
            // Sized from both axes (CLAUDE.md): the grid leaves room for the clue and the keys.
            val room = if (constraints.hasBoundedHeight) (maxHeight - controls).coerceAtLeast(0.dp) else maxWidth
            val square = minOf(maxWidth / s.size, room / s.size, MAX_SQUARE)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Grid(
                    s.size, s.letters, square,
                    modifier = Modifier.highlightGrid(s.size, s.size),
                    feel = feel,
                    tint = { i ->
                        when {
                            i == s.cursor -> here
                            i in inWord -> word
                            else -> scheme.surface
                        }
                    },
                    letterColour = { i -> if (i in red) scheme.error else if (i == s.cursor || i in inWord) scheme.onSurface else ink },
                    squareModifier = { i ->
                        val look = highlight.look(i)
                        Modifier
                            .then(if (look.dim) Modifier.alpha(0.3f) else Modifier)
                            .ring(look, glow, pulse, corner = 4.dp)
                            .then(if (i in red) Modifier.border(2.dp, scheme.error, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable(enabled = interactive) { onState(s.select(i)) }
                    },
                )

                Spacer(Modifier.height(GAP))

                // The clue the cursor is in, at a fixed height so a longer clue never moves the board. Tap to turn.
                val clueLook = highlight.look(MiniLogic.CLUE_BAR, dims = false)
                Row(
                    Modifier
                        .widthIn(max = MAX_KEYBOARD)
                        .fillMaxWidth()
                        .height(CLUE_HEIGHT)
                        .highlightAnchor(MiniLogic.CLUE_BAR)
                        .keepClear()
                        .ring(clueLook, glow, pulse, corner = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(word),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClueArrow("‹", interactive) { onState(s.selectSlot(((current ?: 0) - 1 + slots.size) % slots.size)) }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = interactive && current != null) { onState(s.turn()) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            if (current == null) "Tap a square to start." else "${slots[current].number}${if (slots[current].across) "A" else "D"}  ${s.clues[current]}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ClueArrow("›", interactive) { onState(s.selectSlot(((current ?: -1) + 1) % slots.size)) }
                }

                Spacer(Modifier.height(GAP))

                Column(
                    Modifier.widthIn(max = MAX_KEYBOARD).fillMaxWidth().keepClear(),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP),
                ) {
                    keyRows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                            if (r == 1) Spacer(Modifier.weight(0.5f))
                            for (c in row) {
                                val index = MiniLogic.letterKey(c - 'a')
                                Key(c.uppercase(), Modifier.weight(1f).highlightAnchor(index).ring(highlight.look(index, dims = false), glow, pulse, corner = 6.dp), interactive && s.cursor != null) {
                                    press(MiniKeyAction.Letter(c))
                                }
                            }
                            if (r == 1) Spacer(Modifier.weight(0.5f))
                            if (r == 2) Key("Del", Modifier.weight(1.5f), interactive && s.cursor != null, small = true) { press(MiniKeyAction.Erase) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ClueArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
        Box(Modifier.width(36.dp).fillMaxHeight().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }

    @Composable
    private fun Key(label: String, modifier: Modifier, enabled: Boolean, small: Boolean = false, onClick: () -> Unit) {
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier
                .height(KEY_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.surfaceVariant)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = if (small) 12.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }

    // ---- walkthrough -----------------------------------------------------------------------

    /** The walkthrough's grid in reading order (see [TUTORIAL_WORDS]). */
    internal const val TUTORIAL_GRID = "#tap" + "taxi" + "isle" + "eke#"

    /** The walkthrough's board: [TUTORIAL_GRID] with [typed] squares filled from it, the cursor at [cursor]. */
    internal fun tutorialBoard(typed: Set<Int> = emptySet(), cursor: Int? = null, across: Boolean = true): MiniState {
        val slots = MiniLogic.slots(4, TUTORIAL_GRID)
        return MiniState(
            size = 4, answer = TUTORIAL_GRID,
            clues = slots.map { MiniLogic.clueOf(MiniLogic.spelled(TUTORIAL_GRID, it))!! },
            letters = TUTORIAL_GRID.indices.map { if (TUTORIAL_GRID[it] == BLACK) BLACK else if (it in typed) TUTORIAL_GRID[it] else OPEN }.joinToString(""),
            cursor = cursor, across = across,
        )
    }

    private val PREVIEW by lazy { tutorialBoard(typed = MiniLogic.slots(4, TUTORIAL_GRID)[0].squares.toSet()) }

    /** The walkthrough's two taught words, 1 Across and 1 Down; the free play is the rest. */
    internal val TUTORIAL_ACROSS by lazy { MiniLogic.slots(4, TUTORIAL_GRID)[0] }
    internal val TUTORIAL_DOWN by lazy { MiniLogic.slots(4, TUTORIAL_GRID).first { !it.across } }

    /** The square the walkthrough's slip goes in: 4 Across's first, open after the two words. */
    internal val TUTORIAL_WRONG by lazy {
        MiniLogic.slots(4, TUTORIAL_GRID).first { it.across && it.number != 1 }.squares.first { it !in TUTORIAL_DOWN.squares }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val a1 = TUTORIAL_ACROSS
        val d1 = TUTORIAL_DOWN
        val first = a1.squares[0]
        val acrossWord = MiniLogic.spelled(TUTORIAL_GRID, a1).uppercase()
        val downWord = MiniLogic.spelled(TUTORIAL_GRID, d1).uppercase()
        val b0 = tutorialBoard()
        val clueA = b0.clues[0]
        val clueD = b0.clues[MiniLogic.slots(4, TUTORIAL_GRID).indexOfFirst { !it.across }]
        fun typed(s: PuzzleState, set: Set<Int>) = s is MiniState && s.letters == tutorialBoard(set).letters
        /** Typing [slot]'s word into it from [before]: one stage a letter, in order, after the [start] stage. */
        fun word(before: Set<Int>, slot: MiniLogic.Slot, start: (MiniState) -> Boolean): List<(PuzzleState) -> Boolean> {
            val open = slot.squares.filter { it !in before }
            return listOf<(PuzzleState) -> Boolean>({ it is MiniState && typed(it, before) && start(it) }) +
                open.indices.map { k -> { s: PuzzleState -> typed(s, before + open.take(k + 1)) } }
        }
        val pa = TutorialPath(*word(emptySet(), a1) { it.cursor == first && it.across }.toTypedArray())
        val afterA = a1.squares.toSet()
        val second = d1.squares[1]
        val pd = TutorialPath(
            { it is MiniState && typed(it, afterA) && it.cursor == second && it.across },
            *word(afterA, d1) { it.cursor == second && !it.across }.toTypedArray(),
        )
        val both = afterA + d1.squares.toList()
        val wrong = TUTORIAL_WRONG
        val slip = 'z'
        val pw = TutorialPath(
            { it is MiniState && typed(it, both) && it.cursor == wrong },
            { it is MiniState && it.letters == tutorialBoard(both).withLetter(wrong, slip).letters },
            // Back where it started, by way of the Z: only a move counted since says so.
            { it is MiniState && typed(it, both) && it.moves > 0 },
        )
        listOf(
            TutorialFrame(
                state = b0,
                caption = "Every row and column of white squares is a word, and each has a clue. Letters cross: " +
                    "a letter of one word is a letter of the word across it.",
                highlight = BoardHighlight(strong = a1.squares.toSet(), soft = d1.squares.toSet()),
            ),
            TutorialFrame(
                state = b0,
                caption = "1 Across is \"$clueA\": $acrossWord. Tap the glowing square, then type $acrossWord.",
                glow = { s ->
                    val k = pa.stage(s)
                    if (k < 0) BoardHighlight(strong = setOf(first), soft = a1.squares.toSet())
                    else BoardHighlight(strong = setOf(MiniLogic.letterKey(acrossWord[minOf(k, acrossWord.length - 1)].lowercaseChar() - 'a')), soft = a1.squares.toSet())
                },
                steps = pa.steps,
                retry = "Tap the glowing square, then type $acrossWord.",
                done = "$acrossWord is in, and its first letter starts 1 Down.",
            ),
            TutorialFrame(
                state = tutorialBoard(afterA, cursor = a1.squares.last()),
                caption = "1 Down is \"$clueD\": $downWord. Tap the glowing square under the 1, tap it again to turn it Down, then type the rest.",
                glow = { s ->
                    val k = pd.stage(s)
                    val open = d1.squares.filter { it !in afterA }
                    if (k < 1) BoardHighlight(strong = setOf(second), soft = d1.squares.toSet())
                    else BoardHighlight(strong = setOf(MiniLogic.letterKey(TUTORIAL_GRID[open[minOf(k - 1, open.size - 1)]] - 'a')), soft = d1.squares.toSet())
                },
                steps = pd.steps,
                retry = "Tap the glowing square twice, then type $downWord.",
                done = "Tapping a square again turns it between Across and Down.",
            ),
            TutorialFrame(
                state = tutorialBoard(both, cursor = d1.squares.last(), across = false),
                caption = "A slip is easy to mend. Tap the glowing square and type Z, then tap Del to clear it.",
                glow = { s ->
                    when (pw.stage(s)) {
                        -1 -> BoardHighlight(strong = setOf(wrong))
                        0 -> BoardHighlight(strong = setOf(MiniLogic.letterKey(slip - 'a')), soft = setOf(wrong))
                        else -> BoardHighlight(strong = setOf(wrong), warning = true)
                    }
                },
                steps = pw.steps,
                retry = "Tap the glowing square, type Z, then tap Del.",
                done = "Cleared. Del takes back the letter under the cursor.",
            ),
            TutorialFrame(
                state = tutorialBoard(both, cursor = wrong),
                caption = "Your turn: fill the rest. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved: " + listed(MiniLogic.slots(4, TUTORIAL_GRID).map { MiniLogic.spelled(TUTORIAL_GRID, it).uppercase() }) + ".",
            ),
        )
    }
}
