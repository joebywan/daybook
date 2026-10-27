package com.joebywan.daybook

import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.MetTechnique
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.data.TechniquesMet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The techniques page: what hints have explained, per puzzle, in the teacher's order. */
class TechniquesMetTest {

    @Test
    fun `a store from before the page reads as nothing met, and every teacher is listed`() = runBlocking {
        val store = ProgressStore(MemoryStore())
        val ids = PuzzleRegistry.all.map { it.id }
        val page = TechniquesMet.of(store.techniquesMet.first(), ids)
        assertTrue(page.all { p -> p.techniques.none { it.met } })
        // Pointers and Snap have no teacher, and Target's steps are not skills (a step, the last step), so no list for them.
        assertEquals(ids.filter { praisedTechniquesOf(it).isNotEmpty() }, page.map { it.puzzleId })
        assertTrue("pointers" !in page.map { it.puzzleId } && "snap" !in page.map { it.puzzleId })
    }

    @Test
    fun `a technique a hint explained is met once, in ladder order, and steps that are not skills are left out`() = runBlocking {
        val store = ProgressStore(MemoryStore())
        store.recordTechniqueMet("sudoku", "naked-single")
        store.recordTechniqueMet("sudoku", "naked-single")
        store.recordTechniqueMet("words", "opener")
        store.recordTechniqueMet("words", "pinned-letter")
        store.recordTechniqueMet("gone-puzzle", "x")
        val raw = store.techniquesMet.first()
        assertEquals(setOf("sudoku|naked-single", "words|opener", "words|pinned-letter", "gone-puzzle|x"), raw)

        val page = TechniquesMet.of(raw, listOf("words", "sudoku", "pointers"))
        assertEquals(listOf("words", "sudoku"), page.map { it.puzzleId })
        val sudoku = page[1].techniques
        assertEquals(techniqueNamesOf("sudoku").values.toList(), sudoku.map { it.name })
        assertEquals(listOf(MetTechnique("a square with one digit left", true, "naked-single")), sudoku.filter { it.met })
        // "a good opening word" points at a guess rather than teaching a way of seeing, so it is not on the page.
        assertEquals(listOf("a letter pinned to its slot"), page[0].techniques.filter { it.met }.map { it.name })
        assertEquals(praisedTechniquesOf("words").size, page[0].techniques.size)
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
}
