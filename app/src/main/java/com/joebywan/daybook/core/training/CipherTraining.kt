package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherState
import com.joebywan.daybook.puzzles.CipherTeacher

/**
 * Cipher's course, on [MoveDrill]: a step gives one letter its digit (select the letter, then tap the digit). "The only
 * letter left for a digit" is not here: no walk of the practice seeds ever reaches it (docs/TRAINING_SPEC.md).
 */
val CipherTraining: Course = Course(
    puzzle = Cipher,
    explanations = linkedMapOf(
        CipherTeacher.LEADING to "When the total is one letter longer than the numbers added, its first letter is the " +
            "carry out of the last column. Two digits and a carry make at most 19, so that letter is 1.",
        CipherTeacher.COLUMN to "Each column adds up on its own: its digits and the carry from the right end in the " +
            "total's letter. When enough of a column is known, one letter has a single digit left.",
        CipherTeacher.PAIR to "Take two neighbouring columns together, through the carry between them: each cuts what " +
            "the other allows. Find the one digit that lets both add up.",
        CipherTeacher.CHAIN to "When no column or pair settles it, work every column and carry through together, giving " +
            "no two letters the same digit, until a letter has one digit left.",
        CipherTeacher.WHAT_IF to "Try every choice for two neighbouring columns at once, keeping what the other columns " +
            "rule out. When only one digit for a letter lets both add up, that's its digit.",
    ),
    // The generator's tiers: Standard reaches the first three, Hard every column together, Expert the what-if.
    tierFor = tierByIndex({ CipherTraining.techniques }, hard = 3, expert = 4),
    // CipherTeacher.deduce is handed the sum and the placed digits; the answer cannot reach it.
    drill = MoveDrill { s -> (s as CipherState).let { c -> CipherTeacher.deduce(c.sum, c.digits)?.let { Cipher.deduction(c, it) } } },
    badge = "+",
    // The first practice seed whose Expert walk needs all five (16 of the first 60). Pinned by CipherTrainingTest.
    examIndex = 0,
)
