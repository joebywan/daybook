# Reckon (arithmetic cages on a Latin square) — spec

A daily **arithmetic cage** puzzle: a 6x6 grid, digits 1 to 6 once in every row and column, cut into cages that each
carry a target and an operation (+ − × ÷). The digits in a cage must make its target with its operation. Requested in
issue #483. Read `docs/PUZZLE_STANDARDS.md` first; this file records only what is specific to this puzzle and where it
departs from the recipe. Where the two disagree on a point not listed here, the standards win.

## Decisions (made; do not reopen without the owner)

| | |
|---|---|
| `id` | `"reckon"` (hashed into every seed: never rename). Display name **Reckon** (the owner left the name to Claude, 2026-11-14: a plain word for working a sum out, one short line on a home tile). Tagline along the lines of "Make every cage's sum with 1 to 6 in each line". The genre is published as *KenKen* (a trademark) and *Calcudoku*: neither name appears anywhere the player sees (Cage Sudoku / Dominoes precedent; `GuideTest` bans both). |
| Grid | **6x6 on every tier**, digits 1-6. Tiers differ by cages, givens and the depth the hints need, never by size (the issue's suggestion; Cage Sudoku's precedent). One size is also one layout to get right on a phone, below. |
| Not a Cage Sudoku tier | Its own puzzle: no boxes, four operations, and repeats allowed in a cage, so its logic, hints and save are its own. |
| Answer | **Exactly one**, proved exhaustively. `solved` checks the rules, never the stored answer. |
| Notes | Pencil marks, as Sudoku's (stored, never refused, struck from peers in the same state). Candidate work is most of this genre. |
| Bad judgements allowed | Any digit in any open square. Clashes and broken cages are shown after the usual 1 s settle, never refused. |

## Rules

- Every row and every column holds 1 to 6 once.
- Cages partition the grid; a cage is 4-connected.
- A cage shows a target and an operation; its digits, combined by that operation, make the target:
  - `+` and `×`: 2 to 6 squares for `+`, **2 to 4 for `×`** (below: the label must fit), every digit used.
  - `−` and `÷`: exactly 2 squares; the larger less (or over) the smaller. `÷` only where it comes out whole.
  - One square: shows its digit alone. It is a given.
- **A cage may repeat a digit** when the two squares share no row or column (the genre's rule; the Latin square already
  forbids the rest). This is where Reckon departs most from Cage Sudoku: a cage's combinations depend on its *shape*, not
  only its size, and the hints must say so ("8× in an L: 1·2·4, or 2·2·2 is out, two of its squares share a row").
- **Solved:** every square filled, every line 1-6, every cage makes its target.

## Tiers

| | Standard | Hard | Expert |
|---|---|---|---|
| Biggest cage | 3 | 4 | 5 (`×` still at most 4) |
| One-square cages (givens) | 3-5 | 1-2 | none |
| Operations | all four | all four | all four |
| Depth the hints' walk must need | singles and cage combinations | a pair or a cage confined to a line, never a what-if | a what-if (Tally's `MAX_LINKS` style cap) |

Gate as Tally's `Spec.needs`: a draw needing more or less is redrawn. `TierNotes` lines (a size, a count, what is given;
never "harder"): "Cages of up to 3 squares, a few digits given" / "Cages of up to 4 squares, one or two digits given" /
"Cages of up to 5 squares, no digits given". The bands above are a starting point: measure, then fix them here.

## Generation (`puzzles/ReckonRules.kt`, object `ReckonLogic`)

- **Latin square:** a fresh 6x6 per attempt from `Rng(seed + attempt)` (rows shuffled under the column rule, or
  Inequality's own filler if it generalises). No hash order may reach the `Rng`.
- **Cages:** grown from the first uncovered square in reading order (Shikaku's pattern), a size drawn up to the tier's
  cap, stray singles merged into a neighbour on Hard and Expert. Then an operation per cage drawn from those its digits
  allow (`÷` only when whole, `×` only up to 4 squares), weighted so every operation appears on every board.
- **Proof:** `ReckonLogic.solutions` (candidate masks per square and per cage tuple, propagated to a fixpoint, then a branch
  on the cage with fewest tuples; no budget; a sealed `Verdict` where only `ExactlyOne` carries an answer), then the
  ladder's walk must finish at the tier's depth. Taking back is cheap here: a draw that fails is redrawn, not dug.
  `ReckonOracle` (test) shares nothing: square by square in reading order over 1-6, the cages checked only when full.
- `lastResort` per tier is the first seed from 1 the tier accepts, proved by a test; `FallbackTest` walks a year per tier.

**Measured before building** (2026-11-14, a throwaway Python probe, not kept: random Latin square, random cages, a
random allowed operation per cage, no tuning, 200 draws each):

| Cages | One answer |
|---|---|
| up to 3, singles allowed | 68% |
| up to 4, singles allowed | 42% |
| up to 4, no singles | 26% |
| up to 6, no singles | 10% |

So uniqueness alone is cheap on every tier; the cost will be the depth gate, which is the number to measure first
(draws a board over a year, JVM and both browsers, as the other puzzles record).

## The ladder (`ReckonTeacher.kt`, shared with the generator)

Simplest first; each rung ends in a placement or a note struck, and a hint never asks the player to keep an elimination
it has not shown:

1. Last square in a line; only digit left; only place in a line (Inequality's singles).
2. **Cage combinations:** a cage's tuples (respecting its shape's repeat rule and the digits its squares can still take)
   leave a square one digit, or strike a digit from it. One-square cages are givens and need no rung.
3. **Cage in a line:** every tuple of a cage lying in one line uses some digit, so the line's other squares lose it.
4. Naked pair in a line (Inequality's).
5. **What-if:** suppose a digit, follow rungs 1-4 at most `MAX_LINKS` steps, some square or cage is left with nothing.

The generator's ladder is the teacher's (the Cage Sudoku lesson: a stronger generator leaves the teacher a step it
cannot explain). A mistake is a digit that differs from the stored square, sound because the board has one answer.
Training: a course on rungs 2, 3 and 5 (`core/training/`), once the ladder is measured.

## The board on a phone

The owner's concern (2026-11-14): with cages over a grid, the board can get busy, or its labels too small. What was
measured, and the rules that follow:

- **Squares are half as wide again as Cage Sudoku's.** Rendered 2026-11-14 at 375x537 (Chromium, Expert): the board box
  is 297 dp wide, so Cage Sudoku's squares are 33 dp and a 6x6 grid's 49 dp; at 390x844 (366 dp box) 41 dp and 61 dp.
  Inequality's 6x6 board already sits in that box with its own 1-6 pad, so the pad and toolbar fit as they are.
- **One set of thick lines, not two.** Cage Sudoku is busy because it draws two structures: the 3x3 box walls *and* a
  dashed outline inset in every cage. Reckon has no boxes, so the cage walls are the only heavy line (`ColorScheme.wall`,
  solid, never dashed), and inside a cage the squares are split by a hairline. No inset outlines.
- **Labels stay short.** At most four glyphs: three digits and the sign (`120×`). That is why `×` cages stop at four
  squares (6·6·5·5 = 900 is the largest possible); `+` of six squares is at most 36. Drawn in the top-left of the cage's
  first square in reading order, in a top band (Cage Sudoku's `topBand`), sized by `boardSp()` from the square, never the
  font scale. Shown as `+ − × ÷` (the bundled Fredoka has them: Equation draws them on its tiles).
- **Notes under the label.** Six notes in a 2x3 under the band: about 11 dp each at 375x537, larger than Cage Sudoku's
  nine in 33 dp. A square with no label keeps the full square for notes.
- **Colour is not the cage.** Walls carry the cages in both looks; an Arcade tint per cage (a four-colouring, as Regions'
  `regionColours`) is optional and is decided by rendering it beside the plain board, not assumed.
- **Check by rendering**, both browsers, 375x537 and 390x844, light and dark, the longest label on the board, and a
  board full of notes: `tools/render/render.py`. Then on the emulator. The board ships only if no label touches a wall
  or a note, and squares read as squares (not stripes) where cages run in long lines.

## Board, input, keys

- The grid and pad are Sudoku's (`SudokuGridBoard`, notes, digit-first entry, the keys), **generalised from 9 to n** with
  Sudoku's and Cage Sudoku's boards pinned unchanged (rendered before and after). It is 9x9-bound today (about 20 places);
  if generalising it costs more than a copy would, say so in the PR rather than forking it quietly.
- State (`ReckonState`): a `SudokuState`-shaped grid (digits, answer, notes, selection, moves), `cageOf` (cage per
  square, numbered by first square), `ops` and `targets` per cage. Field names fixed once shipped (saves).
- Red after a 1 s settle: a repeat in a line, a full cage that misses its target, a cage no tuple can finish any more.
- Highlight indices: squares `0 until 36`, digit keys `36 + d`, the pencil 43.

## Walkthrough

A 4x4 (1-4) built by hand with one answer (by the oracle): a one-square cage, a `+` cage, a `−` pair with two ways round,
a `÷` pair, an L-shaped `×` cage that repeats a digit, and a clash shown red then cleared. Then free play.

## Done means

Everything in `docs/PUZZLE_STANDARDS.md` section 13, plus: the measured numbers above replaced by the built ones, the
fallback table in `docs/TEACHING.md`, a guide page, `version.txt`'s middle number bumped, and What's new rewritten.
