package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.ln

/**
 * Tower shipped a difficulty curve that ran backwards: 10 guesses for 1,296 codes, then 10 for
 * 16,807, then 9 for 32,768. Expert was the largest search in the game and the smallest budget,
 * which is why it read as unfair rather than hard.
 *
 * Asserting the replacement constants are 10/12/14 would only restate the source file, so these
 * tests bound the budget from underneath with something that can actually fail: a solver that
 * plays each tier for real. If a future shape change makes a tier unwinnable, or quietly re-inverts
 * the curve, the solver stops finishing inside the budget and this goes red.
 *
 * The solver is deliberately a *consistent guesser*, not a minimax one: it guesses any code still
 * possible given every prior reply. That is the strongest thing a person could plausibly be doing
 * in their head, so its worst case is a lower bound on what a player needs, never an upper one —
 * the budget has to clear it with room to spare, not merely match it.
 */
class TowerBalanceTest {

    private val tiers = Difficulty.entries.map { it to shapeOf(it) }

    /** `(slots, colours, maxGuesses)` read back off a real generated board. */
    private data class Shape(val slots: Int, val colours: Int, val budget: Int) {
        val space: Int get() = pow(colours, slots)

        /**
         * Distinct (exact, misplaced) replies a guess can draw: every pair summing to at most
         * [slots], less `(slots - 1, 1)` — one peg left over cannot be the wrong-place match of a
         * peg that is already somewhere else.
         */
        val outcomes: Int get() = (0..slots).sumOf { exact -> slots - exact + 1 } - 1

        /** No strategy can beat `log(space) / log(outcomes)`. A floor, not a target. */
        val floor: Int get() = ceil(ln(space.toDouble()) / ln(outcomes.toDouble())).toInt()
    }

    private fun shapeOf(difficulty: Difficulty): Shape {
        val state = Tower.generate(seedFor(difficulty, 0), difficulty) as TowerState
        return Shape(state.slots, state.colours, state.maxGuesses)
    }

    private fun seedFor(difficulty: Difficulty, day: Int) =
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(day.toLong()), "tower", difficulty)

    @Test
    fun `a harder board never gets fewer guesses than an easier one`() {
        for ((left, right) in tiers.zipWithNext()) {
            val (easier, easierShape) = left
            val (harder, harderShape) = right

            assertTrue(
                "${harder.name} searches ${harderShape.space} codes but ${easier.name} searches " +
                    "${easierShape.space} — the tiers are not ordered by difficulty",
                harderShape.space > easierShape.space,
            )
            assertTrue(
                "${harder.name} searches ${harderShape.space} codes on ${harderShape.budget} " +
                    "guesses while the easier ${easier.name} gets ${easierShape.budget} for " +
                    "${easierShape.space} — the budget falls as the board grows",
                harderShape.budget >= easierShape.budget,
            )
        }
    }

    @Test
    fun `every tier is budgeted well clear of the information-theoretic floor`() {
        for ((difficulty, shape) in tiers) {
            assertTrue(
                "${difficulty.name} has ${shape.budget} guesses for ${shape.space} codes, under " +
                    "the ${shape.floor}-guess floor no strategy can beat",
                shape.budget > shape.floor,
            )
            // The floor is unreachable in practice, so a budget merely above it is still a trap.
            assertTrue(
                "${difficulty.name} gives ${shape.budget} guesses against a floor of " +
                    "${shape.floor} — too little headroom for a player doing this in their head",
                shape.budget >= shape.floor * 2,
            )
        }
    }

    @Test
    fun `the swatch row stays tappable at every tier's colour count`() {
        for ((difficulty, shape) in tiers) {
            assertTrue(
                "${difficulty.name} asks for ${shape.colours} colours but the palette holds " +
                    "${Tower.palette.size}",
                shape.colours <= Tower.palette.size,
            )
        }
    }

    @Test
    fun `a consistent guesser wins every tier inside the budget`() {
        for ((difficulty, shape) in tiers) {
            val space = codeSpace(shape.slots, shape.colours)
            val counts = (0 until GAMES).map { game ->
                val state = Tower.generate(seedFor(difficulty, game), difficulty) as TowerState
                solve(state.secret.toIntArray(), space, shape)
            }

            val worst = counts.max()
            val average = counts.average()
            println(
                "%-8s %dx%-2d %6d codes  floor %d  budget %2d | solver avg %.2f worst %d".format(
                    difficulty.name, shape.slots, shape.colours, shape.space, shape.floor,
                    shape.budget, average, worst,
                )
            )

            assertTrue(
                "${difficulty.name}: a solver tracking every reply still needed $worst guesses, " +
                    "over the ${shape.budget} allowed — no player could win this",
                worst <= shape.budget,
            )
            // A budget a perfect solver only just scrapes into is a budget a person cannot use.
            assertTrue(
                "${difficulty.name}: budget ${shape.budget} leaves only ${shape.budget - worst} " +
                    "guesses over the solver's worst case of $worst",
                shape.budget - worst >= MIN_HEADROOM,
            )
        }
    }

    /**
     * Guards the fast scorer below against the one the game actually shows players. Without this
     * the solver could be winning a game Tower is not scoring the same way, and every bound in
     * this file would be measuring the wrong thing.
     */
    @Test
    fun `the test scorer matches the scoring the board displays`() {
        val rng = Rng(4242)
        for ((difficulty, shape) in tiers) {
            val board = Tower.generate(seedFor(difficulty, 0), difficulty) as TowerState
            repeat(400) {
                val secret = List(shape.slots) { rng.nextInt(shape.colours) }
                val guess = List(shape.slots) { rng.nextInt(shape.colours) }
                val shown = board.copy(secret = secret).score(guess)
                val packed = score(guess.toIntArray(), secret.toIntArray(), shape.colours)

                assertEquals(
                    "${difficulty.name}: $guess vs $secret exact",
                    shown.exact,
                    packed shr SHIFT,
                )
                assertEquals(
                    "${difficulty.name}: $guess vs $secret misplaced",
                    shown.misplaced,
                    packed and MASK,
                )
            }
        }
    }

    /** Plays one game, returning how many guesses it took. */
    private fun solve(secret: IntArray, space: List<IntArray>, shape: Shape): Int {
        // Seeded off the secret so a failure is reproducible rather than flaky.
        val rng = Rng(secret.fold(17L) { acc, c -> acc * 31 + c })
        var candidates = space
        var guesses = 0
        while (true) {
            guesses++
            val guess = candidates[rng.nextInt(candidates.size)]
            val reply = score(guess, secret, shape.colours)
            if (reply shr SHIFT == shape.slots) return guesses
            candidates = candidates.filter { score(guess, it, shape.colours) == reply }

            assertTrue(
                "no candidates left for ${secret.toList()} after $guesses guesses — the solver " +
                    "eliminated the secret itself, so the scoring is inconsistent",
                candidates.isNotEmpty(),
            )
        }
    }

    /**
     * `exact shl 4 or misplaced`, so a reply compares as one Int. The filter below runs once per
     * surviving candidate per guess, up to 32,768 of them on Expert's first move, which is cheap
     * only as long as comparing replies allocates nothing.
     */
    private fun score(guess: IntArray, secret: IntArray, colours: Int): Int {
        var exact = 0
        val secretLeft = IntArray(colours)
        val guessLeft = IntArray(colours)
        for (i in guess.indices) {
            if (guess[i] == secret[i]) exact++ else {
                secretLeft[secret[i]]++
                guessLeft[guess[i]]++
            }
        }
        var misplaced = 0
        for (c in 0 until colours) misplaced += minOf(secretLeft[c], guessLeft[c])
        return (exact shl SHIFT) or misplaced
    }

    /** Every code of [slots] pegs in [colours] colours, in odometer order. */
    private fun codeSpace(slots: Int, colours: Int): List<IntArray> =
        List(pow(colours, slots)) { index ->
            var rest = index
            IntArray(slots) { (rest % colours).also { rest /= colours } }
        }

    private companion object {
        /** Enough games per tier to expose a tail without the whole class outstaying its welcome. */
        const val GAMES = 300

        /** Spare guesses a tier must leave above a perfect solver's worst case. */
        const val MIN_HEADROOM = 3

        const val SHIFT = 4
        const val MASK = 0xF

        fun pow(base: Int, exponent: Int): Int {
            var out = 1
            repeat(exponent) { out *= base }
            return out
        }
    }
}
