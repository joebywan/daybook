package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Mini
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.MiniLogic.BLACK
import com.joebywan.daybook.puzzles.MiniLogic.OPEN
import com.joebywan.daybook.puzzles.MiniState
import com.joebywan.daybook.puzzles.MiniTeacher
import com.joebywan.daybook.puzzles.WheelWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniTeachingTest {


    private fun board(day: Int, d: Difficulty) = Mini.generate(daySeed(day, Mini.id, d), d) as MiniState

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = sortedMapOf<String, Int>()
            val times = mutableListOf<Long>()
            var steps = 0
            for (day in 0 until 200) {
                var s = board(day, d)
                while (!s.solved) {
                    val t0 = System.nanoTime()
                    val step = Mini.teach(s)!!
                    times += System.nanoTime() - t0
                    assertFalse(step.technique, step.mistake)
                    assertEquals(step.technique == MiniTeacher.CLUE, step.fallback)
                    counts.merge(step.technique, 1, Int::plus)
                    steps++
                    val next = step.apply(s) as MiniState
                    assertTrue(step.isReached(next))
                    s = next
                }
                assertEquals(s.answer, s.letters)
            }
            times.sort()
            val clue = counts[MiniTeacher.CLUE] ?: 0
            report.appendLine(
                "${d.name}: clue (fallback) ${"%.1f".format(100.0 * clue / steps)}% of steps; steps per board " +
                    counts.map { "${it.key} %.2f".format(it.value / 200.0) } +
                    "; hint median ${times[times.size / 2] / 1000} us, p95 ${times[times.size * 95 / 100] / 1000} us, max ${times.last() / 1000} us",
            )
            // Every board needs the clues to start, and the crossings to finish: neither may take the whole board.
            assertTrue("${d.name}: the crossings settle words", counts.keys.any { it != MiniTeacher.CLUE })
            assertTrue("${d.name}: the clue share is the measured one, under two thirds of steps", clue * 3 < steps * 2)
        }
        writeReport("mini-teaching-coverage.txt", report.toString())
        println(report)
    }

    @Test
    fun `the fallback gives the second clue first, and the answer's letter only on the next tap`() {
        var seen = 0
        for (d in Difficulty.entries) for (day in 0 until 30) {
            val s = board(day, d)
            val step = MiniTeacher.teach(s)!!
            assertEquals(MiniTeacher.CLUE, step.technique)
            val slot = MiniLogic.slots(s.size, s.answer)[step.slot]
            val word = MiniLogic.spelled(s.answer, slot)
            // Tap 1: the slot and its word's second clue; nothing read off the answer but which clue to quote.
            assertEquals("Another way in: ${slot.name}, \"${MiniLogic.secondClueOf(word)}\".", step.nudge)
            // Tap 2: the letter, saying openly that it read the answer.
            assertTrue(step.explanation, "reads the answer" in step.explanation && step.letter.uppercase() in step.explanation)
            // The slot is chosen from the board alone: a different stored answer names the same slot.
            assertEquals(step.slot, MiniTeacher.teach(s.copy(answer = s.answer.map { if (it == BLACK) it else 'q' }.joinToString("")))!!.slot)
            // A save from before second clues: the old nudge, the same letter.
            val old = MiniTeacher.teach(s.copy(secondClues = emptyList()))!!
            assertEquals("Try ${slot.name}.", old.nudge)
            assertEquals(step.explanation, old.explanation)
            seen++
        }
        assertEquals(90, seen)
    }

    @Test
    fun `measure - how often a solved second clue lets the walk move on`() {
        // The walk reads letters, never clues, so on its own terms a second clue changes nothing on the board. The proxy:
        // at each fallback step, suppose the player solves the second clue and types the whole word, and ask whether the
        // walk's next step is then a logic step (crossings settle another word) or the board is done, rather than another
        // fallback. Beside it: how often the one letter the next tap gives would, through the crossings, have settled the
        // same word anyway (then the clue saved a tap, not a letter). The walk itself goes on with the letter, as hints do.
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            var clueSteps = 0
            var wordMovesOn = 0
            var letterSettlesWord = 0
            val fit = mutableListOf<Int>()
            for (day in 0 until 200) {
                val s = board(day, d)
                val shape = MiniTeacher.Shape(s.size, s.answer)
                val now = s.letters.toCharArray()
                // Logic steps until the walk stalls: the board the next fallback would see.
                fun stall(from: CharArray): CharArray {
                    val g = from.copyOf()
                    while (OPEN in g) {
                        val hit = MiniTeacher.findStep(shape, g.concatToString()) ?: break
                        for (sq in shape.slots[hit.slot].squares) g[sq] = s.answer[sq]
                    }
                    return g
                }
                while (true) {
                    val stalled = stall(now)
                    stalled.copyInto(now)
                    if (OPEN !in now) break
                    val letters = now.concatToString()
                    val r = MiniTeacher.clueSlot(shape, letters)!!
                    val slot = shape.slots[r]
                    clueSteps++
                    fit += MiniTeacher.Sight(shape, letters).words[r].size
                    val withWord = now.copyOf().also { g -> for (sq in slot.squares) g[sq] = s.answer[sq] }
                    if (OPEN !in withWord || MiniTeacher.findStep(shape, withWord.concatToString()) != null) wordMovesOn++
                    val sq = slot.squares.first { now[it] == OPEN }
                    now[sq] = s.answer[sq]
                    if (slot.squares.all { stall(now)[it] != OPEN }) letterSettlesWord++
                }
            }
            fit.sort()
            report.appendLine(
                "${d.name}: $clueSteps fallback steps over 200 boards; a solved second clue lets the crossings move on " +
                    "${"%.1f".format(100.0 * wordMovesOn / clueSteps)}% of the time; the letter alone settles that word " +
                    "${"%.1f".format(100.0 * letterSettlesWord / clueSteps)}%; words fitting the slot named: median " +
                    "${fit[fit.size / 2]}, p90 ${fit[fit.size * 9 / 10]}",
            )
            assertTrue(d.name, wordMovesOn > 0)
        }
        File("build/reports").mkdirs()
        File("build/reports/mini-second-clue.txt").writeText(report.toString())
        println(report)
    }

    /** Every grid word a slot reading [pattern] (`' '` open) could be, by brute force over the list. */
    private fun fits(pattern: String) = WheelWords.grid(pattern.length).filter { w -> w.indices.all { pattern[it] == OPEN || pattern[it] == w[it] } }

    @Test
    fun `every word step is the only word every fill the letters allow puts there`() {
        // Soundness on a one-answer board cannot tell reasoning from peeking, so check each step against the oracle: no
        // fill of the grid that keeps the letters on the board puts another word in the step's slot.
        val rng = Rng(3)
        var checked = 0
        for (d in listOf(Difficulty.STANDARD, Difficulty.HARD)) for (day in 0 until 30) {
            val s = board(day, d)
            val whites = s.answer.indices.filter { s.answer[it] != BLACK }
            // Over half the letters, so the oracle's count stays quick.
            val keep = rng.shuffled(whites).take(whites.size * 3 / 5).toSet()
            val letters = s.answer.indices.map { if (it in keep) s.answer[it] else if (s.answer[it] == BLACK) BLACK else OPEN }.joinToString("")
            val step = MiniTeacher.deduce(s.size, letters, s.clues) ?: continue
            val slot = MiniLogic.slots(s.size, s.answer)[step.slot]
            // Put any other word that fits the slot's letters in, and the oracle finds no fill.
            for (other in fits(MiniLogic.spelled(letters, slot)).filter { it != step.word }) {
                val tried = letters.toCharArray()
                slot.squares.forEachIndexed { i, sq -> tried[sq] = other[i] }
                assertEquals("${step.technique} $letters: $other", 0, MiniOracle.count(s.size, tried.concatToString(), limit = 1))
            }
            checked++
        }
        assertTrue("checked $checked", checked > 20)
    }

    @Test
    fun `steps stay sound from boards a player made, not only the solver's path`() {
        val rng = Rng(5)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val whites = s.answer.indices.filter { s.answer[it] != BLACK }
            val keep = rng.shuffled(whites).take(rng.nextInt(whites.size)).toSet()
            val now = s.copy(letters = s.answer.indices.map { if (it in keep || s.answer[it] == BLACK) s.answer[it] else OPEN }.joinToString(""))
            if (now.solved) continue
            val step = MiniTeacher.teach(now)!!
            assertTrue(step.technique, step.technique != MiniTeacher.MISTAKE)
            if (step.word != null) assertEquals(step.technique, MiniLogic.spelled(s.answer, MiniLogic.slots(s.size, s.answer)[step.slot]), step.word)
            else assertEquals(s.answer[step.square], step.letter)
        }
    }

    @Test
    fun `a wrong letter is addressed first, and a right one is never called wrong`() {
        val rng = Rng(9)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val whites = s.answer.indices.filter { s.answer[it] != BLACK }
            val right = whites[rng.nextInt(whites.size)]
            val wrong = whites.filter { it != right }[rng.nextInt(whites.size - 1)]
            val bad = 'a' + (s.answer[wrong] - 'a' + 1 + rng.nextInt(25)) % 26
            val now = s.withLetter(right, s.answer[right]).withLetter(wrong, bad)
            val step = Mini.teach(now)!!
            assertTrue(step.mistake)
            assertEquals(setOf(wrong), step.targets)
            assertTrue(step.isReached(step.apply(now)))
            val fixed = step.apply(now) as MiniState
            assertEquals(OPEN, fixed.letters[wrong])
            assertFalse(Mini.teach(fixed)!!.mistake)
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        val rng = Rng(13)
        var longest = 0
        for (d in Difficulty.entries) for (day in 0 until 365) {
            var s = board(day, d)
            if (day % 3 == 0) {
                val sq = s.answer.indexOfFirst { it != BLACK }
                s = s.withLetter(sq, 'a' + (s.answer[sq] - 'a' + 1 + rng.nextInt(25)) % 26)
            }
            while (!s.solved) {
                val step = MiniTeacher.teach(s)!!
                longest = maxOf(longest, step.explanation.length)
                assertTrue(step.nudge, step.nudge.length <= if (step.technique == MiniTeacher.CLUE) MiniTeacher.MAX_CLUE_NUDGE else MiniTeacher.MAX_NUDGE)
                assertTrue(step.explanation, step.explanation.length <= MiniTeacher.MAX_EXPLANATION)
                s = Mini.teach(s)!!.apply(s) as MiniState
            }
        }
        println("longest explanation $longest")
    }

    @Test
    fun `the teacher cannot see the answer but where it says so`() {
        // deduce takes the letters and the clues, nothing else; a changed stored answer changes no reasoned step.
        for (day in 0 until 20) {
            val s = board(day, Difficulty.HARD)
            var now = s
            repeat(6) { now = Mini.teach(now)!!.apply(now) as MiniState }
            val a = MiniTeacher.teach(now)!!
            val b = MiniTeacher.teach(now.copy(answer = now.letters.map { if (it == OPEN) 'q' else it }.joinToString("")))
            if (a.technique == MiniTeacher.CLUE) continue
            assertEquals(a.explanation, b?.explanation)
            assertEquals(a.word, b?.word)
        }
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Mini.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(true, false, false, false, false), frames.map { it.judge == null && !it.freePlay })
        val grid = Mini.TUTORIAL_GRID
        val a1 = Mini.TUTORIAL_ACROSS
        val d1 = Mini.TUTORIAL_DOWN
        val b0 = Mini.tutorialBoard()
        // Frame 2: tap 1 Across's first square, type its word.
        val pa = frames[1].steps!!
        assertEquals(TutorialStep.REJECT, pa(b0))
        var s = b0.select(a1.squares[0])
        assertEquals(TutorialStep.PROGRESS, pa(s))
        assertEquals(TutorialStep.REJECT, pa(s.type(if (grid[a1.squares[0]] == 'q') 'z' else 'q')))
        for ((k, sq) in a1.squares.withIndex()) {
            s = s.type(grid[sq])
            assertEquals(if (k == a1.length - 1) TutorialStep.DONE else TutorialStep.PROGRESS, pa(s))
        }
        // Frame 3: the square under the 1, again to turn Down, then the rest of 1 Down.
        val pd = frames[2].steps!!
        s = frames[2].state as MiniState
        assertEquals(TutorialStep.REJECT, pd(s))
        s = s.select(d1.squares[1])
        assertTrue(s.across)
        assertEquals(TutorialStep.PROGRESS, pd(s))
        s = s.select(d1.squares[1])
        assertFalse(s.across)
        assertEquals(TutorialStep.PROGRESS, pd(s))
        for ((k, sq) in d1.squares.drop(1).withIndex()) {
            s = s.type(grid[sq])
            assertEquals(if (k == d1.length - 2) TutorialStep.DONE else TutorialStep.PROGRESS, pd(s))
        }
        // Frame 4: a Z in the slip square, then Del.
        val pw = frames[3].steps!!
        s = frames[3].state as MiniState
        assertEquals(s.letters, (frames[2].state as MiniState).letters.let { s.letters })
        assertEquals(TutorialStep.REJECT, pw(s))
        s = s.select(Mini.TUTORIAL_WRONG)
        assertEquals(TutorialStep.PROGRESS, pw(s))
        assertEquals(TutorialStep.REJECT, pw(s.type('y')))
        s = s.type('z')
        assertEquals(TutorialStep.PROGRESS, pw(s))
        assertTrue(frames[3].glow!!(s).warning)
        assertTrue(Mini.teach(s)!!.mistake)
        s = s.copy(cursor = Mini.TUTORIAL_WRONG).erase()
        assertEquals(TutorialStep.DONE, pw(s))
        // Frame 5: free play from the two words, finished by hints alone with no fallback and no mistake; one answer.
        assertTrue(frames[4].freePlay)
        var now = frames[4].state as MiniState
        assertEquals(s.letters, now.letters)
        assertEquals(1, MiniOracle.count(4, now.letters))
        while (!now.solved) {
            val d = Mini.teach(now)!!
            assertFalse(d.fallback); assertFalse(d.mistake)
            now = d.apply(now) as MiniState
        }
        assertEquals(grid, now.letters)
        // The captions' claims: each named word is its clue's word.
        assertEquals(b0.clues[0], WheelWords.clue(MiniLogic.spelled(grid, a1)))
        for (f in frames) assertTrue("caption ${f.caption.length}: ${f.caption}", f.caption.length <= 170)
    }
}
