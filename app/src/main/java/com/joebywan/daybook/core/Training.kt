package com.joebywan.daybook.core

import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import kotlinx.coroutines.flow.Flow

/**
 * Training, the Sudoku pilot (`docs/TRAINING_SPEC.md`): each technique Sudoku's teacher names, a short explanation in
 * the hints' voice, and practice boards on which the next hint step is exactly that technique. Pass a technique with
 * [RUN] right in a row; pass them all, then solve [exam], a board whose own walk needs every one.
 *
 * Pure and platform-free (the web compiles it). Practice boards are found, not stored: real generated boards walked by
 * the teacher's ladder from their givens, in seed order, so the same index is the same board everywhere.
 */
object SudokuTraining {
    const val PUZZLE = "sudoku"

    /** Right answers in a row that pass a technique. A hinted board does not count, nor break the run. */
    const val RUN = 3

    /** The training record's entry for the exam, beside the techniques' ids. */
    const val EXAM = "exam"

    /** The ladder's techniques, simplest first: the order the teacher tries them and the list shows them. */
    val TECHNIQUES: List<String> = techniqueNamesOf(PUZZLE).keys.toList()

    /** What each technique is, before the first board. The hints' voice: plain, short, "cell", "box". */
    val EXPLANATIONS: Map<String, String> = mapOf(
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
    )

    /**
     * The tier whose boards a technique's practice is drawn from: the easiest tier whose walks reach it, since the
     * generator gates each tier's depth (`Sudoku.gate`): Standard needs singles in a unit only, Hard a naked single,
     * Expert an elimination or a what-if.
     */
    private fun tierFor(technique: String): Difficulty = when (TECHNIQUES.indexOf(technique)) {
        in 0..2 -> Difficulty.STANDARD
        3 -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }

    /** Training's seeds are their own range, far from any daily's hash, so no practice board is somebody's daily. */
    private const val SEED_BASE = 7_000_000L

    /**
     * Between one practice seed and the next. `Sudoku.generateVerified` tries `Rng(seed + attempt)` for up to
     * `Sudoku.ATTEMPTS`, so neighbouring seeds share grids and would deal the same board twice; this keeps them apart.
     */
    private const val STRIDE = 1_000L

    internal fun seed(k: Int): Long = SEED_BASE + k * STRIDE

    /**
     * Seeds tried before giving up on a technique. Measured: every technique's first 30 seeds give 30 distinct practices
     * (a hidden pair, the slowest, in about 1 s on a desktop JVM for all 30). Each index re-walks the seeds before it.
     */
    private const val SEARCH = 4000

    /** One practice: [board], whose next hint step is [step] (that technique, from what is visible). */
    class Practice(val board: SudokuState, val step: PlacementStep)

    /**
     * The practices for [technique], in order: per seed, the first position on that board's walk where the teacher's next
     * step is [technique]. Positions are the walk's own digits written in as givens, so the player cannot erase them.
     */
    fun practices(technique: String): Sequence<Practice> = sequence {
        val tier = tierFor(technique)
        for (k in 0 until SEARCH) {
            val start = Sudoku.generate(seed(k), tier) as SudokuState
            positionOn(start, technique)?.let { yield(it) }
        }
    }

    /**
     * The [index]th practice for [technique], wrapping round if the search ran out. Null only if it found none.
     * ponytail: re-walks every board before [index]; cache the walk if players run past a few dozen boards.
     */
    fun practice(technique: String, index: Int): Practice? {
        val found = practices(technique).take(index + 1).toList()
        return found.getOrNull(index) ?: found.getOrNull(index % found.size.coerceAtLeast(1))
    }

    private fun positionOn(start: SudokuState, technique: String): Practice? {
        val grid = start.cells.toMutableList()
        while (0 in grid) {
            val step = SudokuTeacher.deduce(grid) ?: return null
            if (step.technique == technique) {
                val board = SudokuState(givens = grid.map { it != 0 }, cells = grid.toList(), solution = start.solution)
                return Practice(board, step)
            }
            grid[step.cell] = step.digit
        }
        return null
    }

    /** The techniques a walk by the teacher's ladder from [cells] uses. */
    fun techniquesUsed(cells: List<Int>): Set<String> {
        val grid = cells.toMutableList()
        val used = LinkedHashSet<String>()
        while (0 in grid) {
            val step = SudokuTeacher.deduce(grid) ?: return used
            used += step.technique
            grid[step.cell] = step.digit
        }
        return used
    }

    /**
     * The final board's seed: the first practice seed whose Expert board's walk needs every technique (index 219; one
     * board in the first 400 does). Pinned by `SudokuTrainingTest`.
     */
    internal val EXAM_SEED = seed(219)

    /** The final board: a whole Expert Sudoku whose walk needs every technique in [TECHNIQUES]. */
    fun exam(): SudokuState = Sudoku.generate(EXAM_SEED, Difficulty.EXPERT) as SudokuState
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

/** The Training achievements that passing [technique] earns, given the record [before] it. */
fun newlyTrained(before: Set<String>, technique: String): List<Achievement> {
    val after = before + TrainingRecord.entry(SudokuTraining.PUZZLE, technique)
    return ACHIEVEMENTS.filter { a -> a.trained?.let { !it(before) && it(after) } == true }
}
