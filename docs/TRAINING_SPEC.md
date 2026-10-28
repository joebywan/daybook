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

- **Training** (route `training`): a list of the puzzles that have it (Sudoku, Cage Sudoku, Inequality, Regions, Kings, Mosaic), each
  with how many of its techniques are passed and a tick once it is finished (its final board passed).
- **A puzzle's techniques** (route `training||…|<puzzle>`): its techniques in the teacher's ladder order
  (`core/TechniqueNames.kt`), each with its name and a tick once passed, and at the foot the final board.
- **A technique** (route `training|<technique>|…|<puzzle>`): a short explanation in the hints' voice, then a practice
  board. The player finds the step and makes it. A right digit adds one to the run; three in a row passes the technique
  (the chime, "Passed! 3 in a row." and any achievement it earns, with the board and Next board locked), and after
  2.5 s back takes the player to where the practice was opened from: the puzzle's technique list, with the new tick, when
  opened from it; the game or the Techniques page when opened by "Practise this" or "Practise", as back would. The owner
  (2026-10-28): "send the user back to the list when they achieve the goal. Otherwise they're likely to get stuck there."
  Every run that reaches three does this, on a technique already passed too (1.19 said only "That's it." there and kept
  the player on the board, which read as no pass at all); achievements are announced once. A wrong digit ends the run
  and shows the reasoning that was there to find. The next board is one tap away. Hints work as in a game (nudge, why, show me); a board where a hint was opened does not count towards
  the run and does not break it.
- **The final board** opens once every technique of that puzzle is passed: a whole Expert board whose own walk by the hint
  ladder needs every technique. Solving it without "Show me" earns the second achievement, and
  like a technique's pass shows "Solved!" for 2.5 s and then goes back to the list (a solve with a Show me stays, to start again). Hints are allowed (they teach).
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
  `inequalityTrained`, `inequalityGraduate`, `regionsTrained`, `regionsGraduate`, `kingsTrained`, `kingsGraduate`, `mosaicTrained`, `mosaicGraduate`. They
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
  - Mosaic: all three on Standard. Its tiers are board size, sections and palette, not depth of reasoning, and Standard
    walks reach every technique (counting and the biggest swallow on 60 of 60 boards, the centre on 5); the smallest
    boards are the clearest to learn on.
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
- **Keys** (a hardware or web keyboard): the board's own, plus H for the hint's next step and Esc to close it, through
  `hintKeys` (`ui/teach/Hints.kt`), the modifier the play screen wraps its board in too, so a puzzle added to Training
  needs nothing for them. No Ctrl/Cmd+Z: neither practice nor the final board has an undo.

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
- **`MoveDrill(next, every)`** (every other teacher): `next(state)` is the teacher's next step as a `Deduction`, built from the
  teacher's visible-state entry point (one with no parameter for the answer, as `KingsTeacher.deduce(n, region, marks)`)
  and the puzzle's own Deduction builder (Kings' was pulled out of `teach` as `Kings.deduction(state, step)`; the
  pattern for the others is the same one-function refactor). The answer cannot reach the step by signature, as before.
  - The walk is the hints' own "Show me" (`Deduction.apply`) from the generated board; a position is simply the walked
    state. Nothing is locked: a walked cross can be rubbed out, and the practice waits until the step is made again.
    There is no undo on a practice board, so there is no floor to set.
  - **Right** = the move completes *any* step of the drilled technique on the position: the teacher's own pick, or any of
    `every(position, technique)` (the owner, 2026-10-28: "If the move satisfies the move that's asked for without being
    exactly what the app assumed people would do, it's still right"). Completing means that step's `isReached` holds:
    all its crosses made, one at a time or in a sweep, or a correct king that sweeps them, as the hint itself accepts.
    `every` is the course author's second hook, `(PuzzleState, technique: String) -> List<Deduction>`, built from a teacher
    entry point that lists every step of one rung from visible state (Kings: `KingsTeacher.steps(n, region, marks,
    technique)`, which `deduce`'s pick is always one of; its picking is unchanged, so no daily board moved). Leaving it
    null keeps to the teacher's one step.
  - **Wrong** = the teacher's `teach` calls the new board a mistake, which each teacher defines as "no legal answer keeps
    this" (never "differs from the stored answer": LITS's and Pipes' several answers, PR #15).
  - **Neither** = play on: one cross of three, an erasure, or a mark that is true but that no step of the technique
    justifies. That last is never passed, because a lone cross is right by chance on most squares of a Kings board, and it
    gets no "true, but not this technique" note either: nothing can tell it from the first cross of a step the player is
    still making, so a note would fire on honest progress.
  - Judging asks the teacher, so every emitted state is judged on `Dispatchers.Default`; a verdict for a state the player
    has already moved on from is dropped and the newer state judged instead.
- The practice screen is the same for both: the board in what is left above the fixed 144 dp slot (explanation, hint or
  verdict) and the button row, so feedback never moves it; the verdict outlines the step's targets and marks its cited
  squares more quietly.
- Tests for a new course, as `KingsTrainingTest`: the step is the technique (and the hint agrees); the board has one
  answer by the puzzle's independent oracle; the walked marks agree with the answer; no simpler rung is on the board by a
  check written in the test (plain counting, nothing borrowed from the teacher); right, wrong and play-on moves; every other
  instance of the technique, enumerated by the test's own counting, is right, and a true cross no instance justifies plays
  on; the final
  board is the first practice seed whose Expert walk needs every technique, and is proved. The achievement ids need no
  listing (`TrainingTest` derives their order from `COURSES`); add the course's id to `TrainingTest`'s list of courses.
- **Mosaic** (`MosaicDrill`, `core/training/MosaicTraining.kt`) walks as `MoveDrill` does (from
  `MosaicTeacher.deduce`, which sees the board, the palette and the fills left) and judges by the same rule as
  `MoveDrill(next, every)`, in its own drill for two reasons. The teacher's whole hint (lost-board check plus a search
  for the step) took up to 1.2 s on the emulator, which judging per move on the web's one thread could not afford, so a
  fill is judged by one question of the same search, "does the board still finish in the fills left?". And every move is
  a fill that cannot be taken back, so only the first fill after the practice's board is judged:
  - the step's own fill is right with no search (`isReached`);
  - a fill that loses the board is wrong (what `teach` calls a mistake);
  - a fill that keeps the limit and is any instance of the technique is right: counting, any keeping fill (with one
    fill fewer than colours each must wipe a colour out); the biggest swallow, any keeping fill that swallows as many;
    the centre, any keeping fill of a most central area;
  - a fill that keeps the limit but is no instance plays on with no verdict (the owner, 2026-10-28: a fill that still
    wins is never "Not quite"). The step is then gone, so nothing later on that board is judged: the player plays it out
    or takes "Another board", which neither counts nor breaks the run.

  Guessing rarely passes: of every fill on 10 practices each, 19% are right on counting, 13% on the biggest swallow, 6%
  on the centre.
- Watch for a teacher that treats some marks as notes (Tents: "crosses are notes and never read as knowledge"): a wrong
  note is then never a mistake, so a wrong cross plays on instead of failing. Decide per puzzle whether the teacher should
  flag it.

## Measured

Desktop JVM, the first 30 practices of each technique (all 30 distinct boards in every case), and how many of the first
60 practice seeds' boards on each tier reach each technique (`TrainingMeasureTest`, `DAYBOOK_TRAINING_MEASURE=1`).
Nothing had to be excluded: every technique every teacher names is found within a second.

- **Sudoku** (unchanged by the generalisation): singles 20-150 ms, locked candidates 280 ms, what-if 300 ms, naked pair
  890 ms, hidden pair 930 ms.
- **Cage Sudoku:** the five singles and the cage's combinations 50-140 ms; the 45 rule 160 ms; linked totals 330 ms (24 of
  60 Hard boards reach it, 57 of 60 Expert); every cage worked through together 770 ms (10 of 60 Hard, all 60 Expert). The
  chain's hint, and so its practice, is a worked line like linked totals' (each group named in turn with the digits it
  leaves, ending in the square), not a summary: median 247 characters, 65% within 300, the rest scrolling in the slot
  (`docs/TEACHING.md`). It kept its id: the same step, only explained, so a pass of it stands and no record or achievement
  moved. Its square is now the one of the chain's first three singles with the shortest line, so walks after the first
  chain step can differ: a practice stops at the first step of its technique, so only one whose walk passes a chain step
  first (some linked-totals practices on Hard) may land elsewhere. Index 0 still needs all nine (33 of the first 60 Expert
  boards do).
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
- **Mosaic** (Standard boards): first board through the cache 13 ms counting, under 1 ms the biggest swallow, 1 ms
  the centre; each next board a mean of 0-2 ms, at most 5 ms (the centre, 5 of 60 Standard boards). Judging a fill,
  every fill on 10 practices of each technique: at most 0.2 ms on the JVM, 0.6 ms in Chromium and 1 ms in WebKit (wasm,
  `?trainprobe`-style timing in the page, not kept); the hint on a practice board at most 1 ms. Far under the 300 ms
  bar, so Mosaic ships: practice boards are small, and the 1.2 s hint was an Expert board mid-game. Walking a whole
  board costs a mean of 1 / 3 / 38 ms by tier; the final board's generation 1-2 ms in the browser.
- **Final boards** (Expert, the first practice seed whose walk needs every technique, each pinned by a test):
  Sudoku index 219 (one in the first 400); Cage Sudoku index 0 (33 of the first 60); Inequality index 4 (17 of the first
  60); Regions index 0 (all 60); Kings index 1 (17 of the first 60); Mosaic index 4 (17 of the first 60; Standard
  5 of 60, Hard 14).
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
- Mosaic's three practices and its final board checked at 375x537 and 390x844 in Chromium and WebKit (`render.py --home
  --query "screen=training&puzzle=mosaic&technique=count"`, with taps for a losing fill and for the step), its badges on
  `?screen=achievements` (a harness hook added with it), and on the Android emulator: a hinted step, a wrong fill, the
  board still through each verdict. The 375x537 board is small (the slot and buttons take what Kings' take).

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
| Mosaic | 3: count, biggest swallow, centre 2/5/10 (finish, fill within the limit: not praised) | move (own judge) | 0-13 ms | **done** |
| Atoms | 6: one neighbour … isolation 0/16/29, what-if 0/0/6 (only way out: never) | move | 4-29 ms | include |
| LITS | 6: whole region 5/13/8, overlap, avoid 2x2, letter clash 30/30/30, neighbour 0/15/27, what-if 0/1/14 | move | 26-173 ms | include |
| Nonogram | 4: all 26-30 a tier | move | 4-6 ms | include |
| Chess | 3: mate in one, forcing key 27-30 a tier; quiet key 0/2/9 | move | 8-47 ms | include: a key move is a real technique, and its walkthrough teaches the rules, not these |
| Tents | 6: no tree, line done, line full 29-30; one spot 1/25/21; shared 0/1/12; served 1/7/20 (touch: never) | move | 2-74 ms | include; its teacher reads grass as notes (see above) |
| Cipher | 5: leading carry, column, column pair 29-30; columns together 0/30/9; pair what-if 0/0/30 (only letter: never) | move | 2-11 ms | include |
| Tally | 6: all reached; every way 0/30/30, what-if 0/0/30 | move | 2-11 ms | include |
| Dominoes | 6: only place 10 a tier, counting 0/30/10, what-if 0/0/30, the rest 14-30 | move | 6-33 ms | include |
| Mines | 6: subset 0/28/26, pair 0/17/17, count edge 0/0/29, what-if 0/0/1 (count: never) | move | 4-22 ms | include (what-if is one board in 30: ~0.2 s a board) |
| Lamps | 7: all reached; dark 0/11/14 | move | 3-21 ms | include |
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
6. **Mosaic** alone: done (its own judge, above).
