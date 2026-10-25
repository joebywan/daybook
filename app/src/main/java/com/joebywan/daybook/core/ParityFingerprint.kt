package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.CipherState
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.InequalityState
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PointersState
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.RegionsState
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.SandState
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.SnapState
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.puzzles.WordWheelState

/**
 * One line per generated board, identical on the JVM and in the browser, so the two can be diffed:
 * `<id> <yyyy-mm-dd> <TIER> seed=<seed> <body>`. The body is whatever pins that puzzle's board and
 * answer; it is the format `BoardPinTest` pins. The web build prints these under
 * `?dump`, and `WebParityDumpTest` writes the JVM's side.
 */
object ParityFingerprint {

    fun line(type: PuzzleType, date: String, tier: Difficulty, seed: Long): String =
        "${type.id} $date ${tier.name} seed=$seed ${body(type.generate(seed, tier))}"

    /** A level's five boards at today's pool and the latest deal, one line each: `levels <id> L<n> <TIER> seed=<seed> <body>`. */
    fun levelLines(level: Int): List<String> = Levels.plan(level).map { b ->
        "levels " + line(PuzzleRegistry.byId(b.puzzleId)!!, "L$level", b.difficulty, b.seed)
    }

    fun body(state: PuzzleState): String = when (val s = state) {
        is KingsState -> "regions=${s.region.joinToString("")} kings=${s.solution.sorted().joinToString(",")}"
        is LitsState -> "regions=${s.region.joinToString("") { it.toString(36) }} " +
            "shading=${s.solution.joinToString("") { if (it) "1" else "0" }}"
        is MosaicState -> "${s.width}x${s.height} colours=${s.colours} limit=${s.limit} " +
            "cells=${s.cells.joinToString("")}"
        is AtomsState -> "n=${s.size} atoms=${s.atoms.joinToString(";") { "${it.row},${it.col},${it.bonds}" }} " +
            "pairs=${s.pairs.joinToString(";") { "${it.a}-${it.b}${if (it.horizontal) "h" else "v"}" }} " +
            "solution=${s.solution.joinToString("")}"
        is ShikakuState -> "${s.width}x${s.height} clues=" +
            s.clues.joinToString(",") { it?.toString() ?: "." } +
            " blocks=" + s.solution.joinToString(";") { "${it.r0},${it.c0},${it.r1},${it.c1}" }
        is SnapState -> "${s.width}x${s.height} waypoints=${s.waypoints.joinToString(",")}"
        is SudokuState -> "givens=" + s.cells.joinToString("") + " solution=" + s.solution.joinToString("")
        is MamboState -> "n=${s.size} givens=${s.givens.joinToString("") { if (it) "1" else "0" }} " +
            "links=${s.links.joinToString(",") { "${it.a}${if (it.same) "=" else "x"}${it.b}" }} " +
            "solution=${s.solution.joinToString("") { if (it == Sym.SUN) "S" else "M" }}"
        is PipesState -> "w=${s.width} h=${s.height} source=${s.source} " +
            "cells=${s.cells.joinToString("") { it.toString(16) }}"
        is SetsState -> "target=${s.target} " +
            "cards=${s.cards.joinToString(",") { c -> c.traits.joinToString("") }} " +
            "sets=${Sets.allSets(s.cards).joinToString(";") { it.joinToString(",") }}"
        is TowerState -> "slots=${s.slots} colours=${s.colours} max=${s.maxGuesses} " +
            "secret=${s.secret.joinToString("")}"
        is NonogramState -> "${s.width}x${s.height} solution=${s.solution}"
        is InequalityState -> "n=${s.size} givens=${s.givens.joinToString("") { if (it) "1" else "0" }} " +
            "signs=${s.signs.joinToString(",") { "${it.lo}<${it.hi}" }} solution=${s.solution.joinToString("")}"
        is ChessState -> "start=${s.start} last=${s.last} mateIn=${s.mateIn}"
        is TentsState -> "n=${s.size} trees=${s.trees.joinToString("") { if (it) "1" else "0" }} rows=${s.rowCounts} cols=${s.colCounts} solution=${s.solution.joinToString("") { if (it) "1" else "0" }}"
        is RegionsState -> "n=${s.size} region=${s.region.joinToString(",")} givens=${s.givens.joinToString("") { if (it) "1" else "0" }} solution=${s.solution.joinToString("")}"
        is CageState -> "givens=" + s.grid.cells.joinToString("") + " cages=" + s.cageOf.joinToString("") { it.toString(36) } +
            " sums=" + s.sums.joinToString(",") + " solution=" + s.grid.solution.joinToString("")
        is TargetState -> "start=${s.start.joinToString(",")} target=${s.target} par=${s.par}"
        is TallyState -> "n=${s.size} digits=${s.digits} keep=${s.keep}"
        is LampsState -> "n=${s.size} walls=${s.walls} solution=${s.solution}"
        is DominoState -> "${s.width}x${s.height} open=${s.open.joinToString("") { if (it) "1" else "0" }} " +
            "regions=${s.regionOf.joinToString(",")} rules=${s.rules.joinToString(",") { "${it.kind.name[0]}${it.n}" }} " +
            "tray=${s.tray.joinToString(",") { "${it.a}${it.b}" }} answer=${s.answer.joinToString(",") { "${it.first}-${it.second}" }}"
        is SandState -> "c=${s.colours} h=${s.height} tubes=${s.tubes.joinToString("/") { t -> t.joinToString("") }}"
        is PointersState -> "${s.width}x${s.height} arrows=" + s.arrows.joinToString(",") { it.joinToString(".") }
        is MinesState -> "${s.width}x${s.height} opening=${s.opening} mines=${s.mines} marks=${s.marks}"
        is LexiconState -> "length=${s.length} max=${s.maxGuesses} answer=${s.answer} opener=${s.opener}"
        is CipherState -> "words=${s.words.joinToString(",")} solution=${s.solution.joinToString("")}"
        is WordWheelState -> "wheel=${s.wheel} ${s.width}x${s.height} slots=" +
            s.slots.joinToString(";") { "${it.word}@${it.row},${it.col}${if (it.across) "a" else "d"}" }
    }
}
