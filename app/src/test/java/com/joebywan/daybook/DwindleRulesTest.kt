package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Dwindle
import com.joebywan.daybook.puzzles.DwindleEntry
import com.joebywan.daybook.puzzles.DwindleGoal
import com.joebywan.daybook.puzzles.DwindleLogic
import com.joebywan.daybook.puzzles.DwindleState
import com.joebywan.daybook.puzzles.WheelKeyAction
import com.joebywan.daybook.puzzles.WheelWords
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DwindleRulesTest {

    private val start = LocalDate(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        DwindleLogic.generateVerified(DailySeed.seedFor(LocalDate.fromEpochDays(start.toEpochDays() + day), Dwindle.id, d), d)!!

    private val small get() = Dwindle.tutorialBoard()

    @Test
    fun `a goal is found, a real word is a bonus, and nothing else changes the board`() {
        val s = small
        assertEquals(DwindleEntry.Goal, DwindleLogic.judge(s, "chef"))
        assertEquals(DwindleEntry.Bonus, DwindleLogic.judge(s, "she"))
        assertEquals(DwindleEntry.TooShort, DwindleLogic.judge(s, "he"))
        assertEquals(DwindleEntry.NotOnTiles, DwindleLogic.judge(s, "cat"))
        assertEquals(DwindleEntry.NotAWord, DwindleLogic.judge(s, "chs"))
        assertEquals(s, s.enter("cat"))
        val one = s.enter("chef").enter("she")
        assertEquals(listOf("chef"), one.found)
        assertEquals(listOf("she"), one.bonus)
        assertEquals("one word is one move", 2, one.moves)
        assertEquals(DwindleEntry.Found, DwindleLogic.judge(one, "chef"))
        assertEquals(DwindleEntry.Found, DwindleLogic.judge(one, "she"))
    }

    @Test
    fun `counts fall by the goal found, a bonus word uses nothing, and a used-up tile can no longer be spelled through`() {
        val s = small
        assertEquals(listOf(2, 2, 1, 2, 2, 1, 2, 2, 2), s.counts().toList())
        assertEquals(s.counts().toList(), s.enter("she").counts().toList())
        val after = s.enter("chef").enter("chest")
        assertEquals(listOf(1, 0, 1, 0, 1, 1, 0, 1, 2), after.counts().toList())
        assertEquals("SHE goes through a vanished H", DwindleEntry.NotOnTiles, DwindleLogic.judge(after, "she"))
    }

    @Test
    fun `solved is every goal found, in any order, and only that`() {
        val words = small.goals.map { it.word }
        for (order in listOf(words, words.reversed(), listOf(words[2], words[0], words[3], words[1]))) {
            var s = small
            order.forEachIndexed { i, w ->
                assertFalse(s.solved)
                s = s.enter(w)
                assertEquals(i + 1, s.found.size)
            }
            assertTrue(s.solved)
            assertTrue("every tile has gone", s.counts().all { it == 0 })
        }
    }

    @Test
    fun `an old save without bonus words still loads`() {
        val saved = """{"width":3,"height":3,"letters":"tckhsrefi","goals":[{"word":"chef","cells":[1,3,6,7]}]}"""
        val s = kotlinx.serialization.json.Json.decodeFromString(DwindleState.serializer(), saved)
        assertEquals(emptyList<String>(), s.bonus)
        assertEquals(0, s.moves)
    }

    @Test
    fun `the oracle walks every order on the walkthrough board and catches a board with two ways or an orphan tile`() {
        assertEquals(emptyList<String>(), DwindleOracle.problems(small))
        assertTrue(DwindleLogic.proved(small))
        // TEE has two ways over the two Es on either side of the T.
        val two = DwindleState(3, 1, "ete", listOf(DwindleGoal("et", listOf(0, 1))))
        assertTrue(DwindleOracle.problems(two).isNotEmpty())
        assertFalse(DwindleLogic.proved(two))
        // A tile no goal uses.
        val orphan = DwindleState(3, 1, "abc", listOf(DwindleGoal("ab", listOf(0, 1))))
        assertTrue(DwindleOracle.problems(orphan).isNotEmpty())
        assertFalse(DwindleLogic.proved(orphan))
    }

    @Test
    fun `the oracle sees a position with a word lost when the stored way is a lie`() {
        // Two goals, the second stored over tiles it does not spell, so the counts disagree with the letters.
        val lie = DwindleState(2, 2, "abcd", listOf(DwindleGoal("ab", listOf(0, 1)), DwindleGoal("cd", listOf(0, 1))))
        assertFalse(DwindleLogic.proved(lie))
        assertTrue(DwindleOracle.problems(lie).isNotEmpty())
    }

    @Test
    fun `every board reaches every order by the oracle - a year of Standard and Hard, a sample of Expert`() {
        for (d in Difficulty.entries) {
            val days = if (d == Difficulty.EXPERT) 30 else 365
            for (day in 0 until days) {
                val b = board(day, d)
                assertEquals("$d day $day", emptyList<String>(), DwindleOracle.problems(b))
            }
        }
    }

    @Test
    fun `a year of daily boards - only listed, clued words, and the tiers get bigger, longer and more shared`() {
        val report = StringBuilder("Dwindle, 365 daily boards a tier from $start\n")
        var last = doubleArrayOf(0.0, 0.0, 0.0)
        for (d in Difficulty.entries) {
            val spec = DwindleLogic.specFor(d)
            var words = 0.0
            var shared = 0.0
            var uses = 0.0
            var worstShared = 1f
            val ms = mutableListOf<Double>()
            for (day in 0 until 365) {
                val seed = DailySeed.seedFor(LocalDate.fromEpochDays(start.toEpochDays() + day), Dwindle.id, d)
                val t0 = System.nanoTime()
                val b = DwindleLogic.generateVerified(seed, d)!!
                ms += (System.nanoTime() - t0) / 1e6
                assertEquals(spec.width * spec.height, b.letters.length)
                assertTrue(b.goals.size in spec.minWords..spec.maxWords)
                for (g in b.goals) {
                    assertTrue(g.word, WheelWords.isGridWord(g.word))
                    assertTrue(g.word, g.word.length in DwindleLogic.MIN_LENGTH..spec.maxLength)
                    assertTrue(g.word, WheelWords.clue(g.word).isNotBlank())
                }
                val s = DwindleLogic.sharedShare(b)
                assertTrue("most letters are used in more than one word, day $day $d: $s", s > 0.5f)
                words += b.goals.size
                shared += s
                uses += b.counts().average()
                worstShared = minOf(worstShared, s)
            }
            ms.sort()
            val now = doubleArrayOf(words / 365, shared / 365, uses / 365)
            for (i in now.indices) assertTrue("$d is not past the tier below: ${now.toList()} ${last.toList()}", now[i] > last[i])
            last = now
            report.append(
                "${d.name}: ${spec.width}x${spec.height}, ${"%.1f".format(now[0])} words, shared ${"%.0f".format(now[1] * 100)}% (worst ${"%.0f".format(worstShared * 100)}%), " +
                    "uses a tile ${"%.2f".format(now[2])}; generation ms median ${"%.1f".format(ms[182])}, p95 ${"%.1f".format(ms[346])}, max ${"%.1f".format(ms.last())}\n",
            )
        }
        File("build/reports").mkdirs()
        File("build/reports/dwindle-tiers.txt").writeText(report.toString())
        println(report)
    }

    @Test
    fun `the last resort is a fixed, proved board on every tier`() {
        for (d in Difficulty.entries) {
            val b = DwindleLogic.lastResort(d)
            assertEquals(b, DwindleLogic.lastResort(d))
            assertTrue(DwindleLogic.accepts(b, DwindleLogic.specFor(d)))
        }
    }

    @Test
    fun `swiping joins touching live tiles, backs out, and ignores the rest`() {
        val s = small
        val c = s.counts()
        assertEquals(listOf(1), DwindleLogic.swipe(s, c, emptyList(), 1))
        assertEquals("a diagonal touches", listOf(1, 3), DwindleLogic.swipe(s, c, listOf(1), 3))
        assertEquals("too far", listOf(0), DwindleLogic.swipe(s, c, listOf(0), 2))
        assertEquals("back out", listOf(1), DwindleLogic.swipe(s, c, listOf(1, 3), 1))
        assertEquals("no tile twice", listOf(1, 3, 4, 0), DwindleLogic.swipe(s, c, listOf(1, 3, 4, 0), 3))
        val gone = s.enter("chef").enter("chest").counts()
        assertEquals("a vanished tile does not join", listOf(0), DwindleLogic.swipe(s, gone, listOf(0), 3))
    }

    @Test
    fun `tapping joins neighbours, takes the last back and restarts elsewhere`() {
        val s = small
        val c = s.counts()
        assertEquals(listOf(1, 3), DwindleLogic.tap(s, c, listOf(1), 3))
        assertEquals(listOf(1), DwindleLogic.tap(s, c, listOf(1, 3), 3))
        assertEquals(listOf(8), DwindleLogic.tap(s, c, listOf(0), 8))
    }

    @Test
    fun `typing finds tiles for the letters, and refuses a letter no live way takes`() {
        val s = small
        assertEquals(listOf(1, 3, 6, 7), DwindleLogic.liveWay(s, "chef"))
        assertNull(DwindleLogic.liveWay(s, "chz"))
        assertNull("a vanished tile", DwindleLogic.liveWay(s.enter("chef").enter("chest"), "she"))
        assertNotNull(WheelKeyAction.Enter)
    }

    @Test
    fun `a diagonal swipe passes between its two side tiles`() {
        val side = 100f
        fun at(x: Float, y: Float) = Dwindle.tileAt(x, y, side, 4, 4, 0.42f)
        assertEquals(0, at(50f, 50f))
        assertEquals(5, at(150f, 150f))
        assertNull("the corner between four tiles is nobody's", at(100f, 100f))
        assertNull(at(95f, 105f))
        assertNull("outside", at(450f, 50f))
    }

    @Test
    fun `the board fits its box from both axes, at the short and tall phones and on its side`() {
        for ((w, h) in listOf(375f to 330f, 390f to 620f, 360f to 430f, 700f to 240f, 900f to 1100f)) for (d in Difficulty.entries) {
            val b = board(0, d)
            val f = Dwindle.fit(w, h, b.width, b.height, b.goals.map { it.word.length })
            assertTrue("$w x $h $d: tile ${f.tile}", f.tile > 0f)
            assertTrue("wide", f.tile * b.width <= w)
            assertTrue("tall: ${f.tile * b.height + f.listHeight}", f.tile * b.height + f.listHeight + 82f <= h + 0.01f)
            assertEquals("no goal lost in the wrapping", b.goals.size, f.rows.flatten().size)
        }
        val phone = board(0, Difficulty.EXPERT)
        val f = Dwindle.fit(375f, 330f, phone.width, phone.height, phone.goals.map { it.word.length })
        assertTrue("Expert at 375x537 keeps a thumb-sized tile: ${f.tile}", f.tile >= 28f)
    }
}
