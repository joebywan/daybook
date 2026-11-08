package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.EquationLogic
import com.joebywan.daybook.puzzles.EquationTeacher
import com.joebywan.daybook.puzzles.GuessTeacher
import com.joebywan.daybook.puzzles.GuessTeacher.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Equation's teacher (Lexicon's ladder) held to Lexicon's standard: sound against the reference marker, honest about when
 * it falls back, never able to see the answer (its signature takes only guesses, marks and the row being typed). A pin
 * must hold for every true sum that fits the marks (the oracle's lists), not only the answers.
 */
class EquationTeachingTest {

    private fun marksOf(answer: String, guesses: List<String>) = guesses.map { LexiconSupport.refMark(it, answer) }

    @Test
    fun `a hand-built board teaches a pinned symbol in words a person can follow`() {
        // The walkthrough's board: after 42/7=6, 9+9=18 and 3*5=15, 5 is in the sum but not third or sixth, and slots
        // 2, 4 and 5 hold +, = and 1, so it goes first.
        val guesses = listOf("42/7=6", "9+9=18", "3*5=15")
        val marks = marksOf("5+8=13", guesses)
        assertEquals(listOf("5+8=13"), EquationSupport.refCandidates(6, guesses, marks))
        val step = EquationTeacher.teach(6, guesses, marks, "")!!
        assertEquals(EquationTeacher.PIN, step.technique)
        val move = step.move as Move.Pin
        assertEquals(0, move.slot)
        assertEquals('5', move.letter)
        assertEquals("5+8=13", move.word)
        assertEquals(
            "5 is in the sum, but not in slots 3 and 6, and slots 2, 4 and 5 already hold +, = and 1, so it goes in slot 1.",
            step.explanation,
        )
        // Typed there already, the one sum left is named by its shape, not given.
        val next = EquationTeacher.teach(6, guesses, marks, "5")!!
        assertEquals(EquationTeacher.ONLY_SUM, next.technique)
        assertEquals(Move.Fill("5+8=13"), next.move)
        assertTrue(next.explanation, next.explanation.contains("?+?=13") && !next.explanation.contains("5+8"))
    }

    @Test
    fun `an empty board opens with one of the openers, each among the best first guesses of its list`() {
        for (length in 6..8) {
            val openers = EquationTeacher.openers(length)
            assertEquals(openers.size, openers.toSet().size)
            val answers = EquationLogic.answers(length)
            fun spread(g: String) = answers.groupingBy { LexiconSupport.refMark(g, it) }.eachCount().values.sumOf { it.toLong() * it }
            // The best of every answer with no symbol twice (where the openers were searched); the first is it.
            val best = answers.filter { it.toSet().size == it.length }.minOf(::spread)
            assertEquals("the first opener for $length symbols is the best", best, spread(openers[0]))
            for ((pick, word) in openers.withIndex()) {
                val step = EquationTeacher.teach(length, emptyList(), emptyList(), "", pick)!!
                assertEquals(EquationTeacher.OPENER, step.technique)
                assertEquals(Move.Open(word), step.move)
                assertTrue(word in answers && EquationOracle.isTrue(word))
                assertTrue("$word leaves ${spread(word)} against the best $best", spread(word) * 100 <= best * 113)
            }
        }
        for (tier in Difficulty.entries) {
            val picks = (0 until 200).map { EquationSupport.board(it, tier).opener }.toSet()
            assertEquals(EquationTeacher.openers(EquationLogic.shape(tier).length).indices.toSet(), picks)
        }
    }

    @Test
    fun `a row that is not a true sum is a mistake, and a legal probe is not`() {
        val guesses = listOf("42/7=6", "9+9=18")
        val marks = marksOf("5+8=13", guesses)
        val junk = EquationTeacher.teach(6, guesses, marks, "5+8=14")!!
        assertEquals(EquationTeacher.MISTAKE, junk.technique)
        assertEquals("5+8=14 isn't true: the left makes 13, so Enter won't take it. Every guess must be a true sum.", junk.explanation)
        // Ignoring every green is a probe, not a mistake.
        val probe = EquationTeacher.teach(6, guesses, marks, "3*5=15")!!
        assertNotEquals(EquationTeacher.MISTAKE, probe.technique)
        val fits = EquationTeacher.teach(6, guesses, marks, "8+5=13")!!
        assertEquals(EquationTeacher.SUBMIT, fits.technique)
        assertEquals(Move.Submit, fits.move)
        // Any true sum is a probe: none of a sample of them is ever called wrong.
        for (row in EquationLogic.valid(6)) assertNotEquals(row, EquationTeacher.MISTAKE, EquationTeacher.teach(6, guesses, marks, row)!!.technique)
    }

    @Test
    fun `every step walked by hints is sound against independent checks - coverage`() {
        val days = 120
        val report = StringBuilder()
        for (tier in Difficulty.entries) {
            var solved = 0
            var guessTotal = 0
            var fallbackBoards = 0
            var steps = 0
            var fallbackSteps = 0
            val techniques = sortedMapOf<String, Int>()
            for (day in 0 until days) {
                val s = EquationSupport.board(day, tier)
                val walk = EquationSupport.walkByHints(s)
                if (walk.solved) solved++
                guessTotal += walk.guesses.size
                if (walk.steps.any { it.technique in EquationTeacher.FALLBACKS }) fallbackBoards++
                steps += walk.steps.size
                fallbackSteps += walk.steps.count { it.technique in EquationTeacher.FALLBACKS }
                walk.steps.forEach { techniques.merge(it.technique, 1, Int::plus) }
                for ((i, step) in walk.steps.withIndex()) {
                    val before = walk.guesses.take(i)
                    val marks = marksOf(s.answer, before)
                    val fits = EquationSupport.refCandidates(s.length, before, marks)
                    val where = "${tier.name} day $day (${s.answer}) after $before"
                    assertTrue("$where: the answer must still be possible", s.answer in fits)
                    assertEquals("$where: candidates", fits, EquationTeacher.candidates(s.length, before, marks))
                    assertTrue("$where: too long: ${step.explanation}", step.explanation.length <= GuessTeacher.MAX_EXPLANATION)
                    when (val m = step.move) {
                        is Move.Pin -> {
                            // True of every true sum that fits the marks, lone zeros included.
                            val all = EquationOracle.lists.getValue(s.length)
                                .filter { w -> before.indices.all { LexiconSupport.refMark(before[it], w) == marks[it] } }
                            assertTrue("$where: pin ${m.letter}@${m.slot} fails for ${all.firstOrNull { it[m.slot] != m.letter }}", all.all { it[m.slot] == m.letter })
                            assertEquals(m.letter, m.word[m.slot])
                            assertTrue("$where: pin row must fit", m.word in fits)
                        }
                        is Move.Fill -> {
                            assertTrue("$where: ${m.word} must fit every mark", m.word in fits)
                            // The shape is given, never the left side's digits.
                            assertFalse("$where: ${step.explanation}", step.explanation.contains(m.word.substringBefore('=')))
                            if (step.technique == EquationTeacher.ONLY_SUM) assertEquals(listOf(m.word), fits)
                            else {
                                val counts = fits.groupingBy { LexiconSupport.refMark(m.word, it) }.eachCount()
                                val e = step.explanation
                                if (fits.size > 2) assertTrue(
                                    "$where: $e",
                                    e.contains("${fits.size} sums") && when {
                                        e.contains("marked") -> e.endsWith("marked ${counts.size} ways, and none leaves more than ${counts.values.max()}.")
                                        counts.values.max() == 1 -> e.contains("you will know the sum") || e.contains("at most 1 will")
                                        else -> e.endsWith("at most ${counts.values.max()} will be left.")
                                    },
                                )
                            }
                        }
                        is Move.Open -> {
                            assertEquals(0, i)
                            assertEquals(EquationTeacher.opener(s.length, s.opener), m.word)
                        }
                        else -> error("$where: unexpected $m")
                    }
                }
            }
            report.appendLine(
                "${tier.name}: solved $solved/$days, mean guesses ${"%.2f".format(guessTotal.toDouble() / days)}, " +
                    "fallback on $fallbackSteps of $steps steps (${100 * fallbackSteps / steps}%), $fallbackBoards boards, steps $techniques",
            )
            assertTrue("${tier.name}: a player doing what the hints say won only $solved of $days", solved >= days * 95 / 100)
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/equation-teaching-coverage.txt").writeText(report.toString())
    }

    @Test
    fun `a legal board never leaves the teacher with nothing to say`() {
        for (tier in Difficulty.entries) {
            val length = EquationLogic.shape(tier).length
            for (answer in EquationLogic.answers(length).let { a -> a.filterIndexed { i, _ -> i % maxOf(1, a.size / 60) == 0 } }) {
                var guesses = emptyList<String>()
                for (turn in 0 until 6) {
                    val marks = marksOf(answer, guesses)
                    for (current in listOf("", answer.take(1), answer.take(3), answer)) {
                        assertTrue("no hint for $answer after $guesses typing '$current'", EquationTeacher.teach(length, guesses, marks, current) != null)
                    }
                    val step = EquationTeacher.teach(length, guesses, marks, "")!!
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

    @Test
    fun `hints are quick`() {
        // The slowest hint is the first after the opener on eight symbols: the most sums left to weigh.
        var worst = 0L
        for (day in 0 until 30) {
            val s = EquationSupport.board(day, Difficulty.EXPERT)
            val opener = EquationTeacher.opener(8, s.opener)
            val t = System.nanoTime()
            EquationTeacher.teach(8, listOf(opener), marksOf(s.answer, listOf(opener)), "")
            worst = maxOf(worst, (System.nanoTime() - t) / 1_000_000)
        }
        println("worst Expert hint after the opener: $worst ms")
        assertTrue("$worst ms", worst < 1000)
    }
}
