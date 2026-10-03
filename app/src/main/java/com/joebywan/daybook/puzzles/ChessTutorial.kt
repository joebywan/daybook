package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.TutorialFrame

/**
 * The Mate walkthrough, played on the real board: tap a piece, tap its square, one emitted state per move
 * (selection and the promotion picker are the board's own transient state). Four hand-built positions, each with
 * one forced mate that `ChessTutorialTest` proves with an independent minimax. Highlight indices: squares 0..63
 * (a1 = 0, rank * 8 + file), promotion buttons 100 + kind (queen = 105).
 */
internal object ChessTutorial {
    /** White rook a1, Black king g8 behind f7 g7 h7: Ra8#. */
    const val BACK_RANK = "6k1/5ppp/8/8/8/8/8/R3K3 w - -"

    /** Kings a5 / a8 and a rook on d1: 1.Kb6 Kb8 (the only move) 2.Rd8#. */
    const val LADDER = "k7/8/8/K7/8/8/8/3R4 w - -"

    /** Pawn h7, Kf5 against Kh6: 1.h8=Q#, while h8=R+ lets the king out to g7. */
    const val PROMOTION = "8/7P/7k/5K2/8/8/8/8 w - -"

    /** Free play: Black's king in the corner, a king and rook to mate it in 2. */
    const val FREE = "7k/8/8/5K2/8/8/8/R7 w - -"

    fun sq(name: String) = (name[1] - '1') * 8 + (name[0] - 'a')

    private fun state(start: String, mateIn: Int, vararg played: String) =
        ChessState(start, "", mateIn, played.toList(), played.size)

    /** Accepts exactly the board that [start] reaches by playing [played]. */
    private fun only(start: String, mateIn: Int, vararg played: String): (PuzzleState) -> Boolean {
        val want = state(start, mateIn, *played)
        return { it is ChessState && it.start == want.start && it.mateIn == mateIn && it.played == want.played }
    }

    private fun hl(strong: Set<String> = emptySet(), soft: Set<String> = emptySet(), warning: Boolean = false) =
        BoardHighlight(strong.map(::sq).toSet(), soft.map(::sq).toSet(), warning)

    val frames: List<TutorialFrame> by lazy {
        val queen = 100 + 5
        listOf(
            TutorialFrame(
                state(BACK_RANK, 1),
                "Mate in 1: you play White and checkmate Black's king on g8. Its own pawns on f7, g7 and h7 box it in, and your rook on a1 can reach the back rank.",
                hl(strong = setOf("g8"), soft = setOf("f7", "g7", "h7", "a1")),
            ),
            TutorialFrame(
                state(BACK_RANK, 1),
                "Tap the rook on a1, then tap a8. Two taps make one move: the first picks the piece up, the second puts it down.",
                hl(strong = setOf("a1"), soft = setOf("a8")),
                accepts = only(BACK_RANK, 1, "a1a8"),
                retry = "Tap the rook on a1, then a8.",
                done = "Ra8#. The rook checks along the 8th rank.",
            ),
            TutorialFrame(
                state(BACK_RANK, 1, "a1a8"),
                "Checkmate. The king is in check, f8 and h8 lie on the rook's line, and its own pawns hold f7, g7 and h7. Nothing can block or capture: a back-rank mate.",
                hl(strong = setOf("a8", "g8"), soft = setOf("f8", "h8", "f7", "g7", "h7")),
            ),
            TutorialFrame(
                state(LADDER, 2),
                "Mate in 2: you move, Black must reply, then you mate. Black's king on a8 has a7, b7 and b8, and your king on a5 guards none of them yet.",
                hl(strong = setOf("a8"), soft = setOf("a7", "b7", "b8", "a5")),
            ),
            TutorialFrame(
                state(LADDER, 2, "d1d7"),
                "Rd7 looks strong, but after Kb8 you have no forced mate left. In a real game the hint says so, and Undo takes the move back.",
                hl(strong = setOf("d7"), soft = setOf("b8"), warning = true),
            ),
            TutorialFrame(
                state(LADDER, 2),
                "The quiet move is the key: tap your king on a5, then b6. It guards a7 and b7, so Black's king will have just one move.",
                hl(strong = setOf("a5"), soft = setOf("b6", "a7", "b7")),
                accepts = only(LADDER, 2, "a5b6"),
                retry = "Tap the king on a5, then b6.",
                done = "Kb6. Black has exactly one legal move, Kb8, and has just played it.",
            ),
            TutorialFrame(
                state(LADDER, 2, "a5b6"),
                "Black had to play Kb8. Now tap the rook on d1, then d8. Your king still guards a7, b7 and c7, and the rook takes a8 and c8.",
                hl(strong = setOf("d1"), soft = setOf("d8", "a7", "b7", "c7")),
                accepts = only(LADDER, 2, "a5b6", "d1d8"),
                retry = "Tap the rook on d1, then d8.",
                done = "Rd8#. Every square the king could use is covered.",
            ),
            TutorialFrame(
                state(PROMOTION, 1),
                "A pawn reaching the last rank becomes a piece. Tap h7, tap h8, then pick the queen: a rook would let the king escape to g7.",
                hl(strong = setOf("h7"), soft = setOf("h8", "g7")).let { it.copy(soft = it.soft + queen) },
                accepts = only(PROMOTION, 1, "h7h8q"),
                retry = "Tap h7, then h8, then the queen.",
                done = "h8=Q#. The queen covers g7 and the h-file; your king covers g5 and g6.",
            ),
            TutorialFrame(
                state(FREE, 2),
                "Your turn: mate in 2. Move any piece, and ask for a hint when stuck. The first tap nudges, the second explains.",
                freePlay = true,
            ),
        )
    }
}
