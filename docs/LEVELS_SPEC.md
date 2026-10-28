# Levels — spec

A third way to play beside Daily and Random: numbered levels, each a fixed set of five boards from different puzzles.
Finish all five and the next level opens. Built: see the two "As built" sections and "Level difficulty (deal 3)" below for where the build differs from
the plan that makes up the rest of this file.

**Why.** A player's feedback (2026-10-22): they finish the dailies, but lose interest in Random because nothing on the
home screen ticks off. Levels give the boards after the dailies something to tick off and a place in a sequence, without
inventing a reward. The idea came from the owner, after another app (Almanac) that does this.

This reopens one line of `docs/REWARDS.md` ("Not doing: points/XP/levels"). That line was about XP-style levels, a
number that goes up for activity. These levels are fixed packs of boards, so update that line in the same PR.

## Decisions

Defaults chosen in the spec; the owner can change any of them before the build starts.

| | |
|---|---|
| Name | **Levels**, the middle choice of the home toggle: `Daily · Levels · Random` (since 2026-10-28 `Daily/Random · Levels · Training`). |
| Size | **Five boards a level**, five different puzzles. With the "All levels" tile that makes two rows of three on a phone. |
| Order | **Any order within a level**, as the dailies. A level is done when all five are solved. |
| Unlocking | Level `n + 1` opens when level `n` is done. Nothing can be skipped. Finished levels can be replayed. |
| Tier | **The level sets the tier of each board** (the curve below). The home tier row is hidden in Levels mode. |
| Streak | **Levels do not count toward the streak.** The streak stays a daily habit (`core/Streak.kt` is unchanged). |
| Stats | A level solve is recorded as a practice `Completion` (`day = null`), so totals, best times and the achievements that count solves see it, exactly as a random solve. |
| Same for everyone | Level 12 is the same five boards for every player, on every device and in the browser. |
| Offline | Generated on the device from the level number, like the dailies from the date. No list of boards is shipped. |
| Random | **Stays as it is.** |

## What a level is

`core/Levels.kt`, pure, no platform types (the web compiles it):

```kotlin
data class LevelBoard(val puzzleId: String, val difficulty: Difficulty, val seed: Long)

object Levels {
    const val SIZE = 5
    /** Append-only. Its order is part of the contract (see below). */
    val POOL: List<String> = listOf("sudoku", "kings", /* ...every id in PuzzleRegistry order today... */)
    fun plan(level: Int, poolSize: Int = POOL.size): List<LevelBoard>
}
```

- **Seed:** `SeedHash.level(level, puzzleId, difficulty)`, a new function beside `daily` and `random`, mixed with its own
  constant so a level board can never be the same as a daily or random board.
- **Puzzles:** drawn from `POOL.take(poolSize)` with `Rng(SeedHash.levelPick(level))`. Five different puzzles, and no puzzle
  that was in level `n - 1` unless the pool is too small to avoid it. No hash container's iteration order may reach the `Rng`
  (CLAUDE.md, Web build).
- **Fair coverage:** over any ten levels in a row, every puzzle in the pool appears at least once. Use a shuffled deck,
  dealt five at a time and reshuffled when empty. A pure random draw lets a puzzle go missing for a long time.
- **Tiles in a level** are shown in the order drawn.

### Adding a puzzle must not change a level someone has started

A plan depends on the pool, and adding a puzzle changes the pool. So:

- `POOL` is **append-only**: a new puzzle goes on the end and nothing is removed or reordered. A test pins it against a
  copied list, the way `BoardPinTest` pins Lexicon's list.
- Each level the player opens records the `poolSize` it was opened with (see Storage). Replaying it later uses the same size,
  so the same five boards come back. Levels not yet opened use the full pool, so new puzzles appear from the next new level.
- A puzzle removed from the registry (it has never happened) would leave old plans naming it. That board shows as finished,
  the same way a saved game that names a missing puzzle already returns home (`DaybookApp`, `Route.Play`).

## Difficulty curve

The level sets each board's tier. A level has five boards, so the curve is how many of the five are Hard and Expert. Start
gentle; levels can go on for ever.

| Levels | Standard | Hard | Expert |
|---|---|---|---|
| 1–5 | 5 | 0 | 0 |
| 6–15 | 4 | 1 | 0 |
| 16–30 | 3 | 2 | 0 |
| 31–50 | 2 | 2 | 1 |
| 51–80 | 1 | 3 | 1 |
| 81+ | 0 | 3 | 2 |

Which board gets which tier is drawn with the same `Rng`. The table is a constant in `Levels.kt`, so retuning it is one
edit. It changes the plans of levels not yet opened, never one already opened, because the opened ones are stored.

**Levels 1–5 use only puzzles with a walkthrough** offered on first play, so a new player meets the hint system on easy boards.
All 24 have one today, so the rule only matters once a puzzle without one is added.

## Storage

In `ProgressStore` (`KeyValueStore.PROGRESS`). New keys only; nothing already on a phone changes shape.

- `levels` — a string set, one entry per level the player has opened: `"n|poolSize"`, or `"n|poolSize|deal"` since deal 2
  (see "Deal 2" below; an entry without a deal is deal 1).
- `level_solves` — a string set, one entry per board solved in a level: `"n|puzzleId|seconds|hints"`.
- Derived, never stored: the current level is the lowest opened level that is not done, else the highest done plus one. A level
  is done when all five of its plan's puzzle ids have a solve. Level 1 is open from the start.
- A board in progress is an ordinary saved game: `savedGameKey(puzzleId, difficulty, seed)` with the level seed. Resuming a
  level board works the way dailies do. Five open boards sit well inside `SavedGames.KEEP`.
- The web build's export/import backup (stats screen, `BackupControls` in the web platform file) must carry the two new keys;
  check what it copies. Android relies on its system backup, which takes the whole DataStore file.

## Screens

The mockup shown to the owner on 2026-10-22 is the reference.

### Home, Levels mode

- `LaunchMode` gains `LEVELS("Levels")`, between Daily and Random. The mode is still not remembered between launches
  (`LaunchOptions.kt` explains why: the app should open on the day's puzzles).
- **The tier row is hidden** in Levels mode. Reserve its space or let the header shrink, but the grid must not jump when
  switching modes. Check how the mode switch behaves today before choosing.
- **A level card** sits above the grid: "Level 14", "3 of 5", a progress bar, and one line naming the tiers ("Mostly Hard,
  one Expert").
- **The grid** shows the level's five puzzles with the usual tiles and the usual done tick (`PuzzleGrid`'s `doneToday` set
  becomes "solved in this level"). A board that is not Standard says its tier on the tile.
- **A sixth tile, "All levels",** opens the levels list.
- **When the fifth board is solved:** the finish frame says the level is done. Back on home, the card shows the next level,
  with a short "Level 14 done" line in the card's reserved space. Nothing pops up over the grid.

### Levels list (`Route.Levels`)

- Newest first: the current level (Play), then finished ones (five dots, total time), then the next one, locked ("Finish 14
  to open").
- Tapping a finished level shows its five boards (the same grid, read-only ticks) so any of them can be replayed. A replay
  that is solved again records nothing new for the level, as a re-solved daily records nothing new.
- A long list pages in tens. A `LazyColumn` is enough.

### Play

- `Route.Play` gains `level: Int? = null`. `RouteSaver` writes it as one more field, and an old saved route without it still
  reads. The seed comes from `Levels.plan(level, poolSize)` for that puzzle.
- No "New game" on a level board (as a daily: `onNewGame = null`).
- The finish frame's next steps (`NextSteps.kt`) offer **"Next in level 14"** (the first unsolved board of the level) and
  **"Home"**. Once the level is done, offer **"Level 15"**.
- **Share card:** "Daybook · Level 14 · Kings" where the daily has the date.

## Tests

- **Plan is pure and pinned:** the plans of levels 1–50 at today's pool size, written out as a golden list. They are the same
  in the JVM and the browser (add levels to `tools/web-parity/check.py`'s year of boards, as a year of levels).
- **Pool is append-only:** `POOL` starts with a pinned copy of today's list, and every registered puzzle is in it.
- **Opened levels never change:** plan `n` at an older `poolSize` is unchanged by appending to the pool.
- **Five different puzzles** in every level; tiers match the curve for levels 1–500; every puzzle appears in any ten levels
  in a row.
- **Every level board is proved:** walk 365 levels through each puzzle's `generateVerified` where one exists, as
  `FallbackTest` does for days. A level seed is just another seed, but sample it anyway rather than assume.
- **Storage:** current level derivation (fresh install, middle of a level, a level just done, replays), old saves load,
  the backup round-trips the new keys.
- **Saved games:** `SaveCompatibilityTest` is unaffected (no state class changes). If `Route.Play` or anything serialised
  changes, it must still load what a released build wrote.

## Verification

- Render home in Levels mode, the levels list and a finish frame on the web at 375x537, 390x844 and a wide window, in
  Chromium and WebKit, light and dark (`tools/render/render.py`). Then on the emulator: a phone, and a tablet in both
  orientations.
- Play levels 1–3 through on the emulator from a clean install, and check level 4 opens and the home tick behaves.

## Size and order of work

**L**: two to three days. One PR, or two if it helps review:
1. `Levels.kt`, `SeedHash.level`, storage and their tests (no UI).
2. The home mode, the list, the play route, the share card and the docs (`REWARDS.md`, `CLAUDE.md`, `TODO.md`).

## As built (part 1)

`core/Levels.kt`, `SeedHash.level` / `SeedHash.levelPick`, `LevelSolve` and `LevelProgress` in `data/ProgressStore.kt`,
tests `LevelsTest`, `LevelProgressTest`, `LevelBoardsProvedTest`, and a year of levels in the parity dump. Where it
differs from the plan above:

- **The deck is a stream of decks, each seeded on its own** (`SeedHash.levelPick(deck, ...)`), not one `Rng` per level.
  Deck 0 is the first `Levels.STARTER` (25) pool ids shuffled, which is exactly levels 1-5; decks 1, 2, ... are the whole
  pool (`POOL.take(poolSize)`), and level `n` takes stream cards `5(n-1) until 5n`. A plan builds at most three decks, so
  level 500 costs what level 6 does (1.3 us a plan on a desktop JVM, measured by `LevelsTest`); nothing is replayed from
  level 1. Where two decks meet, the first ten cards of the new deck are repaired (a card that would repeat a puzzle in its
  own level or the level before is swapped with the first later card that does not); a deck's own last ten cards are never
  moved, so no deck's repair depends on a repair before it and the plan stays pure.
- **Coverage is "any ten levels" at today's pool of 25.** Every puzzle is in every deck once, so the window is about
  `2 * poolSize / 5` levels: a bigger pool stretches it (ten levels hold only 50 boards). `LevelsTest` checks ten levels at 25
  and the stretched window at 26, 30, 40 and 60 (with made-up ids appended), plus five different puzzles and no repeat from
  the level before at every pool size from 25 to 60.
- **Levels 1-5 are the first 25 pool ids, not "puzzles with a tutorial today"**, so a plan is a function of (level, pool size)
  alone and a walkthrough added later cannot move an opened level. A test checks all 25 have a walkthrough; new puzzles never
  reach levels 1-5.
- **Tiers** come from `Rng(SeedHash.levelPick(level, ...))` shuffling the curve's multiset, in draw order. The card's line is
  `Levels.summary(boards)`: "All Standard", "Mostly Hard, one Expert", "Two Standard, two Hard, one Expert".
- **Storage:** `ProgressStore.openLevel(n)` writes `"n|poolSize"` once (now with the deal: see Deal 2) and returns the stored plan;
  `recordLevelSolve(LevelSolve): Boolean` writes `"n|puzzleId|seconds|hints"` unless that puzzle of that level was solved
  before (so a replay records nothing for the level). The caller records the practice `Completion` as well. `levelProgress`
  is a flow of `LevelProgress.of(levels, levelSolves)`, pure: `current`, `opened`, `solves`, `done`, `plan(n)`, `solved(n)`,
  `isUnlocked(n)`.
- **Deal 2, a gentle first deck (2026-10-25).** Deal 1's deck 0 shuffled the 25 starters freely, so level 1 could hand a
  newcomer Cipher, Mate, Dominoes and Cages. Deal 2 deals deck 0 in bands of `Levels.GENTLE`, a frozen copy of
  `PuzzleOrder.GENTLEST` as it stood that day (frozen as `POOL` is, so a later edit to the home order cannot move a plan):
  level `i` draws five, by one `Rng`, from the cards not yet dealt among the first 10 / 10 / 17 / 25 / 25 of that order.
  So levels 1-2 are the gentlest ten, level 3 comes from what is left of the gentlest 17, levels 4-5 take the rest, and
  deck 0 still deals each starter once. Decks 1 on are shuffled as before, but deck 1's repair reads deck 0's tail, so the
  deal can change any plan, not only 1-5 (today levels 6 on happen to agree): a plan is `(level, poolSize, deal)`.
  **The deal is per player, not per level.** Mixing them inside deck 0 (levels 1-3 under deal 1, level 4 under deal 2) would
  repeat some puzzles in levels 1-5 and skip others, and swapping a level mid-deck has no clean repair. So
  `ProgressStore.openLevel` writes the deal the player already has (`LevelProgress.deal`: the lowest stored, an entry with
  none being deal 1) and `Levels.LATEST` only when nothing is opened yet. Anyone who opened a level before this build keeps
  deal 1 everywhere; a new player gets deal 2 everywhere. Entries are still written once, so an opened level never moves.
  `openLevel` now returns the plan itself. `LevelsTest` pins 50 levels of each deal and runs every rule over both;
  `LevelProgressTest` covers the old entry, the new one and the mixed history. The parity dump uses the latest deal (deal 3 since "Level difficulty" below).
- **Backup needed no change:** the web export copies every `daybook.*` localStorage entry verbatim, so the two new keys
  travel with it.
- **Proof:** `LevelBoardsProvedTest` walks levels 1-365 (1,387 boards with a `generateVerified`) and none falls back; about
  25 s on a desktop JVM, half of it proving (Regions 4 s, Snap 2.4 s, Cages, LITS and Dominoes about 1 s each).
- **Parity:** `WebParityDumpTest` appends levels 1..N under the pseudo-puzzle `levels` (`ParityFingerprint.levelLines`), the
  page prints them for `?dump&range=N&puzzle=levels`, and `tools/web-parity/check.py` diffs it as one more page.

## As built (part 2, the screens)

`ui/home/LevelsScreen.kt` (the level strip, the level grid, the list, the pure `levelNote` / `solvedCount`),
`LaunchMode.LEVELS`, `Route.Levels(level)` and `Route.Play(level)` in `ui/DaybookApp.kt` (`encodeRoute` / `decodeRoute`,
`playLevel`, `levelFinishedBy`), `levelNextOptions` in `ui/play/NextSteps.kt`, and `shareCard(level = n)`. Test:
`LevelsUiTest`. Where it differs from the plan above (all four accepted by the owner's coordinator, 2026-10-23):

- **The tier row is kept, invisible, and the level strip is drawn over it** ("Level 14", "3 of 5", a bar; tapping it opens the
  list), so the strip has the tier row's exact height and nothing below it moves when the mode changes, stacked or side by
  side. Since 2026-10-24 (the owner) the mode row is on top and the tier row (or the strip) beneath it; side by side, mode on
  the left. The tier summary is one line above the grid.
- **"Level 13 done" shares that one line with the summary** ("Level 13 done · Mostly Hard, one Expert") rather than having a
  line of its own. It is derived: shown while the level before is done and this one has no solve, gone at the first solve.
- **The list is newest first throughout:** the next level, locked ("Finish 14 to open"), at the top, then the current one with
  Play, then the finished ones (five dots, total time). Tapping the current or a finished level shows its five boards.
- **"Level 15" on the finish frame starts that level's first board** rather than going home. On a replay of an old level the
  button is the level the player is on, not the one after the replayed level.
- **A solved level board is cleared like a practice board**, so opening it again (from home or the list) is a fresh replay;
  it records a practice `Completion` but nothing new for the level.
- **Home's arranged order (#231) does not apply to a level:** its five tiles keep the order they were dealt, and its tiles
  cannot be dragged. The "In progress" label does apply: a level board with a saved game says so, as a daily does.
- `PlayScreen` now takes the finish frame's `options` from its caller (daily, random or level) instead of `otherTiersDone`.
- Back from a level board goes home, where the mode is still Levels; back from one level's boards goes to the list.
- Web render hooks: `?mode=levels`, `?screen=levels[&level=N]`, `?puzzle=<id>&level=N`, and `&leveldone` with `?finish=`;
  `tools/render/render.py --query` passes them.

## Level difficulty (deal 3)

**Why (2026-10-26, approved by the owner).** Deal 2 dealt deck 0 in bands of the gentlest-first order, so the five
hardest starters all landed in level 5, and it treated every puzzle's Standard as the same step, though some puzzles
are simply harder than others at the same tier (the owner named Sets and Word Wheel). The owner's direction: if levels
are tuned across a spread, do it over 100 levels, not 5. Deal 3 is `Levels.LATEST` from this build; a player who opened
a level under deal 1 or 2 keeps it (the deal is per player, as before).

**The measure (no player data).** `LevelDifficultyBuildTest` (env-gated, `DAYBOOK_LEVEL_DIFFICULTY=1`, about a minute)
walks 60 boards of every puzzle and tier (the level seeds of levels 1-60) with the puzzle's own teacher, from the blank
board to the end, taking every hint, and records three things a board:

- **steps**: hints the walk took, the deductions or moves a player has to make;
- **depth**: the deepest technique it needed, as its place on the puzzle's own ladder (`techniqueNamesOf`, simplest
  first) from 0 to 1; a fallback step, or a walk the teacher loses (Lexicon, 2 and 4 of 60), counts as the top;
- **open**: the share of steps that were the fallback: nothing short explained the move, so the player chooses or
  recalls (the word games and Codebreaker; zero on every board a ladder gates).

Snap and Pointers have no teacher. Snap's steps are its squares and its depth is 1 when the whole-board rules of
`SnapDeducer` (test code) cannot finish it from blank (a what-if is needed); Pointers' steps are its arrows, depth and
open 0 (nothing is hidden; its challenge is care). A Sudoku step is not a chess move, so the numbers are not compared
as numbers: each measure becomes a percentile rank among all 93 puzzle-tiers, and their mean is the **measured** score
(0-1000). The score deal 3 uses is the mean of that and the tier itself (Standard 0, Hard 500, Expert 1000), because
each generator already measures and gates its own tiers (size, and the ladder rung a board must need), and the walk
alone misreads a puzzle whose one step is a whole search: on the walk Mate is the easiest puzzle at every tier, since a
key move is one step. So the measure moves a puzzle within its tier's band and the tiers overlap only at the edges.

What it says (of solving, not learning; see the learning cost below): the word games (Crossword, Lexicon, Codebreaker, Word Wheel), Cage Sudoku and Nonogram are the hardest
at Standard; Word Wheel's Standard (276) is harder than every ladder puzzle's but Cage Sudoku's. Sets sits
above the middle (215, 21st of 31 Standards); what makes it hard is finding a set among the cards, which a walk of
three or four steps cannot see, so it is probably still underrated. Mate, Atoms, Codeword, Tents and Who's Who are the
gentlest Standards: short walks on low rungs. Known ceilings: steps favour big boards; depth depends on how finely each
ladder is cut; open is zero for most puzzles. Player data (solve times by tier) would replace all of it.

**The deal.** `Levels.spread`, built once from `core/LevelSpread.kt` (written by the test above; never edited by hand)
and `Levels.LEARN` (a frozen copy of `PuzzleOrder.GENTLEST` on 2026-10-26, quickest to learn first; never changed, as
`GENTLE` is not), and nothing else, so a plan is still `(level, poolSize, deal)` alone.

- **Target.** Level `n` of 1-100 aims at a target rising evenly from the mean Standard of the five quickest to learn
  (221) to the mean of the five hardest Experts (882), both reachable by a real level.
- **Learning cost.** The measure says how hard a board is to solve, not how long a puzzle takes to learn: on it alone,
  level 1 was Mate, Atoms, Codeword, Tents and Who's Who, the "takes a bit" group, which `GENTLEST` exists to keep from a
  newcomer. So a puzzle not yet met may join only once its place in `LEARN` is inside a window that opens on the first 10
  and takes in all 31 by level 15, must join by level `1 + 13r / 31` (`r` its place), and is met on Standard. The term
  is strongest at level 1 and gone by level 15; after that only the measured score counts.
- **Choice.** Every puzzle not in the level before offers its tier nearest the target (Standard if not yet met). They
  queue by staleness (never met first, quickest to learn first; then the longest away, blurred by up to two levels so
  puzzles met together do not keep returning together), and the level is the five of the first 16 whose mean is nearest
  the target, each board's own distance counting 0.3 as much. A puzzle away 15 levels must come back. Integer arithmetic
  only, no hash order (web parity). Tiles show gentlest first.

Levels 1-5:

| level | boards (all Standard) |
|---|---|
| 1 | Arrows, Target, Pipes, Sudoku, Word Wheel |
| 2 | Kings, Sand, Trail, Nonogram, Lexicon |
| 3 | Target, Pipes, Sudoku, Sun & Moon, Word Wheel |
| 4 | Kings, Sand, Trail, Nonogram, Crossword |
| 5 | Sets, Target, Sudoku, Word Wheel, Lexicon |

Measured (`LevelsTest` holds each): levels 1-5 all Standard and from the 15 quickest to learn; every level's mean
within 92 of its target (10 on average); ten-level means 231, 302, 388, 454, 518, 569, 652, 730, 787, 830; every puzzle
met by level 15 and in 13 to 22 of levels 1-100, never away more than 15 levels nor in two levels running; 48 levels
mix tiers. The first 31 pool ids are ranked (`LevelSpread.RANKED`); a puzzle appended later joins deal 3 from level 101,
where deal 3 deals decks 1, 2, ... of the whole pool on the old curve (3 Hard, 2 Expert), deck 1 repaired against levels
99-100. That is a step down from level 100's five Experts, a breather rather than a cliff. Re-measuring, or ranking a new
puzzle into 1-100, is a new deal: rewriting `LevelSpread.kt` or `LEARN` would move every deal-3 plan, opened ones
included. Building the spread costs about 150 ms on a desktop JVM, once, cold; a plan after that costs what it did.

<!-- level-difficulty:start -->
Generated by `LevelDifficultyBuildTest` (60 boards a tier, the level seeds of levels 1-60); do not edit by hand.
Steps: median hints a board. Depth: mean deepest technique, 0-1 on the puzzle's ladder. Open: fallback share of steps.
Score: measured, the mean of the three percentile ranks among all 93 puzzle-tiers (0-1000), then **the score deal 3 uses**,
the mean of that and the tier (Standard 0, Hard 500, Expert 1000). Sorted by Standard's score.

| puzzle | Standard: steps / depth / open | measured, score | Hard | measured, score | Expert | measured, score |
|---|---|---|---|---|---|---|
| Mate (`chess`) | 2 / 0.50 / 0% | 194, **97** | 3 / 0.50 / 0% | 199, **350** | 4 / 0.67 / 0% | 283, **642** |
| Atoms (`atoms`) | 7 / 0.19 / 0% | 219, **110** | 15 / 0.56 / 0% | 359, **430** | 24 / 0.75 / 0% | 498, **749** |
| Codeword (`codeword`) | 12 / 0.08 / 0% | 261, **131** | 17 / 0.67 / 0% | 411, **456** | 19 / 1.00 / 0% | 598, **799** |
| Tents (`tents`) | 10 / 0.39 / 0% | 263, **132** | 15 / 0.74 / 0% | 420, **460** | 21 / 0.95 / 0% | 547, **774** |
| Who's Who (`whoswho`) | 10.5 / 0.25 / 0% | 264, **132** | 23 / 0.75 / 0% | 498, **499** | 36 / 1.00 / 0% | 681, **841** |
| Shikaku (`shikaku`) | 12 / 0.20 / 0% | 272, **136** | 18 / 0.53 / 0% | 371, **436** | 21 / 0.82 / 0% | 522, **761** |
| Arrows (`pointers`) | 14 / 0.00 / 0% | 275, **138** | 34 / 0.00 / 0% | 393, **447** | 48 / 0.00 / 0% | 435, **718** |
| Cipher (`cipher`) | 7 / 0.60 / 0% | 293, **147** | 7 / 0.80 / 0% | 370, **435** | 7 / 1.00 / 0% | 484, **742** |
| Dominoes (`dominoes`) | 7 / 0.60 / 0% | 293, **147** | 10 / 0.80 / 0% | 399, **450** | 13 / 1.00 / 0% | 543, **772** |
| Lamps (`lamps`) | 11 / 0.50 / 0% | 295, **148** | 25 / 0.73 / 0% | 500, **500** | 44 / 1.00 / 0% | 710, **855** |
| Kings (`kings`) | 14 / 0.49 / 0% | 315, **158** | 18 / 0.75 / 0% | 458, **479** | 20 / 0.81 / 0% | 504, **752** |
| Mosaic (`mosaic`) | 4 / 0.77 / 0% | 319, **160** | 5 / 0.84 / 1% | 514, **507** | 6 / 0.88 / 1% | 543, **772** |
| Tally (`tally`) | 14 / 0.51 / 0% | 333, **167** | 21 / 0.80 / 0% | 502, **501** | 32.5 / 1.00 / 0% | 667, **834** |
| Mines (`mines`) | 20.5 / 0.17 / 0% | 341, **171** | 34 / 0.42 / 0% | 426, **463** | 43.5 / 0.85 / 0% | 623, **812** |
| Inequality (`inequality`) | 16 / 0.59 / 0% | 373, **187** | 25 / 0.83 / 0% | 558, **529** | 36 / 1.00 / 0% | 681, **841** |
| LITS (`lits`) | 15 / 0.60 / 0% | 375, **188** | 22 / 0.70 / 0% | 471, **486** | 29 / 0.87 / 0% | 583, **792** |
| Regions (`regions`) | 19 / 0.53 / 0% | 384, **192** | 40 / 1.00 / 0% | 697, **599** | 68 / 1.00 / 0% | 739, **870** |
| Pearls (`pearls`) | 19 / 0.58 / 0% | 395, **198** | 40 / 0.86 / 0% | 618, **559** | 71.5 / 1.00 / 0% | 743, **872** |
| Sand (`sand`) | 17 / 0.67 / 0% | 411, **206** | 23 / 0.67 / 0% | 460, **480** | 26 / 0.67 / 0% | 487, **744** |
| Trail (`snap`) | 25 / 0.50 / 0% | 418, **209** | 36 / 0.55 / 0% | 471, **486** | 42 / 0.75 / 0% | 572, **786** |
| Sets (`sets`) | 3 / 1.00 / 0% | 429, **215** | 4 / 1.00 / 0% | 442, **471** | 6 / 1.00 / 0% | 467, **734** |
| Target (`target`) | 4 / 1.00 / 0% | 442, **221** | 4 / 1.00 / 0% | 442, **471** | 5 / 1.00 / 0% | 457, **729** |
| Pipes (`pipes`) | 24 / 0.62 / 0% | 455, **228** | 36.5 / 0.69 / 0% | 533, **517** | 60 / 0.74 / 0% | 591, **796** |
| Sudoku (`sudoku`) | 53 / 0.27 / 0% | 467, **234** | 53 / 0.43 / 0% | 478, **489** | 53 / 0.81 / 0% | 616, **808** |
| Sun & Moon (`mambo`) | 30.5 / 0.60 / 0% | 473, **237** | 56 / 0.80 / 0% | 614, **557** | 87 / 1.00 / 0% | 757, **879** |
| Nonogram (`nonogram`) | 13 / 1.00 / 0% | 543, **272** | 37 / 1.00 / 0% | 692, **596** | 80.5 / 1.00 / 0% | 750, **875** |
| Word Wheel (`wordwheel`) | 5 / 0.97 / 31% | 551, **276** | 6 / 0.98 / 37% | 589, **545** | 8.5 / 1.00 / 30% | 659, **830** |
| Cage Sudoku (`cages`) | 67 / 0.63 / 0% | 558, **279** | 73 / 0.82 / 0% | 649, **575** | 81 / 0.99 / 0% | 699, **850** |
| Lexicon (`words`) | 7 / 0.98 / 34% | 591, **296** | 8 / 1.00 / 34% (lost 2) | 668, **584** | 8 / 1.00 / 36% (lost 4) | 672, **836** |
| Codebreaker (`tower`) | 10 / 1.00 / 29% | 665, **333** | 10 / 1.00 / 33% | 676, **588** | 12 / 1.00 / 36% | 710, **855** |
| Crossword (`mini`) | 13 / 1.00 / 45% | 728, **364** | 17 / 1.00 / 54% | 768, **634** | 24.5 / 1.00 / 61% | 833, **917** |
<!-- level-difficulty:end -->

## Open questions for the owner

Both defaults were taken: no achievements for levels in the first build, and a level's share card shows its hints as every
other card does, never as a penalty.
