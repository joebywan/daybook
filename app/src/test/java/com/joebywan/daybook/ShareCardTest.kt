package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.Lexicon
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.TargetMove
import com.joebywan.daybook.puzzles.TargetOp
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.ui.play.PlayLog
import com.joebywan.daybook.ui.play.SHARE_URL
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.WordWheelState
import com.joebywan.daybook.ui.play.dayShareCard
import com.joebywan.daybook.ui.play.shareCard
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The share text, written out in full for each format, so a change to any of them is seen here first. */
class ShareCardTest {

    private val sudoku = Sudoku.generate(1L, Difficulty.EXPERT) as SudokuState

    private fun card(
        puzzle: PuzzleType, state: PuzzleState, log: String, hints: Int = log.count { it == 'h' },
        tier: Difficulty = Difficulty.EXPERT, date: String? = "22 Oct", streak: Int = 6,
    ) = shareCard(puzzle, tier, date, "12:04", streak, state, log, hints)

    @Test fun `a clean strip is ten green squares`() {
        val c = card(Sudoku, sudoku, "m".repeat(23))
        assertEquals(
            "Daybook · Sudoku · Expert · 22 Oct\n🟩🟩🟩🟩🟩🟩🟩🟩🟩🟩\n12:04 · 6 day streak\n" +
                "🟩 own move  🟨 undone  🟥 hint\n$SHARE_URL",
            c.text,
        )
        assertEquals("A clean solve. Nothing to improve on.", c.nudge)
    }

    @Test fun `each strip square is the worst of its tenth, and a random board says so`() {
        // 20 moves, two a square: move 2 undone, move 4 after a hint.
        val c = card(Sudoku, sudoku, "mmmumhm" + "m".repeat(15), date = null, streak = 1)
        assertEquals(
            "Daybook · Sudoku · Expert · random board\n🟩🟨🟥🟩🟩🟩🟩🟩🟩🟩\n12:04 · 1 hint\n" +
                "🟩 own move  🟨 undone  🟥 hint\n$SHARE_URL",
            c.text,
        )
        assertEquals("The hints got you there. Try the next one with fewer.", c.nudge)
        assertEquals("The hints got you there. Try tomorrow's with fewer.", card(Sudoku, sudoku, "mhm").nudge)
    }

    @Test fun `the stats carry the hints, clock first, and none means nothing`() {
        assertEquals("12:04 · 2 hints · 6 day streak", card(Sudoku, sudoku, "mhmhm").stats)
        assertEquals("12:04 · 1 hint", card(Sudoku, sudoku, "mhm", streak = 1).stats)
        // The count is the game's, not the log's: an older save logged none of them.
        assertEquals("12:04 · 3 hints · 6 day streak", card(Sudoku, sudoku, "mmm", hints = 3).stats)
        val s = WordWheel.generate(1L, Difficulty.STANDARD) as WordWheelState
        assertEquals("12:04 · 1 hint · +1 bonus word · 6 day streak", card(WordWheel, s.copy(bonus = listOf("one")), "hm").stats)
    }

    @Test fun `a board that is not a daily never says tomorrow`() {
        val lex = LexiconState(5, 6, "crane", listOf("slate", "pride", "crane"))
        val op = TargetMove(0, 1, 2, 3, TargetOp.Add, 5)
        val target = TargetState(listOf(2, 3, 4, 5, 25, 50), target = 999, par = 2, log = listOf(op, op, op))
        assertEquals("No hints. 1 undone from a clean strip. Next time?", card(Sudoku, sudoku, "mmum", date = null).nudge)
        assertEquals("No hints, 3 guesses. Fewer next time?", card(Lexicon, lex, "mmm", date = null).nudge)
        assertEquals("No hints, nothing undone. Par is 2: next time?", card(Target, target, "mmm", date = null).nudge)
        val random = listOf(
            card(Sudoku, sudoku, "mhm", date = null), card(Sudoku, sudoku, "mmum", date = null),
            card(Lexicon, lex, "mmm", date = null), card(Target, target, "mmm", date = null),
            card(Target, target, "mmmum", date = null), card(Chess, ChessState(start = "8/8/8/8/8/8/8/8 w - - 0 1", mateIn = 2), "mmum", date = null),
            card(Sets, SetsState(emptyList(), 0, emptyList(), emptyList()), "mwm", date = null),
            shareCard(Sudoku, Difficulty.HARD, null, "3:10", 0, sudoku, "mhm", 1, level = 14),
        )
        random.forEach { assertFalse(it.nudge, it.nudge.contains("tomorrow", ignoreCase = true)) }
    }

    @Test fun `the nudge is for the preview, never the shared text`() {
        val cards = listOf(
            card(Sudoku, sudoku, "mhm"), card(Sudoku, sudoku, "mmum"), card(Sudoku, sudoku, "mmm"),
            card(Sudoku, sudoku, "mhm", date = null), card(Sudoku, sudoku, "mmum", date = null),
            card(Lexicon, LexiconState(5, 6, "crane", listOf("slate", "crane")), "mm"),
        )
        cards.forEach { assertFalse(it.text, it.nudge in it.text) }
        cards.forEach { assertFalse(it.text, it.text.contains("tomorrow", ignoreCase = true) || it.text.contains("next time", ignoreCase = true)) }
    }

    @Test fun `under ten moves is a square a move`() {
        assertEquals("🟩🟨🟩🟥", card(Sudoku, sudoku, "mmumhm").lines.single().text(false))
        val undid = card(Sudoku, sudoku, "mmum")
        assertEquals("🟩🟨🟩", undid.lines.single().text(false))
        assertEquals("No hints. 1 undone from a clean strip. Tomorrow?", undid.nudge)
        // Restart takes back every move so far.
        assertEquals("🟨🟨🟩🟩", card(Sudoku, sudoku, "mmrmm").lines.single().text(false))
        // Undoing the restart hands those moves back; a move made after it is taken back first.
        assertEquals("🟩🟩🟩", card(Sudoku, sudoku, "mmrum").lines.single().text(false))
        assertEquals("🟩🟨🟨🟩", card(Sudoku, sudoku, "mmrmuuum").lines.single().text(false))
    }

    @Test fun `lexicon is wordle rows with a red square after a hinted guess`() {
        val s = LexiconState(5, 6, "crane", listOf("slate", "pride", "crane"))
        val c = card(Lexicon, s, "mmhm", tier = Difficulty.STANDARD, streak = 0)
        assertEquals(
            "Daybook · Lexicon · Standard · 22 Oct · 3/6\n⬜⬜🟩⬜🟩\n⬜🟩⬜⬜🟩\n🟩🟩🟩🟩🟩 🟥\n12:04 · 1 hint\n" +
                "🟩 right place  🟨 wrong place  🟥 hint\n$SHARE_URL",
            c.text,
        )
        assertFalse(c.text.contains("crane", ignoreCase = true))
        // A save from before the log: only the last guess was logged, so only it can carry a hint.
        assertEquals(listOf(false, false, true), card(Lexicon, s, "hm").lines.map { it.hinted })
        assertEquals("No hints, 3 guesses. Fewer tomorrow?", card(Lexicon, s, "mmm").nudge)
        assertEquals("First guess. Nothing to improve on.", card(Lexicon, s.copy(guesses = listOf("crane")), "m").nudge)
    }

    @Test fun `tower is pips per guess in the board's order`() {
        val s = TowerState(4, 6, 8, listOf(0, 1, 2, 3), listOf(listOf(1, 0, 4, 4), listOf(0, 1, 3, 2), listOf(0, 1, 2, 3)), List(4) { -1 })
        assertEquals(
            "Daybook · Tower · Expert · 22 Oct · 3/8\n○ ○ · ·\n● ● ○ ○ 🟥\n● ● ● ●\n12:04 · 1 hint · 6 day streak\n" +
                "● right colour and slot  ○ right colour  🟥 hint\n$SHARE_URL",
            card(Tower, s, "mhmm").text,
        )
    }

    @Test fun `mate is a square a move then the mate`() {
        val s = ChessState(start = "8/8/8/8/8/8/8/8 w - - 0 1", mateIn = 2)
        val c = card(Chess, s, "mmum", tier = Difficulty.STANDARD)
        assertEquals(
            "Daybook · Mate · Standard · 22 Oct · mate in 2\n🟩🟨🟩#\n12:04 · 6 day streak\n" +
                "🟩 move  🟨 taken back  🟥 hint\n$SHARE_URL",
            c.text,
        )
        assertEquals("No hints. 1 move taken back from a clean mate. Tomorrow?", c.nudge)
        assertEquals("🟥🟩#", card(Chess, s, "hmm").lines.single().text(false))
    }

    @Test fun `target is a square an operation then steps and par`() {
        val op = TargetMove(0, 1, 2, 3, TargetOp.Add, 5)
        val s = TargetState(listOf(2, 3, 4, 5, 25, 50), target = 999, par = 3, log = listOf(op, op, op))
        val c = card(Target, s, "mmmum")
        assertEquals(
            "Daybook · Target · Expert · 22 Oct\n🟩🟩🟨🟩   3 steps · par 3\n12:04 · 6 day streak\n" +
                "🟩 step  🟨 undone  🟥 hint\n$SHARE_URL",
            c.text,
        )
        assertEquals("No hints. 1 undone from a clean run. Tomorrow?", c.nudge)
        assertEquals("On par, nothing undone. Nothing to improve on.", card(Target, s, "mmm").nudge)
    }

    @Test fun `pointers is the lives left as hearts`() {
        val board = Pointers.tutorialBoard()
        var s = board.tap(Pointers.TUTORIAL_ARROWS[Pointers.ROW][0])!!
        for (i in listOf(Pointers.SIDE, Pointers.ROW, Pointers.HOOK, Pointers.TOP, Pointers.SHORT)) s = s.tap(Pointers.TUTORIAL_ARROWS[i][0])!!
        val c = card(Pointers, s, "m".repeat(6))
        assertEquals(
            "Daybook · Pointers · Expert · 22 Oct\n❤️❤️🖤\n12:04 · 6 day streak\n" +
                "❤️ life left  🖤 life lost\n$SHARE_URL",
            c.text,
        )
        assertEquals("1 bump. None tomorrow?", c.nudge)
        val clean = board.copy(removed = board.arrows.indices.toList())
        assertEquals("Not a single bump.", card(Pointers, clean, "mmmmm").nudge)
        assertEquals("❤️❤️❤️", card(Pointers, clean, "mmmmm").lines.single().text(false))
    }

    @Test fun `mines is the lives left as hearts, and a red square for hints`() {
        val board = Mines.tutorialBoard()
        var s: MinesState = board.reveal(Mines.BUMP)!!
        for (i in 0 until 25) if (!s.isMine(i)) s = s.reveal(i) ?: s
        assertTrue(s.solved)
        val c = card(Mines, s, "m".repeat(9))
        assertEquals(
            "Daybook · Mines · Expert · 22 Oct\n❤️❤️🖤\n12:04 · 6 day streak\n" +
                "❤️ life left  🖤 life lost\n$SHARE_URL",
            c.text,
        )
        assertEquals("1 mine found the hard way. None tomorrow?", c.nudge)
        val hinted = card(Mines, s, "mhmm")
        assertEquals("❤️❤️🖤 🟥", hinted.lines.single().text(false))
        assertEquals("The hints got you there. Try tomorrow's with fewer.", hinted.nudge)
        var clean = board
        for (i in 0 until 25) if (!clean.isMine(i)) clean = clean.reveal(i) ?: clean
        assertEquals("Not a single mine.", card(Mines, clean, "mmmmm").nudge)
        assertEquals("❤️❤️❤️", card(Mines, clean, "mmmmm").lines.single().text(false))
    }

    @Test fun `sets is a square a claim`() {
        val s = SetsState(emptyList(), 0, emptyList(), emptyList())
        assertEquals(
            "Daybook · Sets · Expert · 22 Oct\n🟩⬜🟨🟩🟥\n12:04 · 1 hint · 6 day streak\n" +
                "🟩 a set  ⬜ not a set  🟨 undone  🟥 hint\n$SHARE_URL",
            card(Sets, s, "mwmumhm").text,
        )
        assertEquals("Every claim a set. Nothing to improve on.", card(Sets, s, "mmm").nudge)
        assertEquals("No hints. 1 claim off a clean run. Tomorrow?", card(Sets, s, "mwm").nudge)
    }

    @Test fun `the log records board changes, guesses and wrong claims, not picks or letters`() {
        val cell = sudoku.givens.indexOfFirst { !it }
        assertEquals("", PlayLog.step(Sudoku, sudoku, sudoku.select(cell)))
        assertEquals("m", PlayLog.step(Sudoku, sudoku.select(cell), sudoku.select(cell).withCell(cell, 4)))
        val typed = LexiconState(5, 6, "crane", current = "slat")
        assertEquals("", PlayLog.step(Lexicon, typed, typed.withLetter('e')))
        assertEquals("m", PlayLog.step(Lexicon, typed.withLetter('e'), typed.withLetter('e').submit()))
        val sets = SetsState(emptyList(), 1, emptyList(), listOf(0, 1))
        assertEquals("w", PlayLog.step(Sets, sets, sets.copy(selected = emptyList(), lastWrong = true)))
        val op = TargetMove(0, 1, 2, 3, TargetOp.Add, 5)
        val t = TargetState(listOf(2, 3, 4, 5, 25, 50), 999, 3, log = listOf(op, op))
        assertEquals("uu", PlayLog.step(Target, t, t.copy(log = emptyList())))
    }

    @Test fun `a save from before the log loads with an empty one, and the log round-trips`() {
        val game = SavedGame(sudoku.withCell(sudoku.givens.indexOfFirst { !it }, 4), listOf(sudoku), hints = 2, seconds = 30)
        val old = game.encode()
        assertFalse(old, "\"log\"" in old)
        assertEquals(game, SavedGame.decode(old))
        assertEquals("", SavedGame.decode(old)?.log)
        val logged = game.copy(log = "mmhmu")
        assertEquals(logged, SavedGame.decode(logged.encode()))
    }

    @Test fun `word wheel counts its bonus words, and says nothing of none`() {
        val s = WordWheel.generate(1L, Difficulty.STANDARD) as WordWheelState
        val c = card(WordWheel, s.copy(bonus = listOf("one", "two", "three")), "m".repeat(8), tier = Difficulty.STANDARD)
        assertEquals(
            "Daybook · Word Wheel · Standard · 22 Oct\n🟩🟩🟩🟩🟩🟩🟩🟩\n12:04 · +3 bonus words · 6 day streak\n" +
                "🟩 own move  🟨 undone  🟥 hint\n$SHARE_URL",
            c.text,
        )
        assertEquals("12:04 · +1 bonus word · 6 day streak", card(WordWheel, s.copy(bonus = listOf("one")), "m").stats)
        assertEquals("12:04 · 6 day streak", card(WordWheel, s, "m").stats)
    }

    private val today = LocalDate(2026, 10, 24)
    private val ids = List(26) { "p$it" }
    private fun solved(tier: Difficulty, vararg at: Int, day: LocalDate? = today) = at.map { Completion("p$it", tier, day, 60, 0) }

    @Test fun `the day's card marks every puzzle per tier solved today, two lines a tier`() {
        val completions = solved(Difficulty.STANDARD, 0, 2, 5, 13, 20, 25) + solved(Difficulty.HARD, 1, 14) +
            solved(Difficulty.EXPERT, 0) +
            // Not today's dailies: an archive day's board, a level board; and a second solve of one counts once.
            solved(Difficulty.EXPERT, 3, day = LocalDate(2026, 10, 23)) + solved(Difficulty.HARD, 4, day = null) +
            solved(Difficulty.STANDARD, 0)
        val c = dayShareCard(ids, completions, today, "24 Oct", 12)!!
        assertEquals(
            "Daybook · 24 Oct · today's dailies\n" +
                "Standard 6/26\n🟩⬜🟩⬜⬜🟩⬜⬜⬜⬜⬜⬜⬜\n🟩⬜⬜⬜⬜⬜⬜🟩⬜⬜⬜⬜🟩\n" +
                "Hard 2/26\n⬜🟩⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜\n⬜🟩⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜\n" +
                "Expert 1/26\n🟩⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜\n⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜\n" +
                "9 solved today · 12 day streak\n🟩 solved  ⬜ not yet\n$SHARE_URL",
            c.text,
        )
        assertEquals("20 more at Standard to clear the day.", c.nudge)
    }

    @Test fun `the day's card leaves out untouched tiers, and is not offered before a solve`() {
        assertNull(dayShareCard(ids, solved(Difficulty.HARD, 1, day = LocalDate(2026, 10, 23)), today, "24 Oct", 1))
        val c = dayShareCard(ids, solved(Difficulty.HARD, *IntArray(26) { it }), today, "24 Oct", 1)!!
        assertEquals(listOf("Hard 26/26"), c.lines.filter { it.marks.isEmpty() }.map { it.tail })
        assertEquals("26 solved today", c.stats)
        assertEquals("Every Hard board done. A clean sweep.", c.nudge)
    }
}
