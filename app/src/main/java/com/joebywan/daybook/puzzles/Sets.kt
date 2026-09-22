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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * A card is four traits, each with three values, so it encodes neatly as four base-3 digits:
 * count, shape, shading, colour.
 */
@Serializable
data class Card(val count: Int, val shape: Int, val shading: Int, val colour: Int) {
    val traits: List<Int> get() = listOf(count, shape, shading, colour)
}

@Serializable
data class SetsState(
    val cards: List<Card>,
    val target: Int,
    val found: List<List<Int>>,
    val selected: List<Int>,
    val lastWrong: Boolean = false,
    val lastRepeat: Boolean = false,
    override val moves: Int = 0,
) : PuzzleState {
    override val solved: Boolean get() = found.size == target
}

/**
 * Sets — the card game SET.
 *
 * The target counts *every* set the board contains, and sets on a board overlap: any two cards
 * determine their third, so one card routinely sits in several sets at once. Spotting those shared
 * cards is the puzzle. Cards are therefore never used up — claiming a set marks its cards but
 * leaves them tappable, because retiring them would cap a player at ⌊cards / 3⌋ claims and put
 * every richer target permanently out of reach.
 *
 * Boards are rejection-sampled until they contain exactly the intended number of sets, so the
 * target shown is always both complete and reachable.
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
        "Cards are never used up — a tinted card is one you have already used, and it is still in play.",
    )

    private val colours = listOf(0xFFD9584C, 0xFF4C86D9, 0xFF54B07A)

    /**
     * The two cards on the home tile.
     *
     * Picked, not generated, and picked to disagree on all four traits at once: two solid red
     * ovals against three hatched green diamonds. A tile is ~80dp, so only two cards fit at a
     * size where the count can still be counted and the hatching still reads as hatching — and
     * the traits *are* the game, so a pair that differs on every one of them says more about
     * Sets than three near-identical cards would.
     */
    private val previewCards = listOf(
        Card(count = 1, shape = 0, shading = 0, colour = 0),
        Card(count = 2, shape = 1, shading = 2, colour = 2),
    )

    private const val DRAWS = 4000

    /**
     * Board size and set count, per tier.
     *
     * Two axes decide how hard a board plays, and both have to climb or the ladder inverts — which
     * is how Expert once shipped easier than Standard. The first is how many triples must be
     * examined before you can be sure nothing is left: C(9,3) = 84, C(12,3) = 220. The second is
     * how many discoveries the tier asks for. Twelve cards is the ceiling — the board lays out
     * three to a row in a pane that does not scroll, so a fifth row would be cut off — so Expert
     * climbs the second axis where Hard has already maxed the first.
     */
    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 9 to 3   //  84 triples to scan, 3 sets to find
        Difficulty.HARD -> 12 to 4      // 220 triples to scan, 4 sets to find
        Difficulty.EXPERT -> 12 to 6    // 220 triples to scan, 6 sets to find
    }

    /**
     * Two sets can share a card but never a pair, since any two cards fix their third uniquely. So
     * each set spends three of the board's C(n,2) pairs outright, and this bound is exact at n = 9.
     * It turns an unsatisfiable target into a failure on the first call rather than 4000 futile
     * draws followed by a board that quietly disagrees with the number on screen.
     */
    fun maxSets(cards: Int) = cards * (cards - 1) / 2 / 3

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (size, wanted) = shape(difficulty)
        require(wanted in 1..maxSets(size)) {
            "$difficulty wants $wanted sets from $size cards, which hold at most ${maxSets(size)}"
        }
        val deck = buildList {
            for (a in 0..2) for (b in 0..2) for (c in 0..2) for (d in 0..2) add(Card(a, b, c, d))
        }

        for (draw in 0..DRAWS) {
            val board = Rng(if (draw == 0) seed else seed + draw).shuffled(deck).take(size)
            val sets = allSets(board)
            if (sets.size == wanted) return SetsState(board, sets.size, emptyList(), emptyList())
        }
        // Falling through with whatever was drawn last is what shipped the unwinnable board: the
        // target silently became "however many this one happens to have" instead of the tier's.
        error("no $size-card board holds exactly $wanted sets after $DRAWS draws ($difficulty)")
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

    /**
     * Public so the rules can be played, not just inspected, from a test — the board's target is
     * only meaningful if tapping actually reaches it.
     *
     * A card already used in a found set is deliberately still tappable; only the *trio* is spent.
     * Re-offering a trio already claimed is a misread rather than a mistake, so it clears the
     * selection without the wrong-answer flash.
     */
    fun tap(s: SetsState, index: Int): SetsState {
        val stepped = s.copy(lastWrong = false, lastRepeat = false, moves = s.moves + 1)
        if (index in s.selected) return stepped.copy(selected = s.selected - index)

        val picked = s.selected + index
        if (picked.size < 3) return stepped.copy(selected = picked)

        val (a, b, c) = picked
        val trio = picked.sorted()
        return when {
            !isSet(s.cards[a], s.cards[b], s.cards[c]) ->
                stepped.copy(selected = emptyList(), lastWrong = true)
            trio in s.found ->
                stepped.copy(selected = emptyList(), lastRepeat = true)
            else ->
                stepped.copy(found = s.found + listOf(trio), selected = emptyList())
        }
    }

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as SetsState
        val next = allSets(s.cards).firstOrNull { it !in s.found } ?: return null
        return s.copy(
            found = s.found + listOf(next),
            selected = emptyList(),
            lastWrong = false,
            lastRepeat = false,
            moves = s.moves + 1,
        )
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val corner = RoundedCornerShape(8.dp)
        Row(
            modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            previewCards.forEach { card ->
                Box(
                    Modifier
                        .weight(1f)
                        // Taller than the board's 0.78 on purpose: the tile is square, and at two
                        // cards wide the width is spoken for long before the height is, so the
                        // spare height goes into the symbols rather than into margin.
                        .aspectRatio(0.64f)
                        .clip(corner)
                        .background(scheme.surface)
                        .border(1.dp, Color(accent).copy(alpha = 0.55f), corner),
                    contentAlignment = Alignment.Center,
                ) {
                    CardFace(card, inset = 4.dp)
                }
            }
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SetsState
        val scheme = MaterialTheme.colorScheme
        val used = s.found.flatten().toSet()
        // Used cards get a wash and a hairline rather than the grey-out a spent card would earn:
        // they are still live, and the tint is only there to show where the found sets already run.
        val usedTint = Color(accent).copy(alpha = 0.14f).compositeOver(scheme.surface)

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "${s.found.size} of ${s.target} sets found" + when {
                    s.lastWrong -> "   ·   not a set"
                    s.lastRepeat -> "   ·   already found"
                    else -> ""
                },
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
                        val isUsed = index in used
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.78f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isUsed) usedTint else scheme.surface)
                                .border(
                                    when {
                                        isSelected -> 2.5.dp
                                        isUsed -> 1.dp
                                        else -> 0.dp
                                    },
                                    Color(accent).copy(alpha = if (isSelected) 1f else 0.45f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = interactive) { onState(tap(s, index)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            CardFace(card, inset = 10.dp)
                        }
                    }
                }
            }
        }
    }

    /** [inset] scales the symbols to the card: a full board card is ~100dp, a home tile's ~36dp. */
    @Composable
    private fun CardFace(card: Card, inset: Dp) {
        val colour = Color(colours[card.colour])
        Canvas(Modifier.fillMaxSize().padding(inset)) {
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
