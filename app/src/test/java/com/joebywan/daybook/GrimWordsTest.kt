package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.CipherLogic
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.WheelWords
import com.joebywan.daybook.puzzles.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The breakfast-table screen (`tools/words/grim.txt`, one list for every word game) is read here from the file itself,
 * not from anything the builders made, and no word it holds may be an answer or a printed grid word anywhere: Lexicon's
 * answers, Word Wheel's grid words and wheels, the words of every Cipher sum, and every run of every Codeword and
 * Crossword grid. Guesses and bonus words are left alone on purpose: a player may still type them.
 */
class GrimWordsTest {

    private val grim: Set<String> = File("../tools/words/grim.txt").readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

    private fun shown(): Map<String, Set<String>> = buildMap {
        put("Lexicon answers", (4..5).flatMap { WordList.answers(it) }.toSet())
        put("Word Wheel grid words", (3..7).flatMap { WheelWords.grid(it) }.toSet())
        put("Word Wheel wheels", (5..7).flatMap { WheelWords.roots(it) }.toSet())
        put("Cipher sums", Difficulty.entries.flatMap { d -> CipherLogic.sums(d).flatMap { it.split('+', '=') } }.toSet())
        put("Codeword grids", Difficulty.entries.flatMap { d ->
            CodewordLogic.entries(d).flatMap { MiniOracle.runs(CodewordLogic.specFor(d).size, it.substringBefore(':')) }
        }.toSet())
        put("Crossword grids", Difficulty.entries.flatMap { d ->
            MiniLogic.entries(d).flatMap { MiniOracle.runs(MiniLogic.specFor(d).size, it) }
        }.toSet())
    }

    @Test
    fun `the screen is read and holds the owner's additions`() {
        assertTrue("grim.txt has ${grim.size} words", grim.size > 50)
        assertTrue(grim.containsAll(listOf("loony", "prick", "vomit", "death", "murder")))
        assertTrue(grim.all { w -> w.all { it in 'a'..'z' } })
    }

    @Test
    fun `no game shows a screened word`() {
        for ((game, words) in shown()) {
            assertTrue("$game read nothing", words.isNotEmpty())
            assertEquals("$game shows screened words", emptyList<String>(), words.filter { it in grim }.sorted())
        }
    }
}
