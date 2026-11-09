package com.joebywan.daybook.core

/**
 * A drag's trail of squares on a [w] x [h] board after the finger reaches [cell]: added when it is a side-neighbour of
 * the last square, taken back when it is the square before that (backing up along the drag undoes it), else unchanged.
 * Shared by the boards that draw lines through squares (Pearls, Links).
 */
fun dragTrail(path: List<Int>, cell: Int, w: Int, h: Int = w): List<Int> = when {
    path.isEmpty() -> listOf(cell)
    cell == path.last() -> path
    path.size >= 2 && cell == path[path.size - 2] -> path.dropLast(1)
    sideBySide(path.last(), cell, w, h) -> path + cell
    else -> path
}

/** Whether squares [a] and [b] of a [w] x [h] board share a side. */
fun sideBySide(a: Int, b: Int, w: Int, h: Int = w): Boolean {
    if (a !in 0 until w * h || b !in 0 until w * h) return false
    val dr = a / w - b / w
    val dc = a % w - b % w
    return dr * dr + dc * dc == 1
}
