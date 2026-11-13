package com.joebywan.daybook.puzzles

import com.joebywan.daybook.ui.theme.vivid
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.ring
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.arcadeLift
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.style
import androidx.compose.ui.graphics.SolidColor
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.boardSp

/**
 * Target: six numbers and a target; combine two tiles with + - x / (whole numbers only) and the answer is a new tile.
 * Tap a tile, an operator, another tile. Highlight indices: tiles are slots `0..5`, an operator is [OP0] + its ordinal,
 * the target is [TARGET]. See [TargetLogic] for the proof each board carries and [TargetTeacher] for the hints.
 */
object Target : PuzzleType {

    /** Highlight index of the first operator (+); the others follow in [TargetOp] order. */
    const val OP0 = 10

    /** Highlight index of the target banner. */
    const val TARGET = 20

    override val id = "target"
    override val displayName = "Target"
    override val tagline = "Six numbers make the target"
    override val accent = 0xFFB47699
    override val rules = listOf(
        "Reach the target. Pick two numbers and an operator, and the answer replaces them as a new tile.",
        "Any tile equal to the target wins. Each number is used once, and you need not use them all.",
        "Answers must be whole numbers from 1 to 9,999: no negatives, fractions or zero. Subtract and divide put the bigger tile first.",
        "Tap a number, tap + − × ÷, then tap another number. Undo takes a step back.",
    )

    override val keyboardHelp = listOf(
        "Type a tile's number, then + - * /, then another tile's number and Enter. Backspace steps back.",
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        TargetLogic.generateVerified(seed, difficulty) ?: TargetLogic.lastResort(difficulty)

    override fun teach(state: PuzzleState): Deduction? = TargetTeacher.deduction(state as TargetState)

    // ---- walkthrough --------------------------------------------------------------------------

    /** 25 x 4 = 100, then 100 + 3 x 2 (or 100 + 7 - 1) = 106. Proved to need three steps by a test. */
    internal fun tutorialBoard(): TargetState = TargetState(listOf(25, 4, 3, 2, 7, 1), 106, 3)

    /** Accepts a board whose tiles are exactly those of [expected], however the player ordered the two. */
    private fun makes(expected: TargetState): (PuzzleState) -> Boolean = { next ->
        next is TargetState && next.tiles.sorted() == expected.tiles.sorted()
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val s0 = tutorialBoard()
        val s1 = s0.combine(0, TargetOp.Mul, 1)!!
        listOf(
            TutorialFrame(
                state = s0,
                caption = "Reach 106 from these six numbers. Combine two with + − × ÷ and the answer is a new tile. " +
                    "It must be a whole number above zero: no 7 ÷ 2, no 5 − 5.",
                highlight = BoardHighlight(strong = setOf(TARGET), soft = (0..3).map { OP0 + it }.toSet()),
            ),
            TutorialFrame(
                state = s0,
                caption = "25 × 4 makes 100. Tap 25, tap ×, then tap 4.",
                highlight = BoardHighlight(strong = setOf(0, 1, OP0 + TargetOp.Mul.ordinal)),
                accepts = makes(s1),
                retry = "Tap the glowing 25, then ×, then 4.",
                done = "100 is a new tile, and 25 and 4 are used up.",
            ),
            TutorialFrame(
                state = s1,
                caption = "Your turn: make 106 from here. You need not use every number. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- drawing helpers ----------------------------------------------------------------------

    /** A tile's number on the digit scale: a given's size for its square, the square shrunk until three or four digits fit. */
    @Composable
    private fun tileStyle(value: Int, u: Dp) = BoardGlyph.GIVEN.style(
        u * 0.95f * when {
            value >= 1000 -> 0.6f
            value >= 100 -> 0.75f
            else -> 0.9f
        },
    )

    // ---- the home motif -----------------------------------------------------------------------

    @Composable
    override fun Preview(modifier: Modifier) {
        if (!classicLook()) return ArcadePreview(modifier)
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val (hueA, hueB) = BoardHues.HUE_PAIRS[0]
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val u = minOf(maxWidth, maxHeight) / 3.0f
            val made = BoardHues.fill(hueB, dark)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(u * 0.12f)) {
                Box(
                    Modifier.width(u * 2.6f).height(u * 0.95f).clip(RoundedCornerShape(u * 0.2f)).background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("42", color = BoardHues.ink(hueB, dark), fontSize = (u * 0.6f).boardSp(), lineHeight = (u * 0.6f).boardSp() * 1.2f, fontWeight = FontWeight.Bold) }
                Row(horizontalArrangement = Arrangement.spacedBy(u * 0.12f), verticalAlignment = Alignment.CenterVertically) {
                    for (n in listOf(6, 7)) Box(
                        Modifier.size(u * 0.95f).clip(RoundedCornerShape(u * 0.2f)).background(scheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = scheme.onSurface, fontSize = (u * 0.52f).boardSp(), lineHeight = (u * 0.52f).boardSp() * 1.2f, fontWeight = FontWeight.SemiBold) }
                    Text("×", color = BoardHues.ink(hueA, dark), fontSize = (u * 0.6f).boardSp(), lineHeight = (u * 0.6f).boardSp() * 1.2f, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(u * 0.95f).clip(RoundedCornerShape(u * 0.2f)).background(made),
                    contentAlignment = Alignment.Center,
                ) { Text("42", color = BoardHues.onFill(made), fontSize = (u * 0.4f).boardSp(), lineHeight = (u * 0.4f).boardSp() * 1.2f, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    /** The motif in Arcade: the target on its given's lavender, two tiles, and the answer they make in the control's gradient. */
    @Composable
    private fun ArcadePreview(modifier: Modifier) {
        val a = ArcadeBoard.current
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val u = minOf(maxWidth, maxHeight) / 3.0f
            val shape = RoundedCornerShape(u * 0.2f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(u * 0.12f)) {
                Box(
                    Modifier.width(u * 2.6f).height(u * 0.95f).clip(shape).background(a.given),
                    contentAlignment = Alignment.Center,
                ) { Text("42", color = a.givenInk, fontSize = (u * 0.6f).boardSp(), lineHeight = (u * 0.6f).boardSp() * 1.2f, fontWeight = FontWeight.Bold) }
                Row(horizontalArrangement = Arrangement.spacedBy(u * 0.12f), verticalAlignment = Alignment.CenterVertically) {
                    for (n in listOf(6, 7)) Box(
                        Modifier.size(u * 0.95f).clip(shape).background(a.square).border(1.dp, a.frame, shape),
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = a.squareInk, fontSize = (u * 0.52f).boardSp(), lineHeight = (u * 0.52f).boardSp() * 1.2f, fontWeight = FontWeight.SemiBold) }
                    Text("×", color = a.squareInk, fontSize = (u * 0.6f).boardSp(), lineHeight = (u * 0.6f).boardSp() * 1.2f, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(u * 0.95f).clip(shape).background(a.control.brush),
                    contentAlignment = Alignment.Center,
                ) { Text("42", color = a.control.ink, fontSize = (u * 0.4f).boardSp(), lineHeight = (u * 0.4f).boardSp() * 1.2f, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    // ---- the board ----------------------------------------------------------------------------

    /** The line under the target: the move being built, a refusal, the last sum, or what to do. One line, always reserved. */
    private fun captionOf(s: TargetState, e: TargetEntry, note: String?): String {
        if (note != null) return note
        val first = e.first?.let { s.slots[it] }
        return when {
            first != null || e.typed.isNotEmpty() ->
                listOfNotNull(first?.toString(), e.op?.symbol, e.typed.ifEmpty { null }).joinToString(" ").let { if (e.typed.isEmpty()) "$it …" else it }
            s.log.isNotEmpty() -> s.log.last().text()
            else -> "Tap a number, then + − × ÷"
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as TargetState
        val scheme = MaterialTheme.colorScheme
        val dark = BoardHues.isDark(scheme)
        val hues = remember(s.start, s.target) { BoardHues.pair((s.start + s.target).hashCode()) }
        val (hueA, hueB) = hues
        val classic = classicLook()
        val a = ArcadeBoard.current
        val (highlight, glow, pulse) = if (classic) rememberHintPaint() else rememberHintPaint(ink = a.selected.from)

        // The move being built is transient: it starts over whenever the board changes (a finished operation, undo, a hint).
        var entry by remember(s.slots) { mutableStateOf(TargetEntry()) }
        var note by remember(s.slots) { mutableStateOf<String?>(null) }
        // An operation's answer settles into its tile. A unit is a band of nearness (within half the target, a quarter, a
        // tenth, 4%): the first tile into a band glows and plays a note, read off the tiles alone, never a road.
        val near = TargetLogic.NEAR.map { TargetLogic.within(s.slots, s.target, it) }
        val feel = rememberMoveFeel(s.slots, near) { near[it].isNotEmpty() }
        fun go(step: TargetStep) {
            entry = step.entry
            note = step.note
            step.state?.let(onState)
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .boardKeys(interactive && !s.solved, shift = true) { key, shift, repeat ->
                    val action = targetKeyAction(key, shift) ?: return@boardKeys false
                    if (!repeat) go(s.press(entry, action))
                    true
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Both axes: the column is 3.6 units wide and about 4.7 tall.
            val u = if (constraints.hasBoundedHeight) minOf(maxWidth / 3.6f, maxHeight / 5.0f) else maxWidth / 3.6f
            val gap = u * 0.12f
            val corner = u * 0.16f
            val shape = RoundedCornerShape(corner)
            val made = s.log.map { it.a }.toSet()

            Column(Modifier.width(u * 3.6f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                // The target.
                val look = highlight.look(TARGET)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(u * 1.05f)
                        .highlightAnchor(TARGET)
                        .ring(look, glow, pulse, corner, outside = true)
                        .dimmed(look)
                        // Arcade: the target is the board's given, lavender; reached, it takes the win's amber.
                        .then(if (!classic && s.solved) Modifier.arcadeLift(a.selected, corner) else Modifier)
                        .clip(shape)
                        .background(if (classic) SolidColor(scheme.surface) else if (s.solved) a.selected.brush else SolidColor(a.given)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val targetInk = if (s.solved) a.selected.ink else a.givenInk
                    Text("TARGET", color = if (classic) scheme.onSurfaceVariant else targetInk.copy(alpha = 0.75f), fontSize = (u * 0.17f).boardSp(), lineHeight = (u * 0.17f).boardSp() * 1.2f, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${s.target}",
                        color = if (!classic) targetInk else if (s.solved) scheme.primary else BoardHues.ink(hueB, dark),
                        fontSize = (u * 0.62f).boardSp(), lineHeight = (u * 0.62f).boardSp() * 1.2f,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // The move being made, or the last one. Always one line high, so nothing moves when it changes.
                Box(Modifier.fillMaxWidth().height(u * 0.42f), contentAlignment = Alignment.Center) {
                    Text(
                        captionOf(s, entry, note),
                        color = if (note != null) (if (classic) scheme.error else a.error) else scheme.onSurfaceVariant,
                        fontSize = (u * 0.2f).boardSp(), lineHeight = (u * 0.2f).boardSp() * 1.2f,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }

                // The tiles: six places, an answer goes where its first tile was.
                Column(Modifier.keepClear(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until 3) {
                            val slot = row * 3 + col
                            val value = s.slots[slot]
                            val l = highlight.look(slot)
                            val picked = entry.first == slot
                            val hit = value == s.target
                            val fill = if (value > 0 && slot in made) BoardHues.fill(hueB, dark) else scheme.surface
                            // Arcade: a starting number is a plain tile, one the player made the control's gradient (as Sudoku's
                            // digits), the picked one amber, the target reached amber too; picked and reached also take an edge.
                            val tile = when {
                                value <= 0 -> null
                                picked || hit -> a.selected
                                slot in made -> a.control
                                else -> ArcadeFill(a.square, a.square, a.squareInk)
                            }
                            Box(
                                Modifier
                                    .size(u * 1.1f, u * 0.95f)
                                    .moveFeel(feel, slot, if (classic) vivid(accent) else a.selected.from)
                                    .highlightAnchor(slot)
                                    .ring(l, glow, pulse, corner, outside = true)
                                    .dimmed(l)
                                    .then(if (!classic && tile != null) Modifier.arcadeLift(tile, corner) else Modifier)
                                    .clip(shape)
                                    .then(if (classic) Modifier else arcadeTile(tile, picked || hit, shape, a))
                                    .then(if (classic) classicTile(value, fill, picked, hit, hueA, dark, shape) else Modifier)
                                    .clickable(enabled = interactive && value > 0 && !s.solved) { go(s.tapTile(entry, slot)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (value > 0) Text(
                                    "$value",
                                    color = tile?.takeIf { !classic }?.ink ?: if (fill == scheme.surface) scheme.onSurface else BoardHues.onFill(fill),
                                    style = tileStyle(value, u),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // The operators.
                Row(Modifier.fillMaxWidth().keepClear(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (op in TargetOp.entries) {
                        val l = highlight.look(OP0 + op.ordinal)
                        val chosen = entry.op == op
                        // Arcade: an operator is a key on the given's lavender; the chosen one amber with an edge.
                        val key = if (chosen) a.selected else ArcadeFill(a.given, a.given, a.givenInk)
                        Box(
                            Modifier
                                .size(u * 0.82f)
                                .highlightAnchor(OP0 + op.ordinal)
                                .ring(l, glow, pulse, corner, outside = true)
                                .dimmed(l)
                                .then(if (!classic && chosen) Modifier.arcadeLift(key, corner) else Modifier)
                                .clip(shape)
                                .then(
                                    if (classic) Modifier
                                        .background(scheme.surface)
                                        .background(if (chosen) BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.40f) else Color.Transparent)
                                        .border(if (chosen) 3.dp else 1.dp, if (chosen) BoardHues.mark(hueA) else scheme.outline, shape)
                                    else arcadeTile(key, chosen, shape, a)
                                )
                                .clickable(enabled = interactive && !s.solved) { go(s.tapOp(entry, op)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(op.symbol, color = if (classic) BoardHues.ink(hueA, dark) else key.ink, fontSize = (u * 0.5f).boardSp(), lineHeight = (u * 0.5f).boardSp() * 1.2f, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    /** Classic's tile, as before Arcade: its fill, the picked tint, and an outline heavier when picked or the target. */
    @Composable
    private fun classicTile(value: Int, fill: Color, picked: Boolean, hit: Boolean, hueA: Float, dark: Boolean, shape: RoundedCornerShape): Modifier {
        val scheme = MaterialTheme.colorScheme
        return Modifier
            .background(if (value > 0) fill else Color.Transparent)
            .background(if (picked) BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.40f) else Color.Transparent)
            .border(
                if (hit || picked) 3.dp else 1.dp,
                when {
                    hit -> scheme.primary
                    picked -> BoardHues.mark(hueA)
                    value > 0 -> scheme.outline
                    else -> scheme.outline.copy(alpha = 0.3f)
                },
                shape,
            )
    }

    /** Arcade's tile: [fill], an edge when [edged]; an empty place ([fill] null) is a faint outline of the frame. */
    private fun arcadeTile(fill: ArcadeFill?, edged: Boolean, shape: RoundedCornerShape, a: ArcadeBoard): Modifier = when {
        fill == null -> Modifier.border(1.dp, a.frame.copy(alpha = a.frame.alpha * 0.3f), shape)
        edged -> Modifier.background(fill.brush).border(3.dp, a.selectedEdge, shape)
        else -> Modifier.background(fill.brush).border(1.dp, a.frame.copy(alpha = a.frame.alpha * 0.35f), shape)
    }
}
