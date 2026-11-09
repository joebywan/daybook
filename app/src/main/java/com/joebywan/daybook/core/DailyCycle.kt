package com.joebywan.daybook.core

/**
 * A daily answer that goes round a cycle rather than being hashed, so no answer comes back until its tier has used
 * every other (Lexicon's words, Equation's sums). The daily seed then carries the day ([SeedHash.cycleDay] reads it
 * back) and [pick] maps it through one fixed shuffle of the tier's answer list per round.
 */
object DailyCycle {

    /**
     * The answer index of cycle day [day] for an answer list of [n] on tier [tier]: round `day / n` is one shuffle of
     * every index, `Rng(salt + round * 3 + tier)`, so no index comes back within a round. In round 0, the indices
     * [usedBefore] marks go to the back of the shuffle, so the first cycle does not repeat what was drawn before it;
     * only the cycle's own boundary can bring an answer back early.
     */
    fun pick(day: Long, n: Int, salt: Long, tier: Difficulty, usedBefore: (() -> BooleanArray)? = null): Int {
        val round = day / n
        val order = Rng(salt + round * 3 + tier.ordinal).shuffled(List(n) { it })
        val first = if (round == 0L && usedBefore != null) {
            val used = usedBefore()
            order.filter { !used[it] } + order.filter { used[it] }
        } else order
        return first[(day % n).toInt()]
    }
}
