package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CageTeachingTest {

    private fun board(day: Int, d: Difficulty) =
        CageLogic.generateVerified(daySeed(day, Cages.id, d), d)
            ?: error("no proved board for day $day ${d.name}")

    /** Applies each hint until solved; every step must agree with the (unique) answer and change something. */
    private fun walk(s: CageState, onStep: (String) -> Unit = {}): CageState = hintWalk(s, 90) { now ->
        val d = Cages.teach(now) ?: error("no step on an unsolved board")
        assertFalse("unexpected mistake: ${d.explanation}", d.mistake)
        onStep(d.technique)
        val next = d.apply(now) as CageState
        for (t in d.targets) assertEquals("${d.technique} is wrong at $t", s.grid.solution[t], next.cells[t])
        assertNotEquals("a step must change something", now.cells, next.cells)
        assertTrue(d.isReached(next))
        next
    }

    @Test
    fun `coverage and fallback rate per tier`() {
        val report = StringBuilder()
        for (d in Difficulty.entries) {
            val counts = linkedMapOf<String, Int>().apply { CageTeacher.TECHNIQUES.forEach { put(it, 0) } }
            var fellBack = 0
            var steps = 0
            val boards = 200
            for (day in 0 until boards) {
                var used = false
                walk(board(day, d)) { t -> counts[t] = counts.getValue(t) + 1; steps++; if (t == CageTeacher.FALLBACK) used = true }
                if (used) fellBack++
            }
            val line = "${d.name}: boards fell back $fellBack/$boards, steps $steps (${steps / boards.toDouble()}/board), $counts"
            println(line)
            report.appendLine(line)
            assertEquals("${d.name} fell back", 0, fellBack)
            assertEquals(0, counts.getValue(CageTeacher.FALLBACK))
            // Measured 2026-10 (200 boards): Expert linked totals 607, cage chain 708 of 16,200 steps (the chain was 1,287).
            if (d == Difficulty.EXPERT) {
                assertTrue("linked totals too rare: $counts", counts.getValue(CageTeacher.LINKED_TOTALS) >= 2 * boards)
                assertTrue("cage chain too common: $counts", counts.getValue(CageTeacher.CAGE_CHAIN) * 100 <= steps * 6)
            }
        }
        writeReport("cages-teaching-coverage.txt", report.toString())
    }

    @Test
    fun `a year of daily boards on every tier is finished by hints alone`() {
        for (d in Difficulty.entries) for (day in 0 until 365) {
            walk(board(day, d)) { t -> assertNotEquals("${d.name} day $day", CageTeacher.FALLBACK, t) }
        }
    }

    @Test
    fun `steps stay sound from boards a player made`() {
        val rng = Rng(7)
        for (d in Difficulty.entries) for (day in 0 until 40) {
            val s = board(day, d)
            val open = s.grid.givens.indices.filter { !s.grid.givens[it] }
            var now = s
            // Some correct digits, in any order, and some arbitrary pencil marks.
            for (i in rng.shuffled(open).take(rng.nextInt(open.size + 1))) now = now.withCell(i, s.grid.solution[i])
            for (i in rng.shuffled(open).take(10)) now = now.toggleNote(i, rng.nextInt(1, 9))
            if (now.solved) continue
            val step = Cages.teach(now)!!
            assertFalse(step.mistake)
            assertFalse("${d.name} day $day fell back from a correct partial board", step.fallback)
            walk(now)
        }
    }

    @Test
    fun `a wrong digit is addressed first and a right one is never called wrong`() {
        val rng = Rng(11)
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val open = s.grid.givens.indices.filter { !s.grid.givens[it] }
            val half = open.take(open.size / 2).fold(s) { a, i -> a.withCell(i, s.grid.solution[i]) }
            assertFalse(Cages.teach(half)?.mistake ?: false)
            val at = rng.pick(open.filter { half.cells[it] == 0 })
            val wrongDigit = (1..9).filter { it != s.grid.solution[at] }.let { rng.pick(it) }
            val bad = half.withCell(at, wrongDigit)
            val step = Cages.teach(bad)!!
            assertTrue("${d.name} day $day did not flag the wrong digit", step.mistake)
            assertEquals(setOf(at), step.targets)
            assertFalse(step.isReached(bad))
            assertTrue(step.isReached(bad.withCell(at, 0)))
            assertEquals(0, (step.apply(bad) as CageState).cells[at])
        }
    }

    @Test
    fun `every nudge and explanation fits the panel`() {
        var longest = 0
        var nudge = 0
        val chains = ArrayList<Int>()
        for (d in Difficulty.entries) for (day in 0 until 150) {
            val s = board(day, d)
            val at = s.grid.givens.indices.first { !s.grid.givens[it] }
            val wrong = (1..9).first { it != s.grid.solution[at] }
            for (state in listOf(s, s.withCell(at, wrong))) {
                hintWalk(state, 90) { cur ->
                    val step = CageTeacher.teach(cur)!!
                    assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= 70)
                    // The cage chain's worked line is as long as the reasoning (it scrolls in the slot); its spread is below.
                    if (step.technique == CageTeacher.CAGE_CHAIN) chains += step.explanation.length
                    else {
                        val limit = if (step.technique == CageTeacher.LINKED_TOTALS) CageTeacher.MAX_LINKED_EXPLANATION else CageTeacher.MAX_EXPLANATION
                        assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= limit)
                        longest = maxOf(longest, step.explanation.length)
                    }
                    nudge = maxOf(nudge, step.nudge.length)
                    Cages.teach(cur)!!.apply(cur) as CageState
                }
            }
        }
        chains.sort()
        val median = chains[chains.size / 2]
        println("longest explanation: $longest, longest nudge: $nudge; cage chain ${chains.size} steps, median $median, " +
            "p90 ${chains[chains.size * 9 / 10]}, max ${chains.last()}, within 300: ${chains.count { it <= 300 }}")
        // Measured 2026-10 (450 boards): median 247; most chains are two or three totals, as long as a linked-totals step.
        assertTrue("cage chain lines too long: median $median", median <= CageTeacher.MAX_LINKED_EXPLANATION)
    }

    /**
     * The cage chain's worked line, checked from outside: the square's digit holds in every answer [CageOracle] finds
     * (cage by cage over every filling, no inference); the line is not the old summary; and every cage it names by its
     * total is a cage of the board lying within the squares the hint marks.
     */
    @Test
    fun `cage chain lines are worked through, name real cages and hold in every answer`() {
        var checked = 0
        for (d in listOf(Difficulty.HARD, Difficulty.EXPERT)) for (day in 0 until 30) {
            var here = 0
            hintWalk(board(day, d), 90) { now ->
                if (here == 2) return@hintWalk null
                val step = CageTeacher.teach(now)!!
                if (step.technique == CageTeacher.CAGE_CHAIN) {
                    val text = step.explanation
                    assertFalse(text, text.startsWith("Work the cage totals"))
                    assertTrue(text, "glowing square" in text)
                    val cages = CageLogic.cageCells(now.cageOf)
                    // "the cage totalling 11", "the top-right cage totalling 11", "the cage totalling 11 at row 2, column 7".
                    val named = Regex("the (?:([a-z]+(?:-[a-z]+)?) )?cage totalling (\\d+)(?: at row (\\d), column (\\d))?")
                    val phrases = named.findAll(text).map { it.value }.toSet()
                    for (m in named.findAll(text)) {
                        val (where, total, row, col) = m.destructured
                        val first = { k: Int -> cages[k].min() }
                        val fits = cages.indices.filter { k ->
                            now.sums[k] == total.toInt() && cages[k].all { it in step.cited } &&
                                (where.isEmpty() || CageLogic.BOX_NAMES[first(k) / 27 * 3 + first(k) % 9 / 3] == where) &&
                                (row.isEmpty() || first(k) == (row.toInt() - 1) * 9 + col.toInt() - 1)
                        }
                        assertTrue("${m.value} in: $text", fits.isNotEmpty())
                        // A bare name is never used beside a qualified one of the same total: two cages, two names.
                        if (where.isEmpty() && row.isEmpty()) {
                            assertTrue(text, phrases.none { it != m.value && it.endsWith("cage totalling $total") || it.contains("cage totalling $total at") })
                        }
                    }
                    val answers = CageOracle.all(now.cageOf, now.sums, now.cells, limit = 5)
                    assertTrue(answers.isNotEmpty())
                    for (a in answers) assertEquals("${d.name} day $day: $text", a[step.cell], step.digit)
                    checked++
                    here++
                }
                Cages.teach(now)!!.apply(now) as CageState
            }
        }
        println("cage chain steps checked against the oracle: $checked")
        assertTrue("only $checked chain steps checked", checked >= 30)
    }

    @Test
    fun `the reasoning cannot see the answer`() {
        for (d in Difficulty.entries) for (day in 0 until 12) {
            val s = board(day, d)
            // The answer blanked wherever the board is still open; the player's own digits stay, so nothing is a mistake.
            val blind = s.copy(grid = s.grid.copy(solution = s.grid.cells))
            val a = CageTeacher.teach(s)!!
            val b = CageTeacher.teach(blind)!!
            assertEquals(a.explanation, b.explanation)
            assertEquals(a.cell, b.cell)
            assertEquals(a.digit, b.digit)
        }
    }

    /**
     * A linked-totals step names a digit no single group gives: [CageOracle] (cage by cage over every filling, no inference)
     * must find that digit in every answer of the board as it stands, and at least one answer.
     */
    @Test
    fun `linked totals hold in every answer the oracle finds`() {
        var checked = 0
        for (d in listOf(Difficulty.HARD, Difficulty.EXPERT)) for (day in 0 until 30) {
            var here = 0
            hintWalk(board(day, d), 90) { now ->
                if (here == 2) return@hintWalk null
                val step = CageTeacher.teach(now)!!
                if (step.technique == CageTeacher.LINKED_TOTALS) {
                    val answers = CageOracle.all(now.cageOf, now.sums, now.cells, limit = 5)
                    assertTrue(answers.isNotEmpty())
                    for (a in answers) assertEquals("${d.name} day $day: ${step.explanation}", a[step.cell], step.digit)
                    checked++
                    here++
                }
                Cages.teach(now)!!.apply(now) as CageState
            }
        }
        println("linked totals checked against the oracle: $checked")
        assertTrue("only $checked linked steps checked", checked >= 30)
    }

    /**
     * Boards the generator did not make, with several answers: every non-fallback step holds in every answer. Cages grown
     * by [spec]'s mix, [givens] digits of a random grid shown; the steps taken, per technique.
     */
    private fun multiAnswer(seed: Long, spec: CageLogic.Spec, givens: IntRange, want: Int): Pair<Int, Map<String, Int>> {
        val rng = Rng(seed)
        var boards = 0
        val steps = linkedMapOf<String, Int>()
        var attempts = 0
        while (boards < want && attempts++ < 400) {
            val solution = Sudoku.fullGrid(rng)
            val cageOf = CageLogic.partition(rng, solution, spec)
            val sums = CageLogic.sumsOf(cageOf, solution)
            val shown = rng.shuffled((0 until 81).toList()).take(rng.nextInt(givens.first, givens.last + 1)).toSet()
            val cells = List(81) { if (it in shown) solution[it] else 0 }
            val answers = CageOracle.all(cageOf, sums, cells, limit = 30)
            if (answers.size < 2 || answers.size >= 30) continue
            boards++
            val start = CageState(
                SudokuState(givens = List(81) { it in shown }, cells = cells, solution = solution),
                cageOf, sums,
            )
            walkEveryAnswer(start, answers, steps)
        }
        return boards to steps
    }

    /** Hints from [start] until one falls back: each step's digits must be those of every one of [answers]. */
    private fun walkEveryAnswer(start: CageState, answers: List<List<Int>>, steps: MutableMap<String, Int>) {
        hintWalk(start, 90) { now ->
            val step = Cages.teach(now) ?: return@hintWalk null
            if (step.fallback) return@hintWalk null
            for (t in step.targets) {
                val digit = (step.apply(now) as CageState).cells[t]
                for (a in answers) assertEquals("${step.technique} at $t: ${step.explanation}", a[t], digit)
            }
            steps.merge(step.technique, 1, Int::plus)
            step.apply(now) as CageState
        }
    }

    @Test
    fun `steps hold in every answer of boards that have several`() {
        val (boards, steps) = multiAnswer(3, CageLogic.specFor(Difficulty.STANDARD), 18 until 24, want = 12)
        println("multi-answer check: $boards boards, $steps")
        assertTrue("found $boards boards", boards >= 6)
        assertTrue(steps.values.sum() > 40)
    }

    /**
     * Random boards with several answers finish on singles before any chain, so these are made from Expert boards: walked
     * by hints to their first cage chain, then two neighbouring cages merged into one (its total theirs together, kept only
     * if its digits stay different) until [CageOracle] finds several answers. Every step after must hold in all of them.
     */
    @Test
    fun `cage chain lines hold in every answer of boards that have several`() {
        val steps = linkedMapOf<String, Int>()
        var boards = 0
        for (day in 0 until 40) {
            var at: CageState? = null
            hintWalk(board(day, Difficulty.EXPERT), 90) { now ->
                val step = Cages.teach(now) ?: return@hintWalk null
                if (step.technique == CageTeacher.CAGE_CHAIN) { at = now; return@hintWalk null }
                step.apply(now) as CageState
            }
            val p = at ?: continue
            val solution = p.grid.solution
            val cages = CageLogic.cageCells(p.cageOf)
            merged@ for (a in cages.indices) for (b in a + 1 until cages.size) {
                val both = cages[a] + cages[b]
                if (cages[a].none { c -> cages[b].any { kotlin.math.abs(c - it) == 9 || (c / 9 == it / 9 && kotlin.math.abs(c - it) == 1) } }) continue
                if (both.map { solution[it] }.toSet().size != both.size) continue
                // Renumbered by first square, as cages always are.
                val raw = p.cageOf.map { if (it == b) a else it }
                val order = raw.distinct()
                val cageOf = raw.map { order.indexOf(it) }
                val sums = CageLogic.sumsOf(cageOf, solution)
                val answers = CageOracle.all(cageOf, sums, p.cells, limit = 30)
                if (answers.size < 2 || answers.size >= 30) continue
                boards++
                walkEveryAnswer(p.copy(cageOf = cageOf, sums = sums), answers, steps)
                break@merged
            }
        }
        println("multi-answer chain check: $boards boards, $steps")
        assertTrue("found $boards boards", boards >= 6)
        assertTrue("chain steps: $steps", (steps[CageTeacher.CAGE_CHAIN] ?: 0) >= 10)
    }

    // ---- the walkthrough -----------------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val b = Cages.tutorialBoard()
        assertEquals(1, CageOracle.count(b.cageOf, b.sums, b.cells))
        val all = CageOracle.all(b.cageOf, b.sums, b.cells, 2)
        assertEquals(b.grid.solution, all.single())
    }

    @Test
    fun `each walkthrough frame accepts its move and rejects a near miss`() {
        val frames = Cages.tutorial
        assertEquals(5, frames.size)
        assertEquals(listOf(false, true, true, true, false), frames.map { it.judge != null })
        assertTrue(frames.last().freePlay)

        val first = Cages.TUTORIAL_FIRST
        val wrong = Cages.TUTORIAL_WRONG
        // The captions' claims, from the board itself.
        val b = Cages.tutorialBoard()
        val cageFirst = CageLogic.cageCells(b.cageOf)[b.cageOf[first]]
        assertEquals(2, cageFirst.size)
        assertEquals(11, b.sums[b.cageOf[first]])
        assertEquals(7, b.cells[cageFirst.first { it != first }])
        assertEquals(4, b.grid.solution[first])
        val cageWrong = CageLogic.cageCells(b.cageOf)[b.cageOf[wrong]]
        assertEquals(11, b.sums[b.cageOf[wrong]])
        assertEquals(2, b.cells[cageWrong.first { it != wrong }])
        assertEquals(9, b.grid.solution[wrong])

        // 1: tap the first gap (the 4 glows next), then the 4; another square or digit is refused.
        val place = frames[1].steps!!
        assertEquals(TutorialStep.REJECT, place(b))
        val chosen = b.select(first)
        assertEquals(TutorialStep.PROGRESS, place(chosen))
        assertEquals(setOf(SudokuTeacher.pad(4)), frames[1].glow!!(chosen).strong)
        assertEquals(TutorialStep.REJECT, place(b.select(wrong)))
        assertEquals(TutorialStep.REJECT, place(chosen.enter(first, 5, notesMode = false)))
        val placed = chosen.enter(first, 4, notesMode = false)
        assertEquals(TutorialStep.DONE, place(placed))
        // 2: the player puts the 5 in (its cage red, and a repeat in its column), then taps 5 again.
        val clash = frames[2].steps!!
        val start = frames[2].state as CageState
        assertEquals(placed.cells, start.cells)
        assertEquals(TutorialStep.REJECT, clash(start))
        val picked = start.select(wrong)
        assertEquals(TutorialStep.PROGRESS, clash(picked))
        assertEquals(TutorialStep.REJECT, clash(picked.enter(wrong, 9, notesMode = false)))
        val shown = picked.enter(wrong, 5, notesMode = false)
        assertEquals(TutorialStep.PROGRESS, clash(shown))
        assertTrue(frames[2].glow!!(shown).warning)
        assertTrue("the cage is shown wrong", CageLogic.cageConflicts(shown.cells, shown.cageOf, shown.sums).containsAll(cageWrong.toList()))
        assertTrue("and the 5 repeats in its column", (0 until 9).any { it * 9 + wrong % 9 != wrong && shown.cells[it * 9 + wrong % 9] == 5 })
        assertTrue(wrong in shown.grid.conflicts())
        assertEquals(TutorialStep.DONE, clash(shown.enter(wrong, 5, notesMode = false)))
        // 3: pencil the 9.
        val cleared = frames[3].state as CageState
        assertEquals(0, cleared.cells[wrong])
        assertTrue(frames[3].accepts!!(cleared.toggleNote(wrong, 9)))
        assertFalse(frames[3].accepts!!(cleared.toggleNote(wrong, 8)))
        assertFalse(frames[3].accepts!!(cleared.withCell(wrong, 9)))
        // Each frame's board is the previous one's result.
        assertEquals(Cages.tutorialBoard(mapOf(first to 4), selected = wrong), frames[3].state)
        assertEquals(cleared.toggleNote(wrong, 9).grid.notes, (frames[4].state as CageState).grid.notes)
        for (f in frames) assertTrue(f.caption.length <= 170)
    }

    @Test
    fun `from the free-play frame hints alone finish the walkthrough with no fallback and no mistake`() {
        walk(Cages.tutorial.last().state as CageState) { t -> assertNotEquals(CageTeacher.FALLBACK, t) }
    }
}
