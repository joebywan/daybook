package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.ui.lineUnits
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A report, not a check (`DAYBOOK_MILESTONES=all`, or a comma list of ids): walks six boards a tier by the teacher's "Show me"
 * and counts the moves that finish a move-feel unit (the unit's note), with each board's own `complete` rule copied here.
 * Mambo and Nonogram are the yardstick. [randomOrder] fills Sudoku, Cages and Mambo from the answer at random and digit by digit.
 * Output: `build/reports/milestones.txt`, `milestones-random.txt`.
 */
class MilestoneMeasureTest {
    private val games: List<Pair<PuzzleType, (PuzzleState) -> BooleanArray>> = listOf(
        Mambo to { st -> val s = st as MamboState; val live = s.violations(); val u = lineUnits(s.size, s.size)
            BooleanArray(u.size) { i -> u[i].none { s.cells[it] == Sym.NONE } && live.none { v -> v.cells.all { it in u[i] } } } },
        Nonogram to { st -> val s = st as NonogramState; val c = NonogramLogic.clues(s); BooleanArray(c.size) { NonogramLogic.lineDone(s, it, c[it]) } },
        Atoms to { st -> val s = st as AtomsState; BooleanArray(s.atoms.size) { s.degree(it) == s.atoms[it].bonds } },
        Codeword to { st -> val s = st as CodewordState; val runs = CodewordLogic.runs(s.size, s.numbers)
            BooleanArray(runs.size) { r -> val w = runs[r].map { s.letters[s.numbers[it] - 1] }
                w.none { it == CodewordLogic.EMPTY } && CodewordWords.isWord(CharArray(w.size) { 'a' + w[it] }.concatToString()) } },
        Dominoes to { st -> val s = st as DominoState; val p = DominoLogic.pipsOf(s); BooleanArray(s.rules.size) { DominoLogic.regionHolds(s.layout, p, it) } },
        Dwindle to { st -> val s = st as DwindleState; BooleanArray(s.goals.size) { s.goals[it].word in s.found } },
        Inequality to { st -> val s = st as InequalityState; val n = s.size; val b = InequalityLogic.brokenSigns(s.cells, s.signs); val u = lineUnits(n, n)
            BooleanArray(u.size) { i -> val l = u[i]; l.map { s.cells[it] }.toSet() == (1..n).toSet() && b.none { s.signs[it].lo in l && s.signs[it].hi in l } } },
        Lamps to { st -> val s = st as LampsState; val w = (0 until s.size * s.size).filter { s.numberAt(it) > 0 }
            BooleanArray(w.size) { s.lampsBeside(w[it]) == s.numberAt(w[it]) } },
        Lits to { st -> val s = st as LitsState; val u = s.region.indices.groupBy { s.region[it] }.values.map { it.toIntArray() }; val h = s.letters()
            BooleanArray(u.size) { i -> u[i].count { s.shaded[it] } == 4 && u[i].all { !s.shaded[it] || h[it] != null } } },
        Mini to { st -> val s = st as MiniState; val sl = MiniLogic.slots(s.size, s.answer); BooleanArray(sl.size) { WheelWords.isGridWord(MiniLogic.spelled(s.letters, sl[it])) } },
        Regions to { st -> val s = st as RegionsState; val c = s.conflicts(); val u = s.region.indices.groupBy { s.region[it] }.values.map { it.toIntArray() }
            BooleanArray(u.size) { i -> u[i].map { s.cells[it] }.toSet() == (1..u[i].size).toSet() && u[i].none { it in c } } },
        Sand to { st -> val s = st as SandState; BooleanArray(s.tubes.size) { SandLogic.finished(s.tubes[it], s.height) } },
        Sudoku to { st -> val s = st as SudokuState; BooleanArray(36) { if (it < 27) s.cells.holdsOneToNine(SUDOKU_UNITS[it]) else s.cells.allNine(it - 26) } },
        Cages to { st -> val s = st as CageState; val cages = CageLogic.cageCells(s.cageOf)
            BooleanArray(36 + cages.size) { u -> if (u < 27) s.cells.holdsOneToNine(SUDOKU_UNITS[u]) else if (u >= 27 + cages.size) s.cells.allNine(u - 26 - cages.size) else { val c = cages[u - 27]
                c.all { s.cells[it] != 0 } && c.map { s.cells[it] }.toSet().size == c.size && c.sumOf { s.cells[it] } == s.sums[u - 27] } } },
        Tally to { st -> val s = st as TallyState; val t = TallyLogic.targets(s); BooleanArray(2 * s.size) { TallyLogic.leftIn(s, it) == t[it] } },
    )

    @Test fun measure() {
        assumeTrue(System.getenv("DAYBOOK_MILESTONES") != null)
        val only = System.getenv("DAYBOOK_MILESTONES")!!.split(',').filter { it.isNotBlank() && it != "all" }
        val out = StringBuilder()
        for ((game, done) in games) {
            if (only.isNotEmpty() && game.id !in only) continue
            for (d in Difficulty.entries) {
                var steps = 0; var notes = 0; var silentSteps = 0; var longest = 0; var firstSum = 0; var units = 0; var boards = 0
                for (seed in 1L..6L) {
                    var s = game.generate(seed * 7919, d)
                    var before = done(s)
                    units += before.size
                    var gap = 0; var first = -1; var k = 0
                    while (!s.solved && k < 2000) {
                        val step = game.teach(s) ?: break
                        val next = step.apply(s)
                        if (next == s) break
                        k++
                        val now = done(next)
                        val fresh = now.indices.count { now[it] && !before[it] }
                        if (fresh > 0 && !next.solved) { notes++; if (first < 0) first = k; longest = maxOf(longest, gap); gap = 0 } else { gap++ }
                        before = now; s = next
                    }
                    longest = maxOf(longest, gap - 1)
                    steps += k; firstSum += if (first < 0) k else first; boards++
                    silentSteps += 0
                }
                out.appendLine("%-10s %-8s units/board %5.1f steps/board %6.1f notes/board %5.1f steps/note %5.1f firstNote@ %5.1f longestSilence %d".format(
                    game.id, d.label, units / 6.0, steps / 6.0, notes / 6.0, if (notes == 0) Double.NaN else steps.toDouble() / notes, firstSum / 6.0, longest))
            }
        }
        println(out)
        java.io.File("build/reports/milestones.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }

    @Test fun randomOrder() {
        assumeTrue(System.getenv("DAYBOOK_MILESTONES") != null)
        val out = StringBuilder()
        fun run(name: String, d: Difficulty, boards: (Long) -> Triple<PuzzleState, List<Int>, (PuzzleState, Int) -> PuzzleState>, done: (PuzzleState) -> BooleanArray, order: String) {
            var steps = 0; var notes = 0; var first = 0; var longest = 0
            for (seed in 1L..6L) {
                val (start, open, put) = boards(seed * 7919)
                val rng = java.util.Random(seed)
                val o = when (order) { "random" -> open.shuffled(rng); else -> open.sortedBy { (start as? SudokuState ?: (start as CageState).grid).solution[it] * 1000 + rng.nextInt(1000) } }
                var s = start; var before = done(s); var gap = 0; var f = -1; var k = 0
                for (c in o) { val next = put(s, c); k++
                    val now = done(next); val fresh = now.indices.count { now[it] && !before[it] }
                    if (fresh > 0 && !next.solved) { notes++; if (f < 0) f = k; longest = maxOf(longest, gap); gap = 0 } else gap++
                    before = now; s = next }
                steps += k; first += if (f < 0) k else f
            }
            out.appendLine("%-8s %-8s %-7s steps/board %5.1f notes/board %5.1f steps/note %4.1f firstNote@ %5.1f longestSilence %d".format(name, d.label, order, steps / 6.0, notes / 6.0, steps.toDouble() / notes, first / 6.0, longest))
        }
        val sudoku = games.first { it.first == Sudoku }.second
        val cages = games.first { it.first == Cages }.second
        val mambo = games.first { it.first == Mambo }.second
        for (d in Difficulty.entries) {
            for (order in listOf("random", "digit")) {
                run("sudoku", d, { seed -> val s = Sudoku.generate(seed, d) as SudokuState
                    Triple(s, s.cells.indices.filter { s.cells[it] == 0 }) { st, c -> val x = st as SudokuState; x.copy(cells = x.cells.toMutableList().also { it[c] = x.solution[c] }) } }, sudoku, order)
                run("cages", d, { seed -> val s = Cages.generate(seed, d) as CageState
                    Triple(s, s.grid.cells.indices.filter { s.grid.cells[it] == 0 }) { st, c -> val x = st as CageState; x.copy(grid = x.grid.copy(cells = x.grid.cells.toMutableList().also { it[c] = x.grid.solution[c] })) } }, cages, order)
            }
            run("mambo", d, { seed -> val s = Mambo.generate(seed, d) as MamboState
                Triple(s, s.cells.indices.filter { s.cells[it] == Sym.NONE }) { st, c -> val x = st as MamboState; x.copy(cells = x.cells.toMutableList().also { it[c] = x.solution[c] }) } }, mambo, "random")
        }
        println(out)
        java.io.File("build/reports/milestones-random.txt").writeText(out.toString())
    }
}
