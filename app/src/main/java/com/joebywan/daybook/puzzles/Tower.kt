package com.joebywan.daybook.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

/** Score for one submitted guess. */
data class Feedback(val exact: Int, val misplaced: Int)

data class TowerState(
    val slots: Int,
    val colours: Int,
    val maxGuesses: Int,
    val secret: List<Int>,
    val guesses: List<List<Int>>,
    val current: List<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = guesses.lastOrNull() == secret
    override val failed: Boolean get() = !solved && guesses.size >= maxGuesses

    val ready: Boolean get() = current.none { it < 0 }

    fun score(guess: List<Int>): Feedback {
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
        return Feedback(exact, misplaced)
    }

    fun withPeg(slot: Int, colour: Int): TowerState =
        copy(current = current.toMutableList().also { it[slot] = colour }, moves = moves + 1)

    fun submit(): TowerState =
        if (!ready) this
        else copy(
            guesses = guesses + listOf(current),
            current = List(slots) { -1 },
            moves = moves + 1,
        )
}

/**
 * Tower — Mastermind.
 *
 * Break a hidden colour code from scored guesses: a filled pip per peg that is the right colour in
 * the right place, a hollow pip per peg that is the right colour in the wrong place.
 */
object Tower : PuzzleType {

    override val id = "tower"
    override val displayName = "Tower"
    override val tagline = "Break the hidden colour code"
    override val accent = 0xFFD08A33
    override val rules = listOf(
        "A secret row of coloured pegs is hidden at the top.",
        "Tap a colour, then tap a slot to place it. Submit a full row to score it.",
        "A filled pip means one peg is the right colour in the right slot.",
        "A hollow pip means one peg is the right colour in the wrong slot.",
        "Pips are not aligned with the pegs — working out which is which is the puzzle.",
        "Colours may repeat in the secret.",
    )

    val palette = listOf(
        0xFFD9584C, 0xFF4C86D9, 0xFF54B07A, 0xFFE0B23C,
        0xFF9B6FD0, 0xFF48B9C4, 0xFFD97FB0, 0xFF9A8264,
    )

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> Triple(4, 6, 10)
        Difficulty.HARD -> Triple(5, 7, 10)
        Difficulty.EXPERT -> Triple(5, 8, 9)
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val rng = Rng(seed)
        val (slots, colours, tries) = shape(difficulty)
        val secret = List(slots) { rng.nextInt(colours) }
        return TowerState(slots, colours, tries, secret, emptyList(), List(slots) { -1 })
    }

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as TowerState
        // Drop one correct peg into the working row.
        val slot = s.current.indices.firstOrNull { s.current[it] != s.secret[it] } ?: return null
        return s.withPeg(slot, s.secret[slot])
    }

    override fun reveal(state: PuzzleState): PuzzleState {
        val s = state as TowerState
        return s.copy(guesses = s.guesses + listOf(s.secret), current = List(s.slots) { -1 })
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as TowerState
        val scheme = MaterialTheme.colorScheme
        var selectedColour by remember(s.secret) { mutableIntStateOf(0) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {

            Text(
                "${s.maxGuesses - s.guesses.size} guesses left",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = true,
            ) {
                items(s.guesses.reversed()) { guess ->
                    GuessRow(guess, s.score(guess), s.slots)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!s.solved && !s.failed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    s.current.forEachIndexed { slot, colour ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colour >= 0) Color(palette[colour]) else scheme.surfaceVariant
                                )
                                .border(1.dp, scheme.outline, CircleShape)
                                .clickable(enabled = interactive) {
                                    onState(s.withPeg(slot, selectedColour))
                                }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Submit",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (s.ready) scheme.primary else scheme.outline,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = interactive && s.ready) { onState(s.submit()) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(s.colours) { colour ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(palette[colour]))
                                .border(
                                    if (selectedColour == colour) 3.dp else 0.dp,
                                    scheme.onBackground,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(enabled = interactive) { selectedColour = colour }
                        )
                    }
                }
            }

            if (s.failed) {
                Text(
                    "Out of guesses. The code was:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                Row {
                    s.secret.forEach { colour ->
                        Box(
                            Modifier.padding(3.dp).size(30.dp).clip(CircleShape)
                                .background(Color(palette[colour]))
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun GuessRow(guess: List<Int>, feedback: Feedback, slots: Int) {
        val scheme = MaterialTheme.colorScheme
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surface)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            guess.forEach { colour ->
                Box(
                    Modifier.padding(2.dp).size(26.dp).clip(CircleShape)
                        .background(Color(palette[colour]))
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.width(66.dp), horizontalArrangement = Arrangement.End) {
                repeat(feedback.exact) {
                    Box(
                        Modifier.padding(1.dp).size(9.dp).clip(CircleShape)
                            .background(scheme.onSurface)
                    )
                }
                repeat(feedback.misplaced) {
                    Box(
                        Modifier.padding(1.dp).size(9.dp).clip(CircleShape)
                            .border(1.5.dp, scheme.onSurface, CircleShape)
                    )
                }
                repeat(slots - feedback.exact - feedback.misplaced) {
                    Box(
                        Modifier.padding(1.dp).size(9.dp).clip(CircleShape)
                            .background(scheme.surfaceVariant)
                    )
                }
            }
        }
    }
}
