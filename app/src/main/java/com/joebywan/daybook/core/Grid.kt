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
