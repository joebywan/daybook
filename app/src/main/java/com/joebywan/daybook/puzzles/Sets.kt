package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
        "Sets you have claimed sit above the board; tap one to light up its three cards again.",
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
     * how many discoveries the tier asks for. Twelve cards is the ceiling — the pane does not
     * scroll, and a board that size already lays out four to a row (see [boardColumns]) rather than
     * three, so a fifth row would still be cut off — so Expert climbs the second axis where Hard
     * has already maxed the first.
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

    /**
     * Height of the found-set strip, held open from the first move whether or not anything has
     * been claimed.
     *
     * The strip shares a column with the board, so a strip that grew as sets were banked would
     * shunt the cards downward under a finger already on its way to one. Mambo shipped that bug
     * with an appearing caption and it moved the grid about 45dp; here the slots are all laid out
     * empty at the start and filled in place, so the column's height never changes.
     *
     * 38dp is the smallest height at which a rendered thumbnail still shows all four traits on a
     * 360dp screen, and every dp of it is taken off a board that already has four rows to fit.
     */
    val SLOT_HEIGHT = 38.dp

    /** Wide enough for a comfortable thumbnail, so Standard's three do not sprawl across the row. */
    val SLOT_MAX_WIDTH = 76.dp

    val SLOT_GAP = 5.dp

    /**
     * The width below which a thumbnail stops carrying its four traits.
     *
     * Not enforced at runtime — nothing useful happens by refusing to draw — but asserted against
     * [slotWidth] so that raising [SLOT_MAX_WIDTH] or [SLOT_GAP] cannot quietly squeeze Expert's
     * six slots past the point the renders were judged at.
     */
    val SLOT_MIN_WIDTH = 40.dp

    /**
     * Width of one slot when [target] of them share [available].
     *
     * Dividing the row rather than fixing a width is what keeps all six Expert slots on one line
     * at any screen size. The alternatives both hide part of the record the strip exists to be:
     * scrolling puts the earliest sets off-screen behind a gesture nothing advertises, and
     * wrapping to a second line costs another [SLOT_HEIGHT] of a board with none to spare.
     */
    fun slotWidth(available: Dp, target: Int): Dp =
        minOf(SLOT_MAX_WIDTH, (available - SLOT_GAP * (target - 1)) / target)

    /** Gap between cards, and between rows of them. */
    val CARD_GAP = 8.dp

    /** A card is taller than it is wide: width / height, as [androidx.compose.ui.Modifier] asks. */
    const val CARD_ASPECT = 0.78f

    /** Inset as a fraction of the card, so shrinking a card shrinks its margin with it. */
    private const val CARD_INSET = 0.1f

    /**
     * The card width below which the four traits stop reading.
     *
     * Calibrated against the thumbnails rather than guessed: a slot 43.8dp wide holds three card
     * faces and was judged legible, so a board card at this width — wider than one of those faces
     * by a factor of two — is no harder to read than the strip already sitting above it.
     *
     * The tightest screen the app is expected on (320x533dp, with a three-button navigation bar)
     * lands at about 32dp at Expert, so this is a floor with a couple of dp of slack, not a target.
     */
    val CARD_MIN_WIDTH = 30.dp

    /**
     * Cards per row.
     *
     * Nine cards read cleanly as 3x3. Twelve do not divide the same way, and 3-across is what
     * forced Hard and Expert into four rows — on the narrowest supported phone that priced the
     * card right down to [CARD_MIN_WIDTH] with nothing left, because a fourth row is expensive in
     * a pane that does not scroll. 4-across turns the same twelve cards into three rows instead,
     * which is real slack rather than a floor reached on arrival.
     */
    fun boardColumns(cards: Int) = if (cards == 12) 4 else 3

    fun boardRows(cards: Int): Int {
        val columns = boardColumns(cards)
        return (cards + columns - 1) / columns
    }

    /**
     * Card width for a board of [cards] given both of the pane's constraints.
     *
     * Width alone is what the grid used to size from, and four rows of a width-derived card are
     * taller than the pane: at Hard and Expert the bottom row ran underneath the tool bar, on an
     * emulator as well as on paper. The home-screen motifs had the identical bug for the identical
     * reason, and [Mosaic]'s board is the fix already in this codebase — take the smaller of what
     * the width allows and what the height allows, and let the board be as big as *both* permit.
     */
    fun cardWidth(available: Dp, availableHeight: Dp, cards: Int): Dp {
        val columns = boardColumns(cards)
        val rows = boardRows(cards)
        val byWidth = (available - CARD_GAP * (columns - 1)) / columns
        val byHeight = (availableHeight - CARD_GAP * (rows - 1)) / rows * CARD_ASPECT
        return minOf(byWidth, byHeight).coerceAtLeast(1.dp)
    }

    /** The grid's full height, the quantity [cardWidth] is solving to keep inside the pane. */
    fun boardHeight(cardWidth: Dp, cards: Int): Dp {
        val rows = boardRows(cards)
        return cardWidth / CARD_ASPECT * rows + CARD_GAP * (rows - 1)
    }

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

        // Which thumbnail is being peeked at. Deliberately not in SetsState: PlayScreen pushes an
        // undo entry for every state handed to it, so a look at a set you already banked would
        // otherwise cost a press of Undo to take back.
        var peeked by remember(s.cards) { mutableIntStateOf(-1) }
        // Read through the list rather than trusted: Undo can retract the set being peeked at.
        val peekedCards = s.found.getOrNull(peeked)?.toSet().orEmpty()

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "${s.found.size} of ${s.target} sets found" + when {
                    s.lastWrong -> "   ·   not a set"
                    s.lastRepeat -> "   ·   already found"
                    else -> ""
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (s.lastWrong) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            FoundStrip(s, peeked) { slot -> peeked = if (peeked == slot) -1 else slot }
            Spacer(Modifier.height(10.dp))

            // `fill = false` so the column still wraps its content and stays centred in the pane.
            // The weight is here only to learn how much height is left once the counter and the
            // strip have taken theirs — the quantity the old grid never asked for.
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f, fill = false),
                contentAlignment = Alignment.TopCenter,
            ) {
                val width = cardWidth(maxWidth, maxHeight, s.cards.size)
                val columns = boardColumns(s.cards.size)
                Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                    s.cards.chunked(columns).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                            row.forEachIndexed { colIndex, card ->
                                val index = rowIndex * columns + colIndex
                                BoardCard(
                                    card = card,
                                    width = width,
                                    selected = index in s.selected,
                                    peeked = index in peekedCards,
                                    used = index in used,
                                    interactive = interactive,
                                ) {
                                    // A tap is the player moving on; leaving the highlight up
                                    // would tint cards they are now picking between.
                                    peeked = -1
                                    onState(tap(s, index))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * One card on the board.
     *
     * A used card gets a wash and a hairline rather than the grey-out a spent card would earn: it
     * is still live, and the tint only shows where the found sets already run. A peeked card —
     * one of the three behind a thumbnail the player is pointing at — is washed harder, because
     * it answers a direct question and has to be told apart from that faint tint at a glance.
     */
    @Composable
    private fun BoardCard(
        card: Card,
        width: Dp,
        selected: Boolean,
        peeked: Boolean,
        used: Boolean,
        interactive: Boolean,
        onTap: () -> Unit,
    ) {
        val scheme = MaterialTheme.colorScheme
        val corner = RoundedCornerShape(12.dp)
        val fill = when {
            peeked -> Color(accent).copy(alpha = 0.34f).compositeOver(scheme.surface)
            used -> Color(accent).copy(alpha = 0.14f).compositeOver(scheme.surface)
            else -> scheme.surface
        }
        Box(
            Modifier
                .width(width)
                .height(width / CARD_ASPECT)
                .clip(corner)
                .background(fill)
                .border(
                    when {
                        selected -> 2.5.dp
                        peeked -> 2.dp
                        used -> 1.dp
                        else -> 0.dp
                    },
                    Color(accent).copy(alpha = if (selected || peeked) 1f else 0.45f),
                    corner,
                )
                .clickable(enabled = interactive, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            CardFace(card, inset = width * CARD_INSET)
        }
    }

    /**
     * The record of what has already been banked.
     *
     * A counter saying "2 of 6" leaves the player no way to tell a set they have claimed from one
     * they have only re-derived, so they spend the hard part of the game rediscovering their own
     * work. Each slot is a whole set at about a third of a card's footprint: the three cards side
     * by side, drawn by the same [CardFace] the board uses so a thumbnail cannot drift away from
     * what it stands for. Side by side rather than stacked because this game's card art runs its
     * symbols *down* the card — three of them laid out across is the same "one row per card" the
     * arrangement is for, turned to match the art.
     */
    @Composable
    private fun FoundStrip(state: SetsState, peeked: Int, onPeek: (Int) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(SLOT_HEIGHT)) {
            val width = slotWidth(maxWidth, state.target)
            Row(
                Modifier.fillMaxWidth(),
                // Centred because the cap leaves Standard's three slots well short of the board's
                // width, and a short row hung off the left edge reads as a row that got cut off.
                horizontalArrangement = Arrangement.spacedBy(SLOT_GAP, Alignment.CenterHorizontally),
            ) {
                repeat(state.target) { slot ->
                    SetSlot(
                        cards = state.found.getOrNull(slot)?.map { state.cards[it] },
                        width = width,
                        peeked = slot == peeked,
                        onPeek = { onPeek(slot) },
                    )
                }
            }
        }
    }

    /**
     * One slot in the strip: a claimed set, or the outline of one still to come.
     *
     * The empty outlines are not decoration. They are what makes the reserved height read as a
     * place for something rather than a gap the layout forgot, and they say how many sets are
     * left in the same glance that says which ones are done.
     */
    @Composable
    private fun SetSlot(cards: List<Card>?, width: Dp, peeked: Boolean, onPeek: () -> Unit) {
        val scheme = MaterialTheme.colorScheme
        val corner = RoundedCornerShape(7.dp)
        Box(
            Modifier
                .width(width)
                .fillMaxHeight()
                .clip(corner)
                .background(if (cards == null) Color.Transparent else scheme.surface)
                .border(
                    if (peeked) 2.dp else 1.dp,
                    Color(accent).copy(
                        alpha = when {
                            cards == null -> 0.20f
                            peeked -> 1f
                            else -> 0.45f
                        }
                    ),
                    corner,
                )
                .then(if (cards == null) Modifier else Modifier.clickable(onClick = onPeek))
                .padding(4.dp),
        ) {
            if (cards != null) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    cards.forEach { card ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            // Far smaller than the board's 10dp: at this size the inset is margin
                            // the symbols cannot spare.
                            CardFace(card, inset = 1.5.dp)
                        }
                    }
                }
            }
        }
    }

    /**
     * [inset] scales the symbols to the card: a board card runs from ~100dp down to under 40dp
     * once the pane's height gets a say, a home tile's is ~36dp, and a found-set thumbnail's is
     * barely 15dp — so the board passes a fraction of its own width rather than a fixed margin.
     */
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
