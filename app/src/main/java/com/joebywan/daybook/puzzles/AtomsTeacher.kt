package com.joebywan.daybook.puzzles

/**
 * Atoms, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [KingsTeacher], the split is the point. [deduce] is handed the board as the player sees it —
 * the atoms, the lines between them and the bonds already drawn — and has no parameter through
 * which the stored answer could reach it. The answer is read only in [teach]: to decide which of
 * the player's bonds are mistakes, and for [FALLBACK], which says openly that it is pointing at the
 * answer.
 *
 * The player can only ever *add* bonds (there is no "no bond here" mark), so every step is a bond
 * or two to lay down. The facts that rule a bond out — a crossing, a closed-off group — never reach
 * the board themselves; they are the reason a bond somewhere else becomes forced, and the step is
 * named after that reason.
 *
 * Techniques, simplest first:
 * 1. [ONE_NEIGHBOUR] — an atom with only one neighbour it can still bond with.
 * 2. [ALL_FORCED] — an atom that needs exactly as many bonds as its neighbours have room for.
 * 3. [AT_LEAST_ONE] — an atom that can leave only a little room unused, so any neighbour with more
 *    room than that must take a bond (the 3-with-two-neighbours rule).
 * 4. [CROSSING] — one of the above, once a bond already drawn blocks a line that crosses it.
 * 5. [ISOLATION] — one of the above, once a bond that would close a group off (1–1, 2=2, or a
 *    larger finished group) is ruled out.
 * 6. [ONLY_WAY_OUT] — a group of bonded atoms with only one line left to the rest.
 * 7. [WHAT_IF] — leaving a line unbonded forces a short chain that ends in an atom that can't
 *    reach its number, or a group closed off.
 *
 * Cell indices, as [com.joebywan.daybook.core.Deduction] and the board's highlight use them: atom
 * `a` is cell `a`, and line `p` is cell `atoms.size + p` — see [pairCell].
 *
 * Every loop walks atoms and lines in index order and nothing iterates a hash, so the same board
 * gets the same hint on every platform.
 */
internal object AtomsTeacher {

    const val ONE_NEIGHBOUR = "one-neighbour"
    const val ALL_FORCED = "all-forced"
    const val AT_LEAST_ONE = "at-least-one"
    const val CROSSING = "crossing"
    const val ISOLATION = "isolation"
    const val ONLY_WAY_OUT = "only-way-out"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(
        ONE_NEIGHBOUR, ALL_FORCED, AT_LEAST_ONE, CROSSING, ISOLATION, ONLY_WAY_OUT, WHAT_IF, FALLBACK,
    )

    /**
     * How many forced atoms a [WHAT_IF] may walk through. One sentence has to carry the whole
     * argument; past two it stops being something to hold in your head while looking at the board.
     */
    const val MAX_CHAIN = 2

    /** The highlight cell for line [pair] on a board of [atomCount] atoms. */
    fun pairCell(atomCount: Int, pair: Int) = atomCount + pair

    /**
     * One step: bonds to raise to at least a count, or a mistaken line to clear, and why.
     *
     * [raise] maps a line to the count it should reach; [clear] is only ever a mistake.
     */
    class Step(
        val technique: String,
        val raise: Map<Int, Int> = emptyMap(),
        val clear: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    // ---- the board as the player sees it --------------------------------------------------------

    private const val BASIC = 0
    private const val WITH_CROSSING = 1
    private const val WITH_ISOLATION = 2

    /**
     * Atoms, lines and bonds. [lo] is what is drawn (a line can only gain bonds in a step);
     * [ceiling] caps a line below two, which only a [WHAT_IF] hypothesis does.
     */
    private class Sight(
        val atoms: List<Atom>,
        val pairs: List<Pair2>,
        val lo: IntArray,
        val ceiling: IntArray,
        val crossing: List<List<Int>>,
        val incident: List<List<Int>>,
    ) {
        val n get() = atoms.size

        fun other(p: Int, a: Int) = if (pairs[p].a == a) pairs[p].b else pairs[p].a

        fun degree(a: Int) = incident[a].sumOf { lo[it] }

        fun need(a: Int) = atoms[a].bonds - degree(a)

        fun crossedBy(p: Int): Int? = crossing[p].firstOrNull { lo[it] > 0 }

        /** The most bonds line [p] could end up with, from what [level] lets the player see. */
        fun hi(p: Int, level: Int): Int {
            val pr = pairs[p]
            var h = minOf(ceiling[p], lo[p] + maxOf(0, need(pr.a)), lo[p] + maxOf(0, need(pr.b)))
            if (level >= WITH_CROSSING && lo[p] == 0 && crossedBy(p) != null) h = 0
            if (level >= WITH_ISOLATION && h > lo[p] && closes(p, h - lo[p])) h -= 1
            return maxOf(h, lo[p])
        }

        fun cap(p: Int, level: Int) = hi(p, level) - lo[p]

        /**
         * Whether [k] more bonds on [p] would finish every atom in the group it joins while leaving
         * that group short of the whole molecule.
         */
        fun closes(p: Int, k: Int): Boolean {
            val pr = pairs[p]
            if (need(pr.a) != k || need(pr.b) != k) return false
            val group = component(pr.a, extra = p)
            if (group.size == n) return false
            return group.all { it == pr.a || it == pr.b || need(it) == 0 }
        }

        /** Atoms joined to [start] by drawn bonds (and by [extra], as if it were drawn). */
        fun component(start: Int, extra: Int = -1): List<Int> {
            val seen = BooleanArray(n)
            val stack = ArrayDeque<Int>()
            seen[start] = true
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val a = stack.removeLast()
                for (p in incident[a]) {
                    if (lo[p] == 0 && p != extra) continue
                    val b = other(p, a)
                    if (!seen[b]) {
                        seen[b] = true
                        stack.addLast(b)
                    }
                }
            }
            return (0 until n).filter { seen[it] }
        }

        /** Every group of joined atoms, each listed once, in order of its lowest atom. */
        fun components(): List<List<Int>> {
            val done = BooleanArray(n)
            val out = mutableListOf<List<Int>>()
            for (a in 0 until n) {
                if (done[a]) continue
                val c = component(a)
                c.forEach { done[it] = true }
                out += c
            }
            return out
        }

        fun copy(ceilingAt: Int = -1, ceilingTo: Int = 0) = Sight(
            atoms, pairs, lo.copyOf(),
            ceiling.copyOf().also { if (ceilingAt >= 0) it[ceilingAt] = ceilingTo },
            crossing, incident,
        )
    }

    private fun sightOf(atoms: List<Atom>, pairs: List<Pair2>, counts: List<Int>): Sight {
        val crossing = pairs.indices.map { p -> pairs.indices.filter { q -> q != p && crosses(atoms, pairs[p], pairs[q]) } }
        val incident = atoms.indices.map { a -> pairs.indices.filter { pairs[it].a == a || pairs[it].b == a } }
        return Sight(atoms, pairs, counts.toIntArray(), IntArray(pairs.size) { 2 }, crossing, incident)
    }

    /** Whether two lines intersect. Re-derived here so the teacher needs no [AtomsState]. */
    private fun crosses(atoms: List<Atom>, one: Pair2, two: Pair2): Boolean {
        if (one.horizontal == two.horizontal) return false
        val (h, v) = if (one.horizontal) one to two else two to one
        val row = atoms[h.a].row
        val col = atoms[v.a].col
        val cLo = minOf(atoms[h.a].col, atoms[h.b].col)
        val cHi = maxOf(atoms[h.a].col, atoms[h.b].col)
        val rLo = minOf(atoms[v.a].row, atoms[v.b].row)
        val rHi = maxOf(atoms[v.a].row, atoms[v.b].row)
        return col in (cLo + 1) until cHi && row in (rLo + 1) until rHi
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------

    /** The next thing to show this player, or null on a solved board. */
    fun teach(s: AtomsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.atoms, s.pairs, s.counts) ?: fallback(s)
    }

    /** A line carrying more bonds than the answer gives it. */
    private fun mistake(s: AtomsState): Step? {
        val wrong = s.pairs.indices.firstOrNull { s.counts[it] > s.solution[it] } ?: return null
        val n = s.atoms.size
        val pr = s.pairs[wrong]
        // Reason from the bonds the answer agrees with, plus this one: a second wrong bond could
        // otherwise be what makes this one look wrong.
        val counts = s.counts.indices.map { if (it == wrong) s.counts[it] else minOf(s.counts[it], s.solution[it]) }
        val sight = sightOf(s.atoms, s.pairs, counts)
        val howToClear = if (s.counts[wrong] == 2) "Tap it once to clear it." else "Tap it until it's gone."
        val line = pairCell(n, wrong)
        fun step(explanation: String, cited: Set<Int>) = Step(
            technique = MISTAKE,
            clear = listOf(wrong),
            focus = setOf(line),
            cited = cited,
            targets = setOf(line),
            nudge = "Check this bond.",
            explanation = "$explanation $howToClear",
        )

        for (end in listOf(pr.a, pr.b)) {
            if (sight.need(end) < 0) {
                return step("The ${s.atoms[end].bonds} it joins now has more bonds than its number.", setOf(end))
            }
        }
        val group = sight.component(pr.a)
        if (group.size < n && group.all { sight.need(it) == 0 }) {
            val what = if (group.size == 2) {
                "This bond finishes both of its atoms and closes the pair off"
            } else {
                "This bond finishes every atom in this group and closes it off"
            }
            return step("$what from the rest of the molecule.", group.toSet())
        }
        propagate(sight)?.let { chain ->
            return step("With this bond, ${chain.phrase(sight, lead = false)}.", chain.cited(sight))
        }
        return step("This bond isn't part of the answer. Take it back and look again.", emptySet())
    }

    /**
     * Nothing short enough to explain applies, so point at a line the answer bonds, next to the atom
     * with the least room to spare. Says plainly that it is doing this.
     */
    private fun fallback(s: AtomsState): Step? {
        val sight = sightOf(s.atoms, s.pairs, s.counts)
        val n = s.atoms.size
        val open = s.pairs.indices.filter { s.counts[it] < s.solution[it] }
        val target = open.minByOrNull { p ->
            val pr = s.pairs[p]
            minOf(spare(sight, pr.a), spare(sight, pr.b)) * 1000 + p
        } ?: return null
        val pr = s.pairs[target]
        val atom = if (spare(sight, pr.a) <= spare(sight, pr.b)) pr.a else pr.b
        return Step(
            technique = FALLBACK,
            raise = mapOf(target to s.counts[target] + 1),
            focus = setOf(atom),
            cited = setOf(pr.a, pr.b),
            targets = setOf(pairCell(n, target)),
            nudge = "This one takes a longer chain. Look at the glowing ${s.atoms[atom].bonds}.",
            explanation = "This one needs a longer chain than a hint can walk through, " +
                "so here's a line that holds a bond.",
        )
    }

    private fun spare(s: Sight, a: Int): Int =
        s.incident[a].sumOf { s.cap(it, WITH_ISOLATION) } - s.need(a)

    // ---- reasoning from what the player can see ---------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [counts] are taken as the player's; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(atoms: List<Atom>, pairs: List<Pair2>, counts: List<Int>): Step? {
        val s = sightOf(atoms, pairs, counts)
        if (atoms.indices.any { s.need(it) < 0 }) return null
        for (level in BASIC..WITH_ISOLATION) {
            val f = atomRule(s, level) ?: continue
            return explainAtom(s, f, level)
        }
        onlyWayOut(s)?.let { return it }
        return whatIf(s)
    }

    /** What one atom's count forces on its lines, at one level of sight. */
    private class Forced(
        val atom: Int,
        val rule: String,
        /** Line to its new count. */
        val raise: Map<Int, Int>,
        val need: Int,
        val room: Int,
    )

    /**
     * The first atom whose count forces a bond, simplest rule first: one neighbour, then all
     * forced, then at least one.
     */
    private fun atomRule(s: Sight, level: Int): Forced? {
        val found = mutableListOf<Forced>()
        for (a in 0 until s.n) {
            val need = s.need(a)
            if (need <= 0) continue
            val caps = s.incident[a].associateWith { s.cap(it, level) }
            val room = caps.values.sum()
            if (room < need) continue // not this rule's business; a contradiction elsewhere
            val raise = s.incident[a].mapNotNull { p ->
                val k = need - (room - caps.getValue(p))
                // A relaxed level must still be legal once everything is seen.
                if (k > 0 && s.lo[p] + k <= s.hi(p, WITH_ISOLATION)) p to s.lo[p] + k else null
            }.toMap()
            if (raise.isEmpty()) continue
            val usable = caps.count { it.value > 0 }
            val rule = when {
                usable == 1 -> ONE_NEIGHBOUR
                need == room -> ALL_FORCED
                else -> AT_LEAST_ONE
            }
            found += Forced(a, rule, raise, need, room)
        }
        return listOf(ONE_NEIGHBOUR, ALL_FORCED, AT_LEAST_ONE).firstNotNullOfOrNull { r -> found.firstOrNull { it.rule == r } }
    }

    private fun explainAtom(s: Sight, f: Forced, level: Int): Step {
        val a = f.atom
        val x = s.atoms[a].bonds
        val n = s.n
        val lines = s.incident[a]
        val technique = when (level) {
            BASIC -> f.rule
            WITH_CROSSING -> CROSSING
            else -> ISOLATION
        }
        // Why each line out of this atom has the room it has, when that room is less than a
        // stranger to the board would assume.
        val reasons = mutableListOf<String>()
        val citedExtra = mutableSetOf<Int>()
        for (p in lines) {
            val b = s.other(p, a)
            val who = "the ${s.atoms[b].bonds} ${where(s.atoms[a], s.atoms[b])}"
            val basic = s.cap(p, BASIC)
            val crossed = s.cap(p, WITH_CROSSING)
            val isolated = s.cap(p, WITH_ISOLATION)
            when {
                level >= WITH_CROSSING && crossed < basic -> {
                    reasons += "the way to $who is blocked by a bond crossing it"
                    s.crossedBy(p)?.let { citedExtra += pairCell(n, it) }
                }
                level >= WITH_ISOLATION && isolated < crossed -> {
                    val group = s.component(s.pairs[p].a, extra = p)
                    citedExtra += group
                    reasons += if (isolated == 0) {
                        "a bond to $who would close ${if (group.size == 2) "the two of them" else "that group"} " +
                            "off from the rest, so that's out"
                    } else {
                        "two bonds to $who would close ${if (group.size == 2) "the two of them" else "that group"} " +
                            "off from the rest, so it can take one at most"
                    }
                }
            }
        }
        val prefix = if (reasons.isEmpty()) "" else reasons.joinToString("; ").cap() + ". "

        val needWords = if (s.degree(a) == 0) "needs ${bondsWord(f.need)}" else "still needs ${bondsWord(f.need)}"
        val body = when (f.rule) {
            ONE_NEIGHBOUR -> {
                val p = f.raise.keys.first()
                val b = s.other(p, a)
                val others = lines.filter { it != p }
                val otherWhy = if (others.isEmpty()) "" else {
                    val full = others.filter { s.cap(it, BASIC) == 0 }.map { s.other(it, a) }
                    if (full.isNotEmpty() && reasons.isEmpty()) " (its other neighbours have no room left)" else ""
                }
                val goes = when {
                    f.need == 1 && s.degree(a) > 0 -> "its last bond goes there"
                    f.need == 1 -> "its bond goes there"
                    s.degree(a) > 0 -> "its remaining ${numberWord(f.need)} go there"
                    else -> "${if (f.need == 2) "both" else "all"} its bonds go there"
                }
                "This $x has only one neighbour it can still bond with, the ${s.atoms[b].bonds} " +
                    "${where(s.atoms[a], s.atoms[b])}$otherWhy, so $goes."
            }
            ALL_FORCED -> {
                val parts = f.raise.entries.sortedBy { it.key }.map { (p, to) ->
                    "${numberWord(to - s.lo[p])} ${where(s.atoms[a], s.atoms[s.other(p, a)])}"
                }
                "This $x $needWords, and its neighbours have room for exactly that, " +
                    "so it takes every bond it can: ${join(parts)}."
            }
            else -> {
                val spare = f.room - f.need
                val targets = f.raise.entries.sortedBy { it.key }
                val each = targets.size == lines.count { s.cap(it, level) > 0 } && targets.all { it.value - s.lo[it.key] == 1 }
                val tail = if (each && targets.size > 1) {
                    "so each neighbour with room gets at least one bond"
                } else {
                    val parts = targets.map { (p, to) ->
                        val b = s.other(p, a)
                        "the ${s.atoms[b].bonds} ${where(s.atoms[a], s.atoms[b])} gets at least ${numberWord(to - s.lo[p])}"
                    }
                    "so ${join(parts)}"
                }
                "This $x $needWords, and its neighbours have room for ${numberWord(f.room)}. " +
                    "Only ${numberWord(spare)} of that room can go unused, $tail."
            }
        }
        val targets = f.raise.keys.map { pairCell(n, it) }.toSet()
        return Step(
            technique = technique,
            raise = f.raise,
            focus = setOf(a),
            cited = setOf(a) + lines.map { s.other(it, a) } + lines.map { pairCell(n, it) } + citedExtra,
            targets = targets,
            nudge = "Look at the glowing $x.",
            explanation = prefix + body,
        )
    }

    /** A group of joined atoms, not yet the whole molecule, with only one line left to the rest. */
    private fun onlyWayOut(s: Sight): Step? {
        if (s.components().size == 1) return null
        for (group in s.components()) {
            if (group.size < 2) continue // a lone atom is [ONE_NEIGHBOUR]'s case
            val inside = BooleanArray(s.n).also { m -> group.forEach { m[it] = true } }
            val exits = group.flatMap { a -> s.incident[a] }
                .filter { p -> inside[s.pairs[p].a] != inside[s.pairs[p].b] && s.cap(p, WITH_ISOLATION) > 0 }
                .distinct().sorted()
            if (exits.size != 1) continue
            val p = exits[0]
            val pr = s.pairs[p]
            val from = if (inside[pr.a]) pr.a else pr.b
            val to = s.other(p, from)
            return Step(
                technique = ONLY_WAY_OUT,
                raise = mapOf(p to s.lo[p] + 1),
                focus = group.toSet(),
                cited = group.toSet() + to,
                targets = setOf(pairCell(s.n, p)),
                nudge = "Look at the glowing group.",
                explanation = "These atoms are bonded to each other but not yet to the rest, and every " +
                    "atom must join one molecule. The only way out is from the ${s.atoms[from].bonds} to " +
                    "the ${s.atoms[to].bonds} ${where(s.atoms[from], s.atoms[to])}, so that line needs a bond.",
            )
        }
        return null
    }

    // ---- what if ---------------------------------------------------------------------------------

    private sealed interface Dead
    private class Short(val atom: Int) : Dead
    private class Closed(val group: List<Int>) : Dead

    /** A hypothesis followed through at most [MAX_CHAIN] forced atoms to a dead end. */
    private class Chain(val forced: List<Forced>, val dead: Dead) {
        fun cited(s: Sight): Set<Int> {
            val out = mutableSetOf<Int>()
            forced.forEach { f -> out += f.atom; f.raise.keys.forEach { out += pairCell(s.n, it) } }
            when (dead) {
                is Short -> out += dead.atom
                is Closed -> out += dead.group
            }
            return out
        }

        /** "the 3 would have to bond below, and then the 2 couldn't reach its number". */
        fun phrase(s: Sight, lead: Boolean): String {
            val steps = forced.map { f ->
                val a = s.atoms[f.atom]
                val where = f.raise.keys.sorted().map { where(a, s.atoms[s.other(it, f.atom)]) }
                "the ${a.bonds} would have to bond ${join(where)}"
            }
            val end = when (dead) {
                is Short -> "the ${s.atoms[dead.atom].bonds} couldn't reach its number"
                is Closed -> if (dead.group.size == 2) {
                    "two atoms would be closed off from the rest"
                } else {
                    "a group would be closed off from the rest"
                }
            }
            val all = steps + end
            val text = if (all.size == 1) all[0] else all.dropLast(1).joinToString(", ") + ", and then " + all.last()
            return if (lead) text.cap() else text
        }
    }

    private fun contradiction(s: Sight): Dead? {
        for (a in 0 until s.n) {
            val need = s.need(a)
            if (need < 0) return Short(a)
            if (s.incident[a].sumOf { s.cap(it, WITH_ISOLATION) } < need) return Short(a)
        }
        val groups = s.components()
        if (groups.size > 1) {
            for (g in groups) {
                val inside = BooleanArray(s.n).also { m -> g.forEach { m[it] = true } }
                val exits = g.flatMap { s.incident[it] }.any { p ->
                    inside[s.pairs[p].a] != inside[s.pairs[p].b] && s.cap(p, WITH_ISOLATION) > 0
                }
                if (!exits) return Closed(g)
            }
        }
        return null
    }

    /** Forced atoms, one at a time, until a dead end or [MAX_CHAIN] of them. Mutates [start]. */
    private fun propagate(start: Sight): Chain? {
        val forced = mutableListOf<Forced>()
        while (true) {
            contradiction(start)?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val f = atomRule(start, WITH_ISOLATION) ?: return null
            f.raise.forEach { (p, to) -> start.lo[p] = to }
            forced += f
        }
    }

    private fun whatIf(s: Sight): Step? {
        var best: Pair<Int, Chain>? = null
        for (p in s.pairs.indices) {
            if (s.cap(p, WITH_ISOLATION) == 0) continue
            val chain = propagate(s.copy(ceilingAt = p, ceilingTo = s.lo[p])) ?: continue
            if (best == null || chain.forced.size < best.second.forced.size) best = p to chain
        }
        val (p, chain) = best ?: return null
        val pr = s.pairs[p]
        val a = s.atoms[pr.a]
        val b = s.atoms[pr.b]
        val line = pairCell(s.n, p)
        val suppose = if (s.lo[p] == 0) "weren't bonded" else "got no more bonds"
        val need = if (s.lo[p] == 0) "a bond" else "another bond"
        return Step(
            technique = WHAT_IF,
            raise = mapOf(p to s.lo[p] + 1),
            focus = setOf(pr.a, pr.b, line),
            cited = chain.cited(s) + pr.a + pr.b,
            targets = setOf(line),
            nudge = "What if these two ${if (s.lo[p] == 0) "stayed apart" else "got no more bonds"}?",
            explanation = "Suppose the ${a.bonds} and the ${b.bonds} $suppose. Then " +
                "${chain.phrase(s, lead = false)}. So they need $need.",
        )
    }

    // ---- words -----------------------------------------------------------------------------------

    /** Where [to] sits, seen from [from]: "to its right", "above". */
    private fun where(from: Atom, to: Atom): String = when {
        to.row == from.row && to.col > from.col -> "to the right"
        to.row == from.row -> "to the left"
        to.row < from.row -> "above"
        else -> "below"
    }

    private fun bondsWord(k: Int) = if (k == 1) "one bond" else "${numberWord(k)} bonds"

    private fun join(words: List<String>): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
    }

    private fun numberWord(k: Int) =
        listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight")
            .getOrElse(k) { k.toString() }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
