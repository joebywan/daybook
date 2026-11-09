package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PenMarks
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.bentWalk
import com.joebywan.daybook.core.lineSquares
import com.joebywan.daybook.core.orthogonal
import kotlinx.serialization.Serializable

/**
 * A Thermometers board in progress: thermometers ([tubes]) tiling a [size] x [size] grid, the answer, and the
 * player's marks.
 *
 * Each tube is its squares from the bulb to the tip, the tubes in the order of their bulbs (reading order). [fill] is
 * the answer, one character a square: `1` filled, `0` empty. The counts beside the rows and under the columns are not
 * stored: each is the number of filled squares [fill] puts in that line ([ThermometersLogic.targets]), so a save cannot
 * hold counts that disagree with its own answer. [marks] is the player's: `.` untouched, `#` filled, `x` crossed (a
 * note that the square is empty; it counts exactly as untouched does).
 *
 * A tap fills a tube from its bulb up to the square tapped (or crosses from there to the tip) as one state
 * ([ThermometersLogic.tubeTap]); a sweep marks the squares it passes, as Nonogram's and Tally's do.
 */
@Serializable
data class ThermometersState(
    val size: Int,
    val tubes: List<List<Int>>,
    val fill: String,
    val marks: String = ThermometersLogic.UNMARKED.toString().repeat(size * size),
    override val moves: Int = 0,
) : PuzzleState, PenMarks<ThermometersState> {

    /** Solved when every tube is filled from its bulb with no gap and every line holds its count. The rules, not [fill]. */
    override val solved: Boolean get() = ThermometersLogic.isSolved(this)

    override val penMarks get() = marks
    override val penWidth get() = size
    override val penHeight get() = size
    override val pens get() = "${ThermometersLogic.FILLED}${ThermometersLogic.CROSSED}"

    override fun withPenMarks(marks: String, changed: Int) = copy(marks = marks, moves = moves + changed)

    /** A tap fills the tube to [index], or crosses it from there up (see [ThermometersLogic.tubeTap]). */
    override fun tap(index: Int, pen: Char): ThermometersState = ThermometersLogic.tubeTap(this, index, pen)
}

/**
 * The rules, the ladder, the exhaustive count and the generator. Nothing here touches the screen, the platform or a
 * hash container, so the same seed makes the same board on the phone and in the browser.
 *
 * ## The ladder
 * A square is open, full or empty ([OPEN], [FULL], [EMPTY]). Simplest first:
 * 1. [TUBE]: a full square fills every square below it to the bulb; an empty one empties every square above it.
 * 2. [DONE] / [ROOM]: a line already holding its count empties the rest; a line with exactly enough open squares
 *    fills them (Tally's shortcuts).
 * 3. [AT_MOST] / [AT_LEAST]: what one tube can give a line. A tube's open squares in a line fill bulb side first, so
 *    if the line needs only k more, the tube's (k+1)-th open square there stays empty (a tip that cannot); and if
 *    the other tubes' open squares there fall j short, the tube must fill its j-th (a bulb that must). Every
 *    amount between those bounds is possible for one line, so this is everything a single line says.
 * 4. [WHAT_IF]: suppose a square full (or empty), follow at most [MAX_LINKS] lines, and some line or tube breaks.
 *
 * The teacher ([ThermometersTeacher]) explains exactly these, and the generator ships a board only when they finish
 * it, so the hints never run out.
 *
 * ## Tiers
 * Size, tube length, and the deepest rung a board must need ([depth]): Standard 5x5 is finished by the lines and a
 * tip that cannot fill ([TIPS]), Hard 6x6 needs a bulb that must fill ([BULBS]), Expert 8x8 needs a what-if. A draw
 * needing more or less is redrawn, and so is one with a line counting 0 or every square: such a line gives itself away
 * (Tally's rule), and without them no board is finished by full and empty lines alone, so tips are where every board starts.
 */
internal object ThermometersLogic {

    const val UNMARKED = '.'
    const val FILLED = '#'
    const val CROSSED = 'x'

    /** A square, as the ladder sees it. */
    const val OPEN = 0
    const val FULL = 1
    const val EMPTY = 2

    // ---- geometry ---------------------------------------------------------------------------

    fun lineCells(n: Int, line: Int): List<Int> = lineSquares(n, n, line)

    /** Highlight indices for the counts, which sit after the squares (see [ThermometersTeacher]). */
    fun rowCountIndex(n: Int, r: Int): Int = n * n + r

    fun colCountIndex(n: Int, c: Int): Int = n * n + n + c

    /** Which tube holds each square, and how far up it ([pos] 0 is the bulb). */
    class Layout(val n: Int, val tubes: List<List<Int>>) {
        val tubeOf = IntArray(n * n) { -1 }
        val posOf = IntArray(n * n)

        init {
            tubes.forEachIndexed { t, tube -> tube.forEachIndexed { p, i -> tubeOf[i] = t; posOf[i] = p } }
        }

        /** The square one up [i]'s tube from it, or -1 at a tip. */
        fun above(i: Int): Int = tubes[tubeOf[i]].getOrElse(posOf[i] + 1) { -1 }
    }

    fun layout(s: ThermometersState) = Layout(s.size, s.tubes)

    // ---- counts and the win check ------------------------------------------------------------

    /** Counts of every line, rows then columns. */
    fun targets(s: ThermometersState): List<Int> = List(2 * s.size) { line -> lineCells(s.size, line).count { s.fill[it] == '1' } }

    /** Filled squares the player has in [line]. */
    fun filledIn(s: ThermometersState, line: Int): Int = lineCells(s.size, line).count { s.marks[it] == FILLED }

    /** Every tube filled from its bulb with no gap, and every line holding its count. */
    fun isSolved(s: ThermometersState): Boolean {
        if (s.tubes.any { tube -> (1 until tube.size).any { s.marks[tube[it]] == FILLED && s.marks[tube[it - 1]] != FILLED } }) return false
        val t = targets(s)
        return (0 until 2 * s.size).all { filledIn(s, it) == t[it] }
    }

    /** A tube whose fill has a gap: a filled square over one crossed out. Read from the marks alone. */
    fun tubeBroken(s: ThermometersState, t: Int): Boolean {
        val tube = s.tubes[t]
        var crossed = false
        for (i in tube) {
            if (s.marks[i] == CROSSED) crossed = true
            if (s.marks[i] == FILLED && crossed) return true
        }
        return false
    }

    /** Whether [line] can no longer hold [target]: more filled than it, or too few squares left open. */
    fun lineImpossible(s: ThermometersState, line: Int, target: Int): Boolean {
        val at = lineCells(s.size, line)
        val filled = at.count { s.marks[it] == FILLED }
        return filled > target || at.count { s.marks[it] != CROSSED } < target
    }

    // ---- input ------------------------------------------------------------------------------

    /**
     * A tap on [index] with [pen] as one state. Filling fills the tube from its bulb to the square and takes any fill
     * above it back to untouched; on the top square of a fill it lowers the fill below the square instead. Crossing
     * crosses from the square to the tip and takes any cross below it back; on the lowest square of a run of crosses
     * it clears them. So a square is never filled over a cross or under one.
     */
    fun tubeTap(s: ThermometersState, index: Int, pen: Char): ThermometersState {
        val tube = s.tubes.first { index in it }
        val k = tube.indexOf(index)
        val next = s.marks.toCharArray()
        if (pen == FILLED) {
            val top = s.marks[index] == FILLED && (k == tube.size - 1 || s.marks[tube[k + 1]] != FILLED)
            for ((p, i) in tube.withIndex()) {
                if (p < k || (p == k && !top)) next[i] = FILLED
                else if (next[i] == FILLED) next[i] = UNMARKED
            }
        } else {
            val low = s.marks[index] == CROSSED && (k == 0 || s.marks[tube[k - 1]] != CROSSED)
            for ((p, i) in tube.withIndex()) {
                if (p >= k && !low) next[i] = CROSSED
                else if (next[i] == CROSSED) next[i] = UNMARKED
            }
        }
        val marks = next.concatToString()
        return if (marks == s.marks) s else s.withPenMarks(marks, 1)
    }

    /** The player's marks as the ladder reads them: filled is full, crossed is empty. */
    fun markState(marks: String): IntArray = IntArray(marks.length) {
        when (marks[it]) {
            FILLED -> FULL
            CROSSED -> EMPTY
            else -> OPEN
        }
    }

    // ---- the ladder -------------------------------------------------------------------------

    const val TUBE = 0
    const val DONE = 1
    const val ROOM = 2
    const val AT_MOST = 3
    const val AT_LEAST = 4

    /** One fact a rung found: [kind] on [line] (-1 for [TUBE]) through [tube], settling [full] and [empty]. */
    class Fact(val kind: Int, val line: Int, val tube: Int, val full: List<Int>, val empty: List<Int>)

    /**
     * The fact [TUBE] finds on tube [t] of [grid]: open squares below its highest full one fill, open squares above
     * its lowest empty one empty. Null when there is none, or when a full square sits above an empty one.
     */
    fun tubeFact(l: Layout, grid: IntArray, t: Int): Fact? {
        val tube = l.tubes[t]
        val lo = tube.indexOfLast { grid[it] == FULL } + 1
        val hi = tube.indexOfFirst { grid[it] == EMPTY }.let { if (it < 0) tube.size else it }
        if (lo > hi) return null
        val full = (0 until lo).map { tube[it] }.filter { grid[it] == OPEN }
        val empty = (hi until tube.size).map { tube[it] }.filter { grid[it] == OPEN }
        if (full.isEmpty() && empty.isEmpty()) return null
        return Fact(TUBE, -1, t, full, empty)
    }

    /** Whether some tube of [grid] has a full square above an empty one. */
    fun tubeBroken(l: Layout, grid: IntArray): Boolean = l.tubes.any { tube ->
        tube.indexOfLast { grid[it] == FULL } > tube.indexOfFirst { grid[it] == EMPTY }.let { if (it < 0) tube.size else it }
    }

    /** What [line] still needs: its count less its full squares. */
    private fun need(l: Layout, grid: IntArray, targets: List<Int>, line: Int) = targets[line] - lineCells(l.n, line).count { grid[it] == FULL }

    /** Whether [line] can no longer hold its count. */
    fun lineBroken(l: Layout, grid: IntArray, targets: List<Int>, line: Int): Boolean {
        val need = need(l, grid, targets, line)
        return need < 0 || need > lineCells(l.n, line).count { grid[it] == OPEN }
    }

    /**
     * The facts [line] gives at rung [kind] ([DONE], [ROOM], [AT_MOST] or [AT_LEAST]), on a [grid] no [TUBE] fact is
     * left on. A tube's squares from the one a fact names to the tip ([AT_MOST]), or from the bulb to it
     * ([AT_LEAST]), are the fact's: the one tap that marks them.
     */
    fun lineFacts(l: Layout, grid: IntArray, targets: List<Int>, line: Int, kind: Int): List<Fact> {
        val at = lineCells(l.n, line)
        val open = at.filter { grid[it] == OPEN }
        if (open.isEmpty()) return emptyList()
        val need = need(l, grid, targets, line)
        if (need < 0 || need > open.size) return emptyList()
        when (kind) {
            DONE -> return if (need == 0) listOf(Fact(DONE, line, -1, emptyList(), open)) else emptyList()
            ROOM -> return if (need == open.size) listOf(Fact(ROOM, line, -1, open, emptyList())) else emptyList()
        }
        if (need == 0 || need == open.size) return emptyList()
        // Each tube's open squares in the line, bulb side first.
        val byTube = ArrayList<Pair<Int, List<Int>>>()
        for (t in l.tubes.indices) {
            val mine = l.tubes[t].filter { it in open }
            if (mine.isNotEmpty()) byTube += t to mine
        }
        val facts = ArrayList<Fact>()
        for ((t, mine) in byTube) {
            val tube = l.tubes[t]
            if (kind == AT_MOST && mine.size > need) {
                val from = l.posOf[mine[need]]
                facts += Fact(AT_MOST, line, t, emptyList(), (from until tube.size).map { tube[it] }.filter { grid[it] == OPEN })
            }
            if (kind == AT_LEAST) {
                val must = need - (open.size - mine.size)
                if (must >= 1) {
                    val to = l.posOf[mine[must - 1]]
                    facts += Fact(AT_LEAST, line, t, (0..to).map { tube[it] }.filter { grid[it] == OPEN }, emptyList())
                }
            }
        }
        return facts
    }

    /** Rungs up to [upTo] ([ROOM] for the shortcuts, [AT_LEAST] for every line rung), simplest first. */
    private fun rungs(upTo: Int) = (DONE..upTo).toList()

    /**
     * The first fact on [grid] at rungs up to [upTo]: any [TUBE] fact first, then each line rung in turn over every
     * line, rows top to bottom then columns left to right. Null when there is none.
     */
    fun nextFact(l: Layout, grid: IntArray, targets: List<Int>, upTo: Int): Fact? {
        for (t in l.tubes.indices) tubeFact(l, grid, t)?.let { return it }
        for (kind in rungs(upTo)) for (line in 0 until 2 * l.n) lineFacts(l, grid, targets, line, kind).firstOrNull()?.let { return it }
        return null
    }

    private fun apply(grid: IntArray, f: Fact) {
        f.full.forEach { grid[it] = FULL }
        f.empty.forEach { grid[it] = EMPTY }
    }

    /** Whether [grid] has broken a rule: a tube with a gap, or a line that can no longer hold its count. */
    fun broken(l: Layout, grid: IntArray, targets: List<Int>): Boolean =
        tubeBroken(l, grid) || (0 until 2 * l.n).any { lineBroken(l, grid, targets, it) }

    /**
     * Every fact of rungs up to [upTo] applied to a fixpoint from [start]. Null on a contradiction.
     */
    fun solveBy(l: Layout, targets: List<Int>, start: IntArray, upTo: Int): IntArray? {
        val grid = start.copyOf()
        while (true) {
            if (broken(l, grid, targets)) return null
            val f = nextFact(l, grid, targets, upTo) ?: return grid
            apply(grid, f)
        }
    }

    // ---- the what-if --------------------------------------------------------------------------

    /** How many lines a [whatIf] may follow before it gives up: the sentence has to carry them all. */
    const val MAX_LINKS = 3

    /** One line of a what-if's chain and what it settles (its tube's own consequences included). */
    class Link(val line: Int, val full: List<Int>, val empty: List<Int>)

    /** A supposition that failed: [square] set to [assume] led through [links] to [line] being unable to hold its count. */
    class WhatIf(val square: Int, val assume: Int, val links: List<Link>, val line: Int)

    /** [grid] with every [TUBE] fact applied; false when a tube breaks. */
    private fun closeTubes(l: Layout, grid: IntArray): Boolean {
        for (t in l.tubes.indices) {
            val tube = l.tubes[t]
            val lo = tube.indexOfLast { grid[it] == FULL } + 1
            val hi = tube.indexOfFirst { grid[it] == EMPTY }.let { if (it < 0) tube.size else it }
            if (lo > hi) return false
            for (p in 0 until lo) grid[tube[p]] = FULL
            for (p in hi until tube.size) grid[tube[p]] = EMPTY
        }
        return true
    }

    /**
     * The what-if: with [square] set to [assume] (its tube following), follow the lines it touches. Each link is one
     * line (through a square the supposition or an earlier link changed, the first such in line order) settled by
     * every line rung, its tubes following; after each, if some line can no longer hold its count, the supposition is
     * impossible. At most [maxLinks] links. Null when it leads nowhere.
     */
    fun whatIf(l: Layout, targets: List<Int>, grid: IntArray, square: Int, assume: Int, maxLinks: Int = MAX_LINKS): WhatIf? {
        val n = l.n
        val g = grid.copyOf()
        g[square] = assume
        val touched = BooleanArray(2 * n)
        fun touchChanges(before: IntArray) {
            for (i in g.indices) if (g[i] != before[i]) {
                touched[i / n] = true
                touched[n + i % n] = true
            }
        }
        // Asked only of a grid with no tube fact left, whose open squares are each tube's middle: a supposition there
        // cannot break its tube.
        check(closeTubes(l, g))
        touchChanges(grid)
        val links = ArrayList<Link>()
        while (true) {
            for (line in 0 until 2 * n) if (lineBroken(l, g, targets, line)) return WhatIf(square, assume, links, line)
            if (links.size == maxLinks) return null
            var link: Link? = null
            for (line in 0 until 2 * n) {
                if (!touched[line]) continue
                val before = g.copyOf()
                val trial = g.copyOf()
                var any = false
                // Every line rung on this one line, to its own fixpoint, tubes following.
                while (true) {
                    val f = (DONE..AT_LEAST).firstNotNullOfOrNull { lineFacts(l, trial, targets, line, it).firstOrNull() } ?: break
                    apply(trial, f)
                    any = true
                    // On a grid with no tube fact left a line's facts cannot break a tube (they only ever mark open squares).
                    check(closeTubes(l, trial))
                    if (lineBroken(l, trial, targets, line)) break
                }
                if (!any) continue
                trial.copyInto(g)
                link = Link(line, changed(before, g, FULL), changed(before, g, EMPTY))
                touchChanges(before)
                break
            }
            if (link == null) return null
            links.add(link)
        }
    }

    private fun changed(before: IntArray, after: IntArray, to: Int) = after.indices.filter { after[it] == to && before[it] != to }

    /**
     * The what-if with the shortest chain over every open square (full tried before empty), the first in reading
     * order on a tie. Null when none settles anything.
     */
    fun firstWhatIf(l: Layout, targets: List<Int>, grid: IntArray): WhatIf? {
        var best: WhatIf? = null
        for (i in 0 until l.n * l.n) {
            if (grid[i] != OPEN) continue
            for (assume in intArrayOf(FULL, EMPTY)) {
                val w = whatIf(l, targets, grid, i, assume, best?.links?.size?.minus(1) ?: MAX_LINKS) ?: continue
                best = w
                if (w.links.isEmpty()) return w
            }
        }
        return best
    }

    /** The whole ladder from [start]: lines to a fixpoint, then one what-if, then lines again. Returns the grid reached (null on a contradiction) and how many what-ifs it took. */
    fun settle(l: Layout, targets: List<Int>, start: IntArray): Pair<IntArray?, Int> {
        var grid = solveBy(l, targets, start, AT_LEAST) ?: return null to 0
        var used = 0
        while (grid.any { it == OPEN }) {
            val w = firstWhatIf(l, targets, grid) ?: break
            val next = grid.copyOf()
            next[w.square] = if (w.assume == FULL) EMPTY else FULL
            grid = solveBy(l, targets, next, AT_LEAST) ?: return null to used
            used++
        }
        return grid to used
    }

    // ---- the exhaustive count -------------------------------------------------------------------

    /**
     * How many fills keep every tube filled from its bulb and every line at its count, with no budget: tube by tube,
     * every level, cutting a branch as soon as a line passes its count or can no longer reach it. Only
     * [Verdict.ExactlyOne] carries the answer.
     */
    fun countSolutions(n: Int, tubes: List<List<Int>>, targets: List<Int>): Verdict<String> {
        val lines = 2 * n
        // What tubes i.. can still add to each line.
        val rest = Array(tubes.size + 1) { IntArray(lines) }
        for (t in tubes.indices.reversed()) {
            rest[t + 1].copyInto(rest[t])
            for (i in tubes[t]) {
                rest[t][i / n]++
                rest[t][n + i % n]++
            }
        }
        val count = IntArray(lines)
        val level = IntArray(tubes.size)
        var found = 0
        var answer = IntArray(0)
        fun fits(t: Int) = (0 until lines).all { count[it] <= targets[it] && count[it] + rest[t][it] >= targets[it] }
        fun walk(t: Int) {
            if (t == tubes.size) {
                found++
                if (found == 1) answer = level.copyOf()
                return
            }
            val tube = tubes[t]
            var k = 0
            while (true) {
                level[t] = k
                if (fits(t + 1)) walk(t + 1)
                if (found > 1 || k == tube.size) break
                val i = tube[k]
                count[i / n]++
                count[n + i % n]++
                k++
                if (count[i / n] > targets[i / n] || count[n + i % n] > targets[n + i % n]) break
            }
            for (p in 0 until k) {
                val i = tube[p]
                count[i / n]--
                count[n + i % n]--
            }
        }
        walk(0)
        return Verdict.of(found) {
            val out = CharArray(n * n) { '0' }
            tubes.forEachIndexed { t, tube -> for (p in 0 until answer[t]) out[tube[p]] = '1' }
            out.concatToString()
        }
    }

    // ---- generation -------------------------------------------------------------------------

    /** Tiers' rungs: lines and tips ([AT_MOST] and simpler), bulbs ([AT_LEAST]), a what-if. */
    const val TIPS = 0
    const val BULBS = 1
    const val WHAT_IF = 2

    /** Side, the tubes' lengths, their chance of keeping straight (percent), and the deepest rung a board must need. */
    class Spec(val side: Int, val minLength: Int, val maxLength: Int, val straight: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, 2, 4, 50, TIPS)
        Difficulty.HARD -> Spec(6, 3, 6, 40, BULBS)
        Difficulty.EXPERT -> Spec(8, 4, 8, 30, WHAT_IF)
    }

    /** The deepest rung [s] needs from a blank board ([TIPS], [BULBS] or [WHAT_IF]), or null when the ladder does not finish it. */
    fun depth(s: ThermometersState): Int? {
        val l = layout(s)
        val targets = targets(s)
        val blank = IntArray(s.size * s.size)
        fun finished(g: IntArray?) = g != null && g.none { it == OPEN }
        if (finished(solveBy(l, targets, blank, AT_MOST))) return TIPS
        if (finished(solveBy(l, targets, blank, AT_LEAST))) return BULBS
        return if (finished(settle(l, targets, blank).first)) WHAT_IF else null
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 2000

    /**
     * A board for [seed] that the ladder finishes at the tier's depth and the count proves, or null when none of
     * [ATTEMPTS] draws was. Draw `a` uses `Rng(seed + a)`.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): ThermometersState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): ThermometersState? {
        val spec = specFor(difficulty)
        val s = draw(Rng(seed + a), spec) ?: return null
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: the ladder finishes it, needing exactly the tier's deepest rung, and it has one answer. */
    fun accepts(s: ThermometersState, spec: Spec): Boolean {
        // The count first: it is the cheaper of the two, and most draws fail it.
        val verdict = countSolutions(s.size, s.tubes, targets(s))
        if (verdict !is Verdict.ExactlyOne || verdict.value != s.fill) return false
        return depth(s) == spec.needs
    }

    /** A random tiling by tubes and a random level for each, or null when a square was left that no tube could take. */
    fun draw(rng: Rng, spec: Spec): ThermometersState? {
        val n = spec.side
        val tubes = layTubes(rng, n, spec) ?: return null
        val fill = CharArray(n * n) { '0' }
        for (tube in tubes) for (p in 0 until rng.nextInt(0, tube.size)) fill[tube[p]] = '1'
        // A line with nothing filled, or everything, gives itself away (as Tally's do).
        for (line in 0 until 2 * n) if (lineCells(n, line).count { fill[it] == '1' } !in 1 until n) return null
        return ThermometersState(n, tubes, fill.concatToString())
    }

    /**
     * Tubes covering the board: from every square still bare, in a shuffled order, a walk of the tier's length
     * ([bentWalk], Pointers' arrow bodies); a walk that cannot leave its square is left bare and later joins the end
     * of a tube beside it. Each tube is turned bulb-first at random, then the tubes are put in their bulbs' order.
     */
    fun layTubes(rng: Rng, n: Int, spec: Spec): List<List<Int>>? {
        val owner = IntArray(n * n) { -1 }
        val tubes = ArrayList<MutableList<Int>>()
        for (start in rng.shuffled((0 until n * n).toList())) {
            if (owner[start] >= 0) continue
            val walk = mutableListOf(start)
            bentWalk(rng, walk, -1, rng.nextInt(spec.minLength, spec.maxLength), n, n, spec.straight) { owner[it] < 0 }
            if (walk.size < 2) continue
            if (rng.nextBoolean()) walk.reverse()
            walk.forEach { owner[it] = tubes.size }
            tubes += walk
        }
        // A square left bare joins a tube whose end is beside it.
        for (i in 0 until n * n) {
            if (owner[i] >= 0) continue
            val t = orthogonal(i, n).firstNotNullOfOrNull { j ->
                owner[j].takeIf { t -> t >= 0 && tubes[t].size < spec.maxLength && (tubes[t].first() == j || tubes[t].last() == j) }
            } ?: return null
            val tube = tubes[t]
            if (tube.last() in orthogonal(i, n)) tube.add(i) else tube.add(0, i)
            owner[i] = t
        }
        return tubes.sortedBy { it.first() }
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never reaches
     * (`FallbackTest`). A fixed board per tier: the first seed from 1 whose first draw the tier accepts (Standard 57,
     * Hard 13, Expert 21), which `ThermometersRulesTest` proves is accepted at its tier.
     */
    fun lastResort(difficulty: Difficulty): ThermometersState = when (difficulty) {
        Difficulty.STANDARD -> ThermometersState(
            5,
            listOf(listOf(2, 7, 8, 9), listOf(3, 4), listOf(6, 1, 0, 5), listOf(11, 10, 15), listOf(12, 13, 18), listOf(14, 19, 24), listOf(16, 17), listOf(20, 21, 22, 23)),
            "1101001000001110111111000",
        )
        Difficulty.HARD -> ThermometersState(
            6,
            listOf(
                listOf(1, 0, 6, 7), listOf(4, 10, 11, 5), listOf(8, 9, 3, 2), listOf(12, 18, 19, 13), listOf(14, 15, 16, 17, 23),
                listOf(22, 21, 27, 33), listOf(29, 28, 34, 35), listOf(30, 24, 25, 31), listOf(32, 26, 20),
            ),
            "001110001110001100000110111011111000",
        )
        Difficulty.EXPERT -> ThermometersState(
            8,
            listOf(
                listOf(0, 8, 16, 17, 25), listOf(1, 2), listOf(7, 15, 14, 6, 5, 13, 12, 4), listOf(9, 10, 11, 3), listOf(19, 27, 26, 18),
                listOf(29, 37, 38, 30, 22, 21, 20), listOf(33, 41), listOf(34, 42, 50, 49, 48, 40, 32, 24), listOf(35, 36, 28),
                listOf(39, 31, 23), listOf(43, 51), listOf(44, 45, 46, 47, 55, 54, 53), listOf(52, 60, 59, 58, 57, 56), listOf(61, 62, 63),
            ),
            "1100011110001111000101110000011101111111001011110000101100000100",
        )
    }
}
