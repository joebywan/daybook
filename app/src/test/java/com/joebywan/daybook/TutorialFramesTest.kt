package com.joebywan.daybook

import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TutorialStep
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the runner relies on in every walkthrough, now that a finished move advances by itself. */
class TutorialFramesTest {
    @Test
    fun `no move frame is finished before the player moves, or it would skip itself`() {
        for (puzzle in PuzzleRegistry.all) {
            puzzle.tutorial.forEachIndexed { i, frame ->
                val judge = frame.judge ?: return@forEachIndexed
                assertNotEquals("${puzzle.id} frame ${i + 1}", TutorialStep.DONE, judge(frame.state))
            }
        }
    }

    @Test
    fun `a free-play frame is not also a move frame`() {
        for (puzzle in PuzzleRegistry.all) {
            puzzle.tutorial.filter { it.freePlay }.forEach {
                assertNull(puzzle.id, it.judge)
            }
        }
    }
}
