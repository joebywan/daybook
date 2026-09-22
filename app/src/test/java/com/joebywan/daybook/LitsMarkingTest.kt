package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The two things the LITS board now tells the player about itself: which letter a region settled
 * into, and which squares can no longer be shaded.
 *
 * Both are derived on every render rather than stored, so a mistake here would not crash — it
 * would quietly colour a shape wrongly, or cross off a square that is part of the answer. The
 * second is the serious one: a player who trusts a wrong cross is being walked away from a board
 * they could have finished, so the crosses are checked against every legal answer the board has
 * rather than against the reasoning that produced them.
 */
class LitsMarkingTest {

    // ---- letters -------------------------------------------------------------------------------

    /** A 4x4 board that is all one region, so any four squares are that region's tetromino. */
    private fun oneRegion(vararg shaded: Int): LitsState = LitsState(
        width = 4,
        height = 4,
        region = List(16) { 0 },
        shaded = List(16) { it in shaded.toSet() },
        solution = List(16) { false },
    )

    @Test
    fun `a settled region wears its letter on every one of its squares`() {
        val cases = mapOf(
            // A 1x4 strip, a 3-plus-1 elbow, the T's stub and the S's step.
            listOf(0, 1, 2, 3) to Lits.Piece.I,
            listOf(0, 4, 8, 9) to Lits.Piece.L,
            listOf(0, 1, 2, 5) to Lits.Piece.T,
            listOf(1, 2, 4, 5) to Lits.Piece.S,
        )
        for ((cells, piece) in cases) {
            val letters = oneRegion(*cells.toIntArray()).letters()
            for (cell in cells) assertEquals("$cells should read as $piece", piece, letters[cell])
            for (cell in 0 until 16) if (cell !in cells) assertNull(letters[cell])
        }
    }

    @Test
    fun `a region with no legal shape on it has no letter at all`() {
        // Three squares is not yet a tetromino, five is no longer one, the 2x2 block is the shape
        // the rules refuse to name, and four squares in two pieces are not one tetromino either.
        val unnamed = listOf(
            listOf(0, 1, 2),
            listOf(0, 1, 2, 3, 4),
            listOf(0, 1, 4, 5),
            listOf(0, 1, 10, 11),
        )
        for (cells in unnamed) {
            val letters = oneRegion(*cells.toIntArray()).letters()
            assertTrue("$cells should be left unnamed", letters.all { it == null })
        }
    }

    // ---- crosses -------------------------------------------------------------------------------

    private val width = 4
    private val height = 6

    /**
     * Four six-square regions that interlock rather than stack, so a region's tetromino is a real
     * choice — a region of exactly four squares would have nothing to decide and would never
     * exercise a cross.
     */
    private val regions = listOf(
        0, 0, 0, 1,
        0, 0, 0, 1,
        2, 2, 1, 1,
        2, 2, 1, 1,
        2, 3, 3, 3,
        2, 3, 3, 3,
    )

    private fun board(shaded: Set<Int>) = LitsState(
        width = width,
        height = height,
        region = regions,
        shaded = List(width * height) { it in shaded },
        solution = List(width * height) { false },
    )

    /**
     * Every shading of this board that satisfies [Lits.isSolved], found by trying all of them.
     *
     * This is the ground truth the crosses are judged against: a cross claims no answer survives,
     * and the only way to check that claim without re-deriving the same reasoning under a different
     * name is to hold the answers in hand.
     */
    private val answers: List<Set<Int>> by lazy {
        val byRegion = regions.distinct().map { id ->
            choose(regions.indices.filter { regions[it] == id }, 4)
        }
        val out = mutableListOf<Set<Int>>()
        fun walk(depth: Int, taken: Set<Int>) {
            if (depth == byRegion.size) {
                val shaded = List(width * height) { it in taken }
                if (Lits.isSolved(width, height, regions, shaded)) out += taken
                return
            }
            for (quad in byRegion[depth]) walk(depth + 1, taken + quad)
        }
        walk(0, emptySet())
        out
    }

    private fun choose(cells: List<Int>, k: Int): List<List<Int>> {
        if (k == 0) return listOf(emptyList())
        if (cells.size < k) return emptyList()
        val (head, rest) = cells.first() to cells.drop(1)
        return choose(rest, k - 1).map { listOf(head) + it } + choose(rest, k)
    }

    @Test
    fun `the board this is checked against really does have answers`() {
        // Guards the test itself: a board with nothing to solve would pass every claim below
        // vacuously, because every square really would be impossible.
        assertTrue("the fixture board has no legal shading", answers.size > 4)
    }

    /**
     * The whole promise: a crossed square is one that no legal answer containing the current
     * shading shades. Checked over every shading of up to two squares and a spread of larger ones,
     * because the crosses are at their most dangerous early, when the player has the least of their
     * own reasoning to check them against.
     */
    @Test
    fun `no square is crossed off that an answer would have shaded`() {
        val random = Random(20260923)
        val partials = buildList {
            add(emptySet<Int>())
            for (a in 0 until width * height) {
                add(setOf(a))
                for (b in a + 1 until width * height) add(setOf(a, b))
            }
            repeat(600) {
                add((0 until width * height).shuffled(random).take(random.nextInt(3, 8)).toSet())
            }
        }

        for (partial in partials) {
            val crossed = board(partial).impossible()
            assertTrue(
                "$partial: a square already shaded was crossed off",
                crossed.none { it in partial },
            )
            val viable = answers.filter { it.containsAll(partial) }
            for (cell in crossed) {
                assertNull(
                    "$partial: crossing $cell off denies an answer",
                    viable.firstOrNull { cell in it },
                )
            }
        }
    }

    /** The two conditions a player would notice missing first. */
    @Test
    fun `the square that would complete a 2x2 is crossed off`() {
        // (1,1), (1,2) and (2,1) are shaded across two regions; (2,2) would close the block, and
        // it belongs to a third region that is otherwise free to take it.
        val crossed = board(setOf(5, 6, 9)).impossible()
        assertTrue("the fourth corner of a 2x2 should be crossed off", 10 in crossed)
    }

    @Test
    fun `a region holding its four squares crosses off the rest of itself`() {
        // Region 0's top-left L is complete, so its remaining two squares are spoken for.
        val crossed = board(setOf(0, 1, 2, 4)).impossible()
        assertTrue(
            "a settled region should cross off what it did not use",
            listOf(5, 6).all { it in crossed },
        )
    }

    /**
     * The condition that is worth having and easy to get wrong: a square whose region could only
     * finish as a letter that is already sitting against it.
     *
     * On its own board, because the fixture above has only one region that can hold an I and a
     * clash needs two. Left and right halves of a 4x4, each of which can run an I down a column.
     */
    @Test
    fun `a square is crossed off when every shape left would repeat a neighbour's letter`() {
        val halves = List(16) { if (it % 4 < 2) 0 else 1 }
        fun split(shaded: Set<Int>) = LitsState(
            width = 4,
            height = 4,
            region = halves,
            shaded = List(16) { it in shaded },
            solution = List(16) { false },
        )

        // The right half has settled as the I down column 2. The left half holds the top three
        // squares of column 1, so the only square that finishes it is (3,1) — and that would make
        // a second I, edge to edge with the first.
        val neighbourI = setOf(2, 6, 10, 14)
        assertEquals(Lits.Piece.I, split(neighbourI).letters()[2])
        assertTrue(
            "the square that would repeat the neighbouring I should be crossed off",
            13 in split(neighbourI + setOf(1, 5, 9)).impossible(),
        )

        // The same three squares against a right half that settled as an L instead: the I is now
        // allowed, so the square must not be crossed. This is what separates the letter rule from
        // a cross that merely fires whenever a neighbour is finished.
        val neighbourL = setOf(2, 3, 7, 11)
        assertEquals(Lits.Piece.L, split(neighbourL).letters()[2])
        assertTrue(
            "a different neighbouring letter leaves the square open",
            13 !in split(neighbourL + setOf(1, 5, 9)).impossible(),
        )
    }
}
