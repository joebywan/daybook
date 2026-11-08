package com.joebywan.daybook.core

/**
 * What a search over positions found (Sand, Unblock). Only [Solvable] carries anything, so a search that ran out of
 * budget cannot be read as an answer, and neither can "no answer" (the structural rule of CLAUDE.md, "A truncated search
 * is not a proof").
 */
sealed interface SearchVerdict<out M> {
    /** A sequence of moves from the position searched that ends at a goal; empty when it already was one. */
    data class Solvable<M>(val path: List<M>) : SearchVerdict<M>

    /** Every position reachable was searched (within the budget) and none is a goal. A proof. */
    data object Unsolvable : SearchVerdict<Nothing>

    /** The budget ran out first: nothing is known. Never read this as [Unsolvable]. */
    data object Truncated : SearchVerdict<Nothing>
}

/** What [Search.shortest] makes of a position it reaches: a goal, one to search on from, or one to leave alone. */
enum class Reached { GOAL, EXPAND, DROP }

/** Every position [Search.reach] found, in breadth-first order, each with its distance from the nearest start. */
class Reach<P>(val positions: List<P>, val distance: IntArray) {
    val farthest: Int get() = distance.maxOrNull() ?: -1
}

/**
 * Breadth-first search over positions, shared by the puzzles whose boards are a few thousand positions apart (Sand's
 * plan line, Unblock's minimum). A position's key decides whether it was seen; the set is membership only, and positions
 * are expanded first in, first out, children in the order [moves] gives them, so no hash order ever reaches a choice and
 * the JVM and the web agree.
 */
object Search {

    /**
     * The fewest moves from [start] to a position [classify] calls a [Reached.GOAL]. A [Reached.DROP] position is neither
     * a goal nor searched past. Positions are marked seen before they are classified, so a dropped one is never reached a
     * second way. [budget] is the number of distinct positions the search may see (the start included); past it the
     * answer is [SearchVerdict.Truncated]. [SearchVerdict.Unsolvable] only when every reachable position was seen.
     */
    fun <P, K, M> shortest(
        start: P,
        budget: Int,
        key: (P) -> K,
        moves: (P, (M, P) -> Unit) -> Unit,
        classify: (P) -> Reached,
    ): SearchVerdict<M> {
        when (classify(start)) {
            Reached.GOAL -> return SearchVerdict.Solvable(emptyList())
            Reached.DROP -> return SearchVerdict.Unsolvable
            Reached.EXPAND -> Unit
        }
        val seen = HashSet<K>() // membership only
        seen.add(key(start))
        val positions = ArrayList<P>()
        val parent = ArrayList<Int>()
        val via = ArrayList<M?>()
        positions.add(start)
        parent.add(-1)
        via.add(null)
        var head = 0
        var found = -1
        var truncated = false
        while (head < positions.size && found < 0 && !truncated) {
            val at = head++
            moves(positions[at]) { m, child ->
                if (found >= 0 || truncated) return@moves
                if (!seen.add(key(child))) return@moves
                if (seen.size > budget) { truncated = true; return@moves }
                when (classify(child)) {
                    Reached.DROP -> Unit
                    Reached.EXPAND -> { positions.add(child); parent.add(at); via.add(m) }
                    Reached.GOAL -> { positions.add(child); parent.add(at); via.add(m); found = positions.size - 1 }
                }
            }
        }
        if (found < 0) return if (truncated) SearchVerdict.Truncated else SearchVerdict.Unsolvable
        val path = ArrayList<M>()
        var i = found
        while (parent[i] >= 0) {
            @Suppress("UNCHECKED_CAST")
            path.add(via[i] as M)
            i = parent[i]
        }
        path.reverse()
        return SearchVerdict.Solvable(path)
    }

    /**
     * Every position reachable from [starts] by [moves], breadth first, with its distance from the nearest start: the
     * whole space when the moves can be undone (Unblock's yard), or null when it holds more than [budget] positions.
     */
    fun <P, K> reach(starts: List<P>, budget: Int, key: (P) -> K, moves: (P, (P) -> Unit) -> Unit): Reach<P>? {
        val seen = HashSet<K>() // membership only
        val positions = ArrayList<P>()
        val distance = ArrayList<Int>()
        for (s in starts) if (seen.add(key(s))) { positions.add(s); distance.add(0) }
        if (positions.size > budget) return null
        var head = 0
        var over = false
        while (head < positions.size && !over) {
            val at = head++
            val d = distance[at] + 1
            moves(positions[at]) { child ->
                if (over || !seen.add(key(child))) return@moves
                if (seen.size > budget) { over = true; return@moves }
                positions.add(child)
                distance.add(d)
            }
        }
        if (over) return null
        return Reach(positions, distance.toIntArray())
    }
}
