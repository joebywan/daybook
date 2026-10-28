# Training

The owner (2026-10-26): "Have a 'Training' section, where there are tutorials for each technique, and the player can
play examples of them to get a dopamine hit for 'passing the test', potentially getting an achieve per game when passing
all of the techniques, then completing a game that requires all of the techniques."

This replaces the earlier decline of a per-technique practice mode (`docs/TODO.md`). It was piloted on Sudoku alone; after
trying it the owner decided (2026-10-28): "link Training from the rules sheet and the Techniques page, and extend it to
Cages, Inequality and Regions." Those three were chosen because their teachers place digits in the same shape as Sudoku's
(`PlacementStep`), so one recipe serves all four.

## What the player sees

- **Training** (route `training`): a list of the puzzles that have it (Sudoku, Cage Sudoku, Inequality, Regions), each
  with how many of its techniques are passed and a tick once it is finished (its final board passed).
- **A puzzle's techniques** (route `training||…|<puzzle>`): its techniques in the teacher's ladder order
  (`core/TechniqueNames.kt`), each with its name and a tick once passed, and at the foot the final board.
- **A technique** (route `training|<technique>|…|<puzzle>`): a short explanation in the hints' voice, then a practice
  board. The player finds the step and makes it. A right digit adds one to the run; three in a row passes the technique
  (a tick, the chime, "Passed"). A wrong digit ends the run and shows the reasoning that was there to find. The next board
  is one tap away. Hints work as in a game (nudge, why, show me); a board where a hint was opened does not count towards
  the run and does not break it.
- **The final board** opens once every technique of that puzzle is passed: a whole Expert board whose own walk by the hint
  ladder needs every technique. Solving it without "Show me" earns the second achievement. Hints are allowed (they teach).
- **Ways in:**
  - **"Train: practise the techniques"** on the rules sheet (the play screen's "?") of a puzzle that has Training: opens
    that puzzle's techniques; back from there returns to the game, and back from a technique opened there returns to the list.
  - **"Practise"** beside each technique on Statistics → Techniques that has a practice: opens it; back returns to the page.
  - **"Practise this"** in a hint's panel, beside Close, once the hint is explaining (not a mistake, not the fallback):
    opens that technique's practice; back returns to the game, which is saved as on any leave.
  - **Settings**' Training row stays, opening the list of puzzles: it is the one place a player can find every course
    without first playing.
- **Achievements** (appended, two per puzzle): "<Puzzle> trained" for passing every technique, "<Puzzle> graduate" for
  then solving the final board: Sudoku's two (`sudokuTrained`, `sudokuGraduate`), then `cagesTrained`, `cagesGraduate`,
  `inequalityTrained`, `inequalityGraduate`, `regionsTrained`, `regionsGraduate`. They are announced on the training screen
  as they are earned and shown on the Achievements page.

## How it works

- `core/Training.kt`: one `Course` per puzzle, which is the puzzle, the explanations (whose keys, in order, are the
  technique list: the teacher's named techniques), the tier each technique is practised on, the teacher's next step from
  the visible digits, and how to write a position's digits in as givens. `Training.COURSES` lists them; nothing else is
  per puzzle.
- **A practice is a real position:** per seed from Training's own range (`SEED_BASE`, 1,000 apart), generate the board of
  the easiest tier whose walks reach the technique, walk it with the teacher's `deduce` from its givens, and stop at the
  first position where the next step is the technique. The walk's digits become givens, so they cannot be erased. Pure,
  seed order only, no hash order: the same index is the same board on every platform.
- **Tiers** (the generator gates each tier's depth, so the easiest tier that reaches a technique):
  - Sudoku: singles in a unit on Standard, the naked single on Hard, the rest on Expert (`Sudoku.gate`).
  - Cage Sudoku: up to the cage's combinations on Standard, the 45 rule and linked totals on Hard, the chain on Expert.
  - Inequality: the five singles on Standard, the naked pair on Hard, the hidden pair on Expert (`InequalityLogic.specFor`).
  - Regions: the three singles on Standard, the locked candidate on Hard (`RegionsLogic.specFor`).
- **What counts as right:** the step's own digit, which is what the hint's `isReached` checks. Another correct digit
  elsewhere also counts: the position has no simpler step anywhere (each teacher tries every simpler technique on the whole
  board first), so a correct digit there was found with this technique or a harder one. It is answered about its own
  square ("That's it: 7 goes there.", outlined where it went), never with the step's explanation of the other square:
  1.19 said "That's it." and then explained the step's square, which the owner read as being told they were wrong and
  should have filled that one (`verdictLine`, `TrainingVerdictTest`). A wrong digit is judged against
  the stored answer, sound because a generated board has one answer.
- **Stored:** `training_passed`, its own key set in the progress store (`puzzleId|technique`, `puzzleId|exam`), never on a
  save and never a `Completion`, so training touches no streak, total or best time. The pilot's entries (`sudoku|…`) are
  the same keys, so a store written by the pilot reads unchanged. An older store has none.
- **Routes** gained `puzzle` and `listFromHint` at the end; a route string from the pilot (`training|naked-pair|false`)
  decodes as Sudoku's (`SudokuTrainingTest`).
- **Achievements** stay predicates: `Achievement.trained` reads the training set, beside `earned` over completions.
  `earnedAchievements` takes the set (default empty); `newlyTrained(record, puzzleId, technique)` says what a pass earns.
- The run (right answers in a row) is not stored: leaving a technique starts its run again.
- **Keys** (a hardware or web keyboard): the board's own, plus H for the hint's next step and Esc to close it, through
  `hintKeys` (`ui/teach/Hints.kt`), the modifier the play screen wraps its board in too, so a puzzle added to Training
  needs nothing for them. No Ctrl/Cmd+Z: neither practice nor the final board has an undo.

## Measured

Desktop JVM, the first 30 practices of each technique (all 30 distinct boards in every case), and how many of the first
60 practice seeds' boards on each tier reach each technique (`TrainingMeasureTest`, `DAYBOOK_TRAINING_MEASURE=1`).
Nothing had to be excluded: every technique every teacher names is found within a second.

- **Sudoku** (unchanged by the generalisation): singles 20-150 ms, locked candidates 280 ms, what-if 300 ms, naked pair
  890 ms, hidden pair 930 ms.
- **Cage Sudoku:** the five singles and the cage's combinations 50-100 ms; the 45 rule 120 ms; linked totals 280 ms (25 of
  60 Hard boards reach it, 55 of 60 Expert); every cage worked through together 690 ms (10 of 60 Hard, all 60 Expert). The
  chain is the teacher's catch-all, so its practice's explanation is the hint's summary ("work the cage totals through
  together"), not a worked line; it is kept because it is a step the hints really take.
- **Inequality:** the five singles 6-16 ms (4x4 boards; a sign-bound or sign-place position is often the empty board, so
  boards differ by their signs); naked pair 90 ms (all 60 Hard boards); hidden pair 400 ms (all 60 Expert boards; 19 of 60
  reach a naked pair there, against all 60 on Hard).
- **Regions:** the three singles 40-80 ms; the locked candidate 220 ms (every Hard and Expert board needs it).
- **Cached per session:** `Course.practice` keeps the practices it has found per technique and the next seed to search,
  so board n costs only the seeds after board n-1's (they come from pure generators, so they never go stale). A coroutine
  `Mutex` per course guards it: on Android a second search (a quick second "Another board") waits for the first on
  `Dispatchers.Default` and then finds its work cached; wasm has one thread. Through the cache, the first board of a technique takes 0-70 ms (Sudoku's hidden
  pair the dearest) and each next board a mean of 0-30 ms, at most 151 ms (Sudoku's naked pair); a board already found
  takes under 0.01 ms. Before it, board 29 alone re-walked up to 940 ms of seeds.
- **Final boards** (Expert, the first practice seed whose walk needs every technique, each pinned by a test):
  Sudoku index 219 (one in the first 400); Cage Sudoku index 0 (32 of the first 60); Inequality index 4 (17 of the first
  60); Regions index 0 (all 60).
- `SudokuTrainingTest` checks 8 Sudoku practices per technique; `TrainingTest` 5 Cage Sudoku practices and 8 Inequality
  and Regions practices per technique: the teacher's next step (and the hint's, through `PuzzleType.teach`) is the
  technique, the step's digit is the answer's, the shown digits agree with the answer, the board has one answer by the
  puzzle's own oracle (`CageOracle`, `InequalityOracle`, `RegionsOracle`), and, by singles counted here with no candidate
  logic of the teacher's (for Inequality, sign bounds worked to a fixpoint as well), no simpler single is anywhere on it.
- Layout: at 375x537 a practice board sits in what is left above the 144 dp explanation slot and the button row; the
  slot's height is fixed, so a verdict or a hint never moves the board.
