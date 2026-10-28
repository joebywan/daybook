package com.joebywan.daybook

import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.CagesTraining
import com.joebywan.daybook.core.Course
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementDrill
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.InequalityTraining
import com.joebywan.daybook.core.RegionsTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.core.newlyTrained
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.InequalityState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.RegionsLogic
import com.joebywan.daybook.puzzles.RegionsState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Training for Cage Sudoku, Inequality and Regions (docs/TRAINING_SPEC.md); Sudoku's is `SudokuTrainingTest`. Each
 * practice is checked against singles found here by plain counting and against each puzzle's oracle, neither of which
 * shares anything with the teachers.
 */
class TrainingTest {

    /** A board's singles by plain counting: [units] each hold one of each digit, [fits] says whether a digit may go in a square. */
    private class Singles(cells: List<Int>, units: List<List<Int>>, digitsOf: (List<Int>) -> IntRange, fits: (Int, Int) -> Boolean) {
        val last = units.any { u -> u.count { cells[it] == 0 } == 1 }
        val naked = cells.indices.any { i -> cells[i] == 0 && (1..9).count { fits(i, it) } == 1 }
        val hidden = units.any { u -> digitsOf(u).any { d -> u.none { cells[it] == d } && u.count { cells[it] == 0 && fits(it, d) } == 1 } }
        val any: Boolean get() = last || naked || hidden
    }

    // ---- Cage Sudoku: rows, columns, boxes; a digit is out if a peer or a cage-mate holds it -----------------------

    private val sudokuUnits: List<List<Int>> = List(9) { r -> List(9) { r * 9 + it } } + List(9) { c -> List(9) { it * 9 + c } } +
        List(9) { b -> List(9) { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 } }

    private fun cageSingles(s: CageState, cells: List<Int>, units: List<List<Int>>): Singles {
        val cage = s.cageOf
        fun fits(i: Int, d: Int) = cells[i] == 0 && d in 1..9 && cells.indices.none { j ->
            j != i && cells[j] == d && (cage[j] == cage[i] || sudokuUnits.any { i in it && j in it })
        }
        return Singles(cells, units, { 1..9 }, ::fits)
    }

    private fun cageLast(s: CageState, cells: List<Int>): Boolean = s.sums.indices.any { k ->
        val members = cells.indices.filter { s.cageOf[it] == k }
        members.count { cells[it] == 0 } == 1 && (s.sums[k] - members.sumOf { cells[it] }) in 1..9
    }

    private fun checkCages(t: String, s: CageState) {
        val c = s.cells
        assertEquals("one answer", 1, CageOracle.count(s.cageOf, s.sums, c))
        val all = cageSingles(s, c, sudokuUnits)
        val boxes = cageSingles(s, c, sudokuUnits.drop(18))
        val lines = cageSingles(s, c, sudokuUnits.take(18))
        when (CagesTraining.techniques.indexOf(t)) {
            0 -> assertTrue(all.last)
            1 -> assertTrue(!all.last && cageLast(s, c))
            2 -> assertTrue(!all.last && !cageLast(s, c) && boxes.hidden)
            3 -> assertTrue(!all.last && !cageLast(s, c) && !boxes.hidden && lines.hidden)
            4 -> assertTrue(!all.last && !cageLast(s, c) && !all.hidden && all.naked)
            else -> assertTrue("$t board has a single", !all.any && !cageLast(s, c))
        }
    }

    // ---- Inequality: rows and columns; the signs cut each end's bounds to a fixpoint -------------------------------

    private fun checkInequality(t: String, s: InequalityState) {
        val n = s.size
        val c = s.cells
        assertEquals("one answer", 1L, InequalityOracle.count(n, c, s.signs))
        val units = List(n) { r -> List(n) { r * n + it } } + List(n) { col -> List(n) { it * n + col } }
        fun plain(i: Int, d: Int) = d in 1..n && units.none { u -> i in u && u.any { cells -> c[cells] == d } }
        // Each square's digits from the plain rule, then every sign's ends bounded by each other until nothing moves.
        val can = Array(n * n) { i -> if (c[i] != 0) mutableSetOf(c[i]) else (1..n).filterTo(mutableSetOf()) { plain(i, it) } }
        do {
            var moved = false
            for (sign in s.signs) {
                val hiMax = can[sign.hi].maxOrNull() ?: 0
                val loMin = can[sign.lo].minOrNull() ?: n + 1
                if (can[sign.lo].removeAll { it >= hiMax }) moved = true
                if (can[sign.hi].removeAll { it <= loMin }) moved = true
            }
        } while (moved)
        val bare = Singles(c, units, { 1..n }, ::plain)
        val signed = Singles(c, units, { 1..n }) { i, d -> c[i] == 0 && d in can[i] }
        when (InequalityTraining.techniques.indexOf(t)) {
            0 -> assertTrue(bare.last)
            1 -> assertTrue(!bare.last && bare.naked)
            2 -> assertTrue(!bare.last && !bare.naked && bare.hidden)
            3 -> assertTrue(!bare.any && signed.naked)
            4 -> assertTrue(!bare.any && !signed.naked && signed.hidden)
            else -> assertTrue("$t board has a single", !bare.any && !signed.any)
        }
    }

    // ---- Regions: a region of k squares holds 1..k; equal digits never touch, even at a corner ---------------------

    private fun checkRegions(t: String, s: RegionsState) {
        val n = s.size
        val c = s.cells
        assertEquals("one answer", 1L, RegionsOracle.count(n, s.region, c))
        val units = s.region.distinct().map { r -> c.indices.filter { s.region[it] == r } }
        fun touches(i: Int, j: Int) = i != j && kotlin.math.abs(i / n - j / n) <= 1 && kotlin.math.abs(i % n - j % n) <= 1
        fun fits(i: Int, d: Int): Boolean {
            val mine = units.first { i in it }
            return c[i] == 0 && d <= mine.size && mine.none { c[it] == d } && c.indices.none { touches(i, it) && c[it] == d }
        }
        val x = Singles(c, units, { 1..it.size }, ::fits)
        when (RegionsTraining.techniques.indexOf(t)) {
            0 -> assertTrue(x.last)
            1 -> assertTrue(!x.last && x.naked)
            2 -> assertTrue(!x.last && !x.naked && x.hidden)
            else -> assertTrue("$t board has a single", !x.any)
        }
    }

    private fun check(course: Course, count: Int, independent: (String, PuzzleState) -> Unit) {
        for (t in course.techniques) {
            val list = course.practices(t).take(count).toList()
            assertEquals("practices for ${course.id} $t", count, list.size)
            assertEquals("distinct boards for ${course.id} $t", count, list.map { it.board }.distinct().size)
            val drill = course.drill as PlacementDrill
            for (p in list) {
                val cells = drill.cellsOf(p.board)
                val answer = drill.solutionOf(p.board)
                val step = p.step as PlacementStep
                assertEquals(t, p.step.technique)
                assertEquals(t, course.puzzle.teach(p.board)!!.technique)
                assertEquals("the step's digit is the answer's", answer[step.cell], step.digit)
                assertEquals(0, cells[step.cell])
                assertTrue("shown digits agree with the answer", cells.indices.all { cells[it] == 0 || cells[it] == answer[it] })
                independent(t, p.board)
            }
        }
    }

    @Test fun `cage practices are real positions whose next step is their technique`() =
        check(CagesTraining, 5) { t, b -> checkCages(t, b as CageState) }

    @Test fun `inequality practices are real positions whose next step is their technique`() =
        check(InequalityTraining, 8) { t, b ->
            val s = b as InequalityState
            assertTrue("every digit shown is a given", s.cells.indices.all { (s.cells[it] != 0) == s.givens[it] })
            checkInequality(t, s)
        }

    @Test fun `regions practices are real positions whose next step is their technique`() =
        check(RegionsTraining, 8) { t, b ->
            val s = b as RegionsState
            assertTrue("every digit shown is a given", s.cells.indices.all { (s.cells[it] != 0) == s.givens[it] })
            checkRegions(t, s)
        }

    @Test fun `the session cache hands out the same boards as a fresh search, in any order of asking`() = runBlocking {
        val c = RegionsTraining
        val t = c.techniques.last()
        val fresh = c.practices(t).take(5).map { it.board }.toList()
        assertEquals(fresh[3], c.practice(t, 3)!!.board)
        assertEquals(fresh, (0 until 5).map { c.practice(t, it)!!.board })
    }

    @Test fun `every course lists its teacher's named techniques, each explained, and its final board needs them all`() {
        // Appended only: the achievements follow this order.
        assertEquals(listOf("sudoku", "cages", "inequality", "regions", "kings", "whoswho", "cipher", "chess"), Training.COURSES.map { it.id })
        // The teacher's named techniques in its order, less any no practice search reaches (docs/TRAINING_SPEC.md).
        for (c in Training.COURSES) assertEquals(c.id, techniqueNamesOf(c.id).keys.filter { it in c.techniques }, c.techniques)
        assertEquals(Training.seed(0), CagesTraining.examSeed)
        assertEquals(Training.seed(4), InequalityTraining.examSeed)
        assertEquals(Training.seed(0), RegionsTraining.examSeed)
        assertNotNull(CageLogic.generateVerified(CagesTraining.examSeed!!, Difficulty.EXPERT))
        assertNotNull(Inequality.generateVerified(InequalityTraining.examSeed!!, Difficulty.EXPERT))
        assertNotNull(RegionsLogic.generateVerified(RegionsTraining.examSeed!!, Difficulty.EXPERT))
        for (c in Training.COURSES.drop(1).take(3)) assertEquals(c.id, c.techniques.toSet(), c.techniquesUsed(c.exam()!!))
    }

    @Test fun `each course earns its own two achievements, appended after the pilot's`() {
        val ids = ACHIEVEMENTS.map { it.id }
        assertEquals(
            listOf("sudokuTrained", "sudokuGraduate", "cagesTrained", "cagesGraduate", "inequalityTrained", "inequalityGraduate",
                "regionsTrained", "regionsGraduate"),
            ids.subList(ids.indexOf("sudokuTrained"), ids.indexOf("sudokuTrained") + 8),
        )
        // Every course after them brings its two, in the courses' order, at the end of the list.
        assertEquals(Training.COURSES.flatMap { listOf("${it.id}Trained", "${it.id}Graduate") }, ids.takeLast(2 * Training.COURSES.size))
        val today = LocalDate(2026, 10, 28)
        for (c in Training.COURSES.drop(1)) {
            val all = c.techniques.map { TrainingRecord.entry(c.id, it) }.toSet()
            fun got(t: Set<String>) = earnedAchievements(emptyList(), today, training = t).map { it.id }.toSet()
            assertFalse(got(all - all.first()).any { it.startsWith(c.id) })
            assertEquals(listOf("${c.id}Trained"), newlyTrained(all - all.last(), c.id, c.techniques.last()).map { it.id })
            assertEquals(listOf("${c.id}Graduate"), newlyTrained(all, c.id, Training.EXAM).map { it.id })
            // Another puzzle's record never earns this one's.
            assertTrue(newlyTrained(emptySet(), "sudoku", Training.EXAM).none { it.id.startsWith(c.id) })
        }
    }
}
