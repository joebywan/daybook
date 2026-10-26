package com.joebywan.daybook.core

/** Digit [d]'s bit in a candidate mask: bit d stands for digit d (Sudoku, Cages, Inequality, Regions). */
internal fun bit(d: Int) = 1 shl d

/** Whether [mask] holds digit [d]. */
internal fun has(mask: Int, d: Int) = mask and bit(d) != 0

/** The digits 1..[max] that [mask] holds, in order. */
internal fun digitsOf(mask: Int, max: Int = 9) = (1..max).filter { has(mask, it) }

/** The candidate mask of a square that may hold any digit 1..[max]. */
internal fun fullMask(max: Int) = (1 shl (max + 1)) - 2

/**
 * How many ways the candidate masks [start] settle to one digit a square, counted up to [limit]. Exhaustive (no node
 * budget), so a count of one is a proof. [settle] prunes in place and returns false on a contradiction; the unsettled
 * square with the fewest digits left is split on, lowest digit first.
 */
internal fun countMaskSolutions(start: IntArray, limit: Int, settle: (IntArray) -> Boolean): Int {
    var found = 0
    fun search(c: IntArray) {
        if (!settle(c)) return
        var best = -1
        for (i in c.indices) if (c[i].countOneBits() > 1 && (best < 0 || c[i].countOneBits() < c[best].countOneBits())) best = i
        if (best < 0) { found++; return }
        var rest = c[best]
        while (rest != 0) {
            val low = rest and -rest
            rest -= low
            val next = c.copyOf()
            next[best] = low
            search(next)
            if (found >= limit) return
        }
    }
    search(start)
    return found
}
