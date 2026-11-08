package com.joebywan.daybook.puzzles

/**
 * Equation, reasoned from the marks already on the board: Lexicon's ladder ([GuessTeacher]) with Equation's words.
 *
 * - [MISTAKE]: the row is not a true sum, so Enter will not take it. Any true sum is allowed, so a probe that cannot be
 *   the answer is never called wrong.
 * - [OPENER]: nothing is known yet; one of the sums that split the answers best, named outright (it tells nothing about
 *   the hidden one).
 * - [PIN]: a symbol known to be in the sum has only as many slots left as it has copies. A fact about the marks alone,
 *   so it holds for every true sum that fits them (`EquationTeachingTest` checks it against all of them).
 * - [SUBMIT]: the row fits every mark; press Enter.
 * - [ONLY_SUM] / [CHOOSE]: pins have run out. Of the sums it can be (true, and no lone 0), one fits or several do;
 *   these read that list, say so, and count as the fallback. They give the sum's shape (its signs and its right side,
 *   the left side's digits as ?) rather than the sum: only "Show me" types it.
 */
internal object EquationTeacher {

    const val MISTAKE = GuessTeacher.MISTAKE
    const val OPENER = GuessTeacher.OPENER
    const val PIN = "pinned-symbol"
    const val SUBMIT = GuessTeacher.SUBMIT
    const val ONLY_SUM = "only-sum-left"
    const val CHOOSE = GuessTeacher.CHOOSE

    /** Every step [teach] can produce, for reports. */
    val TECHNIQUES = listOf(MISTAKE, OPENER, PIN, SUBMIT, ONLY_SUM, CHOOSE)

    /** The two steps that point at a sum from the answer list rather than at a fact about a slot. */
    val FALLBACKS = listOf(ONLY_SUM, CHOOSE)

    /**
     * First guesses for an empty board, one per board by [EquationState.opener]. Each is an answer of its length among
     * the best at leaving few answers (the sum of squared outcome sizes over the list, measured by `EquationTeachingTest`
     * `the openers are among the best first guesses`).
     */
    fun openers(length: Int): List<String> = when (length) {
        6 -> listOf("28/7=4", "4*7=28", "42/7=6", "32/8=4", "36/9=4", "4*8=32", "42/6=7")
        7 -> listOf("5+26=31", "6+18=24", "6+25=31", "7+24=31", "8+16=24", "4+27=31", "8+15=23")
        else -> listOf("48-32=16", "48-36=12", "43-25=18", "43-27=16", "45-27=18", "46-27=19", "43-28=15")
    }

    /** One symbol as the player sees it: the minus, times and divide signs typeset. */
    fun shown(symbol: Char): String = when (symbol) {
        '*' -> "×"
        '/' -> "÷"
        '-' -> "−"
        else -> symbol.toString()
    }

    fun shown(row: String): String = row.map(::shown).joinToString("")

    /** [row]'s shape: its signs and right side, the left side's digits as ?. */
    private fun shape(row: String): String {
        val eq = row.indexOf('=')
        return shown(row.mapIndexed { i, c -> if (i < eq && c.isDigit()) '?' else c }.joinToString(""))
    }

    val vocab = object : GuessVocab {
        override val alphabet = EquationLogic.ALPHABET
        override fun isLegal(row: String) = EquationLogic.isTrue(row)
        override fun answers(length: Int) = EquationLogic.answers(length)
        override fun openers(length: Int) = EquationTeacher.openers(length)
        override val pinTechnique = PIN
        override val onlyTechnique = ONLY_SUM
        override val noun = "sum"
        override fun shout(symbol: Char) = shown(symbol)
        override fun show(row: String) = shown(row)
        override fun refusal(row: String): String {
            val why = EquationLogic.problem(row, row.length) ?: "not a true sum"
            return "${shown(row)} can't be guessed: ${why.replaceFirstChar { it.lowercaseChar() }}. " +
                "Every guess must be a true sum."
        }
        override val openerNudge = "Nothing is known yet. Start with a sum of many different symbols."
        override fun openerText(word: String) =
            "A good first guess uses many different digits and signs, so it cuts the list down fast. Try ${shown(word)}."
        override val onlyNudge = "Check every mark against the sums it could be."
        override fun onlyText(word: String) =
            "Only one sum it could be fits every mark. It has the shape ${shape(word)}. Work out the rest and type it."
        override val chooseNudge = "Nothing more is forced. Pick a sum that fits and splits what is left."
        override fun splitText(word: String, size: Int, ways: Int, worst: Int): List<String> {
            val it = shape(word)
            val long = when {
                size == 2 -> "Two sums fit every mark, and either could be it. Try the one shaped $it: " +
                    "if it isn't the sum, its marks point to the other."
                worst == 1 -> "$size sums fit every mark. Try one shaped $it: " +
                    "each would mark it differently, so after it you will know the sum."
                else -> "$size sums fit every mark. Try one shaped $it: it can be marked $ways ways, and none leaves more than $worst."
            }
            val short = if (size == 2) "Two sums fit. Try the one shaped $it: if it isn't the sum, it is the other."
            else "$size sums fit. Try one shaped $it: whatever its marks, at most $worst will be left."
            return listOf(long, short)
        }
    }

    private val engine = GuessTeacher(vocab)

    fun opener(length: Int, pick: Int = 0): String = engine.opener(length, pick)

    fun key(symbol: Char) = engine.key(symbol)

    /** Answers that would have drawn every mark on the board. */
    fun candidates(length: Int, guesses: List<String>, marks: List<List<Int>>): List<String> =
        engine.candidates(length, guesses, marks)

    fun teach(length: Int, guesses: List<String>, marks: List<List<Int>>, current: String, opener: Int = 0): GuessTeacher.Step? =
        engine.teach(length, guesses, marks, current, opener)

    fun deduction(s: EquationState) = engine.deduction(s, FALLBACKS, s.opener)
}
