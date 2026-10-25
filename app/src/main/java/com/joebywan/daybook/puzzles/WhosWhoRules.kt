package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * One clue. [a], [b] and [c] are items: `category * n + slot`, where category 0 is the names (slot = the person) and
 * category `k + 1` is column `k` of the table (column 0 the house). [kind] is one of [WhosWhoLogic.SAME] ... [WhosWhoLogic.EITHER];
 * [c] is used by EITHER only ("[a] has either [b] or [c]", [b] and [c] from one category).
 */
@Serializable
data class WhoClue(val kind: Int, val a: Int, val b: Int, val c: Int = -1)

/**
 * A Who's Who board and the player's marks.
 *
 * The table has a row per person ([names]) and a column per category ([groups]: ids into [WhosWhoWords], the house
 * first). [items] holds, per column, which of that category's words the board uses (indices, in list order; the house
 * column's are the numbers 1..n). [answer] is, per column and person, the slot (index into that column's items) they
 * hold: the one assignment the [clues] allow.
 *
 * [marks] is the player's, one character per (person, column, slot) at `(person * columns + column) * n + slot`:
 * `.` nothing, `o` a tick (this person has it), `x` a cross (they do not). What a tick rules out is derived, never
 * stored. [struck] has a `1` for each clue the player has struck through (a note), shorter strings meaning the rest are not.
 */
@Serializable
data class WhosWhoState(
    val names: List<String>,
    val groups: List<Int>,
    val items: List<List<Int>>,
    val clues: List<WhoClue>,
    val answer: List<List<Int>>,
    val marks: String = WhosWhoLogic.EMPTY.toString().repeat(names.size * groups.size * names.size),
    val struck: String = "",
    override val moves: Int = 0,
) : PuzzleState {

    val n: Int get() = names.size
    val columns: Int get() = groups.size

    /** Every row has one tick in every column, no item is ticked twice, and every clue holds. The rules, not [answer]. */
    override val solved: Boolean get() = WhosWhoLogic.isSolved(this)

    fun markIndex(person: Int, column: Int, slot: Int) = (person * columns + column) * n + slot

    fun isStruck(clue: Int): Boolean = struck.getOrNull(clue) == '1'

    /** The word an item shows: a name, a house number, "cat". */
    fun word(column: Int, slot: Int): String = WhosWhoWords.WORDS[groups[column]][items[column][slot]]

    /**
     * A tap of [slot] on the pad with the tick pen: tick it (taking any other tick off the cell), or take the tick off.
     * With the cross pen: cross it, or take the cross off. One state, one move.
     */
    fun mark(person: Int, column: Int, slot: Int, tick: Boolean): WhosWhoState {
        val next = marks.toCharArray()
        val at = markIndex(person, column, slot)
        val want = if (tick) WhosWhoLogic.TICK else WhosWhoLogic.CROSS
        if (next[at] == want) {
            next[at] = WhosWhoLogic.EMPTY
        } else {
            if (tick) for (j in 0 until n) if (next[markIndex(person, column, j)] == WhosWhoLogic.TICK) next[markIndex(person, column, j)] = WhosWhoLogic.EMPTY
            next[at] = want
        }
        return copy(marks = next.concatToString(), moves = moves + 1)
    }

    /** Every mark in the cell taken off; this state when there are none. */
    fun clearCell(person: Int, column: Int): WhosWhoState {
        val next = marks.toCharArray()
        var changed = false
        for (j in 0 until n) {
            val at = markIndex(person, column, j)
            if (next[at] != WhosWhoLogic.EMPTY) {
                next[at] = WhosWhoLogic.EMPTY
                changed = true
            }
        }
        return if (changed) copy(marks = next.concatToString(), moves = moves + 1) else this
    }

    /** Clue [clue] struck through, or back. One state, one move. */
    fun strike(clue: Int): WhosWhoState {
        val next = CharArray(clues.size) { if (isStruck(it)) '1' else '0' }
        next[clue] = if (next[clue] == '1') '0' else '1'
        return copy(struck = next.concatToString(), moves = moves + 1)
    }
}

/**
 * The rules, the reasoning ladder, the exhaustive count and the generator. Nothing here touches the screen, the
 * platform or a hash container, and every loop walks people, columns, items and clues in index order, so the same seed
 * makes the same board, and the same hint, on the phone and in the browser.
 *
 * ## The ladder ([deduce])
 * Read from the marks alone ([gridOf]), simplest first, each step ending in ticks or crosses the player can make:
 * [CLUE] a clue that names a person settles that person; [ONLY] a cell with one item left, or an item with one person
 * left; [LINK] is / is not / either-or between two things carried through what is known; [HOUSE] next to and left of
 * read against the houses still possible; [WHAT_IF] suppose a tick, and at most [MAX_LINKS] of the steps above reach a
 * contradiction ([broken]). The teacher explains exactly these, and a board ships only when they finish it.
 */
internal object WhosWhoLogic {

    const val EMPTY = '.'
    const val TICK = 'o'
    const val CROSS = 'x'

    const val SAME = 0
    const val NOT = 1
    const val NEXT = 2
    const val LEFT = 3
    const val EITHER = 4

    /** The techniques, in the order [deduce] tries them. */
    const val CLUE = 0
    const val ONLY = 1
    const val LINK = 2
    const val HOUSE = 3
    const val WHAT_IF = 4

    /** Ladder depths: the direct steps ([CLUE], [ONLY]), then [LINK] and [HOUSE], then [WHAT_IF]. */
    const val DIRECT = 0
    const val LINKED = 1
    const val SUPPOSED = 2

    fun levelOf(technique: Int): Int = when (technique) {
        CLUE, ONLY -> DIRECT
        LINK, HOUSE -> LINKED
        else -> SUPPOSED
    }

    /** How many steps a what-if may follow before the contradiction: the sentence has to carry them. */
    const val MAX_LINKS = 3

    /**
     * The teacher's longer what-if, for a board the player has marked their own way: the steps a what-if follows are
     * taken first come, so a board with more marked can lead a short chain astray. Never used by the generator.
     */
    const val LONG_LINKS = 12

    // ---- the visible board ----------------------------------------------------------------------

    /** What the reasoning may see: the size, the columns and the clues. No answer. */
    class Puzzle(val n: Int, val columns: Int, val clues: List<WhoClue>)

    fun puzzleOf(s: WhosWhoState) = Puzzle(s.n, s.columns, s.clues)

    /** An item's category: 0 the names, k + 1 column k. */
    fun cat(p: Puzzle, ref: Int) = ref / p.n

    fun slot(p: Puzzle, ref: Int) = ref % p.n

    /**
     * What the marks leave possible: per (person, column), a mask of slots, and the slot a lone tick has placed there
     * (-1 for none). A cross removes its slot; a tick keeps only its slot and removes it from the other rows of that
     * column. Two ticks of one item both keep it (a clash, shown, never refused).
     */
    class Grid(val n: Int, val columns: Int, val cand: IntArray, val placed: IntArray) {
        fun copy() = Grid(n, columns, cand.copyOf(), placed.copyOf())

        fun cell(person: Int, column: Int) = person * columns + column

        /** Whether [person] can still hold item [ref]. */
        fun can(person: Int, ref: Int): Boolean {
            val cat = ref / n
            val s = ref % n
            return if (cat == 0) person == s else cand[cell(person, cat - 1)] shr s and 1 == 1
        }

        /** Whether [person] is known to hold item [ref]: it is their name, or a tick placed it. */
        fun has(person: Int, ref: Int): Boolean {
            val cat = ref / n
            val s = ref % n
            return if (cat == 0) person == s else placed[cell(person, cat - 1)] == s
        }

        fun houses(person: Int) = cand[cell(person, 0)]

        fun tick(mark: Int) {
            val c = mark / n
            val s = mark % n
            val column = c % columns
            placed[c] = s
            cand[c] = 1 shl s
            for (q in 0 until n) if (q != c / columns) cand[cell(q, column)] = cand[cell(q, column)] and (1 shl s).inv()
        }

        fun cross(mark: Int) {
            cand[mark / n] = cand[mark / n] and (1 shl (mark % n)).inv()
        }

        val finished: Boolean get() = placed.all { it >= 0 }
    }

    fun full(n: Int) = (1 shl n) - 1

    fun blankGrid(n: Int, columns: Int) = Grid(n, columns, IntArray(n * columns) { full(n) }, IntArray(n * columns) { -1 })

    /** The grid the player's [marks] make (see [Grid]). */
    fun gridOf(n: Int, columns: Int, marks: String): Grid {
        val cand = IntArray(n * columns) { full(n) }
        val placed = IntArray(n * columns) { -1 }
        for (c in 0 until n * columns) {
            var ticks = 0
            var crosses = 0
            for (s in 0 until n) {
                when (marks[c * n + s]) {
                    TICK -> ticks = ticks or (1 shl s)
                    CROSS -> crosses = crosses or (1 shl s)
                }
            }
            cand[c] = if (ticks != 0) ticks else full(n) and crosses.inv()
            if (ticks.countOneBits() == 1) placed[c] = ticks.countTrailingZeroBits()
        }
        for (column in 0 until columns) for (s in 0 until n) {
            val holders = (0 until n).filter { marks[(it * columns + column) * n + s] == TICK }
            if (holders.isEmpty()) continue
            for (q in 0 until n) if (q !in holders) cand[q * columns + column] = cand[q * columns + column] and (1 shl s).inv()
        }
        return Grid(n, columns, cand, placed)
    }

    // ---- the rules ------------------------------------------------------------------------------

    /** Whether the houses [ha] (of a's holder) and [hb] (of b's) stand as [kind] says. */
    fun related(kind: Int, ha: Int, hb: Int): Boolean = if (kind == NEXT) ha - hb == 1 || hb - ha == 1 else ha < hb

    /**
     * Whether [clue] holds of a complete assignment: [holder] gives the person holding an item, [house] a person's
     * house slot.
     */
    fun holds(clue: WhoClue, holder: (Int) -> Int, house: (Int) -> Int): Boolean = when (clue.kind) {
        SAME -> holder(clue.a) == holder(clue.b)
        NOT -> holder(clue.a) != holder(clue.b)
        EITHER -> holder(clue.a).let { it == holder(clue.b) || it == holder(clue.c) }
        else -> related(clue.kind, house(holder(clue.a)), house(holder(clue.b)))
    }

    /** The person holding [ref] in [s]'s ticks, or -1 when nobody, or more than one, has it ticked. */
    private fun tickedHolder(s: WhosWhoState, ref: Int): Int {
        val cat = ref / s.n
        val slot = ref % s.n
        if (cat == 0) return slot
        val who = (0 until s.n).filter { s.marks[s.markIndex(it, cat - 1, slot)] == TICK }
        return if (who.size == 1) who[0] else -1
    }

    fun isSolved(s: WhosWhoState): Boolean {
        for (p in 0 until s.n) for (column in 0 until s.columns) {
            if ((0 until s.n).count { s.marks[s.markIndex(p, column, it)] == TICK } != 1) return false
        }
        for (column in 0 until s.columns) for (slot in 0 until s.n) {
            if ((0 until s.n).count { s.marks[s.markIndex(it, column, slot)] == TICK } != 1) return false
        }
        val n = s.n
        return s.clues.all { clue ->
            holds(clue, { tickedHolder(s, it) }, { p -> (0 until n).first { s.marks[s.markIndex(p, 0, it)] == TICK } })
        }
    }

    /**
     * Whether [clue] can still hold on [g]: some choice of the people (and their houses) the grid still allows makes
     * it true. Read from the grid alone; the answer is never consulted.
     */
    fun possible(p: Puzzle, g: Grid, clue: WhoClue): Boolean {
        val n = p.n
        return when (clue.kind) {
            SAME -> (0 until n).any { g.can(it, clue.a) && g.can(it, clue.b) }
            NOT -> (0 until n).any { x -> g.can(x, clue.a) && (0 until n).any { y -> y != x && g.can(y, clue.b) } }
            EITHER -> (0 until n).any { g.can(it, clue.a) && (g.can(it, clue.b) || g.can(it, clue.c)) }
            else -> (0 until n).any { x -> g.can(x, clue.a) && supported(p, g, clue.kind, clue.b, x, g.houses(x), aSide = true) != 0 }
        }
    }

    /**
     * Of the houses in [houses] (a mask) for [person], holding one end of a NEXT / LEFT clue, those that some other
     * person who can hold [other] could stand beside as the clue says. [aSide]: [person] holds the clue's `a`.
     */
    fun supported(p: Puzzle, g: Grid, kind: Int, other: Int, person: Int, houses: Int, aSide: Boolean): Int {
        val n = p.n
        var out = 0
        for (h in 0 until n) {
            if (houses shr h and 1 == 0) continue
            var ok = false
            for (q in 0 until n) {
                if (q == person || !g.can(q, other)) continue
                val hq = g.houses(q)
                for (h2 in 0 until n) {
                    if (hq shr h2 and 1 == 0) continue
                    if (if (aSide) related(kind, h, h2) else related(kind, h2, h)) {
                        ok = true
                        break
                    }
                }
                if (ok) break
            }
            if (ok) out = out or (1 shl h)
        }
        return out
    }

    /** What makes a grid impossible: a cell with nothing left, an item nobody can hold, or a clue that cannot hold. */
    sealed interface Break {
        data class EmptyCell(val person: Int, val column: Int) : Break
        data class NoHolder(val column: Int, val slot: Int) : Break
        data class Clue(val clue: Int) : Break
    }

    /** The first thing, in order, that makes [g] impossible, or null. */
    fun broken(p: Puzzle, g: Grid): Break? {
        val n = p.n
        for (person in 0 until n) for (column in 0 until p.columns) {
            if (g.cand[g.cell(person, column)] == 0) return Break.EmptyCell(person, column)
        }
        for (column in 0 until p.columns) for (s in 0 until n) {
            if ((0 until n).none { g.cand[g.cell(it, column)] shr s and 1 == 1 }) return Break.NoHolder(column, s)
        }
        for (k in p.clues.indices) if (!possible(p, g, p.clues[k])) return Break.Clue(k)
        return null
    }

    // ---- the ladder -----------------------------------------------------------------------------

    /**
     * One step: the marks it makes (indices as [WhosWhoState.markIndex]) and what the teacher needs to say why.
     * [person]: the person the step is about, when one ([ONLY] by cell, a known holder in [LINK] / [HOUSE]).
     * [item]: the item the step settles ([ONLY] by item, the crossed item of a [LINK] / [HOUSE]). [partner]: the
     * clue's other item the step reads. [chain] and [broke]: a [WHAT_IF]'s steps and where they ran out.
     */
    class Step(
        val technique: Int,
        val clue: Int = -1,
        val ticks: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val person: Int = -1,
        val item: Int = -1,
        val partner: Int = -1,
        val supposed: Int = -1,
        val chain: List<Step> = emptyList(),
        val broke: Break? = null,
    )

    fun apply(g: Grid, step: Step) {
        step.crosses.forEach { g.cross(it) }
        step.ticks.forEach { g.tick(it) }
    }

    private fun mark(p: Puzzle, person: Int, ref: Int) = (person * p.columns + (ref / p.n - 1)) * p.n + ref % p.n

    /** [CLUE]: is, is not or either-or naming a person. */
    fun clueStep(p: Puzzle, g: Grid): Step? {
        val n = p.n
        for ((k, clue) in p.clues.withIndex()) {
            when (clue.kind) {
                SAME, NOT -> {
                    val (name, other) = when {
                        cat(p, clue.a) == 0 -> clue.a to clue.b
                        cat(p, clue.b) == 0 -> clue.b to clue.a
                        else -> continue
                    }
                    val who = slot(p, name)
                    if (clue.kind == SAME && !g.has(who, other) && g.can(who, other)) {
                        return Step(CLUE, k, ticks = listOf(mark(p, who, other)), person = who, item = other)
                    }
                    if (clue.kind == NOT && g.can(who, other)) {
                        return Step(CLUE, k, crosses = listOf(mark(p, who, other)), person = who, item = other)
                    }
                }
                EITHER -> {
                    if (cat(p, clue.a) == 0) {
                        val who = slot(p, clue.a)
                        val base = cat(p, clue.b) * n
                        val out = (0 until n).map { base + it }
                            .filter { it != clue.b && it != clue.c && g.can(who, it) }
                        if (out.isNotEmpty()) return Step(CLUE, k, crosses = out.map { mark(p, who, it) }, person = who)
                    } else if (cat(p, clue.b) == 0) {
                        val out = (0 until n).filter { it != slot(p, clue.b) && it != slot(p, clue.c) && g.can(it, clue.a) }
                        if (out.isNotEmpty()) return Step(CLUE, k, crosses = out.map { mark(p, it, clue.a) }, item = clue.a)
                    }
                }
            }
        }
        return null
    }

    /** [ONLY]: a cell with one item left, then an item with one person left. */
    fun onlyStep(p: Puzzle, g: Grid): Step? {
        val n = p.n
        for (person in 0 until n) for (column in 0 until p.columns) {
            val c = g.cell(person, column)
            if (g.placed[c] < 0 && g.cand[c].countOneBits() == 1) {
                val s = g.cand[c].countTrailingZeroBits()
                return Step(ONLY, ticks = listOf(c * n + s), person = person, item = (column + 1) * n + s)
            }
        }
        for (column in 0 until p.columns) for (s in 0 until n) {
            val who = (0 until n).filter { g.cand[g.cell(it, column)] shr s and 1 == 1 }
            if (who.size == 1 && g.placed[g.cell(who[0], column)] != s) {
                return Step(ONLY, ticks = listOf(g.cell(who[0], column) * n + s), item = (column + 1) * n + s)
            }
        }
        return null
    }

    /** [LINK]: is / is not / either-or between two things that are not names, carried through what is known. */
    fun linkStep(p: Puzzle, g: Grid): Step? {
        val n = p.n
        for ((k, clue) in p.clues.withIndex()) {
            when (clue.kind) {
                SAME -> {
                    if (cat(p, clue.a) == 0 || cat(p, clue.b) == 0) continue
                    for ((x, y) in listOf(clue.a to clue.b, clue.b to clue.a)) {
                        for (who in 0 until n) {
                            if (g.has(who, x) && !g.has(who, y) && g.can(who, y)) {
                                return Step(LINK, k, ticks = listOf(mark(p, who, y)), person = who, item = y, partner = x)
                            }
                        }
                    }
                    for ((x, y) in listOf(clue.a to clue.b, clue.b to clue.a)) {
                        val out = (0 until n).filter { g.can(it, x) && !g.can(it, y) }
                        if (out.isNotEmpty()) return Step(LINK, k, crosses = out.map { mark(p, it, x) }, item = x, partner = y)
                    }
                }
                NOT -> {
                    if (cat(p, clue.a) == 0 || cat(p, clue.b) == 0) continue
                    for ((x, y) in listOf(clue.a to clue.b, clue.b to clue.a)) {
                        for (who in 0 until n) {
                            if (g.has(who, x) && g.can(who, y)) {
                                return Step(LINK, k, crosses = listOf(mark(p, who, y)), person = who, item = y, partner = x)
                            }
                        }
                    }
                }
                EITHER -> {
                    if (cat(p, clue.a) == 0 || cat(p, clue.b) == 0) continue
                    val base = cat(p, clue.b) * n
                    for (who in 0 until n) {
                        if (!g.has(who, clue.a)) continue
                        val out = (0 until n).map { base + it }.filter { it != clue.b && it != clue.c && g.can(who, it) }
                        if (out.isNotEmpty()) return Step(LINK, k, crosses = out.map { mark(p, who, it) }, person = who, partner = clue.a)
                    }
                    val out = (0 until n).filter { g.can(it, clue.a) && !g.can(it, clue.b) && !g.can(it, clue.c) }
                    if (out.isNotEmpty()) return Step(LINK, k, crosses = out.map { mark(p, it, clue.a) }, item = clue.a)
                }
            }
        }
        return null
    }

    /** [HOUSE]: next to and somewhere left of, read against the houses still possible. */
    fun houseStep(p: Puzzle, g: Grid): Step? {
        val n = p.n
        for ((k, clue) in p.clues.withIndex()) {
            if (clue.kind != NEXT && clue.kind != LEFT) continue
            for (aSide in listOf(true, false)) {
                val x = if (aSide) clue.a else clue.b
                val y = if (aSide) clue.b else clue.a
                // A person known to hold x cannot stand where nobody who could hold y would be beside them.
                for (who in 0 until n) {
                    if (!g.has(who, x)) continue
                    val hs = g.houses(who)
                    val bad = hs and supported(p, g, clue.kind, y, who, hs, aSide).inv()
                    if (bad != 0 && bad != hs) {
                        return Step(HOUSE, k, crosses = (0 until n).filter { bad shr it and 1 == 1 }.map { g.cell(who, 0) * n + it }, person = who, item = x, partner = y)
                    }
                }
                // Nobody can hold x where no house of theirs has a partner.
                if (cat(p, x) == 0) continue
                val out = (0 until n).filter { g.can(it, x) && supported(p, g, clue.kind, y, it, g.houses(it), aSide) == 0 }
                if (out.isNotEmpty()) return Step(HOUSE, k, crosses = out.map { mark(p, it, x) }, item = x, partner = y)
            }
        }
        return null
    }

    /** The first step of the ladder below the what-if. */
    fun simpleStep(p: Puzzle, g: Grid, level: Int): Step? =
        clueStep(p, g) ?: onlyStep(p, g) ?: if (level >= LINKED) (linkStep(p, g) ?: houseStep(p, g)) else null

    /**
     * [WHAT_IF]: over every slot still open (people, columns, slots in order), suppose a tick there and follow the
     * simpler steps; the shortest road to a contradiction, first in order on a tie, crosses that slot.
     */
    fun whatIfStep(p: Puzzle, g: Grid, maxLinks: Int = MAX_LINKS): Step? {
        var best: Step? = null
        for (c in 0 until p.n * p.columns) {
            if (g.placed[c] >= 0) continue
            for (s in 0 until p.n) {
                if (g.cand[c] shr s and 1 == 0) continue
                val limit = best?.chain?.size?.minus(1) ?: maxLinks
                val found = suppose(p, g, c * p.n + s, limit) ?: continue
                best = found
                if (found.chain.isEmpty()) return found
            }
        }
        return best
    }

    /** A tick at [mark] and at most [limit] simpler steps after it, ending in a contradiction; null when none comes. */
    fun suppose(p: Puzzle, g: Grid, mark: Int, limit: Int): Step? {
        val trial = g.copy()
        trial.tick(mark)
        val chain = ArrayList<Step>()
        while (true) {
            broken(p, trial)?.let { return Step(WHAT_IF, crosses = listOf(mark), supposed = mark, chain = chain, broke = it) }
            if (chain.size >= limit) return null
            val step = simpleStep(p, trial, LINKED) ?: return null
            apply(trial, step)
            chain.add(step)
        }
    }

    /** The next step on [g] of the ladder up to [level]. */
    fun deduce(p: Puzzle, g: Grid, level: Int = SUPPOSED): Step? =
        simpleStep(p, g, level) ?: if (level >= SUPPOSED) whatIfStep(p, g) else null

    /** [g] taken by the ladder up to [level] as far as it goes. False when it reaches a contradiction. */
    fun settle(p: Puzzle, g: Grid, level: Int): Boolean {
        // Every step is sound, so a grid of a board with an answer never breaks on the way; checked once, at the end.
        while (!g.finished) {
            val step = deduce(p, g, level) ?: return true
            apply(g, step)
        }
        return broken(p, g) == null
    }

    /** The deepest level the ladder needs from a blank grid ([DIRECT], [LINKED] or [SUPPOSED]), or null when it does not finish. */
    fun depth(p: Puzzle): Int? {
        val g = blankGrid(p.n, p.columns)
        for (level in DIRECT..SUPPOSED) {
            if (!settle(p, g, level)) return null
            if (g.finished) return level
        }
        return null
    }

    /** The deepest technique the hint walk from a blank grid uses, or null when it does not finish. */
    fun deepest(p: Puzzle): Int? {
        val g = blankGrid(p.n, p.columns)
        var deepest = CLUE
        while (!g.finished) {
            if (broken(p, g) != null) return null
            val step = deduce(p, g) ?: return null
            deepest = maxOf(deepest, step.technique)
            apply(g, step)
        }
        return deepest
    }

    fun finishes(p: Puzzle, level: Int): Boolean {
        val g = blankGrid(p.n, p.columns)
        return settle(p, g, level) && g.finished
    }

    // ---- the exhaustive count -------------------------------------------------------------------

    /** What the count found. Only [ExactlyOne] carries an answer, so nothing else can be shipped as one. */
    sealed interface Verdict {
        data object None : Verdict
        data class ExactlyOne(val answer: List<List<Int>>) : Verdict
        data object Several : Verdict
    }

    /** Every permutation of 0 until n (person -> slot), in lexicographic order. */
    fun permutations(n: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        fun walk(prefix: IntArray, used: Int, depth: Int) {
            if (depth == n) {
                out.add(prefix.copyOf())
                return
            }
            for (s in 0 until n) {
                if (used shr s and 1 == 1) continue
                prefix[depth] = s
                walk(prefix, used or (1 shl s), depth + 1)
            }
        }
        walk(IntArray(n), 0, 0)
        return out
    }

    private val PERMS = Array(6) { if (it < 2) emptyList() else permutations(it) }

    /**
     * How many assignments satisfy every clue, with no budget: column by column over every permutation, each clue
     * checked as soon as every column it reads is placed. Only [Verdict.ExactlyOne] carries the answer.
     */
    fun count(p: Puzzle): Verdict {
        val n = p.n
        val perms = PERMS[n]
        val of = Array(p.columns) { IntArray(n) }
        val who = Array(p.columns) { IntArray(n) }
        // The column after which each clue can be read: the last it touches (the house column for next to and left of).
        val due = p.clues.map { clue ->
            var last = if (clue.kind == NEXT || clue.kind == LEFT) 0 else -1
            for (ref in listOf(clue.a, clue.b, clue.c)) if (ref >= 0) last = maxOf(last, ref / n - 1)
            last
        }
        val byColumn = List(p.columns) { column -> p.clues.indices.filter { due[it] == column || (column == 0 && due[it] < 0) } }
        val holder = { ref: Int -> if (ref / n == 0) ref % n else who[ref / n - 1][ref % n] }
        val house = { person: Int -> of[0][person] }
        var found = 0
        var answer: List<List<Int>> = emptyList()
        fun walk(column: Int) {
            if (column == p.columns) {
                found++
                if (found == 1) answer = of.map { it.toList() }
                return
            }
            for (perm in perms) {
                for (person in 0 until n) {
                    of[column][person] = perm[person]
                    who[column][perm[person]] = person
                }
                if (byColumn[column].all { holds(p.clues[it], holder, house) }) walk(column + 1)
                if (found > 1) return
            }
        }
        walk(0)
        return when (found) {
            0 -> Verdict.None
            1 -> Verdict.ExactlyOne(answer)
            else -> Verdict.Several
        }
    }

    // ---- generation -----------------------------------------------------------------------------

    /**
     * People, categories besides the house, the weight of each clue kind (SAME, NOT, NEXT, LEFT, EITHER) in the draw,
     * whether every clue must name a person, and the ladder level a board must need exactly.
     */
    class Spec(val people: Int, val attributes: Int, val weights: List<Int>, val namesOnly: Boolean, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(4, 1, listOf(1, 2, 0, 0, 0), namesOnly = true, needs = DIRECT)
        Difficulty.HARD -> Spec(4, 2, listOf(2, 2, 2, 2, 0), namesOnly = false, needs = LINKED)
        Difficulty.EXPERT -> Spec(5, 2, listOf(2, 2, 2, 2, 2), namesOnly = false, needs = SUPPOSED)
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 400

    fun generateVerified(seed: Long, difficulty: Difficulty): WhosWhoState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): WhosWhoState? {
        val spec = specFor(difficulty)
        val rng = Rng(seed + a)
        val n = spec.people
        val names = rng.shuffled(WhosWhoWords.NAMES.indices.toList()).take(n).sorted().map { WhosWhoWords.NAMES[it] }
        val groups = listOf(WhosWhoWords.HOUSE) + rng.shuffled(WhosWhoWords.ATTRIBUTES).take(spec.attributes)
        val items = groups.map { if (it == WhosWhoWords.HOUSE) (0 until n).toList() else rng.shuffled((0 until 5).toList()).take(n).sorted() }
        val answer = groups.map { rng.shuffled((0 until n).toList()) }
        val pool = cluePool(n, groups, answer, spec).map { rng.shuffled(it) }.map { ArrayDeque(it) }

        // Add clues until the tier's ladder finishes it: every step being sound, that leaves one answer (the count proves it below).
        val chosen = ArrayList<WhoClue>()
        while (true) {
            val kinds = pool.indices.filter { pool[it].isNotEmpty() && spec.weights[it] > 0 }
            if (kinds.isEmpty()) return null
            var r = rng.nextInt(kinds.sumOf { spec.weights[it] })
            val kind = kinds.first { r -= spec.weights[it]; r < 0 }
            chosen.add(pool[kind].removeFirst())
            val puzzle = Puzzle(n, groups.size, chosen)
            if (finishes(puzzle, spec.needs)) break
        }
        // The order they are shown in, which the ladder reads in (its first step wins), so it is fixed before the checks.
        val shown = rng.shuffled(chosen.toList()).toMutableList()
        // Take back every clue the rest already implies.
        // Again until none goes: the what-if is not monotone, so a clue needed by a larger set can be spare in a smaller one.
        do {
            var removed = false
            for (clue in rng.shuffled(shown.toList())) {
                val puzzle = Puzzle(n, groups.size, shown.filter { it != clue })
                if (finishes(puzzle, spec.needs)) {
                    shown.remove(clue)
                    removed = true
                }
            }
        } while (removed)
        val puzzle = Puzzle(n, groups.size, shown.toList())
        if (depth(puzzle) != spec.needs) return null
        val proof = count(puzzle) as? Verdict.ExactlyOne ?: return null
        if (proof.answer != answer) return null
        return WhosWhoState(names, groups, items, shown.toList(), answer)
    }

    /**
     * Every clue of the tier's kinds that is true of [answer], per kind, in a fixed order (items ascending). An item is
     * `category * n + slot`. Next to and left of never name a house number; either-or never starts from a house or a
     * colour (no sentence reads well that way) and offers two items of one category.
     */
    fun cluePool(n: Int, groups: List<Int>, answer: List<List<Int>>, spec: Spec): List<List<WhoClue>> {
        val cats = groups.size + 1
        val holder = { ref: Int -> if (ref / n == 0) ref % n else answer[ref / n - 1].indexOf(ref % n) }
        val house = { person: Int -> answer[0][person] }
        val refs = (0 until cats * n).toList()
        val out = List(5) { ArrayList<WhoClue>() }
        fun category(ref: Int) = if (ref / n == 0) WhosWhoWords.NAME else groups[ref / n - 1]
        for (x in refs) for (y in refs) {
            if (x / n >= y / n) continue
            if (spec.namesOnly && x / n != 0) continue
            out[if (holder(x) == holder(y)) SAME else NOT].add(WhoClue(if (holder(x) == holder(y)) SAME else NOT, x, y))
        }
        val placeable = refs.filter { category(it) != WhosWhoWords.HOUSE }
        for (x in placeable) for (y in placeable) {
            if (x == y) continue
            val hx = house(holder(x))
            val hy = house(holder(y))
            if (x < y && related(NEXT, hx, hy)) out[NEXT].add(WhoClue(NEXT, x, y))
            if (hx < hy) out[LEFT].add(WhoClue(LEFT, x, y))
        }
        for (a in refs) {
            if (category(a) == WhosWhoWords.HOUSE || category(a) == WhosWhoWords.COLOUR) continue
            for (cat in 0 until cats) {
                if (cat == a / n) continue
                for (b in cat * n until cat * n + n) for (c in b + 1 until cat * n + n) {
                    if (holder(a) == holder(b) || holder(a) == holder(c)) out[EITHER].add(WhoClue(EITHER, a, b, c))
                }
            }
        }
        return out
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never reaches
     * (`FallbackTest`). A fixed board per tier, which `WhosWhoRulesTest` proves: one answer, the tier's depth exactly.
     */
    fun lastResort(difficulty: Difficulty): WhosWhoState = generateVerified(1L, difficulty)!!

    // ---- clue text ------------------------------------------------------------------------------

    fun category(s: WhosWhoState, ref: Int): Int = if (ref / s.n == 0) WhosWhoWords.NAME else s.groups[ref / s.n - 1]

    /** The word an item shows: a name, a house number, "cat". */
    fun word(s: WhosWhoState, ref: Int): String = if (ref / s.n == 0) s.names[ref % s.n] else s.word(ref / s.n - 1, ref % s.n)

    fun who(s: WhosWhoState, ref: Int) = WhosWhoWords.who(category(s, ref), word(s, ref))

    fun has(s: WhosWhoState, ref: Int) = WhosWhoWords.has(category(s, ref), word(s, ref))

    fun hasNot(s: WhosWhoState, ref: Int) = WhosWhoWords.hasNot(category(s, ref), word(s, ref))

    fun base(s: WhosWhoState, ref: Int) = WhosWhoWords.base(category(s, ref), word(s, ref))

    fun thing(s: WhosWhoState, ref: Int) = WhosWhoWords.thing(category(s, ref), word(s, ref))

    private fun near(s: WhosWhoState, ref: Int) = WhosWhoWords.near(category(s, ref), word(s, ref))

    fun capital(text: String) = text.replaceFirstChar { it.uppercaseChar() }

    /** Clue [k] as the player reads it, without its number. */
    fun clueText(s: WhosWhoState, k: Int): String {
        val clue = s.clues[k]
        val rank = { ref: Int -> WhosWhoWords.rank(category(s, ref)) }
        val colour = { ref: Int -> category(s, ref) == WhosWhoWords.COLOUR }
        val houseNo = { ref: Int -> category(s, ref) == WhosWhoWords.HOUSE }
        val (x, y) = if (rank(clue.b) < rank(clue.a) && clue.kind != LEFT) clue.b to clue.a else clue.a to clue.b
        return when (clue.kind) {
            SAME, NOT -> if (colour(x) && houseNo(y)) {
                "The ${word(s, x)} house is${if (clue.kind == NOT) " not" else ""} house ${word(s, y)}."
            } else {
                capital(who(s, x)) + " " + (if (clue.kind == SAME) has(s, y) else hasNot(s, y)) + "."
            }
            NEXT -> if (colour(x)) "The ${word(s, x)} house is next to ${near(s, y)}." else capital(who(s, x)) + " lives next to " + near(s, y) + "."
            LEFT -> {
                val flip = rank(clue.b) < rank(clue.a)
                val first = if (flip) clue.b else clue.a
                val second = if (flip) clue.a else clue.b
                val side = if (flip) "right" else "left"
                if (colour(first)) "The ${word(s, first)} house is somewhere $side of ${near(s, second)}."
                else capital(who(s, first)) + " lives somewhere $side of " + near(s, second) + "."
            }
            else -> capital(who(s, clue.a)) + " " + WhosWhoWords.either(category(s, clue.b), word(s, clue.b), word(s, clue.c)) + "."
        }
    }
}
