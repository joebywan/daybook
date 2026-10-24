package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Difficulty.EXPERT
import com.joebywan.daybook.core.Difficulty.HARD
import com.joebywan.daybook.core.Difficulty.STANDARD
import com.joebywan.daybook.ui.home.LaunchMode
import com.joebywan.daybook.ui.home.swipedTier
import org.junit.Assert.assertEquals
import org.junit.Test

class TierSwipeTest {
    /** A 400px box, a 28px edge band and a 56px swipe. */
    private fun swipe(dx: Float, dy: Float = 0f, x: Float = 200f, mode: LaunchMode = LaunchMode.DAILY, tier: Difficulty = HARD) =
        swipedTier(x, dx, dy, 400f, 28f, 56f, mode, tier)

    @Test
    fun `left is harder, right is easier, nothing past the ends`() {
        assertEquals(EXPERT, swipe(-80f))
        assertEquals(STANDARD, swipe(80f))
        assertEquals(null, swipe(-80f, tier = EXPERT))
        assertEquals(null, swipe(80f, tier = STANDARD))
        assertEquals(HARD, swipe(-80f, tier = STANDARD))
        assertEquals(HARD, swipe(80f, tier = EXPERT, mode = LaunchMode.RANDOM))
    }

    @Test
    fun `too short or too diagonal is not a swipe`() {
        assertEquals(null, swipe(-55f))
        assertEquals(EXPERT, swipe(-56f))
        assertEquals(EXPERT, swipe(-80f, dy = 40f))
        assertEquals(null, swipe(-80f, dy = 41f))
        assertEquals(null, swipe(0f, dy = 300f))
    }

    @Test
    fun `not from the edges, and never in Levels`() {
        assertEquals(null, swipe(-80f, x = 27f))
        assertEquals(EXPERT, swipe(-80f, x = 28f))
        assertEquals(null, swipe(80f, x = 373f))
        assertEquals(STANDARD, swipe(80f, x = 372f))
        assertEquals(null, swipe(-80f, mode = LaunchMode.LEVELS))
        assertEquals(null, swipe(80f, mode = LaunchMode.LEVELS))
    }
}
