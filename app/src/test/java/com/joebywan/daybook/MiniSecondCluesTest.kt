package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.WheelWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Crossword's second clues (`tools/words/second-clues.txt`, compiled into `MiniSecondClues.kt`). */
class MiniSecondCluesTest {

    /** Every word the current grids use, read run by run with the oracle's own reader. */
    private val gridWords: Set<String> = Difficulty.entries.flatMap { d ->
        val n = MiniLogic.specFor(d).size
        MiniLogic.entries(d).flatMap { MiniOracle.runs(n, it) }
    }.toSet()

    private val source: Map<String, String> = File("../tools/words/second-clues.txt").readLines().filter { it.isNotBlank() }
        .associate { it.substringBefore(": ") to it.substringAfter(": ") }

    @Test
    fun `the compiled clues are the source file's`() {
        for ((w, c) in source) assertEquals("$w: run python3 tools/words/build.py --second-clues", c, MiniLogic.secondClueOf(w))
    }

    @Test
    fun `every grid word has a second clue, which neither gives the word away nor repeats a first clue`() {
        val firsts = gridWords.associateWith { WheelWords.clue(it).lowercase() }
        val firstClues = firsts.values.toSet()
        for (w in gridWords) {
            val clue = MiniLogic.secondClueOf(w)
            assertTrue("$w has no second clue", clue.isNotBlank())
            val words = Regex("[a-z]+").findAll(clue.lowercase()).map { it.value }
            assertTrue("$w: \"$clue\" contains the word", words.none { it == w || it.startsWith(w) })
            assertTrue("$w: \"$clue\" is its first clue", clue.lowercase() != firsts[w])
            // Nor any grid word's first clue: it would name that word instead.
            assertTrue("$w: \"$clue\" is another word's first clue", clue.lowercase() !in firstClues)
        }
    }

    @Test
    fun `no two words share a second clue`() {
        val dupes = source.keys.groupBy { MiniLogic.secondClueOf(it).lowercase() }.filterValues { it.size > 1 }
        assertEquals("shared second clues: $dupes", emptyMap<String, List<String>>(), dupes)
    }
}
