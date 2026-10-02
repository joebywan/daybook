package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.SnapState
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.TowerState

/**
 * One line per generated board, identical on the JVM and in the browser, so the two can be diffed:
 * `<id> <yyyy-mm-dd> <TIER> seed=<seed> <body>`. The body is whatever pins that puzzle's board and
 * answer; it is the format each puzzle's web parity test pins. The web build prints these under
 * `?dump`, and `WebParityDumpTest` writes the JVM's side.
 */
object ParityFingerprint {

    fun line(type: PuzzleType, date: String, tier: Difficulty, seed: Long): String =
        "${type.id} $date ${tier.name} seed=$seed ${body(type.generate(seed, tier))}"

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
        is LexiconState -> "length=${s.length} max=${s.maxGuesses} answer=${s.answer}"
    }
}
