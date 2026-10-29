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
  Pipes, Pearls, Atoms, Who's Who, Cipher, Chess, Mosaic, Mambo, Nonogram, Tally, Mines, Lamps, Tents, Shikaku, Dominoes,
  LITS), each
  with how many of its techniques are passed and a tick once it is finished (its final board passed).
- **A puzzle's techniques** (route `training||…|<puzzle>`): its techniques in the teacher's ladder order
  (`core/TechniqueNames.kt`), each with its name and a tick once passed, and at the foot the final board.
- Both lists show how much is passed as a bar (`PassedBar`) as well as "n of m" (the owner, 1.32: progress that reads
  at a glance), and word hints kindly: the owner read "with a hint, so it doesn't count" as a reproach. A hinted board
  says the run is kept for the next one solved alone; the intro says a hint never breaks the run.
- **A technique** (route `training|<technique>|…|<puzzle>`): a short explanation in the hints' voice, then a practice
  board. The player finds the step and makes it. A right digit adds one to the run; three in a row passes the technique.
  The run shows in the header, after the subtitle on its line (`TitledScreen(beside)`, so the title keeps its width:
  beside the title it wrapped to four lines at 2x font and squeezed the board out at 375x537), as three boxes
  (`RunBoxes`): ticked for each right answer in a row, hollow for each to go, read by a screen reader as "n of 3 in a
  row". A box pops as it ticks (not under reduced motion); a wrong answer empties them; a hinted board leaves them as
  they were. The boxes are 16 dp, inside the subtitle's line height at any font size, so they never change the
  header's height. A technique already passed shows its current re-run in the boxes,
  with "Passed" as the subtitle (not three ticks, which would leave a re-run nothing to show).
- **A pass is celebrated as a solved game is** (the owner, 1.31: "similar to a game congrats. Popup, graphic, confetti,
  sound plays"): the game's own finished frame (`FinishedFrame`, `Confetti`, `ResultsBlock`, `AchievementBadge`), over
  the screen so the board keeps its box. The banner reads "Passed!" between the party poppers, then the technique's
  title and "3 in a row"; the centre shows the progress to the course's next achievement (the owner: "show their
  progress to the achievement"; `passCheer`, tested by `TrainingPassLineTest`). Until every technique is passed, the
  "<Puzzle> trained" badge as a silhouette over one box per technique, the passed ones ticked (the one this pass added
  ticks in a beat after the popup shows, not under reduced motion or after a recreation), and "<Puzzle> trained: n of m
  techniques". The pass that passes the last one shows the achievement as the game shows a new one, then "Final board
  unlocked. Solve it to become a <Puzzle> graduate." A re-pass shows the same progress, nothing announced; once all are
  passed, the badge in colour and, while the final board is unsolved, "The final board is open: solve it to become a
  <Puzzle> graduate." The run's own boxes are not repeated in the popup: "3 in a row" says it, and a second row of
  boxes beside the techniques' read as the same thing. The solve sound (`LocalChime`) and the success buzz play once; confetti
  once (saved, so a recreation replays nothing); under reduced motion no confetti, no flip, no pop, as in a game. One
  button: "Back to <Puzzle> training", or "Continue" when the practice was opened by "Practise this" or "Practise"; it,
  a tap anywhere outside it, or back, all do what back does: to the list with the new tick, or to the game or the
  Techniques page. The owner (2026-10-28): "send the user back to the list when they achieve the goal. Otherwise
  they're likely to get stuck there." So there is no Next board from a pass: it stays locked under the popup.
  Every run that reaches three does this, on a technique already passed too (1.19 said only "That's it." there and kept
  the player on the board, which read as no pass at all); achievements are announced once. The celebration showing is
  saved (`rememberSaveable`), and the pass is recorded once, as before. A wrong digit ends the run
  and shows the reasoning that was there to find. The next board is one tap away. Hints work as in a game (nudge, why, show me); a board where a hint was opened does not count towards
  the run and does not break it.
- **The final board** opens once every technique of that puzzle is passed: a whole Expert board whose own walk by the hint
  ladder needs every technique. Solving it without "Show me" earns the second achievement, and is celebrated as a pass
  is: "Graduated!" (stamped in, as a game's milestone), "<Puzzle> graduate", "The final board, solved", and the
  "<Puzzle> graduate" achievement the first time, its badge otherwise, then Training's progress as a whole,
  "Training: n of 21 courses graduated"; "Back to <Puzzle> training" (a solve with a
  Show me is not celebrated and stays, to start again). The solve is answered once, saved across a recreation. Hints are allowed (they teach).
- **Ways in:**
  - **Home's Training mode**, the third segment of the mode row (`Daily/Random · Levels · Training`): the tier row gives
    way to one line, "N of 21 graduated · M techniques passed", and the grid shows only the puzzles with a course, in the
    home grid's order, each tile saying "3 of 8" (techniques passed of its techniques) and ticked once graduated. A tap
    opens that course, as the rules sheet's Train does (`Route.Training(fromHint = true)`, `practiseFrom` = Home): back
    from it is Home, still in Training (the mode is saveable state, never stored), and back from a technique opened there
    is the course. The segment's second line is "N of 21": **graduated** courses (`Course.graduated`: the final board
    passed, or every technique where a course has none, the list's tick) of `COURSES.size`; `Training.tally` counts both
    numbers. The mode, like the others, is not stored: the app always opens on Daily. Today's three folds away in
    Training and the grid slides up into its room, and back down as it returns (the owner, 1.33: the gap it left
    "irks me"); the grid keeps its height through the fold, so its columns never change mid-slide.
  - **"Train: practise the techniques"** on the rules sheet (the play screen's "?") of a puzzle that has Training: opens
    that puzzle's techniques; back from there returns to the game, and back from a technique opened there returns to the list.
  - **"Practise"** beside each technique on Statistics → Techniques that has a practice: opens it; back returns to the page.
  - **"Practise this"** in a hint's panel, beside Close, once the hint is explaining (not a mistake, not the fallback):
    opens that technique's practice; back returns to the game, which is saved as on any leave.
  - **Settings**' Training row stays, opening the list of puzzles (back from it is Settings).
- **Achievements** (appended, two per puzzle): "<Puzzle> trained" for passing every technique, "<Puzzle> graduate" for
  then solving the final board: Sudoku's two (`sudokuTrained`, `sudokuGraduate`), then `cagesTrained`, `cagesGraduate`,
  `inequalityTrained`, `inequalityGraduate`, `regionsTrained`, `regionsGraduate`, `kingsTrained`, `kingsGraduate`,
  `pipesTrained`, `pipesGraduate`, `pearlsTrained`, `pearlsGraduate`, `atomsTrained`, `atomsGraduate`, `whoswhoTrained`,
  `whoswhoGraduate`, `cipherTrained`, `cipherGraduate`, `chessTrained`, `chessGraduate`, `mosaicTrained`,
  `mosaicGraduate`, `mamboTrained`, `mamboGraduate`, `nonogramTrained`, `nonogramGraduate`, `tallyTrained`,
  `tallyGraduate`, `minesTrained`, `minesGraduate`, `lampsTrained`, `lampsGraduate`, `tentsTrained`, `tentsGraduate`,
  `shikakuTrained`, `shikakuGraduate`, `dominoesTrained`, `dominoesGraduate`, `litsTrained`, `litsGraduate`
  (Who's Who's, Cipher's and Chess's badges `?`, `A+B`, `M`; Mines', Lamps' and Tents' a drawn mine, `L`, `T`; a badge
  glyph must not start with `#` unless it names a drawn shape). They
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
  - Mosaic: all three on Standard. Its tiers are board size, sections and palette, not depth of reasoning, and Standard
    walks reach every technique (counting and the biggest swallow on 60 of 60 boards, the centre on 5); the smallest
    boards are the clearest to learn on.
  - Mambo: pair, sandwich, link and quota met on Standard, one short on Hard, the what-if on Expert (`MamboTeacher.depth`).
  - Nonogram: all four on Standard (58-60 of 60 Standard boards reach each).
  - Tally: the four shortcuts on Standard, every way on Hard, the what-if on Expert (`TallyLogic.accepts`).
  - Mines: single numbers on Standard, subset and pair on Hard (57 and 33 of 60 boards), the count against the numbers and
    the what-if on Expert (57 and 4 of 60).
  - Lamps: zero, done, room and only light on Standard; starve and dark on Hard (51 and 22 of 60); the what-if on Expert.
  - Tents: no tree and the two line rules on Standard; one spot, shared and served on Hard (52, 3 and 11 of 60).
  - Shikaku: only fits and only reaches on Standard, common cells and the what-if on Hard (8 and 11 of 60 Hard boards).
  - Dominoes: the three "only" steps and region values on Standard, counting on Hard, the what-if on Expert (each tier
    needs exactly its level: `Dominoes.neededTechnique`).
  - LITS: whole region, overlap, the 2x2 and the letter rule on Standard, the neighbour on Hard, the what-if on Expert.
    Hard reaches the what-if on only 3 boards in 60 (Expert 25), so its practice is drawn from Expert: on Hard each
    board would cost some twenty seeds' generation and walk.
- **What counts as right** (digits; Kings and the general shape below): the step's own digit, which is what the hint's `isReached` checks. Another correct digit
  elsewhere also counts: the position has no simpler step anywhere (each teacher tries every simpler technique on the whole
  board first), so a correct digit there was found with this technique or a harder one. It is answered about its own
  square ("That's it. 7 goes there.", outlined where it went), never with the step's explanation of the other square:
  1.19 said "That's it." and then explained the step's square, which the owner read as being told they were wrong and
  should have filled that one. A move drill does the same with the instance the player completed, in that instance's own
  words and cells (`Course.made`, `verdictLine`, `TrainingVerdictTest`), and so do the courses with their own drill:
  Pipes explains the tile the player turned (any tile the technique settles), Mosaic the fill they made (the
  technique's reasoning about that fill, `MosaicTeacher.explainFill`), and Chess the key they played (`ChessTeacher.deduce`
  with `prefer`; most practice positions have one key, a few mates in one have several). A wrong digit is judged against
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
    list for the technique (it already listed every placement of the first technique that finds any). Chess has none (its
    step's `isReached` already accepts every key the prover names, which on a walked position is every move that still
    mates in time; its own drill only re-explains the key played, `ChessDrill`).
  - **Wrong** = the teacher's `teach` calls the new board a mistake, which each teacher defines as "no legal answer keeps
    this" (never "differs from the stored answer": LITS's and Pipes' several answers, PR #15).
  - **Neither** = play on: one cross of three, an erasure, or a mark that is true but that no step of the technique
    justifies. That last is never passed, because a lone cross is right by chance on most squares of a Kings board, and it
    gets no "true, but not this technique" note either: nothing can tell it from the first cross of a step the player is
    still making, so a note would fire on honest progress.
  - **Progress** (2026-10-28, the owner dislikes a player left without feedback): a play-on move that is a right part of
    an instance of the technique not yet finished shows "Right so far: 3 of 12. Keep going." in the slot, in place of the
    explanation (`Course.progress`, `MoveDrill(view)`). It is no verdict: it neither counts, ends the board nor breaks
    the run, and the board stays interactive. Per instance (the teacher's own first, then `every`'s), "need" is the
    targets whose mark the instance's own Show me changes and "done" those the board already holds; the line shows when
    every square the player changed holds what that Show me puts there (a flood or a sprout it makes too counts) and
    0 < done < need. So it agrees with every answer, names a count and never which squares are left, and a true mark no
    instance justifies still plays on silently. `view` is the course's marks indexed like its targets: Kings, Tents
    (`seen`, so grass already sprouted is not a move), Mines, Lamps, Nonogram, LITS, Tally, Mambo, Pearls (edges after
    the squares, `PearlsTeacher.edgeIndex`), Atoms (gaps after the atoms, `pairCell`) and Who's Who (a table square, n
    marks wide). Cipher, Shikaku, Dominoes and Chess have none: every step is one move (a letter, a block, a domino, a
    chess move), as are Pipes' tile and Mosaic's fill. `TrainingProgressTest` makes each several-square step one square
    at a time from its Show me on two practices per technique of eleven courses, and checks an unjustified true grass
    (Tents) and cross (Kings) get no line.
  - Mambo, Nonogram and Tally supply `every` too (`MamboTeacher.steps`, `NonogramTeacher.steps`, `TallyTeacher.steps`
    filtered to the technique: every Mambo instance in ladder order, every line's step, every Tally what-if within the
    hint's three lines; each `deduce` is still their first, so no daily board moved). Their tests make a different
    instance (right) and an unjustified true mark (play on) on every practice that has one.
  - Shikaku, Dominoes and LITS supply `every` from `ShikakuTeacher.instances`, `DominoTeacher.instances` and
    `LitsTeacher.instances`. Shikaku's and LITS's rung functions take an optional list to collect every step into and
    otherwise return their first, as before, so `deduce` keeps main's direct path; LITS's what-if still takes the
    shortest chain. Not sequences: `deduce` runs inside those generators' gates, and `sequence { }` made a year of LITS
    daily boards about 25% slower (Expert mean 111 ms to 141-157 ms on the JVM) and Shikaku Expert 15-30% slower; with
    the collector the year matches main within noise (LITS Expert 99.6 against 98.3 ms, worst 216 against 216 ms;
    Shikaku Expert 2.55 against 2.81 ms). Dominoes' what-if list is a sequence, since its ladder's what-if is reached
    only after steps 1-5 are spent, and a year of Dominoes boards stayed within noise (Expert 20.0 against 20.8 ms).
    The training tests pin each list's first step to `deduce`'s pick over the first 30 practice seeds' Expert walks. Dominoes' instances of a rung are the placements settled once that rung's level and
    those below it have struck all they can, named as the hint names them (so an "only place" practice passes another
    domino with one place left, not a square with one partner); its what-if's, every square a what-if settles.
  - Only proved boards: `Course`'s last parameter, `generate`, is a seed's board or null to skip the seed. It defaults
    to `puzzle.generate`; Shikaku, Dominoes and LITS pass their `generateVerified`, so no practice or final board is one
    the generator did not prove (LITS's `generate` can ship an unproved floor; the other two a fixed last-resort board,
    which would deal the same practice twice).
  - Judging asks the teacher, so every emitted state is judged on `Dispatchers.Default`; a verdict for a state the player
    has already moved on from is dropped and the newer state judged instead.
  - `MoveDrill(settleMillis)`: a wrong verdict waits that long before it shows, so a newer move drops it. Mambo needs it
    (1 s, the board's own caption delay): a tap cycles moon, sun, empty, so every sun is a moon first, and that moon is
    a mistake whenever the answer is a sun. Without it a player making a sun step failed on the first tap. Right and
    play-on verdicts are not held. Every other course leaves it at 0.
- The practice screen is the same for both: the board in what is left above the slot (explanation, hint or verdict) and
  the button row, so feedback never moves it; the verdict outlines the step's targets and marks its cited squares more
  quietly. The slot is 144 dp, and below 700 dp of window it gives way in proportion, never under 96 dp (110 dp at 537,
  a phone browser with both toolbars), as the play screen's hint slot did before it became a popover; its height is
  fixed for a given window, and longer text scrolls. The final board's slot is the same.
- A practice Chess board that a wrong move has lost reads "No forced mate from here." under it: the daily game's
  "... Undo or Restart" names buttons a practice does not have (the board takes it from `interactive`, false once a
  verdict is in; the daily game's board is interactive whenever it is lost, so its caption is unchanged).
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
  - a fill that keeps the limit but is no instance is never "Not quite" (the owner, 2026-10-28: a fill that still wins
    is never wrong), nor right. The step is then gone and nothing later on that board could be judged, so the slot says
    so (`Course.aside`: "That fill works, it keeps you in the limit. This practice is after a different technique, so
    try it on another board.") and the board locks with Next board, no outline. It neither counts nor breaks the run. It used to
    play on silently, which left the player stuck with no feedback (the owner dislikes that).

  Guessing rarely passes: of every fill on 10 practices each, 19% are right on counting, 13% on the biggest swallow, 6%
  on the centre.
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
- **Mosaic** (Standard boards): first board through the cache 13 ms counting, under 1 ms the biggest swallow, 1 ms
  the centre; each next board a mean of 0-2 ms, at most 5 ms (the centre, 5 of 60 Standard boards). Judging a fill,
  every fill on 10 practices of each technique: at most 0.2 ms on the JVM, 0.6 ms in Chromium and 1 ms in WebKit (wasm,
  `?trainprobe`-style timing in the page, not kept); the hint on a practice board at most 1 ms. Far under the 300 ms
  bar, so Mosaic ships: practice boards are small, and the 1.2 s hint was an Expert board mid-game. Walking a whole
  board costs a mean of 1 / 3 / 38 ms by tier; the final board's generation 1-2 ms in the browser.
- **Mambo** (move drill, through the cache): pair 32 ms first, then a mean of 3 ms a board (max 7); sandwich, link and
  quota met 1-9 ms first, 1-4 ms next; one short 13 ms first, 7 ms next (max 22); the what-if 22 ms first, 20 ms next
  (max 56). Standard reaches pair, link and quota met on all 60 boards and sandwich on 51; every Hard board reaches one
  short, every Expert board the what-if.
- **Nonogram:** every technique 0-4 ms first and 0-2 ms a board after. Standard boards reach the full line on 58 of 60,
  the other three on all 60.
- **Tally:** line done 9 ms first, the rest 0-5 ms first; next boards a mean of 0-7 ms, at most 17 ms (the what-if).
  Standard reaches the first three on all 60 and all left on 26; every Hard board reaches every way, every Expert board
  the what-if.
- **Shikaku** (move drill): the first board of each technique 1-20 ms through the cache, each next 1-5 ms (max 35 ms,
  common cells); 30 practices of common cells or the what-if 120 ms. Standard reaches only fits on 60 of 60 boards and
  only reaches on 36; Hard adds common cells on 8 and the what-if on 11; Expert 41 and 24.
- **Dominoes** (move drill): the three "only" steps and region values 1-37 ms first, 1-3 ms a board after; counting 4-7
  ms first, 5-7 ms after; the what-if 28-37 ms first, 18-20 ms a board after (max 44 ms). Only place is the rarest
  "only" step (18 of 60 Standard boards).
- **LITS** (move drill, proved boards only): whole region 159 ms first, 70 ms a board after (max 442 ms), since 10 of
  60 Standard boards have a four-square region left for it; overlap, the 2x2 and the letter rule 11 ms first, 13 ms
  after; the neighbour 22 ms first, 24 ms after; the what-if 195 ms first, 198 ms a board after (max 730 ms; 25 of 60
  Expert boards reach it). In the browser (the web build, 390x844, the page's own load subtracted, on a busy machine)
  the first board costs about 0.15-0.2 s for the whole region and 0.8-1.1 s for the what-if, in Chromium and WebKit
  alike: wasm has one thread, so "Finding a board..." stays up that long. Judging a move asks LITS's teacher, whose
  mistake check searches for an answer that keeps the shading: on the JVM a judged step takes 0.1 ms on average and at
  most 8 ms (the what-if's), so it is no cost in a browser either, and a wrong square's "Not quite" showed at once.
- **Judging** (`TrainingMeasureTest`'s JUDGE lines, the step made, per practice): Shikaku and Dominoes under 0.5 ms,
  Kings at most 2.4 ms, LITS at most 8.3 ms.
- **Final boards** (Expert, the first practice seed whose walk needs every technique, each pinned by a test):
  Sudoku index 219 (one in the first 400); Cage Sudoku index 0 (33 of the first 60); Inequality index 4 (17 of the
  first 60); Regions index 0 (all 60); Kings index 1 (17 of the first 60); Pipes index 1 (15 of the first 60); Pearls
  index 0 (54 of 60); Atoms index 1 (10 of 60); Who's Who index 0 (all 60); Cipher index 0 (16 of the first 60); Chess
  index 6 (13 of the first 60); Mosaic index 4 (17 of the first 60; Standard 5 of 60, Hard 14); Mambo index 0 (all 60);
  Nonogram index 0 (54 of the first 60); Tally index 0 (58 of the first 60); Mines index 44 (one of the first 60);
  Lamps index 4 (31 of 60); Tents index 34 (5 of 60); Shikaku index 15 (5 of the first 60); Dominoes index 2 (3 of the
  first 60); LITS index 5 (5 of the first 60).
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
- **Any instance** (Mines, Lamps, Tents): `every` is `MinesTeacher.steps`, `LampsTeacher.steps` and `TentsTeacher.steps`,
  every step of the technique on the practice board, taking the walk's own flags, crosses and grass as known.
  `MinesLogic.facts` and `TentsTeacher.facts` are the ladder's own loops as lazy sequences, kept beside the untouched
  `nextFact`, which the generators' walks still call: walking a year of Tents boards through the sequence's first took
  1.5-3x as long as through `nextFact` (8-58 ms against 31-97 ms a tier, warm). Tests hold each sequence's first step to
  `nextFact`'s along whole walks, so the lists cannot drift from the ladder; `LampsLogic.facts` collects each rule's instances (a what-if: every supposition that breaks within
  three links, not only the shortest). Judging a move that is no step, which asks for them all, takes under 2 ms on the
  JVM for every technique (`JUDGE` lines). Tests: another instance is right (Mines' all found and all mines, Lamps' zero
  and done, Tents' one spot), and a true reveal, cross or grass that no instance justifies gets no verdict.
- **Left out:** Mines' "counting the mines left" and Tents' "grass around a tent", which no walk reaches. The count rule
  needs the hidden squares to be all mines or all safe, which the count against the numbers (tried after it, and more
  general) always settles first in practice. A tent's surrounding grass is drawn by itself (`TentsState.seen`), so that
  step is always already shown and the teacher skips it.
- Mines', Lamps' and Tents' final boards: Mines index 44 (the only one of the first 60 whose walk needs all six: the what-if is rare), Lamps index 4
  (31 of 60), Tents index 34 (5 of 60). `MinesTrainingTest`, `LampsTrainingTest` and `TentsTrainingTest` pin them, and
  check 6 practices per technique against `MinesOracle`, `LampsOracle` and `TentsOracle` and lower rungs counted in the
  test (Mines' two numbers brute-forced over the mines their shared squares hold; Lamps' starve and dark by placing the
  lamp and walking its rays); right, wrong (a mine, a wrong flag; a wrong lamp or cross; a wrong tent or grass) and play-on.
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
- `MamboTrainingTest`, `NonogramTrainingTest` and `TallyTrainingTest` check 6 practices per technique as `KingsTrainingTest`
  does: one answer by `MamboOracle`, `NonogramOracle` and `TallyOracle`; the walked marks agree with it; each line's rung
  classified in the test by plain counting (Mambo's runs, gaps, links and halves; Nonogram's runs packed left and right
  and every layout of a line tried; Tally's sums and every subset of a line's open numbers), so the technique is the
  simplest step on the board; right, wrong (a wrong cross or a wrong circle too, as the hints flag them) and play-on
  moves; and the final board.
- Layout: at 375x537 a practice board sits in what is left above the explanation slot (110 dp there) and the button
  row; the slot's height is fixed, so a verdict or a hint never moves the board.
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
  then left (at 375x537 Who's Who's clue list got one line beside a fixed 144 dp slot, and a wrong Chess move showed
  "... Undo or Restart") are fixed: the slot now gives way below 700 dp (three clues show), and the caption reads
  "No forced mate from here."
- Mosaic's three practices and its final board checked at 375x537 and 390x844 in Chromium and WebKit (`render.py --home
  --query "screen=training&puzzle=mosaic&technique=count"`, with taps for a losing fill and for the step), its badges on
  `?screen=achievements` (a harness hook added with it), and on the Android emulator: a hinted step, a wrong fill, the
  board still through each verdict. The 375x537 board is small (the slot and buttons take what Kings' take).
- Shikaku's, Dominoes' and LITS' practices and final boards checked at 375x537 and 390x844 in Chromium and WebKit
  (LITS with taps for a right square then a wrong one: the board's box measured the same before and after the
  verdict), and on the Android emulator: a wrong LITS square, a wrong domino laid from the tray, and a Shikaku Show me.
  The badges ("2x3", "6|6", "L") rendered at true size on the Achievements page.
- Final check of all 21 courses (2026-10-28), on the Android emulator (Pixel 6, API 36, light and dark): each opened from
  Settings → Training, its first technique, one right move made by hand, "That's it" and the board's box the same before
  and after; Sudoku's last square and Kings' last open square passed (three in a row, "Passed! 3 in a row.", back to the
  list with the tick); the rules sheet's Train and a hint's "Practise this" on Pipes and Sudoku; a wrong Chess move
  ("No forced mate from here."); a Mosaic biggest-swallow fill that keeps the limit (the aside note, Next board). A
  practice of every course rendered at 375x537 and 390x844 in Chromium and WebKit, no console error.
  Behaviours seen then: a step that marks several squares (Tents' no-tree grass, all twelve) passes only when every one
  is made, and a single right square played on with no verdict; the Training list opened at its top again when the
  player came back from a course. Both since fixed (below).
- Polish (2026-10-28): the progress line above; the Training list, a course's techniques and the Techniques page keep
  their scroll place across a practice and back (`KeepPlace` in `DaybookApp`: a `SaveableStateHolder` per list route,
  lists only, since a game's state lives in its save; Achievements links nowhere, so it has nothing to come back to);
  and the verdict outline on Mosaic and LITS, whose palettes are mid-tones (violet, rose, teal) that a fixed rim
  colour all but vanished on in one scheme, now takes per square whichever of the scheme's ink and page stands out on
  the fill (`standOut`), Mosaic's band and rim swapping with it. The other boards draw their rims over surface tones,
  where the fixed ink-on-page rim reads in both schemes.

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
| Mambo | 6: pair, sandwich, link, quota met 30/30/30; one short 0/30/30; what-if 0/0/30 | move | 17-41 ms | **done** |
| Pipes | 4: border, set neighbour, whichever way 25-30 a tier; no loop 5/4/6 | move (own drill) | 3-56 ms | **done** |
| Shikaku | 4: only fits, only reaches; common cells 0/5/18; what-if 0/6/14 | move | 1-20 ms | **done** |
| Mosaic | 3: count, biggest swallow, centre 2/5/10 (finish, fill within the limit: not praised) | move (own judge) | 0-13 ms | **done** |
| Atoms | 6: one neighbour … isolation 0/16/29, what-if 0/0/6 (only way out: never, left out) | move | 4-29 ms | **done** |
| LITS | 6: whole region 5/13/8, overlap, avoid 2x2, letter clash 30/30/30, neighbour 0/15/27, what-if 0/1/14 | move | 11-195 ms | **done** (what-if from Expert) |
| Nonogram | 4: all 26-30 a tier | move | 4-6 ms | **done** |
| Chess | 3: mate in one, forcing key 27-30 a tier; quiet key 0/2/9 | move | 1-88 ms | **done** (quiet key from Expert) |
| Tents | 6: no tree, line done, line full 29-30; one spot 1/25/21; shared 0/1/12; served 1/7/20 (touch: never) | move | 0-52 ms | **done** (grass no answer keeps is wrong in practice) |
| Cipher | 5: leading carry, column, column pair 29-30; columns together 0/30/9; pair what-if 0/0/30 (only letter: never) | move | 1-10 ms | **done** (only letter dropped) |
| Tally | 6: all reached; every way 0/30/30, what-if 0/0/30 | move | 2-11 ms | **done** |
| Dominoes | 6: only place 10 a tier, counting 0/30/10, what-if 0/0/30, the rest 14-30 | move | 1-37 ms | **done** |
| Mines | 6: subset 0/28/26, pair 0/17/17, count edge 0/0/29, what-if 0/0/1 (count: never) | move | 1-24 ms | **done** (what-if kept: 42 ms a board through the cache) |
| Lamps | 7: all reached; dark 0/11/14 | move | 0-15 ms | **done** (a cross where the answer has a lamp is wrong in practice) |
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

1. **Mambo, Nonogram, Tally**: line rules on a grid, cheap, every rung reached. **Done**: no technique dropped (every
   rung is reached within milliseconds); Mambo's tap cycle needed `settleMillis` (above).
2. **Pipes, Pearls, Atoms**: paths and loops. **Done** (Pipes needed its own drill, above).
3. **Mines, Lamps, Tents**: marks and counts. **Done** (a course's `wrong` flags Tents' grass and Lamps' crosses no
   answer keeps, and Mines' lost board).
4. **Shikaku, Dominoes, LITS**: pieces placed by drag or pairs (each placement must stay one emitted state). **Done**:
   every rung kept (none was unreached), each board's drag or tap sequence already emits one state.
5. **Who's Who, Cipher, Chess**: deduction grids and moves (done).
6. **Mosaic** alone: done (its own judge, above).
