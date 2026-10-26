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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.ui.MoveFeel
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberSettledFlags
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.theme.BoardHues

/**
 * Who's Who: a few people, a row of houses and one or two more categories (a pet, a job...), and a numbered list of
 * clues that fix exactly one way to match them all up. Spec: `docs/WHOSWHO_SPEC.md`.
 *
 * Every board is dealt only if the clues give one answer (an exhaustive count) and the hint ladder finishes it, with
 * every clue needed ([WhosWhoLogic.attempt]), so the hints never run out ([WhosWhoTeacher]).
 *
 * Tiers: four people with the house and one more category, every clue naming someone (Standard); two more categories
 * and clues that link things or place houses (Hard, which must need them); five people and either-or clues, needing a
 * what-if (Expert).
 */
object WhosWho : PuzzleType {

    override val id = "whoswho"
    override val displayName = "Who's Who"
    override val tagline = "Match everyone to everything from the clues"
    override val accent = 0xFF4457A2

    override val rules = listOf(
        "Each row is a person and each column a category: their house, and one or two more. No two people share anything.",
        "The houses stand in a row, numbered from the left. Next to means one house either side; somewhere left of means a lower number.",
        "Tap a square, then an item under the board. With the tick chosen it is theirs; with the cross chosen it is not.",
        "A tick rules that item out of the rest of its column, and the square shows what is still possible.",
        "Tap a clue to strike it through once you have used it. It is only a note.",
        "Solved when every square has its tick and every clue is true. Every board has one answer, found without guessing.",
    )

    override val keyboardHelp = listOf(
        "Arrows move between squares, 1-5 tick that item (Shift+1-5 crosses it), Backspace clears the square.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = WhosWhoLogic.generate(seed, difficulty)

    /** The deepest step of the hint walk from a blank board; null for the direct steps alone. */
    override fun neededTechnique(state: PuzzleState): String? =
        WhosWhoLogic.deepest(WhosWhoLogic.puzzleOf(state as WhosWhoState))?.takeIf { it > WhosWhoLogic.ONLY }?.let { WhosWhoTeacher.name(it) }

    // ---- the walkthrough ------------------------------------------------------------------------

    /**
     * Three people, the house and a pet; one answer: Ann in 2 with the dog, Ben in 1 with the fish, Cara in 3 with
     * the cat.
     */
    internal val TUTORIAL_CLUES = listOf(
        WhoClue(WhosWhoLogic.SAME, 0, 4), // Ann lives in house 2.
        WhoClue(WhosWhoLogic.NOT, 1, 7), // Ben does not have the dog.
        WhoClue(WhosWhoLogic.SAME, 6, 5), // The cat owner lives in house 3.
        WhoClue(WhosWhoLogic.NOT, 1, 5), // Ben does not live in house 3.
    )

    internal fun tutorialBoard() = WhosWhoState(
        names = listOf("Ann", "Ben", "Cara"),
        groups = listOf(WhosWhoWords.HOUSE, WhosWhoWords.PET),
        items = listOf(listOf(0, 1, 2), listOf(0, 1, 2)),
        clues = TUTORIAL_CLUES,
        answer = listOf(listOf(1, 0, 2), listOf(1, 2, 0)),
    )

    /** Accepts exactly [want]'s marks and strikes and nothing else (Nonogram's rule). */
    private fun only(want: WhosWhoState): (PuzzleState) -> Boolean = { next ->
        next is WhosWhoState && next.marks == want.marks && (0 until next.clues.size).all { next.isStruck(it) == want.isStruck(it) }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val empty = tutorialBoard()
        val solved = empty.mark(0, 0, 1, true).mark(1, 0, 0, true).mark(2, 0, 2, true)
            .mark(0, 1, 1, true).mark(1, 1, 2, true).mark(2, 1, 0, true).copy(moves = 0)
        val ann = empty.mark(0, 0, 1, true)
        val struck = ann.strike(0)
        val noDog = struck.mark(1, 1, 1, false)
        val noThree = noDog.mark(1, 0, 2, false)
        val benOne = noThree.mark(1, 0, 0, true)
        val slip = benOne.mark(2, 1, 1, true)
        val cell = { p: Int, c: Int -> p * 2 + c }
        val clue = { k: Int -> WhosWhoTeacher.CLUE_BASE + k }
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Each row is a person, each column a category. The clues fix one way to fill every square: " +
                    "Ann lives in house 2 and has the dog.",
                highlight = BoardHighlight(strong = setOf(cell(0, 0), cell(0, 1))),
            ),
            TutorialFrame(
                state = empty,
                caption = "Clue 1 says Ann lives in house 2. Tap Ann's House square, then 2 under the board.",
                highlight = BoardHighlight(strong = setOf(cell(0, 0)), soft = setOf(clue(0))),
                accepts = only(ann),
                retry = "Tap the glowing square, then 2 with the tick chosen.",
                done = "Ticked. Nobody else can live in house 2 now.",
            ),
            TutorialFrame(
                state = ann,
                caption = "Clue 1 is used up. Tap it to strike it through.",
                highlight = BoardHighlight(strong = setOf(clue(0))),
                accepts = only(struck),
                retry = "Tap the glowing clue.",
                done = "Struck through: only a note, to keep your place.",
            ),
            TutorialFrame(
                state = struck,
                caption = "Clue 2: Ben does not have the dog. Choose the cross under the board, then tap Ben's Pet square and the dog.",
                highlight = BoardHighlight(strong = setOf(cell(1, 1)), soft = setOf(clue(1))),
                accepts = only(noDog),
                retry = "Choose the cross, then tap the glowing square and the dog.",
                done = "Crossed off. Ben's square no longer offers the dog.",
            ),
            TutorialFrame(
                state = noDog,
                caption = "Clue 4: Ben does not live in house 3. Cross 3 off in Ben's House square.",
                highlight = BoardHighlight(strong = setOf(cell(1, 0)), soft = setOf(clue(3))),
                accepts = only(noThree),
                retry = "Tap the glowing square, then 3 with the cross chosen.",
                done = "Crossed off. House 2 is Ann's, so Ben has only house 1 left.",
            ),
            TutorialFrame(
                state = noThree,
                caption = "Only 1 is left in Ben's House square. Choose the tick and tick it.",
                highlight = BoardHighlight(strong = setOf(cell(1, 0))),
                accepts = only(benOne),
                retry = "Choose the tick, then tap the glowing square and 1.",
                done = "Ticked. That leaves Cara only house 3.",
            ),
            TutorialFrame(
                state = benOne,
                caption = "See what a wrong tick does: tick the dog for Cara.",
                highlight = BoardHighlight(strong = setOf(cell(2, 1))),
                accepts = only(slip),
                retry = "Tap Cara's glowing Pet square, then the dog, with the tick chosen.",
                done = "Clue 3 turns red: the cat owner must live in house 3, and only Cara can.",
            ),
            TutorialFrame(
                state = slip,
                caption = "So Cara has the cat, not the dog. Tap the dog again to take the tick off.",
                highlight = BoardHighlight(strong = setOf(cell(2, 1)), soft = setOf(clue(2))),
                accepts = only(benOne),
                retry = "Tap Cara's glowing Pet square, then the dog.",
                done = "Taken back. Every board has one answer, so every clue can be kept.",
            ),
            TutorialFrame(
                state = benOne,
                caption = "Your turn: fill in the rest. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every board works out one clue at a time.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as WhosWhoState
        val step = WhosWhoTeacher.teach(s) ?: return null
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == WhosWhoTeacher.MISTAKE,
            applyTo = { now -> applyStep(now as WhosWhoState, step) },
            reachedBy = { now -> reached(now as WhosWhoState, step) },
        )
    }

    /** Whether [s] already shows [step]: its ticks made, what it crosses ruled out, a wrong mark taken off. */
    internal fun reached(s: WhosWhoState, step: WhosWhoTeacher.Lesson): Boolean {
        val grid = WhosWhoLogic.gridOf(s.n, s.columns, s.marks)
        return step.ticks.all { s.marks[it] == WhosWhoLogic.TICK } &&
            step.crosses.all { grid.cand[it / s.n] shr (it % s.n) and 1 == 0 } &&
            step.clears.all { s.marks[it] == WhosWhoLogic.EMPTY }
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    internal fun applyStep(s: WhosWhoState, step: WhosWhoTeacher.Lesson): WhosWhoState {
        val next = s.marks.toCharArray()
        step.clears.forEach { next[it] = WhosWhoLogic.EMPTY }
        step.crosses.forEach { if (next[it] == WhosWhoLogic.EMPTY) next[it] = WhosWhoLogic.CROSS }
        for (t in step.ticks) {
            val cell = t / s.n
            for (j in 0 until s.n) if (next[cell * s.n + j] == WhosWhoLogic.TICK) next[cell * s.n + j] = WhosWhoLogic.EMPTY
            next[t] = WhosWhoLogic.TICK
        }
        val marks = next.concatToString()
        return if (marks == s.marks) s else s.copy(marks = marks, moves = s.moves + 1)
    }

    // ---- what the board shows -------------------------------------------------------------------

    /** Cells that cannot be right whatever else happens: nothing left, or a tick another row of the column shares. */
    internal fun badCells(s: WhosWhoState, grid: WhosWhoLogic.Grid): BooleanArray {
        val bad = BooleanArray(s.n * s.columns) { grid.cand[it] == 0 }
        for (column in 0 until s.columns) for (slot in 0 until s.n) {
            val holders = (0 until s.n).filter { s.marks[s.markIndex(it, column, slot)] == WhosWhoLogic.TICK }
            if (holders.size > 1) holders.forEach { bad[it * s.columns + column] = true }
        }
        return bad
    }

    /** Clues no choice the marks still allow can keep. */
    internal fun badClues(s: WhosWhoState, grid: WhosWhoLogic.Grid): BooleanArray {
        val puzzle = WhosWhoLogic.puzzleOf(s)
        return BooleanArray(s.clues.size) { !WhosWhoLogic.possible(puzzle, grid, s.clues[it]) }
    }

    // ---- drawing --------------------------------------------------------------------------------

    private val HEADER = 22.dp
    private val PAD = 50.dp
    private val MIN_CLUES = 118.dp
    private val NAME_W = 50.dp

    /** The clue list's text size, and the line height the minimum strip is sized for. */
    private val CLUE_SP = 13.5.sp

    private class Look(
        val scheme: ColorScheme,
        val hueA: Float,
        val hueB: Float,
        val dark: Boolean,
        val highlight: BoardHighlight,
        val glow: Color,
        val pulse: State<Float>,
    ) {
        fun named(i: Int) = i in highlight.strong || i in highlight.soft
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as WhosWhoState
        val scheme = MaterialTheme.colorScheme
        // The selection and the pen belong to the screen, not the board: the play screen pushes an undo entry for every
        // state it is handed.
        var selected by remember(s.names, s.clues) { mutableStateOf<Int?>(null) }
        var tick by rememberSaveable { mutableStateOf(true) }
        val highlight = LocalBoardHighlight.current
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())
        val (hueA, hueB) = BoardHues.pair(s.answer.hashCode())
        val look = Look(scheme, hueA, hueB, BoardHues.isDark(scheme), highlight, if (highlight.warning) scheme.error else scheme.onBackground, pulse)

        val grid = WhosWhoLogic.gridOf(s.n, s.columns, s.marks)
        val cellsNow = badCells(s, grid)
        val cluesNow = badClues(s, grid)
        val badCell = rememberSettledFlags(cellsNow, s.clues, s.marks)
        val badClue = rememberSettledFlags(cluesNow, s.clues, s.marks)
        val feel = rememberMoveFeel(s.marks.chunked(s.n))

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .boardKeys(interactive, shift = true) { key, shift, repeat ->
                    val action = whosWhoKeyAction(key, shift) ?: return@boardKeys false
                    if (!repeat || action is WhosWhoKeyAction.Move) {
                        val result = s.applyKey(selected, action)
                        selected = result.cursor
                        if (result.board !== s) onState(result.board)
                    }
                    true
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
                val wide = maxWidth > maxHeight * 1.3f && maxWidth >= 520.dp
                val onCell = { c: Int -> selected = c }
                val onPad = { slot: Int ->
                    val c = selected
                    if (c != null) onState(s.mark(c / s.columns, c % s.columns, slot, tick))
                }
                val onClue = { k: Int -> onState(s.strike(k)) }
                if (wide) {
                    val rowH = ((maxHeight - HEADER - PAD - 12.dp) / s.n).coerceIn(30.dp, 52.dp)
                    Row(Modifier.widthIn(max = 1000.dp).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(0.55f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            Table(s, grid, rowH, selected, badCell, look, feel, interactive, onCell)
                            Spacer(Modifier.height(12.dp))
                            Pad(s, grid, selected, tick, interactive, look, { tick = it }, onPad)
                        }
                        Clues(s, badClue, look, interactive, onClue, Modifier.weight(0.45f).fillMaxHeight())
                    }
                } else {
                    val rowH = ((maxHeight - HEADER - PAD - MIN_CLUES - 12.dp) / s.n).coerceIn(30.dp, 48.dp)
                    Column(Modifier.widthIn(max = 560.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Table(s, grid, rowH, selected, badCell, look, feel, interactive, onCell)
                        Clues(s, badClue, look, interactive, onClue, Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp))
                        Pad(s, grid, selected, tick, interactive, look, { tick = it }, onPad)
                    }
                }
            }
        }
    }

    /** The column widths: the names, then the house (narrow: one digit each), then the rest. */
    private fun weights(s: WhosWhoState) = List(s.columns) { if (it == 0) 0.7f else 1f }

    @Composable
    private fun Table(
        s: WhosWhoState,
        grid: WhosWhoLogic.Grid,
        rowH: Dp,
        selected: Int?,
        bad: BooleanArray,
        look: Look,
        feel: MoveFeel,
        interactive: Boolean,
        onCell: (Int) -> Unit,
    ) {
        val scheme = look.scheme
        val weights = weights(s)
        val line = scheme.outline
        Column(Modifier.fillMaxWidth().border(1.5.dp, scheme.onSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) {
            Row(Modifier.fillMaxWidth().height(HEADER).background(BoardHues.fill(look.hueA, look.dark).copy(alpha = if (look.dark) 0.55f else 0.45f))) {
                Spacer(Modifier.width(NAME_W))
                for (column in 0 until s.columns) {
                    Box(Modifier.weight(weights[column]).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            WhosWhoWords.HEADERS[s.groups[column]],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
            for (p in 0 until s.n) {
                Row(Modifier.fillMaxWidth().height(rowH).background(scheme.surface)) {
                    Box(Modifier.width(NAME_W).fillMaxHeight().border(0.5.dp, line), contentAlignment = Alignment.CenterStart) {
                        Text(
                            s.names[p],
                            Modifier.padding(start = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                    }
                    for (column in 0 until s.columns) {
                        val c = p * s.columns + column
                        Cell(s, grid, c, selected == c, bad[c], look, feel, interactive, Modifier.weight(weights[column]).fillMaxHeight(), onCell)
                    }
                }
            }
        }
    }

    @Composable
    private fun Cell(
        s: WhosWhoState,
        grid: WhosWhoLogic.Grid,
        c: Int,
        isSelected: Boolean,
        isBad: Boolean,
        look: Look,
        feel: MoveFeel,
        interactive: Boolean,
        modifier: Modifier,
        onCell: (Int) -> Unit,
    ) {
        val scheme = look.scheme
        val column = c % s.columns
        val placed = grid.placed[c]
        val open = (0 until s.n).filter { grid.cand[c] shr it and 1 == 1 }
        val hint = look.highlight.look(c)
        val accent = Color(accent)
        Box(
            modifier
                .highlightAnchor(c)
                .background(
                    when {
                        isBad -> scheme.error.copy(alpha = 0.22f)
                        isSelected -> accent.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    },
                )
                .border(0.5.dp, scheme.outline)
                .then(if (isSelected) Modifier.border(2.dp, accent) else Modifier)
                .ring(hint, look.glow, look.pulse, 0.dp)
                .moveFeel(feel, c, accent)
                .clickable(enabled = interactive) { onCell(c) }
                .dimmed(hint),
            contentAlignment = Alignment.Center,
        ) {
            if (placed >= 0) {
                Text(
                    s.word(column, placed),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBad) scheme.error else BoardHues.ink(look.hueB, look.dark),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            } else {
                Text(
                    open.joinToString(" ") { s.word(column, it) },
                    Modifier.padding(horizontal = 3.dp),
                    fontSize = if (column == 0) 12.sp else 10.5.sp,
                    lineHeight = 12.sp,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }

    @Composable
    private fun Clues(s: WhosWhoState, bad: BooleanArray, look: Look, interactive: Boolean, onClue: (Int) -> Unit, modifier: Modifier) {
        val scheme = look.scheme
        val list = rememberLazyListState()
        val hinted = s.clues.indices.firstOrNull { look.named(WhosWhoTeacher.CLUE_BASE + it) }
        LaunchedEffect(hinted) { if (hinted != null) list.animateScrollToItem(hinted) }
        LazyColumn(modifier, state = list) {
            itemsIndexed(s.clues) { k, _ ->
                val index = WhosWhoTeacher.CLUE_BASE + k
                val struck = s.isStruck(k)
                val hint = look.highlight.look(index)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .highlightAnchor(index)
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .ring(hint, look.glow, look.pulse, 8.dp)
                        .background(if (bad[k]) scheme.error.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(enabled = interactive) { onClue(k) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .dimmed(hint),
                ) {
                    Text(
                        "${k + 1}",
                        Modifier.width(22.dp),
                        fontSize = CLUE_SP,
                        fontWeight = FontWeight.Bold,
                        color = if (bad[k]) scheme.error else BoardHues.ink(look.hueA, look.dark),
                    )
                    Text(
                        WhosWhoLogic.clueText(s, k),
                        fontSize = CLUE_SP,
                        lineHeight = 18.sp,
                        color = when {
                            bad[k] -> scheme.error
                            struck -> scheme.onSurface.copy(alpha = 0.38f)
                            else -> scheme.onSurface
                        },
                        textDecoration = if (struck) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }

    /** The pen (tick or cross) and the selected square's items. Kept clear of the hint popover. */
    @Composable
    private fun Pad(
        s: WhosWhoState,
        grid: WhosWhoLogic.Grid,
        selected: Int?,
        tick: Boolean,
        interactive: Boolean,
        look: Look,
        onPen: (Boolean) -> Unit,
        onSlot: (Int) -> Unit,
    ) {
        val scheme = look.scheme
        val accent = Color(accent)
        Row(
            Modifier.fillMaxWidth().height(PAD).keepClear(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (pen in listOf(true, false)) {
                val chosen = tick == pen
                Box(
                    Modifier
                        .size(42.dp, 46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (chosen) accent.copy(alpha = 0.22f) else scheme.surface)
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) accent else scheme.outline, RoundedCornerShape(12.dp))
                        .clickable(enabled = interactive) { onPen(pen) },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(18.dp)) { if (pen) drawTick(scheme.onSurface, 1f) else drawCross(scheme.onSurface, 1f) }
                }
            }
            Spacer(Modifier.width(4.dp))
            if (selected == null) {
                Text(
                    "Tap a square in the table",
                    Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                val column = selected % s.columns
                for (slot in 0 until s.n) {
                    val mark = s.marks[selected * s.n + slot]
                    val possible = grid.cand[selected] shr slot and 1 == 1
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (mark == WhosWhoLogic.TICK) BoardHues.fill(look.hueB, look.dark) else scheme.surface)
                            .border(1.dp, scheme.outline, RoundedCornerShape(10.dp))
                            .clickable(enabled = interactive) { onSlot(slot) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.word(column, slot),
                            fontSize = if (column == 0) 17.sp else 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mark == WhosWhoLogic.TICK) BoardHues.onFill(BoardHues.fill(look.hueB, look.dark)) else scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textDecoration = if (mark == WhosWhoLogic.CROSS) TextDecoration.LineThrough else null,
                            modifier = Modifier.alpha(if (possible) 1f else 0.35f),
                        )
                    }
                }
            }
        }
    }

    /** A tick in the square of side [w] at [at]. */
    private fun DrawScope.drawTick(colour: Color, alpha: Float, at: Offset = Offset.Zero, w: Float = size.width) {
        val stroke = maxOf(w * 0.16f, 1.5f)
        val p = { x: Float, y: Float -> Offset(at.x + w * x, at.y + w * y) }
        drawLine(colour.copy(alpha = alpha), p(0.12f, 0.55f), p(0.4f, 0.82f), stroke, StrokeCap.Round)
        drawLine(colour.copy(alpha = alpha), p(0.4f, 0.82f), p(0.9f, 0.2f), stroke, StrokeCap.Round)
    }

    /** A cross in the square of side [w] at [at]. */
    private fun DrawScope.drawCross(colour: Color, alpha: Float, at: Offset = Offset.Zero, w: Float = size.width) {
        val stroke = maxOf(w * 0.16f, 1.5f)
        val p = { x: Float, y: Float -> Offset(at.x + w * x, at.y + w * y) }
        drawLine(colour.copy(alpha = alpha), p(0.18f, 0.18f), p(0.82f, 0.82f), stroke, StrokeCap.Round)
        drawLine(colour.copy(alpha = alpha), p(0.82f, 0.18f), p(0.18f, 0.82f), stroke, StrokeCap.Round)
    }

    // ---- the home motif -------------------------------------------------------------------------

    /**
     * A fixed little logic grid for the home card: four people against four items, one tick in every row and column,
     * some crosses, the header band in the board's first hue. A legal crop of a board. Never calls `generate()`.
     */
    private val MOTIF_TICKS = intArrayOf(2, 0, 3, 1)
    private val MOTIF_CROSSES = setOf(0, 1, 5, 7, 8, 9, 14)

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(Modifier.size(side)) {
                val u = size.width / 5f
                drawRect(scheme.surface, Offset(u, u), Size(4 * u, 4 * u))
                drawRoundRect(BoardHues.fill(hueA, dark), Offset(u, u * 0.15f), Size(4 * u, u * 0.7f), CornerRadius(u * 0.15f))
                drawRoundRect(BoardHues.fill(hueA, dark), Offset(u * 0.15f, u), Size(u * 0.7f, 4 * u), CornerRadius(u * 0.15f))
                for (k in 0..4) {
                    val w = if (k == 0 || k == 4) u * 0.06f else u * 0.03f
                    drawLine(scheme.outline, Offset(u + k * u, u), Offset(u + k * u, 5 * u), w)
                    drawLine(scheme.outline, Offset(u, u + k * u), Offset(5 * u, u + k * u), w)
                }
                for (r in 0 until 4) for (c in 0 until 4) {
                    val at = Offset(u + c * u + u * 0.2f, u + r * u + u * 0.2f)
                    if (MOTIF_TICKS[r] == c) {
                        drawTick(BoardHues.ink(hueB, dark), 1f, at, u * 0.6f)
                    } else if (r * 4 + c in MOTIF_CROSSES) {
                        drawCross(scheme.onSurfaceVariant, 0.7f, at, u * 0.6f)
                    }
                }
            }
        }
    }
}
