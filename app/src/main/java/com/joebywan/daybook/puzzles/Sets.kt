package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

/**
 * A card is four traits, each with three values, so it encodes neatly as four base-3 digits:
 * count, shape, shading, colour.
 */
data class Card(val count: Int, val shape: Int, val shading: Int, val colour: Int) {
    val traits: List<Int> get() = listOf(count, shape, shading, colour)
}

data class SetsState(
    val cards: List<Card>,
    val target: Int,
    val found: List<List<Int>>,
    val selected: List<Int>,
    val lastWrong: Boolean = false,
    override val moves: Int = 0,
) : PuzzleState {
    override val solved: Boolean get() = found.size == target
}

/**
 * Sets — the card game SET.
 *
 * Boards are rejection-sampled until they contain exactly the intended number of sets, so the
 * target count shown to the player is always achievable and never an over-count.
 */
object Sets : PuzzleType {

    override val id = "sets"
    override val displayName = "Sets"
    override val tagline = "All alike or all different, four ways"
    override val accent = 0xFF54B07A
    override val rules = listOf(
        "Each card has a count, a shape, a shading and a colour.",
        "Three cards form a set when, for every one of those four traits, they are either all the same or all different.",
        "Tap three cards to claim a set. Find them all to finish.",
    )

    private val colours = listOf(0xFFD9584C, 0xFF4C86D9, 0xFF54B07A)

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 9 to 4
        Difficulty.HARD -> 12 to 5
        Difficulty.EXPERT -> 12 to 3
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (size, wanted) = shape(difficulty)
        var rng = Rng(seed)
        val deck = buildList {
            for (a in 0..2) for (b in 0..2) for (c in 0..2) for (d in 0..2) add(Card(a, b, c, d))
        }

        var board = rng.shuffled(deck).take(size)
        var attempts = 0
        while (allSets(board).size != wanted && attempts < 4000) {
            rng = Rng(seed + attempts + 1)
            board = rng.shuffled(deck).take(size)
            attempts++
        }
        return SetsState(board, allSets(board).size, emptyList(), emptyList())
    }

    fun isSet(a: Card, b: Card, c: Card): Boolean =
        a.traits.indices.all { t ->
            val values = setOf(a.traits[t], b.traits[t], c.traits[t])
            values.size == 1 || values.size == 3
        }

    fun allSets(cards: List<Card>): List<List<Int>> = buildList {
        for (i in cards.indices) for (j in i + 1 until cards.size) for (k in j + 1 until cards.size) {
            if (isSet(cards[i], cards[j], cards[k])) add(listOf(i, j, k))
        }
    }

    private fun tap(s: SetsState, index: Int): SetsState {
        if (s.found.any { index in it }) return s
        if (index in s.selected) {
            return s.copy(selected = s.selected - index, lastWrong = false, moves = s.moves + 1)
        }
        val picked = s.selected + index
        if (picked.size < 3) return s.copy(selected = picked, lastWrong = false, moves = s.moves + 1)

        val (a, b, c) = picked
        val trio = picked.sorted()
        return if (isSet(s.cards[a], s.cards[b], s.cards[c]) && trio !in s.found) {
            s.copy(found = s.found + listOf(trio), selected = emptyList(), lastWrong = false, moves = s.moves + 1)
        } else {
            s.copy(selected = emptyList(), lastWrong = true, moves = s.moves + 1)
        }
    }

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as SetsState
        val next = allSets(s.cards).firstOrNull { it !in s.found } ?: return null
        return s.copy(found = s.found + listOf(next), selected = emptyList(), moves = s.moves + 1)
    }

    override fun reveal(state: PuzzleState): PuzzleState {
        val s = state as SetsState
        return s.copy(found = allSets(s.cards), selected = emptyList())
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SetsState
        val scheme = MaterialTheme.colorScheme
        val claimed = s.found.flatten().toSet()

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "${s.found.size} of ${s.target} sets found" + if (s.lastWrong) "   ·   not a set" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (s.lastWrong) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            s.cards.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEachIndexed { colIndex, card ->
                        val index = rowIndex * 3 + colIndex
                        val isSelected = index in s.selected
                        val isClaimed = index in claimed
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.78f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isClaimed) scheme.surfaceVariant else scheme.surface)
                                .border(
                                    if (isSelected) 2.5.dp else 0.dp,
                                    Color(accent),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = interactive && !isClaimed) {
                                    onState(tap(s, index))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            CardFace(card, dimmed = isClaimed)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CardFace(card: Card, dimmed: Boolean) {
        val colour = Color(colours[card.colour]).copy(alpha = if (dimmed) 0.25f else 1f)
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val slots = card.count + 1
            val slotHeight = size.height / 3f
            val symbolHeight = slotHeight * 0.74f
            val symbolWidth = size.width * 0.66f
            val top = (size.height - slots * slotHeight) / 2f
            repeat(slots) { i ->
                val cx = size.width / 2f
                val cy = top + slotHeight * (i + 0.5f)
                drawSymbol(
                    shape = card.shape,
                    shading = card.shading,
                    colour = colour,
                    centre = Offset(cx, cy),
                    width = symbolWidth,
                    height = symbolHeight,
                )
            }
        }
    }

    private fun DrawScope.drawSymbol(
        shape: Int,
        shading: Int,
        colour: Color,
        centre: Offset,
        width: Float,
        height: Float,
    ) {
        val path = Path()
        val left = centre.x - width / 2
        val top = centre.y - height / 2
        when (shape) {
            0 -> path.addOval(androidx.compose.ui.geometry.Rect(left, top, left + width, top + height))
            1 -> {
                path.moveTo(centre.x, top)
                path.lineTo(left + width, centre.y)
                path.lineTo(centre.x, top + height)
                path.lineTo(left, centre.y)
                path.close()
            }
            else -> path.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left, top, left + width, top + height,
                    androidx.compose.ui.geometry.CornerRadius(height * 0.3f),
                )
            )
        }

        when (shading) {
            0 -> drawPath(path, colour)                                     // solid
            1 -> drawPath(path, colour, style = Stroke(width = height * 0.09f))  // outline
            else -> {                                                       // hatched
                clipPath(path) {
                    var x = left
                    while (x < left + width) {
                        drawLine(
                            colour,
                            Offset(x, top),
                            Offset(x, top + height),
                            strokeWidth = height * 0.06f,
                        )
                        x += height * 0.20f
                    }
                }
                drawPath(path, colour, style = Stroke(width = height * 0.09f))
            }
        }
    }

    private fun DrawScope.clipPath(path: Path, block: DrawScope.() -> Unit) {
        drawContext.canvas.save()
        drawContext.canvas.clipPath(path)
        block()
        drawContext.canvas.restore()
    }
}
