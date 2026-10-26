package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.backFrom
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {
    private val day = LocalDate(2026, 10, 3)

    @Test fun `routes round-trip, and strings saved before the archive fields still decode`() {
        listOf(
            Route.Home, Route.Stats, Route.Settings, Route.Achievements(true), Route.Techniques, Route.Archive("kings"), Route.Archive("kings", day),
            Route.Play("kings", Difficulty.HARD, day, fromArchive = true), Route.Play("kings", Difficulty.HARD, null, 7),
        ).forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
        assertEquals(Route.Archive("kings"), decodeRoute("archive|kings"))
        assertEquals(Route.Play("kings", Difficulty.HARD, day), decodeRoute("play|kings|HARD|2026-10-03|0"))
        // The Arrange screen is gone (the grid is dragged instead); a route saved on it goes home.
        assertEquals(Route.Home, decodeRoute("arrange|true"))
        assertEquals(Route.Home, decodeRoute("arrange|false"))
    }

    @Test fun `back from an archive game is that archive, at that day`() {
        assertEquals(Route.Archive("kings", day), backFrom(Route.Play("kings", Difficulty.HARD, day, fromArchive = true)))
        assertEquals(Route.Home, backFrom(Route.Play("kings", Difficulty.HARD, day)))
        assertEquals(Route.Stats, backFrom(Route.Achievements()))
        assertEquals(Route.Stats, backFrom(Route.Techniques))
        assertEquals(Route.Home, backFrom(Route.Archive("kings", day)))
    }

    @Test fun `the archive by day round-trips, and older archive and play strings still decode`() {
        listOf(
            Route.Days(), Route.Days(day), Route.Days(day, fromStats = true),
            Route.Archive("kings", day, fromDays = true), Route.Archive("kings", day, fromDays = true, fromStats = true),
            Route.Play("kings", Difficulty.HARD, day, fromDays = true, fromStats = true),
            Route.Play("kings", Difficulty.HARD, day, fromArchive = true, fromDays = true),
        ).forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
        assertEquals(Route.Days(), decodeRoute("days"))
        assertEquals(Route.Archive("kings", day), decodeRoute("archive|kings|2026-10-03"))
        assertEquals(Route.Play("kings", Difficulty.HARD, day, fromArchive = true), decodeRoute("play|kings|HARD|2026-10-03|0|true|"))
    }

    @Test fun `back from the archive by day goes where each screen was opened from`() {
        // Home, archive by day, a board: back to that day; back again, Home.
        assertEquals(Route.Days(day), backFrom(Route.Play("kings", Difficulty.HARD, day, fromDays = true)))
        assertEquals(Route.Home, backFrom(Route.Days(day)))
        // From the streak calendar, the same chain ends at Statistics.
        assertEquals(Route.Days(day, fromStats = true), backFrom(Route.Play("kings", Difficulty.HARD, day, fromDays = true, fromStats = true)))
        assertEquals(Route.Stats, backFrom(Route.Days(day, fromStats = true)))
        // A puzzle's own calendar opened from a day, and a board played from it: back through each.
        val board = Route.Play("kings", Difficulty.HARD, day, fromArchive = true, fromDays = true, fromStats = true)
        assertEquals(Route.Archive("kings", day, fromDays = true, fromStats = true), backFrom(board))
        assertEquals(Route.Days(day, fromStats = true), backFrom(backFrom(board)))
        assertEquals(Route.Stats, backFrom(backFrom(backFrom(board))))
    }
}
