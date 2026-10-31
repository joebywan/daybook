package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.WheelWords
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.WordWheelState
import com.joebywan.daybook.puzzles.WordWheelTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hints, checked against the lists read again here. A pinned step must name the stored word of its slot (the stored
 * word always fits, so one word fitting is that word), whatever the player has found or tried; the clue is the fallback
 * and is counted.
 */
class WordWheelTeachingTest {

    private fun board(day: Int, d: Difficulty): WordWheelState =
        WordWheel.generate(daySeed(day, WordWheel.id, d), d) as WordWheelState

    /** Grid words that fit [slot] of [s] as the player sees it, counted with a pattern match over the list. */
    private fun fitting(s: WordWheelState, slot: Int): List<String> {
        val shown = s.shown()
        val sl = s.slots[slot]
        val pattern = Regex(sl.cells(s.width).joinToString("") { shown[it]?.toString() ?: "." })
        val wheel = s.wheel.groupingBy { it }.eachCount()
        return WheelWords.grid(sl.length).filter { w ->
            pattern.matches(w) && w !in s.found && w !in s.bonus &&
                w.groupingBy { it }.eachCount().all { (c, n) -> (wheel[c] ?: 0) >= n }
        }
    }

    /**
     * Whether the empty slots of [s] can all be filled with [slot] holding [word]: each a different grid word that fits it as
     * the player sees it ([fitting]) and agrees with every slot it crosses. Reading order, no pruning beyond that.
     */
    private fun fillable(s: WordWheelState, slot: Int, word: String): Boolean {
        val empty = s.slots.indices.filter { !s.filled(it) }
        val cells = s.slots.map { it.cells(s.width) }
        val words = HashMap<Int, String>()
        fun agrees(k: Int, w: String) = words.all { (o, v) ->
            v != w && cells[k].indices.all { i -> cells[o].indexOf(cells[k][i]).let { j -> j < 0 || v[j] == w[i] } }
        }
        fun go(n: Int): Boolean {
            if (n == empty.size) return true
            val k = empty[n]
            for (w in if (k == slot) listOf(word) else fitting(s, k)) {
                if (!agrees(k, w)) continue
                words[k] = w
                if (go(n + 1)) return true
                words.remove(k)
            }
            return false
        }
        return go(0)
    }

    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val report = StringBuilder("Word Wheel hints alone, 365 daily boards per tier from 2026-01-01\n")
        for (d in Difficulty.entries) {
            var pinned = 0
            var crossed = 0
            var clued = 0
            var noClue = 0
            var choices = 0
            var worst = 0
            for (day in 0 until 365) {
                var s = board(day, d)
                var boardClues = 0
                while (!s.solved) {
                    val step = WordWheel.teach(s) ?: error("no hint on an unsolved board, day $day ${d.name}")
                    assertFalse(step.mistake)
                    if (step.fallback) {
                        clued++
                        boardClues++
                        assertEquals(WordWheelTeacher.CLUE, step.technique)
                    } else if (step.technique == WordWheelTeacher.CROSSING) {
                        crossed++
                        val t = WordWheelTeacher.teach(s)!!
                        assertEquals(s.slots[t.slot].word, t.word)
                        for (w in fitting(s, t.slot)) assertEquals("day $day ${d.name} $w", w == t.word, fillable(s, t.slot, w))
                    } else {
                        pinned++
                        assertEquals(WordWheelTeacher.PINNED, step.technique)
                    }
                    val next = step.apply(s) as WordWheelState
                    assertTrue(step.isReached(next))
                    assertEquals(s.found.size + 1, next.found.size)
                    s = next
                }
                if (boardClues == 0) noClue++
                val walk = WordWheelTeacher.walk(board(day, d))
                choices += walk.maxChoice
                worst = maxOf(worst, walk.maxChoice)
            }
            val steps = pinned + crossed + clued
            report.append(
                "${d.name}: $steps steps, pinned ${"%.0f".format(100.0 * pinned / steps)}%, crossing ${"%.0f".format(100.0 * crossed / steps)}%, " +
                    "clue (fallback) ${"%.1f".format(100.0 * clued / steps)}%; boards needing no clue $noClue/365; " +
                    "most words fitting a clued slot (generator's walk): mean ${"%.1f".format(choices / 365.0)}, worst $worst\n",
            )
        }
        writeReport("wordwheel-teaching-coverage.txt", report.toString())
        println(report)
    }

    @Test
    fun `a pinned step names its slot's stored word, the only one that fits, from boards a player made`() {
        for (d in Difficulty.entries) {
            for (day in 0 until 80) {
                val b = board(day, d)
                val rng = Rng(day * 31L + d.ordinal)
                // Some grid words found in a random order, and some bonus words tried.
                var s = b
                for (w in rng.shuffled(b.slots.map { it.word }).take(rng.nextInt(b.slots.size))) s = s.enter(w)
                val extras = WheelWords.gridWordsFrom(b.wheel).filter { w -> b.slots.none { it.word == w } }
                for (w in rng.shuffled(extras).take(rng.nextInt(extras.size + 1))) s = s.enter(w)
                val step = WordWheelTeacher.teach(s) ?: continue
                assertFalse("names a filled slot", s.filled(step.slot))
                assertEquals(s.slots[step.slot].word, step.word)
                val fits = fitting(s, step.slot)
                if (step.technique == WordWheelTeacher.PINNED) {
                    assertEquals("day $day ${d.name}", listOf(step.word), fits)
                } else if (step.technique == WordWheelTeacher.CROSSING) {
                    // Every other word that fits leaves no way to fill the grid: checked by a search of every filling.
                    assertTrue(step.word in fits && fits.size > 1)
                    for (w in fits) assertEquals("day $day ${d.name} $w", w == step.word, fillable(s, step.slot, w))
                } else {
                    assertEquals(fits.size, step.choices)
                    // A clue is offered only when no empty slot is pinned.
                    assertTrue(s.slots.indices.filter { !s.filled(it) }.all { fitting(s, it).size > 1 })
                    assertTrue(step.word in fits)
                }
            }
        }
    }

    @Test
    fun `the reasoning cannot see the slots' words`() {
        // Swapping what an empty slot stores for another word of the same shape changes nothing deduce is handed.
        val s = WordWheel.tutorialBoard().enter("brain").enter("barn")
        val swapped = s.copy(slots = s.slots.map { if (it.word == "rain") it.copy(word = "zzzz") else it })
        fun pin(t: WordWheelState) = WordWheelTeacher.deduce(
            t.wheel, t.width, WordWheelTeacher.shapes(t), t.slots.indices.filter { !t.filled(it) }, t.shown().toList(), t.found + t.bonus,
        )
        assertEquals(pin(s)!!.word, pin(swapped)!!.word)
        assertEquals("rain", pin(s)!!.word)
    }

    @Test
    fun `every nudge and explanation fits the panel and never names the word`() {
        for (d in Difficulty.entries) {
            for (day in 0 until 730) {
                var s = board(day, d)
                while (!s.solved) {
                    val step = WordWheelTeacher.teach(s)!!
                    assertTrue("${step.nudge.length}: ${step.nudge}", step.nudge.length <= WordWheelTeacher.MAX_NUDGE)
                    assertTrue("${step.explanation.length}: ${step.explanation}", step.explanation.length <= WordWheelTeacher.MAX_EXPLANATION)
                    // A hint gives a clue, not the word: only "Show me" types it. Words are shown in capitals, so a capital
                    // spelling is the word handed over; the same letters in the prose ("fit", "one") give nothing away.
                    assertFalse(step.explanation, step.word.uppercase() in step.explanation)
                    assertFalse(step.nudge, step.word.uppercase() in step.nudge)
                    s = s.enter(step.word)
                }
            }
        }
    }

    @Test
    fun `a hint costs little to compute`() {
        val times = mutableListOf<Long>()
        for (day in 0 until 200) {
            val s = board(day, Difficulty.EXPERT)
            val t0 = System.nanoTime()
            WordWheelTeacher.teach(s)
            times += System.nanoTime() - t0
        }
        times.sort()
        println("first hint, Expert: median ${times[100] / 1e6} ms, p95 ${times[190] / 1e6} ms, max ${times.last() / 1e6} ms")
        assertTrue(times[190] < 50_000_000)
    }

    // ---- the walkthrough --------------------------------------------------------------------------

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = WordWheel.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(true, false, false, false, true), frames.map { it.accepts == null })
        val s0 = WordWheel.tutorialBoard()
        assertEquals(s0, frames[0].state)
        // The words are spelled by wheel positions, as a swipe or taps make them.
        fun spell(s: WordWheelState, word: String): WordWheelState {
            var picked = listOf<Int>()
            for (c in word) picked = com.joebywan.daybook.puzzles.typeWheelLetter(s.wheel, picked, c)!!
            return s.enter(picked.map { s.wheel[it] }.joinToString(""))
        }
        val s1 = spell(s0, "brain")
        assertTrue(frames[1].accepts!!(s1))
        assertFalse(frames[1].accepts!!(spell(s0, "barn")))
        assertFalse(frames[1].accepts!!(spell(s0, "ban")))
        assertEquals(s1, frames[2].state)
        val s2 = spell(s1, "barn")
        assertTrue(frames[2].accepts!!(s2))
        assertFalse(frames[2].accepts!!(spell(s1, "rain")))
        assertFalse(frames[2].accepts!!(spell(s1, "bar")))
        assertEquals(s2, frames[3].state)
        val s3 = spell(s2, "bran")
        assertTrue(frames[3].accepts!!(s3))
        assertFalse(frames[3].accepts!!(spell(s2, "rain")))
        assertEquals(s3, frames[4].state)
        assertTrue(frames[4].freePlay)
        frames.forEach { assertTrue(it.caption, it.caption.length <= 170) }
    }

    @Test
    fun `the walkthrough's claims hold against the lists`() {
        val s0 = WordWheel.tutorialBoard()
        assertEquals(emptyList<String>(), WordWheelOracle.problems(s0))
        assertEquals("only BRAIN uses every letter", listOf("brain"), fitting(s0, 0))
        val s1 = s0.enter("brain")
        assertEquals("only BARN has R third", listOf("barn"), fitting(s1, 1))
        assertTrue("BRAN is a bonus word", WheelWords.isWord("bran") && s0.slots.none { it.word == "bran" })
        val s3 = s1.enter("barn").enter("bran")
        assertEquals(listOf("bran"), s3.bonus)
        assertEquals("only RAIN has I third", listOf("rain"), fitting(s3, 2))
    }

    @Test
    fun `the free-play frame is finished by hints alone, with no clue`() {
        var s = WordWheel.tutorial.last().state as WordWheelState
        while (!s.solved) {
            val d = WordWheel.teach(s)!!
            assertFalse(d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as WordWheelState
        }
    }
}
