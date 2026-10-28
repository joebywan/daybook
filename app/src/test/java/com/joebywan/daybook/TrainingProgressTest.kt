package com.joebywan.daybook

import com.joebywan.daybook.core.AtomsTraining
import com.joebywan.daybook.core.Course
import com.joebywan.daybook.core.PearlsTraining
import com.joebywan.daybook.core.WhosWhoTraining
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.KingsTraining
import com.joebywan.daybook.core.LampsTraining
import com.joebywan.daybook.core.LitsTraining
import com.joebywan.daybook.core.MamboTraining
import com.joebywan.daybook.core.MinesTraining
import com.joebywan.daybook.core.NonogramTraining
import com.joebywan.daybook.core.TallyTraining
import com.joebywan.daybook.core.TentsTraining
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.KingsTeacher
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Mark
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.TentsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progress line on a step that takes several moves (`docs/TRAINING_SPEC.md`): a right part of the step says how far
 * along it is, without a verdict (so the run is untouched), and a true move that no instance of the technique justifies
 * says nothing. Partial boards are built here by copying the step's squares one at a time from its own Show me, through
 * each state's own fields, not through the drill's view.
 */
class TrainingProgressTest {

    private fun String.with(i: Int, c: Char) = substring(0, i) + c + substring(i + 1)

    /** Course, and how to copy square i of one state onto another. */
    private val courses: List<Pair<Course, (PuzzleState, Int, PuzzleState) -> PuzzleState>> = listOf(
        KingsTraining to { s, i, f -> (s as KingsState).copy(marks = s.marks.toMutableList().also { it[i] = (f as KingsState).marks[i] }) },
        TentsTraining to { s, i, f -> (s as TentsState).withMark(i, (f as TentsState).marks[i]) },
        NonogramTraining to { s, i, f -> (s as NonogramState).copy(cells = s.cells.with(i, (f as NonogramState).cells[i])) },
        LitsTraining to { s, i, f -> (s as LitsState).copy(shaded = s.shaded.toMutableList().also { it[i] = (f as LitsState).shaded[i] }) },
        MinesTraining to { s, i, f -> (s as MinesState).copy(marks = s.marks.with(i, (f as MinesState).marks[i])) },
        LampsTraining to { s, i, f -> (s as LampsState).withMark(i, (f as LampsState).marks[i]) },
        TallyTraining to { s, i, f -> (s as TallyState).copy(marks = s.marks.with(i, (f as TallyState).marks[i])) },
        MamboTraining to { s, i, f -> (s as MamboState).copy(cells = s.cells.toMutableList().also { it[i] = (f as MamboState).cells[i] }) },
        // A pearls target is an edge, numbered after the squares (PearlsTeacher.edgeIndex).
        PearlsTraining to { s, i, f -> (s as PearlsState).let { p -> p.copy(edges = p.edges.with(i - p.size * p.size, (f as PearlsState).edges[i - p.size * p.size])) } },
        // An atoms step's target is a gap, numbered after the atoms (AtomsTeacher.pairCell).
        AtomsTraining to { s, i, f ->
            (s as AtomsState).copy(counts = s.counts.toMutableList().also { it[i - s.atoms.size] = (f as AtomsState).counts[i - s.atoms.size] })
        },
        // A who's-who target is a square of the table, n marks wide.
        WhosWhoTraining to { s, i, f -> (s as WhosWhoState).let { w -> w.copy(marks = w.marks.replaceRange(i * w.n, (i + 1) * w.n, (f as WhosWhoState).marks.substring(i * w.n, (i + 1) * w.n))) } },
    )

    /** The step's squares that its Show me changes, read off each state's own fields. */
    private fun changed(b: PuzzleState, f: PuzzleState, targets: Set<Int>): List<Int> = targets.sorted().filter { i ->
        when (b) {
            is KingsState -> b.marks[i] != (f as KingsState).marks[i]
            is TentsState -> b.seen[i] != (f as TentsState).seen[i] // grass already sprouted round a tent is shown: no move
            is NonogramState -> b.cells[i] != (f as NonogramState).cells[i]
            is LitsState -> b.shaded[i] != (f as LitsState).shaded[i]
            is MinesState -> b.marks[i] != (f as MinesState).marks[i]
            is LampsState -> b.marks[i] != (f as LampsState).marks[i]
            is TallyState -> b.marks[i] != (f as TallyState).marks[i]
            is MamboState -> b.cells[i] != (f as MamboState).cells[i]
            is PearlsState -> b.edges[i - b.size * b.size] != (f as PearlsState).edges[i - b.size * b.size]
            is AtomsState -> b.counts[i - b.atoms.size] != (f as AtomsState).counts[i - b.atoms.size]
            is WhosWhoState -> b.marks.substring(i * b.n, (i + 1) * b.n) != (f as WhosWhoState).marks.substring(i * b.n, (i + 1) * b.n)
            else -> error("no course $b")
        }
    }

    @Test fun `a right part of a step shows how far along it is, and neither counts nor breaks the run`() {
        var seen = 0
        for ((course, put) in courses) {
            for (t in course.techniques) {
                for (p in course.practices(t).take(2)) {
                    val d = p.step as Deduction
                    val full = d.apply(p.board)
                    val need = changed(p.board, full, d.targets)
                    if (need.size < 2) continue
                    var s = p.board
                    for ((k, i) in need.dropLast(1).withIndex()) {
                        s = put(s, i, full)
                        // A step reached early (a king's sweep counts its crosses), or another instance finished on
                        // the way (one of Mines' subsets inside a bigger one), is right, as it should be.
                        val verdict = course.judge(p, s)
                        if (d.isReached(s) || verdict == true) break
                        assertNull("${course.id} $t: no verdict for part of a step", verdict)
                        assertEquals("${course.id} $t", "Right so far: ${k + 1} of ${need.size}. Keep going.", course.progress(p, s))
                        seen++
                    }
                    assertEquals("${course.id} $t: the whole step is right", true, course.judge(p, full))
                    assertNull(course.progress(p, p.board))
                }
            }
        }
        assertTrue("some steps take several moves ($seen)", seen > 20)
    }

    @Test fun `a true grass no instance of no tree justifies gets no progress line`() {
        val p = TentsTraining.practices(TentsTeacher.NO_TREE).first()
        val b = p.board as TentsState
        val n = b.size
        fun orth(i: Int) = listOf(i - n, i + n).filter { it in 0 until n * n } +
            listOf(i - 1, i + 1).filter { it in 0 until n * n && it / n == i / n }
        // Beside a tree, so no "no tree" step can name it, and not the answer's tent, so grass there is true.
        val x = b.marks.indices.first { !b.trees[it] && b.seen[it] == 0 && !b.solution[it] && orth(it).any { j -> b.trees[j] } }
        val after = b.withMark(x, TentsLogic.GRASS)
        assertNull(TentsTraining.judge(p, after))
        assertNull(TentsTraining.progress(p, after))
        // And one of the step's own squares, beside it, still counts.
        val d = p.step as Deduction
        val one = b.withMark(d.targets.first { b.seen[it] == 0 }, TentsLogic.GRASS)
        assertTrue(TentsTraining.progress(p, one)!!.startsWith("Right so far: 1 of "))
    }

    @Test fun `a true cross outside every locked line gets no progress line`() {
        var checked = 0
        for (p in KingsTraining.practices(KingsTeacher.LOCKED).take(5)) {
            val b = p.board as KingsState
            val named = KingsTeacher.steps(b.size, b.region, b.marks, KingsTeacher.LOCKED).flatMap { it.targets }.toSet() + p.step.targets
            val x = b.marks.indices.firstOrNull { b.marks[it] == Mark.EMPTY && it !in b.solution && it !in named } ?: continue
            val after = b.copy(marks = b.marks.toMutableList().also { it[x] = Mark.BLOCKED })
            assertNull(KingsTraining.judge(p, after))
            assertNull(KingsTraining.progress(p, after))
            checked++
        }
        assertTrue(checked > 0)
    }
}
