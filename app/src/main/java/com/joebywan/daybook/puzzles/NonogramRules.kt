package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PenMarks
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * A Nonogram in progress: the picture to find, and what the player has marked so far.
 *
 * [solution] is the hidden picture, one character per cell, row by row: `1` filled, `0` empty. The
 * clues are not stored — they are the run lengths of that picture's rows and columns, derived on
 * demand ([NonogramLogic.rowClues]) — so a saved game cannot hold clues that disagree with its own
 * answer. [cells] is the player's marks in the same order: `.` untouched, `#` filled, `x` crossed
 * out ("this one is empty", a note to self that never counts towards the win).
 *
 * Strings rather than lists: a 15x15 board is two 225-character strings, and a saved game keeps
 * two dozen undo copies of it.
 */
@Serializable
data class NonogramState(
    val width: Int,
    val height: Int,
    val solution: String,
    val cells: String = NonogramLogic.UNMARKED.toString().repeat(width * height),
    override val moves: Int = 0,
) : PuzzleState, PenMarks<NonogramState> {

    /** Solved when the filled squares give every row and column the clue it was dealt. */
    override val solved: Boolean get() = NonogramLogic.isSolved(this)

    override val penMarks get() = cells
    override val penWidth get() = width
    override val penHeight get() = height
    override val pens get() = "${NonogramLogic.FILLED}${NonogramLogic.CROSSED}"

    override fun withPenMarks(marks: String, changed: Int) = copy(cells = marks, moves = moves + changed)
}

/**
 * The rules, the line solver and the generator. Nothing here touches the screen, the platform or
 * a hash container, so the same seed makes the same board on the phone and in the browser.
 *
 * ## What "line-solvable" means, and why it is the shipping standard
 * A line is a row or a column. With a line's clue and whatever is already marked in it, some
 * squares are the same in *every* way the clue can still be laid out: those are forced. A board is
 * line-solvable when repeating that on every line, using nothing but forced squares, fills in the
 * whole picture. Such a board has exactly one answer (every square was forced), a player can always
 * be shown the next step by looking at one line, and nobody ever has to guess.
 *
 * The generator draws random pictures and keeps the first that is line-solvable. A picture that
 * needs a guess is thrown away, however attractive.
 */
internal object NonogramLogic {

    const val UNMARKED = '.'
    const val FILLED = '#'
    const val CROSSED = 'x'

    /** A line's square, as the solver sees it. */
    const val UNKNOWN = 0
    const val FILL = 1
    const val CROSS = 2

    // ---- clues ----------------------------------------------------------------------------

    /** The run lengths of the filled squares in a line, left to right. Empty for a blank line. */
    fun runs(line: BooleanArray): List<Int> {
        val out = ArrayList<Int>()
        var run = 0
        for (v in line) {
            if (v) {
                run++
            } else if (run > 0) {
                out.add(run)
                run = 0
            }
        }
        if (run > 0) out.add(run)
        return out
    }

    /** Each row's clue, then each column's, read off a picture ([solution], `1` filled, reading order). */
    private fun rowsOf(width: Int, height: Int, solution: String) =
        List(height) { r -> runs(BooleanArray(width) { c -> solution[r * width + c] == '1' }) }

    private fun colsOf(width: Int, height: Int, solution: String) =
        List(width) { c -> runs(BooleanArray(height) { r -> solution[r * width + c] == '1' }) }

    fun rowClues(s: NonogramState): List<List<Int>> = rowsOf(s.width, s.height, s.solution)

    fun colClues(s: NonogramState): List<List<Int>> = colsOf(s.width, s.height, s.solution)

    /** Every line's clue: rows first, then columns (as [lineCells] numbers them). */
    fun clues(s: NonogramState): List<List<Int>> = rowClues(s) + colClues(s)

    /** A line's squares as indices into the board: rows first (0 until height), then columns (height until height + width). */
    fun lineCells(width: Int, height: Int, line: Int): List<Int> =
        if (line < height) List(width) { line * width + it } else List(height) { it * width + line - height }

    /** Highlight indices for the clues, which sit after the squares (see [NonogramTeacher]). */
    fun rowClueIndex(width: Int, height: Int, r: Int): Int = width * height + r

    fun colClueIndex(width: Int, height: Int, c: Int): Int = width * height + height + c

    // ---- the win check ----------------------------------------------------------------------

    /**
     * Whether the filled squares give every line its clue. The rules, not a comparison with
     * [NonogramState.solution]: a board whose clues several pictures satisfy accepts any of them.
     */
    fun isSolved(s: NonogramState): Boolean = clues(s).withIndex().all { (line, clue) -> lineDone(s, line, clue) }

    /** Whether the filled squares of [line] (rows first, then columns) make exactly [clue]. */
    fun lineDone(s: NonogramState, line: Int, clue: List<Int>): Boolean {
        val at = lineCells(s.width, s.height, line)
        return runs(BooleanArray(at.size) { s.cells[at[it]] == FILLED }) == clue
    }

    // ---- the line solver ------------------------------------------------------------------

    /** The smallest width a clue can be laid out in: its blocks and one gap between each. */
    fun minLength(clue: List<Int>): Int = if (clue.isEmpty()) 0 else clue.sum() + clue.size - 1

    /**
     * What every legal layout of [clue] in [line] has in common.
     *
     * [line] holds [UNKNOWN], [FILL] or [CROSS] per square. The result has the same length: [FILL]
     * or [CROSS] where every layout agrees and the square is still [UNKNOWN] in [line], [UNKNOWN]
     * everywhere else (including squares the player has already marked). Null when no layout fits
     * the marks at all, which only happens on a board with a wrong mark.
     *
     * The search is a table over (square, blocks placed): [fits] says whether the rest of the line
     * can still be completed from there, and a second pass from the start walks only the cells
     * that can be completed, noting which values each square takes.
     */
    fun forced(clue: List<Int>, line: IntArray): IntArray? {
        val n = line.size
        val k = clue.size
        // fits[i][j]: squares i.. can hold blocks j.., given that square i-1 is empty (or off the edge).
        val fits = Array(n + 1) { BooleanArray(k + 1) }
        fits[n][k] = true
        fun placeable(i: Int, len: Int): Boolean {
            if (i + len > n) return false
            for (x in i until i + len) if (line[x] == CROSS) return false
            return i + len == n || line[i + len] != FILL
        }
        for (i in n - 1 downTo 0) {
            for (j in 0..k) {
                var ok = line[i] != FILL && fits[i + 1][j]
                if (!ok && j < k && placeable(i, clue[j])) ok = fits[minOf(i + clue[j] + 1, n)][j + 1]
                fits[i][j] = ok
            }
        }
        if (!fits[0][0]) return null

        val canFill = BooleanArray(n)
        val canEmpty = BooleanArray(n)
        val reach = Array(n + 1) { BooleanArray(k + 1) }
        reach[0][0] = true
        for (i in 0 until n) {
            for (j in 0..k) {
                if (!reach[i][j] || !fits[i][j]) continue
                if (line[i] != FILL && fits[i + 1][j]) {
                    canEmpty[i] = true
                    reach[i + 1][j] = true
                }
                if (j < k && placeable(i, clue[j])) {
                    val next = minOf(i + clue[j] + 1, n)
                    if (fits[next][j + 1]) {
                        for (x in i until i + clue[j]) canFill[x] = true
                        if (i + clue[j] < n) canEmpty[i + clue[j]] = true
                        reach[next][j + 1] = true
                    }
                }
            }
        }
        val out = IntArray(n)
        for (i in 0 until n) {
            if (line[i] != UNKNOWN) continue
            out[i] = when {
                canFill[i] && !canEmpty[i] -> FILL
                canEmpty[i] && !canFill[i] -> CROSS
                else -> UNKNOWN
            }
        }
        return out
    }

    /**
     * True when no legal layout of [clue] holds the squares filled at [indices]: too many, a run
     * longer than any clue, or two runs with no gap. Crosses are notes and are ignored, and the
     * stored picture is never consulted, so a line that is merely unfinished is never impossible.
     */
    fun lineImpossible(clue: List<Int>, cells: String, indices: List<Int>): Boolean =
        forced(clue, IntArray(indices.size) { if (cells[indices[it]] == FILLED) FILL else UNKNOWN }) == null

    /** What every layout of [clue] agrees on in a blank line of [n] squares: the overlap of its slides. */
    fun forcedOnBlank(clue: List<Int>, n: Int): IntArray = forced(clue, IntArray(n))!!

    /** The player's marks on one line, as the solver reads them. */
    fun lineMarks(cells: String, indices: List<Int>): IntArray = IntArray(indices.size) {
        when (cells[indices[it]]) {
            FILLED -> FILL
            CROSSED -> CROSS
            else -> UNKNOWN
        }
    }

    /**
     * Solves [rows] x [cols] by lines alone, starting from [start] (squares as [UNKNOWN], [FILL]
     * or [CROSS]) and returns every square it could settle. Complete when no [UNKNOWN] is left;
     * null on a contradiction.
     */
    fun solveByLines(width: Int, height: Int, rows: List<List<Int>>, cols: List<List<Int>>, start: IntArray): IntArray? =
        settle(width, height, rows, cols, start)?.first

    /** [solveByLines], with the number of passes over every line that changed something. */
    private fun settle(width: Int, height: Int, rows: List<List<Int>>, cols: List<List<Int>>, start: IntArray): Pair<IntArray, Int>? {
        val grid = start.copyOf()
        var passes = 0
        var changed = true
        while (changed) {
            changed = false
            for (line in 0 until height + width) {
                val clue = if (line < height) rows[line] else cols[line - height]
                val at = lineCells(width, height, line)
                val marks = IntArray(at.size) { grid[at[it]] }
                val found = forced(clue, marks) ?: return null
                for (x in at.indices) {
                    if (found[x] != UNKNOWN) {
                        grid[at[x]] = found[x]
                        changed = true
                    }
                }
            }
            if (changed) passes++
        }
        return grid to passes
    }

    /** Whether a picture ([solution], row by row) can be finished by lines alone from a blank board. */
    fun lineSolvable(width: Int, height: Int, solution: String): Boolean = linePasses(width, height, solution) != null

    /**
     * How many passes over every row and column lines alone take to finish [solution] from a blank
     * board (one pass is each line's overlap alone), or null when lines alone cannot finish it.
     * The generator's depth gate ([Spec.minPasses]).
     */
    fun linePasses(width: Int, height: Int, solution: String): Int? {
        val (done, passes) = settle(width, height, rowsOf(width, height, solution), colsOf(width, height, solution), IntArray(width * height)) ?: return null
        return if (done.none { it == UNKNOWN }) passes else null
    }

    // ---- generation -----------------------------------------------------------------------

    /**
     * Side length, the share of squares drawn filled, and the fewest line passes ([linePasses]) a
     * board must need, per tier. The gate puts Hard and Expert apart in passes as well as size.
     */
    class Spec(val side: Int, val density: Double, val minPasses: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, 0.60, 2)
        Difficulty.HARD -> Spec(10, 0.62, 3)
        Difficulty.EXPERT -> Spec(15, 0.64, 4)
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 400

    /**
     * A line-solvable board for [seed] that needs the tier's [Spec.minPasses], or null when none of
     * [ATTEMPTS] draws was. Draw `a` uses
     * `Rng(seed + a)`, so a board that needed three attempts still depends on its seed alone.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): NonogramState? {
        val spec = specFor(difficulty)
        val n = spec.side
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val picture = drawPicture(rng, n, spec.density) ?: continue
            val passes = linePasses(n, n, picture) ?: continue
            if (passes >= spec.minPasses) return NonogramState(n, n, picture)
        }
        return null
    }

    /**
     * Only when none of [ATTEMPTS] draws was line-solvable, which a year of daily seeds on every
     * tier never reaches (`NonogramRulesTest`). A fixed diagonal pattern, checked line-solvable at
     * every tier's size by the same test, so even this board is one a player can finish without
     * guessing.
     */
    fun lastResort(difficulty: Difficulty): NonogramState {
        val n = specFor(difficulty).side
        val picture = (0 until n * n).joinToString("") { i ->
            if ((i / n + i % n) % 3 != 2 || i % n == 0) "1" else "0"
        }
        return NonogramState(n, n, picture)
    }

    /**
     * Random squares at [density]. Rejected when any line is blank or full (a clue of "0" or of the
     * whole width needs no thought and makes a board look broken), when the board is a long way from
     * the tier's density, or when it reads the same mirrored either way (half of it would give the
     * rest away: mirrored pictures were tried and dropped).
     */
    private fun drawPicture(rng: Rng, n: Int, density: Double): String? {
        val threshold = (density * 1000).toInt()
        val cells = BooleanArray(n * n) { rng.nextInt(1000) < threshold }
        val total = cells.count { it }
        if (total < n * n * 0.45 || total > n * n * 0.8) return null
        for (r in 0 until n) {
            val row = (0 until n).count { cells[r * n + it] }
            val col = (0 until n).count { cells[it * n + r] }
            if (row == 0 || row == n || col == 0 || col == n) return null
        }
        val across = (0 until n * n).all { cells[it] == cells[it - it % n + n - 1 - it % n] }
        val down = (0 until n * n).all { cells[it] == cells[(n - 1 - it / n) * n + it % n] }
        if (across || down) return null
        return cells.joinToString("") { if (it) "1" else "0" }
    }
}
