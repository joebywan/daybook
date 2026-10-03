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
    private fun genPiece(p: ChessPosition, s: Int, out: IntArray, n0: Int): Int {
        val b = p.sq
        val white = p.whiteToMove
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
                for (d in -1..1 step 2) {
                    val nf = f + d
                    if (nf !in 0..7) continue
                    val t = nr * 8 + nf
                    if (b[t] * sg < 0 || t == p.ep) {
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
                if (v == 6) n = castles(p, s, out, n)
            }
            else -> {
                for (dirs in arrayOf(DIAG, ORTHO)) {
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
        for (i in n0 + 1 until n) {
            val m = out[i]; val k = sortKey(m)
            var j = i - 1
            while (j >= n0 && sortKey(out[j]) > k) { out[j + 1] = out[j]; j-- }
            out[j + 1] = m
        }
        return n
    }

    private fun castles(p: ChessPosition, s: Int, out: IntArray, n0: Int): Int {
        val white = p.whiteToMove
        val base = if (white) 0 else 56
        if (s != base + 4) return n0
        val b = p.sq
        val sg = if (white) 1 else -1
        var n = n0
        val kr = if (white) 1 else 4
        val qr = if (white) 2 else 8
        val rook = 4 * sg
        if (p.castling and (kr or qr) == 0 || isAttacked(b, s, !white)) return n
        if (p.castling and kr != 0 && b[base + 7] == rook && b[base + 5] == 0 && b[base + 6] == 0 &&
            !isAttacked(b, base + 5, !white) && !isAttacked(b, base + 6, !white)
        ) out[n++] = mv(s, base + 6)
        if (p.castling and qr != 0 && b[base] == rook && b[base + 1] == 0 && b[base + 2] == 0 && b[base + 3] == 0 &&
            !isAttacked(b, base + 3, !white) && !isAttacked(b, base + 2, !white)
        ) out[n++] = mv(s, base + 2)
        return n
    }

    private fun legalArray(p: ChessPosition): IntArray {
        val buf = IntArray(256)
        val b = p.sq
        val sg = if (p.whiteToMove) 1 else -1
        var n = 0
        for (s in 0..63) if (b[s] * sg > 0) n = genPiece(p, s, buf, n)
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
            val n = genPiece(p, s, buf, 0)
            for (i in 0 until n) if (!checked(apply(p, buf[i]).sq, p.whiteToMove)) return true
        }
        return false
    }

    /** Applies a pseudo-legal move (no legality check). */
    private fun apply(p: ChessPosition, m: Int): ChessPosition {
        val b = p.sq.copyOf()
        val f = from(m); val t = to(m); val pr = promo(m)
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
        val f = from(m); val t = to(m); val pc = p.sq[f]
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

    private class Search(var budget: Int) {
        var truncated = false
        // Results of mates(p, d) by exact position. Looked up only, never iterated, so no hash order reaches a pick.
        val memo = HashMap<String, Boolean>()
        fun tick(): Boolean {
            if (--budget < 0) truncated = true
            return truncated
        }
    }

    private const val MEMO_CAP = 150_000

    private fun memoKey(p: ChessPosition, d: Int): String {
        val c = CharArray(68)
        for (i in 0..63) c[i] = ('a' + p.sq[i] + 6)
        c[64] = if (p.whiteToMove) 'w' else 'b'
        c[65] = 'a' + p.castling
        c[66] = 'a' + (p.ep + 1)
        c[67] = '0' + d
        return c.concatToString()
    }

    /** Can the side to move mate right now? Only a check can, so only moves that give one are looked at. */
    private fun mateInOne(p: ChessPosition): Boolean {
        val buf = IntArray(256)
        val b = p.sq
        val white = p.whiteToMove
        val sg = if (white) 1 else -1
        for (s in 0..63) if (b[s] * sg > 0) {
            val n = genPiece(p, s, buf, 0)
            for (i in 0 until n) {
                val c = apply(p, buf[i])
                if (checked(c.sq, !white) && !checked(c.sq, white) && !hasLegal(c)) return true
            }
        }
        return false
    }

    /** Attacker to move: can they mate within [d] of their own moves? Early exit; result ignored once truncated. */
    private fun mates(p: ChessPosition, d: Int, s: Search): Boolean {
        if (s.tick()) return false
        if (d == 1) return mateInOne(p)
        val key = memoKey(p, d)
        s.memo[key]?.let { return it }
        val moves = legalArray(p)
        val n = moves.size
        val kids = arrayOfNulls<ChessPosition>(n)
        val rank = IntArray(n) // 0 check, 1 capture, 2 quiet
        for (i in 0 until n) {
            val c = apply(p, moves[i])
            kids[i] = c
            rank[i] = if (inCheck(c)) 0 else if (p.sq[to(moves[i])] != 0) 1 else 2
        }
        var res = false
        for (i in 0 until n) if (rank[i] == 0 && !hasLegal(kids[i]!!)) { res = true; break }
        if (!res) loop@ for (pass in 0..2) for (i in 0 until n) {
            if (rank[i] != pass) continue
            if (allReplies(kids[i]!!, d - 1, s)) { res = true; break@loop }
            if (s.truncated) return false
        }
        if (s.memo.size < MEMO_CAP) s.memo[key] = res
        return res
    }

    /** Defender to move: every legal reply (there must be one) leaves the attacker a mate within [d]. */
    private fun allReplies(p: ChessPosition, d: Int, s: Search): Boolean {
        val replies = legalArray(p)
        val n = replies.size
        if (n == 0) return false // stalemate (mate is handled by the caller)
        // checks first, then captures, then the rest: the likeliest refutations of an attempted mate
        for (pass in 0..2) for (r in replies) {
            val rk = if (inCheck(apply(p, r))) 0 else if (p.sq[to(r)] != 0) 1 else 2
            if (rk != pass) continue
            if (!mates(apply(p, r), d, s)) return false
        }
        return true
    }

    fun forcedMate(p: ChessPosition, n: Int, nodeBudget: Int = DEFAULT_BUDGET): Mate {
        val s = Search(nodeBudget)
        val moves = legalArray(p)
        for (d in 1..n) {
            val keys = ArrayList<Int>()
            for (m in moves) {
                val c = apply(p, m)
                val ok = if (inCheck(c) && !hasLegal(c)) true else d > 1 && allReplies(c, d - 1, s)
                if (s.truncated) return Mate.Truncated
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
