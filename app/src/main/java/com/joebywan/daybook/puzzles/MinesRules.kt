package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.neighbours8
import kotlinx.serialization.Serializable

/**
 * A Mines board (`docs/MINES_SPEC.md`). Squares are numbered in reading order. [mines] is the board: '1' a mine, '0'
 * none. [marks] is what the player has done: [MinesLogic.HIDDEN], [MinesLogic.OPEN] or [MinesLogic.FLAG]; the opening
 * (the spread from [opening]) is open when the board loads. [bumps] is the mine revealed, which loses the board: at most
 * one, but a list because builds with three lives saved up to three (such a save loads as lost, with Try again).
 *
 * [bumps] has a default, so a save that lacks it loads as a board still in play.
 */
@Serializable
data class MinesState(
    val width: Int,
    val height: Int,
    val mines: String,
    val opening: Int,
    val marks: String,
    val bumps: List<Int> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {

    val total: Int get() = mines.count { it == '1' }

    fun isMine(i: Int) = mines[i] == '1'
    fun isOpen(i: Int) = marks[i] == MinesLogic.OPEN
    fun isFlag(i: Int) = marks[i] == MinesLogic.FLAG

    /** Mines left to find, as the counter shows it: the total less flags and mines hit (below 0 with too many flags). */
    val minesLeft: Int get() = total - marks.count { it == MinesLogic.FLAG } - bumps.size

    /** The number a revealed square shows: the mines among its eight neighbours. */
    fun number(i: Int): Int = neighbours8(width, height, i).count { mines[it] == '1' }

    /** Every square without a mine revealed: the rule, flags play no part. */
    override val solved: Boolean get() = marks.indices.all { mines[it] == '1' || marks[it] == MinesLogic.OPEN }

    override val failed: Boolean get() = !solved && bumps.isNotEmpty()

    /**
     * Reveals [i]: a mine stays shown and loses the board; a safe square opens, spreading from a 0. Null (nothing happens,
     * nothing is spent) on a square already open, flagged or hit, or a board already won or lost.
     */
    fun reveal(i: Int): MinesState? {
        if (solved || failed || marks[i] != MinesLogic.HIDDEN || i in bumps) return null
        if (isMine(i)) return copy(bumps = bumps + i, moves = moves + 1)
        return copy(marks = MinesLogic.open(width, height, mines, marks, i), moves = moves + 1)
    }

    /**
     * Chording: on a revealed number whose neighbouring flags and mines hit already make its count, reveals every other
     * hidden neighbour, in reading order, as one move. A wrong flag means a mine among them: the first one revealed loses
     * the board, as a tap on it would, and the chord stops there. Null when the count is not met by flags
     * and hits, when nothing is left to reveal, or on a board already won or lost.
     */
    fun chord(i: Int): MinesState? {
        if (solved || failed || !isOpen(i)) return null
        val nb = neighbours8(width, height, i)
        if (nb.count { isFlag(it) || it in bumps } != number(i)) return null
        val todo = nb.filter { marks[it] == MinesLogic.HIDDEN && it !in bumps }
        if (todo.isEmpty()) return null
        val m = marks.toCharArray()
        val hit = bumps.toMutableList()
        for (j in todo) {
            if (isMine(j)) {
                hit += j
                break
            } else MinesLogic.openInPlace(width, height, mines, m, j)
        }
        return copy(marks = m.concatToString(), bumps = hit, moves = moves + 1)
    }

    /** Flags [i], or takes its flag off. Null on a square open or hit, or a board already won or lost. */
    fun flag(i: Int): MinesState? {
        if (solved || failed || marks[i] == MinesLogic.OPEN || i in bumps) return null
        val m = marks.toCharArray()
        m[i] = if (m[i] == MinesLogic.FLAG) MinesLogic.HIDDEN else MinesLogic.FLAG
        return copy(marks = m.concatToString(), moves = moves + 1)
    }

    /** The board from the opening again, no flags, no mine shown: one state, so one move. */
    fun retry(): MinesState = copy(marks = MinesLogic.openingMarks(width, height, mines, opening), bumps = emptyList(), moves = moves + 1)
}

/**
 * Rules, the ladder and the generator. The ladder ([nextFact]) is the teacher's and the generator's alike: a board ships
 * only if it, walked from the opening, reveals every safe square ([walk]), so every mine is forced and the answer is unique.
 *
 * Web parity: no hash container anywhere here; every walk is over arrays in index order and every choice the [Rng] makes
 * is over a list.
 */
internal object MinesLogic {

    const val HIDDEN = '.'
    const val OPEN = 'o'
    const val FLAG = 'f'

    /** What the ladder knows of a square: nothing yet, a mine, or safe. */
    const val UNKNOWN = 0
    const val MINE = 1
    const val SAFE = 2

    /** How far a what-if follows the single-number rules: numbers that fire after the supposition. */
    const val MAX_LINKS = 3

    /** Draws tried per seed before [lastResort]; draw `a` is `Rng(seed + a)`. */
    const val ATTEMPTS = 3000

    /** At most this share (per mille) of the safe squares open with the board, or it gives itself away. */
    const val MAX_OPENING = 450

    /**
     * [width] x [height] squares, [mines] of them mines, and the deepest step of the ladder ([nextFact]'s levels: 1 the
     * single numbers, 2 subset and pair, 3 the count and the what-if) a board must need: a draw needing more or less is
     * redrawn. At level 3 the walk must also use [MinesTeacher.EDGE] or [MinesTeacher.WHAT_IF] ([accepts]).
     */
    class Spec(val width: Int, val height: Int, val mines: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(8, 8, 10, 1)
        Difficulty.HARD -> Spec(8, 10, 16, 2)
        Difficulty.EXPERT -> Spec(9, 10, 21, 3)
    }

    /** [marks] with [start] opened and the spread from every 0 it reaches; flagged squares are passed by. */
    fun open(w: Int, h: Int, mines: String, marks: String, start: Int): String {
        val m = marks.toCharArray()
        openInPlace(w, h, mines, m, start)
        return m.concatToString()
    }

    /** Opens [start] in [m] and spreads from every 0, breadth first in reading order; returns the squares opened, in order. */
    fun openInPlace(w: Int, h: Int, mines: String, m: CharArray, start: Int): List<Int> {
        val opened = ArrayList<Int>()
        if (m[start] != HIDDEN || mines[start] == '1') return opened
        m[start] = OPEN
        opened += start
        var head = 0
        while (head < opened.size) {
            val i = opened[head++]
            val nb = neighbours8(w, h, i)
            if (nb.any { mines[it] == '1' }) continue
            for (j in nb) if (m[j] == HIDDEN) {
                m[j] = OPEN
                opened += j
            }
        }
        return opened
    }

    /** The marks the board loads with: everything hidden, then the opening spread from [opening]. */
    fun openingMarks(w: Int, h: Int, mines: String, opening: Int): String =
        open(w, h, mines, HIDDEN.toString().repeat(w * h), opening)

    // ---- the ladder -------------------------------------------------------------------------------------------------

    /**
     * One step of the ladder: [safe] squares and [mines] it proves, from the numbers in [cited]. [level] is 1 for the
     * single-number rules, 2 for subset and pair, 3 for the count and the what-if. A what-if keeps its [chain] (the
     * numbers that fired after the supposition, and whether each made its squares mines) and the number it [broke]
     * ([tooMany]: it would touch too many mines; else it would have too few squares left). A count step keeps [left].
     */
    class Fact(
        val technique: String,
        val level: Int,
        val safe: List<Int>,
        val mines: List<Int>,
        val cited: List<Int>,
        val chain: List<Pair<Int, Boolean>> = emptyList(),
        val broke: Int = -1,
        val tooMany: Boolean = false,
        val left: Int = 0,
    )

    /**
     * The first step the ladder finds, simplest first, or null when it has none. [num] is each square's number when it is
     * revealed, else -1; [st] what is known ([UNKNOWN], [MINE], [SAFE]: revealed squares are [SAFE], mines hit [MINE]);
     * [total] the board's mines. Visible facts only: nothing here can see where a hidden mine is.
     */
    fun nextFact(w: Int, h: Int, num: IntArray, st: IntArray, total: Int): Fact? {
        val n = w * h
        val nbs = Array(n) { neighbours8(w, h, it) }
        fun unknownOf(i: Int) = nbs[i].filter { st[it] == UNKNOWN }
        fun needOf(i: Int) = num[i] - nbs[i].count { st[it] == MINE }

        // 1. Single numbers.
        for (i in 0 until n) {
            if (num[i] < 0) continue
            val unk = unknownOf(i)
            if (unk.isEmpty()) continue
            val need = needOf(i)
            if (need == 0) return Fact(MinesTeacher.ALL_FOUND, 1, unk, emptyList(), listOf(i))
            if (need == unk.size) return Fact(MinesTeacher.ALL_MINES, 1, emptyList(), unk, listOf(i))
        }

        // 2. Two numbers: one's squares inside the other's, or overlapping.
        for (a in 0 until n) {
            if (num[a] < 0) continue
            val ua = unknownOf(a)
            if (ua.isEmpty()) continue
            val needA = needOf(a)
            val ar = a / w
            val ac = a % w
            for (b in 0 until n) {
                if (b == a || num[b] < 0) continue
                if (kotlin.math.abs(b / w - ar) > 2 || kotlin.math.abs(b % w - ac) > 2) continue
                val ub = unknownOf(b)
                if (ub.none { it in ua }) continue
                val onlyA = ua.filter { it !in ub }
                val onlyB = ub.filter { it !in ua }
                if (onlyB.isEmpty()) continue
                val diff = needOf(b) - needA
                if (onlyA.isEmpty()) {
                    if (diff == 0) return Fact(MinesTeacher.SUBSET, 2, onlyB, emptyList(), listOf(a, b))
                    if (diff == onlyB.size) return Fact(MinesTeacher.SUBSET, 2, emptyList(), onlyB, listOf(a, b))
                } else if (diff == onlyB.size) {
                    return Fact(MinesTeacher.PAIR, 2, onlyA, onlyB, listOf(a, b))
                }
            }
        }

        // 3a. The mine count.
        val hidden = (0 until n).filter { st[it] == UNKNOWN }
        if (hidden.isNotEmpty()) {
            val left = total - st.count { it == MINE }
            if (left == 0) return Fact(MinesTeacher.COUNT, 3, hidden, emptyList(), emptyList(), left = 0)
            if (left == hidden.size) return Fact(MinesTeacher.COUNT, 3, emptyList(), hidden, emptyList(), left = left)
            // Numbers on squares none of them share, needing every mine left between them: the rest is clear.
            val order = (0 until n).filter { num[it] >= 0 && unknownOf(it).isNotEmpty() && needOf(it) > 0 }
                .sortedWith(compareBy({ -needOf(it) }, { it }))
            val used = BooleanArray(n)
            val chosen = ArrayList<Int>()
            var sum = 0
            for (i in order) {
                val unk = unknownOf(i)
                if (unk.any { used[it] }) continue
                unk.forEach { used[it] = true }
                chosen += i
                sum += needOf(i)
            }
            if (sum == left) {
                val rest = hidden.filter { !used[it] }
                if (rest.isNotEmpty()) return Fact(MinesTeacher.EDGE, 3, rest, emptyList(), chosen.sorted(), left = left)
            }
        }

        // 3b. What if: suppose a square beside a number, follow single numbers, and one breaks.
        for (x in hidden) {
            if (nbs[x].none { num[it] >= 0 }) continue
            for (assume in intArrayOf(MINE, SAFE)) {
                val f = whatIf(n, nbs, num, st, x, assume) ?: continue
                return if (assume == MINE) Fact(MinesTeacher.WHAT_IF, 3, listOf(x), emptyList(), f.cited, f.chain, f.broke, f.tooMany)
                else Fact(MinesTeacher.WHAT_IF, 3, emptyList(), listOf(x), f.cited, f.chain, f.broke, f.tooMany)
            }
        }
        return null
    }

    /**
     * Every step the ladder finds on [st], in the order [nextFact] tries them (so its first is [nextFact]'s), lazily: Training
     * lists every step of one technique with it. Nothing in [st] changes while it runs. The same rules as [nextFact], kept
     * apart because the generator's walk calls [nextFact] and a sequence there costs time; `MinesTrainingTest` holds the
     * two to the same first step.
     */
    fun facts(w: Int, h: Int, num: IntArray, st: IntArray, total: Int): Sequence<Fact> = sequence {
        val n = w * h
        val nbs = Array(n) { neighbours8(w, h, it) }
        fun unknownOf(i: Int) = nbs[i].filter { st[it] == UNKNOWN }
        fun needOf(i: Int) = num[i] - nbs[i].count { st[it] == MINE }

        // 1. Single numbers.
        for (i in 0 until n) {
            if (num[i] < 0) continue
            val unk = unknownOf(i)
            if (unk.isEmpty()) continue
            val need = needOf(i)
            if (need == 0) yield(Fact(MinesTeacher.ALL_FOUND, 1, unk, emptyList(), listOf(i)))
            if (need == unk.size) yield(Fact(MinesTeacher.ALL_MINES, 1, emptyList(), unk, listOf(i)))
        }

        // 2. Two numbers: one's squares inside the other's, or overlapping.
        for (a in 0 until n) {
            if (num[a] < 0) continue
            val ua = unknownOf(a)
            if (ua.isEmpty()) continue
            val needA = needOf(a)
            val ar = a / w
            val ac = a % w
            for (b in 0 until n) {
                if (b == a || num[b] < 0) continue
                if (kotlin.math.abs(b / w - ar) > 2 || kotlin.math.abs(b % w - ac) > 2) continue
                val ub = unknownOf(b)
                if (ub.none { it in ua }) continue
                val onlyA = ua.filter { it !in ub }
                val onlyB = ub.filter { it !in ua }
                if (onlyB.isEmpty()) continue
                val diff = needOf(b) - needA
                if (onlyA.isEmpty()) {
                    if (diff == 0) yield(Fact(MinesTeacher.SUBSET, 2, onlyB, emptyList(), listOf(a, b)))
                    if (diff == onlyB.size) yield(Fact(MinesTeacher.SUBSET, 2, emptyList(), onlyB, listOf(a, b)))
                } else if (diff == onlyB.size) {
                    yield(Fact(MinesTeacher.PAIR, 2, onlyA, onlyB, listOf(a, b)))
                }
            }
        }

        // 3a. The mine count.
        val hidden = (0 until n).filter { st[it] == UNKNOWN }
        if (hidden.isNotEmpty()) {
            val left = total - st.count { it == MINE }
            if (left == 0) yield(Fact(MinesTeacher.COUNT, 3, hidden, emptyList(), emptyList(), left = 0))
            if (left == hidden.size) yield(Fact(MinesTeacher.COUNT, 3, emptyList(), hidden, emptyList(), left = left))
            // Numbers on squares none of them share, needing every mine left between them: the rest is clear.
            val order = (0 until n).filter { num[it] >= 0 && unknownOf(it).isNotEmpty() && needOf(it) > 0 }
                .sortedWith(compareBy({ -needOf(it) }, { it }))
            val used = BooleanArray(n)
            val chosen = ArrayList<Int>()
            var sum = 0
            for (i in order) {
                val unk = unknownOf(i)
                if (unk.any { used[it] }) continue
                unk.forEach { used[it] = true }
                chosen += i
                sum += needOf(i)
            }
            if (sum == left) {
                val rest = hidden.filter { !used[it] }
                if (rest.isNotEmpty()) yield(Fact(MinesTeacher.EDGE, 3, rest, emptyList(), chosen.sorted(), left = left))
            }
        }

        // 3b. What if: suppose a square beside a number, follow single numbers, and one breaks.
        for (x in hidden) {
            if (nbs[x].none { num[it] >= 0 }) continue
            for (assume in intArrayOf(MINE, SAFE)) {
                val f = whatIf(n, nbs, num, st, x, assume) ?: continue
                yield(
                    if (assume == MINE) Fact(MinesTeacher.WHAT_IF, 3, listOf(x), emptyList(), f.cited, f.chain, f.broke, f.tooMany)
                    else Fact(MinesTeacher.WHAT_IF, 3, emptyList(), listOf(x), f.cited, f.chain, f.broke, f.tooMany),
                )
            }
        }
    }

    private class Broken(val cited: List<Int>, val chain: List<Pair<Int, Boolean>>, val broke: Int, val tooMany: Boolean)

    /** [x] supposed [assume]; the single-number rules followed at most [MAX_LINKS] times; the first number that breaks. */
    private fun whatIf(n: Int, nbs: Array<IntArray>, num: IntArray, st0: IntArray, x: Int, assume: Int): Broken? {
        val st = st0.copyOf()
        st[x] = assume
        val chain = ArrayList<Pair<Int, Boolean>>()
        while (true) {
            for (i in 0 until n) {
                if (num[i] < 0) continue
                val mines = nbs[i].count { st[it] == MINE }
                val unk = nbs[i].count { st[it] == UNKNOWN }
                if (mines > num[i] || mines + unk < num[i]) {
                    if (chain.isEmpty() && i !in nbs[x]) continue // only a number the supposition touches breaks at once
                    return Broken((chain.map { it.first } + i).distinct(), chain.toList(), i, mines > num[i])
                }
            }
            if (chain.size == MAX_LINKS) return null
            var fired = -1
            for (i in 0 until n) {
                if (num[i] < 0) continue
                val unk = nbs[i].filter { st[it] == UNKNOWN }
                if (unk.isEmpty()) continue
                val need = num[i] - nbs[i].count { st[it] == MINE }
                if (need == 0) { unk.forEach { st[it] = SAFE }; chain += i to false; fired = i; break }
                if (need == unk.size) { unk.forEach { st[it] = MINE }; chain += i to true; fired = i; break }
            }
            if (fired < 0) return null
        }
    }

    /** The ladder's walk from the opening: the deepest level it needed and the techniques it used, or null when it stalls. */
    class Walk(val depth: Int, val used: List<String>)

    fun walk(w: Int, h: Int, mines: String, opening: Int): Walk? {
        val n = w * h
        val m = openingMarks(w, h, mines, opening).toCharArray()
        val num = IntArray(n) { -1 }
        val st = IntArray(n)
        val total = mines.count { it == '1' }
        fun reveal(i: Int) {
            num[i] = neighbours8(w, h, i).count { mines[it] == '1' }
            st[i] = SAFE
        }
        for (i in 0 until n) if (m[i] == OPEN) reveal(i)
        var safeLeft = (0 until n).count { mines[it] == '0' && m[it] != OPEN }
        var depth = 0
        val used = ArrayList<String>()
        while (safeLeft > 0) {
            val f = nextFact(w, h, num, st, total) ?: return null
            depth = maxOf(depth, f.level)
            if (f.technique !in used) used += f.technique
            for (i in f.mines) st[i] = MINE
            for (i in f.safe) for (j in openInPlace(w, h, mines, m, i)) {
                reveal(j)
                safeLeft--
            }
        }
        return Walk(depth, used)
    }

    // ---- generation -------------------------------------------------------------------------------------------------

    fun generate(seed: Long, difficulty: Difficulty): MinesState = generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /** A board the ladder clears from its opening, needing exactly the tier's deepest step, or null if no draw was one. */
    fun generateVerified(seed: Long, difficulty: Difficulty): MinesState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): MinesState? {
        val spec = specFor(difficulty)
        val s = draw(Rng(seed + a), spec)
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: its opening gives away little enough, and the ladder clears it needing exactly [Spec.needs]. */
    fun accepts(s: MinesState, spec: Spec): Boolean {
        val safe = s.mines.count { it == '0' }
        if (s.marks.count { it == OPEN } * 1000 > MAX_OPENING * safe) return false
        val walk = walk(s.width, s.height, s.mines, s.opening) ?: return false
        if (walk.depth != spec.needs) return false
        // The count alone at the very end (every mine found, a pocket no number touches left) is too slight to make an
        // Expert board: it must need the count against the numbers or a what-if.
        return spec.needs < 3 || MinesTeacher.EDGE in walk.used || MinesTeacher.WHAT_IF in walk.used
    }

    /** The opening square, then the mines: a shuffle of the squares outside the opening's 3x3, the first [Spec.mines]. */
    private fun draw(rng: Rng, spec: Spec): MinesState {
        val w = spec.width
        val h = spec.height
        val opening = rng.nextInt(w * h)
        val clear = neighbours8(w, h, opening).toList() + opening
        val pool = (0 until w * h).filter { it !in clear }
        val chosen = rng.shuffled(pool).take(spec.mines)
        val mines = CharArray(w * h) { '0' }
        chosen.forEach { mines[it] = '1' }
        val ms = mines.concatToString()
        return MinesState(w, h, ms, opening, openingMarks(w, h, ms, opening))
    }

    /** Only when no draw of [ATTEMPTS] was accepted: the first seed counting from 1 whose first draw the tier accepts. */
    fun lastResort(difficulty: Difficulty): MinesState {
        val spec = specFor(difficulty)
        var seed = 1L
        while (true) {
            val s = draw(Rng(seed++), spec)
            if (accepts(s, spec)) return s
        }
    }
}
