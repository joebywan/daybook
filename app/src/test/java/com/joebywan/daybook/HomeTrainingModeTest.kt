package com.joebywan.daybook

import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.backFrom
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import com.joebywan.daybook.ui.home.LaunchMode.DAILY
import com.joebywan.daybook.ui.home.LaunchMode.LEVELS
import com.joebywan.daybook.ui.home.LaunchMode.RANDOM
import com.joebywan.daybook.ui.home.LaunchMode.TRAINING
import com.joebywan.daybook.ui.home.ModeSegment
import com.joebywan.daybook.ui.home.segment
import com.joebywan.daybook.ui.home.tappedMode
import com.joebywan.daybook.core.Training.tally
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's Training mode: the mode row's shared Daily/Random segment, the progress it shows, and the course route it opens. */
class HomeTrainingModeTest {

    @Test fun `the first segment swaps Daily and Random when selected, and returns to the last of them otherwise`() {
        assertEquals(RANDOM, tappedMode(ModeSegment.DAY, DAILY, DAILY))
        assertEquals(DAILY, tappedMode(ModeSegment.DAY, RANDOM, RANDOM))
        assertEquals(DAILY, tappedMode(ModeSegment.DAY, LEVELS, DAILY))
        assertEquals(RANDOM, tappedMode(ModeSegment.DAY, TRAINING, RANDOM))
        assertEquals(LEVELS, tappedMode(ModeSegment.LEVELS, RANDOM, RANDOM))
        assertEquals(TRAINING, tappedMode(ModeSegment.TRAINING, DAILY, DAILY))
        assertEquals(ModeSegment.DAY, RANDOM.segment)
        assertEquals(ModeSegment.TRAINING, TRAINING.segment)
    }

    @Test fun `the tally counts graduations and passed techniques, never the exam as a technique`() {
        val sudoku = Training.of("sudoku")!!
        val kings = Training.of("kings")!!
        val record = sudoku.techniques.map { TrainingRecord.entry("sudoku", it) }.toSet() +
            TrainingRecord.entry("sudoku", Training.EXAM) + TrainingRecord.entry("kings", kings.techniques.first())
        val t = tally(record)
        assertEquals(1, t.graduated)
        assertEquals(Training.COURSES.size, t.courses)
        assertEquals(sudoku.techniques.size + 1, t.passed)
        assertEquals(tally(emptySet()).let { it.graduated to it.passed }, 0 to 0)
    }

    @Test fun `a course opened from Home is a Training route that old builds already wrote, and its practices come back to it`() {
        // Home opens a course as the rules sheet does: fromHint, so back is the screen it left (Home, mode kept).
        val course = Route.Training(fromHint = true, puzzle = "kings")
        assertEquals(course, decodeRoute(encodeRoute(course)))
        assertEquals(course, decodeRoute("training||true|kings|false"))
        val practice = Route.Training("last-square", puzzle = "kings", listFromHint = true)
        assertEquals(practice, decodeRoute(encodeRoute(practice)))
        assertEquals(course, backFrom(practice))
    }
}
