package com.joebywan.daybook

import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessPositions
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.ChessTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Mate teacher. Its checks never use the prover's search: [mateWithin] is a plain recursive minimax (no ordering,
 * no memo, no "only checks can mate" shortcut, no budget; it only stops early on the first refutation or the first
 * mate found), written for this file, over the perft-verified move generator.
 *
 * No `fallback` exists to measure (the teacher reads no stored answer), so there is no ceiling test: the coverage
 * test counts techniques and asserts that hints alone finish every sampled board with no mistake.
 */
class ChessTeachingTest {

    // ---- independent checker ------------------------------------------------------------------------------

    /** The side to move can force mate within [d] of its own moves. */
    private fun mateWithin(p: ChessPosition, d: Int): Boolean {
        if (d <= 0) return false
        for (m in ChessRules.legalMoves(p)) if (keeps(ChessRules.play(p, m), d)) return true
        return false
    }

    /** After an attacker move (the defender to move), the mate still comes within [d] attacker moves in all. */
    private fun keeps(c: ChessPosition, d: Int): Boolean {
        val replies = ChessRules.legalMoves(c)
        if (replies.isEmpty()) return ChessRules.inCheck(c)
        if (d <= 1) return false
        return replies.all { mateWithin(ChessRules.play(c, it), d - 1) }
    }

    // ---- data -----------------------------------------------------------------------------------------------

    private class Entry(val fen: String, val last: String, val mateIn: Int) {
        fun state(played: List<String> = emptyList()) = ChessState(fen, last, mateIn, played, played.size)
    }

    private fun entries(list: List<String>, mateIn: Int, take: Int): List<Entry> {
        val all = list.flatMap { it.split('\n') }.filter { it.isNotBlank() }
        val step = maxOf(1, all.size / take)
        return all.filterIndexed { i, _ -> i % step == 0 }.take(take).map {
            val cut = it.lastIndexOf(' ')
            Entry(it.substring(0, cut), it.substring(cut + 1).takeIf { l -> l != "-" } ?: "", mateIn)
        }
    }

    private val tiers = listOf(
        Triple("Standard", ChessPositions.STANDARD, 2),
        Triple("Hard", ChessPositions.HARD, 3),
        Triple("Expert", ChessPositions.EXPERT, 4),
    )

    private fun sample(name: String, count: Int): List<Entry> =
        tiers.first { it.first == name }.let { entries(it.second, it.third, count) }

    /** The state after the player's [uci] and the defence's derived reply, as the game replays it. */
    private fun after(e: Entry, played: List<String>): ChessPosition? {
        var pos = ChessPosition.fromFen(e.fen)
        for ((i, u) in played.withIndex()) {
            val c = ChessRules.play(pos, ChessRules.parseUci(pos, u)!!)
            if (ChessRules.isCheckmate(c)) return null
            pos = ChessRules.play(c, ChessRules.defence(c, e.mateIn - i - 1) ?: return null)
        }
        return pos
    }

    private fun pickedMove(before: ChessState, step: com.joebywan.daybook.core.Deduction): String =
        (step.apply(before) as ChessState).played.last()

    // ---- soundness ------------------------------------------------------------------------------------------

    @Test
    fun `every step names a key by the naive checker and explains it`() {
        var checked = 0
        for ((name, n) in listOf("Standard" to 40, "Hard" to 6, "Expert" to 1)) for (e in sample(name, n)) {
            val s = e.state()
            val d = ChessTeacher.teach(s)
            assertNotNull("$name ${e.fen}", d)
            d!!
            assertFalse(d.mistake); assertFalse(d.fallback)
            assertTrue(d.technique in ChessTeacher.TECHNIQUES)
            val uci = pickedMove(s, d)
            val p = ChessPosition.fromFen(e.fen)
            val m = ChessRules.parseUci(p, uci)
            assertNotNull("$name ${e.fen}: $uci is not legal", m)
            assertTrue("$name ${e.fen}: $uci is not a mate within ${e.mateIn}", keeps(ChessRules.play(p, m!!), e.mateIn))
            assertTrue(d.focus.isNotEmpty() && d.targets.isNotEmpty())
            assertTrue((d.focus + d.cited + d.targets).all { it in 0..63 || it in 102..105 })
            checked++
        }
        assertTrue(checked > 40)
    }

    @Test
    fun `steps stay sound from positions a player made, not only the solver's path`() {
        var checked = 0
        for ((name, n) in listOf("Standard" to 25, "Hard" to 3)) for (e in sample(name, n)) {
            // every legal first move the naive checker says keeps the mate, then every later move: the second step
            // starts from the position the game's own defence leaves.
            val p0 = ChessPosition.fromFen(e.fen)
            val firsts = ChessRules.legalMoves(p0).filter { keeps(ChessRules.play(p0, it), e.mateIn) }
            for (f in firsts) {
                val played = listOf(ChessRules.uci(f))
                val st = e.state(played)
                if (ChessRules.isCheckmate(ChessRules.play(p0, f))) { assertNull(ChessTeacher.teach(st)); continue }
                val d = ChessTeacher.teach(st)!!
                assertFalse("a key is never a mistake: ${e.fen} ${played}", d.mistake)
                val p1 = after(e, played)!!
                val m = ChessRules.parseUci(p1, pickedMove(st, d))!!
                assertTrue(keeps(ChessRules.play(p1, m), e.mateIn - 1))
                checked++
            }
        }
        assertTrue("barely checked: $checked", checked >= 20)
    }

    // ---- mistakes -----------------------------------------------------------------------------------------

    @Test
    fun `a move is a mistake exactly when no forced mate is left, and a mistake is addressed first`() {
        var mistakes = 0
        var legalNonKeys = 0
        for ((name, n) in listOf("Standard" to 12, "Hard" to 2)) for (e in sample(name, n)) {
            val p0 = ChessPosition.fromFen(e.fen)
            for (m in ChessRules.legalMoves(p0)) {
                val c = ChessRules.play(p0, m)
                val keep = keeps(c, e.mateIn)
                val st = e.state(listOf(ChessRules.uci(m)))
                val d = ChessTeacher.teach(st)
                if (ChessRules.isCheckmate(c)) { assertNull(d); continue }
                assertNotNull(d)
                d!!
                assertEquals("${e.fen} ${ChessRules.uci(m)}", !keep, d.mistake)
                if (d.mistake) {
                    mistakes++
                    assertEquals(ChessTeacher.MISTAKE, d.technique)
                    assertTrue(d.targets == setOf(m and 63, (m shr 6) and 63))
                    // Show me takes it back; and taking it back (or playing another move) is what clears the hint
                    val undone = d.apply(st) as ChessState
                    assertEquals(emptyList<String>(), undone.played)
                    assertTrue(d.isReached(undone))
                    assertFalse(d.isReached(st))
                    // reasoning from the board with the mistake gone is the plain first step again
                    assertFalse(ChessTeacher.teach(undone)!!.mistake)
                } else legalNonKeys++
            }
        }
        assertTrue("no mistakes sampled: $mistakes", mistakes > 100)
        assertTrue(legalNonKeys >= 12)
    }

    @Test
    fun `a legal move that keeps a forced mate is never called a mistake, at any move`() {
        // second move: every legal move after the key, judged by the naive checker
        var kept = 0
        var bad = 0
        for (e in sample("Hard", 3)) {
            val p0 = ChessPosition.fromFen(e.fen)
            val key = ChessRules.legalMoves(p0).first { keeps(ChessRules.play(p0, it), e.mateIn) }
            val played = listOf(ChessRules.uci(key))
            val p1 = after(e, played)!!
            for (m in ChessRules.legalMoves(p1)) {
                val c = ChessRules.play(p1, m)
                val st = e.state(played + ChessRules.uci(m))
                val d = ChessTeacher.teach(st)
                if (ChessRules.isCheckmate(c)) { assertNull(d); kept++; continue }
                val ok = keeps(c, e.mateIn - 1)
                assertEquals("${e.fen} $played ${ChessRules.uci(m)}", !ok, d!!.mistake)
                if (ok) kept++ else bad++
            }
        }
        assertTrue(kept > 0 && bad > 0)
    }

    @Test
    fun `a wrong promotion piece is a mistake only when it loses the mate`() {
        // Kb6 and a pawn on a7 against the king on a8... mate by promoting to a queen or rook (b8 square): a7 is blocked,
        // so use g7 with the black king on h6 hemmed in: g8=Q# is mate, g8=R# is mate, g8=B and g8=N are not.
        val fen = "8/6P1/5K1k/8/8/8/8/8 w - -"
        val p = ChessPosition.fromFen(fen)
        val st = { u: String -> ChessState(fen, "", 2, listOf(u), 1) }
        for (m in ChessRules.legalMoves(p)) {
            val u = ChessRules.uci(m)
            if (u.startsWith("g7g8")) {
                val d = ChessTeacher.teach(st(u))
                val keep = keeps(ChessRules.play(p, m), 2)
                assertEquals(u, !keep, d?.mistake ?: false)
            }
        }
        val promos = ChessRules.legalMoves(p).filter { ChessRules.uci(it).startsWith("g7g8") }
        assertEquals(4, promos.size)
        // the step that offers a promotion also asks for the picker button: 100 + kind
        val d = ChessTeacher.teach(ChessState(fen, "", 2))!!
        val move = pickedMove(ChessState(fen, "", 2), d)
        if (move.length == 5) assertTrue(d.targets.any { it in 102..105 })
    }

    @Test
    fun `a promotion that is a later move also asks for the picker button`() {
        // After some first move the hint's next step is the promotion, and it asks for the picker button too.
        var later = 0
        for (fen in listOf("7k/5P2/6K1/8/8/8/8/8 w - -", "8/6P1/5K1k/8/8/8/8/8 w - -", "k7/2P5/1K6/8/8/8/8/8 w - -"))
            for (n in 2..3) {
                val p = ChessPosition.fromFen(fen)
                for (m in ChessRules.legalMoves(p)) {
                    val st = ChessState(fen, "", n, listOf(ChessRules.uci(m)))
                    val d = ChessTeacher.teach(st)
                    if (d == null || d.mistake || pickedMove(st, d).length != 5) continue
                    later++
                    assertTrue("$fen ${ChessRules.uci(m)}", d.targets.any { it in 102..105 })
                }
            }
        assertTrue(later > 0)
    }

    // ---- text fit ---------------------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel`() {
        var texts = 0
        for ((name, n) in listOf("Standard" to 150, "Hard" to 60, "Expert" to 25)) for (e in sample(name, n)) {
            var played = emptyList<String>()
            val p0 = ChessPosition.fromFen(e.fen)
            // plant a mistake once, then walk the hints
            val bad = ChessRules.legalMoves(p0).firstOrNull { !keeps(ChessRules.play(p0, it), e.mateIn) }
            if (bad != null) {
                val d = ChessTeacher.teach(e.state(listOf(ChessRules.uci(bad))))!!
                check(d, "$name mistake ${e.fen}"); texts++
            }
            var guard = 0
            while (guard++ < 6) {
                val st = e.state(played)
                val d = ChessTeacher.teach(st) ?: break
                check(d, "$name ${e.fen} $played"); texts++
                played = (d.apply(st) as ChessState).played
            }
        }
        assertTrue(texts > 300)
    }

    private fun check(d: com.joebywan.daybook.core.Deduction, what: String) {
        assertTrue("$what: nudge ${d.nudge.length}: ${d.nudge}", d.nudge.length in 1..70)
        assertTrue("$what: explanation ${d.explanation.length}: ${d.explanation}", d.explanation.length in 1..200)
        assertFalse(what, d.explanation.contains("\n"))
    }

    // ---- hints alone, coverage, timing ----------------------------------------------------------------------

    private fun pct(sorted: List<Long>, q: Double) = sorted[minOf(sorted.size - 1, (sorted.size * q).toInt())]

    @Test
    fun `coverage - which techniques boards need, per difficulty, and what a hint costs`() {
        val out = StringBuilder()
        for ((name, n, take) in listOf(Triple("Standard", 2, 200), Triple("Hard", 3, 100), Triple("Expert", 4, 60))) {
            val counts = linkedMapOf<String, Int>()
            val times = ArrayList<Long>()
            var boards = 0
            for (e in sample(name, take)) {
                var st = e.state()
                var guard = 0
                while (true) {
                    val t0 = System.nanoTime()
                    val d = ChessTeacher.teach(st)
                    times += (System.nanoTime() - t0) / 1_000_000
                    if (d == null) break
                    assertTrue("$name ${e.fen}: walk did not finish", guard++ <= n)
                    assertFalse("$name ${e.fen}: a mistake on the hints' own path", d.mistake)
                    assertFalse(d.fallback)
                    if (counts.merge(d.technique, 1, Int::plus) == 1) out.appendLine("  first [${d.technique}] ${d.nudge} / ${d.explanation}")
                    st = d.apply(st) as ChessState
                }
                assertTrue("$name ${e.fen}: not solved by hints alone", st.solved && st.played.size <= n)
                boards++
            }
            times.sort()
            // the first hint of a board is the costly one (it searches the whole mate); later ones are shorter
            out.appendLine("$name (mate in $n), $boards boards, hints alone solve all, no mistake, no fallback exists")
            out.appendLine("  techniques: ${ChessTeacher.TECHNIQUES.joinToString { "$it=${counts[it] ?: 0}" }}")
            out.appendLine("  teach() ms over ${times.size} calls: median ${pct(times, 0.5)}, p95 ${pct(times, 0.95)}, max ${times.last()}")
        }
        for ((name, _, _) in tiers) for (e in sample(name, 3)) {
            val d = ChessTeacher.teach(e.state())!!
            out.appendLine("  e.g. [${d.technique}] ${d.nudge} / ${d.explanation}")
            val bad = ChessPosition.fromFen(e.fen).let { p -> ChessRules.legalMoves(p).first { !keeps(ChessRules.play(p, it), e.mateIn) } }
            out.appendLine("  e.g. [mistake] " + ChessTeacher.teach(e.state(listOf(ChessRules.uci(bad))))!!.explanation)
        }
        println(out)
        File("build/reports").mkdirs()
        File("build/reports/chess-teaching-coverage.txt").writeText(out.toString())
    }

    @Test
    fun `timing - the first hint over every shipped position`() {
        val out = StringBuilder()
        for ((name, list, n) in tiers) {
            val all = entries(list, n, 100000)
            val times = ArrayList<Long>()
            for (e in all) {
                val t0 = System.nanoTime()
                assertNotNull(ChessTeacher.teach(e.state()))
                times += (System.nanoTime() - t0) / 1_000_000
            }
            times.sort()
            out.appendLine("$name: ${all.size} positions, first hint ms: median ${pct(times, 0.5)}, p95 ${pct(times, 0.95)}, max ${times.last()}")
        }
        println(out)
        File("build/reports").mkdirs()
        File("build/reports/chess-teaching-timing.txt").writeText(out.toString())
    }
}
