package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.has
import com.joebywan.daybook.core.listed
import com.joebywan.daybook.puzzles.MiniLogic.BLACK
import com.joebywan.daybook.puzzles.MiniLogic.OPEN

/**
 * The Mini crossword, reasoned from the crossings.
 *
 * The split is the point, as in [CodewordTeacher]: [findStep] and [deduce] are handed the letters on the board and
 * nothing else, so a step cannot lean on the stored answer. The words that *fit* a slot are the grid list's words of
 * its length ([WheelWords.grid]) that agree with the letters in it. A clue is read only to quote it: the teacher cannot
 * solve a clue, and matching a clue to its word would be reading the answer.
 *
 * Techniques, simplest first; each ends in a word the player types (only "Show me" types it):
 * 1. [ONE_WORD] — the letters in a slot leave one word that fits.
 * 2. [CROSSING] — of the words that fit a slot, only one leaves every slot crossing it a word that fits.
 * 3. [ALL_WORDS] — every square keeps only the letters both its words allow, over and over until nothing changes,
 *    and a slot is left one word. It cannot name one crossing, so it says so.
 * 4. [CLUE] — the fallback: no crossing settles a word. Its first tap names the open slot the fewest words fit with
 *    that word's second clue ("Another way in: ..."), chosen from the board alone; only the next tap reads the answer,
 *    says so, and gives one letter of the slot. From an empty grid nothing else can start, so every board needs some:
 *    they are the letters a player must find from the clues, and the tiers are cut by them (`docs/MINI_SPEC.md`).
 *
 * A mistake is a letter that differs from the stored answer, sound because each clue names one word. Every loop walks
 * slots, squares and words in index order, so a board gets the same hint on every platform.
 *
 * Highlight indices: squares `0 until size * size`, letter l's key [MiniLogic.letterKey], the clue bar [MiniLogic.CLUE_BAR].
 */
internal object MiniTeacher {

    const val ONE_WORD = "one-word"
    const val CROSSING = "crossing"
    const val ALL_WORDS = "all-words"
    const val CLUE = "clue"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(ONE_WORD, CROSSING, ALL_WORDS, CLUE)

    private const val ALL = (1 shl 26) - 1

    /** The board's slots, and for every square the slots through it and its place in each. */
    internal class Shape(val size: Int, grid: String) {
        val slots: List<MiniLogic.Slot> = MiniLogic.slots(size, grid)
        /** Per square: (slot, place) pairs, the across one first. */
        val through: List<List<Pair<Int, Int>>> = List(size * size) { sq ->
            slots.indices.mapNotNull { r -> slots[r].squares.indexOf(sq).takeIf { it >= 0 }?.let { r to it } }
        }
        /** Per slot: the other slots crossing it, as (place in this slot, crossing slot, place in that one). */
        val crossings: List<List<Triple<Int, Int, Int>>> = slots.indices.map { r ->
            slots[r].squares.withIndex().flatMap { (i, sq) -> through[sq].filter { it.first != r }.map { Triple(i, it.first, it.second) } }
        }
    }

    /** The words still fitting every slot, from the letters placed (and, once [settle] has run, from each other). */
    internal class Sight(val shape: Shape, letters: String) {
        val dom = IntArray(shape.size * shape.size) { sq ->
            when (val c = letters[sq]) {
                BLACK -> 0
                OPEN -> ALL
                else -> bit(c - 'a')
            }
        }
        val words: Array<IntArray> = Array(shape.slots.size) { r -> filter(r, WheelWords.grid(shape.slots[r].length).indices.toList().toIntArray()) }
        var consistent = words.all { it.isNotEmpty() }

        private fun filter(r: Int, from: IntArray): IntArray {
            val slot = shape.slots[r]
            val list = WheelWords.grid(slot.length)
            return from.filter { w -> val word = list[w]; slot.squares.indices.all { has(dom[slot.squares[it]], word[it] - 'a') } }.toIntArray()
        }

        fun word(r: Int, w: Int): String = WheelWords.grid(shape.slots[r].length)[w]

        /** The letters place [i] of slot [r] takes in some word still fitting it. */
        fun support(r: Int, i: Int): Int {
            var m = 0
            val list = WheelWords.grid(shape.slots[r].length)
            for (w in words[r]) m = m or bit(list[w][i] - 'a')
            return m
        }

        /** Every slot and square cut by the other, until nothing changes. False when some slot is left no word. */
        fun settle(): Boolean {
            do {
                var changed = false
                for (r in shape.slots.indices) {
                    val next = filter(r, words[r])
                    if (next.isEmpty()) { consistent = false; return false }
                    if (next.size != words[r].size) { words[r] = next; changed = true }
                    for (i in shape.slots[r].squares.indices) {
                        val sq = shape.slots[r].squares[i]
                        val m = dom[sq] and support(r, i)
                        if (m != dom[sq]) { dom[sq] = m; changed = true }
                    }
                }
            } while (changed)
            return true
        }
    }

    /**
     * A step: slot [slot] takes [word] (the word techniques), or square [square] takes [letter] ([CLUE]). [weighed] is how
     * many words fit the slot before the step; [crossers] the crossing slots that ruled the others out ([CROSSING]).
     */
    internal class Hit(
        val technique: String,
        val slot: Int,
        val word: String? = null,
        val square: Int = -1,
        val letter: Char = OPEN,
        val weighed: Int = 0,
        val crossers: List<Int> = emptyList(),
    )

    private fun open(letters: String, slot: MiniLogic.Slot) = slot.squares.any { letters[it] == OPEN }

    /**
     * The first step of the first technique (up to [upTo], an index into [TECHNIQUES]) that finds one, in slot order, or
     * null when none does. [letters] is the board as the player sees it; the stored answer is not a parameter.
     */
    internal fun findStep(shape: Shape, letters: String, upTo: Int = TECHNIQUES.indexOf(ALL_WORDS)): Hit? {
        val base = Sight(shape, letters)
        if (!base.consistent) return null
        val slots = shape.slots
        // 1. The letters in a slot leave one word.
        for (r in slots.indices) {
            if (open(letters, slots[r]) && base.words[r].size == 1) return Hit(ONE_WORD, r, base.word(r, base.words[r][0]), weighed = 1)
        }
        if (upTo < 1) return null
        // 2. Of the words that fit, one leaves every crossing slot a word.
        for (r in slots.indices) {
            if (!open(letters, slots[r])) continue
            val crossing = shape.crossings[r].filter { (i, _, _) -> letters[slots[r].squares[i]] == OPEN }
            val supports = crossing.map { (_, t, j) -> base.support(t, j) }
            val keep = base.words[r].filter { w ->
                val word = base.word(r, w)
                crossing.indices.all { k -> has(supports[k], word[crossing[k].first] - 'a') }
            }
            if (keep.size == 1) {
                val word = base.word(r, keep[0])
                val crossers = crossing.indices.filter { k ->
                    base.words[r].any { w -> w != keep[0] && !has(supports[k], base.word(r, w)[crossing[k].first] - 'a') }
                }.map { crossing[it].second }.distinct()
                return Hit(CROSSING, r, word, weighed = base.words[r].size, crossers = crossers)
            }
        }
        if (upTo < 2) return null
        // 3. Everything worked through together.
        val all = Sight(shape, letters)
        if (!all.settle()) return null
        for (r in slots.indices) {
            if (open(letters, slots[r]) && all.words[r].size == 1) return Hit(ALL_WORDS, r, all.word(r, all.words[r][0]), weighed = base.words[r].size)
        }
        return null
    }

    /** The open slot the fewest words fit (the first of those), the one a player would try a clue on. */
    internal fun clueSlot(shape: Shape, letters: String): Int? {
        val base = Sight(shape, letters)
        return shape.slots.indices.filter { open(letters, shape.slots[it]) }.minByOrNull { base.words[it].size }
    }

    /**
     * The hints' walk from an empty grid to [answer]: each step the first logic step, else a [CLUE] letter from the
     * answer. Null when a step ever disagrees with the answer (the ladder would be unsound) or a slot is left no word.
     */
    fun walk(size: Int, answer: String, upTo: Int = TECHNIQUES.indexOf(ALL_WORDS)): List<Hit>? {
        val shape = Shape(size, answer)
        val now = CharArray(answer.length) { if (answer[it] == BLACK) BLACK else OPEN }
        val steps = mutableListOf<Hit>()
        while (OPEN in now) {
            val letters = now.concatToString()
            val hit = findStep(shape, letters, upTo) ?: run {
                val r = clueSlot(shape, letters) ?: return null
                val sq = shape.slots[r].squares.first { now[it] == OPEN }
                Hit(CLUE, r, square = sq, letter = answer[sq])
            }
            if (hit.word != null) {
                val slot = shape.slots[hit.slot]
                if (MiniLogic.spelled(answer, slot) != hit.word) return null
                for (sq in slot.squares) now[sq] = answer[sq]
            } else {
                now[hit.square] = hit.letter
            }
            steps += hit
        }
        return steps
    }

    /** The deepest logic technique a walk used: what the tiers are cut by. A walk of clue letters and single words is [ONE_WORD]. */
    fun depthOf(walk: List<Hit>): String =
        walk.map { it.technique }.filter { it != CLUE }.maxByOrNull { TECHNIQUES.indexOf(it) } ?: ONE_WORD

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ------------------------

    const val MAX_EXPLANATION = 200
    const val MAX_NUDGE = 70

    /** The fallback's nudge carries a whole second clue, so it may run longer; the popover wraps it. */
    const val MAX_CLUE_NUDGE = 100

    /**
     * One step: [slot] to type [word] into, or [square] to take [letter] ([OPEN] to clear a mistake); where to look and
     * why. [square] is -1 for a word.
     */
    class Step(
        val technique: String,
        val slot: Int,
        val word: String?,
        val square: Int,
        val letter: Char,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    fun teach(s: MiniState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.letters, s.clues) ?: fallback(s)
    }

    /** A slot as the player reads it: letters in capitals, open squares as dashes. */
    private fun read(letters: String, slot: MiniLogic.Slot): String =
        slot.squares.joinToString(" ") { if (letters[it] == OPEN) "_" else letters[it].uppercase() }

    private val ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh")

    private fun fallback(s: MiniState): Step? {
        val shape = Shape(s.size, s.letters)
        val r = clueSlot(shape, s.letters) ?: return null
        val slot = shape.slots[r]
        val i = slot.squares.indexOfFirst { s.letters[it] == OPEN }
        val sq = slot.squares[i]
        val l = s.answer[sq]
        val where = if (i == 0) "starts with ${l.uppercase()}" else "has ${l.uppercase()} as its ${ORDINALS[i]} letter"
        val squares = slot.squares.toSet()
        // The second clue first (tap 1), the letter of the answer only on the next tap. A save from before second
        // clues has none: the nudge is then the old pointer to the slot.
        val second = s.secondClues.getOrNull(r)?.takeIf { it.isNotBlank() }
        return Step(
            CLUE, r, null, sq, l, squares, squares, if (second != null) "Another way in: ${slot.name}, \"$second\"." else "Try ${slot.name}.",
            "No crossing letters settle a word yet, so this hint reads the answer: ${slot.name}, \"${s.clues[r]}\", $where.",
        )
    }

    private fun mistake(s: MiniState): Step? {
        val wrong = s.letters.indices.filter { s.letters[it] != OPEN && s.letters[it] != BLACK && s.letters[it] != s.answer[it] }
        if (wrong.isEmpty()) return null
        val shape = Shape(s.size, s.letters)
        val dead = MiniLogic.deadSlots(s.size, s.letters).sorted()
        val sq = wrong.firstOrNull { w -> dead.any { w in shape.slots[it].squares } } ?: wrong.first()
        val l = s.letters[sq].uppercase()
        val clear = "Tap it and type over it, or Backspace to clear it."
        val inDead = dead.firstOrNull { sq in shape.slots[it].squares }
        if (inDead != null) {
            val slot = shape.slots[inDead]
            return Step(
                MISTAKE, inDead, null, sq, OPEN, setOf(sq), slot.squares.toSet(), "Check the $l in ${slot.name}.",
                "${slot.name} spells ${MiniLogic.spelled(s.letters, slot).uppercase()}, which is not a word in the list, and this $l is not the answer's. $clear",
            )
        }
        val (r, _) = shape.through[sq].first()
        val slot = shape.slots[r]
        return Step(
            MISTAKE, r, null, sq, OPEN, setOf(sq), slot.squares.toSet(), "Check the $l in ${slot.name}.",
            "This $l is not in the answer to ${slot.name}, \"${s.clues[r]}\". $clear",
        )
    }

    /** The step to hint from the letters on the board alone; [clues] are only quoted. */
    fun deduce(size: Int, letters: String, clues: List<String>): Step? {
        val shape = Shape(size, letters)
        val hit = findStep(shape, letters) ?: return null
        val r = hit.slot
        val slot = shape.slots[r]
        val squares = slot.squares.toSet()
        val clue = "\"${clues[r]}\""
        fun fit(text: String) = text.takeIf { it.length <= MAX_EXPLANATION }
        return when (hit.technique) {
            ONE_WORD -> Step(
                ONE_WORD, r, hit.word, -1, OPEN, squares, squares, "One word fits ${slot.name}.",
                fit("${slot.name} reads ${read(letters, slot)}. Only one word in the list fits those letters: the answer to $clue.")
                    ?: "${slot.name} reads ${read(letters, slot)}. Only one word in the list fits those letters.",
            )
            CROSSING -> {
                val names = hit.crossers.map { shape.slots[it].name }
                val cited = squares + hit.crossers.flatMap { shape.slots[it].squares.toList() }
                Step(
                    CROSSING, r, hit.word, -1, OPEN, squares, cited, "Look at the words crossing ${slot.name}.",
                    fit("${hit.weighed} words fit ${slot.name} (${read(letters, slot)}), but only one leaves ${listed(names)} a word that fits: the answer to $clue.")
                        ?: "${hit.weighed} words fit ${slot.name} (${read(letters, slot)}), but only one leaves ${listed(names)} a word that fits.",
                )
            }
            else -> Step(
                ALL_WORDS, r, hit.word, -1, OPEN, squares, squares + slot.squares.flatMap { sq -> shape.through[sq].flatMap { shape.slots[it.first].squares.toList() } },
                "Work the crossings through together.",
                fit("Work every crossing through together: each square keeps only letters both its words allow. That leaves ${slot.name} one word: the answer to $clue.")
                    ?: "Work every crossing through together: each square keeps only letters both its words allow. That leaves ${slot.name} one word.",
            )
        }
    }
}
