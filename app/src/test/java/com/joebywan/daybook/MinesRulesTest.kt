package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesLogic
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.MinesTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinesRulesTest {

    /**
     * ```
     * . . . .
     * . * . .
     * . . . .
     * . . . *
     * ```
     * Opened from square 3 (top right).
     */
    private val mines = "0000" + "0100" + "0000" + "0001"
    private fun board(marks: String = MinesLogic.openingMarks(4, 4, mines, 3)) = MinesState(4, 4, mines, 3, marks)

    /** Naive neighbour count, written here so the board's number is checked by something else. */
    private fun count(s: MinesState, i: Int): Int {
        var k = 0
        for (j in 0 until s.width * s.height) {
            if (j == i) continue
            if (kotlin.math.abs(j / s.width - i / s.width) <= 1 && kotlin.math.abs(j % s.width - i % s.width) <= 1 && s.mines[j] == '1') k++
        }
        return k
    }

    @Test fun `numbers count the mines touching a square, corners included`() {
        val s = board()
        for (i in 0 until 16) assertEquals("square $i", count(s, i), s.number(i))
    }

    @Test fun `the opening spreads from every 0 it reaches and stops at numbers`() {
        // 1 1 1 0
        // 1 * 1 0
        // 1 1 2 1
        // 0 0 1 *
        // From the top-right 0: it and the 0 below it open, with every square around them. The 0s bottom left are not
        // reached: the spread only goes through 0s.
        assertEquals("..oo" + "..oo" + "..oo" + "....", board().marks)
        assertEquals("..oo" + "..oo" + "oooo" + "ooo.", MinesLogic.open(4, 4, mines, board().marks, 12))
    }

    @Test fun `a flag is a note that protects its square and is passed by the spread`() {
        val blank = board(".".repeat(16))
        val flagged = blank.flag(7)!!
        assertTrue(flagged.isFlag(7))
        assertEquals(1, flagged.moves)
        assertNull("a flagged square is never revealed", flagged.reveal(7))
        assertEquals(3, flagged.lives)
        val opened = flagged.reveal(3)!!
        assertTrue("the spread passes the flag by", opened.isFlag(7) && !opened.isOpen(7))
        assertFalse(opened.flag(7)!!.isFlag(7))
        assertNull("an open square takes no flag", opened.flag(3))
        // A wrong flag is allowed and costs nothing.
        assertEquals(3, blank.flag(0)!!.lives)
        assertEquals(blank.total - 1, blank.flag(0)!!.minesLeft)
    }

    @Test fun `a mine costs a life and stays shown, three lose the board, and Try again restores it`() {
        var s = MinesState(4, 4, "1110" + "0000" + "0000" + "0000", 15, MinesLogic.openingMarks(4, 4, "1110" + "0000" + "0000" + "0000", 15))
        s = s.reveal(0)!!
        assertEquals(listOf(0), s.bumps)
        assertEquals(2, s.lives)
        assertNull("a mine hit is not revealed twice", s.reveal(0))
        assertNull("nor flagged", s.flag(0))
        assertEquals(s.total - 1, s.minesLeft)
        s = s.reveal(1)!!.reveal(2)!!
        assertTrue(s.failed)
        assertFalse(s.solved)
        assertNull("nothing happens on a lost board", s.reveal(3))
        assertNull(s.flag(3))
        val again = s.retry()
        assertEquals(3, again.lives)
        assertEquals(MinesLogic.openingMarks(4, 4, s.mines, 15), again.marks)
        assertFalse(again.failed)
    }

    @Test fun `solved is every safe square revealed, whatever the flags`() {
        var s = board()
        val safe = (0 until 16).filter { !s.isMine(it) && !s.isOpen(it) }
        // Square 0 (a 1) touches no 0, so nothing but its own reveal opens it.
        for (i in safe - 0) s = s.reveal(i) ?: s
        assertFalse(s.solved)
        s = s.flag(5)!!.reveal(0)!!
        assertTrue("solved with a flag on one mine and none on the other", s.solved)
        assertNull("nothing happens on a won board", s.flag(15))
        // A board solved with a mine hit along the way still counts: the mine is not a safe square.
        var t = board().reveal(5)!!
        for (i in safe) t = t.reveal(i) ?: t
        assertTrue(t.solved)
    }

    // ---- generated boards ----------------------------------------------------------------------------------------

    /** Every board the year makes: its opening clear, within the cap, and the ladder clears it at the tier's depth. */
    @Test fun `a year of daily boards - the ladder clears each at its tier's depth, and the tiers get harder`() {
        val report = StringBuilder()
        val meanMines = DoubleArray(3)
        for (t in Difficulty.entries) {
            val spec = MinesLogic.specFor(t)
            val draws = ArrayList<Int>()
            val opening = ArrayList<Int>()
            val times = ArrayList<Double>()
            val used = HashMap<String, Int>()
            for (day in 0 until 365) {
                val seed = daySeed(day, Mines.id, t)
                val t0 = System.nanoTime()
                var a = 0
                var s: MinesState? = null
                while (s == null && a < MinesLogic.ATTEMPTS) s = MinesLogic.attempt(seed, t, a++)
                requireNotNull(s) { "day $day $t: no draw of ${MinesLogic.ATTEMPTS} accepted" }
                times += (System.nanoTime() - t0) / 1e6
                assertEquals(s, Mines.generate(seed, t))
                draws += a
                assertEquals(spec.width, s.width)
                assertEquals(spec.height, s.height)
                assertEquals(spec.mines, s.total)
                for (j in MinesLogic.neighbours(s.width, s.height, s.opening).toList() + s.opening) assertFalse("$t $day: mine in the opening", s.isMine(j))
                val share = s.marks.count { it == 'o' } * 1000 / s.mines.count { it == '0' }
                assertTrue("$t $day opening $share", share <= MinesLogic.MAX_OPENING)
                opening += share
                val walk = MinesLogic.walk(s.width, s.height, s.mines, s.opening)!!
                assertEquals("$t $day", spec.needs, walk.depth)
                walk.used.forEach { used[it] = (used[it] ?: 0) + 1 }
            }
            meanMines[t.ordinal] = spec.mines.toDouble() / (spec.width * spec.height)
            draws.sort(); opening.sort(); times.sort()
            report.appendLine(
                "$t ${spec.width}x${spec.height}, ${spec.mines} mines: draws mean %.1f max %d; opening median %d‰ max %d‰; ms median %.2f p95 %.2f max %.2f; boards using %s".format(
                    draws.average(), draws.last(), opening[182], opening.last(), times[182], times[346], times.last(),
                    MinesTeacher.TECHNIQUES.filter { it in used }.joinToString { "$it ${used[it]}" },
                ),
            )
        }
        writeReport("mines-generation.txt", report.toString())
        print(report)
        assertTrue("density rises with the tier", meanMines[0] < meanMines[1] && meanMines[1] < meanMines[2])
    }

    @Test fun `the last resort is accepted at its tier`() {
        for (t in Difficulty.entries) assertTrue(t.name, MinesLogic.accepts(MinesLogic.lastResort(t), MinesLogic.specFor(t)))
    }

    /** Each step the ladder takes from the opening holds in every layout the oracle finds, so every square is forced. */
    @Test fun `every ladder step is forced in every layout the oracle allows`() {
        for (t in Difficulty.entries) for (seed in 1L..(if (t == Difficulty.EXPERT) 8L else 15L)) {
            var s = Mines.generate(seed, t) as MinesState
            val n = s.width * s.height
            var steps = 0
            while (!s.solved) {
                val num = IntArray(n) { if (s.isOpen(it)) s.number(it) else -1 }
                val hit = BooleanArray(n)
                val st = IntArray(n) { if (s.isOpen(it)) MinesLogic.SAFE else MinesLogic.UNKNOWN }
                val f = MinesLogic.nextFact(s.width, s.height, num, st, s.total)!!
                val o = MinesOracle.solve(s.width, s.height, num, hit, s.total)
                assertTrue(o.layouts > 0)
                for (i in f.safe) assertFalse("$t $seed step $steps: $i could be a mine (${f.technique})", o.canMine[i])
                for (i in f.mines) assertFalse("$t $seed step $steps: $i could be safe (${f.technique})", o.canSafe[i])
                // Flags are not knowledge to the ladder, so the walk here moves only by reveals: mines are left unflagged.
                if (f.safe.isEmpty()) {
                    // A mines-only step: carry it as known and look again, as the generator does.
                    var known = st
                    var g: com.joebywan.daybook.puzzles.MinesLogic.Fact = f
                    while (g.safe.isEmpty()) {
                        known = known.copyOf().also { k -> g.mines.forEach { k[it] = MinesLogic.MINE } }
                        g = MinesLogic.nextFact(s.width, s.height, num, known, s.total)!!
                        for (i in g.safe) assertFalse("$t $seed: $i could be a mine (${g.technique})", o.canMine[i])
                        for (i in g.mines) assertFalse("$t $seed: $i could be safe (${g.technique})", o.canSafe[i])
                    }
                    for (i in g.safe) s = s.reveal(i) ?: s
                } else {
                    for (i in f.safe) s = s.reveal(i) ?: s
                }
                steps++
            }
        }
    }
}
