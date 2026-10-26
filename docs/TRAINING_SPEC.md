# Training (Sudoku pilot)

The owner (2026-10-26): "Have a 'Training' section, where there are tutorials for each technique, and the player can
play examples of them to get a dopamine hit for 'passing the test', potentially getting an achieve per game when passing
all of the techniques, then completing a game that requires all of the techniques."

This replaces the earlier decline of a per-technique practice mode (`docs/TODO.md`): the owner now wants it. The pilot is
Sudoku alone, small enough to judge before any other puzzle gets one.

## What the player sees

- **Training** (route `training`), opened from a row in Settings. A list of Sudoku's techniques in the teacher's ladder
  order (`core/TechniqueNames.kt`), each with its name and a tick once passed, and at the foot the final board.
- **A technique** (route `training|<technique>`): a short explanation in the hints' voice, then a practice board. The
  player finds the step and makes it. A right digit adds one to the run; three in a row passes the technique (a tick,
  the chime, "Passed"). A wrong digit ends the run and shows the reasoning that was there to find. The next board is one
  tap away. Hints work as in a game (nudge, why, show me); a board where a hint was opened does not count towards the
  run and does not break it.
- **The final board** opens once every technique is passed: a whole Expert Sudoku whose own walk by the hint ladder needs
  every technique. Solving it without "Show me" earns the second achievement. Hints are allowed (they teach).
- **"Practise this"** in a Sudoku hint's panel, beside Close, once the hint is explaining (not a mistake, not the
  fallback): opens that technique's practice; back returns to the game, which is saved as on any leave.
- **Achievements** (appended): "Sudoku trained" for passing every technique, "Sudoku graduate" for then solving the
  final board. They are announced on the training screen as they are earned and shown on the Achievements page.

## How it works

- `core/Training.kt` (`SudokuTraining`): the technique list (`TechniqueNames` order), the explanations, and the search.
  A practice is a real position: per seed from a range of its own (`SEED_BASE`), generate the board of the easiest tier
  whose walks reach the technique (Standard for singles in a unit, Hard for the naked single, Expert above), walk it with
  `SudokuTeacher.deduce` from its givens, and stop at the first position where the next step is the technique. The walk's
  digits become givens, so they cannot be erased. Pure, seed order only, no hash order: the same index is the same board on
  every platform.
- **What counts as right:** the step's own digit, which is what the hint's `isReached` checks. Another correct digit
  elsewhere also counts: the position has no simpler step anywhere (the teacher tries every simpler technique on the whole
  board first), so a correct digit there was found with this technique or a harder one. A wrong digit is judged against
  the stored answer, sound because a generated board has one answer.
- **Stored:** `training_passed`, its own key set in the progress store (`puzzleId|technique`, `sudoku|exam`), never on a
  save and never a `Completion`, so training touches no streak, total or best time. An older store has none.
- **Achievements** stay predicates: `Achievement.trained` reads the training set, beside `earned` over completions.
  `earnedAchievements` takes the set (default empty); `newlyTrained` says what a pass earns.
- The run (right answers in a row) is not stored: leaving a technique starts its run again.

## Measured

- Search cost (desktop JVM, 30 practices each, all distinct boards): singles 40-160 ms, locked candidates 330 ms, what-if
  330 ms, naked pair 930 ms, hidden pair 1.0 s. The practice for index n re-walks the seeds before it (a `ponytail:` note
  in `Training.kt`); cache it if players run past a few dozen boards.
- Neighbouring seeds share grids (`generateVerified` tries `Rng(seed + attempt)`), which dealt the same board twice;
  practice seeds are 1,000 apart.
- The final board: of the first 400 practice seeds' Expert boards, one (index 219) needs all eight techniques.
- `SudokuTrainingTest` checks 8 practices per technique: the teacher's next step is the technique, the step's digit is the
  answer's, the board has one answer, and, by an independent count of singles, no simpler single is anywhere on it.
- Layout: at 375x537 the board is about 190 dp wide (as in the walkthrough, which shares the shape); at 390x844 it is full
  width.

## After the pilot

Placement: the owner decides after trying it. Recommendation: a "Train" link on each puzzle's rules sheet (WS2's "?"
sheet) and on the Techniques page beside each technique, rather than a Home tile: Home is the daily menu, and training
is something a player looks for when a technique has beaten them. Other puzzles follow the same recipe where their
teacher names placements (Cages, Inequality, Regions first: the same `PlacementStep` shape).
