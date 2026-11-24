package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.QuartetsCategory
import com.joebywan.daybook.puzzles.QuartetsLogic
import com.joebywan.daybook.puzzles.QuartetsState
import com.joebywan.daybook.puzzles.QuartetsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quartets' hints ([QuartetsTeacher]). Soundness is judged by this test's own splitter ([Splits]), written on another
 * principle from [QuartetsLogic.split]: it lists every four-word combination of the words left by index, keeps those
 * one category fits whole, and counts every exact cover by always covering the *highest* word left, with no limit. A
 * step that is not the fallback must be a group of **every** split of the words left, so the boards with several splits
 * (hand-built, and random ones with a planted split) are the real test; a shipped board has one.
 */
class QuartetsTeachingTest {

    /** Every split of [remaining] into fours each fitting one of [masks], all of them. */
    private object Splits {
        fun all(masks: List<Int>, remaining: Int): List<Set<Int>> {
            val left = (0 until 16).filter { remaining and (1 shl it) != 0 }
            val fours = ArrayList<Int>()
            for (a in left.indices) for (b in a + 1 until left.size) for (c in b + 1 until left.size) for (d in c + 1 until left.size) {
                val f = (1 shl left[a]) or (1 shl left[b]) or (1 shl left[c]) or (1 shl left[d])
                if (masks.any { it and f == f }) fours += f
            }
            val out = ArrayList<Set<Int>>()
            fun cover(rest: Int, chosen: List<Int>) {
                if (rest == 0) { out += chosen.toSet(); return }
                val high = 1 shl (31 - Integer.numberOfLeadingZeros(rest))
                for (f in fours) if (f and high != 0 && f and rest == f) cover(rest and f.inv(), chosen + f)
            }
            if (left.size % 4 == 0) cover(remaining, emptyList())
            return out
        }
    }

    private class Walk(val steps: List<QuartetsTeacher.Step>, val solved: Boolean)

    /**
     * Follows hints by "Show me" from [start] until solved, checking every step on the way: reached only after it is
     * applied, sound when not the fallback, and its words within bounds.
     */
    private fun walk(start: QuartetsState, label: String): Walk {
        var s = start
        val steps = ArrayList<QuartetsTeacher.Step>()
        while (!s.solved) {
            assertTrue("$label: more steps than groups", steps.size < 4)
            val step = QuartetsTeacher.teach(s)
            assertNotNull("$label: no hint on an unsolved board", step)
            step!!
            assertTrue("$label: unknown technique ${step.technique}", step.technique in QuartetsTeacher.TECHNIQUES)
            assertEquals("$label: a group is four words", 4, step.group.countOneBits())
            assertEquals("$label: a group of words left", step.group, step.group and s.remaining)
            if (step.technique != QuartetsTeacher.EVERY_SPLIT) {
                val splits = Splits.all(s.masks, s.remaining)
                assertTrue("$label: positions here always split", splits.isNotEmpty())
                for (sp in splits) assertTrue("$label: ${step.technique} ${names(s, step.group)} is not in every split", step.group in sp)
            }
            checkText(s, step, label)
            val d = QuartetsTeacher.deduction(s)!!
            assertEquals(step.technique == QuartetsTeacher.EVERY_SPLIT, d.fallback)
            assertFalse("$label: reached before it is made", d.isReached(s))
            val next = d.apply(s) as QuartetsState
            assertTrue("$label: not reached after Show me", d.isReached(next))
            assertEquals("$label: Show me is one move", s.moves + 1, next.moves)
            assertEquals("$label: Show me costs no life", s.livesLeft, next.livesLeft)
            steps += step
            s = next
        }
        assertNull("$label: a hint on a solved board", QuartetsTeacher.teach(s))
        return Walk(steps, s.solved)
    }

    private fun names(s: QuartetsState, mask: Int) = s.words.indices.filter { mask and (1 shl it) != 0 }.map { s.words[it] }

    /** At most one word of the group named (titles aside), at most one tile glowing, within the panel's room. */
    private fun checkText(s: QuartetsState, step: QuartetsTeacher.Step, label: String) {
        for (text in listOf(step.nudge, step.explanation)) {
            assertFalse("$label: $text", text.contains("null") || text.contains("undefined"))
            val outsideTitles = text.replace(Regex("“[^”]*”"), " ")
            val tokens = Regex("[A-Za-z]+").findAll(outsideTitles).map { it.value.lowercase() }.toSet()
            val named = names(s, step.group).filter { it in tokens }
            assertTrue("$label: names ${named.size} words of its group: $text", named.size <= 1)
        }
        assertTrue("$label: nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= QuartetsTeacher.MAX_NUDGE)
        assertTrue("$label: explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= QuartetsTeacher.MAX_EXPLANATION)
        val tiles = step.focus + step.cited + step.targets - QuartetsLogic.SUBMIT
        assertTrue("$label: glows ${tiles.size} tiles", tiles.size <= 1)
        assertTrue("$label: targets $step.targets", step.targets - QuartetsLogic.SUBMIT == step.focus)
    }

    // ---- hand-built positions with several splits ------------------------------------------------------------------

    private fun cat(title: String, vararg words: String) = QuartetsCategory(title, 1, words.toList())

    private fun board(words: List<String>, vararg cats: QuartetsCategory) = QuartetsLogic.board(words, cats.toList())

    private val grid = (0 until 16).map { "w${'a' + it / 4}${'a' + it % 4}" }

    /** Rows and columns of a 4x4: every theme fits exactly four and every word two themes, so it splits two ways. */
    @Test
    fun `a theme of exactly four is not claimed when every word of it fits another`() {
        val cats = (0 until 4).map { r -> cat("Row $r", *(0 until 4).map { grid[r * 4 + it] }.toTypedArray()) } +
            (0 until 4).map { c -> cat("Column $c", *(0 until 4).map { grid[it * 4 + c] }.toTypedArray()) }
        val s = board(grid, *cats.toTypedArray())
        assertEquals(2, Splits.all(s.masks, s.remaining).size)
        assertEquals(QuartetsTeacher.EVERY_SPLIT, QuartetsTeacher.teach(s)!!.technique)
        // Once one row is taken the columns fit three words each, so the other rows are settled.
        val after = s.guess(s.masks[s.titles.indexOf("Row 0")])!!
        assertEquals(QuartetsTeacher.EXACTLY_FOUR, QuartetsTeacher.teach(after)!!.technique)
        walk(s, "rows and columns")
    }

    /** Eight words fit one theme and nothing else: two groups lie inside it, in 35 ways, so its loners are not one group. */
    @Test
    fun `eight words of one theme are not called one group`() {
        val s = board(grid, cat("Eight", *grid.take(8).toTypedArray()), cat("Four A", *grid.subList(8, 12).toTypedArray()),
            cat("Four B", *grid.subList(12, 16).toTypedArray()))
        assertEquals(35, Splits.all(s.masks, s.remaining).size)
        val w = walk(s, "eight")
        assertEquals(listOf(QuartetsTeacher.EXACTLY_FOUR, QuartetsTeacher.EXACTLY_FOUR, QuartetsTeacher.EVERY_SPLIT, QuartetsTeacher.LAST_FOUR),
            w.steps.map { it.technique })
    }

    /** Four themes of five in a ring, each sharing a word with the next: the ring turns either way, so nothing is settled. */
    @Test
    fun `a ring of spares is left to the fallback`() {
        val g = grid
        val s = board(g,
            cat("A", g[0], g[1], g[2], g[3], g[4]), cat("B", g[4], g[5], g[6], g[7], g[8]),
            cat("C", g[8], g[9], g[10], g[11], g[12]), cat("D", g[12], g[13], g[14], g[15], g[0]))
        assertEquals(2, Splits.all(s.masks, s.remaining).size)
        assertEquals(QuartetsTeacher.EVERY_SPLIT, QuartetsTeacher.teach(s)!!.technique)
        walk(s, "ring")
    }

    /**
     * MERCURY fits three themes of five and four; no theme of exactly four has a word with nowhere else to go, so the
     * first step is a spare: five metals, four of which fit nothing else.
     */
    @Test
    fun `a spare word leaves the four loners as the group`() {
        val words = listOf("mars", "venus", "earth", "saturn", "mercury", "iron", "tin", "copper", "zinc",
            "pigeon", "courier", "herald", "crow", "owl", "robin", "eagle")
        val s = board(words,
            cat("Messengers", "mercury", "pigeon", "courier", "herald"),
            cat("Metals", "mercury", "iron", "tin", "copper", "zinc"),
            cat("Planets", "mars", "venus", "earth", "saturn", "mercury"),
            cat("Things that fly", "pigeon", "courier", "herald", "crow", "owl", "robin", "eagle"))
        assertEquals(1, Splits.all(s.masks, s.remaining).size)
        val step = QuartetsTeacher.teach(s)!!
        assertEquals(QuartetsTeacher.SPARE, step.technique)
        assertEquals(setOf("iron", "tin", "copper", "zinc"), names(s, step.group).toSet())
        assertTrue(step.explanation, step.explanation.contains("MERCURY"))
        assertEquals(setOf(words.indexOf("mercury")), step.focus)
        walk(s, "metals")
    }

    @Test
    fun `no hint on a lost board`() {
        val s = QuartetsLogic.lastResort(Difficulty.STANDARD)
        var t = s
        val split = Splits.all(s.masks, s.remaining).single()
        // Four wrong fours: the k-th word of each group.
        val groups = split.map { g -> (0 until 16).filter { g and (1 shl it) != 0 } }
        for (k in 0 until 4) t = t.guess(groups.fold(0) { m, g -> m or (1 shl g[k]) })!!
        assertTrue(t.failed)
        assertNull(QuartetsTeacher.teach(t))
    }

    // ---- random positions with a planted split ---------------------------------------------------------------------

    /**
     * Sixteen words, a random partition planted as four themes, each grown by up to three other words, and up to four
     * themes of three to seven words besides. Most have several splits; every non-fallback step must hold in all of them.
     */
    @Test
    fun `on random boards with several splits every step holds in every split`() {
        val counts = sortedMapOf<String, Int>()
        var several = 0
        var reasonedOnSeveral = 0
        for (seed in 1L..3000L) {
            val rng = Rng(seed)
            val order = rng.shuffled(grid)
            val cats = ArrayList<QuartetsCategory>()
            for (gi in 0 until 4) {
                val extra = rng.shuffled(order.drop((gi + 1) * 4) + order.take(gi * 4)).take(rng.nextInt(4))
                cats += cat("Planted $gi", *(order.subList(gi * 4, gi * 4 + 4) + extra).toTypedArray())
            }
            repeat(rng.nextInt(5)) { k -> cats += cat("Extra $k", *rng.shuffled(grid).take(3 + rng.nextInt(5)).toTypedArray()) }
            val s = board(grid, *cats.toTypedArray())
            val splits = Splits.all(s.masks, s.remaining)
            if (splits.size > 1) several++
            val w = walk(s, "random $seed")
            for (st in w.steps) counts.merge(st.technique, 1, Int::plus)
            if (splits.size > 1 && w.steps.first().technique != QuartetsTeacher.EVERY_SPLIT) reasonedOnSeveral++
        }
        println("random boards: $several of 3000 split several ways, $reasonedOnSeveral of those opened with reasoning; steps $counts")
        assertTrue("too few boards with several splits: $several", several > 300)
        assertTrue("reasoning never tested on several splits", reasonedOnSeveral > 100)
        for (t in listOf(QuartetsTeacher.LAST_FOUR, QuartetsTeacher.EXACTLY_FOUR, QuartetsTeacher.SPARE)) {
            assertTrue("$t never reached on random boards: $counts", (counts[t] ?: 0) > 20)
        }
    }

    // ---- shipped boards ---------------------------------------------------------------------------------------------

    /** Every group found first, by a player's own order, then hints: still sound and still finishing. */
    @Test
    fun `hints from boards a player started stay sound`() {
        for (d in Difficulty.entries) {
            for (day in 0 until 40) {
                val s = QuartetsLogic.generate(daySeed(day, "test-quartets-own", d), d)
                for (g in Splits.all(s.masks, s.remaining).single()) {
                    walk(s.guess(g)!!, "$d day $day after ${names(s, g)}")
                }
            }
        }
    }

    @Test
    fun `a year of daily boards - steps by technique and the fallback share`() {
        val report = StringBuilder("Quartets hints, a year of daily boards per tier from $TEST_START, walked from the start by Show me.\n")
        val shares = HashMap<Difficulty, Double>()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { QuartetsTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var boardsWithFallback = 0
            var firstFallback = 0
            var worstNanos = 0L
            for (day in 0 until 365) {
                val s = QuartetsLogic.generate(daySeed(day, "test-quartets", d), d)
                assertEquals("$d day $day", 1, Splits.all(s.masks, s.remaining).size)
                val t0 = System.nanoTime()
                QuartetsTeacher.teach(s)
                worstNanos = maxOf(worstNanos, System.nanoTime() - t0)
                val w = walk(s, "$d day $day")
                assertEquals(4, w.steps.size)
                for (st in w.steps) counts.merge(st.technique, 1, Int::plus)
                if (w.steps.any { it.technique == QuartetsTeacher.EVERY_SPLIT }) boardsWithFallback++
                if (w.steps.first().technique == QuartetsTeacher.EVERY_SPLIT) firstFallback++
            }
            val total = counts.values.sum()
            val share = counts.getValue(QuartetsTeacher.EVERY_SPLIT).toDouble() / total
            shares[d] = share
            report.append("$d: ${counts.entries.joinToString { "${it.key} ${it.value}" }} (of $total steps); " +
                "fallback ${"%.1f".format(share * 100)}% of steps, on $boardsWithFallback boards, the first step on $firstFallback; " +
                "worst first hint ${worstNanos / 1000} us\n")
        }
        print(report)
        writeReport("quartets-teaching.txt", report.toString())
        for ((d, share) in shares) assertTrue("$d fallback ${share * 100}%", share <= CEILING.getValue(d))
    }

    private companion object {
        /**
         * Loose ceilings on the fallback share of steps. Measured (`build/reports/quartets-teaching.txt`, 2026-11-24): no
         * daily board of a year on any tier needed it. A shipped board splits one way, but one could still defeat both
         * reasoning rungs, so the ceiling allows a little room for the tier gate to move.
         */
        val CEILING = mapOf(Difficulty.STANDARD to 0.02, Difficulty.HARD to 0.02, Difficulty.EXPERT to 0.05)
    }
}
