package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Broken
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.Violation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bargain that keeps the board still.
 *
 * The caption under the grid occupies a permanently reserved slot of [Mambo.CAPTION_LINES] lines,
 * so the layout no longer shifts when a rule breaks — but that only works while every message the
 * game can produce actually fits those lines. Overflowing does not grow the slot, it ellipsises,
 * silently dropping the name of a rule the player can see marked in red. Wordings are therefore
 * cut to the budget, and this test is what notices when someone lengthens one back.
 */
class MamboCaptionTest {

    @Test
    fun `every combination of broken rules fits the reserved caption`() {
        for (rules in subsetsOf(Broken.entries)) {
            if (rules.isEmpty()) continue
            val caption = Mambo.captionFor(rules.map { Violation(it, listOf(0)) })
            val lines = wrap(caption, Mambo.CAPTION_LINE_CHARS)
            assertTrue(
                "$rules caption needs ${lines.size} lines: ${lines.joinToString(" / ")}",
                lines.size <= Mambo.CAPTION_LINES,
            )
        }
    }

    @Test
    fun `no single rule name is too long to wrap at all`() {
        // A word longer than a line cannot be broken across one, so it would overflow however many
        // lines the slot held.
        for (rule in Broken.entries) {
            Mambo.captionFor(listOf(Violation(rule, listOf(0)))).split(" ").forEach { word ->
                assertTrue(
                    "\"$word\" is wider than a caption line",
                    word.length <= Mambo.CAPTION_LINE_CHARS,
                )
            }
        }
    }

    @Test
    fun `a legal board captions nothing`() {
        // The slot stays, but it must stay blank: a standing message would read as a live
        // complaint about a board that is breaking no rule.
        assertEquals("", Mambo.captionFor(emptyList()))
    }

    @Test
    fun `a rule broken in several places is named once`() {
        val threeBadRuns = listOf(
            Violation(Broken.TRIPLE, listOf(0, 1, 2)),
            Violation(Broken.TRIPLE, listOf(6, 7, 8)),
            Violation(Broken.TRIPLE, listOf(1, 5, 9)),
        )
        assertEquals(
            Mambo.captionFor(listOf(threeBadRuns.first())),
            Mambo.captionFor(threeBadRuns),
        )
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun subsetsOf(rules: List<Broken>): List<List<Broken>> =
        (0 until (1 shl rules.size)).map { mask ->
            rules.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
        }

    /** Greedy word wrap, the same way a Text lays a line out: break between words, never inside. */
    private fun wrap(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in text.split(" ").filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (candidate.length <= width) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) lines += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }
}
