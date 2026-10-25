package com.joebywan.daybook.core

/** Digit [d]'s bit in a candidate mask: bit d stands for digit d (Sudoku, Cages, Inequality, Regions). */
internal fun bit(d: Int) = 1 shl d

/** Whether [mask] holds digit [d]. */
internal fun has(mask: Int, d: Int) = mask and bit(d) != 0

/** The digits 1..[max] that [mask] holds, in order. */
internal fun digitsOf(mask: Int, max: Int = 9) = (1..max).filter { has(mask, it) }
