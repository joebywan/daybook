package com.joebywan.daybook.ui.theme

import androidx.compose.ui.graphics.Color

// The board content hues of docs/COLOUR.md: one HSL recipe, so any two hues sit at the same weight.
// Web-safe (no android.*, java.*). Derive colours from these; never store them.
object BoardHues {
    /** Hue in degrees, in table order. */
    val all = listOf(8f, 34f, 46f, 145f, 175f, 215f, 268f, 330f)

    /** Cell or region background; carries `onSurface` text. */
    fun fill(hue: Float, dark: Boolean) = if (dark) Color.hsl(hue, .40f, .30f) else Color.hsl(hue, .62f, .76f)

    /** Text or a thin stroke on the surface. */
    fun ink(hue: Float, dark: Boolean) = Color.hsl(hue, .55f, if (dark) .64f else .38f)

    /** A solid shape (token, path, dot), either scheme. */
    fun mark(hue: Float) = Color.hsl(hue, .55f, .56f)

    /** [n] hues spread across the table, every other row first: Coral, Green, Violet, Amber, Teal, Rose, Blue, Gold. */
    fun contentHues(n: Int): List<Float> {
        require(n in 2..5) { "a board takes 2 to 5 content hues, not $n" }
        return SPREAD.take(n)
    }

    private val SPREAD = listOf(0, 3, 6, 1, 4, 7, 5, 2).map { all[it] }
}
