package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.QuartetsBank
import com.joebywan.daybook.puzzles.QuartetsLogic
import com.joebywan.daybook.puzzles.QuartetsLogic.Split
import com.joebywan.daybook.puzzles.QuartetsWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Quartets on the shipped bank (the `tools/words/quartets` files, generated into [QuartetsWords]): a year of daily boards on
 * every tier is proved, gated and free of screened words, and the measurements land in
 * `build/reports/quartets-generation.txt` (`docs/QUARTETS_SPEC.md`, "Measured"). The bank is read here from its strings,
 * not through [QuartetsBank], and spares, phantoms and the split are counted here, not by [QuartetsLogic.overlap]. The
 * split counts every fit, quiet ones included; spares and phantoms, which the tier gate holds, count plain fits only.
 */
class QuartetsBankTest {

    /** One generated line read as plain strings: title, level, the words dealt for it, and its quiet words. */
    private class Line(val title: String, val level: Int, val words: Set<String>, val quiet: Set<String>) {
        val fits: Set<String> = words + quiet
    }

    private val lines: List<Line> = QuartetsWords.CATEGORIES.map { l ->
        val p = l.split('|')
        fun set(i: Int) = p.getOrElse(i) { "" }.split(',').filter { it.isNotEmpty() }.toSet()
        Line(p[0], p[1].toInt(), set(2), set(3))
    }
    private val byTitle = lines.associateBy { it.title }

    private val grim: Set<String> = File("../tools/words/grim.txt").readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

    private val big = setOf("Add S at the front", "Hide an animal", "Backwards, another word")

    private fun maskOf(set: Set<Int>): Int = set.fold(0) { acc, i -> acc or (1 shl i) }

    private fun wordsOf(words: List<String>, mask: Int): List<String> = words.indices.filter { mask and (1 shl it) != 0 }.map { words[it] }

    @Test
    fun `the shipped bank parses and every category can be dealt several ways`() {
        val bank = QuartetsBank.shipped
        assertEquals(lines.size, bank.categories.size)
        assertTrue("grim.txt has ${grim.size} words", grim.size > 50)
        for (c in bank.categories) {
            val l = byTitle.getValue(c.title)
            assertEquals(c.title, l.level, c.level)
            assertEquals(c.title, l.words, c.words.toSet())
            assertEquals(c.title, l.quiet, c.quiet.toSet())
            assertTrue("${c.title} has ${l.words.size} words to deal", l.words.size >= 6)
            assertTrue("${c.title} lists a quiet word as its own", (l.words intersect l.quiet).isEmpty())
            for (w in l.fits) assertTrue("${c.title}: $w", c.fits(w) && w.length in 3..8 && w.all { it in 'a'..'z' })
            assertEquals("${c.title} holds a screened word", emptySet<String>(), l.fits intersect grim)
        }
        // The quiet memberships the bank auditor named (docs/QUARTETS_SPEC.md, "The bank").
        assertTrue(byTitle.getValue("Head ___").quiet.contains("stone"))
        assertTrue(byTitle.getValue("Black ___").quiet.containsAll(listOf("eye", "mail")))
        assertTrue(byTitle.getValue("Water ___").quiet.contains("board"))
        assertTrue(byTitle.getValue("Add S at the front").quiet.contains("hat"))
        // The second sweep's (docs/QUARTETS_SPEC.md, "The bank"): obscure but real fits are quiet, ordinary ones plain.
        assertTrue(byTitle.getValue("___ fish").quiet.containsAll(listOf("moon", "bone", "black")))
        assertTrue(byTitle.getValue("___ ship").quiet.containsAll(listOf("ward", "light")))
        assertTrue(byTitle.getValue("Rocks").words.contains("onyx"))
        assertTrue(byTitle.getValue("Road vehicles").quiet.containsAll(listOf("mini", "compact")))
    }

    @Test
    fun `every lastResort board is proved by the oracle`() {
        for (d in Difficulty.entries) {
            val s = QuartetsLogic.lastResort(d)
            val sets = s.masks.map { m -> s.words.indices.filter { m and (1 shl it) != 0 }.toSet() }
            assertEquals("$d", 1, QuartetsOracle.partitions(16, sets).size)
        }
    }

    @Test
    fun `a year of daily boards on every tier is proved, gated, agrees with the oracle and shows nothing screened`() {
        val report = StringBuilder("Quartets on the shipped bank: ${lines.size} categories " +
            "(level 1/2/3: ${(1..3).map { lv -> lines.count { it.level == lv } }.joinToString("/")}), " +
            "${lines.flatMap { it.words }.toSet().size} words, ${lines.sumOf { it.quiet.size }} quiet memberships; " +
            "a year of daily seeds from $TEST_START per tier.\n")
        val bank = QuartetsBank.shipped
        for (d in Difficulty.entries) {
            val spec = QuartetsLogic.specFor(d)
            val allowed = lines.filter { it.level in spec.levels }
            val attempts = ArrayList<Int>()
            val micros = ArrayList<Long>()
            val spares = sortedMapOf<Int, Int>()
            val phantoms = sortedMapOf<Int, Int>()
            val levels = sortedMapOf<String, Int>()
            val dealt = HashMap<String, Int>()
            val seen = HashSet<String>()
            val rejected = sortedMapOf<String, Int>()
            var draws = 0
            var bigPhantomDraws = 0
            var oracleChecked = 0
            for (day in 0 until 365) {
                val seed = daySeed(day, "test-quartets", d)
                val t0 = System.nanoTime()
                val verified = QuartetsLogic.generateVerified(seed, d)
                micros += (System.nanoTime() - t0) / 1000
                assertNotNull("$d day $day gave up", verified)
                val s = verified!!
                assertEquals("$d day $day", s, QuartetsLogic.generate(seed, d))
                val deal = QuartetsLogic.deal(seed, d, bank)!!
                assertEquals(s, deal.state)
                attempts += deal.attempts
                for (a in 0 until deal.attempts) {
                    val draw = QuartetsLogic.draw(seed, a, d, bank)!!
                    draws++
                    if (draw.verdict != QuartetsLogic.Verdict.Kept) rejected.merge(draw.verdict.name, 1, Int::plus)
                    val st = draw.state ?: continue
                    val cut = QuartetsLogic.split(st.masks, st.all) as? Split.ExactlyOne ?: continue
                    if (st.titles.indices.any { i -> st.titles[i] in big && cut.groups.none { st.masks[i] and it == it } }) {
                        if (draw.verdict == QuartetsLogic.Verdict.TooMuchOverlap) bigPhantomDraws++
                    }
                }
                // The board, read again from the bank's strings.
                assertEquals(16, s.words.toSet().size)
                assertEquals("$d day $day shows a screened word", emptyList<String>(), s.words.filter { it in grim })
                val fitting = lines.filter { l -> s.words.count { it in l.fits } >= 3 }
                assertEquals(fitting.map { it.title }, s.titles)
                val sets = fitting.map { l -> s.words.indices.filter { s.words[it] in l.fits }.toSet() }
                assertEquals(sets.map { maskOf(it) }, s.masks)
                // The split: the oracle's on a sample (about 50 ms a board on a desktop JVM), the rules' elsewhere.
                val cut: Set<Set<Int>> = if (day % 7 == 0) {
                    val oracle = QuartetsOracle.partitions(16, sets)
                    assertEquals("$d day $day", 1, oracle.size)
                    val split = QuartetsLogic.split(s.masks, s.all) as Split.ExactlyOne
                    assertEquals(oracle[0].map { maskOf(it) }.toSet(), split.groups.toSet())
                    oracleChecked++
                    oracle[0]
                } else {
                    (QuartetsLogic.split(s.masks, s.all) as Split.ExactlyOne).groups
                        .map { g -> s.words.indices.filter { g and (1 shl it) != 0 }.toSet() }.toSet()
                }
                assertEquals(4, cut.size)
                // Spares and phantoms, counted here over plain fits only: a quiet fit is never a spare, and a category
                // reaching three words only through quiet ones is no phantom.
                val spare = HashSet<String>()
                var phantom = 0
                for (l in fitting) {
                    val inside = cut.filter { g -> g.all { s.words[it] in l.words } }
                    if (inside.isEmpty()) {
                        if (s.words.count { it in l.words } >= 3) phantom++
                    } else {
                        for (g in inside) spare += s.words.indices.filter { it !in g && s.words[it] in l.words }.map { s.words[it] }
                    }
                }
                assertTrue("$d day $day spares ${spare.size}", spare.size in spec.spares)
                assertTrue("$d day $day overlap ${spare.size + phantom}", spare.size + phantom in spec.overlap)
                spares.merge(spare.size, 1, Int::plus)
                phantoms.merge(phantom, 1, Int::plus)
                // The dealt categories: one per group, the tier's levels, the level it needs first, deep enough, and no
                // group holding a quiet word of a category it fits.
                assertEquals(4, deal.categories.size)
                assertEquals(spec.needsLevel, deal.categories[0].level)
                assertTrue(deal.categories.all { it.level in spec.levels })
                assertTrue(deal.categories.count { it.level >= 2 } >= spec.deep)
                for (g in cut) {
                    val words = g.map { s.words[it] }
                    assertTrue("$d day $day $words", deal.categories.any { c -> words.all { it in byTitle.getValue(c.title).words } })
                    for (l in lines) {
                        if (words.all { it in l.fits }) assertTrue("$d day $day $words: quiet in ${l.title}", words.none { it in l.quiet })
                    }
                }
                levels.merge(deal.categories.map { it.level }.sorted().joinToString(""), 1, Int::plus)
                for (c in deal.categories) dealt.merge(c.title, 1, Int::plus)
                seen += s.words
            }
            assertTrue("$d oracle sample $oracleChecked", oracleChecked >= 50)
            val sorted = micros.sorted()
            fun ms(p: Double) = "%.1f".format(sorted[((sorted.size - 1) * p).toInt()] / 1000.0)
            fun mean(m: Map<Int, Int>) = "%.2f".format(m.entries.sumOf { it.key * it.value } / 365.0)
            val never = allowed.map { it.title }.filter { it !in dealt }
            val most = dealt.entries.sortedWith(compareBy({ -it.value }, { it.key }))
            report.append("\n$d: proved 365/365, lastResort 0; draws a board mean ${"%.1f".format(attempts.average())}, " +
                "most ${attempts.max()} of ${QuartetsLogic.ATTEMPTS}\n")
            report.append("  generation ms median ${ms(0.5)} p95 ${ms(0.95)} max ${ms(1.0)}\n")
            report.append("  spares a board $spares (mean ${mean(spares)}); phantoms $phantoms (mean ${mean(phantoms)})\n")
            report.append("  levels of the four groups $levels\n")
            report.append("  words shown in the year ${seen.size}; categories dealt ${dealt.size} of ${allowed.size}, never: $never\n")
            report.append("  most dealt (boards of 365): ${most.take(6).joinToString { "${it.key} ${it.value}" }}\n")
            report.append("  least dealt: ${most.takeLast(5).reversed().joinToString { "${it.key} ${it.value}" }}\n")
            report.append("  draws turned down $rejected of $draws; too much overlap with a big wordplay phantom $bigPhantomDraws\n")
            assertTrue("$d came close to the allowance: ${attempts.max()}", attempts.max() < QuartetsLogic.ATTEMPTS / 4)
        }
        println(report)
        writeReport("quartets-generation.txt", report.toString())
    }
}
