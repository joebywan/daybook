package com.joebywan.daybook.puzzles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joebywan.daybook.core.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Quartets' board drawn and tapped, as a finger does it (Who's Who's lesson, PR #486: a pure rule can be right while the
 * board hands its tiles a stale copy). Standard's fixed board: planets, birds, fruit and metals, no word in two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuartetsTapUiTest {

    @get:Rule val rule = createComposeRule()

    private val start = QuartetsLogic.lastResort(Difficulty.STANDARD)

    private fun maskOf(vararg words: String): Int = words.fold(0) { m, w -> m or (1 shl start.words.indexOf(w)) }

    private fun tap(word: String) {
        rule.onNodeWithText(word.uppercase()).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `four of a group and Submit set the group aside`() {
        var state by mutableStateOf<PuzzleState>(start)
        var emitted = 0
        rule.setContent { MaterialTheme { Quartets.Board(state, { state = it; emitted++ }, interactive = true) } }
        rule.onNodeWithText("Submit").assertIsNotEnabled()
        for (w in listOf("mars", "earth", "venus")) tap(w)
        rule.onNodeWithText("Submit").assertIsNotEnabled()
        tap("saturn")
        assertEquals("taps on tiles emit nothing", 0, emitted)
        rule.onNodeWithText("Submit").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(1, emitted)
        val now = state as QuartetsState
        assertEquals(listOf(maskOf("mars", "earth", "venus", "saturn")), now.found)
        assertEquals(emptyList<Int>(), now.misses)
        // The pick is cleared once the group is set aside.
        rule.onNodeWithText("Submit").assertIsNotEnabled()
    }

    @Test
    fun `a wrong four costs a life and keeps the pick`() {
        var state by mutableStateOf<PuzzleState>(start)
        var emitted = 0
        rule.setContent { MaterialTheme { Quartets.Board(state, { state = it; emitted++ }, interactive = true) } }
        for (w in listOf("mars", "earth", "venus", "owl")) tap(w)
        rule.onNodeWithText("Submit").performClick()
        rule.waitForIdle()
        assertEquals(1, emitted)
        val now = state as QuartetsState
        assertEquals(listOf(maskOf("mars", "earth", "venus", "owl")), now.misses)
        assertEquals(QuartetsLogic.LIVES - 1, now.livesLeft)
        assertTrue(now.found.isEmpty())
        rule.onNodeWithText("One away").assertExists()
        // The pick is kept, so the same four can be sent again, and is refused without a cost.
        rule.onNodeWithText("Submit").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals("a repeat emits nothing", 1, emitted)
        rule.onNodeWithText("Already tried").assertExists()
    }

    @Test
    fun `a fifth tap picks nothing and a second tap lets go`() {
        var state by mutableStateOf<PuzzleState>(start)
        var emitted = 0
        rule.setContent { MaterialTheme { Quartets.Board(state, { state = it; emitted++ }, interactive = true) } }
        for (w in listOf("owl", "robin", "crow", "eagle", "plum")) tap(w)
        // Four picked, the fifth refused: Submit takes exactly the birds.
        rule.onNodeWithText("Submit").performClick()
        rule.waitForIdle()
        assertEquals(listOf(maskOf("owl", "robin", "crow", "eagle")), (state as QuartetsState).found)
        // Tap again lets go.
        tap("plum")
        tap("apple")
        tap("apple")
        tap("pear")
        tap("lemon")
        rule.onNodeWithText("Submit").assertIsNotEnabled()
        tap("apple")
        rule.onNodeWithText("Submit").performClick()
        rule.waitForIdle()
        assertEquals(2, emitted)
        assertEquals(maskOf("plum", "apple", "pear", "lemon"), (state as QuartetsState).found.last())
    }
}
