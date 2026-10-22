# Levels — spec

A third way to play beside Daily and Random: numbered levels, each a fixed set of five boards from different puzzles.
Finish all five and the next level opens. Not built yet; this file is the plan.

**Why.** A player's feedback (2026-10-22): they finish the dailies, but lose interest in Random because nothing on the
home screen ticks off. Levels give the boards after the dailies something to tick off and a place in a sequence, without
inventing a reward. The idea came from the owner, after another app (Almanac) that does this.

This reopens one line of `docs/REWARDS.md` ("Not doing: points/XP/levels"). That line was about XP-style levels, a
number that goes up for activity. These levels are fixed packs of boards, so update that line in the same PR.

## Decisions

Defaults chosen in the spec; the owner can change any of them before the build starts.

| | |
|---|---|
| Name | **Levels**, the middle choice of the home toggle: `Daily · Levels · Random`. |
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
  copied list, the way `LexiconWebParityTest` pins its list.
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

- `levels` — a string set, one entry per level the player has opened: `"n|poolSize"`.
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

## Open questions for the owner

- Does finishing a level earn an achievement (level 10, 50, 100)? It would be cheap, as the achievements are derived.
  Default: not in the first build.
- Hints are free everywhere else. Should a level show its hint count, as the share card does? Default: yes, on the card,
  never as a penalty.
