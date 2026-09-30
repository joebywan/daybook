package com.joebywan.daybook.puzzles

/**
 * LITS, reasoned the way a person reasons it, one named step at a time.
 *
 * The same split as [KingsTeacher]. [deduce] is handed the board as the player sees it — the walls
 * and the shading — and has no parameter through which the stored answer could reach it. The answer
 * is read only in [teach]: to decide which shaded squares are mistakes, and for [FALLBACK], which
 * says openly that it is pointing at an answer.
 *
 * The rules reasoned from are the win check's, [Lits.isSolved]: one L, I, T or S per region, no 2x2
 * of shading, no two tetrominoes of one letter edge to edge across a wall. **Not** connectivity: the
 * win check stopped asking for it (PR #15), so a step that leaned on "the shading must join up" would
 * be arguing from a rule the board does not have. The generator still proves uniqueness only among
 * connected shadings, which leaves a sixth to a half of boards (by tier) with more than one answer under the real
 * rules; on those, reasoning runs out where the answers part, and the fallback says so.
 *
 * The player has one mark — shading — and the board crosses off what can't be shaded by itself
 * ([Lits.impossible]). So every step here ends in squares to shade; ruling a square out is never a
 * move on its own, only the reason for one. The candidate shapes each step reasons over are filtered
 * by exactly the rule the crosses use, so a step never argues from a cross the board isn't showing.
 *
 * Techniques, simplest first:
 * 1. [WHOLE_REGION] — a region of exactly four squares is its own tetromino.
 * 2. [OVERLAP] — every shape that fits the region covers these squares.
 * 3. [AVOID_BLOCK] / [LETTER_CLASH] — the same, once the shapes that would finish a 2x2 or touch a
 *    finished tetromino of their own letter are set aside.
 * 4. [NEIGHBOUR] — the same, once the shapes that would leave a neighbouring region no legal shape
 *    are set aside.
 * 5. [WHAT_IF] — leaving this square empty forces a short chain that ends with a region with no
 *    legal shape.
 *
 * Every loop walks regions, shapes and cells in index order; nothing reaches the output through a
 * hash's iteration order (CLAUDE.md, "Hash iteration order").
 */
internal object LitsTeacher {

    const val WHOLE_REGION = "whole-region"
    const val OVERLAP = "overlap"
    const val AVOID_BLOCK = "avoid-2x2"
    const val LETTER_CLASH = "letter-clash"
    const val NEIGHBOUR = "neighbour"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(WHOLE_REGION, OVERLAP, AVOID_BLOCK, LETTER_CLASH, NEIGHBOUR, WHAT_IF, FALLBACK)

    /**
     * How many forced shapes a [WHAT_IF] may walk through before it counts as a longer chain. The
     * same limit as Kings, for the same reason: past two, one sentence can't carry the argument.
     */
    const val MAX_CHAIN = 2

    /**
     * How many nodes a search for an answer may visit. The regions are small enough that the tree is
     * exhausted far inside this; if it ever is not, the result is [Search.Unknown], which nothing
     * reads as a proof (CLAUDE.md, "A truncated search is not a proof").
     */
    private const val SEARCH_BUDGET = 400_000

    /** One step: what to shade or clear, where to look, and why. [clears] only ever undoes a mistake. */
    class Step(
        val technique: String,
        val shades: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val targets: Set<Int> get() = (shades + clears).toSet()
    }

    // ---- the board's fixed geometry ----------------------------------------------------------

    private class Shape(val cells: List<Int>, val piece: Lits.Piece)

    /** Walls and every tetromino each region could ever hold. Regions are numbered 0 until [count]. */
    private class Layout(val w: Int, val h: Int, val region: List<Int>) {
        val n = w * h
        private val ids = region.distinct().sorted()
        val count = ids.size
        /** Region number of each cell, as an index into [cellsOf]. */
        val of = IntArray(n) { ids.binarySearch(region[it]) }
        val cellsOf: List<List<Int>> = List(count) { k -> (0 until n).filter { of[it] == k } }
        val shapes: List<List<Shape>> = List(count) { k ->
            Lits.tetrominoes(cellsOf[k], w, h).map { (cells, piece) -> Shape(cells.sorted(), piece) }
        }

        fun neighbours(cell: Int) = Lits.neighbours(cell, w, h)

        /** Top-left corners of every 2x2 window containing [cell]. */
        fun windowsAt(cell: Int): List<Int> {
            val r = cell / w
            val c = cell % w
            return buildList {
                for (dr in -1..0) for (dc in -1..0) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr >= 0 && cc >= 0 && rr + 1 < h && cc + 1 < w) add(rr * w + cc)
                }
            }
        }

        fun window(corner: Int) = listOf(corner, corner + 1, corner + w, corner + w + 1)

        /** Regions other than [self] with a square touching [cells], corners included. */
        fun near(cells: List<Int>, self: Int): List<Int> {
            val out = BooleanArray(count)
            for (cell in cells) {
                val r = cell / w
                val c = cell % w
                for (dr in -1..1) for (dc in -1..1) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr in 0 until h && cc in 0 until w) out[of[rr * w + cc]] = true
                }
            }
            out[self] = false
            return (0 until count).filter { out[it] }
        }
    }

    // ---- what the player can see -------------------------------------------------------------

    /**
     * The shading on the board, plus — for a what-if — squares supposed to stay empty, and what
     * each region can still become. [cand] is the rule the crosses use ([Lits.impossible]): a shape
     * must cover the region's shading, avoid a 2x2 with the shading already down, and not sit
     * against a finished tetromino of its own letter. [cand1] also asks that every neighbouring
     * region still has a shape that can stand beside it.
     */
    private class Sight(val layout: Layout, val shaded: BooleanArray, val empty: BooleanArray) {
        val letters: List<Lits.Piece?> = Lits.letters(layout.w, layout.h, layout.region, shaded.asList())

        fun shadedIn(k: Int) = layout.cellsOf[k].filter { shaded[it] }

        fun complete(k: Int) = shadedIn(k).size >= 4

        /** Shapes that fit the region's shading and the supposed empties, before any rule. */
        fun geo(k: Int): List<Shape> {
            val have = shadedIn(k)
            return layout.shapes[k].filter { s -> s.cells.containsAll(have) && s.cells.none { empty[it] } }
        }

        fun fillsBlock(s: Shape): Boolean = s.cells.any { cell ->
            layout.windowsAt(cell).any { corner -> layout.window(corner).all { it in s.cells || shaded[it] } }
        }

        fun clashes(s: Shape): Boolean = s.cells.any { cell ->
            layout.neighbours(cell).any { next -> next !in s.cells && shaded[next] && letters[next] == s.piece }
        }

        private val candCache = arrayOfNulls<List<Shape>>(layout.count)
        fun cand(k: Int): List<Shape> =
            candCache[k] ?: geo(k).filter { !fillsBlock(it) && !clashes(it) }.also { candCache[k] = it }

        /**
         * Why [t] and [u], shapes of two different regions, cannot both stand: [BLOCK] when together
         * with the shading they fill a 2x2, [LETTER] when they are one letter edge to edge. Zero when
         * they can.
         */
        fun conflict(t: Shape, u: Shape): Int {
            var why = 0
            if (t.piece == u.piece && t.cells.any { a -> layout.neighbours(a).any { it in u.cells } }) why = why or LETTER
            val block = t.cells.any { a ->
                layout.windowsAt(a).any { corner ->
                    val win = layout.window(corner)
                    win.any { it in u.cells } && win.all { it in t.cells || it in u.cells || shaded[it] }
                }
            }
            if (block) why = why or BLOCK
            return why
        }

        /** Neighbouring regions that [t], a shape of region [k], would leave with no shape at all. */
        fun killers(k: Int, t: Shape): List<Int> =
            layout.near(t.cells, k).filter { b -> cand(b).none { u -> conflict(t, u) == 0 } }

        private val cand1Cache = arrayOfNulls<List<Shape>>(layout.count)
        fun cand1(k: Int): List<Shape> =
            cand1Cache[k] ?: cand(k).filter { killers(k, it).isEmpty() }.also { cand1Cache[k] = it }

        fun with(shade: Collection<Int> = emptyList(), keepEmpty: Collection<Int> = emptyList()): Sight {
            val s = shaded.copyOf().also { a -> shade.forEach { a[it] = true } }
            val e = empty.copyOf().also { a -> keepEmpty.forEach { a[it] = true } }
            return Sight(layout, s, e)
        }
    }

    private const val BLOCK = 1
    private const val LETTER = 2

    private fun common(shapes: List<Shape>): List<Int> =
        if (shapes.isEmpty()) emptyList() else shapes[0].cells.filter { c -> shapes.all { c in it.cells } }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback --------------------

    /**
     * The next thing to show this player, or null on a solved board.
     *
     * A shaded square is a mistake only when no answer at all — under the win check's rules — keeps
     * it. A player who has wandered onto a *different* legal answer than the stored one has made no
     * mistake, and the win check would accept their board; calling it wrong would be the bug PR #15
     * fixed, back again in the hint panel.
     */
    fun teach(s: LitsState): Step? {
        if (s.solved) return null
        val layout = Layout(s.width, s.height, s.region)
        val shaded = s.shaded.toBooleanArray()
        val found = extend(layout, shaded, emptySet())
        mistake(s, layout, shaded, found)?.let { return it }
        deduce(s.width, s.height, s.region, s.shaded)?.let { return it }
        val reference = when {
            s.shaded.indices.all { !s.shaded[it] || s.solution[it] } -> s.solution.indices.filter { s.solution[it] }.toSet()
            found is Search.Found -> found.answer
            else -> return null
        }
        return fallback(layout, shaded, reference)
    }

    private sealed interface Search {
        data class Found(val answer: Set<Int>) : Search
        data object NoAnswer : Search
        data object Unknown : Search
    }

    /**
     * An answer that keeps every shaded square and shades none of [forbidden], by exhaustive search
     * under the win check's rules. [Search.NoAnswer] only once the tree is covered.
     */
    private fun extend(layout: Layout, shaded: BooleanArray, forbidden: Set<Int>): Search {
        val n = layout.n
        val options = (0 until layout.count).map { k ->
            val have = layout.cellsOf[k].filter { shaded[it] }
            layout.shapes[k].filter { s -> s.cells.containsAll(have) && s.cells.none { it in forbidden } }
        }
        if (options.any { it.isEmpty() }) return Search.NoAnswer
        val order = (0 until layout.count).sortedBy { options[it].size }
        val on = BooleanArray(n)
        val mark = arrayOfNulls<Lits.Piece>(n)
        var nodes = 0
        var truncated = false
        var answer: Set<Int>? = null

        fun blocks(s: Shape) = s.cells.any { cell ->
            layout.windowsAt(cell).any { corner -> layout.window(corner).all { on[it] } }
        }

        fun twins(s: Shape) = s.cells.any { cell ->
            layout.neighbours(cell).any { it !in s.cells && on[it] && mark[it] == s.piece }
        }

        fun walk(depth: Int) {
            if (answer != null || truncated) return
            if (nodes++ > SEARCH_BUDGET) {
                truncated = true
                return
            }
            if (depth == order.size) {
                answer = (0 until n).filter { on[it] }.toSet()
                return
            }
            for (s in options[order[depth]]) {
                s.cells.forEach { on[it] = true; mark[it] = s.piece }
                if (!blocks(s) && !twins(s)) walk(depth + 1)
                s.cells.forEach { on[it] = false; mark[it] = null }
                if (answer != null || truncated) return
            }
        }
        walk(0)
        val a = answer
        return when {
            a != null -> Search.Found(a)
            truncated -> Search.Unknown
            else -> Search.NoAnswer
        }
    }

    /**
     * A shaded square no answer keeps. When the search could not decide ([Search.Unknown]), falls
     * back to the stored answer rather than calling nothing a mistake.
     */
    private fun mistake(s: LitsState, layout: Layout, shaded: BooleanArray, found: Search): Step? {
        if (found is Search.Found) return null
        val offAnswer = s.shaded.indices.filter { s.shaded[it] && !s.solution[it] }
        if (offAnswer.isEmpty()) return null
        // Prefer the square whose clearing alone rescues the board: that one is the mistake, and
        // the others may only look wrong because of it.
        val wrong = if (found is Search.NoAnswer) {
            offAnswer.firstOrNull { c ->
                extend(layout, shaded.copyOf().also { it[c] = false }, emptySet()) is Search.Found
            } ?: offAnswer.first()
        } else {
            offAnswer.first()
        }
        return explainMistake(layout, shaded, wrong)
    }

    private fun explainMistake(layout: Layout, shaded: BooleanArray, wrong: Int): Step {
        val k = layout.of[wrong]
        val region = layout.cellsOf[k]
        val sight = Sight(layout, shaded, BooleanArray(layout.n))
        val have = sight.shadedIn(k)
        fun step(explanation: String, cited: Collection<Int>) = Step(
            technique = MISTAKE,
            clears = listOf(wrong),
            focus = setOf(wrong),
            cited = cited.toSet(),
            nudge = "Check this square.",
            explanation = "$explanation Tap it to clear it.",
        )
        if (have.size > 4) return step("This region has more than four squares shaded.", region)
        layout.windowsAt(wrong).firstOrNull { corner -> layout.window(corner).all { shaded[it] } }?.let { corner ->
            return step("This square completes a 2x2 block of shading, which is never allowed.", layout.window(corner))
        }
        if (sight.geo(k).isEmpty()) {
            return step("No L, I, T or S in this region covers all of its shaded squares.", region)
        }
        sight.letters[wrong]?.let { piece ->
            val twin = region.flatMap { layout.neighbours(it) }
                .firstOrNull { layout.of[it] != k && shaded[it] && sight.letters[it] == piece }
            if (twin != null) {
                val theirs = layout.cellsOf[layout.of[twin]].filter { shaded[it] }
                return step("This makes ${article(piece)} touching the ${piece.name} next door.", have + theirs)
            }
        }
        if (sight.cand(k).isEmpty()) {
            return step(
                "With this square shaded, every shape left in this region would finish a 2x2 block " +
                    "or touch a tetromino of its own letter.",
                region,
            )
        }
        return step("This square can't be part of any finished board.", emptyList())
    }

    /**
     * Nothing short enough to explain applies, so point at a square of [reference] in the region
     * with the fewest shapes left. Says plainly which of two things is going on: a square every
     * remaining answer shades (reasoning exists, just longer than a hint), or a genuine choice
     * between answers (there is nothing to reason out).
     */
    private fun fallback(layout: Layout, shaded: BooleanArray, reference: Set<Int>): Step? {
        val sight = Sight(layout, shaded, BooleanArray(layout.n))
        val open = reference.sorted().filter { !shaded[it] }
        if (open.isEmpty()) return null
        val byTightness = open.sortedBy { sight.cand(layout.of[it]).size }
        var pick = byTightness.first()
        var verdict: Search = Search.Unknown
        for (c in byTightness) {
            val v = extend(layout, shaded, setOf(c))
            if (v is Search.NoAnswer) {
                pick = c
                verdict = v
                break
            }
            if (verdict is Search.Unknown && v is Search.Found) verdict = v
        }
        val home = layout.cellsOf[layout.of[pick]].toSet()
        return when (verdict) {
            Search.NoAnswer -> Step(
                technique = FALLBACK, shades = listOf(pick), focus = home, cited = emptySet(),
                nudge = "This one takes a longer chain. Look at the glowing region.",
                explanation = "This one needs a longer chain than a hint can walk through, so here's " +
                    "a square every answer shades.",
            )
            is Search.Found -> Step(
                technique = FALLBACK, shades = listOf(pick), focus = home, cited = emptySet(),
                nudge = "From here there's a choice to make. Look at the glowing region.",
                explanation = "More than one finish is still possible from here, so there's nothing " +
                    "to work out: this square is shaded in one of them.",
            )
            Search.Unknown -> Step(
                technique = FALLBACK, shades = listOf(pick), focus = home, cited = emptySet(),
                nudge = "This one takes a longer chain. Look at the glowing region.",
                explanation = "This needs more than a hint can walk through, so here's a square " +
                    "from the answer.",
            )
        }
    }

    // ---- reasoning from what the player can see --------------------------------------------

    /**
     * The simplest step visible on this board, or null when none applies — including on a board
     * whose shading already leaves some region no legal shape, since nothing reasoned from a
     * contradiction is worth teaching.
     *
     * Takes no answer on purpose — see the class comment.
     */
    fun deduce(width: Int, height: Int, region: List<Int>, shaded: List<Boolean>): Step? {
        val layout = Layout(width, height, region)
        val sight = Sight(layout, shaded.toBooleanArray(), BooleanArray(layout.n))
        if ((0 until layout.count).any { sight.cand(it).isEmpty() }) return null
        return wholeRegion(sight)
            ?: overlap(sight)
            ?: filtered(sight)
            ?: neighbour(sight)
            ?: whatIf(sight)
    }

    private fun wholeRegion(s: Sight): Step? {
        for (k in 0 until s.layout.count) {
            val cells = s.layout.cellsOf[k]
            if (cells.size != 4 || s.complete(k)) continue
            val todo = cells.filter { !s.shaded[it] }
            return Step(
                technique = WHOLE_REGION,
                shades = todo,
                focus = cells.toSet(),
                cited = cells.toSet(),
                nudge = "Look at the glowing region.",
                explanation = "This region has exactly four squares, so its tetromino is the whole " +
                    "region. Shade ${if (todo.size == 4) "all four" else "the rest"}.",
            )
        }
        return null
    }

    private fun overlap(s: Sight): Step? {
        for (k in 0 until s.layout.count) {
            if (s.complete(k)) continue
            val shapes = s.geo(k)
            val todo = common(shapes).filter { !s.shaded[it] }
            if (todo.isEmpty()) continue
            val cells = s.layout.cellsOf[k].toSet()
            val around = if (s.shadedIn(k).isEmpty()) "" else " around its shaded squares"
            return Step(
                technique = OVERLAP,
                shades = todo,
                focus = cells,
                cited = cells,
                nudge = "Look at the glowing region.",
                explanation = if (shapes.size == 1) {
                    "Only one L, I, T or S fits in this region$around, so shade it."
                } else {
                    "Every L, I, T or S that fits in this region$around covers ${these(todo)}, so " +
                        "${they(todo)} shaded whichever it is."
                },
            )
        }
        return null
    }

    /** [OVERLAP] after setting aside the shapes the 2x2 rule, the letter rule, or both, forbid. */
    private fun filtered(s: Sight): Step? {
        for (k in 0 until s.layout.count) {
            if (s.complete(k)) continue
            val geo = s.geo(k)
            val noBlock = geo.filter { !s.fillsBlock(it) }
            val noClash = geo.filter { !s.clashes(it) }
            val both = s.cand(k)
            val (technique, kept) = when {
                common(noBlock).any { !s.shaded[it] } -> AVOID_BLOCK to noBlock
                common(noClash).any { !s.shaded[it] } -> LETTER_CLASH to noClash
                common(both).any { !s.shaded[it] } -> LETTER_CLASH to both
                else -> continue
            }
            val todo = common(kept).filter { !s.shaded[it] }
            val dropped = geo.filter { it !in kept }
            val cells = s.layout.cellsOf[k].toSet()
            val witnesses = dropped.flatMap { witnessesOf(s, it) }.toSet()
            val blockDropped = dropped.any { s.fillsBlock(it) }
            val clashing = dropped.filter { s.clashes(it) }.map { it.piece }.distinct().sortedBy { it.ordinal }
            val why = when {
                clashing.isEmpty() -> "would finish a 2x2 with the shading beside them"
                !blockDropped && clashing.size == 1 ->
                    "would make ${article(clashing[0])} touching the ${clashing[0].name} next door"
                !blockDropped -> "would touch a tetromino of their own letter"
                else -> "would finish a 2x2 or touch their own letter next door"
            }
            return Step(
                technique = technique,
                shades = todo,
                focus = cells,
                cited = cells + witnesses,
                nudge = "Look at the glowing region.",
                explanation = "Shapes here that $why are out. Every shape left covers ${these(todo)}, " +
                    "so shade ${them(todo)}.",
            )
        }
        return null
    }

    /** The shaded squares outside [t]'s region that condemn it: its 2x2 partners and letter twins. */
    private fun witnessesOf(s: Sight, t: Shape): List<Int> = buildList {
        for (cell in t.cells) {
            for (corner in s.layout.windowsAt(cell)) {
                val win = s.layout.window(corner)
                if (win.all { it in t.cells || s.shaded[it] }) addAll(win.filter { it !in t.cells })
            }
            for (next in s.layout.neighbours(cell)) {
                if (next !in t.cells && s.shaded[next] && s.letters[next] == t.piece) {
                    addAll(s.layout.cellsOf[s.layout.of[next]].filter { s.shaded[it] })
                }
            }
        }
    }

    /** [OVERLAP] after setting aside the shapes that would leave a neighbouring region no shape. */
    private fun neighbour(s: Sight): Step? {
        for (k in 0 until s.layout.count) {
            if (s.complete(k)) continue
            val kept = s.cand1(k)
            val todo = common(kept).filter { !s.shaded[it] }
            if (todo.isEmpty()) continue
            val dropped = s.cand(k).filter { it !in kept }
            val victims = dropped.flatMap { s.killers(k, it) }.distinct().sorted()
            var why = 0
            for (t in dropped) for (b in s.killers(k, t)) for (u in s.cand(b)) why = why or s.conflict(t, u)
            val reason = when (why) {
                BLOCK -> "no shape that avoids a 2x2 with them"
                LETTER -> "no shape that avoids touching their letter"
                else -> "no legal shape beside them"
            }
            val cells = s.layout.cellsOf[k].toSet()
            val next = victims.flatMap { s.layout.cellsOf[it] }.toSet()
            val whose = if (victims.size == 1) "the marked region" else "a marked region"
            return Step(
                technique = NEIGHBOUR,
                shades = todo,
                focus = cells,
                cited = cells + next,
                nudge = "Look at the glowing region, and at what's next to it.",
                explanation = "Some shapes here would leave $whose $reason. Every shape left " +
                    "covers ${these(todo)}, so shade ${them(todo)}.",
            )
        }
        return null
    }

    /** A supposition followed through: squares it forces, region by region, to a region left empty. */
    private class Chain(val forced: List<Pair<Int, List<Int>>>, val emptied: Int)

    private fun chainToEmpty(start: Sight, changed: List<Int>): Chain? {
        var cur = start
        var touched = changed
        val forced = mutableListOf<Pair<Int, List<Int>>>()
        while (true) {
            val nearby = nearbyRegions(cur.layout, touched)
            nearby.firstOrNull { cur.cand1(it).isEmpty() }?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val (k, next) = nearby.firstNotNullOfOrNull { k ->
                if (cur.complete(k)) null else common(cur.cand1(k)).filter { !cur.shaded[it] }.ifEmpty { null }?.let { k to it }
            } ?: return null
            forced += k to next
            cur = cur.with(shade = next)
            touched = touched + next
        }
    }

    /**
     * Regions a change at [cells] can reach within a step: ones with a square within three of it.
     * Only limits where a what-if looks for its contradiction; every contradiction it reports is
     * checked in full.
     */
    private fun nearbyRegions(layout: Layout, cells: List<Int>): List<Int> {
        val out = BooleanArray(layout.count)
        for (cell in cells) {
            val r = cell / layout.w
            val c = cell % layout.w
            for (dr in -3..3) for (dc in -3..3) {
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until layout.h && cc in 0 until layout.w) out[layout.of[rr * layout.w + cc]] = true
            }
        }
        return (0 until layout.count).filter { out[it] }
    }

    private fun whatIf(s: Sight): Step? {
        var best: Pair<Int, Chain>? = null
        for (x in 0 until s.layout.n) {
            if (s.shaded[x]) continue
            val k = s.layout.of[x]
            val shapes = s.cand1(k)
            // Squares no shape can use are the board's crosses; squares every shape uses were the
            // neighbour step's. Neither is a what-if.
            if (shapes.none { x in it.cells } || shapes.all { x in it.cells }) continue
            val chain = chainToEmpty(s.with(keepEmpty = listOf(x)), listOf(x)) ?: continue
            if (best == null || chain.forced.size < best.second.forced.size) best = x to chain
            if (chain.forced.size <= 1) break
        }
        val (x, chain) = best ?: return null
        val emptied = s.layout.cellsOf[chain.emptied]
        val home = s.layout.of[x]
        val steps = chain.forced.mapIndexed { i, (k, cells) ->
            val count = if (cells.size == 1) "a marked square" else "${numberWord(cells.size)} marked squares"
            when {
                i == 0 && k == home -> "its own region would need $count"
                i == 0 -> "a nearby region would need $count"
                else -> "then another ${if (cells.size == 1) "one" else numberWord(cells.size)}"
            }
        }
        val tail = "the marked region would have no legal shape left"
        return Step(
            technique = WHAT_IF,
            shades = listOf(x),
            focus = setOf(x),
            cited = (chain.forced.flatMap { it.second } + emptied).toSet(),
            nudge = "What if the glowing square stayed empty?",
            explanation = "If the glowing square stayed empty, " +
                (if (steps.isEmpty()) tail else steps.joinToString(", ") + ", leaving the marked region no shape") +
                ". So shade it.",
        )
    }

    // ---- words ---------------------------------------------------------------------------------

    private fun numberWord(k: Int) = listOf("no", "one", "two", "three", "four").getOrElse(k) { k.toString() }

    private fun article(p: Lits.Piece) = if (p == Lits.Piece.T) "a T" else "an ${p.name}"

    private fun these(cells: List<Int>) = if (cells.size == 1) "the glowing square" else "the glowing squares"

    private fun them(cells: List<Int>) = if (cells.size == 1) "it" else "them"

    private fun they(cells: List<Int>) = if (cells.size == 1) "it's" else "they're"
}
