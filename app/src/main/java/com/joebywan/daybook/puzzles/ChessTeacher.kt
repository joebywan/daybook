package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction

/**
 * Mate, taught by the prover. Indices in `focus` / `cited` / `targets` are the board's own squares, 0..63
 * (a1 = 0, rank * 8 + file), plus the four promotion-picker buttons at 100 + kind (2 N, 3 B, 4 R, 5 Q).
 *
 * [deduce] takes only the visible position and the number of moves left; the prover is the reasoning, so there is no
 * stored answer in reach and NO FALLBACK EXISTS to measure (as Nonogram): if the player's moves still allow a forced
 * mate, [ChessRules.forcedMate] names every key and one of them is explained. A step is MATE_IN_ONE, FORCING_KEY or
 * QUIET_KEY (the same, for a key that is not a check).
 *
 * A mistake is a move after which the defence has an answer to every plan ([ChessRules.forcedMate] is `None`: proved,
 * never `Truncated`, and never "differs from the data"). Mistakes outrank steps. A legal move that keeps a forced
 * mate is never one, so a second mating line, or a non-shortest one, is accepted. Every loop is in move or square
 * order; the one hash map in the prover is only looked up.
 */
internal object ChessTeacher {
    const val MATE_IN_ONE = "mate-in-one"
    const val FORCING_KEY = "forcing-key"
    const val QUIET_KEY = "quiet-key"
    const val MISTAKE = "mistake"
    val TECHNIQUES = listOf(MATE_IN_ONE, FORCING_KEY, QUIET_KEY)

    const val MAX_EXPLANATION = 200
    const val MAX_NUDGE = 70

    /** [move] is the key to play, or (for a mistake) the move to take back, the player's move number [undoTo] (0-based). */
    class Step(
        val technique: String,
        val move: Int,
        val keys: List<Int>,
        val undoTo: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val targets: Set<Int>
            get() = if (promo(move) != 0 && technique != MISTAKE) setOf(fromSq(move), toSq(move), 100 + promo(move))
            else setOf(fromSq(move), toSq(move))
    }

    private fun fromSq(m: Int) = m and 63
    private fun toSq(m: Int) = (m shr 6) and 63
    private fun promo(m: Int) = m shr 12

    private fun sqName(s: Int) = "${'a' + s % 8}${'1' + s / 8}"
    private val KIND = listOf("", "pawn", "knight", "bishop", "rook", "queen", "king")
    private fun side(white: Boolean) = if (white) "White" else "Black"
    /** Black's moves are written "...Kg8"; White's plain. [p] is the position the move is made in. */
    private fun dots(p: ChessPosition) = if (p.whiteToMove) "" else "..."
    private fun abs(x: Int) = if (x < 0) -x else x

    /** "Rd1", "pawn d4": a piece and its square, for naming what covers a square. */
    private fun named(p: ChessPosition, s: Int): String {
        val k = kind(p, s)
        return if (k == 1) "pawn ${sqName(s)}" else "${" PNBRQK"[k]}${sqName(s)}"
    }

    private fun kind(p: ChessPosition, s: Int): Int = p.pieceAt(s).let { if (it < 0) -it else it }

    // ---- replaying what the player has done ------------------------------------------------------------

    /** The board the player faces. A mistake is the first move that gave the mate up, with the board around it. */
    class Line(
        val position: ChessPosition,
        val remaining: Int,
        val mated: Boolean,
        val mistakeIndex: Int,
        val before: ChessPosition?,
        val bad: Int,
        val after: ChessPosition?,
        val movesLeftAfter: Int,
    )

    fun replay(s: ChessState): Line? {
        var pos = ChessPosition.fromFen(s.start)
        for (i in s.played.indices) {
            val m = ChessRules.parseUci(pos, s.played[i]) ?: return null
            val c = ChessRules.play(pos, m)
            val rem = s.mateIn - (i + 1)
            if (ChessRules.isCheckmate(c)) return Line(c, 0, true, -1, null, 0, null, 0)
            val reply = if (rem > 0) ChessRules.defence(c, rem) else null
            val next = if (reply != null) ChessRules.play(c, reply) else null
            val keeps = next != null &&
                ChessRules.forcedMate(next, rem, Int.MAX_VALUE) !is ChessRules.Mate.None
            if (!keeps) return Line(c, rem, false, i, pos, m, c, rem)
            pos = next
        }
        return Line(pos, s.mateIn - s.played.size, false, -1, null, 0, null, 0)
    }

    fun isSolved(s: ChessState): Boolean = s.played.size <= s.mateIn && replay(s)?.mated == true

    // ---- the whole hint ----------------------------------------------------------------------------------

    fun teach(s: ChessState): Deduction? {
        val line = replay(s) ?: return null
        if (line.mated) return null
        val step = (if (line.mistakeIndex >= 0) mistake(line) else deduce(line.position, line.remaining)) ?: return null
        val base = s.played
        val index = if (step.technique == MISTAKE) line.mistakeIndex else base.size
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == MISTAKE,
            fallback = false,
            applyTo = { now ->
                now as ChessState
                if (step.technique == MISTAKE) now.copy(played = now.played.take(index), moves = minOf(now.moves, index))
                else now.copy(played = now.played + ChessRules.uci(step.move), moves = now.moves + 1)
            },
            reachedBy = { now ->
                now as ChessState
                if (step.technique == MISTAKE) now.played.take(index + 1) != base.take(index + 1)
                else now.played.size > index && now.played.take(index) == base &&
                    step.keys.any { ChessRules.uci(it) == now.played[index] }
            },
        )
    }

    private fun mistake(l: Line): Step {
        val before = l.before!!
        val after = l.after!!
        val san = ChessRules.san(before, l.bad)
        val who = side(!before.whiteToMove)
        val why = when {
            ChessRules.isStalemate(after) -> "$san is stalemate: $who has no move but is not in check. Take it back."
            l.movesLeftAfter <= 0 -> "$san was the last move allowed and it is not mate. Take it back."
            else -> {
                val r = ChessRules.defence(after, l.movesLeftAfter)
                val reply = if (r == null) "" else "after ${dots(after)}${ChessRules.san(after, r)} "
                "$san lets $who escape: ${reply}there is no forced mate in ${l.movesLeftAfter}. Take it back."
            }
        }
        val f = setOf(fromSq(l.bad), toSq(l.bad))
        return Step(MISTAKE, l.bad, emptyList(), l.mistakeIndex, f, f,
            "That move gives up the mate. Look at it again.", fit(why))
    }

    /** One step for the side to move, who has [remaining] moves; null when no forced mate is left. */
    fun deduce(position: ChessPosition, remaining: Int): Step? {
        val res = ChessRules.forcedMate(position, remaining, Int.MAX_VALUE) as? ChessRules.Mate.Forced ?: return null
        if (res.keys.isEmpty()) return null
        val white = position.whiteToMove
        val key = res.keys.firstOrNull { ChessRules.inCheck(ChessRules.play(position, it)) } ?: res.keys.first()
        val c = ChessRules.play(position, key)
        val king = kingSquare(c, !white)
        val piece = "${KIND[kind(position, fromSq(key))]} on ${sqName(fromSq(key))}"
        return if (res.inMoves == 1) {
            val cited = mutableSetOf(king)
            val text = mateText(position, key, c, king, cited)
            Step(MATE_IN_ONE, key, res.keys, -1, setOf(king, fromSq(key)), cited,
                "One move ends it. Where can that king go?", fit(text))
        } else {
            val check = ChessRules.inCheck(c)
            val text = forcingText(position, key, c, res.inMoves - 1, check)
            Step(if (check) FORCING_KEY else QUIET_KEY, key, res.keys, -1, setOf(fromSq(key)), setOf(king, toSq(key)),
                "Look at the $piece.", fit(text))
        }
    }

    private fun kingSquare(p: ChessPosition, white: Boolean): Int {
        val k = if (white) 6 else -6
        for (s in 0..63) if (p.pieceAt(s) == k) return s
        return 0
    }

    /** Never longer than the panel. */
    private fun fit(t: String): String =
        if (t.length <= MAX_EXPLANATION) t else t.take(MAX_EXPLANATION - 1).trimEnd() + "."

    // ---- why a move is mate ------------------------------------------------------------------------------

    private fun mateText(p: ChessPosition, m: Int, c: ChessPosition, king: Int, cited: MutableSet<Int>): String {
        val white = p.whiteToMove
        val san = ChessRules.san(p, m)
        val ownSign = if (white) -1 else 1
        // The king's own square must not shield the squares behind it from a checking line.
        val bare = ChessPosition(IntArray(64) { if (it == king) 0 else c.pieceAt(it) }, c.whiteToMove, c.castling, c.ep)
        val own = ArrayList<Int>()
        val covered = ArrayList<Pair<Int, Int>>() // square, first piece covering it
        for (s in 0..63) {
            if (s == king || abs(s % 8 - king % 8) > 1 || abs(s / 8 - king / 8) > 1) continue
            if (c.pieceAt(s) * ownSign > 0) own.add(s)
            else ChessRules.attackers(bare, s, white).firstOrNull()?.let { covered.add(s to it) }
        }
        val checkers = ChessRules.attackers(c, king, white)
        val end = if (checkers.size > 1) "a double check, so only the king could move"
        else "nothing can capture the checker or block it"
        cited.addAll(own); cited.addAll(covered.map { it.first }); cited.addAll(covered.map { it.second })
        cited.addAll(checkers)
        val ownPart = if (own.isEmpty()) "" else "own pieces on ${own.joinToString(" ") { sqName(it) }}"
        val covPart = covered.joinToString(", ") { "${sqName(it.first)} by ${named(c, it.second)}" }
        val full = "$san is mate: check; " +
            listOf(ownPart, covPart).filter { it.isNotEmpty() }.joinToString("; ") + "; $end."
        if (full.length <= MAX_EXPLANATION) return full
        val mid = "$san is mate: check, ${own.size} neighbouring squares blocked by its own pieces, " +
            "${covered.size} covered, and $end."
        if (mid.length <= MAX_EXPLANATION) return mid
        return "$san is mate: the king is in check with no safe square, and $end."
    }

    // ---- why a key forces mate ---------------------------------------------------------------------------

    private fun forcingText(p: ChessPosition, m: Int, c: ChessPosition, left: Int, check: Boolean): String {
        val san = ChessRules.san(p, m)
        val replies = ChessRules.legalMoves(c)
        val who = side(c.whiteToMove)
        // the likeliest escapes first: checks, captures, then the rest, each group in move order
        val checks = replies.filter { ChessRules.inCheck(ChessRules.play(c, it)) }
        val rest = replies.filter { it !in checks }
        val ranked = checks + rest.filter { c.pieceAt(toSq(it)) != 0 } + rest.filter { c.pieceAt(toSq(it)) == 0 }
        val shown = ranked.take(2).mapNotNull { r ->
            val d = ChessRules.play(c, r)
            val res = ChessRules.forcedMate(d, left, Int.MAX_VALUE) as? ChessRules.Mate.Forced ?: return@mapNotNull null
            val first = dots(c) + ChessRules.san(c, r)
            if (res.inMoves == 1) "$first ${ChessRules.san(d, res.keys.first())}"
            else "$first then mate in ${res.inMoves}"
        }
        val n = replies.size
        val head = (if (check) "$san is check" else "$san is quiet, not a check,") +
            " and leaves $who ${if (n == 1) "1 reply" else "$n replies"}; " +
            (if (n == 1) "it allows" else "each allows") +
            if (left == 1) " mate next move" else " mate within $left more moves"
        return head + if (shown.isEmpty()) "." else ", e.g. ${shown.joinToString(", ")}."
    }
}
