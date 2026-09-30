package com.joebywan.daybook.puzzles

/**
 * Tower (Mastermind), reasoned the way a person reasons it: from the scores already on the board.
 *
 * [deduce] is handed exactly what the player sees — the submitted guesses, the pips each one
 * scored, and the row being built — and has no parameter through which the hidden code could reach
 * it. Unlike Kings, Tower never needs the answer at all: a pending row that contradicts the scores
 * is a mistake whatever the code is, and the last resort is not "here is part of the answer" but
 * "here is a guess that fits every score so far", which is honest reasoning too, just not reasoning
 * that fits in a sentence. It is still flagged [CONSISTENT] so the coverage test can count it.
 *
 * The facts it teaches are facts about one slot ("slot 2 is red"), because that is a move the
 * player can make: place that peg in the row they are building. They come from a small set of
 * named rules, each short enough to say in a line:
 *
 * - [ONE_CHANGE] — two guesses differ in one slot only and the filled count moved, so the slot was
 *   right in the guess that scored higher.
 * - [ONLY_LEFT] — every colour but one is ruled out of a slot. Colours are ruled out of a slot by a
 *   guess that scored nothing (its colours are nowhere), by a guess with no filled pips (none of
 *   its pegs is in place), by two guesses that differ only there and scored the same (neither colour
 *   is right there), or by a guess whose filled pips are all used up by slots already known.
 * - [ACCOUNTED] — a guess's filled pips must sit on the pegs not already ruled out, because exactly
 *   that many are left.
 *
 * Only facts that can be explained are ever learned: a fact whose sentence would not fit the hint
 * panel is not added, so nothing later can lean on a fact the player was never shown. Facts are
 * taught in the order they were derived, so one that cites another ("slot 1 is green") only ever
 * comes after it.
 *
 * Orders never come from a hash: guesses, slots and colours are walked by index, so the same board
 * gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object TowerTeacher {

    const val MISTAKE = "mistake"
    const val OPENER = "opener"
    const val ONE_CHANGE = "one-change"
    const val ONLY_LEFT = "only-colour-left"
    const val ACCOUNTED = "pips-accounted"
    const val SUBMIT = "submit"
    const val ONLY_CODE = "only-code-left"
    const val CONSISTENT = "consistent-guess"

    /** The one-sentence slot facts. */
    val FACTS = listOf(ONE_CHANGE, ONLY_LEFT, ACCOUNTED)

    /** Every step [deduce] can produce, for reports. */
    val TECHNIQUES = listOf(OPENER, ONE_CHANGE, ONLY_LEFT, ACCOUNTED, SUBMIT, ONLY_CODE, CONSISTENT, MISTAKE)

    /**
     * The hint panel shows four lines of body text, about 170 characters on a phone. A fact whose
     * explanation is longer is not a one-sentence fact and is not learned.
     */
    const val MAX_EXPLANATION = 170

    // ---- what the indices in a Deduction mean on Tower's board -----------------------------------
    // A submitted guess's peg is `guess * slots + slot`; the rest sit in their own ranges so a
    // highlight can name pips, the working row, a swatch or Submit without clashing with a peg.

    const val PIPS = 1000
    const val CURRENT = 2000
    const val SWATCH = 3000
    const val SUBMIT_BUTTON = 4000

    fun peg(slots: Int, guess: Int, slot: Int) = guess * slots + slot

    /** Names for [Tower.palette], index for index. */
    val colourNames = listOf("red", "blue", "green", "yellow", "purple", "teal", "pink", "brown")

    /** What a step asks the player to do. */
    sealed interface Move {
        /** Put [colour] in [slot] of the row being built. */
        data class Place(val slot: Int, val colour: Int) : Move

        /** Build this whole row. */
        data class Fill(val code: List<Int>) : Move

        /** Take these pegs back out of the row. */
        data class Clear(val slots: List<Int>) : Move

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

    /** Mastermind's score, with no stored code: [code] is whichever code is being tested. */
    fun score(guess: List<Int>, code: List<Int>, colours: Int): Feedback {
        var exact = 0
        val codeLeft = IntArray(colours)
        val guessLeft = IntArray(colours)
        for (i in guess.indices) {
            if (guess[i] == code[i]) exact++ else {
                codeLeft[code[i]]++
                guessLeft[guess[i]]++
            }
        }
        var misplaced = 0
        for (c in 0 until colours) misplaced += minOf(codeLeft[c], guessLeft[c])
        return Feedback(exact, misplaced)
    }

    /** Whether [code] would have drawn every score on the board. */
    fun consistent(code: List<Int>, guesses: List<List<Int>>, feedback: List<Feedback>, colours: Int): Boolean =
        guesses.indices.all { score(guesses[it], code, colours) == feedback[it] }

    // ---- why a colour is ruled out of a slot -----------------------------------------------------

    private sealed interface Why {
        /** Guess [g] scored no pips at all, so none of its colours is in the code. */
        data class Nothing(val g: Int) : Why

        /** Guess [g] had this colour in this slot and scored no filled pips. */
        data class NoFilled(val g: Int) : Why

        /** Guesses [a] and [b] differ only in this slot and have the same filled count. */
        data class SameScore(val a: Int, val b: Int) : Why

        /** Guess [g]'s filled pips are all taken by slots already known. */
        data class Accounted(val g: Int) : Why

        /** The slot is already known to be [colour]. */
        data class Known(val colour: Int) : Why
    }

    /** A slot the scores pin down, and the sentence that says why. */
    private class Fact(
        val slot: Int,
        val colour: Int,
        val technique: String,
        val nudge: String,
        val explanation: String,
        val focus: Set<Int>,
        val cited: Set<Int>,
    )

    /**
     * Everything the named rules can learn from the scores, closed under each other. Holds only
     * explainable facts — see the class comment.
     */
    private class Knowledge(
        val slots: Int,
        val colours: Int,
        val guesses: List<List<Int>>,
        val feedback: List<Feedback>,
    ) {
        private val absent = arrayOfNulls<Why>(colours)
        private val ruledOut = Array(slots) { arrayOfNulls<Why>(colours) }
        private val known = arrayOfNulls<Fact>(slots)
        val facts = mutableListOf<Fact>()

        init {
            learn()
        }

        /** Why [colour] cannot be in [slot], or null if nothing the rules know rules it out. */
        fun excluded(slot: Int, colour: Int): Why? {
            known[slot]?.let { return if (it.colour != colour) Why.Known(it.colour) else null }
            return absent[colour] ?: ruledOut[slot][colour]
        }

        private fun add(f: Fact) {
            known[f.slot] = f
            facts += f
        }

        private fun learn() {
            val n = guesses.size
            for (g in 0 until n) {
                val fb = feedback[g]
                if (fb.exact + fb.misplaced == 0) {
                    for (c in guesses[g]) if (absent[c] == null) absent[c] = Why.Nothing(g)
                }
                if (fb.exact == 0) {
                    for (i in 0 until slots) {
                        val c = guesses[g][i]
                        if (ruledOut[i][c] == null) ruledOut[i][c] = Why.NoFilled(g)
                    }
                }
            }
            // Pairs of guesses one peg apart. Unequal filled counts pin the slot outright; equal
            // ones rule both colours out of it.
            for (a in 0 until n) for (b in a + 1 until n) {
                val slot = onlyDifference(guesses[a], guesses[b]) ?: continue
                val ea = feedback[a].exact
                val eb = feedback[b].exact
                if (ea == eb) {
                    for (c in listOf(guesses[a][slot], guesses[b][slot])) {
                        if (ruledOut[slot][c] == null) ruledOut[slot][c] = Why.SameScore(a, b)
                    }
                } else if (known[slot] == null) {
                    val hi = if (ea > eb) a else b
                    val lo = if (ea > eb) b else a
                    val colour = guesses[hi][slot]
                    val name = colourNames[colour]
                    add(
                        Fact(
                            slot, colour, ONE_CHANGE,
                            nudge = "Compare guesses ${a + 1} and ${b + 1}.",
                            explanation = "Guesses ${a + 1} and ${b + 1} differ only in slot ${slot + 1}, and guess " +
                                "${lo + 1} has one filled pip fewer, so guess ${hi + 1}'s $name was right: " +
                                "slot ${slot + 1} is $name.",
                            focus = rowCells(a) + rowCells(b),
                            cited = setOf(peg(slots, a, slot), peg(slots, b, slot), PIPS + a, PIPS + b),
                        ),
                    )
                }
            }
            var changed = true
            while (changed) {
                changed = accounted() || onlyLeft()
            }
        }

        private fun rowCells(g: Int): Set<Int> = (0 until slots).map { peg(slots, g, it) }.toSet() + (PIPS + g)

        /** One [ACCOUNTED] pass over the guesses; true if it learned anything. */
        private fun accounted(): Boolean {
            var learned = false
            for (g in guesses.indices) {
                val guess = guesses[g]
                val f = feedback[g].exact
                val right = (0 until slots).filter { known[it]?.colour == guess[it] }
                val wrong = (0 until slots).filter { it !in right && excluded(it, guess[it]) != null }
                val open = (0 until slots).filter { it !in right && it !in wrong }
                if (open.isEmpty()) continue
                if (f == right.size) {
                    // Every filled pip is spoken for; the rest of this guess's pegs are out of place.
                    for (j in open) if (ruledOut[j][guess[j]] == null) {
                        ruledOut[j][guess[j]] = Why.Accounted(g)
                        learned = true
                    }
                } else if (f - right.size == open.size) {
                    for (j in open) {
                        if (known[j] != null) continue
                        val text = accountedText(g, right, wrong, open, j)
                        if (text.length > MAX_EXPLANATION) continue
                        val reasonRows = wrong.mapNotNull { excluded(it, guess[it]) }.flatMap(::whyRows)
                        add(
                            Fact(
                                j, guess[j], ACCOUNTED,
                                nudge = "Look at guess ${g + 1}'s filled pips.",
                                explanation = text,
                                focus = rowCells(g),
                                cited = rowCells(g) + reasonRows.map { PIPS + it },
                            ),
                        )
                        learned = true
                    }
                }
            }
            return learned
        }

        private fun accountedText(g: Int, right: List<Int>, wrong: List<Int>, open: List<Int>, slot: Int): String {
            val guess = guesses[g]
            val f = feedback[g].exact
            fun pegs(list: List<Int>) = join(list.map { "its ${colourNames[guess[it]]} in slot ${it + 1}" })
            val parts = buildList {
                if (right.isNotEmpty()) {
                    add(pegs(right) + if (right.size == 1) " is one" else " are ${numberWord(right.size)} of them")
                }
                if (wrong.isNotEmpty()) {
                    val reasons = wrong.map { whyClause(it, excluded(it, guess[it])!!) }.distinct()
                    add(pegs(wrong) + " can't be" + (if (right.isEmpty()) " one" else "") + " (${join(reasons)})")
                }
            }
            val rest = if (open.size == 1) "its last peg is right" else "its other pegs are right"
            return "Guess ${g + 1} has ${count(f, "filled pip")}. ${parts.joinToString(", and ").cap()}, " +
                "so $rest: slot ${slot + 1} is ${colourNames[guess[slot]]}."
        }

        /** One [ONLY_LEFT] pass over the slots; true if it learned anything. */
        private fun onlyLeft(): Boolean {
            var learned = false
            for (i in 0 until slots) {
                if (known[i] != null) continue
                val left = (0 until colours).filter { excluded(i, it) == null }
                if (left.size != 1) continue
                val colour = left[0]
                val reasons = (0 until colours).filter { it != colour }.map { it to excluded(i, it)!! }
                val text = onlyLeftText(i, colour, reasons)
                if (text.length > MAX_EXPLANATION) continue
                val rows = reasons.flatMap { whyRows(it.second) }.distinct().sorted()
                add(
                    Fact(
                        i, colour, ONLY_LEFT,
                        nudge = "What can slot ${i + 1} still be?",
                        explanation = text,
                        focus = rows.map { peg(slots, it, i) }.toSet() + (CURRENT + i),
                        cited = rows.flatMap { listOf(peg(slots, it, i), PIPS + it) }.toSet(),
                    ),
                )
                learned = true
            }
            return learned
        }

        /**
         * "Slot 2 can't be red or blue (guess 1 scored nothing) or green (guess 3 had it there, no
         * filled pip). That leaves yellow." Colours that share a kind of reason share a clause.
         */
        private fun onlyLeftText(slot: Int, colour: Int, reasons: List<Pair<Int, Why>>): String {
            // Group by the kind of reason, in the order the kinds first appear.
            val groups = linkedMapOf<String, MutableList<Pair<Int, Why>>>()
            for (r in reasons) {
                val key = when (val w = r.second) {
                    is Why.Nothing -> "nothing"
                    is Why.NoFilled -> "nofilled"
                    is Why.SameScore -> "same ${w.a} ${w.b}"
                    is Why.Accounted -> "accounted"
                    is Why.Known -> "known"
                }
                groups.getOrPut(key) { mutableListOf() } += r
            }
            val clauses = groups.values.map { group ->
                val names = join(group.map { colourNames[it.first] }, "or")
                val why = group.map { it.second }
                val rows = why.flatMap(::whyRows).distinct().sorted()
                val guessesWord = guessList(rows)
                val clause = when (val w = why[0]) {
                    is Why.Nothing -> "$guessesWord scored nothing"
                    is Why.NoFilled ->
                        "$guessesWord had ${if (group.size == 1) "it" else "them"} there, no filled pip"
                    is Why.SameScore -> "guesses ${w.a + 1} and ${w.b + 1} differ only there, same score"
                    is Why.Accounted -> "$guessesWord ${if (rows.size == 1) "has its" else "have their"} filled pips used up"
                    is Why.Known -> ""
                }
                "$names ($clause)"
            }
            return "Slot ${slot + 1} can't be ${join(clauses, "or")}. That leaves ${colourNames[colour]}."
        }

        /** The guesses a reason cites. */
        fun whyRows(w: Why): List<Int> = when (w) {
            is Why.Nothing -> listOf(w.g)
            is Why.NoFilled -> listOf(w.g)
            is Why.SameScore -> listOf(w.a, w.b)
            is Why.Accounted -> listOf(w.g)
            is Why.Known -> emptyList()
        }

        /** A reason as a short clause, for inside brackets. */
        fun whyClause(slot: Int, w: Why): String = when (w) {
            is Why.Nothing -> "guess ${w.g + 1} scored nothing"
            is Why.NoFilled -> "guess ${w.g + 1} had it there, no filled pip"
            is Why.SameScore -> "guesses ${w.a + 1} and ${w.b + 1} differ only there, same score"
            is Why.Accounted -> "guess ${w.g + 1}'s filled pips are used up"
            is Why.Known -> "slot ${slot + 1} is ${colourNames[w.colour]}"
        }

        /** A reason as a full sentence, for a mistake. */
        fun whySentence(slot: Int, colour: Int, w: Why): String {
            val name = colourNames[colour]
            return when (w) {
                is Why.Nothing -> "Guess ${w.g + 1} scored nothing, so $name isn't in the code."
                is Why.NoFilled -> "Guess ${w.g + 1} had $name in slot ${slot + 1} and no filled pips, " +
                    "so slot ${slot + 1} isn't $name."
                is Why.SameScore -> "Guesses ${w.a + 1} and ${w.b + 1} differ only in slot ${slot + 1} and have the " +
                    "same filled count, so slot ${slot + 1} is neither " +
                    "${colourNames[guesses[w.a][slot]]} nor ${colourNames[guesses[w.b][slot]]}."
                is Why.Accounted -> "Guess ${w.g + 1}'s filled pips are all used up by slots already known, " +
                    "so its $name in slot ${slot + 1} is out of place."
                is Why.Known -> {
                    val head = "Slot ${slot + 1} has to be ${colourNames[w.colour]}, not $name."
                    val why = known[slot]!!.explanation
                    if (head.length + 1 + why.length <= MAX_EXPLANATION) "$head $why" else head
                }
            }
        }
    }

    /** The one slot where [a] and [b] differ, or null if they differ in none or several. */
    private fun onlyDifference(a: List<Int>, b: List<Int>): Int? {
        var slot = -1
        for (i in a.indices) {
            if (a[i] == b[i]) continue
            if (slot >= 0) return null
            slot = i
        }
        return slot.takeIf { it >= 0 }
    }

    // ---- the whole hint ----------------------------------------------------------------------------

    /**
     * The next thing to show this player, or null once the code is cracked.
     *
     * Takes no code on purpose — see the class comment. Mistakes come first: a row that cannot be
     * the code is taken back before anything new is taught.
     */
    fun deduce(
        slots: Int,
        colours: Int,
        guesses: List<List<Int>>,
        feedback: List<Feedback>,
        current: List<Int>,
    ): Step? {
        require(guesses.size == feedback.size && current.size == slots)
        if (feedback.any { it.exact == slots }) return null
        val k = Knowledge(slots, colours, guesses, feedback)
        val space = lazy { codeSpace(slots, colours).filter { consistent(it, guesses, feedback, colours) }.toList() }
        mistake(k, current, space)?.let { return it }
        if (current.none { it < 0 }) return submit(slots, guesses, space)
        if (guesses.isEmpty()) return opener(slots, current)
        k.facts.firstOrNull { current[it.slot] != it.colour }?.let { f ->
            return Step(
                technique = f.technique,
                move = Move.Place(f.slot, f.colour),
                focus = f.focus,
                cited = f.cited,
                targets = setOf(CURRENT + f.slot),
                nudge = f.nudge,
                explanation = f.explanation,
            )
        }
        return fill(slots, colours, current, space.value)
    }

    /** A peg in the row being built that the scores rule out, or a row no code can be. */
    private fun mistake(k: Knowledge, current: List<Int>, space: Lazy<List<List<Int>>>): Step? {
        val slots = k.slots
        val placed = current.indices.filter { current[it] >= 0 }
        if (placed.isEmpty()) return null

        for (i in placed) {
            val why = k.excluded(i, current[i]) ?: continue
            return clear(
                listOf(i),
                nudge = "Check this peg.",
                explanation = k.whySentence(i, current[i], why) + " Change it.",
                rows = k.whyRows(why),
            )
        }

        if (placed.size == slots) {
            val g = k.guesses.indices.firstOrNull {
                score(k.guesses[it], current, k.colours) != k.feedback[it]
            } ?: return null
            val would = score(k.guesses[g], current, k.colours)
            return clear(
                placed,
                nudge = "Check this row against guess ${g + 1}.",
                explanation = "If this row were the code, guess ${g + 1} would have scored ${pips(would)}, " +
                    "not ${pips(k.feedback[g])}. Something here has to change.",
                rows = listOf(g),
            )
        }

        fun fits(pegs: List<Int>) = space.value.any { code -> pegs.all { code[it] == current[it] } }
        if (fits(placed)) return null
        val culprit = placed.firstOrNull { i -> fits(placed - i) }
        return clear(
            if (culprit != null) listOf(culprit) else placed,
            nudge = if (culprit != null) "Check this peg." else "Check these pegs.",
            explanation = "No code fits every score so far with these pegs together, so " +
                (if (culprit != null) "the glowing one has to change." else "they can't all stay."),
            rows = emptyList(),
        )
    }

    private fun clear(slotsToClear: List<Int>, nudge: String, explanation: String, rows: List<Int>) = Step(
        technique = MISTAKE,
        move = Move.Clear(slotsToClear),
        focus = slotsToClear.map { CURRENT + it }.toSet(),
        cited = rows.map { PIPS + it }.toSet(),
        targets = slotsToClear.map { CURRENT + it }.toSet(),
        nudge = nudge,
        explanation = explanation,
    )

    private fun submit(slots: Int, guesses: List<List<Int>>, space: Lazy<List<List<Int>>>): Step {
        val row = (0 until slots).map { CURRENT + it }.toSet()
        val explanation = when {
            guesses.isEmpty() -> "Nothing is scored yet, so any first guess fits. Tap Submit to score it."
            space.value.size == 1 -> "This is the only code that fits every score so far. Tap Submit."
            else -> "Every peg agrees with each score so far, so this row could be the code. Tap Submit to score it."
        }
        return Step(
            technique = SUBMIT,
            move = Move.Submit,
            focus = row,
            cited = row,
            targets = setOf(SUBMIT_BUTTON),
            nudge = "Your row fits every score so far.",
            explanation = explanation,
        )
    }

    /** Pairs of colours, the classic Mastermind opener: red red blue blue (green). */
    private fun opener(slots: Int, current: List<Int>): Step {
        val code = List(slots) { if (current[it] >= 0) current[it] else it / 2 }
        val empty = current.indices.filter { current[it] < 0 }
        return Step(
            technique = OPENER,
            move = Move.Fill(code),
            focus = empty.map { CURRENT + it }.toSet(),
            cited = emptySet(),
            targets = empty.map { CURRENT + it }.toSet(),
            nudge = "Nothing is scored yet. Any first guess tells you something.",
            explanation = "With nothing scored, every code is still possible. A few colours used twice " +
                "each, like ${join(code.map { colourNames[it] })}, tells you a lot.",
        )
    }

    /**
     * No single slot can be read off the scores, so suggest a whole row that fits them all — the
     * one that splits the remaining codes most evenly, of a sample, so the suggestion is a good
     * guess rather than merely a legal one. Still reasoning only from the board.
     */
    private fun fill(slots: Int, colours: Int, current: List<Int>, space: List<List<Int>>): Step {
        val placed = current.indices.filter { current[it] >= 0 }
        val fitting = space.filter { code -> placed.all { code[it] == current[it] } }
        val code = if (fitting.size <= 2) fitting.first() else bestSplit(fitting, colours)
        val empty = current.indices.filter { current[it] < 0 }
        val names = join(code.map { colourNames[it] })
        val only = fitting.size == 1
        return Step(
            technique = if (only) ONLY_CODE else CONSISTENT,
            move = Move.Fill(code),
            focus = empty.map { CURRENT + it }.toSet(),
            cited = emptySet(),
            targets = empty.map { CURRENT + it }.toSet(),
            nudge = if (only) "Only one code still fits." else "No single slot is certain yet. Try a guess that fits every score.",
            explanation = when {
                only && space.size == 1 -> "Checked against every score, only one code still fits: $names."
                only -> "With the pegs you've placed, only one code fits every score: $names."
                else -> "No one slot can be read off the scores yet, so here's a guess that fits every " +
                    "score so far: $names."
            },
        )
    }

    private fun bestSplit(codes: List<List<Int>>, colours: Int): List<Int> {
        val tries = spread(codes, 40)
        val against = spread(codes, 300)
        var best = tries[0]
        var bestWorst = Int.MAX_VALUE
        val buckets = IntArray(64)
        for (t in tries) {
            buckets.fill(0)
            var worst = 0
            for (c in against) {
                val fb = score(t, c, colours)
                val key = fb.exact * 8 + fb.misplaced
                buckets[key]++
                if (buckets[key] > worst) worst = buckets[key]
            }
            if (worst < bestWorst) {
                bestWorst = worst
                best = t
            }
        }
        return best
    }

    /** Up to [n] members of [list], evenly spaced, in order. */
    private fun <T> spread(list: List<T>, n: Int): List<T> =
        if (list.size <= n) list else List(n) { list[(it.toLong() * list.size / n).toInt()] }

    /** Every code, slot 1 most significant, so the list is in reading order. */
    private fun codeSpace(slots: Int, colours: Int): Sequence<List<Int>> {
        var total = 1
        repeat(slots) { total *= colours }
        return (0 until total).asSequence().map { index ->
            var rest = index
            val code = IntArray(slots)
            for (i in slots - 1 downTo 0) {
                code[i] = rest % colours
                rest /= colours
            }
            code.toList()
        }
    }

    // ---- words ---------------------------------------------------------------------------------------

    private fun pips(fb: Feedback) = "${fb.exact} filled and ${fb.misplaced} hollow"

    private fun guessList(rows: List<Int>): String =
        (if (rows.size == 1) "guess " else "guesses ") + join(rows.map { (it + 1).toString() })

    private fun join(words: List<String>, last: String = "and"): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " $last " + words.last()
    }

    private fun count(n: Int, noun: String) = "${numberWord(n)} $noun${if (n == 1) "" else "s"}"

    private fun numberWord(k: Int) =
        listOf("no", "one", "two", "three", "four", "five").getOrElse(k) { k.toString() }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
