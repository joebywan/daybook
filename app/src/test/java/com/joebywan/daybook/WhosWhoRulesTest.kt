package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.WhoClue
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalDate

class WhosWhoRulesTest {

    private fun daily(day: Long, tier: Difficulty): WhosWhoState =
        WhosWho.generate(DailySeed.seedFor(LocalDate.fromEpochDays(START + day.toInt()), WhosWho.id, tier), tier) as WhosWhoState

    private companion object {
        val START = LocalDate(2026, 1, 1).toEpochDays()
    }

    /** [s] with every square ticked as [assignment] says. */
    private fun ticked(s: WhosWhoState, assignment: List<List<Int>>): WhosWhoState {
        var t = s
        for (c in 0 until s.columns) for (p in 0 until s.n) t = t.mark(p, c, assignment[c][p], tick = true)
        return t
    }

    @Test
    fun `solved checks the clues, so any assignment that keeps them wins`() {
        // Two people, the house and a pet, and one clue that both ways of filling it keep.
        val s = WhosWhoState(
            names = listOf("Ann", "Ben"),
            groups = listOf(WhosWhoWords.HOUSE, WhosWhoWords.PET),
            items = listOf(listOf(0, 1), listOf(0, 1)),
            clues = listOf(WhoClue(WhosWhoLogic.SAME, 2, 4)), // whoever lives in house 1 has the cat
            answer = listOf(listOf(0, 1), listOf(0, 1)),
        )
        assertTrue(ticked(s, s.answer).solved)
        assertTrue("the other legal answer", ticked(s, listOf(listOf(1, 0), listOf(1, 0))).solved)
        assertFalse("breaks the clue", ticked(s, listOf(listOf(0, 1), listOf(1, 0))).solved)
        assertFalse("a square left empty", s.mark(0, 0, 0, true).solved)
    }

    @Test
    fun `a tick replaces the cell's other tick, a second tap takes it off, and a cross is its own mark`() {
        val s = daily(0, Difficulty.HARD)
        val a = s.mark(1, 1, 2, tick = true)
        assertEquals(WhosWhoLogic.TICK, a.marks[a.markIndex(1, 1, 2)])
        val b = a.mark(1, 1, 3, tick = true)
        assertEquals(WhosWhoLogic.EMPTY, b.marks[b.markIndex(1, 1, 2)])
        assertEquals(WhosWhoLogic.TICK, b.marks[b.markIndex(1, 1, 3)])
        assertEquals(s.marks, b.mark(1, 1, 3, tick = true).mark(1, 1, 3, tick = true).clearCell(1, 1).marks)
        val c = s.mark(0, 0, 1, tick = false)
        assertEquals(WhosWhoLogic.CROSS, c.marks[c.markIndex(0, 0, 1)])
        assertEquals(s.marks, c.mark(0, 0, 1, tick = false).marks)
        assertEquals(2, b.moves)
        assertTrue(s.strike(2).isStruck(2))
        assertFalse(s.strike(2).strike(2).isStruck(2))
    }

    @Test
    fun `a tick rules its item out of the column, and a cross out of its cell, in what the board shows`() {
        val s = daily(3, Difficulty.EXPERT)
        val g = WhosWhoLogic.gridOf(s.n, s.columns, s.mark(0, 1, 2, true).mark(1, 1, 0, false).marks)
        assertEquals(1 shl 2, g.cand[0 * s.columns + 1])
        for (p in 1 until s.n) assertEquals(0, g.cand[p * s.columns + 1] shr 2 and 1)
        assertEquals(0, g.cand[1 * s.columns + 1] and 1)
        assertEquals(2, g.placed[1])
    }

    /** Random clue sets from a random answer's true clues, small enough for the oracle; some with several answers or none. */
    private fun randomBoards(count: Int, n: Int, columns: Int, seed: Long): List<WhosWhoLogic.Puzzle> {
        val rng = Rng(seed)
        val spec = WhosWhoLogic.Spec(n, columns - 1, listOf(1, 1, 1, 1, 1), namesOnly = false, needs = 0)
        return List(count) {
            val answer = List(columns) { rng.shuffled((0 until n).toList()) }
            val groups = listOf(WhosWhoWords.HOUSE) + WhosWhoWords.ATTRIBUTES.take(columns - 1)
            val pool = WhosWhoLogic.cluePool(n, groups, answer, spec).flatten()
            val clues = rng.shuffled(pool).take(rng.nextInt(2, 3 * n)).toMutableList()
            // Now and then a false clue, so some boards have no answer.
            if (rng.nextInt(4) == 0 && clues[0].kind <= WhosWhoLogic.NOT) clues[0] = clues[0].copy(kind = 1 - clues[0].kind)
            WhosWhoLogic.Puzzle(n, columns, clues)
        }
    }

    @Test
    fun `the count agrees with the oracle, including boards with several answers or none`() {
        val seen = IntArray(3)
        for ((n, columns) in listOf(3 to 2, 3 to 3, 4 to 2, 4 to 3)) {
            for (p in randomBoards(60, n, columns, 7L * n + columns)) {
                val all = WhosWhoOracle.answers(n, columns, p.clues, limit = 2)
                when (val v = WhosWhoLogic.count(p)) {
                    WhosWhoLogic.Verdict.None -> { assertEquals(0, all.size); seen[0]++ }
                    is WhosWhoLogic.Verdict.ExactlyOne -> { assertEquals(listOf(v.answer), all); seen[1]++ }
                    WhosWhoLogic.Verdict.Several -> { assertEquals(2, all.size); seen[2]++ }
                }
            }
        }
        println("whoswho count vs oracle: none ${seen[0]}, one ${seen[1]}, several ${seen[2]}")
        assertTrue("every verdict exercised: ${seen.toList()}", seen.all { it > 0 })
    }

    @Test
    fun `every generated board has exactly one answer by the oracle, and it is the stored one`() {
        for ((tier, days) in listOf(Difficulty.STANDARD to 20, Difficulty.HARD to 12, Difficulty.EXPERT to 3)) {
            for (day in 0 until days) {
                val s = daily(day * 37L, tier)
                assertEquals("$tier day $day", listOf(s.answer), WhosWhoOracle.answers(s.n, s.columns, s.clues, limit = 2))
            }
        }
    }

    @Test
    fun `every clue of a shipped board is needed - without it there are several answers or the ladder cannot finish`() {
        var several = 0
        var ladder = 0
        for (tier in Difficulty.entries) for (day in 0 until 30L) {
            val s = daily(day * 11, tier)
            val spec = WhosWhoLogic.specFor(tier)
            for (k in s.clues.indices) {
                val p = WhosWhoLogic.Puzzle(s.n, s.columns, s.clues.filterIndexed { i, _ -> i != k })
                val unique = WhosWhoLogic.count(p) is WhosWhoLogic.Verdict.ExactlyOne
                if (!unique) several++ else ladder++
                assertFalse("$tier day $day clue $k is implied", unique && WhosWhoLogic.finishes(p, spec.needs))
            }
        }
        println("whoswho minimal: removing a clue leaves several answers $several times, an answer the ladder cannot reach $ladder")
    }

    @Test
    fun `clue kinds are the tier's, and every clue is true of the answer`() {
        for (tier in Difficulty.entries) for (day in 0 until 40L) {
            val s = daily(day, tier)
            val kinds = s.clues.map { it.kind }.toSet()
            when (tier) {
                Difficulty.STANDARD -> {
                    assertTrue(kinds.all { it == WhosWhoLogic.SAME || it == WhosWhoLogic.NOT })
                    assertTrue("every clue names a person", s.clues.all { it.a / s.n == 0 || it.b / s.n == 0 })
                }
                Difficulty.HARD -> assertFalse(WhosWhoLogic.EITHER in kinds)
                Difficulty.EXPERT -> {}
            }
            val rows = WhosWhoOracle.rowsOf(s.n, s.answer)
            for (c in s.clues) assertTrue("$tier day $day $c", WhosWhoOracle.keeps(s.n, c, rows))
        }
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        val clues = DoubleArray(3)
        for (tier in Difficulty.entries) {
            for (day in 0 until 60L) {
                val s = daily(day, tier)
                assertEquals("$tier depth", WhosWhoLogic.specFor(tier).needs, WhosWhoLogic.depth(WhosWhoLogic.puzzleOf(s)))
                clues[tier.ordinal] += s.clues.size / 60.0
            }
        }
        println("whoswho clues per board: ${clues.joinToString(" / ") { "%.1f".format(it) }}")
        assertTrue(clues[0] < clues[1] && clues[1] < clues[2])
        val spec = Difficulty.entries.map { WhosWhoLogic.specFor(it) }
        assertTrue(spec[0].people <= spec[1].people && spec[1].people < spec[2].people)
        assertTrue(spec[0].attributes < spec[1].attributes)
    }

    @Test
    fun `the last resort of every tier is proved, one answer and the tier's depth`() {
        for (tier in Difficulty.entries) {
            val s = WhosWhoLogic.lastResort(tier)
            val p = WhosWhoLogic.puzzleOf(s)
            assertEquals(WhosWhoLogic.Verdict.ExactlyOne(s.answer), WhosWhoLogic.count(p))
            assertEquals(WhosWhoLogic.specFor(tier).needs, WhosWhoLogic.depth(p))
        }
    }

    @Test
    fun `generation is pure`() {
        for (tier in Difficulty.entries) for (seed in listOf(1L, 99L, -12345L)) {
            assertEquals(WhosWho.generate(seed, tier), WhosWho.generate(seed, tier))
        }
    }

    @Test
    fun `clue text is a plain sentence from the board's own words`() {
        val banned = listOf("einstein", "zebra", "  ", "null", "-1")
        for (tier in Difficulty.entries) for (day in 0 until 60L) {
            val s = daily(day, tier)
            for (k in s.clues.indices) {
                val t = WhosWhoLogic.clueText(s, k)
                assertTrue(t, t[0].isUpperCase() && t.endsWith(".") && t.length <= 72)
                assertTrue(t, banned.none { t.lowercase().contains(it) })
            }
        }
    }

    @Test
    fun `a clue the board shows as broken really has no assignment the marks allow`() {
        // Random true and false marks on daily boards; whenever possible() says a clue cannot hold, no assignment the
        // marks still allow keeps it (soundness of the red).
        val rng = Rng(5)
        var broken = 0
        for (day in 0 until 40L) {
            val s = daily(day, Difficulty.HARD)
            var t = s
            repeat(rng.nextInt(2, 8)) {
                t = t.mark(rng.nextInt(s.n), rng.nextInt(s.columns), rng.nextInt(s.n), tick = rng.nextInt(3) == 0)
            }
            val g = WhosWhoLogic.gridOf(s.n, s.columns, t.marks)
            val p = WhosWhoLogic.puzzleOf(s)
            for ((k, clue) in s.clues.withIndex()) {
                if (WhosWhoLogic.possible(p, g, clue)) continue
                broken++
                val any = WhosWhoOracle.answers(s.n, s.columns, listOf(clue), limit = 1) { person, column, slot ->
                    g.cand[person * s.columns + column] shr slot and 1 == 1
                }
                assertTrue("day $day clue $k is shown broken but can hold", any.isEmpty())
            }
        }
        assertTrue("some broken clues met: $broken", broken > 0)
    }
}
