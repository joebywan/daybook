package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Card
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Sets shipped a board nobody could finish: the target counted every set present, overlaps
 * included, while the board retired a card the moment it was claimed. Generation was healthy, the
 * state round-tripped, the target matched the board — every test already here passed. The one that
 * would not is `claiming every set on the board finishes the puzzle`: it plays the board out
 * through the real tap handler instead of reading it, which is the only way the gap between "the
 * sets exist" and "a player can reach them" shows up.
 */
class SetsRulesTest {

    private val seeds = (0 until 12).map {
        DailySeed.seedFor(LocalDate.of(2026, 2, 1).plusDays(it.toLong()), "sets", Difficulty.HARD)
    }

    @Test
    fun `the advertised target is the number of sets actually on the board`() {
        forEachBoard { label, board ->
            assertEquals(
                "$label promises ${board.target} sets on a board holding others",
                Sets.allSets(board.cards).size,
                board.target,
            )
            assertTrue("$label has nothing to find", board.target > 0)
            assertFalse("$label starts finished", board.solved)
        }
    }

    @Test
    fun `claiming every set on the board finishes the puzzle`() {
        var sawSharedCard = false
        forEachBoard { label, board ->
            var s = board
            for (trio in Sets.allSets(board.cards)) {
                val claimed = s.found.size
                if (trio.any { it in s.found.flatten() }) sawSharedCard = true
                for (index in trio) s = Sets.tap(s, index)

                assertFalse("$label: $trio was rejected as not a set", s.lastWrong)
                assertEquals("$label: claiming $trio did not register", claimed + 1, s.found.size)
            }
            assertTrue(
                "$label: found ${s.found.size} of ${s.target} after claiming every set present",
                s.solved,
            )
        }
        // Without this the test above could pass on boards whose sets never overlap, which is
        // exactly the case the old card lock happened to handle.
        assertTrue("no board reused a card, so nothing tested the fix", sawSharedCard)
    }

    @Test
    fun `a card claimed once is still live for the next set it belongs to`() {
        forEachBoard { label, board ->
            val sets = Sets.allSets(board.cards)
            val overlapping = sets.firstNotNullOfOrNull { first ->
                sets.firstOrNull { it != first && it.intersect(first.toSet()).isNotEmpty() }
                    ?.let { first to it }
            } ?: return@forEachBoard

            val (first, second) = overlapping
            val afterFirst = first.fold(board, Sets::tap)
            val afterSecond = second.fold(afterFirst, Sets::tap)

            assertFalse("$label: reusing a card was scored as a mistake", afterSecond.lastWrong)
            assertEquals(
                "$label: $second was refused because it shares a card with $first",
                2,
                afterSecond.found.size,
            )
        }
    }

    @Test
    fun `offering a set already claimed is a misread rather than a mistake`() {
        forEachBoard { label, board ->
            val trio = Sets.allSets(board.cards).first()
            val once = trio.fold(board, Sets::tap)
            val twice = trio.fold(once, Sets::tap)

            assertEquals("$label counted the same set twice", 1, twice.found.size)
            assertFalse("$label flashed 'not a set' at a real set", twice.lastWrong)
            assertTrue("$label gave no feedback at all", twice.lastRepeat)
        }
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        val tiers = Difficulty.entries.map { difficulty ->
            val board = Sets.generate(seeds.first(), difficulty) as SetsState
            Triple(difficulty, board.cards.size, board.target)
        }

        for ((easier, harder) in tiers.zipWithNext()) {
            val (lower, lowerCards, lowerTarget) = easier
            val (upper, upperCards, upperTarget) = harder
            // Candidate triples to scan before the board can be called finished, and discoveries
            // required. Neither may fall going up the ladder, and the second must rise.
            assertTrue(
                "$upper scans ${triples(upperCards)} triples, $lower scans ${triples(lowerCards)}",
                triples(upperCards) >= triples(lowerCards),
            )
            assertTrue(
                "$upper asks for $upperTarget sets, $lower already asks for $lowerTarget",
                upperTarget > lowerTarget,
            )
        }
    }

    @Test
    fun `the ceiling the generator refuses targets above is the real one`() {
        // Nine cards sharing two traits form a plane, the densest nine cards there are: every one
        // of their 36 pairs lies in a set, so all 12 of the bound's sets are present. A ceiling
        // that came out below this would reject a legal tier; one above would wave an impossible
        // one through to the 4000-draw path the unwinnable board came from.
        val plane = (0..2).flatMap { count -> (0..2).map { shape -> Card(count, shape, 0, 0) } }

        assertEquals("the pair bound is not tight at nine cards", 12, Sets.maxSets(9))
        assertEquals("a plane does not attain the bound", Sets.maxSets(9), Sets.allSets(plane).size)

        for (difficulty in Difficulty.entries) {
            val board = Sets.generate(seeds.first(), difficulty) as SetsState
            assertTrue(
                "$difficulty asks for ${board.target} of at most ${Sets.maxSets(board.cards.size)}",
                board.target <= Sets.maxSets(board.cards.size),
            )
        }
    }

    private fun triples(cards: Int) = cards * (cards - 1) * (cards - 2) / 6

    private fun forEachBoard(check: (String, SetsState) -> Unit) {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                check("sets/${difficulty.name}/$seed", Sets.generate(seed, difficulty) as SetsState)
            }
        }
    }
}
