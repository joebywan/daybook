# Chess ("Mate") — spec

A daily **mate-in-N** puzzle, not a chess game. No opponent engine to play against, no clock, no ratings. Read
`docs/PUZZLE_STANDARDS.md` first; this file only records what is specific to this puzzle and where it departs
from the usual recipe. Where the two disagree on a point not listed here, the standards win.

## Decisions (made; do not reopen without the owner)

| | |
|---|---|
| `id` | `"chess"` (hashed into every seed: never rename). Display name **Mate**, tagline along the lines of "White to play and mate". |
| Source of positions | **Bundled list, picked by index** (the Lexicon pattern), from the Lichess puzzle database (CC0, so no notice must travel with it; credit it in the README anyway). No on-device generation: random positions do not look like chess. |
| Tiers | Standard **mate in 2**, Hard **mate in 3**, Expert **mate in 4**. Provisional: if the prover's Expert search is too slow for a phone (see Budgets), Expert becomes a harder mate in 3 (quiet key move), decided by measurement and recorded in `docs/PUZZLE_STANDARDS.md` section 3. |
| Uniqueness | The *shortest* forced mate is exactly N, and exactly **one** first move forces it (the key). Later moves may have several mates; `solved` is a rule check. |
| Side | The player always moves the side to play in the stored position, drawn at the bottom. |
| Input | Tap a piece, tap a destination. Promotion opens a four-piece picker inside the board's box. Selection and the picker are `remember` state, never `PuzzleState`. |
| No hard refusals | Any legal move is allowed. A move that gives up the forced mate is a *mistake* (derived), not refused (the owner's "bad judgements allowed", as Sudoku). |

## Rules and state

Standard chess rules, all of them: castling, en passant, promotion (all four pieces), check, mate, stalemate.
Fifty-move and repetition are irrelevant at this depth and are not implemented. FEN is stored without the clocks
(`placement side castling ep`).

```kotlin
@Serializable data class ChessState(
    val start: String,            // FEN after the opponent's last move, side to move = the player
    val last: String = "",        // that last move in UCI ("e2e4"), drawn faintly; "" for none
    val mateIn: Int,              // N
    val played: List<String> = emptyList(),   // the player's moves only, UCI, in order
    override val moves: Int = 0,  // == played.size
) : PuzzleState
```

- **The opponent's replies are derived, not stored**: replay `played` from `start`, answering each with
  `ChessRules.defence`. Saved games therefore depend on that policy: it is pinned by a test, and changing it
  is a save-compatibility change (CLAUDE.md "Saved games are the real danger").
- `defence(pos, remaining)`: among the defender's legal moves, prefer one after which the attacker has **no forced
  mate within `remaining` moves**; if every reply loses, the one that loses *slowest*; ties broken by move-generation
  order (never by a hash container). So a player can only ever win by a genuine forced mate.
- `solved` = after replaying `played`, the side to move is checkmated and `played.size <= mateIn`. A rule check,
  not a comparison with a stored line.
- Derived and never stored: the current position, whose move it is, the flight squares, `lost` (see below).
- **`lost`** (derived): `played.size == mateIn` and not solved, or no forced mate remains within the moves left. The
  board shows it through a reserved caption line ("No forced mate from here: Undo or Restart"), debounced ~1 s, in
  the same slot as other feedback, so nothing moves. `PlayScreen` never reads `failed`; do not override it.
- The reply plays after a short settle delay in the board (a `rememberCoroutineScope` job, not a `LaunchedEffect` that
  every tap cancels), but the *state* is one emission per player move: the reply is derived from it, so Undo takes
  back the move and its reply together.

## Engine API (`puzzles/ChessRules.kt`, `internal`, no `android.*`/`java.*`)

Squares are `0..63`, `a1 = 0`, `rank * 8 + file`. A move is an `Int` (`from | to << 6 | promo << 12`; promo 0 for none, else the
kind 2..5 = N, B, R, Q). Pieces are signed ints, kinds 1..6 = P N B R Q K, positive white, negative black. The agent may
change the encoding but must keep this surface:

```kotlin
class ChessPosition { /* 64 squares, side, castling, ep */
    companion object { fun fromFen(fen: String): ChessPosition }
    fun toFen(): String
    val whiteToMove: Boolean
    fun pieceAt(sq: Int): Int
}
object ChessRules {
    fun legalMoves(p: ChessPosition): List<Int>        // deterministic order: from ascending, to ascending, promo Q R B N
    fun play(p: ChessPosition, m: Int): ChessPosition  // m must be legal
    fun inCheck(p: ChessPosition): Boolean
    fun isCheckmate(p: ChessPosition): Boolean
    fun isStalemate(p: ChessPosition): Boolean
    fun uci(m: Int): String;  fun parseUci(p: ChessPosition, s: String): Int?   // null if not legal here
    fun san(p: ChessPosition, m: Int): String          // for hint text ("Qh7#"), standard SAN
    fun attackers(p: ChessPosition, sq: Int, byWhite: Boolean): List<Int>
    sealed interface Mate { class Forced(val keys: List<Int>, val inMoves: Int) : Mate; object None : Mate; object Truncated : Mate }
    fun forcedMate(p: ChessPosition, n: Int, nodeBudget: Int = ...): Mate   // attacker to move; only Forced is a proof
    fun defence(p: ChessPosition, remaining: Int): Int?  // see above; null if no legal move
}
```

**A truncated search is not a proof** (CLAUDE.md): `Mate.Truncated` is its own type and nothing may read it as `None`.
`forcedMate` must return the *complete* key list for the shortest N, so uniqueness is checkable.

**Budgets.** The teacher runs it off the main thread on a phone and on the web's one thread. Measure `forcedMate`
at N = 2, 3, 4 over the shipped positions (median, p95, max) and write the numbers in the PR. Target p95 under
150 ms on this machine for the tier to ship as specified; if Expert misses, apply the fallback in the table above.
Search order matters (checks, captures, then quiet; at depth 1 only check moves can mate), but any speed-up must
keep the *result* identical; the independent checker in tests proves it.

## Data (`tools/chess/build.py`, `puzzles/ChessPositions.kt`)

- Input: the Lichess puzzle CSV (`lichess_db_puzzle.csv.zst`, CC0, https://database.lichess.org/#puzzles), themes
  `mateIn2`/`mateIn3`/`mateIn4`. In the CSV, `FEN` is the position *before* the opponent's move and `Moves[0]` is
  that move: apply it, store the position after it, and its UCI as `last`.
- **The build script verifies each candidate with python-chess** (a venv under the scratchpad; do not vendor it):
  shortest forced mate is exactly N, exactly one key move, side to move is the player, not already in check-with-no-
  moves, not too many pieces to read on a phone board (all 32 is fine). Candidates failing any check are dropped.
  python-chess is deliberately a different implementation from the Kotlin prover, so the two are an independent check.
- Target at least 1,000 positions per tier (Expert may be fewer; a smaller list just repeats sooner; report the
  counts). Selection among the survivors favours variety: cap how many share the same first-move piece type and the
  same `last` square, then sort by (FEN) so **the file's order is a pure function of the input**, like `LexiconWords`.
  Dedupe by FEN.
- Output: one `ChessPositions.kt`, `internal object ChessPositions { val STANDARD, HARD, EXPERT: List<String> }`,
  entries `"<fen> <last-uci-or-->"` in chunked string constants (a JVM string constant is capped at 64 KB, see
  `LexiconWords.kt`). Header comment: GENERATED, by which script, from which dump (name and date), sorted-order contract.
- Picked by index: `list[seed.mod(list.size)]`-style with `Rng`; adding or removing a position moves every later
  daily board, so a parity pin goes red on purpose (as `LexiconWebParityTest`).
- Credit: README one line, and a short `docs/chess-positions/NOTICE.md` stating source, licence (CC0) and the date
  of the dump used.

## Teaching

The hint flow is the shared one (tap 1 nudge, tap 2 explain, player moves, panel confirms; "Show me" applies it).

- `ChessTeacher.deduce(position, remaining)` takes **only visible state**; the prover is the reasoning (it needs the
  position and the move budget, nothing else). The stored/data answer is never read by the teacher, and no field of
  `ChessState` holds one, so there is no fallback to measure: say so, as Nonogram does.
- Techniques, simplest first: `MATE_IN_ONE` (a move that mates now: explain *why* it is mate: check, and for each
  neighbouring square of the king, own piece / covered by X / capture or block impossible; cap to one sentence of
  at most 200 chars), `FORCING_KEY` (the move after which every reply allows a mate: explain "N replies, each
  allows mate next move", naming the one or two most forcing replies in SAN), `QUIET_KEY` is the same with the
  note that the move is not a check. No chess-theme classification in v1.
- **Mistake** = the player's last move leaves no forced mate within the moves left (`forcedMate(p, remaining) is
  None`, never `Truncated`, and never "differs from the data"). Mistakes outrank steps; taking the move back clears
  the hint. A legal move that keeps a forced mate is never a mistake even if it is not the key (it cannot exist for
  the first move because the key is unique, but it can for later moves).
- `focus`/`cited`/`targets` index squares `0..63` (the board's own square indices; document it in the file header),
  plus the four promotion-picker buttons at `100 + kind`.
- Every loop walks squares/moves in index order; no hash container reaches a pick (`CLAUDE.md`, section 5).

## Board

- Square size from both axes: `minOf(maxWidth / 8, maxHeight / 8)`; the box is the same size before and after
  solving, while a hint opens, and while the promotion picker is open (the picker is an overlay in the board).
  Check 390x844, 390x664, 360x640 and 375x537.
- Pieces are **vector shapes drawn on a `Canvas`** (`puzzles/ChessPieces.kt`), authored for this project: no font
  glyphs (the web's font is subset), no copied piece set (licences). Colour is never the only signal: white and
  black pieces differ in fill *and* outline, and both read on both light and dark themes. Square colours from
  `MaterialTheme.colorScheme`, a small accent for the last opponent move and the selected piece's legal squares.
- The board reports its highlight with `highlightGrid(8, 8)` plus `highlightAnchor(100 + kind)` for the picker, and
  `keepClear()` over the picker. When a hint is open, the board dims as for the other puzzles.
- Keyboard (web / hardware): not in v1. `keyboardHelp` empty. (A "type the move in SAN" box is a later item.)
- Orientation: the player's side at the bottom, always. Rank/file labels drawn small on the edge squares.
- `Preview`: a fixed, legal crop (for example a back-rank mate pattern: a few pieces), real piece shapes, never
  `generate()`.

## Walkthrough (`tutorial`)

Hand-built tiny positions, each with a unique forced mate, proved by the independent checker in tests. About 7 frames:
(1) read the goal on a solved mate-in-1 (back rank) with a `BoardHighlight`; (2) make the move by the real
tap-tap gestures (one frame per emitted state: the move is one state, selection is not); (3) a mate-in-2 where the
reply is shown and the player sees why the first move had to be the one it was; (4) the promotion picker; (5) the
caption on a mistake (Undo); (6) free play on a mate-in-2 the hints can finish with no fallback and no mistake.
Captions at most 170 characters. Teach with the position's own squares and pieces.

## Tests

Independent of the code they check (CLAUDE.md "Tests"):

- **Perft**: hard-coded published counts (start position depth 1-4 = 20, 400, 8902, 197281; "Kiwipete" depth 1-3 =
  48, 2039, 97862; and the other standard perft positions 3-6), cross-checked against python-chess when written.
  This pins castling, en passant, promotion and pins.
- **Mate-in-N checker**: a naive, unpruned, independently written minimax (no ordering, no budget) over the walkthrough
  boards and a sample of every tier's data, agreeing with `forcedMate` on key lists and on shortest N. Slow on N = 4,
  so the sample thins as N grows (SnapCluesTest's rule).
- **Data**: every shipped position, per tier, passes the property in "Data"; sorted, unique, parsable; the year of
  daily seeds per tier never repeats a malformed entry. (There is no generator and so no fallback: `FallbackTest`
  does not apply, and `docs/PUZZLE_STANDARDS.md` section 4 says so.)
- Rules: a legal mate that is not the data's line wins; a non-forcing first move is a mistake and the opponent
  then escapes mate in every case; stalemate is not mate; every reply a defender could choose against a key is
  mated (exhaustive on the walkthrough boards).
- `defence` policy pinned (the saved-game contract); `StateSerializationTest.mutate`; `ParityFingerprint.body`
  (`start` + `mateIn`); parity pin test over three dates x three tiers; `PuzzleNotesTest` entry; teacher soundness,
  text fit (200 / 70), tutorial per section 8 of the standards.

## Work split (what each agent owns; the files must not overlap)

**Phase 1, in parallel**
- **A, engine**: `ChessRules.kt`, `ChessPosition`, `ChessRulesTest`, `ChessMateCheckerTest` (naive checker),
  the Budgets measurements.
- **B, data**: `tools/chess/build.py`, `tools/chess/README.md`, `ChessPositions.kt`, `docs/chess-positions/NOTICE.md`.
  A Kotlin data test lands in phase 2 (it needs A's engine).
- **C, pieces**: `ChessPieces.kt` only: `DrawScope.drawChessPiece(white: Boolean, kind: Char /* K Q R B N P */, ...)`,
  plus a render harness check at true size (cell sizes 28, 40, 48 dp; light and dark), the motif for `Preview` as a
  separate function `DrawScope.drawChessMotif`.

**Phase 2, after phase 1 is merged**
- **D, game**: `Chess.kt` (state, `PuzzleType`, `generate`, board, input, promotion picker, caption), registry line,
  `ParityFingerprint` and `StateSerializationTest` branches, parity pin, `PuzzleNotes`, home grid check, web render.
- **E, teacher**: `ChessTeacher.kt`, `ChessTeachingTest`, the `docs/TEACHING.md` row.
- **F, walkthrough**: `ChessTutorial.kt`, `ChessTutorialTest`.
- Then housekeeping (README list, screenshots, this file's matrix row in `docs/PUZZLE_STANDARDS.md`, `docs/TODO.md`),
  and one PR.

## Out of scope

Playing against a computer, a full game, opening or endgame training, ratings, mate-pattern classification, move
input by typing, "mate in 5+", board flipping. Accessibility stays deferred (CLAUDE.md "Settled"); do not make it
worse (tap-tap input uses `clickable` squares, not raw pointer input, so it is the first board with real click actions).
