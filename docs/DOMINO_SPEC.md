# Dominoes (domino placement on a region board) — spec

A daily **domino placement** puzzle: an irregular board of squares, some grouped into coloured regions that each carry a
rule, and a tray of dominoes. Lay every domino on two neighbouring squares so that every region's rule holds. Read
`docs/PUZZLE_STANDARDS.md` first; this file records only what is specific to this puzzle and where it departs from the
recipe. Where the two disagree on a point not listed here, the standards win.

## Decisions (made; do not reopen without the owner)

| | |
|---|---|
| `id` | `"dominoes"` (hashed into every seed: never rename). Display name **Dominoes** (changeable later). Tagline along the lines of "Lay every domino so each region keeps its rule". The genre is published by the New York Times as *Pips*: that name, and any NYT wording or art, never appears anywhere the player sees (Cage Sudoku / Target precedent). |
| Rules | Below. No timer pressure beyond the usual clock, no move limit, no lives. |
| Answer | **Exactly one placement** (which domino on which two squares, which half where), proved exhaustively. `solved` still checks the rules, never the stored answer. |
| Tray | Dominoes are **distinct**, drawn from the double-six set (28 dominoes, pips 0..6). "The tray has one double six" is a deduction the player may use. |
| Input | **Tap model, no drag from the tray** (height is scarce at 375x537, and a drag is many states). Below. One placement = one state = one undo; lifting a domino is one state. |
| Bad judgements allowed | Any placement on two empty neighbouring squares is accepted, even one that breaks a rule. Rule breaks are shown (debounced), never refused. |

## Rules

- The board is a set of squares inside a `w x h` box (an irregular shape, 4-connected, even square count).
- **Regions** partition *some* squares. A region is 4-connected, 1 to 6 squares. Squares in no region have no rule.
- Each region has exactly one rule:
  - `SUM n` — its pips total exactly n (a one-square region shows just the number).
  - `LESS n` — total < n. `MORE n` — total > n.
  - `EQUAL` — every square shows the same pip count.
  - `DIFFERENT` — no two squares show the same pip count.
- The tray holds `d` distinct dominoes; the board has exactly `2d` squares. A domino covers two orthogonally adjacent
  empty squares, either way round. A domino may straddle regions; regions say nothing about domino boundaries.
- **Solved:** every domino is on the board (so every square is covered) and every region's rule holds.

## State (`puzzles/DominoRules.kt`)

```kotlin
@Serializable data class Domino(val a: Int, val b: Int)            // a <= b, 0..6
@Serializable enum class RuleKind { SUM, LESS, MORE, EQUAL, DIFFERENT }
@Serializable data class RegionRule(val kind: RuleKind, val n: Int = 0)
@Serializable data class Placement(val first: Int, val second: Int) // squares; `first` shows domino.a, `second` shows domino.b

@Serializable data class DominoState(
    val width: Int,
    val height: Int,
    val open: List<Boolean>,          // w*h, true where the board has a square
    val regionOf: List<Int>,          // w*h, region index or -1
    val rules: List<RegionRule>,      // per region
    val tray: List<Domino>,           // fixed order (sorted), the tray's slots never move
    val placed: List<Placement?>,     // per tray index; null = still in the tray
    val answer: List<Placement>,      // per tray index; for mistakes, the fallback and tests only
    override val moves: Int = 0,
) : PuzzleState
```

The selected tray domino, its flip (which half leads), the first tapped square and the cursor are `remember` /
`rememberSaveable` state in the board, never `PuzzleState`. A placed domino's flip *is* state (it is `Placement`).
Squares are indexed `row * width + col`. Keep the field names and meanings fixed once shipped (saves).

## Logic API (object `DominoLogic` in `DominoRules.kt`)

Pure Kotlin, no `java.*`, no hash iteration order reaching the `Rng` or any choice.

- `pipsOf(state): IntArray` (per square, -1 uncovered), `canPlace(state, i, p): Boolean`, `place(state, i, p)`, `lift(state, i)`.
- `ruleHolds(rule, pips): Boolean` for a fully covered region; `ruleBroken(rule, pips, open): Boolean` — no completion of
  the region's uncovered squares with values 0..6 can satisfy it (the red feedback; ignores the tray, cheap, honest).
- `isSolved(state)`: the one rule function the validator, the feedback and the generator's last check all call.
- `solutions(shape, regions, rules, tray, limit = 2): Verdict` — exhaustive, **no budget**:
  `Verdict.None` / `Verdict.ExactlyOne(placements)` / `Verdict.Several`. Only `ExactlyOne` carries an answer. Pick the most
  constrained empty square (fewest legal partner-and-domino options), branch over its options, prune with every region's
  bounds (partial sums against SUM/LESS/MORE using min/max of the remaining tray values, EQUAL and DIFFERENT on the
  covered squares). If profiling demands a node cap, it must surface as a separate `Truncated` that never ships.
- `generateVerified(seed, difficulty): DominoState?`, `lastResort(difficulty)`, `generate = generateVerified ?: lastResort`.

## Generation

1. **Shape and tiling together.** Grow a 4-connected shape inside the tier's box by adding one domino at a time on
   empty squares beside the shape (`Rng(seed + attempt)`), until it has the tier's domino count. The tiling that grew
   it is the answer's tiling. 2x2 blocks are allowed (they are where tiling ambiguity lives; the regions resolve it).
2. **Pips.** Draw `d` distinct dominoes from the double-six set, assign each to a tile, each way round at random.
3. **Regions.** Cut the shape into connected regions of 1-6 squares (grow from the first uncut square in reading order,
   tier-sized), leave a tier-dependent share of squares in no region. Read each region's strongest rule off the answer:
   `EQUAL` if all equal (and >1 square), `DIFFERENT` if all differ and the tier allows it, else `SUM`.
4. **Force, then take back** (the Snap lesson: forcing is not forcing minimally). If `solutions` says `Several`, tighten
   (split a region or turn an unruled square into a one-square `SUM`) and retry, up to a cap. Then, in random order, try
   each weakening — a `SUM n` becomes `LESS n+1`/`MORE n-1` (where true), a rule becomes no rule (region dissolved), two
   neighbouring regions merge — and keep it while the board is still `ExactlyOne` **and the teacher's ladder finishes it**.
5. **Tier filter.** The board must need the tier's deepest ladder step at least once (Tally's `depth` / `Spec.needs`);
   a draw needing more or less is redrawn.
6. Up to `ATTEMPTS` draws; none proved → `null`. `lastResort` is one fixed board per tier, itself proved by a test.

## Tiers (targets; measure and record the real numbers)

| | Standard | Hard | Expert |
|---|---|---|---|
| Box | 5x5 | 6x6 | 7x7 |
| Dominoes | 6-7 | 9-10 | 12-14 |
| Rule kinds | SUM, EQUAL, LESS, MORE (no DIFFERENT) | + DIFFERENT | all |
| Ladder must need | a region-value step (and nothing deeper) | a counting step (and no what-if) | a what-if |

Slack never varies (there is none). `DominoRulesTest` needs a test that fails if a harder tier gets easier
(the deepest step needed and the number of steps, over a year of daily boards).

## Teacher (`puzzles/DominoTeacher.kt`)

**Every hint ends in a placement**: "these two squares are the 6-3, the 6 here". There are no pip notes, so a hint may
never ask the player to remember an elimination it has not turned into a domino (Cipher's rule). `deduce(visible)` takes
only visible state (shape, regions, rules, tray, placements); the answer is read only for mistakes and the fallback.

Internally the ladder keeps, per empty square, candidate pip values and candidate partners, and per tray domino its
candidate placements, cut to a fixpoint by named steps. A step is *emitted* only when it fixes one domino's placement;
its explanation names the facts it leaned on, in order, cheapest first. Ladder, in order of cost:

1. `ONLY_PARTNER` — a square with one empty neighbour must pair with it (geometry), plus whatever names the domino.
2. `ONLY_PLACE` — a tray domino has one place left on the board.
3. `ONLY_DOMINO` — a pair of squares known to pair has one tray domino that fits their rules.
4. `REGION_VALUE` — a region rule fixes values (a one-square SUM; a SUM of 2 squares needing 12 is two 6s; LESS 2 over
   two squares is 0 and 1 or 0 and 0; an EQUAL region sharing a square with a known value), combined with the tray (the
   values the remaining dominoes can still supply, counted: there is one 6-6).
5. `COUNTING` — a pip value needed more times than the remaining dominoes hold, or a value only one domino still has.
6. `WHAT_IF` — suppose one of a square's two (at most three) options, propagate steps 1-5 a bounded depth, reach a
   contradiction; the other option is the step (Tally's `firstWhatIf`, named as such).

**The generator's ladder is the teacher's**: a board ships only if this ladder places every domino. Raise the generator
and the teacher must learn the new step. Hence, as for Tally and Cage, **hints never fall back on a generated board**;
the fallback (point at a domino of the stored answer, `fallback = true`) exists only for boards not made here.

**Mistake:** a placed domino that differs from the stored answer (domino, squares or way round), sound because the
answer is proved unique. "Show me" lifts it.

Highlight indices: squares `0 until w*h`, tray slot `i` is `TRAY + i` (`TRAY = 1000`), region rule badge `RULE + r`
(`RULE = 2000`).

## Measured (logic built; JVM, a year of daily seeds per tier unless stated)

From `DominoRulesTest` `a year of daily boards ...` (`app/build/reports/dominoes-generation.txt`), `FallbackTest`
`dominoes proves every daily board of a year on every tier` and `DominoTeachingTest` `coverage ...`
(`app/build/reports/dominoes-teaching-coverage.txt`, 200 boards a tier).

| | Standard | Hard | Expert |
|---|---|---|---|
| Box, dominoes | 5x5, 6-7 | 6x6, 9-10 | 7x7, 12-14 |
| Draws per board (mean / most of `ATTEMPTS` = 200) | 1.9 / 7 | 5.6 / 29 | 3.4 / 21 |
| Days reaching `lastResort` | 0 | 0 | 0 |
| Ladder level needed (every board) | region value (4), all 365 | counting (5), all 365 | what-if (6), all 365 |
| Squares in a ruled region | 71% | 74% | 79% |
| Squares whose value a one-square sum states outright | 9% | 12% | 15% |
| Rules that are exact sums | 44% | 23% | 28% |
| Rules a board (sum / < / > / = / ≠, a year) | 6.4 (1035 / 608 / 605 / 106 / 0) | 11.7 (974 / 1467 / 1520 / 214 / 103) | 17.1 (1720 / 1950 / 2057 / 396 / 109) |
| Hint steps a board | 6.5 | 9.5 | 13.1 |
| of which partner / place / domino | 3.1 / 0.4 / 1.5 | 3.7 / 0.5 / 2.7 | 4.2 / 0.8 / 3.6 |
| of which region value / counting / what-if | 1.6 / 0 / 0 | 0.6 / 2.0 / 0 | 0.9 / 1.3 / 2.3 |
| Generation ms, median / p95 / max | 0.5 / 1.2 / 1.9 | 3.3 / 10.5 / 22.7 | 14.0 / 33.0 / 53.8 |

- **Oracle agreement:** `DominoOracle` agrees with `solutions` on 40 / 40 / 12 generated boards (one answer, the
  stored one; the oracle takes about 0.2 s an Expert board, which is why the sample thins) and on 5,774 small planted
  boards: 1,842 with no answer, 83 with one, 3,849 with several. `lastResort` is the board of seed 20261022 per tier,
  proved by the oracle in a test.
- **Hints:** hints alone finish every daily board of a year on every tier with no fallback and no mistake, and from
  over 500 part-played boards; on 582 steps from small boards with several answers (1,495 of the sample ambiguous) every
  step holds for every answer still possible. Longest explanation 200 characters (the cap); nudges 70.
- **Standard was changed after measuring, to give less away.** With placement steps only and no bounds it stated 39%
  of its squares outright through one-square sums: the ladder had nothing else to lean on. The owner's stance is that
  sparser beats friendlier (CLAUDE.md, Snap), so Standard now needs a region-value step on every board and may use
  < and >; that took it to 9%. Hard now needs a counting step on every board (before, about half its boards needed only
  region values), so the tiers stay strictly ascending. `DominoRulesTest` holds every tier under 20%.
- **≠ is rare** (0.3 a board on Hard and Expert even with all-different regions read as ≠): it is the weakest rule,
  so loosening keeps it only where nothing weaker would do.
- The web runs generation single-threaded; Expert has not yet been timed in a browser (the UI agent's `?dump&times` check).

### Where the build departs from the text above, and why

- **Tightening, then loosening, as measured.** A draw is rejected at once when the ladder cannot finish it even with
  every square given (two tilings both made of tray dominoes): before this, every rejected draw cost a full tightening
  walk to find out. Tightening adds one fact at a time (doubling batches was tried: the overshoot left Hard boards too
  easy for their tier and draws rose sixfold) and is checked against the ladder without the what-if (level 5); loosening
  then runs at the tier's full ladder.
- **Loosening order:** each region once, in random order, trying its strongest loosening first (drop the rule, then a
  merge with a neighbour, then a bound), keeping the first the ladder still finishes. One pass in fully random order
  over every loosening left more of the board ruled (Expert 82% against 79%) at the same cost; a second such pass
  doubled the time and gained under a point. Loosenings also
  include a sum to ≠ where the values all differ (the spec listed only bounds, drops and merges).
- **Uniqueness during loosening** is not re-proved per trial: a ladder that places every domino by sound strikes has
  proved the answer unique. `solutions` proves the finished board once, and has never disagreed.
- **Tiers by ladder level:** Standard needs region values (4) and nothing deeper, Hard counting (5), Expert a what-if (6);
  each tier is capped at its level while loosening and redraws a board that needs less.
- **Ladder details:** counting is "as many squares that can take only v as halves of v left in the tray, so no other
  square gets a v" (covers "only one domino still has a 6"). The what-if tries squares only (two candidates before
  three), follows two rounds of steps 1-5, and is emitted only when every candidate but one breaks, so it always ends in a
  placement. Restricting it to two-candidate squares raised Expert draws by 40%; three rounds cost more for nothing.
  The step names `only-partner` / `only-place` / `only-domino` are all placement-level (3); which one a hint gets
  follows how the placement settled.
- `ruleBroken(rule, pips, open)` takes the covered squares' pips and the count of uncovered squares. Added for the
  board: `DominoLogic.flip`, `dominoAt`, `regionHolds`, `regionBroken`.

## Board UI (`puzzles/Dominoes.kt`)

- **Layout:** the board above, the tray below in a fixed grid sized for the tier's tray (at most 7 slots a row); a
  placed domino leaves its slot empty but reserved, so nothing ever moves. Size from both axes (`minOf`), check at
  375x537 and 390x844, phone landscape and tablet. The side-panel layout (`PlayLayout`) applies as for every board.
- **Input:** tap a tray domino to select it (tap again to flip which half leads; the tray shows it as it will go).
  Tap an empty square, then an empty neighbour: the domino is laid with its leading half on the first square
  (one state). A drag from a square to its neighbour does the same as the two taps. Tapping a non-neighbour moves the
  first square there. Tap a placed domino to lift it back to its slot and select it (one state). Long-press or double-tap
  on a placed domino flips it in place (one state). No domino selected and a square tapped: select the first tray
  domino still in the tray. Gestures read the board through a cell updated on every emit (Tents' pattern; Snap's lesson).
- **Keys** (`DominoKeys.kt`, pure and tested like `SudokuKeys`): arrows move a cursor over squares; `[`/`]` or Tab
  cycle the tray selection; `F` or Space flips; Enter marks the first square, Enter on a neighbour lays it;
  Backspace/Delete lifts the domino under the cursor.
- **Drawing:** dominoes as rounded rectangles with a centre line and real pip layouts (0..6, as on a domino), sized to
  the square. Regions as tinted squares with a thick outline, hues per `docs/COLOUR.md` (derived from the region graph,
  never stored); the rule as a badge on the region's top-right square: the number, `=`, `≠`, `<n`, `>n` — draw `≠`,
  `<`, `>` as paths if Fredoka lacks the glyph (check by rendering). Unruled squares plain.
- **Feedback** (derived, 1 s debounce held in `remember`): a badge turns green when its region is fully covered and
  holds, red (`scheme.error`, solid) when `ruleBroken`. Never moves the board.
- **Preview:** a fixed motif (a few squares, two regions with badges, two dominoes with pips). Never calls `generate`.
- **Walkthrough:** a hand-built tiny board (3-4 dominoes): select and lay a domino; a one-square SUM; an EQUAL region;
  lift and re-lay; a free-play frame the hints finish. As built: the player lays the 5-2 unturned, so its 5 lands on the
  2 (their own mistake), turns it round where it lies (double tap or long press), and lays the 1-0 by a drag.
- **Share card:** the standard grid for the play log; nothing board-specific unless it reads better.

## Tests (section 10 of the standards, plus)

- `DominoRulesTest`: rules by hand-built cases (each kind, straddling dominoes, a valid non-stored answer accepted when
  one exists on a hand-built several-answer board); a year of daily seeds per tier through `generateVerified` (no null,
  `lastResort` proved); the tiers-get-harder test; generation time median / p95 / max per tier.
- `DominoOracle` (test): independent solver on a different principle — fill squares in reading order, for each the
  partner to its right or below, try every unused domino both ways, check rules only on finished regions, no pruning,
  no most-constrained choice. Agrees with `solutions` (count, capped at 2) on sampled boards per tier and on hand-built
  boards with 0, 1 and 2+ answers.
- `DominoTeachingTest`: walk hints from 200 boards a tier and a year of daily boards; assert no fallback, every step
  sound (its placement is the answer's — the answer is unique), steps counted per technique for `docs/TEACHING.md`.
  Mistake flagged for a wrong domino, a right domino flipped, a right domino on wrong squares.
- `DominoKeysTest`, `StateSerializationTest.mutate` branch, `FallbackTest` entry, `ParityFingerprint` branch and a
  `DominoWebParityTest`-style pin, `GeneratorTest` (automatic).
