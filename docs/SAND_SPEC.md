# Sand (colour sort) — spec

A daily **colour-sort** puzzle: tubes of layered coloured sand, pour from tube to tube until every tube holds one
colour. The water-sort / ball-sort family, drawn as sand. Read `docs/PUZZLE_STANDARDS.md` first; this file records
only what is specific to this puzzle and where it departs from the recipe. Where the two disagree on a point not
listed here, the standards win.

## Decisions (made; do not reopen without the owner)

| | |
|---|---|
| `id` | `"sand"` (hashed into every seed: never rename). Display name **Sand** (changeable later), tagline along the lines of "Pour every colour into a tube of its own". Not "Sand Sort" or any shop title in player-facing text. |
| Rules | Standard water-sort rules (below). No hidden layers, no move limit, no timer pressure beyond the usual clock. |
| Answer | **Several answers by policy** (as Target): any sequence of legal pours that sorts the board wins. Nothing to prove unique; what must be proved is that the board **can** be sorted. |
| Proof | A found solution is the proof (a witness). The search is budgeted, and its verdict is structural: `Solvable(path)`, `Unsolvable` (exhaustive, budget not reached), `Truncated`. Only `Solvable` may ship; only `Unsolvable` may call a pour a mistake. |
| Spare tubes | **Two empty tubes on every tier.** Spare tubes are slack, and tiers never vary slack (standards section 3). |
| Input | Tap a tube to lift it, tap another to pour. Tap the lifted tube again to put it down. The lifted tube is `remember` state, never `PuzzleState`. One pour = one state = one undo. |
| No hard refusals beyond the rules | An illegal pour (onto a different colour, into a full tube) is refused with a short shake of the target, nothing else. A legal pour that makes the board unsortable is allowed; it is a *mistake* the teacher flags, not a refusal (the owner's "bad judgements allowed"). |

## Rules

- `N = C + 2` tubes, each holding up to `H` layers. `C` colours, each exactly `H` layers. The two spare tubes start empty.
- A **pour** from A to B: A is not empty, B is not full, and B is empty or B's top colour equals A's top colour. It moves
  the whole run of A's top colour, or as much of it as fits in B. Pouring a tube onto itself is not a pour.
- **Solved:** every tube is empty or holds `H` layers of one colour. Checked by the rule, never against a stored path.
- **Stuck** (derived, never stored): not solved and no legal pour exists. Shown as a reserved caption ("No pours left:
  undo a few"); the board never moves for it. This is the only unsortable state shown without asking for a hint.

## State

```kotlin
@Serializable data class SandState(
    val tubes: List<List<Int>>,   // bottom to top, colour indices 0 until colours
    val height: Int,              // H
    val colours: Int,             // C
    override val moves: Int = 0,
) : PuzzleState
```

Nothing else is stored: no solution path, no par. The lifted tube and the pour animation are UI state. Keep the field
names and meanings fixed once shipped (saves).

## Logic API (`puzzles/SandRules.kt`, object `SandLogic`)

Pure Kotlin, no `java.*`. The independent oracle in `test/` is written against these, so keep the names:

- `canPour(tubes, height, from, to): Boolean`, `pour(tubes, height, from, to): List<List<Int>>` (precondition: `canPour`).
- `isSolved(tubes, height): Boolean`, `isStuck(tubes, height): Boolean`.
- `solve(tubes, height, budget): Verdict`, `Verdict` = `Solvable(path: List<Pair<Int, Int>>)` / `Unsolvable` / `Truncated`.
  Depth-first with a visited set of **canonical** positions (tubes sorted, since tubes are interchangeable). Membership
  only: the visited set's iteration order must never reach a choice. Moves are tried in a fixed order by tube index
  with the pruning below. The verdict says which case it is; never return a count the caller could misread.
- Pruning that keeps the search exact: never pour a single-colour tube into an empty tube; when two empty tubes would
  receive the same pour, try only the first; never pour back a pour just made (it restores the previous position, which
  the visited set already covers).
- `generateVerified(seed, difficulty): SandState?` and `lastResort(difficulty)`, `generate = generateVerified ?: lastResort`.

## Generation

1. `Rng(seed + attempt)`: lay out `C x H` layers (each colour `H` times), shuffle with the `Rng` (Fisher-Yates, list
   only), deal into `C` full tubes, add two empty tubes.
2. Reject a deal that gives the board away: any tube already holding one colour, or any run of the same colour longer
   than `H - 2` in one tube (`H - 1` would leave only one layer to move). Measure how often this rejects; if the rate is
   high, deal smarter rather than loosening it.
3. `solve` within the tier's budget. Ship only `Solvable`. `Unsolvable` and `Truncated` redraw. Measure the rates.
4. `lastResort(difficulty)` is one fixed board per tier, itself proved `Solvable` by a test. `FallbackTest` walks a year
   of daily seeds on every tier through `generateVerified` and asserts it never gives up.
5. The difficulty filter (below) rejects boards too easy for their tier, after attempts that already succeeded keep their
   layout (standards section 4, "When a fix changes generation").

## Tiers

| Tier | Colours `C` | Height `H` | Tubes |
|---|---|---|---|
| Standard | 5 | 4 | 7 |
| Hard | 7 | 4 | 9 |
| Expert | 8 | 4 | 10 |

Provisional; settle by measurement and record the result in `docs/PUZZLE_STANDARDS.md` section 3. Two players, as
`LexiconBalanceTest`: a **naive** player (each turn picks uniformly among legal pours that are not an immediate undo,
seeded) and a **careful** player (prefers pours that complete a tube or uncover a colour that has a home). Over a year
of daily boards, the naive player's win rate within a move cap must fall Standard > Hard > Expert, and the solver's node
count must rise. If 8x4 is not clearly harder than 7x4, Expert moves to height 5 (8x5, 10 tubes) before anything else;
it may not drop a spare tube. A `the tiers get harder in the order they are offered` test pins whatever is chosen.

A difficulty filter per tier (e.g. a floor on the naive player's failure rate per board, or on how many first pours lead
to `Unsolvable`) is allowed if the year measurement shows boards that are trivial for their tier. Measure first.

## Teaching (`puzzles/SandTeacher.kt`)

There is no hidden answer: everything is visible, so the solver may look at all of it, and there is **no fallback** in
the usual sense. What can happen is a `Truncated` search; measure how often per tier from player-like positions (random
legal play from daily boards), and say so in the hint ("I can't see a way through from here quickly; try undoing to …")
rather than guessing. Hint search runs off the main thread (Chess's pattern), with a time ceiling measured on the
emulator and the web.

- **Mistake** (outranks steps): the last pour left the board `Unsolvable`, proved exhaustively. Name the pour and offer
  "Show me" = undo back to the last sortable position. `Truncated` is never a mistake.
- **Stuck:** same as a mistake, from the position where pours ran out.
- **Steps**, in ladder order, each the first pour of a `Solvable` path from the current position (so a hint is always
  on a real road):
  1. *Finish a tube*: a pour that completes a colour.
  2. *Gather*: pour onto a matching colour, preferring the pour that uncovers a colour with somewhere to go.
  3. *Use a spare*: pour into an empty tube to free a buried colour; say which colour it frees.
  4. *Plan ahead*: anything else on the solver's path, explained as "this clears the way for …".
- Tap 1 nudges (highlights the source tube), tap 2 explains, the player makes the pour, the panel confirms when it is
  seen. "Show me" applies it. One hint counted per deduction opened.
- Highlight indices: tubes `0 until N`. Report bounds through `reportHighlight` / `highlightGrid` so the popover and
  the wide-window side panel both place correctly.
- `SandTeachingTest`: every hint step keeps the board `Solvable` (checked by the oracle, not the teacher's solver);
  following hints from any daily board solves it; a mistake is flagged only where the oracle proves unsortable; text
  fits the panel. Add the measured `Truncated` rate to the table in `docs/TEACHING.md`.

## Board UI (`puzzles/Sand.kt`)

- Tubes in one row when they fit, else two rows (Standard 7 likely one row on a phone, Hard and Expert two). Size from
  both axes: tube width and height from `minOf` of what the box allows. Check 375x537 and a tall phone, a tablet in
  both orientations, and the wide-window layout from the tablet work (#188).
- **Sand, not water:** each layer is a flat band with a slightly uneven top and a fixed grain texture. The texture is a
  fixed pattern derived from the tube and layer index, never random per frame and never stored.
- **Colours:** eight colours exceed the five-hue limit, so follow `docs/COLOUR.md` "More than five groups: steps, not
  new hues", ordered by CIEDE2000 including deuteranopia and protanopia (`SandPaletteTest`, as `TowerPaletteTest`), and
  put a small **symbol** on every layer (colour is never the only signal). Same colours in both schemes.
- **Pour:** a short animation (sand stream from source to target, ~250 ms). The state changes at once; the animation is
  decoration and must not block the next tap or the undo.
- Lifted tube rises a little; a refused pour gives the target a small shake. Neither moves the board's box.
- Solved: each full tube gets a small flourish (a cap or a settled top). Reuse the shared finish frame.
- Keyboard (`SandKeys.kt`, as `TargetKeys.kt`): `1`-`9`, `0` pick tube 1-10 (first press lifts, second pours), Escape
  puts it down. Pure and tested.

## Walkthrough

A hand-built tiny board: 3 colours, height 3, 5 tubes. Frames: lift and pour onto a match; pour into a spare to free a
colour; finish a tube; then free play the hints can finish. Each frame waits for the real tap and rejects a near miss.

## Preview

Fixed motif: four or five tubes of sand mid-sort, one finished. Never calls `generate`. Render at true size, both themes.

## Tests (beyond the standards' table)

- `SandRulesTest`: each pour rule alone refuses; partial pours when the target fills; solved by rule on a sorted board
  in any tube order; stuck detection.
- `SandOracle` (test): breadth-first over raw positions (no canonical sort, no pruning, no budget on small boards), so it
  shares nothing with `solve`. Differential: on small random boards (3-5 colours, height 3-4), `solve` returns
  `Solvable` exactly when the oracle finds a solution, its path replays to solved, and `Unsolvable` only when the
  oracle exhausts the space.
- Year x tier through `generateVerified` (`FallbackTest`), the balance test above, parity pins, serialization `mutate`
  branch, `PuzzleNotes` entries, generation time inside `GeneratorTest`'s budget and measured on the web for Expert.

## Out of scope

Hidden ("mystery") layers, move limits, extra tubes as a purchasable help, physics simulation of grains.
