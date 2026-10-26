package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WhoClue

/**
 * An independent reading of Who's Who, sharing nothing with `WhosWhoLogic`: every combination of one permutation per
 * column, built afresh by swapping (not the lexicographic list the count walks), each person's items written out as a
 * row, and every clue read off those rows at the leaf with no pruning at all.
 *
 * Its own words for the clue kinds: 0 same person, 1 different people, 2 neighbouring houses, 3 a's house number lower,
 * 4 a's holder holds b or c. Column 0 is the house.
 */
object WhosWhoOracle {

    /** Every assignment (per column, per person: the slot) that keeps [clues], optionally only those [allowed] admits. */
    fun answers(
        n: Int,
        columns: Int,
        clues: List<WhoClue>,
        limit: Int = Int.MAX_VALUE,
        allowed: (person: Int, column: Int, slot: Int) -> Boolean = { _, _, _ -> true },
    ): List<List<List<Int>>> {
        val perms = ArrayList<List<Int>>()
        fun heap(k: Int, a: IntArray) {
            if (k == 1) {
                perms.add(a.toList())
                return
            }
            for (i in 0 until k) {
                heap(k - 1, a)
                val j = if (k % 2 == 0) i else 0
                val t = a[j]
                a[j] = a[k - 1]
                a[k - 1] = t
            }
        }
        heap(n, IntArray(n) { it })
        val out = ArrayList<List<List<Int>>>()
        val pick = IntArray(columns)
        fun walk(column: Int) {
            if (out.size >= limit) return
            if (column == columns) {
                val assignment = List(columns) { perms[pick[it]] }
                for (p in 0 until n) for (c in 0 until columns) if (!allowed(p, c, assignment[c][p])) return
                val rows = rowsOf(n, assignment)
                if (clues.all { keeps(n, it, rows) }) out.add(assignment)
                return
            }
            for (i in perms.indices) {
                pick[column] = i
                walk(column + 1)
            }
        }
        walk(0)
        return out
    }

    /** Each person's row: their own index, then their slot in every column. */
    fun rowsOf(n: Int, assignment: List<List<Int>>): List<IntArray> = List(n) { p -> IntArray(assignment.size + 1) { if (it == 0) p else assignment[it - 1][p] } }

    /** Whether [clue] is true of the [rows] of an assignment. An item is `category * n + slot`, category 0 the names. */
    fun keeps(n: Int, clue: WhoClue, rows: List<IntArray>): Boolean {
        fun owner(item: Int): Int = rows.indexOfFirst { it[item / n] == item % n }
        val a = owner(clue.a)
        val b = owner(clue.b)
        return when (clue.kind) {
            0 -> a == b
            1 -> a != b
            2 -> kotlin.math.abs(rows[a][1] - rows[b][1]) == 1
            3 -> rows[a][1] < rows[b][1]
            else -> a == b || a == owner(clue.c)
        }
    }
}
