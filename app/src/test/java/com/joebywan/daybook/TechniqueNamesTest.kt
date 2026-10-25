package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TECHNIQUE_NAME_MAX
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.techniqueToPraise
import com.joebywan.daybook.puzzles.AtomsTeacher
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.ChessTeacher
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherTeacher
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.Dominoes
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesTeacher
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.KingsTeacher
import com.joebywan.daybook.puzzles.LexiconTeacher
import com.joebywan.daybook.puzzles.LitsTeacher
import com.joebywan.daybook.puzzles.MamboTeacher
import com.joebywan.daybook.puzzles.MosaicTeacher
import com.joebywan.daybook.puzzles.NonogramTeacher
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.RegionsTeacher
import com.joebywan.daybook.puzzles.SandTeacher
import com.joebywan.daybook.puzzles.SetsTeacher
import com.joebywan.daybook.puzzles.ShikakuTeacher
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyTeacher
import com.joebywan.daybook.puzzles.TargetTeacher
import com.joebywan.daybook.puzzles.TentsTeacher
import com.joebywan.daybook.puzzles.TowerTeacher
import com.joebywan.daybook.puzzles.WordWheelTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.measureTime

class TechniqueNamesTest {
    /** Every teacher's techniques by puzzle id. Snap and Pointers teach by walkthrough only. */
    private val teachers = mapOf(
        "sudoku" to SudokuTeacher.TECHNIQUES, "kings" to KingsTeacher.TECHNIQUES, "mambo" to MamboTeacher.TECHNIQUES,
        "pipes" to PipesTeacher.TECHNIQUES, "shikaku" to ShikakuTeacher.TECHNIQUES, "mosaic" to MosaicTeacher.TECHNIQUES,
        "sets" to SetsTeacher.TECHNIQUES, "atoms" to AtomsTeacher.TECHNIQUES, "lits" to LitsTeacher.TECHNIQUES,
        "tower" to TowerTeacher.TECHNIQUES, "words" to LexiconTeacher.TECHNIQUES, "nonogram" to NonogramTeacher.TECHNIQUES,
        "inequality" to InequalityTeacher.TECHNIQUES, "chess" to ChessTeacher.TECHNIQUES, "tents" to TentsTeacher.TECHNIQUES,
        "regions" to RegionsTeacher.TECHNIQUES, "cages" to CageTeacher.TECHNIQUES, "target" to TargetTeacher.TECHNIQUES,
        "cipher" to CipherTeacher.TECHNIQUES, "wordwheel" to WordWheelTeacher.TECHNIQUES, "tally" to TallyTeacher.TECHNIQUES,
        "sand" to SandTeacher.TECHNIQUES, "dominoes" to DominoTeacher.TECHNIQUES,
        "mines" to MinesTeacher.TECHNIQUES,
    )
    private val unnamed = setOf("mistake", "fallback")

    @Test fun everyTeacherTechniqueHasAShortName() {
        val registered = PuzzleRegistry.all.map { it.id }.toSet() - "snap" - "pointers"
        assertEquals("a registered puzzle is missing from this test", registered, teachers.keys)
        for ((id, techniques) in teachers) {
            val names = techniqueNamesOf(id)
            assertEquals("$id: names in core/TechniqueNames.kt", techniques.toSet() - unnamed, names.keys)
            for (n in names.values) {
                assertTrue("$id: name over $TECHNIQUE_NAME_MAX chars: $n", n.length <= TECHNIQUE_NAME_MAX)
                assertTrue("$id: a name is a lower-case noun phrase: $n", n.isNotBlank() && !n[0].isUpperCase() && !n.endsWith("."))
            }
        }
        assertTrue(techniqueNamesOf("snap").isEmpty())
    }

    /** Every puzzle with a real technique has something to praise; Target's steps are moves, not skills. */
    @Test fun everyTeacherButTargetHasAPraisedTechnique() {
        for (id in teachers.keys - "target") assertTrue("$id: nothing praised", praisedTechniquesOf(id).isNotEmpty())
        assertTrue(praisedTechniquesOf("target").isEmpty())
        assertEquals(null, techniqueToPraise("words", listOf("best-guess", "opener")))
        assertEquals("a letter pinned to its slot", techniqueToPraise("words", listOf("pinned-letter", "best-guess")))
    }

    /** The no-hint line is a claim about the board, so it must match what the generator built the tier to need. */
    @Test fun neededTechniqueMatchesTheTier() {
        val expected = mapOf(
            Tally to listOf(null, TallyTeacher.WAYS, TallyTeacher.WHAT_IF),
            Dominoes to listOf(DominoTeacher.REGION_VALUE, DominoTeacher.COUNTING, DominoTeacher.WHAT_IF),
            Cipher to listOf(null, CipherTeacher.CHAIN, CipherTeacher.WHAT_IF),
        )
        for ((p, want) in expected) for (t in Difficulty.entries) for (seed in 1L..10L) {
            val board = p.generate(seed, t)
            var got: String? = null
            val took = measureTime { got = p.neededTechnique(board) }
            // Cipher's Standard is "columns and pairs only": whichever of those its walk ends on.
            if (p == Cipher && t == Difficulty.STANDARD) {
                assertTrue("cipher seed $seed: $got", got in setOf(CipherTeacher.COLUMN, CipherTeacher.PAIR, CipherTeacher.ONLY_LETTER))
            } else {
                assertEquals("${p.id} $t seed $seed", want[t.ordinal], got)
            }
            assertTrue("${p.id} $t took $took", took.inWholeMilliseconds < 500)
        }
        // Mines' Hard and Expert each gate on a level of two techniques: whichever the walk used.
        val mines = listOf(setOf(null), setOf(MinesTeacher.SUBSET, MinesTeacher.PAIR), setOf(MinesTeacher.COUNT, MinesTeacher.WHAT_IF))
        for (t in Difficulty.entries) for (seed in 1L..10L) {
            val got = Mines.neededTechnique(Mines.generate(seed, t))
            assertTrue("mines $t seed $seed: $got", got in mines[t.ordinal])
        }
    }
}
