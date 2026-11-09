package com.joebywan.daybook

import com.joebywan.daybook.puzzles.UnblockCar

/**
 * An independent count of an Unblock board's fewest moves, sharing nothing with `UnblockLogic`: the yard is a 36-character
 * picture (a letter per car, '.' for empty), a car is found by scanning for its letter, and it moves one square at a time.
 * A move is a run of steps by one car in one direction, so the search is 0-1 breadth first over (picture, last car, last
 * direction): carrying on the same way is free, anything else costs a move. The generator counts whole slides over packed
 * positions; this counts steps over text. No budget: it runs until the queue is empty.
 */
object UnblockOracle {
    private const val N = 6

    fun picture(cars: List<UnblockCar>): String {
        val g = CharArray(N * N) { '.' }
        for ((i, c) in cars.withIndex()) {
            for (k in 0 until c.length) {
                val r = if (c.across) c.row else c.row + k
                val col = if (c.across) c.col + k else c.col
                check(g[r * N + col] == '.') { "cars overlap" }
                g[r * N + col] = 'a' + i
            }
        }
        return String(g)
    }

    /** The picture with car [ch] stepped one square in [dir] (0 left, 1 right, 2 up, 3 down), or null when it cannot go. */
    private fun step(p: String, ch: Char, dir: Int): String? {
        val squares = p.indices.filter { p[it] == ch }
        val first = squares.first()
        val last = squares.last()
        val across = squares.size > 1 && squares[1] == first + 1
        if (across && dir >= 2 || !across && dir < 2) return null
        val (dr, dc) = when (dir) { 0 -> 0 to -1; 1 -> 0 to 1; 2 -> -1 to 0; else -> 1 to 0 }
        val lead = if (dir == 0 || dir == 2) first else last
        val tail = if (dir == 0 || dir == 2) last else first
        val r = lead / N + dr
        val c = lead % N + dc
        if (r !in 0 until N || c !in 0 until N || p[r * N + c] != '.') return null
        val g = p.toCharArray()
        g[r * N + c] = ch
        g[tail] = '.'
        return String(g)
    }

    private fun out(p: String): Boolean = p[2 * N + N - 1] == 'a'

    /** The fewest moves to get car 0 (the red car) to the right edge of its row, or -1 if it never can. */
    fun fewest(cars: List<UnblockCar>): Int {
        val start = picture(cars)
        if (out(start)) return 0
        val letters = start.filter { it != '.' }.toSet().sorted()
        // State: picture + the car and direction of the move under way ('-' for none).
        val dist = HashMap<String, Int>()
        val deque = ArrayDeque<String>()
        val s0 = "$start-0"
        dist[s0] = 0
        deque.add(s0)
        while (deque.isNotEmpty()) {
            val s = deque.removeFirst()
            val d = dist.getValue(s)
            val p = s.substring(0, N * N)
            if (out(p)) return d
            val lastCar = s[N * N]
            val lastDir = s[N * N + 1] - '0'
            for (ch in letters) for (dir in 0 until 4) {
                val q = step(p, ch, dir) ?: continue
                val free = ch == lastCar && dir == lastDir
                val nd = if (free) d else d + 1
                val key = "$q$ch$dir"
                val old = dist[key]
                if (old != null && old <= nd) continue
                dist[key] = nd
                if (free) deque.addFirst(key) else deque.addLast(key)
            }
        }
        return -1
    }
}
