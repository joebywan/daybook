package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.Tower

/**
 * The one place the app learns which puzzles exist.
 *
 * Adding a genre is a one-line change here plus its file in `puzzles/`. Order is the order shown
 * on the home screen.
 */
object PuzzleRegistry {

    val all: List<PuzzleType> = listOf(
        Sudoku,
        Kings,
        Mambo,
        Pipes,
        Shikaku,
        Mosaic,
        Sets,
        Atoms,
        Snap,
        Lits,
        Tower,
    )

    fun byId(id: String): PuzzleType? = all.firstOrNull { it.id == id }

    /** Puzzle featured as "today's headline" — rotates daily but deterministically. */
    fun featured(epochDay: Long): PuzzleType = all[(epochDay.mod(all.size.toLong())).toInt()]
}
