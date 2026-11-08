package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Reach
import com.joebywan.daybook.core.Reached
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Search
import com.joebywan.daybook.core.SearchVerdict
import kotlinx.serialization.Serializable

/**
 * One car of the yard: its top-left square ([row], [col]), [length] squares long, lying [across] (sliding left and right)
 * or down (sliding up and down). Field names and meanings are fixed once shipped (saves).
 */
@Serializable
data class UnblockCar(val row: Int, val col: Int, val length: Int, val across: Boolean) {
    /** Its squares, `row * SIZE + col`, from the top-left. */
    fun cells(): List<Int> = List(length) { k ->
        if (across) row * UnblockLogic.SIZE + col + k else (row + k) * UnblockLogic.SIZE + col
    }

    /** The car moved [by] squares along its own axis. */
    fun moved(by: Int): UnblockCar = if (across) copy(col = col + by) else copy(row = row + by)
}

/** One move: car [car] slid [by] squares (negative is left or up). */
data class UnblockMove(val car: Int, val by: Int)

/**
 * An Unblock board: [cars] on a [UnblockLogic.SIZE]-square yard, car 0 the marked car, lying across the exit row. [par]
 * is the fewest moves the generator proved from the start (metadata, for the share card and the tests; the teacher never
 * reads it, it searches the board afresh). A move is one car slid any distance, and nothing else is stored.
 */
@Serializable
data class UnblockState(
    val cars: List<UnblockCar>,
    val par: Int,
    override val moves: Int = 0,
) : PuzzleState {
    override val solved: Boolean get() = UnblockLogic.isSolved(cars)

    /** The board after car [car] slides [by] squares, one move, or null when the rules refuse it (a car or the edge in the way, or no slide at all). */
    fun slide(car: Int, by: Int): UnblockState? {
        if (by == 0 || car !in cars.indices) return null
        val (lo, hi) = UnblockLogic.room(cars, car)
        if (by !in lo..hi) return null
        return copy(cars = UnblockLogic.slide(cars, car, by), moves = moves + 1)
    }
}

/**
 * Unblock's rules, and the search that proves each board's minimum (`docs/TODO.md`, "Unblock").
 *
 * The yard is [SIZE] x [SIZE]; cars are 2 or 3 squares long and slide only along their own axis, any distance, over empty
 * squares. The marked car (car 0) lies across row [EXIT_ROW]; the board is solved when it reaches the exit in the right
 * edge of that row. A slide is undone by sliding back, so every board made here stays solvable whatever is played: there
 * are no mistakes, and many answers.
 *
 * **Generation.** A random yard (no other car across the exit row, which could never let the marked car by), then the
 * whole space of positions it can reach ([Search.reach], at most [SPACE_CAP]); every position in it with the marked car at
 * the exit is a goal, and a second breadth-first pass from all of them gives each position its fewest moves. The start is
 * drawn among the positions at the tier's distance (the farthest the space has, capped at [Spec.most]), and the board
 * ships only if a fresh [Search.shortest] from it says [SearchVerdict.Solvable] with exactly that many moves: the minimum
 * is proved by a search that knows nothing of the first two. Pure Kotlin; positions are arrays walked in index order, so
 * no hash order reaches the [Rng].
 */
internal object UnblockLogic {

    const val SIZE = 6
    const val EXIT_ROW = 2

    /** Positions a yard's space may hold; a yard with more is redrawn. Every hint search fits inside it (the same space). */
    const val SPACE_CAP = 12_000

    /** Positions a search from a board may see: the whole space and more, so a hint never runs out. */
    const val SEARCH_BUDGET = 100_000

    const val ATTEMPTS = 5_000

    /** [cars] in the yard (the marked car included), [trucks] of them 3 long, and the fewest moves a start must need, [least]..[most]. */
    class Spec(val cars: IntRange, val trucks: IntRange, val least: Int, val most: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(10..11, 1..2, 6, 10)
        Difficulty.HARD -> Spec(12..13, 2..3, 13, 18)
        Difficulty.EXPERT -> Spec(13..14, 3..3, 22, 60)
    }

    // ---- the rules ----------------------------------------------------------------------------

    /** Square -> the car on it, or -1. */
    fun owners(cars: List<UnblockCar>): IntArray {
        val o = IntArray(SIZE * SIZE) { -1 }
        for ((i, c) in cars.withIndex()) for (q in c.cells()) o[q] = i
        return o
    }

    /** How far car [i] can slide: (most back, most forward), each 0 when blocked. */
    fun room(cars: List<UnblockCar>, i: Int): Pair<Int, Int> {
        val o = owners(cars)
        val c = cars[i]
        fun free(r: Int, col: Int) = r in 0 until SIZE && col in 0 until SIZE && o[r * SIZE + col] < 0
        var back = 0
        var fwd = 0
        if (c.across) {
            while (free(c.row, c.col - back - 1)) back++
            while (free(c.row, c.col + c.length + fwd)) fwd++
        } else {
            while (free(c.row - back - 1, c.col)) back++
            while (free(c.row + c.length + fwd, c.col)) fwd++
        }
        return -back to fwd
    }

    fun slide(cars: List<UnblockCar>, i: Int, by: Int): List<UnblockCar> = cars.mapIndexed { j, c -> if (j == i) c.moved(by) else c }

    /** Every slide the rules allow, car by car, back before forward, nearest first. */
    fun slides(cars: List<UnblockCar>): List<UnblockMove> = cars.indices.flatMap { i ->
        val (lo, hi) = room(cars, i)
        (-1 downTo lo).map { UnblockMove(i, it) } + (1..hi).map { UnblockMove(i, it) }
    }

    /** The marked car at the exit. */
    fun isSolved(cars: List<UnblockCar>): Boolean {
        val m = cars.firstOrNull() ?: return false
        return m.across && m.row == EXIT_ROW && m.col + m.length == SIZE
    }

    /** Whether [cars] is a legal yard: every car inside, none overlapping, the marked car across the exit row. */
    fun isLegal(cars: List<UnblockCar>): Boolean {
        if (cars.isEmpty() || !cars[0].across || cars[0].row != EXIT_ROW) return false
        val seen = BooleanArray(SIZE * SIZE)
        for (c in cars) {
            if (c.length !in 2..3 || c.row < 0 || c.col < 0) return false
            if (c.across && c.col + c.length > SIZE || !c.across && c.row + c.length > SIZE || c.row >= SIZE || c.col >= SIZE) return false
            for (q in c.cells()) { if (seen[q]) return false; seen[q] = true }
        }
        return true
    }

    // ---- the search ---------------------------------------------------------------------------

    /**
     * The yard's fixed part: per car, whether it lies across, its fixed line (row or column) and length. A position is an
     * [IntArray] of each car's place along its own line (column for an across car, row for one lying down). Packing is for
     * speed only; [room] and [slide] above are the definition.
     */
    class Yard(cars: List<UnblockCar>) {
        val n = cars.size
        val across = BooleanArray(n) { cars[it].across }
        val line = IntArray(n) { if (cars[it].across) cars[it].row else cars[it].col }
        val length = IntArray(n) { cars[it].length }

        init {
            require(n <= 21) { "too many cars: $n" }
        }

        fun position(cars: List<UnblockCar>): Long {
            var p = 0L
            for (i in 0 until n) p = p or ((if (across[i]) cars[i].col else cars[i].row).toLong() shl (3 * i))
            return p
        }

        fun place(p: Long, i: Int): Int = ((p ushr (3 * i)) and 7L).toInt()

        fun cars(p: Long): List<UnblockCar> = List(n) { i ->
            if (across[i]) UnblockCar(line[i], place(p, i), length[i], true) else UnblockCar(place(p, i), line[i], length[i], false)
        }

        fun solved(p: Long) = place(p, 0) + length[0] == SIZE

        private fun square(i: Int, at: Int) = if (across[i]) line[i] * SIZE + at else at * SIZE + line[i]

        /** Every slide from [p], in [slides]' order, with the position it makes. */
        fun moves(p: Long, emit: (UnblockMove, Long) -> Unit) {
            var o = 0L
            for (i in 0 until n) {
                val at = place(p, i)
                for (k in 0 until length[i]) o = o or (1L shl square(i, at + k))
            }
            for (i in 0 until n) {
                val at = place(p, i)
                val clear = p and (7L shl (3 * i)).inv()
                var to = at - 1
                while (to >= 0 && (o ushr square(i, to)) and 1L == 0L) {
                    emit(UnblockMove(i, to - at), clear or (to.toLong() shl (3 * i)))
                    to--
                }
                to = at + 1
                while (to + length[i] - 1 < SIZE && (o ushr square(i, to + length[i] - 1)) and 1L == 0L) {
                    emit(UnblockMove(i, to - at), clear or (to.toLong() shl (3 * i)))
                    to++
                }
            }
        }
    }

    /** The fewest slides from [cars] to the exit, breadth first: [SearchVerdict.Solvable] with one such road. */
    fun solve(cars: List<UnblockCar>, budget: Int = SEARCH_BUDGET): SearchVerdict<UnblockMove> {
        val yard = Yard(cars)
        return Search.shortest<Long, Long, UnblockMove>(yard.position(cars), budget, { it }, yard::moves) { p ->
            if (yard.solved(p)) Reached.GOAL else Reached.EXPAND
        }
    }

    /** Every position [cars] can reach, each with its fewest moves to the exit; null when the space is over [cap] or has no exit. */
    fun space(cars: List<UnblockCar>, cap: Int = SPACE_CAP): Pair<Yard, Reach<Long>>? {
        val yard = Yard(cars)
        val step: (Long, (Long) -> Unit) -> Unit = { p, emit -> yard.moves(p) { _, c -> emit(c) } }
        val all = Search.reach(listOf(yard.position(cars)), cap, { it }, step) ?: return null
        val goals = all.positions.filter { yard.solved(it) }
        if (goals.isEmpty()) return null
        val back = Search.reach(goals, cap, { it }, step) ?: return null
        return yard to back
    }

    // ---- generation ---------------------------------------------------------------------------

    /**
     * Attempt [rng]'s yard: the marked car on the exit row, then [crossing] cars lying down across the exit row, then cars
     * dropped on free squares, the first [trucks] of all of them 3 long, until [count] are down; null when they would not fit.
     */
    fun randomYard(rng: Rng, count: Int, trucks: Int, crossing: Int = 0): List<UnblockCar>? {
        val cars = mutableListOf(UnblockCar(EXIT_ROW, rng.nextInt(SIZE - 1), 2, true))
        val taken = BooleanArray(SIZE * SIZE)
        cars[0].cells().forEach { taken[it] = true }
        var tries = 0
        while (cars.size < count && tries++ < 400) {
            val length = if (cars.size <= trucks) 3 else 2
            val car = if (cars.size <= crossing) {
                UnblockCar(EXIT_ROW - rng.nextInt(length), rng.nextInt(SIZE), length, false)
            } else if (rng.nextBoolean()) {
                val row = rng.nextInt(SIZE)
                if (row == EXIT_ROW) continue
                UnblockCar(row, rng.nextInt(SIZE - length + 1), length, true)
            } else UnblockCar(rng.nextInt(SIZE - length + 1), rng.nextInt(SIZE), length, false)
            if (car.cells().any { taken[it] }) continue
            car.cells().forEach { taken[it] = true }
            cars += car
        }
        return if (cars.size == count) cars else null
    }

    /** The board of [seed]'s first attempt whose space has a start at the tier's distance, proved by [solve]; null when none did. */
    fun generateVerified(seed: Long, difficulty: Difficulty): UnblockState? {
        val spec = specFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val count = rng.nextInt(spec.cars.first, spec.cars.last)
            val trucks = rng.nextInt(spec.trucks.first, spec.trucks.last)
            val yard = randomYard(rng, count, trucks) ?: continue
            val board = pick(yard, spec, rng) ?: continue
            return board
        }
        return null
    }

    /** A start in [yard]'s space at the tier's distance, proved by a fresh search, or null. */
    internal fun pick(yard: List<UnblockCar>, spec: Spec, rng: Rng): UnblockState? {
        val (y, back) = space(yard) ?: return null
        val d = minOf(back.farthest, spec.most)
        if (d < spec.least) return null
        val at = back.positions.indices.filter { back.distance[it] == d }
        val start = y.cars(back.positions[rng.pick(at)])
        val v = solve(start)
        if (v !is SearchVerdict.Solvable || v.path.size != d) return null
        return UnblockState(start, d)
    }

    /**
     * One fixed board per tier for when nothing was proved (a year per tier never reaches it, `FallbackTest`), written out
     * so a change to the generator cannot move it. A test proves each one's minimum is its par and inside its tier.
     */
    fun lastResort(difficulty: Difficulty): UnblockState = when (difficulty) {
        Difficulty.STANDARD -> UnblockState(LAST_STANDARD, 0)
        Difficulty.HARD -> UnblockState(LAST_STANDARD, 0)
        Difficulty.EXPERT -> UnblockState(LAST_STANDARD, 0)
    }

    private val LAST_STANDARD = listOf(UnblockCar(EXIT_ROW, 0, 2, true))
}
