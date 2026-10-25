package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.LexiconWords
import com.joebywan.daybook.puzzles.WheelEntry
import com.joebywan.daybook.puzzles.WheelSlot
import com.joebywan.daybook.puzzles.WheelWords
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.WordWheelLogic
import com.joebywan.daybook.puzzles.WordWheelState
import com.joebywan.daybook.puzzles.WordWheelTeacher
import com.joebywan.daybook.puzzles.WordWheelWords
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordWheelRulesTest {

    private val lenient = Json { ignoreUnknownKeys = true }

    private fun daily(day: Int, d: Difficulty): WordWheelState? =
        WordWheel.generateVerified(daySeed(day, WordWheel.id, d), d)

    @Test
    fun `a word fills its own slot, a real word is a bonus, and nothing else changes the board`() {
        val s = WordWheel.tutorialBoard() // BRAIN, BARN, RAIN on the wheel "irnba"
        assertEquals(WheelEntry.Fills(1), WordWheelLogic.judge(s, "barn"))
        assertEquals(WheelEntry.Bonus, WordWheelLogic.judge(s, "ban"))
        assertEquals(WheelEntry.Bonus, WordWheelLogic.judge(s, "bran")) // accepted, not a grid word
        assertEquals(WheelEntry.NotAWord, WordWheelLogic.judge(s, "nabr"))
        assertEquals(WheelEntry.TooShort, WordWheelLogic.judge(s, "an"))
        assertEquals(WheelEntry.NotOnWheel, WordWheelLogic.judge(s, "barns"))
        assertEquals(WheelEntry.NotOnWheel, WordWheelLogic.judge(s, "babe"))
        val one = s.enter("barn")
        assertEquals(listOf("barn"), one.found)
        assertEquals(1, one.moves)
        assertEquals(WheelEntry.Found, WordWheelLogic.judge(one, "barn"))
        assertEquals(one, one.enter("barn"))
        assertEquals(one, one.enter("nabr"))
        val bonus = one.enter("ban")
        assertEquals(listOf("ban"), bonus.bonus)
        assertEquals(WheelEntry.Found, WordWheelLogic.judge(bonus, "ban"))
        assertFalse(bonus.solved)
    }

    @Test
    fun `solved is every slot holding its word, in any order, and only that`() {
        val s = WordWheel.tutorialBoard()
        val words = s.slots.map { it.word }
        for (order in listOf(words, words.reversed(), listOf(words[1], words[0], words[2]))) {
            var t = s
            order.dropLast(1).forEach { t = t.enter(it) }
            assertFalse(t.solved)
            assertTrue(t.enter(order.last()).solved)
        }
        // Bonus words never count towards it.
        var t = s.enter("ban").enter("bin").enter("bar")
        assertFalse(t.solved)
        words.forEach { t = t.enter(it) }
        assertTrue(t.solved)
    }

    @Test
    fun `the squares show exactly the found words`() {
        val s = WordWheel.tutorialBoard().enter("barn")
        val shown = s.shown()
        assertEquals(listOf('b', 'a', 'r', 'n'), s.slots[1].cells(s.width).map { shown[it] })
        assertEquals(4, shown.count { it != null })
        assertEquals(11, s.used().count { it }) // 5 + 4 + 4 - 2 crossings
    }

    @Test
    fun `an old save without bonus words still loads`() {
        val json = """{"wheel":"irnba","width":5,"height":4,"slots":[{"word":"brain","row":2,"col":0,"across":true}],"found":["brain"]}"""
        val s = lenient.decodeFromString(WordWheelState.serializer(), json)
        assertEquals(emptyList<String>(), s.bonus)
        assertTrue(s.solved)
    }

    /** Every board of a year per tier, laid by [WordWheelLogic.lay], re-read square by square by the oracle. Reports what the tiers look like. */
    @Test
    fun `a year of daily boards obeys the layout rules, by an independent reading of the squares`() {
        val report = StringBuilder("Word Wheel, 365 daily boards per tier from 2026-01-01\n")
        val means = mutableListOf<Double>()
        for (d in Difficulty.entries) {
            val spec = WordWheelLogic.specFor(d)
            var words = 0
            var minWords = Int.MAX_VALUE
            var maxWords = 0
            var cells = 0
            val sizes = sortedMapOf<String, Int>()
            val times = mutableListOf<Long>()
            var pinned = 0
            var clued = 0
            for (day in 0 until 365) {
                val t0 = System.nanoTime()
                val b = daily(day, d) ?: error("no board on day $day ${d.name}")
                times += System.nanoTime() - t0
                val problems = WordWheelOracle.problems(b)
                assertTrue("day $day ${d.name}: $problems", problems.isEmpty())
                assertEquals(spec.letters, b.wheel.length)
                assertTrue(b.slots.size in spec.minWords..spec.maxWords)
                assertTrue(b.width <= spec.maxCols && b.height <= spec.maxRows)
                if (spec.needSix) assertTrue(b.slots.drop(1).any { it.length >= 6 })
                assertTrue("the wheel spells its word outright", b.wheel != b.slots[0].word)
                words += b.slots.size
                minWords = minOf(minWords, b.slots.size)
                maxWords = maxOf(maxWords, b.slots.size)
                cells += b.used().count { it }
                sizes.merge("${b.width}x${b.height}", 1, Int::plus)
                val walk = WordWheelTeacher.walk(b)
                assertTrue(walk.maxChoice <= WordWheelLogic.MAX_CHOICE)
                pinned += walk.pinned
                clued += walk.clued
            }
            times.sort()
            means += words / 365.0
            report.append(
                "${d.name}: words ${"%.2f".format(words / 365.0)} (min $minWords, max $maxWords), squares ${"%.1f".format(cells / 365.0)}, " +
                    "pinned steps ${"%.0f".format(100.0 * pinned / (pinned + clued))}%, " +
                    "ms median ${"%.1f".format(times[182] / 1e6)} p95 ${"%.1f".format(times[346] / 1e6)} max ${"%.1f".format(times.last() / 1e6)}, " +
                    "sizes $sizes\n",
            )
        }
        writeReport("wordwheel-generation.txt", report.toString())
        println(report)
        assertTrue("the tiers get harder in the order they are offered: $means", means[0] < means[1] && means[1] < means[2])
    }

    @Test
    fun `the last resort is a fixed, proved board on every tier`() {
        for (d in Difficulty.entries) {
            val b = WordWheelLogic.lastResort(d)
            assertEquals(WordWheel.generateVerified(WordWheelLogic.LAST_RESORT_SEED, d), b)
            assertEquals(emptyList<String>(), WordWheelOracle.problems(b))
            assertTrue(WordWheelTeacher.walk(b).maxChoice <= WordWheelLogic.MAX_CHOICE)
        }
    }

    @Test
    fun `the oracle catches a word that touches another and a word made by accident`() {
        val good = WordWheel.tutorialBoard()
        assertEquals(emptyList<String>(), WordWheelOracle.problems(good))
        // RAIN moved one column left runs beside BARN: two letters side by side make words nobody placed.
        val touching = good.copy(slots = listOf(good.slots[0], good.slots[1], WheelSlot("rain", 0, 2, false)))
        assertTrue(WordWheelOracle.problems(touching).isNotEmpty())
        // A word placed off the grid, and one not on the wheel.
        assertTrue(WordWheelOracle.problems(good.copy(slots = good.slots + WheelSlot("bin", 3, 3, true))).isNotEmpty())
        assertTrue(WordWheelOracle.problems(good.copy(wheel = "irnbe")).isNotEmpty())
    }

    // ---- the lists --------------------------------------------------------------------------------

    @Test
    fun `every list is sorted and unique, and grid words are well known and clued`() {
        val all = (3..7).map { WheelWords.grid(it) } + listOf(WordWheelWords.ROOTS_5, WordWheelWords.ROOTS_6, WordWheelWords.ROOTS_7, WordWheelWords.BONUS)
        for (list in all) {
            assertEquals(list.sorted(), list)
            assertEquals(list.size, list.toSet().size)
            assertTrue(list.all { w -> w.all { it in 'a'..'z' } })
        }
        for (n in 3..7) assertTrue(WheelWords.grid(n).all { it.length == n })
        // Four and five letters are exactly Lexicon's answers: one screening for both games.
        assertEquals(LexiconWords.ANSWERS_4, WheelWords.grid(4))
        assertEquals(LexiconWords.ANSWERS_5, WheelWords.grid(5))
        val gridAll = (3..7).flatMap { WheelWords.grid(it) }.toSet()
        assertTrue("a bonus word is also a grid word", WordWheelWords.BONUS.none { it in gridAll })
        for (w in listOf("sex", "ass", "cretin", "vagina", "fatter", "loaves")) assertFalse(w, w in gridAll)
    }

    @Test
    fun `every grid word has a clue that does not give it away, and no two share one`() {
        val words = (3..7).flatMap { WheelWords.grid(it) }
        for (w in words) {
            val clue = WheelWords.clue(w)
            assertTrue("$w: empty clue", clue.isNotBlank())
            val tokens = Regex("[a-z]+").findAll(clue.lowercase()).map { it.value }
            assertTrue("$w: its clue \"$clue\" contains the word", tokens.none { it == w || it.startsWith(w) })
        }
        val dupes = words.groupBy { WheelWords.clue(it).lowercase() }.filterValues { it.size > 1 }
        assertEquals("shared clues: $dupes", emptyMap<String, List<String>>(), dupes)
    }

    @Test
    fun `every root spells enough grid words, counted afresh`() {
        val minimum = mapOf(5 to 8, 6 to 12, 7 to 14)
        val gridAll = (3..7).flatMap { WheelWords.grid(it) }
        fun counts(w: String) = IntArray(26).also { a -> w.forEach { a[it - 'a']++ } }
        for (n in 5..7) {
            for (root in WheelWords.roots(n)) {
                assertTrue(root in WheelWords.grid(n))
                val have = counts(root)
                val spelt = gridAll.filter { it != root && it.length <= n && counts(it).withIndex().all { (k, v) -> v <= have[k] } }
                assertTrue("$root spells only ${spelt.size}", spelt.size >= minimum.getValue(n))
                if (n == 7) assertTrue("$root spells no six", spelt.any { it.length == 6 })
            }
        }
    }

    @Test
    fun `every accepted word some wheel spells is accepted, Lexicon's guesses included`() {
        val roots = (5..7).flatMap { WheelWords.roots(it) }.map { r -> IntArray(26).also { a -> r.forEach { a[it - 'a']++ } } }
        fun onSomeWheel(w: String): Boolean {
            val need = IntArray(26).also { a -> w.forEach { a[it - 'a']++ } }
            return roots.any { have -> (0 until 26).all { need[it] <= have[it] } }
        }
        for (w in LexiconWords.GUESSES_4 + LexiconWords.GUESSES_5) {
            if (onSomeWheel(w)) assertTrue(w, WheelWords.isWord(w))
        }
        for (w in listOf("larp", "ate", "eat", "tea", "rain", "brain", "planet")) assertTrue(w, WheelWords.isWord(w))
        assertFalse(WheelWords.isWord("xqz"))
    }
}
