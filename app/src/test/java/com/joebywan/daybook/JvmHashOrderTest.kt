package com.joebywan.daybook

import com.joebywan.daybook.core.jvmHashSetOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [jvmHashSetOrder] replays `java.util.HashMap` by hand so the web build can reproduce the order a
 * JVM `HashSet` iterates in. Here the real `HashSet` is the oracle.
 *
 * The random cases are built to reach every path the replay has: plain buckets, the early doubling
 * a nine-deep bucket forces on a small table, tree bins, and tree bins split by a later resize.
 */
class JvmHashOrderTest {

    @Test
    fun `matches java util HashSet on sets of tetromino-shaped lists`() {
        // The shape of the real caller: every connected four-square set holding one square, over
        // randomly blocked boards of the sizes LITS generates.
        val random = Random(20260930)
        var paths = 0
        repeat(60_000) {
            val side = 6 + random.nextInt(3)
            val blocked = random.nextDouble() * 0.7
            val free = BooleanArray(side * side) { random.nextDouble() >= blocked }
            val cell = random.nextInt(side * side)
            if (!free[cell]) return@repeat
            val found = quadsContaining(cell, side, free)
            check(found)
            paths++
        }
        assertTrue(paths > 30_000)
    }

    @Test
    fun `matches java util HashSet under heavy collisions`() {
        // Hashes below 2^16 pass HashMap's spreading unchanged, so the low bits pick the bucket
        // directly: four buckets take everything, tree bins form, rebalance and are split when the
        // table doubles (bit 6 varies), and a small table doubles early for a nine-deep bucket.
        val random = Random(7)
        repeat(20_000) {
            val count = 1 + random.nextInt(120)
            val keys = List(count) { Key(random.nextInt(4) + 64 * random.nextInt(1024)) }
            val real = HashSet<Key>()
            keys.forEach { real += it }
            assertEquals(real.toList(), jvmHashSetOrder(keys) { it.hashCode() })
        }
    }

    /** Distinct keys never share a hash, as in the real caller. */
    private data class Key(val hash: Int) {
        override fun hashCode() = hash
    }

    /**
     * The one JVM behaviour the replay cannot copy is the order of two different keys with the
     * same hash in a tree bin. No two tetromino-shaped lists holding the same square share a hash
     * on any board LITS uses, so it cannot arise there.
     */
    @Test
    fun `no two LITS candidate quads share a hash`() {
        for (side in 6..8) {
            for (cell in 0 until side * side) {
                val quads = quadsContaining(cell, side, BooleanArray(side * side) { true }).distinct()
                assertEquals(quads.size, quads.map { it.hashCode() }.toSet().size)
            }
        }
    }

    private fun check(insertions: List<List<Int>>) {
        val real = HashSet<List<Int>>()
        insertions.forEach { real += it }
        assertEquals(real.toList(), jvmHashSetOrder(insertions) { it.hashCode() })
    }

    /** Connected four-square sets containing [cell], sorted, in discovery order with repeats. */
    private fun quadsContaining(cell: Int, side: Int, free: BooleanArray): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        fun neighbours(c: Int) = listOfNotNull(
            (c - side).takeIf { c / side > 0 },
            (c + side).takeIf { c / side < side - 1 },
            (c - 1).takeIf { c % side > 0 },
            (c + 1).takeIf { c % side < side - 1 },
        )
        fun grow(current: List<Int>) {
            if (current.size == 4) {
                out += current
                return
            }
            for (c in current) for (next in neighbours(c)) {
                if (next in current || !free[next]) continue
                grow((current + next).sorted())
            }
        }
        grow(listOf(cell))
        return out
    }
}
