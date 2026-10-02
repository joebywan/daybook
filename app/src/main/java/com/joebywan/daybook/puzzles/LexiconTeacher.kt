package com.joebywan.daybook.puzzles

/**
 * Lexicon, reasoned the way a person reasons it: from the marks already on the board.
 *
 * [teach] is handed exactly what the player sees: the guesses, the marks each drew, and the row
 * being typed. It has no parameter through which the hidden word could reach it, so a step cannot
 * lean on the answer, and it never needs it: a row that cannot be submitted is a mistake whatever
 * the word is, and the last resort is "here is a word that fits every mark", which is honest
 * reasoning that does not fit in a sentence. That last resort is flagged so the coverage test can
 * count it.
 *
 * What it teaches, simplest first:
 *
 * - [MISTAKE] — the row cannot be submitted because it is not a word. Any word is allowed, so
 *   probing with one that cannot be the answer is never called wrong.
 * - [OPENER] — nothing is known yet, so a word that splits the answer list best.
 * - [PIN] — a letter known to be in the word has only as many slots left as it has copies, because
 *   every other slot is a green of another letter or was marked against it.
 * - [SUBMIT] — the row fits every mark; press Enter.
 * - [ONLY_WORD] / [CHOOSE] — pins have run out. Of the answer list, one word fits (type it), or
 *   several do and this guess leaves the fewest whichever way it is marked.
 *
 * A pin is a fact about the marks alone, so it holds for every word that fits them, on the answer
 * list or not. Only [ONLY_WORD] and [CHOOSE] read the answer list, and they say so.
 *
 * Nothing here walks a hash container: letters, slots and words are visited by index, so the same
 * board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object LexiconTeacher {

    const val MISTAKE = "mistake"
    const val OPENER = "opener"
    const val PIN = "pinned-letter"
    const val SUBMIT = "submit"
    const val ONLY_WORD = "only-word-left"
    const val CHOOSE = "best-guess"

    /** Every step [teach] can produce, for reports. */
    val TECHNIQUES = listOf(MISTAKE, OPENER, PIN, SUBMIT, ONLY_WORD, CHOOSE)

    /** The two steps that point at a word from the answer list rather than at a fact about a slot. */
    val FALLBACKS = listOf(ONLY_WORD, CHOOSE)

    /**
     * The hint panel shows four lines of body text, about 170 characters on a phone. A step whose
     * explanation would be longer is shortened rather than shown cut off.
     */
    const val MAX_EXPLANATION = 170

    /** The most guesses weighed when picking [CHOOSE]'s word; the rest of the list is a stride. */
    private const val POOL = 300

    /**
     * The first guess on an empty board: the answer-list word that leaves the fewest words in
     * expectation (found offline over every answer; `LexiconTeachingTest` checks it is a word of
     * the right length and still the best of its list).
     */
    fun opener(length: Int): String = if (length == 4) "tale" else "raise"

    // ---- what the indices in a Deduction mean on Lexicon's board -----------------------------------
    // A tile is `row * length + slot`, rows counting down the grid with the row being typed after the
    // submitted guesses, which is what `highlightGrid` expects. Keys and buttons have their own ranges.

    const val KEY = 3000
    const val ENTER = 4000
    const val DELETE = 4001

    fun tile(length: Int, row: Int, slot: Int) = row * length + slot

    fun key(letter: Char) = KEY + (letter - 'a')

    /** What a step asks the player to do. */
    sealed interface Move {
        /** Take the row back to its first [keep] letters. */
        data class Retype(val keep: Int) : Move

        /** Type this whole word. It is done when a row that fits every mark is typed or submitted. */
        data class Fill(val word: String) : Move

        /** Type this opening word. It is done when any whole row is typed or submitted. */
        data class Open(val word: String) : Move

        /** Get [letter] into [slot]; "show me" types [word], which has it there and fits every mark. */
        data class Pin(val slot: Int, val letter: Char, val word: String) : Move

        data object Submit : Move
    }

    class Step(
        val technique: String,
        val move: Move,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    /** Answer-list words that would have drawn every mark on the board. */
    fun candidates(length: Int, guesses: List<String>, marks: List<List<Int>>): List<String> =
        WordList.answers(length).filter { LexiconRules.consistent(it, guesses, marks) }

    /** What the marks say, slot by slot and letter by letter. */
    private class Clues(val length: Int, val guesses: List<String>, val marks: List<List<Int>>) {
        /** The letter a green fixed in each slot, and the guess that showed it. */
        val green = arrayOfNulls<Char>(length)
        val greenGuess = IntArray(length) { -1 }

        /** At least this many of each letter: the most any one guess showed as green or yellow. */
        val min = IntArray(26)

        /** Whether a slot is ruled out for a letter, and the guess that ruled it out. */
        val ruledBy = Array(length) { IntArray(26) { -1 } }

        /** The first tile showing each letter as green or yellow, or -1. */
        val shownAt = IntArray(26) { -1 }

        init {
            for (g in guesses.indices) {
                val seen = IntArray(26)
                for (i in 0 until length) {
                    val k = guesses[g][i] - 'a'
                    when (marks[g][i]) {
                        LexiconMark.CORRECT -> {
                            seen[k]++
                            if (green[i] == null) {
                                green[i] = guesses[g][i]
                                greenGuess[i] = g
                            }
                            if (shownAt[k] < 0) shownAt[k] = tile(length, g, i)
                        }
                        LexiconMark.PRESENT -> {
                            seen[k]++
                            if (ruledBy[i][k] < 0) ruledBy[i][k] = g
                            if (shownAt[k] < 0) shownAt[k] = tile(length, g, i)
                        }
                        else -> if (ruledBy[i][k] < 0) ruledBy[i][k] = g
                    }
                }
                for (k in 0 until 26) if (seen[k] > min[k]) min[k] = seen[k]
            }
        }
    }

    fun teach(
        length: Int,
        guesses: List<String>,
        marks: List<List<Int>>,
        current: String,
    ): Step? {
        val clues = Clues(length, guesses, marks)
        mistake(clues, current)?.let { return it }
        if (guesses.isEmpty()) return opening(length)
        pin(clues, current)?.let { return it }
        return choose(clues, current)
    }

    // ---- mistakes ---------------------------------------------------------------------------------

    private fun mistake(c: Clues, current: String): Step? {
        val row = c.guesses.size
        if (current.length != c.length) return null
        if (!WordList.isWord(current)) {
            return Step(
                MISTAKE, Move.Retype(c.length - 1),
                focus = (0 until c.length).map { tile(c.length, row, it) }.toSet(),
                cited = emptySet(),
                targets = setOf(DELETE),
                nudge = "Look at the row you typed.",
                explanation = "${current.uppercase()} isn't in the word list, so Enter won't take it. " +
                    "Change a letter or two.",
            )
        }
        return null
    }

    // ---- opener -----------------------------------------------------------------------------------

    private fun opening(length: Int): Step {
        val word = opener(length)
        return Step(
            OPENER, Move.Open(word),
            focus = (0 until length).map { tile(length, 0, it) }.toSet(),
            cited = emptySet(),
            targets = word.map { key(it) }.toSet(),
            nudge = "Nothing is known yet. Start with a word of common letters.",
            explanation = "${word.uppercase()} is a good first guess: its letters turn up in more words than most, " +
                "so whatever the marks say, they cut the list down fast.",
        )
    }

    // ---- pinned letters ---------------------------------------------------------------------------

    private fun pin(c: Clues, current: String): Step? {
        val row = c.guesses.size
        for (k in 0 until 26) {
            if (c.min[k] == 0) continue
            val letter = 'a' + k
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
                (0 until c.length).filter { c.guesses[g][it] == letter && c.marks[g][it] != LexiconMark.ABSENT }
                    .map { tile(c.length, g, it) }
            }.toSet()
            val shout = letter.uppercaseChar()
            val where = if (need == 1) "slot ${slot + 1}" else "slots ${list(open.map { it + 1 })}"
            val long = buildString {
                append("$shout is in the word, but ")
                if (ruled.isNotEmpty()) append("not in ${slots(ruled)}")
                if (ruled.isNotEmpty() && taken.isNotEmpty()) append(", and ")
                if (taken.isNotEmpty()) {
                    append("${slots(taken)} already ${if (taken.size == 1) "holds" else "hold"} ")
                    append(list(taken.map { c.green[it]!!.uppercaseChar() }))
                }
                append(if (need == 1) ", so it goes in $where." else ", so it fills $where.")
            }
            val explanation = if (long.length <= MAX_EXPLANATION) long
            else "$shout is in the word, and every slot but $where is ruled out or taken."
            return Step(
                PIN, Move.Pin(slot, letter, word),
                focus = shownTiles,
                cited = cited,
                targets = setOf(tile(c.length, row, slot), key(letter)),
                nudge = "Look at where $shout has been marked.",
                explanation = explanation,
            )
        }
        return null
    }

    /** The best answer-list word that fits every mark and has [letter] in [slot], or null. */
    private fun wordWith(c: Clues, letter: Char, slot: Int): String? {
        val fits = candidates(c.length, c.guesses, c.marks).filter { it[slot] == letter }
        return if (fits.isEmpty()) null else bestGuess(fits, fits)
    }

    // ---- choosing a word --------------------------------------------------------------------------

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
                explanation = "${current.uppercase()} fits every mark so far, so it could be the word. " +
                    "Press Enter to try it.",
            )
        }
        val lastRow = (0 until c.length).map { tile(c.length, row - 1, it) }.toSet()
        if (fits.size == 1) {
            val word = fits[0]
            return Step(
                ONLY_WORD, Move.Fill(word),
                focus = lastRow,
                cited = lastRow,
                targets = word.map { key(it) }.toSet(),
                nudge = "Check every mark against the word list.",
                explanation = "Only one word on the answer list fits every mark: ${word.uppercase()}. Type it.",
            )
        }
        val word = bestGuess(fits, fits)
        val worst = worstCase(word, fits)
        val shown = if (fits.size == 2) {
            "Two words fit every mark: ${fits[0].uppercase()} and ${fits[1].uppercase()}. Either could be it, so try ${word.uppercase()}."
        } else {
            "${fits.size} words on the answer list fit every mark. ${word.uppercase()} is one, and " +
                "however it is marked it leaves at most $worst."
        }
        return Step(
            CHOOSE, Move.Fill(word),
            focus = lastRow,
            cited = lastRow,
            targets = word.map { key(it) }.toSet(),
            nudge = "Nothing more is forced. Pick a word that fits and splits what is left.",
            explanation = shown,
        )
    }

    /**
     * The word in [pool] that leaves the fewest of [fits] in its worst case (then in the most
     * outcomes, then first alphabetically, so the pick is the same everywhere). A long pool is
     * thinned by a fixed stride rather than at random.
     */
    fun bestGuess(pool: List<String>, fits: List<String>): String {
        val stride = (pool.size + POOL - 1) / POOL
        var best = pool[0]
        var bestWorst = Int.MAX_VALUE
        var bestSpread = -1
        var i = 0
        while (i < pool.size) {
            val word = pool[i]
            // A probe-only hash container: only its size and largest value are read, never its order.
            val counts = HashMap<Int, Int>()
            for (f in fits) {
                val code = LexiconRules.markCode(word, f)
                counts[code] = (counts[code] ?: 0) + 1
            }
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

    /** The most words of [fits] that [word] could leave, over every mark it might draw. */
    fun worstCase(word: String, fits: List<String>): Int {
        val counts = HashMap<Int, Int>()
        for (f in fits) {
            val code = LexiconRules.markCode(word, f)
            counts[code] = (counts[code] ?: 0) + 1
        }
        return counts.values.max()
    }

    // ---- wording ----------------------------------------------------------------------------------

    private fun slots(slots: List<Int>): String =
        (if (slots.size == 1) "slot " else "slots ") + list(slots.map { it + 1 })

    /** "1", "1 and 2", "1, 2 and 4". */
    private fun list(items: List<Any>): String = when (items.size) {
        0 -> ""
        1 -> "${items[0]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }
}
