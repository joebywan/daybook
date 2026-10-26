package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.listed

/**
 * Dominoes, reasoned the way a person reasons it (`docs/DOMINO_SPEC.md` "Teacher").
 *
 * The ladder ([DominoLadder]) keeps every *candidate*: a tray domino on two free neighbouring
 * squares, one way round. Named steps strike candidates, cheapest first, and a hint is emitted only
 * when one domino's placement is settled (a square with one candidate left, or a domino with one
 * place left), so every hint ends in a placement and never asks the player to remember an
 * elimination (Cipher's rule). The steps, by level:
 *
 * 1. geometry — a square with one partner left pairs with it, so its partner pairs with nothing else.
 * 3. rule fit — a candidate that breaks a region's rule whatever goes on the region's other empty
 *    squares ([DominoLogic.ruleBroken]).
 * 4. region values — a candidate that leaves a region no way to keep its rule from the values its
 *    other squares can still take (those are what the tray can still put there).
 * 5. counting — as many squares that can only take a value as there are halves of it left in the
 *    tray, so no other square gets one (this covers "only one domino still has a 6").
 * 6. what-if — a square with two or three candidates, all but one of which run into a contradiction
 *    within [WHAT_IF_ROUNDS] rounds of steps 1-5.
 *
 * Level 2 is a settled domino striking what overlaps it; it is free on every tier. The hint's
 * technique is named from the deepest level used before the placement settled: [ONLY_PARTNER],
 * [ONLY_PLACE] or [ONLY_DOMINO] for levels up to 3 (by how the placement settled), else
 * [REGION_VALUE], [COUNTING] or [WHAT_IF].
 *
 * **The generator's ladder is this one**: a board ships only if [solveLevel] places every domino
 * within the tier's levels, so hints never fall back on a generated board. [deduce] takes only the
 * visible [DominoLogic.Layout] and the placements; the answer is read in [teach] alone, for mistakes
 * and the [FALLBACK] (boards not made here).
 *
 * Every loop walks squares, dominoes and regions in index order: no hash container is iterated.
 *
 * ## Highlight indices
 * Squares are `row * width + col`; tray slot `i` is [TRAY] `+ i`; region `r`'s rule badge is [RULE] `+ r`.
 */
internal object DominoTeacher {

    const val ONLY_PARTNER = "only-partner"
    const val ONLY_PLACE = "only-place"
    const val ONLY_DOMINO = "only-domino"
    const val REGION_VALUE = "region-value"
    const val COUNTING = "counting"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"
    const val FALLBACK = "fallback"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(ONLY_PARTNER, ONLY_PLACE, ONLY_DOMINO, REGION_VALUE, COUNTING, WHAT_IF)

    const val LEVEL_GEOMETRY = 1
    const val LEVEL_FIX = 2
    const val LEVEL_RULE_FIT = 3
    const val LEVEL_REGION_VALUE = 4
    const val LEVEL_COUNTING = 5
    const val LEVEL_WHAT_IF = 6

    /** How far a what-if follows its supposition: rounds of steps 1-5 over the whole board. */
    const val WHAT_IF_ROUNDS = 2

    const val TRAY = 1000
    const val RULE = 2000

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /**
     * One hint. [domino] is a tray index; [placement] is where it goes, or null for a [MISTAKE]
     * (lift [domino]). [level] is the ladder level the step needed.
     */
    class Step(
        override val technique: String,
        val level: Int,
        val domino: Int,
        val placement: Placement?,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val nudge: String,
        override val explanation: String,
    ) : TeachStep {
        override val targets: Set<Int>
            get() = (placement?.let { setOf(it.first, it.second) } ?: emptySet()) + (TRAY + domino)
        val mistake: Boolean get() = technique == MISTAKE
        val fallback: Boolean get() = technique == FALLBACK
    }

    fun teach(s: DominoState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.layout, s.placed) ?: fallback(s)
    }

    /** [s] with [step] made, as Show me does: the domino laid, or for a mistake lifted. */
    fun applyTo(s: DominoState, step: Step): DominoState {
        val p = step.placement ?: return DominoLogic.lift(s, step.domino)
        return DominoLogic.place(s, step.domino, p)
    }

    /** Whether the board shows [step] made: the domino where it says (a double either way round), or lifted. */
    fun reached(s: DominoState, step: Step): Boolean {
        val now = s.placed.getOrNull(step.domino)
        val p = step.placement ?: return now == null
        return now != null && same(s.tray[step.domino], now, p)
    }

    // ---- mistakes and the fallback ----------------------------------------------------------------

    /** [a] and [b] lay [dom] the same: the same squares the same way round, or either way for a double. */
    private fun same(dom: Domino, a: Placement, b: Placement) =
        a == b || (dom.isDouble && a == Placement(b.second, b.first))

    /** The first placed domino, in tray order, that the board's one answer does not have there that way round. */
    private fun mistake(s: DominoState): Step? {
        val words = Words(s.layout)
        for (d in s.tray.indices) {
            val p = s.placed[d] ?: continue
            if (same(s.tray[d], p, s.answer[d])) continue
            return Step(
                technique = MISTAKE,
                level = 0,
                domino = d,
                placement = null,
                focus = setOf(p.first, p.second),
                cited = emptySet(),
                nudge = "One of your dominoes isn't where it belongs.",
                explanation = "The ${words.domino(s.tray[d])} at ${words.square(p.first)} can't stay as it is: no way of keeping " +
                    "every rule has it there that way round. Lift it.",
            )
        }
        return null
    }

    /** Only for a board the ladder cannot finish (not one made here): point at a domino of the stored answer, openly. */
    private fun fallback(s: DominoState): Step? {
        val d = s.placed.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return null
        val p = s.answer[d]
        val words = Words(s.layout)
        return Step(
            technique = FALLBACK,
            level = 0,
            domino = d,
            placement = p,
            focus = setOf(p.first, p.second),
            cited = emptySet(),
            nudge = "This one takes a longer chain. Look at the ${words.domino(s.tray[d])}.",
            explanation = "This one needs a longer chain than a hint can walk through, so here's the answer for the " +
                "${words.domino(s.tray[d])}: it goes here, " +
                words.half(s.tray[d], p) + ".",
        )
    }

    // ---- the ladder --------------------------------------------------------------------------------

    /**
     * The generator's acceptance test: the ladder, capped at [maxLevel], places every domino of
     * [layout] from an empty board. The deepest level it used, or null when it gets stuck.
     */
    fun solveLevel(layout: DominoLogic.Layout, maxLevel: Int, candidates: DominoCandidates? = null): Int? {
        val empty = List(layout.tray.size) { null }
        return DominoLadder.build(layout, empty, candidates ?: DominoCandidates(layout, empty)).solve(maxLevel)
    }

    /**
     * The next placement reasoned from what the player sees, or null when there is none (a finished
     * board, one with a wrong domino on it, or one the ladder cannot finish).
     */
    fun deduce(layout: DominoLogic.Layout, placed: List<Placement?>): Step? {
        val ladder = DominoLadder.build(layout, placed)
        var used = 0
        while (true) {
            ladder.forced()?.let { return placementStep(ladder, it, used) }
            if (ladder.broken != DominoLadder.NONE || ladder.done()) return null
            used = maxOf(
                used,
                when {
                    ladder.geometry() -> LEVEL_GEOMETRY
                    ladder.ruleFit() -> LEVEL_RULE_FIT
                    ladder.regionValue() -> LEVEL_REGION_VALUE
                    ladder.counting() -> LEVEL_COUNTING
                    else -> return ladder.whatIf()?.let { whatIfStep(ladder, it) }
                },
            )
        }
    }

    private fun placementStep(ladder: DominoLadder, forced: DominoLadder.Forced, used: Int): Step {
        val layout = ladder.layout
        val words = Words(layout)
        val c = forced.cand
        val d = ladder.cDom[c]
        val dom = layout.tray[d]
        val p = ladder.placement(c)
        val q = forced.square
        // The candidates whose striking settled this one.
        val rivals = if (q >= 0) ladder.bySquare[q].filter { it != c } else ladder.byDomino[d].filter { it != c }
        val facts = ladder.reasonsOf(rivals)
        val technique = when {
            used >= LEVEL_COUNTING -> COUNTING
            used >= LEVEL_REGION_VALUE -> REGION_VALUE
            q >= 0 && layout.neighbours[q].count { ladder.free[it] && ladder.known[it] < 0 } == 1 -> ONLY_PARTNER
            q < 0 -> ONLY_PLACE
            else -> ONLY_DOMINO
        }
        val name = words.domino(dom)
        val so = "So the $name goes here, ${words.half(dom, p)}."
        val where = if (q >= 0) words.square(q) else ""
        val partner = if (q >= 0) (if (p.first == q) p.second else p.first) else -1
        fun factList(max: Int) = words.facts(facts.take(max))
        val leaves = if (q >= 0) "one way to cover $where" else "one place for the $name"
        val nudge: String
        val text: (Int) -> String
        when (technique) {
            ONLY_PARTNER -> {
                nudge = "Look at $where: how many free neighbours has it?"
                text = { m -> "${where.cap()} has one free neighbour, so the two share a domino. " +
                    (if (facts.isEmpty()) "Only the $name is left. " else "Only the $name fits ${factList(m)}. ") + so }
            }
            ONLY_PLACE -> {
                nudge = "Where can the $name go?"
                text = { m -> if (facts.isEmpty()) "The $name has one place left. $so" else "Everywhere else the $name could lie breaks ${factList(m)}. $so" }
            }
            ONLY_DOMINO -> {
                nudge = "Which dominoes could cover $where?"
                text = { m -> "Only one domino can cover $where: ${factList(m)} rule${if (facts.size == 1) "s" else ""} out the rest. $so" }
            }
            REGION_VALUE -> {
                nudge = "Look at what ${words.regionOrThis(facts)} can be made of."
                val value = facts.filter { it.kind == DominoLadder.VALUE_KIND }
                text = { m -> if (value.isEmpty()) "The values the tray can still supply settle the regions around it. That leaves $leaves. $so"
                    else "${words.facts(value.take(m)).cap()} can only be made from what the tray still has. That leaves $leaves. $so" }
            }
            else -> {
                nudge = "Count the ${words.countValue(facts)} left in the tray."
                val v = words.countValue(facts)
                text = { _ -> "The tray has only as many $v as squares that can take nothing else, so no other square gets one. " +
                    "That leaves $leaves. $so" }
            }
        }
        val explanation = fit(listOf(3, 2, 1).map(text) + "${words.techniqueName(technique).cap()}. $so")
        val cited = regionsIn(facts).map { RULE + it }.toSet() + (if (partner >= 0) setOf(q, partner) else emptySet())
        return Step(technique, used, d, p, setOf(p.first, p.second), cited, fitNudge(nudge, "Look at the $name."), explanation)
    }

    private fun whatIfStep(ladder: DominoLadder, w: DominoLadder.WhatIf): Step {
        val layout = ladder.layout
        val words = Words(layout)
        val c = w.survivor
        val d = ladder.cDom[c]
        val dom = layout.tray[d]
        val p = ladder.placement(c)
        val where = words.square(w.square)
        val so = "So the ${words.domino(dom)} goes here, ${words.half(dom, p)}."
        fun refute(r: DominoLadder.Refuted, full: Boolean): String {
            val d = ladder.cDom[r.cand]
            val twin = (w.refuted.map { it.cand } + c).count { ladder.cDom[it] == d } > 1
            val head = "with the " + words.way(layout.tray[d], ladder.placement(r.cand), w.square, twin)
            return if (full) "$head, ${words.broken(r.kind, r.arg)}" else head + " something breaks"
        }
        val texts = listOf(w.refuted.size, 1, 0).map { full ->
            "Try what could cover $where: " + w.refuted.withIndex().joinToString("; ") { (i, r) -> refute(r, i < full) } + ". $so"
        } + "Every other domino that could cover $where runs into trouble within a few steps. $so"
        val cited = w.refuted.filter { it.kind == DominoLadder.REGION }.map { RULE + it.arg }.toSet()
        return Step(
            WHAT_IF, LEVEL_WHAT_IF, d, p, setOf(w.square), cited,
            fitNudge("Try each domino that could cover $where.", "Try each domino that could cover this square."),
            fit(texts),
        )
    }

    private fun fit(options: List<String>): String = options.firstOrNull { it.length <= MAX_EXPLANATION } ?: options.last()

    private fun fitNudge(nudge: String, short: String) = if (nudge.length <= MAX_NUDGE) nudge else short

    /** Region indices named by [facts], in order, without a hash set. */
    private fun regionsIn(facts: List<DominoLadder.Reason>): List<Int> {
        val out = ArrayList<Int>()
        for (f in facts) if ((f.kind == DominoLadder.RULE_KIND || f.kind == DominoLadder.VALUE_KIND) && f.arg !in out) out.add(f.arg)
        return out
    }

    // ---- words -------------------------------------------------------------------------------------

    private class Words(val layout: DominoLogic.Layout) {
        fun square(q: Int) = "row ${q / layout.width + 1}, column ${q % layout.width + 1}"

        /** "6-3 going right, its 3 here": a candidate as seen from square [q]. */
        fun way(d: Domino, p: Placement, q: Int, which: Boolean): String {
            val other = if (p.first == q) p.second else p.first
            val dir = when (other - q) { 1 -> "right"; -1 -> "left"; layout.width -> "down"; else -> "up" }
            val here = if (p.first == q) d.a else d.b
            return if (!which) domino(d) else if (d.isDouble) "${domino(d)} going $dir" else "${domino(d)} going $dir, its $here here"
        }

        fun domino(d: Domino) = if (d.isDouble) "double ${d.a}" else "${d.b}-${d.a}"

        /** Which half goes where: "its 6 on row 2, column 3" (the larger half), or "across both squares" for a double. */
        fun half(d: Domino, p: Placement) = if (d.isDouble) "a ${d.a} on each square" else "its ${d.b} on ${square(p.second)}"

        private fun ruleWords(r: Int): String {
            val rule = layout.rules[r]
            val one = layout.regionSquares[r].size == 1
            return when (rule.kind) {
                RuleKind.SUM -> if (one) "the square marked ${rule.n}" else "the region totalling ${rule.n}"
                RuleKind.LESS -> if (one) "the square under ${rule.n}" else "the region under ${rule.n}"
                RuleKind.MORE -> if (one) "the square over ${rule.n}" else "the region over ${rule.n}"
                RuleKind.EQUAL -> "the equal region"
                RuleKind.DIFFERENT -> "the all-different region"
            }
        }

        /** A region by its rule, with where it starts when another region reads the same. */
        fun region(r: Int): String {
            val words = ruleWords(r)
            val twin = layout.rules.indices.any { it != r && ruleWords(it) == words }
            return if (twin) "$words at ${square(layout.regionSquares[r][0])}" else words
        }

        fun fact(f: DominoLadder.Reason): String = when (f.kind) {
            DominoLadder.GEOMETRY_KIND -> "the only partner left for ${square(f.arg)}"
            DominoLadder.RULE_KIND, DominoLadder.VALUE_KIND -> region(f.arg)
            DominoLadder.COUNT_KIND -> "the ${f.arg}s"
            else -> "where the other dominoes must go"
        }

        fun facts(list: List<DominoLadder.Reason>): String {
            val parts = list.map { fact(it) }
            return if (parts.isEmpty()) "the rules" else listed(parts)
        }

        fun regionOrThis(facts: List<DominoLadder.Reason>): String =
            facts.firstOrNull { it.kind == DominoLadder.VALUE_KIND }?.let { region(it.arg) }?.takeIf { it.length <= 30 } ?: "this region"

        fun countValue(facts: List<DominoLadder.Reason>): String =
            facts.firstOrNull { it.kind == DominoLadder.COUNT_KIND }?.let { "${it.arg}s" } ?: "values"

        fun broken(kind: Int, arg: Int): String = when (kind) {
            DominoLadder.SQUARE -> "${square(arg)} could not be covered"
            DominoLadder.DOMINO -> "the ${domino(layout.tray[arg])} would have nowhere to go"
            DominoLadder.REGION -> "${region(arg)} could not keep its rule"
            else -> "there would not be enough ${arg}s"
        }

        fun techniqueName(t: String) = when (t) {
            ONLY_PARTNER -> "a square with one free neighbour"
            ONLY_PLACE -> "the domino has one place left"
            ONLY_DOMINO -> "one domino fits this square"
            REGION_VALUE -> "a region's values settle it"
            else -> "counting the values left in the tray"
        }
    }
}

/**
 * Every candidate of a board: each unplaced tray domino on each pair of free neighbouring squares,
 * both ways round (a double once). It depends on the shape, the tray and the placements, not on the
 * regions, so the generator builds it once per draw and reuses it for every rule it tries.
 */
internal class DominoCandidates(layout: DominoLogic.Layout, placed: List<Placement?>) {
    val n = layout.size

    /** Open and not under a placed domino. */
    val free = BooleanArray(n) { layout.open[it] }

    /** Pips under the placed dominoes, -1 elsewhere. */
    val placedPips = IntArray(n) { -1 }
    val placedDom = BooleanArray(layout.tray.size)
    val dom: IntArray
    val s: IntArray
    val t: IntArray
    val vs: IntArray
    val vt: IntArray
    val bySquare: Array<IntArray>
    val byDomino: Array<IntArray>

    init {
        val tray = layout.tray
        for (k in tray.indices) {
            val p = placed[k] ?: continue
            placedDom[k] = true
            free[p.first] = false
            free[p.second] = false
            placedPips[p.first] = tray[k].a
            placedPips[p.second] = tray[k].b
        }
        var count = 0
        for (k in tray.indices) {
            if (placedDom[k]) continue
            for (x in 0 until n) if (free[x]) for (y in layout.neighbours[x]) if (y > x && free[y]) count += if (tray[k].isDouble) 1 else 2
        }
        dom = IntArray(count); s = IntArray(count); t = IntArray(count); vs = IntArray(count); vt = IntArray(count)
        val sqCount = IntArray(n)
        val domCount = IntArray(tray.size)
        var c = 0
        fun add(k: Int, x: Int, y: Int) {
            dom[c] = k; s[c] = x; t[c] = y; vs[c] = tray[k].a; vt[c] = tray[k].b
            sqCount[x]++; sqCount[y]++; domCount[k]++
            c++
        }
        for (k in tray.indices) {
            if (placedDom[k]) continue
            for (x in 0 until n) {
                if (!free[x]) continue
                for (y in layout.neighbours[x]) {
                    if (y < x || !free[y]) continue
                    add(k, x, y)
                    if (!tray[k].isDouble) add(k, y, x)
                }
            }
        }
        bySquare = Array(n) { IntArray(sqCount[it]) }
        byDomino = Array(tray.size) { IntArray(domCount[it]) }
        val sqFill = IntArray(n)
        val domFill = IntArray(tray.size)
        for (i in 0 until count) {
            bySquare[s[i]][sqFill[s[i]]++] = i
            bySquare[t[i]][sqFill[t[i]]++] = i
            byDomino[dom[i]][domFill[dom[i]]++] = i
        }
    }
}

/**
 * The ladder's working state for one board: which candidates ([DominoCandidates]) are still alive,
 * why each struck one went, and what has settled. Built from the visible board alone.
 */
internal class DominoLadder private constructor(
    val layout: DominoLogic.Layout,
    private val k: DominoCandidates,
    val alive: BooleanArray,
    /** Why each struck candidate went: an index into [reasons], or -1 while alive (or when not recorded). */
    private val reasonOf: IntArray,
    /** Pips on each square under a placed or settled domino, -1 elsewhere. */
    val known: IntArray,
    /** The candidate each settled (not yet placed) domino is fixed to, or -1. */
    private val fixedAt: IntArray,
    private val aliveSq: IntArray,
    private val aliveDom: IntArray,
    /** Regions whose settled pips changed since [ruleFit] last looked (only they can strike anything new). */
    private val ruleDirty: BooleanArray,
    /** Regions whose squares' possible pips changed since [regionValue] last looked. */
    private val valueDirty: BooleanArray,
    /** Squares whose candidates changed since [geometry] last looked. */
    private val squareDirty: BooleanArray,
    /** Alive candidates showing value v on square q, at `q * 7 + v`; and each square's possible pips as a bit mask. */
    private val valCount: IntArray,
    private val domMask: IntArray,
    /** Null inside a what-if, which needs only whether it breaks. */
    private val reasons: ArrayList<Reason>?,
) {
    class Reason(val level: Int, val kind: Int, val arg: Int)
    class Forced(val cand: Int, val square: Int)
    class Refuted(val cand: Int, val kind: Int, val arg: Int)
    class WhatIf(val square: Int, val survivor: Int, val refuted: List<Refuted>)

    /** What contradiction was found ([NONE], [SQUARE], [DOMINO], [REGION], [COUNT]) and about what. */
    var broken = NONE
    var brokenArg = -1

    /** Whether a domino has settled since [forced] last checked every region's settled pips. */
    private var regionsToCheck = true

    /** Whether anything was struck since [counting] last looked. */
    private var countingDirty = true

    /** Scratch for the region checks. */
    private val pipsBuf = IntArray(DominoLogic.MAX_REGION)
    private val maskBuf = IntArray(DominoLogic.MAX_REGION)

    /**
     * Hypotheses [whatIf] has already refuted in this run, with why. The ladder only ever learns, so a
     * refuted hypothesis stays refuted: only the survivors need trying again. Never copied into a hypothesis.
     */
    private var refutedKind: IntArray? = null
    private var refutedArg: IntArray? = null

    private val n = layout.size
    private val regions = layout.regionSquares

    val free: BooleanArray get() = k.free
    val cDom: IntArray get() = k.dom
    val bySquare: Array<IntArray> get() = k.bySquare
    val byDomino: Array<IntArray> get() = k.byDomino
    private val cS = k.s
    private val cT = k.t
    private val cVs = k.vs
    private val cVt = k.vt

    fun placement(c: Int) = Placement(cS[c], cT[c])

    /** A copy for a what-if: same knowledge, no reasons kept. */
    private fun hypothesis() = DominoLadder(
        layout, k, alive.copyOf(), reasonOf, known.copyOf(), fixedAt.copyOf(), aliveSq.copyOf(), aliveDom.copyOf(),
        ruleDirty.copyOf(), valueDirty.copyOf(), squareDirty.copyOf(), valCount.copyOf(), domMask.copyOf(), null,
    ).also { it.broken = broken; it.brokenArg = brokenArg; it.regionsToCheck = regionsToCheck; it.countingDirty = countingDirty }

    private fun reason(level: Int, kind: Int, arg: Int): Int {
        val list = reasons ?: return -1
        list.add(Reason(level, kind, arg))
        return list.size - 1
    }

    private fun markRegions(flags: BooleanArray, c: Int) {
        val rs = layout.regionOf[cS[c]]
        val rt = layout.regionOf[cT[c]]
        if (rs >= 0) flags[rs] = true
        if (rt >= 0) flags[rt] = true
    }

    private fun kill(c: Int, r: Int): Boolean {
        if (!alive[c]) return false
        alive[c] = false
        if (reasons != null) reasonOf[c] = r
        aliveSq[cS[c]]--
        aliveSq[cT[c]]--
        aliveDom[cDom[c]]--
        squareDirty[cS[c]] = true
        squareDirty[cT[c]] = true
        markRegions(valueDirty, c)
        countingDirty = true
        if (--valCount[cS[c] * 7 + cVs[c]] == 0) domMask[cS[c]] = domMask[cS[c]] and (1 shl cVs[c]).inv()
        if (--valCount[cT[c] * 7 + cVt[c]] == 0) domMask[cT[c]] = domMask[cT[c]] and (1 shl cVt[c]).inv()
        return true
    }

    private fun fail(kind: Int, arg: Int) {
        if (broken == NONE) { broken = kind; brokenArg = arg }
    }

    private fun settled(d: Int) = k.placedDom[d] || fixedAt[d] >= 0

    /** Every domino placed or settled. */
    fun done(): Boolean = layout.tray.indices.all { settled(it) }

    /** The distinct reasons that struck [cands], cheapest level first, in the order they were found. */
    fun reasonsOf(cands: List<Int>): List<Reason> {
        val list = reasons ?: return emptyList()
        val ids = cands.map { reasonOf[it] }.filter { it >= 0 }.distinct().sorted()
        val out = ArrayList<Reason>()
        for (i in ids) {
            val r = list[i]
            if (out.none { it.kind == r.kind && it.arg == r.arg }) out.add(r)
        }
        return out.sortedBy { it.level }
    }

    /** Settles candidate [c]: its domino lies there, and nothing else may use the domino or the squares. */
    fun fix(c: Int) {
        val d = cDom[c]
        fixedAt[d] = c
        known[cS[c]] = cVs[c]
        known[cT[c]] = cVt[c]
        markRegions(ruleDirty, c)
        markRegions(valueDirty, c)
        regionsToCheck = true
        val r = reason(DominoTeacher.LEVEL_FIX, FIX_KIND, d)
        for (o in byDomino[d]) if (o != c) kill(o, r)
        for (o in bySquare[cS[c]]) if (o != c) kill(o, r)
        for (o in bySquare[cT[c]]) if (o != c) kill(o, r)
    }

    private fun lastAlive(list: IntArray): Int {
        for (c in list) if (alive[c]) return c
        return -1
    }

    /**
     * The first placement now settled and not yet fixed: a square with one candidate left (reading
     * order), else a domino with one place left (tray order). Notes a contradiction instead when a
     * square or domino has none, or a region can no longer keep its rule.
     */
    fun forced(): Forced? {
        if (broken != NONE) return null
        for (q in 0 until n) {
            if (!free[q] || known[q] >= 0) continue
            if (aliveSq[q] == 0) { fail(SQUARE, q); return null }
            if (aliveSq[q] == 1) return Forced(lastAlive(bySquare[q]), q)
        }
        for (d in layout.tray.indices) {
            if (settled(d)) continue
            if (aliveDom[d] == 0) { fail(DOMINO, d); return null }
            if (aliveDom[d] == 1) return Forced(lastAlive(byDomino[d]), -1)
        }
        if (!regionsToCheck) return null
        regionsToCheck = false
        for (r in regions.indices) {
            val sq = regions[r]
            val pips = sq.filter { known[it] >= 0 }.map { known[it] }
            if (DominoLogic.ruleBroken(layout.rules[r], pips, sq.size - pips.size)) { fail(REGION, r); return null }
        }
        return null
    }

    /** Level 1: a square with one partner left pairs with it, so the partner takes no other candidate. */
    fun geometry(): Boolean {
        var changed = false
        for (q in 0 until n) {
            if (!squareDirty[q]) continue
            squareDirty[q] = false
            if (!free[q] || known[q] >= 0) continue
            var partner = -1
            var single = true
            for (c in bySquare[q]) {
                if (!alive[c]) continue
                val t = if (cS[c] == q) cT[c] else cS[c]
                if (partner < 0) partner = t else if (partner != t) { single = false; break }
            }
            if (!single || partner < 0) continue
            var r = -2
            for (c in bySquare[partner]) {
                if (!alive[c] || cS[c] == q || cT[c] == q) continue
                if (r == -2) r = reason(DominoTeacher.LEVEL_GEOMETRY, GEOMETRY_KIND, q)
                changed = kill(c, r) || changed
            }
        }
        return changed
    }

    /**
     * Levels 3 and 4: strikes every live, unfixed candidate after which [breaks] says a region in [look] it touches
     * fails, given the region's placed pips (the candidate's own included, in [pipsBuf], nf of them) and the [dom]
     * masks of its other empty squares ([maskBuf], nm of them). One reason per region, at [level].
     */
    private inline fun strike(look: BooleanArray, dom: IntArray, level: Int, kind: Int, breaks: (r: Int, nf: Int, nm: Int) -> Boolean): Boolean {
        var changed = false
        val struck = IntArray(regions.size) { -2 }
        val fixed = pipsBuf
        val masks = maskBuf
        for (c in alive.indices) {
            if (!alive[c] || fixedAt[cDom[c]] == c) continue
            val rs = layout.regionOf[cS[c]]
            val rt = layout.regionOf[cT[c]]
            for (r in intArrayOf(rs, if (rt == rs) -1 else rt)) {
                if (r < 0 || !look[r]) continue
                var nf = 0
                var nm = 0
                for (q in regions[r]) {
                    when {
                        q == cS[c] -> fixed[nf++] = cVs[c]
                        q == cT[c] -> fixed[nf++] = cVt[c]
                        known[q] >= 0 -> fixed[nf++] = known[q]
                        else -> masks[nm++] = dom[q]
                    }
                }
                if (breaks(r, nf, nm)) {
                    if (struck[r] == -2) struck[r] = reason(level, kind, r)
                    changed = kill(c, struck[r]) || changed
                    break
                }
            }
        }
        return changed
    }

    /** Level 3: a candidate that breaks a region's rule whatever goes on the region's other empty squares. */
    fun ruleFit(): Boolean {
        val look = ruleDirty.copyOf()
        ruleDirty.fill(false)
        return strike(look, domMask, DominoTeacher.LEVEL_RULE_FIT, RULE_KIND) { r, nf, nm -> DominoLogic.ruleBroken(layout.rules[r], pipsBuf, nf, nm) }
    }

    /** Level 4: a candidate after which a region cannot keep its rule from the values its other squares can still take. */
    fun regionValue(): Boolean {
        val look = valueDirty.copyOf()
        if (look.none { it }) return false
        valueDirty.fill(false)
        // A copy of each square's pips mask: the pass reads the board it started from.
        return strike(look, domMask.copyOf(), DominoTeacher.LEVEL_REGION_VALUE, VALUE_KIND) { r, nf, nm ->
            !feasible(layout.rules[r], pipsBuf, nf, maskBuf, nm)
        }
    }

    /**
     * Level 5: as many squares that can only take value v as there are halves of v left in the tray
     * (a double has two), so no other square can get a v.
     */
    fun counting(): Boolean {
        if (!countingDirty) return false
        countingDirty = false
        val dom = domMask.copyOf() // the pips each square can take as the pass starts
        val need = IntArray(DominoLogic.MAX_PIPS + 1)
        for (q in 0 until n) {
            val m = dom[q]
            if (free[q] && m != 0 && m and (m - 1) == 0) need[m.countTrailingZeroBits()]++
        }
        val supply = IntArray(DominoLogic.MAX_PIPS + 1)
        for (d in layout.tray.indices) if (!k.placedDom[d]) { supply[layout.tray[d].a]++; supply[layout.tray[d].b]++ }
        var tight = 0
        for (v in 0..DominoLogic.MAX_PIPS) {
            // A contradiction counts as a change, so the caller stops at once.
            if (need[v] > supply[v]) { fail(COUNT, v); return true }
            if (need[v] == supply[v]) tight = tight or (1 shl v)
        }
        val reasonFor = IntArray(DominoLogic.MAX_PIPS + 1) { -2 }
        var changed = false
        for (c in alive.indices) {
            if (!alive[c] || fixedAt[cDom[c]] == c) continue
            val v = when {
                tight shr cVs[c] and 1 == 1 && dom[cS[c]] != 1 shl cVs[c] -> cVs[c]
                tight shr cVt[c] and 1 == 1 && dom[cT[c]] != 1 shl cVt[c] -> cVt[c]
                else -> continue
            }
            if (reasonFor[v] == -2) reasonFor[v] = reason(DominoTeacher.LEVEL_COUNTING, COUNT_KIND, v)
            changed = kill(c, reasonFor[v]) || changed
        }
        return changed
    }

    /** Steps 1-5 and settling, for at most [rounds] rounds or until nothing changes or something breaks. */
    private fun propagate(rounds: Int) {
        repeat(rounds) {
            var changed = false
            while (true) {
                val f = forced() ?: break
                fix(f.cand)
                changed = true
            }
            if (broken != NONE) return
            changed = geometry() || changed
            changed = ruleFit() || changed
            changed = regionValue() || changed
            changed = counting() || changed
            if (broken != NONE || !changed) return
        }
        forced()
    }

    /**
     * Level 6: the first square (two candidates before three, then reading order) all of whose
     * candidates but one run into a contradiction within [DominoTeacher.WHAT_IF_ROUNDS] rounds.
     */
    fun whatIf(): WhatIf? {
        val kinds = refutedKind ?: IntArray(alive.size).also { refutedKind = it }
        val args = refutedArg ?: IntArray(alive.size).also { refutedArg = it }
        for (size in 2..3) {
            for (q in 0 until n) {
                if (!free[q] || known[q] >= 0 || aliveSq[q] != size) continue
                val refuted = ArrayList<Refuted>()
                var survivor = -1
                for (o in bySquare[q]) {
                    if (!alive[o]) continue
                    if (kinds[o] == NONE) {
                        val h = hypothesis()
                        h.fix(o)
                        h.propagate(DominoTeacher.WHAT_IF_ROUNDS)
                        kinds[o] = h.broken
                        args[o] = h.brokenArg
                    }
                    if (kinds[o] != NONE) refuted.add(Refuted(o, kinds[o], args[o]))
                    else if (survivor < 0) survivor = o else { survivor = -2; break }
                }
                if (survivor >= 0 && refuted.size == size - 1) return WhatIf(q, survivor, refuted)
            }
        }
        return null
    }

    /**
     * The whole ladder, capped at [maxLevel]: settle what is settled, else the cheapest step that
     * strikes something, else (at level 6) a what-if. The deepest level used once every domino is
     * settled; null when it gets stuck or finds a contradiction.
     */
    fun solve(maxLevel: Int): Int? {
        var used = 0
        while (true) {
            while (true) {
                val f = forced() ?: break
                fix(f.cand)
            }
            if (broken != NONE) return null
            if (done()) return used
            val level = when {
                geometry() -> DominoTeacher.LEVEL_GEOMETRY
                maxLevel >= DominoTeacher.LEVEL_RULE_FIT && ruleFit() -> DominoTeacher.LEVEL_RULE_FIT
                maxLevel >= DominoTeacher.LEVEL_REGION_VALUE && regionValue() -> DominoTeacher.LEVEL_REGION_VALUE
                maxLevel >= DominoTeacher.LEVEL_COUNTING && counting() -> DominoTeacher.LEVEL_COUNTING
                maxLevel >= DominoTeacher.LEVEL_WHAT_IF -> {
                    val w = whatIf() ?: return null
                    fix(w.survivor)
                    DominoTeacher.LEVEL_WHAT_IF
                }
                else -> return null
            }
            used = maxOf(used, level)
        }
    }

    companion object {
        const val NONE = 0
        const val SQUARE = 1
        const val DOMINO = 2
        const val REGION = 3
        const val COUNT = 4

        const val GEOMETRY_KIND = 1
        const val FIX_KIND = 2
        const val RULE_KIND = 3
        const val VALUE_KIND = 4
        const val COUNT_KIND = 5

        /** Whether [rule] can hold with pips [fixed] and one value from each of [masks] (bit v: value v possible). */
        fun feasible(rule: RegionRule, fixed: IntArray, nf: Int, masks: IntArray, nm: Int): Boolean {
            when (rule.kind) {
                RuleKind.SUM, RuleKind.LESS, RuleKind.MORE -> {
                    var sum = 0
                    for (i in 0 until nf) sum += fixed[i]
                    var reach = 1L shl sum
                    for (i in 0 until nm) {
                        val m = masks[i]
                        var next = 0L
                        for (v in 0..DominoLogic.MAX_PIPS) if (m shr v and 1 == 1) next = next or (reach shl v)
                        reach = next
                    }
                    // Totals never pass 6 * 6 = 36, so every bit fits a Long.
                    return when (rule.kind) {
                        RuleKind.SUM -> rule.n in 0..62 && reach shr rule.n and 1L == 1L
                        RuleKind.LESS -> rule.n > 0 && reach and ((1L shl minOf(rule.n, 62)) - 1) != 0L
                        else -> rule.n < 62 && reach ushr (rule.n + 1) != 0L
                    }
                }
                RuleKind.EQUAL -> {
                    var m = 0x7F
                    for (i in 0 until nf) m = m and (1 shl fixed[i])
                    for (i in 0 until nm) m = m and masks[i]
                    return m != 0
                }
                RuleKind.DIFFERENT -> {
                    var taken = 0
                    for (i in 0 until nf) {
                        if (taken shr fixed[i] and 1 == 1) return false
                        taken = taken or (1 shl fixed[i])
                    }
                    fun place(i: Int, taken: Int): Boolean {
                        if (i == nm) return true
                        var open = masks[i] and taken.inv() and 0x7F
                        while (open != 0) {
                            val bit = open and -open
                            if (place(i + 1, taken or bit)) return true
                            open = open xor bit
                        }
                        return false
                    }
                    return place(0, taken)
                }
            }
        }

        /** The ladder for [layout] with [placed] down, from scratch; [candidates] when the caller already has them. */
        fun build(
            layout: DominoLogic.Layout,
            placed: List<Placement?>,
            candidates: DominoCandidates = DominoCandidates(layout, placed),
        ): DominoLadder {
            val k = candidates
            val count = k.dom.size
            val d = layout.tray.size
            val valCount = IntArray(layout.size * 7)
            val domMask = IntArray(layout.size) { if (k.placedPips[it] >= 0) 1 shl k.placedPips[it] else 0 }
            for (c in 0 until count) {
                valCount[k.s[c] * 7 + k.vs[c]]++
                valCount[k.t[c] * 7 + k.vt[c]]++
                domMask[k.s[c]] = domMask[k.s[c]] or (1 shl k.vs[c])
                domMask[k.t[c]] = domMask[k.t[c]] or (1 shl k.vt[c])
            }
            return DominoLadder(
                layout, k, BooleanArray(count) { true }, IntArray(count) { -1 }, k.placedPips.copyOf(), IntArray(d) { -1 },
                IntArray(layout.size) { k.bySquare[it].size }, IntArray(d) { k.byDomino[it].size },
                BooleanArray(layout.rules.size) { true }, BooleanArray(layout.rules.size) { true }, BooleanArray(layout.size) { true },
                valCount, domMask, ArrayList(),
            )
        }
    }
}
