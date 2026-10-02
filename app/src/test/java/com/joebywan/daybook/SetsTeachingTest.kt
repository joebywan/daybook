package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.Card
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.SetsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * The Sets teacher, checked against a rule written out again here on a different principle.
 *
 * [Sets.isSet] asks whether each trait's values form a set of size 1 or 3; this file asks whether
 * each trait's values sum to a multiple of three — the same rule, reached by arithmetic rather than
 * by counting, so the two cannot share a slip. Sets has no hidden answer (every card is face up),
 * so soundness here means: every step names a real set not yet claimed, the card the walk describes
 * is the card it glows, and anything called a mistake really cannot become a new set.
 */
class SetsTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent rules ----------------------------------------------------------------------

    private fun traits(c: Card) = listOf(c.count, c.shape, c.shading, c.colour)

    private fun set(a: Card, b: Card, c: Card) =
        (0..3).all { (traits(a)[it] + traits(b)[it] + traits(c)[it]) % 3 == 0 }

    /** The card completing [a] and [b]: the value each trait needs to sum to a multiple of three. */
    private fun completing(a: Card, b: Card): Card {
        val t = (0..3).map { (6 - traits(a)[it] - traits(b)[it]) % 3 }
        return Card(count = t[0], shape = t[1], shading = t[2], colour = t[3])
    }

    private fun sets(cards: List<Card>): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        for (i in cards.indices) for (j in i + 1 until cards.size) for (k in j + 1 until cards.size) {
            if (set(cards[i], cards[j], cards[k])) out += listOf(i, j, k)
        }
        return out
    }

    private fun open(s: SetsState) = sets(s.cards).filter { it !in s.found }

    private val deck = buildList {
        for (a in 0..2) for (b in 0..2) for (c in 0..2) for (d in 0..2) add(Card(a, b, c, d))
    }

    /** Checks one non-mistake step against the board it was made on. */
    private fun checkStep(label: String, s: SetsState, step: SetsTeacher.Step) {
        assertFalse("$label: a clean board produced a mistake: ${step.explanation}", step.mistake)
        val trio = step.claim
        assertEquals("$label: claim is not three sorted cards", trio.sorted().distinct(), trio)
        assertEquals(3, trio.size)
        assertTrue("$label: $trio is not a set", set(s.cards[trio[0]], s.cards[trio[1]], s.cards[trio[2]]))
        assertFalse("$label: $trio was already found", trio in s.found)
        assertTrue("$label: the claim ignores the player's pick", trio.containsAll(s.selected))
        assertEquals("$label: nudge should glow two cards", 2, step.focus.size)
        assertTrue(trio.containsAll(step.focus))
        assertEquals(trio.toSet() - step.focus, step.targets)
        val (a, b) = step.focus.sorted()
        val third = completing(s.cards[a], s.cards[b])
        assertEquals("$label: the glowing card is not the one the walk finds", s.cards[step.targets.single()], third)
        assertTrue(
            "$label: the walk does not end on the card: ${step.explanation}",
            step.explanation.endsWith("So the third is ${SetsTeacher.describe(third)}."),
        )
    }

    // ---- soundness ------------------------------------------------------------------------------

    @Test
    fun `walked from empty, every step claims a real set not yet found, and the walk names its card`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(100, difficulty, "sets-teach")) {
                var s = Sets.generate(seed, difficulty) as SetsState
                while (!s.solved) {
                    val step = SetsTeacher.teach(s)!!
                    checkStep("$difficulty/$seed", s, step)
                    val d = Sets.teach(s)!!
                    assertFalse(d.isReached(s))
                    s = d.apply(s) as SetsState
                    assertTrue(d.isReached(s))
                }
                assertEquals(sets(s.cards).toSet(), s.found.toSet())
                assertNull("a solved board has nothing to teach", Sets.teach(s))
            }
        }
    }

    /**
     * Boards a player could have made — some sets claimed, a card or two picked, perhaps a pick just
     * rejected — rather than only the teacher's own path. Every step must be sound, and anything
     * called a mistake must really be one.
     */
    @Test
    fun `from boards a player made, steps are sound and mistakes are real`() {
        val seen = mutableMapOf<String, Int>()
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(60, difficulty, "sets-played")) {
                val fresh = Sets.generate(seed, difficulty) as SetsState
                val rng = Random(seed)
                repeat(20) { trial ->
                    val label = "$difficulty/$seed/$trial"
                    val all = sets(fresh.cards)
                    val found = all.filter { rng.nextBoolean() }.take(fresh.target - 1)
                    var s = fresh.copy(found = found)
                    // Tap a few random cards through the real rules.
                    repeat(rng.nextInt(0, 4)) { s = Sets.tap(s, rng.nextInt(s.cards.size)) }
                    if (s.solved) return@repeat
                    val step = SetsTeacher.teach(s)!!
                    seen[step.technique] = (seen[step.technique] ?: 0) + 1
                    if (!step.mistake) {
                        assertFalse("$label: a rejected pick went unmentioned", s.lastWrong)
                        checkStep(label, s, step)
                        return@repeat
                    }
                    if (s.lastWrong) {
                        val trio = s.lastPick.map { s.cards[it] }
                        assertFalse("$label: called a set a mistake", set(trio[0], trio[1], trio[2]))
                        val broken = (0..3).filter { t -> trio.map { traits(it)[t] }.distinct().size == 2 }
                        val name = listOf("count", "shape", "shading", "colour")
                        // The first trait the explanation names must really be broken.
                        val named = name.filter { "and $it must be" in step.explanation }
                        assertEquals("$label: ${step.explanation}", 1, named.size)
                        assertTrue("$label: ${step.explanation}", name.indexOf(named[0]) in broken)
                        assertEquals(s.lastPick.toSet(), step.focus)
                    } else {
                        assertTrue(
                            "$label: picked cards ${s.selected} lead to a new set but were called a mistake",
                            open(s).none { it.containsAll(s.selected) },
                        )
                        assertEquals(s.selected, step.release)
                    }
                }
            }
        }
        println("played-board steps: $seen")
        assertTrue("never met a rejected pick or a dead pick", (seen[SetsTeacher.MISTAKE] ?: 0) > 50)
        assertTrue("never built on a pick", (seen[SetsTeacher.CONTINUE] ?: 0) > 50)
    }

    @Test
    fun `a pick that leads to a new set is never called a mistake`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(30, difficulty, "sets-onpath")) {
                val s = Sets.generate(seed, difficulty) as SetsState
                for (trio in sets(s.cards)) {
                    for (pick in listOf(listOf(trio[0]), listOf(trio[2], trio[1]))) {
                        val step = SetsTeacher.teach(pick.fold(s, Sets::tap))!!
                        assertEquals(SetsTeacher.CONTINUE, step.technique)
                        assertTrue(step.claim.containsAll(pick))
                    }
                }
            }
        }
    }

    // ---- coverage -------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/sets-teaching-coverage.txt`: how often
     * a hint's set is a fresh one and how often it has to run through a used card. There is no
     * fallback, and that is asserted rather than assumed.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 500
        val report = StringBuilder()
        report.appendLine("Sets teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = SetsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = SetsTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var longest = ""
            for (seed in seeds(perTier, difficulty, "sets-coverage")) {
                var s = Sets.generate(seed, difficulty) as SetsState
                val used = mutableSetOf<String>()
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 20)
                    val d = Sets.teach(s)
                    assertNotNull("$difficulty/$seed: no step on an unsolved board (a fallback would be needed)", d)
                    d!!
                    assertFalse(d.fallback)
                    if (d.explanation.length > longest.length) longest = d.explanation
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as SetsState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${difficulty.name}: $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board, fallback 0")
            for (t in SetsTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-10s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine("  longest explanation, ${longest.length} characters: $longest")
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/sets-teaching-coverage.txt").writeText(report.toString())
    }

    // ---- the panel's four lines -----------------------------------------------------------------

    /** Exhaustive: every pair of cards in the deck, and every trio that is not a set. */
    @Test
    fun `every explanation and nudge fits the panel`() {
        val limit = 200
        var longest = ""
        for (a in deck) for (b in deck) {
            if (a == b) continue
            val walk = SetsTeacher.walkToThird(a, b)
            if (walk.length > longest.length) longest = walk
            // The pair-with-no-third mistake names the card too.
            val missing = "Only ${SetsTeacher.describe(completing(a, b))} would finish these two, and that card " +
                "isn't here. Tap them again to let go."
            assertTrue(missing, missing.length <= limit)
        }
        assertTrue("${longest.length}: $longest", longest.length <= limit)
        var longestWrong = ""
        for (i in deck.indices) for (j in i + 1 until deck.size) for (k in j + 1 until deck.size) {
            if (set(deck[i], deck[j], deck[k])) continue
            val text = "Not a set: " + SetsTeacher.notASet(listOf(deck[i], deck[j], deck[k])).replaceFirstChar { it.lowercaseChar() }
            if (text.length > longestWrong.length) longestWrong = text
        }
        assertTrue("${longestWrong.length}: $longestWrong", longestWrong.length <= limit)
        println("longest walk ${longest.length}: $longest")
        println("longest not-a-set ${longestWrong.length}: $longestWrong")

        // Every nudge and explanation actually produced on real and played boards.
        for (difficulty in Difficulty.entries) for (seed in seeds(40, difficulty, "sets-length")) {
            var s = Sets.generate(seed, difficulty) as SetsState
            val rng = Random(seed)
            repeat(40) {
                val step = SetsTeacher.teach(s) ?: return@repeat
                assertTrue(step.nudge, step.nudge.length <= 80)
                assertTrue(step.explanation, step.explanation.length <= limit)
                s = Sets.tap(s, rng.nextInt(s.cards.size))
            }
        }
        for (frame in Sets.tutorial) {
            assertTrue("caption too long (${frame.caption.length}): ${frame.caption}", frame.caption.length <= limit)
        }
    }

    /** The owner's ask: the rules page leads with the one test a set must pass. */
    @Test
    fun `rules open with the set test`() {
        assertTrue(Sets.rules.first(), Sets.rules.first().contains("all the same or all different"))
    }

    /**
     * The owner's ask for the walkthrough: the four traits one at a time (each on three cards that
     * show all three of its values), then the rule stated outright, then an example, then the
     * near miss, and only then the guided taps.
     */
    @Test
    fun `the walkthrough introduces each trait, then states the rule, before any tap`() {
        val frames = Sets.tutorial
        val cards = Sets.TUTORIAL_CARDS
        val intros = listOf(
            Sets.TUTORIAL_SHAPES to { c: Card -> c.shape },
            Sets.TUTORIAL_COLOURS to { c: Card -> c.colour },
            Sets.TUTORIAL_SHADINGS to { c: Card -> c.shading },
            Sets.TUTORIAL_NUMBERS to { c: Card -> c.count },
        )
        intros.forEachIndexed { i, (shown, trait) ->
            assertEquals("frame ${i + 1} glows its three cards", shown.toSet(), frames[i].highlight.strong)
            assertEquals("frame ${i + 1} shows all three values", setOf(0, 1, 2), shown.map { trait(cards[it]) }.toSet())
            assertNull(frames[i].accepts)
        }
        assertTrue(frames[4].caption, frames[4].caption.contains("all the same or all different"))
        assertTrue(frames[4].highlight.isEmpty)
        assertEquals(Sets.TUTORIAL_SET.toSet(), frames[5].highlight.strong)
        assertTrue(frames[6].highlight.warning)
        assertTrue("no tap before frame 8", frames.take(7).all { it.accepts == null })
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private val tutorialBoard = SetsState(Sets.TUTORIAL_CARDS, 2, emptyList(), emptyList())

    @Test
    fun `a rejected pick is addressed first, naming the trait that broke it`() {
        val s = Sets.TUTORIAL_NEAR_MISS.fold(tutorialBoard, Sets::tap)
        assertTrue(s.lastWrong)
        assertEquals(Sets.TUTORIAL_NEAR_MISS, s.lastPick)
        val d = Sets.teach(s)!!
        assertTrue(d.mistake)
        assertEquals(Sets.TUTORIAL_NEAR_MISS.toSet(), d.focus)
        assertEquals(
            "Not a set: two are striped and one is solid, and shading must be all alike or all different.",
            d.explanation,
        )
        assertFalse(d.isReached(s))
        assertSame("nothing to take back: Show me must not cost an undo entry", s, d.apply(s))
        // Moving on is what clears it.
        assertTrue(d.isReached(Sets.tap(s, 0)))
    }

    @Test
    fun `two picked cards whose third is missing are let go, and the missing card is named`() {
        for (difficulty in Difficulty.entries) for (seed in seeds(20, difficulty, "sets-missing")) {
            val fresh = Sets.generate(seed, difficulty) as SetsState
            val pair = fresh.cards.indices.flatMap { i -> (i + 1 until fresh.cards.size).map { listOf(i, it) } }
                .first { (i, j) -> completing(fresh.cards[i], fresh.cards[j]) !in fresh.cards }
            val s = pair.fold(fresh, Sets::tap)
            val d = Sets.teach(s)!!
            assertTrue(d.mistake)
            assertTrue(d.explanation, SetsTeacher.describe(completing(s.cards[pair[0]], s.cards[pair[1]])) in d.explanation)
            val fixed = d.apply(s) as SetsState
            assertEquals(emptyList<Int>(), fixed.selected)
            assertTrue(d.isReached(fixed))
            assertTrue("letting go by tapping counts", d.isReached(Sets.tap(s, pair[0])))
        }
    }

    @Test
    fun `a pair from a set already found is let go, and so is a card with no set left`() {
        val found = tutorialBoard.copy(found = listOf(listOf(0, 4, 5), listOf(1, 3, 5)))
        // Not solved only because the target is raised; the point is the pick.
        val board = found.copy(target = 3)
        val pair = Sets.teach(Sets.tap(Sets.tap(board, 1), 3))!!
        assertTrue(pair.mistake)
        assertTrue(pair.explanation, "two cards only ever make one set" in pair.explanation)
        // Card 2 is in no set at all on the walkthrough board.
        val lone = Sets.teach(Sets.tap(tutorialBoard, 2))!!
        assertTrue(lone.mistake)
        assertTrue(lone.explanation, "isn't in any set" in lone.explanation)
    }

    @Test
    fun `an old save without the rejected pick still loads`() {
        val s = Sets.TUTORIAL_NEAR_MISS.fold(tutorialBoard, Sets::tap)
        val json = SavedGame(s).encode()
        assertTrue(json, "lastPick" in json)
        val old = json.replace(Regex(",?\"lastPick\":\\[[0-9,]*]"), "")
        assertFalse(old, "lastPick" in old)
        assertEquals(s.copy(lastPick = emptyList()), SavedGame.decode(old)?.state)
    }

    // ---- the walkthrough ------------------------------------------------------------------------

    @Test
    fun `the walkthrough board holds exactly the two sets it teaches, and the near miss breaks on shading only`() {
        val cards = Sets.TUTORIAL_CARDS
        assertEquals(listOf(listOf(0, 4, 5), listOf(1, 3, 5)), sets(cards))
        assertTrue(Sets.TUTORIAL_SET in sets(cards))
        assertEquals(sets(cards).size, tutorialBoard.target)
        val near = Sets.TUTORIAL_NEAR_MISS.map { cards[it] }
        val broken = (0..3).filter { t -> near.map { traits(it)[t] }.distinct().size == 2 }
        assertEquals("only shading breaks", listOf(2), broken)
    }

    @Test
    fun `each walkthrough frame accepts its tap and rejects a wrong one`() {
        val frames = Sets.tutorial
        assertEquals(14, frames.size)
        fun board(i: Int) = frames[i].state as SetsState
        listOf(0, 1, 2, 3, 4, 5, 6, 12).forEach { assertNull("frame ${it + 1} should be Next-only", frames[it].accepts) }

        // Each gesture frame: the intended tap is accepted and lands on the next frame's board;
        // a tap on another card is not.
        val gestures = listOf(7 to 2, 8 to 2, 9 to 1, 10 to 3, 11 to 5)
        for ((f, card) in gestures) {
            val accepts = frames[f].accepts!!
            val next = Sets.tap(board(f), card)
            assertTrue("frame ${f + 1}: tap on $card rejected", accepts(next))
            assertEquals("frame ${f + 1} does not lead to frame ${f + 2}", board(f + 1).selected, next.selected)
            assertEquals(board(f + 1).found, next.found)
            for (other in board(f).cards.indices) {
                if (other == card) continue
                assertFalse("frame ${f + 1}: tap on $other accepted", accepts(Sets.tap(board(f), other)))
            }
        }
        // The pick frames name the card they glow; the last one walks to the card without glowing it.
        assertTrue(frames[11].highlight.strong.isEmpty())
        assertTrue(frames[11].caption.startsWith(SetsTeacher.walkToThird(board(11).cards[1], board(11).cards[3])))

        // 14: free play, finished by hints alone, through the used card.
        assertTrue(frames[13].freePlay)
        var s = board(13)
        val first = SetsTeacher.teach(s)!!
        assertEquals(SetsTeacher.REUSE, first.technique)
        while (!s.solved) s = Sets.teach(s)!!.apply(s) as SetsState
        assertEquals(sets(s.cards).toSet(), s.found.toSet())
    }
}
