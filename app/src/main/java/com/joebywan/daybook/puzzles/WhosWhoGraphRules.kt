package com.joebywan.daybook.puzzles

import kotlinx.serialization.Serializable

/**
 * A Who's Who board played as a graph (the lab puzzle `test-whoswho`, `WhosWhoGraph`): every item is a node, the people
 * included, and the player marks a pair of items from different categories as linked (the same person's) or anti-linked
 * (never the same person's). No category is the base, so the board can be read from the house, a pet or a person alike.
 *
 * [board] is a dealt Who's Who board, kept whole for its names, words, clues, answer and struck clues; its own table
 * marks are never used. [links] holds one character per pair `a < b` of items at `a * size + b` (items are
 * `category * n + slot`, category 0 the names, as [WhoClue]'s): `.` open, `o` a link, `x` an anti-link. Shorter
 * strings mean the rest are open, so a fresh board stores nothing. What the marks imply is derived
 * ([WhosWhoGraphLogic.close]), never stored.
 */
@Serializable
data class WhosWhoGraphState(
    val board: WhosWhoState,
    val links: String = "",
    override val moves: Int = 0,
) : PuzzleState {

    val n: Int get() = board.n

    /** Items: the people and every column's. */
    val size: Int get() = board.n * (board.columns + 1)

    /** The derived links make a full table and every clue holds. The rules, not [WhosWhoState.answer]. */
    override val solved: Boolean get() = WhosWhoGraphLogic.isSolved(this)

    fun mark(a: Int, b: Int): Char = links.getOrElse(WhosWhoGraphLogic.pair(size, a, b)) { WhosWhoGraphLogic.OPEN }

    /** The pair [a]-[b] with [mark] instead; this state when it already has it. One move. */
    fun withMark(a: Int, b: Int, mark: Char): WhosWhoGraphState {
        if (this.mark(a, b) == mark) return this
        val next = links.padEnd(size * size, WhosWhoGraphLogic.OPEN).toCharArray()
        next[WhosWhoGraphLogic.pair(size, a, b)] = mark
        return copy(links = next.concatToString().trimEnd(WhosWhoGraphLogic.OPEN), moves = moves + 1)
    }

    /** A tap on the pair: open, then a link, then an anti-link, then open again. */
    fun cycle(a: Int, b: Int): WhosWhoGraphState = withMark(
        a, b,
        when (mark(a, b)) {
            WhosWhoGraphLogic.OPEN -> WhosWhoGraphLogic.LINK
            WhosWhoGraphLogic.LINK -> WhosWhoGraphLogic.ANTI
            else -> WhosWhoGraphLogic.OPEN
        },
    )

    /** Clue [clue] struck through, or back: a note. One move. */
    fun strike(clue: Int): WhosWhoGraphState = copy(board = board.strike(clue), moves = moves + 1)
}

/**
 * What the graph's marks imply, and the rules. Pure and in index order (no hash container reaches anything), so the
 * phone and the browser agree.
 */
internal object WhosWhoGraphLogic {

    const val OPEN = '.'
    const val LINK = 'o'
    const val ANTI = 'x'

    fun pair(size: Int, a: Int, b: Int) = minOf(a, b) * size + maxOf(a, b)

    /**
     * Every pair's mark once the player's are carried through: [at] is 1 linked, -1 anti-linked, 0 open, for both orders.
     * [badPair] flags a pair where the marks say both; [badItem] an item linked to two of one category, or with nothing left
     * in some category.
     */
    class Closure(val size: Int, val n: Int, val m: IntArray, val badPair: BooleanArray, val badItem: BooleanArray) {
        fun at(a: Int, b: Int) = m[a * size + b]

        val clash: Boolean get() = badPair.any { it } || badItem.any { it }

        /** The item of [category] linked to [a], or -1. */
        fun linked(a: Int, category: Int): Int = (category * n until category * n + n).firstOrNull { at(a, it) == 1 } ?: -1
    }

    /**
     * The player's marks carried to a fixpoint by four rules: links join groups (whatever is linked to one is linked to
     * all); an anti-link between two items holds between their whole groups; an item linked to one of a category is
     * anti-linked to the rest of it; an item with one item of a category left is linked to it.
     */
    fun close(s: WhosWhoGraphState): Closure {
        val size = s.size
        val n = s.n
        val m = IntArray(size * size)
        val badPair = BooleanArray(size * size)
        val badItem = BooleanArray(size)
        fun cat(i: Int) = i / n
        for (a in 0 until size) for (b in a + 1 until size) {
            val v = when (s.mark(a, b)) {
                LINK -> 1
                ANTI -> -1
                else -> 0
            }
            m[a * size + b] = v
            m[b * size + a] = v
        }
        var changed = true
        fun set(a: Int, b: Int, v: Int) {
            if (a == b || cat(a) == cat(b)) return
            val now = m[a * size + b]
            if (now == v) return
            if (now == -v) {
                badPair[a * size + b] = true
                badPair[b * size + a] = true
                return
            }
            m[a * size + b] = v
            m[b * size + a] = v
            changed = true
        }
        var rounds = 0
        while (changed && rounds++ < 4 * size) {
            changed = false
            val group = IntArray(size) { it }
            fun root(x: Int): Int {
                var r = x
                while (group[r] != r) r = group[r]
                return r
            }
            for (a in 0 until size) for (b in a + 1 until size) if (m[a * size + b] == 1) {
                val ra = root(a)
                val rb = root(b)
                if (ra != rb) group[maxOf(ra, rb)] = minOf(ra, rb)
            }
            val roots = IntArray(size) { root(it) }
            for (a in 0 until size) for (b in a + 1 until size) if (roots[a] == roots[b]) {
                if (cat(a) == cat(b)) {
                    badItem[a] = true
                    badItem[b] = true
                } else set(a, b, 1)
            }
            for (a in 0 until size) for (b in 0 until size) if (m[a * size + b] == -1) {
                for (x in 0 until size) if (roots[x] == roots[a]) for (y in 0 until size) if (roots[y] == roots[b]) set(x, y, -1)
            }
            for (a in 0 until size) for (b in 0 until size) if (m[a * size + b] == 1) {
                for (z in cat(b) * n until cat(b) * n + n) if (z != b) set(a, z, -1)
            }
            for (a in 0 until size) for (c in 0..s.board.columns) {
                if (c == cat(a)) continue
                val left = (c * n until c * n + n).filter { m[a * size + it] != -1 }
                if (left.size == 1) set(a, left[0], 1)
                if (left.isEmpty()) badItem[a] = true
            }
        }
        return Closure(size, n, m, badPair, badItem)
    }

    /** The closure's person rows as a Who's Who table: a tick where a person is linked, a cross where anti-linked. */
    fun table(s: WhosWhoGraphState, c: Closure = close(s)): WhosWhoState {
        val b = s.board
        val marks = CharArray(b.n * b.columns * b.n) { WhosWhoLogic.EMPTY }
        for (p in 0 until b.n) for (column in 0 until b.columns) for (j in 0 until b.n) {
            marks[b.markIndex(p, column, j)] = when (c.at(p, (column + 1) * b.n + j)) {
                1 -> WhosWhoLogic.TICK
                -1 -> WhosWhoLogic.CROSS
                else -> WhosWhoLogic.EMPTY
            }
        }
        return b.copy(marks = marks.concatToString())
    }

    /** No marks that contradict each other, and the table they make is a solved Who's Who board. */
    fun isSolved(s: WhosWhoGraphState): Boolean {
        val c = close(s)
        return !c.clash && WhosWhoLogic.isSolved(table(s, c))
    }

    /** The person who holds item [ref] in the board's one answer. */
    fun holder(b: WhosWhoState, ref: Int): Int {
        val category = ref / b.n
        return if (category == 0) ref % b.n else b.answer[category - 1].indexOf(ref % b.n)
    }
}
