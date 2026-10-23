package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.data.PuzzleOrder
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The home grid: its order ([PuzzleOrder]) and which tiles say "In progress" ([ProgressStore.inProgress]). */
class HomeOrderTest {

    private val ids = listOf("a", "b", "c", "d")
    private val today = LocalDate(2026, 10, 23)
    private fun solve(id: String, daysAgo: Int?) =
        Completion(id, Difficulty.STANDARD, daysAgo?.let { LocalDate.fromEpochDays(today.toEpochDays() - it) }, 60, 0)

    @Test
    fun `most played first, ties and the unplayed in registry order`() {
        val all = listOf(solve("c", 1), solve("c", 2), solve("b", 3), solve("d", 4))
        assertEquals(listOf("c", "b", "d", "a"), PuzzleOrder.mostPlayed(all, today, ids))
        assertEquals("nothing played keeps the registry", ids, PuzzleOrder.mostPlayed(emptyList(), today, ids))
    }

    @Test
    fun `old dailies stop counting, practice games always count`() {
        val old = PuzzleOrder.WINDOW_DAYS + 1
        val all = listOf(solve("a", old), solve("a", old), solve("d", null))
        assertEquals(listOf("d", "a", "b", "c"), PuzzleOrder.mostPlayed(all, today, ids))
    }

    @Test
    fun `a stored order drops removed puzzles and appends new ones`() {
        assertEquals(listOf("c", "a", "b", "d"), PuzzleOrder.arranged(listOf("c", "gone", "a", "c"), ids))
        assertEquals(ids, PuzzleOrder.arranged(emptyList(), ids))
    }

    private class MemoryStore : KeyValueStore {
        val sets = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
        override fun stringSet(key: String): Flow<Set<String>> = sets.map { it[key].orEmpty() }
        override suspend fun updateStringSet(key: String, transform: (Set<String>) -> Set<String>) {
            sets.value = sets.value + (key to transform(sets.value[key].orEmpty()))
        }
        override fun string(key: String): Flow<String?> = MutableStateFlow(null)
        override suspend fun putString(key: String, value: String) = Unit
    }

    @Test
    fun `in progress means saved, unsolved and moved, and only among the keys asked`() = runBlocking {
        val board = Sudoku.generate(1L, Difficulty.STANDARD) as SudokuState
        val moved = board.withCell(board.givens.indexOfFirst { !it }, 4)
        val store = ProgressStore(MemoryStore())
        store.saveGame("played", SavedGame(moved))
        store.saveGame("other", SavedGame(moved))
        store.saveGame("solved", SavedGame(board.copy(cells = board.solution)))
        store.saveGame("untouched", SavedGame(board))
        assertEquals(1, moved.moves - board.moves)
        assertEquals(
            setOf("played"),
            store.inProgress(setOf("played", "solved", "untouched", "missing")).first(),
        )
    }
}
