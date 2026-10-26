package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PenMarks
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlinx.serialization.Serializable

/**
 * A Tally in progress: a square of digits, the answer, and the player's marks.
 *
 * [digits] is one character `1`..`9` per square, row by row. [keep] is the answer in the same
 * order: `1` stays, `0` goes. The targets are not stored: each is the sum of what [keep] keeps in
 * that row or column ([TallyLogic.rowTargets]), so a saved game cannot hold targets that disagree
 * with its own answer. [marks] is the player's: `.` untouched, `x` removed (gone: it no longer
 * counts), `o` kept (circled, a note that the player is sure; it counts exactly as untouched does).
 *
 * Strings rather than lists, as Nonogram's: a saved game keeps two dozen undo copies.
 */
@Serializable
data class TallyState(
    val size: Int,
    val digits: String,
    val keep: String,
    val marks: String = TallyLogic.UNMARKED.toString().repeat(size * size),
    override val moves: Int = 0,
) : PuzzleState, PenMarks<TallyState> {

    /** Solved when what is left in every row and column adds up to its target. The rules, not [keep]. */
    override val solved: Boolean get() = TallyLogic.isSolved(this)

    fun digitAt(index: Int): Int = digits[index] - '0'

    override val penMarks get() = marks
    override val penWidth get() = size
    override val penHeight get() = size
    override val pens get() = "${TallyLogic.REMOVED}${TallyLogic.KEPT}"

    override fun withPenMarks(marks: String, changed: Int) = copy(marks = marks, moves = moves + changed)
}


/**
 * The rules, the line logic, the exhaustive count and the generator. Nothing here touches the
 * screen, the platform or a hash container, so the same seed makes the same board on the phone and
 * in the browser.
 *
 * ## The ladder
 * A line is a row or a column. Its *ways* are the sets of its squares that could stay: every
 * circled (kept) square in, every removed one out, adding up to the target. A square in every way
 * must stay; a square in none must go ([forced]). Four special cases of that are spotted without
 * listing ways ([shortcuts]: done, all left, too big, too much to lose). Repeating either over every
 * line to a fixpoint is [solveByLines]. Past that, [whatIf]: suppose one square stays (or goes),
 * follow at most [MAX_LINKS] lines from it, and some line is left with no way, so the square does
 * the opposite. The teacher ([TallyTeacher]) explains exactly these, and the generator ships a
 * board only when they finish it, so the hints never run out.
 *
 * ## Tiers
 * Size, and the deepest rung a board must need ([depth]): Standard 5x5 is finished by the
 * shortcuts alone, Hard 6x6 needs the ways of a line at least once, Expert 7x7 needs a what-if at
 * least once. A board needing more or less than its tier's rung is redrawn, so the rungs are what
 * set the tiers apart, not just the size.
 */
internal object TallyLogic {

    const val UNMARKED = '.'
    const val KEPT = 'o'
    const val REMOVED = 'x'

    /** A square, as the line logic sees it. */
    const val OPEN = 0
    const val STAY = 1
    const val GO = 2

    // ---- geometry ---------------------------------------------------------------------------

    /** Rows first (0 until n), then columns (n until 2n). */
    fun lineCells(n: Int, line: Int): List<Int> =
        if (line < n) List(n) { line * n + it } else List(n) { it * n + (line - n) }

    /** Highlight indices for the targets, which sit after the squares (see [TallyTeacher]). */
    fun rowTargetIndex(n: Int, r: Int): Int = n * n + r

    fun colTargetIndex(n: Int, c: Int): Int = n * n + n + c

    // ---- targets and the win check ------------------------------------------------------------

    fun rowTargets(s: TallyState): List<Int> = List(s.size) { r -> lineSum(s, r) }

    fun colTargets(s: TallyState): List<Int> = List(s.size) { c -> lineSum(s, s.size + c) }

    /** Targets of every line, rows then columns. */
    fun targets(s: TallyState): List<Int> = rowTargets(s) + colTargets(s)

    private fun lineSum(s: TallyState, line: Int) = lineCells(s.size, line).sumOf { if (s.keep[it] == '1') s.digitAt(it) else 0 }

    /** What is left in [line]: every square not removed. Circled and untouched count alike. */
    fun leftIn(s: TallyState, line: Int): Int =
        lineCells(s.size, line).sumOf { if (s.marks[it] == REMOVED) 0 else s.digitAt(it) }

    fun isSolved(s: TallyState): Boolean {
        val t = targets(s)
        return (0 until 2 * s.size).all { leftIn(s, it) == t[it] }
    }

    // ---- the line logic ---------------------------------------------------------------------

    /**
     * Every way [line] (digits [values], squares [state] as [OPEN]/[STAY]/[GO]) can reach [target]:
     * bit x set means square x stays. In ascending order of mask.
     */
    fun ways(values: IntArray, state: IntArray, target: Int): List<Int> {
        val n = values.size
        var must = 0
        var never = 0
        for (x in 0 until n) {
            if (state[x] == STAY) must = must or (1 shl x)
            if (state[x] == GO) never = never or (1 shl x)
        }
        val out = ArrayList<Int>()
        for (mask in 0 until (1 shl n)) {
            if (mask and must != must || mask and never != 0) continue
            var sum = 0
            for (x in 0 until n) if (mask shr x and 1 == 1) sum += values[x]
            if (sum == target) out.add(mask)
        }
        return out
    }

    /**
     * What every way agrees on: [STAY] or [GO] for an [OPEN] square every way treats alike, [OPEN]
     * everywhere else (including squares already settled). Null when the line has no way at all.
     */
    fun forced(values: IntArray, state: IntArray, target: Int): IntArray? {
        val all = ways(values, state, target)
        if (all.isEmpty()) return null
        val always = all.reduce { a, b -> a and b }
        val ever = all.reduce { a, b -> a or b }
        return IntArray(values.size) { x ->
            when {
                state[x] != OPEN -> OPEN
                always shr x and 1 == 1 -> STAY
                ever shr x and 1 == 0 -> GO
                else -> OPEN
            }
        }
    }

    /** The player's marks as the line logic reads them: circled stays, removed goes. */
    fun markState(marks: String): IntArray = IntArray(marks.length) {
        when (marks[it]) {
            KEPT -> STAY
            REMOVED -> GO
            else -> OPEN
        }
    }

    private fun lineValues(n: Int, digits: IntArray, at: List<Int>) = IntArray(n) { digits[at[it]] }

    /**
     * Whether no way is left for [line]: what is circled already passes its target, or what is not
     * removed falls short of it, or no choice of the open squares lands on it exactly. Read from the
     * marks alone; the answer is never consulted.
     */
    fun lineImpossible(s: TallyState, line: Int, target: Int): Boolean {
        val at = lineCells(s.size, line)
        val state = markState(s.marks)
        return ways(IntArray(s.size) { s.digitAt(at[it]) }, IntArray(s.size) { state[at[it]] }, target).isEmpty()
    }

    /**
     * The four shortcuts a person spots without listing ways, which the teacher names: the circled
     * numbers already make the target (the rest go); what is left makes it exactly (all of it
     * stays); a number bigger than the target less the circled goes; a number bigger than the
     * surplus (what is left less the target) stays. Each is a special case of [forced], so the
     * result is a part of it: [STAY] or [GO] where a shortcut settles an open square, else [OPEN].
     * Null when the line has no way at all.
     */
    fun shortcuts(values: IntArray, state: IntArray, target: Int): IntArray? {
        if (forced(values, state, target) == null) return null
        var kept = 0
        var left = 0
        for (x in values.indices) {
            if (state[x] == STAY) kept += values[x]
            if (state[x] != GO) left += values[x]
        }
        return IntArray(values.size) { x ->
            when {
                state[x] != OPEN -> OPEN
                kept == target -> GO
                left == target -> STAY
                values[x] > target - kept -> GO
                values[x] > left - target -> STAY
                else -> OPEN
            }
        }
    }

    /**
     * Settles every square the lines alone can settle, starting from [start] ([OPEN]/[STAY]/[GO]
     * per square), to a fixpoint: by every way of each line, or with [shortcutsOnly] by the four
     * [shortcuts] alone. Null on a contradiction (a line with no way).
     */
    fun solveByLines(n: Int, digits: IntArray, targets: List<Int>, start: IntArray, shortcutsOnly: Boolean = false): IntArray? {
        val grid = start.copyOf()
        var changed = true
        while (changed) {
            changed = false
            for (line in 0 until 2 * n) {
                val at = lineCells(n, line)
                val values = lineValues(n, digits, at)
                val state = IntArray(n) { grid[at[it]] }
                val found = (if (shortcutsOnly) shortcuts(values, state, targets[line]) else forced(values, state, targets[line]))
                    ?: return null
                for (x in 0 until n) {
                    if (found[x] != OPEN) {
                        grid[at[x]] = found[x]
                        changed = true
                    }
                }
            }
        }
        return grid
    }

    /**
     * The what-if: with [square] set to [assume], follow the lines it touches. Each link is one line
     * (through a square the supposition or an earlier link changed, the first such in line order)
     * settled by its ways; after each, if some line has no way left, the supposition is impossible.
     * At most [maxLinks] links, so one sentence can carry the chain. Null when it leads nowhere.
     */
    fun whatIf(n: Int, digits: IntArray, targets: List<Int>, grid: IntArray, square: Int, assume: Int, maxLinks: Int = MAX_LINKS): WhatIf? {
        val g = grid.copyOf()
        g[square] = assume
        val touched = BooleanArray(2 * n)
        fun touch(i: Int) {
            touched[i / n] = true
            touched[n + i % n] = true
        }
        touch(square)
        val links = ArrayList<Link>()
        while (true) {
            for (line in 0 until 2 * n) {
                val at = lineCells(n, line)
                if (ways(lineValues(n, digits, at), IntArray(n) { g[at[it]] }, targets[line]).isEmpty()) {
                    return WhatIf(square, assume, links, line)
                }
            }
            if (links.size == maxLinks) return null
            var link: Link? = null
            for (line in 0 until 2 * n) {
                if (!touched[line]) continue
                val at = lineCells(n, line)
                val found = forced(lineValues(n, digits, at), IntArray(n) { g[at[it]] }, targets[line]) ?: continue
                val stays = at.filterIndexed { x, _ -> found[x] == STAY }
                val goes = at.filterIndexed { x, _ -> found[x] == GO }
                if (stays.isEmpty() && goes.isEmpty()) continue
                link = Link(line, stays, goes)
                break
            }
            if (link == null) return null
            link.stays.forEach { g[it] = STAY; touch(it) }
            link.goes.forEach { g[it] = GO; touch(it) }
            links.add(link)
        }
    }

    /** How many lines a [whatIf] may follow before it gives up: the sentence has to carry them all. */
    const val MAX_LINKS = 3

    /** One line of a what-if's chain, and what its ways then settle. */
    class Link(val line: Int, val stays: List<Int>, val goes: List<Int>)

    /** A supposition that failed: [square] set to [assume] led through [links] to [line] having no way. */
    class WhatIf(val square: Int, val assume: Int, val links: List<Link>, val line: Int)

    /**
     * The what-if with the shortest chain over every open square (staying tried before going), the
     * first in reading order on a tie. Null when none settles anything.
     */
    fun firstWhatIf(n: Int, digits: IntArray, targets: List<Int>, grid: IntArray): WhatIf? {
        var best: WhatIf? = null
        for (i in 0 until n * n) {
            if (grid[i] != OPEN) continue
            for (assume in intArrayOf(STAY, GO)) {
                val w = whatIf(n, digits, targets, grid, i, assume, best?.links?.size?.minus(1) ?: MAX_LINKS) ?: continue
                best = w
                if (w.links.isEmpty()) return w
            }
        }
        return best
    }

    /**
     * The whole ladder from [start]: lines to a fixpoint, then one what-if, then lines again, until
     * nothing moves. [allowWhatIf] off is the lines alone. Returns the grid reached (null on a
     * contradiction) and how many what-ifs it took.
     */
    fun settle(n: Int, digits: IntArray, targets: List<Int>, start: IntArray, allowWhatIf: Boolean): Pair<IntArray?, Int> {
        var grid = solveByLines(n, digits, targets, start) ?: return null to 0
        var used = 0
        while (allowWhatIf && grid.any { it == OPEN }) {
            val w = firstWhatIf(n, digits, targets, grid) ?: break
            val next = grid.copyOf()
            next[w.square] = if (w.assume == STAY) GO else STAY
            grid = solveByLines(n, digits, targets, next) ?: return null to used
            used++
        }
        return grid to used
    }

    // ---- the exhaustive count -------------------------------------------------------------------

    /**
     * How many keep masks satisfy every target, with no budget: row by row over each row's ways,
     * cutting a branch as soon as a column passes its target or can no longer reach it. Only
     * [Verdict.ExactlyOne] carries the answer.
     */
    fun countSolutions(n: Int, digits: IntArray, targets: List<Int>): Verdict {
        val rowWays = List(n) { r -> ways(IntArray(n) { digits[r * n + it] }, IntArray(n), targets[r]) }
        // What each column can still gain from rows r.. : the sum of its digits there.
        val rest = Array(n + 1) { IntArray(n) }
        for (r in n - 1 downTo 0) for (c in 0 until n) rest[r][c] = rest[r + 1][c] + digits[r * n + c]
        val col = IntArray(n)
        val chosen = IntArray(n)
        var found = 0
        var answer = 0L
        fun walk(r: Int) {
            if (found > 1) return
            if (r == n) {
                if ((0 until n).all { col[it] == targets[n + it] }) {
                    found++
                    if (found == 1) answer = chosen.foldIndexed(0L) { i, acc, m -> acc or (m.toLong() shl (i * n)) }
                }
                return
            }
            for (mask in rowWays[r]) {
                var ok = true
                for (c in 0 until n) if (mask shr c and 1 == 1) col[c] += digits[r * n + c]
                for (c in 0 until n) {
                    val t = targets[n + c]
                    if (col[c] > t || col[c] + rest[r + 1][c] < t) ok = false
                }
                if (ok) {
                    chosen[r] = mask
                    walk(r + 1)
                }
                for (c in 0 until n) if (mask shr c and 1 == 1) col[c] -= digits[r * n + c]
                if (found > 1) return
            }
        }
        walk(0)
        return Verdict.of(found) { CharArray(n * n) { if (answer shr it and 1L == 1L) '1' else '0' }.concatToString() }
    }

    // ---- generation -------------------------------------------------------------------------

    /**
     * Side and the deepest step a board must need: [SHORTCUTS]
     * (the shortcuts may finish it), [WAYS] (they must not; the ways of single lines must) or
     * [WHAT_IF] (single lines must not; a what-if must be needed at least once).
     */
    class Spec(val side: Int, val needs: Int)

    /** The share of squares kept, per mille, on every tier. */
    private const val KEEP_PERMILLE = 580

    const val SHORTCUTS = 0
    const val WAYS = 1
    const val WHAT_IF = 2

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, SHORTCUTS)
        Difficulty.HARD -> Spec(6, WAYS)
        Difficulty.EXPERT -> Spec(7, WHAT_IF)
    }

    /** The deepest step [s] needs from a blank board ([SHORTCUTS], [WAYS] or [WHAT_IF]), or null when the ladder does not finish it. */
    fun depth(s: TallyState): Int? {
        val n = s.size
        val digits = digitArray(s)
        val targets = targets(s)
        val blank = IntArray(n * n)
        fun finished(g: IntArray?) = g != null && g.none { it == OPEN }
        if (finished(solveByLines(n, digits, targets, blank, shortcutsOnly = true))) return SHORTCUTS
        if (finished(solveByLines(n, digits, targets, blank))) return WAYS
        return if (finished(settle(n, digits, targets, blank, allowWhatIf = true).first)) WHAT_IF else null
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 2000

    fun digitArray(s: TallyState) = IntArray(s.size * s.size) { s.digitAt(it) }

    /**
     * A board for [seed] that the ladder finishes and the count proves, or null when none of
     * [ATTEMPTS] draws was. Draw `a` uses `Rng(seed + a)`, so a board that needed three attempts
     * still depends on its seed alone.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): TallyState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): TallyState? {
        val spec = specFor(difficulty)
        val s = draw(Rng(seed + a), spec.side) ?: return null
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: the ladder finishes it, needing exactly the tier's deepest step, and it has one answer. */
    fun accepts(s: TallyState, spec: Spec): Boolean {
        if (depth(s) != spec.needs) return false
        val verdict = countSolutions(s.size, digitArray(s), targets(s))
        return verdict is Verdict.ExactlyOne && verdict.value == s.keep
    }

    /**
     * Random digits 1..9 and a random keep mask, rejected when some line keeps nothing or removes
     * nothing (a line where every number stays, or none does, gives itself away).
     */
    private fun draw(rng: Rng, n: Int): TallyState? {
        val digits = CharArray(n * n) { '0' + rng.nextInt(1, 9) }
        val keep = CharArray(n * n) { if (rng.nextInt(1000) < KEEP_PERMILLE) '1' else '0' }
        for (line in 0 until 2 * n) {
            val kept = lineCells(n, line).count { keep[it] == '1' }
            if (kept == 0 || kept == n) return null
        }
        return TallyState(n, digits.concatToString(), keep.concatToString())
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier
     * never reaches (`FallbackTest`). A fixed board per tier (what seed 424242 drew), which
     * `TallyRulesTest` proves is accepted at its tier: one answer, the tier's ladder needed exactly.
     */
    fun lastResort(difficulty: Difficulty): TallyState = when (difficulty) {
        Difficulty.STANDARD -> TallyState(5, "6528823183756391353367985", "1000011011111100101011110")
        Difficulty.HARD -> TallyState(6, "652882318375639135336798518452972324", "111001010111101010100100010111100100")
        Difficulty.EXPERT -> TallyState(
            7,
            "8666353862725172911115112116497795778175669374899",
            "0010100000111101101010111001010000100100111111010",
        )
    }
}
