# Pearls (a pearl loop) — spec

A daily **pearl loop** puzzle: a square grid with black and white pearls on some squares. Draw one closed loop through
the middles of squares that passes through every pearl and keeps each pearl's rule. Read `docs/PUZZLE_STANDARDS.md`
first; this file records only what is specific to this puzzle. Where the two disagree on a point not listed here, the
standards win. The genre is published by Nikoli as *Masyu*: that name, and any Nikoli wording or art, never appears
anywhere the player sees (Cage Sudoku / Dominoes precedent).

## Decisions

| | |
|---|---|
| `id` | `"pearls"` (hashed into every seed: never rename). Display name **Pearls**. |
| Rules | Below. No move limit, no lives, no way to lose. |
| Answer | **Exactly one loop**, proved exhaustively (`PearlsLogic.solutions`, no budget, sealed `PearlsVerdict`: only `ExactlyOne` carries a loop). `solved` checks the rules, never the stored loop. |
| Input | **Drag through squares draws the line** (Snap's drag: the square under the finger, every square a fast swipe crosses taken in order), one state on lift. **A tap near the middle of an edge** crosses it (a note: "no line here"), or rubs out the line or the cross already there. |
| Bad judgements allowed | Any line is accepted, even one that breaks a rule. Breaks are shown red after a 1 s settle, never refused. |

## Rules

- The board is an `n x n` grid of squares; some hold a **black** or a **white** pearl.
- The loop is drawn through square middles, moving up, down, left or right. It is **one closed loop**: every square it
  visits has exactly two of its sides on the loop; it never crosses or branches. Not every square need be visited.
- **Every pearl is on the loop.**
- **Black pearl:** the loop turns on it, and goes straight on through the next square on both sides.
- **White pearl:** the loop goes straight through it, and turns on at least one of the two squares just before or after it.
- **Solved:** the lines form one closed loop and every pearl's rule holds (`PearlsLogic.isSolved`). Crosses never count.

## State (`puzzles/PearlsRules.kt`)

```kotlin
@Serializable data class PearlsState(
    val size: Int,
    val pearls: String,   // n*n: '.' none, 'b' black, 'w' white; row by row
    val loop: String,     // one char per edge, '1' on the answer loop: for mistakes and tests only
    val edges: String = ".".repeat(edgeCount), // the player's: '.' open, '-' line, 'x' cross
    override val moves: Int = 0,
) : PuzzleState
```

Edges: horizontal edge `(r, c)-(r, c+1)` is `r * (n - 1) + c` (`n(n-1)` of them), then vertical edge `(r, c)-(r+1, c)` is
`n(n-1) + r * n + c`. Strings, as Nonogram's and Tally's, because a save keeps two dozen undo copies. The drag in
progress, the keyboard cursor and its armed cross are `remember` state in the board.

## The ladder (`PearlsLogic`, shared by the generator and `PearlsTeacher`)

Every rule is applied to an edge state (open / line / cross) and only ever adds lines and crosses, so the fixpoint is the
same in any order (the generator may take steps in any order; the teacher shows the simplest first).

1. **Squares** (`SQUARE`): a square with two lines takes no more (implicit: the teacher treats those crosses as made,
   so it never asks the player to tidy them); a square with one line and one way on continues; an empty square with one
   way in is a dead end (crossed); a pearl with exactly two ways left takes both.
2. **Black pearl** (`BLACK`): an arm (the edge out and the next square's straight edge on) is impossible when an edge is
   crossed or off the board, the next square already has a line across that way, or the next square is a black pearl;
   then the opposite arm is drawn. A line out of a black pearl is an arm: it continues, and the opposite side is crossed.
3. **White pearl** (`WHITE`): an impossible direction (an edge crossed or off the board, a line across) leaves the other:
   straight through. A line through a white pearl sets its direction. Two straight neighbours on one side and the other
   side's continuation is crossed (it must turn on one side).
4. **Small loop** (`SMALL_LOOP`, Hard): an open edge joining the two ends of one stretch of line would close a loop that
   leaves out a pearl or another stretch, so it is crossed.
5. **What-if** (`WHAT_IF`, Expert): suppose an edge is a line (or a cross), apply 1-4 for at most `MAX_ROUNDS` rounds,
   and some square or pearl is left impossible (three lines, a dead end on a pearl, a black pearl that cannot turn, a
   small loop), so the edge is the opposite.

**Tiers** by size and the deepest rung a board must need (`PearlsLogic.depth`, Tally's `Spec.needs`): Standard 6x6
finished by 1-3 alone; Hard 8x8 needs 4 at least once and no what-if; Expert 10x10 needs a what-if at least once. A draw
needing more or less is redrawn. Sizes checked against a thumb: at 375x537 a 10x10 square is about 32 dp.

## Generation

1. **A random simple loop.** The loop through square middles is the boundary of a region of the `(n-1) x (n-1)` grid of
   faces between them. Grow a face region from one random face, one face at a time (a random face beside the region),
   keeping a face only when the boundary is still one simple closed curve (no square with four sides on it, one cycle).
   Stop at a target share of faces. All picks walk lists in index order; no hash container reaches the `Rng`.
2. **The pearls it implies.** Black on every turn whose two neighbours along the loop go straight; white on every straight
   with a turn just before or after.
3. **Take pearls back** in random order while the tier's ladder still finishes the board; then require
   `depth == needs` (redraw otherwise) and `solutions == ExactlyOne(loop)`.
4. `generateVerified(seed, difficulty)` tries `Rng(seed + attempt)` up to `ATTEMPTS`; `lastResort` is a fixed board per
   tier, proved by a test. `FallbackTest` walks a year of daily seeds per tier and asserts none falls back.
5. **Speed is the main risk** (the council flagged 10x10). The ladder runs per pearl taken back, so propagation is a work
   list over squares, not full passes. Measured numbers go in the Measured section and `CLAUDE.md`.

## Teaching

`PearlsTeacher.deduce` gets the pearls and the player's edges, never the loop. A mistake is a line the loop lacks or a
cross on an edge it uses, sound because the loop is unique. Steps end in lines or crosses (crosses are notes, Nonogram's
precedent). Highlight indices: squares `0 until n*n`, edge `e` is `n*n + e`. No fallback exists on a generated board.

## Feedback

Derived, red after a 1 s settle: a square with three or more lines, a pearl whose rule is broken by the lines around it,
and a closed loop that is not the whole answer (it leaves out a pearl or another line). A closed loop that keeps every
rule is the solve.

## Measured

Filled in by the PR that builds it (draws per board, generation times on the JVM, Chromium and WebKit, the teaching
coverage).
