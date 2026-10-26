package com.joebywan.daybook

/**
 * An independent count of a Pearls board's answers, sharing nothing with `PearlsLogic`: no edge states, no
 * propagation, no rule deduced ahead of time. It decides every edge in reading order, square by square (each
 * square's edge to the right and edge down, every combination), and checks a rule only once everything it reads
 * is decided: a square's two lines (or none) when its own edges are in (and with them its own pearl's turn or
 * straight, and the straight a black pearl beside it asks of it), a pearl's whole rule once its neighbours' edges
 * are in too, and the loop's being one when an edge closes a loop (which must then hold every line drawn, with
 * none drawn after it). No budget: it walks until it has found [limit] loops or tried every assignment.
 *
 * Pearls are `b` / `w` / `.`, row by row. Returns the answers found, each as its squares in loop order.
 */
object PearlsOracle {

    fun answers(n: Int, pearls: String, limit: Int = 2): List<List<Int>> {
        val sq = n * n
        val right = IntArray(sq)
        val down = IntArray(sq)
        // For a path's end: the square at its other end, and how many edges the path has. A bare square is its own end.
        val end = IntArray(sq) { it }
        val len = IntArray(sq)
        val undo = ArrayList<Int>()
        var lines = 0
        var closed = false
        val out = ArrayList<List<Int>>()

        fun left(s: Int) = if (s % n > 0) right[s - 1] else 0
        fun up(s: Int) = if (s >= n) down[s - n] else 0
        fun degree(s: Int) = left(s) + up(s) + right[s] + down[s]

        fun set(i: Int, e: Int, l: Int) {
            undo.add(i); undo.add(end[i]); undo.add(len[i])
            end[i] = e
            len[i] = l
        }

        // Joins path ends a and b with a line; false when it closes a loop that is not every line drawn so far.
        fun join(a: Int, b: Int): Boolean {
            val ea = end[a]
            val eb = end[b]
            if (ea == b) {
                if (len[a] != lines) return false
                closed = true
                return true
            }
            val l = len[a] + len[b] + 1
            set(ea, eb, l)
            set(eb, ea, l)
            return true
        }

        // On a square with two lines: whether they meet at a right angle.
        fun turns(s: Int): Boolean = left(s) + right[s] == 1

        // Pearl p's rule, read from the decided edges around it.
        fun pearlFine(p: Int): Boolean {
            val nb = buildList {
                if (left(p) == 1) add(p - 1)
                if (right[p] == 1) add(p + 1)
                if (up(p) == 1) add(p - n)
                if (down[p] == 1) add(p + n)
            }
            if (nb.size != 2) return false
            return when (pearls[p]) {
                'b' -> turns(p) && nb.all { degree(it) == 2 && !turns(it) }
                else -> !turns(p) && nb.any { degree(it) == 2 && turns(it) }
            }
        }

        // A pearl is checked once its lower-right neighbour's square has been decided.
        val checksAt = Array(sq) { ArrayList<Int>() }
        for (p in 0 until sq) if (pearls[p] != '.') checksAt[minOf(p / n + 1, n - 1) * n + minOf(p % n + 1, n - 1)].add(p)

        fun trace(): List<Int> {
            val start = (0 until sq).first { degree(it) == 2 }
            val loop = arrayListOf(start)
            var prev = -1
            var at = start
            while (true) {
                val next = listOf(at - 1 to left(at), at + 1 to right[at], at - n to up(at), at + n to down[at])
                    .first { (q, on) -> on == 1 && q != prev }.first
                if (next == start) break
                check(loop.size < sq) { "not a loop" }
                loop.add(next)
                prev = at
                at = next
            }
            return loop
        }

        // Positions from which no loop was found, so a second way of arriving at one need not be walked again. What
        // the walk can still do from square k reads only how the ends of the paths reaching past k pair up, whether a
        // loop has closed, and the edges around each pearl whose whole rule is still to be checked; those are the key.
        val dead = HashSet<String>()
        val pending = (0 until sq).filter { pearls[it] != '.' }
        fun key(k: Int): String {
            val b = StringBuilder()
            b.append(k).append(if (closed) 'c' else 'o')
            for (j in k until minOf(sq, k + n)) {
                val inbound = left(j) + up(j)
                b.append(inbound)
                if (inbound == 1) b.append('>').append(end[j] - j).append(',')
            }
            for (p in pending) {
                val ready = minOf(p / n + 1, n - 1) * n + minOf(p % n + 1, n - 1)
                if (ready < k) continue
                b.append('p').append(p)
                for (q in listOf(p, p - 1, p + 1, p - n, p + n)) for (r in listOf(q, q - 1, q - n)) {
                    if (r in 0 until k) b.append(right[r] * 2 + down[r])
                }
            }
            return b.toString()
        }

        fun walk(k: Int) {
            if (out.size >= limit) return
            if (k == sq) {
                if (closed) out.add(trace())
                return
            }
            val here = key(k)
            if (here in dead) return
            val before = out.size
            val maxRight = if (k % n < n - 1) 1 else 0
            val maxDown = if (k / n < n - 1) 1 else 0
            for (rv in 0..maxRight) for (dv in 0..maxDown) {
                val d = left(k) + up(k) + rv + dv
                if (d != 0 && d != 2) continue
                if (pearls[k] != '.' && d != 2) continue
                if (closed && rv + dv > 0) continue
                val mark = undo.size
                val wasClosed = closed
                val wasLines = lines
                right[k] = rv
                down[k] = dv
                var ok = true
                if (rv == 1) { ok = join(k, k + 1); lines++ }
                if (ok && dv == 1) {
                    if (closed) ok = false else { ok = join(k, k + n); lines++ }
                }
                // What square k's own edges settle at once: its own pearl's turn, and a black neighbour's straight arm.
                if (ok && d == 2) {
                    if (pearls[k] == 'b' && !turns(k) || pearls[k] == 'w' && turns(k)) ok = false
                    if (left(k) == 1 && pearls[k - 1] == 'b' || up(k) == 1 && pearls[k - n] == 'b' ||
                        rv == 1 && pearls[k + 1] == 'b' || dv == 1 && pearls[k + n] == 'b'
                    ) {
                        if (turns(k)) ok = false
                    }
                }
                if (ok) for (p in checksAt[k]) if (!pearlFine(p)) { ok = false; break }
                if (ok) walk(k + 1)
                while (undo.size > mark) {
                    val l = undo.removeAt(undo.size - 1)
                    val e = undo.removeAt(undo.size - 1)
                    val i = undo.removeAt(undo.size - 1)
                    end[i] = e
                    len[i] = l
                }
                closed = wasClosed
                lines = wasLines
                right[k] = 0
                down[k] = 0
                if (out.size >= limit) return
            }
            if (out.size == before) dead.add(here)
        }

        walk(0)
        return out
    }
}
