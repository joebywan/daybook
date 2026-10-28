package com.joebywan.daybook

import com.joebywan.daybook.core.KingsTraining
import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.puzzles.KingsTeacher
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.ui.training.Cheer
import com.joebywan.daybook.ui.training.graduateCheer
import com.joebywan.daybook.ui.training.passCheer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner on 1.19: three in a row on "The last square in a row, column or box", already passed in the pilot, and no
 * congratulation. A run that reaches [Training.RUN] is celebrated every time; only the achievements are once. Since 1.31
 * the celebration is the game's finished frame with the progress to the course's next achievement, so this checks what
 * the popup says and counts.
 */
class TrainingPassLineTest {
    private val c = SudokuTraining
    private val t = SudokuTeacher.FULL_HOUSE
    private fun entry(technique: String) = TrainingRecord.entry(c.id, technique)
    private val othersPassed = (c.techniques - t).map(::entry).toSet()
    private val n = c.techniques.size

    @Test fun celebratedOnFirstPass() {
        val p = cheer(emptySet())
        assertEquals("Passed!", p.praise.title)
        assertEquals(listOf(c.titleOf(t), "3 in a row"), p.praise.lines)
        assertEquals(emptyList<Any>(), p.praise.achievements)
        assertEquals("Sudoku trained: 1 of $n techniques", p.praise.toGo)
        assertEquals("sudokuTrained", p.badge)
        assertFalse(p.badgeEarned)
        assertEquals(1, p.passed)
        assertEquals(n, p.of)
        assertTrue(p.fresh)
    }

    @Test fun rePassShowsTheSameProgressWithNothingNew() {
        val p = cheer(setOf(entry(t)))
        assertEquals("Passed!", p.praise.title)
        assertEquals(emptyList<Any>(), p.praise.achievements)
        assertEquals("Sudoku trained: 1 of $n techniques", p.praise.toGo)
        assertEquals(1, p.passed)
        assertFalse(p.fresh)
    }

    @Test fun progressCountsOtherPassedTechniques() {
        val two = setOf(entry(c.techniques[1]), entry(c.techniques[2]))
        val p = cheer(two)
        assertEquals(3, p.passed)
        assertEquals("Sudoku trained: 3 of $n techniques", p.praise.toGo)
        // Another course's passes and the exam are not this course's techniques.
        val noise = setOf(TrainingRecord.entry("kings", KingsTeacher.LAST_SQUARE), entry(Training.EXAM))
        assertEquals(1, cheer(noise).passed)
    }

    @Test fun onlyWhenTheRunReachesThree() {
        assertNull(passCheer(c, emptySet(), t, Training.RUN - 1))
        assertNull(passCheer(c, emptySet(), t, Training.RUN + 1))
    }

    @Test fun achievementOnlyTheFirstTimeThenOnToTheFinalBoard() {
        val first = cheer(othersPassed)
        assertEquals(listOf("Sudoku trained"), first.praise.achievements.map { it.title })
        assertEquals("Final board unlocked. Solve it to become a Sudoku graduate.", first.praise.toGo)
        assertEquals(n, first.passed)
        assertTrue(first.badgeEarned)
        val again = cheer(othersPassed + entry(t))
        assertEquals(emptyList<Any>(), again.praise.achievements)
        assertEquals("Sudoku trained: $n of $n techniques. The final board is open: solve it to become a Sudoku graduate.", again.praise.toGo)
        val graduated = cheer(othersPassed + entry(t) + entry(Training.EXAM))
        assertEquals("Sudoku trained: $n of $n techniques", graduated.praise.toGo)
    }

    @Test fun graduateAchievementOnlyTheFirstTimeWithTrainingTally() {
        val all = c.techniques.map(::entry).toSet()
        val first = graduateCheer(c, all)
        assertEquals("Graduated!", first.praise.title)
        assertEquals("Sudoku graduate", first.praise.lines.first())
        assertEquals(listOf("Sudoku graduate"), first.praise.achievements.map { it.title })
        assertEquals("Training: 1 of ${Training.COURSES.size} courses graduated", first.praise.toGo)
        assertEquals("sudokuGraduate", first.badge)
        assertEquals(0, first.of)
        val kingsDone = (KingsTraining.techniques + Training.EXAM).map { TrainingRecord.entry("kings", it) }
        val again = graduateCheer(c, all + entry(Training.EXAM) + kingsDone)
        assertEquals(emptyList<Any>(), again.praise.achievements)
        assertEquals("Training: 2 of ${Training.COURSES.size} courses graduated", again.praise.toGo)
    }

    private fun cheer(record: Set<String>): Cheer = passCheer(c, record, t, Training.RUN).also { assertNotNull(it) }!!
}
