package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * Tents: every tree gets one tent, orthogonally next to it (so tents and trees pair off one to one);
 * no two tents touch, not even at a corner; each row and column holds as many tents as its clue says.
 *
 * Squares are numbered row by row. [marks] is the player's: [TentsLogic.TENT] is a tent, [TentsLogic.GRASS] a cross
 * (a note, never counted by the rules; the grass a tent sprouts is not stored, see [seen]). Trees never carry a mark. [solution] is the tent set the generator
 * chose, kept for hints and mistakes only; `solved` reads the rules.
 *
 * Highlight conventions suggested for the board: squares `0 until n*n`, row clue r = `n*n + r`,
 * column clue c = `n*n + n + c`.
 */
@Serializable
data class TentsState(
    val size: Int,
    val trees: List<Boolean>,
    val rowCounts: List<Int>,
    val colCounts: List<Int>,
    val solution: List<Boolean>,
    val marks: List<Int> = List(size * size) { 0 },
    override val moves: Int = 0,
) : PuzzleState {

    /** The rules, not a comparison with [solution]. */
    override val solved: Boolean
        get() = TentsLogic.isSolved(size, trees, rowCounts, colCounts, marks.map { it == TentsLogic.TENT })

    /** Sets a mark (0 clear, TENT, GRASS). `this` for a tree or when nothing changes. */
    fun withMark(index: Int, mark: Int): TentsState =
        if (trees[index] || marks[index] == mark) this
        else copy(marks = marks.toMutableList().also { it[index] = mark }, moves = moves + 1)

    /**
     * [marks] as the board shows them: every empty square beside a tent also reads as grass. Derived, never stored,
     * so taking a tent back takes its grass with it and the player's own grass (in [marks]) is never touched.
     */
    val seen: List<Int>
        get() {
            val out = marks.toMutableList()
            for (i in marks.indices) if (marks[i] == TentsLogic.TENT) {
                for (j in TentsLogic.around(size, i)) if (marks[j] == 0 && !trees[j]) out[j] = TentsLogic.GRASS
            }
            return out
        }

    /** Tents in a row / column right now. */
    fun rowTents(r: Int) = (0 until size).count { marks[r * size + it] == TentsLogic.TENT }
    fun colTents(c: Int) = (0 until size).count { marks[it * size + c] == TentsLogic.TENT }

    /** Tents that touch another tent (including at a corner). */
    fun touching(): Set<Int> = TentsLogic.touching(size, marks.map { it == TentsLogic.TENT })
}

internal object TentsLogic {

    const val TENT = 1
    const val GRASS = 2

    fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.STANDARD -> 6
        Difficulty.HARD -> 8
        Difficulty.EXPERT -> 10
    }

    /** Trees (= tents) per tier: about a fifth of the squares. */
    fun treesFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.STANDARD -> 7
        Difficulty.HARD -> 13
        Difficulty.EXPERT -> 20
    }

    private val ORTHO = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    /** In-bounds orthogonal neighbours of square [i]. */
    fun orth(n: Int, i: Int): List<Int> = ORTHO.mapNotNull { (dr, dc) ->
        val r = i / n + dr
        val c = i % n + dc
        if (r in 0 until n && c in 0 until n) r * n + c else null
    }

    /** In-bounds neighbours of [i] including diagonals, excluding [i]. */
    fun around(n: Int, i: Int): List<Int> = buildList {
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = i / n + dr
            val c = i % n + dc
            if (r in 0 until n && c in 0 until n) add(r * n + c)
        }
    }

    fun touching(n: Int, tent: List<Boolean>): Set<Int> =
        tent.indices.filterTo(mutableSetOf()) { tent[it] && around(n, it).any { j -> tent[j] } }

    /** Every tent can be given its own adjacent tree and no tree is left without one. */
    fun matched(n: Int, trees: List<Boolean>, tent: List<Boolean>): Boolean {
        val tents = tent.indices.filter { tent[it] }
        val treeIdx = trees.indices.filter { trees[it] }
        if (tents.size != treeIdx.size) return false
        val owner = HashMap<Int, Int>() // tree -> tent
        fun augment(t: Int, seen: MutableSet<Int>): Boolean {
            for (tr in orth(n, t)) {
                if (!trees[tr] || !seen.add(tr)) continue
                val cur = owner[tr]
                if (cur == null || augment(cur, seen)) { owner[tr] = t; return true }
            }
            return false
        }
        return tents.all { augment(it, mutableSetOf()) }
    }

    /** The rules and nothing else. */
    fun isSolved(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, tent: List<Boolean>): Boolean {
        if (tent.indices.any { tent[it] && trees[it] }) return false
        for (k in 0 until n) {
            if ((0 until n).count { tent[k * n + it] } != rowCounts[k]) return false
            if ((0 until n).count { tent[it * n + k] } != colCounts[k]) return false
        }
        return touching(n, tent).isEmpty() && matched(n, trees, tent)
    }

    // ---- the ladder (what a person can do; the teacher mirrors it) ---------------------------------

    /**
     * Reasons from the trees and the clues alone and returns the status of every square: 0 unknown,
     * [TENT], [GRASS]; tree squares stay 0 and are never read. Steps, repeated to a fixpoint:
     *  1. a square with no tree beside it is grass;
     *  2. a row or column with its tents all placed: the rest is grass; with as many unknowns left as
     *     tents still owed: all tents;
     *  3. every square touching a tent is grass;
     *  4. a tree with one square left to put its tent in: that is a tent; and a square outside a tree's
     *     choices that touches all of them is grass (a tent there would leave the tree none);
     *  5. a tent beside only one tree is that tree's: a square whose trees are all so served is grass.
     */
    fun deduce(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>): IntArray {
        val st = IntArray(n * n)
        val treeIdx = trees.indices.filter { trees[it] }
        fun free(i: Int) = !trees[i] && st[i] == 0
        for (i in st.indices) if (!trees[i] && orth(n, i).none { trees[it] }) st[i] = GRASS
        do {
            var changed = false
            fun put(i: Int, v: Int) { if (!trees[i] && st[i] != v) { st[i] = v; changed = true } }
            for (k in 0 until n) {
                for (line in listOf((0 until n).map { k * n + it } to rowCounts[k], (0 until n).map { it * n + k } to colCounts[k])) {
                    val (sq, want) = line
                    val need = want - sq.count { st[it] == TENT }
                    val open = sq.filter { free(it) }
                    if (open.isEmpty()) continue
                    if (need == 0) open.forEach { put(it, GRASS) } else if (need == open.size) open.forEach { put(it, TENT) }
                }
            }
            for (i in st.indices) if (!trees[i] && st[i] == TENT) around(n, i).forEach { if (free(it)) put(it, GRASS) }
            for (t in treeIdx) {
                val choices = orth(n, t).filter { !trees[it] && st[it] != GRASS }
                if (choices.size == 1 && st[choices[0]] == 0) put(choices[0], TENT)
                if (choices.isEmpty()) continue
                for (x in st.indices) {
                    if (!free(x) || x in choices) continue
                    val near = around(n, x)
                    if (choices.all { it in near }) put(x, GRASS)
                }
            }
            val served = treeIdx.filter { t ->
                orth(n, t).any { c ->
                    !trees[c] && st[c] == TENT && orth(n, c).count { trees[it] } == 1
                }
            }.toSet()
            for (x in st.indices) {
                if (!free(x)) continue
                if (orth(n, x).filter { trees[it] }.all { it in served }) put(x, GRASS)
            }
        } while (changed)
        return st
    }

    /** True when the ladder alone decides every square. */
    fun solvesByLogic(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>): Boolean {
        val st = deduce(n, trees, rowCounts, colCounts)
        return st.indices.all { trees[it] || st[it] != 0 }
    }

    // ---- generator ---------------------------------------------------------------------------------

    /** Retries with `Rng(seed + attempt)`; the rate that needs more than one is measured in `TentsRulesTest`. */
    const val ATTEMPTS = 400

    /** A random tent set and trees beside each tent, or null; nothing proved yet. */
    private fun draw(n: Int, count: Int, rng: Rng): TentsState? {
        val tent = BooleanArray(n * n)
        var placed = 0
        for (i in rng.shuffled((0 until n * n).toList())) {
            if (placed == count) break
            if (around(n, i).any { tent[it] }) continue
            tent[i] = true
            placed++
        }
        if (placed < count) return null
        val tree = BooleanArray(n * n)
        for (t in rng.shuffled(tent.indices.filter { tent[it] })) {
            val spots = orth(n, t).filter { !tent[it] && !tree[it] }
            if (spots.isEmpty()) return null
            tree[rng.pick(spots)] = true
        }
        return TentsState(
            size = n,
            trees = tree.toList(),
            rowCounts = List(n) { r -> (0 until n).count { tent[r * n + it] } },
            colCounts = List(n) { c -> (0 until n).count { tent[it * n + c] } },
            solution = tent.toList(),
        )
    }

    /** Null when nothing was proved. Pure in (seed, difficulty); only lists reach the `Rng`. */
    fun generateVerified(seed: Long, difficulty: Difficulty): TentsState? {
        val n = sizeFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val s = draw(n, treesFor(difficulty), Rng(seed + attempt)) ?: continue
            if (!solvesByLogic(n, s.trees, s.rowCounts, s.colCounts)) continue
            if (countSolutions(n, s.trees, s.rowCounts, s.colCounts) != 1) continue
            return s
        }
        return null
    }

    /** A fixed board for a seed nothing proved: the first seed counting from 1 whose board has one answer (logic not required). */
    fun lastResort(difficulty: Difficulty): TentsState {
        val n = sizeFor(difficulty)
        var seed = 1L
        while (true) {
            val s = draw(n, treesFor(difficulty), Rng(seed++)) ?: continue
            if (countSolutions(n, s.trees, s.rowCounts, s.colCounts) == 1) return s
        }
    }

    // ---- proof -------------------------------------------------------------------------------------

    /**
     * How many tent sets satisfy the rules, counted up to [limit]. Exhaustive, no node budget: "1" means
     * one. Reading-order search over squares that sit beside a tree, pruned by the row/column counts and
     * the no-touch rule, with the one-to-one pairing checked on each finished set.
     */
    fun countSolutions(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, limit: Int = 2): Int {
        val cand = BooleanArray(n * n) { !trees[it] && orth(n, it).any { j -> trees[j] } }
        // colLeft[r*n+c]: candidates in column c from row r down; rowLeft: in row r from column c on.
        val colLeft = IntArray(n * n + n)
        val rowLeft = IntArray(n * n + 1)
        for (i in n * n - 1 downTo 0) {
            colLeft[i] = (if (cand[i]) 1 else 0) + colLeft[i + n]
            rowLeft[i] = (if (cand[i]) 1 else 0) + (if (i % n == n - 1) 0 else rowLeft[i + 1])
        }
        val tent = BooleanArray(n * n)
        val rows = IntArray(n)
        val cols = IntArray(n)
        var found = 0
        fun go(i: Int) {
            if (found >= limit) return
            if (i == n * n) {
                if (matched(n, trees, tent.toList())) found++
                return
            }
            val r = i / n
            val c = i % n
            if (rows[r] + rowLeft[i] < rowCounts[r] || cols[c] + colLeft[i] < colCounts[c]) return
            if (cand[i] && rows[r] < rowCounts[r] && cols[c] < colCounts[c] &&
                (c == 0 || !tent[i - 1]) && (r == 0 || (!tent[i - n] && (c == 0 || !tent[i - n - 1]) && (c == n - 1 || !tent[i - n + 1])))
            ) {
                tent[i] = true; rows[r]++; cols[c]++
                if (c < n - 1 || rows[r] == rowCounts[r]) go(i + 1)
                tent[i] = false; rows[r]--; cols[c]--
            }
            if (c < n - 1 || rows[r] == rowCounts[r]) go(i + 1)
        }
        go(0)
        return found
    }
}
