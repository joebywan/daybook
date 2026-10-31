package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.Dwindle
import com.joebywan.daybook.puzzles.DwindleLogic
import com.joebywan.daybook.puzzles.DwindleState
import com.joebywan.daybook.puzzles.DwindleTeacher
import com.joebywan.daybook.puzzles.WheelWords
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DwindleTeachingTest {

    private val start = LocalDate(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        Dwindle.generate(DailySeed.seedFor(LocalDate.fromEpochDays(start.toEpochDays() + day), Dwindle.id, d), d) as DwindleState

    @Test
    fun `coverage - which techniques boards need, per difficulty, and how often the fallback`() {
        val report = StringBuilder("Dwindle hints alone, 365 daily boards per tier from $start\n")
        val ceiling = mapOf(Difficulty.STANDARD to 30.0, Difficulty.HARD to 30.0, Difficulty.EXPERT to 35.0)
        for (d in Difficulty.entries) {
            val tally = DwindleTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var noClue = 0
            var worstMs = 0.0
            for (day in 0 until 365) {
                var s = board(day, d)
                var clues = 0
                while (!s.solved) {
                    val t0 = System.nanoTime()
                    val h = Dwindle.teach(s)!!
                    if (day > 0) worstMs = maxOf(worstMs, (System.nanoTime() - t0) / 1e6)
                    assertFalse(h.mistake)
                    tally[h.technique] = tally.getValue(h.technique) + 1
                    if (h.fallback) clues++
                    assertEquals(h.fallback, h.technique == DwindleTeacher.CLUE)
                    val next = h.apply(s) as DwindleState
                    assertTrue(h.isReached(next))
                    assertEquals(s.found.size + 1, next.found.size)
                    s = next
                }
                if (clues == 0) noClue++
            }
            val steps = tally.values.sum()
            val share = 100.0 * tally.getValue(DwindleTeacher.CLUE) / steps
            report.append(
                "${d.name}: $steps steps, " + tally.entries.joinToString(", ") { "${it.key} ${"%.1f".format(100.0 * it.value / steps)}%" } +
                    "; boards needing no clue $noClue/365; worst hint ${"%.0f".format(worstMs)} ms\n",
            )
            assertTrue("$d fallback $share%", share < ceiling.getValue(d))
        }
        File("build/reports").mkdirs()
        File("build/reports/dwindle-teaching-coverage.txt").writeText(report.toString())
        println(report)
    }

    @Test
    fun `a reasoned step is sound from boards a player made - it names a goal, and every board its reasoning allows has it`() {
        for (d in Difficulty.entries) for (day in 0 until if (d == Difficulty.EXPERT) 40 else 120) {
            val rng = Rng(day * 37L + d.ordinal)
            var s = board(day, d)
            // A player's own order, with a bonus word or two along the way.
            val order = rng.shuffled(s.goals.map { it.word })
            for (w in order) {
                val step = DwindleTeacher.teach(s)!!
                assertTrue("day $day $d: ${step.word}", s.goals.any { it.word == step.word } && step.word !in s.found)
                if (step.technique != DwindleTeacher.CLUE) {
                    // Independent count: the same forced word against a cover search with no propagation.
                    val covers = bruteCovers(s)
                    if (covers != null) assertTrue("day $day $d ${step.technique} ${step.word}", covers.all { step.word in it })
                }
                s = s.enter(w)
            }
        }
    }

    /** Every set of list words that uses every tile exactly its count and every dash once, by plain backtracking; null if too many to walk. */
    private fun bruteCovers(s: DwindleState): List<Set<String>>? {
        val counts = s.counts()
        val open = s.goals.filter { it.word !in s.found }.map { it.word.length }.sorted()
        val tried = s.found + s.bonus
        val options = open.distinct().flatMap { n ->
            WheelWords.grid(n).filter { it !in tried }.mapNotNull { w ->
                val v = DwindleLogic.ways(s.letters, s.width, s.height, w)
                if (v is Verdict.ExactlyOne && v.value.all { counts[it] > 0 }) w to v.value else null
            }
        }
        if (options.size > 40) return null
        val out = mutableListOf<Set<String>>()
        fun go(i: Int, left: IntArray, picked: List<String>) {
            if (picked.size == open.size) {
                if (left.all { it == 0 } && picked.map { it.length }.sorted() == open) out += picked.toSet()
                return
            }
            if (i == options.size) return
            go(i + 1, left, picked)
            val (w, tiles) = options[i]
            if (tiles.all { left[it] > 0 }) {
                val l = left.copyOf()
                for (t in tiles) l[t]--
                go(i + 1, l, picked + w)
            }
        }
        go(0, counts, emptyList())
        return out
    }

    @Test
    fun `the reasoning takes only what the player can see`() {
        val fn = DwindleTeacher::class.java.methods.first { it.name == "deduce" }
        assertEquals(
            listOf("int", "int", "java.lang.String", "int[]", "java.util.List", "java.util.List"),
            fn.parameterTypes.map { it.typeName },
        )
    }

    @Test
    fun `every nudge and explanation fits the panel, never names the word, and glows none of its other tiles`() {
        for (d in Difficulty.entries) for (day in 0 until 120) {
            var s = board(day, d)
            while (!s.solved) {
                val st = DwindleTeacher.teach(s)!!
                assertTrue("nudge ${st.nudge}", st.nudge.length <= DwindleTeacher.MAX_NUDGE)
                assertTrue("explanation ${st.explanation.length}: ${st.explanation}", st.explanation.length <= DwindleTeacher.MAX_EXPLANATION)
                assertFalse(st.explanation, Regex("\\b${st.word}\\b", RegexOption.IGNORE_CASE).containsMatchIn(st.nudge + " " + st.explanation))
                assertTrue("a clue is given", st.explanation.contains("“"))
                val tiles = (st.focus + st.cited + st.targets).filter { it < DwindleTeacher.DASH }
                assertTrue("at most one tile glows, the word's way stays hidden: $tiles", tiles.size <= 1)
                s = s.enter(st.word)
            }
        }
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Dwindle.tutorial
        assertEquals(5, frames.size)
        assertEquals("one READ frame first, then moves", null, frames[0].accepts)
        val s0 = Dwindle.tutorialBoard()
        fun spell(s: DwindleState, cells: List<Int>) = cells.map { s.letters[it] }.joinToString("")
        // Real gestures: the swipe and the taps build the path the frames name.
        var path = emptyList<Int>()
        for (c in listOf(1, 3, 6, 7)) path = DwindleLogic.swipe(s0, s0.counts(), path, c)
        assertEquals("chef", spell(s0, path))
        val s1 = s0.enter(spell(s0, path))
        assertTrue(frames[1].accepts!!(s1))
        assertFalse(frames[1].accepts!!(s0.enter("she")))
        assertEquals(s1, frames[2].state)

        path = emptyList()
        for (c in listOf(4, 3, 6)) path = DwindleLogic.swipe(s1, s1.counts(), path, c)
        val s2 = s1.enter(spell(s1, path))
        assertEquals(listOf("she"), s2.bonus)
        assertTrue(frames[2].accepts!!(s2))
        assertFalse(frames[2].accepts!!(s1.enter("chest")))
        assertEquals(s2, frames[3].state)

        path = emptyList()
        for (c in listOf(1, 3, 6, 4, 0)) path = DwindleLogic.tap(s2, s2.counts(), path, c)
        val s3 = s2.enter(spell(s2, path))
        assertTrue(frames[3].accepts!!(s3))
        assertFalse(frames[3].accepts!!(s2.enter("fist")))
        assertEquals(s3, frames[4].state)
        for (c in listOf(1, 3, 6)) assertEquals("the caption says C, H and E vanish", 0, s3.counts()[c])
        for (c in listOf(1, 3, 6)) assertEquals("the caption says one use left", 1, s2.counts()[c])
        for (f in frames) assertTrue(f.caption, f.caption.length <= 170)
    }

    @Test
    fun `the walkthrough board is proved, and hints alone finish its free-play frame with no fallback`() {
        val s0 = Dwindle.tutorialBoard()
        assertTrue(DwindleLogic.proved(s0))
        assertEquals(emptyList<String>(), DwindleOracle.problems(s0))
        assertTrue(s0.goals.all { WheelWords.isGridWord(it.word) })
        assertTrue("SHE is real", WheelWords.isWord("she"))
        var s = Dwindle.tutorial.last().state as DwindleState
        while (!s.solved) {
            val h = Dwindle.teach(s)!!
            assertFalse("free play falls back on ${h.technique}", h.fallback)
            s = h.apply(s) as DwindleState
        }
    }
}
