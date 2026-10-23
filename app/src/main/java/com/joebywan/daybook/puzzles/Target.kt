package com.joebywan.daybook.puzzles

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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.ui.rememberHintPulse
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel

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
    override val accent = 0xFF6661D1
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
        generateVerified(seed, difficulty) ?: TargetLogic.lastResort(difficulty)

    /** The board when its target was proved reachable in the tier's number of steps, null otherwise. */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): TargetState? =
        TargetLogic.generateVerified(seed, difficulty)

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

    /** What [LocalBoardHighlight] asks of one element: it glows when named, and everything else steps back. */
    private class Look(val strong: Boolean, val soft: Boolean, val dim: Boolean)

    private fun BoardHighlight.look(index: Int): Look {
        val strong = index in this.strong
        val soft = !strong && index in this.soft
        return Look(strong, soft, !isEmpty && !strong && !soft)
    }

    /** A ring just outside the element: breathing for strong, quiet for soft. Apply before the element's own clip. */
    private fun Modifier.ring(look: Look, glow: Color, pulse: State<Float>, corner: Dp): Modifier =
        if (!look.strong && !look.soft) this
        else drawWithContent {
            drawContent()
            val w = (if (look.strong) 2.5.dp else 1.5.dp).toPx()
            val out = 1.dp.toPx() + w / 2
            drawRoundRect(
                if (look.strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f),
                topLeft = Offset(-out, -out),
                size = Size(size.width + 2 * out, size.height + 2 * out),
                cornerRadius = CornerRadius(corner.toPx() + out),
                style = Stroke(w),
            )
        }

    private fun Modifier.dimmed(look: Look): Modifier = if (look.dim) alpha(0.3f) else this

    private fun fontFor(value: Int, u: Dp) = (u.value * when {
        value >= 1000 -> 0.34f
        value >= 100 -> 0.42f
        else -> 0.5f
    }).sp

    // ---- the home motif -----------------------------------------------------------------------

    @Composable
    override fun Preview(modifier: Modifier) {
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
                ) { Text("42", color = BoardHues.ink(hueB, dark), fontSize = (u.value * 0.6f).sp, fontWeight = FontWeight.Bold) }
                Row(horizontalArrangement = Arrangement.spacedBy(u * 0.12f), verticalAlignment = Alignment.CenterVertically) {
                    for (n in listOf(6, 7)) Box(
                        Modifier.size(u * 0.95f).clip(RoundedCornerShape(u * 0.2f)).background(scheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = scheme.onSurface, fontSize = (u.value * 0.52f).sp, fontWeight = FontWeight.SemiBold) }
                    Text("×", color = BoardHues.ink(hueA, dark), fontSize = (u.value * 0.6f).sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(u * 0.95f).clip(RoundedCornerShape(u * 0.2f)).background(made),
                    contentAlignment = Alignment.Center,
                ) { Text("42", color = BoardHues.onFill(made), fontSize = (u.value * 0.4f).sp, fontWeight = FontWeight.SemiBold) }
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
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse = rememberHintPulse(highlight.strong.isNotEmpty())

        // The move being built is transient: it starts over whenever the board changes (a finished operation, undo, a hint).
        var entry by remember(s.slots) { mutableStateOf(TargetEntry()) }
        var note by remember(s.slots) { mutableStateOf<String?>(null) }
        // An operation's answer settles into its tile. No units: there is one target, and reaching it is the solve.
        val feel = rememberMoveFeel(s.slots)
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
                        .ring(look, glow, pulse, corner)
                        .dimmed(look)
                        .clip(shape)
                        .background(scheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("TARGET", color = scheme.onSurfaceVariant, fontSize = (u.value * 0.17f).sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${s.target}",
                        color = if (s.solved) scheme.primary else BoardHues.ink(hueB, dark),
                        fontSize = (u.value * 0.62f).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // The move being made, or the last one. Always one line high, so nothing moves when it changes.
                Box(Modifier.fillMaxWidth().height(u * 0.42f), contentAlignment = Alignment.Center) {
                    Text(
                        captionOf(s, entry, note),
                        color = if (note != null) scheme.error else scheme.onSurfaceVariant,
                        fontSize = (u.value * 0.2f).sp,
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
                            Box(
                                Modifier
                                    .size(u * 1.1f, u * 0.95f)
                                    .moveFeel(feel, slot, Color.Unspecified)
                                    .highlightAnchor(slot)
                                    .ring(l, glow, pulse, corner)
                                    .dimmed(l)
                                    .clip(shape)
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
                                    .clickable(enabled = interactive && value > 0 && !s.solved) { go(s.tapTile(entry, slot)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (value > 0) Text(
                                    "$value",
                                    color = if (fill == scheme.surface) scheme.onSurface else BoardHues.onFill(fill),
                                    fontSize = fontFor(value, u),
                                    fontWeight = FontWeight.SemiBold,
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
                        Box(
                            Modifier
                                .size(u * 0.82f)
                                .highlightAnchor(OP0 + op.ordinal)
                                .ring(l, glow, pulse, corner)
                                .dimmed(l)
                                .clip(shape)
                                .background(scheme.surface)
                                .background(if (chosen) BoardHues.mark(hueA).copy(alpha = if (dark) 0.55f else 0.40f) else Color.Transparent)
                                .border(if (chosen) 3.dp else 1.dp, if (chosen) BoardHues.mark(hueA) else scheme.outline, shape)
                                .clickable(enabled = interactive && !s.solved) { go(s.tapOp(entry, op)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(op.symbol, color = BoardHues.ink(hueA, dark), fontSize = (u.value * 0.5f).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
