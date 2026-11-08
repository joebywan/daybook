package com.joebywan.daybook

import com.joebywan.daybook.ui.play.guideUrl
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.puzzles.Atom
import com.joebywan.daybook.puzzles.AtomsTeacher
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.Card
import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessTeacher
import com.joebywan.daybook.puzzles.CipherLogic
import com.joebywan.daybook.puzzles.CipherSum
import com.joebywan.daybook.puzzles.CipherTeacher
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.CodewordTeacher
import com.joebywan.daybook.puzzles.Domino
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.DwindleTeacher
import com.joebywan.daybook.puzzles.Feedback
import com.joebywan.daybook.puzzles.HexTake
import com.joebywan.daybook.puzzles.HexwordsState
import com.joebywan.daybook.puzzles.HexwordsTeacher
import com.joebywan.daybook.puzzles.InequalityLogic
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.KingsTeacher
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsTeacher
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.puzzles.GuessTeacher
import com.joebywan.daybook.puzzles.LexiconTeacher
import com.joebywan.daybook.puzzles.Link
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsTeacher
import com.joebywan.daybook.puzzles.MamboTeacher
import com.joebywan.daybook.puzzles.Mark
import com.joebywan.daybook.puzzles.MinesTeacher
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.MiniTeacher
import com.joebywan.daybook.puzzles.MosaicTeacher
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramTeacher
import com.joebywan.daybook.puzzles.Pair2
import com.joebywan.daybook.puzzles.PearlsTeacher
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.RegionRule
import com.joebywan.daybook.puzzles.RegionsLayout
import com.joebywan.daybook.puzzles.RegionsLogic
import com.joebywan.daybook.puzzles.RegionsTeacher
import com.joebywan.daybook.puzzles.RuleKind
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandLogic
import com.joebywan.daybook.puzzles.SandTeacher
import com.joebywan.daybook.puzzles.SetsTeacher
import com.joebywan.daybook.puzzles.ShikakuTeacher
import com.joebywan.daybook.puzzles.Sign
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyTeacher
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.TentsTeacher
import com.joebywan.daybook.puzzles.TowerTeacher
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoTeacher
import com.joebywan.daybook.puzzles.WhosWhoWords
import com.joebywan.daybook.puzzles.WordWheelTeacher
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The player guide (`tools/guide/pages/<puzzle id>.txt`, see `tools/guide/AUTHORING.md`) against the app. Gradle knows the pages as an input (`app/build.gradle.kts`), so a page-only change re-runs this.
 */
class GuideTest {

    private class Board(val technique: String, val rows: List<List<String>>, val fields: Map<String, String>) {
        val width = rows[0].size

        /** `r1c1` and rectangles `r1c1-r3c9` as `row * width + col`, the index most boards use for a highlight. */
        fun squares(key: String): Set<Int> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.flatMap { part ->
            val n = Regex("\\d+").findAll(part).map { it.value.toInt() - 1 }.toList()
            val (r2, c2) = if (n.size == 4) n[2] to n[3] else n[0] to n[1]
            (n[0]..r2).flatMap { r -> (n[1]..c2).map { c -> r * width + c } }
        }.toSet()

        /** `aab acc` as a number per square, numbered by first square as the puzzles do; `.` is -1. */
        fun letters(key: String): List<Int> {
            val seen = HashMap<Char, Int>()
            return fields[key].orEmpty().filter { it != ' ' }.map { if (it == '.') -1 else seen.getOrPut(it) { seen.size } }
        }

        /** `r3c4=17` as square to "17". */
        fun marks(key: String): Map<Int, String> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.associate { part ->
            val n = Regex("\\d+").findAll(part.substringBefore('=')).map { it.value.toInt() - 1 }.toList()
            n[0] * width + n[1] to part.substringAfter('=')
        }
    }

    /** A teacher's step with its highlights mapped to the board's squares: clues in a margin, signs it draws differently. */
    private class Mapped(step: TeachStep, override val focus: Set<Int>, override val targets: Set<Int> = step.targets) : TeachStep by step

    private class Page(val id: String, val text: String) {
        val lines = text.lines()
        val headings = lines.filter { it.startsWith("## ") }.map { it.drop(3).trim() }
        val techniques = lines.filter { it.startsWith("### ") }.map { it.drop(4).split('|').map(String::trim) }

        fun section(name: String): List<String> =
            lines.dropWhile { it.trim() != "## $name" }.drop(1).takeWhile { !it.startsWith("## ") }

        val boards: List<Board> = buildList {
            var technique = ""
            var block: MutableList<String>? = null
            for (line in lines) {
                if (line.startsWith("### ")) technique = line.drop(4).substringBefore('|').trim()
                when {
                    line.trim() == "~~~board" -> block = mutableListOf()
                    line.trim() == "~~~" -> {
                        val (fields, rows) = block!!.partition { ':' in it }
                        add(
                            Board(
                                technique,
                                rows.filter { it.isNotBlank() }.map { it.trim().split(Regex("\\s+")) },
                                fields.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() },
                            )
                        )
                        block = null
                    }
                    else -> block?.add(line)
                }
            }
        }
    }

    private val pages = File("../tools/guide/pages").listFiles { f -> f.extension == "txt" }.orEmpty()
        .map { Page(it.nameWithoutExtension, it.readText()) }

    /**
     * What a puzzle's teacher says on the position a board draws. Every page's puzzle needs a line here: it is what
     * proves the diagrams are the technique they sit under. Add puzzle-specific checks in the function, as Sudoku's.
     */
    private val teachers: Map<String, (Board) -> TeachStep?> = mapOf(
        "sudoku" to ::sudokuStep,
        "chess" to ::chessStep,
        "whoswho" to ::whosWhoStep,
        "inequality" to ::inequalityStep,
        "regions" to ::regionsStep,
        "cages" to ::cagesStep,
        "tally" to ::tallyStep,
        "nonogram" to ::nonogramStep,
        "shikaku" to ::shikakuStep,
        "pipes" to ::pipesStep,
        "atoms" to ::atomsStep,
        "pearls" to ::pearlsStep,
        "mosaic" to ::mosaicStep,
        "dominoes" to ::dominoesStep,
        // No techniques and so no boards (hints are off): these two lines are never asked.
        "snap" to { _ -> null },
        "pointers" to { _ -> null },
        "kings" to ::kingsStep,
        "mambo" to ::mamboStep,
        "tents" to ::tentsStep,
        "lamps" to ::lampsStep,
        "mines" to ::minesStep,
        "lits" to ::litsStep,
        "words" to ::lexiconStep,
        "wordwheel" to ::wordWheelStep,
        "mini" to ::miniStep,
        "codeword" to ::codewordStep,
        "dwindle" to ::dwindleStep,
        "hexwords" to ::hexwordsStep,
        "cipher" to ::cipherStep,
        "target" to { _ -> null }, // Nothing praised, so no boards: see its page.
        "sets" to ::setsStep,
        "tower" to ::towerStep,
        "sand" to ::sandStep,
    )

    /** A step's squares as a board draws them, for a teacher whose highlights are not all squares (or whose move is a line). */
    private class Drawn(override val technique: String, override val focus: Set<Int>, override val targets: Set<Int> = emptySet()) : TeachStep {
        override val nudge = ""
        override val explanation = ""
        override val cited = emptySet<Int>()
    }

    /** `lines` or `moves` as (lower square, the mark between, higher square). */
    private fun Board.drawn(key: String): List<Triple<Int, Char, Int>> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.map { part ->
        val n = Regex("\\d+").findAll(part).map { it.value.toInt() - 1 }.toList()
        val a = n[0] * width + n[1]
        val b = n[2] * width + n[3]
        Triple(minOf(a, b), Regex("[-=x]").find(part)!!.value[0], maxOf(a, b))
    }

    private fun pipesStep(b: Board): TeachStep? {
        val dirs = "urdl"
        fun mask(token: String) = token.fold(0) { m, c -> m or (1 shl dirs.indexOf(c)) }
        val cells = b.rows.flatten().map(::mask)
        assertTrue("${b.technique}: a board the reasoning settles, so one answer", PipesTeacher.settles(b.width, b.rows.size, cells))
        val step = PipesTeacher.deduce(b.width, b.rows.size, cells) ?: return null
        assertEquals("${b.technique}: the turn", mask(b.marks("answer").getValue(step.cell)), step.mask)
        return step
    }

    private fun atomsStep(b: Board): TeachStep? {
        val atoms = b.rows.flatMapIndexed { r, row ->
            row.mapIndexedNotNull { c, t -> t.toIntOrNull()?.let { Atom(r, c, it) } }
        }
        val at = { a: Atom -> a.row * b.width + a.col }
        // Who sees whom along a clear line, worked out here from the rows.
        val pairs = buildList {
            for (i in atoms.indices) for (j in i + 1 until atoms.size) {
                val (x, y) = atoms[i] to atoms[j]
                val across = x.row == y.row && atoms.none { it.row == x.row && it.col in x.col + 1 until y.col }
                val down = x.col == y.col && atoms.none { it.col == x.col && it.row in x.row + 1 until y.row }
                if (across || down) add(Pair2(i, j, across))
            }
        }
        fun pairOf(d: Triple<Int, Char, Int>) = pairs.indexOfFirst { at(atoms[it.a]) == d.first && at(atoms[it.b]) == d.third }
        val counts = MutableList(pairs.size) { 0 }
        for (d in b.drawn("lines")) counts[pairOf(d)] = if (d.second == '=') 2 else 1
        val step = AtomsTeacher.deduce(atoms, pairs, counts) ?: return null
        val moved = b.drawn("moves").associate { pairOf(it) to if (it.second == '=') 2 else 1 }
        assertEquals("${b.technique}: the bonds", moved, step.raise)
        return Drawn(step.technique, step.focus.filter { it < atoms.size }.map { at(atoms[it]) }.toSet())
    }

    private fun pearlsStep(b: Board): TeachStep? {
        val n = b.width
        val pearls = b.rows.flatten().joinToString("")
        fun edge(d: Triple<Int, Char, Int>) =
            if (d.third == d.first + 1) d.first / n * (n - 1) + d.first % n else n * (n - 1) + d.first
        val edges = CharArray(2 * n * (n - 1)) { '.' }
        for (d in b.drawn("lines")) edges[edge(d)] = if (d.second == 'x') 'x' else '-'
        val marks = edges.concatToString()
        val step = PearlsTeacher.deduce(n, pearls, marks) ?: return null
        val moved = b.drawn("moves")
        assertEquals("${b.technique}: the lines", moved.filter { it.second != 'x' }.map(::edge).toSet(), step.lines.toSet())
        assertEquals("${b.technique}: the crosses", moved.filter { it.second == 'x' }.map(::edge).toSet(), step.crosses.toSet())
        return Drawn(step.technique, step.focus.filter { it < n * n }.toSet())
    }

    /** The two things a Mosaic board's squares can't show (fills left, the colour poured), and how many colours it deals. */
    private val mosaicFixtures = mapOf("count" to Triple(3, 2, 2), "biggest-swallow" to Triple(3, 4, 0), "centre" to Triple(3, 3, 1))

    private fun mosaicStep(b: Board): TeachStep? {
        val cells = b.fields.getValue("regions").filter { it != ' ' }.map { it - 'a' }
        val (colours, fillsLeft, colour) = mosaicFixtures.getValue(b.technique)
        val step = MosaicTeacher.deduce(b.width, b.rows.size, colours, cells, fillsLeft) ?: return null
        assertEquals("${b.technique}: the colour poured", colour, step.colour)
        // The board tints the area filled: the hint's own focus can be the area and all its neighbours.
        return Drawn(step.technique, step.targets)
    }

    private fun dominoesStep(b: Board): TeachStep? {
        val w = b.width
        val grid = b.rows.take(w).flatten()
        val regions = b.fields.getValue("regions").split(' ').take(w).joinToString("")
        val badges = b.marks("corners")
        val regionOf = regions.map { if (it == '.') -1 else it - 'a' }
        val rules = (0..regionOf.max()).map { r ->
            val rule = badges.entries.first { regionOf[it.key] == r }.value
            when {
                rule == "=" -> RegionRule(RuleKind.EQUAL)
                rule == "≠" -> RegionRule(RuleKind.DIFFERENT)
                rule[0] == '<' -> RegionRule(RuleKind.LESS, rule.drop(1).toInt())
                rule[0] == '>' -> RegionRule(RuleKind.MORE, rule.drop(1).toInt())
                else -> RegionRule(RuleKind.SUM, rule.toInt())
            }
        }
        // The tray is under the grid and an empty row, a slot per domino in the tray's sorted order; a laid one's slot is empty.
        val slots = b.rows.drop(w + 1).flatten()
        val laid = b.drawn("lines").map { (x, _, y) -> x to y }
        fun domino(p: Int, q: Int) = Domino(minOf(p, q), maxOf(p, q))
        val fromSlots = slots.filter { it != "_" }.map { t -> t.split('-').let { domino(it[0].toInt(), it[1].toInt()) } }
        val tray = (fromSlots + laid.map { (x, y) -> domino(grid[x].toInt(), grid[y].toInt()) }).sortedWith(compareBy({ it.a }, { it.b }))
        val placed = tray.indices.map<Int, Placement?> { k ->
            if (slots[k] != "_") return@map null
            val (x, y) = laid.first { (x, y) -> domino(grid[x].toInt(), grid[y].toInt()) == tray[k] }
            if (grid[x].toInt() == tray[k].a) Placement(x, y) else Placement(y, x)
        }
        val layout = DominoLogic.Layout(w, w, grid.map { it != "_" }, regionOf, rules, tray)
        assertTrue("${b.technique}: a position with one answer", DominoLogic.solutions(layout) is Verdict.ExactlyOne)
        val step = DominoTeacher.deduce(layout, placed) ?: return null
        val slotSquare = { k: Int -> (w + 1) * w + k }
        val move = step.placement!!
        val domino = tray[step.domino]
        assertEquals("${b.technique}: the digits", mapOf(move.first to "${domino.a}", move.second to "${domino.b}", slotSquare(step.domino) to "${domino.b}-${domino.a}"), b.marks("answer"))
        val mapped = { s: Set<Int> -> s.map { if (it >= DominoTeacher.TRAY) slotSquare(it - DominoTeacher.TRAY) else it }.toSet() }
        return Drawn(step.technique, mapped(step.focus), mapped(step.targets))
    }

    private fun sudokuStep(b: Board): PlacementStep? {
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        assertEquals("${b.technique}: a position with one answer", 1, Sudoku.countSolutions(cells))
        val step = SudokuTeacher.deduce(cells) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        // Pencil marks on a board are digits that square can really hold, worked out here from the rules alone.
        for ((cell, digits) in b.marks("notes") + b.marks("struck")) for (d in digits) {
            val seen = (0 until 81).filter { it != cell && (it / 9 == cell / 9 || it % 9 == cell % 9 || (it / 27 == cell / 27 && it % 9 / 3 == cell % 9 / 3)) }
            assertTrue("${b.technique}: $d is no pencil mark for square $cell", seen.none { cells[it] == d.digitToInt() })
        }
        return step
    }

    /** A step with its squares moved to the board's `row * width + col`. */
    private fun mapped(step: TeachStep, focus: Set<Int>, cited: Set<Int>, targets: Set<Int>): TeachStep = object : TeachStep by step {
        override val focus = focus
        override val cited = cited
        override val targets = targets
    }

    /** The moves White has left on each chess board: the rows don't show them. White to move, no castling, no en passant. */
    private val chessMoves = mapOf("mate-in-one" to 1, "forcing-key" to 2, "quiet-key" to 2)

    private fun chessStep(b: Board): TeachStep? {
        assertEquals("a b c d e f g h", b.fields["top"])
        assertEquals("8 7 6 5 4 3 2 1", b.fields["left"])
        val letters = mapOf(
            '♔' to 'K', '♕' to 'Q', '♖' to 'R', '♗' to 'B', '♘' to 'N', '♙' to 'P',
            '♚' to 'k', '♛' to 'q', '♜' to 'r', '♝' to 'b', '♞' to 'n', '♟' to 'p',
        )
        val placement = b.rows.joinToString("/") { row -> row.map { letters[it[0]] ?: '1' }.joinToString("") }
        val step = ChessTeacher.deduce(ChessPosition.fromFen("$placement w - -"), chessMoves.getValue(b.technique)) ?: return null
        val at = { sq: Int -> (7 - sq / 8) * 8 + sq % 8 } // a1 = 0 to row * width + col, White at the bottom
        assertEquals("${b.technique}: rings", step.cited.map(at).toSet(), b.squares("cite"))
        // The filled squares are drawn before the move: the piece that moves, and what stands where it lands.
        val rows = b.rows.flatten()
        for (sq in listOf(ChessRules.from(step.move), ChessRules.to(step.move))) {
            assertEquals("${b.technique}: the move's square $sq", rows[at(sq)], b.marks("answer")[at(sq)])
        }
        return mapped(step, step.focus.map(at).toSet(), step.cited.map(at).toSet(), step.targets.map(at).toSet())
    }

    /** The two games on the Who's Who page, their words and clues: what its rows don't show. */
    private fun whosWhoGame(json: String) = Json.decodeFromString(PuzzleState.serializer(), json) as WhosWhoState
    private val whosWhoHard = whosWhoGame(
        """{"type":"com.joebywan.daybook.puzzles.WhosWhoState","names":["Eve","Kai","Leo","Raj"],"groups":[0,5,1],"items":[[0,1,2,3],[0,1,3,4],[0,2,3,4]],"clues":[{"kind":3,"a":0,"b":9},{"kind":2,"a":0,"b":13},{"kind":0,"a":1,"b":10},{"kind":3,"a":2,"b":3},{"kind":2,"a":0,"b":12},{"kind":0,"a":0,"b":8},{"kind":0,"a":1,"b":15},{"kind":0,"a":4,"b":13}],"answer":[[1,3,0,2],[0,2,3,1],[2,3,1,0]]}"""
    )
    private val whosWhoExpert = whosWhoGame(
        """{"type":"com.joebywan.daybook.puzzles.WhosWhoState","names":["Ann","Ben","Finn","Ivy","Kai"],"groups":[0,5,4],"items":[[0,1,2,3,4],[0,1,2,3,4],[0,1,2,3,4]],"clues":[{"kind":3,"a":1,"b":10},{"kind":2,"a":0,"b":14},{"kind":1,"a":6,"b":14},{"kind":0,"a":5,"b":18},{"kind":0,"a":13,"b":16},{"kind":0,"a":2,"b":6},{"kind":3,"a":10,"b":16},{"kind":4,"a":4,"b":7,"c":8},{"kind":2,"a":3,"b":11},{"kind":0,"a":3,"b":17},{"kind":2,"a":11,"b":19}],"answer":[[4,0,1,3,2],[3,2,0,4,1],[1,3,4,2,0]]}"""
    )
    private val whosWhoBoards = mapOf(
        "clue" to whosWhoHard.copy(marks = "..................o............................."),
        "only" to whosWhoHard.copy(marks = "x..xo....x..x.....o....o...x........x..........."),
        "link" to whosWhoHard.copy(marks = "x..xo....x..x.....o....oo..x........x..........."),
        "house" to whosWhoHard.copy(marks = "x..xo....x..x.....o....o........................"),
        "what-if" to whosWhoExpert.copy(marks = "x........x.....o...xxxoxx...o..o...ox.xx.x.x.x.....x.xo..o..x...x........x."),
    )

    private fun whosWhoStep(b: Board): TeachStep? {
        val s = whosWhoBoards.getValue(b.technique)
        assertEquals(s.groups.joinToString(" ") { WhosWhoWords.HEADERS[it] }, b.fields["top"])
        assertEquals(s.names.joinToString(" "), b.fields["left"])
        val text = pages.first { it.id == "whoswho" }.text
        for (k in s.clues.indices) {
            val line = "- ${k + 1}. ${WhosWhoLogic.clueText(s, k)}"
            assertTrue("${b.technique}: the page should list \"$line\"", line in text)
        }
        // Every square but the move's shows what the marks leave it: its ticked word, or the items still possible.
        val grid = WhosWhoLogic.gridOf(s.n, s.columns, s.marks)
        val step = WhosWhoTeacher.deduce(s, s.marks) ?: return null
        for (c in 0 until s.n * s.columns) {
            if (c in step.targets) continue
            val column = c % s.columns
            val placed = grid.placed[c]
            assertEquals("${b.technique}: square $c", if (placed >= 0) s.word(column, placed) else ".", b.rows[c / s.columns][column])
            val open = (0 until s.n).filter { placed < 0 && grid.cand[c] shr it and 1 == 1 }.map { s.word(column, it) }
            assertEquals("${b.technique}: what square $c can hold", open.joinToString(","), b.marks("notes")[c].orEmpty())
        }
        // The move: a tick says the item, a cross says "not" it.
        for (t in step.ticks) assertEquals(s.word(t / s.n % s.columns, t % s.n), b.marks("answer")[t / s.n])
        for (t in step.crosses) assertEquals("not_" + s.word(t / s.n % s.columns, t % s.n), b.marks("answer")[t / s.n])
        // A clue's index isn't a square: a step that reads one must name it in the caption.
        for (k in step.focus.filter { it >= WhosWhoTeacher.CLUE_BASE }) {
            assertTrue("${b.technique}: the caption names its clue", "lue ${k - WhosWhoTeacher.CLUE_BASE + 1}" in b.fields["caption"].orEmpty())
        }
        val cells = { set: Set<Int> -> set.filter { it < WhosWhoTeacher.CLUE_BASE }.toSet() }
        assertEquals("${b.technique}: rings", cells(step.cited), b.squares("cite"))
        return mapped(step, cells(step.focus), cells(step.cited), step.targets)
    }

    private fun inequalityStep(b: Board): TeachStep? {
        val n = b.width
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        val signs = b.fields["edges"].orEmpty().split(' ').filter { it.isNotEmpty() }.map { part ->
            val (first, second) = Regex("r(\\d+)c(\\d+)").findAll(part).map { (it.groupValues[1].toInt() - 1) * n + it.groupValues[2].toInt() - 1 }.toList()
            if (part.endsWith("<")) Sign(first, second) else Sign(second, first)
        }
        assertEquals("${b.technique}: a position with one answer", 1, InequalityLogic.countSolutions(n, cells, signs))
        val step = InequalityTeacher.deduce(n, cells, signs) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        return Mapped(step, step.focus.filter { it < n * n }.toSet())
    }

    private fun regionsStep(b: Board): TeachStep? {
        val layout = RegionsLayout(b.width, b.letters("regions"))
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        assertEquals("${b.technique}: a position with one answer", 1, RegionsLogic.countSolutions(layout, cells))
        val step = RegionsTeacher.deduce(layout, cells) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        return step
    }

    private fun cagesStep(b: Board): TeachStep? {
        val cageOf = b.letters("cages")
        val corners = b.marks("corners")
        val sums = List(cageOf.max() + 1) { k -> corners.getValue(cageOf.indexOf(k)).toInt() }
        val board = CageLogic.Board(cageOf, sums)
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        assertTrue("${b.technique}: a position with one answer", CageLogic.solutions(board, cells) is Verdict.ExactlyOne)
        val step = CageTeacher.deduce(board, cells) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        return step
    }

    /** A margin of targets right and below. A ring is a circled number, `struck` a removed one; both must agree with the one answer. */
    private fun tallyStep(b: Board): TeachStep? {
        val n = b.width - 1
        fun board(i: Int) = i / n * b.width + i % n
        val digits = IntArray(n * n) { b.rows[it / n][it % n].toInt() }
        val targets = List(n) { b.rows[it][n].toInt() } + List(n) { b.rows[n][it].toInt() }
        val kept = b.squares("cite")
        val gone = b.marks("struck").keys
        val marks = String(CharArray(n * n) {
            when (board(it)) {
                in kept -> TallyLogic.KEPT
                in gone -> TallyLogic.REMOVED
                else -> TallyLogic.UNMARKED
            }
        })
        val answer = TallyLogic.countSolutions(n, digits, targets)
        assertTrue("${b.technique}: a position with one answer", answer is Verdict.ExactlyOne)
        val keep = (answer as Verdict.ExactlyOne).value
        for (i in 0 until n * n) {
            if (board(i) in kept) assertEquals("${b.technique}: a ring the answer removes", '1', keep[i])
            if (board(i) in gone) assertEquals("${b.technique}: a strike the answer keeps", '0', keep[i])
        }
        val step = TallyTeacher.deduce(n, digits, targets, marks) ?: return null
        for (i in step.targets) assertEquals("${b.technique}: the number marked", digits[i].toString(), b.marks("answer")[board(i)])
        fun mapped(i: Int) = when {
            i < n * n -> board(i)
            i < n * n + n -> (i - n * n) * b.width + n
            else -> n * b.width + i - n * n - n
        }
        return Mapped(step, step.focus.map(::mapped).toSet(), step.targets.map(::mapped).toSet())
    }

    /** Clues in margins above and to the left: `.` or `_` where a line has fewer. An answer of `.` is a fill, `x` a cross. */
    private fun nonogramStep(b: Board): TeachStep? {
        val height = b.rows.size
        val n = listOf(5, 10, 15).last { it <= minOf(b.width, height) }
        val left = b.width - n
        val top = height - n
        fun clue(tokens: List<String>) = tokens.mapNotNull { it.toIntOrNull() }
        val rows = List(n) { clue(b.rows[top + it].take(left)) }
        val cols = List(n) { c -> clue(List(top) { b.rows[it][left + c] }) }
        fun board(i: Int) = (i / n + top) * b.width + i % n + left
        val filled = b.squares("solid")
        val start = String(CharArray(n * n) {
            when {
                board(it) in filled -> NonogramLogic.FILLED
                b.rows[it / n + top][it % n + left] == "x" -> NonogramLogic.CROSSED
                else -> NonogramLogic.UNMARKED
            }
        })
        val step = NonogramTeacher.deduce(n, n, rows, cols, start) ?: return null
        val answer = b.marks("answer")
        for (i in step.fills) assertEquals("${b.technique}: a fill", ".", answer[board(i)])
        for (i in step.crosses) assertEquals("${b.technique}: a cross", "x", answer[board(i)])
        // Lines alone finish it from here, and the picture has the clues it shows: so it is the board's one answer.
        val cells = start.toCharArray()
        var next: NonogramTeacher.Step? = step
        while (next != null) {
            next.fills.forEach { cells[it] = NonogramLogic.FILLED }
            next.crosses.forEach { cells[it] = NonogramLogic.CROSSED }
            next = NonogramTeacher.deduce(n, n, rows, cols, String(cells))
        }
        fun runs(line: List<Char>) = line.joinToString("").split(Regex("[^#]+")).filter { it.isNotEmpty() }.map { it.length }
        for (i in 0 until n) {
            assertEquals("${b.technique}: row ${i + 1} finished", rows[i], runs(List(n) { cells[i * n + it] }))
            assertEquals("${b.technique}: column ${i + 1} finished", cols[i], runs(List(n) { cells[it * n + i] }))
        }
        fun mapped(i: Int) = when {
            i < n * n -> setOf(board(i))
            i < n * n + n -> List(left) { ((i - n * n) + top) * b.width + it }.toSet()
            else -> List(top) { it * b.width + left + i - n * n - n }.toSet()
        }
        return Mapped(step, step.focus.flatMap(::mapped).toSet(), step.targets.map(::board).toSet())
    }

    /** `regions` are the rectangles drawn, `.` the squares still open. The answer is proved here by trying every rectangle. */
    private fun shikakuStep(b: Board): TeachStep? {
        val w = b.width
        val h = b.rows.size
        val clues = b.rows.flatten().map { it.toIntOrNull() }
        val region = b.letters("regions").ifEmpty { List(w * h) { -1 } }
        val blocks = (0..(region.maxOrNull() ?: -1)).map { k ->
            val at = region.indices.filter { region[it] == k }
            val block = Block(at.minOf { it / w }, at.minOf { it % w }, at.maxOf { it / w }, at.maxOf { it % w })
            assertEquals("${b.technique}: a drawn region is a rectangle", block.area, at.size)
            assertEquals("${b.technique}: a drawn rectangle's number", block.area, at.firstNotNullOf { clues[it] })
            block
        }
        val taken = BooleanArray(w * h) { region[it] >= 0 }
        fun tilings(): Int {
            val first = taken.indexOfFirst { !it }
            if (first < 0) return 1
            var found = 0
            val r0 = first / w
            val c0 = first % w
            // The first open square in reading order is the top-left of whatever covers it.
            for (r1 in r0 until h) for (c1 in c0 until w) {
                val inside = (r0..r1).flatMap { r -> (c0..c1).map { r * w + it } }
                if (inside.any { taken[it] }) continue
                val number = inside.mapNotNull { clues[it] }
                if (number.size != 1 || number[0] != inside.size) continue
                inside.forEach { taken[it] = true }
                found += tilings()
                inside.forEach { taken[it] = false }
            }
            return found
        }
        assertEquals("${b.technique}: a position with one answer", 1, tilings())
        return ShikakuTeacher.deduce(w, h, clues, blocks)
    }

    /** `regions:` as the game's numbering: `a` is region 0. */
    private fun regionsOf(b: Board) = b.fields.getValue("regions").replace(" ", "").map { it - 'a' }

    private fun kingsStep(b: Board): TeachStep? {
        val marks = b.rows.flatten().map { when (it) { "K" -> Mark.KING; "x" -> Mark.BLOCKED; else -> Mark.EMPTY } }
        val step = KingsTeacher.deduce(b.width, regionsOf(b), marks) ?: return null
        assertEquals("${b.technique}: the move", step.kings.associateWith { "K" } + step.crosses.associateWith { "x" }, b.marks("answer"))
        return step
    }

    private fun mamboStep(b: Board): TeachStep? {
        val cells = b.rows.flatten().map { when (it) { "S" -> Sym.SUN; "M" -> Sym.MOON; else -> Sym.NONE } }
        val links = b.fields.getValue("edges").split(' ').map { part ->
            val n = Regex("\\d+").findAll(part).map { it.value.toInt() - 1 }.toList()
            Link(n[0] * b.width + n[1], n[2] * b.width + n[3], part.endsWith("=="))
        }
        val step = MamboTeacher.deduce(b.width, cells, links) ?: return null
        assertEquals("${b.technique}: the move", step.places.mapValues { (_, s) -> if (s == Sym.SUN) "S" else "M" }, b.marks("answer"))
        return step
    }

    /** The clues are a top row and a left column, so the teacher's squares and clues move into the board's numbering. */
    private fun tentsStep(b: Board): TeachStep? {
        val n = b.width - 1
        val grid = b.rows.drop(1).map { it.drop(1) }.flatten()
        val rowCounts = b.rows.drop(1).map { it[0].toInt() }
        val colCounts = b.rows[0].drop(1).map { it.toInt() }
        val marks = grid.map { when (it) { "▲" -> TentsLogic.TENT; "x" -> TentsLogic.GRASS; else -> 0 } }
        // Grass around a tent sprouts by itself: the teacher reads the board with it, as `TentsState.seen` does.
        val seen = marks.toMutableList()
        for (i in marks.indices) if (marks[i] == TentsLogic.TENT) {
            for (j in TentsLogic.around(n, i)) if (marks[j] == 0 && grid[j] != "♣") seen[j] = TentsLogic.GRASS
        }
        val step = TentsTeacher.deduce(n, grid.map { it == "♣" }, rowCounts, colCounts, seen) ?: return null
        fun drawn(i: Int) = when {
            i < n * n -> (i / n + 1) * b.width + i % n + 1
            i < n * n + n -> (i - n * n + 1) * b.width
            else -> i - n * n - n + 1
        }
        val mark = if (step.value == TentsLogic.TENT) "▲" else "x"
        assertEquals("${b.technique}: the move", step.targets.map(::drawn).associateWith { mark }, b.marks("answer"))
        return object : TeachStep by step {
            override val focus = step.focus.map(::drawn).toSet()
            override val cited = step.cited.map(::drawn).toSet()
            override val targets = step.targets.map(::drawn).toSet()
        }
    }

    private fun lampsStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val solid = b.squares("solid")
        val isWall = tokens.indices.map { it in solid }
        val walls = tokens.indices.joinToString("") { i -> if (!isWall[i]) "." else if (tokens[i] == ".") "#" else tokens[i] }
        val marks = tokens.indices.joinToString("") { i -> if (isWall[i]) "." else when (tokens[i]) { "●" -> "L"; "x" -> "x"; else -> "." } }
        val lit = LampsLogic.lit(b.width, walls, BooleanArray(tokens.size) { marks[it] == 'L' })
        for (i in tokens.indices) if (!isWall[i] && tokens[i] in listOf(".", "·")) {
            assertEquals("${b.technique}: the dot on square $i", lit[i], tokens[i] == "·")
        }
        val step = LampsTeacher.deduce(b.width, walls, marks) ?: return null
        assertEquals("${b.technique}: the move", step.targets.associateWith { if (step.value == LampsLogic.LAMP) "●" else "x" }, b.marks("answer"))
        return step
    }

    private fun minesStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val hidden = b.squares("solid")
        val num = IntArray(tokens.size) { if (it in hidden) -1 else tokens[it].toIntOrNull() ?: 0 }
        val total = mapOf(64 to 10, 80 to 16, 90 to 21).getValue(tokens.size)
        val step = MinesTeacher.deduce(
            b.width, b.rows.size, num, BooleanArray(tokens.size), BooleanArray(tokens.size) { tokens[it] == "F" }, total,
        ) ?: return null
        assertEquals("${b.technique}: the move", step.safe.associateWith { "✓" } + step.mines.associateWith { "F" }, b.marks("answer"))
        return step
    }

    private fun litsStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val region = regionsOf(b)
        val shaded = tokens.map { it != "." && it != "x" }
        val letters = Lits.letters(b.width, b.rows.size, region, shaded)
        for (i in tokens.indices) if (shaded[i]) assertEquals("${b.technique}: square $i", letters[i]?.name ?: "■", tokens[i])
        assertEquals("${b.technique}: the crosses", Lits.impossible(b.width, b.rows.size, region, shaded), tokens.indices.filter { tokens[it] == "x" }.toSet())
        val step = LitsTeacher.deduce(b.width, b.rows.size, region, shaded) ?: return null
        assertEquals("${b.technique}: the move", step.shades.associateWith { "■" }, b.marks("answer"))
        return step
    }

    /** A teacher's step with its highlights mapped to the board's squares. */
    private fun step(from: TeachStep, focus: Set<Int>, targets: Set<Int>): TeachStep = object : TeachStep by from {
        override val focus = focus
        override val targets = targets
    }

    /** The guesses are the rows with marks, and their marks draw the board's `tiles`; the last row is the one being typed. */
    private fun lexiconStep(b: Board): TeachStep? {
        val tiles = b.marks("tiles")
        val n = b.width
        val guessRows = b.rows.indices.filter { r -> (0 until n).all { r * n + it in tiles } }
        val guesses = guessRows.map { b.rows[it].joinToString("").lowercase() }
        val marks = guessRows.map { r ->
            List(n) { c ->
                when (tiles.getValue(r * n + c)) {
                    "right" -> GuessMarks.CORRECT
                    "near" -> GuessMarks.PRESENT
                    else -> GuessMarks.ABSENT
                }
            }
        }
        assertTrue("${b.technique}: some word draws these marks", LexiconTeacher.candidates(n, guesses, marks).isNotEmpty())
        val taught = LexiconTeacher.teach(n, guesses, marks, "") ?: return null
        val move = taught.move as GuessTeacher.Move.Pin
        assertEquals("${b.technique}: the letter", move.letter.uppercase(), b.marks("answer")[guesses.size * n + move.slot])
        return step(taught, taught.focus, taught.targets.filter { it < LexiconTeacher.KEY }.toSet())
    }

    /** Maximal lines of two or more squares among [rows], across by row and then down by column. */
    private fun wheelShapes(rows: List<List<String>>): List<WordWheelTeacher.Shape> {
        val h = rows.size
        val w = rows[0].size
        val out = mutableListOf<WordWheelTeacher.Shape>()
        fun runs(lines: Int, length: Int, open: (Int, Int) -> Boolean, add: (Int, Int, Int) -> Unit) {
            for (l in 0 until lines) {
                var i = 0
                while (i < length) {
                    var e = i
                    while (e < length && open(l, e)) e++
                    if (e - i >= 2) add(l, i, e - i)
                    i = maxOf(e, i + 1)
                }
            }
        }
        runs(h, w, { r, c -> rows[r][c] != "_" }) { r, c, n -> out += WordWheelTeacher.Shape(r, c, true, n) }
        runs(w, h, { c, r -> rows[r][c] != "_" }) { c, r, n -> out += WordWheelTeacher.Shape(r, c, false, n) }
        return out
    }

    /** The last row is the wheel, under a row of nothing; a slot with no gap is a word already found. */
    private fun wordWheelStep(b: Board): TeachStep? {
        val grid = b.rows.dropLast(2)
        assertTrue("${b.technique}: a gap above the wheel", b.rows[grid.size].all { it == "_" })
        val wheel = b.rows.last().filter { it != "_" }.joinToString("").lowercase()
        val shown = grid.flatten().map { if (it == "." || it == "_") null else it.lowercase()[0] }
        val shapes = wheelShapes(grid)
        val letters = shapes.map { s -> s.cells(b.width).map { shown[it] } }
        val empty = shapes.indices.filter { null in letters[it] }
        val tried = shapes.indices.filter { it !in empty }.map { letters[it].joinToString("") }
        val taught = WordWheelTeacher.deduce(wheel, b.width, shapes, empty, shown, tried) ?: return null
        val spelled = shapes[taught.slot].cells(b.width).joinToString("") { b.marks("answer").getValue(it) }
        assertEquals("${b.technique}: the word", taught.word.uppercase(), spelled)
        return taught
    }

    private fun miniStep(b: Board): TeachStep? {
        val size = b.width
        val letters = b.rows.flatten().joinToString("") {
            when (it) {
                "_" -> MiniLogic.BLACK.toString()
                "." -> MiniLogic.OPEN.toString()
                else -> it.lowercase()
            }
        }
        val slots = MiniLogic.slots(size, letters)
        val taught = MiniTeacher.deduce(size, letters, List(slots.size) { "" }) ?: return null
        val squares = slots[taught.slot].squares
        assertEquals("${b.technique}: the word", taught.word!!.uppercase(), squares.joinToString("") { b.marks("answer").getValue(it) })
        return object : TeachStep {
            override val technique = taught.technique
            override val nudge = taught.nudge
            override val explanation = taught.explanation
            override val focus = taught.focus
            override val cited = taught.cited
            override val targets = squares.toSet()
        }
    }

    /** Open squares show their number and placed ones their letter. A placed number's own value does not matter, so each letter takes a number left over. */
    private fun codewordStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val open = tokens.mapNotNull { it.toIntOrNull() }.distinct()
        val placed = tokens.filter { it != "_" && it.toIntOrNull() == null }.distinct()
        val k = open.size + placed.size
        val spare = ((1..k) - open.toSet()).toList()
        val numberOf = placed.withIndex().associate { (i, letter) -> letter to spare[i] }
        val numbers = tokens.map { if (it == "_") 0 else it.toIntOrNull() ?: numberOf.getValue(it) }
        val letters = MutableList(k) { CodewordLogic.EMPTY }
        for ((letter, n) in numberOf) letters[n - 1] = letter[0] - 'A'
        val taught = CodewordTeacher.deduce(b.width, numbers, letters) ?: return null
        val squares = numbers.indices.filter { numbers[it] == taught.number + 1 }.toSet()
        for (v in b.marks("answer").values) assertEquals("${b.technique}: the letter", (taught.letter + 'A'.code).toChar().toString(), v)
        return object : TeachStep {
            override val technique = taught.technique
            override val nudge = taught.nudge
            override val explanation = taught.explanation
            override val focus = taught.focus
            override val cited = taught.cited
            override val targets = squares
        }
    }

    /**
     * What Dwindle's hint reads beyond the tiles: the open dashes' lengths, the words already found, and (as the one way
     * of a word is counted on the full grid) the letters of the tiles gone. All from the position the board was copied from.
     */
    private class DwindleFixture(val lengths: List<Int>, val tried: List<String> = emptyList(), val letters: String? = null)

    private val dwindleFixtures = mapOf(
        "through" to DwindleFixture(listOf(5, 4, 4, 4, 4, 3, 3)),
        "length" to DwindleFixture(
            listOf(7, 5, 5, 4, 4, 4, 4, 4, 3, 3, 3, 3),
            listOf("swift", "sweat", "dry", "pod", "cutlet"),
            "tapalbfsazsuiwwetpaetfyotlifrducubev",
        ),
        "all-tiles" to DwindleFixture(
            listOf(4, 4, 4, 4, 4, 3, 3, 3, 3),
            listOf("swift", "sweat", "dry", "pod", "cutlet", "rebuilt", "blaze", "cliff"),
            "tapalbfsazsuiwwetpaetfyotlifrducubev",
        ),
    )

    /** A tile's token is its letter and its uses left, `S2`; `_` a tile gone. */
    private fun dwindleStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val fixture = dwindleFixtures.getValue(b.technique)
        val letters = fixture.letters ?: tokens.joinToString("") { it.take(1).lowercase() }
        tokens.forEachIndexed { i, t -> if (t != "_") assertEquals("${b.technique}: tile $i", letters[i], t[0].lowercaseChar()) }
        val counts = IntArray(tokens.size) { tokens[it].drop(1).toIntOrNull() ?: 0 }
        return DwindleTeacher.deduce(b.width, b.rows.size, letters, counts, fixture.lengths, fixture.tried, diagonal = false)
    }

    /** The points still to go, shown in the app's header line and said in each caption. */
    private val hexPointsToGo = mapOf("biggest" to 16, "hold-back" to 13, "rare" to 5)

    /**
     * Hexes sit in a staggered grid: `_` is a gap and `.` a hex already spent, so a hex's number is its place among the
     * other tokens. A spent hex's letter cannot matter, and the points still needed are the whole target of a state whose
     * spent hexes are one word worth nothing.
     */
    private fun hexwordsStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val cells = tokens.indices.filter { tokens[it] != "_" }
        val letters = cells.joinToString("") { if (tokens[it] == ".") "q" else tokens[it].lowercase() }
        val spent = cells.indices.filter { tokens[cells[it]] == "." }
        val state = HexwordsState(
            rows = b.rows.map { row -> row.count { it != "_" } },
            letters = letters,
            target = hexPointsToGo.getValue(b.technique),
            taken = listOf(HexTake("", spent)),
        )
        val taught = HexwordsTeacher.teach(state) ?: return null
        assertEquals("${b.technique}: the first letter", taught.word[0].uppercase(), b.marks("answer")[cells[taught.path[0]]])
        return step(taught, taught.focus.map { cells[it] }.toSet(), taught.targets.map { cells[it] }.toSet())
    }

    /** A step whose highlights have been mapped onto the board's squares, for a teacher that numbers them its own way. */
    private class Shown(step: TeachStep, override val focus: Set<Int>, override val targets: Set<Int>) : TeachStep by step

    /** Rows are the words, right-aligned; a placed letter's token is the letter and its digit (`E7`). */
    private fun cipherStep(b: Board): TeachStep? {
        val tokens = b.rows.map { row -> row.filter { it != "_" } }
        val sum = CipherSum(tokens.map { row -> row.joinToString("") { it.take(1).lowercase() } })
        val answer = (CipherLogic.prove(sum) as? Verdict.ExactlyOne)?.value ?: error("${b.technique}: a sum with one answer")
        val digits = sum.letters.indices.map { l ->
            tokens.flatten().firstOrNull { it[0].lowercaseChar() == sum.letters[l] }?.drop(1)?.toIntOrNull() ?: CipherLogic.EMPTY
        }
        digits.forEachIndexed { l, d -> if (d != CipherLogic.EMPTY) assertEquals("${b.technique}: a placed digit", answer[l], d) }
        val step = CipherTeacher.deduce(sum, digits) ?: return null
        assertEquals("${b.technique}: the digit placed", answer[step.letter], step.digit)
        val name = "${sum.letters[step.letter].uppercaseChar()}${step.digit}"
        assertEquals("${b.technique}: the move", setOf(name), b.marks("answer").values.toSet())
        fun square(tile: Int) = tile / CipherLogic.TILE_ROW * b.width + b.width - 1 - tile % CipherLogic.TILE_ROW
        return object : TeachStep {
            override val technique = step.technique
            override val nudge = step.nudge
            override val explanation = step.explanation
            override val focus = step.focus.map(::square).toSet()
            override val cited = step.cited.map(::square).toSet()
            override val targets = sum.tilesOf(step.letter).map(::square).toSet()
        }
    }

    /** Each column is a card: count, shading, colour, shape, then a dot if a set found (below) has used it. */
    private fun setsStep(b: Board): TeachStep? {
        val shadings = listOf("so", "ou", "st")
        val colours = listOf("am", "te", "vi")
        val shapes = listOf("ov", "di", "re")
        val found = listOf(listOf(0, 4, 7), listOf(3, 5, 8)) // The one board's sets already claimed.
        val cards = (0 until b.width).map { c ->
            val shown = b.rows.map { it[c] }
            Card(shown[0].toInt() - 1, shapes.indexOf(shown[3]), shadings.indexOf(shown[1]), colours.indexOf(shown[2]))
        }
        assertEquals("${b.technique}: the dots are the used cards", found.flatten().toSet(), (0 until b.width).filter { b.rows[4][it] == "•" }.toSet())
        val step = SetsTeacher.deduce(cards, found, emptyList()) ?: return null
        fun column(card: Int) = (0..3).map { it * b.width + card }
        for (card in step.targets) for (r in 0..3) assertEquals("${b.technique}: the card", b.rows[r][card], b.marks("answer")[r * b.width + card])
        return Shown(step, step.focus.flatMap(::column).toSet(), step.targets.flatMap(::column).toSet())
    }

    private val towerColours = listOf("bl", "go", "br", "te", "mi", "li", "wi", "cr")

    /** A row a guess, its pegs then a gap then its pips (`●` filled, `○` hollow); the last row the one being built. */
    private fun towerStep(b: Board): TeachStep? {
        val slots = (b.width - 1) / 2
        val colours = if (slots == 4) 5 else 6 // Expert (8 colours) has no board here.
        fun pegs(row: List<String>) = row.take(slots).map(towerColours::indexOf)
        val guesses = b.rows.dropLast(1).map(::pegs)
        val feedback = b.rows.dropLast(1).map { row -> Feedback(row.count { it == "●" }, row.count { it == "○" }) }
        val step = TowerTeacher.deduce(slots, colours, guesses, feedback, pegs(b.rows.last())) ?: return null
        (step.move as? TowerTeacher.Move.Place)?.let { m ->
            assertEquals("${b.technique}: the colour placed", towerColours[m.colour], b.marks("answer")[guesses.size * b.width + m.slot])
        }
        fun squares(i: Int): List<Int> = when {
            i >= TowerTeacher.CURRENT -> listOf(guesses.size * b.width + i - TowerTeacher.CURRENT)
            i >= TowerTeacher.PIPS -> (slots + 1 until b.width).map { (i - TowerTeacher.PIPS) * b.width + it }
            else -> listOf(i / slots * b.width + i % slots)
        }
        return Shown(step, step.focus.flatMap(::squares).toSet(), step.targets.flatMap(::squares).toSet())
    }

    /** A column a tube, top row its mouth; a pour's `answer` is where its sand lands. */
    private fun sandStep(b: Board): TeachStep? {
        val colours = Sand.colourNames.map { it.take(2) }
        val height = b.rows.size
        val tubes = (0 until b.width).map { t -> b.rows.reversed().map { it[t] }.filter { it != "." }.map(colours::indexOf) }
        val road = (SandLogic.solve(tubes, height, SandTeacher.HINT_BUDGET) as? SandLogic.Verdict.Solvable)?.path
            ?: error("${b.technique}: a board that sorts")
        val step = SandTeacher.deduce(tubes, height, road) ?: return null
        val landed = (tubes[step.to].size until step.after[step.to].size).map { (height - 1 - it) * b.width + step.to }
        for (square in landed) assertEquals("${b.technique}: the sand", colours[tubes[step.from].last()], b.marks("answer")[square])
        return Shown(step, step.focus.flatMap { t -> (0 until height).map { it * b.width + t } }.toSet(), landed.toSet())
    }

    @Test
    fun `every registered puzzle has a page, and every page is a puzzle`() {
        assertEquals(PuzzleRegistry.all.map { it.id }.toSet(), pages.map { it.id }.toSet())
    }

    @Test
    fun `the app's links name a real page and heading`() {
        fun parts(url: String) = Regex("https://knowhowit\\.com\\.au/daybook/guide/([^/#]+)/(?:#(.+))?").matchEntire(url)
            ?.destructured ?: error("not a guide link in the slash form: $url")
        for (puzzle in PuzzleRegistry.all) {
            assertEquals(puzzle.id to "", parts(guideUrl(puzzle.id)).let { (id, anchor) -> id to anchor })
            for (technique in praisedTechniquesOf(puzzle.id)) {
                val (id, anchor) = parts(guideUrl(puzzle.id, technique))
                val page = pages.first { it.id == id }
                assertTrue("$id has no heading for #$anchor", page.lines.any { it.startsWith("### $anchor |") })
            }
        }
    }

    @Test
    fun `a page has the sections in order and the puzzle's own name and tagline`() {
        for (p in pages) {
            val puzzle = PuzzleRegistry.all.first { it.id == p.id }
            val techniques = if (praisedTechniquesOf(p.id).isEmpty() && p.techniques.isEmpty()) emptyList() else listOf("Techniques")
            assertEquals(p.id, listOf("How to play", "Rules", "What changes per tier") + techniques, p.headings)
            assertEquals(p.id, "# ${puzzle.displayName}", p.lines.first { it.startsWith("# ") }.trim())
            assertEquals(p.id, "> ${puzzle.tagline}", p.lines.first { it.startsWith("> ") }.trim())
        }
    }

    @Test
    fun `a page says what TierNotes says`() {
        for (p in pages) {
            val items = p.section("What changes per tier").filter { it.startsWith("- ") }
            assertEquals("${p.id}: one line a tier", 3, items.size)
            listOf("Standard", "Hard", "Expert").zip(tierNotes(p.id)).forEachIndexed { i, (tier, note) ->
                assertTrue("${p.id}: \"${items[i]}\" should start \"- $tier: $note\"", items[i].startsWith("- $tier: $note"))
            }
        }
    }

    @Test
    fun `a page has the teacher's techniques, no others, in order, under the app's names`() {
        for (p in pages) {
            val names = techniqueNamesOf(p.id)
            val ids = p.techniques.map { it[0] }
            // A step in NOT_PRAISED (it only points at a word or a guess, or a hint never gives it): a page may leave it out.
            assertEquals("${p.id}: technique ids", names.keys.filter { it in ids || it in praisedTechniquesOf(p.id) }, ids)
            for ((id, shown) in p.techniques) {
                assertEquals("${p.id}#$id", names.getValue(id).replaceFirstChar { it.uppercase() }, shown)
            }
        }
    }

    @Test
    fun `every technique has a board, and the teacher gives that technique on it`() {
        for (p in pages) {
            val drawn = p.boards.map { it.technique }.toSet()
            assertTrue("${p.id}: a technique without a board", drawn.containsAll(praisedTechniquesOf(p.id)))
            assertTrue("${p.id}: a board outside a technique", p.techniques.map { it[0] }.containsAll(drawn))
            val teacher = teachers[p.id] ?: error("${p.id}: add it to GuideTest.teachers")
            for (b in p.boards) {
                val step = teacher(b) ?: error("${p.id}#${b.technique}: the teacher has no step here")
                assertEquals("${p.id}: the teacher's step on this board", b.technique, step.technique)
                assertEquals("${p.id}#${b.technique}: focus", step.focus, b.squares("focus"))
                assertEquals("${p.id}#${b.technique}: the move", step.targets, b.marks("answer").keys)
            }
        }
    }

    @Test
    fun `no page names a banned genre or trademark`() {
        // Capitalised only: a domino's pips, a lamp that lights up and a target's countdown are plain words.
        val exact = listOf("Pips", "Light Up", "Countdown", "Kami", "Mastermind")
        val anyCase = listOf(
            "minesweeper", "masyu", "killer sudoku", "rullo", "akari", "wordle", "kakuro", "number sums", "hashiwokakero",
            "hashi", "takuzu", "binairo", "einstein", "zebra puzzle", "fill-a-pix", "nikoli", "new york times", "nyt",
            "wordscapes", "water sort",
        )
        for (p in pages) {
            for (word in exact) assertTrue("${p.id} says $word", !Regex("\\b$word\\b").containsMatchIn(p.text))
            for (word in anyCase) {
                assertTrue("${p.id} says $word", !Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(p.text))
            }
        }
    }

    /**
     * For authors: `DAYBOOK_GUIDE_FIND=<puzzle id>` walks generated boards by hints and writes the first positions at
     * each technique, with the step's squares and words, to `app/build/guide-positions-<id>.txt`. Not a check.
     */
    @Test
    fun `find positions for a page's boards`() {
        val id = System.getenv("DAYBOOK_GUIDE_FIND")
        assumeTrue("set DAYBOOK_GUIDE_FIND=<puzzle id>", id != null)
        val puzzle = PuzzleRegistry.all.first { it.id == id }
        val found = HashMap<String, Int>()
        val out = StringBuilder()
        for (seed in 1L..400L) for (tier in Difficulty.entries) {
            var state = puzzle.generate(seed, tier)
            // Capped: following Sand's hints can go round for ever (Expert, seed 71).
            for (n in 1..200) {
                val step = puzzle.teach(state) ?: break
                // Codebreaker's "fallback" is a row that fits every score, never the answer, and most of its techniques come after one.
                if (step.mistake || (step.fallback && id != "tower")) break
                if (found.merge(step.technique, 1, Int::plus)!! <= 40) {
                    out.append("== ${step.technique}  seed $seed $tier\n")
                    out.append("focus ${step.focus.sorted()}\ncited ${step.cited.sorted()}\ntargets ${step.targets.sorted()}\n")
                    out.append("${step.nudge}\n${step.explanation}\n")
                    out.append(Json.encodeToString(PuzzleState.serializer(), state)).append('\n')
                }
                state = step.apply(state)
            }
        }
        File("build/guide-positions-$id.txt").writeText(out.toString())
    }
}
