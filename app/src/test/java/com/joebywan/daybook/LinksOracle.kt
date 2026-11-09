package com.joebywan.daybook

/**
 * An independent Links checker, sharing nothing with `LinksLogic`: no reach, no ladder, no branching on a path's end.
 *
 * [count] decides the squares in reading order, each square choosing whether it has a line to the right and a line
 * down (its left and up lines are already fixed by its neighbours), and keeps a frontier of the strands still open
 * below the row and to the right of the square, each labelled with its piece and the piece's pair. States that agree on
 * the frontier are merged and counted (a broken-profile count), so it is exact and needs no budget. [obeys] walks the
 * finished lines from each end.
 *
 * [ends] is one character per square (`.` or `a`..), masks are bit 1 up, 2 right, 4 down, 8 left, as the board stores.
 * [count] can be told lines every answer counted must have ([must]) or must not ([not]), as masks: only their right and
 * down bits are read, so a line is named once, by its upper or left square.
 */
object LinksOracle {

    private fun pairs(ends: String) = IntArray(ends.length) { if (ends[it] == '.') -1 else ends[it] - 'a' }

    /** Every finished board's line set for [ends] on an [n] x [n] board, counted exactly (saturating at [Long.MAX_VALUE]). */
    fun count(n: Int, ends: String, must: IntArray? = null, not: IntArray? = null): Long {
        val pair = pairs(ends)
        // Frontier: downs[c] the piece label of a strand going down out of column c (0: none), left the strand coming in
        // from the left, and a colour per label (-1 none yet). Labels renumbered in order of first appearance.
        data class St(val downs: List<Int>, val left: Int, val colour: List<Int>)

        fun norm(downs: IntArray, left: Int, colour: Map<Int, Int>): St {
            val map = HashMap<Int, Int>()
            fun relabel(x: Int): Int = if (x == 0) 0 else map.getOrPut(x) { map.size + 1 }
            val d = downs.map { relabel(it) }
            val l = relabel(left)
            val cols = IntArray(map.size)
            for ((old, new) in map) cols[new - 1] = colour[old] ?: -1
            return St(d, l, cols.toList())
        }

        var states = HashMap<St, Long>()
        states[St(List(n) { 0 }, 0, emptyList())] = 1L
        for (q in 0 until n * n) {
            val r = q / n
            val c = q % n
            val next = HashMap<St, Long>()
            for ((st, ways) in states) {
                val left = if (c == 0) 0 else st.left
                if (c == 0 && st.left != 0) continue
                val up = st.downs[c]
                val cap = if (pair[q] >= 0) 1 else 2
                for (right in 0..(if (c < n - 1) 1 else 0)) for (down in 0..(if (r < n - 1) 1 else 0)) {
                    val incoming = (if (up != 0) 1 else 0) + (if (left != 0) 1 else 0)
                    if (incoming + right + down != cap) continue
                    // Lines the caller requires (bit 2 right, 4 down) or forbids.
                    if (must != null && ((must[q] and 2 != 0 && right == 0) || (must[q] and 4 != 0 && down == 0))) continue
                    if (not != null && ((not[q] and 2 != 0 && right == 1) || (not[q] and 4 != 0 && down == 1))) continue
                    val downs = st.downs.toIntArray()
                    val colour = HashMap<Int, Int>()
                    st.colour.forEachIndexed { i, col -> colour[i + 1] = col }
                    var fresh = (st.colour.size + 1)
                    fun openCount(label: Int, ds: IntArray, l: Int) = ds.count { it == label } + (if (l == label) 1 else 0)
                    var label: Int // the piece any outgoing strand carries
                    var ok = true
                    downs[c] = 0
                    val inLabels = listOf(up, left).filter { it != 0 }
                    if (pair[q] >= 0) {
                        val p = pair[q]
                        if (inLabels.size == 1) {
                            label = inLabels[0]
                            val col = colour[label] ?: -1
                            if (col != -1 && col != p) ok = false
                            if (col == -1) colour[label] = p
                            // A piece with an end at both ends must have nothing left open.
                            if (ok && col == p && openCount(label, downs, 0) > 0) ok = false
                        } else {
                            label = fresh++
                            colour[label] = p
                        }
                    } else {
                        when (inLabels.size) {
                            2 -> {
                                val x = inLabels[0]
                                val y = inLabels[1]
                                if (x == y) {
                                    ok = false
                                    label = x
                                } else {
                                    val cx = colour[x] ?: -1
                                    val cy = colour[y] ?: -1
                                    if (cx != -1 && cy != -1 && cx != cy) ok = false
                                    for (i in downs.indices) if (downs[i] == y) downs[i] = x
                                    colour[x] = if (cx != -1) cx else cy
                                    colour.remove(y)
                                    label = x
                                    if (cx != -1 && cy != -1 && openCount(x, downs, 0) > 0) ok = false
                                }
                            }
                            1 -> label = inLabels[0]
                            else -> {
                                label = fresh++
                                colour[label] = -1
                            }
                        }
                    }
                    if (!ok) continue
                    if (down == 1) downs[c] = label
                    val newLeft = if (right == 1) label else 0
                    // Drop labels no longer on the frontier.
                    val alive = downs.filter { it != 0 }.toMutableSet().apply { if (newLeft != 0) add(newLeft) }
                    val kept = colour.filterKeys { it in alive }
                    val key = norm(downs, newLeft, kept)
                    val sum = (next[key] ?: 0L) + ways
                    next[key] = if (sum < 0) Long.MAX_VALUE else sum
                }
            }
            states = next
        }
        return states.filterKeys { st -> st.downs.all { it == 0 } && st.left == 0 }.values.fold(0L) { a, b -> val s = a + b; if (s < 0) Long.MAX_VALUE else s }
    }

    /** Whether [masks] solve the board: lines agree across every side, each end has one, every other square two, and the walk from each end reaches its partner, the walks covering every square. */
    fun obeys(n: Int, ends: String, masks: IntArray): Boolean {
        val pair = pairs(ends)
        val dr = intArrayOf(-1, 0, 1, 0)
        val dc = intArrayOf(0, 1, 0, -1)
        fun at(q: Int, d: Int): Int {
            val r = q / n + dr[d]
            val c = q % n + dc[d]
            return if (r in 0 until n && c in 0 until n) r * n + c else -1
        }
        for (q in 0 until n * n) {
            var lines = 0
            for (d in 0 until 4) {
                if (masks[q] shr d and 1 == 0) continue
                val x = at(q, d)
                if (x < 0 || masks[x] shr ((d + 2) % 4) and 1 == 0) return false
                lines++
            }
            if (lines != (if (pair[q] >= 0) 1 else 2)) return false
        }
        val seen = BooleanArray(n * n)
        for (q in 0 until n * n) {
            if (pair[q] < 0 || seen[q]) continue
            var prev = -1
            var cur = q
            seen[cur] = true
            while (true) {
                val nxt = (0 until 4).filter { masks[cur] shr it and 1 == 1 }.map { at(cur, it) }.firstOrNull { it != prev } ?: break
                if (seen[nxt]) return false
                prev = cur
                cur = nxt
                seen[cur] = true
                if (pair[cur] >= 0) break
            }
            if (cur == q || pair[cur] != pair[q]) return false
        }
        return seen.all { it }
    }
}
