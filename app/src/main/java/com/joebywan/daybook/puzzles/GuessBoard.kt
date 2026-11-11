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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.arcadeGlow
import com.joebywan.daybook.ui.theme.arcadeHardShadow
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.HintLook
import com.joebywan.daybook.ui.dimmed
import com.joebywan.daybook.ui.look
import com.joebywan.daybook.ui.ring
import kotlinx.coroutines.delay
import com.joebywan.daybook.ui.moveFeel
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.boardSp

/**
 * The board of a guess game ([GuessRows]): the grid of marked rows over an on-screen keyboard, shared by Lexicon and
 * Equation. Each puzzle hands it its alphabet, its keyboard rows and how a symbol is shown; the indices a hint names
 * are [GuessTeacher]'s (tiles, keys, Enter, Del).
 */
internal object GuessBoard {

    // ---- colours ---------------------------------------------------------------------------------

    /**
     * The marks, on [ArcadeBoard]'s squares. Present is the board's own amber letter tile. Colour is never alone: right has an
     * inset ring, present a corner dot ([markCue]), and absent is flat and lighter in weight. Right runs towards teal, so it
     * keeps some blue a red-green eye can part from the amber. Measured in ArcadeBoardTest.
     */
    internal class Marks(val correct: ArcadeFill, val present: ArcadeFill, val absent: Color, val absentInk: Color)

    internal val LightMarks = Marks(
        ArcadeFill(Color(0xFF08854A), Color(0xFF00806E), Color.White),
        ArcadeBoard.Light.letter,
        Color(0xFF736D9A),
        Color.White,
    )
    internal val DarkMarks = Marks(
        ArcadeFill(Color(0xFF2BF08F), Color(0xFF00C2A8), Color(0xFF04231C), Color(0xFF00E5A0)),
        ArcadeBoard.Dark.letter,
        Color(0xFF6E68A0),
        Color.White,
    )

    @Composable
    @ReadOnlyComposable
    private fun isDark() = BoardHues.isDark(MaterialTheme.colorScheme)

    /**
     * The Classic look's marks (issue #418), as Lexicon drew them before Arcade: flat green and gold with the letter's ink
     * picked per fill (white was 3.2:1 on green and 2.3:1 on gold), absent the outline at 45%.
     */
    @Composable
    @ReadOnlyComposable
    private fun classicMarks(): Marks {
        val absent = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        return Marks(
            ArcadeFill(CLASSIC_CORRECT, CLASSIC_CORRECT, BoardHues.onFill(CLASSIC_CORRECT)),
            ArcadeFill(CLASSIC_PRESENT, CLASSIC_PRESENT, BoardHues.onFill(CLASSIC_PRESENT)),
            absent,
            MaterialTheme.colorScheme.onSurface,
        )
    }

    internal val CLASSIC_CORRECT = Color(0xFF4E9F6C)
    internal val CLASSIC_PRESENT = Color(0xFFD1A32F)

    @Composable
    @ReadOnlyComposable
    fun marks() = if (classicLook()) classicMarks() else if (isDark()) DarkMarks else LightMarks

    /** Right's ring or present's dot, in the fill's ink; [unit] is the ring's stroke. Drawn, so it takes no room. */
    private fun Modifier.markCue(mark: Int?, ink: Color, unit: Dp) = when (mark) {
        GuessMarks.CORRECT -> drawBehind {
            val w = unit.toPx()
            drawRoundRect(ink, Offset(w * 1.5f, w * 1.5f), Size(size.width - w * 3, size.height - w * 3), CornerRadius(w * 2), style = Stroke(w))
        }
        GuessMarks.PRESENT -> drawBehind {
            val r = unit.toPx() * 1.3f
            drawCircle(ink, r, Offset(size.width - r * 2, r * 2))
        }
        else -> this
    }

    /**
     * Scored rows of squares for a home tile: the colours are most of what reads at 80dp. [symbols], one string a row,
     * are drawn in the squares when given (Equation's signs tell it from Lexicon's tile).
     */
    @Composable
    fun PreviewRows(rows: List<List<Int>>, modifier: Modifier, symbols: List<String>? = null) {
        val m = marks()
        val width = rows.maxOf { it.size }
        // Sized from both axes, like every board: the tile is square-ish and its box is not.
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val gap = 3.dp
            val cell = minOf((maxWidth - gap * (width - 1)) / width, (maxHeight - gap * (rows.size - 1)) / rows.size).coerceAtLeast(0.dp)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEachIndexed { i, mark ->
                            Box(
                                Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .then(
                                        when (mark) {
                                            GuessMarks.CORRECT -> Modifier.background(m.correct.brush)
                                            GuessMarks.PRESENT -> Modifier.background(m.present.brush)
                                            else -> Modifier.background(m.absent)
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                val symbol = symbols?.getOrNull(r)?.getOrNull(i)
                                if (symbol != null) {
                                    Text(
                                        symbol.toString(),
                                        fontSize = (cell * 0.62f).boardSp(), lineHeight = (cell * 0.62f).boardSp() * 1.2f,
                                        fontWeight = FontWeight.Bold,
                                        color = when (mark) {
                                            GuessMarks.CORRECT -> m.correct.ink
                                            GuessMarks.PRESENT -> m.present.ink
                                            else -> m.absentInk
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** The best clue the board holds for each symbol of [alphabet]: a [GuessMarks] mark, or -1 for one not yet tried. */
    private fun keyMarks(s: GuessRows<*>, alphabet: String): IntArray {
        val best = IntArray(alphabet.length) { -1 }
        s.guesses.forEach { guess ->
            val marks = s.marks(guess)
            guess.forEachIndexed { i, c ->
                val k = alphabet.indexOf(c)
                if (marks[i] > best[k]) best[k] = marks[i]
            }
        }
        return best
    }

    /**
     * The board. [keyRows] are the keyboard's rows of symbols (each from [alphabet]); the last row has Del and Enter at
     * its ends, and a shorter row is centred under the widest. [label] is how a symbol is shown on a tile and a key.
     * [keyAction] maps a physical key (Shift reaches it only with [shift] set). [problem] says why Enter refuses the row (shown in the header line), [lost] what the header says once the guesses
     * have run out.
     */
    @Composable
    fun <S> Board(
        s: S,
        onState: (PuzzleState) -> Unit,
        interactive: Boolean,
        accent: Long,
        alphabet: String,
        keyRows: List<String>,
        label: (Char) -> String,
        keyAction: (key: Key, shift: Boolean) -> GuessKeyAction?,
        shift: Boolean,
        problem: (S) -> String?,
        lost: (S) -> String,
    ) where S : PuzzleState, S : GuessRows<S> {
        val scheme = MaterialTheme.colorScheme
        val (highlight, glow, pulse) = rememberHintPaint()
        val tilesNamed = highlight.strong.any { it < GuessTeacher.KEY } || highlight.soft.any { it < GuessTeacher.KEY }
        val a = ArcadeBoard.current
        val m = marks()
        val playable = interactive && s.open

        // Why Enter was refused. Transient UI state, so it lives here and not in the state:
        // PlayScreen would otherwise make every refusal an undo step. It is shown in the header line,
        // whose room is always reserved, so a refusal never moves the board.
        var notice by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(1600)
                notice = null
            }
        }
        LaunchedEffect(s.current, s.guesses.size) { notice = null }

        fun press(action: GuessKeyAction) {
            if (!playable) return
            when (action) {
                is GuessKeyAction.Letter -> s.withLetter(action.letter).takeIf { it != s }?.let(onState)
                GuessKeyAction.Backspace -> s.withoutLetter().takeIf { it != s }?.let(onState)
                GuessKeyAction.Enter -> {
                    val why = problem(s)
                    if (why != null) notice = why else onState(s.submit())
                }
            }
        }

        // Grows past phone size on a tablet or a large window, from both axes (CLAUDE.md): 1 wherever the
        // phone keyboard's 520dp cap does not bind on width or the box is under 640dp tall, so a phone in
        // either orientation keeps its exact look; at most 1.5 (keys about 63dp tall, tiles up to 96dp).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = minOf(maxWidth / MAX_KEYBOARD, maxHeight / SCALE_HEIGHT).coerceIn(1f, MAX_SCALE)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .boardKeys(playable, shift) { key, shifted, repeat ->
                        // A held Backspace would eat the whole row, a held symbol fill it.
                        val action = keyAction(key, shifted) ?: return@boardKeys false
                        if (!repeat) press(action)
                        true
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One line, always laid out: the guesses left, or why Enter refused, or the answer.
                val line = when {
                    notice != null -> notice!!
                    s.failed -> lost(s)
                    else -> "${s.maxGuesses - s.guesses.size} guesses left"
                }
                val classic = classicLook()
                Text(
                    line,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (notice != null || s.failed) (if (classic) scheme.error else a.error) else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )

                // Sized from both axes (CLAUDE.md): the grid is the width, or the height left once the
                // keyboard has its room, whichever binds. It never changes size, so a hint opening or a
                // solve cannot move it.
                // A guessed row settles as its marks land, and a row submitted is a unit: it glows along and plays a note
                // as high as its marks landed (the marks are already showing, so the note tells nothing new).
                val rows = remember(s.maxGuesses, s.length) { List(s.maxGuesses) { r -> IntArray(s.length) { r * s.length + it } } }
                val feel = rememberMoveFeel(
                    List(s.maxGuesses * s.length) { s.guesses.getOrNull(it / s.length)?.get(it % s.length) },
                    rows,
                    { Tones.step(s.guesses.lastOrNull()?.let { GuessMarks.step(s.marks(it).sum(), s.length) } ?: 0) },
                ) { it < s.guesses.size }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cell = minOf(maxWidth / s.length, maxHeight / s.maxGuesses, MAX_CELL * scale).coerceAtLeast(0.dp)
                    Column(Modifier.size(cell * s.length, cell * s.maxGuesses).highlightGrid(s.length, s.maxGuesses).clip(RoundedCornerShape(8.dp)).background(if (classic) Color.Transparent else a.frame)) {
                        repeat(s.maxGuesses) { row ->
                            Row {
                                repeat(s.length) { slot ->
                                    val guess = s.guesses.getOrNull(row)
                                    val typing = row == s.guesses.size && s.open
                                    val letter = guess?.get(slot) ?: if (typing) s.current.getOrNull(slot) else null
                                    val mark = guess?.let { s.marks(it)[slot] }
                                    val look = highlight.look(GuessTeacher.tile(s.length, row, slot), dims = tilesNamed)
                                    val place = when {
                                        !typing -> TilePlace.PLAIN
                                        letter != null -> if (notice != null) TilePlace.REFUSED else TilePlace.TYPED
                                        slot == s.current.length -> TilePlace.NEXT
                                        else -> TilePlace.ROW
                                    }
                                    Tile(letter?.let(label), mark, place, cell, look, glow, pulse, Modifier.moveFeel(feel, row * s.length + slot, vivid(accent)))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val known = keyMarks(s, alphabet)
                val widest = keyRows.maxOf { it.length }
                Column(
                    Modifier.widthIn(max = MAX_KEYBOARD * scale).fillMaxWidth().keepClear().padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP * scale),
                ) {
                    keyRows.forEachIndexed { r, symbols ->
                        val last = r == keyRows.lastIndex
                        val pad = if (last) 0f else (widest - symbols.length) / 2f
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP * scale)) {
                            // A shorter row is centred under the widest.
                            if (pad > 0f) Spacer(Modifier.weight(pad))
                            if (last) {
                                KeyButton("Del", Modifier.weight(1.5f), playable, GuessTeacher.DELETE, highlight, glow, pulse,
                                    if (classic) SolidColor(scheme.surfaceVariant) else a.control.brush,
                                    if (classic) scheme.onSurfaceVariant else a.control.ink, a.control.glow, null, small = true, scale) {
                                    press(GuessKeyAction.Backspace)
                                }
                            }
                            symbols.forEach { c ->
                                val mark = known[alphabet.indexOf(c)]
                                // Classic, as before Arcade: the variant, faded once absent, the mark's flat colour once found.
                                val fill = if (classic) when (mark) {
                                    -1 -> SolidColor(scheme.surfaceVariant)
                                    GuessMarks.ABSENT -> SolidColor(scheme.surfaceVariant.copy(alpha = 0.3f))
                                    GuessMarks.CORRECT -> SolidColor(CLASSIC_CORRECT)
                                    else -> SolidColor(CLASSIC_PRESENT)
                                } else when (mark) {
                                    -1 -> SolidColor(a.given)
                                    GuessMarks.ABSENT -> SolidColor(m.absent)
                                    GuessMarks.CORRECT -> m.correct.brush
                                    else -> m.present.brush
                                }
                                val text = if (classic) when (mark) {
                                    -1 -> scheme.onSurfaceVariant
                                    GuessMarks.ABSENT -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> m.correct.ink.takeIf { mark == GuessMarks.CORRECT } ?: m.present.ink
                                } else when (mark) {
                                    -1 -> a.givenInk
                                    GuessMarks.ABSENT -> m.absentInk
                                    GuessMarks.CORRECT -> m.correct.ink
                                    else -> m.present.ink
                                }
                                val lift = when (mark) {
                                    GuessMarks.CORRECT -> m.correct.glow
                                    GuessMarks.PRESENT -> m.present.glow
                                    else -> null
                                }
                                KeyButton(label(c), Modifier.weight(1f), playable, GuessTeacher.KEY + alphabet.indexOf(c), highlight, glow, pulse,
                                    fill, text, lift.takeIf { !classic }, mark.takeIf { !classic }, small = false, scale) {
                                    press(GuessKeyAction.Letter(c))
                                }
                            }
                            if (pad > 0f) Spacer(Modifier.weight(pad))
                            if (last) {
                                KeyButton("Enter", Modifier.weight(1.5f), playable, GuessTeacher.ENTER, highlight, glow, pulse,
                                    if (classic) SolidColor(scheme.surfaceVariant) else a.control.brush,
                                    if (classic) scheme.onSurfaceVariant else a.control.ink, a.control.glow, null, small = true, scale) {
                                    press(GuessKeyAction.Enter)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val MAX_CELL = 64.dp
    private val SCALE_HEIGHT = 640.dp
    private const val MAX_SCALE = 1.5f
    private val MAX_KEYBOARD = 520.dp
    private val KEY_GAP = 5.dp
    private val KEY_HEIGHT = 42.dp

    /** Where an unmarked square stands: the row being typed is one strip, its next square amber, a refused row red. */
    private enum class TilePlace { PLAIN, ROW, NEXT, TYPED, REFUSED }

    /** One square of the grid: a typed symbol, a marked one, or an empty square. */
    @Composable
    private fun Tile(
        letter: String?,
        mark: Int?,
        place: TilePlace,
        cell: Dp,
        look: HintLook,
        glow: Color,
        pulse: State<Float>,
        modifier: Modifier = Modifier,
    ) {
        val a = ArcadeBoard.current
        val m = marks()
        val shape = RoundedCornerShape(6.dp)
        val corner = 6.dp
        if (classicLook()) {
            // Classic, as before Arcade: a marked square is its flat colour; an unmarked one an outline, heavier once typed in.
            val scheme = MaterialTheme.colorScheme
            val typed = place == TilePlace.TYPED || place == TilePlace.REFUSED
            Box(
                Modifier
                    .size(cell)
                    .then(modifier)
                    .padding(2.dp)
                    .ring(look, glow, pulse, corner, outside = true)
                    .dimmed(look)
                    .clip(shape)
                    .then(
                        when (mark) {
                            null -> Modifier.border(if (typed) 2.dp else 1.dp, if (typed) scheme.onSurfaceVariant else scheme.outline, shape)
                            GuessMarks.CORRECT -> Modifier.background(m.correct.from)
                            GuessMarks.PRESENT -> Modifier.background(m.present.from)
                            else -> Modifier.background(m.absent)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (letter != null) {
                    Text(
                        letter,
                        fontSize = (cell * 0.46f).boardSp(), lineHeight = (cell * 0.46f).boardSp() * 1.2f,
                        fontWeight = FontWeight.Bold,
                        color = when (mark) {
                            GuessMarks.CORRECT -> m.correct.ink
                            GuessMarks.PRESENT -> m.present.ink
                            else -> scheme.onSurface
                        },
                    )
                }
            }
            return
        }
        val fill: ArcadeFill? = when {
            mark == GuessMarks.CORRECT -> m.correct
            mark == GuessMarks.PRESENT -> m.present
            mark != null -> null
            place == TilePlace.TYPED -> a.control
            place == TilePlace.NEXT -> a.selected
            else -> null
        }
        val ink = fill?.ink ?: when {
            mark != null -> m.absentInk
            place == TilePlace.REFUSED -> a.errorInk
            else -> a.squareInk
        }
        Box(
            Modifier
                .size(cell)
                .then(modifier)
                .padding(2.dp)
                .ring(look, glow, pulse, corner, outside = true)
                // A dimmed tile fades to a square, not to the dark frame behind it.
                .background(a.square, shape)
                .dimmed(look)
                .then(fill?.glow?.let { Modifier.arcadeGlow(it, corner, reach = 2.dp) } ?: Modifier)
                .clip(shape)
                .then(
                    when {
                        fill != null -> Modifier.background(fill.brush)
                        mark != null -> Modifier.background(m.absent)
                        place == TilePlace.REFUSED -> Modifier.background(a.error)
                        place == TilePlace.ROW -> Modifier.background(a.given)
                        else -> Modifier.background(a.square)
                    }
                )
                .then(if (place == TilePlace.NEXT) Modifier.border(2.dp, a.selectedEdge, shape) else Modifier)
                // A refused row has right's kind of ring in red's own ink, so it is more than a change of hue.
                .markCue(if (place == TilePlace.REFUSED) GuessMarks.CORRECT else mark, ink, 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (letter != null) {
                Text(
                    letter,
                    fontSize = (cell * 0.46f).boardSp(), lineHeight = (cell * 0.46f).boardSp() * 1.2f,
                    fontWeight = if (mark == GuessMarks.ABSENT) FontWeight.Medium else FontWeight.Bold,
                    color = ink,
                )
            }
        }
    }

    /** One key of the on-screen keyboard. [index] is what a hint names it by; [mark] its best clue, or null for Del and Enter. */
    @Composable
    private fun KeyButton(
        label: String,
        modifier: Modifier,
        enabled: Boolean,
        index: Int,
        highlight: BoardHighlight,
        glow: Color,
        pulse: State<Float>,
        fill: Brush,
        text: Color,
        lift: Color?,
        mark: Int?,
        small: Boolean,
        scale: Float,
        onClick: () -> Unit,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier
                .height(KEY_HEIGHT * scale)
                .highlightAnchor(index)
                .ring(highlight.look(index, dims = false), glow, pulse, 6.dp, outside = true)
                // Inside the keys' gap: a glow where dark gives one, else light's sticker edge.
                .then(if (lift != null) Modifier.arcadeGlow(lift, 6.dp, reach = 2.dp) else if (!isDark()) Modifier.arcadeHardShadow(6.dp, drop = 2.dp) else Modifier)
                .clip(shape)
                .background(fill)
                .markCue(mark, text, 1.25.dp)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = (if (small) 12.dp else 16.dp).times(scale).boardSp(),
                lineHeight = (if (small) 14.dp else 19.dp).times(scale).boardSp(),
                fontWeight = if (mark == GuessMarks.ABSENT) FontWeight.Normal else FontWeight.SemiBold,
                textDecoration = if (mark == GuessMarks.ABSENT) TextDecoration.LineThrough else null,
                color = text,
                maxLines = 1,
            )
        }
    }
}
