package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.PointersLogic
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.RegionsLogic
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandLogic
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.TargetLogic
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.WordWheelLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A level seed is just another seed, but `FallbackTest` only walks daily ones: walk the boards of levels
 * 1-365 through each puzzle's `generateVerified`, and check `generate` ships that same proved board.
 */
class LevelBoardsProvedTest {

    private val verified: Map<String, (Long, Difficulty) -> Any?> = mapOf(
        "atoms" to Atoms::generateVerified,
        "kings" to Kings::generateVerified,
        "sets" to Sets::generateVerified,
        "lits" to Lits::generateVerified,
        "mambo" to Mambo::generateVerified,
        "shikaku" to Shikaku::generateVerified,
        "snap" to Snap::generateVerified,
        "inequality" to Inequality::generateVerified,
        "regions" to { s, d -> RegionsLogic.generateVerified(s, d) },
        "cipher" to Cipher::generateVerified,
        "wordwheel" to WordWheelLogic::generateVerified,
        "tally" to TallyLogic::generateVerified,
        "sand" to SandLogic::generateVerified,
        "tents" to TentsLogic::generateVerified,
        "pointers" to PointersLogic::generateVerified,
        "target" to TargetLogic::generateVerified,
        "cages" to Cages::generateVerified,
        "nonogram" to NonogramLogic::generateVerified,
        "dominoes" to DominoLogic::generateVerified,
    )

    @Test
    fun `every level board of levels 1 to 365 is proved, under every deal`() {
        val began = System.nanoTime()
        val gaveUp = mutableListOf<String>()
        var checked = 0
        val ms = sortedMapOf<String, Long>()
        val seen = HashSet<Long>() // both deals deal mostly the same boards; prove each once
        for (level in 1..365) {
            for (b in (1..Levels.LATEST).flatMap { Levels.plan(level, Levels.POOL.size, it) }) {
                val prove = verified[b.puzzleId] ?: continue
                if (!seen.add(b.seed)) continue
                checked++
                val t = System.nanoTime()
                val proved = prove(b.seed, b.difficulty)
                ms[b.puzzleId] = (ms[b.puzzleId] ?: 0L) + (System.nanoTime() - t) / 1_000_000
                if (proved == null) {
                    gaveUp += "L$level ${b.puzzleId}/${b.difficulty.name}"
                    continue
                }
                assertEquals("L$level ${b.puzzleId}", proved, PuzzleRegistry.byId(b.puzzleId)!!.generate(b.seed, b.difficulty))
            }
        }
        println("LevelBoardsProvedTest: $checked boards in ${(System.nanoTime() - began) / 1_000_000} ms, proving by puzzle (ms): $ms")
        assertTrue("fell back on ${gaveUp.size} level boards: $gaveUp", gaveUp.isEmpty())
    }
}
