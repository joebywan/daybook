package com.joebywan.daybook

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glyphs inside a board are sized from their square and never grow with the system font size, or they clip
 * (Sudoku's digits at 2x, cage totals over half a square). `docs/PUZZLE_STANDARDS.md`, `ui/BoardText.kt`.
 */
class BoardTextSizeTest {

    @Test
    fun `a size taken from a square draws the same at font scale 2`() {
        for (scale in listOf(1f, 1.3f, 2f)) {
            val d = Density(2.625f, fontScale = scale)
            with(d) {
                // What `boardSp` and a draw scope's `toSp` do: the same pixels at every font scale.
                assertEquals(26.25f, 10.dp.toSp().toPx(), 0.01f)
                // What they replaced: grows with the font size.
                assertEquals(26.25f * scale, (10.dp.value).sp.toPx(), 0.01f)
            }
        }
    }

    @Test
    fun `no board sizes its text from a square in plain sp`() {
        val path = "app/src/main/java/com/joebywan/daybook/puzzles"
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, path).isDirectory) d = d.parentFile
        val dir = File(d ?: error("$path not found"), path)
        // `(cell.value * 0.5f).sp` and `(px / density).sp`: a square's size turned into sp, which the font size scales.
        val scaled = Regex("""\.value\s*\*[^\n]*\)\.sp\b|/\s*density\)\.sp\b""")
        val offenders = dir.listFiles()!!.filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { scaled.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}" }
        }
        assertTrue("Use boardSp() (or toSp() in a draw scope): $offenders", offenders.isEmpty())
    }
}
