package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.WheelWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Crossword's hard clues (`tools/words/hard-clues.txt`, compiled into `MiniHardClues.kt`). */
class MiniHardCluesTest {

    private val source: Map<String, String> = File("../tools/words/hard-clues.txt").readLines().filter { it.isNotBlank() }
        .associate { it.substringBefore(": ") to it.substringAfter(": ") }

    private fun year(d: Difficulty) = (0 until 365L).map {
        MiniLogic.generate(DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it), "mini", d), d)
    }

    @Test
    fun `the compiled clues are the source file's, each names one word and does not spell it`() {
        val firsts = (3..7).flatMap { WheelWords.grid(it) }.associateBy { WheelWords.clue(it).lowercase() }
        val seconds = source.keys.associateBy { MiniLogic.secondClueOf(it).lowercase() }
        for ((w, c) in source) {
            assertEquals("$w: run python3 tools/words/build.py --second-clues", c, MiniLogic.hardClueOf(w))
            assertTrue("$w is no grid word", WheelWords.isGridWord(w))
            assertTrue("$w: \"$c\" is ${firsts[c.lowercase()]}'s first clue", c.lowercase() !in firsts)
            assertTrue("$w: \"$c\" is another word's second clue", seconds[c.lowercase()].let { it == null || it == w })
            assertTrue("$w: \"$c\" contains the word", Regex("[a-z]+").findAll(c.lowercase()).none { it.value == w || it.value.startsWith(w) })
            assertTrue("$w: \"$c\" draws a character outside the fonts' ASCII", c.all { it.code in 32..126 })
        }
        val dupes = source.keys.groupBy { source[it]!!.lowercase() }.filterValues { it.size > 1 }
        assertEquals(emptyMap<String, List<String>>(), dupes)
    }

    @Test
    fun `a year of boards - Standard is plain, Hard's across and most of Expert are hard, and every board still solves`() {
        fun share(d: Difficulty, acrossOnly: Boolean): Int {
            var hard = 0
            var all = 0
            for (s in year(d)) {
                val slots = MiniLogic.slots(s.size, s.answer)
                assertEquals("clues on a board stay distinct", s.clues.size, s.clues.toSet().size)
                assertTrue(MiniLogic.isSolved(s.size, s.answer, s.clues))
                slots.forEachIndexed { i, slot ->
                    val word = MiniLogic.spelled(s.answer, slot)
                    val isHard = s.clues[i] == MiniLogic.hardClueOf(word)
                    // A hard clue's fallback is the plain clue; a plain clue's is its second.
                    assertEquals(if (isHard) MiniLogic.clueOf(word) else MiniLogic.secondClueOf(word), s.secondClues[i])
                    if (!slot.across && d == Difficulty.HARD) assertTrue("Hard's downs stay plain", !isHard)
                    if (slot.across || !acrossOnly) { all++; if (isHard) hard++ }
                }
            }
            return hard * 100 / all
        }
        assertEquals(0, share(Difficulty.STANDARD, false))
        val hard = share(Difficulty.HARD, true)
        val expert = share(Difficulty.EXPERT, false)
        File("build/reports").mkdirs()
        File("build/reports/mini-hard-clues.txt").writeText("hard across $hard%, expert all $expert%\n")
        assertTrue("Hard's across words with a hard clue: $hard%", hard >= 65)
        assertTrue("Expert's words with a hard clue: $expert%", expert >= 55)
    }
}
