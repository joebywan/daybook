package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.MosaicTeacher
import com.joebywan.daybook.puzzles.PuzzleState

/**
 * Mosaic's course: the three reasons the teacher gives for a fill (counting, the biggest swallow, the centre). The fill
 * that finishes the board and the fallback "stays within the limit" are not skills (`TechniqueNames`' NOT_PRAISED).
 *
 * The walk is [MoveDrill]'s; the judge is [MosaicDrill]'s own, because every Mosaic move is a fill that cannot be taken
 * back, so a practice cannot "play on" past a fill that is not the step: each fill gets a verdict.
 */
val MosaicTraining: Course = Course(
    puzzle = Mosaic,
    explanations = linkedMapOf(
        MosaicTeacher.COUNT to "Count the colours on the board against the fills left. A fill wipes out a colour only " +
            "by covering its last patch, so with one fill fewer than colours, every fill must: pour into a colour " +
            "that's down to one patch.",
        MosaicTeacher.BIGGEST to "Look for the fill that swallows the most neighbouring areas at once: an area " +
            "touching several patches of one colour, poured that colour. It isn't always safe, but here it is.",
        MosaicTeacher.CENTRE to "When no fill swallows much, grow from the middle: find the area with every other " +
            "area fewest steps away, and pour into it the colour that joins the most neighbours and stays in the limit.",
    ),
    // Standard boards reach all three (counting and the biggest swallow on 60 of 60, the centre on 5), and the smallest
    // boards are the clearest to learn on: a tier is board size and palette here, not depth of reasoning.
    tierFor = { Difficulty.STANDARD },
    drill = MosaicDrill,
    badge = "Fill",
    // The first practice seed whose Expert board's walk needs all three. Pinned by MosaicTrainingTest.
    examIndex = 4,
)

/**
 * Mosaic's drill. A step comes from [MosaicTeacher.deduce], which sees only the board, the palette and the fills left.
 *
 * Judging cannot be the teacher's whole hint: that searches for a fill as well as asking whether the board is lost, up to
 * 1.2 s on the emulator, and on the web every move would wait for it on the one thread. So a fill is judged by one
 * question of the same search, "does the board still finish in the fills left?":
 * - the step's own fill is right without a search (its `isReached`);
 * - a fill that loses the board is wrong (what the teacher would call a mistake);
 * - a fill that keeps the board but is not this technique is wrong too: it cannot be undone here, so the practice cannot
 *   wait for the step, and the verdict shows the step that was there to find. A fill that is the technique by its own
 *   test (counting: any fill that keeps the board, since each must wipe out a colour; the biggest swallow: one that
 *   swallows as many; the centre: any fill of a most central area) is right.
 * A search that runs out of budget says nothing (play on), as the hint would.
 */
object MosaicDrill : Drill {
    private val moves = MoveDrill { s ->
        val m = s as MosaicState
        MosaicTeacher.deduce(m.width, m.height, m.colours, m.cells, m.limit - m.moves)?.let { Mosaic.deduction(m, it) }
    }

    override fun walk(start: PuzzleState): Sequence<Course.Practice> = moves.walk(start)

    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? {
        val b = p.board as MosaicState
        val m = after as MosaicState
        if ((p.step as Deduction).isReached(m)) return true
        val cell = b.cells.indices.firstOrNull { b.cells[it] != m.cells[it] } ?: return null
        val a = MosaicTeacher.Areas.of(b.width, b.height, b.cells) ?: return null
        val search = MosaicTeacher.Search(b.colours)
        val left = b.limit - b.moves
        val g = a.label[cell]
        val colour = m.cells[cell]
        val keeps = search.keeps(a.root, g, colour, left)
        if (search.exhausted) return null
        if (!keeps) return false
        val root = a.root
        return when (p.step.technique) {
            MosaicTeacher.COUNT -> true
            MosaicTeacher.BIGGEST -> {
                val best = (0 until root.count).maxOf { h ->
                    (0 until b.colours).filter { it != root.hue[h] }.maxOfOrNull { search.gain(root, h, it) } ?: 0
                }
                search.gain(root, g, colour) == best
            }
            MosaicTeacher.CENTRE -> {
                val ecc = IntArray(root.count) { search.eccentricity(root, it) }
                ecc[g] == ecc.min()
            }
            else -> false
        }
    }
}
