package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Link
import com.joebywan.daybook.puzzles.Sym

/**
 * An independent answer counter for Mambo, written apart from the generator's propagation: depth
 * first over every grid in reading order, each cell checked only against what lies behind it.
 */
object MamboOracle {

    /**
     * How many full grids keep [clue]'s filled cells ([Sym.NONE] is open) and honour [links],
     * counting no further than [cap].
     */
    fun countAnswers(n: Int, clue: List<Sym>, links: List<Link>, cap: Int = 2): Int {
        val given = List(n * n) { clue[it] != Sym.NONE }
        val grid = clue.toMutableList()
        val linksAt = Array(n * n) { mutableListOf<Link>() }
        links.forEach { linksAt[maxOf(it.a, it.b)] += it }
        var found = 0

        fun fits(i: Int): Boolean {
            val r = i / n
            val c = i % n
            val sym = grid[i]
            if (c >= 2 && grid[i - 1] == sym && grid[i - 2] == sym) return false
            if (r >= 2 && grid[i - n] == sym && grid[i - 2 * n] == sym) return false
            if ((0..c).count { grid[r * n + it] == sym } > n / 2) return false
            if ((0..r).count { grid[it * n + c] == sym } > n / 2) return false
            if (c == n - 1 && (0 until n).count { grid[r * n + it] == sym } != n / 2) return false
            if (r == n - 1 && (0 until n).count { grid[it * n + c] == sym } != n / 2) return false
            return linksAt[i].all { (grid[it.a] == grid[it.b]) == it.same }
        }

        fun fill(i: Int) {
            if (found >= cap) return
            if (i == n * n) {
                // The last cell of each line was balance-checked as it landed; the other symbol's
                // count follows because the line is full.
                found++
                return
            }
            val options = if (given[i]) listOf(grid[i]) else listOf(Sym.SUN, Sym.MOON)
            for (sym in options) {
                grid[i] = sym
                if (fits(i)) fill(i + 1)
            }
            if (!given[i]) grid[i] = Sym.NONE
        }

        fill(0)
        return found
    }
}
