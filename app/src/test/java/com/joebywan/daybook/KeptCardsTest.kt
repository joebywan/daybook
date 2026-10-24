package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.KeptCard
import com.joebywan.daybook.data.KeptCards
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.ui.play.Mark
import com.joebywan.daybook.ui.play.ShareCard
import com.joebywan.daybook.ui.play.ShareLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The share cards kept for the stats screen: a tier's best (replaced only when beaten) and its last random board. */
class KeptCardsTest {

    private fun card(tag: String) = ShareCard(
        "Daybook · Sudoku · Standard · $tag", listOf(ShareLine(listOf(Mark.GREEN, Mark.YELLOW), hinted = true, tail = " x")),
        "1:00", listOf(Mark.GREEN to "own move"), "nudge",
    )

    private fun kept(best: Boolean, seconds: Int, hints: Int, puzzle: String = "sudoku", tier: Difficulty = Difficulty.STANDARD) =
        KeptCard(puzzle, tier, best, seconds, hints, card("$seconds/$hints"))

    private fun List<KeptCard>.slot(best: Boolean) = single { it.puzzleId == "sudoku" && it.difficulty == Difficulty.STANDARD && it.best == best }

    @Test
    fun `a best is replaced only when beaten`() {
        var all = KeptCards.keep(emptyList(), kept(true, 100, 1))
        all = KeptCards.keep(all, kept(true, 100, 1).copy(card = card("tie")))
        assertEquals("a tie is not a record", card("100/1"), all.slot(true).card)
        all = KeptCards.keep(all, kept(true, 120, 1))
        assertEquals("slower loses", 100, all.slot(true).seconds)
        all = KeptCards.keep(all, kept(true, 50, 2))
        assertEquals("faster with more hints loses", 100, all.slot(true).seconds)
        all = KeptCards.keep(all, kept(true, 90, 1))
        assertEquals("faster with as many hints wins", 90, all.slot(true).seconds)
        all = KeptCards.keep(all, kept(true, 500, 0))
        assertEquals("fewer hints wins however slow", 500, all.slot(true).seconds)
        assertEquals(1, all.size)
    }

    @Test
    fun `the last card always replaces its slot and leaves the others alone`() {
        var all = KeptCards.keep(emptyList(), kept(true, 30, 0))
        all = KeptCards.keep(all, kept(false, 30, 0))
        all = KeptCards.keep(all, kept(false, 300, 4))
        all = KeptCards.keep(all, kept(false, 40, 0, tier = Difficulty.HARD))
        all = KeptCards.keep(all, kept(false, 40, 0, puzzle = "kings"))
        assertEquals(300, all.slot(false).seconds)
        assertEquals(30, all.slot(true).seconds)
        assertEquals(4, all.size)
    }

    @Test
    fun `a card survives the store, and an older store has none`() = runBlocking {
        val kv = MemoryStore()
        kv.sets.value = mapOf("completions" to setOf("sudoku|STANDARD|-|60|0")) // as an older build left it
        val store = ProgressStore(kv)
        assertTrue(store.keptCards.first().isEmpty())
        kv.sets.value = kv.sets.value + ("share_cards" to setOf("not json", "{\"puzzleId\":\"x\"}"))
        assertTrue("a damaged entry is skipped", store.keptCards.first().isEmpty())
        val k = kept(true, 60, 0)
        store.keepCard(k)
        assertEquals(listOf(k), store.keptCards.first())
        assertEquals(k.card.text, store.keptCards.first().single().card.text)
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
