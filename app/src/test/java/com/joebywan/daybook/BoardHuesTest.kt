package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joebywan.daybook.ui.theme.BoardHues
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pins the helper to the hex table in docs/COLOUR.md, read straight from the file.
class BoardHuesTest {
    private fun doc(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "docs/COLOUR.md").exists()) d = d.parentFile
        return File(d ?: error("docs/COLOUR.md not found"), "docs/COLOUR.md")
    }

    private fun near(want: String, got: Color, what: String) {
        val w = want.toInt(16)
        val g = got.toArgb()
        for (shift in listOf(16, 8, 0)) {
            val a = (w shr shift) and 255
            val b = (g shr shift) and 255
            assertTrue("$what: want $want, got ${g.toUInt().toString(16)}", abs(a - b) <= 1)
        }
    }

    @Test fun helperMatchesTheDocumentedTable() {
        val rows = doc().readLines().filter { Regex("""^\| \w+ \| \d+ \|""").containsMatchIn(it) }
        assertEquals(8, rows.size)
        for (row in rows) {
            val c = row.split("|").map { it.trim().trim('`') }.drop(1)
            val h = c[1].toFloat()
            assertTrue("hue $h in helper", h in BoardHues.all)
            near(c[2], BoardHues.fill(h, false), "${c[0]} fill light")
            near(c[3], BoardHues.fill(h, true), "${c[0]} fill dark")
            near(c[4], BoardHues.ink(h, false), "${c[0]} ink light")
            near(c[5], BoardHues.ink(h, true), "${c[0]} ink dark")
            near(c[6], BoardHues.mark(h), "${c[0]} mark")
        }
    }

    @Test fun contentHuesAreDistinctAndSpread() {
        for (n in 2..5) {
            val hs = BoardHues.contentHues(n)
            assertEquals(n, hs.size)
            assertEquals(n, hs.toSet().size)
            if (n <= 4) for (i in 0 until n - 1) {
                val d = abs(hs[i] - hs[i + 1])
                assertTrue("adjacent picks too close: $hs", min(d, 360f - d) >= 90f)
            }
        }
    }

    @Test fun pairsAvoidRightWrongAndAreDistinct() {
        val reserved = setOf(8f, 145f) // Coral, Green
        for ((a, b) in BoardHues.HUE_PAIRS) {
            assertTrue(a in BoardHues.all && b in BoardHues.all && a != b)
            assertTrue("pair $a/$b uses a reserved hue", a !in reserved && b !in reserved)
        }
        assertEquals(BoardHues.HUE_PAIRS.size, BoardHues.HUE_PAIRS.toSet().size)
    }

    @Test fun pairPickerIsDeterministicAndCoversEveryPair() {
        val seeds = -50..50
        assertEquals(seeds.map { BoardHues.pair(it) }, seeds.map { BoardHues.pair(it) })
        assertEquals(BoardHues.HUE_PAIRS.toSet(), seeds.map { BoardHues.pair(it) }.toSet())
        BoardHues.pair(Int.MIN_VALUE) // negative seeds must not index out of range
    }

    @Test fun onFillPicksReadableText() {
        assertEquals(Color(0xFF1B2F29), BoardHues.onFill(Color.White)) // light fill: the theme ink
        assertEquals(Color.White, BoardHues.onFill(Color.Black))
        // Teal's deep step fails ink (3.95) and white (3.58): only black reaches 4.5.
        val teal = BoardHues.ink(175f, false)
        assertEquals(Color.Black, BoardHues.onFill(teal))
        assertTrue(BoardHues.contrast(BoardHues.onFill(teal), teal) >= 4.5f)
    }
}
