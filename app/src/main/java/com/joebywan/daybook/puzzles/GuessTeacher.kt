package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.listed

/**
 * What a guess teacher needs from its game: the symbols, what may be submitted, the answers it can be, and its words.
 * Lexicon's is [LexiconTeacher.vocab], Equation's [EquationTeacher.vocab]. The answer itself is never part of it.
 */
internal interface GuessVocab {
    /** Every symbol a row can hold, in the order their keys are numbered ([GuessTeacher.key]). */
    val alphabet: String

    /** Whether [row] may be submitted: a word, a true sum. */
    fun isLegal(row: String): Boolean

    /** The rows the hidden one can be, sorted: what [GuessTeacher.ONLY] and [GuessTeacher.CHOOSE] read. */
    fun answers(length: Int): List<String>

    /** Strong first guesses for an empty board, one per board by its opener pick. */
    fun openers(length: Int): List<String>

    /** The technique names a pin and the last candidate go by. */
    val pinTechnique: String
    val onlyTechnique: String

    /** What the hidden row is called in a sentence: "word", "sum". */
    val noun: String

    /** One symbol as the player sees it ("E", "×"). */
    fun shout(symbol: Char): String

    /** A row as the player sees it ("BOAT", "12×3=36"). */
    fun show(row: String): String

    /** Why [row], whole, will not be taken: the mistake's explanation. */
    fun refusal(row: String): String

    val openerNudge: String
    fun openerText(word: String): String
    val onlyNudge: String
    fun onlyText(word: String): String
    val chooseNudge: String

    /**
     * Why [word] is the guess to make: of [size] rows that fit, its marks can come out [ways] ways, none leaving more than
     * [worst]. The long wording, then a short one for when the long does not fit [GuessTeacher.MAX_EXPLANATION].
     */
    fun splitText(word: String, size: Int, ways: Int, worst: Int): List<String>
}

/**
 * A guess game reasoned the way a person reasons it: from the marks already on the board. Lexicon's ladder, over any
 * alphabet ([GuessVocab]); Equation runs the same one.
 *
 * [teach] is handed exactly what the player sees: the guesses, the marks each drew, and the row being typed. It has no
 * parameter through which the hidden row could reach it, so a step cannot lean on the answer: a row that cannot be
 * submitted is a mistake whatever the answer is, and the last resort is "here is a row that fits every mark", which is
 * honest reasoning that does not fit in a sentence. That last resort is flagged so the coverage test can count it.
 *
 * What it teaches, simplest first:
 *
 * - [MISTAKE]: the row cannot be submitted. Any legal row is allowed, so probing with one that cannot be the answer is
 *   never called wrong.
 * - [OPENER]: nothing is known yet, so one of the rows that split the answers best.
 * - the pin ([GuessVocab.pinTechnique]): a symbol known to be in the answer has only as many slots left as it has
 *   copies, because every other slot is a green of another symbol or was marked against it.
 * - [SUBMIT]: the row fits every mark; press Enter.
 * - the last candidate ([GuessVocab.onlyTechnique]) / [CHOOSE]: pins have run out. Of the answers, one fits, or several
 *   do and this guess leaves the fewest whichever way it is marked.
 *
 * A pin is a fact about the marks alone, so it holds for every row that fits them, on the answer list or not. Only the
 * last two read the answer list, and they say so.
 *
 * Nothing here walks a hash container: symbols, slots and rows are visited by index, so the same board gets the same
 * hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal class GuessTeacher(private val vocab: GuessVocab) {

    companion object {
        const val MISTAKE = "mistake"
        const val OPENER = "opener"
        const val SUBMIT = "submit"
        const val CHOOSE = "best-guess"

        /**
         * The hint panel shows four lines of body text, about 170 characters on a phone. A step whose explanation would
         * be longer is shortened rather than shown cut off.
         */
        const val MAX_EXPLANATION = 170

        /** The most guesses weighed when picking [CHOOSE]'s row; the rest of the list is a stride. */
        private const val POOL = 300

        // ---- what the indices in a Deduction mean on a guess board ----------------------------------
        // A tile is `row * length + slot`, rows counting down the grid with the row being typed after the
        // submitted guesses, which is what `highlightGrid` expects. Keys and buttons have their own ranges.

        const val KEY = 3000
        const val ENTER = 4000
        const val DELETE = 4001

        fun tile(length: Int, row: Int, slot: Int) = row * length + slot

        /**
         * The word of [fits] that leaves the fewest of them in its worst case (then in the most outcomes, then first in
         * the list, so the pick is the same everywhere). A long list is thinned by a fixed stride rather than at random.
         */
        fun bestGuess(fits: List<String>): String {
            val stride = (fits.size + POOL - 1) / POOL
            var best = fits[0]
            var bestWorst = Int.MAX_VALUE
            var bestSpread = -1
            var i = 0
            while (i < fits.size) {
                val word = fits[i]
                val counts = outcomes(word, fits)
                val worst = counts.values.max()
                val spread = counts.size
                if (worst < bestWorst || (worst == bestWorst && spread > bestSpread)) {
                    best = word
                    bestWorst = worst
                    bestSpread = spread
                }
                i += stride
            }
            return best
        }

        /** How many of [fits] draw each mark code for [word]. A probe-only hash container: read for its size and values, never its order. */
        fun outcomes(word: String, fits: List<String>): Map<Int, Int> {
            val counts = HashMap<Int, Int>()
            for (f in fits) {
                val code = GuessMarks.markCode(word, f)
                counts[code] = (counts[code] ?: 0) + 1
            }
            return counts
        }
    }

    /** The key for [symbol]. */
    fun key(symbol: Char) = KEY + vocab.alphabet.indexOf(symbol)

    /** The opener for [pick], a board's opener field. */
    fun opener(length: Int, pick: Int = 0): String = vocab.openers(length).let { it[pick.mod(it.size)] }

    /** What a step asks the player to do. */
    sealed interface Move {
        /** Take the row back to its first [keep] symbols. */
        data class Retype(val keep: Int) : Move

        /** Type this whole row. It is done when a row that fits every mark is typed or submitted. */
        data class Fill(val word: String) : Move

        /** Type this opening row. It is done when any whole row is typed or submitted. */
        data class Open(val word: String) : Move

        /** Get [letter] into [slot]; "show me" types [word], which has it there and fits every mark. */
        data class Pin(val slot: Int, val letter: Char, val word: String) : Move

        data object Submit : Move
    }

    class Step(
        override val technique: String,
        val move: Move,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
        override val nudge: String,
        override val explanation: String,
    ) : TeachStep

    /** Answers that would have drawn every mark on the board. */
    fun candidates(length: Int, guesses: List<String>, marks: List<List<Int>>): List<String> =
        vocab.answers(length).filter { GuessMarks.consistent(it, guesses, marks) }

    /** What the marks say, slot by slot and symbol by symbol (symbol `k` is `alphabet[k]`). */
    private class Clues(val length: Int, val guesses: List<String>, val marks: List<List<Int>>, alphabet: String) {
        val size = alphabet.length

        /** The symbol a green fixed in each slot, and the guess that showed it. */
        val green = arrayOfNulls<Char>(length)
        val greenGuess = IntArray(length) { -1 }

        /** At least this many of each symbol: the most any one guess showed as green or yellow. */
        val min = IntArray(size)

        /** Whether a slot is ruled out for a symbol, and the guess that ruled it out. */
        val ruledBy = Array(length) { IntArray(size) { -1 } }

        /** The first tile showing each symbol as green or yellow, or -1. */
        val shownAt = IntArray(size) { -1 }

        init {
            for (g in guesses.indices) {
                val seen = IntArray(size)
                for (i in 0 until length) {
                    val k = alphabet.indexOf(guesses[g][i])
                    when (marks[g][i]) {
                        GuessMarks.CORRECT -> {
                            seen[k]++
                            if (green[i] == null) {
                                green[i] = guesses[g][i]
                                greenGuess[i] = g
                            }
                            if (shownAt[k] < 0) shownAt[k] = tile(length, g, i)
                        }
                        GuessMarks.PRESENT -> {
                            seen[k]++
                            if (ruledBy[i][k] < 0) ruledBy[i][k] = g
                            if (shownAt[k] < 0) shownAt[k] = tile(length, g, i)
                        }
                        else -> if (ruledBy[i][k] < 0) ruledBy[i][k] = g
                    }
                }
                for (k in 0 until size) if (seen[k] > min[k]) min[k] = seen[k]
            }
        }
    }

    fun teach(
        length: Int,
        guesses: List<String>,
        marks: List<List<Int>>,
        current: String,
        opener: Int = 0,
    ): Step? {
        val clues = Clues(length, guesses, marks, vocab.alphabet)
        mistake(clues, current)?.let { return it }
        if (guesses.isEmpty()) return opening(length, opener)
        pin(clues, current)?.let { return it }
        return choose(clues, current)
    }

    // ---- mistakes ---------------------------------------------------------------------------------

    private fun mistake(c: Clues, current: String): Step? {
        val row = c.guesses.size
        if (current.length != c.length) return null
        if (!vocab.isLegal(current)) {
            return Step(
                MISTAKE, Move.Retype(c.length - 1),
                focus = (0 until c.length).map { tile(c.length, row, it) }.toSet(),
                cited = emptySet(),
                targets = setOf(DELETE),
                nudge = "Look at the row you typed.",
                explanation = vocab.refusal(current),
            )
        }
        return null
    }

    // ---- opener -----------------------------------------------------------------------------------

    private fun opening(length: Int, pick: Int): Step {
        val word = opener(length, pick)
        return Step(
            OPENER, Move.Open(word),
            focus = (0 until length).map { tile(length, 0, it) }.toSet(),
            cited = emptySet(),
            targets = word.map { key(it) }.toSet(),
            nudge = vocab.openerNudge,
            explanation = vocab.openerText(word),
        )
    }

    // ---- pinned symbols ---------------------------------------------------------------------------

    private fun pin(c: Clues, current: String): Step? {
        val row = c.guesses.size
        for (k in 0 until c.size) {
            if (c.min[k] == 0) continue
            val letter = vocab.alphabet[k]
            val fixed = (0 until c.length).count { c.green[it] == letter }
            val need = c.min[k] - fixed
            if (need <= 0) continue
            val open = (0 until c.length).filter { c.green[it] == null && c.ruledBy[it][k] < 0 }
            if (open.size != need) continue
            val slot = open.firstOrNull { !(current.length > it && current[it] == letter) } ?: continue
            val word = wordWith(c, letter, slot) ?: continue
            val ruled = (0 until c.length).filter { c.green[it] == null && c.ruledBy[it][k] >= 0 }
            val taken = (0 until c.length).filter { c.green[it] != null && c.green[it] != letter }
            val cited = buildSet {
                add(c.shownAt[k])
                ruled.forEach { add(tile(c.length, c.ruledBy[it][k], it)) }
                taken.forEach { add(tile(c.length, c.greenGuess[it], it)) }
            }
            val shownTiles = (0 until c.guesses.size).flatMap { g ->
                (0 until c.length).filter { c.guesses[g][it] == letter && c.marks[g][it] != GuessMarks.ABSENT }
                    .map { tile(c.length, g, it) }
            }.toSet()
            val shout = vocab.shout(letter)
            val noun = vocab.noun
            val where = if (need == 1) "slot ${slot + 1}" else "slots ${listed(open.map { it + 1 })}"
            val long = buildString {
                append("$shout is in the $noun, but ")
                if (ruled.isNotEmpty()) append("not in ${slots(ruled)}")
                if (ruled.isNotEmpty() && taken.isNotEmpty()) append(", and ")
                if (taken.isNotEmpty()) {
                    append("${slots(taken)} already ${if (taken.size == 1) "holds" else "hold"} ")
                    append(listed(taken.map { vocab.shout(c.green[it]!!) }))
                }
                append(if (need == 1) ", so it goes in $where." else ", so it fills $where.")
            }
            val explanation = if (long.length <= MAX_EXPLANATION) long
            else "$shout is in the $noun, and every slot but $where is ruled out or taken."
            return Step(
                vocab.pinTechnique, Move.Pin(slot, letter, word),
                focus = shownTiles,
                cited = cited,
                targets = setOf(tile(c.length, row, slot), key(letter)),
                nudge = "Look at where $shout has been marked.",
                explanation = explanation,
            )
        }
        return null
    }

    /** The best answer that fits every mark and has [letter] in [slot], or null. */
    private fun wordWith(c: Clues, letter: Char, slot: Int): String? {
        val fits = candidates(c.length, c.guesses, c.marks).filter { it[slot] == letter }
        return if (fits.isEmpty()) null else bestGuess(fits)
    }

    // ---- choosing a row ---------------------------------------------------------------------------

    private fun choose(c: Clues, current: String): Step? {
        val row = c.guesses.size
        val fits = candidates(c.length, c.guesses, c.marks)
        if (fits.isEmpty()) return null
        val rowFits = current.length == c.length && fits.contains(current)
        if (rowFits) {
            return Step(
                SUBMIT, Move.Submit,
                focus = (0 until c.length).map { tile(c.length, row, it) }.toSet(),
                cited = emptySet(),
                targets = setOf(ENTER),
                nudge = "Your row fits every mark.",
                explanation = "${vocab.show(current)} fits every mark so far, so it could be the ${vocab.noun}. " +
                    "Press Enter to try it.",
            )
        }
        val lastRow = (0 until c.length).map { tile(c.length, row - 1, it) }.toSet()
        if (fits.size == 1) {
            val word = fits[0]
            return Step(
                vocab.onlyTechnique, Move.Fill(word),
                focus = lastRow,
                cited = lastRow,
                targets = word.map { key(it) }.toSet(),
                nudge = vocab.onlyNudge,
                explanation = vocab.onlyText(word),
            )
        }
        val word = bestGuess(fits)
        val counts = outcomes(word, fits)
        val split = vocab.splitText(word, fits.size, counts.size, counts.values.max())
        return Step(
            CHOOSE, Move.Fill(word),
            focus = lastRow,
            cited = lastRow,
            targets = word.map { key(it) }.toSet(),
            nudge = vocab.chooseNudge,
            explanation = split.firstOrNull { it.length <= MAX_EXPLANATION } ?: split.last(),
        )
    }

    private fun slots(slots: List<Int>): String =
        (if (slots.size == 1) "slot " else "slots ") + listed(slots.map { it + 1 })

    // ---- the play screen's side -------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out, for [s]. The teacher is handed the guesses and their marks, never
     * the answer. [fallbacks] are the techniques that read the answer list.
     */
    fun <S> deduction(s: S, fallbacks: List<String>, opener: Int): Deduction?
        where S : PuzzleState, S : GuessRows<S> {
        if (!s.open) return null
        val marks = s.allMarks
        val step = teach(s.length, s.guesses, marks, s.current, opener) ?: return null
        fun fits(word: String) = GuessMarks.consistent(word, s.guesses, marks)
        fun newGuesses(now: GuessRows<*>) = now.guesses.drop(s.guesses.size)
        return Deduction(
            step,
            mistake = step.technique == MISTAKE,
            fallback = step.technique in fallbacks,
            applyTo = { now ->
                @Suppress("UNCHECKED_CAST")
                applyMove(now as S, step.move)
            },
            reachedBy = { now ->
                val t = now as GuessRows<*>
                when (val m = step.move) {
                    is Move.Retype ->
                        t.current.length <= m.keep || newGuesses(t).isNotEmpty()
                    is Move.Open ->
                        t.current.length == t.length || newGuesses(t).isNotEmpty()
                    is Move.Pin ->
                        (t.current.length > m.slot && t.current[m.slot] == m.letter) ||
                            newGuesses(t).any { it[m.slot] == m.letter }
                    // Any whole row that fits the marks does: the suggestion was one of several.
                    is Move.Fill ->
                        (t.current.length == t.length && fits(t.current)) || newGuesses(t).any(::fits)
                    Move.Submit -> newGuesses(t).isNotEmpty()
                }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun <S : GuessRows<S>> applyMove(s: S, move: Move): S {
        fun typed(word: String) = if (s.current == word) s else s.withRows(s.guesses, word)
        return when (move) {
            is Move.Retype ->
                if (s.current.length <= move.keep) s else s.withRows(s.guesses, s.current.take(move.keep))
            is Move.Open -> typed(move.word)
            is Move.Pin -> typed(move.word)
            is Move.Fill -> typed(move.word)
            Move.Submit -> s.submit()
        }
    }
}
