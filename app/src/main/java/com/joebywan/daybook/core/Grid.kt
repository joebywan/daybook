package com.joebywan.daybook.core

// Neighbours on a board of squares numbered in reading order. Generators hand these to the Rng, so each
// order is part of the boards it made: never reorder one, add a function instead.

/** The squares beside [i] on a [w] x [h] board: up, down, left, right. */
fun orthogonal(i: Int, w: Int, h: Int = w): List<Int> = buildList {
    val r = i / w
    val c = i % w
    if (r > 0) add(i - w)
    if (r < h - 1) add(i + w)
    if (c > 0) add(i - 1)
    if (c < w - 1) add(i + 1)
}

/** The squares beside [i] on a [w] x [h] board in reading order: up, left, right, down. */
fun orthogonalReading(i: Int, w: Int, h: Int = w): List<Int> = buildList {
    val r = i / w
    val c = i % w
    if (r > 0) add(i - w)
    if (c > 0) add(i - 1)
    if (c < w - 1) add(i + 1)
    if (r < h - 1) add(i + w)
}

/** The up to eight squares around [i] on a [w] x [h] board, diagonals included, in reading order. */
fun neighbours8(w: Int, h: Int, i: Int): IntArray {
    val r = i / w
    val c = i % w
    val out = IntArray(8)
    var k = 0
    for (dr in -1..1) for (dc in -1..1) {
        if (dr == 0 && dc == 0) continue
        val nr = r + dr
        val nc = c + dc
        if (nr in 0 until h && nc in 0 until w) out[k++] = nr * w + nc
    }
    return out.copyOf(k)
}

/**
 * Line [line] of a [width] x [height] board as squares in reading order: rows first (0 until height), then columns
 * (height until height + width). Nonogram's, Tally's and Thermometers' lines.
 */
fun lineSquares(width: Int, height: Int, line: Int): List<Int> =
    if (line < height) List(width) { line * width + it } else List(height) { it * width + line - height }

/** Row step of each direction a walk takes: up, right, down, left. */
val WALK_DR = intArrayOf(-1, 0, 1, 0)

/** Column step of each direction a walk takes: up, right, down, left. */
val WALK_DC = intArrayOf(0, 1, 0, -1)

/**
 * Grows [walk] (one square or more; its last step went [dir], an index into [WALK_DR], or -1 when it has none yet)
 * a square at a time to [length] squares on a [w] x [h] board: never back the way it came, never onto itself or a
 * square [open] refuses. Ahead is kept with [straight] percent chance when ahead is open (drawn only then), else a
 * direction is drawn from the open ones in up, right, down, left order. Stops short where nothing is open.
 * Pointers' arrow bodies and Thermometers' tubes; the order of the draws is part of their boards.
 */
fun bentWalk(rng: Rng, walk: MutableList<Int>, dir: Int, length: Int, w: Int, h: Int, straight: Int, open: (Int) -> Boolean) {
    var wd = dir
    while (walk.size < length) {
        val cur = walk.last()
        val options = ArrayList<Int>(4)
        for (d in 0 until 4) {
            if (wd >= 0 && d == (wd + 2) % 4) continue
            val r = cur / w + WALK_DR[d]
            val c = cur % w + WALK_DC[d]
            if (r !in 0 until h || c !in 0 until w) continue
            val cell = r * w + c
            if (!open(cell) || cell in walk) continue
            options += d
        }
        if (options.isEmpty()) break
        val next = if (wd in options && rng.nextInt(100) < straight) wd else rng.pick(options)
        wd = next
        walk += (cur / w + WALK_DR[next]) * w + cur % w + WALK_DC[next]
    }
}
