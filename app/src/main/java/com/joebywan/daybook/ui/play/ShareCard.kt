package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.LexiconMark
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.PointersLogic
import com.joebywan.daybook.puzzles.PointersState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.TowerState

/**
 * The play log: what happened in a game, in order, one character per event, kept in [com.joebywan.daybook.data.SavedGame.log]
 * beside the undo stack. It is never `PuzzleState`. The share graphic is read from it ([shareCard]).
 *
 * `m` a move, `w` a claim that was not a set (Sets: no board change, so Undo never takes it back), `h` a hint opened
 * (one per counted hint), `u` Undo took back the latest live move (or, with none since, the latest restart, handing its
 * moves back), `r` Restart took back every live move.
 */
object PlayLog {
    const val MOVE = 'm'
    const val WRONG = 'w'
    const val HINT = 'h'
    const val UNDO = 'u'
    const val RESTART = 'r'

    /**
     * What a push from [prev] to [next] adds to the log. A move is a change of board ([PuzzleType.sameBoard]), so picking
     * a cell or a card is not one; in Lexicon and Tower only a submitted guess is (typing a letter or a peg is not), and in
     * Target a push that takes operations back ("Show me" on a mistake) is that many undos.
     */
    fun step(puzzle: PuzzleType, prev: PuzzleState, next: PuzzleState): String = when {
        next is SetsState && next.lastWrong -> WRONG.toString()
        next is LexiconState && prev is LexiconState -> if (next.guesses.size > prev.guesses.size) MOVE.toString() else ""
        next is TowerState && prev is TowerState -> if (next.guesses.size > prev.guesses.size) MOVE.toString() else ""
        next is TargetState && prev is TargetState -> (next.log.size - prev.log.size).let {
            if (it > 0) MOVE.toString() else UNDO.toString().repeat(-it)
        }
        puzzle.sameBoard(prev, next) -> ""
        else -> MOVE.toString()
    }

    /** One move the log records: a hint opened since the move before makes it [hinted]. */
    class Played(val wrong: Boolean, val hinted: Boolean) {
        var undone = false
            internal set
    }

    /** Every move in [log], in order, each knowing whether it was later taken back. */
    fun played(log: String): List<Played> {
        val all = mutableListOf<Played>()
        val live = ArrayDeque<Played>()
        // What each restart took back, so an Undo of the restart can hand it back.
        val restarts = ArrayDeque<List<Played>>()
        var hint = false
        for (c in log) when (c) {
            HINT -> hint = true
            MOVE, WRONG -> {
                val p = Played(c == WRONG, hint)
                hint = false
                all += p
                if (c == MOVE) live.addLast(p)
            }
            UNDO -> if (live.isNotEmpty()) {
                live.removeLast().undone = true
            } else {
                restarts.removeLastOrNull()?.let { back -> back.forEach { it.undone = false }; live.addAll(back) }
            }
            RESTART -> { live.forEach { it.undone = true }; restarts.addLast(live.toList()); live.clear() }
        }
        return all
    }
}

/** One symbol of the graphic: its emoji (or pip) in the shared text; the preview draws it natively. */
enum class Mark(val text: String) {
    // Squares in rising severity: a strip square shows the worst of its moves.
    GREEN("🟩"), WHITE("⬜"), YELLOW("🟨"), RED("🟥"),
    /** Tower: right colour, right slot. */
    EXACT("●"),
    /** Tower: right colour, wrong slot. */
    CLOSE("○"),
    /** Tower: a peg that scored nothing. */
    NONE("·"),
    /** Pointers: a life left. */
    HEART("❤️"),
    /** Pointers: a life lost to a bump. */
    LOST("🖤"),
}

/** A line of the graphic: [marks], then a red square when a hint came before it ([hinted]), then [tail] as written. */
data class ShareLine(val marks: List<Mark>, val hinted: Boolean = false, val tail: String = "") {
    fun text(spaced: Boolean): String =
        marks.joinToString(if (spaced) " " else "") { it.text } + (if (hinted) " " + Mark.RED.text else "") + tail
}

/** Everything the share preview shows and the text it sends. Pure data. */
data class ShareCard(
    val header: String,
    val lines: List<ShareLine>,
    val stats: String,
    val key: List<Pair<Mark, String>>,
    val nudge: String,
    /** Tower's pips are spaced apart in the text. */
    val spaced: Boolean = false,
) {
    val text: String
        get() = buildString {
            append(header).append('\n')
            lines.forEach { append(it.text(spaced)).append('\n') }
            append(stats).append('\n')
            append(key.joinToString("  ") { "${it.first.text} ${it.second}" }).append('\n')
            append(SHARE_URL)
        }
}

private const val HINTED_NUDGE = "The hints got you there. Try tomorrow's with fewer."

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/** Worst first: a hint, then a taken-back move, then a claim that was not a set. */
private fun PlayLog.Played.mark() = when {
    hinted -> Mark.RED
    undone -> Mark.YELLOW
    wrong -> Mark.WHITE
    else -> Mark.GREEN
}

/**
 * Ten squares, each a tenth of [moves] in order (one per move under ten), each the worst of its tenth:
 * red (a hint) over yellow (undone) over green.
 */
internal fun strip(moves: List<PlayLog.Played>): List<Mark> {
    val n = moves.size
    if (n <= 10) return moves.map { it.mark() }
    return List(10) { i -> moves.subList(i * n / 10, (i + 1) * n / 10).maxOf { it.mark() } }
}

/**
 * The share card for a solve: [puzzle] finished in [state], with the play [log] ([PlayLog]). [date] is the daily's date
 * already formatted, null for a random board or a [level] board (whose header names the level where the daily has the date); [hints] is the game's counted hints (the log may have started part way,
 * from an older save). Spoiler-free: marks, pips and counts only, never a square, piece, word or number of the board.
 */
fun shareCard(
    puzzle: PuzzleType, tier: Difficulty, date: String?, clock: String, streak: Int,
    state: PuzzleState, log: String, hints: Int, level: Int? = null,
): ShareCard {
    val moves = PlayLog.played(log)
    val undone = moves.count { it.undone }
    var suffix = ""
    val lines: List<ShareLine>
    val key: List<Pair<Mark, String>>
    val nudge: String
    when (state) {
        is LexiconState, is TowerState -> {
            // Rows are the final guesses; the log's live moves are the last of them (an older save logged none before).
            val guessed = if (state is LexiconState) state.guesses.size else (state as TowerState).guesses.size
            val live = moves.filter { !it.undone && !it.wrong }
            val offset = guessed - live.size
            fun hinted(row: Int) = live.getOrNull(row - offset)?.hinted == true
            if (state is LexiconState) {
                suffix = " · $guessed/${state.maxGuesses}"
                lines = state.allMarks.mapIndexed { row, marks ->
                    ShareLine(marks.map {
                        when (it) { LexiconMark.CORRECT -> Mark.GREEN; LexiconMark.PRESENT -> Mark.YELLOW; else -> Mark.WHITE }
                    }, hinted(row))
                }
                key = listOf(Mark.GREEN to "right place", Mark.YELLOW to "wrong place", Mark.RED to "hint")
            } else {
                state as TowerState
                suffix = " · $guessed/${state.maxGuesses}"
                lines = state.guesses.mapIndexed { row, guess ->
                    val f = state.score(guess)
                    ShareLine(
                        List(f.exact) { Mark.EXACT } + List(f.misplaced) { Mark.CLOSE } +
                            List(state.slots - f.exact - f.misplaced) { Mark.NONE },
                        hinted(row),
                    )
                }
                key = listOf(Mark.EXACT to "right colour and slot", Mark.CLOSE to "right colour", Mark.RED to "hint")
            }
            nudge = when {
                hints > 0 -> HINTED_NUDGE
                guessed == 1 -> "First guess. Nothing to improve on."
                else -> "No hints, ${plural(guessed, "guess", "guesses")}. Fewer tomorrow?"
            }
        }
        is SetsState -> {
            lines = listOf(ShareLine(moves.map { it.mark() }))
            key = listOf(Mark.GREEN to "a set", Mark.WHITE to "not a set", Mark.YELLOW to "undone", Mark.RED to "hint")
            val off = undone + moves.count { it.wrong }
            nudge = when {
                hints > 0 -> HINTED_NUDGE
                off > 0 -> "No hints. ${plural(off, "claim")} off a clean run. Tomorrow?"
                else -> "Every claim a set. Nothing to improve on."
            }
        }
        is ChessState -> {
            suffix = " · mate in ${state.mateIn}"
            lines = listOf(ShareLine(moves.map { it.mark() }, tail = "#"))
            key = listOf(Mark.GREEN to "move", Mark.YELLOW to "taken back", Mark.RED to "hint")
            nudge = when {
                hints > 0 -> HINTED_NUDGE
                undone > 0 -> "No hints. ${plural(undone, "move")} taken back from a clean mate. Tomorrow?"
                else -> "A clean mate. Nothing to improve on."
            }
        }
        is TargetState -> {
            val steps = state.log.size
            lines = listOf(ShareLine(moves.map { it.mark() }, tail = "   ${plural(steps, "step")} · par ${state.par}"))
            key = listOf(Mark.GREEN to "step", Mark.YELLOW to "undone", Mark.RED to "hint")
            nudge = when {
                hints > 0 -> HINTED_NUDGE
                undone > 0 -> "No hints. $undone undone from a clean run. Tomorrow?"
                steps > state.par -> "No hints, nothing undone. Par is ${state.par}: tomorrow?"
                else -> "On par, nothing undone. Nothing to improve on."
            }
        }
        // No hints and no undo: the lives are the whole story.
        is PointersState -> {
            val lost = PointersLogic.LIVES - state.lives
            lines = listOf(ShareLine(List(state.lives) { Mark.HEART } + List(lost) { Mark.LOST }))
            key = listOf(Mark.HEART to "life left", Mark.LOST to "life lost")
            nudge = if (lost == 0) "Not a single bump." else "${plural(lost, "bump")}. None tomorrow?"
        }
        else -> {
            lines = listOf(ShareLine(strip(moves)))
            key = listOf(Mark.GREEN to "own move", Mark.YELLOW to "undone", Mark.RED to "hint")
            nudge = when {
                hints > 0 -> HINTED_NUDGE
                undone > 0 -> "No hints. $undone undone from a clean strip. Tomorrow?"
                else -> "A clean solve. Nothing to improve on."
            }
        }
    }
    return ShareCard(
        header = if (level != null) "Daybook · Level $level · ${puzzle.displayName} · ${tier.label}$suffix"
            else "Daybook · ${puzzle.displayName} · ${tier.label} · ${date ?: "random board"}$suffix",
        lines = lines,
        stats = clock + if (streak >= 2) " · $streak day streak" else "",
        key = key,
        nudge = nudge,
        spaced = state is TowerState,
    )
}
