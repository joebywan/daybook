package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconCluesTest {
    private val answers = listOf(4, 5).flatMap { WordList.answers(it) }

    @Test
    fun `every answer has a clue and none gives the word away`() {
        for (w in answers) {
            val clue = WordList.clue(w)
            assertTrue("$w: empty clue", clue.isNotBlank())
            val words = Regex("[a-z]+").findAll(clue.lowercase()).map { it.value }
            assertTrue("$w: its clue \"$clue\" contains the word", words.none { it == w || it.startsWith(w) })
        }
    }

    @Test
    fun `no two answers share a clue`() {
        val dupes = answers.groupBy { WordList.clue(it).lowercase() }.filterValues { it.size > 1 }
        assertEquals("shared clues: $dupes", emptyMap<String, List<String>>(), dupes)
    }
}
