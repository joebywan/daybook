# Mines (no-guessing minefield) — spec

A daily **minefield**: numbers say how many mines touch a square; reveal every square that has none. Every board is
cleared by reasoning alone, from an opening the board gives. Read `docs/PUZZLE_STANDARDS.md` first; this file records only
what is specific to Mines and where it departs from the recipe. Where the two disagree on a point not listed here, the
standards win.

## Decisions (the owner's; do not reopen)

| | |
|---|---|
| `id` | `"mines"` (hashed into every seed: never rename). Display name **Mines**. Never "Minesweeper" in anything the player sees. |
| No guessing | **No board ever needs a guess.** A board ships only if the teacher's ladder clears it from the opening, which also makes the answer unique: every mine is forced. |
| Opening | **Given.** The seed picks a square; it and its eight neighbours are kept clear of mines, and the squares it opens (the usual spread from a square with no mines around it) are revealed when the board loads. No first-click luck, and everyone gets the same board. |
| Lives | **Three, as Pointers'.** Revealing a mine shows it, marks it as a mine and costs a life. Lose all three and the board is failed: Try again restarts it from the opening with the lives restored. Losing is never a completion. |
| Undo | **None** (`offersUndo = false`). A reveal is never wrong except on a mine, and undo would only hand back a life. Flags are notes and toggle off by the same gesture. |
| Hints | **On** (unlike Pointers). |

## Rules

- A `width` x `height` grid; `mines` of its squares hold a mine. A revealed square shows the number of mines among its eight
  neighbours, or nothing for none.
- **Reveal** a hidden square: a mine costs a life and stays shown as a mine (it counts as found). A safe square opens; if its
  number is 0, its neighbours open too, and so on outward (breadth first in reading order, so it is the same on every
  platform). A flagged square is never revealed (take the flag off first), so a flag protects the square under it, and
  the spread from a 0 passes flagged squares by.
- **Flag** a hidden square: a note that it holds a mine. Flags are the player's, never checked when placed and never a
  life; a wrong flag is allowed (the hint calls it out). "Mines left" is `mines - flags - mines hit`, and can go below 0.
- **Solved** when every square without a mine is revealed: a rule check on the board, flags play no part.
- **Failed** when no lives are left and the board is not solved.
- **No chording** (a tap on a satisfied number opening its neighbours). With a wrong flag it would open a mine, which is a
  life lost to a note; a tap per square is the safe default. Revisit only if the owner asks (`docs/TODO.md`).

## Tiers

Every tier is **8 squares wide**: a mis-tap costs a life, so the square must be a thumb's width. At 375 dp wide, with the
board's 16 dp gutters, an 8-wide square is 42 dp (Pointers capped its width for the same reason). The tiers differ in
height, density and the deepest step of the ladder a board must need (Tally's `Spec.needs` gate): a draw needing more or
less is redrawn.

| Tier | Board | Mines | Must need |
|---|---|---|---|
| Standard | 8x8 | 10 (16%) | the single-number rules only |
| Hard | 8x10 | 16 (20%) | a subset or pair step at least once |
| Expert | 8x12 | 22 (23%) | the count against the numbers or a what-if at least once |

## Generator and proof

`MinesLogic.generateVerified(seed, difficulty)`: for `attempt in 0 until ATTEMPTS`, `Rng(seed + attempt)` picks the opening
square, then the mines by shuffling the squares outside the opening's 3x3 and taking the first `mines`; only lists reach the
`Rng`, never a hash container. The opening is revealed; a draw whose opening reveals more than `MAX_OPENING` of the safe
squares is redrawn (the board must not give itself away). The ladder then walks the board from the opening, revealing each
square it proves safe (reading its number off the board, as the player would), and the draw is accepted only if the walk
reveals every safe square and its deepest step is the tier's. That walk is the proof: every square is forced, so the answer
is unique. `lastResort(difficulty)` is the first seed counting from 1 whose draw the tier accepts, itself checked by a test;
`FallbackTest` walks a year per tier through `generateVerified` and none falls back.

Independent check, in tests (`MinesOracle`): exhaustive enumeration of every mine layout consistent with the revealed
numbers and the mine total (frontier squares by backtracking, the rest counted combinatorially), with no rules at all.
Every ladder step, on the generator's path and from boards a player made, is checked to hold in every consistent layout;
since the steps reveal every safe square, every mine is forced and the answer is unique.

## The ladder (teacher = generator)

Knowledge is what the player can see: revealed numbers and mines hit. Flags are never read as knowledge (they may be
wrong); a step whose squares are already flagged (for mines) or revealed (for safe squares) is skipped as already shown, so
the hint is the first fact the player has not put down. One fact at a time, simplest first, restarting after each:

1. `ALL_FOUND` (level 1): a number already touching as many known mines as it shows: its other hidden neighbours are safe.
2. `ALL_MINES` (level 1): a number whose hidden neighbours are exactly as many as the mines it still needs: all mines.
3. `SUBSET` (level 2): one number's hidden neighbours all lie among another's; the difference in what they still need is in
   the other's extra squares, and it is 0 (all safe) or all of them (all mines). The 1-1 and 1-2 at a wall.
4. `PAIR` (level 2): two numbers that share squares; if B needs as many more mines than A as B has squares A cannot see, those
   squares are all mines and A's own squares are safe. The 1-2-1.
5. `COUNT` (level 3): the mine total. Mines left 0: every hidden square is safe; mines left equal to the hidden squares: all
   mines.
5b. `EDGE` (`count-edge`, level 3): the count against the numbers. Numbers on squares none of them share (picked greedily,
   largest need first) need every mine left between them, so every other hidden square is safe. An Expert board must use
   this or a what-if: the plain count alone at the end (a pocket no number touches) is too slight.
6. `WHAT_IF` (level 3): suppose a hidden square beside a number is a mine (or safe), follow the single-number rules at most
   `MAX_LINKS` (3) numbers, and one number is left with too many mines or too few squares: so the square is the other.

A **mistake** is a flag on a safe square, sound because the board has one answer (the answer is read only for this and for
the fallback). The **fallback** points at a safe square from the answer and says so; on a shipped board it is never reached
(measured below). `neededTechnique` is `SUBSET` / `PAIR`, `EDGE` / `WHAT_IF` from the generator's walk, null on Standard.

Highlight indices: squares `0 until width * height` (the counter is never highlighted; the count steps name it in words).

## Input

- **A Reveal / Flag pen** under the board (Nonogram's pen model: `rememberSaveable` in the board, never state, Reveal by
  default each visit). A tap uses the pen; a **long press uses the other tool**, so a flag never needs a trip to the pen.
- A tap on a revealed square does nothing. One tap = one state (a cascade is one state).
- Keyboard (pure, tested, `MinesKeys.kt`): arrows move a cursor, Space or Enter reveals, F flags or unflags.
- A hit mine flashes in `scheme.error` and the board gives Pointers' bump shake (the flash alone under reduced motion),
  with `PlayScreen`'s firmer bump haptic.

## Layout

A row above the board holds the hearts (Pointers' `Hearts`) and "N mines left" or, when failed, "Out of lives" and Try
again, always the same height. The board sizes from both axes; the pen row sits under it and is `keepClear`. A box wider
than 1.1 times its height (a phone on its side, a tablet) puts the hearts, the count and the pens in a column beside the
board instead, which then has the whole height.

Square size (CSS px / dp): 390x844 every tier 44.75 wide-bound; Expert at 375x537 (a phone browser with both toolbars)
21.5, height-bound; a phone on its side (844x390) Expert about 31.

## Colours

`BoardHues.pair(mines.hashCode())`: hue A tints the hidden squares (fill), hue B inks the flags. Revealed squares are the
surface. Numbers take one hue each, as a digit always (colour is never the only signal): 1 Blue, 2 Teal, 3 Violet, 4 Amber,
5 Rose, 6 Gold, 7 and 8 `onSurface`. A mine hit is `scheme.error` behind a mine glyph; no Coral or Green content.

## Share card

As Pointers': the hearts left at the solve (`❤️❤️🖤`), then the hint count as every card shows it. Nudge "Not a single
mine." with all three and no hints.

## State

```kotlin
@Serializable data class MinesState(
    val width: Int, val height: Int,
    val mines: String,            // '1' a mine, '0' none, per square in reading order
    val opening: Int,             // the square the opening spreads from
    val marks: String,            // '.' hidden, 'o' revealed, 'f' flagged
    val bumps: List<Int> = emptyList(), // mines hit, in order: lives = 3 - bumps.size
    override val moves: Int = 0,
) : PuzzleState
```

`retry()` is the opening again with no flags and no bumps, one state.

## Measured

A year of daily boards per tier (`MinesRulesTest`, `build/reports/mines-generation.txt`), Standard / Hard / Expert:

- Draws per board (of `ATTEMPTS` = 3000): mean 9.3 / 5.6 / 28.2, most 54 / 46 / 161. No day reaches `lastResort` (`FallbackTest`).
- Opening (share of the safe squares open at load): median 35 / 25 / 24%, at most 44 / 44 / 45% (the cap).
- Generation, desktop JVM, ms median / p95 / max: 0.3 / 1.3 / 4.1, 0.4 / 2.1 / 8.7, 2.5 / 12.1 / 30.9. Chromium: 0.7 / 2.7 / 6.8,
  0.9 / 3.7 / 12.3, 6.9 / 32.4 / 54.3. WebKit: 1 / 3 / 7, 1 / 4 / 10, 7 / 28 / 63.
- Hints alone (200 boards per tier, `MinesTeachingTest`): 20.8 / 32.8 / 42.8 steps per board; subset 0 / 2.1 / 2.8, pair 0 / 0.8 / 1.2,
  count against the numbers 0 / 0 / 0.9, what-if 0 / 0 / 0.16 per board; the plain count never appears on a shipped board.
  **Fallback: 0** on every tier, and none from boards a player made.
- Of raw draws before the gate (2,000 a tier), the walk stalls (a guess would be needed) on 30 / 63 / 86%.
