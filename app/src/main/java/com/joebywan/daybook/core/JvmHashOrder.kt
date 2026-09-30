package com.joebywan.daybook.core

/**
 * The order a `java.util.HashSet` built by adding [insertions] one by one would iterate in, worked
 * out by hand so every platform gets it.
 *
 * Why this exists: a generator that picked from a hash set's iteration order made its boards depend
 * on the platform's hash-table internals. The JVM (and Android's ART, which ships the same
 * `java.util.HashMap`) walks buckets in index order; Kotlin/Wasm walks in insertion order. Changing
 * the generator to any *new* explicit order would have changed every Android board, so instead
 * this replays what `HashMap.putVal` does — the table doubling at three quarters full, the early
 * doubling when a bucket reaches nine entries on a table under 64, the red-black "tree bins" a
 * bucket turns into on larger tables, `moveRootToFront`, and `split` on resize — and reads the
 * table back the way `HashIterator` does. `JvmHashOrderTest` diffs it against the real `HashSet`.
 *
 * [hashOf] must be the key's JVM `hashCode()`. Keys are compared with `==`.
 *
 * One JVM behaviour cannot be reproduced: two *different* keys with the *same* hash in one tree bin
 * are ordered by `System.identityHashCode`, which is not even stable from run to run on the JVM.
 * Such a pair is placed to the right here. Callers must make sure it cannot arise; LITS does, see
 * `JvmHashOrderTest`.
 */
internal fun <T> jvmHashSetOrder(insertions: Iterable<T>, hashOf: (T) -> Int): List<T> {
    val map = JvmHashTable<T>()
    for (key in insertions) map.add(key, hashOf(key))
    return map.keys()
}

private class JvmHashTable<T> {

    private class Node<T>(val hash: Int, val key: T, var next: Node<T>?) {
        var tree = false
        var parent: Node<T>? = null
        var left: Node<T>? = null
        var right: Node<T>? = null
        var prev: Node<T>? = null
        var red = false
    }

    private var table: Array<Node<T>?> = arrayOfNulls(0)
    private var size = 0
    private var threshold = 0

    fun keys(): List<T> {
        val out = ArrayList<T>(size)
        for (head in table) {
            var e = head
            while (e != null) {
                out += e.key
                e = e.next
            }
        }
        return out
    }

    fun add(key: T, rawHash: Int) {
        val h = rawHash xor (rawHash ushr 16)
        if (table.isEmpty()) resize()
        val tab = table
        val i = (tab.size - 1) and h
        val first = tab[i]
        if (first == null) {
            tab[i] = Node(h, key, null)
        } else if (first.hash == h && first.key == key) {
            return
        } else if (first.tree) {
            if (!putTreeVal(tab, first, h, key)) return
        } else {
            var p: Node<T> = first
            var binCount = 0
            while (true) {
                val e = p.next
                if (e == null) {
                    p.next = Node(h, key, null)
                    if (binCount >= TREEIFY_THRESHOLD - 1) treeifyBin(h)
                    break
                }
                if (e.hash == h && e.key == key) return
                p = e
                binCount++
            }
        }
        if (++size > threshold) resize()
    }

    private fun resize() {
        val oldTab = table
        val oldCap = oldTab.size
        val newCap: Int
        if (oldCap == 0) {
            newCap = DEFAULT_CAPACITY
            threshold = (DEFAULT_CAPACITY * 3) / 4
        } else {
            newCap = oldCap shl 1
            threshold = threshold shl 1
        }
        val newTab = arrayOfNulls<Node<T>>(newCap)
        table = newTab
        for (j in 0 until oldCap) {
            val e = oldTab[j] ?: continue
            oldTab[j] = null
            if (e.next == null) {
                newTab[e.hash and (newCap - 1)] = e
            } else if (e.tree) {
                split(newTab, e, j, oldCap)
            } else {
                var loHead: Node<T>? = null
                var loTail: Node<T>? = null
                var hiHead: Node<T>? = null
                var hiTail: Node<T>? = null
                var p: Node<T>? = e
                while (p != null) {
                    val next = p.next
                    if (p.hash and oldCap == 0) {
                        if (loTail == null) loHead = p else loTail.next = p
                        loTail = p
                    } else {
                        if (hiTail == null) hiHead = p else hiTail.next = p
                        hiTail = p
                    }
                    p = next
                }
                if (loTail != null) {
                    loTail.next = null
                    newTab[j] = loHead
                }
                if (hiTail != null) {
                    hiTail.next = null
                    newTab[j + oldCap] = hiHead
                }
            }
        }
    }

    private fun treeifyBin(hash: Int) {
        val tab = table
        if (tab.size < MIN_TREEIFY_CAPACITY) {
            resize()
            return
        }
        val index = (tab.size - 1) and hash
        val head = tab[index] ?: return
        // The bin keeps its list order; the nodes only gain tree links and a prev pointer.
        var tl: Node<T>? = null
        var e: Node<T>? = head
        while (e != null) {
            e.tree = true
            e.prev = tl
            tl = e
            e = e.next
        }
        treeify(tab, head)
    }

    private fun treeify(tab: Array<Node<T>?>, head: Node<T>) {
        var root: Node<T>? = null
        var x: Node<T>? = head
        while (x != null) {
            val next = x.next
            x.left = null
            x.right = null
            if (root == null) {
                x.parent = null
                x.red = false
                root = x
            } else {
                var p: Node<T> = root
                while (true) {
                    val dir = if (p.hash > x.hash) -1 else 1
                    val xp = p
                    val child = if (dir <= 0) p.left else p.right
                    if (child == null) {
                        x.parent = xp
                        if (dir <= 0) xp.left = x else xp.right = x
                        root = balanceInsertion(root!!, x)
                        break
                    }
                    p = child
                }
            }
            x = next
        }
        moveRootToFront(tab, root!!)
    }

    /** False when the key was already there. */
    private fun putTreeVal(tab: Array<Node<T>?>, first: Node<T>, h: Int, key: T): Boolean {
        var root = first
        while (root.parent != null) root = root.parent!!
        var p: Node<T> = root
        while (true) {
            val dir = when {
                p.hash > h -> -1
                p.hash < h -> 1
                p.key == key -> return false
                // Equal hashes, different keys: the JVM would consult identityHashCode here. See
                // the note on [jvmHashSetOrder]; this branch must be unreachable for real callers.
                else -> findInSubtrees(p, h, key)?.let { return false } ?: 1
            }
            val xp = p
            val child = if (dir <= 0) p.left else p.right
            if (child == null) {
                val xpn = xp.next
                val x = Node(h, key, xpn)
                x.tree = true
                if (dir <= 0) xp.left = x else xp.right = x
                xp.next = x
                x.parent = xp
                x.prev = xp
                if (xpn != null) xpn.prev = x
                moveRootToFront(tab, balanceInsertion(root, x))
                return true
            }
            p = child
        }
    }

    private fun findInSubtrees(p: Node<T>, h: Int, key: T): Node<T>? {
        fun find(n: Node<T>?): Node<T>? {
            if (n == null) return null
            if (n.hash == h && n.key == key) return n
            return find(n.left) ?: find(n.right)
        }
        return find(p.left) ?: find(p.right)
    }

    private fun moveRootToFront(tab: Array<Node<T>?>, root: Node<T>) {
        val index = (tab.size - 1) and root.hash
        val first = tab[index]
        if (root !== first) {
            tab[index] = root
            val rp = root.prev
            val rn = root.next
            if (rn != null) rn.prev = rp
            if (rp != null) rp.next = rn
            if (first != null) first.prev = root
            root.next = first
            root.prev = null
        }
    }

    private fun split(tab: Array<Node<T>?>, b: Node<T>, index: Int, bit: Int) {
        var loHead: Node<T>? = null
        var loTail: Node<T>? = null
        var hiHead: Node<T>? = null
        var hiTail: Node<T>? = null
        var lc = 0
        var hc = 0
        var e: Node<T>? = b
        while (e != null) {
            val next = e.next
            e.next = null
            if (e.hash and bit == 0) {
                e.prev = loTail
                if (loTail == null) loHead = e else loTail.next = e
                loTail = e
                lc++
            } else {
                e.prev = hiTail
                if (hiTail == null) hiHead = e else hiTail.next = e
                hiTail = e
                hc++
            }
            e = next
        }
        if (loHead != null) {
            if (lc <= UNTREEIFY_THRESHOLD) {
                tab[index] = untreeify(loHead)
            } else {
                tab[index] = loHead
                if (hiHead != null) treeify(tab, loHead)
            }
        }
        if (hiHead != null) {
            if (hc <= UNTREEIFY_THRESHOLD) {
                tab[index + bit] = untreeify(hiHead)
            } else {
                tab[index + bit] = hiHead
                if (loHead != null) treeify(tab, hiHead)
            }
        }
    }

    /** A plain list again, in the same order, as `TreeNode.untreeify` rebuilds it. */
    private fun untreeify(head: Node<T>): Node<T> {
        var e: Node<T>? = head
        while (e != null) {
            e.tree = false
            e.parent = null
            e.left = null
            e.right = null
            e.prev = null
            e.red = false
            e = e.next
        }
        return head
    }

    private fun rotateLeft(root0: Node<T>, p: Node<T>): Node<T> {
        var root = root0
        val r = p.right ?: return root
        val rl = r.left
        p.right = rl
        if (rl != null) rl.parent = p
        val pp = p.parent
        r.parent = pp
        if (pp == null) {
            root = r
            r.red = false
        } else if (pp.left === p) {
            pp.left = r
        } else {
            pp.right = r
        }
        r.left = p
        p.parent = r
        return root
    }

    private fun rotateRight(root0: Node<T>, p: Node<T>): Node<T> {
        var root = root0
        val l = p.left ?: return root
        val lr = l.right
        p.left = lr
        if (lr != null) lr.parent = p
        val pp = p.parent
        l.parent = pp
        if (pp == null) {
            root = l
            l.red = false
        } else if (pp.right === p) {
            pp.right = l
        } else {
            pp.left = l
        }
        l.right = p
        p.parent = l
        return root
    }

    private fun balanceInsertion(root0: Node<T>, x0: Node<T>): Node<T> {
        var root = root0
        var x = x0
        x.red = true
        while (true) {
            var xp = x.parent
            if (xp == null) {
                x.red = false
                return x
            }
            var xpp = xp.parent
            if (!xp.red || xpp == null) return root
            val xppl = xpp.left
            if (xp === xppl) {
                val xppr = xpp.right
                if (xppr != null && xppr.red) {
                    xppr.red = false
                    xp.red = false
                    xpp.red = true
                    x = xpp
                } else {
                    if (x === xp.right) {
                        x = xp
                        root = rotateLeft(root, x)
                        xp = x.parent
                        xpp = xp?.parent
                    }
                    if (xp != null) {
                        xp.red = false
                        if (xpp != null) {
                            xpp.red = true
                            root = rotateRight(root, xpp)
                        }
                    }
                }
            } else {
                if (xppl != null && xppl.red) {
                    xppl.red = false
                    xp.red = false
                    xpp.red = true
                    x = xpp
                } else {
                    if (x === xp.left) {
                        x = xp
                        root = rotateRight(root, x)
                        xp = x.parent
                        xpp = xp?.parent
                    }
                    if (xp != null) {
                        xp.red = false
                        if (xpp != null) {
                            xpp.red = true
                            root = rotateLeft(root, xpp)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
        const val TREEIFY_THRESHOLD = 8
        const val UNTREEIFY_THRESHOLD = 6
        const val MIN_TREEIFY_CAPACITY = 64
    }
}
