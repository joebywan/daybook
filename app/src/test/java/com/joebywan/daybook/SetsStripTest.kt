package com.joebywan.daybook

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bargain that keeps every claimed set visible at once.
 *
 * The found-set strip does not scroll and does not wrap, so the whole tier's worth of slots has to
 * survive being divided across one line — and a thumbnail that gets too narrow stops carrying the
 * four traits, which is the only reason it is on screen. Both halves of that are arithmetic on
 * [Sets.slotWidth], so both can be checked here rather than on a device.
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
}
