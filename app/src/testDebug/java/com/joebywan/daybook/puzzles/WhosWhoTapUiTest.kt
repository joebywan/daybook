package com.joebywan.daybook.puzzles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Who's Who's board drawn and tapped, as a finger does it. The pure tap ([WhosWhoGraphState.tap]) was right while the
 * board handed its pills a stale copy of it, so a second pill was chosen instead of linked (PR #486); only a test
 * that composes the board, taps and lets it recompose between taps sees that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhosWhoTapUiTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `a tap on a pill of another category links it to the chosen one`() {
        val start = WhosWho.tutorialBoard()
        var state by mutableStateOf<PuzzleState>(start)
        rule.setContent { MaterialTheme { WhosWho.Board(state, { state = it }, interactive = true) } }
        val ann = WhosWhoLogic.word(start.board, 0)
        val cat = WhosWhoLogic.word(start.board, 2 * start.n)
        rule.onNodeWithText(ann).performClick()
        rule.waitForIdle()
        assertEquals("choosing a pill changes no board", start, state)
        rule.onNodeWithText(cat).performClick()
        rule.waitForIdle()
        assertEquals(start.cycle(0, 2 * start.n), state)
        rule.onNodeWithText(cat).performClick()
        rule.waitForIdle()
        assertEquals("a second tap moves the same pair on", start.cycle(0, 2 * start.n).cycle(0, 2 * start.n), state)
    }
}
