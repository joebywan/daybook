package com.joebywan.daybook.core

import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.InequalityState
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.RegionsLayout
import com.joebywan.daybook.puzzles.RegionsState
import com.joebywan.daybook.puzzles.RegionsTeacher
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Training (`docs/TRAINING_SPEC.md`): per puzzle with a teacher, each technique it names, a short explanation in the hints'
 * voice, and practice boards on which the next hint step is exactly that technique. Pass a technique with [Training.RUN]
 * right in a row; pass them all, then solve the [Course.exam], a board whose own walk needs every one.
 *
 * Pure and platform-free (the web compiles it). Practice boards are found, not stored: real generated boards walked by
 * the teacher's ladder, in seed order, so the same index is the same board everywhere. How a walk steps and how a move is
 * judged is the course's [Drill]: [PlacementDrill] for teachers that place digits, [MoveDrill] for every other.
 */
class Course(
    val puzzle: PuzzleType,
    /** What each technique is, before the first board, in the ladder's order (the list shows them so). */
    val explanations: Map<String, String>,
    /** The tier a technique's practice is drawn from: the easiest whose walks reach it (the generator gates depth). */
    private val tierFor: (String) -> Difficulty,
    val drill: Drill,
    /** The "<Puzzle> trained" badge's glyph (`ui/stats/AchievementBadge.kt`); the graduate's is the crown. */
    val badge: String,
    /** The index of the practice seed whose Expert board's walk needs every technique; null when none was found. */
    examIndex: Int?,
    /**
     * A seed's board on a tier, or null to skip the seed: a course whose `generate` can ship an unproved board passes its
     * `generateVerified`, so no practice or final board is one the generator has not proved (CLAUDE.md).
     */
    private val generate: (Long, Difficulty) -> PuzzleState? = puzzle::generate,
) {
    /** A course on a digit-placing teacher: [PlacementDrill]'s four parts, as the first four courses wrote them. */
    constructor(
        puzzle: PuzzleType,
        explanations: Map<String, String>,
        tierFor: (String) -> Difficulty,
        stepper: (PuzzleState) -> (List<Int>) -> PlacementStep?,
        cellsOf: (PuzzleState) -> List<Int>,
        solutionOf: (PuzzleState) -> List<Int>,
        withGivens: (PuzzleState, List<Int>) -> PuzzleState,
        badge: String,
        examIndex: Int?,
    ) : this(puzzle, explanations, tierFor, PlacementDrill(stepper, cellsOf, solutionOf, withGivens), badge, examIndex)

    val id: String get() = puzzle.id

    /** The ladder's techniques, simplest first: the order the teacher tries them and the list shows them. */
    val techniques: List<String> = explanations.keys.toList()

    /**
     * One practice: [board], whose next hint step is [step] (that technique, from what is visible). For a [PlacementDrill]
     * the step is a [PlacementStep]; for a [MoveDrill] it is the [Deduction] whose `isReached` passes the practice.
     */
    class Practice(val board: PuzzleState, val step: TeachStep)

    /**
     * The practices for [technique], in order: per seed, the first position on that board's walk where the teacher's next
     * step is [technique].
     */
    fun practices(technique: String): Sequence<Practice> = sequence {
        val tier = tierFor(technique)
        for (k in 0 until Training.SEARCH) {
            val start = board(k, tier) ?: continue
            positionOn(start, technique)?.let { yield(it) }
        }
    }

    /**
     * Per technique, the practices found this session and the next seed to search: they come from pure generators, so they
     * never go stale, and board n costs only the seeds after board n-1's. Read and written under [lock] only: on Android
     * two searches can be under way at once on `Dispatchers.Default` (a quick second "Another board"); the second waits and
     * then finds the first's work cached. Wasm has one thread, where the lock never waits. Keyed lookups only, never
     * iterated, so no hash order reaches a board.
     */
    private val found = HashMap<String, MutableList<Practice>>()
    private val searched = HashMap<String, Int>()
    private val lock = Mutex()

    /** The [index]th practice for [technique], wrapping round if the search ran out. Null only if it found none. */
    suspend fun practice(technique: String, index: Int): Practice? = lock.withLock {
        val list = found.getOrPut(technique) { ArrayList() }
        var k = searched[technique] ?: 0
        if (index >= list.size && k < Training.SEARCH) {
            val tier = tierFor(technique)
            while (index >= list.size && k < Training.SEARCH) {
                board(k, tier)?.let { b -> positionOn(b, technique)?.let { list += it } }
                k++
            }
            searched[technique] = k
        }
        list.getOrNull(index) ?: list.getOrNull(index % list.size.coerceAtLeast(1))
    }

    /** Practice seed [k]'s board on [tier], or null when the generator proved none for it. */
    fun board(k: Int, tier: Difficulty): PuzzleState? = generate(Training.seed(k), tier)

    private fun positionOn(start: PuzzleState, technique: String): Practice? =
        drill.walk(start).firstOrNull { it.step.technique == technique }

    /** The techniques a walk by the teacher's ladder from [start] uses. */
    fun techniquesUsed(start: PuzzleState): Set<String> = drill.walk(start).mapTo(LinkedHashSet()) { it.step.technique }

    /**
     * What the practice board's [after] (a state it emitted from [p]'s board) makes of [p]: true right, false wrong, null
     * play on. It may ask the teacher, which can take a while: call it off the main thread.
     */
    fun judge(p: Practice, after: PuzzleState): Boolean? = drill.judge(puzzle, p, after)

    internal val examSeed: Long? = examIndex?.let { Training.seed(it) }

    val hasExam: Boolean get() = examSeed != null

    /** The final board: a whole Expert board whose walk needs every technique, or null when this course has none. */
    fun exam(): PuzzleState? = examSeed?.let { generate(it, Difficulty.EXPERT) }

    /** The technique's name as a title: "A digit's only place in a box". */
    fun titleOf(technique: String): String =
        techniqueNamesOf(id)[technique]?.replaceFirstChar { it.uppercase() } ?: "Final board"

    fun passed(record: Set<String>, technique: String) = TrainingRecord.entry(id, technique) in record

    /** Whether every technique is passed, which opens the final board. */
    fun allPassed(record: Set<String>) = techniques.all { passed(record, it) }
}

object Training {
    /** Right answers in a row that pass a technique. A hinted board does not count, nor break the run. */
    const val RUN = 3

    /** The training record's entry for the exam, beside the techniques' ids. */
    const val EXAM = "exam"

    /** Training's seeds are their own range, far from any daily's hash, so no practice board is somebody's daily. */
    private const val SEED_BASE = 7_000_000L

    /**
     * Between one practice seed and the next. `generateVerified` tries `Rng(seed + attempt)`, so neighbouring seeds share
     * grids and would deal the same board twice; this keeps them apart.
     */
    private const val STRIDE = 1_000L

    internal fun seed(k: Int): Long = SEED_BASE + k * STRIDE

    /** Seeds tried before giving up on a technique. */
    internal const val SEARCH = 4000

    /**
     * Every course, in the order the list shows them. A new course is its own file (`core/training/<Name>Training.kt`)
     * and one line here, at the end: achievements and their badges follow this order, so append only.
     */
    val COURSES: List<Course> by lazy {
        listOf(
            SudokuTraining,
            CagesTraining,
            InequalityTraining,
            RegionsTraining,
            KingsTraining,
            ShikakuTraining,
            DominoesTraining,
            LitsTraining,
        )
    }

    fun of(puzzleId: String?): Course? = COURSES.firstOrNull { it.id == puzzleId }
}

/** The tier for [technique] at its index in [course]: Standard below [hard], Hard below [expert], then Expert. */
internal fun tierByIndex(techniques: () -> List<String>, hard: Int, expert: Int): (String) -> Difficulty = { t ->
    val i = techniques().indexOf(t)
    when {
        i < hard -> Difficulty.STANDARD
        i < expert -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }
}

val SudokuTraining: Course = Course(
    puzzle = Sudoku,
    explanations = linkedMapOf(
        SudokuTeacher.FULL_HOUSE to "When a row, column or box has one empty square left, the digit it's missing goes there.",
        SudokuTeacher.HIDDEN_SINGLE_BOX to "Pick a box and a digit it still needs. When every other empty square in the box " +
            "already sees that digit in its row or column, the one square left must hold it.",
        SudokuTeacher.HIDDEN_SINGLE_LINE to "The same idea along a row or column: when every other empty square in the line " +
            "already sees a digit in its column, row or box, the one square left takes it.",
        SudokuTeacher.NAKED_SINGLE to "Look at one square instead of one digit. When its row, column and box between them " +
            "hold eight different digits, the ninth is the only one left for it.",
        SudokuTeacher.LOCKED to "When a digit's places in a box all lie in one row or column, it must go there, so the " +
            "rest of that line can't have it. (The same works the other way round, a line's places all in one box.) " +
            "That often leaves a single.",
        SudokuTeacher.NAKED_PAIR to "Two squares in a row, column or box that can each be only the same two digits take both " +
            "of them, so no other square there can hold either. Strike them off and look for the single it leaves.",
        SudokuTeacher.HIDDEN_PAIR to "Two digits that fit in only the same two squares of a row, column or box take those " +
            "squares, so those two squares can't hold anything else. Look for the single that follows.",
        SudokuTeacher.WHAT_IF to "A square with two digits left, or a digit with two places left: suppose one and follow it " +
            "a step or two. If something runs out of digits or places, the other choice is the answer.",
    ),
    // Sudoku.gate: Standard needs singles in a unit only, Hard a naked single, Expert an elimination or a what-if.
    tierFor = tierByIndex({ SudokuTraining.techniques }, hard = 3, expert = 4),
    stepper = { _ -> { cells -> SudokuTeacher.deduce(cells) } },
    cellsOf = { (it as SudokuState).cells },
    solutionOf = { (it as SudokuState).solution },
    withGivens = { s, cells -> SudokuState(givens = cells.map { it != 0 }, cells = cells, solution = (s as SudokuState).solution) },
    // One board in the first 400 practice seeds' Expert boards needs all eight. Pinned by SudokuTrainingTest.
    badge = "9",
    examIndex = 219,
)

val CagesTraining: Course = Course(
    puzzle = Cages,
    explanations = linkedMapOf(
        CageTeacher.FULL_HOUSE to "When a row, column or box has one empty square left, the digit it's missing goes there.",
        CageTeacher.CAGE_LAST to "When a cage has one empty square left, it takes whatever the cage's total still needs.",
        CageTeacher.HIDDEN_SINGLE_BOX to "Pick a box and a digit it still needs. When every other empty square in the box " +
            "already sees that digit, or its cage already has one, the one square left must hold it.",
        CageTeacher.HIDDEN_SINGLE_LINE to "The same idea along a row or column: when every other empty square in the line " +
            "already sees a digit, the one square left takes it.",
        CageTeacher.NAKED_SINGLE to "Look at one square. When its row, column, box and cage between them hold eight " +
            "different digits, the ninth is the only one left for it.",
        CageTeacher.CAGE_COMBO to "A cage's total can only be made a few ways with digits that don't repeat. A 3 in " +
            "two squares is 1 and 2; a 17 is 8 and 9. List the ways that still fit and see what they leave.",
        CageTeacher.FORTY_FIVE to "Every row, column and box adds up to 45. Add the cages lying wholly inside one and take " +
            "that from 45: the squares left over must make the difference.",
        CageTeacher.LINKED_TOTALS to "Two or three totals that share squares: settle one, and it cuts the others. Work " +
            "them through one after another until a square has one digit left.",
        CageTeacher.CAGE_CHAIN to "When no one cage settles it, work every cage's ways through together with the digits " +
            "around them, again and again, until a square has one digit left.",
    ),
    // The 45 rule and linked totals from Hard (88 and 42% of its boards reach them), the chain from Expert (all of them; 17% on Hard).
    tierFor = tierByIndex({ CagesTraining.techniques }, hard = 6, expert = 8),
    stepper = { s -> val b = CageLogic.Board((s as CageState).cageOf, s.sums); { cells -> CageTeacher.deduce(b, cells) } },
    cellsOf = { (it as CageState).cells },
    solutionOf = { (it as CageState).grid.solution },
    withGivens = { s, cells ->
        val c = s as CageState
        c.copy(grid = SudokuState(givens = cells.map { it != 0 }, cells = cells, solution = c.grid.solution))
    },
    // The first practice seed whose Expert board needs all nine. Pinned by TrainingTest.
    badge = "45",
    examIndex = 0,
)

val InequalityTraining: Course = Course(
    puzzle = Inequality,
    explanations = linkedMapOf(
        InequalityTeacher.LAST to "When a row or column has one empty square left, the digit it's missing goes there.",
        InequalityTeacher.ONLY_DIGIT to "Look at one square. When its row and column between them hold every digit but " +
            "one, that one is all it can be.",
        InequalityTeacher.ONLY_PLACE to "Pick a line and a digit it still needs. When every other empty square in it " +
            "already has that digit in its own row or column, the one square left takes it.",
        InequalityTeacher.SIGN_BOUND to "A sign points at the smaller side. A square smaller than another can't be the " +
            "biggest digit, and one bigger can't be 1; along a chain of signs the bounds add up. Count them in and see " +
            "what's left for the square.",
        InequalityTeacher.SIGN_PLACE to "The same as a digit's only place in a line, with the signs counted too: a square " +
            "the signs keep below a digit can't hold it.",
        InequalityTeacher.NAKED_PAIR to "Two squares in a line that can each be only the same two digits take both of them, " +
            "so no other square in that line can hold either. Strike them off and look for the single it leaves.",
        InequalityTeacher.HIDDEN_PAIR to "Two digits that fit in only the same two squares of a line take those squares, so " +
            "those two squares can't hold anything else. Look for the single that follows.",
    ),
    // InequalityLogic.specFor: Standard the five singles, Hard must need a naked pair, Expert a hidden pair.
    tierFor = tierByIndex({ InequalityTraining.techniques }, hard = 5, expert = 6),
    stepper = { s -> val q = s as InequalityState; { cells -> InequalityTeacher.deduce(q.size, cells, q.signs) } },
    cellsOf = { (it as InequalityState).cells },
    solutionOf = { (it as InequalityState).solution },
    withGivens = { s, cells -> (s as InequalityState).copy(givens = cells.map { it != 0 }, cells = cells) },
    // The first practice seed whose Expert board needs all seven (17 of the first 60 do). Pinned by TrainingTest.
    badge = "<",
    examIndex = 4,
)

val RegionsTraining: Course = Course(
    puzzle = Regions,
    explanations = linkedMapOf(
        RegionsTeacher.LAST to "When a region has one empty square left, the digit it's missing goes there.",
        RegionsTeacher.ONLY_DIGIT to "Look at one square. Its region's digits and the digits touching it, even at a " +
            "corner, rule digits out. When only one is left, that's it.",
        RegionsTeacher.ONLY_PLACE to "Pick a region and a digit it still needs. When every other empty square in it " +
            "touches that digit already, the one square left takes it.",
        RegionsTeacher.LOCKED to "When every place a region's digit could go touches the same square outside the region, " +
            "that square can't be that digit. That often leaves a single.",
    ),
    // RegionsLogic.specFor: Standard the three singles, Hard and Expert must take locked steps.
    tierFor = tierByIndex({ RegionsTraining.techniques }, hard = 3, expert = 4),
    stepper = { s -> val l = RegionsLayout((s as RegionsState).size, s.region); { cells -> RegionsTeacher.deduce(l, cells) } },
    cellsOf = { (it as RegionsState).cells },
    solutionOf = { (it as RegionsState).solution },
    withGivens = { s, cells -> (s as RegionsState).copy(givens = cells.map { it != 0 }, cells = cells) },
    // Every Hard and Expert board needs all four; the first practice seed's Expert board. Pinned by TrainingTest.
    badge = "5",
    examIndex = 0,
)

/** How a course walks a board by its teacher's steps, and judges a move on a practice board. */
interface Drill {
    /** The positions on a walk by the teacher's ladder from [start], each with its next step, until the ladder stops. */
    fun walk(start: PuzzleState): Sequence<Course.Practice>

    /** See [Course.judge]. */
    fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean?
}

/**
 * For a teacher that places digits (Sudoku, Cages, Inequality, Regions): the walk writes each step's digit in, and a
 * position's digits are all givens, so the walk's cannot be erased. A placed digit is judged at once: right when it is the
 * answer's, whether it is the step's or another square's, since the position has no simpler step anywhere (the teacher
 * tries every simpler technique on the whole board first), so a correct digit elsewhere took this technique or a harder
 * one. Judged against the stored answer, which is sound because these generators prove one answer.
 */
class PlacementDrill(
    /** The teacher's next step on a board shaped like the state, from [cells] alone (the answer is never read). */
    private val stepper: (PuzzleState) -> (List<Int>) -> PlacementStep?,
    /** The state's digits and its stored answer. */
    val cellsOf: (PuzzleState) -> List<Int>,
    val solutionOf: (PuzzleState) -> List<Int>,
    /** The state with [cells] in place, every digit a given (so the player cannot erase the walk's digits). */
    private val withGivens: (PuzzleState, List<Int>) -> PuzzleState,
) : Drill {
    override fun walk(start: PuzzleState): Sequence<Course.Practice> = sequence {
        val next = stepper(start)
        val grid = cellsOf(start).toMutableList()
        while (0 in grid) {
            val step = next(grid) ?: break
            yield(Course.Practice(withGivens(start, grid.toList()), step))
            grid[step.cell] = step.digit
        }
    }

    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? {
        val was = cellsOf(p.board)
        val cells = cellsOf(after)
        val placed = cells.indices.firstOrNull { cells[it] != 0 && was[it] == 0 } ?: return null
        return cells[placed] == solutionOf(p.board)[placed]
    }
}

/**
 * For every other teacher (kings, crosses, tents, shading, turns...): a step is the [Deduction] the teacher's next step
 * makes, so the walk is the hints' own "Show me" ([Deduction.apply]) from the generated board, and a position is simply the
 * walked state. Nothing is locked: a mark the walk made can be rubbed out, and the practice then waits until the step is
 * made again.
 *
 * Judged by the teacher, never against the stored answer: wrong when the teacher calls the new board a mistake (a
 * teacher's mistake means "no legal answer keeps this", which is what LITS's and Pipes' several-answer boards need, PR
 * #15); right when the step's [Deduction.isReached] holds; otherwise the player plays on (one cross of a step that crosses
 * three, or a sound mark elsewhere). Only the step itself passes: on these boards a lone cross is cheap to guess, so
 * "another correct mark" would pass a guess, where a placed digit could not be.
 */
class MoveDrill(
    /**
     * The teacher's next step on the state as a [Deduction], from what the player can see: build it from the teacher's
     * visible-state entry point (one with no parameter for the answer), never from `teach`, which reads the answer.
     */
    private val next: (PuzzleState) -> Deduction?,
) : Drill {
    override fun walk(start: PuzzleState): Sequence<Course.Practice> = sequence {
        var s = start
        while (!s.solved) {
            val d = next(s) ?: break
            yield(Course.Practice(s, d))
            val moved = d.apply(s)
            if (moved == s) break
            s = moved
        }
    }

    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? = when {
        puzzle.teach(after)?.mistake == true -> false
        (p.step as Deduction).isReached(after) -> true
        else -> null
    }
}

/**
 * What the player has passed in Training, as `puzzleId|technique` (or `puzzleId|exam`). Its own key set in the
 * progress store, never on a save; an older store has none.
 */
class TrainingRecord(private val store: KeyValueStore) {
    val passed: Flow<Set<String>> = store.stringSet(KEY)

    suspend fun pass(puzzleId: String, technique: String) {
        store.updateStringSet(KEY) { it + entry(puzzleId, technique) }
    }

    companion object {
        const val KEY = "training_passed"
        fun entry(puzzleId: String, technique: String): String = "$puzzleId|$technique"
    }
}

/** The Training achievements that passing [technique] of [puzzleId] earns, given the record [before] it. */
fun newlyTrained(before: Set<String>, puzzleId: String, technique: String): List<Achievement> {
    val after = before + TrainingRecord.entry(puzzleId, technique)
    return ACHIEVEMENTS.filter { a -> a.trained?.let { !it(before) && it(after) } == true }
}
