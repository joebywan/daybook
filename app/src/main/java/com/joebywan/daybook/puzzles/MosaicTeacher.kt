package com.joebywan.daybook.puzzles

/**
 * Mosaic, taught one fill at a time.
 *
 * Mosaic is a planning puzzle, not a deduction puzzle: nothing on the board is hidden, there is no
 * stored answer, and every fill is legal. What can go wrong is running out, and because the limit
 * is the proven optimum with nothing to spare, one fill off an optimal line loses. So the teacher's
 * one hard rule is **never teach a fill the search has not proved still finishes inside the
 * limit**. Its reasons are then chosen from what the player can see and say out loud, simplest
 * first:
 *
 * 1. [FINISH] — one fill turns the whole board one colour.
 * 2. [COUNT] — as many fills left as colours to wipe out. A fill can only wipe a colour out by
 *    covering its last patch, so every fill must do exactly that. At zero slack this is very often
 *    what the end of a board comes down to.
 * 3. [BIGGEST] — the fill that swallows the most neighbouring areas at once, when it is on an
 *    optimal line. (It is not always: the obvious fill loses a good share of Expert boards, and the
 *    hint says so when it does.)
 * 4. [CENTRE] — grow the most central area: the one with every other area fewest steps away.
 * 5. [KEEPS] — no plain reason singles a fill out. The hint says exactly that, and offers a fill
 *    the search proved keeps the board finishable. This is the honest fallback, counted as such.
 *
 * [deduce] takes the board as the player sees it — sizes, cells, colours and fills left — and
 * nothing else; there is no answer to take. [teach] adds the one thing it needs on top: the
 * player's own fills ([MosaicState.trail]), to say which fill lost the board and how far back
 * Undo has to go.
 *
 * Free of `java.*` and never ordered by a hash: every loop walks areas, colours or fills in index
 * order, so a board gets the same hint on every platform.
 */
internal object MosaicTeacher {

    const val FINISH = "finish"
    const val COUNT = "count"
    const val BIGGEST = "biggest-swallow"
    const val CENTRE = "centre"
    const val KEEPS = "keeps-in-limit"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. [KEEPS] is the fallback. */
    val TECHNIQUES = listOf(FINISH, COUNT, BIGGEST, CENTRE, KEEPS)

    /**
     * Search nodes one hint may spend, across all its questions. Generous next to
     * [Mosaic.SOLVE_BUDGET] because a hint asks several questions of one board — is it still
     * winnable, and does each candidate keep it so — though they share one table, so the later
     * questions are mostly answered by the earlier ones. A board that runs this out gets no hint
     * rather than a guess, the same rule [Mosaic.solve] keeps.
     */
    const val BUDGET = 1_500_000

    /** Names for [Mosaic.palette], index for index. The fourth swatch is amber; players say yellow. */
    val colourNames = listOf("green", "red", "blue", "yellow", "purple")

    /**
     * One step: a fill to make ([cell] and [colour]), or — for a [MISTAKE] — a board to go back to.
     *
     * [rewind] is the board as it stood at the last point it could still be finished, when the
     * player's fills are on record; [rewindTo] is how many fills had been used there.
     */
    class Step(
        val technique: String,
        val cell: Int = -1,
        val colour: Int = -1,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        val nudge: String,
        val explanation: String,
        val rewind: MosaicState? = null,
        val rewindTo: Int = -1,
    )

    // ---- the board as areas --------------------------------------------------------------------

    /**
     * The board's single-colour areas, labelled in cell order, with who touches whom.
     *
     * Capped at [MAX_AREAS] so a group's membership and its colour pack into one Long for the
     * search's table; generated boards run to two dozen areas and a fill never adds one.
     */
    class Areas private constructor(
        val width: Int,
        val cells: List<Int>,
        val label: IntArray,
        val count: Int,
        val nbr: LongArray,
        val hue: IntArray,
    ) {
        fun cellsOf(area: Int): Set<Int> = cells.indices.filter { label[it] == area }.toSet()

        fun cellsOf(areas: Long): Set<Int> = cells.indices.filter { (areas shr label[it]) and 1L != 0L }.toSet()

        /** The first cell of [area] in reading order, which is what a fill is recorded against. */
        fun firstCell(area: Int): Int = cells.indices.first { label[it] == area }

        val root: Pos
            get() = Pos(
                LongArray(count) { 1L shl it },
                nbr.copyOf(),
                hue.copyOf(),
                if (count == 64) -1L else (1L shl count) - 1,
                count,
            )

        companion object {
            const val MAX_AREAS = 61

            fun of(width: Int, height: Int, cells: List<Int>): Areas? {
                val n = cells.size
                val label = IntArray(n) { -1 }
                val queue = IntArray(n)
                var count = 0
                for (i in 0 until n) {
                    if (label[i] >= 0) continue
                    if (count == MAX_AREAS) return null
                    var head = 0
                    var tail = 0
                    label[i] = count
                    queue[tail++] = i
                    while (head < tail) {
                        val x = queue[head++]
                        val r = x / width
                        val c = x % width
                        for (y in intArrayOf(
                            if (r > 0) x - width else -1,
                            if (r < height - 1) x + width else -1,
                            if (c > 0) x - 1 else -1,
                            if (c < width - 1) x + 1 else -1,
                        )) {
                            if (y >= 0 && label[y] < 0 && cells[y] == cells[x]) {
                                label[y] = count
                                queue[tail++] = y
                            }
                        }
                    }
                    count++
                }
                val nbr = LongArray(count)
                for (x in 0 until n) {
                    val right = if (x % width < width - 1) x + 1 else -1
                    val down = if (x / width < height - 1) x + width else -1
                    for (y in intArrayOf(right, down)) {
                        if (y < 0 || label[x] == label[y]) continue
                        nbr[label[x]] = nbr[label[x]] or (1L shl label[y])
                        nbr[label[y]] = nbr[label[y]] or (1L shl label[x])
                    }
                }
                val hue = IntArray(count)
                for (x in 0 until n) hue[label[x]] = cells[x]
                return Areas(width, cells, label, count, nbr, hue)
            }
        }
    }

    /**
     * A search position: groups of the board's areas, each group named by the area that was
     * flooded to make it. No two touching groups share a colour, so a fill merges exactly the
     * flooded group's same-coloured neighbours and never cascades.
     */
    class Pos(
        val member: LongArray,
        val nbr: LongArray,
        val hue: IntArray,
        val alive: Long,
        val count: Int,
    )

    /** An exact table key: every live group's membership with its colour packed on top, sorted. */
    private class Key(val v: LongArray) {
        override fun equals(other: Any?) = other is Key && v.contentEquals(other.v)
        override fun hashCode() = v.contentHashCode()
    }

    // ---- the search ----------------------------------------------------------------------------

    /**
     * "Can this position still be finished in r fills?", answered exactly or not at all.
     *
     * Depth-first under the same admissible bound [Mosaic]'s generator uses — colours left minus
     * one, and half the spread of the area graph — with a table of refuted and proven positions
     * shared across every question one hint asks. Once [exhausted] is set, no answer it gave is to
     * be trusted, and the caller says nothing rather than guess.
     */
    class Search(val colours: Int, private val budget: Int = BUDGET) {
        var exhausted = false
            private set
        var spent = 0
            private set
        private val failedAt = HashMap<Key, Int>()
        private val wonAt = HashMap<Key, Int>()

        fun gain(p: Pos, group: Int, colour: Int): Int {
            var n = 0
            var near = p.nbr[group]
            while (near != 0L) {
                val j = near.countTrailingZeroBits()
                near = near and (near - 1)
                if (p.hue[j] == colour) n++
            }
            return n
        }

        fun play(p: Pos, group: Int, colour: Int): Pos {
            var eaten = 0L
            var near = p.nbr[group]
            while (near != 0L) {
                val j = near.countTrailingZeroBits()
                near = near and (near - 1)
                if (p.hue[j] == colour) eaten = eaten or (1L shl j)
            }
            val member = p.member.copyOf()
            val nbr = p.nbr.copyOf()
            val hue = p.hue.copyOf()
            var grown = p.member[group]
            var touching = p.nbr[group]
            var bits = eaten
            while (bits != 0L) {
                val j = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                grown = grown or p.member[j]
                touching = touching or p.nbr[j]
            }
            touching = touching and (eaten or (1L shl group)).inv()
            member[group] = grown
            nbr[group] = touching
            hue[group] = colour
            var rim = touching
            while (rim != 0L) {
                val k = rim.countTrailingZeroBits()
                rim = rim and (rim - 1)
                nbr[k] = (nbr[k] and eaten.inv()) or (1L shl group)
            }
            return Pos(member, nbr, hue, p.alive and eaten.inv(), p.count - eaten.countOneBits())
        }

        fun distinctColours(p: Pos): Int {
            var present = 0
            var bits = p.alive
            while (bits != 0L) {
                val j = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                present = present or (1 shl p.hue[j])
            }
            return present.countOneBits()
        }

        /** Distances from [from] to every live group, -1 for dead ones. */
        fun distances(p: Pos, from: Int): IntArray {
            val dist = IntArray(p.hue.size) { -1 }
            val queue = IntArray(p.hue.size)
            var head = 0
            var tail = 0
            dist[from] = 0
            queue[tail++] = from
            while (head < tail) {
                val u = queue[head++]
                var bits = p.nbr[u]
                while (bits != 0L) {
                    val v = bits.countTrailingZeroBits()
                    bits = bits and (bits - 1)
                    if (dist[v] >= 0) continue
                    dist[v] = dist[u] + 1
                    queue[tail++] = v
                }
            }
            return dist
        }

        private fun farthest(p: Pos, from: Int): Pair<Int, Int> {
            val d = distances(p, from)
            var far = from
            for (i in d.indices) if (d[i] > d[far]) far = i
            return far to d[far]
        }

        /** Admissible lower bound on the fills still needed. */
        fun bound(p: Pos): Int {
            if (p.count <= 1) return 0
            val first = farthest(p, p.alive.countTrailingZeroBits()).first
            val spread = farthest(p, first).second
            return maxOf(distinctColours(p) - 1, (spread + 1) / 2)
        }

        private fun key(p: Pos): Key {
            val v = LongArray(p.count)
            var k = 0
            var bits = p.alive
            while (bits != 0L) {
                val j = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                v[k++] = p.member[j] or (p.hue[j].toLong() shl 61)
            }
            v.sort()
            return Key(v)
        }

        /** Every fill in [p], encoded `group * 8 + colour`, most swallowing first, then index order. */
        fun moves(p: Pos): List<Int> {
            val out = ArrayList<Int>()
            var bits = p.alive
            while (bits != 0L) {
                val g = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                for (c in 0 until colours) if (c != p.hue[g]) out += g * 8 + c
            }
            return out.sortedWith(compareByDescending<Int> { gain(p, it / 8, it % 8) }.thenBy { it })
        }

        /** Whether [p] can be finished in at most [r] fills. Meaningless once [exhausted]. */
        fun canFinish(p: Pos, r: Int): Boolean {
            if (p.count <= 1) return true
            if (r <= 0 || exhausted) return false
            if (bound(p) > r) return false
            val key = key(p)
            if ((failedAt[key] ?: -1) >= r) return false
            if ((wonAt[key] ?: Int.MAX_VALUE) <= r) return true
            if (++spent > budget) {
                exhausted = true
                return false
            }
            for (m in moves(p)) {
                if (canFinish(play(p, m / 8, m % 8), r - 1)) {
                    wonAt[key] = r
                    return true
                }
                if (exhausted) return false
            }
            failedAt[key] = r
            return false
        }

        /** Whether filling [group] with [colour] leaves [p] finishable in the [r] - 1 fills after it. */
        fun keeps(p: Pos, group: Int, colour: Int, r: Int): Boolean = canFinish(play(p, group, colour), r - 1)

        /** The fewest fills that finish [p], or null past [cap] or the budget. */
        fun optimum(p: Pos, cap: Int): Int? {
            for (r in bound(p)..cap) {
                if (canFinish(p, r)) return r
                if (exhausted) return null
            }
            return null
        }
    }

    // ---- the whole hint: a lost board first, then a reason, then the honest fallback -------------

    /**
     * The next thing to show on [s], or null on a finished board or one the search could not
     * decide inside [BUDGET].
     *
     * A board that can no longer be finished comes first. Teaching a good fill on a lost board would
     * be teaching a move that cannot matter; what the player needs is where it slipped.
     */
    fun teach(s: MosaicState): Step? {
        if (s.solved) return null
        val areas = Areas.of(s.width, s.height, s.cells) ?: return null
        val search = Search(s.colours)
        val left = s.limit - s.moves
        val alive = left > 0 && search.canFinish(areas.root, left)
        if (search.exhausted) return null
        if (!alive) return lost(s, areas, search)
        return deduce(areas, s.colours, left, search)
    }

    /**
     * The simplest fill on this board that is still on an optimal line, with the reason for it.
     *
     * Everything it takes is on the player's screen: the board, the palette size and the count of
     * fills left. Null when the board is finished, already lost, or too big to decide.
     */
    fun deduce(width: Int, height: Int, colours: Int, cells: List<Int>, fillsLeft: Int): Step? {
        val areas = Areas.of(width, height, cells) ?: return null
        if (areas.count <= 1 || fillsLeft <= 0) return null
        val search = Search(colours)
        if (!search.canFinish(areas.root, fillsLeft)) return null
        return deduce(areas, colours, fillsLeft, search)
    }

    private fun deduce(a: Areas, colours: Int, r: Int, search: Search): Step? {
        val root = a.root
        val step = finish(a, root)
            ?: count(a, root, colours, r, search)
            ?: biggest(a, root, colours, r, search)
            ?: centre(a, root, colours, r, search)
            ?: keeps(a, root, r, search)
        return if (search.exhausted) null else step
    }

    private fun name(colour: Int) = colourNames.getOrElse(colour) { "colour ${colour + 1}" }

    private fun fill(
        a: Areas,
        technique: String,
        group: Int,
        colour: Int,
        focus: Set<Int>,
        cited: Set<Int>,
        nudge: String,
        explanation: String,
    ) = Step(
        technique = technique,
        cell = a.firstCell(group),
        colour = colour,
        focus = focus,
        cited = cited,
        targets = a.cellsOf(group),
        nudge = nudge,
        explanation = explanation,
    )

    /** The neighbours of [group] in [colour], as cells: what the fill would swallow. */
    private fun swallowed(a: Areas, group: Int, colour: Int): Set<Int> {
        var eaten = 0L
        var near = a.nbr[group]
        while (near != 0L) {
            val j = near.countTrailingZeroBits()
            near = near and (near - 1)
            if (a.hue[j] == colour) eaten = eaten or (1L shl j)
        }
        return a.cellsOf(eaten)
    }

    /** Why this colour for this area, in a few words, and never claiming more than is true. */
    private fun colourReason(search: Search, root: Pos, colours: Int, group: Int, colour: Int): String {
        val g = search.gain(root, group, colour)
        val best = (0 until colours).filter { it != root.hue[group] }.maxOf { search.gain(root, group, it) }
        return when {
            g > 0 && g == best && g == 1 -> "it joins the ${name(colour)} area beside it"
            g > 0 && g == best -> "it joins the most neighbours"
            else -> "that colour keeps you in the limit"
        }
    }

    private fun finish(a: Areas, root: Pos): Step? {
        for (g in 0 until a.count) {
            if (root.nbr[g] != root.alive and (1L shl g).inv()) continue
            val others = (0 until a.count).filter { it != g }.map { a.hue[it] }.distinct()
            if (others.size != 1) continue
            val c = others[0]
            return fill(
                a, FINISH, g, c,
                focus = a.cellsOf(g),
                cited = swallowed(a, g, c),
                nudge = "One fill can finish this.",
                explanation = "Every other area touches the glowing one, and they're all ${name(c)}. " +
                    "Pour ${name(c)} into it and the whole board is one colour.",
            )
        }
        return null
    }

    private fun count(a: Areas, root: Pos, colours: Int, r: Int, search: Search): Step? {
        val k = search.distinctColours(root)
        if (k < 3 || r != k - 1) return null
        val lone = (0 until a.count).filter { g -> (0 until a.count).count { a.hue[it] == a.hue[g] } == 1 }
        val present = (0 until colours).filter { c -> a.hue.any { it == c } }
        val candidates = lone.flatMap { g -> present.filter { it != a.hue[g] }.map { g * 8 + it } }
            .sortedWith(compareByDescending<Int> { search.gain(root, it / 8, it % 8) }.thenBy { it })
        val move = candidates.firstOrNull { search.keeps(root, it / 8, it % 8, r) } ?: return null
        val g = move / 8
        val c = move % 8
        val which = if (lone.size == 1) {
            "Only ${name(a.hue[g])} is down to one patch: pour ${name(c)} into it."
        } else {
            "Pour ${name(c)} into the glowing ${name(a.hue[g])} patch: " +
                "${colourReason(search, root, colours, g, c)}."
        }
        return fill(
            a, COUNT, g, c,
            focus = lone.flatMap { a.cellsOf(it) }.toSet(),
            cited = swallowed(a, g, c),
            nudge = "Count: ${fills(r)} left, $k colours on the board.",
            explanation = "A fill wipes out a colour only by covering its last patch. ${fills(r).cap()} " +
                "for ${k - 1} colours means every fill must. $which",
        )
    }

    private fun maxGain(search: Search, root: Pos, colours: Int): Int =
        (0 until root.count).maxOf { g ->
            (0 until colours).filter { it != root.hue[g] }.maxOfOrNull { search.gain(root, g, it) } ?: 0
        }

    private fun biggest(a: Areas, root: Pos, colours: Int, r: Int, search: Search): Step? {
        val mx = maxGain(search, root, colours)
        if (mx < 2) return null
        for (g in 0 until a.count) {
            for (c in 0 until colours) {
                if (c == a.hue[g] || search.gain(root, g, c) != mx) continue
                if (!search.keeps(root, g, c, r)) continue
                var near = a.cellsOf(g)
                var bits = a.nbr[g]
                while (bits != 0L) {
                    val j = bits.countTrailingZeroBits()
                    bits = bits and (bits - 1)
                    near = near + a.cellsOf(j)
                }
                return fill(
                    a, BIGGEST, g, c,
                    focus = near,
                    cited = swallowed(a, g, c),
                    nudge = "Which fill swallows the most areas?",
                    explanation = "Pouring ${name(c)} into the glowing area swallows $mx neighbouring areas " +
                        "at once. No fill on the board swallows more.",
                )
            }
        }
        return null
    }

    /** Whether some fill swallows several areas and none of the fills that swallow the most is safe. */
    private fun biggestLoses(a: Areas, root: Pos, colours: Int, r: Int, search: Search): Boolean {
        val mx = maxGain(search, root, colours)
        if (mx < 2) return false
        for (g in 0 until a.count) for (c in 0 until colours) {
            if (c != a.hue[g] && search.gain(root, g, c) == mx && search.keeps(root, g, c, r)) return false
        }
        return true
    }

    private fun centre(a: Areas, root: Pos, colours: Int, r: Int, search: Search): Step? {
        if (a.count < 3) return null
        val ecc = IntArray(a.count) { g -> search.distances(root, g).max() }
        val least = ecc.min()
        if (least == ecc.max()) return null
        for (g in 0 until a.count) {
            if (ecc[g] != least) continue
            val order = (0 until colours).filter { it != a.hue[g] }
                .sortedWith(compareByDescending<Int> { search.gain(root, g, it) }.thenBy { it })
            val c = order.firstOrNull { search.keeps(root, g, it, r) } ?: continue
            val warn = if (biggestLoses(a, root, colours, r, search)) " The biggest swallow falls short here." else ""
            return fill(
                a, CENTRE, g, c,
                focus = a.cellsOf(g),
                cited = swallowed(a, g, c),
                nudge = "Think about growing from the middle.",
                explanation = "Every other area is at most $least steps from the glowing one; none is more " +
                    "central. Grow it with ${name(c)}: ${colourReason(search, root, colours, g, c)}.$warn",
            )
        }
        return null
    }

    private fun keeps(a: Areas, root: Pos, r: Int, search: Search): Step? {
        // Up to two keeping fills: the first to teach, the second only to know whether it is alone.
        // "The only fill that works" is a fact the player can use — everything else loses — even
        // where no rule of thumb explains why.
        val safe = search.moves(root).asSequence().filter { search.keeps(root, it / 8, it % 8, r) }.take(2).toList()
        val m = safe.firstOrNull() ?: return null
        val g = m / 8
        val c = m % 8
        val warn = if (biggestLoses(a, root, search.colours, r, search)) " The biggest swallow falls short." else ""
        val claim = if (safe.size == 1) {
            "it's the only fill that stays in the limit: pour ${name(c)} into the glowing area."
        } else {
            "pouring ${name(c)} into the glowing area stays in the limit."
        }
        return fill(
            a, KEEPS, g, c,
            focus = a.cellsOf(g),
            cited = swallowed(a, g, c),
            nudge = "Try growing the glowing area.",
            explanation = "No rule of thumb explains this one, but $claim$warn",
        )
    }

    // ---- a board that can no longer be finished ----------------------------------------------------

    /** The board after the first [k] of [s]'s recorded fills, or null when they are not on record. */
    fun replay(s: MosaicState, k: Int): MosaicState? {
        if (s.start.size != s.cells.size || s.trail.size != s.moves) return null
        var b = MosaicState(s.width, s.height, s.colours, s.limit, s.start)
        for (f in s.trail.take(k)) b = b.flood(f / 8, f % 8)
        return b
    }

    private fun lost(s: MosaicState, a: Areas, search: Search): Step {
        val left = s.limit - s.moves
        val k = search.distinctColours(a.root)
        val why = when {
            left <= 0 -> "You're out of fills."
            k - 1 > left -> "$k colours are left and a fill wipes out one at most, so it needs ${fills(k - 1)}; " +
                "you have $left."
            else -> "No order of ${fills(left)} finishes it from here."
        }
        val nudge = if (left <= 0) "Out of fills." else "The limit can't be met from here."

        // The record is only trusted if it replays to the board on screen.
        val whole = replay(s, s.moves)
        val known = whole != null && whole.cells == s.cells
        var good = -1
        if (known) {
            for (j in s.moves - 1 downTo 0) {
                val b = replay(s, j)!!
                val bAreas = Areas.of(b.width, b.height, b.cells) ?: break
                val probe = Search(s.colours)
                val ok = probe.canFinish(bAreas.root, s.limit - j)
                if (probe.exhausted) break
                if (ok) {
                    good = j
                    break
                }
            }
        }
        if (good < 0) {
            return Step(
                technique = MISTAKE,
                focus = emptySet(),
                cited = emptySet(),
                targets = emptySet(),
                nudge = nudge,
                explanation = "$why Undo back to the last point it could still be done, or restart.",
            )
        }
        val at = replay(s, good)!!
        val slip = s.trail[good]
        val slipCells = at.area(slip / 8).toSet()
        val undo = s.moves - good
        val back = if (good == 0) {
            "Undo back to the start: your first fill, ${name(slip % 8)} on the glowing cells, lost it."
        } else {
            "Undo ${fills(undo)}, back to $good used: fill ${good + 1}, ${name(slip % 8)} on the glowing " +
                "cells, lost it."
        }
        return Step(
            technique = MISTAKE,
            focus = slipCells,
            cited = emptySet(),
            targets = slipCells,
            nudge = nudge,
            explanation = "$why $back",
            rewind = at,
            rewindTo = good,
        )
    }

    // ---- words ----------------------------------------------------------------------------------

    private fun fills(n: Int) = if (n == 1) "1 fill" else "$n fills"

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
