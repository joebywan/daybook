# Who's Who (a logic grid from templated clues) — spec

A daily **logic grid**: a few people, each with one item of every category (a house in a row, a pet, a job...), and a
numbered list of clues that fix exactly one way to match them up. Read `docs/PUZZLE_STANDARDS.md` first; this file
records only what is specific to this puzzle and where it departs from the recipe. Where the two disagree on a point not
listed here, the standards win.

## Decisions

| | |
|---|---|
| `id` | `"whoswho"` (hashed into every seed: never rename). Display name **Who's Who**. Tagline "Match everyone to everything from the clues". Never called an "Einstein puzzle" or "Zebra puzzle" anywhere the player sees. |
| Answer | **Exactly one assignment**, proved exhaustively. `solved` checks the clues and the one-each rule, never the stored answer. |
| Generated | On the device from fixed item lists shipped in the code (`WhosWhoWords.kt`): names, and five categories of five plain, screened words. No trademarks, nothing regional. |
| Marks | The player's ✓ and ✗ per (person, item) are their notes and live in `PuzzleState`. A ✓ shows the other items of that cell, and that item in the other rows, as ruled out: **derived, never stored** (Tents' grass). Bad judgements are allowed: two people ticked for one item, or a cell with everything crossed, is shown, never refused. |
| Clue strikes | Tapping a clue strikes it through: a note, in state, one move. |
| Input | Tap a cell to select it (`remember`, not state), then an item on the pad under the board, with the pen (✓ or ✗) beside it. One tap of an item = one state. |

## Rules (as shown to the player)

- N people live in a row of N houses, numbered 1 to N from the left; each has one item of every other category, and no
  two people share one.
- Every clue is true. "Next to" means a house number one higher or lower; "somewhere left of" means a lower number.
- Solved when every person has one item of every category and every clue holds.

## Tiers

| | People | Categories (besides names) | Clue kinds | Must need (gate, Tally's `Spec.needs`) |
|---|---|---|---|---|
| Standard | 4 | house + 1 | is / is not, always naming a person | nothing past the direct steps (clue says so, last one left) |
| Hard | 4 | house + 2 | + is / is not between two things, next to, somewhere left of | a cross-category link or a house step at least once; no what-if |
| Expert | 5 | house + 2 | + either / or | a what-if at least once |

Clue kinds (an item is a name, a house number or a category word):

- `SAME(a, b)`: one person has both ("The baker has the cat").
- `NOT(a, b)`: different people ("Ann is not the baker").
- `NEXT(a, b)`: their houses are neighbours ("The cat owner lives next to the red house"). Never on a house number.
- `LEFT(a, b)`: a's house number is lower ("Ben lives somewhere left of the pianist"). Never on a house number.
- `EITHER(a; b, c)`: b and c from one category, a has one of them ("Ann has either the cat or the dog"). One category,
  so b and c are always two different people and "either" cannot be read two ways.

## Logic (`puzzles/WhosWhoRules.kt`, object `WhosWhoLogic`)

- Candidates: for every person and every category, a bit mask of the items still possible, read from the marks (a ✗
  removes; a ✓ keeps only its item and removes it from the other rows of that category).
- **The ladder**, simplest first (the teacher's and the generator's, one list):
  1. `clue` — a clue that names a person settles that person directly: ✓ for is, ✗ for is not, ✗ for every item
     either/or leaves out.
  2. `only` — a cell with one item left, or an item with one person left: ✓.
  3. `link` — is / is not / either-or between two things, carried through what is known: "the baker has the cat; Ben
     cannot have the cat, so Ben is not the baker"; "Ann is the baker, so she has the cat".
  4. `house` — next to / somewhere left of: a person cannot hold an item when none of their possible houses has a
     possible partner on the right side; a person known to hold an item cannot be in a house with no partner beside it.
  5. `what-if` — suppose a ✓; the steps above (at most `MAX_LINKS` = 3 of them) reach a contradiction (a cell or an
     item with nothing left, or a clue no placement keeps), so it is a ✗. Shortest chain wins.
- **The count** (`count`): category by category over permutations, every clue checked as soon as its categories are
  placed, no budget. Sealed `Verdict`: `None`, `ExactlyOne(answer)`, `Several`; only `ExactlyOne` carries an answer.

## Generation

1. `Rng(seed + attempt)`: N names from the name list, the tier's categories from the five (house always), N items of
   each (in list order), a random permutation per category: the answer.
2. Every clue of the tier's kinds that is true of the answer, in a fixed enumeration order (no hash container), then
   shuffled by the `Rng`.
3. Clues are added from the shuffled pool until the count says `ExactlyOne` and the tier's ladder finishes the board.
4. **Taken back while it holds:** every clue, in random order, is removed if the rest still give one answer and the
   ladder still finishes. The result is minimal: no clue is implied by the others (a test removes each in turn).
5. Shipped only if its depth is exactly the tier's gate; otherwise the next attempt. `generateVerified` returns null
   after `ATTEMPTS`; `lastResort` is one fixed seed's board per tier, proved by a test. `FallbackTest` walks a year of
   daily seeds per tier and never reaches it.

## Teacher (`puzzles/WhosWhoTeacher.kt`)

Mistakes first: a ✓ the answer lacks or a ✗ on the answer's item (sound: one answer). Then the ladder from the marks
alone (`deduce` takes the clues and the marks, never the answer), every step ending in ✓ or ✗ marks the player can make.
No fallback exists on a generated board (measured). Highlight indices: table cell `person * columns + column`, clue `k`
is `CLUE + k`, pad item `i` is `PAD + i`.

## Board UI (`puzzles/WhosWho.kt`)

- A table, rows = people, columns = categories (house first). A cell shows its ✓ item, else the items still possible in
  small type. A cell or a clue that no answer can keep (a clash, nothing left, a clue the marks break) turns
  `scheme.error` after a one-second settle (derived).
- Under it the clue list, a scrolling strip that takes the height left (it scrolls to a hinted clue); under that the
  pad: the pen (✓ / ✗) and the selected category's items. Nothing changes size when feedback shows.
- A window wider than tall puts the clues beside the table.
- Keyboard (`WhosWhoKeys.kt`, pure): arrows move the selection, 1-5 tick that item, Shift+1-5 cross it,
  Backspace clears the cell.

## Measured (JVM unless stated; a year of daily seeds per tier)

- Clues a board: 6.6 / 8.8 / 12.2. Removing any one clue of 90 sampled boards leaves several answers 773 times and one answer
  the ladder cannot reach 52 times: no clue is spare.
- Draws a board: mean 1.00 / 1.00 / 1.49, most 1 / 1 / 4 of 400; no day reaches `lastResort`.
- Generation, median / p95 / max ms: JVM (a loaded desktop) 0.1 / 0.4 / 30, 0.9 / 4.8 / 14, 25 / 89 / 228; Expert in Chromium
  40 / 106 / 294, in WebKit 53 / 126 / 267.
- Hints: `docs/TEACHING.md`. No fallback exists on a shipped board.

## Tests

The standards' table, plus: `WhosWhoOracle` (every combination of permutations, its own clue reading) agrees with the
count on sampled boards; every shipped board is minimal; the tiers get harder (gate held, clue kinds and size).
