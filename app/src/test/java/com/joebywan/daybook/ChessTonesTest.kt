package com.joebywan.daybook

import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.chessMoveTone
import com.joebywan.daybook.puzzles.chessVerdictTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which of Mate's sounds plays when, written out by hand rather than derived from the code it checks. */
class ChessTonesTest {
    @Test fun everyMoveClimbsTheSameWhateverItsWorth() {
        // Mate in 4: the three moves before the last are the phrase's bottom, middle and top; mate in 2 has one note.
        assertEquals(listOf(0, 3, 7).map(Tones::step), (1..3).map { chessMoveTone(true, false, it, 4) })
        assertEquals(Tones.step(0), chessMoveTone(true, false, 1, 2))
        // The fourth move of a mate in 4 that did not mate still sounds, at the top (its verdict follows).
        assertEquals(Tones.step(7), chessMoveTone(true, false, 4, 4))
    }

    @Test fun theMatingMoveIsTheWinChimesAlone() = assertNull(chessMoveTone(true, true, 2, 2))

    @Test fun aStateNotPushedIsSilent() {
        assertNull(chessMoveTone(false, false, 1, 3))
        assertNull(chessVerdictTone(false, ChessRules.Mate.None))
    }

    @Test fun onlyAProvedLossBaBongs() {
        assertEquals(Tones.miss(), chessVerdictTone(true, ChessRules.Mate.None))
        assertNull(chessVerdictTone(true, ChessRules.Mate.Truncated))
        assertNull(chessVerdictTone(true, ChessRules.Mate.Forced(listOf(0), 2)))
        assertNull(chessVerdictTone(true, null)) // solved
    }
}
