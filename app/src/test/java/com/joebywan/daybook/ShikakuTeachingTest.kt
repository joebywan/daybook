package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.ShikakuTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shikaku hint solver, checked against an enumerator written out again here on a different
 * principle from both the generator's (most-constrained clue first) and the teacher's (options per
 * clue, reach per square): it walks the grid in reading order and asks only which rectangle can
 * have the first uncovered square as its top-left corner.
 *
 * As for Kings, soundness on a unique board cannot tell reasoning from peeking, so the solver is also
 * walked over boards with several tilings, where a rectangle is only sound if every tiling still
 * standing has it.
 */
class ShikakuTeachingTest {

    // ---- independent rules --------------------------------------------------------------------

    /** Every tiling of the grid by clue rectangles, in reading order, up to [cap] of them. */
    private fun allTilings(w: Int, h: Int, clues: List<Int?>, cap: Int = 2000): List<Set<Block>> {
        val out = mutableListOf<Set<Block>>()
        val used = BooleanArray(w * h)
        val chosen = ArrayDeque<Block>()
        fun go() {
            if (out.size >= cap) return
            val first = used.indexOfFirst { !it }
            if (first < 0) {
                out += chosen.toSet()
                return
            }
            val r0 = first / w
            val c0 = first % w
            for (r1 in r0 until h) for (c1 in c0 until w) {
                var clue: Int? = null
                var count = 0
                var clash = false
                for (r in r0..r1) for (c in c0..c1) {
                    if (used[r * w + c]) clash = true
                    clues[r * w + c]?.let { clue = it; count++ }
                }
                if (clash || count != 1 || clue != (r1 - r0 + 1) * (c1 - c0 + 1)) continue
                val b = Block(r0, c0, r1, c1)
                for (r in r0..r1) for (c in c0..c1) used[r * w + c] = true
                chosen.addLast(b)
                go()
                chosen.removeLast()
                for (r in r0..r1) for (c in c0..c1) used[r * w + c] = false
            }
        }
        go()
        return out
    }

    private fun cells(w: Int, b: Block) = (b.r0..b.r1).flatMap { r -> (b.c0..b.c1).map { c -> r * w + c } }.toSet()

    // ---- soundness on real boards ---------------------------------------------------------------

    @Test
    fun `every step on a real board draws a rectangle of the answer`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "shikaku-teach")) {
                var s = Shikaku.generate(seed, difficulty) as ShikakuState
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 100)
                    val d = Shikaku.teach(s)
                    assertNotNull("$difficulty/$seed: no hint on an unsolved board", d)
                    d!!
                    assertFalse("$difficulty/$seed: a mistake on a board built from hints", d.mistake)
                    val step = ShikakuTeacher.teach(s)!!
                    val block = step.place
                    assertNotNull("$difficulty/$seed ${d.technique}: a step with no rectangle", block)
                    assertTrue("$difficulty/$seed ${d.technique}: $block is not in the answer", block in s.solution)
                    assertFalse("$difficulty/$seed ${d.technique}: already drawn", block in s.blocks)
                    assertEquals(cells(s.width, block!!), d.targets)
                    assertTrue("$difficulty/$seed ${d.technique}: no explanation", d.explanation.isNotBlank())
                    val next = d.apply(s) as ShikakuState
                    assertTrue("$difficulty/$seed ${d.technique}: applying it did not reach it", d.isReached(next))
                    assertFalse("$difficulty/$seed ${d.technique}: reached before it was made", d.isReached(s))
                    s = next
                }
                assertEquals(s.solution.toSet(), s.blocks.toSet())
            }
        }
    }

    @Test
    fun `hints stay sound from boards a player made, not just from the solver's own path`() {
        val rng = java.util.Random(7)
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "shikaku-teach-played")) {
                val fresh = Shikaku.generate(seed, difficulty) as ShikakuState
                val s = fresh.copy(blocks = fresh.solution.filter { rng.nextInt(3) == 0 })
                if (s.solved) continue
                val step = ShikakuTeacher.teach(s)!!
                assertTrue("$difficulty/$seed: a correct board was called a mistake", step.technique != ShikakuTeacher.MISTAKE)
                assertTrue("$difficulty/$seed ${step.technique}: not in the answer", step.place in s.solution)
            }
        }
    }

    // ---- soundness from sight alone -------------------------------------------------------------

    /** Halves a block at random until every piece is at most [maxArea], never leaving a piece of 1. */
    private fun slice(rng: java.util.Random, b: Block, maxArea: Int): List<Block> {
        val rows = b.r1 - b.r0 + 1
        val cols = b.c1 - b.c0 + 1
        if (b.area <= maxArea && rng.nextInt(100) < 45) return listOf(b)
        val canRows = if (cols >= 2) rows >= 2 else rows >= 4
        val canCols = if (rows >= 2) cols >= 2 else cols >= 4
        if (!canRows && !canCols) return listOf(b)
        val byRows = if (!canCols) true else if (!canRows) false else rng.nextBoolean()
        return if (byRows) {
            val inset = if (cols >= 2) 0 else 1
            val cut = b.r0 + inset + rng.nextInt(rows - 1 - 2 * inset)
            slice(rng, b.copy(r1 = cut), maxArea) + slice(rng, b.copy(r0 = cut + 1), maxArea)
        } else {
            val inset = if (rows >= 2) 0 else 1
            val cut = b.c0 + inset + rng.nextInt(cols - 1 - 2 * inset)
            slice(rng, b.copy(c1 = cut), maxArea) + slice(rng, b.copy(c0 = cut + 1), maxArea)
        }
    }

    @Test
    fun `on boards with several tilings, a step holds for every tiling still possible`() {
        val rng = java.util.Random(11)
        var boards = 0
        var steps = 0
        val seen = mutableMapOf<String, Int>()
        while (boards < 300) {
            val w = 4 + rng.nextInt(4)
            val h = 4 + rng.nextInt(4)
            val cut = slice(rng, Block(0, 0, h - 1, w - 1), 4 + rng.nextInt(6))
            val clues = MutableList<Int?>(w * h) { null }
            cut.forEach { clues[(it.r0 + rng.nextInt(it.r1 - it.r0 + 1)) * w + it.c0 + rng.nextInt(it.c1 - it.c0 + 1)] = it.area }
            val tilings = allTilings(w, h, clues)
            if (tilings.size < 2 || tilings.size >= 2000) continue
            boards++
            var blocks = listOf<Block>()
            while (true) {
                val alive = tilings.filter { it.containsAll(blocks) }
                assertTrue("a sound walk left no tiling standing", alive.isNotEmpty())
                // Deliberately no answer passed: this is the entry point that cannot see one.
                val step = ShikakuTeacher.deduce(w, h, clues, blocks) ?: break
                steps++
                seen[step.technique] = (seen[step.technique] ?: 0) + 1
                val block = step.place!!
                assertTrue(
                    "${step.technique} drew $block, which some remaining tiling does not have: ${step.explanation}",
                    alive.all { block in it },
                )
                blocks = blocks + block
            }
        }
        assertTrue("the walk barely stepped, so it proved little: $steps", steps > boards)
        println("several-tiling walk: $boards boards, $steps steps, $seen")
    }

    // ---- coverage -----------------------------------------------------------------------------

    /**
     * Not only a correctness check but a measurement, printed and written to
     * `app/build/reports/shikaku-teaching-coverage.txt`: how often each technique is what a player
     * needs next, and how often the what-if and the fallback are reached, per tier.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 500
        val report = StringBuilder()
        report.appendLine("Shikaku teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = ShikakuTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = ShikakuTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var longest = ""
            var deepBoards = 0
            var pastFits = 0
            for (seed in seeds(perTier, difficulty, "shikaku-coverage")) {
                var s = Shikaku.generate(seed, difficulty) as ShikakuState
                val used = mutableSetOf<String>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 100)
                    val d = Shikaku.teach(s)!!
                    if (d.explanation.length > longest.length) longest = d.explanation
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as ShikakuState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
                if (ShikakuTeacher.COMMON_CELLS in used || ShikakuTeacher.WHAT_IF in used) deepBoards++
                if (used.any { it != ShikakuTeacher.ONLY_FITS }) pastFits++
            }
            report.appendLine()
            report.appendLine("${difficulty.name} — $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board")
            for (t in ShikakuTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-14s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine("  longest explanation, ${longest.length} characters: $longest")
            // The generator's gate, seen from the walk: never the fallback, and each tier's demand.
            val fallbackBoards = boardCounts.getValue(ShikakuTeacher.FALLBACK)
            assertEquals("$difficulty: the fallback is reached on $fallbackBoards/$perTier boards", 0, fallbackBoards)
            when (difficulty) {
                Difficulty.STANDARD -> assertEquals("Standard boards needing common cells or a what-if", 0, deepBoards)
                Difficulty.HARD -> assertEquals("Hard boards needing only-reaches or deeper", perTier, pastFits)
                Difficulty.EXPERT -> assertEquals("Expert boards needing common cells or a what-if", perTier, deepBoards)
            }
        }
        println(report)
        writeReport("shikaku-teaching-coverage.txt", report.toString())
    }

    /**
     * The panel shows four lines and ellipsizes the rest; about 200 characters fit. Every
     * explanation any path can produce, mistakes included, is checked against that, on boards walked
     * by hints and on boards where a wrong rectangle has been drawn.
     */
    @Test
    fun `every explanation fits the hint panel`() {
        val limit = 200
        var checked = 0
        fun check(label: String, text: String) {
            checked++
            assertTrue("$label: ${text.length} characters: $text", text.length <= limit)
        }
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(100, difficulty, "shikaku-length")) {
                val fresh = Shikaku.generate(seed, difficulty) as ShikakuState
                var s = fresh
                while (!s.solved) {
                    val d = Shikaku.teach(s)!!
                    check("$difficulty/$seed ${d.technique}", d.explanation)
                    check("$difficulty/$seed ${d.technique} nudge", d.nudge)
                    s = d.apply(s) as ShikakuState
                }
                for (b in wrongRectangles(fresh).take(6)) {
                    val d = Shikaku.teach(fresh.place(b))!!
                    check("$difficulty/$seed mistake", d.explanation)
                }
            }
        }
        for (frame in Shikaku.tutorial) check("walkthrough", frame.caption)
        assertTrue(checked > 1000)
    }

    // ---- mistakes -------------------------------------------------------------------------------

    /** Legal-to-draw rectangles (one number inside) that the answer does not have. */
    private fun wrongRectangles(s: ShikakuState): List<Block> = buildList {
        for (i in s.clues.indices) {
            val n = s.clues[i] ?: continue
            val r = i / s.width
            val c = i % s.width
            for (hh in 1..n) {
                if (n % hh != 0) continue
                val ww = n / hh
                for (r0 in r - hh + 1..r) for (c0 in c - ww + 1..c) {
                    val b = Block(r0, c0, r0 + hh - 1, c0 + ww - 1)
                    if (r0 < 0 || c0 < 0 || b.r1 >= s.height || b.c1 >= s.width) continue
                    if (cells(s.width, b).count { s.clues[it] != null } != 1) continue
                    if (b !in s.solution) add(b)
                }
            }
        }
    }

    @Test
    fun `a rectangle the wrong size is named as the reason`() {
        val s = Shikaku.tutorial[1].state as ShikakuState
        // The top 6 drawn as two by two: four squares on a 6.
        val d = Shikaku.teach(s.place(Block(0, 2, 1, 3)))!!
        assertTrue(d.mistake)
        assertTrue("should give the area: ${d.explanation}", "four squares" in d.explanation && "6" in d.explanation)
    }

    @Test
    fun `a wrong rectangle on a real board outranks every step, and tapping it away clears it`() {
        var checked = 0
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(20, difficulty, "shikaku-mistake")) {
                val fresh = Shikaku.generate(seed, difficulty) as ShikakuState
                val wrong = wrongRectangles(fresh).firstOrNull() ?: continue
                val s = fresh.place(wrong)
                val d = Shikaku.teach(s)!!
                assertTrue("$difficulty/$seed: wrong rectangle not flagged", d.mistake)
                assertEquals(cells(fresh.width, wrong), d.targets)
                assertTrue("$difficulty/$seed: should say how to remove it", "Tap it" in d.explanation)
                assertFalse(d.isReached(s))
                // The tap the board makes: the rectangle under the finger comes off.
                val tapped = s.copy(blocks = s.blocks - s.blockAt(wrong.r0, wrong.c0)!!, moves = s.moves + 1)
                assertTrue(d.isReached(tapped))
                assertTrue(d.isReached(d.apply(s)))
                checked++
            }
        }
        assertTrue("too few boards had a wrong rectangle to test: $checked", checked > 30)
    }

    @Test
    fun `a rectangle of the answer is never called a mistake`() {
        val s = Shikaku.tutorial[1].state as ShikakuState
        assertFalse(Shikaku.teach(s.place(Shikaku.TUTORIAL_THREE))!!.mistake)
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one tiling, and it is the stored one`() {
        assertEquals(listOf(Shikaku.TUTORIAL_SOLUTION.toSet()), allTilings(5, 5, Shikaku.TUTORIAL_CLUES))
    }

    @Test
    fun `each walkthrough move is the one the hints would give there`() {
        val frames = Shikaku.tutorial
        val top = Shikaku.TUTORIAL_TOP_SIX
        // The board each move is made on, and the rectangle the hints would draw there.
        val expect = listOf(
            frames[1].state to (ShikakuTeacher.ONLY_FITS to top),
            frames[3].state to (ShikakuTeacher.ONLY_REACHES to Shikaku.TUTORIAL_FOUR),
            (frames[3].state as ShikakuState).place(Shikaku.TUTORIAL_FOUR) to (ShikakuTeacher.ONLY_REACHES to Shikaku.TUTORIAL_THREE),
            frames[4].state to (ShikakuTeacher.COMMON_CELLS to Shikaku.TUTORIAL_LOW_SIX),
        )
        for ((k, e) in expect.withIndex()) {
            val step = ShikakuTeacher.teach(e.first as ShikakuState)!!
            assertEquals("move ${k + 1}", e.second.first, step.technique)
            assertEquals("move ${k + 1}", e.second.second, step.place)
        }
        // The wrong 4 the player draws is a mistake the hints would name for the reason the caption gives.
        val m = ShikakuTeacher.teach((frames[2].state as ShikakuState).place(Shikaku.TUTORIAL_WRONG_FOUR))!!
        assertEquals(ShikakuTeacher.MISTAKE, m.technique)
        assertEquals(Shikaku.TUTORIAL_WRONG_FOUR, m.remove)
        assertTrue("should cite the corner: ${m.explanation}", 0 in m.cited && "no number can reach" in m.explanation)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Shikaku.tutorial
        assertEquals(6, frames.size)
        fun board(i: Int) = frames[i].state as ShikakuState
        // The tap the board makes: the rectangle under the finger comes off, one move.
        fun ShikakuState.tap(r: Int, c: Int) = copy(blocks = blocks - blockAt(r, c)!!, moves = moves + 1)

        assertNull("frame 1 should be Next-only", frames[0].judge)
        assertTrue(board(0).solved)

        // A drag: the board places the rectangle between the two corners.
        val draw = frames[1].accepts!!
        assertTrue(draw(board(1).place(Shikaku.TUTORIAL_TOP_SIX)))
        assertFalse("a rectangle elsewhere", draw(board(1).place(Shikaku.TUTORIAL_THREE)))
        assertEquals(board(2).blocks, board(1).place(Shikaku.TUTORIAL_TOP_SIX).blocks)

        // The player draws the wrong 4 (the glow turns red), then taps it away: one frame.
        val wrong = frames[2].steps!!
        val s2 = board(2)
        assertEquals(TutorialStep.REJECT, wrong(s2))
        assertEquals(TutorialStep.REJECT, wrong(s2.place(Shikaku.TUTORIAL_FOUR)))
        val drawn = s2.place(Shikaku.TUTORIAL_WRONG_FOUR)
        assertEquals(TutorialStep.PROGRESS, wrong(drawn))
        assertFalse(frames[2].glow!!(s2).warning)
        assertTrue(frames[2].glow!!(drawn).warning)
        assertEquals(TutorialStep.REJECT, wrong(drawn.tap(0, 3)))
        val removed = drawn.tap(1, 1)
        assertEquals(TutorialStep.DONE, wrong(removed))
        assertEquals(board(3).blocks, removed.blocks)

        // The 4, then the 3, in one frame: the glow moves from the corner to the square below it.
        val pair = frames[3].steps!!
        val s3 = board(3)
        assertEquals(setOf(0), frames[3].glow!!(s3).strong)
        assertEquals(TutorialStep.REJECT, pair(s3.place(Shikaku.TUTORIAL_THREE)))
        val four = s3.place(Shikaku.TUTORIAL_FOUR)
        assertEquals(TutorialStep.PROGRESS, pair(four))
        assertEquals(setOf(10), frames[3].glow!!(four).strong)
        assertEquals(TutorialStep.REJECT, pair(four.place(Shikaku.TUTORIAL_MID_SIX)))
        val both = four.place(Shikaku.TUTORIAL_THREE)
        assertEquals(TutorialStep.DONE, pair(both))
        assertEquals(board(4).blocks.toSet(), both.blocks.toSet())

        val low = frames[4].accepts!!
        assertTrue(low(board(4).place(Shikaku.TUTORIAL_LOW_SIX)))
        assertFalse("the last 6 instead", low(board(4).place(Shikaku.TUTORIAL_MID_SIX)))
        assertEquals(board(5).blocks.toSet(), board(4).place(Shikaku.TUTORIAL_LOW_SIX).blocks.toSet())

        assertTrue(frames[5].freePlay)
        assertTrue(board(5).place(Shikaku.TUTORIAL_MID_SIX).solved)
    }
}
