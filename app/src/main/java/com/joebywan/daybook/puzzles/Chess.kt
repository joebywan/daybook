package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.highlightAnchor
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.reportHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * A Mate puzzle in play: a position and the player's moves so far. Only the player's moves are stored; the
 * opponent's replies are derived by [ChessRules.defence] (so that function is a saved-game contract: see
 * `ChessRulesGameTest`). The position, whose move it is, `lost` and the legal moves are all derived.
 */
@Serializable
data class ChessState(
    /** FEN (no clocks) after the opponent's last move; the side to move is the player, drawn at the bottom. */
    val start: String,
    /** That last move in UCI, drawn faintly; "" for none. */
    val last: String = "",
    val mateIn: Int,
    /** The player's moves only, UCI, in order. */
    val played: List<String> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {

    /** A rule check: after the moves played the side to move is checkmated, within [mateIn] moves. */
    override val solved: Boolean
        get() = played.size <= mateIn && replay().let { it.over && ChessRules.isCheckmate(it.position) }

    internal fun replay(): ChessReplay = ChessReplay.of(this)

    /** What the player faces now (after the last reply). */
    internal fun position(): ChessPosition = replay().position

    /** Whether the player moves white. */
    val playerIsWhite: Boolean get() = start.split(' ').getOrNull(1) != "b"

    /** Moves left for the player. */
    val remaining: Int get() = mateIn - played.size

    /** The player's legal moves now: none once the game is over or the moves are used up. */
    internal fun legalMoves(): List<Int> {
        val r = replay()
        return if (r.over || played.size >= mateIn) emptyList() else ChessRules.legalMoves(r.position)
    }

    /**
     * No forced mate remains: the moves are used up unsolved, the game ended in stalemate, or the position
     * (attacker to move) has no forced mate within the moves left. Only a proof of "none" counts, a search
     * that ran out of budget is not read as one.
     */
    fun lost(): Boolean {
        if (solved) return false
        val r = replay()
        if (played.size >= mateIn || r.over) return true
        return ChessRules.forcedMate(r.position, remaining) is ChessRules.Mate.None
    }

    /** The state after the player plays [m] (legal now). One emission; the reply is derived from it. */
    internal fun play(m: Int): ChessState = copy(played = played + ChessRules.uci(m), moves = played.size + 1)
}

/** The derived line of play. [position] is what the player faces; [beforeReply] is the board just after their last move. */
internal class ChessReplay(
    val position: ChessPosition,
    val beforeReply: ChessPosition,
    /** The player's last move and the reply to it, as move codes; -1 for none. */
    val playerMove: Int,
    val reply: Int,
    /** The defender had no legal move after the last played move: checkmate or stalemate. */
    val over: Boolean,
) {
    companion object {
        // Replays are costly (each reply is a search), and `solved` is read often, so the last few are kept,
        // keyed by start + moves. Copy-on-write list, looked up only: no hash order, and safe across threads.
        private var memo: List<Pair<String, ChessReplay>> = emptyList()
        private const val MEMO_SIZE = 16

        fun of(s: ChessState): ChessReplay = at(s.start, s.mateIn, s.played, s.played.size)

        private fun at(start: String, mateIn: Int, played: List<String>, n: Int): ChessReplay {
            val key = "$start|$mateIn|${played.take(n).joinToString(",")}"
            memo.firstOrNull { it.first == key }?.let { return it.second }
            val p0 = ChessPosition.fromFen(start)
            val r = if (n == 0) ChessReplay(p0, p0, -1, -1, false) else {
                val prev = at(start, mateIn, played, n - 1)
                val m = if (prev.over) null else ChessRules.parseUci(prev.position, played[n - 1])
                if (m == null) prev else {
                    val after = ChessRules.play(prev.position, m)
                    // `remaining` is the attacker's moves left after the reply: the contract with `defence`.
                    val reply = ChessRules.defence(after, mateIn - n)
                    if (reply == null) ChessReplay(after, after, m, -1, true)
                    else ChessReplay(ChessRules.play(after, reply), after, m, reply, false)
                }
            }
            memo = (listOf(key to r) + memo).take(MEMO_SIZE)
            return r
        }
    }
}

/** Mate: the forced checkmate in 2, 3 or 4, from a bundled list of verified positions. */
object Chess : PuzzleType {

    override val id = "chess"
    override val displayName = "Mate"
    override val tagline = "Find the forced checkmate"
    override val accent = 0xFFB06A3B
    override val rules = listOf(
        "You play the side shown at the bottom. Checkmate the opposing king in the number of moves shown.",
        "Tap a piece, then tap where it goes. Tap another of your pieces to change your mind.",
        "The opponent answers each move. A move that lets them escape mate is not refused: you will be told, and can Undo.",
        "Standard chess rules, including castling, en passant and promotion (you choose the piece).",
        "Any mate within the moves counts, not only the one the hint knows.",
    )

    // ---- picking a position ------------------------------------------------------------------

    /** The tier's mate length. Provisional until the prover's cost is measured (docs/CHESS_SPEC.md "Budgets"). */
    internal fun mateInFor(d: Difficulty): Int = when (d) {
        Difficulty.STANDARD -> 2
        Difficulty.HARD -> 3
        Difficulty.EXPERT -> 4
    }

    internal fun positionsFor(d: Difficulty): List<String> = when (d) {
        Difficulty.STANDARD -> ChessPositions.STANDARD
        Difficulty.HARD -> ChessPositions.HARD
        Difficulty.EXPERT -> ChessPositions.EXPERT
    }

    /** One entry of the list by index: a pure function of the seed, so the whole archive is free and offline. */
    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val list = positionsFor(difficulty)
        return fromEntry(list[Rng(seed).nextInt(list.size)], mateInFor(difficulty))
    }

    /** `"<fen without clocks> <last move uci or ->"` to a fresh state. */
    internal fun fromEntry(entry: String, mateIn: Int): ChessState {
        val cut = entry.lastIndexOf(' ')
        val last = entry.substring(cut + 1)
        return ChessState(start = entry.substring(0, cut), last = if (last == "-") "" else last, mateIn = mateIn)
    }

    // ---- teaching and walkthrough: the files that own them ---------------------------------------

    override fun teach(state: PuzzleState): Deduction? = ChessTeacher.teach(state as ChessState)

    override val tutorial: List<TutorialFrame> get() = ChessTutorial.frames

    // ---- drawing -----------------------------------------------------------------------------

    private val WHITE_FILL = Color(0xFFF6F2E8)
    private val WHITE_LINE = Color(0xFF1C1B1F)
    private val BLACK_FILL = Color(0xFF2B2A30)
    private val BLACK_LINE = Color(0xFFF6F2E8)

    /** The two reserved caption lines under the board, so feedback never moves it. */
    private val CAPTION_HEIGHT = 44.dp
    private val CAPTION_GAP = 8.dp
    internal const val CAPTION_LINES = 2
    private const val SETTLE_MILLIS = 450L
    private const val LOST_MILLIS = 1000L

    internal const val LOST_CAPTION = "No forced mate from here: Undo or Restart"

    /** Highlight index of a promotion-picker button for piece [kind] (2..5 = N B R Q). */
    internal fun picker(kind: Int) = 100 + kind

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val (light, dark) = squareColours(scheme)
        Canvas(modifier) {
            drawChessMotif(size, light, dark, WHITE_FILL, WHITE_LINE, BLACK_FILL, BLACK_LINE)
        }
    }

    /** The light and the dark square, whichever way the theme tints them, so a1 is dark in both themes. */
    private fun squareColours(scheme: androidx.compose.material3.ColorScheme): Pair<Color, Color> {
        val a = scheme.surfaceVariant
        val b = lerp(a, scheme.onSurfaceVariant, 0.30f)
        return if (b.luminance() > a.luminance()) b to a else a to b
    }

    /** Display column and row (row 0 on top) of a square, the player's side at the bottom. */
    private fun col(sq: Int, whiteBottom: Boolean) = if (whiteBottom) sq % 8 else 7 - sq % 8
    private fun row(sq: Int, whiteBottom: Boolean) = if (whiteBottom) 7 - sq / 8 else sq / 8
    private fun squareAt(col: Int, row: Int, whiteBottom: Boolean) =
        if (whiteBottom) (7 - row) * 8 + col else row * 8 + (7 - col)

    private fun src(m: Int) = m and 63
    private fun dst(m: Int) = (m shr 6) and 63
    private fun promo(m: Int) = m shr 12

    private const val PIECE_LETTERS = "PNBRQK"

    private fun DrawScope.ring(topLeft: Offset, cell: Float, color: Color, width: Float) {
        drawRect(color, topLeft + Offset(width / 2, width / 2), Size(cell - width, cell - width), style = Stroke(width))
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as ChessState
        val scheme = MaterialTheme.colorScheme
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val pulse: State<Float> = if (highlight.strong.isEmpty()) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "hint").animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "hint-pulse",
            )
        }
        val whiteBottom = s.playerIsWhite
        val replay = remember(s) { s.replay() }
        val moves = remember(s) { s.legalMoves() }

        // Transient UI, never PuzzleState (PlayScreen pushes an undo entry for every state): the selection and the
        // promotion picker go with the moves played; the settle is a job in a scope no tap cancels.
        var selected by remember(s.played) { mutableIntStateOf(-1) }
        var pending by remember(s.played) { mutableStateOf<Pair<Int, Int>?>(null) }
        var settling by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // "No forced mate" is derived by a search, so it is computed off the main thread and shown a second late.
        var lostShown by remember { mutableStateOf(false) }
        LaunchedEffect(s) {
            lostShown = false
            val lost = withContext(Dispatchers.Default) { s.lost() }
            if (lost) {
                delay(LOST_MILLIS)
                lostShown = true
            }
        }

        val shown = if (settling) replay.beforeReply else replay.position
        val kingSq = if (ChessRules.inCheck(shown)) (0..63).first { shown.pieceAt(it) == if (shown.whiteToMove) 6 else -6 } else -1
        val destinations = if (selected >= 0) moves.filter { src(it) == selected }.map { dst(it) } else emptyList()
        val lastSquares: Set<Int> = buildSet {
            if (s.played.isEmpty() && s.last.length >= 4) {
                fun sq(i: Int) = (s.last[i + 1] - '1') * 8 + (s.last[i] - 'a')
                add(sq(0)); add(sq(2))
            }
            if (replay.playerMove >= 0) { add(src(replay.playerMove)); add(dst(replay.playerMove)) }
            if (!settling && replay.reply >= 0) { add(src(replay.reply)); add(dst(replay.reply)) }
        }

        fun commit(m: Int) {
            selected = -1
            pending = null
            onState(s.play(m))
            scope.launch {
                settling = true
                delay(SETTLE_MILLIS)
                settling = false
            }
        }

        fun tap(sq: Int) {
            if (settling || pending != null || !interactive) return
            val at = selected
            if (at >= 0) {
                val candidates = moves.filter { src(it) == at && dst(it) == sq }
                if (candidates.size > 1) { pending = at to sq; return }
                if (candidates.size == 1) { commit(candidates[0]); return }
            }
            selected = if (sq != at && moves.any { src(it) == sq }) sq else -1
        }

        val (light, dark) = squareColours(scheme)
        val note = Color(accent)

        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Sized from both axes (CLAUDE.md): the board leaves room for the caption slot under it.
            val side = if (constraints.hasBoundedHeight) {
                minOf(maxWidth, (maxHeight - CAPTION_GAP - CAPTION_HEIGHT).coerceAtLeast(0.dp))
            } else {
                maxWidth
            }
            val cell = side / 8
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(side)
                        // The index of a highlight is the square (a1 = 0), whichever way the board faces.
                        .reportHighlight { size, h ->
                            val w = size.width / 8f
                            var out: Rect? = null
                            for (i in h.strong + h.soft) {
                                if (i !in 0..63) continue
                                val r = Rect(
                                    Offset(col(i, whiteBottom) * w, row(i, whiteBottom) * w),
                                    Size(w, w),
                                )
                                out = out?.let { o -> Rect(minOf(o.left, r.left), minOf(o.top, r.top), maxOf(o.right, r.right), maxOf(o.bottom, r.bottom)) } ?: r
                            }
                            out
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = size.width / 8f
                        fun at(sq: Int) = Offset(col(sq, whiteBottom) * c, row(sq, whiteBottom) * c)
                        val dims = !highlight.isEmpty
                        for (sq in 0..63) {
                            drawRect(if ((sq % 8 + sq / 8) % 2 == 0) dark else light, at(sq), Size(c, c))
                        }
                        for (sq in lastSquares) drawRect(note.copy(alpha = 0.30f), at(sq), Size(c, c))
                        if (selected >= 0) drawRect(note.copy(alpha = 0.55f), at(selected), Size(c, c))
                        if (kingSq >= 0) {
                            drawCircle(scheme.error.copy(alpha = 0.55f), c * 0.46f, at(kingSq) + Offset(c / 2, c / 2))
                        }
                        for (sq in 0..63) {
                            val v = shown.pieceAt(sq)
                            if (v == 0) continue
                            val white = v > 0
                            val faded = dims && sq !in highlight.strong && sq !in highlight.soft
                            val k = if (faded) 0.35f else 1f
                            drawChessPiece(
                                PIECE_LETTERS[abs(v) - 1], at(sq) + Offset(c * 0.04f, c * 0.04f), c * 0.92f,
                                (if (white) WHITE_FILL else BLACK_FILL).copy(alpha = k),
                                (if (white) WHITE_LINE else BLACK_LINE).copy(alpha = k),
                            )
                        }
                        for (sq in destinations) {
                            if (shown.pieceAt(sq) == 0) {
                                drawCircle(note.copy(alpha = 0.7f), c * 0.16f, at(sq) + Offset(c / 2, c / 2))
                            } else {
                                ring(at(sq), c, note.copy(alpha = 0.85f), c * 0.09f)
                            }
                        }
                        for (sq in highlight.soft) if (sq in 0..63) ring(at(sq), c, glow.copy(alpha = 0.6f), c * 0.05f)
                        for (sq in highlight.strong) if (sq in 0..63) ring(at(sq), c, glow.copy(alpha = pulse.value), c * 0.09f)
                    }
                    // Real click targets over the drawing: the first board whose squares have click actions.
                    Column(Modifier.fillMaxSize()) {
                        for (r in 0 until 8) Row(Modifier.fillMaxWidth()) {
                            for (cc in 0 until 8) {
                                val sq = squareAt(cc, r, whiteBottom)
                                Box(
                                    Modifier.size(cell).clickable(enabled = interactive) { tap(sq) },
                                ) {
                                    val labelColor = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    if (r == 7) {
                                        Text(
                                            "${'a' + sq % 8}",
                                            Modifier.align(Alignment.BottomEnd).padding(end = cell * 0.06f),
                                            fontSize = (cell.value * 0.2f).sp, color = labelColor, lineHeight = (cell.value * 0.2f).sp,
                                        )
                                    }
                                    if (cc == 0) {
                                        Text(
                                            "${'1' + sq / 8}",
                                            Modifier.align(Alignment.TopStart).padding(start = cell * 0.06f),
                                            fontSize = (cell.value * 0.2f).sp, color = labelColor, lineHeight = (cell.value * 0.2f).sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    pending?.let { (a, b) ->
                        // Promotion: four pieces over the board, so the box never changes size.
                        Box(
                            Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.55f)).clickable { pending = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                Modifier.keepClear(),
                                horizontalArrangement = Arrangement.spacedBy(cell * 0.12f),
                            ) {
                                for (kind in intArrayOf(5, 4, 3, 2)) {
                                    val look = picker(kind)
                                    Box(
                                        Modifier
                                            .size(cell * 1.5f)
                                            .highlightAnchor(look)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(scheme.surface)
                                            .clickable {
                                                moves.firstOrNull { src(it) == a && dst(it) == b && promo(it) == kind }?.let(::commit)
                                            },
                                    ) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            val white = whiteBottom
                                            drawChessPiece(
                                                PIECE_LETTERS[kind - 1], Offset.Zero, size.width,
                                                if (white) WHITE_FILL else BLACK_FILL, if (white) WHITE_LINE else BLACK_LINE,
                                            )
                                            if (look in highlight.strong) ring(Offset.Zero, size.width, glow.copy(alpha = pulse.value), size.width * 0.06f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(CAPTION_GAP))
                Box(Modifier.width(side).height(CAPTION_HEIGHT)) {
                    val lost = lostShown && !s.solved
                    val text = when {
                        lost -> LOST_CAPTION
                        s.solved -> "Checkmate."
                        else -> "${if (whiteBottom) "White" else "Black"} to move: mate in ${s.remaining}."
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lost) scheme.error else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        minLines = CAPTION_LINES,
                        maxLines = CAPTION_LINES,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
