# Training

The owner (2026-10-26): "Have a 'Training' section, where there are tutorials for each technique, and the player can
play examples of them to get a dopamine hit for 'passing the test', potentially getting an achieve per game when passing
all of the techniques, then completing a game that requires all of the techniques."

This replaces the earlier decline of a per-technique practice mode (`docs/TODO.md`). It was piloted on Sudoku alone; after
trying it the owner decided (2026-10-28): "link Training from the rules sheet and the Techniques page, and extend it to
Cages, Inequality and Regions." Those three were chosen because their teachers place digits in the same shape as Sudoku's
(`PlacementStep`), so one recipe serves all four. Kings (2026-10-28) is the first on the general shape, for teachers that
crown, cross, shade, turn or place pieces instead (`MoveDrill`, below); Pipes, Pearls and Atoms (2026-10-28, the second
batch) follow it; the rest of the puzzles are classified at the end.

## What the player sees

- **Training** (route `training`): a list of the puzzles that have it (Sudoku, Cage Sudoku, Inequality, Regions, Kings,
  Pipes, Pearls, Atoms, Who's Who, Cipher, Chess), each
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
  `inequalityTrained`, `inequalityGraduate`, `regionsTrained`, `regionsGraduate`, `kingsTrained`, `kingsGraduate`,
  `pipesTrained`, `pipesGraduate`, `pearlsTrained`, `pearlsGraduate`, `atomsTrained`, `atomsGraduate`, `whoswhoTrained`,
  `whoswhoGraduate`, `cipherTrained`, `cipherGraduate`, `chessTrained`, `chessGraduate` (Who's Who's, Cipher's and
  Chess's badges `?`, `A+B`, `M`; a badge glyph must not start with `#`, which names a drawn shape). They
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
  - Pipes: all four on Standard. Its tiers differ only in size (5x7 to 8x11), and Standard's small boards reach every
    technique (no loop on 8 of 60).
  - Pearls: the pearls' and squares' own rules on Standard, the one loop on Hard, the what-if on Expert
    (`PearlsLogic.specFor`).
  - Atoms: one neighbour, all forced and at least one on Standard (`Atoms.specFor` allows depth up to it there; 7 of 60
    boards reach it, against 57 on Hard), crossing and isolation on Hard, the what-if on Expert.
  - Who's Who: a clue and the one item left on Standard, links and houses on Hard, the what-if on Expert.
  - Cipher: the leading carry, a column and a column pair on Standard, every column together on Hard, the pair what-if on
    Expert.
  - Chess: mate in one and a forcing key on Standard (mate in two). A quiet key is reached on 3 of 60 Hard boards and 18 of
    60 Expert; Expert, since Hard would walk 20 seeds a board.
- **What counts as right** (digits; Kings and the general shape below): the step's own digit, which is what the hint's `isReached` checks. Another correct digit
  elsewhere also counts: the position has no simpler step anywhere (each teacher tries every simpler technique on the whole
  board first), so a correct digit there was found with this technique or a harder one. It is answered about its own
  square ("That's it. 7 goes there.", outlined where it went), never with the step's explanation of the other square:
  1.19 said "That's it." and then explained the step's square, which the owner read as being told they were wrong and
  should have filled that one. A move drill does the same with the instance the player completed, in that instance's own
  words and cells (`Course.made`, `verdictLine`, `TrainingVerdictTest`). A wrong digit is judged against
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
    null keeps to the teacher's one step. Pearls' `PearlsTeacher.steps` lists each square's local rule of the kind, and
    each edge that would close a small loop; Atoms' `AtomsTeacher.steps` each atom the technique's level forces; Pipes'
    `PipesTeacher.steps` each tile of the technique the reasoning pins (for the simpler techniques, only those pinned
    before the first no-loop, which needed nothing more). A what-if is only ever the teacher's one pick in all three.
    Who's Who: `WhosWhoTeacher.steps` over `WhosWhoLogic.clueSteps`/`onlySteps`/
    `linkSteps`/`houseSteps` (every step of each rung, in the order the ladder's direct pick takes its first; kept
    beside the picks rather than replacing them, since `firstOrNull()` over them made Expert generation 23% slower) and, for a what-if, every
    open slot whose supposition runs out within the teacher's long chain. Cipher: `CipherTeacher.steps`, `findSteps`'
    list for the technique (it already listed every placement of the first technique that finds any). Chess has none: its
    step's `isReached` already accepts every key the prover names, which on a walked position is every move that still
    mates in time.
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
- **A practice board is never dealt twice**: `Course` drops a walked position equal to one already found. Every course
  before Chess found distinct boards anyway, so none of theirs moved; Chess picks from fixed position lists, and seeds
  collide (29 of its first 30 mate-in-one practices were distinct before).
- Tests for a new course, as `KingsTrainingTest`: the step is the technique (and the hint agrees); the board has one
  answer by the puzzle's independent oracle; the walked marks agree with the answer; no simpler rung is on the board by a
  check written in the test (plain counting, nothing borrowed from the teacher); right, wrong and play-on moves; every other
  instance of the technique, enumerated by the test's own counting, is right, and a true cross no instance justifies plays
  on; the final
  board is the first practice seed whose Expert walk needs every technique, and is proved. The achievement ids need no
  listing (`TrainingTest` derives their order from `COURSES`); add the course's id to `TrainingTest`'s list of courses.
- **A course may bring its own `Drill`** when a puzzle's input passes through wrong states on the way to a right one.
  Pipes does (`core/training/PipesTraining.kt`): a tap turns a tile a quarter at a time, so the step's tile reads two or
  three wrong turns on its way round, and the teacher would call a wet one a mistake. Its drill walks as `MoveDrill` but
  never judges a tile of any instance wrong (it is right once one reads its turn); another tile the player turned is wrong when
  it runs full and no finished network has it that way (the teacher's own test, `PipesTeacher.solve` with that turn
  pinned: "no legal answer keeps this rotation", sound on a board with several answers too); a dry turn, or one some
  network keeps, plays on. Pearls (a tap crosses, a drag draws) and Atoms (a tap adds a bond, up to two, and only past
  the answer is a mistake) never pass through a wrong state, so the plain `MoveDrill` serves them.
- **Techniques no practice search reaches are left out**, and named in the course's file and here: Pearls' "a pearl with
  two ways in" and Atoms' "a group's only way out" (0 of 30 walks on any tier; the rungs before them always settle those
  squares and lines first), and Cipher's "only letter" (never reached on any tier's walk: with ten letters, a column or the
  leading carry always settles the last letter first). A course's techniques are the teacher's named ones, in order, less
  these (`TrainingTest` lists them); a hint's "Practise this" is not offered for them.
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
- **Pipes** (its own drill, Standard boards): border 19 ms for the first board (the course's first generation), then a
  mean of 1 ms a board; set neighbour and whichever way 1 ms first, 1 ms next; no loop 10 ms first, then a mean of 9 ms
  a board (max 34 ms), since only 8 of 60 Standard boards reach it (9 of 60 Hard, 15 of 60 Expert). That is the rung
  reached on few boards, and the cache makes it cheap: 30 no-loop practices from scratch take 234 ms.
- **Pearls:** the four pearl and square rules and the dead end 1-8 ms first, 1 ms next (the dead end on 37 of 60
  Standard boards); the small loop (Hard, 60 of 60) 2 ms first, 3 ms next; the what-if (Expert, 60 of 60) 28 ms first, 17
  ms a board after (max 45 ms).
- **Atoms:** one neighbour and all forced 0-6 ms first, 1 ms next; at least one (7 of 60 Standard boards) 2 ms first,
  3 ms next (max 7 ms); crossing and isolation (Hard, 43 and 33 of 60) 2-3 ms first, 3-5 ms next (max 17 ms); the
  what-if (Expert, 15 of 60) 19 ms first, 45 ms a board after (max 154 ms).
- **Who's Who** (move drill): clue, only, link, house 1-21 ms first, a mean of 0-1 ms a board after (max 4 ms); the what-if
  77 ms first, 25 ms a board after (max 99 ms). Standard reaches clue and only on all 60, Hard link on 57 and house on all
  60, Expert everything on all 60. Every step reads clue kinds (same, not, next to, left of, either) and the marks; the
  words are only labels, so no board leans on knowing anything (`WhosWhoTrainingTest` re-derives the rungs from indices).
- **Cipher** (move drill): every technique 1-10 ms first, a mean of 1-2 ms a board after (max 5 ms). Standard reaches the
  first three on 59-60 of 60; Hard every column together on all 60 (Expert only 18); Expert the pair what-if on all 60.
  Only letter: never, on any tier (dropped).
- **Chess** (move drill): mate in one 18 ms first, forcing key 1 ms, then about 1 ms a board; the quiet key 88 ms first, a
  mean of 54 ms a board after (max 208 ms). A move is right when the prover names it a key (`isReached` accepts any of
  them) and wrong when the defence escapes (proved): no move plays on. On every walked position the keys are every move
  that still mates in time, since each puzzle's length is its shortest mate and the defence picks the slowest reply;
  `ChessTrainingTest` holds every legal move on the practices with two moves left to an independent full-width minimax.
- **Final boards** (Expert, the first practice seed whose walk needs every technique, each pinned by a test):
  Sudoku index 219 (one in the first 400); Cage Sudoku index 0 (33 of the first 60); Inequality index 4 (17 of the
  first 60); Regions index 0 (all 60); Kings index 1 (17 of the first 60); Pipes index 1 (15 of the first 60); Pearls
  index 0 (54 of 60); Atoms index 1 (10 of 60); Who's Who index 0 (all 60); Cipher index 0 (16 of the first 60); Chess
  index 6 (13 of the first 60).
- `SudokuTrainingTest` checks 8 Sudoku practices per technique; `TrainingTest` 5 Cage Sudoku practices and 8 Inequality
  and Regions practices per technique: the teacher's next step (and the hint's, through `PuzzleType.teach`) is the
  technique, the step's digit is the answer's, the shown digits agree with the answer, the board has one answer by the
  puzzle's own oracle (`CageOracle`, `InequalityOracle`, `RegionsOracle`), and, by singles counted here with no candidate
  logic of the teacher's (for Inequality, sign bounds worked to a fixpoint as well), no simpler single is anywhere on it.
- `PipesTrainingTest`, `PearlsTrainingTest` and `AtomsTrainingTest` (6 practices per technique) check the same against
  each puzzle's independent enumerator (`PipesTeachingTest`'s, `PearlsOracle`, `AtomsTeachingTest`'s) and lower rungs
  re-derived in the test: Pipes' border and neighbour agreement from the shapes alone; Pearls' pearl rules, a square's
  ways and the small loop by counting lines and crosses; Atoms' room on each line, then with crossings and closed-off
  groups. Pipes' also turns the step's tile all the way round (never wrong, right at its turn) and every other tile a
  quarter (wrong when wet, play on when dry).
- Layout: at 375x537 a practice board sits in what is left above the 144 dp explanation slot and the button row; the
  slot's height is fixed, so a verdict or a hint never moves the board.
- Kings' practice checked at 375x537 and 390x844 in Chromium and WebKit (`tools/render/render.py --home --query
  "screen=training&puzzle=kings&technique=would-empty"`, with taps for a wrong cross and for the step), and on the
  Android emulator through a hint's "Practise this" and a Show me: the board stays put through every verdict.
- Pipes', Pearls' and Atoms' practices and final boards checked at 375x537 and 390x844 in Chromium and WebKit (a Pipes
  no-loop practice turned round three quarter turns to its way: "That's it", never a wrong verdict on the way), their six
  badges on the Achievements page in both schemes, and on the Android emulator from the rules sheet's Train and from
  Settings: a wet Pipes tile turned off every answer fails ("Not quite"), a turn of the step's tile passes, one Atoms bond
  of a double plays on, and a Pearls black pearl's arms pass once both are drawn. (Before the `every` hook, another black
  pearl's correct arms played on silently; they now pass.)
- Who's Who, Cipher and Chess checked at 375x537 and 390x844 in Chromium and WebKit (a practice of each, a wrong move's
  verdict for Cipher and Chess, and each final board), the badges in light and dark, and on the Android emulator (Pixel 6):
  a right Chess key, a wrong Who's Who tick and a right Cipher digit, the board still through each verdict. Two things
  left as they are: at 375x537 Who's Who's clue list (it scrolls) gets one line beside the fixed 144 dp slot, a few lines
  less than the daily game, whose hint slot shrinks below 700 dp (the practice slot could shrink the same way); and a wrong
  Chess move also shows the board's own caption, "No forced mate from here: Undo or Restart", though a practice has neither.

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
| Pipes | 4: border, set neighbour, whichever way 25-30 a tier; no loop 5/4/6 | move (own drill) | 3-56 ms | **done** |
| Shikaku | 4: only fits, only reaches; common cells 0/5/18; what-if 0/6/14 | move | 4-13 ms | include |
| Mosaic | 3: count, biggest swallow, centre 2/5/10 (finish, fill within the limit: not praised) | move | 8-21 ms | include; its teacher can take 1.2 s a hint, and judging asks it per move: measure on the web first |
| Atoms | 6: one neighbour … isolation 0/16/29, what-if 0/0/6 (only way out: never, left out) | move | 4-29 ms | **done** |
| LITS | 6: whole region 5/13/8, overlap, avoid 2x2, letter clash 30/30/30, neighbour 0/15/27, what-if 0/1/14 | move | 26-173 ms | include |
| Nonogram | 4: all 26-30 a tier | move | 4-6 ms | include |
| Chess | 3: mate in one, forcing key 27-30 a tier; quiet key 0/2/9 | move | 1-88 ms | **done** (quiet key from Expert) |
| Tents | 6: no tree, line done, line full 29-30; one spot 1/25/21; shared 0/1/12; served 1/7/20 (touch: never) | move | 2-74 ms | include; its teacher reads grass as notes (see above) |
| Cipher | 5: leading carry, column, column pair 29-30; columns together 0/30/9; pair what-if 0/0/30 (only letter: never) | move | 1-10 ms | **done** (only letter dropped) |
| Tally | 6: all reached; every way 0/30/30, what-if 0/0/30 | move | 2-11 ms | include |
| Dominoes | 6: only place 10 a tier, counting 0/30/10, what-if 0/0/30, the rest 14-30 | move | 6-33 ms | include |
| Mines | 6: subset 0/28/26, pair 0/17/17, count edge 0/0/29, what-if 0/0/1 (count: never) | move | 4-22 ms | include (what-if is one board in 30: ~0.2 s a board) |
| Lamps | 7: all reached; dark 0/11/14 | move | 3-21 ms | include |
| Pearls | 7: pearl's arms and lines 26-30, dead end 18/28/29, small loop 0/30/29, what-if 0/0/30 (pearl ways: never, left out) | move | 5-26 ms | **done** |
| Who's Who | 5: clue, only 29-30; link, house 0/29-30/30; what-if 0/0/30 | move | 1-77 ms | **done**: the steps are logic, the words only labels |
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
2. **Pipes, Pearls, Atoms**: paths and loops. **Done** (Pipes needed its own drill, above).
3. **Mines, Lamps, Tents**: marks and counts (settle Tents' grass-as-notes first).
4. **Shikaku, Dominoes, LITS**: pieces placed by drag or pairs (each placement must stay one emitted state).
5. **Who's Who, Cipher, Chess**: deduction grids and moves (done).
6. **Mosaic** alone: its teacher's cost per judged move needs measuring in a browser first.
