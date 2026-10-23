package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction

/**
 * Word Wheel, reasoned from what the player can see: the wheel, the shape of each slot, the letters already in the grid,
 * and the words already tried (found ones and bonus ones, which are known not to be the word of any empty slot).
 *
 * - [PINNED]: an empty slot whose length, crossing letters and the wheel leave exactly one grid word, given by its
 *   crossword clue ("five letters, S _ O _ E: only one fits. Its clue: “A small rock” (5)"), never the word itself,
 *   which only "Show me" types. This reads the grid list, the public list of well-known words a slot can hold
 *   ([WheelWords.grid]), never the board's stored words, so it is reasoning: the stored word is always among the words
 *   that fit, and when one fits it is that one.
 * - [CLUE]: nothing is pinned. The slot with the fewest words that fit is named with its word's crossword clue. That reads
 *   the stored word, so it is the fallback, and it says so ("the one here is clued").
 *
 * No mistake is possible: a word that is not the slot's is simply not accepted (or is a bonus word), so nothing is ever
 * flagged. What is measured is the share of steps that are pinned against those that need the clue (`docs/TEACHING.md`).
 *
 * Every loop walks slots and lists by index, so the same board gets the same hint on every platform.
 *
 * Highlight indices: grid squares `row * width + col`; wheel letter `i` (its place in [WordWheelState.wheel], not where a
 * shuffle has moved it on screen) is [WHEEL] + i.
 */
internal object WordWheelTeacher {

    const val PINNED = "pinned"
    const val CLUE = "clue"

    /** Every step [teach] can produce, simplest first. */
    val TECHNIQUES = listOf(PINNED, CLUE)

    /** The step that reads a stored word. */
    val FALLBACKS = listOf(CLUE)

    const val WHEEL = 1000
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** A slot as the player sees it: where it runs and how long it is, not its word. */
    class Shape(val row: Int, val col: Int, val across: Boolean, val length: Int) {
        fun cell(i: Int, width: Int): Int = if (across) row * width + col + i else (row + i) * width + col
    }

    class Step(
        val technique: String,
        val slot: Int,
        /** The word the move enters: the one word that fits for [PINNED], the stored one for [CLUE]. */
        val word: String,
        /** How many grid words fit the slot. */
        val choices: Int,
        val nudge: String,
        val explanation: String,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
    )

    fun shapes(s: WordWheelState): List<Shape> = s.slots.map { Shape(it.row, it.col, it.across, it.length) }

    /** Grid words of [length] from [wheel] that agree with the letters showing ([pattern], null for a blank) and were not tried yet. */
    fun candidates(wheel: String, pattern: List<Char?>, tried: List<String>): List<String> =
        WheelWords.grid(pattern.size).filter { w ->
            w !in tried && WheelWords.spellable(w, wheel) && pattern.indices.all { pattern[it] == null || pattern[it] == w[it] }
        }

    fun teach(s: WordWheelState): Step? {
        if (s.solved) return null
        val empty = s.slots.indices.filter { !s.filled(it) }
        return deduce(s.wheel, s.width, shapes(s), empty, s.shown().toList(), s.found + s.bonus) ?: clue(s, empty)
    }

    /**
     * A slot pinned to one word, from visible facts only: the wheel, the slots' shapes, which are still [empty], the letters
     * [shown] in the grid and the words [tried]. Nothing here can see a slot's word.
     */
    fun deduce(wheel: String, width: Int, shapes: List<Shape>, empty: List<Int>, shown: List<Char?>, tried: List<String>): Step? {
        for (k in empty) {
            val shape = shapes[k]
            val pattern = List(shape.length) { shown[shape.cell(it, width)] }
            val fits = candidates(wheel, pattern, tried)
            if (fits.size != 1) continue
            val word = fits[0]
            val cells = List(shape.length) { shape.cell(it, width) }
            val known = cells.filter { shown[it] != null }.toSet()
            val shapeText = pattern.joinToString(" ") { it?.uppercaseChar()?.toString() ?: "_" }
            val explanation = if (known.isEmpty()) {
                "Of the well-known words the wheel spells, only one has ${shape.length} letters" +
                    (if (shape.length == wheel.length) ", using every letter" else "") + ". Its clue: ${clueFor(word)}."
            } else {
                "${shape.length} letters, $shapeText. Of the well-known words the wheel spells, only one fits. Its clue: ${clueFor(word)}."
            }
            return Step(
                PINNED, k, word, 1,
                nudge = if (known.isEmpty()) "One slot's length leaves only one word." else "The letters already in this slot leave one word.",
                explanation = explanation,
                focus = cells.toSet(),
                cited = known,
                targets = cells.toSet(),
            )
        }
        return null
    }

    /** Nothing is pinned: the empty slot with the fewest words that fit, given by its word's clue. Reads the stored word. */
    private fun clue(s: WordWheelState, empty: List<Int>): Step? {
        val shown = s.shown()
        val tried = s.found + s.bonus
        var best = -1
        var bestCount = Int.MAX_VALUE
        for (k in empty) {
            val slot = s.slots[k]
            val pattern = List(slot.length) { shown[slot.cell(it, s.width)] }
            val n = candidates(s.wheel, pattern, tried).size
            if (n < bestCount) {
                best = k
                bestCount = n
            }
        }
        if (best < 0) return null
        val slot = s.slots[best]
        val cells = slot.cells(s.width)
        return Step(
            CLUE, best, slot.word, bestCount,
            nudge = "Try the slot with the fewest words that fit.",
            explanation = "$bestCount words from the wheel fit this ${slot.length}-letter slot. " +
                "The one here is clued ${clueFor(slot.word)}.",
            focus = cells.toSet(),
            cited = cells.filter { shown[it] != null }.toSet(),
            targets = cells.toSet(),
        )
    }

    /** "“Lift” (5)": a crossword clue to [word] without the word, as Lexicon gives it. Only "Show me" types the word. */
    private fun clueFor(word: String) = "\u201C${WheelWords.clue(word)}\u201D (${word.length})"

    /** The wheel letters that spell [word], first unused copy of each letter, as highlight indices. */
    fun letters(wheel: String, word: String): Set<Int> {
        val used = BooleanArray(wheel.length)
        val out = mutableSetOf<Int>()
        for (c in word) {
            val i = wheel.indices.firstOrNull { !used[it] && wheel[it] == c } ?: continue
            used[i] = true
            out += WHEEL + i
        }
        return out
    }

    /** What a hints-alone walk of a board meets: steps by technique, and the most words that fit the slot a clue names. */
    class Walk(val pinned: Int, val clued: Int, val maxChoice: Int)

    /** Fills [start] by hints alone, counting what it takes. The generator keeps a board only if [Walk.maxChoice] stays small. */
    fun walk(start: WordWheelState): Walk {
        var s = start
        var pinned = 0
        var clued = 0
        var maxChoice = 0
        while (!s.solved) {
            val step = teach(s) ?: break
            if (step.technique == PINNED) pinned++ else {
                clued++
                maxChoice = maxOf(maxChoice, step.choices)
            }
            s = s.enter(step.word)
        }
        return Walk(pinned, clued, maxChoice)
    }

    /** The [Deduction] for the play screen; `WordWheel.teach` is just this. */
    fun deduction(s: WordWheelState): Deduction? {
        val step = teach(s) ?: return null
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            fallback = step.technique in FALLBACKS,
            applyTo = { now -> (now as WordWheelState).enter(step.word) },
            reachedBy = { now -> step.word in (now as WordWheelState).found },
        )
    }
}
