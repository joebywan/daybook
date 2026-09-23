package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two promises LITS broke, both of which cost a player a finished board.
 *
 * The win condition used to be `shaded == solution`, so a second legal answer was reported as
 * wrong; and the uniqueness check used to abandon its search at a node cap and return what it had,
 * so the caller's `solutions.size == 1` could not tell a proof from a shrug and ambiguous boards
 * shipped. Both are pinned here.
 *
 * The solver below is deliberately written out again rather than reusing the generator's — including
 * its own reading of which letter a shape is — so a board that only the generator's own idea of the
 * rules considers unique would still fail this test. It carries no node budget at all: it either
 * finishes the tree or takes as long as it takes.
 */
class LitsRulesTest {

    private fun seeds(difficulty: Difficulty, count: Int) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 7, 1).plusDays(it.toLong()), "lits", difficulty)
    }

    // ---- the win condition is the rules -------------------------------------------------------

    @Test
    fun `the stored solution satisfies the rules on every generated board`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(difficulty, 6)) {
                val state = Lits.generate(seed, difficulty) as LitsState
                assertTrue(
                    "lits/${difficulty.name}/$seed ships a solution that breaks its own rules",
                    Lits.isSolved(state.width, state.height, state.region, state.solution),
                )
                assertTrue(
                    "lits/${difficulty.name}/$seed is not solved by its own solution",
                    state.copy(shaded = state.solution).solved,
                )
            }
        }
    }

    @Test
    fun `a generated board does not start solved`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(difficulty, 6)) {
                val state = Lits.generate(seed, difficulty) as LitsState
                assertFalse("lits/${difficulty.name}/$seed starts solved", state.solved)
            }
        }
    }

    @Test
    fun `a legal board is accepted whether or not it is the stored answer`() {
        assertTrue(Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9, 6, 7, 11, 15)))
    }

    @Test
    fun `a fully shaded two by two is rejected`() {
        // Every other rule holds: an S and an I, four squares each, joined up and different
        // letters. Only the block of four at 1-2-5-6 is wrong.
        assertFalse(Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 1, 4, 5, 8, 2, 6, 10, 14)))
    }

    @Test
    fun `a region with the wrong number of shaded squares is rejected`() {
        assertFalse(
            "three squares in a region is not a tetromino",
            Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9, 6, 7, 11)),
        )
        assertFalse(
            "five squares in a region is not a tetromino either",
            Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9, 3, 6, 7, 11, 15)),
        )
        assertFalse(
            "a region left blank is not merely unfinished, it is wrong",
            Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9)),
        )
    }

    @Test
    fun `shading that falls into two separate pieces is accepted`() {
        // Two legal I tetrominoes, four squares each, no 2x2, and too far apart to count as one
        // same-letter contact. They never touch, which this version of LITS does not require.
        assertTrue(Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 8, 12, 3, 7, 11, 15)))
    }

    @Test
    fun `the disconnected answer a player was refused on 23 Sept 2026 Expert is accepted`() {
        // Transcribed from the player's screenshot. The T in the middle (rows 3 to 5) touches no
        // other shading; every region still holds a legal tetromino. The regions are written out
        // rather than regenerated from the seed, because capping region size changed the board
        // that date now produces.
        val region = listOf(
            2, 1, 1, 0, 0, 3, 3, 3,
            2, 2, 1, 1, 0, 0, 3, 3,
            2, 5, 5, 1, 0, 9, 3, 3,
            2, 5, 5, 5, 0, 9, 3, 4,
            8, 8, 8, 8, 9, 9, 4, 4,
            8, 8, 7, 7, 9, 9, 9, 4,
            7, 8, 7, 7, 7, 6, 6, 4,
            7, 7, 7, 6, 6, 6, 6, 4,
        )
        val state = LitsState(8, 8, region, List(64) { false }, List(64) { false })
        val picture = listOf(
            "I S S . T . . S",
            "I . S S T T S S",
            "I . T . T . S .",
            "I T T T . T . I",
            "S . . . T T . I",
            "S S . T . T . I",
            ". S T T T . L I",
            ". . . . L L L .",
        )
        val shaded = picture.flatMap { row -> row.split(" ").map { it != "." } }
        assertEquals(state.width * state.height, shaded.size)
        val played = state.copy(shaded = shaded)
        assertEquals(
            "the screenshot's letters should be what the board shows",
            picture.flatMap { row -> row.split(" ").map { if (it == ".") null else it } },
            played.letters().map { it?.name },
        )
        assertTrue(played.solved)
    }

    @Test
    fun `two touching tetrominoes of the same letter are rejected`() {
        // Two L tetrominoes — one a rotation of the other, which the rule counts as the same
        // letter — meeting edge to edge at squares 0 and 1.
        assertFalse(Lits.isSolved(5, 4, STAIRCASE, shade(20, 0, 5, 10, 11, 1, 2, 3, 8)))
    }

    // ---- region size ---------------------------------------------------------------------------

    @Test
    fun `generated regions hold at most seven squares`() {
        // Uncapped, about one region in six came out at eight squares or more, up to fourteen:
        // nearly room for two tetrominoes, most of it squares that only ever get crossed off.
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(difficulty, 10)) {
                val state = Lits.generate(seed, difficulty) as LitsState
                val largest = state.region.groupingBy { it }.eachCount().values.max()
                assertTrue("lits/${difficulty.name}/$seed has a $largest-square region", largest <= 7)
            }
        }
    }

    // ---- dragging --------------------------------------------------------------------------------

    @Test
    fun `a drag sets every square it crosses the same way, in one state`() {
        val blank = LitsState(4, 4, TWO_COLUMNS, List(16) { false }, List(16) { false })
        val start = blank.toggle(5)
        val dragged = start.paint(listOf(4, 5, 6), on = true)
        assertEquals(shade(16, 4, 5, 6), dragged.shaded)
        assertEquals("one move per square that changed", start.moves + 2, dragged.moves)

        val cleared = dragged.paint(listOf(5, 6, 7), on = false)
        assertEquals(shade(16, 4), cleared.shaded)
        assertEquals(dragged.moves + 2, cleared.moves)
    }

    @Test
    fun `a drag that changes nothing is not a new state`() {
        val board = LitsState(4, 4, TWO_COLUMNS, shade(16, 0, 1), List(16) { false })
        assertTrue(board.paint(listOf(0, 1), on = true) === board)
        assertTrue(board.paint(listOf(2, 3), on = false) === board)
    }

    // ---- uniqueness is proven, never assumed --------------------------------------------------

    @Test
    fun `every generated board has exactly one solution`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(difficulty, 3)) {
                val state = Lits.generate(seed, difficulty) as LitsState
                assertEquals(
                    "lits/${difficulty.name}/$seed does not have exactly one solution",
                    1,
                    countSolutions(state),
                )
            }
        }
    }

    /**
     * Every legal *connected* shading of [state]'s regions, counted to a stop of [cap].
     *
     * Connected because that is what the generator proves unique: it still insists on one area
     * when choosing boards, even though the win condition no longer does.
     *
     * No node budget on purpose. The bug this guards against was a search that gave up quietly and
     * let the caller read the result as a proof of uniqueness, so a count from here is either
     * complete or the test never returns.
     */
    private fun countSolutions(state: LitsState, cap: Int = 2): Int {
        val w = state.width
        val h = state.height
        val n = w * h
        // Fewest choices first: the order regions are tried in cannot change the count, only how
        // soon a dead branch is abandoned.
        val options = state.region.distinct().sorted().map { id ->
            piecesIn(state.region.indices.filter { state.region[it] == id }, w, h)
        }.sortedBy { it.size }
        val shaded = BooleanArray(n)
        val letter = arrayOfNulls<Char>(n)
        var found = 0

        fun blockOfFour(): Boolean {
            for (r in 0 until h - 1) for (c in 0 until w - 1) {
                val i = r * w + c
                if (shaded[i] && shaded[i + 1] && shaded[i + w] && shaded[i + w + 1]) return true
            }
            return false
        }

        fun twinContact(quad: List<Int>, mark: Char): Boolean = quad.any { cell ->
            around(cell, w, h).any { it !in quad && shaded[it] && letter[it] == mark }
        }

        // Shading already laid down must still be able to reach itself through squares no region
        // has been given a piece in yet, or the branch is dead however it is finished.
        fun stillOneArea(free: BooleanArray): Boolean {
            val start = (0 until n).firstOrNull { shaded[it] } ?: return true
            val seen = BooleanArray(n)
            val queue = ArrayDeque<Int>()
            queue += start
            seen[start] = true
            var reached = 1
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                for (next in around(cell, w, h)) {
                    if (seen[next] || !(shaded[next] || free[next])) continue
                    seen[next] = true
                    if (shaded[next]) reached++
                    queue += next
                }
            }
            return reached == (0 until n).count { shaded[it] }
        }

        val undecided = Array(options.size + 1) { BooleanArray(n) }
        for (depth in options.indices.reversed()) {
            undecided[depth + 1].copyInto(undecided[depth])
            for (cell in 0 until n) {
                if (options[depth].any { cell in it.first }) undecided[depth][cell] = true
            }
        }

        fun walk(depth: Int) {
            if (found >= cap) return
            if (depth == options.size) {
                if (Lits.isSolved(w, h, state.region, shaded.toList())) found++
                return
            }
            for ((quad, mark) in options[depth]) {
                quad.forEach { shaded[it] = true; letter[it] = mark }
                if (!blockOfFour() && !twinContact(quad, mark) && stillOneArea(undecided[depth + 1])) {
                    walk(depth + 1)
                }
                quad.forEach { shaded[it] = false; letter[it] = null }
                if (found >= cap) return
            }
        }
        walk(0)
        return found
    }

    /** Every four-square L, I, T or S inside [cells], each tagged with its letter. */
    private fun piecesIn(cells: List<Int>, w: Int, h: Int): List<Pair<List<Int>, Char>> {
        val out = mutableListOf<Pair<List<Int>, Char>>()
        for (a in cells.indices) for (b in a + 1 until cells.size)
            for (c in b + 1 until cells.size) for (d in c + 1 until cells.size) {
                val quad = listOf(cells[a], cells[b], cells[c], cells[d])
                if (!joined(quad, w, h)) continue
                out += (quad to (letterOf(quad, w) ?: continue))
            }
        return out
    }

    /**
     * The letter of a four-square shape, or null for the 2x2 block.
     *
     * Read off the offsets rather than the bounding box, so this agrees with the generator only by
     * agreeing with the rules. Each of the four rotations of the shape is compared against the
     * canonical squares of each letter, which is what makes a rotation or a reflection the same
     * letter.
     */
    private fun letterOf(cells: List<Int>, w: Int): Char? {
        var shape = cells.map { it / w to it % w }
        repeat(4) {
            for ((mark, forms) in LETTERS) if (normalise(shape) in forms) return mark
            shape = shape.map { (r, c) -> c to -r }   // quarter turn
        }
        return null
    }

    private fun normalise(shape: List<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val r0 = shape.minOf { it.first }
        val c0 = shape.minOf { it.second }
        return shape.map { (r, c) -> r - r0 to c - c0 }.toSet()
    }

    private fun joined(cells: List<Int>, w: Int, h: Int): Boolean {
        val rest = cells.toMutableSet()
        val queue = ArrayDeque<Int>()
        queue += cells.first()
        rest -= cells.first()
        var seen = 1
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (next in around(cell, w, h)) if (rest.remove(next)) { seen++; queue += next }
        }
        return seen == cells.size
    }

    private fun around(cell: Int, w: Int, h: Int): List<Int> {
        val r = cell / w
        val c = cell % w
        return buildList {
            if (r > 0) add(cell - w)
            if (r < h - 1) add(cell + w)
            if (c > 0) add(cell - 1)
            if (c < w - 1) add(cell + 1)
        }
    }

    private fun shade(size: Int, vararg cells: Int): List<Boolean> {
        val on = cells.toSet()
        return List(size) { it in on }
    }

    private companion object {
        /** A 4x4 grid split down the middle, two regions of eight squares each. */
        val TWO_COLUMNS = listOf(
            0, 0, 1, 1,
            0, 0, 1, 1,
            0, 0, 1, 1,
            0, 0, 1, 1,
        )

        /** A 5x4 grid split along a diagonal, so both halves can hold an L. */
        val STAIRCASE = listOf(
            0, 1, 1, 1, 1,
            0, 0, 1, 1, 1,
            0, 0, 0, 1, 1,
            0, 0, 0, 1, 1,
        )

        /** Canonical squares of each letter, up to the rotations [letterOf] applies. */
        val LETTERS = mapOf(
            'I' to setOf(setOf(0 to 0, 0 to 1, 0 to 2, 0 to 3)),
            'L' to setOf(
                setOf(0 to 0, 1 to 0, 2 to 0, 2 to 1),
                setOf(0 to 1, 1 to 1, 2 to 1, 2 to 0),
            ),
            'T' to setOf(setOf(0 to 0, 0 to 1, 0 to 2, 1 to 1)),
            'S' to setOf(
                setOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
                setOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
            ),
        )
    }
}
