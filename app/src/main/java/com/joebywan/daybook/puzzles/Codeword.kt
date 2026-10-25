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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
import com.joebywan.daybook.puzzles.CodewordLogic.EMPTY
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/**
 * A Codeword in play. [numbers] (a number 1..k per square, 0 for black), [solution] (a letter 0..25 per number, index
 * `number - 1`) and [given] are fixed for the board; [letters] is what the player changes, a letter per number (the
 * givens' included), [EMPTY] for none yet. [selected] is a number 1..k.
 */
@Serializable
data class CodewordState(
    val size: Int,
    val numbers: List<Int>,
    val solution: List<Int>,
    val given: List<Int>,
    val letters: List<Int>,
    val selected: Int? = null,
    override val moves: Int = 0,
) : PuzzleState {

    /** The rules, not a comparison with [solution]: see [CodewordLogic.isSolved]. */
    override val solved: Boolean get() = CodewordLogic.isSolved(size, numbers, letters)

    /** Gives [number] the letter [letter], or takes it away with [EMPTY]. `this` when nothing changes or it is given. */
    fun withLetter(number: Int, letter: Int): CodewordState =
        if (number in given || letters[number - 1] == letter) this
        else copy(letters = letters.toMutableList().also { it[number - 1] = letter }, moves = moves + 1)

    /** Selects [number]; a given number cannot be selected, since nothing about it can change. */
    fun select(number: Int): CodewordState = if (number in given) this else copy(selected = number)
}

private val NUMBER_KEY_HEIGHT = 32.dp
private val LETTER_KEY_HEIGHT = 40.dp
private val ROW_GAP = 3.dp
private val GAP = 6.dp
private val MAX_SQUARE = 52.dp

/** Letters a row on the letter keys: A to M, then N to Z. */
private const val LETTERS_A_ROW = 13

/** How long a broken rule waits before it shows, so passing through one is not shouted at (CLAUDE.md). */
private const val SETTLE_MILLIS = 1000L

/** Codeword: a filled crossword with a number for every letter. */
object Codeword : PuzzleType {

    override val id = "codeword"
    override val displayName = "Codeword"
    override val tagline = "Each number is a letter. Crack the grid."
    override val accent = 0xFF3C28BD
    override val rules = listOf(
        "Every number stands for one letter, the same letter wherever it appears. Different numbers are different letters.",
        "Every word across and down is a real word.",
        "A few letters are given. Find the rest: there is exactly one way.",
        "Tap a square or a number, then a letter. Tap the letter again to clear it.",
        "A word that no word can match any more, or a letter on two numbers, turns red.",
    )

    override val keyboardHelp = listOf("With a number picked, type its letter (again to clear it); Backspace clears, arrows walk the numbers.")

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: CodewordLogic.lastResort(difficulty)

    /** The board when its answer was proved, null otherwise. What `FallbackTest` asserts on. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): CodewordState? = CodewordLogic.generateVerified(seed, difficulty)

    /** The hardest step of the ladder's walk from the givens, the measure the tiers are cut by. */
    override fun neededTechnique(state: PuzzleState): String? {
        val s = state as CodewordState
        val start = s.letters.indices.map { if (it + 1 in s.given) s.solution[it] else EMPTY }
        return CodewordTeacher.walk(s.size, s.numbers, start)?.maxByOrNull { CodewordTeacher.TECHNIQUES.indexOf(it.technique) }?.technique
    }

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as CodewordState).let { if (it.selected == null) it else it.copy(selected = null) }

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as CodewordState
        val step = CodewordTeacher.teach(s) ?: return null
        val number = step.number + 1
        val letter = step.letter
        val mistake = step.technique == CodewordTeacher.MISTAKE
        val wrong = s.letters[step.number]
        val squares = s.numbers.indices.filter { s.numbers[it] == number }.toSet()
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = squares + CodewordLogic.key(number) + CodewordLogic.letterKey(if (mistake) wrong else letter),
            mistake = mistake,
            fallback = step.technique == CodewordTeacher.FALLBACK,
            applyTo = { now ->
                val t = now as CodewordState
                if (t.letters[number - 1] == letter) t else t.withLetter(number, letter).select(number)
            },
            reachedBy = { now ->
                val t = now as CodewordState
                if (mistake) t.letters[number - 1] != wrong else t.letters[number - 1] == letter
            },
        )
    }

    // ---- drawing ---------------------------------------------------------------------------

    private class Look(val strong: Boolean, val soft: Boolean, val dim: Boolean)

    private fun BoardHighlight.look(index: Int, dims: Boolean = true): Look {
        val strong = index in this.strong
        val soft = !strong && index in this.soft
        return Look(strong, soft, dims && !isEmpty && !strong && !soft)
    }

    private fun Modifier.ring(look: Look, glow: Color, pulse: State<Float>, corner: Float): Modifier =
        if (!look.strong && !look.soft) this
        else drawWithContent {
            drawContent()
            val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
            drawRoundRect(
                if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f),
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner.dp.toPx()),
                style = Stroke(w),
            )
        }

    private fun up(l: Int) = ('A' + l).toString()

    /**
     * The grid: black squares, and white ones with their number in the corner and their letter, once placed, in the
     * middle. [tint] colours a white square; [modifier] adds to each (index, number).
     */
    @Composable
    private fun Grid(
        s: CodewordState,
        square: Dp,
        tint: (Int) -> Color,
        letterColour: (number: Int) -> Color,
        modifier: Modifier = Modifier,
        feel: MoveFeel = MoveFeel.Still,
        squareModifier: @Composable (index: Int, number: Int) -> Modifier = { _, _ -> Modifier },
    ) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        // Blocks: near-black on the light card, nearly the background in the dark (the white squares carry the grid).
        val block = if (dark) scheme.background else scheme.onSurface.copy(alpha = 0.85f)
        val n = s.size
        Box(modifier.size(square * n)) {
            for (i in s.numbers.indices) {
                val number = s.numbers[i]
                val at = Modifier.padding(start = square * (i % n), top = square * (i / n)).size(square)
                if (number == 0) {
                    Box(at.padding(0.5.dp).background(block))
                    continue
                }
                val letter = s.letters[number - 1]
                Box(
                    at
                        .moveFeel(feel, i, Color(accent))
                        .then(squareModifier(i, number))
                        .padding(0.5.dp)
                        .clip(RoundedCornerShape(minOf(4.dp, square * 0.1f)))
                        .background(tint(i)),
                ) {
                    if (square >= 14.dp) Text(
                        number.toString(),
                        Modifier.align(Alignment.TopStart).padding(start = square * 0.07f),
                        fontSize = (square.value * 0.27f).sp,
                        lineHeight = (square.value * 0.3f).sp,
                        color = scheme.onSurfaceVariant,
                    )
                    if (letter != EMPTY) Text(
                        up(letter),
                        Modifier.align(Alignment.Center).padding(top = square * 0.12f),
                        fontSize = (square.value * 0.5f).sp,
                        lineHeight = (square.value * 0.55f).sp,
                        fontWeight = if (number in s.given) FontWeight.Bold else FontWeight.Medium,
                        color = letterColour(number),
                    )
                }
            }
        }
    }

    // ---- home-grid motif -------------------------------------------------------------------

    /** The walkthrough's grid with three of its letters found: a legal moment of a real board. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val s = tutorialBoard(placed = PREVIEW_PLACED)
            val square = minOf(maxWidth, maxHeight) / s.size
            val dark = BoardHues.isDark(scheme)
            val (hueA, hueB) = BoardHues.HUE_PAIRS[1]
            Grid(
                s, square,
                tint = { i -> if (s.numbers[i] == PREVIEW_PICK) BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.4f).compositeOver(scheme.surfaceVariant) else scheme.surfaceVariant },
                letterColour = { n -> if (n in s.given) scheme.onSurface else BoardHues.ink(hueB, dark) },
            )
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as CodewordState
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.pair(s.solution.hashCode())
        val pick = BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.4f)
        val ink = BoardHues.ink(hueB, dark)
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())
        val k = s.letters.size

        // A broken rule shows after a pause and goes at once when mended; held here, not in the state (undo).
        val runs = remember(s.size, s.numbers) { CodewordLogic.runs(s.size, s.numbers) }
        val live = remember(s.letters) { CodewordLogic.conflicts(s.size, s.numbers, s.letters) }
        var shown by remember(s.numbers) { mutableStateOf(CodewordLogic.Conflicts(emptySet(), emptySet())) }
        LaunchedEffect(live) {
            val keep = CodewordLogic.Conflicts(shown.numbers intersect live.numbers, shown.runs intersect live.runs)
            shown = keep
            if (keep.numbers == live.numbers && keep.runs == live.runs) return@LaunchedEffect
            delay(SETTLE_MILLIS)
            shown = live
        }
        val red = shown.runs.flatMap { runs[it].toList() }.toSet()
        // A letter set on a number settles on every square of it; a word spelled by the rules glows once.
        val cells = s.numbers.map { if (it == 0) null else s.letters[it - 1] }
        val feel = rememberMoveFeel(cells, runs) { r ->
            val word = runs[r].map { s.letters[s.numbers[it] - 1] }
            word.none { it == EMPTY } && CodewordWords.isWord(CharArray(word.size) { 'a' + word[it] }.concatToString())
        }
        val holder = IntArray(26) { 0 }.also { h -> s.letters.forEachIndexed { i, l -> if (l != EMPTY) h[l] = i + 1 } }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .boardKeys(interactive) { key, _, repeat ->
                    val action = codewordKeyAction(key) ?: return@boardKeys false
                    // With nothing picked a letter is left to the play screen, so H still asks for a hint there.
                    if (action is CodewordKeyAction.Letter && s.selected == null) return@boardKeys false
                    if (!repeat || action is CodewordKeyAction.Move) s.applyKey(action)?.let(onState)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            val numberRows = if (k > LETTERS_A_ROW) 2 else 1
            val controls = NUMBER_KEY_HEIGHT * numberRows + ROW_GAP * (numberRows - 1) + LETTER_KEY_HEIGHT * 2 + ROW_GAP + GAP * 2
            // Sized from both axes (CLAUDE.md): the grid leaves room for the number key and the letters.
            val room = if (constraints.hasBoundedHeight) (maxHeight - controls).coerceAtLeast(0.dp) else maxWidth
            val square = minOf(maxWidth / s.size, room / s.size, MAX_SQUARE)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Grid(
                    s, square,
                    modifier = Modifier.highlightGrid(s.size, s.size),
                    feel = feel,
                    tint = { i -> if (s.numbers[i] == s.selected) pick.compositeOver(scheme.surface) else scheme.surface },
                    letterColour = { n ->
                        when {
                            n in shown.numbers -> scheme.error
                            n in s.given || n == s.selected -> scheme.onSurface
                            else -> ink
                        }
                    },
                    squareModifier = { i, n ->
                        val look = highlight.look(i)
                        Modifier
                            .then(if (look.dim) Modifier.alpha(0.3f) else Modifier)
                            .ring(look, glow, pulse, corner = 4f)
                            .then(if (i in red) Modifier.border(2.dp, scheme.error, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable(enabled = interactive && n !in s.given) { onState(s.select(n)) }
                    },
                )

                Spacer(Modifier.height(GAP))

                // The key: every number with its letter, also a way to pick a number.
                Column(Modifier.fillMaxWidth().keepClear(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    val perRow = (k + numberRows - 1) / numberRows
                    for (row in 0 until numberRows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (slot in 0 until perRow) {
                            val n = row * perRow + slot + 1
                            if (n > k) { Spacer(Modifier.weight(1f)); continue }
                            val l = s.letters[n - 1]
                            val given = n in s.given
                            Column(
                                Modifier
                                    .weight(1f)
                                    .height(NUMBER_KEY_HEIGHT)
                                    .highlightAnchor(CodewordLogic.key(n))
                                    .ring(highlight.look(CodewordLogic.key(n), dims = false), glow, pulse, corner = 6f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            s.selected == n -> pick.compositeOver(scheme.surface)
                                            given -> scheme.surfaceVariant
                                            else -> scheme.surface
                                        },
                                    )
                                    .clickable(enabled = interactive && !given) { onState(s.select(n)) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(n.toString(), fontSize = 9.sp, lineHeight = 10.sp, color = scheme.onSurfaceVariant)
                                Text(
                                    if (l == EMPTY) "·" else up(l),
                                    fontSize = 14.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = if (given) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        n in shown.numbers -> scheme.error
                                        l == EMPTY -> scheme.outline
                                        given || n == s.selected -> scheme.onSurface
                                        else -> ink
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(GAP))

                // The letters, A to Z: one another number holds is greyed with that number under it, never refused.
                Column(Modifier.fillMaxWidth().keepClear(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    for (row in 0 until 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (slot in 0 until LETTERS_A_ROW) {
                            val l = row * LETTERS_A_ROW + slot
                            val owner = holder[l].takeIf { it != 0 && it != s.selected }
                            val at = s.selected
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(LETTER_KEY_HEIGHT)
                                    .highlightAnchor(CodewordLogic.letterKey(l))
                                    .ring(highlight.look(CodewordLogic.letterKey(l), dims = false), glow, pulse, corner = 6f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            at != null && s.letters[at - 1] == l -> pick.compositeOver(scheme.surface)
                                            owner != null -> scheme.surfaceVariant
                                            else -> scheme.surface
                                        },
                                    )
                                    .clickable(enabled = interactive && at != null) {
                                        val next = s.enter(at ?: return@clickable, l)
                                        if (next !== s) onState(next)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(up(l), fontSize = 16.sp, color = if (owner != null) scheme.outline else scheme.onSurface)
                                if (owner != null) Text(
                                    owner.toString(),
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = scheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- walkthrough -----------------------------------------------------------------------

    /**
     * The walkthrough's grid, ATE / PEAT / EXTRA / TEEN / NET across and APE / TEXT / EATEN / TREE / ANT down, with
     * T and E given. Its numbers are fixed: [TUTORIAL_ORDER]'s letters are 1 to 7.
     */
    internal const val TUTORIAL_GRID = "ate##" + "peat#" + "extra" + "#teen" + "##net"
    internal const val TUTORIAL_ORDER = "rteanxp"

    /** Number [letter]'s in the walkthrough. */
    internal fun tutorialNumber(letter: Char): Int = TUTORIAL_ORDER.indexOf(letter) + 1

    /** The motif: A and X found, X's number picked. */
    private val PREVIEW_PLACED = mapOf(tutorialNumber('a') to 'a' - 'a', tutorialNumber('x') to 'x' - 'a')
    private val PREVIEW_PICK = tutorialNumber('x')

    /** The walkthrough's board, T and E given, with [placed] (number to letter) and [selected]. */
    internal fun tutorialBoard(placed: Map<Int, Int> = emptyMap(), selected: Int? = null): CodewordState {
        val numbers = TUTORIAL_GRID.map { if (it == '#') 0 else tutorialNumber(it) }
        val solution = TUTORIAL_ORDER.map { it - 'a' }
        val given = listOf(tutorialNumber('t'), tutorialNumber('e')).sorted()
        return CodewordState(
            size = 5, numbers = numbers, solution = solution, given = given,
            letters = solution.indices.map { if (it + 1 in given) solution[it] else placed[it + 1] ?: EMPTY },
            selected = selected,
        )
    }

    /** The squares of ATE, the top word, and EXTRA, the middle row. */
    internal val TUTORIAL_ATE = setOf(0, 1, 2)
    internal val TUTORIAL_EXTRA = setOf(10, 11, 12, 13, 14)

    override val tutorial: List<TutorialFrame> by lazy {
        val e = tutorialNumber('e')
        val a = tutorialNumber('a')
        val x = tutorialNumber('x')
        val r = tutorialNumber('r')
        val t = tutorialNumber('t')
        val b0 = tutorialBoard()
        fun squaresOf(n: Int) = b0.numbers.indices.filter { b0.numbers[it] == n }.toSet()
        fun letters(placed: Map<Int, Int>) = tutorialBoard(placed).letters
        /** Pick [number], then give it [letter]: one frame, its squares or key glowing until it is picked. */
        fun place(before: Map<Int, Int>, number: Int, letter: Char) = TutorialPath(
            { it is CodewordState && it.letters == letters(before) && it.selected == number },
            { it is CodewordState && it.letters == letters(before + (number to (letter - 'a'))) },
        )
        val oneA = mapOf(a to 'a' - 'a')
        val twoX = oneA + (x to 'x' - 'a')
        val pa = place(emptyMap(), a, 'a')
        val px = place(oneA, x, 'x')
        val clash = TutorialPath(
            { it is CodewordState && it.letters == letters(twoX) && it.selected == r },
            { it is CodewordState && it.letters == letters(twoX + (r to 'x' - 'a')) },
            // Back where it started, but by way of the X: only a move counted since says so.
            { it is CodewordState && it.letters == letters(twoX) && it.moves > 0 },
        )
        listOf(
            TutorialFrame(
                state = b0,
                caption = "Each number is a letter, the same one everywhere, and no two numbers share one. T and E are given. " +
                    "Every word across and down is a real word.",
                highlight = BoardHighlight(strong = squaresOf(t) + squaresOf(e)),
            ),
            TutorialFrame(
                state = b0,
                caption = "The top word reads $a T E. Only ATE fits, so $a is A. Tap one of its squares, then A.",
                glow = { s ->
                    if (pa.stage(s) < 0) BoardHighlight(strong = TUTORIAL_ATE)
                    else BoardHighlight(strong = setOf(CodewordLogic.letterKey(0)), soft = squaresOf(a))
                },
                steps = pa.steps,
                retry = "Tap a glowing square, then the A.",
                done = "Every $a is A.",
            ),
            TutorialFrame(
                state = tutorialBoard(oneA, selected = a),
                caption = "The middle row reads E $x T $r A. Only EXTRA fits, so $x is X. Tap $x in the key under the grid, then X.",
                glow = { s ->
                    if (px.stage(s) < 0) BoardHighlight(strong = setOf(CodewordLogic.key(x)), soft = TUTORIAL_EXTRA)
                    else BoardHighlight(strong = setOf(CodewordLogic.letterKey('x' - 'a')), soft = squaresOf(x))
                },
                steps = px.steps,
                retry = "Tap the glowing $x, then the X.",
                done = "Every $x is X.",
            ),
            TutorialFrame(
                state = tutorialBoard(twoX, selected = x),
                caption = "A letter on two numbers turns red. Give $r the X that $x has: tap $r in the key, then X. " +
                    "Then tap X again to clear it.",
                glow = { s ->
                    when (clash.stage(s)) {
                        -1 -> BoardHighlight(strong = setOf(CodewordLogic.key(r)), soft = squaresOf(x))
                        0 -> BoardHighlight(strong = setOf(CodewordLogic.letterKey('x' - 'a')), soft = squaresOf(x))
                        else -> BoardHighlight(strong = squaresOf(r) + CodewordLogic.letterKey('x' - 'a'), soft = squaresOf(x), warning = true)
                    }
                },
                steps = clash.steps,
                retry = "Tap the glowing $r, then the X, then the X again.",
                done = "Cleared. Tapping the letter again takes it out.",
            ),
            TutorialFrame(
                state = tutorialBoard(twoX, selected = r),
                caption = "Your turn: crack the rest. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved: ATE, PEAT, EXTRA, TEEN and NET.",
            ),
        )
    }
}
