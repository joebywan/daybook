package com.joebywan.daybook.puzzles

import kotlin.math.abs

// Chess rules and a mate-in-N prover for the Mate puzzle. No android.* / java.*, and no hash container is
// ever iterated (squares and moves are walked in index order), so the JVM and the browser agree move for move.
//
// Squares 0..63, a1 = 0, rank * 8 + file. Pieces are signed ints (kinds 1..6 = P N B R Q K, positive white).
// A move is `from | to << 6 | promo << 12` (promo 0 none, else kind 2..5 = N B R Q). Castling is the king's
// two-square move; en passant is a pawn move to the ep square.

/** An immutable position: board, side to move, castling rights (1 K, 2 Q, 4 k, 8 q) and en passant target (-1 none). */
class ChessPosition internal constructor(
    internal val sq: IntArray,
    val whiteToMove: Boolean,
    internal val castling: Int,
    internal val ep: Int,
) {
    fun pieceAt(sq: Int): Int = this.sq[sq]

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val v = sq[r * 8 + f]
                if (v == 0) { empty++; continue }
                if (empty > 0) { sb.append(empty); empty = 0 }
                val c = LETTERS[abs(v)]
                sb.append(if (v > 0) c else c.lowercaseChar())
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        if (castling == 0) sb.append('-') else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ')
        if (ep < 0) sb.append('-') else sb.append(('a' + ep % 8)).append(('1' + ep / 8))
        return sb.toString()
    }

    companion object {
        internal const val LETTERS = " PNBRQK"

        /** Parses the first four FEN fields (clocks, if present, are ignored). Throws on anything malformed. */
        fun fromFen(fen: String): ChessPosition {
            val parts = fen.trim().split(' ').filter { it.isNotEmpty() }
            require(parts.size >= 2) { "bad FEN: $fen" }
            val board = IntArray(64)
            val rows = parts[0].split('/')
            require(rows.size == 8) { "bad FEN placement: $fen" }
            for ((i, row) in rows.withIndex()) {
                val r = 7 - i
                var f = 0
                for (ch in row) {
                    if (ch in '1'..'8') { f += ch - '0'; continue }
                    val k = LETTERS.indexOf(ch.uppercaseChar())
                    require(k >= 1 && f < 8) { "bad FEN piece '$ch': $fen" }
                    board[r * 8 + f] = if (ch.isUpperCase()) k else -k
                    f++
                }
                require(f == 8) { "bad FEN rank: $fen" }
            }
            require(parts[1] == "w" || parts[1] == "b") { "bad FEN side: $fen" }
            var castling = 0
            val cs = parts.getOrElse(2) { "-" }
            for (ch in cs) when (ch) {
                'K' -> castling = castling or 1
                'Q' -> castling = castling or 2
                'k' -> castling = castling or 4
                'q' -> castling = castling or 8
                '-' -> {}
                else -> throw IllegalArgumentException("bad FEN castling: $fen")
            }
            val e = parts.getOrElse(3) { "-" }
            val ep = if (e == "-") -1 else {
                require(e.length == 2 && e[0] in 'a'..'h' && e[1] in '1'..'8') { "bad FEN ep: $fen" }
                (e[1] - '1') * 8 + (e[0] - 'a')
            }
            return ChessPosition(board, parts[1] == "w", castling, ep)
        }
    }
}

internal object ChessRules {
    private val KNIGHT = intArrayOf(1, 2, 2, 1, 2, -1, 1, -2, -1, -2, -2, -1, -2, 1, -1, 2) // (df, dr) pairs
    private val KING = intArrayOf(1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -1, 1, -1)
    private val DIAG = intArrayOf(1, 1, -1, 1, -1, -1, 1, -1)
    private val ORTHO = intArrayOf(1, 0, 0, 1, -1, 0, 0, -1)
    private val BOTH = arrayOf(DIAG, ORTHO)
    private val PAWN_DF = intArrayOf(-1, 1)

    private fun from(m: Int) = m and 63
    private fun to(m: Int) = (m shr 6) and 63
    private fun promo(m: Int) = m shr 12
    private fun mv(f: Int, t: Int, pr: Int = 0) = f or (t shl 6) or (pr shl 12)

    // ---- attacks -------------------------------------------------------------------------------------------

    private fun isAttacked(b: IntArray, s: Int, byWhite: Boolean): Boolean {
        val f = s % 8
        val r = s / 8
        val sg = if (byWhite) 1 else -1
        // pawns: a white pawn attacks upward, so it sits one rank below the target
        val pr = if (byWhite) r - 1 else r + 1
        if (pr in 0..7) {
            if (f > 0 && b[pr * 8 + f - 1] == sg) return true
            if (f < 7 && b[pr * 8 + f + 1] == sg) return true
        }
        var i = 0
        while (i < 16) {
            val nf = f + KNIGHT[i]; val nr = r + KNIGHT[i + 1]
            if (nf in 0..7 && nr in 0..7 && b[nr * 8 + nf] == 2 * sg) return true
            val kf = f + KING[i]; val kr = r + KING[i + 1]
            if (kf in 0..7 && kr in 0..7 && b[kr * 8 + kf] == 6 * sg) return true
            i += 2
        }
        return slides(b, f, r, DIAG, 3 * sg, 5 * sg) || slides(b, f, r, ORTHO, 4 * sg, 5 * sg)
    }

    private fun slides(b: IntArray, f: Int, r: Int, dirs: IntArray, a: Int, c: Int): Boolean {
        var i = 0
        while (i < dirs.size) {
            var nf = f + dirs[i]; var nr = r + dirs[i + 1]
            while (nf in 0..7 && nr in 0..7) {
                val v = b[nr * 8 + nf]
                if (v != 0) { if (v == a || v == c) return true; break }
                nf += dirs[i]; nr += dirs[i + 1]
            }
            i += 2
        }
        return false
    }

    fun attackers(p: ChessPosition, sq: Int, byWhite: Boolean): List<Int> {
        val out = ArrayList<Int>()
        val b = p.sq
        val sg = if (byWhite) 1 else -1
        val f = sq % 8
        val r = sq / 8
        for (s in 0..63) {
            val v = b[s] * sg
            if (v <= 0) continue
            val df = f - s % 8
            val dr = r - s / 8
            val adf = if (df < 0) -df else df
            val adr = if (dr < 0) -dr else dr
            val hit = when (v) {
                1 -> adf == 1 && dr == sg
                2 -> adf * adr == 2
                6 -> adf <= 1 && adr <= 1 && adf + adr > 0
                3, 4, 5 -> {
                    val diag = adf == adr && adf > 0
                    val ortho = (adf == 0) != (adr == 0)
                    if (!(diag && v != 4) && !(ortho && v != 3)) false else {
                        val sf = if (df > 0) 1 else if (df < 0) -1 else 0
                        val sr = if (dr > 0) 1 else if (dr < 0) -1 else 0
                        var cf = s % 8 + sf; var cr = s / 8 + sr
                        var clear = true
                        while (cf != f || cr != r) {
                            if (b[cr * 8 + cf] != 0) { clear = false; break }
                            cf += sf; cr += sr
                        }
                        clear
                    }
                }
                else -> false
            }
            if (hit) out.add(s)
        }
        return out
    }

    private fun kingSq(b: IntArray, white: Boolean): Int {
        val k = if (white) 6 else -6
        for (s in 0..63) if (b[s] == k) return s
        return -1
    }

    fun inCheck(p: ChessPosition): Boolean = checked(p.sq, p.whiteToMove)

    private fun checked(b: IntArray, white: Boolean): Boolean {
        val k = kingSq(b, white)
        return k >= 0 && isAttacked(b, k, !white)
    }

    // ---- move generation -----------------------------------------------------------------------------------

    private fun sortKey(m: Int): Int {
        val pr = promo(m)
        return to(m) * 8 + (if (pr == 0) 0 else 6 - pr) // promo order Q R B N
    }

    /** Pseudo-legal moves of the piece on [s] appended to [out] from index [n]; returns the new count, sorted. */
    private fun genPiece(b: IntArray, white: Boolean, castling: Int, ep: Int, s: Int, out: IntArray, n0: Int, sorted: Boolean = true): Int {
        val sg = if (white) 1 else -1
        val v = b[s] * sg
        val f = s % 8
        val r = s / 8
        var n = n0
        when (v) {
            1 -> {
                val nr = r + sg
                val last = nr == 0 || nr == 7
                if (b[nr * 8 + f] == 0) {
                    if (last) for (pr in 5 downTo 2) out[n++] = mv(s, nr * 8 + f, pr)
                    else {
                        out[n++] = mv(s, nr * 8 + f)
                        if (r == (if (white) 1 else 6) && b[(nr + sg) * 8 + f] == 0) out[n++] = mv(s, (nr + sg) * 8 + f)
                    }
                }
                for (d in PAWN_DF) {
                    val nf = f + d
                    if (nf !in 0..7) continue
                    val t = nr * 8 + nf
                    if (b[t] * sg < 0 || t == ep) {
                        if (last) for (pr in 5 downTo 2) out[n++] = mv(s, t, pr) else out[n++] = mv(s, t)
                    }
                }
            }
            2, 6 -> {
                val d = if (v == 2) KNIGHT else KING
                var i = 0
                while (i < 16) {
                    val nf = f + d[i]; val nr = r + d[i + 1]
                    if (nf in 0..7 && nr in 0..7 && b[nr * 8 + nf] * sg <= 0) out[n++] = mv(s, nr * 8 + nf)
                    i += 2
                }
                if (v == 6) n = castles(b, white, castling, s, out, n)
            }
            else -> {
                for (dirs in BOTH) {
                    if (v == 3 && dirs === ORTHO || v == 4 && dirs === DIAG) continue
                    var i = 0
                    while (i < 8) {
                        var nf = f + dirs[i]; var nr = r + dirs[i + 1]
                        while (nf in 0..7 && nr in 0..7) {
                            val o = b[nr * 8 + nf] * sg
                            if (o <= 0) out[n++] = mv(s, nr * 8 + nf)
                            if (o != 0) break
                            nf += dirs[i]; nr += dirs[i + 1]
                        }
                        i += 2
                    }
                }
            }
        }
        // insertion sort of this piece's moves by (to, promo order)
        if (sorted) for (i in n0 + 1 until n) {
            val m = out[i]; val k = sortKey(m)
            var j = i - 1
            while (j >= n0 && sortKey(out[j]) > k) { out[j + 1] = out[j]; j-- }
            out[j + 1] = m
        }
        return n
    }

    private fun castles(b: IntArray, white: Boolean, castling: Int, s: Int, out: IntArray, n0: Int): Int {
        val base = if (white) 0 else 56
        if (s != base + 4) return n0
        val sg = if (white) 1 else -1
        var n = n0
        val kr = if (white) 1 else 4
        val qr = if (white) 2 else 8
        val rook = 4 * sg
        if (castling and (kr or qr) == 0 || isAttacked(b, s, !white)) return n
        if (castling and kr != 0 && b[base + 7] == rook && b[base + 5] == 0 && b[base + 6] == 0 &&
            !isAttacked(b, base + 5, !white) && !isAttacked(b, base + 6, !white)
        ) out[n++] = mv(s, base + 6)
        if (castling and qr != 0 && b[base] == rook && b[base + 1] == 0 && b[base + 2] == 0 && b[base + 3] == 0 &&
            !isAttacked(b, base + 3, !white) && !isAttacked(b, base + 2, !white)
        ) out[n++] = mv(s, base + 2)
        return n
    }

    private fun legalArray(p: ChessPosition): IntArray {
        val buf = IntArray(256)
        val b = p.sq
        val sg = if (p.whiteToMove) 1 else -1
        var n = 0
        for (s in 0..63) if (b[s] * sg > 0) n = genPiece(b, p.whiteToMove, p.castling, p.ep, s, buf, n)
        var k = 0
        for (i in 0 until n) {
            val child = apply(p, buf[i])
            if (!checked(child.sq, p.whiteToMove)) buf[k++] = buf[i]
        }
        return buf.copyOf(k)
    }

    fun legalMoves(p: ChessPosition): List<Int> = legalArray(p).asList()

    private fun hasLegal(p: ChessPosition): Boolean {
        val buf = IntArray(256)
        val b = p.sq
        val sg = if (p.whiteToMove) 1 else -1
        for (s in 0..63) if (b[s] * sg > 0) {
            val n = genPiece(b, p.whiteToMove, p.castling, p.ep, s, buf, 0)
            for (i in 0 until n) if (!checked(apply(p, buf[i]).sq, p.whiteToMove)) return true
        }
        return false
    }

    /** Applies a pseudo-legal move (no legality check). */
    private fun apply(p: ChessPosition, m: Int): ChessPosition {
        val b = p.sq.copyOf()
        val f = from(m); val t = ChessRules.to(m); val pr = promo(m)
        val pc = b[f]
        val white = pc > 0
        val kind = if (pc < 0) -pc else pc
        var ep = -1
        if (kind == 1) {
            if (t == p.ep && b[t] == 0 && f % 8 != t % 8) b[if (white) t - 8 else t + 8] = 0
            if (t - f == 16 || f - t == 16) ep = (f + t) / 2
        }
        b[t] = if (pr != 0) (if (white) pr else -pr) else pc
        b[f] = 0
        if (kind == 6 && (t - f == 2 || f - t == 2)) {
            if (t > f) { b[t - 1] = b[t + 1]; b[t + 1] = 0 } else { b[t + 1] = b[t - 2]; b[t - 2] = 0 }
        }
        var c = p.castling
        for (s in intArrayOf(f, t)) c = c and when (s) {
            0 -> 2.inv(); 7 -> 1.inv(); 4 -> 3.inv(); 56 -> 8.inv(); 63 -> 4.inv(); 60 -> 12.inv(); else -> -1
        }
        // keep the en passant square only when a pawn could actually take (canonical FEN)
        if (ep >= 0) {
            val er = ep / 8; val ef = ep % 8
            val opp = if (white) -1 else 1
            val pr2 = if (white) er + 1 else er - 1
            val ok = (ef > 0 && b[pr2 * 8 + ef - 1] == opp) || (ef < 7 && b[pr2 * 8 + ef + 1] == opp)
            if (!ok) ep = -1
        }
        return ChessPosition(b, !p.whiteToMove, c, ep)
    }

    fun play(p: ChessPosition, m: Int): ChessPosition = apply(p, m)

    fun isCheckmate(p: ChessPosition): Boolean = inCheck(p) && !hasLegal(p)
    fun isStalemate(p: ChessPosition): Boolean = !inCheck(p) && !hasLegal(p)

    // ---- notation ------------------------------------------------------------------------------------------

    private fun name(s: Int) = "${'a' + s % 8}${'1' + s / 8}"

    fun uci(m: Int): String {
        val pr = promo(m)
        return name(from(m)) + name(to(m)) + (if (pr == 0) "" else "nbrq"[pr - 2].toString())
    }

    fun parseUci(p: ChessPosition, s: String): Int? = legalMoves(p).firstOrNull { uci(it) == s }

    fun san(p: ChessPosition, m: Int): String {
        val f = from(m); val t = ChessRules.to(m); val pc = p.sq[f]
        val kind = if (pc < 0) -pc else pc
        val sb = StringBuilder()
        val capture = p.sq[t] != 0 || (kind == 1 && f % 8 != t % 8)
        if (kind == 6 && (t - f == 2 || f - t == 2)) sb.append(if (t > f) "O-O" else "O-O-O")
        else {
            if (kind == 1) {
                if (capture) sb.append('a' + f % 8)
            } else {
                sb.append(ChessPosition.LETTERS[kind])
                val others = legalMoves(p).filter { it != m && to(it) == t && from(it) != f && p.sq[from(it)] == pc }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { from(it) % 8 == f % 8 }
                    val sameRank = others.any { from(it) / 8 == f / 8 }
                    if (!sameFile) sb.append('a' + f % 8)
                    else if (!sameRank) sb.append('1' + f / 8)
                    else sb.append(name(f))
                }
            }
            if (capture) sb.append('x')
            sb.append(name(t))
            if (promo(m) != 0) sb.append('=').append(ChessPosition.LETTERS[promo(m)])
        }
        val child = apply(p, m)
        if (inCheck(child)) sb.append(if (hasLegal(child)) '+' else '#')
        return sb.toString()
    }

    // ---- mate search ---------------------------------------------------------------------------------------

    sealed interface Mate {
        /** A proof: [keys] are every first move that mates in exactly [inMoves], the shortest there is. */
        class Forced(val keys: List<Int>, val inMoves: Int) : Mate
        /** Proved: no forced mate within the requested moves. */
        object None : Mate
        /** The node budget ran out: nothing is known. Never read this as [None]. */
        object Truncated : Mate
    }

    private const val DEFAULT_BUDGET = 3_000_000

    // Zobrist keys from a fixed splitmix64 stream: piece (+6) * 64 + square, side, castling (16), ep file (8).
    private const val Z_SIDE = 832
    private const val Z_CASTLE = 833
    private const val Z_EP = 849
    private val Z = LongArray(857).also {
        var x = 0x9E3779B97F4A7C15uL.toLong()
        for (i in it.indices) {
            x += -0x61c8864680b583ebL
            var z = x
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            it[i] = z xor (z ushr 31)
        }
    }

    /**
     * The mate search's own mutable board: make/unmake instead of a new position per node, an incremental
     * Zobrist hash, and a transposition table (bounds on "attacker mates within d", looked up only, never iterated).
     * Squares and moves are walked in index order, so nothing here depends on a hash container's order.
     */
    private class Ctx(p: ChessPosition, n: Int, var budget: Int) {
        val b = p.sq.copyOf()
        var white = p.whiteToMove
        var cast = p.castling
        var ep = p.ep
        var hash = 0L
        val ks = intArrayOf(-1, -1) // king squares: white, black
        var sp = 0
        var truncated = false
        private val depth = 2 * n + 8
        private val ttBits = (10 + 2 * n).coerceIn(12, 18)
        private val uCast = IntArray(depth)
        private val uEp = IntArray(depth)
        private val uHash = LongArray(depth)
        private val uMoved = IntArray(depth)
        private val uCap = IntArray(depth)
        private val killer = IntArray(depth)
        private val bufs = arrayOfNulls<IntArray>(depth)
        private val scratch = IntArray(256)
        private val ttKey = LongArray(1 shl ttBits)
        private val ttWin = ByteArray(1 shl ttBits) // smallest d proved to mate (127 none)
        private val ttLose = ByteArray(1 shl ttBits) // largest d proved not to mate

        init {
            for (s in 0..63) {
                val v = b[s]
                if (v == 0) continue
                hash = hash xor Z[(v + 6) * 64 + s]
                if (v == 6) ks[0] = s else if (v == -6) ks[1] = s
            }
            if (white) hash = hash xor Z[Z_SIDE]
            hash = hash xor Z[Z_CASTLE + cast]
            if (ep >= 0) hash = hash xor Z[Z_EP + ep % 8]
        }

        private fun buf(): IntArray = bufs[sp] ?: IntArray(256).also { bufs[sp] = it }

        private fun genAll(out: IntArray): Int {
            val sg = if (white) 1 else -1
            var n = 0
            for (s in 0..63) if (b[s] * sg > 0) n = genPiece(b, white, cast, ep, s, out, n, false)
            return n
        }

        fun make(m: Int) {
            val f = from(m); val t = ChessRules.to(m); val pr = promo(m)
            val pc = b[f]
            val kind = if (pc < 0) -pc else pc
            val cap = b[t]
            val w = white
            uCast[sp] = cast; uEp[sp] = ep; uHash[sp] = hash; uMoved[sp] = pc; uCap[sp] = cap
            var h = hash xor Z[(pc + 6) * 64 + f] xor Z[Z_CASTLE + cast]
            if (cap != 0) h = h xor Z[(cap + 6) * 64 + t]
            if (ep >= 0) h = h xor Z[Z_EP + ep % 8]
            var nep = -1
            if (kind == 1) {
                if (t == ep && cap == 0 && f % 8 != t % 8) {
                    val cs = if (w) t - 8 else t + 8
                    h = h xor Z[(b[cs] + 6) * 64 + cs]
                    b[cs] = 0
                }
                if (t - f == 16 || f - t == 16) nep = (f + t) / 2
            }
            val np = if (pr != 0) (if (w) pr else -pr) else pc
            b[t] = np; b[f] = 0
            h = h xor Z[(np + 6) * 64 + t]
            if (kind == 6) {
                ks[if (w) 0 else 1] = t
                if (t - f == 2 || f - t == 2) {
                    val rf = if (t > f) t + 1 else t - 2
                    val rt = if (t > f) t - 1 else t + 1
                    val rk = b[rf]
                    b[rt] = rk; b[rf] = 0
                    h = h xor Z[(rk + 6) * 64 + rf] xor Z[(rk + 6) * 64 + rt]
                }
            }
            var c = cast
            for (s in 0..1) c = c and when (if (s == 0) f else t) {
                0 -> 2.inv(); 7 -> 1.inv(); 4 -> 3.inv(); 56 -> 8.inv(); 63 -> 4.inv(); 60 -> 12.inv(); else -> -1
            }
            if (nep >= 0) {
                val er = nep / 8; val ef = nep % 8
                val opp = if (w) -1 else 1
                val pr2 = if (w) er + 1 else er - 1
                val ok = (ef > 0 && b[pr2 * 8 + ef - 1] == opp) || (ef < 7 && b[pr2 * 8 + ef + 1] == opp)
                if (!ok) nep = -1
            }
            cast = c; ep = nep
            h = h xor Z[Z_CASTLE + c] xor Z[Z_SIDE]
            if (nep >= 0) h = h xor Z[Z_EP + nep % 8]
            hash = h
            white = !w
            sp++
        }

        fun unmake(m: Int) {
            sp--
            white = !white
            val f = from(m); val t = ChessRules.to(m)
            val pc = uMoved[sp]
            val cap = uCap[sp]
            val kind = if (pc < 0) -pc else pc
            b[f] = pc; b[t] = cap
            if (kind == 1 && t == uEp[sp] && cap == 0 && f % 8 != t % 8) b[if (white) t - 8 else t + 8] = if (white) -1 else 1
            if (kind == 6) {
                ks[if (white) 0 else 1] = f
                if (t - f == 2 || f - t == 2) {
                    val rf = if (t > f) t + 1 else t - 2
                    val rt = if (t > f) t - 1 else t + 1
                    b[rf] = b[rt]; b[rt] = 0
                }
            }
            cast = uCast[sp]; ep = uEp[sp]; hash = uHash[sp]
        }

        /** The side that just moved has left its own king attacked. */
        fun exposed(): Boolean {
            val k = ks[if (white) 1 else 0]
            return k >= 0 && isAttacked(b, k, white)
        }

        /** The side to move is in check. */
        fun inCheckNow(): Boolean {
            val k = ks[if (white) 0 else 1]
            return k >= 0 && isAttacked(b, k, !white)
        }

        fun hasLegal(): Boolean {
            val n = genAll(scratch)
            for (i in 0 until n) {
                val m = scratch[i]
                make(m)
                val ok = !exposed()
                unmake(m)
                if (ok) return true
            }
            return false
        }

        private fun tick(): Boolean {
            if (--budget < 0) truncated = true
            return truncated
        }

        /**
         * Could [m] give check? A cheap superset ([make] settles it): the piece lands where its kind attacks the enemy
         * king (a slider needs a clear line, the square it leaves counted as clear), or leaving its square uncovers one of
         * our sliders on the king, or the move is a castle, a promotion or an en passant capture. A move this says no to
         * never gives check.
         */
        private fun mayCheck(m: Int): Boolean {
            val ek = ks[if (white) 1 else 0]
            if (ek < 0 || promo(m) != 0) return true
            val f = from(m); val t = ChessRules.to(m)
            val pc = b[f]
            val kind = if (pc < 0) -pc else pc
            if (kind == 6) return t - f == 2 || f - t == 2 || discovers(f, ek)
            if (kind == 1 && t == ep) return true
            if (discovers(f, ek)) return true
            val df = abs(t % 8 - ek % 8)
            val dr = abs(t / 8 - ek / 8)
            return when (kind) {
                1 -> df == 1 && ek / 8 - t / 8 == (if (white) 1 else -1)
                2 -> df * dr == 2
                3 -> df == dr && clear(t, ek, f)
                4 -> (df == 0 || dr == 0) && clear(t, ek, f)
                else -> (df == dr || df == 0 || dr == 0) && clear(t, ek, f)
            }
        }

        /** Squares strictly between [a] and [c] (lined up, a != c) are empty, [skip] counting as empty. */
        private fun clear(a: Int, c: Int, skip: Int): Boolean {
            val step = dirStep(a, c)
            var s = a + step
            while (s != c) { if (s != skip && b[s] != 0) return false; s += step }
            return true
        }

        private fun dirStep(a: Int, c: Int): Int {
            val sf = if (c % 8 > a % 8) 1 else if (c % 8 < a % 8) -1 else 0
            val sr = if (c / 8 > a / 8) 1 else if (c / 8 < a / 8) -1 else 0
            return sr * 8 + sf
        }

        /** [f] is the only thing between the enemy king [ek] and one of our sliders that attacks along that line. */
        private fun discovers(f: Int, ek: Int): Boolean {
            val df = f % 8 - ek % 8
            val dr = f / 8 - ek / 8
            val diag = abs(df) == abs(dr)
            if (!diag && df != 0 && dr != 0) return false
            if (!clear(ek, f, -1)) return false
            val sf = if (df > 0) 1 else if (df < 0) -1 else 0
            val sr = if (dr > 0) 1 else if (dr < 0) -1 else 0
            var nf = f % 8 + sf; var nr = f / 8 + sr
            val sg = if (white) 1 else -1
            while (nf in 0..7 && nr in 0..7) {
                val v = b[nr * 8 + nf] * sg
                if (v != 0) return v == 5 || (v == 3 && diag) || (v == 4 && !diag)
                nf += sf; nr += sr
            }
            return false
        }

        /** Can the side to move mate right now? Only a check can, so only moves that may give one are looked at. */
        private fun mateInOne(): Boolean {
            val buf = buf()
            val n = genAll(buf)
            for (i in 0 until n) {
                val m = buf[i]
                if (!mayCheck(m)) continue
                make(m)
                val mate = !exposed() && inCheckNow() && !hasLegal()
                unmake(m)
                if (mate) return true
            }
            return false
        }

        /** Attacker to move: can they mate within [d] of their own moves? Result ignored once truncated. */
        fun mates(d: Int): Boolean {
            if (tick()) return false
            val idx = (hash ushr (64 - ttBits)).toInt()
            if (ttKey[idx] == hash) {
                if (ttWin[idx] <= d) return true
                if (ttLose[idx] >= d) return false
            }
            if (d == 1) {
                val r1 = mateInOne()
                store(idx, 1, r1)
                return r1
            }
            val buf = buf()
            val n = genAll(buf)
            var k = 0
            var res = false
            for (i in 0 until n) { // ranked 0 check, 1 capture, 2 quiet; a mating check ends it. Legality is settled on make.
                val m = buf[i]
                var rank = if (b[ChessRules.to(m)] != 0) 1 else 2
                if (mayCheck(m)) {
                    make(m)
                    if (exposed()) { unmake(m); continue }
                    val chk = inCheckNow()
                    val mated = chk && !hasLegal()
                    unmake(m)
                    if (mated) { res = true; break }
                    if (chk) rank = 0
                }
                buf[k++] = m or (rank shl 16)
            }
            if (!res) loop@ for (pass in 0..2) for (i in 0 until k) {
                if (buf[i] shr 16 != pass) continue
                val m = buf[i] and 0xFFFF
                make(m)
                if (exposed()) { unmake(m); continue }
                val ok = allReplies(d - 1)
                unmake(m)
                if (ok) { res = true; break@loop }
                if (truncated) return false
            }
            if (!truncated) store(idx, d, res)
            return res
        }

        private fun store(idx: Int, d: Int, res: Boolean) {
            if (ttKey[idx] != hash) { ttKey[idx] = hash; ttWin[idx] = 127; ttLose[idx] = 0 }
            if (res) { if (d < ttWin[idx]) ttWin[idx] = d.toByte() } else if (d > ttLose[idx]) ttLose[idx] = d.toByte()
        }

        /** Defender to move: every legal reply (there must be one) leaves the attacker a mate within [d]. */
        fun allReplies(d: Int): Boolean {
            val buf = buf()
            val n = genAll(buf)
            val kl = killer[sp] // the reply that refuted the last attempt at this depth: tried first among its kind
            var k = 0
            for (i in 0 until n) { // ranked: check, capture, quiet; each with the last refuter ahead of the rest
                val m = buf[i]
                var base = if (b[ChessRules.to(m)] != 0) 2 else 4
                if (mayCheck(m)) { // exact only here; the rest are never checks, and are tested for legality on make
                    make(m)
                    val legal = !exposed()
                    val chk = legal && inCheckNow()
                    unmake(m)
                    if (!legal) continue
                    if (chk) base = 0
                }
                buf[k++] = m or ((base + (if (m == kl) 0 else 1)) shl 16)
            }
            var any = false
            for (pass in 0..5) for (i in 0 until k) {
                if (buf[i] shr 16 != pass) continue
                val m = buf[i] and 0xFFFF
                make(m)
                if (exposed()) { unmake(m); continue }
                any = true
                val ok = mates(d)
                unmake(m)
                if (!ok) { killer[sp] = m; return false }
            }
            return any // no legal reply at all: stalemate (mate is the caller's case)
        }
    }

    fun forcedMate(p: ChessPosition, n: Int, nodeBudget: Int = DEFAULT_BUDGET): Mate {
        val cx = Ctx(p, n, nodeBudget)
        val moves = legalArray(p)
        for (d in 1..n) {
            val keys = ArrayList<Int>()
            for (m in moves) {
                cx.make(m)
                val ok = if (cx.inCheckNow() && !cx.hasLegal()) true else d > 1 && cx.allReplies(d - 1)
                cx.unmake(m)
                if (cx.truncated) return Mate.Truncated
                if (ok) keys.add(m)
            }
            if (keys.isNotEmpty()) return Mate.Forced(keys, d)
        }
        return Mate.None
    }

    /**
     * The defender's reply in a mate puzzle, a pure function of the position (saved games replay it, so changing
     * it is a save-compatibility change): the first legal reply after which the attacker has no forced mate within
     * [remaining] moves; if every reply loses, the first of those that loses slowest; null with no legal move.
     */
    fun defence(p: ChessPosition, remaining: Int): Int? {
        var best: Int? = null
        var bestLen = 0
        for (r in legalArray(p)) {
            val len = when (val res = forcedMate(apply(p, r), remaining, Int.MAX_VALUE)) {
                is Mate.Forced -> res.inMoves
                else -> return r
            }
            if (len > bestLen) { best = r; bestLen = len }
        }
        return best
    }
}
