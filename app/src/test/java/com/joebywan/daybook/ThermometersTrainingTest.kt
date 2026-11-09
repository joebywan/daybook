package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.ThermometersTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Thermometers
import com.joebywan.daybook.puzzles.ThermometersLogic
import com.joebywan.daybook.puzzles.ThermometersState
import com.joebywan.daybook.puzzles.ThermometersTeacher
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thermometers' course (docs/TRAINING_SPEC.md). Each practice's board is counted by [ThermometersOracle] (one answer,
 * the walked marks agree with it), and the moves judged are built by the board's own taps.
 */
class ThermometersTrainingTest {

    private fun counts(s: ThermometersState): Pair<List<Int>, List<Int>> {
        val n = s.size
        return List(n) { r -> (0 until n).count { s.fill[r * n + it] == '1' } } to List(n) { c -> (0 until n).count { s.fill[it * n + c] == '1' } }
    }

    private fun with(b: ThermometersState, i: Int, c: Char) = b.copy(marks = b.marks.substring(0, i) + c + b.marks.substring(i + 1))

    @Test fun `every practice is a real position whose next step is its technique`() {
        assertTrue(ThermometersTraining.drill is MoveDrill)
        for (t in ThermometersTraining.techniques) {
            val list = ThermometersTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as ThermometersState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Thermometers.teach(b)!!.technique)
                val (rows, cols) = counts(b)
                assertEquals("one answer", listOf(b.fill), ThermometersOracle.answers(b.size, b.tubes, rows, cols, cap = 2))
                assertTrue("walked marks agree with it", b.marks.indices.all {
                    b.marks[it] == ThermometersLogic.UNMARKED || (b.marks[it] == ThermometersLogic.FILLED) == (b.fill[it] == '1')
                })
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { b.marks[it] == ThermometersLogic.UNMARKED })
                val after = d.apply(b) as ThermometersState
                for (i in d.targets) assertEquals(b.fill[i] == '1', after.marks[i] == ThermometersLogic.FILLED)
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the answer disagrees, and otherwise plays on`() {
        var others = 0
        var lucky = 0
        var taps = 0
        for (t in ThermometersTraining.techniques) {
            for (p in ThermometersTraining.practices(t).take(3)) {
                val b = p.board as ThermometersState
                val d = p.step as Deduction
                assertEquals(true, ThermometersTraining.judge(p, d.apply(b)))
                // A one-thermometer step is one tap, as the board takes it: fill to its highest square, or cross from its lowest.
                val step = ThermometersTeacher.deduce(b.size, b.tubes, ThermometersLogic.targets(b), b.marks)!!
                val l = ThermometersLogic.layout(b)
                if (step.full.map { l.tubeOf[it] }.distinct().size + step.empty.map { l.tubeOf[it] }.distinct().size == 1) {
                    val tapped = if (step.full.isNotEmpty()) b.tap(step.full.maxBy { l.posOf[it] }, ThermometersLogic.FILLED)
                    else b.tap(step.empty.minBy { l.posOf[it] }, ThermometersLogic.CROSSED)
                    assertEquals("$t by one tap", true, ThermometersTraining.judge(p, tapped))
                    taps++
                }
                // A wrong fill, or a wrong cross, is wrong wherever it is.
                val open = b.marks.indices.filter { b.marks[it] == ThermometersLogic.UNMARKED }
                for (i in listOf(open.first(), d.targets.first())) {
                    val wrong = if (b.fill[i] == '1') ThermometersLogic.CROSSED else ThermometersLogic.FILLED
                    assertEquals(false, ThermometersTraining.judge(p, with(b, i, wrong)))
                }
                // Another instance of the technique is right; a true mark no instance justifies plays on.
                val same = ThermometersTeacher.steps(b.size, b.tubes, ThermometersLogic.targets(b), b.marks, t).map { Thermometers.deduction(b, it) }
                assertTrue("the teacher's own step is among its instances", same.any { it.targets == d.targets })
                same.firstOrNull { it.targets != d.targets }?.let { assertEquals(true, ThermometersTraining.judge(p, it.apply(b))); others++ }
                val justified = same.flatMap { it.targets }.toSet()
                open.firstOrNull { it !in justified }?.let { i ->
                    val right = if (b.fill[i] == '1') ThermometersLogic.FILLED else ThermometersLogic.CROSSED
                    assertNull(ThermometersTraining.judge(p, with(b, i, right)))
                    lucky++
                }
            }
        }
        assertTrue("some practice has another instance to find", others > 0)
        assertTrue("and a true mark nothing justifies", lucky > 0)
        assertTrue("and one-tap steps", taps > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("thermometers").keys.toList(), ThermometersTraining.techniques)
        val index = (0 until 60).first { k ->
            ThermometersTraining.techniquesUsed(Thermometers.generate(Training.seed(k), Difficulty.EXPERT)) == ThermometersTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), ThermometersTraining.examSeed)
        assertNotNull(ThermometersLogic.generateVerified(ThermometersTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(ThermometersTraining.techniques.toSet(), ThermometersTraining.techniquesUsed(ThermometersTraining.exam()!!))
    }

    @Test fun `thermometers routes round-trip`() {
        listOf(Route.Training(puzzle = "thermometers"), Route.Training("bulb-must", puzzle = "thermometers"), Route.Training("exam", puzzle = "thermometers"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
