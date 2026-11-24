package com.joebywan.daybook.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.PuzzleOrder
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home grid drawn and a tile dragged to another row, as a finger does it (issue #531). When rows were as tall as
 * their own tallest tile, a wrapped name changing rows changed the heights mid-drag, which restarted the gesture and
 * dropped the tile where it started; sideways moves worked. Every tile is one height now, whatever the order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDragUiTest {

    @get:Rule val rule = createComposeRule()

    private val ids = listOf("wordwheel", "sudoku", "kings", "tents", "lamps", "mines")

    @Test
    fun `a tile with a wrapped name drags to the row below`() {
        var order: List<String>? = null
        rule.setContent {
            // A large font on a narrow grid, so "Word Wheel" takes two lines and the one-word names one.
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                MaterialTheme {
                    Box(Modifier.width(300.dp)) {
                        PuzzleGrid(
                            puzzles = ids.map { PuzzleRegistry.byId(it)!! },
                            inProgress = { emptySet() },
                            difficulty = Difficulty.STANDARD,
                            doneToday = emptySet(),
                            height = 600.dp,
                            onLaunch = {},
                            onOrder = { order = it },
                            scroll = { 0f },
                            window = GridWindow(),
                        )
                    }
                }
            }
        }
        fun bounds(name: String) = rule.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val from = bounds("Word Wheel")
        val to = bounds("Tents")
        assertTrue("the test needs a wrapped name beside one-line ones: $from ${bounds("Sudoku")}", from.height > 1.5f * bounds("Sudoku").height)
        rule.onNodeWithText("Word Wheel", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            val by = to.center - from.center
            repeat(10) { moveBy(by / 10f, 50) }
            up()
        }
        rule.waitForIdle()
        assertEquals(PuzzleOrder.moved(ids, 0, 3), order)
        val heights = ids.map { rule.onNodeWithText(PuzzleRegistry.byId(it)!!.displayName).fetchSemanticsNode().size.height }
        assertEquals("every tile is one height: $heights", 1, heights.toSet().size)
    }
}
