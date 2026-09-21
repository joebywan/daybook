package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.data.SavedGames
import com.joebywan.daybook.data.StoredGame
import com.joebywan.daybook.data.savedGameKey
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The store's rules live in [SavedGames] rather than inside the DataStore call so they can be
 * checked here, off-device, the same way [com.joebywan.daybook.data.Stats] is.
 */
class SavedGameTest {

    private val board = Sudoku.generate(1L, Difficulty.STANDARD) as SudokuState

    private fun game(seconds: Int) =
        SavedGame(board.withCell(board.givens.indexOfFirst { !it }, 4), seconds = seconds)

    private fun stored(key: String, savedAt: Long) = StoredGame(key, savedAt, game(savedAt.toInt()))

    @Test
    fun `boards are told apart by puzzle, difficulty and seed`() {
        val monday = DailySeed.seedFor(LocalDate.of(2026, 3, 2), "sudoku", Difficulty.HARD)
        val tuesday = DailySeed.seedFor(LocalDate.of(2026, 3, 3), "sudoku", Difficulty.HARD)

        assertEquals(
            savedGameKey("sudoku", Difficulty.HARD, monday),
            savedGameKey("sudoku", Difficulty.HARD, monday),
        )
        assertNotEquals(
            savedGameKey("sudoku", Difficulty.HARD, monday),
            savedGameKey("sudoku", Difficulty.HARD, tuesday),
        )
        assertNotEquals(
            savedGameKey("sudoku", Difficulty.HARD, monday),
            savedGameKey("sudoku", Difficulty.EXPERT, monday),
        )
        assertNotEquals(
            savedGameKey("sudoku", Difficulty.HARD, monday),
            savedGameKey("kings", Difficulty.HARD, monday),
        )
    }

    @Test
    fun `yesterday's puzzle does not hand back today's game`() {
        val all = listOf(stored("sudoku|HARD|100", 1L))
        assertNull(SavedGames.find(all, "sudoku|HARD|200"))
        assertEquals(game(1), SavedGames.find(all, "sudoku|HARD|100"))
    }

    @Test
    fun `saving the same board again replaces it instead of piling up`() {
        var all = listOf(stored("a", 1L))
        all = SavedGames.upsert(all, StoredGame("a", 2L, game(30)))
        all = SavedGames.upsert(all, StoredGame("a", 3L, game(60)))

        assertEquals(1, all.size)
        assertEquals(game(60), SavedGames.find(all, "a"))
    }

    @Test
    fun `several boards stay saved side by side`() {
        val all = SavedGames.upsert(SavedGames.upsert(emptyList(), stored("a", 1L)), stored("b", 2L))

        assertEquals(2, all.size)
        assertEquals(game(1), SavedGames.find(all, "a"))
        assertEquals(game(2), SavedGames.find(all, "b"))
    }

    @Test
    fun `clearing one board leaves the rest alone`() {
        val all = SavedGames.without(
            SavedGames.upsert(SavedGames.upsert(emptyList(), stored("a", 1L)), stored("b", 2L)),
            "a",
        )

        assertNull(SavedGames.find(all, "a"))
        assertEquals(game(2), SavedGames.find(all, "b"))
    }

    @Test
    fun `the oldest games fall off once the store is full`() {
        var all = emptyList<StoredGame>()
        for (i in 1..SavedGames.KEEP + 5) all = SavedGames.upsert(all, stored("board-$i", i.toLong()))

        assertEquals(SavedGames.KEEP, all.size)
        assertNull("the first game abandoned should be gone", SavedGames.find(all, "board-1"))
        assertEquals(
            game(SavedGames.KEEP + 5),
            SavedGames.find(all, "board-${SavedGames.KEEP + 5}"),
        )
    }

    @Test
    fun `touching a board keeps it out of the eviction queue`() {
        var all = emptyList<StoredGame>()
        for (i in 1..SavedGames.KEEP) all = SavedGames.upsert(all, stored("board-$i", i.toLong()))
        all = SavedGames.upsert(all, StoredGame("board-1", 99L, game(99)))
        all = SavedGames.upsert(all, stored("newcomer", 100L))

        assertEquals(SavedGames.KEEP, all.size)
        assertEquals(game(99), SavedGames.find(all, "board-1"))
        assertNull("board-2 was the stalest once board-1 was played again", SavedGames.find(all, "board-2"))
    }

    @Test
    fun `a stored game survives encoding`() {
        val entry = stored("sudoku|STANDARD|7", 1234L)
        assertEquals(entry, StoredGame.decode(entry.encode()))
    }

    @Test
    fun `unreadable saved games are dropped, not thrown`() {
        // Whatever a future version writes here, the worst it may cost is one abandoned game.
        assertNull(SavedGame.decode("not json at all"))
        assertNull(SavedGame.decode("""{"state":{"type":"com.example.Gone","moves":2}}"""))
        assertNull(StoredGame.decode("{}"))
    }
}
