package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.placementDeduction
import com.joebywan.daybook.core.reportHighlight
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY
import com.joebywan.daybook.ui.PAD_HEIGHT
import com.joebywan.daybook.ui.PadKey
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.BoardHues
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel

/**
 * A Cipher sum in play. [words] (the addends, then the total) and [solution] are fixed for the board;
 * [digits] is what the player changes, one per letter in alphabetical order, [EMPTY] for none yet.
 * [selected] is a letter index.
 */
@Serializable
data class CipherState(
    val words: List<String>,
    val solution: List<Int>,
    val digits: List<Int>,
    val selected: Int? = null,
    override val moves: Int = 0,
) : PuzzleState {

    internal val sum: CipherSum get() = CipherSum(words)

    /** The rules, not a comparison with [solution]: see [CipherLogic.isSolved]. */
    override val solved: Boolean get() = CipherLogic.isSolved(sum, digits)

    /** Gives [letter] the digit [digit], or takes its digit away with [EMPTY]. `this` when nothing changes. */
    fun withDigit(letter: Int, digit: Int): CipherState =
        if (digits[letter] == digit) this
        else copy(digits = digits.toMutableList().also { it[letter] = digit }, moves = moves + 1)

    fun select(letter: Int): CipherState = copy(selected = letter)
}

private val KEY_HEIGHT = 46.dp
private val GAP = 14.dp
private val MAX_SLOT = 64.dp

/** A tile's side as a share of its slot. */
private const val TILE = 0.88f

/** The gap between the addends and the total, where the rule is drawn, as a share of a slot. */
private const val RULE = 0.32f

/** How long a broken rule waits before it shows, so passing through one is not shouted at (CLAUDE.md). */
private const val SETTLE_MILLIS = 1000L

/** Cipher: a sum of words in which each letter stands for a digit. */
object Cipher : PuzzleType {

    override val id = "cipher"
    override val displayName = "Cipher"
    override val tagline = "Each letter is a digit. Make the sum true."
    override val accent = 0xFFC84161
    override val rules = listOf(
        "Each letter stands for one digit, 0 to 9, the same digit everywhere it appears.",
        "Different letters are different digits, and no word starts with 0.",
        "Give every letter its digit so the sum is true. There is exactly one way.",
        "Tap a letter, then a digit. Tap the digit again to clear it.",
        "Two letters on one digit, a word starting with 0, or a column that doesn't add up turns red.",
    )

    override val keyboardHelp = listOf("Type a letter to pick it (H too, when the sum has an H), then a digit. Backspace clears, arrows walk the letters.")

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: CipherLogic.lastResort(difficulty)

    /** The board when its answer was proved, null otherwise. What `FallbackTest` asserts on. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): CipherState? =
        CipherLogic.generateVerified(seed, difficulty)

    /** The hardest step of the ladder's walk from an empty board, the measure the tiers are cut by. */
    override fun neededTechnique(state: PuzzleState): String? =
        CipherTeacher.walk((state as CipherState).sum)?.maxByOrNull { CipherTeacher.TECHNIQUES.indexOf(it.technique) }?.technique

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as CipherState).let { if (it.selected == null) it else it.copy(selected = null) }

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as CipherState
        val step = CipherTeacher.teach(s) ?: return null
        val l = step.letter
        return placementDeduction(
            s, PlacementStep(step.technique, l, step.digit, step.focus, step.cited, step.nudge, step.explanation),
            mistake = step.technique == CipherTeacher.MISTAKE,
            fallback = step.technique == CipherTeacher.FALLBACK,
            targets = s.sum.tilesOf(l) + CipherLogic.key(l),
            valueAt = { t, letter -> t.digits[letter] },
            place = { t, letter, digit -> t.withDigit(letter, digit).select(letter) },
        )
    }

    // ---- walkthrough -----------------------------------------------------------------------

    /** TO + GO = OUT: 21 + 81 = 102, its one answer. Letters in order: G O T U. */
    internal val TUTORIAL_WORDS = listOf("to", "go", "out")
    internal val TUTORIAL_SOLUTION = listOf(8, 1, 2, 0)
    private const val G = 0
    private const val O = 1
    private const val T = 2
    private const val U = 3

    internal fun tutorialBoard(placed: Map<Int, Int> = emptyMap(), selected: Int? = null): CipherState =
        CipherState(
            words = TUTORIAL_WORDS,
            solution = TUTORIAL_SOLUTION,
            digits = List(4) { placed[it] ?: EMPTY },
            selected = selected,
        )

    override val tutorial: List<TutorialFrame> by lazy {
        val sum = CipherSum(TUTORIAL_WORDS)
        val all = (0 until 4).flatMap { sum.tilesOf(it) }.toSet()
        val one = mapOf(O to 1)
        val two = one + (T to 2)
        fun digits(placed: Map<Int, Int>) = tutorialBoard(placed).digits
        /** Select [letter], then give it [digit]: one frame, the tiles glowing until the letter is selected. */
        fun place(before: Map<Int, Int>, letter: Int, digit: Int) = TutorialPath(
            { it is CipherState && it.digits == digits(before) && it.selected == letter },
            { it is CipherState && it.digits == digits(before + (letter to digit)) },
        )
        val o = place(emptyMap(), O, 1)
        val t = place(one, T, 2)
        val clash = TutorialPath(
            { it is CipherState && it.digits == digits(two) && it.selected == U },
            { it is CipherState && it.digits == digits(two + (U to 1)) },
            // Back where it started, but by way of the 1: only a move counted since says so.
            { it is CipherState && it.digits == digits(two) && it.moves > 0 },
        )
        listOf(
            TutorialFrame(
                state = tutorialBoard(),
                caption = "Each letter is a digit, the same one wherever it appears, and no two letters share one. Find them so the sum is true.",
                highlight = BoardHighlight(strong = all),
            ),
            TutorialFrame(
                state = tutorialBoard(),
                caption = "OUT is longer than TO and GO, so its O is what the tens column carries. Two digits and a carry " +
                    "make at most 19, so O is 1. Tap an O, then 1 below.",
                glow = { s ->
                    if (o.stage(s) < 0) BoardHighlight(strong = sum.tilesOf(O))
                    else BoardHighlight(strong = setOf(CipherLogic.pad(1)), soft = sum.tilesOf(O))
                },
                steps = o.steps,
                retry = "Tap a glowing O, then the 1.",
                done = "Every O shows 1.",
            ),
            TutorialFrame(
                state = tutorialBoard(one, selected = O),
                caption = "The units column: O + O is 1 + 1, which makes 2, so T is 2. Tap a T, then 2.",
                glow = { s ->
                    if (t.stage(s) < 0) BoardHighlight(strong = sum.tilesOf(T), soft = sum.columnTiles(0))
                    else BoardHighlight(strong = setOf(CipherLogic.pad(2)), soft = sum.tilesOf(T))
                },
                steps = t.steps,
                retry = "Tap a glowing T, then the 2.",
                done = "T is 2.",
            ),
            TutorialFrame(
                state = tutorialBoard(two, selected = T),
                caption = "A digit that breaks a rule turns red. Give U the 1 that O already has: tap a U, then 1. " +
                    "Then tap 1 again to clear it.",
                glow = { s ->
                    when (clash.stage(s)) {
                        -1 -> BoardHighlight(strong = sum.tilesOf(U), soft = sum.tilesOf(O))
                        0 -> BoardHighlight(strong = setOf(CipherLogic.pad(1)), soft = sum.tilesOf(O))
                        else -> BoardHighlight(strong = sum.tilesOf(U) + CipherLogic.pad(1), soft = sum.tilesOf(O), warning = true)
                    }
                },
                steps = clash.steps,
                retry = "Tap a glowing U, then the 1, then the 1 again.",
                done = "Cleared. Tapping the digit again takes it out.",
            ),
            TutorialFrame(
                state = tutorialBoard(two, selected = U),
                caption = "Your turn: finish the sum. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved: 21 + 81 = 102.",
            ),
        )
    }

    // ---- drawing ---------------------------------------------------------------------------

    /** Where tile (row, col) sits in the sum's box: columns from the right, the total below the rule. */
    private fun tileOrigin(sum: CipherSum, row: Int, col: Int, slot: Float): Offset =
        Offset((sum.width - col) * slot, (row + if (row == sum.rows - 1) RULE else 0f) * slot)

    /** The sum: a column for the plus sign, then right-aligned tiles, a rule, and the total. */
    @Composable
    private fun SumTiles(
        sum: CipherSum,
        digits: List<Int>,
        selected: Int?,
        slot: Dp,
        ruleInk: Color,
        tileColour: (letter: Int) -> Color,
        textColour: (letter: Int, placed: Boolean) -> Color,
        modifier: Modifier = Modifier,
        tileModifier: @Composable (row: Int, col: Int, letter: Int) -> Modifier = { _, _, _ -> Modifier },
    ) {
        val scheme = MaterialTheme.colorScheme
        val tile = slot * TILE
        Box(modifier.size(slot * (sum.width + 1), slot * (sum.rows + RULE))) {
            Canvas(Modifier.fillMaxSize()) {
                val px = slot.toPx()
                val ink = ruleInk
                // Midway between the last addend's tiles and the total's, from the plus sign to the last tile's edge.
                val y = ((sum.rows - 2 + TILE) * px + (sum.rows - 1 + RULE) * px) / 2
                drawLine(ink, Offset(px * 0.1f, y), Offset(size.width - px * (1 - TILE), y), strokeWidth = px * 0.05f, cap = StrokeCap.Round)
                val cx = px * TILE / 2
                val cy = (sum.rows - 2 + TILE / 2) * px
                val arm = px * 0.17f
                drawLine(ink, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = px * 0.06f, cap = StrokeCap.Round)
                drawLine(ink, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = px * 0.06f, cap = StrokeCap.Round)
            }
            for (r in 0 until sum.rows) for (c in 0 until sum.words[r].length) {
                val l = sum.letterAt(r, c)
                val at = tileOrigin(sum, r, c, slot.value)
                val placed = digits[l] != EMPTY
                Box(
                    Modifier
                        .padding(start = at.x.dp, top = at.y.dp)
                        .size(tile)
                        .then(tileModifier(r, c, l))
                        .clip(RoundedCornerShape(minOf(8.dp, tile * 0.16f)))
                        .background(tileColour(l))
                        .then(if (selected == l) Modifier.border(2.5.dp, scheme.onBackground, RoundedCornerShape(8.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    val letter = sum.name(l)
                    if (placed) {
                        // The letter in the corner, too small to read on the home grid's motif.
                        if (tile >= 28.dp) Text(
                            letter,
                            Modifier.align(Alignment.TopStart).padding(start = tile * 0.08f, top = tile * 0.02f),
                            fontSize = (tile.value * 0.24f).sp,
                            color = textColour(l, false).copy(alpha = 0.75f),
                        )
                        Text(digits[l].toString(), fontSize = (tile.value * 0.56f).sp, fontWeight = FontWeight.Medium, color = textColour(l, true))
                    } else {
                        Text(letter, fontSize = (tile.value * 0.5f).sp, fontWeight = FontWeight.Bold, color = textColour(l, false))
                    }
                }
            }
        }
    }

    // ---- home-grid motif -------------------------------------------------------------------

    /** TO + GO = OUT with O and T found: a legal moment from the walkthrough's sum. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val sum = CipherSum(TUTORIAL_WORDS)
            val slot = minOf(maxWidth / (sum.width + 1), maxHeight / (sum.rows + RULE))
            val dark = BoardHues.isDark(scheme)
            val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
            SumTiles(
                sum, digits = listOf(EMPTY, 1, 2, EMPTY), selected = null, slot = slot, ruleInk = BoardHues.ink(hueA, dark),
                tileColour = { if (it == T) BoardHues.fill(hueB, dark) else scheme.surfaceVariant },
                textColour = { _, placed -> if (placed) BoardHues.ink(hueB, dark) else scheme.onSurface },
            )
        }
    }

    // ---- the board ---------------------------------------------------------------------------

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as CipherState
        val sum = s.sum
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.pair(s.solution.hashCode())
        val mark = BoardHues.mark(hueA)
        val ink = BoardHues.ink(hueB, dark)
        val (highlight, glow, pulse) = rememberHintPaint()

        // A broken rule shows after a pause and goes at once when mended; held here, not in the state (undo).
        val live = remember(s.digits) { CipherLogic.conflicts(sum, s.digits) }
        var shown by remember(s.words) { mutableStateOf(CipherLogic.Conflicts(emptySet(), null)) }
        LaunchedEffect(live) {
            val keep = CipherLogic.Conflicts(shown.letters intersect live.letters, shown.column.takeIf { it == live.column })
            shown = keep
            if (keep.letters == live.letters && keep.column == live.column) return@LaunchedEffect
            delay(SETTLE_MILLIS)
            shown = live
        }
        val redColumn = shown.column?.let { sum.columnTiles(it) } ?: emptySet()
        // A digit set on a letter settles on every tile of that letter. No units: a column is judged with its carry.
        val feel = rememberMoveFeel(s.digits)

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .boardKeys(interactive) { key, _, repeat ->
                    val action = cipherKeyAction(key) ?: return@boardKeys false
                    // A letter the sum lacks is left to the play screen, so H still asks for a hint there.
                    if (action is CipherKeyAction.Letter && action.letter !in sum.letters) return@boardKeys false
                    if (!repeat || action is CipherKeyAction.Move) s.applyKey(action)?.let(onState)
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            // Sized from both axes (CLAUDE.md): the sum leaves room for the letter keys and the digit pad.
            val room = if (constraints.hasBoundedHeight) (maxHeight - KEY_HEIGHT - PAD_HEIGHT - GAP * 2).coerceAtLeast(0.dp) else maxWidth
            val slot = minOf(maxWidth / (sum.width + 1), room / (sum.rows + RULE), MAX_SLOT)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SumTiles(
                    sum, s.digits, s.selected, slot, ruleInk = BoardHues.ink(hueA, dark),
                    modifier = Modifier.reportHighlight { size, h ->
                        val px = size.width / (sum.width + 1f)
                        var out: Rect? = null
                        for (r in 0 until sum.rows) for (c in 0 until sum.words[r].length) {
                            if (sum.tile(r, c) !in h.strong && sum.tile(r, c) !in h.soft) continue
                            val o = tileOrigin(sum, r, c, px)
                            val rect = Rect(o, Size(px * TILE, px * TILE))
                            out = out?.let { Rect(minOf(it.left, rect.left), minOf(it.top, rect.top), maxOf(it.right, rect.right), maxOf(it.bottom, rect.bottom)) } ?: rect
                        }
                        out
                    },
                    tileColour = { l -> if (s.selected == l) mark.copy(alpha = if (dark) 0.55f else 0.4f) else scheme.surfaceVariant },
                    textColour = { l, placed ->
                        when {
                            l in shown.letters -> scheme.error
                            // On the selection's tint the player's hue would be faint.
                            placed && s.selected == l -> scheme.onSurface
                            placed -> ink
                            else -> scheme.onSurface
                        }
                    },
                    tileModifier = { r, c, l ->
                        val look = highlight.look(sum.tile(r, c))
                        Modifier
                            .moveFeel(feel, l, Color(accent))
                            .dimmed(look)
                            .ring(look, glow, pulse, corner = 8.dp)
                            .then(if (sum.tile(r, c) in redColumn) Modifier.border(2.dp, scheme.error, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable(enabled = interactive) { onState(s.select(l)) }
                    },
                )

                Spacer(Modifier.height(GAP))

                // The key: every letter with its digit, also a way to pick a letter.
                Row(Modifier.fillMaxWidth().keepClear(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sum.letters.indices.forEach { l ->
                        val look = highlight.look(CipherLogic.key(l), dims = false)
                        Column(
                            Modifier
                                .weight(1f)
                                .height(KEY_HEIGHT)
                                .highlightAnchor(CipherLogic.key(l))
                                .ring(look, glow, pulse, corner = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (s.selected == l) mark.copy(alpha = if (dark) 0.55f else 0.4f) else scheme.surface)
                                .clickable(enabled = interactive) { onState(s.select(l)) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(sum.name(l), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            Text(
                                if (s.digits[l] == EMPTY) "·" else s.digits[l].toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (l in shown.letters) scheme.error else if (s.digits[l] == EMPTY) scheme.outline else ink,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(GAP))

                Row(Modifier.fillMaxWidth().keepClear(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..9).forEach { digit ->
                        val at = s.selected
                        val owner = s.digits.indices.firstOrNull { s.digits[it] == digit && it != at }
                        PadKey(
                            CipherLogic.pad(digit), highlight, glow, pulse,
                            fill = if (owner != null) scheme.surfaceVariant else scheme.surface,
                            enabled = interactive && at != null,
                            onClick = {
                                val next = s.enter(at ?: return@PadKey, digit)
                                if (next !== s) onState(next)
                            },
                            corner = 8.dp,
                        ) {
                            Text(digit.toString(), style = MaterialTheme.typography.titleMedium, color = if (owner != null) scheme.outline else scheme.onSurface)
                            if (owner != null) {
                                Text(
                                    sum.name(owner),
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                                    fontSize = 10.sp,
                                    color = scheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
