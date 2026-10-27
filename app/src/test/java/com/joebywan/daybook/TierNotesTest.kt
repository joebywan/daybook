package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TIER_NOTE_MAX
import com.joebywan.daybook.core.tierNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierNotesTest {
    @Test fun everyRegisteredPuzzleSaysWhatEachTierChanges() {
        for (p in PuzzleRegistry.all) {
            val notes = tierNotes(p.id)
            assertEquals("${p.id} needs one line per tier in core/TierNotes.kt", Difficulty.entries.size, notes.size)
            assertEquals("${p.id}: two tiers say the same", notes.size, notes.toSet().size)
            for (n in notes) {
                assertTrue("${p.id}: blank tier line", n.isNotBlank())
                assertTrue("${p.id}: over $TIER_NOTE_MAX chars: $n", n.length <= TIER_NOTE_MAX)
                assertFalse("${p.id}: say what changes, not that it is harder: $n", Regex("(?i)harder|easier|difficult").containsMatchIn(n))
            }
        }
    }
}
