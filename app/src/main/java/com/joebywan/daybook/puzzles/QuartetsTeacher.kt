package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep

/**
 * Quartets' hints (`docs/QUARTETS_SPEC.md`, Hints), reasoned from what the board holds: the words left and which of them
 * each bank category fits. That is public knowledge about words, as Word Wheel's grid list is; the state stores no
 * answer, and a category fitting four of the words left is not thereby a group (that is the whole game).
 *
 * A theme is *live* when four or more of the words left fit it: only a live theme can hold a group. Themes that fit the
 * very same words are one theme here (the first title names it). A word is a *loner* of a theme when no other live
 * theme fits it: its group can only be four of that theme's words.
 *
 * - [LAST_FOUR]: four words are left.
 * - [EXACTLY_FOUR]: a theme fits exactly four of the words left and one of them is a loner, so its group is those four.
 * - [SPARE]: a theme fits five to seven of the words left and four of them are loners. Seven words cannot hold two
 *   groups, so the loners share one group: those four. The others are spares, needed elsewhere.
 * - [EVERY_SPLIT]: none of those. The fallback: works through every way to split the words left ([QuartetsLogic.split]),
 *   names one group of the only split and says so.
 *
 * Every step ends in one group the player submits, names at most one word of it and glows at most that tile (the four
 * would be the answer); only "Show me" submits it. No mistake exists: a refused four is spent, not on the board.
 *
 * Highlight indices: tile `i` is the index into [QuartetsState.words]; Submit is [QuartetsLogic.SUBMIT].
 */
internal object QuartetsTeacher {

    const val LAST_FOUR = "last-four"
    const val EXACTLY_FOUR = "exactly-four"
    const val SPARE = "spare"
    const val EVERY_SPLIT = "every-split"

    /** Every step [teach] can produce, simplest first. */
    val TECHNIQUES = listOf(LAST_FOUR, EXACTLY_FOUR, SPARE, EVERY_SPLIT)

    const val MAX_NUDGE = 80
    const val MAX_EXPLANATION = 200

    /** The most words a theme may fit for its loners to be sure of sharing one group: eight could hold two. */
    private const val ONE_GROUP_MOST = 2 * QuartetsLogic.GROUP - 1

    class Step(
        override val technique: String,
        /** The group the step settles: what the player submits, and what "Show me" submits. */
        val group: Int,
        override val nudge: String,
        override val explanation: String,
        override val focus: Set<Int>,
        override val cited: Set<Int> = emptySet(),
    ) : TeachStep {
        override val targets: Set<Int> get() = focus + QuartetsLogic.SUBMIT
    }

    /** A live theme: the words left it fits, and the title it is named by. */
    private class Theme(val fit: Int, val title: String)

    /** The live themes over [remaining], one per distinct set of words, in title order. */
    private fun live(titles: List<String>, masks: List<Int>, remaining: Int): List<Theme> {
        val out = ArrayList<Theme>()
        for (i in masks.indices) {
            val fit = masks[i] and remaining
            if (fit.countOneBits() < QuartetsLogic.GROUP || out.any { it.fit == fit }) continue
            out += Theme(fit, titles[i])
        }
        return out
    }

    /** The words of [theme] no other live theme fits. */
    private fun loners(theme: Theme, themes: List<Theme>): Int {
        var others = 0
        for (t in themes) if (t !== theme) others = others or t.fit
        return theme.fit and others.inv()
    }

    /**
     * A group forced by what the board shows. No parameter through which an answer could arrive: the words, the themes
     * that fit them, and which words are left.
     */
    fun deduce(words: List<String>, titles: List<String>, masks: List<Int>, remaining: Int): Step? {
        val left = remaining.countOneBits()
        if (left < QuartetsLogic.GROUP) return null
        val themes = live(titles, masks, remaining)
        if (left == QuartetsLogic.GROUP) {
            val title = themes.firstOrNull { it.fit == remaining }?.title
            return Step(
                LAST_FOUR, remaining,
                nudge = "Only four words are left.",
                explanation = if (title == null) "Only four words are left, so they are the last group."
                else "Only four words are left, so they are the last group: ${quote(title)}.",
                focus = emptySet(),
            )
        }
        // A theme of exactly four with a loner: the loner's group is inside the theme, which is four words.
        var best: Theme? = null
        var bestLoners = 0
        for (t in themes) {
            if (t.fit.countOneBits() != QuartetsLogic.GROUP) continue
            val n = loners(t, themes).countOneBits()
            if (n > bestLoners) { best = t; bestLoners = n }
        }
        best?.let { t ->
            val w = lowest(loners(t, themes))
            val word = words[w].uppercase()
            return Step(
                EXACTLY_FOUR, t.fit,
                nudge = "Exactly four of the words left fit ${quote(t.title)}.",
                explanation = "Only four words left fit ${quote(t.title)}. $word fits no other theme with four words left, " +
                    "so its group can only be those four.",
                focus = setOf(w),
            )
        }
        // A theme of five to seven with four loners: they share one group, which can only be them.
        for (t in themes) {
            val n = t.fit.countOneBits()
            if (n <= QuartetsLogic.GROUP || n > ONE_GROUP_MOST) continue
            val lone = loners(t, themes)
            if (lone.countOneBits() != QuartetsLogic.GROUP) continue
            val spares = bits(t.fit and lone.inv())
            val first = spares[0]
            val elsewhere = themes.first { it !== t && it.fit and (1 shl first) != 0 }
            val spareWord = words[first].uppercase()
            val count = NUMBERS[n]
            return Step(
                SPARE, lone,
                nudge = "$count words left fit ${quote(t.title)}; only four can be its group.",
                explanation = if (spares.size == 1) {
                    "$count words left fit ${quote(t.title)}. $spareWord also fits ${quote(elsewhere.title)}; the other four fit " +
                        "nothing else, so they are the group."
                } else {
                    "$count words left fit ${quote(t.title)}. Four of them fit no other theme, so they are its group; " +
                        "$spareWord and ${if (spares.size == 2) "one other word" else "two others"} go elsewhere."
                },
                focus = setOf(first),
                cited = setOf(first),
            )
        }
        return null
    }

    fun teach(s: QuartetsState): Step? {
        if (s.solved || s.failed) return null
        return deduce(s.words, s.titles, s.masks, s.remaining) ?: everySplit(s)
    }

    /**
     * Nothing short settles a group: the one way to split the words left, worked through ([QuartetsLogic.split]), and one
     * of its groups named by its theme and one word. Said openly; null only on a board with no split left (never one
     * played by the rules, which refuse a four that loses every split).
     */
    private fun everySplit(s: QuartetsState): Step? {
        val groups = QuartetsLogic.reveal(s)
        // The group named by the theme that fits fewest words: the most telling title.
        val g = groups.minByOrNull { g -> s.masks.filter { it and g == g }.minOfOrNull { (it and s.remaining).countOneBits() } ?: 99 }
            ?: return null
        val title = QuartetsLogic.titleOf(s, g)
        val w = lowest(g)
        // A board played here splits one way; one built otherwise may split several, and the words must not say "only".
        val only = QuartetsLogic.split(s.masks, s.remaining) is QuartetsLogic.Split.ExactlyOne
        return Step(
            EVERY_SPLIT, g,
            nudge = "No one theme settles a group here. Try every way to split the words.",
            explanation = "No short reason settles a group, so the hint worked through every way to split the words left. " +
                "In ${if (only) "the only one" else "one"} that works, ${words(s, w)} is in the ${quote(title)} group.",
            focus = setOf(w),
        )
    }

    private fun words(s: QuartetsState, i: Int) = s.words[i].uppercase()

    /** “Planets”: a title quoted, so wordplay titles (`___ fish`, `Add S at the front`) read as themselves. */
    private fun quote(title: String) = "“$title”"

    private fun lowest(mask: Int): Int = mask.countTrailingZeroBits()

    private fun bits(mask: Int): List<Int> = (0 until 32).filter { mask and (1 shl it) != 0 }

    private val NUMBERS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight")

    /** The [Deduction] for the play screen: "Show me" submits the group; it is reached once the group is found. */
    fun deduction(s: QuartetsState): Deduction? {
        val step = teach(s) ?: return null
        return Deduction(
            step,
            fallback = step.technique == EVERY_SPLIT,
            applyTo = { now -> (now as QuartetsState).let { t -> if (step.group in t.found) t else t.guess(step.group) ?: t } },
            reachedBy = { now -> step.group in (now as QuartetsState).found },
        )
    }
}
