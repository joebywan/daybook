package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.PuzzleState

/** What Undo leaves behind: the board to show and the history that remains under it. */
class Undone(val state: PuzzleState, val history: List<PuzzleState>)

/**
 * One press of Undo. Selecting a cell or picking a card is a state of its own (so a tap is exactly
 * one state), which makes it a history entry too; taking back a move by stepping onto the entry
 * before it landed on the board *with the selection that move was made from*, and one more press
 * then changed nothing visible but that highlight. So Undo steps back to the nearest entry that is
 * a different board ([PuzzleType.sameBoard]) and shows it with its selection dropped
 * ([PuzzleType.withoutSelection]). With nothing different left it only drops the selection, and
 * returns null when there is not even that to do. Pure: it adds no history of its own.
 */
fun undone(puzzle: PuzzleType, current: PuzzleState, history: List<PuzzleState>): Undone? {
    for (i in history.indices.reversed()) {
        if (!puzzle.sameBoard(history[i], current)) {
            return Undone(puzzle.withoutSelection(history[i]), history.subList(0, i).toList())
        }
    }
    val bare = puzzle.withoutSelection(current)
    return if (bare == current) null else Undone(bare, emptyList())
}
