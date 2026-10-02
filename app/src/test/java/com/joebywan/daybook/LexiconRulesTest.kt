package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.LexiconRules
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.LexiconMark
import com.joebywan.daybook.puzzles.WordList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconRulesTest {

    private fun marks(guess: String, answer: String) =
        LexiconRules.mark(guess, answer).joinToString("") { "AYG"[it].toString() }

    @Test
    fun `marks follow the repeated-letter rule`() {
        assertEquals("GGGG", marks("coat", "coat"))
        assertEquals("AGGG", marks("boat", "coat"))
        assertEquals("YYAA", marks("tape", "coat"))
        // The answer's other e is already matched, so the first e of the guess is yellow, the second grey.
        assertEquals("YAAAG", marks("eerie", "these"))
        // Two l's in the guess, two in the answer, one in place: the other still finds its twin.
        assertEquals("AYYGA", marks("hello", "swell"))
        // Two l's in the guess, one of them in place, one l left in the answer: the other is yellow.
        assertEquals("AYGAA", marks("allay", "cello"))
        // A green is claimed before a yellow, whatever the order: the e's in slots 3 and 5 are in place.
        assertEquals("AAGGG", marks("geese", "these"))
    }

    @Test
    fun `the marker agrees with an independent one on every pair of a thousand answers`() {
        for (length in listOf(4, 5)) {
            val words = WordList.answers(length)
            var checked = 0
            for (i in words.indices step 3) {
                for (j in words.indices step 7) {
                    val g = words[i]
                    val a = words[j]
                    assertEquals("$g at $a", LexiconSupport.refMark(g, a), LexiconRules.mark(g, a))
                    checked++
                }
            }
            assertTrue(checked > 50_000)
        }
    }

    @Test
    fun `the packed code carries exactly what the marks do`() {
        val words = WordList.answers(5)
        for (i in words.indices step 11) {
            for (j in words.indices step 13) {
                val packed = LexiconRules.mark(words[i], words[j]).foldIndexed(0) { k, acc, m ->
                    var p = 1
                    repeat(k) { p *= 3 }
                    acc + m * p
                }
                assertEquals(packed, LexiconRules.markCode(words[i], words[j]))
            }
        }
    }

    @Test
    fun `hard mode wants every green kept and every yellow reused`() {
        // answer coat; tape drew YYAA, boat drew AGGG.
        val guesses = listOf("tape", "boat")
        val marks = guesses.map { LexiconSupport.refMark(it, "coat") }
        assertNull(LexiconRules.hardProblem("coat", guesses, marks))
        assertNull(LexiconRules.hardProblem("goat", guesses, marks))
        assertEquals("Slot 2 must be O", LexiconRules.hardProblem("cart", guesses, marks))
        assertEquals("Slot 3 must be A", LexiconRules.hardProblem("good", guesses, marks))
        assertEquals("Slot 4 must be T", LexiconRules.hardProblem("goad", guesses, marks))
        // Letters shown yellow must come back even when the greens are all kept.
        val early = listOf("tape")
        val earlyMarks = early.map { LexiconSupport.refMark(it, "coat") }
        assertEquals("Guess must contain A", LexiconRules.hardProblem("cool", early, earlyMarks))
        assertEquals("Guess must contain T", LexiconRules.hardProblem("cabs", early, earlyMarks))
        assertNull(LexiconRules.hardProblem("pant", early, earlyMarks))
    }

    @Test
    fun `hard mode counts repeated letters`() {
        // eerie against these draws yellow, grey, grey, grey, green: two e's are known.
        val guesses = listOf("eerie")
        val marks = guesses.map { LexiconSupport.refMark(it, "these") }
        assertEquals("Guess must contain E 2 times", LexiconRules.hardProblem("shore", guesses, marks))
        assertNull(LexiconRules.hardProblem("these", guesses, marks))
    }

    @Test
    fun `a row is refused for length, for not being a word, and in hard mode for ignoring a clue`() {
        val hard = LexiconState(4, 6, true, "coat", listOf("tape", "boat"))
        assertEquals("Not enough letters", LexiconRules.problem(hard.copy(current = "co")))
        assertEquals("Not in the word list", LexiconRules.problem(hard.copy(current = "cxat")))
        assertEquals("Slot 2 must be O", LexiconRules.problem(hard.copy(current = "cart")))
        assertNull(LexiconRules.problem(hard.copy(current = "goat")))
        // Standard lets any real word through, clues or not.
        assertNull(LexiconRules.problem(hard.copy(hard = false, current = "cart")))
    }

    @Test
    fun `typing, backspace and submit change one thing each, and stop at the edges`() {
        var s = LexiconState(4, 6, false, "coat")
        s = s.withLetter('b').withLetter('o').withLetter('a').withLetter('t').withLetter('x')
        assertEquals("boat", s.current)
        val sent = s.submit()
        assertEquals(listOf("boat"), sent.guesses)
        assertEquals("", sent.current)
        assertEquals("boat", s.withoutLetter().withLetter('t').current)
        assertEquals("", LexiconState(4, 6, false, "coat").withoutLetter().current)
        assertEquals(sent, sent.withoutLetter())
        assertEquals(LexiconState(4, 6, false, "coat"), LexiconState(4, 6, false, "coat").submit())
    }

    @Test
    fun `solving is the last guess being the word, and failing is running out of guesses`() {
        val start = LexiconState(4, 2, false, "coat")
        assertFalse(start.solved)
        assertFalse(start.failed)
        val one = start.copy(guesses = listOf("boat"))
        assertFalse(one.failed)
        val lost = one.copy(guesses = listOf("boat", "goat"))
        assertTrue(lost.failed)
        assertFalse(lost.solved)
        assertFalse(lost.open)
        val won = one.copy(guesses = listOf("boat", "coat"))
        assertTrue(won.solved)
        assertFalse(won.failed)
        // A solved board takes no more letters.
        assertEquals(won, won.withLetter('a'))
    }

    @Test
    fun `each tier is the shape the design says`() {
        for ((tier, shape) in listOf(
            Difficulty.STANDARD to Triple(5, 6, false),
            Difficulty.HARD to Triple(5, 6, true),
            Difficulty.EXPERT to Triple(4, 8, true),
        )) {
            val s = LexiconSupport.board(0, tier)
            assertEquals(shape, Triple(s.length, s.maxGuesses, s.hard))
            assertEquals(s.length, s.answer.length)
            assertTrue(s.answer in WordList.answers(s.length))
            assertTrue(s.guesses.isEmpty() && s.current.isEmpty())
        }
    }

    @Test
    fun `a board is a pure function of its seed, and a year of dailies is varied`() {
        for (tier in Difficulty.entries) {
            val answers = (0 until 365).map { day ->
                val a = LexiconSupport.board(day, tier)
                assertEquals(a, LexiconSupport.board(day, tier))
                a.answer
            }
            // With ~1,400 to ~1,800 words, a year of days repeats a word rarely and never runs the same word twice in a row.
            assertTrue("${tier.name}: only ${answers.toSet().size} different words in a year", answers.toSet().size >= 300)
            assertTrue(answers.zipWithNext().none { (a, b) -> a == b })
        }
    }

    @Test
    fun `a saved board comes back exactly, whatever the lists do later`() {
        val json = Json
        val s = LexiconState(5, 6, true, "crane", listOf("slate", "irate"), "cr", moves = 9)
        val back = json.decodeFromString(LexiconState.serializer(), json.encodeToString(LexiconState.serializer(), s))
        assertEquals(s, back)
        assertNotNull(back.allMarks)
        assertFalse(json.encodeToString(LexiconState.serializer(), s).contains("\"solved\""))
    }
}
