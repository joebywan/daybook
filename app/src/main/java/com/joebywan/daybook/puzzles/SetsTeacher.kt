package com.joebywan.daybook.puzzles

/**
 * Sets, taught the way a person finds a set: pick two cards, and work out trait by trait what the
 * third must be.
 *
 * Any two cards fix their third exactly — for each trait, two alike need a third alike, two
 * different need the one value left over — so finding a set is not a search over triples but a
 * walk over four traits, and that walk is what the explanation shows. It defines one card, and the
 * hint only ever opens a pair whose card is on the table.
 *
 * Sets has no hidden answer: every card is face up, and the sets still to find follow from the
 * cards and the ones already claimed. So [deduce] and [mistake] reason from the board as the player
 * sees it (cards, found sets, picked cards, and the pick the board just rejected), and there is no
 * fallback — an unfound set is always one the walk can explain.
 *
 * Steps, simplest first:
 * 1. [MISTAKE] — the pick the board just rejected, with the trait that broke it; or picked cards
 *    that cannot end in a new set, which are let go.
 * 2. [CONTINUE] — the player has picked one or two cards that do lead to a new set; build on them.
 * 3. [FRESH] — a set none of whose cards has been used yet.
 * 4. [REUSE] — only sets through a tinted card are left; the nudge says a used card still counts.
 *
 * Orders never come from a hash: sets are ranked by an explicit key, then by index.
 */
internal object SetsTeacher {

    const val MISTAKE = "mistake"
    const val CONTINUE = "continue"
    const val FRESH = "fresh"
    const val REUSE = "reuse"

    /** Every technique, for reports. There is no fallback; see the class comment. */
    val TECHNIQUES = listOf(MISTAKE, CONTINUE, FRESH, REUSE)

    /**
     * One step. [claim] is the set to claim (sorted), empty for a mistake; [release] the picked
     * cards a mistake asks the player to let go, empty when there is nothing to take back.
     */
    class Step(
        val technique: String,
        val claim: List<Int> = emptyList(),
        val release: List<Int> = emptyList(),
        /** For a rejected pick: the three cards, so [isReached] can tell when the player moved on. */
        val rejected: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val mistake: Boolean get() = technique == MISTAKE
    }

    // ---- words -----------------------------------------------------------------------------------

    private val counts = listOf("one", "two", "three")
    private val shadings = listOf("solid", "outlined", "striped")
    private val colourNames = listOf("red", "blue", "green")
    private val shapes = listOf("oval", "diamond", "rectangle")
    private val shapesWithArticle = listOf("an oval", "a diamond", "a rectangle")

    /** "three outlined green ovals": count, shading, colour, shape — the order the walk runs in. */
    fun describe(c: Card): String =
        "${counts[c.count]} ${shadings[c.shading]} ${colourNames[c.colour]} ${shapes[c.shape]}" +
            if (c.count > 0) "s" else ""

    /** The one card that makes a set with [a] and [b]. */
    fun third(a: Card, b: Card): Card {
        fun v(x: Int, y: Int) = if (x == y) x else 3 - x - y
        return Card(v(a.count, b.count), v(a.shape, b.shape), v(a.shading, b.shading), v(a.colour, b.colour))
    }

    /** One clause per trait, in [describe]'s order: what [a] and [b] say the third must be. */
    private fun walk(a: Card, b: Card): String {
        val t = third(a, b)
        val parts = listOf(
            if (a.count == b.count) "both have ${counts[a.count]}"
            else "${counts[a.count]} and ${counts[b.count]} need ${counts[t.count]}",
            if (a.shading == b.shading) "both are ${shadings[a.shading]}"
            else "${shadings[a.shading]} and ${shadings[b.shading]} need ${shadings[t.shading]}",
            if (a.colour == b.colour) "both are ${colourNames[a.colour]}"
            else "${colourNames[a.colour]} and ${colourNames[b.colour]} need ${colourNames[t.colour]}",
            if (a.shape == b.shape) "both are ${shapes[a.shape]}s"
            else "${shapesWithArticle[a.shape]} and ${shapesWithArticle[b.shape]} need ${shapesWithArticle[t.shape]}",
        )
        return parts.joinToString("; ").cap()
    }

    /** The walk from [a] and [b], ending on the card it defines. Used by hints and the walkthrough. */
    fun walkToThird(a: Card, b: Card): String = "${walk(a, b)}. So the third is ${describe(third(a, b))}."

    private val traitNames = listOf("count", "shading", "colour", "shape")

    private fun traitValue(c: Card, trait: Int) = when (trait) {
        0 -> c.count
        1 -> c.shading
        2 -> c.colour
        else -> c.shape
    }

    /** Traits, in [traitNames] order, on which [trio] is neither all alike nor all different. */
    fun brokenTraits(trio: List<Card>): List<Int> = (0..3).filter { t ->
        trio.map { traitValue(it, t) }.distinct().size == 2
    }

    /** "two are striped and one is solid", for a trait on which two of [trio] agree. */
    fun whyBroken(trio: List<Card>, trait: Int): String {
        val values = trio.map { traitValue(it, trait) }
        val pair = values.first { v -> values.count { it == v } == 2 }
        val odd = values.first { it != pair }
        return when (trait) {
            0 -> "two show ${counts[pair]} and one shows ${counts[odd]}"
            1 -> "two are ${shadings[pair]} and one is ${shadings[odd]}"
            2 -> "two are ${colourNames[pair]} and one is ${colourNames[odd]}"
            else -> "two are ${shapes[pair]}s and one is ${shapesWithArticle[odd]}"
        }
    }

    /** The whole sentence for a trio that is not a set. Shared with the walkthrough. */
    fun notASet(trio: List<Card>): String {
        val broken = brokenTraits(trio)
        val first = broken.first()
        val also = broken.drop(1).map { traitNames[it] }
        val tail = when (also.size) {
            0 -> ""
            1 -> " ${also[0].cap()} breaks it too."
            else -> " ${also.dropLast(1).joinToString(", ").cap()} and ${also.last()} break it too."
        }
        return "${whyBroken(trio, first).cap()}, and ${traitNames[first]} must be all alike or all different.$tail"
    }

    // ---- the whole hint --------------------------------------------------------------------------

    /** The next thing to show, or null on a solved board. */
    fun teach(s: SetsState): Step? {
        if (s.solved) return null
        return mistake(s) ?: deduce(s.cards, s.found, s.selected)
    }

    /**
     * The pick the board just rejected, or picked cards that cannot end in a set not yet claimed.
     * Everything here is on screen; nothing is compared with a stored answer.
     */
    fun mistake(s: SetsState): Step? {
        if (s.lastWrong && s.lastPick.size == 3) {
            val trio = s.lastPick.map { s.cards[it] }
            return Step(
                technique = MISTAKE,
                rejected = s.lastPick,
                focus = s.lastPick.toSet(),
                cited = emptySet(),
                targets = s.lastPick.toSet(),
                nudge = "Check the three you just picked.",
                explanation = "Not a set: ${notASet(trio).replaceFirstChar { it.lowercaseChar() }}",
            )
        }
        val open = openSets(s.cards, s.found)
        when (s.selected.size) {
            1 -> {
                val card = s.selected[0]
                if (open.any { card in it }) return null
                val anywhere = Sets.allSets(s.cards).any { card in it }
                return Step(
                    technique = MISTAKE,
                    release = s.selected,
                    focus = s.selected.toSet(),
                    cited = emptySet(),
                    targets = s.selected.toSet(),
                    nudge = "Check the card you picked.",
                    explanation = if (anywhere) {
                        "Every set this card is in has been found already. Tap it again to let it go."
                    } else {
                        "This card isn't in any set on this board. Tap it again to let it go."
                    },
                )
            }
            2 -> {
                val (a, b) = s.selected
                val need = third(s.cards[a], s.cards[b])
                val c = s.cards.indexOf(need)
                if (c >= 0 && listOf(a, b, c).sorted() !in s.found) return null
                return Step(
                    technique = MISTAKE,
                    release = s.selected,
                    focus = s.selected.toSet(),
                    cited = if (c >= 0) setOf(c) else emptySet(),
                    targets = s.selected.toSet(),
                    nudge = "Check the two cards you picked.",
                    explanation = if (c < 0) {
                        "Only ${describe(need)} would finish these two, and that card isn't here. " +
                            "Tap them again to let go."
                    } else {
                        "These two are in a set you've found, and two cards only ever make one set. " +
                            "Tap them again to let go."
                    },
                )
            }
        }
        return null
    }

    /** Sets on the board not yet claimed, in index order. */
    private fun openSets(cards: List<Card>, found: List<List<Int>>): List<List<Int>> =
        Sets.allSets(cards).filter { it !in found }

    /**
     * The step to take next, reasoned from the cards, the sets already claimed and the cards the
     * player has picked. [selected] must already be known not to be a mistake.
     */
    fun deduce(cards: List<Card>, found: List<List<Int>>, selected: List<Int>): Step? {
        val open = openSets(cards, found)
        if (open.isEmpty()) return null
        val used = found.flatten().toSet()

        if (selected.isNotEmpty()) {
            val set = open.firstOrNull { it.containsAll(selected) } ?: return null
            val pair = if (selected.size == 2) selected else listOf(selected[0], set.first { it != selected[0] })
            val third = set.first { it !in pair }
            return Step(
                technique = CONTINUE,
                claim = set,
                focus = pair.toSet(),
                cited = pair.toSet(),
                targets = setOf(third),
                nudge = if (selected.size == 2) "Your two cards are good. What finishes them?"
                else "Try your card with this one.",
                explanation = walkToThird(cards[pair[0]], cards[pair[1]]),
            )
        }

        // Fewest used cards first (a fresh set is the plain case), then the most traits alike,
        // which is the set a person spots first. Ties go to index order.
        // sortedWith is stable, so ties keep the index order allSets gives.
        val set = open.sortedWith(
            compareBy<List<Int>>({ t -> t.count { it in used } }, { t -> -alike(t.map { cards[it] }) })
        ).first()
        val reused = set.filter { it in used }
        // Show a used card in the pair when there is one, so the nudge that says "a tinted card
        // still counts" is pointing at one.
        val pair = if (reused.isNotEmpty() && reused.size < 3) {
            listOf(reused[0], set.first { it !in used })
        } else {
            set.take(2)
        }
        val third = set.first { it !in pair }
        return Step(
            technique = if (reused.isEmpty()) FRESH else REUSE,
            claim = set,
            focus = pair.toSet(),
            cited = pair.toSet(),
            targets = setOf(third),
            nudge = if (reused.isEmpty()) "Look at these two." else "Look at these two. A tinted card still counts.",
            explanation = walkToThird(cards[pair[0]], cards[pair[1]]),
        )
    }

    /** How many traits a trio shares outright (all alike). */
    private fun alike(trio: List<Card>): Int = (0..3).count { t -> trio.map { traitValue(it, t) }.distinct().size == 1 }

    // ---- the move --------------------------------------------------------------------------------

    /** "Show me": claim the set, or let go of the picked cards. One state, so one undo entry. */
    fun apply(s: SetsState, step: Step): SetsState = when {
        step.claim.isNotEmpty() -> if (step.claim in s.found) s else s.copy(
            found = s.found + listOf(step.claim),
            selected = emptyList(),
            lastWrong = false,
            lastRepeat = false,
            lastPick = emptyList(),
            moves = s.moves + 1,
        )
        step.release.isNotEmpty() -> if (s.selected.none { it in step.release }) s else s.copy(
            selected = s.selected - step.release.toSet(),
            lastWrong = false,
            lastRepeat = false,
            lastPick = emptyList(),
            moves = s.moves + 1,
        )
        // A rejected pick has already been undone by the board; there is nothing to take back, and
        // handing back the same state keeps "Show me" from costing an undo entry.
        else -> s
    }

    /** Whether [s] already has this step's result, however the player got there. */
    fun isReached(s: SetsState, step: Step): Boolean = when {
        step.claim.isNotEmpty() -> step.claim in s.found
        step.release.isNotEmpty() -> !s.selected.containsAll(step.release)
        else -> !(s.lastWrong && s.lastPick == step.rejected)
    }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
