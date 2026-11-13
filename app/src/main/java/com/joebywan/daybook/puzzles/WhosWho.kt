package com.joebywan.daybook.puzzles

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.ui.CappedFontScale
import com.joebywan.daybook.ui.LocalReducedMotion
import com.joebywan.daybook.ui.boardSp
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.rememberSettledFlags
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.arcadeLift
import com.joebywan.daybook.ui.theme.classicLook
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Who's Who, drawn as a graph (the owner, 2026-11-13: it replaced the table, which began as the lab puzzle `test-whoswho`).
 * The boards are [WhosWhoLogic.generate]'s, wrapped in a [WhosWhoGraphState]. Every item is a node in a column of its
 * category; tap one, then items of another category, to draw where it can be (one line: that one; several: one of those);
 * a line tapped again becomes an anti-link, then open. What the marks imply is drawn faint ([WhosWhoGraphLogic.close]).
 * "Group" lines linked items up in their person's row, so a solved board settles into the answer table; the category
 * buttons (drawn icons, [WhosWhoIcons]) re-sort the same nodes into a table with that category as the rows.
 *
 * A save from the table build holds a [WhosWhoState]: [restored] turns its ticks and crosses into links and anti-links.
 *
 * Highlight indices: item `ref` (`category * n + slot`), clue `k` [WhosWhoTeacher.CLUE_BASE] `+ k`.
 */
object WhosWho : PuzzleType {

    /** Hashed into every seed: never rename it. */
    override val id = "whoswho"
    override val displayName = "Who's Who"
    override val tagline = "Match everyone to everything from the clues"
    override val accent = 0xFF846CAD

    override val rules = listOf(
        "Each person has one item from every category, and no two people share one. The clues fix who has what.",
        "Every person and every item is a pill, one column per category. Tap one, then pills in another column to draw where it can be: one line means that one, several mean one of those.",
        "Tap a line again to rule the pair out (never the same person's), and again to clear it. Tap a clue to strike it through: only a note.",
        "What your marks imply is drawn faint: links join up, an anti-link holds across whole groups, and a category with one item left links to it.",
        "With a pill chosen you see all its pairs: thick linked, thin one of its choices, dashed ruled out, dotted still open.",
        "Group lines linked items up in their person's row. The category buttons show the marks as a table, any category as the rows.",
        "Solved when every person is linked to one of everything and every clue is true.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        WhosWhoGraphState(WhosWhoLogic.generate(seed, difficulty))

    /** The deepest step of the hint walk from a blank board; null for the direct steps alone. */
    override fun neededTechnique(state: PuzzleState): String? =
        WhosWhoLogic.deepest(WhosWhoLogic.puzzleOf((state as WhosWhoGraphState).board))?.takeIf { it > WhosWhoLogic.ONLY }?.let { WhosWhoTeacher.name(it) }

    /** A save from the table build ([WhosWhoState]) as the graph: every tick a line from both ends, every cross an anti-link. */
    override fun restored(state: PuzzleState): PuzzleState = if (state is WhosWhoState) WhosWhoGraphState.fromTable(state) else state

    /**
     * Said on the finished frame when the board was won with some person's link implied, not drawn: the player's
     * anti-links left the answer standing on its own. Warm, plain, varied; one drawn per solve.
     */
    val ELIMINATION_NOTES = listOf(
        "Your fantastic deduction eliminated all other options!",
        "Sharp work: every other option ruled out, only the answer left standing.",
        "Elimination at its finest: nothing else fits.",
        "Beautifully reasoned: you struck out everything that couldn't be, and only the answer remained.",
        "Clever thinking: you ruled out the rest, so the answer had nowhere else to go.",
        "A fine piece of logic. Every wrong pairing struck off, and the right ones stood alone.",
        "Lovely work: by ruling out what couldn't be, you left only what must be.",
        "Impressive. You never had to draw the answer; your eliminations said it all.",
        "Wonderfully done: once everything else was ruled out, the truth was plain.",
        "Excellent reasoning. Nothing left but the answer, and you got there by elimination.",
    )

    /** One of [ELIMINATION_NOTES] when [state] was solved with a person's link left implied; null when every one was drawn. */
    override fun finishNote(state: PuzzleState): String? = finishNote(state as WhosWhoGraphState, Random.Default)

    /** [finishNote] drawing from [random] (never the board's `Rng`: it is the frame's, not the board's). */
    internal fun finishNote(s: WhosWhoGraphState, random: Random): String? =
        if (s.solved && WhosWhoGraphLogic.leftImplied(s)) ELIMINATION_NOTES[random.nextInt(ELIMINATION_NOTES.size)] else null

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

    /** The walkthrough's board. Items: the people 0-2, houses 1-3 are 3-5, the cat, dog and fish 6-8. */
    internal fun tutorialBoard() = WhosWhoGraphState(
        WhosWhoState(
            names = listOf("Ann", "Ben", "Cara"),
            groups = listOf(WhosWhoWords.HOUSE, WhosWhoWords.PET),
            items = listOf(listOf(0, 1, 2), listOf(0, 1, 2)),
            clues = TUTORIAL_CLUES,
            answer = listOf(listOf(1, 0, 2), listOf(1, 2, 0)),
        ),
    )

    /** Every line as (the item that drew it, the other end), and every anti-link: what a frame compares. */
    private fun marksOf(s: WhosWhoGraphState): Pair<Set<Pair<Int, Int>>, Set<Pair<Int, Int>>> {
        val lines = HashSet<Pair<Int, Int>>()
        val anti = HashSet<Pair<Int, Int>>()
        for (a in 0 until s.size) for (b in a + 1 until s.size) {
            if (s.drew(a, b)) lines += a to b
            if (s.drew(b, a)) lines += b to a
            if (s.mark(a, b) == WhosWhoGraphLogic.ANTI) anti += a to b
        }
        return lines to anti
    }

    /** Exactly [want]'s lines, anti-links and struck clues, nothing else. */
    private fun only(want: WhosWhoGraphState): (PuzzleState) -> Boolean = { next ->
        next is WhosWhoGraphState && marksOf(next) == marksOf(want) && next.board.struck == want.board.struck
    }

    /** From [from], the pair [a]-[b] ruled out: its line drawn first (from either end), then the second tap. */
    private fun lineThenAnti(from: WhosWhoGraphState, a: Int, b: Int) = TutorialPath(
        { next -> only(from.withLine(a, b))(next) || only(from.withLine(b, a))(next) },
        only(from.withMark(a, b, WhosWhoGraphLogic.ANTI)),
    ).steps

    override val tutorial: List<TutorialFrame> by lazy {
        val empty = tutorialBoard()
        val ann = 0
        val ben = 1
        val house = { k: Int -> 2 + k }
        val cat = 6
        val dog = 7
        val fish = 8
        val clue = { k: Int -> WhosWhoTeacher.CLUE_BASE + k }
        // Ann's two lines alone: a solved board would turn to the people's table and show no lines at all.
        val annLines = empty.withLine(ann, house(2)).withLine(ann, dog).copy(moves = 0)
        val annTwo = empty.withLine(ann, house(2))
        val struck = annTwo.strike(0)
        val benFish = struck.withLine(ben, fish)
        val benPets = benFish.withLine(ben, cat)
        val notThree = benPets.withMark(ben, house(3), WhosWhoGraphLogic.ANTI)
        // Not the cat for Cara: its line on the way would solve the board, which turns it to the table mid-move.
        val slip = notThree.withMark(ann, dog, WhosWhoGraphLogic.ANTI)
        listOf(
            TutorialFrame(
                state = annLines,
                caption = "Every person and every item is a pill, one column each. A line joins what goes together: " +
                    "Ann lives in house 2 and has the dog.",
                highlight = BoardHighlight(strong = setOf(ann, house(2), dog)),
            ),
            TutorialFrame(
                state = empty,
                caption = "Clue 1 says Ann lives in house 2. Tap Ann, then house 2.",
                highlight = BoardHighlight(strong = setOf(ann, house(2)), soft = setOf(clue(0))),
                // From house 2 to Ann says the same: house 2 is Ann's.
                accepts = { next -> only(annTwo)(next) || only(empty.withLine(house(2), ann))(next) },
                retry = "Tap the glowing Ann, then the glowing house 2.",
                done = "Linked. One line means that one, so the other houses aren't Ann's.",
            ),
            TutorialFrame(
                state = annTwo,
                caption = "Clue 1 is used up. Tap it to strike it through.",
                highlight = BoardHighlight(strong = setOf(clue(0))),
                accepts = only(struck),
                retry = "Tap the glowing clue.",
                done = "Struck through: only a note, to keep your place.",
            ),
            TutorialFrame(
                state = struck,
                caption = "Clue 2: Ben does not have the dog, so he has the cat or the fish. Tap Ben, then the cat and the fish.",
                highlight = BoardHighlight(strong = setOf(ben, cat, fish), soft = setOf(clue(1))),
                steps = TutorialPath(
                    { next -> next is WhosWhoGraphState && (only(benFish)(next) || only(struck.withLine(ben, cat))(next)) },
                    only(benPets),
                ).steps,
                retry = "Tap the glowing Ben, then the cat and the fish.",
                done = "Two lines mean one of those: the dog is ruled out for Ben.",
            ),
            TutorialFrame(
                state = benPets,
                caption = "Clue 4: Ben does not live in house 3. Tap Ben, then house 3 twice: a line, then a dashed line that rules the pair out.",
                highlight = BoardHighlight(strong = setOf(ben, house(3)), soft = setOf(clue(3))),
                steps = lineThenAnti(benPets, ben, house(3)),
                retry = "Tap the glowing Ben, then house 3 twice.",
                done = "Ruled out. House 2 is Ann's, so Ben has only house 1 left: the faint line is what your marks imply.",
            ),
            TutorialFrame(
                state = notThree,
                caption = "See what a wrong mark does: rule out the dog for Ann. Tap Ann, then the dog twice.",
                highlight = BoardHighlight(strong = setOf(ann, dog)),
                steps = lineThenAnti(notThree, ann, dog),
                retry = "Tap the glowing Ann, then the dog twice.",
                done = "Clue 3 turns red: that leaves Cara the dog, but the cat's owner lives in house 3, which is hers.",
            ),
            TutorialFrame(
                state = slip,
                caption = "So Ann has the dog. Tap Ann, then the dog, to clear the dashed line.",
                highlight = BoardHighlight(strong = setOf(ann, dog), soft = setOf(clue(2))),
                accepts = only(notThree),
                retry = "Tap the glowing Ann, then the dog.",
                done = "Cleared. Every board has one answer, so every clue can be kept.",
            ),
            TutorialFrame(
                state = notThree,
                caption = "Your turn: link everyone to everything. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. Every board works out one clue at a time.",
            ),
        )
    }

    // ---- teaching -------------------------------------------------------------------------------

    /**
     * A mark of the player's that the one answer disagrees with first; else Who's Who's own teacher, asked of the table
     * the marks imply, its cells read as their person and its ticks and crosses made as links and anti-links.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as WhosWhoGraphState
        if (s.solved) return null
        mistake(s)?.let { return it }
        val step = WhosWhoTeacher.teach(WhosWhoGraphLogic.table(s)) ?: return null
        if (step.technique == WhosWhoTeacher.MISTAKE) return null
        return deduction(s, step)
    }

    /** A teacher's step on [s]'s table as a [Deduction] on the graph (Training builds its practices' steps with it too). */
    internal fun deduction(s: WhosWhoGraphState, step: WhosWhoTeacher.Lesson): Deduction {
        val b = s.board
        fun item(mark: Int): Pair<Int, Int> {
            val cell = mark / b.n
            return cell / b.columns to (cell % b.columns + 1) * b.n + mark % b.n
        }
        fun nodes(indices: Set<Int>) = indices.mapTo(LinkedHashSet()) { if (it >= WhosWhoTeacher.CLUE_BASE) it else it / b.columns }
        val ticks = step.ticks.map(::item)
        val crosses = step.crosses.map(::item)
        val moved = (ticks + crosses).flatMapTo(LinkedHashSet()) { listOf(it.first, it.second) }
        return Deduction(
            step.technique, step.nudge, step.explanation,
            focus = nodes(step.focus), cited = nodes(step.cited), targets = nodes(step.targets) + moved,
            applyTo = { now ->
                var next = now as WhosWhoGraphState
                val closed = WhosWhoGraphLogic.close(next)
                // A tick is "is": the pair drawn from both ends, and any other line either end drew into the other's
                // category ruled out, so a stray "might be" cannot keep it open.
                for ((p, ref) in ticks) if (closed.at(p, ref) != 1) {
                    next = next.withMark(p, ref, WhosWhoGraphLogic.LINK)
                    for ((x, y) in listOf(p to ref, ref to p)) for (z in y / b.n * b.n until y / b.n * b.n + b.n) {
                        if (z != y && next.drew(x, z)) next = next.withMark(x, z, WhosWhoGraphLogic.ANTI)
                    }
                }
                for ((p, ref) in crosses) if (closed.at(p, ref) != -1) next = next.withMark(p, ref, WhosWhoGraphLogic.ANTI)
                if (next.moves > now.moves) next.copy(moves = now.moves + 1) else next
            },
            reachedBy = { now ->
                val closed = WhosWhoGraphLogic.close(now as WhosWhoGraphState)
                ticks.all { (p, ref) -> closed.at(p, ref) == 1 } && crosses.all { (p, ref) -> closed.at(p, ref) == -1 }
            },
        )
    }

    /**
     * The first mark the board's one answer disagrees with: an anti-link on a pair that goes together, or an item whose
     * lines into a category leave out the one it really goes with. A line to a wrong item beside the right one is fine:
     * it only says "one of these".
     */
    private fun mistake(s: WhosWhoGraphState): Deduction? {
        val b = s.board
        val n = b.n
        fun same(x: Int, y: Int) = WhosWhoGraphLogic.holder(b, x) == WhosWhoGraphLogic.holder(b, y)
        for (a in 0 until s.size) for (c in a + 1 until s.size) {
            if (s.mark(a, c) != WhosWhoGraphLogic.ANTI || !same(a, c)) continue
            return Deduction(
                technique = WhosWhoTeacher.MISTAKE,
                nudge = "One of your marks doesn't fit.",
                explanation = "${WhosWhoLogic.word(b, a)} and ${WhosWhoLogic.word(b, c)} go together, so that anti-link can't be kept.",
                focus = setOf(a, c), cited = emptySet(), targets = setOf(a, c),
                mistake = true,
                applyTo = { now -> (now as WhosWhoGraphState).withMark(a, c, WhosWhoGraphLogic.OPEN) },
                reachedBy = { now -> (now as WhosWhoGraphState).mark(a, c) == WhosWhoGraphLogic.OPEN },
            )
        }
        for (a in 0 until s.size) for (category in 0..b.columns) {
            if (category == a / n) continue
            val items = category * n until category * n + n
            val drawn = items.filter { s.drew(a, it) }
            if (drawn.isEmpty() || drawn.any { same(a, it) }) continue
            val word = WhosWhoLogic.word(b, a)
            return Deduction(
                technique = WhosWhoTeacher.MISTAKE,
                nudge = "One of your marks doesn't fit.",
                explanation = "None of $word's lines to a ${noun(b, category)} is the right one, so they can't be kept.",
                focus = setOf(a) + drawn, cited = emptySet(), targets = setOf(a) + drawn,
                mistake = true,
                applyTo = { now -> drawn.fold(now as WhosWhoGraphState) { acc, z -> acc.withoutLine(a, z) }.copy(moves = now.moves + 1) },
                reachedBy = { now ->
                    val left = items.filter { (now as WhosWhoGraphState).drew(a, it) }
                    left.isEmpty() || left.any { same(a, it) }
                },
            )
        }
        return null
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** A hue per category: the people, then each column. */
    private val HUES = listOf(268f, 175f, 34f, 215f)

    private val CHIPS = 34.dp
    private val HEADER = 20.dp

    /** How long a change of view takes to re-sort the board. */
    private const val MORPH_MS = 350

    /** A category's noun: "person", "house", "pet". */
    private fun noun(b: WhosWhoState, category: Int) = if (category == 0) "person" else WhosWhoWords.NOUNS[b.groups[category - 1]]

    private fun title(b: WhosWhoState, category: Int) = if (category == 0) "Person" else WhosWhoWords.HEADERS[b.groups[category - 1]]

    /** A category's drawn mark in its column's ink, named for a screen reader. */
    @Composable
    private fun Header(b: WhosWhoState, category: Int, dark: Boolean, modifier: Modifier) = WhosWhoIcons.Icon(
        if (category == 0) WhosWhoIcons.PERSON else b.groups[category - 1],
        BoardHues.ink(HUES[category % HUES.size], dark), dark, title(b, category), modifier,
    )

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as WhosWhoGraphState
        val b = s.board
        val scheme = MaterialTheme.colorScheme
        val closure = remember(s.links, b) { WhosWhoGraphLogic.close(s) }
        val table = remember(closure) { WhosWhoGraphLogic.table(s, closure) }
        // The chosen item, the view and the grouping are the screen's, never the board's: the play screen pushes an undo
        // entry for every state it is handed. The choice belongs to the state it was made on (or emitted from), so a
        // board that arrives from elsewhere (an undo, "Show me", the walkthrough's next frame) starts with nothing chosen.
        var held by remember { mutableStateOf<Pair<WhosWhoGraphState?, Int>>(null to -1) }
        val selected = if (held.first === s) held.second else -1
        var view by rememberSaveable { mutableIntStateOf(-1) }
        var grouped by rememberSaveable { mutableIntStateOf(0) }
        // Once solved, the board turns to the people's table (view 0: a row per person, every item in its row), so the
        // finish shows the answer the marks left. Once per board: saved, so a rotation or a later tap on another view
        // is not undone. A board that opens solved turns there too.
        var turned by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(s.solved) {
            if (s.solved && !turned) {
                turned = true
                view = 0
            }
        }
        val paint = rememberHintPaint()
        val dark = BoardHues.isDark(scheme)

        val grid = WhosWhoLogic.gridOf(b.n, b.columns, table.marks)
        val puzzle = WhosWhoLogic.puzzleOf(b)
        val badClue = rememberSettledFlags(BooleanArray(b.clues.size) { !WhosWhoLogic.possible(puzzle, grid, b.clues[it]) }, b.clues, s.links)
        val badItem = rememberSettledFlags(closure.badItem, b.clues, s.links)
        val badPair = rememberSettledFlags(closure.badPair, b.clues, s.links)

        CappedFontScale {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.fillMaxWidth().height(CHIPS).horizontalScroll(rememberScrollState()).padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    Chip("Links", view < 0 && grouped == 0) { view = -1; grouped = 0 }
                    Chip("Group", view < 0 && grouped == 1) { view = -1; grouped = 1 }
                    for (c in 0..b.columns) Chip(null, view == c, { Header(b, c, dark, Modifier.size(20.dp)) }) { view = c }
                }
                Box(Modifier.weight(1.5f).fillMaxWidth().widthIn(max = 560.dp)) {
                    Graph(s, closure, selected, grouped == 1, view, badItem, badPair, paint.highlight, paint.glow, paint.pulse, dark, interactive) { i ->
                        val now = selected
                        held = when {
                            i < 0 -> s to -1
                            now < 0 || now / b.n == i / b.n -> s to if (i == now) -1 else i
                            else -> s.cycle(now, i).let { next ->
                                onState(next)
                                next to now
                            }
                        }
                    }
                }
                Clues(b, badClue, paint.highlight, paint.glow, paint.pulse, dark, interactive, Modifier.weight(1f).fillMaxWidth().widthIn(max = 560.dp)) {
                    val next = s.strike(it)
                    onState(next)
                    held = next to selected
                }
            }
        }
    }

    @Composable
    private fun Chip(label: String?, on: Boolean, icon: @Composable () -> Unit = {}, onClick: () -> Unit) {
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (on) scheme.secondaryContainer else scheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (label == null) icon()
            else Text(label, fontSize = 13.sp, color = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant, maxLines = 1)
        }
    }

    /**
     * Where each item sits: a column per category and a row per slot, or, grouped, each item in the row of the person its
     * links reach; a group with no person takes the first row free in all its columns, and an item linked to nothing the
     * first row free in its own (drawn faint: it is placed nowhere). Every item takes exactly one row, so no column
     * ever runs out, however the marks clash.
     */
    internal fun rows(s: WhosWhoGraphState, closure: WhosWhoGraphLogic.Closure, grouped: Boolean): Pair<IntArray, BooleanArray> {
        val n = s.n
        val loose = BooleanArray(s.size)
        if (!grouped) return IntArray(s.size) { it % n } to loose
        val row = IntArray(s.size) { -1 }
        val used = Array(s.board.columns + 1) { BooleanArray(n) }
        for (p in 0 until n) {
            row[p] = p
            used[0][p] = true
            // One item a square: a clash can link an item to two people (a second row would leave its column a row short
            // for the rest, and `first` below found nothing: a crash), or a person to two items of a column (drawn on top
            // of each other). The one left over takes a free row below.
            for (x in n until s.size) if (row[x] < 0 && !used[x / n][p] && closure.at(p, x) == 1) {
                row[x] = p
                used[x / n][p] = true
            }
        }
        for (x in n until s.size) {
            if (row[x] >= 0) continue
            val group = (n until s.size).filter { it == x || closure.at(x, it) == 1 }
            if (group.size == 1) loose[x] = true
            val free = (0 until n).firstOrNull { r -> group.all { !used[it / n][r] } } ?: (0 until n).first { !used[x / n][it] }
            for (g in group) if (row[g] < 0 && !used[g / n][free]) {
                row[g] = free
                used[g / n][free] = true
            }
            if (row[x] < 0) row[x] = (0 until n).first { !used[x / n][it] }.also { used[x / n][it] = true }
        }
        return row to loose
    }

    @Composable
    private fun Graph(
        s: WhosWhoGraphState,
        closure: WhosWhoGraphLogic.Closure,
        selected: Int,
        grouped: Boolean,
        view: Int,
        badItem: BooleanArray,
        badPair: BooleanArray,
        highlight: com.joebywan.daybook.core.BoardHighlight,
        glow: Color,
        pulse: State<Float>,
        dark: Boolean,
        interactive: Boolean,
        onItem: (Int) -> Unit,
    ) {
        val b = s.board
        val n = b.n
        val cols = b.columns + 1
        val scheme = MaterialTheme.colorScheme
        val reduced = LocalReducedMotion.current
        val (row, loose) = rows(s, closure, grouped)
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = interactive) { onItem(-1) },
        ) {
            val cellW = maxWidth / cols
            val cellH = (maxHeight - HEADER) / n
            // Pills, so a word fits whole (circles that fitted the height cut "doctor" to "docto"), leaving a third of the
            // column clear: at nine tenths the lines between neighbouring columns hid under them.
            val w: Dp = minOf(cellW * 0.7f, 90.dp)
            val h: Dp = minOf(cellH * 0.78f, 46.dp)
            val density = LocalDensity.current
            val centre = { col: Int, r: Int -> with(density) { Offset((cellW * (col + 0.5f)).toPx(), (HEADER + cellH * (r + 0.5f)).toPx()) } }
            // A table view is the same box and the same pills (the owner: one shape for one item in every view): the chosen
            // category's items head the rows, every other item slides into the row it is certainly linked to; one still open
            // has no place, so it fades out where it stands and the row's slot says what is left. One tween for everything,
            // so the board reads as re-sorting in one movement.
            val table = view >= 0
            val lastTable = remember { intArrayOf(0) }
            if (table) lastTable[0] = view
            val base = lastTable[0]
            val order = if (table) listOf(view) + (0 until cols).filter { it != view } else (0 until cols).toList()
            val colOf = IntArray(cols).also { for ((k, c) in order.withIndex()) it[c] = k }
            fun tableRow(x: Int): Int = if (x / n == base) x % n else closure.linked(x, base).let { if (it >= 0) it % n else -1 }
            fun <T> ease(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(MORPH_MS, easing = FastOutSlowInEasing)
            val t by animateFloatAsState(if (table) 1f else 0f, ease(), label = "table")
            class Node(val pos: State<Offset>, val alpha: State<Float>)
            val nodes = List(s.size) { i ->
                key(i) {
                    val r = if (table) tableRow(i) else -1
                    val open = table && r < 0
                    Node(
                        animateOffsetAsState(if (table) centre(colOf[i / n], if (open) i % n else r) else centre(i / n, row[i]), ease(), label = "item"),
                        animateFloatAsState(if (open) 0f else if (!table && loose[i]) 0.45f else 1f, ease(), label = "alpha"),
                    )
                }
            }
            val pos = nodes.map { it.pos }
            for (c in 0 until cols) {
                val x by animateDpAsState(cellW * colOf[c], ease(), label = "header")
                Header(b, c, dark, Modifier.width(cellW).height(HEADER).offset(x = x).padding(vertical = 2.dp))
            }
            // One table to another fades the old cells out under the new, as the nodes move: the cells alone snapped.
            if (t > 0f) Crossfade(base, animationSpec = ease(), label = "cells") {
                // Full size: a fading layer is only as big as its content, and cells placed by offset outside it were cut off.
                Box(Modifier.fillMaxSize()) { Cells(s, closure, it, cellW, cellH, t) }
            }
            val ink = scheme.onSurface
            val faint = scheme.onSurfaceVariant
            val error = scheme.error
            // Pairs in neighbouring columns are drawn under the pills; a pair that skips a column over them, so it plainly
            // passes over the pills between rather than seeming to end at one (behind them it read as a chain through them).
            // The alpha comes first: a layer only fades what is drawn after it, so behind `drawBehind` the lines popped.
            val edges = { skipping: Boolean ->
                Modifier.fillMaxSize().alpha(1f - t).drawBehind {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                    val dot = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))
                    for (a in 0 until s.size) for (c in a + 1 until s.size) {
                        val gap = c / n - a / n
                        if (gap == 0 || (gap > 1) != skipping) continue
                        val touches = selected == a || selected == c
                        val mark = s.mark(a, c)
                        val mine = mark != WhosWhoGraphLogic.OPEN
                        val line = mine && mark != WhosWhoGraphLogic.ANTI
                        val v = closure.at(a, c)
                        val dim = selected >= 0 && !touches
                        val (colour, width, effect) = when {
                            badPair[a * s.size + c] -> Triple(error, 3.dp, null)
                            v == 1 -> Triple(ink.copy(alpha = if (dim) 0.15f else if (mine) 0.9f else 0.4f), if (mine) 2.5.dp else 1.2.dp, null)
                            // A "might be": thinner and lighter than a link; once the marks rule it out, fainter still.
                            line && v == 0 -> Triple(ink.copy(alpha = if (dim) 0.12f else 0.55f), 1.5.dp, null)
                            line -> Triple(faint.copy(alpha = if (dim) 0.1f else 0.3f), 1.dp, null)
                            v == -1 && touches -> Triple(error.copy(alpha = if (mine) 0.85f else 0.45f), if (mine) 1.6.dp else 1.dp, dash)
                            v == 0 && touches -> Triple(faint.copy(alpha = 0.7f), 1.2.dp, dot)
                            else -> continue
                        }
                        val p = pos[a].value
                        val q = pos[c].value
                        // A skipping pair bows a quarter row (up, or down from the top row) so two of them seldom overlap.
                        val row = cellH.toPx()
                        val midY = (p.y + q.y) / 2
                        val bend = if (!skipping) 0f else if (midY < HEADER.toPx() + row) -row / 2 else row / 2
                        val path = curveBetween(p, Offset((p.x + q.x) / 2, midY - bend), q, w.toPx() / 2, h.toPx() / 2) ?: continue
                        drawPath(path, colour, style = Stroke(width.toPx(), cap = StrokeCap.Round, pathEffect = effect))
                    }
                }
            }
            if (t < 1f) Box(edges(false))
            for (i in 0 until s.size) {
                val hue = HUES[(i / n) % HUES.size]
                val hint = highlight.look(i)
                val node = nodes[i]
                val p = node.pos
                val nw = w
                val nh = h
                val shape = RoundedCornerShape(h / 2)
                // Arcade: a sticker in its category's gradient, carrying its own ink and lifted off the page. Classic: the
                // flat tint under an inked edge it always had.
                val sticker = if (classicLook()) null else ArcadeBoard.arcadeTile(Color.hsl(hue, 0.75f, 0.5f), dark)
                val edge = when {
                    badItem[i] -> scheme.error
                    i == selected -> scheme.onSurface
                    sticker != null -> Color.Transparent
                    else -> BoardHues.ink(hue, dark)
                }
                Box(
                    Modifier
                        .offset { IntOffset((p.value.x - nw.toPx() / 2).roundToInt(), (p.value.y - nh.toPx() / 2).roundToInt()) }
                        .size(nw, nh)
                        .alpha(node.alpha.value)
                        .highlightAnchor(i)
                        .ring(hint, glow, pulse, h / 2, outside = true)
                        .then(if (sticker != null) Modifier.arcadeLift(sticker, h / 2) else Modifier)
                        .clip(shape)
                        .then(if (sticker != null) Modifier.background(sticker.brush) else Modifier.background(BoardHues.fill(hue, dark)))
                        .border(if (i == selected || badItem[i]) 2.5.dp else 0.5.dp, edge, shape)
                        // A table is for reading: its taps did nothing before it shared the nodes, and still do nothing.
                        .clickable(enabled = interactive && !table) { onItem(i) }
                        .dimmed(hint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        WhosWhoLogic.word(b, i),
                        Modifier.padding(horizontal = 2.dp),
                        fontSize = minOf(nh * 0.4f, 14.dp).boardSp(),
                        lineHeight = minOf(nh * 0.5f, 18.dp).boardSp(),
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        color = sticker?.ink ?: scheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            if (t < 1f) Box(edges(true))
        }
    }

    /**
     * The curve from [p] through control [c] to [q], cut where it leaves the pill (half-width [hw], half-height [hh]) at
     * each end, so a line stops at the pill's edge and never runs over the names it joins. Null when nothing is left.
     */
    internal fun curveBetween(p: Offset, c: Offset, q: Offset, hw: Float, hh: Float): Path? {
        fun at(t: Float): Offset {
            val u = 1 - t
            return Offset(u * u * p.x + 2 * u * t * c.x + t * t * q.x, u * u * p.y + 2 * u * t * c.y + t * t * q.y)
        }
        fun inside(x: Offset, end: Offset) = kotlin.math.abs(x.x - end.x) <= hw && kotlin.math.abs(x.y - end.y) <= hh
        // Where the curve crosses an end's edge, by halving: [lo] inside that pill, [hi] outside it.
        fun edge(lo0: Float, hi0: Float, end: Offset): Float {
            var lo = lo0
            var hi = hi0
            repeat(20) { val mid = (lo + hi) / 2; if (inside(at(mid), end)) lo = mid else hi = mid }
            return hi
        }
        val from = edge(0f, 0.5f, p).takeIf { !inside(at(it), q) } ?: return null
        val to = edge(1f, 0.5f, q)
        if (to <= from || inside(at(0.5f), p) || inside(at(0.5f), q)) return null
        return Path().apply {
            val start = at(from)
            moveTo(start.x, start.y)
            val steps = 24
            for (k in 1..steps) at(from + (to - from) * k / steps).let { lineTo(it.x, it.y) }
        }
    }

    /**
     * The table's open slots, faded in by [t]: where the row's head is not yet linked to anything in a column, what is left
     * there ("any", the items, or "none left" in red), as plain text where a pill would sit. No cell is drawn: an item is the
     * same pill in every view, so a table must not turn into a grid of rectangles (the owner).
     */
    @Composable
    private fun Cells(s: WhosWhoGraphState, closure: WhosWhoGraphLogic.Closure, base: Int, cellW: Dp, cellH: Dp, t: Float) {
        val b = s.board
        val n = b.n
        val scheme = MaterialTheme.colorScheme
        val order = listOf(base) + (0..b.columns).filter { it != base }
        for (r in 0 until n) for ((k, c) in order.withIndex()) {
            val head = base * n + r
            if (c == base || closure.linked(head, c) >= 0) continue
            val left = (c * n until c * n + n).filter { closure.at(head, it) != -1 }
            Text(
                if (left.size == n) "any" else left.joinToString(" ") { WhosWhoLogic.word(b, it) }.ifEmpty { "none left" },
                Modifier
                    .offset(cellW * k, HEADER + cellH * r)
                    .size(cellW, cellH)
                    .alpha(t)
                    .wrapContentHeight()
                    .padding(horizontal = 3.dp),
                fontSize = minOf(cellH * 0.22f, 11.dp).boardSp(),
                lineHeight = minOf(cellH * 0.27f, 13.dp).boardSp(),
                color = if (left.isEmpty()) scheme.error else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Clip,
            )
        }
    }

    @Composable
    private fun Clues(
        b: WhosWhoState,
        bad: BooleanArray,
        highlight: com.joebywan.daybook.core.BoardHighlight,
        glow: Color,
        pulse: State<Float>,
        dark: Boolean,
        interactive: Boolean,
        modifier: Modifier,
        onClue: (Int) -> Unit,
    ) {
        val scheme = MaterialTheme.colorScheme
        val list = rememberLazyListState()
        val hinted = b.clues.indices.firstOrNull { WhosWhoTeacher.CLUE_BASE + it in highlight.strong || WhosWhoTeacher.CLUE_BASE + it in highlight.soft }
        LaunchedEffect(hinted) { if (hinted != null) list.animateScrollToItem(hinted) }
        LazyColumn(modifier.padding(vertical = 6.dp), state = list) {
            itemsIndexed(b.clues) { k, _ ->
                val index = WhosWhoTeacher.CLUE_BASE + k
                val struck = b.isStruck(k)
                val hint = highlight.look(index)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .highlightAnchor(index)
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .ring(hint, glow, pulse, 8.dp)
                        .background(if (bad[k]) scheme.error.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(enabled = interactive) { onClue(k) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .dimmed(hint),
                ) {
                    Text(
                        "${k + 1}",
                        Modifier.width(22.dp),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (bad[k]) scheme.error else BoardHues.ink(HUES[0], dark),
                    )
                    Text(
                        WhosWhoLogic.clueText(b, k),
                        fontSize = 13.5.sp,
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

    /** Three columns of circles, two links and an anti-link. Fixed; never calls `generate()`. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(Modifier.size(side)) {
                val u = size.width / 6f
                val at = { c: Int, r: Int -> Offset(u * (1 + 2 * c), u * (1 + 2 * r)) }
                drawLine(scheme.onSurface, at(0, 0), at(1, 1), u * 0.12f, StrokeCap.Round)
                drawLine(scheme.onSurface, at(1, 1), at(2, 0), u * 0.12f, StrokeCap.Round)
                drawLine(
                    scheme.error, at(0, 2), at(1, 0), u * 0.08f, StrokeCap.Round,
                    PathEffect.dashPathEffect(floatArrayOf(u * 0.3f, u * 0.25f)),
                )
                for (c in 0..2) for (r in 0..2) {
                    drawCircle(BoardHues.fill(HUES[c], dark), u * 0.62f, at(c, r))
                    drawCircle(BoardHues.ink(HUES[c], dark), u * 0.62f, at(c, r), style = Stroke(u * 0.06f))
                }
            }
        }
    }
}
