package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.countMaskSolutions
import com.joebywan.daybook.core.fullMask
import kotlinx.serialization.Serializable

/**
 * A Reckon board in play: an n-by-n Latin square (6x6 on every tier, 4x4 in the walkthrough) cut into cages, each with
 * an operation and a target. [cells] holds 0 for an empty square; a one-square cage (op [ReckonLogic.GIVEN]) is a given,
 * already filled. [cageOf] numbers the cages by their first square in reading order; [ops] holds one character per
 * cage (`+ - * / =`) and [targets] one number per cage. [notes] are the player's pencil marks, Sudoku's encoding:
 * bit `d - 1` for digit d. Field names are a save format: never rename one.
 */
@Serializable
data class ReckonState(
    val size: Int,
    val cells: List<Int>,
    val solution: List<Int>,
    val cageOf: List<Int>,
    val ops: String,
    val targets: List<Int>,
    val notes: List<Int> = List(size * size) { 0 },
    val selected: Int? = null,
    override val moves: Int = 0,
    /** Squares a Training practice fixed as given (the walk's digits so far), beside the one-square cages; empty in play. */
    val locked: List<Int> = emptyList(),
) : PuzzleState {

    /** The rules, never [solution]: every line 1..n once and every cage making its target. */
    override val solved: Boolean get() = ReckonLogic.isSolved(size, cells, cageOf, ops, targets)

    /** Whether [index] is a given (a one-square cage, or a square a practice [locked]), which can never change. */
    fun isGiven(index: Int): Boolean = ops[cageOf[index]] == ReckonLogic.GIVEN || index in locked

    /** Squares the board shows to be wrong: repeats in a line, and squares of a cage that cannot make its target. */
    fun conflicts(): Set<Int> = ReckonLogic.clashes(size, cells) + ReckonLogic.cageConflicts(size, cells, cageOf, ops, targets)

    /** The notes drawn on [index]: none on a filled square. */
    fun visibleNotes(index: Int): Int = if (cells[index] != 0) 0 else notes[index]

    fun hasNote(index: Int, digit: Int): Boolean = visibleNotes(index) and (1 shl (digit - 1)) != 0

    fun canNote(index: Int): Boolean = !isGiven(index) && cells[index] == 0

    /**
     * Sets a digit, or clears the square with 0. The square's own notes go either way; a placed digit is also struck from
     * the notes of its row and column, in this same state, so one undo restores both. `this` when nothing changes.
     */
    fun withCell(index: Int, value: Int): ReckonState {
        if (isGiven(index) || cells[index] == value) return this
        val n = size
        val next = notes.toMutableList()
        next[index] = 0
        if (value != 0) {
            val strike = (1 shl (value - 1)).inv()
            for (j in next.indices) if (j != index && (j / n == index / n || j % n == index % n)) next[j] = next[j] and strike
        }
        return copy(
            cells = cells.toMutableList().also { it[index] = value },
            notes = if (next == notes) notes else next,
            moves = moves + 1,
        )
    }

    /** Adds the note for [digit] on [index], or takes it off; never refused on an open square. */
    fun toggleNote(index: Int, digit: Int): ReckonState =
        if (!canNote(index)) this
        else copy(notes = notes.toMutableList().also { it[index] = it[index] xor (1 shl (digit - 1)) }, moves = moves + 1)

    fun select(index: Int): ReckonState = copy(selected = index)

    /** What a pad key or a typed digit does to [cell]: a note in notes mode, else the digit, or clears it if it is there. */
    fun enter(cell: Int, digit: Int, notesMode: Boolean): ReckonState =
        if (notesMode) toggleNote(cell, digit) else withCell(cell, if (cells[cell] == digit) 0 else digit)
}

/**
 * Reckon's rules, the cages' arithmetic, the exhaustive count and the generator.
 *
 * Squares are numbered row by row; a digit's bit in a candidate mask is `1 shl digit` (`core/Bits.kt`).
 * A cage may repeat a digit only in squares that share no row or column (the Latin square forbids the rest), so a
 * cage's ways depend on its shape. Every loop walks squares, cages and lines in index order and no hash container
 * reaches the [Rng] or a choice (the web walks hashes differently).
 */
internal object ReckonLogic {

    const val PLUS = '+'
    const val MINUS = '-'
    const val TIMES = '*'
    const val DIVIDE = '/'
    const val GIVEN = '='

    /** The operations a board must show (all four, on every tier). */
    val OPERATIONS = listOf(PLUS, MINUS, TIMES, DIVIDE)

    /** How the board shows [op]. */
    fun symbol(op: Char): String = when (op) {
        PLUS -> "+"
        MINUS -> "−"
        TIMES -> "×"
        DIVIDE -> "÷"
        else -> ""
    }

    /** A cage's label as drawn: the target and its sign, a given's digit alone. */
    fun label(op: Char, target: Int): String = "$target${symbol(op)}"

    /** Most squares a `×` cage may have, so its label stays at three digits (6·6·5·5 = 900 at most). */
    const val MAX_TIMES = 4

    /** The squares of every cage, each in reading order. */
    fun cages(cageOf: List<Int>): List<List<Int>> {
        val out = List(cageOf.max() + 1) { mutableListOf<Int>() }
        for (i in cageOf.indices) out[cageOf[i]] += i
        return out
    }

    /** Whether [digits] (all filled) make [target] by [op]. */
    fun makes(op: Char, target: Int, digits: List<Int>): Boolean = when (op) {
        PLUS -> digits.sum() == target
        TIMES -> digits.fold(1) { p, d -> p * d } == target
        MINUS -> digits.size == 2 && maxOf(digits[0], digits[1]) - minOf(digits[0], digits[1]) == target
        DIVIDE -> digits.size == 2 && minOf(digits[0], digits[1]).let { lo -> lo > 0 && maxOf(digits[0], digits[1]) == lo * target }
        else -> digits.size == 1 && digits[0] == target
    }

    /** Filled squares that repeat a digit in their row or column. */
    fun clashes(n: Int, cells: List<Int>): Set<Int> = InequalityLogic.clashes(n, cells)

    /**
     * The squares of every cage that can no longer make its target: a full cage that misses it, or a part-filled one no
     * filling of its open squares can finish (its own digits and shape alone; the rows and columns are not consulted,
     * so this never says more than the cage itself shows).
     */
    fun cageConflicts(n: Int, cells: List<Int>, cageOf: List<Int>, ops: String, targets: List<Int>): Set<Int> {
        val bad = mutableSetOf<Int>()
        for ((c, sq) in cages(cageOf).withIndex()) {
            if (sq.all { cells[it] == 0 }) continue
            val cand = IntArray(sq.size) { k -> if (cells[sq[k]] != 0) bit(cells[sq[k]]) else fullMask(n) }
            if (ways(n, sq, ops[c], targets[c], cand).isEmpty()) bad += sq
        }
        return bad
    }

    /** The rules and nothing else: a full Latin square whose every cage makes its target. */
    fun isSolved(n: Int, cells: List<Int>, cageOf: List<Int>, ops: String, targets: List<Int>): Boolean =
        cells.size == n * n && cells.all { it in 1..n } && clashes(n, cells).isEmpty() &&
            cages(cageOf).withIndex().all { (c, sq) -> makes(ops[c], targets[c], sq.map { cells[it] }) }

    /**
     * Every filling of the cage [squares] (in their order) that makes [target] by [op], each square's digit drawn from
     * its mask in [cand] (indexed like [squares]), a digit repeated only in squares that share no line. Exhaustive.
     */
    fun ways(n: Int, squares: List<Int>, op: Char, target: Int, cand: IntArray): List<IntArray> {
        val k = squares.size
        val out = ArrayList<IntArray>()
        val cur = IntArray(k)
        fun clash(a: Int, b: Int) = squares[a] / n == squares[b] / n || squares[a] % n == squares[b] % n
        fun go(pos: Int, acc: Int) {
            if (pos == k) {
                val done = if (op == PLUS || op == TIMES) acc == target else makes(op, target, cur.toList())
                if (done) out += cur.copyOf()
                return
            }
            val left = k - pos - 1
            var m = cand[pos]
            while (m != 0) {
                val low = m and -m
                m -= low
                val d = low.countTrailingZeroBits()
                if ((0 until pos).any { cur[it] == d && clash(it, pos) }) continue
                val next = when (op) {
                    PLUS -> acc + d
                    TIMES -> acc * d
                    else -> 0
                }
                if (op == PLUS && (next + left > target || next + left * n < target)) continue
                if (op == TIMES && (target % next != 0)) continue
                cur[pos] = d
                go(pos + 1, next)
            }
        }
        go(0, if (op == TIMES) 1 else 0)
        return out
    }

    // ---- proof -----------------------------------------------------------------------------------------------

    /**
     * Every answer of the board, counted to [limit]: candidate masks, a settled digit struck from its lines, each cage
     * cut to the digits its ways allow, then a split on the square with fewest digits. No budget, so
     * [Verdict.ExactlyOne] is a proof, and only it carries the answer. [cells] holds 0 for an open square.
     */
    fun solutions(n: Int, cells: List<Int>, cageOf: List<Int>, ops: String, targets: List<Int>, limit: Int = 2): Verdict<List<Int>> {
        val cageList = cages(cageOf)
        var answer: List<Int>? = null
        fun settle(c: IntArray): Boolean {
            do {
                var changed = false
                for (i in c.indices) {
                    if (c[i].countOneBits() != 1) continue
                    for (j in c.indices) {
                        if (j == i || (j / n != i / n && j % n != i % n) || c[j] and c[i] == 0) continue
                        c[j] = c[j] and c[i].inv()
                        if (c[j] == 0) return false
                        changed = true
                    }
                }
                for ((k, sq) in cageList.withIndex()) {
                    val ws = ways(n, sq, ops[k], targets[k], IntArray(sq.size) { c[sq[it]] })
                    if (ws.isEmpty()) return false
                    for (p in sq.indices) {
                        val m = ws.fold(0) { acc, w -> acc or bit(w[p]) }
                        if (m != c[sq[p]]) { c[sq[p]] = m; changed = true }
                    }
                }
            } while (changed)
            if (c.all { it.countOneBits() == 1 }) answer = c.map { it.countTrailingZeroBits() }
            return true
        }
        val start = IntArray(n * n) { if (cells[it] != 0) bit(cells[it]) else fullMask(n) }
        val found = countMaskSolutions(start, limit, ::settle)
        return Verdict.of(found) { answer!! }
    }

    // ---- generator -------------------------------------------------------------------------------------------

    /**
     * A tier: the biggest cage [cap], how many one-square cages are given ([givens]), the ladder the walk may use
     * ([deepest], a rung of [ReckonTeacher.TECHNIQUES]) and the rung it must reach ([needs]), as Tally's `Spec.needs`.
     */
    class Spec(val cap: Int, val givens: IntRange, val deepest: Int, val needs: Int)

    const val SIZE = 6

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(3, 3..5, ReckonTeacher.rung(ReckonTeacher.CAGE_PLACE), -1)
        Difficulty.HARD -> Spec(4, 1..2, ReckonTeacher.rung(ReckonTeacher.NAKED_PAIR), ReckonTeacher.rung(ReckonTeacher.CAGE_LINE))
        Difficulty.EXPERT -> Spec(5, 0..0, ReckonTeacher.rung(ReckonTeacher.WHAT_IF), ReckonTeacher.rung(ReckonTeacher.WHAT_IF))
    }

    /** Retries with `Rng(seed + attempt)` until a draw meets its tier's [Spec]. */
    const val ATTEMPTS = 2000

    /** Null when nothing was proved. Pure in (seed, difficulty). */
    fun generateVerified(seed: Long, difficulty: Difficulty): ReckonState? {
        val spec = specFor(difficulty)
        return (0 until ATTEMPTS).firstNotNullOfOrNull { draw(seed + it, SIZE, spec) }
    }

    /** One attempt: the board `Rng(seed)` draws, if it meets [spec] and its answer is proved; else null. */
    fun draw(seed: Long, n: Int, spec: Spec): ReckonState? {
        val rng = Rng(seed)
        val solution = InequalityLogic.randomSquare(n, rng)
        val cageOf = carve(n, spec, rng) ?: return null
        val (ops, targets) = operations(n, cageOf, solution, rng) ?: return null
        val cells = List(n * n) { if (ops[cageOf[it]] == GIVEN) solution[it] else 0 }
        val depth = ReckonTeacher.depth(n, cells, cageOf, ops, targets, spec.deepest) ?: return null
        if (depth < spec.needs) return null
        // The ladder is sound for every answer, so finishing proves one; the count is a cheap second opinion.
        if (solutions(n, cells, cageOf, ops, targets) !is Verdict.ExactlyOne) return null
        return ReckonState(n, cells, solution, cageOf, ops, targets)
    }

    private fun neighbours(n: Int, i: Int): List<Int> = buildList {
        if (i >= n) add(i - n)
        if (i % n > 0) add(i - 1)
        if (i % n < n - 1) add(i + 1)
        if (i < n * n - n) add(i + n)
    }

    /**
     * Cages for an n-by-n grid, numbered by first square: [Spec.givens] one-square cages placed at random, then each
     * cage grown from the first uncovered square in reading order to a drawn size of 2 to [Spec.cap]; a square left on
     * its own joins a neighbouring cage with room. Null when one cannot.
     */
    fun carve(n: Int, spec: Spec, rng: Rng): List<Int>? {
        val owner = IntArray(n * n) { -1 }
        val givens = rng.nextInt(spec.givens.first, spec.givens.last)
        val grown = mutableListOf<MutableList<Int>>()
        for (i in rng.shuffled((0 until n * n).toList()).take(givens)) { owner[i] = grown.size; grown += mutableListOf(i) }
        val given = grown.size
        for (i in 0 until n * n) {
            if (owner[i] >= 0) continue
            val size = minOf(rng.nextInt(2, spec.cap), rng.nextInt(2, spec.cap))
            val cage = mutableListOf(i)
            owner[i] = grown.size
            while (cage.size < size) {
                val frontier = mutableListOf<Int>()
                for (j in cage) for (o in neighbours(n, j)) if (owner[o] < 0 && o !in frontier) frontier += o
                if (frontier.isEmpty()) break
                val o = rng.pick(frontier.sorted())
                owner[o] = grown.size
                cage += o
            }
            grown += cage
        }
        for (c in given until grown.size) {
            if (grown[c].size != 1) continue
            val i = grown[c][0]
            val room = neighbours(n, i).map { owner[it] }.filter { it >= given && it != c && grown[it].size in 2 until spec.cap }.distinct().sorted()
            if (room.isEmpty()) return null
            val to = rng.pick(room)
            grown[to] += i
            owner[i] = to
            grown[c].clear()
        }
        // Number by first square in reading order.
        val renumber = IntArray(grown.size) { -1 }
        var next = 0
        val out = MutableList(n * n) { 0 }
        for (i in 0 until n * n) {
            val c = owner[i]
            if (renumber[c] < 0) renumber[c] = next++
            out[i] = renumber[c]
        }
        return out
    }

    /**
     * An operation per cage from those its digits allow (`÷` only when whole, `×` only up to [MAX_TIMES] squares, `−` and
     * `÷` only on two squares), drawn uniformly, then moved so every operation appears: a missing one goes to the first
     * (by a drawn pick) cage that allows it whose own operation appears twice or more. Null when no move can do that.
     */
    fun operations(n: Int, cageOf: List<Int>, solution: List<Int>, rng: Rng): Pair<String, List<Int>>? {
        val cageList = cages(cageOf)
        fun allowed(sq: List<Int>): List<Char> {
            val d = sq.map { solution[it] }
            return when {
                sq.size == 1 -> listOf(GIVEN)
                sq.size == 2 -> listOfNotNull(PLUS, MINUS, TIMES, if (maxOf(d[0], d[1]) % minOf(d[0], d[1]) == 0) DIVIDE else null)
                sq.size <= MAX_TIMES -> listOf(PLUS, TIMES)
                else -> listOf(PLUS)
            }
        }
        val ops = cageList.map { sq ->
            val choices = allowed(sq)
            var roll = rng.nextInt(choices.sumOf { WEIGHT.getValue(it) })
            choices.first { roll -= WEIGHT.getValue(it); roll < 0 }
        }.toCharArray()
        for (op in OPERATIONS) {
            if (op in ops) continue
            val movable = cageList.indices.filter { c -> op in allowed(cageList[c]) && ops.count { it == ops[c] } >= 2 }
            if (movable.isEmpty()) return null
            ops[rng.pick(movable)] = op
        }
        if (OPERATIONS.any { it !in ops }) return null
        val targets = cageList.mapIndexed { c, sq -> target(ops[c], sq.map { solution[it] }) }
        return String(ops) to targets
    }

    /**
     * How often each allowed operation is drawn: the ones that say more about their digits are drawn more, since a
     * board of `+` cages rarely has one answer, let alone one the ladder can find.
     */
    private val WEIGHT = mapOf(GIVEN to 1, PLUS to 2, TIMES to 3, MINUS to 3, DIVIDE to 4)

    private fun target(op: Char, d: List<Int>): Int = when (op) {
        PLUS -> d.sum()
        TIMES -> d.fold(1) { p, x -> p * x }
        MINUS -> maxOf(d[0], d[1]) - minOf(d[0], d[1])
        DIVIDE -> maxOf(d[0], d[1]) / minOf(d[0], d[1])
        else -> d[0]
    }

    /** The first seed from 1 the tier accepts: what ships if [generateVerified] proves nothing (no year reaches it). */
    fun lastResort(difficulty: Difficulty): ReckonState {
        val spec = specFor(difficulty)
        return generateSequence(1L) { it + 1 }.firstNotNullOf { draw(it, SIZE, spec) }
    }
}
