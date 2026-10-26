package com.joebywan.daybook

import com.joebywan.daybook.puzzles.PuzzleState
import org.junit.Assert.assertTrue

/**
 * Takes [step] from [start] until the board is solved or [step] returns null (the test's own reason to stop), and fails,
 * never stops quietly, once [limit] steps have not finished it. Each teaching test keeps its checks inside [step].
 */
fun <S : PuzzleState> hintWalk(start: S, limit: Int, step: (S) -> S?): S {
    var now = start
    var taken = 0
    while (!now.solved) {
        assertTrue("$limit steps did not finish the walk", ++taken <= limit)
        now = step(now) ?: return now
    }
    return now
}
