package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.QuartetsBank
import com.joebywan.daybook.puzzles.QuartetsLogic
import com.joebywan.daybook.puzzles.QuartetsLogic.Split
import com.joebywan.daybook.puzzles.QuartetsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Counts every way to cut [n] words into fours that each sit inside one of [categories] (sets of word indices).
 * Written on a different principle from [QuartetsLogic.split]: it labels the words one at a time with a group number
 * (a new group or one already open, never more than four in a group), and only once every word has a label does it ask
 * whether each group fits a category. No pruning by validity, no early stop.
 */
object QuartetsOracle {
    fun partitions(n: Int, categories: List<Set<Int>>): List<Set<Set<Int>>> {
        if (n % 4 != 0) return emptyList()
        val groups = n / 4
        val within = categories.map { c -> c.fold(0) { acc, i -> acc or (1 shl i) } }
        val members = IntArray(groups)
        val size = IntArray(groups)
        val out = ArrayList<Set<Set<Int>>>()
        fun assign(i: Int, open: Int) {
            if (i == n) {
                if (members.all { g -> within.any { it and g == g } }) {
                    out += members.map { g -> (0 until n).filter { g and (1 shl it) != 0 }.toSet() }.toSet()
                }
                return
            }
            for (g in 0 until minOf(open + 1, groups)) {
                if (size[g] == 4) continue
                members[g] = members[g] or (1 shl i)
                size[g]++
                assign(i + 1, maxOf(open, g + 1))
                size[g]--
                members[g] = members[g] and (1 shl i).inv()
            }
        }
        assign(0, 0)
        return out
    }
}

class QuartetsRulesTest {

    /** The test's own bank, so these tests never move with the shipped words. Membership is complete over it. */
    private val lines = listOf(
        "Birds|1|crane,crow,eagle,heron,jay,owl,robin,swan,swift,teal,wren",
        "Body parts|1|arm,chin,ear,elbow,eye,foot,hand,heel,knee,leg,neck,nose,toe,tongue",
        "Fish|2|bass,carp,cod,perch,pike,salmon,sole,trout",
        "Flowers|1|daisy,iris,lily,orchid,poppy,rose,tulip,violet",
        "Fruit|1|apple,banana,cherry,grape,lemon,lime,olive,orange,peach,pear,plum",
        "Herbs|1|basil,bay,chives,dill,mint,parsley,sage,thyme",
        "Hide a number|3|bone,canine,honey,often,stone,tent,weight",
        "In a kitchen|1|fridge,kettle,ladle,oven,pan,sink,toaster,whisk",
        "In a rainbow|1|blue,green,indigo,orange,red,violet,yellow",
        "Metals|1|copper,gold,iron,lead,mercury,nickel,silver,tin,zinc",
        "Musical terms|2|bass,chord,flat,note,pitch,scale,sharp,tempo",
        "Parts of a shoe|2|eyelet,heel,insole,lace,sole,toe,tongue,upper",
        "Planets|1|earth,jupiter,mars,mercury,neptune,saturn,uranus,venus",
        "Shades of green|2|emerald,jade,lime,mint,olive,sage,teal",
        "Sound like letters|3|bee,eye,jay,pea,queue,sea,tea,why,you",
        "Weapons|2|axe,bow,club,dagger,lance,pike,spear,sword",
        "___fish|3|bone,butter,cat,gold,jelly,lion,monk,star,sun,sword",
        "___fly|3|butter,crane,dragon,fire,green,horse,house,may,stone",
    )
    private val bank = QuartetsBank.parse(lines)

    /** The fixture read as plain strings, apart from the parser. */
    private class Cat(val title: String, val level: Int, val words: Set<String>)
    private val cats = lines.map { l -> l.split('|').let { Cat(it[0], it[1].toInt(), it[2].split(',').toSet()) } }

    /** Index sets of every category (from [cats], or from the lastResort's own titles) over a board's words. */
    private fun indexSets(words: List<String>, from: List<Set<String>>): List<Set<Int>> =
        from.map { c -> words.indices.filter { words[it] in c }.toSet() }.filter { it.size >= 3 }

    private fun maskOf(set: Set<Int>): Int = set.fold(0) { acc, i -> acc or (1 shl i) }

    /** Spares and phantoms counted here from the bank's strings and the oracle's one partition. */
    private fun overlapOf(words: List<String>, from: List<Set<String>>, cut: Set<Set<Int>>): Int {
        val board = words.toSet()
        val spares = HashSet<String>()
        var phantoms = 0
        for (c in from) {
            val inside = cut.filter { g -> g.all { words[it] in c } }
            if (inside.isEmpty()) {
                if (board.count { it in c } >= 3) phantoms++
            } else {
                for (g in inside) spares += board.filter { it in c && words.indexOf(it) !in g }
            }
        }
        return spares.size + phantoms
    }

    private fun checkGate(difficulty: Difficulty, overlap: Int) {
        val spec = QuartetsLogic.specFor(difficulty)
        assertTrue("$difficulty overlap $overlap", overlap in spec.minOverlap..spec.maxOverlap)
    }

    @Test
    fun `parse reads the generated format`() {
        assertEquals(18, bank.categories.size)
        assertEquals(bank.categories.map { it.title }.sorted(), bank.categories.map { it.title })
        val planets = bank.categories.first { it.title == "Planets" }
        assertEquals(1, planets.level)
        assertTrue(planets.fits("mercury"))
        assertFalse(planets.fits("gold"))
    }

    @Test
    fun `every lastResort board has one split, which the oracle agrees with, and fits its tier`() {
        for (d in Difficulty.entries) {
            val s = QuartetsLogic.lastResort(d)
            assertEquals(16, s.words.toSet().size)
            assertEquals(s.titles.sorted(), s.titles)
            val sets = s.masks.map { m -> s.words.indices.filter { m and (1 shl it) != 0 }.toSet() }
            val oracle = QuartetsOracle.partitions(16, sets)
            assertEquals("$d", 1, oracle.size)
            val split = QuartetsLogic.split(s.masks, s.remaining)
            assertTrue(split is Split.ExactlyOne)
            assertEquals(oracle[0].map { maskOf(it) }.toSet(), (split as Split.ExactlyOne).groups.toSet())
            val from = sets.map { set -> set.map { s.words[it] }.toSet() }
            checkGate(d, overlapOf(s.words, from, oracle[0]))
            assertEquals(overlapOf(s.words, from, oracle[0]), QuartetsLogic.overlap(s)!!.total)
        }
    }

    @Test
    fun `generated fixture boards are proved, gated and agree with the oracle`() {
        for (d in Difficulty.entries) {
            val spec = QuartetsLogic.specFor(d)
            for (seed in 1L..8L) {
                val s = QuartetsLogic.generateVerified(seed * 7919, d, bank)
                assertNotNull("$d seed $seed", s)
                s!!
                assertEquals(16, s.words.toSet().size)
                // The masks, recomputed here from the strings.
                val sets = indexSets(s.words, cats.map { it.words })
                assertEquals(sets.map { maskOf(it) }, s.masks)
                assertEquals(cats.filter { c -> s.words.count { it in c.words } >= 3 }.map { it.title }, s.titles)
                val oracle = QuartetsOracle.partitions(16, sets)
                assertEquals("$d seed $seed", 1, oracle.size)
                val split = QuartetsLogic.split(s.masks, s.remaining) as Split.ExactlyOne
                assertEquals(oracle[0].map { maskOf(it) }.toSet(), split.groups.toSet())
                val overlap = overlapOf(s.words, cats.map { it.words }, oracle[0])
                checkGate(d, overlap)
                assertEquals(overlap, QuartetsLogic.overlap(s)!!.total)
                // Every group fits a category of the tier's levels, and one fits a category of the level it needs.
                val levelsOf = oracle[0].map { g -> cats.filter { c -> g.all { s.words[it] in c.words } }.map { it.level } }
                assertTrue(levelsOf.all { ls -> ls.any { it in spec.levels } })
                assertTrue(levelsOf.any { ls -> spec.needsLevel in ls })
            }
        }
    }

    @Test
    fun `a year of seeds per tier deals from the fixture bank`() {
        for (d in Difficulty.entries) {
            val attempts = (0L until 365L).map { day -> QuartetsLogic.deal(20_000L + day * 31, d, bank)?.attempts }
            val dealt = attempts.filterNotNull()
            println("Quartets fixture $d: ${dealt.size}/365 dealt, attempts mean ${"%.1f".format(dealt.average())}, " +
                "max ${dealt.max()} of ${QuartetsLogic.ATTEMPTS}")
            assertEquals("$d fell back", 365, dealt.size)
            for (day in 0L until 365L) {
                val s = QuartetsLogic.generateVerified(20_000L + day * 31, d, bank)!!
                checkGate(d, QuartetsLogic.overlap(s)!!.total)
            }
        }
    }

    @Test
    fun `split agrees with the oracle on random small positions of every verdict`() {
        val rnd = Random(4242)
        val seen = HashSet<String>()
        repeat(600) {
            val n = listOf(4, 8, 8, 12).random(rnd)
            val k = rnd.nextInt(1, 6)
            val sets = (0 until k).map { (0 until n).filter { rnd.nextInt(100) < 45 }.toSet() }.filter { it.size >= 3 }
            val masks = sets.map { maskOf(it) }
            val oracle = QuartetsOracle.partitions(n, sets)
            val split = QuartetsLogic.split(masks, (1 shl n) - 1)
            when (oracle.size) {
                0 -> assertEquals(Split.None, split)
                1 -> assertEquals(oracle[0].map { maskOf(it) }.toSet(), (split as Split.ExactlyOne).groups.toSet())
                else -> assertEquals(Split.Several, split)
            }
            seen += split::class.simpleName!!
        }
        assertEquals(setOf("None", "ExactlyOne", "Several"), seen)
    }

    private fun w(s: QuartetsState, vararg words: String): Int =
        words.fold(0) { acc, word -> acc or (1 shl s.words.indexOf(word).also { require(it >= 0) { word } }) }

    @Test
    fun `guesses, lives, one away, repeats, retry and the end of a board`() {
        val s = QuartetsLogic.lastResort(Difficulty.HARD)
        assertEquals(4, s.livesLeft)
        // A valid four that loses the rest is refused (the planets with MERCURY leave the metals SATURN), one away.
        val greedy = w(s, "mars", "earth", "venus", "mercury")
        assertTrue(QuartetsLogic.valid(s, greedy))
        assertFalse(QuartetsLogic.accepts(s, greedy))
        assertTrue(QuartetsLogic.oneAway(s, greedy))
        val a = s.guess(greedy)!!
        assertEquals(3, a.livesLeft)
        assertEquals(1, a.moves)
        assertNull("a repeat costs nothing", a.guess(greedy))
        assertNull("not four", a.guess(w(s, "mars", "earth", "venus")))
        val scattered = w(s, "mars", "owl", "pear", "iron")
        assertFalse(QuartetsLogic.oneAway(s, scattered))
        // A right group.
        val birds = w(s, "owl", "robin", "crow", "eagle")
        val b = a.guess(birds)!!
        assertEquals(listOf(birds), b.found)
        assertEquals("Birds", QuartetsLogic.titleOf(b, birds))
        assertEquals(s.all and birds.inv(), b.remaining)
        assertNull("already found", b.guess(birds))
        assertNull("a found word", b.guess(w(s, "owl", "mars", "earth", "venus")))
        // Three more misses lose it.
        val lost = listOf(w(s, "mars", "tin", "pear", "copper"), w(s, "mars", "tin", "pear", "iron"), w(s, "plum", "tin", "lemon", "iron"))
            .fold(b) { acc, m -> acc.guess(m)!! }
        assertEquals(0, lost.livesLeft)
        assertTrue(lost.failed)
        assertFalse(lost.solved)
        assertNull(lost.guess(w(s, "apple", "pear", "plum", "lemon")))
        assertEquals(
            setOf(w(s, "apple", "pear", "plum", "lemon"), w(s, "iron", "tin", "copper", "mercury"), w(s, "mars", "earth", "venus", "saturn")),
            QuartetsLogic.reveal(lost).toSet(),
        )
        // Try again: the same board afresh.
        val again = lost.retry()
        assertEquals(s.copy(moves = lost.moves + 1), again)
        assertNotNull("a miss may be tried again after a retry", again.guess(greedy))
        // Solving it.
        val solved = QuartetsLogic.reveal(again).fold(again) { acc, g -> acc.guess(g)!! }
        assertTrue(solved.solved)
        assertFalse(solved.failed)
        assertNull(solved.guess(birds))
    }

    @Test
    fun `a right guess is any four that keeps an answer, not a stored group`() {
        // Two ways to split: A B C D / E F G H, or A B E F / C D G H. Either first four is accepted.
        val words = listOf("aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh")
        val s = QuartetsState(words, listOf("One", "Two", "Three", "Four"), listOf(0x0F, 0xF0, 0x33, 0xCC))
        assertEquals(Split.Several, QuartetsLogic.split(s.masks, s.remaining))
        assertTrue(QuartetsLogic.accepts(s, 0x0F))
        assertTrue(QuartetsLogic.accepts(s, 0x33))
        val t = s.guess(0x33)!!
        assertEquals(listOf(0x33), t.found)
        assertTrue(t.guess(0xCC)!!.solved)
    }

    @Test
    fun `solved checks the rules`() {
        val s = QuartetsLogic.lastResort(Difficulty.STANDARD)
        val groups = QuartetsLogic.reveal(s)
        assertTrue(s.copy(found = groups).solved)
        assertFalse(s.copy(found = groups.dropLast(1)).solved)
        // Four disjoint fours covering the board, but not each one category.
        val wrong = listOf(groups[0] xor (groups[0] and -groups[0]) or (groups[1] and -groups[1])) +
            (groups[1] xor (groups[1] and -groups[1]) or (groups[0] and -groups[0])) + groups.drop(2)
        assertEquals(s.all, wrong.fold(0) { acc, g -> acc or g })
        assertFalse(s.copy(found = wrong).solved)
        assertFalse(s.copy(found = groups + groups[0]).solved)
    }

    @Test
    fun `generation is pure`() {
        for (d in Difficulty.entries) {
            for (seed in listOf(1L, 99L, 123_456_789L)) {
                assertEquals(QuartetsLogic.generateVerified(seed, d, bank), QuartetsLogic.generateVerified(seed, d, bank))
                assertEquals(QuartetsLogic.generate(seed, d), QuartetsLogic.generate(seed, d))
            }
        }
    }
}
