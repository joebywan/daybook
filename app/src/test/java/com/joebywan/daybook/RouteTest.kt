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
            Route.Home, Route.Stats, Route.Settings, Route.Achievements(true), Route.Archive("kings"), Route.Archive("kings", day),
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
        assertEquals(Route.Home, backFrom(Route.Archive("kings", day)))
    }
}
