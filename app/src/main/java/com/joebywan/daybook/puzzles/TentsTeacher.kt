package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction

/**
 * Tents, reasoned a fact at a time.
 *
 * [deduce] is handed what the player can see (trees, clues, their marks) and nothing else, so a step it
 * explains cannot lean on the stored answer. The answer is read only in [teach]: to find a tent mark that
 * no answer keeps (sound because a shipped board has one answer, as Inequality's) and for [FALLBACK], which
 * says openly that it points at the answer.
 *
 * It mirrors [TentsLogic.deduce]: the same five rules, but one fact (a tent, or a group of squares to cross
 * out) at a time, in this order, restarting after each:
 * 1. [NO_TREE] squares with no tree beside them are grass;
 * 2. [LINE_DONE] / [LINE_FULL] a row or column with all its tents placed (rest grass) or with exactly as
 *    many possible squares as tents still owed (all tents);
 * 3. [TOUCH] squares around a tent are grass;
 * 4. [ONE_SPOT] a tree with one square left for its tent;
 * 5. [SHARED] a square a tent on which would touch every square its tree could use;
 * 6. [SERVED] a square whose trees already all have their tents.
 *
 * Knowledge starts from the player's *tent* marks (once they are not mistakes) and what the rules give;
 * crosses are notes and never read as knowledge. A fact the board already shows (every target marked) is
 * skipped, so the hint is the first thing the player has not yet put down. Every fact is explained, so a
 * board the generator shipped (the ladder finishes it) never reaches [FALLBACK]; `TentsTeachingTest` measures.
 *
 * Highlight indices: squares `0 until n*n`, row clue r = `n*n + r`, column clue c = `n*n + n + c`.
 */
internal object TentsTeacher {

    const val NO_TREE = "no-tree"
    const val LINE_DONE = "line-done"
    const val LINE_FULL = "line-full"
    const val TOUCH = "touch"
    const val ONE_SPOT = "one-spot"
    const val SHARED = "shared"
    const val SERVED = "served"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(NO_TREE, LINE_DONE, LINE_FULL, TOUCH, ONE_SPOT, SHARED, SERVED, FALLBACK)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    private const val TENT = TentsLogic.TENT
    private const val GRASS = TentsLogic.GRASS

    class Step(
        val technique: String,
        /** What the targets become: TENT, GRASS, or 0 to clear (a mistake). */
        val value: Int,
        val targets: Set<Int>,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ---------------------------

    fun teach(s: TentsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        deduce(s.size, s.trees, s.rowCounts, s.colCounts, s.seen)?.let { return it }
        val i = s.solution.indices.firstOrNull { s.solution[it] && s.marks[it] != TENT } ?: return null
        return Step(
            FALLBACK, TENT, setOf(i), setOf(i), emptySet(),
            "Try this square.",
            "No short reason found: the stored answer has a tent here.",
        )
    }

    /** The [Deduction] for the play screen; `Tents.teach` is just this. */
    fun deduction(s: TentsState): Deduction? {
        val step = teach(s) ?: return null
        val mistake = step.technique == MISTAKE
        val v = step.value
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = mistake,
            fallback = step.technique == FALLBACK,
            applyTo = { now -> step.targets.fold(now as TentsState) { acc, i -> acc.withMark(i, v) } },
            reachedBy = { now ->
                val t = now as TentsState
                if (mistake) step.targets.all { t.marks[it] != TENT } else step.targets.all { t.seen[it] == v }
            },
        )
    }

    // ---- mistakes -------------------------------------------------------------------------------------

    private fun mistake(s: TentsState): Step? {
        val n = s.size
        val wrong = s.marks.indices.firstOrNull { s.marks[it] == TENT && !s.solution[it] } ?: return null
        val tents = s.marks.map { it == TENT }
        fun step(why: String, cited: Set<Int>) =
            Step(MISTAKE, 0, setOf(wrong), setOf(wrong), cited, "Check this tent.", "$why Tap it to clear it.")
        if (TentsLogic.orth(n, wrong).none { s.trees[it] }) return step("This tent has no tree beside it.", emptySet())
        val near = TentsLogic.around(n, wrong).filter { tents[it] }
        if (near.isNotEmpty()) return step("This tent touches another tent.", near.toSet())
        val r = wrong / n
        val c = wrong % n
        if (s.rowTents(r) > s.rowCounts[r]) return step("Row ${r + 1} only holds ${s.rowCounts[r]}, and it has more.", setOf(n * n + r))
        if (s.colTents(c) > s.colCounts[c]) return step("Column ${c + 1} only holds ${s.colCounts[c]}, and it has more.", setOf(n * n + n + c))
        return step("No valid way to finish the board keeps this tent.", emptySet())
    }

    // ---- reasoning ------------------------------------------------------------------------------------

    private fun lineName(n: Int, line: Int) = if (line < n) "row ${line + 1}" else "column ${line - n + 1}"
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun plural(k: Int, word: String) = if (k == 1) "1 $word" else "$k ${word}s"

    /** The first fact the ladder gives that the board does not already show, or null when it has no more. */
    fun deduce(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, marks: List<Int>): Step? {
        val st = IntArray(n * n)
        for (i in st.indices) if (!trees[i] && marks[i] == TENT) st[i] = TENT
        while (true) {
            val f = nextFact(n, trees, rowCounts, colCounts, st) ?: return null
            for (i in f.targets) st[i] = f.value
            if (!f.targets.all { marks[it] == f.value }) return f
        }
    }

    private fun nextFact(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, st: IntArray): Step? {
        fun free(i: Int) = !trees[i] && st[i] == 0
        val treeIdx = trees.indices.filter { trees[it] }

        // 1. No tree beside it.
        val bare = st.indices.filter { free(it) && TentsLogic.orth(n, it).none { j -> trees[j] } }
        if (bare.isNotEmpty()) return Step(
            NO_TREE, GRASS, bare.toSet(), bare.toSet(), emptySet(),
            "Which squares have no tree beside them?",
            "A tent sits right beside its tree (up, down, left or right). These squares have no tree there, so they are grass: cross them out.",
        )

        // 2. A row or column that is full or done.
        for (li in 0 until 2 * n) {
            val k = li % n
            val line = if (li < n) List(n) { k * n + it } else List(n) { it * n + k }
            val want = if (li < n) rowCounts[k] else colCounts[k]
            val clue = if (li < n) n * n + k else n * n + n + k
            val need = want - line.count { st[it] == TENT }
            val open = line.filter { free(it) }
            if (open.isEmpty() || need < 0 || need > open.size) continue
            val name = lineName(n, li)
            val placed = line.filter { st[it] == TENT }.toSet()
            if (need == 0) return Step(
                LINE_DONE, GRASS, open.toSet(), line.toSet() + clue, placed,
                "Count the tents in $name.",
                "${cap(name)} holds $want, and they are all placed, so the rest of it is grass.",
            )
            if (need == open.size) return Step(
                LINE_FULL, TENT, open.toSet(), line.toSet() + clue, placed,
                "Count the squares left in $name.",
                "${cap(name)} still needs ${plural(need, "tent")} and has exactly ${if (need == 1) "1 square" else "$need squares"} left that can hold one, so ${if (need == 1) "it is a tent" else "all of them are tents"}.",
            )
        }

        // 3. Around a tent.
        for (t in st.indices) {
            if (trees[t] || st[t] != TENT) continue
            val around = TentsLogic.around(n, t).filter { free(it) }
            if (around.isNotEmpty()) return Step(
                TOUCH, GRASS, around.toSet(), setOf(t) + around, setOf(t),
                "Look around this tent.",
                "Tents never touch, not even at a corner, so every free square around this tent is grass.",
            )
        }

        // 4. A tree with one square left.
        for (t in treeIdx) {
            val choices = TentsLogic.orth(n, t).filter { !trees[it] && st[it] != GRASS }
            if (choices.size == 1 && st[choices[0]] == 0) return Step(
                ONE_SPOT, TENT, choices.toSet(), setOf(t, choices[0]), setOf(t),
                "Look at this tree.",
                "This tree needs a tent beside it, and every other square next to it is a tree, off the board or ruled out. Only this one is left.",
            )
        }

        // 5. A square that would touch every choice of a tree.
        for (t in treeIdx) {
            val choices = TentsLogic.orth(n, t).filter { !trees[it] && st[it] != GRASS }
            if (choices.isEmpty()) continue
            val xs = st.indices.filter { x -> free(x) && x !in choices && choices.all { it in TentsLogic.around(n, x) } }
            if (xs.isNotEmpty()) return Step(
                SHARED, GRASS, xs.toSet(), setOf(t) + choices + xs, setOf(t) + choices,
                "Where can this tree's tent go?",
                "This tree's tent has to go on one of the marked squares. A tent in the crossed one would touch every one of them, so it is grass.",
            )
        }

        // 6. Every tree beside it already has its tent.
        val served = treeIdx.filter { t ->
            TentsLogic.orth(n, t).any { c -> !trees[c] && st[c] == TENT && TentsLogic.orth(n, c).count { trees[it] } == 1 }
        }.toSet()
        for (x in st.indices) {
            if (!free(x)) continue
            val near = TentsLogic.orth(n, x).filter { trees[it] }
            if (near.isNotEmpty() && near.all { it in served }) {
                val owners = near.flatMap { t -> TentsLogic.orth(n, t).filter { c -> !trees[c] && st[c] == TENT && TentsLogic.orth(n, c).count { trees[it] } == 1 } }
                return Step(
                    SERVED, GRASS, setOf(x), setOf(x) + near, near.toSet() + owners,
                    "Does a tree beside this square still need a tent?",
                    "${if (near.size == 1) "The tree" else "The trees"} beside this square already ${if (near.size == 1) "has its tent" else "have their tents"}, so a tent here would have no tree of its own. It is grass.",
                )
            }
        }
        return null
    }
}
