package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep

/**
 * Hexwords, reasoned from what the player can see: the letters still on the board and the points still needed. There is no
 * stored answer, so no step reads one.
 *
 * - [BIGGEST]: the biggest common word on the board still leaves the target in reach. Given by its crossword clue.
 * - [HOLD_BACK]: the biggest word would fall short, proved by the search; the biggest that does not is given by its clue, and
 *   the trap's hexes are marked. This is the game's lesson.
 * - [RARE]: only a word off the common list keeps the target in reach (the player has already spent common words). No clue
 *   exists for it, so the step gives its length and first hex.
 * - [UNCHECKED], the fallback: the search ran out of budget, so nothing is proved; the biggest word is offered and the step
 *   says it has not checked. It still reads no stored word.
 * - [MISTAKE]: an accepted-word search says [HexReach.No] from what is left. Never from [HexReach.Truncated]. The step names the
 *   earliest word after which the target could not be reached, and Show me puts it and those after it back.
 *
 * Only "Show me" spells a word. Highlight indices are hexes.
 */
internal object HexwordsTeacher {

    const val BIGGEST = "biggest"
    const val HOLD_BACK = "hold-back"
    const val RARE = "rare"
    const val UNCHECKED = "unchecked"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(BIGGEST, HOLD_BACK, RARE, UNCHECKED, MISTAKE)
    val FALLBACKS = listOf(UNCHECKED)

    /** Positions one hint may look at: about 50 ms on a desktop JVM. A step past it says so. */
    const val BUDGET = 300_000
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    class Step(
        override val technique: String,
        /** The word to take; empty for a mistake. */
        val word: String,
        val path: List<Int>,
        /** For a mistake: how many words stay. */
        val keep: Int,
        override val nudge: String,
        override val explanation: String,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
    ) : TeachStep

    fun teach(s: HexwordsState): Step? {
        if (s.solved) return null
        val nb = HexLogic.neighbours(s.rows)
        val n = s.letters.length
        val free = s.free
        val all = (1L shl n) - 1
        // A mistake is "no accepted word keeps it", so it is asked of every accepted word, from the whole board's roads.
        val accepted = HexSearch(HexLogic.roads(s.letters, nb, all, HexLogic.acceptedWords), n, BUDGET)
        if (accepted.reach(free, s.left) == HexReach.No) return mistake(s, accepted)

        val grid = HexLogic.roads(s.letters, nb, free, HexLogic.gridWords)
        val search = HexSearch(grid, n, BUDGET)
        val biggest = grid.maxOfOrNull { it.points } ?: 0
        val trap = grid.firstOrNull { it.points == biggest }
        when (search.reach(free, s.left)) {
            is HexReach.Yes -> {
                for (r in grid.sortedByDescending { it.points }) {
                    if (search.reach(free xor r.mask, s.left - r.points) !is HexReach.Yes) continue
                    return if (r.points == biggest) big(r) else holdBack(r, trap!!)
                }
                return null // unreachable: a road's first word keeps its own road
            }
            HexReach.No -> {}
            HexReach.Truncated -> return trap?.let { unchecked(it) }
        }
        // Only an uncommon word keeps it.
        val wide = HexLogic.roads(s.letters, nb, free, HexLogic.acceptedWords)
        val keep = wide.sortedByDescending { it.points }.firstOrNull {
            accepted.reach(free xor it.mask, s.left - it.points) is HexReach.Yes
        }
        return keep?.let { rare(it) } ?: wide.maxByOrNull { it.points }?.let { unchecked(it) }
    }

    private fun clueOf(word: String) = "“${WheelWords.clue(word)}” (${word.length})"

    private fun big(r: HexRoad) = Step(
        BIGGEST, r.word, r.path.toList(), 0,
        nudge = "There is a ${r.word.length}-letter word starting here.",
        explanation = "Worth ${r.points}, and the target stays in reach after it. Its clue: ${clueOf(r.word)}.",
        focus = setOf(r.path[0]), cited = emptySet(), targets = setOf(r.path[0]),
    )

    private fun holdBack(r: HexRoad, trap: HexRoad) = Step(
        HOLD_BACK, r.word, r.path.toList(), 0,
        nudge = "Not the biggest word yet: it would cost you the target.",
        explanation = "A ${trap.word.length}-letter word runs through the marked hexes, but what it leaves falls short. " +
            "Take this one first (${r.points}). Its clue: ${clueOf(r.word)}.",
        focus = setOf(r.path[0]), cited = trap.path.toSet(), targets = setOf(r.path[0]),
    )

    private fun rare(r: HexRoad) = Step(
        RARE, r.word, r.path.toList(), 0,
        nudge = "Only a less common word keeps the target in reach now.",
        explanation = "It has ${r.word.length} letters and starts here, with ${r.word[0].uppercaseChar()}. Show me spells it, or undo a word for more choice.",
        focus = setOf(r.path[0]), cited = emptySet(), targets = setOf(r.path[0]),
    )

    private fun unchecked(r: HexRoad) = Step(
        UNCHECKED, r.word, r.path.toList(), 0,
        nudge = "Too many ways to check. Here is the biggest word left.",
        explanation = "This hint could not check every way from here, so it is only the biggest word left, not a proved one. " +
            "It has ${r.word.length} letters and starts here.",
        focus = setOf(r.path[0]), cited = emptySet(), targets = setOf(r.path[0]),
    )

    /** The earliest taken word that left the target out of reach; everything before it still reached. */
    private fun mistake(s: HexwordsState, accepted: HexSearch): Step {
        var free = (1L shl s.letters.length) - 1
        var need = s.target
        var k = 0
        for (t in s.taken) {
            for (i in t.path) free = free and (1L shl i).inv()
            need -= HexLogic.points(t.word.length)
            if (accepted.reach(free, need) == HexReach.No) break
            k++
        }
        val wrong = s.taken[minOf(k, s.taken.size - 1)]
        val word = wrong.word.uppercase()
        val hexes = s.taken.drop(k).flatMap { it.path }.toSet()
        return Step(
            MISTAKE, "", emptyList(), k,
            nudge = "The target is out of reach from here.",
            explanation = "After $word, no words from the letters left can make up the points still needed. " +
                "Show me puts it back${if (k < s.taken.size - 1) ", with the words after it" else ""}.",
            focus = wrong.path.toSet(), cited = hexes, targets = wrong.path.toSet(),
        )
    }

    fun deduction(s: HexwordsState): Deduction? {
        val step = teach(s) ?: return null
        if (step.technique == MISTAKE) {
            val keep = step.keep
            return Deduction(
                step,
                mistake = true,
                applyTo = { now -> (now as HexwordsState).let { if (it.taken.size > keep) it.withoutFrom(keep) else it } },
                reachedBy = { now -> (now as HexwordsState).taken.size <= keep },
            )
        }
        val had = s.taken.size
        return Deduction(
            step,
            fallback = step.technique in FALLBACKS,
            applyTo = { now ->
                val t = now as HexwordsState
                val free = t.free
                if (step.path.all { free shr it and 1L == 1L }) t.take(step.word, step.path) else t
            },
            reachedBy = { now -> (now as HexwordsState).taken.drop(had).any { it.word == step.word } },
        )
    }
}
