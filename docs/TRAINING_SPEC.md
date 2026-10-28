# Training

The owner (2026-10-26): "Have a 'Training' section, where there are tutorials for each technique, and the player can
play examples of them to get a dopamine hit for 'passing the test', potentially getting an achieve per game when passing
all of the techniques, then completing a game that requires all of the techniques."

This replaces the earlier decline of a per-technique practice mode (`docs/TODO.md`). It was piloted on Sudoku alone; after
trying it the owner decided (2026-10-28): "link Training from the rules sheet and the Techniques page, and extend it to
Cages, Inequality and Regions." Those three were chosen because their teachers place digits in the same shape as Sudoku's
(`PlacementStep`), so one recipe serves all four. Kings (2026-10-28) is the first on the general shape, for teachers that
crown, cross, shade, turn or place pieces instead (`MoveDrill`, below); the rest of the puzzles are classified at the end.

## What the player sees

- **Training** (route `training`): a list of the puzzles that have it (Sudoku, Cage Sudoku, Inequality, Regions, Kings, Mines, Lamps, Tents), each
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
  `inequalityTrained`, `inequalityGraduate`, `regionsTrained`, `regionsGraduate`, `kingsTrained`, `kingsGraduate`, then
  `minesTrained`, `minesGraduate`, `lampsTrained`, `lampsGraduate`, `tentsTrained`, `tentsGraduate` (badges a drawn mine, `L`, `T`). They
  are built from `Training.COURSES` in its order (so a course appended there appends its two), announced on the training
  screen as they are earned and shown on the Achievements page; the trained badge's glyph is the course's `badge`, the
  graduate's the crown.

## How it works

- `core/Training.kt`: one `Course` per puzzle, which is the puzzle, the explanations (whose keys, in order, are the
  technique list: the teacher's named techniques), the tier each technique is practised on, its `Drill` (how a walk steps
  and a move is judged: see "The two shapes" below), its badge glyph and its final board. `Training.COURSES` lists them;
  nothing else is per puzzle. Kings' course is `core/training/KingsTraining.kt`, the pattern for the rest.
- **A practice is a real position:** per seed from Training's own range (`SEED_BASE`, 1,000 apart), generate the board of
  the easiest tier whose walks reach the technique, walk it with the teacher's `deduce` from its givens, and stop at the
  first position where the next step is the technique. The walk's digits become givens, so they cannot be erased. Pure,
  seed order only, no hash order: the same index is the same board on every platform.
- **Tiers** (the generator gates each tier's depth, so the easiest tier that reaches a technique):
  - Sudoku: singles in a unit on Standard, the naked single on Hard, the rest on Expert (`Sudoku.gate`).
  - Cage Sudoku: up to the cage's combinations on Standard, the 45 rule and linked totals on Hard, the chain on Expert.
  - Inequality: the five singles on Standard, the naked pair on Hard, the hidden pair on Expert (`InequalityLogic.specFor`).
  - Regions: the three singles on Standard, the locked candidate on Hard (`RegionsLogic.specFor`).
  - Kings: last square, locked to a line and would empty on Standard, confined on Hard, the what-if on Expert
    (`Kings.fitsTier`).
  - Mines: single numbers on Standard, subset and pair on Hard (57 and 33 of 60 boards), the count against the numbers and
    the what-if on Expert (57 and 4 of 60).
  - Lamps: zero, done, room and only light on Standard; starve and dark on Hard (51 and 22 of 60); the what-if on Expert.
  - Tents: no tree and the two line rules on Standard; one spot, shared and served on Hard (52, 3 and 11 of 60).
- **What counts as right** (digits; Kings and the general shape below): the step's own digit, which is what the hint's `isReached` checks. Another correct digit
  elsewhere also counts: the position has no simpler step anywhere (each teacher tries every simpler technique on the whole
  board first), so a correct digit there was found with this technique or a harder one. A wrong digit is judged against
  the stored answer, sound because a generated board has one answer.
- **Stored:** `training_passed`, its own key set in the progress store (`puzzleId|technique`, `puzzleId|exam`), never on a
  save and never a `Completion`, so training touches no streak, total or best time. The pilot's entries (`sudoku|…`) are
  the same keys, so a store written by the pilot reads unchanged. An older store has none.
- **Routes** gained `puzzle` and `listFromHint` at the end; a route string from the pilot (`training|naked-pair|false`)
  decodes as Sudoku's (`SudokuTrainingTest`).
- **Achievements** stay predicates: `Achievement.trained` reads the training set, beside `earned` over completions.
  `earnedAchievements` takes the set (default empty); `newlyTrained(record, puzzleId, technique)` says what a pass earns.
- The run (right answers in a row) is not stored: leaving a technique starts its run again.

## The two shapes, and adding a course

A course is one `Course` in its own file (`core/training/<Name>Training.kt`, package `com.joebywan.daybook.core`) plus one
line at the end of `Training.COURSES`. Nothing else is per puzzle: the list, the technique pages, the practice and final
boards, the rules sheet's Train button, Techniques' "Practise", a hint's "Practise this", the achievements and their
badges all read `COURSES`. A `Course` is the puzzle, the explanations (whose keys, in order, are the teacher's named
techniques), `tierFor` (usually `tierByIndex(...)`), a `Drill`, the trained badge's glyph and the final board's index.

- **`PlacementDrill`** (Sudoku, Cages, Inequality, Regions; their four definitions are unchanged, through `Course`'s
  second constructor): the stepper sees only the digits; the walk writes each step's digit in and makes every digit of a
  position a given. A placed digit is judged at once against the stored answer (sound: those generators prove one
  answer); any correct digit counts, because the position has no simpler step anywhere.
- **`MoveDrill(next)`** (every other teacher): `next(state)` is the teacher's next step as a `Deduction`, built from the
  teacher's visible-state entry point (one with no parameter for the answer, as `KingsTeacher.deduce(n, region, marks)`)
  and the puzzle's own Deduction builder (Kings' was pulled out of `teach` as `Kings.deduction(state, step)`; the
  pattern for the others is the same one-function refactor). The answer cannot reach the step by signature, as before.
  - The walk is the hints' own "Show me" (`Deduction.apply`) from the generated board; a position is simply the walked
    state. Nothing is locked: a walked cross can be rubbed out, and the practice waits until the step is made again.
    There is no undo on a practice board, so there is no floor to set.
  - **Right** = the step's `isReached` holds (all its crosses made, one at a time or in a sweep, or a correct king that
    sweeps them, as the hint itself accepts). **Wrong** = the teacher's `teach` calls the new board a mistake, which each
    teacher defines as "no legal answer keeps this" (never "differs from the stored answer": LITS's and Pipes' several
    answers, PR #15). **Neither** = play on (one cross of three, a sound mark elsewhere, an erasure). Unlike digits, only
    the step itself passes: a lone cross is right by chance on most squares of a Kings board, so "another correct mark"
    would pass guesses.
  - Judging asks the teacher, so every emitted state is judged on `Dispatchers.Default`; a verdict for a state the player
    has already moved on from is dropped and the newer state judged instead.
- The practice screen is the same for both: the board in what is left above the fixed 144 dp slot (explanation, hint or
  verdict) and the button row, so feedback never moves it; the verdict outlines the step's targets and marks its cited
  squares more quietly.
- Tests for a new course, as `KingsTrainingTest`: the step is the technique (and the hint agrees); the board has one
  answer by the puzzle's independent oracle; the walked marks agree with the answer; no simpler rung is on the board by a
  check written in the test (plain counting, nothing borrowed from the teacher); right, wrong and play-on moves; the final
  board is the first practice seed whose Expert walk needs every technique, and is proved. The achievement ids need no
  listing (`TrainingTest` derives their order from `COURSES`); add the course's id to `TrainingTest`'s list of courses.
- Watch for a teacher that treats some marks as notes (Tents: "crosses are notes and never read as knowledge"): a wrong
  note is then never a mistake, so a wrong cross plays on instead of failing. Decided (2026-10-28): a practice judges such
  a note wrong when no legal answer keeps it, through `MoveDrill`'s `wrong`, leaving the game's hints alone. Tents: grass
  where the one answer has a tent; Lamps (the same shape): a cross where the answer has a lamp. Both generators prove one
  answer, so "the stored answer's" is "every legal answer's". Mines' `wrong` is a lost board: a mine revealed is wrong,
  the verdict makes the board non-interactive, and the board offers its Try again only while it takes moves, so no
  game-over flow runs inside Training.

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
- **Kings** (move drill): last square 8 ms for the first board, then a mean of 4 ms a board; locked to a line, would
  empty and confined 1-5 ms first, 3-6 ms next; the what-if 153 ms first, 91 ms a board after (max 287 ms), since only 18
  of 60 Expert boards reach it. Standard reaches the first three on 56-60 of 60 boards, Hard adds confined on all 60.
- **Final boards** (Expert, the first practice seed whose walk needs every technique, each pinned by a test):
  Sudoku index 219 (one in the first 400); Cage Sudoku index 0 (32 of the first 60); Inequality index 4 (17 of the first
  60); Regions index 0 (all 60); Kings index 1 (17 of the first 60).
- **Mines** (move drill; measured on a machine shared with other builds, so on the high side): all found, all mines,
  subset and pair 1-38 ms for the first board, a mean of 1-4 ms a board after (max 16 ms); the count against the numbers
  5 ms first, 10 ms a board (max 35 ms); the what-if 14 ms first, 60 ms a board after (max 218 ms; 30 boards in 2.4 s),
  since only 4 of 60 Expert boards reach it. Kept. The walk counts its own flags as found mines (they are its sound
  steps); the game's teacher never trusts a flag, and without this it offered a subset on a board whose flags already
  showed a single number's step.
- **Lamps:** the four direct facts 0-5 ms first and about 1 ms a board; starve 5 ms first, 4 ms a board (max 16 ms); dark
  2 ms first, 6 ms a board (max 24 ms); the what-if 15 ms first, 52 ms a board (max 143 ms).
- **Tents:** no tree and the lines 0-2 ms; one spot 4 ms first, 4 ms a board (max 13 ms); shared 52 ms first, 26 ms a board
  (max 101 ms; 3 of 60 Hard boards); served 4 ms first, 5 ms a board (max 15 ms).
- **Left out:** Mines' "counting the mines left" and Tents' "grass around a tent", which no walk reaches. The count rule
  needs the hidden squares to be all mines or all safe, which the count against the numbers (tried after it, and more
  general) always settles first in practice. A tent's surrounding grass is drawn by itself (`TentsState.seen`), so that
  step is always already shown and the teacher skips it.
- Final boards: Mines index 44 (the only one of the first 60 whose walk needs all six: the what-if is rare), Lamps index 4
  (31 of 60), Tents index 34 (5 of 60). `MinesTrainingTest`, `LampsTrainingTest` and `TentsTrainingTest` pin them, and
  check 6 practices per technique against `MinesOracle`, `LampsOracle` and `TentsOracle` and lower rungs counted in the
  test (Mines' two numbers brute-forced over the mines their shared squares hold; Lamps' starve and dark by placing the
  lamp and walking its rays); right, wrong (a mine, a wrong flag; a wrong lamp or cross; a wrong tent or grass) and play-on.
- `SudokuTrainingTest` checks 8 Sudoku practices per technique; `TrainingTest` 5 Cage Sudoku practices and 8 Inequality
  and Regions practices per technique: the teacher's next step (and the hint's, through `PuzzleType.teach`) is the
  technique, the step's digit is the answer's, the shown digits agree with the answer, the board has one answer by the
  puzzle's own oracle (`CageOracle`, `InequalityOracle`, `RegionsOracle`), and, by singles counted here with no candidate
  logic of the teacher's (for Inequality, sign bounds worked to a fixpoint as well), no simpler single is anywhere on it.
- Layout: at 375x537 a practice board sits in what is left above the 144 dp explanation slot and the button row; the
  slot's height is fixed, so a verdict or a hint never moves the board.
- Kings' practice checked at 375x537 and 390x844 in Chromium and WebKit (`tools/render/render.py --home --query
  "screen=training&puzzle=kings&technique=would-empty"`, with taps for a wrong cross and for the step), and on the
  Android emulator through a hint's "Practise this" and a Show me: the board stays put through every verdict.

## Classification of every puzzle

Measured with `TrainingClassifyTest` (`DAYBOOK_TRAINING_CLASSIFY=1`): for each puzzle with hints, the first 30 practice
seeds' boards per tier walked by the puzzle's own hints (`teach`, then `apply`), stopping at a mistake, the fallback or no
step. "Boards" is how many of 30 Standard / Hard / Expert walks reach the technique; "first" is the time to the first such
position on the easiest tier that has one (generating and walking every seed before it), desktop JVM. A one-rung ladder
is excluded; so are steps that only point at a move, a word or a guess (`NOT_PRAISED` in `TechniqueNames.kt`), and
techniques no walk reached.

| puzzle | rungs worth practising (boards S/H/E of 30) | shape | first board | verdict |
|---|---|---|---|---|
| Sudoku | 8 | placement | 1-70 ms | **done** |
| Cage Sudoku | 9 | placement | 2-25 ms | **done** |
| Inequality | 7 | placement | 0-5 ms | **done** |
| Regions | 4 | placement | 2-14 ms | **done** |
| Kings | 5: last square 30/30/30 … what-if 0/0/10 | move | 1-153 ms | **done (pilot)** |
| Mambo | 6: pair, sandwich, link, quota met 30/30/30; one short 0/30/30; what-if 0/0/30 | move | 17-41 ms | include |
| Pipes | 4: border, set neighbour, whichever way 25-30 a tier; no loop 5/4/6 | move | 3-56 ms | include (several answers: judged by the teacher) |
| Shikaku | 4: only fits, only reaches; common cells 0/5/18; what-if 0/6/14 | move | 4-13 ms | include |
| Mosaic | 3: count, biggest swallow, centre 2/5/10 (finish, fill within the limit: not praised) | move | 8-21 ms | include; its teacher can take 1.2 s a hint, and judging asks it per move: measure on the web first |
| Atoms | 6: one neighbour … isolation 0/16/29, what-if 0/0/6 (only way out: never) | move | 4-29 ms | include |
| LITS | 6: whole region 5/13/8, overlap, avoid 2x2, letter clash 30/30/30, neighbour 0/15/27, what-if 0/1/14 | move | 26-173 ms | include |
| Nonogram | 4: all 26-30 a tier | move | 4-6 ms | include |
| Chess | 3: mate in one, forcing key 27-30 a tier; quiet key 0/2/9 | move | 8-47 ms | include: a key move is a real technique, and its walkthrough teaches the rules, not these |
| Tents | 6: no tree, line done, line full 29-30; one spot 1/25/21; shared 0/1/12; served 1/7/20 (touch: never) | move | 0-52 ms | **done** (grass no answer keeps is wrong in practice) |
| Cipher | 5: leading carry, column, column pair 29-30; columns together 0/30/9; pair what-if 0/0/30 (only letter: never) | move | 2-11 ms | include |
| Tally | 6: all reached; every way 0/30/30, what-if 0/0/30 | move | 2-11 ms | include |
| Dominoes | 6: only place 10 a tier, counting 0/30/10, what-if 0/0/30, the rest 14-30 | move | 6-33 ms | include |
| Mines | 6: subset 0/28/26, pair 0/17/17, count edge 0/0/29, what-if 0/0/1 (count: never) | move | 1-24 ms | **done** (what-if kept: 42 ms a board through the cache) |
| Lamps | 7: all reached; dark 0/11/14 | move | 0-15 ms | **done** (a cross where the answer has a lamp is wrong in practice) |
| Pearls | 7: pearl's arms and lines 26-30, dead end 18/28/29, small loop 0/30/29, what-if 0/0/30 (pearl ways: never) | move | 5-26 ms | include |
| Who's Who | 5: clue, only 29-30; link, house 0/29-30/30; what-if 0/0/30 | move | 3-94 ms | include: the steps are logic, the words only labels |
| Sets | 1: reuse (continue never reached; fresh not praised) | — | — | excluded: one rung |
| Target | 0: step, finish point at an arithmetic move | — | — | excluded: no technique |
| Sand | 2: gather, spare (finish not praised; plan never reached) | — | — | excluded: two thin rungs, and a pour is reversible, so a practice could hardly ever be wrong |
| Tower | 8 named, but every walk reaches the guess fallback right after the opener: the reasoning rungs need a guess history scored against the hidden code | — | — | excluded for now: needs its own walk that plays consistent guesses |
| Lexicon | 5 named; the walk sticks after the opener and the rungs are vocabulary | — | — | excluded: knowledge |
| Word Wheel | 2: pinned, crossing, but 27-29 of 30 walks fall back to a clue's answer | — | — | excluded: knowledge |
| Mini | 3 named; every walk falls back at once (clues) | — | — | excluded: knowledge |
| Codeword | 4: one word, run agrees, crossing, all words, all reached | — | — | excluded: every rung is "which words fit", against a word list the player cannot see |
| Snap, Pointers | — | — | — | excluded by the owner: no hints |

Nothing measured is over a second for a first board; the slowest is LITS's whole region (173 ms, 5 Standard boards in
30). Every board in every included walk ended solved, except Mosaic (3 Hard and 5 Expert walks reached its fallback).

### Batches for follow-up PRs

Each course is its own file, a one-function refactor in its puzzle's file (the Deduction builder pulled out of `teach`,
as Kings'), its own test, and one line at the end of `COURSES`, so batches conflict only on that line, `TrainingTest`'s
id list, the docs and the changelog. Grouped by kind of board, so a batch's agent learns one board idiom:

1. **Mambo, Nonogram, Tally**: line rules on a grid, cheap, every rung reached.
2. **Pipes, Pearls, Atoms**: paths and loops (Pipes' several answers already judged by its teacher).
3. **Mines, Lamps, Tents**: marks and counts (settle Tents' grass-as-notes first).
4. **Shikaku, Dominoes, LITS**: pieces placed by drag or pairs (each placement must stay one emitted state).
5. **Who's Who, Cipher, Chess**: deduction grids and moves.
6. **Mosaic** alone: its teacher's cost per judged move needs measuring in a browser first.
