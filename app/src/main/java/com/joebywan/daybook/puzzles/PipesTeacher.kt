package com.joebywan.daybook.puzzles

/**
 * Pipes, reasoned the way a person reasons it, one tile at a time.
 *
 * A Pipes board never shows an answer, only shapes: every tile's pipework is fixed and only its turn
 * is in question. So the reasoning here starts from nothing but those shapes and the edge of the
 * board, and works out which way round each tile *must* sit. [deduce] takes the tiles as the player
 * sees them and nothing else — [PipesState] carries no stored answer to leak in the first place. The
 * answer this file does work out ([solve]) is used in exactly two places, both in [teach]: to decide
 * which of the player's tiles are mistakes, and for [FALLBACK], which says openly that it is pointing
 * at the answer.
 *
 * The player's own turns are not treated as facts — a fresh board is scrambled, so a tile sitting a
 * certain way proves nothing. They decide only which deduction to show: the first one, in the order
 * the reasoning found them, that the board does not already agree with. Every tile the reasoning
 * pinned before it is then already sitting right, so "the tile above is set and points down into it"
 * describes what the player can see.
 *
 * Techniques, simplest first. The first two are exhausted before [NO_LOOP] is tried, and each tile
 * is labelled by the hardest thing its explanation leans on:
 * 1. [BORDER] — no pipe can point off the board.
 * 2. [SET_NEIGHBOUR] — an opening must meet an opening, so a side a set neighbour has settled settles
 *    this tile's side too. [WHICHEVER_WAY] is the same with a neighbour that is not set yet, but
 *    whose every remaining turn agrees on the side they share.
 * 3. [NO_LOOP] — two tiles already joined the long way round cannot be joined directly.
 *
 * Measured in PipesTeachingTest: the border and the neighbours pin every tile of all but a few
 * boards in a thousand, and [NO_LOOP] finishes those — two-wide corridors of bends that only a loop
 * tells apart. What is left is the odd board the generator made with two answers, where no reasoning
 * can pick and [FALLBACK] says so. "Last way out" and "would seal a network off" were written and
 * measured too; neither ever fired once these had had their say, so they are not carried — a
 * technique no board needs is one no test can check.
 *
 * Orders never come from a hash: every loop walks cells in index order, so the same board gets the
 * same hint on every platform (CLAUDE.md, "Hash iteration order"). Free of `android.*` and `java.*`
 * for the web build.
 */
internal object PipesTeacher {

    const val BORDER = "border"
    const val SET_NEIGHBOUR = "set-neighbour"
    const val WHICHEVER_WAY = "whichever-way"
    const val NO_LOOP = "no-loop"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique, simplest first, for reports. */
    val TECHNIQUES = listOf(BORDER, SET_NEIGHBOUR, WHICHEVER_WAY, NO_LOOP, FALLBACK)

    /** What the hint panel shows at once, in characters; an explanation that would run past it drops its closing line. */
    private const val MAX_EXPLANATION = 200

    private const val UNKNOWN = 0
    private const val OPEN = 1
    private const val CLOSED = 2

    /** Direction index d is the bit `1 shl d`: 0 up, 1 right, 2 down, 3 left, as in [Pipes]. */
    private val towards = listOf("above", "to the right", "below", "to the left")
    private val sideName = listOf("top", "right", "bottom", "left")

    /**
     * One step: turn [cell] until it reads [mask]. For a [MISTAKE], [wrong] is the turn the player
     * left it at, and the step is taken back as soon as the tile reads anything else.
     */
    class Step(
        val technique: String,
        val cell: Int,
        val mask: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
        val wrong: Int? = null,
    )

    /**
     * Why a side is known open or shut: the border (from = -1), or a neighbour [from] whose every
     * remaining turn agrees on it, [fromFixed] when that neighbour was down to one turn.
     */
    private class Why(
        val from: Int,
        val fromFixed: Boolean,
        /** [NO_LOOP] for a side shut to stop a loop; null for the border and neighbours. */
        val rule: String? = null,
        /** For [NO_LOOP]: the path that already joins the two tiles. */
        val cited: List<Int> = emptyList(),
    )

    private val borderWhy = Why(-1, true)

    /** A tile the reasoning pinned to one turn, with what it knew about each side at that moment. */
    private class Forced(val cell: Int, val mask: Int, val status: IntArray, val why: List<Why?>)

    /** An explanation, the tiles it leans on, and the technique it amounts to. */
    private class Reason(
        val text: String,
        val cited: Set<Int>,
        val technique: String,
        val setNeighbours: List<Int>,
        /** [text] without its closing "So it has only one way round.", for when the panel has no room for it. */
        val brief: String = text,
    ) {
        /** [text] after a [lead], or [brief] after it when the two together would not fit the panel. */
        fun after(lead: String = ""): String = if (lead.length + text.length <= MAX_EXPLANATION) lead + text else lead + brief
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------

    /**
     * The next thing to show this player, or null on a solved board.
     *
     * Mistakes come first. On a Pipes board every tile starts turned at random, so "doesn't match the
     * answer" cannot by itself mean "the player got it wrong": only a tile they have turned
     * ([PipesState.turned]) can be their mistake. And turning tiles to see how they sit is how Pipes
     * is played, so a dry tile mid-turn is not a claim either. What the player reads as progress is
     * the water, so a mistake is a tile they turned that runs full but that the finished network has
     * turned another way.
     */
    fun teach(s: PipesState): Step? {
        if (s.solved) return null
        val net = Net(s.width, s.height, s.cells).also { it.run() }
        val solution = solve(s.width, s.height, s.cells)
        if (solution != null) mistake(s, net, solution)?.let { return it }
        return next(net, s.cells) ?: solution?.let { fallback(s, net, it) }
    }

    private fun mistake(s: PipesState, net: Net, solution: List<Int>): Step? {
        val wet = Pipes.filled(s)
        // A handful of generated boards have more than one answer, so a tile that differs from the
        // one [solve] found is only wrong if no answer at all turns it that way.
        val wrong = s.cells.indices.filter {
            it in s.turned && it in wet && s.cells[it] != solution[it] &&
                solve(s.width, s.height, s.cells, it to s.cells[it]) == null
        }
        if (wrong.isEmpty()) return null
        // Prefer one the reasoning reaches and can explain truthfully: every set neighbour its
        // explanation leans on is sitting right on the board.
        for (f in net.forced) {
            if (f.cell !in wrong) continue
            val why = explain(s.width, s.height, s.cells, f)
            if (why.setNeighbours.any { s.cells[it] != solution[it] }) continue
            return Step(
                technique = MISTAKE,
                cell = f.cell,
                mask = solution[f.cell],
                focus = setOf(f.cell),
                cited = why.cited,
                nudge = "Check this tile.",
                explanation = why.after("Wet doesn't mean right. "),
                wrong = s.cells[f.cell],
            )
        }
        val cell = wrong[0]
        return Step(
            technique = MISTAKE,
            cell = cell,
            mask = solution[cell],
            focus = setOf(cell),
            cited = emptySet(),
            nudge = "Check this tile.",
            explanation = "Wet doesn't mean right: no finished network has this " +
                "one turned this way.",
            wrong = s.cells[cell],
        )
    }

    /**
     * Nothing short enough to explain applies, so turn a tile the answer settles. Says plainly that
     * it is doing this.
     */
    private fun fallback(s: PipesState, net: Net, solution: List<Int>): Step? {
        val cell = s.cells.indices.firstOrNull { net.cand[it].size > 1 && s.cells[it] != solution[it] }
            ?: s.cells.indices.firstOrNull { s.cells[it] != solution[it] }
            ?: return null
        return Step(
            technique = FALLBACK,
            cell = cell,
            mask = solution[cell],
            focus = setOf(cell),
            cited = emptySet(),
            nudge = "This one takes a longer chain. Look at the glowing tile.",
            explanation = "Nothing on the board settles this tile yet, so here's a way it sits in a " +
                "finished network.",
        )
    }

    // ---- reasoning from what the player can see ---------------------------------------------------

    /**
     * The first tile the reasoning pins down that the board does not already show, or null when
     * every tile it can pin down is already sitting right.
     *
     * Takes only the tiles as they lie: their shapes to reason from, and their turns to choose which
     * step to show. No answer goes in — see the class comment.
     */
    fun deduce(width: Int, height: Int, cells: List<Int>): Step? =
        next(Net(width, height, cells).also { it.run() }, cells)

    /** Every tile the reasoning pins down, in the order it does, as cell to turn. For tests. */
    fun forcedOrder(width: Int, height: Int, cells: List<Int>): List<Pair<Int, Int>> =
        Net(width, height, cells).also { it.run() }.forced.map { it.cell to it.mask }

    private fun next(net: Net, cells: List<Int>): Step? {
        val f = net.forced.firstOrNull { cells[it.cell] != it.mask } ?: return null
        val why = explain(net.w, net.h, cells, f)
        return Step(
            technique = why.technique,
            cell = f.cell,
            mask = f.mask,
            focus = setOf(f.cell),
            cited = why.cited,
            nudge = "Look at the glowing tile.",
            explanation = why.after(),
        )
    }

    /** The reasoning behind [f], in words. */
    private fun explain(w: Int, h: Int, cells: List<Int>, f: Forced): Reason {
        val t = f.cell
        val shape = cells[t]
        val cited = mutableListOf<Int>()
        val setNeighbours = mutableListOf<Int>()
        val border = mutableListOf<Int>()
        val parts = mutableListOf<String>()
        var whichever = false
        var loop = false
        // Settled sides by what they say: 0 set and open, 1 set and closed, 2 free and open, 3 free
        // and closed. Walked in that order, so the wording is the same whatever side came first.
        val sides = Array(4) { mutableListOf<Int>() }
        for (d in 0 until 4) {
            val why = f.why[d] ?: continue
            if (f.status[d] == UNKNOWN) continue
            if (why === borderWhy) {
                border += d
                continue
            }
            if (why.rule == NO_LOOP) {
                loop = true
                cited += why.cited
                parts += "it is already joined to the tile ${towards[d]} the long way round, so " +
                    "joining them directly would close a loop"
                continue
            }
            // A side this tile settled for itself is a consequence, not a reason.
            if (why.from == t) continue
            val open = f.status[d] == OPEN
            cited += why.from
            if (why.fromFixed) setNeighbours += why.from else whichever = true
            sides[(if (why.fromFixed) 0 else 2) + (if (open) 0 else 1)] += d
        }
        // Neighbours that say the same thing share one clause: "the tiles to the right and to the
        // left can never point at it", not the same clause twice, which ran
        // the longest explanations past what the panel shows at once.
        for (kind in 0 until 4) {
            val ds = sides[kind]
            if (ds.isEmpty()) continue
            val one = ds.size == 1
            val tiles = (if (one) "the tile " else "the tiles ") + join(ds.map { towards[it] })
            parts += tiles + when (kind) {
                0 -> if (one) " is set and points into it" else " are set and point into it"
                1 -> if (one) " is set and doesn't point at it" else " are set and don't point at it"
                2 -> if (one) " always points into it" else " always point into it"
                else -> " can never point at it"
            }
        }
        val technique = when {
            loop -> NO_LOOP
            parts.isEmpty() -> BORDER
            whichever -> WHICHEVER_WAY
            else -> SET_NEIGHBOUR
        }
        val citedSorted = cited.filter { it != t }.distinct().sorted().toSet()

        // The border cases a player learns first get their own sentence.
        if (parts.isEmpty()) {
            val text = when {
                shape == 5 || shape == 10 ->
                    "A straight can't point off the board, so against the border it has to run along it."
                shape.countOneBits() == 3 ->
                    "A T can't point off the board, so its flat side has to face the border."
                shape.countOneBits() == 2 && border.size >= 2 ->
                    "In a corner, a bend can't point off either edge, so it can only turn one way."
                else ->
                    "It sits against the border on its ${join(border.map { sideName[it] })}, and no " +
                        "pipe can point off the board, so it only fits one way round."
            }
            return Reason(text, citedSorted, technique, setNeighbours)
        }
        val all = buildList {
            if (border.isNotEmpty()) add("its ${join(border.map { sideName[it] })} is against the border")
            addAll(parts)
        }
        val reasons = "${clauses(all).cap()}."
        return Reason("$reasons So it has only one way round.", citedSorted, technique, setNeighbours, brief = reasons)
    }

    // ---- the reasoning engine ---------------------------------------------------------------------

    private fun neighbourOf(w: Int, h: Int, i: Int, d: Int): Int {
        val r = i / w
        val c = i % w
        return when (d) {
            0 -> if (r > 0) i - w else -1
            1 -> if (c < w - 1) i + 1 else -1
            2 -> if (r < h - 1) i + w else -1
            else -> if (c > 0) i - 1 else -1
        }
    }

    /** Every distinct turn of [mask], ascending. */
    private fun turns(mask: Int): List<Int> {
        val out = mutableListOf<Int>()
        var m = mask
        repeat(4) {
            if (m !in out) out += m
            m = Pipes.rotateCw(m)
        }
        return out.sorted()
    }

    /**
     * What is known: for each tile the turns still possible, and for each edge between two tiles
     * whether it is open, shut or undecided, with why.
     */
    private class Net(val w: Int, val h: Int, shapes: List<Int>) {
        val n = w * h
        val cand: Array<List<Int>> = Array(n) { turns(shapes[it]) }
        private val edge = IntArray(2 * n)
        private val why = arrayOfNulls<Why>(2 * n)
        val forced = mutableListOf<Forced>()
        var broken = false

        private fun neighbour(i: Int, d: Int) = neighbourOf(w, h, i, d)

        /** Horizontal edges are numbered by their left tile, vertical ones by their top tile plus n. */
        private fun edgeId(i: Int, d: Int) = when (d) {
            0 -> n + i - w
            1 -> i
            2 -> n + i
            else -> i - 1
        }

        private fun status(i: Int, d: Int): Int = if (neighbour(i, d) < 0) CLOSED else edge[edgeId(i, d)]

        private fun whyOf(i: Int, d: Int): Why? = if (neighbour(i, d) < 0) borderWhy else why[edgeId(i, d)]

        fun copyOf(): Net {
            val out = Net(w, h, List(n) { 0 })
            for (i in 0 until n) out.cand[i] = cand[i]
            edge.copyInto(out.edge)
            why.copyInto(out.why)
            out.broken = broken
            return out
        }

        /** Drop every turn of [i] that disagrees with a decided side; record it if one is left. */
        private fun restrict(i: Int) {
            val before = cand[i]
            val after = before.filter { m ->
                (0 until 4).all { d ->
                    val st = status(i, d)
                    st == UNKNOWN || ((m and (1 shl d) != 0) == (st == OPEN))
                }
            }
            if (after.size == before.size) return
            cand[i] = after
            if (after.isEmpty()) broken = true
            if (after.size == 1) {
                forced += Forced(i, after[0], IntArray(4) { status(i, it) }, List(4) { whyOf(i, it) })
            }
        }

        /**
         * Every rule, simplest first: the border and neighbours to a fixed point, and only when those
         * stall, one step of [noLoop] before going back to them.
         */
        fun run() {
            for (i in 0 until n) restrict(i)
            while (!broken) {
                propagate()
                if (broken) return
                if (noLoop()) continue
                break
            }
        }

        private fun setEdge(i: Int, d: Int, value: Int, reason: Why) {
            val id = edgeId(i, d)
            edge[id] = value
            why[id] = reason
            restrict(i)
            restrict(neighbour(i, d))
        }

        /** Each tile's joined-up stretch: tiles linked by edges known open, labelled by lowest index. */
        private fun components(): IntArray {
            val comp = IntArray(n) { -1 }
            for (start in 0 until n) {
                if (comp[start] >= 0) continue
                comp[start] = start
                val stack = ArrayDeque<Int>().apply { addLast(start) }
                while (stack.isNotEmpty()) {
                    val i = stack.removeLast()
                    for (d in 0 until 4) {
                        val j = neighbour(i, d)
                        if (j < 0 || comp[j] >= 0 || status(i, d) != OPEN) continue
                        comp[j] = start
                        stack.addLast(j)
                    }
                }
            }
            return comp
        }

        /** The tiles on the open path from [a] to [b], both included, ascending. */
        private fun pathBetween(a: Int, b: Int): List<Int> {
            val prev = IntArray(n) { -2 }
            prev[a] = -1
            val queue = ArrayDeque<Int>().apply { addLast(a) }
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                if (i == b) break
                for (d in 0 until 4) {
                    val j = neighbour(i, d)
                    if (j < 0 || prev[j] != -2 || status(i, d) != OPEN) continue
                    prev[j] = i
                    queue.addLast(j)
                }
            }
            val out = mutableListOf<Int>()
            var cur = b
            while (cur >= 0) {
                out += cur
                cur = prev[cur]
            }
            return out.sorted()
        }

        /** Undecided edges between two tiles, as (tile, direction right or down), in index order. */
        private fun undecided(): List<Pair<Int, Int>> = buildList {
            for (i in 0 until n) for (d in listOf(1, 2)) {
                if (neighbour(i, d) >= 0 && status(i, d) == UNKNOWN) add(i to d)
            }
        }

        /** Two tiles already joined another way cannot be joined directly: that closes a loop. */
        private fun noLoop(): Boolean {
            val comp = components()
            for ((i, d) in undecided()) {
                val j = neighbour(i, d)
                if (comp[i] != comp[j]) continue
                setEdge(i, d, CLOSED, Why(-1, false, NO_LOOP, pathBetween(i, j)))
                return true
            }
            return false
        }

        /** Neighbours agreeing on shared sides, to a fixed point. */
        private fun propagate() {
            var changed = true
            while (changed && !broken) {
                changed = false
                for (i in 0 until n) {
                    for (d in 0 until 4) {
                        if (broken) return
                        val j = neighbour(i, d)
                        if (j < 0 || status(i, d) != UNKNOWN) continue
                        val bit = 1 shl d
                        val opts = cand[i]
                        val value = when {
                            opts.all { it and bit != 0 } -> OPEN
                            opts.none { it and bit != 0 } -> CLOSED
                            else -> continue
                        }
                        val id = edgeId(i, d)
                        edge[id] = value
                        why[id] = Why(i, opts.size == 1)
                        restrict(i)
                        restrict(j)
                        changed = true
                    }
                }
            }
        }

        /** Pins [cell] to [mask], as a guess in [solve]. */
        fun assume(cell: Int, mask: Int) {
            cand[cell] = listOf(mask)
        }

        val settled: Boolean get() = cand.all { it.size == 1 }
    }

    /**
     * The finished network, found by search: the reasoning above, and a guess wherever it stalls.
     * Used only to spot mistakes and for [FALLBACK], never by [deduce]. Null only for a board with no
     * answer at all, which the generator never makes.
     */
    fun solve(width: Int, height: Int, cells: List<Int>, pin: Pair<Int, Int>? = null): List<Int>? {
        fun search(net: Net): List<Int>? {
            net.run()
            if (net.broken) return null
            if (net.settled) {
                val masks = net.cand.map { it[0] }
                return masks.takeIf { Pipes.connected(PipesState(width, height, it, 0)) }
            }
            val pick = (0 until net.n).filter { net.cand[it].size > 1 }.minBy { net.cand[it].size }
            for (m in net.cand[pick]) {
                val next = net.copyOf()
                next.assume(pick, m)
                search(next)?.let { return it }
            }
            return null
        }
        val start = Net(width, height, cells)
        if (pin != null) {
            if (pin.second !in start.cand[pin.first]) return null
            start.assume(pin.first, pin.second)
        }
        return search(start)
    }

    // ---- words -----------------------------------------------------------------------------------

    private fun join(words: List<String>): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
    }

    /** Whole clauses, which may carry their own "and": a semicolon keeps them apart. */
    private fun clauses(parts: List<String>): String = when (parts.size) {
        1 -> parts[0]
        else -> parts.joinToString("; ")
    }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
