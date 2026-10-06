package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.LexiconRules
import com.joebywan.daybook.puzzles.LexiconTeacher
import com.joebywan.daybook.puzzles.LexiconTeacher.Move
import com.joebywan.daybook.puzzles.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The teacher is held to the same standard as every other one: sound against an independent
 * solver, honest about when it falls back, and never able to see the answer (its signature takes
 * only guesses, marks and the row being typed). Soundness is checked beyond the answer list: a
 * pinned letter must hold for every accepted word that fits the marks, not only the common ones.
 */
class LexiconTeachingTest {

    private fun marksOf(answer: String, guesses: List<String>) = guesses.map { LexiconSupport.refMark(it, answer) }

    @Test
    fun `a hand-built board teaches a pinned letter in words a person can follow`() {
        // Answer mole. lime draws YELLOW grey YELLOW GREEN, loom draws YELLOW GREEN grey YELLOW.
        val guesses = listOf("lime", "loom")
        val marks = marksOf("mole", guesses)
        assertEquals(listOf("mole"), LexiconSupport.refCandidates(4, guesses, marks))

        val step = LexiconTeacher.teach(4, guesses, marks, "")!!
        assertEquals(LexiconTeacher.PIN, step.technique)
        val move = step.move as Move.Pin
        assertEquals(2, move.slot)
        assertEquals('l', move.letter)
        assertEquals("mole", move.word)
        assertEquals(
            "L is in the word, but not in slot 1, and slots 2 and 4 already hold O and E, so it goes in slot 3.",
            step.explanation,
        )
        // The tile it points the player at is the one in the row being typed.
        assertTrue(LexiconTeacher.tile(4, 2, 2) in step.targets)
        assertTrue(LexiconTeacher.key('l') in step.targets)

        // Typed there already, the pin is done and the next thing is the one word left.
        val next = LexiconTeacher.teach(4, guesses, marks, "mol")!!
        assertEquals(LexiconTeacher.ONLY_WORD, next.technique)
        assertEquals(Move.Fill("mole"), next.move)
        assertEquals(
            "Only one word on the answer list fits every mark. Its clue: \u201C${WordList.clue("mole")}\u201D (4). Type it.",
            next.explanation,
        )
    }

    @Test
    fun `an empty board opens with the opener, which is the best first word of its list`() {
        for (length in listOf(4, 5)) {
            val step = LexiconTeacher.teach(length, emptyList(), emptyList(), "")!!
            assertEquals(LexiconTeacher.OPENER, step.technique)
            val word = LexiconTeacher.opener(length)
            assertEquals(Move.Open(word), step.move)
            assertEquals(length, word.length)
            assertTrue(WordList.isWord(word) && word in WordList.answers(length))

            // The best first word leaves the fewest in expectation: sum of squared bucket sizes, by the reference marker.
            val answers = WordList.answers(length)
            fun spread(g: String) = answers.groupingBy { LexiconSupport.refMark(g, it) }.eachCount().values.sumOf { it.toLong() * it }
            val mine = spread(word)
            val best = answers.minOf(::spread)
            assertEquals("$word is not the best opener for $length letters", best, mine)
        }
    }

    @Test
    fun `a row that is not a word is a mistake, and a legal probe is not`() {
        val guesses = listOf("tape", "boat")
        val marks = marksOf("coat", guesses)

        // Not a word, so Enter would refuse it.
        val junk = LexiconTeacher.teach(4, guesses, marks, "xoat")!!
        assertEquals(LexiconTeacher.MISTAKE, junk.technique)
        assertTrue(junk.explanation.contains("isn't in the word list"))

        // Ignoring a green is a probe, not a mistake.
        val probe = LexiconTeacher.teach(4, guesses, marks, "cart")!!
        assertNotEquals(LexiconTeacher.MISTAKE, probe.technique)

        // A row that fits every mark is told to go in.
        val fits = LexiconTeacher.teach(4, guesses, marks, "goat")!!
        assertEquals(LexiconTeacher.SUBMIT, fits.technique)
        assertEquals(Move.Submit, fits.move)
    }

    @Test
    fun `the same board gets the same hint every time`() {
        val s = LexiconSupport.board(17, Difficulty.HARD)
        val a = LexiconSupport.walkByHints(s)
        val b = LexiconSupport.walkByHints(s)
        assertEquals(a.guesses, b.guesses)
        assertEquals(a.steps.map { it.explanation }, b.steps.map { it.explanation })
    }

    @Test
    fun `every step walked by hints is sound against independent checks`() {
        val days = 120
        val report = StringBuilder()
        val rates = LinkedHashMap<Difficulty, Int>()
        for (tier in Difficulty.entries) {
            var solved = 0
            var guessTotal = 0
            var fallbackBoards = 0
            val techniques = HashMap<String, Int>()
            for (day in 0 until days) {
                val s = LexiconSupport.board(day, tier)
                val walk = LexiconSupport.walkByHints(s)
                if (walk.solved) solved++
                guessTotal += walk.guesses.size
                if (walk.steps.any { it.technique in LexiconTeacher.FALLBACKS }) fallbackBoards++
                walk.steps.forEach { techniques.merge(it.technique, 1, Int::plus) }

                for ((i, step) in walk.steps.withIndex()) {
                    val before = walk.guesses.take(i)
                    val marks = marksOf(s.answer, before)
                    val fits = LexiconSupport.refCandidates(s.length, before, marks)
                    val where = "${tier.name} day $day (${s.answer}) after $before"
                    assertTrue("$where: the answer must still be possible", s.answer in fits)
                    assertEquals("$where: candidates", fits, LexiconTeacher.candidates(s.length, before, marks))
                    assertTrue("$where: explanation too long: ${step.explanation}", step.explanation.length <= LexiconTeacher.MAX_EXPLANATION)
                    // A step that points at a word gives its clue (LexiconCluesTest: and the clue never holds the word).
                    ((step.move as? Move.Fill)?.word ?: (step.move as? Move.Open)?.word)?.let {
                        assertTrue("$where: no clue for $it", step.explanation.contains(WordList.clue(it)))
                    }
                    when (val m = step.move) {
                        is Move.Pin -> {
                            // True of every accepted word that fits the marks, on the answer list or not.
                            val all = LexiconSupport.guessList(s.length)
                                .filter { w -> before.indices.all { LexiconSupport.refMark(before[it], w) == marks[it] } }
                            assertTrue("$where: nothing fits", all.isNotEmpty())
                            assertTrue("$where: pin ${m.letter}@${m.slot} fails for ${all.firstOrNull { it[m.slot] != m.letter }}", all.all { it[m.slot] == m.letter })
                            assertEquals(m.letter, m.word[m.slot])
                            assertTrue("$where: pin word must fit", m.word in fits)
                        }
                        is Move.Fill -> {
                            assertTrue("$where: ${m.word} must fit every mark", m.word in fits)
                            if (step.technique == LexiconTeacher.ONLY_WORD) assertEquals(listOf(m.word), fits)
                            else {
                                assertTrue(fits.size >= 2)
                                val worst = fits.groupingBy { LexiconSupport.refMark(m.word, it) }.eachCount().values.max()
                                if (fits.size > 2) assertTrue("$where: ${step.explanation}", step.explanation.contains("${fits.size} words") && step.explanation.endsWith("at most $worst."))
                            }
                        }
                        is Move.Open -> {
                            assertEquals(0, i)
                            assertEquals(LexiconTeacher.opener(s.length), m.word)
                        }
                        else -> error("$where: unexpected $m")
                    }
                }
            }
            report.appendLine(
                "${tier.name}: solved $solved/$days, mean guesses ${"%.2f".format(guessTotal.toDouble() / days)}, " +
                    "fell back on $fallbackBoards boards, steps $techniques",
            )
            rates[tier] = solved
        }
        println(report)
        for ((tier, solved) in rates) {
            assertTrue("${tier.name}: a player doing what the hints say won only $solved of $days", solved >= days * 95 / 100)
        }
    }

    @Test
    fun `a legal board never leaves the teacher with nothing to say`() {
        // Every answer, one typed prefix at a time, from an empty board to a solved one.
        for (tier in listOf(Difficulty.STANDARD, Difficulty.EXPERT)) {
            for (answer in WordList.answers(LexiconRules.shape(tier).length).filterIndexed { i, _ -> i % 40 == 0 }) {
                val shape = LexiconRules.shape(tier)
                var guesses = emptyList<String>()
                for (turn in 0 until 6) {
                    val marks = marksOf(answer, guesses)
                    for (current in listOf("", answer.take(1), answer.take(2), answer)) {
                        assertTrue(
                            "no hint for $answer after $guesses typing '$current'",
                            LexiconTeacher.teach(shape.length, guesses, marks, current) != null,
                        )
                    }
                    val step = LexiconTeacher.teach(shape.length, guesses, marks, "")!!
                    val word = when (val m = step.move) {
                        is Move.Open -> m.word
                        is Move.Fill -> m.word
                        is Move.Pin -> m.word
                        else -> error("$m")
                    }
                    guesses = guesses + word
                    if (word == answer) break
                }
                assertFalse(guesses.isEmpty())
            }
        }
    }
}
