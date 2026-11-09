package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.IntArrayList
import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.LoopFound
import com.joebywan.daybook.core.LoopGrid
import com.joebywan.daybook.core.LoopRules
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlinx.serialization.Serializable

/**
 * A Pearls board in progress: the pearls, the answer loop, and the player's lines and crosses.
 *
 * [pearls] is one character per square, row by row: `.` none, `b` black, `w` white. Edges join the
 * middles of two neighbouring squares: horizontal edge `(r, c)-(r, c+1)` is `r * (n - 1) + c`, then
 * vertical edge `(r, c)-(r+1, c)` is `n * (n - 1) + r * n + c` ([PearlsGrid]). [loop] has a `1` on
 * every edge of the answer, read only for mistakes and tests. [edges] is the player's: `.` open, `-` a
 * line, `x` a cross (a note: no line here; it never counts).
 *
 * Strings rather than lists, as Tally's: a saved game keeps two dozen undo copies.
 */
@Serializable
data class PearlsState(
    val size: Int,
    val pearls: String,
    val loop: String,
    val edges: String = PearlsLogic.OPEN_MARK.toString().repeat(2 * size * (size - 1)),
    override val moves: Int = 0,
) : PuzzleState {

    /** Solved when the lines are one closed loop keeping every pearl's rule. The rules, not [loop]. */
    override val solved: Boolean get() = PearlsLogic.isSolved(size, pearls, edges)

    /** [mark] on every edge of [indices] that is not already it; one state, one move per edge. */
    fun paint(indices: Collection<Int>, mark: Char): PearlsState = moved(LoopEdges.painted(edges, indices, mark))

    /**
     * A tap on edge [e]: an open edge is crossed, and a cross or a line is rubbed out. Lines are drawn
     * by dragging, so a tap never draws one.
     */
    fun tap(e: Int): PearlsState = moved(LoopEdges.tapped(edges, e))

    /**
     * A drag through squares [path] (each a neighbour of the one before). Its first edge decides what the
     * whole drag does, as a pen does: over a line it rubs lines out, anywhere else it draws them (over
     * crosses too). One state for the whole drag.
     */
    fun drag(path: List<Int>): PearlsState = moved(LoopEdges.dragged(PearlsGrid.of(size), edges, path))

    private fun moved(painted: Pair<String, Int>): PearlsState =
        if (painted.second == 0) this else copy(edges = painted.first, moves = moves + painted.second)
}


/** Pearls' points are its squares' middles: the shared lattice of [LoopEdges], one point a square. */
internal typealias PearlsGrid = LoopGrid

/**
 * The rules, the ladder, the exhaustive count and the generator. Nothing here touches the screen, the
 * platform or a hash container, so the same seed makes the same board on the phone and in the browser.
 *
 * ## The ladder
 * Every rule reads edge states ([OPEN], [LINE], [CROSS]) and only ever adds lines and crosses:
 * 1. [found] at a square: the square rules (two lines take no more; one line and one way on
 *    continues; an empty square with one way in is a dead end; a pearl with two ways left takes both)
 *    and the pearl's own rule (a black pearl's arm that cannot be drawn sends it the other way; a
 *    white pearl that cannot go one way goes the other, and must turn on one side).
 * 2. [smallLoop]: an edge joining the two ends of one stretch of line, when the loop it would close
 *    leaves out a pearl or another stretch, is crossed.
 * 3. [whatIf]: suppose an edge is a line (or a cross), follow 1 and 2 for at most [MAX_ROUNDS] rounds,
 *    and something is left impossible, so it is the other.
 * [settle] takes them to a fixpoint. The generator ships a board only when its tier's rungs finish it,
 * and the teacher ([PearlsTeacher]) explains exactly these, so the hints never run out.
 */
internal object PearlsLogic {

    const val OPEN_MARK = LoopEdges.OPEN_MARK
    const val LINE_MARK = LoopEdges.LINE_MARK
    const val CROSS_MARK = LoopEdges.CROSS_MARK

    const val OPEN = LoopEdges.OPEN
    const val LINE = LoopEdges.LINE
    const val CROSS = LoopEdges.CROSS

    const val BLACK = 'b'
    const val WHITE = 'w'

    // ---- what a rule found ----------------------------------------------------------------

    /** Kinds of step, in the teacher's order (simplest first), and kinds of contradiction. The square and loop rules' are [LoopEdges]'. */
    const val K_BLACK = 0
    const val K_WHITE = 1
    const val K_WHITE_TURN = 2
    const val K_ONE_WAY = LoopEdges.K_ONE_WAY
    const val K_PEARL_WAYS = LoopEdges.K_TWO_WAYS
    const val K_DEAD_END = LoopEdges.K_DEAD_END
    const val K_FULL = LoopEdges.K_FULL
    const val K_SMALL_LOOP = LoopEdges.K_SMALL_LOOP

    const val C_THREE = LoopEdges.C_THREE // a square with three or more lines
    const val C_DEAD_LINE = LoopEdges.C_DEAD_LINE // a line into a square with no way on
    const val C_PEARL_SHUT = LoopEdges.C_SHUT // a pearl left fewer than two ways
    const val C_BLACK = 13 // a black pearl that can no longer turn with straight arms
    const val C_WHITE = 14 // a white pearl that can no longer go straight with a turn beside it
    const val C_SMALL_LOOP = LoopEdges.C_SMALL_LOOP // a closed loop that leaves something out

    private val NONE = IntArray(0)

    private fun contra(kind: Int, s: Int) = LoopFound(kind, s, NONE, NONE)

    // ---- the local rules --------------------------------------------------------------------

    /** The square rules at [s] ([LoopEdges.pointRule], a pearl being a square the loop must pass); null when they settle nothing. */
    fun squareRule(g: PearlsGrid, pearls: String, st: IntArray, s: Int, full: Boolean = true): LoopFound? =
        LoopEdges.pointRule(g, st, s, mustPass = pearls[s] != '.', full = full)

    private fun isLine(st: IntArray, e: Int) = e >= 0 && st[e] == LINE

    private fun usable(st: IntArray, e: Int) = e >= 0 && st[e] != CROSS

    /**
     * Whether a black pearl at [p] can send an arm in direction [d]: the edge out and the next square's
     * edge straight on can both be lines, the next square has no line across, and it is not black.
     */
    fun blackArmOk(g: PearlsGrid, pearls: String, st: IntArray, p: Int, d: Int): Boolean {
        if (!usable(st, g.edge(p, d))) return false
        val q = g.next(p, d)
        if (pearls[q] == BLACK) return false
        if (!usable(st, g.edge(q, d))) return false
        return !isLine(st, g.edge(q, (d + 1) % 4)) && !isLine(st, g.edge(q, (d + 3) % 4))
    }

    /** The black pearl's rule at [p]; null when it settles nothing. */
    fun blackRule(g: PearlsGrid, pearls: String, st: IntArray, p: Int): LoopFound? {
        val lines = IntArrayList()
        val crosses = IntArrayList()
        for (axis in 0 until 2) {
            val a = axis
            val b = axis + 2
            val okA = blackArmOk(g, pearls, st, p, a)
            val okB = blackArmOk(g, pearls, st, p, b)
            val hasA = isLine(st, g.edge(p, a))
            val hasB = isLine(st, g.edge(p, b))
            if (hasA && hasB || !okA && !okB || hasA && !okA || hasB && !okB) return contra(C_BLACK, p)
            val arm = when {
                hasA || !okB -> a
                hasB || !okA -> b
                else -> -1
            }
            if (arm < 0) continue
            val out = g.edge(p, arm)
            val on = g.edge(g.next(p, arm), arm)
            if (st[out] == OPEN) lines.add(out)
            if (st[on] == OPEN) lines.add(on)
            val back = g.edge(p, (arm + 2) % 4)
            if (back >= 0 && st[back] == OPEN) crosses.add(back)
        }
        return if (lines.size + crosses.size == 0) null else LoopFound(K_BLACK, p, lines.toArray(), crosses.toArray())
    }

    /** Whether a white pearl at [p] can go straight along [axis] (0 up-down, 1 left-right). */
    fun whiteAxisOk(g: PearlsGrid, st: IntArray, p: Int, axis: Int): Boolean {
        val a = axis
        val b = axis + 2
        if (!usable(st, g.edge(p, a)) || !usable(st, g.edge(p, b))) return false
        if (isLine(st, g.edge(p, (axis + 1) % 4)) || isLine(st, g.edge(p, (axis + 3) % 4))) return false
        // Both neighbours going straight on would leave it no turn.
        return !(isLine(st, g.edge(g.next(p, a), a)) && isLine(st, g.edge(g.next(p, b), b)))
    }

    /** The white pearl's rule at [p]: its direction ([K_WHITE]), then its turn ([K_WHITE_TURN]); null when neither settles anything. */
    fun whiteRule(g: PearlsGrid, st: IntArray, p: Int): LoopFound? {
        val ok = BooleanArray(2) { whiteAxisOk(g, st, p, it) }
        val has = BooleanArray(2) { isLine(st, g.edge(p, it)) || isLine(st, g.edge(p, it + 2)) }
        if (has[0] && has[1] || !ok[0] && !ok[1] || has[0] && !ok[0] || has[1] && !ok[1]) return contra(C_WHITE, p)
        val axis = when {
            has[0] || !ok[1] -> 0
            has[1] || !ok[0] -> 1
            else -> return null
        }
        val lines = IntArrayList()
        val crosses = IntArrayList()
        for (d in intArrayOf(axis, axis + 2)) {
            val e = g.edge(p, d)
            if (st[e] == OPEN) lines.add(e)
        }
        for (d in intArrayOf((axis + 1) % 4, (axis + 3) % 4)) {
            val e = g.edge(p, d)
            if (e >= 0 && st[e] == OPEN) crosses.add(e)
        }
        if (lines.size + crosses.size > 0) return LoopFound(K_WHITE, p, lines.toArray(), crosses.toArray())
        // Straight through: if one neighbour goes straight on, the other must turn.
        for (d in intArrayOf(axis, axis + 2)) {
            if (!isLine(st, g.edge(g.next(p, d), d))) continue
            val o = (d + 2) % 4
            val other = g.edge(g.next(p, o), o)
            if (other >= 0 && st[other] == OPEN) return LoopFound(K_WHITE_TURN, p, NONE, intArrayOf(other))
        }
        return null
    }

    /** Every local rule at [s], pearl first: the first that settles something, or a contradiction. */
    fun localRule(g: PearlsGrid, pearls: String, st: IntArray, s: Int): LoopFound? {
        val pearl = when (pearls[s]) {
            BLACK -> blackRule(g, pearls, st, s)
            WHITE -> whiteRule(g, st, s)
            else -> null
        }
        return pearl ?: squareRule(g, pearls, st, s)
    }

    // ---- the loop rule and the shared ladder ---------------------------------------------------

    /**
     * Pearls' rules as the shared ladder ([LoopEdges]) reads them: a unit is a square; an edge touches its two squares
     * and the pearls beside them; a closed stretch is the whole answer when it holds every line and every pearl.
     */
    class Rules(override val g: PearlsGrid, val pearls: String) : LoopRules {
        override val units = g.nodes

        override fun rule(st: IntArray, u: Int): LoopFound? = localRule(g, pearls, st, u)

        override fun touch(e: Int, add: (Int) -> Unit) {
            for (end in intArrayOf(g.endA[e], g.endB[e])) {
                add(end)
                for (d in 0 until 4) {
                    val q = g.next(end, d)
                    if (q >= 0 && pearls[q] != '.') add(q)
                }
            }
        }

        override fun whole(st: IntArray, s: LoopEdges.Stretches, root: Int, closing: Int): Boolean {
            if (s.edgesIn[root] != s.total) return false
            for (i in pearls.indices) if (pearls[i] != '.' && s.root[i] != root) return false
            return true
        }

        override fun finished(st: IntArray): Boolean = finished(g, pearls, st)

        override fun mustVisit(p: Int): Boolean = pearls[p] != '.'
    }

    fun stretches(g: PearlsGrid, st: IntArray): LoopEdges.Stretches = LoopEdges.stretches(g, st)

    /**
     * The loop rule ([LoopEdges.smallLoop]): every open edge that would close a stretch into a loop leaving out a pearl
     * or another stretch, or the contradiction of a loop already closed that does; null when neither.
     */
    fun smallLoop(g: PearlsGrid, pearls: String, st: IntArray, firstOnly: Boolean = false): LoopFound? =
        LoopEdges.smallLoop(Rules(g, pearls), st, firstOnly)

    // ---- propagation ------------------------------------------------------------------------

    /** [LoopEdges.propagate] with Pearls' rules. */
    fun propagate(
        g: PearlsGrid,
        pearls: String,
        st: IntArray,
        loop: Boolean,
        seeds: IntArray? = null,
        maxRounds: Int = Int.MAX_VALUE,
        linesNew: Boolean = true,
    ): LoopEdges.Outcome = LoopEdges.propagate(Rules(g, pearls), st, loop, seeds, maxRounds, linesNew)

    /** How many rounds a what-if may follow: the explanation has to carry them. */
    const val MAX_ROUNDS = LoopEdges.MAX_ROUNDS

    /** [edge] set to [assume], followed for at most [maxRounds] rounds: the contradiction it reaches, or null. */
    fun whatIf(g: PearlsGrid, pearls: String, st: IntArray, edge: Int, assume: Int, maxRounds: Int = MAX_ROUNDS): LoopEdges.WhatIf? =
        LoopEdges.whatIf(Rules(g, pearls), st, edge, assume, maxRounds)

    /** The what-if with the fewest rounds ([LoopEdges.bestWhatIf]). */
    fun bestWhatIf(g: PearlsGrid, pearls: String, st: IntArray, first: Boolean = false, cap: Int = MAX_ROUNDS): LoopEdges.WhatIf? =
        LoopEdges.bestWhatIf(Rules(g, pearls), st, first, cap)

    // ---- the ladder ---------------------------------------------------------------------------

    /** The rungs a board may need: the local rules alone, the loop rule as well, or a what-if as well. */
    const val LOCAL = LoopEdges.LOCAL
    const val LOOP = LoopEdges.LOOP
    const val WHAT_IF = LoopEdges.WHAT_IF

    /**
     * Everything the rungs up to [depth] settle from [start], to a fixpoint: the edges reached, or null on a
     * contradiction.
     */
    fun settle(n: Int, pearls: String, start: IntArray, depth: Int): IntArray? = LoopEdges.settle(Rules(PearlsGrid.of(n), pearls), start, depth)

    /** Whether the lines in [st] are already the whole answer: one closed loop keeping every rule. */
    fun finished(g: PearlsGrid, pearls: String, st: IntArray): Boolean {
        val marks = CharArray(g.edgeCount) { if (st[it] == LINE) LINE_MARK else OPEN_MARK }.concatToString()
        return isSolved(g.n, pearls, marks)
    }

    /** The deepest rung the board needs from blank ([LOCAL], [LOOP] or [WHAT_IF]), or null when the ladder does not finish it. */
    fun depth(n: Int, pearls: String): Int? = LoopEdges.depth(Rules(PearlsGrid.of(n), pearls))

    fun depth(s: PearlsState): Int? = depth(s.size, s.pearls)

    /** The player's edges as the rules read them. */
    fun stateOf(edges: String): IntArray = LoopEdges.stateOf(edges)

    // ---- the win check ------------------------------------------------------------------------

    /**
     * The rules, read off the lines alone: every square has no line or two, every pearl has two, the
     * lines make one closed loop, a black pearl turns with both neighbours on the loop going straight,
     * and a white pearl goes straight with a turn on at least one neighbour.
     */
    fun isSolved(n: Int, pearls: String, edges: String): Boolean {
        val g = PearlsGrid.of(n)
        val nb = LoopEdges.oneLoop(g) { edges[it] == LINE_MARK } ?: return false
        for (s in 0 until g.nodes) if (pearls[s] != '.' && nb[s * 2 + 1] < 0) return false
        fun turns(s: Int): Boolean {
            val a = nb[s * 2]
            val b = nb[s * 2 + 1]
            return a / n != b / n && a % n != b % n
        }
        for (s in 0 until g.nodes) {
            when (pearls[s]) {
                BLACK -> if (!turns(s) || turns(nb[s * 2]) || turns(nb[s * 2 + 1])) return false
                WHITE -> if (turns(s) || !turns(nb[s * 2]) && !turns(nb[s * 2 + 1])) return false
            }
        }
        return true
    }

    // ---- the exhaustive count -----------------------------------------------------------------

    /**
     * How many loops keep every rule, with no budget ([LoopEdges.solutions]: the local and loop rules to a fixpoint, then
     * a branch on an open edge, a finished branch checked by [isSolved]). Only [Verdict.ExactlyOne] carries the loop.
     */
    fun solutions(n: Int, pearls: String): Verdict<String> {
        val g = PearlsGrid.of(n)
        // The root is first taken as far as the whole ladder goes, what-ifs included: every step of it is sound, so no
        // answer is lost, and it spares the branches a short what-if would have closed (one board's count fell from half a
        // second to a few milliseconds).
        val root = settle(n, pearls, IntArray(g.edgeCount), WHAT_IF) ?: return Verdict.None
        return LoopEdges.solutions(Rules(g, pearls), root)
    }


    // ---- generation ---------------------------------------------------------------------------

    /** Side, the share of the squares the loop visits (per cent, a range), and the deepest rung a board must need. */
    class Spec(val side: Int, val fillLow: Int, val fillHigh: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(6, 60, 90, LOCAL)
        Difficulty.HARD -> Spec(8, 60, 90, LOOP)
        Difficulty.EXPERT -> Spec(10, 60, 90, WHAT_IF)
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 200

    /**
     * A board for [seed] that its tier's rungs finish, needing exactly the deepest, and the count proves,
     * or null when none of [ATTEMPTS] draws was. Draw `a` uses `Rng(seed + a)`, so a board that needed
     * three draws still depends on its seed alone.
     */
    fun generate(seed: Long, difficulty: Difficulty): PearlsState = generateVerified(seed, difficulty) ?: lastResort(difficulty)

    fun generateVerified(seed: Long, difficulty: Difficulty): PearlsState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): PearlsState? {
        val spec = specFor(difficulty)
        val rng = Rng(seed + a)
        val loop = randomLoop(rng, spec.side, spec.fillLow, spec.fillHigh) ?: return null
        val pearls = takeBack(rng, spec.side, implied(spec.side, loop), spec.needs) ?: return null
        val s = PearlsState(spec.side, pearls, loop)
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: the ladder finishes it needing exactly the tier's rung, and it has one answer, [PearlsState.loop]. */
    fun accepts(s: PearlsState, spec: Spec): Boolean {
        if (s.size != spec.side || depth(s) != spec.needs) return false
        val verdict = solutions(s.size, s.pearls)
        return verdict is Verdict.ExactlyOne && verdict.value == s.loop
    }

    /**
     * A random simple loop as a `0`/`1` edge string: the boundary of a region of the `(n-1) x (n-1)`
     * faces between square middles, grown from one random face. A face joins only if the boundary stays
     * one simple closed curve. Each step weighs every face that could join by the pearls its loop would
     * allow (squares visited less [bare] ones) and takes one of the best at random (on [GREEDY_PERCENT]
     * of steps; otherwise any). A loop where nearly every square can carry a pearl is one the pearls can
     * pin down: grown at random, 37 of 39 six-by-six loops with every pearl they allow still had two
     * answers. Growth stops once the loop visits [low]-[high] per cent of the squares (drawn), or when
     * nothing can join (kept if it already visits [low] per cent); null otherwise.
     *
     * Every candidate is walked in face order and scored the same on every platform; no hash order.
     */
    fun randomLoop(rng: Rng, n: Int, low: Int, high: Int): String? =
        LoopEdges.growLoop(rng, n, low, high, GREEDY_PERCENT) { -pearlSquares(it) }

    /** How many squares of [shape]'s loop could carry a pearl ([implied]'s rule). */
    private fun pearlSquares(shape: LoopEdges.LoopShape): Int {
        var count = 0
        for (s in 0 until shape.g.nodes) {
            if (shape.degree[s] != 2) continue
            val t = shape.turns(s)
            val ta = shape.turns(shape.nb[s * 2])
            val tb = shape.turns(shape.nb[s * 2 + 1])
            if (t && !ta && !tb || !t && (ta || tb)) count++
        }
        return count
    }

    /** How often a growth step is greedy (the face that leaves the most pearls); the rest take any face that keeps the loop simple. */
    private const val GREEDY_PERCENT = 95

    /** Every pearl [loop] allows: black on a turn whose neighbours go straight, white on a straight with a turn beside it. */
    fun implied(n: Int, loop: String): String {
        val g = PearlsGrid.of(n)
        val nb = IntArray(g.nodes * 2) { -1 }
        val degree = IntArray(g.nodes)
        for (e in 0 until g.edgeCount) {
            if (loop[e] != '1') continue
            nb[g.endA[e] * 2 + degree[g.endA[e]]++] = g.endB[e]
            nb[g.endB[e] * 2 + degree[g.endB[e]]++] = g.endA[e]
        }
        fun turns(s: Int): Boolean {
            val a = nb[s * 2]
            val b = nb[s * 2 + 1]
            return a / n != b / n && a % n != b % n
        }
        return CharArray(g.nodes) { s ->
            when {
                degree[s] != 2 -> '.'
                turns(s) && !turns(nb[s * 2]) && !turns(nb[s * 2 + 1]) -> BLACK
                !turns(s) && (turns(nb[s * 2]) || turns(nb[s * 2 + 1])) -> WHITE
                else -> '.'
            }
        }.concatToString()
    }

    /**
     * Pearls taken back from [all] in a random order while the rungs up to [needs] still finish the
     * board, so none is left that the rest already imply. On Expert the order is walked twice: first with
     * the local and loop rules alone (cheap), then the pearls still left with the what-if as well; the
     * result is still a board every pearl of which the full ladder needs. Null when [all] itself is not
     * finished.
     */
    fun takeBack(rng: Rng, n: Int, all: String, needs: Int): String? {
        val g = PearlsGrid.of(n)
        val blank = IntArray(g.edgeCount)
        return LoopEdges.takeBack(rng, all, needs) { p, d -> settle(n, p, blank, d)?.let { finished(g, p, it) } == true }
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never
     * reaches (`FallbackTest`). A fixed board per tier (what seed 424242 drew), which `PearlsRulesTest`
     * proves is accepted at its tier: one loop, the tier's rung needed exactly.
     */
    fun lastResort(difficulty: Difficulty): PearlsState = when (difficulty) {
        Difficulty.STANDARD -> PearlsState(
            6, "b.......w.......ww..b....b.w........",
            "110000000110000001100111000001101000101011011011010001000011",
        )
        Difficulty.HARD -> PearlsState(
            8, ".b..w..............bw....w..w...w......b..b....w........b....bw.",
            "0111100000010001100001100000001011100111000000000110001101000100" +
                "010010000001100010111000100000011010010110100101",
        )
        Difficulty.EXPERT -> PearlsState(
            10,
            "..w...b...w....w...w...b...............w.b...w.b.........w....b.b.....w..wb..b...........w.w.....w..",
            "111010111000000000011001000000010000011100011111001000110011000000011011001000000111100111100111" +
                "100110011110011100100001110001000110001101000001101100101100010010111010011000101001",
        )
    }
}
