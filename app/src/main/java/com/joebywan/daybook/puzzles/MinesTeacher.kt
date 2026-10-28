package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.neighbours8
import com.joebywan.daybook.core.plural

/**
 * Mines, reasoned a step at a time (`docs/MINES_SPEC.md`, "The ladder").
 *
 * [deduce] is handed what the player can see (the revealed numbers, the mines hit, their flags) and nothing else, so a
 * step it explains cannot lean on where a hidden mine is. Flags are never knowledge (they may be wrong): they only let a
 * step whose mines are all flagged count as already shown, so the hint is the first fact the player has not put down.
 * The answer is read only in [teach]: for a flag on a safe square (a mistake, sound because a shipped board has one
 * answer) and for [FALLBACK], which says openly that it points at the answer.
 *
 * The ladder is [MinesLogic.nextFact], the generator's own, so a shipped board never reaches [FALLBACK]; `MinesTeachingTest`
 * measures it. Every loop walks squares in index order.
 *
 * Highlight indices: squares `0 until width * height`.
 */
internal object MinesTeacher {

    const val ALL_FOUND = "all-found"
    const val ALL_MINES = "all-mines"
    const val SUBSET = "subset"
    const val PAIR = "pair"
    const val COUNT = "count"
    const val EDGE = "count-edge"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(ALL_FOUND, ALL_MINES, SUBSET, PAIR, COUNT, EDGE, WHAT_IF, FALLBACK)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 230

    class Step(
        override val technique: String,
        /** Squares to reveal. */
        val safe: Set<Int>,
        /** Squares to flag; for a mistake, the flag to take off. */
        val mines: Set<Int>,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val nudge: String,
        override val explanation: String,
    ) : TeachStep {
        override val targets: Set<Int> get() = safe + mines
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback -----------------------------------------

    fun teach(s: MinesState): Step? {
        if (s.solved || s.failed) return null
        mistake(s)?.let { return it }
        deduce(s.width, s.height, visibleNumbers(s), BooleanArray(s.width * s.height) { it in s.bumps }, BooleanArray(s.width * s.height) { s.isFlag(it) }, s.total)
            ?.let { return it }
        val n = s.width * s.height
        val open = (0 until n).filter { !s.isMine(it) && !s.isOpen(it) && !s.isFlag(it) }
        val i = open.firstOrNull { j -> neighbours8(s.width, s.height, j).any { s.isOpen(it) } } ?: open.firstOrNull() ?: return null
        return Step(FALLBACK, setOf(i), emptySet(), setOf(i), emptySet(), "This one takes a longer chain. Look at the glowing square.", "This one needs a longer chain than a hint can walk through, so here's the answer for this square: it's safe.")
    }

    /** Each square's number when it is revealed, else -1: what the player can read off the board. */
    fun visibleNumbers(s: MinesState) = IntArray(s.width * s.height) { if (s.isOpen(it)) s.number(it) else -1 }

    /** The [Deduction] for the play screen; `Mines.teach` is just this. */
    fun deduction(s: MinesState): Deduction? = teach(s)?.let(::deduction)

    /** [step] as the play screen's [Deduction] (Training builds its practices' steps with it too). */
    fun deduction(step: Step): Deduction {
        val mistake = step.technique == MISTAKE
        return Deduction(
            step,
            mistake = mistake,
            fallback = step.technique == FALLBACK,
            applyTo = { now -> apply(now as MinesState, step) },
            reachedBy = { now -> reached(now as MinesState, step) },
        )
    }

    /** "Show me": the safe squares revealed and the mines flagged (a mistake's flag taken off), one state. */
    fun apply(s: MinesState, step: Step): MinesState {
        val m = s.marks.toCharArray()
        if (step.technique == MISTAKE) {
            step.mines.forEach { if (m[it] == MinesLogic.FLAG) m[it] = MinesLogic.HIDDEN }
        } else {
            step.mines.forEach { if (m[it] == MinesLogic.HIDDEN && it !in s.bumps) m[it] = MinesLogic.FLAG }
            step.safe.forEach { MinesLogic.openInPlace(s.width, s.height, s.mines, m, it) }
        }
        val marks = m.concatToString()
        return if (marks == s.marks) s else s.copy(marks = marks, moves = s.moves + 1)
    }

    /** Whether the player has made the step: its safe squares revealed (or, with none, its mines flagged). */
    fun reached(s: MinesState, step: Step): Boolean = when {
        step.technique == MISTAKE -> step.mines.none { s.isFlag(it) }
        step.safe.isNotEmpty() -> step.safe.all { s.isOpen(it) }
        else -> step.mines.all { s.isFlag(it) || it in s.bumps }
    }

    // ---- mistakes ---------------------------------------------------------------------------------------------------

    private fun mistake(s: MinesState): Step? {
        val n = s.width * s.height
        val wrong = (0 until n).firstOrNull { s.isFlag(it) && !s.isMine(it) } ?: return null
        val over = neighbours8(s.width, s.height, wrong).firstOrNull { j ->
            s.isOpen(j) && neighbours8(s.width, s.height, j).count { s.isFlag(it) || it in s.bumps } > s.number(j)
        }
        val why = when {
            over == null -> "No way to finish the board has a mine here."
            s.number(over) == 0 -> "The square beside it has no mines around it at all."
            else -> "The ${s.number(over)} beside it touches more flags than its number."
        }
        return Step(MISTAKE, emptySet(), setOf(wrong), setOf(wrong), setOfNotNull(over), "Check this flag.", "$why Take the flag off: long-press it, or tap it with Flag.")
    }

    // ---- reasoning --------------------------------------------------------------------------------------------------


    private fun others(k: Int) = if (k == 1) "other square" else "other $k squares"

    /** Where square [b] lies from [a], on a board [w] wide: "above", "to the right", "up and to the left"... */
    private fun where(w: Int, a: Int, b: Int): String {
        val dr = b / w - a / w
        val dc = b % w - a % w
        val side = if (dc < 0) "to the left" else "to the right"
        return when {
            dc == 0 -> if (dr < 0) "above" else "below"
            dr == 0 -> side
            else -> "${if (dr < 0) "up" else "down"} and $side"
        }
    }

    /**
     * The first step the ladder gives that the board does not already show, or null when it has no more. [num] holds
     * each revealed square's number (-1 hidden), [hit] the mines revealed, [flags] the player's flags, [total] the mines.
     */
    fun deduce(w: Int, h: Int, num: IntArray, hit: BooleanArray, flags: BooleanArray, total: Int): Step? {
        val n = w * h
        val st = IntArray(n) { if (num[it] >= 0) MinesLogic.SAFE else if (hit[it]) MinesLogic.MINE else MinesLogic.UNKNOWN }
        while (true) {
            val f = MinesLogic.nextFact(w, h, num, st, total) ?: return null
            if (f.safe.isEmpty() && f.mines.all { flags[it] }) {
                f.mines.forEach { st[it] = MinesLogic.MINE }
                continue
            }
            return explain(w, h, num, st, total, f)
        }
    }

    /**
     * Every step of [technique] on the board, each explained, from the same visible facts as [deduce] (with [hit] the mines
     * taken as known): Training passes a move that completes any of them. Facts are found in the ladder's order and the
     * search stops once past [technique]'s place in it.
     */
    fun steps(w: Int, h: Int, num: IntArray, hit: BooleanArray, total: Int, technique: String): List<Step> {
        val st = IntArray(w * h) { if (num[it] >= 0) MinesLogic.SAFE else if (hit[it]) MinesLogic.MINE else MinesLogic.UNKNOWN }
        // The ladder tries single numbers together, then pairs of numbers together, then the count, the edge, the what-if.
        fun rank(t: String) = when (t) { ALL_FOUND, ALL_MINES -> 0; SUBSET, PAIR -> 1; COUNT -> 2; EDGE -> 3; else -> 4 }
        return MinesLogic.facts(w, h, num, st, total)
            .takeWhile { rank(it.technique) <= rank(technique) }
            .filter { it.technique == technique }
            .map { explain(w, h, num, st, total, it) }
            .toList()
    }

    private fun explain(w: Int, h: Int, num: IntArray, st: IntArray, total: Int, f: MinesLogic.Fact): Step {
        fun need(i: Int) = num[i] - neighbours8(w, h, i).count { st[it] == MinesLogic.MINE }
        fun found(i: Int) = neighbours8(w, h, i).count { st[it] == MinesLogic.MINE }
        val safe = f.safe.toSet()
        val mines = f.mines.toSet()
        val cited = f.cited.toSet()
        val targets = safe + mines
        val move = if (safe.isNotEmpty()) "Reveal ${if (safe.size == 1) "it" else "them"}." else "Flag ${if (mines.size == 1) "it" else "them"}."
        return when (f.technique) {
            ALL_FOUND -> {
                val i = f.cited[0]
                val text = if (num[i] == 0) "This square has no mines around it, so every hidden square beside it is safe. $move"
                else "This ${num[i]} already touches ${if (num[i] == 1) "its mine" else "its ${num[i]} mines"}, so every other hidden square around it is safe. $move"
                Step(ALL_FOUND, safe, mines, cited, cited, "Look at this ${num[i]}.", text)
            }
            ALL_MINES -> {
                val i = f.cited[0]
                val k = need(i)
                val text = if (found(i) == 0) "This ${num[i]} has exactly ${plural(k, "hidden square")} around it, so ${if (k == 1) "it is a mine" else "they are all mines"}. $move"
                else "This ${num[i]} still needs ${plural(k, "mine")} and has exactly ${plural(k, "hidden square")} left, so ${if (k == 1) "it is a mine" else "they are all mines"}. $move"
                Step(ALL_MINES, safe, mines, cited, cited, "Count the hidden squares around this ${num[i]}.", text)
            }
            SUBSET -> {
                val (a, b) = f.cited
                val diff = need(b) - need(a)
                val k = targets.size
                val what = when {
                    diff == 0 -> if (k == 1) "it is safe" else "they are safe"
                    k == 1 -> "it is a mine"
                    else -> "they are all mines"
                }
                val text = "This ${num[a]} needs ${need(a)} more ${if (need(a) == 1) "mine" else "mines"}, on squares that all touch the ${num[b]} ${where(w, a, b)}, " +
                    "which needs ${need(b)}. So the ${num[b]}'s ${others(k)} ${if (k == 1) "holds" else "hold"} $diff: $what. $move"
                Step(SUBSET, safe, mines, setOf(a), cited, "Compare this ${num[a]} with the ${num[b]} ${where(w, a, b)}.", text)
            }
            PAIR -> {
                val (a, b) = f.cited
                val k = mines.size
                val text = "The ${num[b]} ${where(w, a, b)} needs ${need(b)} more, and this ${num[a]} lets at most ${need(a)} sit on squares they share. " +
                    "So the ${num[b]}'s ${others(k)} ${if (k == 1) "is a mine" else "are mines"}, and this ${num[a]}'s own other squares are safe."
                Step(PAIR, safe, mines, setOf(a), cited, "Compare this ${num[a]} with the ${num[b]} ${where(w, a, b)}.", text)
            }
            COUNT -> {
                val text = if (f.left == 0) "Every one of the $total mines is found, so every hidden square left is safe. $move"
                else "${plural(f.left, "mine")} ${if (f.left == 1) "is" else "are"} left and only ${plural(f.left, "hidden square")}, so ${if (f.left == 1) "it is a mine" else "they are all mines"}. $move"
                Step(COUNT, safe, mines, targets, cited, "Count the mines left.", text)
            }
            EDGE -> {
                val text = "${plural(f.left, "mine")} ${if (f.left == 1) "is" else "are"} left, and the marked numbers need all ${f.left} on squares none of them share. " +
                    "No mine is left for any other hidden square. $move"
                Step(EDGE, safe, mines, cited, cited, "Count the mines left against these numbers.", text)
            }
            WHAT_IF -> {
                val x = targets.first()
                // The first number the supposition sets off touches it; each after that touches what the one before settled.
                val said = f.chain.mapIndexed { k, (i, m) ->
                    "${if (k == 0) "the ${num[i]} beside it" else "then a ${num[i]}"} ${if (m) "needs all its hidden squares" else "has all its mines"}"
                }
                val end = "${if (f.chain.isEmpty()) "the ${num[f.broke]} beside it" else "until a ${num[f.broke]}"} has ${if (f.tooMany) "too many mines" else "too few squares left"}"
                val chain = (said + end).joinToString(", ")
                val text = "Suppose this square ${if (safe.isNotEmpty()) "held a mine" else "were safe"}. Then $chain. So it is ${if (safe.isNotEmpty()) "safe" else "a mine"}."
                Step(WHAT_IF, safe, mines, setOf(x), cited, "What if this square ${if (safe.isNotEmpty()) "held a mine" else "were safe"}?", text)
            }
            else -> error("no explanation for ${f.technique}")
        }
    }
}
