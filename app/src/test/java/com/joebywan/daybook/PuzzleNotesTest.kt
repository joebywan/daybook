package com.joebywan.daybook

import com.joebywan.daybook.core.NOTE_MAX
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.notesOf
import com.joebywan.daybook.core.puzzleNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleNotesTest {
    @Test fun everyRegisteredPuzzleHasNotes() {
        for (p in PuzzleRegistry.all) {
            val notes = notesOf(p.id)
            assertTrue("${p.id} needs notes in core/PuzzleNotes.kt", notes.isNotEmpty())
            assertEquals("${p.id} has a duplicate note", notes.size, notes.toSet().size)
            for (n in notes) {
                assertTrue("${p.id}: blank note", n.isNotBlank())
                assertTrue("${p.id}: note over $NOTE_MAX chars (${n.length}): $n", n.length <= NOTE_MAX)
            }
        }
    }

    @Test fun deterministicAndEveryNoteComesUp() {
        for (p in PuzzleRegistry.all) {
            assertEquals(puzzleNote(p.id, 20000), puzzleNote(p.id, 20000))
            val seen = (20000L until 20000L + 30).map { puzzleNote(p.id, it) }.toSet()
            assertEquals(notesOf(p.id).toSet(), seen)
        }
        assertNull(puzzleNote("no-such-puzzle", 1))
    }

    @Test fun featuredIsDeterministicAndCoversEveryPuzzle() {
        assertEquals(PuzzleRegistry.featured(20000).id, PuzzleRegistry.featured(20000).id)
        val seen = (20000L until 20000L + PuzzleRegistry.all.size).map { PuzzleRegistry.featured(it).id }.toSet()
        assertEquals(PuzzleRegistry.all.map { it.id }.toSet(), seen)
    }
}
