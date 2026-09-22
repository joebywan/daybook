package com.joebywan.daybook

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pane's fixed budget: the strip across it, and the grid under what the strip leaves.
 *
 * The found-set strip does not scroll and does not wrap, so the whole tier's worth of slots has to
 * survive being divided across one line — and a thumbnail that gets too narrow stops carrying the
 * four traits, which is the only reason it is on screen.
 *
 * The grid has the harder half of the same bargain. It used to size itself from width alone, so
 * four rows of card came out taller than the pane and the bottom row ran under the tool bar — seen
 * on an emulator at Expert, not merely predicted. [Sets.cardWidth] now takes the smaller of what
 * each axis allows, which makes both "it fits" and "it is still big enough to read" arithmetic
 * that can be checked here instead of on a device.
 *
 * Widths below are the board's content box: the screen less its 16dp of padding on each side.
 */
class SetsStripTest {

    /** 320dp is the narrowest phone still supported; the rest are the common sizes above it. */
    private val screens = listOf(320.dp, 360.dp, 384.dp, 393.dp, 411.dp, 412.dp)

    private fun content(screen: Dp) = screen - 32.dp

    private fun targets() = Difficulty.entries.map { difficulty ->
        difficulty to (Sets.generate(1L, difficulty) as SetsState).target
    }

    @Test
    fun `a tier's slots all fit on one line at every supported width`() {
        for (screen in screens) {
            val available = content(screen)
            for ((difficulty, target) in targets()) {
                val used = Sets.slotWidth(available, target) * target +
                    Sets.SLOT_GAP * (target - 1)
                assertTrue(
                    "$difficulty needs $used of $available at $screen — the strip would clip",
                    used <= available,
                )
            }
        }
    }

    @Test
    fun `no slot is squeezed below the width its traits were judged legible at`() {
        for (screen in screens) {
            val available = content(screen)
            for ((difficulty, target) in targets()) {
                val width = Sets.slotWidth(available, target)
                assertTrue(
                    "$difficulty gets ${width}-wide slots at $screen, under ${Sets.SLOT_MIN_WIDTH}",
                    width >= Sets.SLOT_MIN_WIDTH,
                )
            }
        }
    }

    @Test
    fun `a slot never grows past its cap however few the tier asks for`() {
        // Without the cap a Standard board's three slots would stretch to a third of the screen
        // each, and a card face inside one would be wider than it is tall.
        for (screen in screens) {
            for (target in 1..6) {
                assertTrue(
                    "$target slots at $screen exceed the cap",
                    Sets.slotWidth(content(screen), target) <= Sets.SLOT_MAX_WIDTH,
                )
            }
        }
    }

    @Test
    fun `the strip has a slot for every set the board is asking for`() {
        // The strip lays out `target` slots from the first move so its height never changes. If a
        // tier could ever claim more sets than its target, the last one would have nowhere to go.
        for (difficulty in Difficulty.entries) {
            val board = Sets.generate(1L, difficulty) as SetsState
            val played = Sets.allSets(board.cards).flatten().fold(board, Sets::tap)
            assertTrue(
                "$difficulty banked ${played.found.size} sets into ${board.target} slots",
                played.found.size <= board.target,
            )
        }
    }

    // ---- the grid under the strip -------------------------------------------------------------

    /**
     * PlayScreen's furniture above and below the board pane, at the default font scale: the title
     * row (~58dp), the clock (~24dp) and the tool bar (~111dp), rounded up.
     *
     * Approximate on purpose, and deliberately generous — these only decide how pessimistic the
     * legibility floor below is, never whether the grid fits, which [Sets.cardWidth] settles from
     * the constraints it is actually handed.
     */
    private val PLAY_CHROME = 200.dp

    /** Status bar plus a three-button navigation bar: the worst the safe-drawing insets get. */
    private val WORST_INSETS = 72.dp

    /** Counter (~24dp), the strip, and the gap under it — the board pane's share of the column. */
    private val ABOVE_BOARD = 24.dp + Sets.SLOT_HEIGHT + 10.dp

    /** Screens the app is expected on, smallest first. 320x533 is a 480x800 hdpi budget phone. */
    private val devices = listOf(
        320.dp to 533.dp,
        320.dp to 640.dp,
        360.dp to 640.dp,
        360.dp to 780.dp,
        393.dp to 851.dp,
        411.dp to 891.dp,
    )

    private fun gridBox(screen: Pair<Dp, Dp>) =
        screen.second - WORST_INSETS - PLAY_CHROME - ABOVE_BOARD

    @Test
    fun `the grid never asks for more room than it was handed`() {
        // The bug in one assertion. Sweeping both axes rather than testing the tiers on today's
        // phones, because the failure was never about a particular device — it was a size derived
        // from one constraint being used against two.
        var width = 280.dp
        while (width <= 440.dp) {
            var height = 120.dp
            while (height <= 700.dp) {
                for (cards in listOf(9, 12)) {
                    val card = Sets.cardWidth(width - 32.dp, height, cards)
                    val grid = Sets.boardHeight(card, cards)
                    assertTrue(
                        "$cards cards in ${width}x$height make a ${grid}-tall grid",
                        grid.value <= height.value + 0.01f,
                    )
                    assertTrue(
                        "$cards cards in ${width}x$height make a row wider than the pane",
                        (card * 3 + Sets.CARD_GAP * 2).value <= (width - 32.dp).value + 0.01f,
                    )
                }
                height += 10.dp
            }
            width += 4.dp
        }
    }

    @Test
    fun `every supported screen still gets cards whose traits read`() {
        for (device in devices) {
            for (cards in listOf(9, 12)) {
                val card = Sets.cardWidth(device.first - 32.dp, gridBox(device), cards)
                assertTrue(
                    "$cards cards on ${device.first}x${device.second} come out $card wide",
                    card.value >= Sets.CARD_MIN_WIDTH.value,
                )
            }
        }
    }

    @Test
    fun `height is what binds a twelve-card board, not width`() {
        // The regression guard proper: if someone drops back to sizing from width alone this is
        // the assertion that notices, because on an ordinary phone the two answers differ.
        val screen = 360.dp to 640.dp
        val widthOnly = (360.dp - 32.dp - Sets.CARD_GAP * 2) / 3
        val actual = Sets.cardWidth(screen.first - 32.dp, gridBox(screen), 12)
        assertTrue(
            "twelve cards still size to $actual, the width-only answer",
            actual.value < widthOnly.value,
        )
    }
}
