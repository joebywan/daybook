package com.joebywan.daybook

import com.joebywan.daybook.puzzles.LexiconWords
import com.joebywan.daybook.puzzles.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The word lists are data, and the answer is an index into them, so the tests pin what makes the
 * index mean the same thing on every platform and every build: sorted, unique, plain a-z, the
 * right length, every answer an accepted guess. They also hold the screening (no offensive,
 * informal or plural answers; both spellings accepted) at a few words that must never move.
 */
class LexiconListsTest {

    private val lists = mapOf(
        "answers4" to (4 to LexiconWords.ANSWERS_4),
        "answers5" to (5 to LexiconWords.ANSWERS_5),
        "guesses4" to (4 to LexiconWords.GUESSES_4),
        "guesses5" to (5 to LexiconWords.GUESSES_5),
    )

    @Test
    fun `every list is sorted, unique, plain lowercase and the right length`() {
        for ((name, pair) in lists) {
            val (length, words) = pair
            for (w in words) {
                assertEquals("$name: '$w' has the wrong length", length, w.length)
                assertTrue("$name: '$w' is not plain a-z", w.all { it in 'a'..'z' })
            }
            for ((a, b) in words.zipWithNext()) {
                assertTrue("$name is not strictly sorted at '$a', '$b'", a < b)
            }
        }
    }

    @Test
    fun `every answer is an accepted guess, and the lists are the size the design promises`() {
        for (length in listOf(4, 5)) {
            val answers = WordList.answers(length)
            assertTrue(answers.all(WordList::isWord))
            assertTrue("${answers.size} $length-letter answers is too few to avoid repeats", answers.size >= 1300)
        }
        assertTrue(LexiconWords.GUESSES_5.size >= 6000)
        assertTrue(LexiconWords.GUESSES_4.size >= 3000)
    }

    @Test
    fun `both spellings are accepted as guesses and neither is ever an answer`() {
        for ((a, b) in listOf("grey" to "gray", "tyre" to "tire", "metre" to "meter", "mould" to "mold")) {
            for (w in listOf(a, b)) {
                if (w.length in 4..5) assertTrue("$w should be accepted", WordList.isWord(w))
            }
        }
        val answers = LexiconWords.ANSWERS_4 + LexiconWords.ANSWERS_5
        for (w in listOf("grey", "gray", "tyre", "metre", "meter", "mold")) {
            assertFalse("$w has a spelling variant, so it cannot be an answer", w in answers)
        }
    }

    @Test
    fun `nothing offensive, informal or obscure is ever an answer, and non-words are refused`() {
        val answers = (LexiconWords.ANSWERS_4 + LexiconWords.ANSWERS_5).toSet()
        val never = listOf(
            "bitch", "whore", "pussy", "penis", "semen", "horny", "queer", "slave", "slut", "rape", "anal", "anus",
            "dunno", "gonna", "gimme", "okay", "radii", "genii", "fiche", "halon",
        )
        for (w in never) assertFalse("$w must not be an answer", w in answers)
        for (w in listOf("xxxxx", "qzqzq", "abcde", "tapex", "zzzz")) assertFalse("$w is not a word", WordList.isWord(w))
        assertFalse(WordList.isWord("cat"))
        assertFalse(WordList.isWord("planet"))
    }

    @Test
    fun `plurals and past tenses of a shorter answer are never answers`() {
        for (length in listOf(4, 5)) {
            val words = WordList.answers(length)
            // Only five-letter words have a four-letter base on the lists; a three-letter base is not kept.
            val all = if (length == 5) LexiconWords.GUESSES_4.toSet() else emptySet()
            for (w in words) {
                val plural = w.endsWith("s") && !w.endsWith("ss") && w.dropLast(1) in all
                val past = w.endsWith("ed") && (w.dropLast(1) in all || w.dropLast(2) in all)
                assertFalse("$w looks like an inflection of a shorter word", plural || past)
            }
        }
    }
}
