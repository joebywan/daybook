package com.joebywan.daybook.puzzles

/**
 * Lexicon, reasoned the way a person reasons it: from the marks already on the board. The ladder is the shared
 * [GuessTeacher] (mistake, opener, pin, submit, only word, best guess); this file is Lexicon's words for it.
 *
 * - [MISTAKE]: the row cannot be submitted because it is not a word. Any word is allowed, so probing with one that
 *   cannot be the answer is never called wrong.
 * - [OPENER]: nothing is known yet, so one of the words that split the answer list best (which one is the board's
 *   [LexiconState.opener]), given as a clue.
 * - [PIN]: a letter known to be in the word has only as many slots left as it has copies.
 * - [SUBMIT]: the row fits every mark; press Enter.
 * - [ONLY_WORD] / [CHOOSE]: pins have run out. Of the answer list, one word fits (type it), or several do and this
 *   guess leaves the fewest whichever way it is marked.
 *
 * Those three name a word, so they give it as a crossword-style clue ([clueFor]) and leave the player to work it out;
 * only "Show me" types it.
 */
internal object LexiconTeacher {

    const val MISTAKE = GuessTeacher.MISTAKE
    const val OPENER = GuessTeacher.OPENER
    const val PIN = "pinned-letter"
    const val SUBMIT = GuessTeacher.SUBMIT
    const val ONLY_WORD = "only-word-left"
    const val CHOOSE = GuessTeacher.CHOOSE

    /** Every step [teach] can produce, for reports. */
    val TECHNIQUES = listOf(MISTAKE, OPENER, PIN, SUBMIT, ONLY_WORD, CHOOSE)

    /** The two steps that point at a word from the answer list rather than at a fact about a slot. */
    val FALLBACKS = listOf(ONLY_WORD, CHOOSE)

    const val MAX_EXPLANATION = GuessTeacher.MAX_EXPLANATION
    const val KEY = GuessTeacher.KEY
    const val ENTER = GuessTeacher.ENTER
    const val DELETE = GuessTeacher.DELETE

    /**
     * First guesses for an empty board, one per board by [LexiconState.opener], so the opening hint is not the same clue
     * every day. Each is an answer-list word among the best of its list at leaving few words: measured over every answer
     * (the mean number left, sum of squared outcome sizes over the list), five letters 48.4 for RAISE to 52.0 for STARE,
     * four letters 98.1 for TALE to 110.1 for LEAN, within 12% of the best. `LexiconTeachingTest` re-measures them.
     */
    fun openers(length: Int): List<String> =
        if (length == 4) listOf("tale", "sale", "late", "real", "lane", "seal", "lean")
        else listOf("raise", "arise", "irate", "slate", "arose", "alert", "stare")

    /** "Clue: Lift (5)": a clue to [word] without the word, ready to drop into an explanation. */
    private fun clueFor(word: String) = "“${WordList.clue(word)}” (${word.length})"

    val vocab = object : GuessVocab {
        override val alphabet = "abcdefghijklmnopqrstuvwxyz"
        override fun isLegal(row: String) = WordList.isWord(row)
        override fun answers(length: Int) = WordList.answers(length)
        override fun openers(length: Int) = LexiconTeacher.openers(length)
        override val pinTechnique = PIN
        override val onlyTechnique = ONLY_WORD
        override val noun = "word"
        override fun shout(symbol: Char) = symbol.uppercaseChar().toString()
        override fun show(row: String) = row.uppercase()
        override fun refusal(row: String) =
            "${row.uppercase()} isn't in the word list, so Enter won't take it. Change a letter or two."
        override val openerNudge = "Nothing is known yet. Start with a word of common letters."
        override fun openerText(word: String) =
            "A good first guess has common letters, so it cuts the list down fast. Try the word that goes ${clueFor(word)}."
        override val onlyNudge = "Check every mark against the word list."
        override fun onlyText(word: String) =
            "Only one word on the answer list fits every mark. Its clue: ${clueFor(word)}. Type it."
        override val chooseNudge = "Nothing more is forced. Pick a word that fits and splits what is left."

        /**
         * "7 words on the answer list fit every mark. Try the one that goes "Lift" (5): it can be marked 5 ways, and none
         * leaves more than 2." Read off the candidates alone, as the pick is.
         */
        override fun splitText(word: String, size: Int, ways: Int, worst: Int): List<String> {
            val clue = clueFor(word)
            val long = when {
                size == 2 -> "Two words fit every mark, and either could be it. Try the one that goes $clue: " +
                    "if it isn't the word, its marks point to the other."
                worst == 1 -> "$size words on the answer list fit every mark. Try the one that goes $clue: " +
                    "each of them would mark it differently, so after it you will know the word."
                else -> "$size words on the answer list fit every mark. Try the one that goes $clue: " +
                    "it can be marked $ways ways, and none leaves more than $worst."
            }
            val short = if (size == 2) "Two words fit. Try $clue: if it isn't the word, it is the other."
            else "$size words fit. Try $clue: whatever its marks, at most $worst will be left."
            return listOf(long, short)
        }
    }

    private val engine = GuessTeacher(vocab)

    /** The opener for [pick], a board's [LexiconState.opener]. */
    fun opener(length: Int, pick: Int = 0): String = engine.opener(length, pick)

    fun tile(length: Int, row: Int, slot: Int) = GuessTeacher.tile(length, row, slot)

    fun key(letter: Char) = engine.key(letter)

    /** Answer-list words that would have drawn every mark on the board. */
    fun candidates(length: Int, guesses: List<String>, marks: List<List<Int>>): List<String> =
        engine.candidates(length, guesses, marks)

    fun bestGuess(fits: List<String>): String = GuessTeacher.bestGuess(fits)

    fun teach(length: Int, guesses: List<String>, marks: List<List<Int>>, current: String, opener: Int = 0): GuessTeacher.Step? =
        engine.teach(length, guesses, marks, current, opener)

    fun deduction(s: LexiconState) = engine.deduction(s, FALLBACKS, s.opener)
}
