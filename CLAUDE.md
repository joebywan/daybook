# Daybook — working notes for Claude

A daily logic-puzzle Android app. Daily puzzles, generated on device, no ads, no
subscription, works offline. Read this before changing anything; it exists so you don't
rediscover what has already been learned here the hard way.

## Build

Build with **Java 17**: it is what CI uses. Gradle 9 runs on newer JDKs (Gradle 8.14 refused the system
default, Java 25), but only 17 is checked; moving CI's JDK is its own change (`docs/TODO.md`).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/user/Android/Sdk
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

`extraWarnings` is **on**. It was turned on after a probe showed the build reported nothing
for unused declarations, which made "compiles with no warnings" a much weaker claim than it
sounded. Do not turn it off.

## Architecture

- `core/PuzzleType.kt` — the contract. Adding a puzzle is one file in `puzzles/` plus one line
  in `core/PuzzleRegistry.kt` (and its place in `PuzzleOrder.GENTLEST`, the new player's order); home grid, archive, streaks, stats, hints and saves pick it up.
  What every puzzle must have beyond that (tiers, proof, hints, walkthrough, tests): `docs/PUZZLE_STANDARDS.md`.
- Four puzzles show players a different name from their code and `id`: Snap is **Trail**, Mambo **Sun & Moon**, Tower
  **Codebreaker**, Pointers **Arrows** (`displayName`, the owner's choice, 2026-10-25). This file uses the code names.
- `generate(seed, difficulty)` **must be pure**. Daily boards come from
  `hash(date, puzzleId, difficulty)`, so purity is what makes the whole archive free and offline.
- `PuzzleState` is a sealed `@Serializable` interface living in `puzzles/` — Kotlin requires
  sealed implementations to share the sealed type's *package*, not merely its module.
- `core/TierNotes.kt` says, one line per tier, what the player sees change (a size, a count, what is given; never
  "harder"); the rules sheet ("?") shows them with the board's tier marked. `TierNotesTest` fails a registered puzzle
  without three, and a tier change needs its line changed in the same PR.
- `Preview(modifier)` draws a fixed motif for the home grid. **Never call `generate()` in it** —
  every one of them draws on every composition.

## Teaching: hints that explain, and walkthroughs

All puzzles teach (Snap and Pointers through their walkthrough only; Pointers and Mines have no undo). Shared code: `core/Teaching.kt`, `ui/teach/Hints.kt`,
`ui/tutorial/TutorialRunner.kt`, `ui/play/PlayScreen.kt`. How to write a teacher: `docs/PUZZLE_STANDARDS.md`
section 7. The settled play-screen UX (popover, finish frame, solve sound) and the measured fallback table:
`docs/TEACHING.md`. Keep that table current when a teacher changes. Statistics opens a Techniques page (each puzzle's
praised techniques, ticked once a hint explained one; stored as the `techniques_met` key set, not on the save; a record,
never a drill): `docs/REWARDS.md` (Techniques). Training is the drill, separate from that record: for Sudoku, Cage
Sudoku, Inequality, Regions, Kings, Pipes, Pearls, Atoms, Who's Who, Cipher, Chess, Mosaic, Mambo, Nonogram, Tally, Mines, Lamps, Tents, Shikaku, Dominoes and LITS, each technique practised on real positions found by walking generated boards by
hints (`core/Training.kt`: one `Course` per puzzle, a new one its own file in `core/training/` plus one line appended to
`Training.COURSES`; `PlacementDrill` for digit teachers, `MoveDrill` for the rest, judged by the teacher's own mistakes and
any step of the technique being reached (`MoveDrill(next, every)`), never the stored answer (Pipes judges its own way: a
tap turns a tile a quarter at a time, so the step's tile is never wrong on its way round; Mosaic's `MosaicDrill` applies
the same rule to a fill, judged by one search question, and a fill that keeps the limit outside the technique gets
`Course.aside`'s note and locks the board, never silence; a right move is explained and outlined as the move the player
made, `Course.made`, in every course, Chess's other keys and Pipes' other tiles included), plus the course's `wrong` for a lost board or a note the teacher
never reads that no answer keeps (Mines' mine, Tents' grass, Lamps' cross), a play-on move that is a right part of an unfinished instance shows "Right so far: k of n" (`Course.progress`, `MoveDrill(view)`; never a verdict, never which squares are left), a wrong verdict held `settleMillis` where a
move passes through a wrong state (Mambo's sun is a moon first); found practices cached per session; a course whose
`generate` can ship an unproved board passes its `generateVerified` as `Course.generate`, so practices skip that seed;
`ui/training/`; its own
`training_passed` key set, `puzzleId|technique`; two achievements per course, built from `COURSES`'s order; keys through
`hintKeys`, shared with the play screen; a technique no practice search reaches is left out, and a board already
dealt is never dealt again), opened from Home's Training mode (a course per tile, `ui/home/TrainingTiles.kt`; opened as
the rules sheet's are, so back is Home), the rules sheet's "Train", the Techniques page's "Practise", a hint's
"Practise this" and Settings: `docs/TRAINING_SPEC.md`. A pass and a graduation are celebrated with the game's own
finished frame (`FinishedFrame` with no clock and a `centre` badge, `Confetti`, `ResultsBlock`, the chime and
`haptics.success`), shared, never copied, showing the progress to the course's next achievement (`passCheer`,
`graduateCheer`); the run shows as `RunBoxes` beside the subtitle. What must not be broken:

- A puzzle's first board opens on its walkthrough, once (`autoWalkthrough`: never solved, never opened, not a level
  board, no saved moves; Skip one tap). The offer line and the "?" sheet are the way back. `tools/render/render.py` marks
  every walkthrough seen unless `--first-visit`, so a fresh context still shoots the board.
- UX, settled by the owner: tap 1 nudges, tap 2 explains, the **player makes the move**, and the panel confirms
  when `isReached` sees it. Only an explicit "Show me" applies it. **One hint is counted per deduction opened.**
- **The solver must not be able to see the answer.** A teacher's reasoning entry point takes only visible
  state; the answer is read only to flag mistakes and for the fallback, which says so openly.
- **A mistake means "no legal answer keeps this", not "differs from the stored answer"** (the PR #15 bug).
- **Measure the fallback rate; don't assume it.** Test soundness against an independent solver.
- The hint panel is a popover, so the board's box never changes size; boards must report their highlight
  (`core/HighlightBounds.kt`) or the popover assumes "below".
- Keep teacher files free of `java.*`: the web compiles them automatically.

## Lexicon (the word game)

Mastermind for words: `puzzles/Lexicon.kt` (board, keyboard, walkthrough), `LexiconRules.kt` (state, marking,
the tier shapes, `WordList`), `LexiconTeacher.kt`, `LexiconKeys.kt` (hardware keys), `LexiconWords.kt`
(generated). `id = "words"` is hashed into every seed: never rename it.

- **The word is an index into a sorted list**, so the lists' order is part of the contract. `LexiconWords.kt` is
  generated by `tools/words/build.py` from SCOWL (the npm module `wordlist-english` 1.2.1; notice in
  `docs/word-lists/LICENSE-SCOWL.txt`, which has to travel with the lists). Adding or removing a word moves
  every daily board after it, so `BoardPinTest` goes red on purpose. Screening lives in
  `tools/words/exclude.txt`: offensive, informal, obscure, plurals the stem rule cannot see. Answers are
  dialect-neutral SCOWL levels 10/20/35 minus inflections; guesses are every plain a-z word to level 70 in
  every dialect, so colour/color style pairs are both accepted and neither pair is ever an answer, plus every word in both
  ENABLE and WordNet (two sources agreeing keeps out WordNet's proper names and numerals) and `tools/words/guess-extras.txt`
  (modern words no list has: larp). The owner wants guesses wider than answers; answers stay well known.
- **Tiers: 5 letters / 6 guesses, 5 letters / 5 guesses, 4 letters / 7 guesses.** Any real word is a legal guess on
  every tier: there is **no hard mode** (the owner: don't force players to use their clues, "if they don't, that's on
  them"; an earlier build had one, and it is gone). Four letters is the *harder* length: a player who always guesses
  a word still possible wins every five-letter board in six and 98% in five, but only 94% of four-letter boards in
  six and 98% in seven (families like -ATE); one who also spends turns on words that cannot be the answer wins them
  all. So Expert gets a guess more than Hard and is still the tier that asks the most. `LexiconBalanceTest` measures
  both players; the constants alone prove nothing. A saved game from the hard-mode build still loads (the old
  `hard` key is ignored) and plays under the new rules.
- **Hints give a clue, not the word.** `ONLY_WORD`, `CHOOSE` and `OPENER` describe the word as a crossword-style clue
  (`WordList.clue`); only "Show me" types it. The clues are `tools/words/clues.txt` (one per answer, hand-written; the
  build fails if an answer has none), compiled into `LexiconClues.kt`. A new answer needs a clue in the same change;
  `LexiconCluesTest` checks coverage, that no clue contains its word, and that no two answers share one.
  Answers were not enlarged: SCOWL level 40 for answers is slang and vulgarity. Clues are neutral English (the app is
  Australian-built, played anywhere): `build.py` lints both clue files and fails on a capitalised name mid-clue, a
  sport, or a country tag ("in America"); a flagged clue that is fine goes in `tools/words/clue-lint-allow.txt` as its
  exact line (`python3 tools/words/build.py --lint` needs no inputs). Prefer a sense every dialect shares; a word whose
  only sense is regional is tagged ", in Britain" / ", in America".
- **One breakfast-table screen for every word game** (`tools/words/grim.txt`; the owner, 2026-10-26, reversing "grim answers
  such as vomit stay"): death, violence, bodily fluids, words near a slur, LOONY and PRICK. `build.py` takes them out of the
  answers, so they leave Lexicon's answers, Word Wheel's grid words and wheels, and every Cipher, Codeword and Crossword word
  built from those; they stay guesses and bonus words. An ordinary word with a grim second sense stays (die, hang, knife).
  The bar is in the file's header. `GrimWordsTest` reads the file and fails if any game shows one. A change moves daily
  boards: rebuild every generated list and re-pin.
- **Daily words go round a cycle from 2026-11-01** (`SeedHash.LEXICON_CYCLE_FROM`): a hashed seed gave even odds of a repeat
  within two months. From that day `SeedHash.daily` gives `"words"` a tagged seed carrying the day (`lexiconCycleDay` reads it
  back), and `LexiconRules.cycle` maps it through one fixed shuffle of the tier's answer list per round, so no word recurs within
  a round (1,768 / 1,768 / 1,347 days); round 0 puts the words drawn on hashed days of the same length at its back. Earlier days
  keep their hashed seeds, so the archive, saves (which hold the word anyway) and completions are untouched; Random and Levels
  never cycle. `LexiconCycleTest` checks two rounds a tier and the cutover. Changing the list or the shuffle moves every cycled day.
- **The opener rotates**: `LexiconState.opener` (defaulted 0, so an old save opens with RAISE/TALE) picks one of seven strong
  openers per length (`LexiconTeacher.openers`, all within 12% of the best's expected words left), drawn from the seed apart
  from the answer, so it tells nothing about the word. The best-guess fallback says how its word splits what is left ("it can
  be marked 5 ways, and none leaves more than 2"), read off the candidates alone.
- **A probe is never a mistake.** The teacher's only mistake is a row that is not a word. Calling a legal probe
  wrong is the PR #15 bug.
- **A pin is a fact about the marks alone**, so `LexiconTeachingTest` checks it against every accepted word that
  fits, not only the answer list. `ONLY_WORD` and `CHOOSE` do read the answer list, say so, and count as the
  fallback.
- The typed row and the "Not in the word list" note are `remember` state, never `PuzzleState`; the note sits in
  the header line whose room is always reserved, so a refusal cannot move the board.

## Shikaku

Cut the grid into rectangles, one number each, the number its area. `puzzles/Shikaku.kt` (state, generator, `countTilings`, board,
walkthrough), `ShikakuTeacher.kt`. `id = "shikaku"` is hashed into every seed: never rename it.

- **Tilings are grown, not cut** (`tile`: the first uncovered square in reading order is the top-left of whatever covers it, a
  rectangle of 2 to the tier's max area drawn uniformly among those that fit; a boxed-in square redraws). Recursive bisection
  made every board guillotine; now about half are not (`ShikakuClueTest` checks it with its own checker). No clue of 1, ever.
- **A board ships only if `countTilings` proves one answer and the teacher's walk from the blank board finishes without the
  fallback and needs its tier's technique** (`Shikaku.depth`, `accepts`): Standard nothing past only-reaches, Hard something past
  only-fits, Expert at least one common-cells or what-if. On Expert a clue avoids its rectangle's corners (it measurably
  deepened boards). Hints therefore never fall back on a generated board. `lastResort` is the board of one fixed seed, proved
  by a test; a year per tier never reaches it (`FallbackTest`). Changing the ladder changes every board (parity pins go red).

## Atoms (bridges)

Hashiwokakero as chemistry: bond atoms with single or double lines, no crossings, one molecule. `puzzles/Atoms.kt` (state, generator,
`countSolutions`, board, walkthrough), `AtomsTeacher.kt`. `id = "atoms"` is hashed into every seed: never rename it.

- **Tiers: 7x7 with 10 atoms / 9x9 with 18 / 11x11 with 28, and a depth gate** (`Atoms.specFor`, Tally's pattern): the deepest
  technique of a hints-only walk from the empty board (`AtomsTeacher.depth`) must be at most at-least-one on Standard, crossing or
  isolation on Hard, isolation or deeper on Expert, and every walk must finish, so hints never fall back on a generated board.
  Only-way-out is shadowed by the rungs before it and almost never needed. Measured: `docs/TEACHING.md`, "Tier depth".
- **Growth:** each new atom sprouts 2 to 6 squares off an existing one, never onto or across a bond (the old rule that let an atom
  split a bond, and stored a broken answer on 4-35% of boards, is gone). Draw `a` is `Rng(seed + a)`, up to `ATTEMPTS` (2000); a
  year's worst is 10 / 39 / 71 draws. A board ships only if the gate holds and `countSolutions` (exhaustive, no budget) proves one
  answer; `AtomsTeachingTest` re-checks with its own atom-by-atom enumerator. `lastResort` is a three-atom chain no year reaches.
- **`solved` checks the rules**; a mistake is a line with more bonds than the stored answer, sound because the answer is unique.

## Nonogram

Run-length clues beside a grid; fill the squares that give every row and column its numbers. `puzzles/NonogramRules.kt`
(state, rules, line solver, generator), `NonogramTeacher.kt`, `Nonogram.kt` (board, walkthrough, motif). `id = "nonogram"` is hashed
into every seed: never rename it.

- **Tiers: 5x5 / 10x10 / 15x15, and the fewest line passes a board needs: 2 / 3 / 4** (`Spec.minPasses`, `NonogramLogic.linePasses`:
  passes over every row and column from a blank board until lines alone finish it). About 57 / 60 / 61% filled.
- **A board ships only if lines alone finish it** (`NonogramLogic.lineSolvable`: per line, the squares every legal layout of
  the clue agrees on, to a fixpoint). That also makes the answer unique, and `NonogramRulesTest` checks it with an
  independent oracle that tries every arrangement of every row. Of the draws with no blank or full line, 89 / 85 / 87% are
  line-solvable; with the pass gate a year's worst seed needs 17 / 12 / 34 of the 400 draws and none falls back (a year per
  tier is tested); `lastResort` is a fixed diagonal, itself proved line-solvable. Expert takes 1.6 ms median, 18 ms worst on
  the JVM; 2 / 8 / 18 ms median / p95 / max in Chromium, 3 / 10 / 23 in WebKit.
- **`solved` checks the clues, not the stored picture.** Crosses are notes and never count.
- **Hints cannot fall back**, so there is no fallback to measure: if the player's marks are right, some line always has a
  square to settle (settled squares only ever help). The mistake is a mark the stored picture disagrees with, sound because the
  picture is the board's one answer. Highlight indices past the squares are the clues (`rowClueIndex`, `colClueIndex`).
- **The pen (Fill/Cross) is `rememberSaveable` in the board, not state.** A sweep is one state: it locks to the row or column
  of the larger move, a pen only writes over untouched squares, and an eraser only takes out the mark it started on.
  `onDragStart` uses the overload that receives the *down*, since the web's touch slop is wider than a 15x15 square.
- **Hard and Expert zoom** (`NonogramZoom.kt`, the owner's design): pinch zooms (to 2.5x); zoomed in, one finger pans and a
  tap still marks, so sweeping is for the whole board; a corner button (in the empty corner above the row clues) fits the
  board again. Clues stay pinned to the edges and keep their size. The view (`GridView`) is `remember`, never state: a
  hint glides to what it names, a solve to the whole board. Read on the Initial pass, so a second finger cancels a sweep
  already begun (nothing is emitted before lift). Ctrl+wheel zooms on the web. Expert squares unzoomed were 16-19dp; a
  12x18 board was rendered and dropped (bigger on tall phones, smaller on short ones and on its side).
- **Colour is derived, never stored.** Filled squares blend diagonally between a pair from `PALETTES`, picked by hashing the
  board's picture, so each daily and each random board has its own and nothing is saved. A line whose runs match its clue turns
  green; a line no legal layout of its clue can hold (`lineImpossible`: too many, a run too long, no gap; crosses ignored, the
  stored picture never read) turns red once it has stayed so for a second. `NonogramRulesTest` brute-forces that rule.
- **Pictures are random squares, never mirrored** (the owner, 2026-10-26: no symmetry). Mirroring was tried for a day so a
  finished board would look drawn, but half the board (a quarter when mirrored both ways) gave the rest away, and the clues
  showed it (palindrome rows, paired columns). A draw that happens to read the same mirrored either way is redrawn. Smoothing
  into blobs was tried earlier and failed the shape rules on 96% of 5x5 draws.

## Mambo (Takuzu with = and x links; shown to players as Sun & Moon)

Two symbols, balanced in every line, never three alike together, printed `=`/`x` links honoured. `puzzles/Mambo.kt` (state, rules,
generator, `countSolutions`), `MamboTeacher.kt` (the ladder and `depth`). `id = "mambo"` is hashed into every seed: never rename it.

- **Tiers are size and ladder depth: 6x6 / 8x8 / 10x10.** Standard is finished by the first four techniques (pair, sandwich, link,
  quota), Hard must need quota-almost-met and not what-if, Expert must need what-if (`MamboTeacher.depth`, `Mambo.rungFor`).
- **The generator's ladder is the teacher's.** The carve takes back cells, then links, while the ladder up to the tier's rung still
  finishes the board, one pass per rung, cheapest first; then the tier's draw is redrawn unless the walk needs exactly its rung, and
  `countSolutions` (propagation plus a lookahead, then branching, no budget) proves one answer. Standard's boards are the ones it
  always had. `depth`'s what-if is `whatIfAt` (looks only near each square; `MamboTeachingTest` holds it equal to the hint's
  `whatIf`). Draws a board over a year: 1.00 / 1.42 / 1.01 mean, at most 1 / 6 / 2 of 40; JVM median / max 0.9 / 5, 4 / 23,
  18 / 62 ms; Chromium median / p95 / max 1.4 / 1.7 / 2.4, 6.6 / 19 / 35, 29 / 41 / 78 ms, WebKit up to 3 / 49 / 91 ms
  (before the lookahead in `countSolutions`, sparse Expert boards took over a second to count). `MamboRulesTest` walks the year per tier (with its own solver: three rules plus a one-deep supposition) and an
  independent exhaustive count; `lastResort` (checkerboard, every link) is never reached.
- **`solved` checks the rules**; a mistake is a symbol no legal answer keeps.

## Inequality (Futoshiki)

A Latin square (1..N once per row and column, N = 4/5/6 by tier) with `<`/`>` signs between some neighbours. `puzzles/InequalityRules.kt`
(`Sign`, rules, generator, exhaustive proof), `InequalityTeacher.kt`, `InequalityKeys.kt`, `Inequality.kt` (state, board, walkthrough,
motif). `id = "inequality"` is hashed into every seed: never rename it.

- **Tiers: size and ladder depth together** (`InequalityLogic.specFor`, Tally's `Spec.needs`). Standard 4x4 uses the five single
  rungs, ungated (its boards did not move). Hard 5x5 may use naked pairs and must need one; Expert 6x6 may use hidden pairs too and
  must need one. A draw whose walk stops short is redrawn: 8.4 / 11.2 draws mean (max 57 / 75) of `ATTEMPTS` = 200. Clues are minimal
  for the tier's ladder: givens 6 / 3 / 5% of squares, signs 4.6 / 9.5 / 14.7. A sign chain `a < b < c` needs no rung: the sign cut
  runs to a fixpoint, so chain bounds are already `SIGN_BOUND`. Generation: Chromium 0.2 / 4 / 19 ms median, 0.4 / 17 / 83 p95,
  2 / 41 / 181 max; WebKit about 1.5x. 7x7 Expert was measured, not built (22% of draws need a hidden pair; a draw costs ~5x a 6x6's).
- **A board ships only if the teacher's ladder finishes it** (`InequalityTeacher.solvesByLogic`: last square, only digit, only place,
  then the same two with candidates cut by the signs to a fixpoint, then a naked pair and a hidden pair in a line, each a cut that
  leaves a single, counting the signs again if it takes that). Digits and signs are then taken back in random order while that
  still holds, and the result is proved by `InequalityLogic.countSolutions` (candidate masks, exhaustive, no budget). So hints never
  fall back on a generated board; the fallback exists for boards not made here. `InequalityOracle` (naive reading-order DFS) is the
  independent check. Changing the ladder changes every board (parity pins go red on purpose).
- **The generator's walk is `pairDepth`, not `depth`**: it places every single of a pass at once (the five single rungs are
  order-free while every digit is right), so it meets the same stuck boards and pair steps as the hints, 2.5x faster.
  `InequalityRulesTest` checks the two agree, on shipped boards and on boards one clue short.
- **`solved` checks the rules**; a mistake is a digit that differs from the stored square, sound because the board has one answer.
- **No pencil marks** (a 6x6 is small; Sudoku's notes would be the template if asked). The selected square is in the state (as Sudoku's),
  so `withoutSelection` is overridden. Highlight indices: squares `0 until n*n`, the k-th sign `n*n + k`, digit keys `1000 + d`.

## Mate (chess mate-in-N)

White (or Black) to play and mate in 2 / 3 / 4 by tier. Spec and decisions: `docs/CHESS_SPEC.md`. `puzzles/ChessRules.kt` (engine, `forcedMate`,
`defence`), `Chess.kt` (state, board), `ChessTeacher.kt`, `ChessTutorial.kt`, `ChessPieces.kt` (vector pieces), `ChessPositions.kt`
(generated by `tools/chess/build.py` from the Lichess puzzle DB, CC0). `id = "chess"` is hashed into every seed: never rename it.

- **Tiers are a Lichess rating band as well as N** (700-1199 / 1300-1749 / 1800+, medians 1010 / 1504 / 2063), and
  Expert takes up to 400 of its 1,500 with a quiet key first: `tools/chess/build.py` `BANDS`, `QUIET`, measured in the spec.
- **Not generated.** A position is an index into a sorted list, so the list's order is part of the contract (as Lexicon's): adding or
  removing an entry moves every later daily board and `BoardPinTest` goes red on purpose. Each entry has exactly one key move and a
  shortest forced mate of exactly N (proved by python-chess offline, re-proved by `ChessRulesGameTest` with `forcedMate`). There is no
  fallback to measure.
- **Only the player's moves are saved; the opponent's replies are derived** by `ChessRules.defence(pos, mateIn - played.size)` (the attacker's
  moves left after the reply). Changing that policy changes what a saved game shows: it is pinned by a test; treat it as a save change.
- **`solved` is a rule check** (checkmate within N moves). Any legal move is allowed; a move that gives up the forced mate is a mistake the
  teacher flags (`forcedMate` is `None`, never `Truncated`), and `lost` shows a reserved caption, never a refusal.
- **A truncated search is not `None`.** `Mate.Truncated` is its own type.
- Highlight indices are squares (a1 = 0), not `row*8+col`: the board flips for Black, so it uses `reportHighlight`, not `highlightGrid`.
- Expert (mate in 4) hint search is measured on an emulator and the web (p95 94 ms, max 234 ms; `docs/TEACHING.md`); it runs off the main thread.
  `ChessSearchBenchTest` keeps the old search as an oracle: any search change must still agree with it.

## Kings

One king per row, column and colour region, no two touching. `puzzles/Kings.kt` (state, rules, carve, board, walkthrough),
`KingsTeacher.kt`, `KingsKeys.kt`. `id = "kings"` is hashed into every seed: never rename it.

- **Tiers: 7x7 / 8x8 / 9x9, and the depth the hints need from blank** (`Kings.fitsTier` on `KingsTeacher.depth`): Standard nothing
  past would-empty; Hard a confinement of two colours in two lines, nothing wider or a what-if; Expert a confinement of three or
  more, or a what-if. What-if alone for Expert was costed and dropped (2% of 9x9 draws, 0.7 s worst on the JVM). Draws a board
  over a year: mean 2.4 / 5.7 / 14.6, most 13 / 41 / 94 of `ATTEMPTS` = 400; JVM p50 / p95 / max on Expert about 30 / 120 / 300 ms;
  browser Expert 28 / 102 / 209 ms in Chromium, 39 / 142 / 290 in WebKit (Hard 70 / 101 ms worst). The gate also means the ladder finishes every shipped board, so hints never fall back.
- **A board ships only if the carve proves one answer** (`countSolutions`, squares handed out one at a time, monotone) **and the
  gate holds**. `lastResort` is the first seed from 1 whose first draw passes, so it is proved too; a year per tier never reaches it
  (`FallbackTest`). Changing the ladder changes which draws pass (`WebParityTest` goes red on purpose).

## Tents

Tents and trees: a tent beside every tree (orthogonal, one each), no two tents touching even diagonally, row and column counts given.
`puzzles/TentsRules.kt` (state, `TentsLogic` ladder, generator, exhaustive proof), `TentsTeacher.kt`, `TentsKeys.kt`, `Tents.kt` (object `Tents`,
board, walkthrough, motif). `id = "tents"` is hashed into every seed: never rename it.

- **Tiers: 6x6 / 8x8 / 10x10** (7 / 13 / 20 tents) **and the depth the hints need from blank** (`TentsLogic.fitsTier` on
  `TentsTeacher.depth`, Tally's gate): Hard needs a tree's one spot or deeper, Expert a shared or served square; Standard is
  not gated. Draws a board over a year: mean 1.3 / 5.6 / 23, most 5 / 27 / 143 of `ATTEMPTS` = 2000 (`TentsRulesTest`);
  Expert JVM median 5 ms, worst 191 ms; Expert in the browser, p50 / p95 / max: 14 / 66 / 151 ms in Chromium, 17 / 75 / 125 in WebKit.
- **A board ships only if the ladder finishes it** (`TentsLogic.deduce`) and `countSolutions` proves one answer; `TentsOracle` (walks the trees) is the
  independent check. No fallback in a year of seeds per tier, so hints never fall back on a generated board.
  `lastResort` is the first seed from 1 whose first draw passes everything, so it is proved and gated too.
- **`solved` checks the rules**; a mistake is a tent the stored answer lacks, sound because the board has one answer. Crosses are notes.
- A tap lays grass (a note), a double tap or a long press a tent (one move; Kings' crown is the same); a tap on a tent removes it. **The grass a tent sprouts is derived, not stored** (`TentsState.seen`, as Kings' crosses), so removing a tent takes it away and the player's own grass in `marks` is never touched; a saved game from before keeps its stored grass as the player's. A drag lays grass on empty squares only, one move on lift. Highlight indices: squares `0 until n*n`, row clue `n*n+r`, column clue `n*n+n+c`.

## Regions (region digits, no touching repeats)

A grid cut into regions; a region of N squares holds 1..N once each, and equal digits never touch, even diagonally. `puzzles/RegionsRules.kt`
(`RegionsLayout`, `RegionsLogic`: rules, generator, exhaustive proof), `RegionsTeacher.kt`, `RegionsKeys.kt`, `Regions.kt` (state, board, walkthrough,
motif). `id = "regions"` is hashed into every seed: never rename it.

- **Tiers: size and ladder depth: 5x5 / 7x7 / 9x9**, regions of 2 to 5 squares (`RegionsLogic.specFor`, Tally's `Spec.needs`). Standard uses the three
  single rungs, ungated (its boards did not move); Hard and Expert add the locked candidate and must take at least 2 / 6 locked steps (4.0 / 8.2 a board
  on average). Givens are minimal for the tier's ladder: about 27 / 19 / 17% of squares (were 27 / 22 / 19).
- **The partition and the fill are built together** (`randomBoard`): a random cut almost never has a legal fill (every region holds a 1, and no two 1s may
  touch, so a 2x2 window needs four different digits). Pick the first free square in reading order, grow a region, give it 1..k with none touching an
  equal digit; a dead end redraws. Starting from the most boxed-in square instead failed on most 9x9 draws. Regions of one square are refused.
- **A board ships only if the teacher's ladder finishes it** (`RegionsTeacher.solvesByLogic`: last square in a region, only digit left, only place in the
  region, then a locked candidate: a region's digit whose every place touches one square outside it strikes it there, and that square is left one digit
  or its own region's digit one place), digits are taken back in random order while that holds, and `countSolutions` (candidate masks, exhaustive, no
  budget) proves one answer. `RegionsOracle` (naive reading-order search) is the independent check. `lastResort` is the board of one fixed seed, itself
  proved by a test. A dig that misses the gate is dug again in a new order (up to `DIGS` = 4) before the fill is redrawn, because the fill is ~85 ms of
  a 9x9 draw; about 14% of Expert days need a second fill (`RegionsRulesTest`), and a year per tier never reaches `lastResort` (`FallbackTest`).
  Generation, a year a tier, median / p95 / max (2026-10-27): Chromium 1.5 / 7 / 17, 11 / 39 / 92, 99 / 433 / 799 ms; WebKit 2 / 11 / 26,
  17 / 65 / 144, 165 / 719 / 1378; JVM 0.7 / 3.4 / 7.5, 5.1 / 19 / 41, 44 / 188 / 358 (a quiet machine, load 1-3 on 32 cores).
  The fill is 93% of Expert's time; the slowest days throw away 1-2 fills that dead-ended and draw 3-4 (`docs/TODO.md`). Changing the ladder or the draw changes every board
  (parity pins go red on purpose).
- **The generator's walk is `lockedSteps`, not `depth`**: it places every single of a pass at once (the single rungs are order-free while every digit
  is right), so it meets the same stuck boards and locked steps as the hints. `RegionsRulesTest` checks the two agree.
- **`solved` checks the rules**; a mistake is a digit that differs from the stored square, sound because the board has one answer. No pencil marks.
- Region colours are derived (`Regions.regionColours`: a 3 to 5 colouring of the region graph, hues Violet/Amber/Teal/Blue/Gold; the thick walls carry the
  regions as well). The selected square and its digit's twins go plain (surface) so they stand out from the colours. Highlight indices: squares `0 until n*n`,
  digit keys `1000 + d`.
## Sudoku

The classic 9x9. `puzzles/Sudoku.kt` (state, rules, generator, `countSolutions`), `SudokuTeacher.kt`, `SudokuBoard.kt` (grid and pad,
shared with Cage Sudoku), `SudokuKeys.kt`. `id = "sudoku"` is hashed into every seed: never rename it.

- **Tiers are depth of the teacher's ladder, not clue counts** (`Sudoku.gate`): Standard is finished by singles in a unit (full house,
  hidden singles); Hard must need a naked single and nothing past it; Expert must need locked candidates, a pair or a what-if, and
  never the fallback. Hard and Expert clues fall where minimality leaves them: about 28 (22-32, means 27.6 / 27.9). The old
  38 / 30 / 24 targets dug for uniqueness alone, so Hard and Expert overlapped and a third of Expert boards fell back.
- **Standard has a clue floor, `Sudoku.STANDARD_FLOOR` = 34** (the owner, 2026-10-26: minimal Standard boards, 24-33, mean 28.1,
  felt bare). The dig skips any removal that would go below it, so every Standard board has 34 or 35 givens (mean 34.1: the
  floor binds on every day) and is no longer minimal; Hard and Expert did not move. Not trivially easy: 47 squares still to
  find, and the squares placeable at the start rose from 7.4 to 11.6 a board (2-16 to 4-21; a floor of 38 would give 14.8).
- **A board ships only if the tier's ladder finishes it and `countSolutions` (exhaustive, no budget) proves one answer.** A fresh grid
  per attempt (`Rng(seed + attempt)`) is dug in 180-degree pairs (the centre alone) in random order, keeping a removal only while both
  hold, so the givens are symmetric and minimal for the tier's ladder; then the teacher's walk must have needed the tier's technique,
  else the next grid. `finishes` is the dig's shortcut (singles settled with bitmasks, the teacher asked only where they stall;
  `SudokuRulesTest` checks it gives the teacher's verdict). Attempts over a year: 1 / 4.5 / 5.6 mean, at most 1 / 25 / 38 of
  `ATTEMPTS` = 100; `lastResort` is the board of one fixed seed, itself proved; a year never reaches it (`FallbackTest`). JVM 0.7 / 2.4 /
  3.9 ms median, 1.1 / 10 / 15 ms p95, 23 / 27 / 33 max (2026-10-26, `SudokuRulesTest` report); Chromium 2 / 6 / 13 ms median, 3 / 27 / 45 p95, 7 / 57 / 85 max; WebKit Expert 19 / 62 / 147.
  The teacher's what-if chain keeps arrays, not lists, because the dig asks it on every board (same steps, checked on 34k).
  Changing the ladder changes every board (parity pins go red on purpose).
- **`solved` checks the rules**; a mistake is a digit that differs from the stored square, sound because the board has one answer.
  The save holds givens, digits and the answer, so a board in progress never depends on the generator.
- `fullGrid` is shared with Cage Sudoku: changing it moves every Cage board too.

## Cage Sudoku

Sudoku plus dashed cages: the digits of a cage add up to the small number in its corner and never repeat. Not "Killer Sudoku" in
any text the player sees (the owner's call). `puzzles/CageRules.kt` (`CageLogic`: the logic, the prover, the generator), `CageTeacher.kt`,
`CageKeys.kt`, `Cages.kt` (object `Cages`, `CageState`, cage drawing, walkthrough, motif), `SudokuBoard.kt` (the grid and pad it
shares with Sudoku). `id = "cages"` is hashed into every seed: never rename it.

- **It is a Sudoku underneath, not a copy of one.** `CageState` holds a whole `SudokuState` (`grid`: digits, answer, selection, pencil
  marks, moves) plus `cageOf` (cage number per square, numbered by first square) and `sums`; every Sudoku mutator is reused and the
  only addition is that a placed digit also strikes the marks of its cage-mates in the same state. Sudoku's own state, seeds and saves
  were not touched: `Sudoku.Board` is now a call to `SudokuGridBoard` (moved out unchanged apart from callbacks and an overlay slot),
  and `Sudoku.fullGrid` went from private to internal. The grid, pad, notes, highlight and keys are shared; `CageKeys.kt` only applies
  Sudoku's `sudokuKeyAction` to a `CageState`.
- **Tiers: cages and givens together.** Standard: cages of 2-4, about 28 of them, 14 givens. Hard: up to 5, about 24 cages, 8 givens.
  Expert: up to 7, about 24 cages, **no givens** (cages alone). Measured by `CageRulesTest` `the tiers get harder in the order they are
  offered` (fewer givens, bigger biggest cage) and by what the hints must use (`docs/TEACHING.md`: the 45-rule and the cage chain
  appear from Hard: 3.8 and 0.7 steps a board on Hard, 6.7 and 6.3 on Expert). The cage count is not monotone (Hard and Expert both
  average about 24): the ladder will not finish a no-given board with many big cages, so Expert gets its difficulty from having no
  givens to start from, not from more bulk.
- **A board ships only if the logic finishes it** (`CageLogic.settle`: candidates cut by peers, cage-mates, every cage's combinations and
  the 45-rule's innie/outie groups to a fixpoint, then every square with one candidate and every digit with one place in a unit), and
  `solutions` (the same propagation plus branching, no budget, verdict type `Verdict`) proves one answer. Givens are added only where the
  ladder is stuck, then cages are merged and givens taken back while it still finishes (the Snap lesson: no board gives away what the
  rest already implies). `CageOracle` (test) works cage by cage on every filling of each cage with no inference at all, so it shares
  nothing with the prover; it agrees on every sampled board (Expert takes it 3-13 s a board, so three are sampled). Generation: Standard and Hard prove on the first attempt (3 of 365 Hard days
  need a second); Expert's first attempt succeeds about a quarter of the time (a year needed up to 24 of the 40 allowed, none gave up) and
  costs about 25 ms on the JVM per board and 36 / 51 / 58 ms (median / p95 / max) in Chromium, 50 / 70 / 78 in WebKit.
- **The logic must stay inside the teacher.** The generator's ladder is the teacher's, so `deduce` ends in `CAGE_CHAIN` ("every group worked
  through together"), which is exactly `settle`. Raise the generator's power (a new rule in `Sight`) and the teacher already has it
  unexplained; the fix is a named technique, not a fallback. `LINKED_TOTALS` (two or three groups sharing squares, each cut in
  turn, before `CAGE_CHAIN`) is such a slice of `settle`, so it moved no board; Expert's cage chain fell from 6.3 to 3.4 steps a board.
  `CAGE_CHAIN` itself explains with a worked line in linked totals' voice: the fixpoint's cuts toward a single, cut down to the
  groups and then the cuts it needs (the shortest of the first three singles' lines; median 247 characters, long ones scroll).
  The teacher's square choice is not the generator's, so this changes walks, never boards. `CageTeachingTest` walks 200 boards a tier and a year of daily boards and
  asserts no fallback; `FallbackTest` `cages proves every daily board of a year` asserts `generateVerified` never gives up.
- **`solved` checks the rules** (`CageLogic.isSolved`: a finished Sudoku grid, every cage's digits different and totalling its sum). A
  mistake is a digit that differs from the stored square, sound because the board has one answer. Red: Sudoku clashes plus
  `CageLogic.cageConflicts` (a repeat in a cage, a cage over its total, a full cage that misses it).
- **Cage totals are drawn in the top-left of the cage's first square**, so the pencil marks of that square give up the top 27% of it
  (`topBand`). Dashed outlines are `Cages.drawCages` (inset 9% of a square, bends and notches closed), in hue A's ink.
- Highlight indices are Sudoku's: squares 0..80, digit keys `81 + d`, the pencil 91.

## Target (number arithmetic)

Six numbers and a target: combine two tiles with + - x / and the answer is a new tile; any tile equal to the target wins. Many answers per board, so
no unique answer to prove. `puzzles/TargetRules.kt` (state, `TargetLogic`: rules, the exhaustive table, generator), `TargetTeacher.kt`, `TargetKeys.kt` (keys and the
tap/typing entry model), `Target.kt` (board, walkthrough, motif). `id = "target"` is hashed into every seed: never rename it. Named "Target" on purpose, not
after a game show.

- **Rules:** whole numbers from 1 to 9,999 only (`TargetLogic.CAP`); `-` and `/` put the bigger tile first, so there is no order to get wrong; equal tiles give
  `5 - 5` (refused, zero) or `5 / 5 = 1`. The cap is a rule, and the search obeys it as the player does, so "reachable" means the same to both.
- **Tiers: how many large numbers (25/50/75/100) and how many operations the target needs, by the exhaustive search.** Standard 3 large, target needs 3-4; Hard 2 large, 4-5;
  Expert 1 large, exactly 5 (all six numbers). Targets are 100-999 and never a starting tile. Measured over a year of daily seeds per tier
  (`TargetRulesTest`, `build/reports/target-par-distribution.txt`): shortest-answer lengths 3/4 = 164/201 (mean 3.55), 4/5 = 285/80 (4.22), 5 = 365 (5.00); no fallback on any day;
  12-16 ms a board on a desktop JVM. Hard/Expert never ship a target reachable in one or two steps.
- **Expert also caps the roads: at most 5 shortest answers** (`TargetLogic.Roads`, `Spec.maxRoads`). A road is the tree of an answer's operations: the order of
  independent steps, of a step's two tiles, and which of two equal tiles was used do not count; `(3+4)+5` and `3+(4+5)` are two. Counted by a memoised count of
  trees per group of tiles (a shortest answer of p steps uses exactly p + 1 tiles, every step feeding the target). Uncapped, Expert targets had a median of 13 roads
  (p90 41, max 157); now 1/3/3/4/5 (min/p25/median/p75/max). `generateVerified` tries the tier's candidates in a shuffled order for one with few enough, else draws
  new numbers; no fallback in a year. Expert generation on the JVM: median 5 / p95 14 / max 53 ms; Chromium 19 / 54 / 102 ms, WebKit 27 / 68 / 110 (Hard, uncapped, 14 / 30 / 117 and 19 / 42 / 64). Hard is not capped (12 roads turned 78% of its
  targets four steps away into 87%: shorter, not harder). `TargetOracle.roads` writes every answer out as text, both operand orders tried, and agrees on a sample.
- **`TargetLogic.table` is the proof**: depth-first over every order of every operation, no budget (so nothing truncated can pass for a proof), minimum depth per value.
  Equal tiles are tried once and `x1`, `/1` are skipped (they change nothing). `generateVerified` draws the numbers, takes the table, picks a target whose
  depth fits the tier (candidates walked in ascending order; no hash order reaches the `Rng`), retries up to 24 times; `lastResort` is a fixed board per tier, itself checked.
  `TargetOracle` (test) is the independent check: breadth-first over positions, both operand orders, no pruning; it agrees with the table on every value of 75 boards.
- **`solved` is "some tile is the target"**; no stored answer exists. `par` (the generator's minimum) is metadata for the fingerprint and tests; the teacher never reads it.
- **The move is built in `remember` (`TargetEntry`), only a finished operation is a `PuzzleState`** (one undo = one operation). Slots are stable: the answer goes where the first tile was.
  `TargetState.log` holds the operations so a mistake can be taken back exactly (`withoutFrom`); a save without it still loads.
- **Teacher: a search over the visible tiles, never the answer.** A step is on a shortest road from where the player stands (finishing if it can, else the answer nearest the
  target). A mistake is a step after which no sequence reaches the target (found with the same table; a step that merely lengthens the road is fine); "Show me" takes it
  and what followed back. No fallback exists. Highlight indices: tiles `0..5`, operators `Target.OP0 + ordinal`, the target `Target.TARGET`.
- Keyboard reads like a calculator: type a tile's number, an operator, another number, Enter. `TargetKeysTest` pins it against the taps.

## Cipher (word sums, a cryptarithm)

A sum of words in which each letter stands for one digit: different letters, different digits, no word starts with 0, the sum true, one answer.
`puzzles/CipherRules.kt` (`CipherSum`: columns from the right; `CipherLogic`: rules, conflicts, the exhaustive proof, the pick), `CipherTeacher.kt`
(the ladder and its explanations), `CipherKeys.kt`, `Cipher.kt` (state, board, walkthrough, motif), `CipherSums.kt` (generated).
`id = "cipher"` is hashed into every seed: never rename it. Not "Target": Target builds up to a number, this works back from columns and carries.

- **The total is a real word, so the sums are found offline.** `CipherSums.kt` is written by `CipherSumsBuildTest` (env-gated, `DAYBOOK_CIPHER_SUMS=<file>`,
  `--rerun`; reproducible: a fixed seed draws addend pairs, threads are read back in draw order). Every word is a Lexicon *answer* (screened, well known).
  Measured: about a quarter of four-letter addend pairs have a five-letter total with one answer that the ladder finishes, out of 1,768 tried, ~45 ms a pair on
  a desktop JVM: far too slow to search on a phone. Two five-letter addends (no leading carry, no 6-letter list) gave one ladder board per 30 pairs, three
  addends none, though both have many one-answer sums: they need trial, not columns. So **every tier is 4 + 4 = 5**, and the tier is the hardest technique the
  ladder needs (`CipherSumsBuildTest.tierOf`): Standard columns and pairs only, Hard all columns together, Expert a two-column what-if. 1,200 sums a tier, no total more than three times in one (`MAX_PER_TOTAL`; uncapped, `cacao` made 42 of Standard; now 632 / 684 / 721 different totals),
  sorted; the list's order is a contract (`BoardPinTest` goes red on purpose when it moves). Rebuilding after a ladder change re-rates every sum.
- **The device proves each board again:** `generateVerified` picks an entry with `Rng(seed + attempt)`, requires the ladder to finish it and
  `CipherLogic.prove` (column by column, no budget, sealed `Verdict` where only `ExactlyOne` carries an answer) to find one answer, which becomes the stored
  solution. Under a millisecond a board. `lastResort` is one fixed sum per tier, proved by `CipherRulesTest`; `FallbackTest` walks a year per tier.
  `CipherOracle` (test) tries every digit for every letter and adds the words as numbers: no columns at all.
- **`solved` checks the rules** (`CipherLogic.isSolved`); a mistake is a letter whose digit differs from the stored answer, sound because the board has one.
  Red, after a 1 s settle held in `remember`: letters sharing a digit, a leading 0, and the first column that is filled in (with everything to its right) and
  does not add up. A digit another letter holds is greyed on the pad with that letter under it, never refused.
- **Teacher: candidates per letter and per carry, derived from the placed digits on every call.** Leading carry, one column (with the carry once everything to
  its right is placed), only letter left for a digit (ten letters), a pair of columns through their carry, all columns together (named as such, as Cage's chain),
  then a what-if on two neighbouring columns (never a trial of the whole sum). The generator's ladder is the teacher's. Every step ends in a placement, so there
  are **no candidate notes**: a hint never asks the player to track an elimination it has not turned into a digit. Highlight indices: tile `row * 10 + column`
  (column 0 the units, the total the last row), letter key `100 + letter`, digit key `1000 + d`.
- **A hint shows its working, not a verdict** (the owner: "Only I = 0 fits both" taught nothing). `CipherWorking.kt` writes it from the placed digits and
  the carries the step's technique settled: each carry used is said with its reason (a neighbouring column, or "if the tens carried nothing, T would be 6,
  which L has"), then the column as a sum with every other digit ruled out by name ("at most 1 + 9 + 1 = 11, so I is 0 or 1; T has 1"), else each
  other digit followed until it breaks. `deduce` picks, of the placements the first productive technique allows, the shortest it can show within
  `MAX_EXPLANATION` (260, more than other teachers: the popover scrolls beyond its room); the walk keeps `findStep`'s first, so tiers are unchanged. Shown
  on 90 / 80 / 75% of steps (`CipherTeachingTest` floors them); the all-columns and what-if steps mostly keep their summary (`docs/TODO.md`).
- State: `words`, `solution`, `digits` (a digit per letter in alphabetical order, -1 for none) and `selected` (in state, as Inequality's; `withoutSelection`).
  The letter key row under the sum shows each letter's digit and selects it; keyboard: a letter key selects it, a digit sets it, Backspace clears, arrows walk the letters.
## Pointers (clear the arrows; shown to players as Arrows)

The board is covered in arrows, each a bent line of squares ending in a head. Tap one and it slides off the way its head points if
nothing is in its way to the edge; if something is, it bumps (a shake, a warning flash) and a life goes. Clear every arrow; lose three
lives and the board is lost (Try again restarts it, lives restored). `puzzles/PointersRules.kt` (`PointersState`, `PointersLogic`: rules,
generator, `depths`), `PointersKeys.kt`, `Pointers.kt` (object `Pointers`, board, hit testing, walkthrough, motif). `id = "pointers"` is
hashed into every seed: never rename it.

- **No hints and no undo, the owner's decisions; do not reopen.** A free arrow can be seen to be free and taking it never hurts, so a
  hint could only say "this one is free"; a cleared arrow never needs to come back (removal only ever opens lines), so undo could only
  hand back a life. `PuzzleType.offersUndo` (default true) is false here alone: `PlayScreen` drops the Undo button and Ctrl/Cmd+Z, in
  both `PlayLayout` shapes. It is the one Daybook puzzle about care rather than deduction, deliberately: the lives are the challenge.
  Teaching is the walkthrough (a free arrow leaving, a bump costing a life, the blocker cleared first), as Snap's.
- **Lives and the cleared arrows are `PuzzleState`**: `removed` (in the order they left) and `bumps` (the arrows that bumped, in
  order; `lives` is three less those, so the two cannot disagree, and the walkthrough can tell *which* arrow bumped). Both defaulted, so
  a save without them loads as a fresh board. A tap is one state: a removal or a bump (`moves` + 1 either way); a tap on a bare square or a cleared arrow
  emits nothing and costs nothing. Try again is `retry()`, one state. The slide and the bump are `remember` animations in a
  `rememberCoroutineScope`, never state; a slide is drawn only for an arrow the state says is gone, so a move the walkthrough refuses
  shows nothing leaving.
- **`solved` is every arrow removed; `failed` is no lives left and not solved.** Losing is never a completion: only a solve records.
- **A board ships only if it clears.** Arrows go down one at a time (a random bare square as the head, a body walked back from it with a
  tier's chance of keeping straight, never onto its own way out), each only where the "is in the way of" graph stays acyclic; then bare
  squares beside a tail fold into that arrow under the same check, and the board is cleared greedily before it ships. Removal is
  monotone (taking an arrow away never blocks another), so a player can never be stranded and greedy clearing is a proof. No unique
  answer: many orders win. `lastResort` is rows of arrows all pointing right; a year per tier never reaches it (`FallbackTest`).
  `PointersRulesTest` checks a year per tier with its own heading arithmetic, a greedy player and a DFS cycle check, none of it shared
  with the generator.
- **Tiers: size, length and bends.** 6x7 / 12x12 / 14x17 squares, arrows of 2-4 / 3-7 / 3-9 squares: about 14 / 33 / 49 arrows, 2% of squares bare, mean depth 2.0 / 4.4 / 6.3 (depth: rounds of "take every free arrow" before an arrow can go; `PointersLogic.depths`), worst 10 / 23 / 27. Generation on the JVM: 0.02 / 0.2 / 0.8 ms median, 1.2 ms p95 on Expert. Expert was
  meant to be 18x20; at 360dp wide that is a square of about 18dp, too small for a thumb when a mis-tap costs a life, so it is capped at 14
  wide (about 23dp at 360, 26 at 390; Nonogram's 15x15 is the precedent).
- **Press, preview, commit.** A finger down tints the arrow it would take (the keyboard cursor's band, no delay, nothing animated);
  release over the same arrow takes it, sliding off clears the tint and cancels. The arrow under the finger is `remember` state.
  Restart (no Undo to take it back) asks "Start over?" once a move has been made ("your lives come back"; Mines, also without Undo, gets the same prompt with no word of lives).
- Hit testing (`Pointers.hit`): the square under the finger if an arrow is on it, else the nearest line within 0.75 of a square. Arrow
  colours are derived (four hues, Blue/Amber/Violet/Teal ink, touching arrows differ where four allow), never stored; a bump flashes the
  arrow and the one in its way in `scheme.error`. Keyboard: arrows move a cursor (a square) to the nearest other arrow that way, Enter or
  Space taps it. Highlight indices (the walkthrough only) are arrow indices.
- Share card (`ui/play/ShareCard.kt`): the lives at the solve as hearts, `❤️❤️🖤` for two of three left, key `❤️ life left  🖤 life lost`,
  nudge "Not a single bump." with all three; the preview draws the hearts with `Pointers.heartPath`.

## Word Wheel (letter-wheel crossword)

A wheel of 5-7 letters and a small crossword: spell words by swiping across the wheel (or tapping letters, then the word); a word the
grid holds fills its slot, any other real word is a bonus word (counted, shown, never needed). No lives, no way to lose. Never name it
after, or borrow from, a commercial letter-wheel app. `puzzles/WordWheelRules.kt` (state, `WheelWords`, `WordWheelLogic`: judge, layout,
generator), `WordWheelTeacher.kt`, `WordWheelKeys.kt` (keys, and the pure tap/swipe/type functions), `WordWheel.kt` (board, walkthrough,
motif), `WordWheelWords.kt` and `WordWheelClues.kt` (generated). `id = "wordwheel"` is hashed into every seed: never rename it.

- **The lists are a contract, as Lexicon's are.** `tools/words/build.py` writes `WordWheelWords.kt` beside Lexicon's files (which it
  leaves byte-identical): `GRID_3..7` (Lexicon's answer screening at every length, limited to words with a clue: `clues.txt` for 4/5,
  `tools/words/wheel-clues.txt` for every 3-letter answer and the chosen 6/7-letter words, so 4/5 are exactly Lexicon's answers),
  `ROOTS_5..7` (the wheels: grid words spelling at least 8 / 12 / 14 other grid words, a seven also a six) and `BONUS` (Lexicon's guess
  rule at every length, kept to words some root can spell). A wheel is an index into `ROOTS_n` and the grid is laid from the `GRID`
  lists in order, so adding or removing a word moves daily boards and `BoardPinTest` goes red on purpose. Exclusions for
  3/6/7 letters are in their own section of `exclude.txt` (Lexicon never draws those lengths), interjections and archaisms
  (`huh`, `thy`) included: an excluded word leaves the grid but stays a bonus word if the guess rule takes it. A new 6/7-letter grid word needs a clue
  in `wheel-clues.txt`; `WordWheelRulesTest` checks every grid word has one, none contains its word, no two share one (with Lexicon's).
- **Tiers: 5 letters, 4-6 words in 6x6 / 6 letters, 6-8 words in 7x7 / 7 letters, 8-11 words in 8x8 with a six besides the root.**
  Slot 0 is the root (all the letters); the wheel is shown scrambled. Layout: words cross only on equal letters, one across one down;
  no side-by-side squares and nothing before or after a word, so every run is exactly one word; every word crosses the grid. The pool
  is shuffled from the sorted list (no hash order), longest first, each placed at a spot drawn from those with the most crossings.
- **A board ships only if it is winnable without luck:** walking it by hints alone, the slot a clue names never has more than
  `MAX_CHOICE` (6) grid words that fit. `generateVerified` tries 40 seeds (Expert: 71% first attempt, at most 5 in a year);
  `lastResort` is the board of seed 1, proved by a test; a year per tier never reaches it (`FallbackTest`). `WordWheelOracle` (test)
  re-reads every board's squares run by run, shares nothing with the placer. JVM: median 0.3 / 0.5 / 0.7 ms a board, max 3.5 ms;
  Chromium Expert median / p95 / max 1.7 / 4.4 / 10.6 ms, WebKit 4 / 12 / 19 ms.
- **No word shorter than the board's shortest slot is taken** (`shortestWord`), so a board with no three-letter slot refuses three letters; a save already holding a shorter bonus word keeps it. The bonus line opens the full list, A-Z, on a tap (`remember`).
- **A word goes to the slot it was laid for**; every slot holds a different word, so two same-length slots never compete for one.
  `solved` checks the rule: every slot's word is found. The word being spelled, the finger and the notice are `remember`; Shuffle is a
  `rememberSaveable` count that reorders the display only; one accepted word is one state. The gesture is keyed on the wheel and reads
  the board through `rememberUpdatedState` (Snap's lesson). The notice uses the header line, whose room is always kept.
- **Teacher:** `PINNED` (an empty slot whose length, crossing letters and the wheel leave one grid word, found and bonus words excluded:
  reads the public grid list, never a slot's word; given as that word's clue, as Lexicon's hints are: only "Show me" types it), then `CROSSING` (of the words that fit an empty slot,
  only one leaves every empty slot crossing it a different word that fits and agrees at the crossing; same list, same clue), then `CLUE` (the slot with the fewest words that fit, given as its word's clue: reads
  the stored word, so it is the fallback: 33 / 35 / 33% of steps over a year a tier, and an exhaustive search of every consistent filling
  pins only 0.1-0.2 points more, so the rest is honestly open). The generator's `walk` keeps the old ladder (`crossings = false`) so a
  stronger teacher never moves a board. No mistake exists. Highlight indices: squares `row * width + col`, wheel letter `i` (of
  `WordWheelState.wheel`, not its shuffled place) `WordWheelTeacher.WHEEL + i`.

## Tally (keep the numbers that make every sum)

A square of digits 1-9 with a target beside every row and under every column; remove numbers until what is left in every line adds
up to its target. Exactly one answer. The genre is published as Rullo and as "Number Sums"; neither name (nor "Kakuro", a different
puzzle) appears anywhere the player sees. `puzzles/TallyRules.kt` (state, `TallyLogic`: line logic, what-if, exhaustive count with a
`TallyVerdict`, generator), `TallyTeacher.kt`, `Tally.kt` (object `Tally`, board, keys, walkthrough, motif; the pen stack it shares with Nonogram is `core/PenMarks.kt` and `ui/PenBoard.kt`). `id = "tally"`
is hashed into every seed: never rename it.

- **Marks:** untouched, removed (`x`, faded and struck: it no longer counts) and kept (`o`, circled: a note that the player is sure,
  counting exactly as untouched does). Nonogram's pen model: Remove/Keep is `rememberSaveable` in the board, a sweep locks to the
  row or column of the larger move and is one state, a pen writes only over untouched squares. Targets are derived from the stored
  answer (`keep`), never stored. **`solved` checks the rule** (every line's non-removed sum is its target), not `keep`.
- **Tiers are size and ladder depth together: 5x5 / 6x6 / 7x7, about 55% kept, every line keeping at least one number and removing
  at least one.** Standard is finished by the four shortcuts alone (done, all left, too big, too much to lose), Hard must need the
  ways of a line at least once, Expert must need a what-if at least once (`TallyLogic.depth`, `Spec.needs`); a draw needing more or
  less is redrawn. Without the depth rule random 7x7 boards are 97% finished by single lines and Standard/Hard barely differ.
- **A board ships only if the ladder finishes it** (`TallyLogic.accepts`) **and `countSolutions` proves one answer** (row by row over
  each row's ways, no budget; only `TallyVerdict.ExactlyOne` carries an answer). `TallyOracle` (test: every column's subsets by
  recursion, combined with no lookahead) is the independent check. Draws per board over a year: 2.4 / 3.9 / 67 mean, 15 / 23 / 395 most,
  of `ATTEMPTS` = 2000; no day reaches `lastResort` (a fixed board per tier, itself accepted by a test). Generation on a desktop JVM:
  Expert about 5 ms mean, 30 ms worst; in the browser (a year of Expert days) 10 / 34 / 69 ms median / p95 / max in Chromium, 10 / 40 / 68 in WebKit.
- **The generator's ladder is the teacher's.** Techniques 1-4 are `TallyLogic.shortcuts`, 5 is `forced` (the ways of one line: a number
  in every way stays, in none goes), 6 is `firstWhatIf` (suppose a number stays or goes, follow at most `MAX_LINKS` = 3 lines, some line
  is left with no way). Raise the generator and the teacher must learn the new step; no fallback exists. A mistake is a removed number
  the answer keeps or a circled one it removes, sound because the board has one answer.
- Feedback is derived: a target chip turns green (Green mark) when its line adds up, red (`scheme.error`) when no choice of its open
  numbers can make it any more (solid `scheme.error`, so it cannot be mistaken for a Rose or Amber chip), after a 1 s debounce. Hue pair from `BoardHues.pair(digits.hashCode())`: A tints the target chips, B
  inks the circles. Highlight indices: squares `0 until n*n`, row target `n*n + r`, column target `n*n + n + c`.
- The regions variant of the genre (sums per coloured region as well as per line) was deliberately not built; see `docs/TODO.md`.
## Sand (colour sort)

Tubes of layered coloured sand; pour from tube to tube until every tube holds one colour (the water-sort family). Spec and decisions:
`docs/SAND_SPEC.md`. `puzzles/SandRules.kt` (`SandLogic`: rules, the budgeted search, the deal, the difficulty filter), `Sand.kt` (state, board,
palette, walkthrough, motif), `SandTeacher.kt`, `SandKeys.kt`. `id = "sand"` is hashed into every seed: never rename it.

- **Tiers: 5 / 7 / 8 colours, height 4, two spare tubes on every tier** (slack never varies). Measured by `SandBalanceTest` over a year: a naive pourer
  sorts 79 / 44 / 27% of tries, a careful one 84 / 52 / 33%, the solver's median search 21 / 30 / 37 positions. 8x4 is clearly harder than 7x4, so
  Expert stayed at height 4.
- **Several answers by policy; the proof is a witness.** `SandLogic.solve` is depth first over canonical positions (tubes sorted; the visited set is
  membership only) with a budget, and its verdict is structural: `Verdict.Solvable(path)` / `Unsolvable` (exhaustive) / `Truncated`. Only `Solvable`
  ships; only `Unsolvable` calls a pour a mistake. There is **no "never pour back" pruning** (it is not exact when the target already had the colour);
  the two prunings left skip only positions with the same canonical key. Every deal so far has been sortable and none truncated.
- **The deal** is Fisher-Yates over the `Rng(seed + attempt)`, full tubes then two empty ones; a deal with a tube of one colour or a run longer than
  `H - 2` anywhere in a tube is redrawn (15 / 14 / 10% of attempts). Then the **difficulty filter**: a board a naive pourer (`naiveSorts`, seeded)
  sorts on all six tries is redrawn; it rejects about half of Standard's deals and almost none above. A year never reaches `lastResort` (`FallbackTest`).
- **`solved` checks the rules** (every tube empty or `H` of one colour); stuck (no pour at all) is derived and shown in a reserved caption.
- **`SandState.log` keeps the pours** (as Target's log), only so a mistake can name the pour that lost the board and "Show me" can go back to the last
  board that sorts (`withoutFrom`). The spec first said nothing but the tubes would be stored; without the log the real pour cannot be found (a
  median of 14 boards one pour back could have led to an unsortable one). A board with no log falls back to putting back one pour's sand
  (`SandLogic.unpours`). The lifted tube, the shake and the pour stream are `remember` state and decoration; one pour is one state.
- **Hints never fall back** (nothing is hidden). The first three ladder steps are offered only when they make progress (fewer places two colours meet
  in a tube, else fewer tubes in use, else more finished), which no pour ever undoes, so following hints cannot circle; `SandTeachingTest` follows them
  from 200 boards a tier. Highlight indices: tube `i` is `i`.
- **Layout: the row count (1 to 3) is whichever gives the widest tube**, and a tube's tap target is its whole column (gap and full
  height), at least 48 dp and widened towards 56 dp (`Sand.layout`, `SandLayoutTest`). The owner's baseline for too small is
  Nonogram Expert's square on a phone (16.6 dp at 375x537, 19.2 dp at 390x844). At 375x537 every tier is two rows of 39 dp tubes
  in 57 dp columns; at 390x844 Standard's tubes are 63 dp, Hard's and Expert's 50 dp.
- Colours: six hues at three steps, ordered by CIEDE2000 with colour-blind vision for the 5 / 7 / 8 prefixes (`SandPaletteTest`), and a symbol on every
  layer. The grain and the uneven tops come from the tube and layer index, never random per frame and never stored.

## Dominoes (domino placement on a region board)

Lay every domino from the tray on two neighbouring squares so each coloured region keeps its rule: a total, `<n`, `>n`, `=` (all
equal) or `≠` (all different). Spec and decisions: `docs/DOMINO_SPEC.md`. The genre is published by the New York Times as *Pips*;
that name and their art never appear anywhere the player sees. `puzzles/DominoRules.kt` (state, `DominoLogic`: rules, the
exhaustive count with a sealed `Verdict`, generator), `DominoTeacher.kt` (the ladder, shared with the generator), `DominoKeys.kt`
(the tap model `DominoPick` and the keys, pure), `Dominoes.kt` (board, tray, badges, walkthrough, motif). `id = "dominoes"` is
hashed into every seed: never rename it.

- **Tiers: 5x5 with 6-7 dominoes / 6x6 with 9-10 / 7x7 with 12-14**, distinct dominoes from the double-six set, and the deepest ladder
  step a board must need: region values / counting / a what-if (`DominoLogic.specFor`). Measured numbers: the spec's Measured section.
- **A board ships only if the ladder finishes it** and `solutions` (no budget; only `Verdict.ExactlyOne` carries an answer) proves one
  answer; `DominoOracle` (reading order, no pruning) is the independent check. A year per tier never reaches `lastResort`, so hints
  never fall back on a daily board. **`solved` checks the rules**; a mistake is a domino off the stored answer (squares or way
  round), sound because the answer is unique. Every hint ends in a placement.
- **Input: tap model, and a drag from the tray is a swipe** (height is scarce, so the tray never shows an upright domino and nothing rotates by tap): the domino follows the finger onto a square, then the move into a neighbour sets the way it lies. Tap a tray domino to pick it up, again to turn it; tap a square, then
  a neighbour: its leading half (the larger one, unturned, as the tray draws it and the hints name it) goes on the first. A drag
  square to neighbour is the two taps. A tap on a laid domino lifts it into the hand (after the 300 ms double-tap window); a double
  tap or long press turns it in place. The pick, the first square, the drag and the cursor are `remember` state (`DominoPick`), never
  `PuzzleState`; one state per gesture; gestures read the board through a cell updated on every emit (Tents' pattern).
- **The board is the whole box, never cropped to the shape**: the hints name squares by row and column of the box. The tray is a fixed
  grid of at most seven slots a row under the board, sized from both axes (`Dominoes.fit`, `DominoLayoutTest`); a laid domino leaves
  its slot empty but reserved. The tray is `keepClear`.
- Region hues are derived (`Dominoes.regionColours`: a four-colouring of the region graph in Violet/Amber/Teal/Blue, Gold spare; no
  Coral, Green or Rose, since the badges show right and wrong). Badges hang off each region's top-right square, drawn as strokes
  (Fredoka has no `≠`); green when the region is covered and keeps its rule, solid `scheme.error` once it cannot (1 s settle).
  Highlight indices: squares `row * width + col`, tray slot `DominoTeacher.TRAY + k`, badge `DominoTeacher.RULE + r`.

## Mines (a minefield with no guessing)

Numbers say how many mines touch a square; reveal every square without one. Spec and decisions: `docs/MINES_SPEC.md`.
Never "Minesweeper" anywhere the player sees. `puzzles/MinesRules.kt` (`MinesState`, `MinesLogic`: rules, the spread from a 0,
the ladder `nextFact`, the walk, generator), `MinesTeacher.kt` (explanations of the same ladder), `MinesKeys.kt`, `Mines.kt` (board,
pen row, walkthrough, motif). `id = "mines"` is hashed into every seed: never rename it.

- **The owner's decisions; do not reopen:** no board ever needs a guess; the opening is given (the seed picks a square, its 3x3 is
  kept clear and its spread is open when the board loads); **no lives: a mine revealed loses the board** (the owner, 2026-10-26: an
  earlier build gave three, as Pointers'; it is shown, and Try again is `retry()`, one state, the opening again); **no undo**
  (`offersUndo = false`); hints on.
- **The loss is `PuzzleState`:** `bumps` (the mine hit; a list because the three-lives build saved up to three, and such a save
  loads as lost). `marks` is one string
  ('.' hidden, 'o' open, 'f' flag). Flags are the player's notes: never checked, never a loss, and a flagged square is never
  revealed (the spread passes it by). "Mines left" is the total less flags and the mine hit. **`solved` is every safe square open**
  (a rule check; flags play no part); `failed` is a mine hit and not solved; losing is never a completion. **Chording** (the owner,
  2026-10-26; `MinesState.chord`): a tap, long press, Space or Enter on a revealed number whose flags and mines hit make its count
  reveals its other hidden neighbours as one state; a wrong flag opens a mine there and loses the board, as a tap would; the
  chord stops at it.
- **Tiers: 8x8 with 10 mines / 8x10 with 16 / 9x10 with 21, never more than 9 wide or 10 tall** (a mis-tap loses the board, so a
  square stays thumb-sized; Expert was 8x12, 21.5 dp at 375x537, and is never smaller than Hard's on a phone), and the depth of the ladder a board must need (Tally's gate): Standard single numbers only, Hard a subset or pair at
  least once, Expert the count against the numbers or a what-if (the count alone at the end, every mine found and a pocket no number
  touches, is too slight and does not qualify). An opening that shows more than 45% of the safe squares is redrawn.
- **The ladder is the teacher's and the generator's** (`MinesLogic.nextFact`, simplest first): all found, all mines, subset, pair,
  the count, the count against the numbers (numbers on squares none share need every mine left: the rest is clear), a what-if
  (suppose a square beside a number, follow single numbers at most `MAX_LINKS` = 3, one breaks). A board ships only if the walk from
  the opening (revealing each square it proves, reading its number off the board) reveals every safe square at the tier's depth,
  which also makes every mine forced. `MinesOracle` (test: every layout the numbers and the total allow, no inference) checks every
  step. A year per tier never reaches `lastResort` (the first seed from 1 the tier accepts, itself checked).
- **Teacher:** knowledge is the revealed numbers and the mines hit; flags are never knowledge, only a mines step whose squares are
  all flagged counts as shown. A mistake is a flag on a safe square (sound: one answer). No fallback on a generated board, measured.
  Highlight indices are squares. A subset/pair hint names the second number by where it lies ("the 2 to the right").
- Input: a Reveal / Flag pen under the board (`rememberSaveable`, Nonogram's model, Reveal each visit); a tap uses the pen, a long
  press the other tool. Keys: arrows, Space/Enter reveal (or chord), F flag. A mine hit shakes and rings in `scheme.error` (the ring alone
  under reduced motion) with `PlayScreen`'s bump haptic. Colours: hue A tiles the hidden squares, hue B inks flags; numbers 1-6
  each a hue (Blue, Teal, Violet, Amber, Rose, Gold) and always a digit. Share card: the default move strip (no hearts).

## Lamps (light every square)

White squares and walls, some walls numbered 0-4: put lamps so every white square is lit (a lamp lights its row and column
to a wall), no lamp lights another, and a numbered wall touches exactly that many lamps. Spec and measured numbers:
`docs/LAMPS_SPEC.md`. The genre is published by Nikoli as *Akari* ("Light Up"); neither name appears anywhere the player sees.
`puzzles/LampsRules.kt` (state, `LampsLogic`: rules, the ladder, exhaustive count with a sealed `LampsVerdict`, generator),
`LampsTeacher.kt`, `LampsKeys.kt` (keys and the pure tap/lamp/sweep functions), `Lamps.kt` (board, walkthrough, motif).
`id = "lamps"` is hashed into every seed: never rename it.

- **Tiers: 7x7 / 10x10 / 12x12, about a quarter walls (half-turn symmetric), and the rung a board must need, exactly:**
  direct facts (a 0, a number with all its lamps, a number with just enough room, a square only one lamp can light) /
  look-ahead (a lamp that would starve a number or leave a square dark) / a what-if (at most `MAX_LINKS` = 3 direct facts to
  a contradiction). Tally's `Spec.needs`: a draw needing more or less is redrawn.
- **The generator's ladder is the teacher's** (`LampsLogic.nextFact`, one fact at a time). A lamp on every square still dark
  in a random order is the answer; every wall is numbered from it, then numbers are taken back (rung by rung, the tier's own
  last, which must take one more) while the ladder finishes the board. `countSolutions` (direct facts propagated, then the
  first lamp among an unlit square's sources: a partition, no budget) proves it again; `LampsOracle` (test: one row run at
  a time, no propagation) is the independent check. No day of a year reaches `lastResort` (a fixed board per tier, accepted
  by a test), so hints never fall back on a daily board.
- **`solved` checks the rules**; a mistake is a lamp the one answer lacks. Crosses are notes, never read by the rules or
  the teacher. What is lit, clashing lamps and broken numbers (over, or out of reach with crosses ignored) are derived; the
  last two turn red after a 1 s settle. Lit squares are a static Amber wash, so reduced motion has nothing to still.
- Input is Kings' and Tents': tap a cross, double-tap a lamp, tap a lamp to take it away, drag crosses (one state on lift).
  Keyboard: L or Enter lamp, X or Space cross, Backspace clears. Highlight indices are squares (`highlightGrid`).

## Codeword (a crossword with numbers for letters)

A filled crossword grid in which every letter is a number 1..k; the same number is the same letter, a few letters are given,
and every across and down run must be a word. Spec and decisions: `docs/CODEWORD_SPEC.md`. `puzzles/CodewordRules.kt`
(`CodewordWords`: the lists with each word's shape; `CodewordLogic`: rules, conflicts, the exhaustive proof, the pick),
`CodewordTeacher.kt`, `CodewordKeys.kt`, `Codeword.kt` (state, board, walkthrough, motif), `CodewordGrids.kt` (generated).
`id = "codeword"` is hashed into every seed: never rename it.

- **Grids are built offline** (`CodewordGridsBuildTest`, env-gated, reproducible: draw `i` of a tier uses its own `Rng`): a
  dense fill is a backtracking search per pattern whose cost varies wildly (most 11x11 patterns give up within the budget).
  The pattern maker and the filler are `test/CrosswordFill.kt`, free of anything Codeword-specific, for the mini crossword
  that may follow. Grids are **fully checked** (every white square in an across and a down word, words 3-7 letters, no
  two-letter words), symmetric under a half-turn, connected, no word twice; words are Word Wheel's grid lists
  (`WheelWords.grid`, Lexicon's answer screening). Scattering the black squares (no
  2x2 block) interlocked the grid so tightly that almost no fill existed; blocks are allowed. The lists' order is a contract
  (`BoardPinTest` goes red on purpose when it moves).
- **Numbers are not stored in the list:** the device numbers a grid's letters 1..k by a shuffle from the board's `Rng`.
- **Tiers: size and ladder depth.** Standard 7x7, finished one run at a time (a run only one word fits, a letter every
  fitting word shares); Hard 9x9, must need a number cut by every run it is in; Expert 11x11, must need every word worked
  through together. Givens are minimal for the tier's ladder (taken back in a random order while it still finishes, at
  least one kept): about 1.8 / 1.1 / 1.0 a board. From one given letter the lists are so tight that the shallow steps
  finish most boards, so Hard and Expert are cut by what they need, not by their givens (Tally's `Spec.needs` precedent).
  A what-if technique was built and dropped: no board kept needed it.
- **A board ships only if the ladder finishes it from its givens and `CodewordLogic.solve` (branch on the run with fewest
  fitting words, no budget, sealed `Verdict`) finds one answer**; the builder also proves it with Word Wheel's `BONUS`
  words added, so a less common real word cannot open a second answer. `CodewordOracle` (test: letter by letter over the
  alphabet, no word index) is the independent check. `lastResort` is each list's first entry, proved by a test.
- **`solved` checks the rules** (every number a letter, none twice, every run a grid word); a mistake is a number whose letter
  differs from the stored answer, sound because the board has one. Red, after a 1 s settle held in `remember`: numbers
  sharing a letter, and runs no word can be any more (shape, placed letters, letters other numbers hold). Never refused.
- **Selection is a number, in the state** (Cipher's precedent; `withoutSelection`). A given number cannot be selected or
  changed. The key under the grid shows each number's letter and picks it; the A-Z keys under that grey a letter another
  number holds, with its number. Keyboard: with a number picked, a letter types it (again clears), Backspace clears, arrows
  walk the open numbers; with none picked letters are left to the play screen, so H still asks for a hint.
- **Teacher:** candidates per number derived on every call; a run's fitting words are those of its shape whose letters
  its numbers can take. `ONE_WORD`, `RUN_AGREES`, `CROSSING`, `ALL_WORDS` (named as Cipher's chain is). No "only number
  for a letter": a grid uses part of the alphabet. Highlight indices: squares `0 until size*size`, number key
  `CodewordLogic.key(n)` = 1000 + n, letter key `CodewordLogic.letterKey(l)` = 2000 + l.
## Pearls (a pearl loop)

One closed loop through square middles passing every pearl: on a black pearl it turns and runs straight on through the
next square each way; on a white pearl it goes straight and turns on a square beside it. Spec: `docs/PEARLS_SPEC.md`. The
genre is published as Masyu; that name never appears where the player sees it. `puzzles/PearlsRules.kt` (`PearlsState`,
`PearlsGrid`, `PearlsLogic`: rules, ladder, count, generator), `PearlsTeacher.kt`, `PearlsKeys.kt`, `Pearls.kt` (board,
hit testing, walkthrough, motif). `id = "pearls"` is hashed into every seed: never rename it.

- **State is the edges**, strings: `pearls` per square, `loop` (`1` per answer edge), the player's `edges` (`.`, `-` line,
  `x` cross: a note). Horizontal edges first (`r * (n-1) + c`), then vertical (`n(n-1) + r * n + c`).
- **Tiers: 6x6 / 8x8 / 10x10 and the rung a board must need** (`PearlsLogic.depth`): Standard the pearls' and squares'
  local rules alone; Hard needs the small-loop rule; Expert needs a what-if (at most `MAX_ROUNDS` = 6 rounds). A draw
  needing more or less is redrawn.
- **The loop is grown to be pinned by its pearls.** A random loop with every pearl it allows almost always has two answers
  (37 of 39 six-by-six), so `randomLoop` grows a face region greedily by the pearls its boundary allows. Then pearls are taken
  back in random order while the tier's ladder finishes (on Expert, a cheap pass with the loop rule first, then the full
  ladder), and `solutions` (local + loop rules, branching, no budget, sealed `PearlsVerdict`) must say `ExactlyOne(loop)`.
  `PearlsOracle` (test: edges decided in reading order with a frontier memo, no propagation) is the independent check. A
  year per tier never reaches `lastResort` (`FallbackTest`).
- **`solved` checks the rules** (`PearlsLogic.isSolved`, lines only); a mistake is a line off the loop or a cross on it,
  sound because the loop is unique. Red after a 1 s settle, derived from the lines alone: three lines at a square, a pearl its
  lines already break, a closed loop that leaves something out.
- **Input:** a drag through squares draws (its first edge decides: over a line it rubs out), one state on lift; backing up
  along the drag undoes it (`Pearls.trail`). A tap near an edge's middle (`Pearls.edgeNear`, within 0.42 of a square)
  crosses it, or rubs out the line or cross there. Detectors are keyed on the size and read the board through
  `rememberUpdatedState` (Snap's lesson). Keys: arrows, Shift+arrow draws or rubs, X then an arrow crosses.
- **Teacher:** the local rules (black pearl, white pearl, white turn, one way on, a pearl's two ways, dead end), then the
  small loop, then a what-if (the shortest within the cap; if a player's own order has left none short, the shortest of any
  length, so hints never run out). A square with two lines has its other edges read as crossed (`PearlsTeacher.view`), so the
  hints never ask for tidying. Highlight indices: squares `0 until n*n`, edge `e` is `n*n + e`.

## Mini crossword

A small, fully checked crossword: a clue for every word across and down; fill the grid. Spec and decisions: `docs/MINI_SPEC.md`.
`puzzles/MiniRules.kt` (`MiniLogic`: slots and numbering, rules, the exhaustive proof, the pick), `MiniTeacher.kt`, `MiniKeys.kt`,
`Mini.kt` (state, board, walkthrough, motif), `MiniGrids.kt` (generated). `id = "mini"` is hashed into every seed: never rename it.
Display name "Crossword" (one line on a home tile), never a newspaper's.

- **Grids are built offline** (`MiniGridsBuildTest`, env-gated, reproducible) with Codeword's `test/CrosswordFill.kt`, from Word
  Wheel's grid lists, already screened by the app-wide `tools/words/grim.txt` (see Lexicon). The lists' order is
  a contract (`BoardPinTest` goes red on purpose). Clues are `WheelWords.clue` (`clues.txt`, `wheel-clues.txt`), distinct across
  answers, so a clue names one word; the board stores its clue texts, so a reworded clue never changes a saved game.
- **Tiers: size and clue share, not ladder depth.** The clue share is the letters the hints' walk must read from the clues, over
  the white squares: Standard 5x5 (4 black) at most 30%, Hard 6x6 (about 9 black) over 30% to 40%, Expert 7x7 (about 15 black)
  over 40% (`MiniLogic.Spec`). Depth does not separate grids here (93% of 5x5 fills need every rung), and left alone a bigger grid
  interlocks more and has a *lower* share: Expert would be bigger and easier. Clue letters a board 5.6 / 9.4 / 15.2.
- **A board ships only if** every slot is a different grid word with a clue, the hints' walk finishes it with its share in the
  band, and `MiniLogic.solve` (no budget, sealed `Verdict`) finds exactly one fill of the grid from the letters the walk read off
  the clues: the crossings settle everything else. `MiniOracle` (test: square by square over the alphabet, prefix sets) is the
  independent check. `lastResort` is each list's first entry, proved by a test; a year per tier never reaches it (`FallbackTest`).
- **`solved` checks the rules** (every slot spells the word its clue is the clue of); a mistake is a letter off the stored answer,
  sound because the clues name one fill. Red, after a 1 s settle and only once no square is open (`shownDead`): a full slot that is no word of the list, never a real word under
  another clue (that would be reading the answer). A word the list holds glows once, for the same reason not only the right one.
  A full grid not `solved` (`fullButWrong`) says "Something's not right yet" in a line always laid out under the grid, without saying where.
- **Teacher:** `ONE_WORD` (the letters in a slot leave one word), `CROSSING` (one word leaves every crossing slot a fit, the slots
  named), `ALL_WORDS` (arc consistency to a fixpoint, said as such), then `CLUE`, the fallback: its first tap names the open slot
  the fewest words fit (from the board alone) with that word's **second clue** ("Another way in: ..."; the owner: "I'd rather a
  2nd clue before giving the answer"); only the next tap reads the answer, says so, and gives one letter; Show me places it.
  Second clues are `tools/words/second-clues.txt` (one per word the grids use, a different angle from the first, linted by
  `build.py`, which writes `MiniSecondClues.kt`; `--second-clues` writes it alone), stored on the board as `MiniState.secondClues`
  (a save without them gets the old nudge). A new grid word needs one in the same change: `MiniSecondCluesTest`. It cannot be designed out: from an empty grid nothing else starts, and a
  clue is knowledge, not logic. 44 / 54 / 62% of steps on a hints-only walk; that share is the tier measure. Word steps are typed
  by the player; only "Show me" types the word. Highlight indices: squares, letter key `MiniLogic.letterKey(l)` = 2000 + l, the
  clue bar `MiniLogic.CLUE_BAR` = 3000.
- **Clue text is a tier too** (`tools/words/hard-clues.txt`, `MiniHardClues.kt`, `MiniLogic.takesHardClue`): hand-written, less
  direct main clues for the most used words. Standard none, Hard its across slots, Expert every slot; on such a slot the plain
  clue is the fallback's "Another way in". `isSolved` takes either clue; rewording one strands a board in progress with the old
  text, as for first clues. The second clues were read and are not reliably harder, so they were not used for this. No grid
  moved; Hard and Expert's `BoardPinTest` lines did, because the fingerprint holds clue text. `MiniHardCluesTest`.
- **Cursor and direction are in the state** (Codeword's precedent; `withoutSelection` drops both). A tap selects a square, the same
  square again turns between Across and Down; typing moves on to the next empty square of the word; Del clears, or steps back and
  clears. The clue bar (fixed height, room for two lines) shows the cursor's clue, turns on a tap, and ‹ › walk the words. Keys:
  letters, Backspace, arrows (one across the word's way turns first), Space turns.

## Pipes

Rotate tiles until every opening meets another and the pipework is one network. `puzzles/Pipes.kt` (state, generator, board,
walkthrough), `PipesTeacher.kt`, `PipesKeys.kt`. `id = "pipes"` is hashed into every seed: never rename it.

- **The answer is a randomised Kruskal spanning tree** (every grid edge in `Rng.shuffled` order, kept when it joins two pieces):
  per board 7.1 / 11.4 / 18.7 T-junctions and 0.9 / 1.7 / 3.0 crosses (Standard 5x7 / Hard 6x9 / Expert 8x11). The first generator
  was a depth-first maze, long corridors with 3.2 / 5.2 / 8.5 T-junctions and almost no crosses.
- **A board ships only if the teacher's reasoning settles every tile** (`PipesTeacher.settles`: the border, neighbours and no-loop,
  shapes only); each step is sound for every answer, so that also proves one answer. Branchier trees have several answers far more
  often (5-12% of raw Kruskal boards, against none of the maze's sampled), which is what the gate removes. `generateVerified` tries 40
  trees with `Rng(seed + attempt)`: 95 / 91 / 84% pass first, a year never needs more than 3; `lastResort` is seed 1's board,
  itself proved (`FallbackTest`). Under 0.5 ms a board on the JVM. Hints never fall back on a generated board (`PipesTeachingTest`).
- **`solved` checks the rules** (fully joined and one network), never a stored answer; there is none.
- Counter-clockwise rotation and a lock mark were proposed and **declined by the owner**: do not add them.

## Who's Who (a logic grid)

People in a row of houses, each with one item of every category (a pet, a job...), and numbered clues that fix one
assignment. Spec and measurements: `docs/WHOSWHO_SPEC.md`. Never "Einstein puzzle" or "Zebra puzzle" where the player sees.
`puzzles/WhosWhoRules.kt` (state, `WhosWhoLogic`: candidates, the ladder, the count, generator, clue text), `WhosWhoTeacher.kt`,
`WhosWhoKeys.kt`, `WhosWhoWords.kt` (names, five categories of five words, how a clue says each), `WhosWho.kt` (board, walkthrough,
motif). `id = "whoswho"` is hashed into every seed: never rename it.

- **The word lists are a contract** (indices are stored and drawn by index): append-only, with a re-pin.
- **Tiers: 4 people, house + 1 category, every clue naming a person / 4 people, house + 2, plus links and next to / left of /
  5 people, house + 2, plus either-or**, gated by the deepest ladder level the walk needs (direct / link or house / what-if).
- **A board ships only if the ladder finishes it, every clue is needed, and `count` proves one answer** (column by column over
  permutations, no budget, sealed `Verdict`). Clues are drawn until the ladder finishes, shown in a fixed shuffled order (the
  ladder reads them in order, so the checks run on that order), then taken back while it still finishes, repeated until none
  goes (the what-if is not monotone). `WhosWhoOracle` (every combination, its own clue reading) is the independent check.
- **The table holds the player's ticks and crosses per (person, item)**; what a tick rules out is derived. One pad tap or one
  clue strike is one state; the selection and pen are `remember`. `solved` checks the clues. A cell or clue no answer the marks
  allow can keep turns red after a 1 s settle. The board is a Compose layout (table, scrolling clue strip, pad), not a canvas.
- **Teacher:** clue, only, link, house, what-if (at most 3 steps). From boards marked the player's own way the short what-if
  can be led astray (its steps are taken first come), so the teacher alone tries a 12-step one before giving up; measured.

## Motion and haptics

The reduced-motion setting (`LocalReducedMotion`: the system's, unless Settings > Accessibility overrides it) stills decoration; the hint glow is `ui/Motion.kt`'s
`rememberHintPulse`, never a board's own infinite transition. Haptics are fired centrally in `PlayScreen.push`.
Details and the list of what changes: `docs/REWARDS.md` (Reduced motion and haptics).
Sound is a bank of pre-rendered chiptune tones (`core/Tones.kt`: pulse, triangle and noise voices, synthesised, no audio files), played by `rememberTonePlayer` in
the seam (Android `SoundPool`, web Web Audio): ordinary moves are silent (the haptic stays), a unit's note climbs the board's phrase from `rememberMoveFeel` (`Tones.phraseStep`, derived from the units done), Word
Wheel's rising scale, a win by the size of the show (`Praise.winKind`). Boards reach it only through `LocalBoardTones`, so a walkthrough or
a practice is silent; a variant is drawn from `kotlin.random`, never `Rng`. `docs/REWARDS.md` (Sounds). A win thumps, it never wobbles (a 50 ms freeze, then the whole board drops and rebounds with the fanfare and a heavy haptic on one
frame, `PlayScreen`, from a solving move only); mistakes are small and sideways or a piece's own recoil (Mines' blast, Arrows' lunge). The top show (`Praise.allTiers`, all three tiers of a puzzle on a date) is derived from completions.
A move's feel (the changed squares settle, a unit finished by the rules glows and pops once) is `ui/MoveFeel.kt`'s
`rememberMoveFeel`, read in the draw phase; it fires only on `LocalMoveCount` (raised by `push`, so never on load,
undo, restart or the walkthrough). The same helper dips what an Undo took back (`LocalUndoCount`), runs one wave across
every square when a move solves the board (`LocalSolveCount`; the finished frame waits `SOLVE_WAVE_MS` for it) and fades a
board's squares in when it first shows (`LocalBoardArrives`, saved so a rotation is not an arrival). A new board passes its
cells (in reading order) and units to it, draws with `moveFeel`/`feelSquare` and a real accent, rather than animating its own.

## Levels

A home mode, `Daily/Random · Levels · Training`: numbered levels of five boards from five puzzles; finish all five and the next opens.
Spec and what was built: `docs/LEVELS_SPEC.md`. `core/Levels.kt` (the plan), `SeedHash.level`, `LevelSolve` / `LevelProgress` and
`openLevel` / `recordLevelSolve` in `data/ProgressStore.kt`; the screens are `ui/home/LevelsScreen.kt`, `LaunchMode.LEVELS`,
`Route.Levels` / `Route.Play(level)` in `ui/DaybookApp.kt` and `levelNextOptions` in `ui/play/NextSteps.kt`.

- **`Levels.POOL` is append-only and a plan is `(level, poolSize, deal)` alone.** An opened level stores the pool size it was opened with,
  so adding a puzzle never changes a level someone has started; `LevelsTest` pins the plans, and the web parity check diffs a year of them.
- **Levels 1-5 deal the gentlest first (deal 2).** A stored entry is `"n|poolSize|deal"` (no deal = deal 1, the old free
  shuffle of deck 0); deal 2 deals deck 0 in bands of `Levels.GENTLE`, a frozen copy of `GENTLEST` (edit `GENTLEST` freely,
  never `GENTLE`). The deal is **per player**: `openLevel` writes the one already stored, `Levels.LATEST` only for a first
  level, because mixing deals inside deck 0 would repeat and skip puzzles in levels 1-5. A plan is `(level, poolSize, deal)`.
- **Deal 3 (`Levels.LATEST`) spreads difficulty over levels 1-100** (`docs/LEVELS_SPEC.md`, "Level difficulty"): each level
  is the five boards, five puzzles, nearest a target rising from the gentlest measured puzzle-tier to the hardest, tiers mixed.
  Puzzles are met on Standard in `Levels.LEARN` order (a frozen copy of `GENTLEST`, never edited), all by level 15, so levels
  1-5 are the quickest to learn. The scores are `core/LevelSpread.kt`, **generated** by `LevelDifficultyBuildTest`
  (`DAYBOOK_LEVEL_DIFFICULTY=1`) and as frozen as `GENTLE`: rewriting it moves every deal-3 plan, opened ones too, so a new
  measurement (or ranking a new puzzle into 1-100) is a new deal. A puzzle appended to `POOL` joins deal 3 from level 101,
  where decks and the curve take over.
- **A level board's seed and tier come from its level's stored plan**, never from the route or the full pool, so the play route waits
  for `levelProgress` to load; open a level through `playLevel` (it calls `openLevel` first).
- **Everything about progress is derived** (`LevelProgress.of`): the current level, done levels, "Level N done" on home. Nothing pops up.
- A level solve is recorded twice: `recordLevelSolve` (a replay records nothing new) and a practice `Completion` (`day = null`), so the
  streak never counts levels and totals and best times do. No "New game" on a level board; a solved one is cleared, so reopening replays it.
- **The play screen is keyed on its board** (`key(puzzle.id, seed)` in `DaybookApp`): "Next in level" goes straight from one
  puzzle to another, and without the key `PlayScreen`'s produced board was the last puzzle's state for a frame (a crash on the emulator).
- `RouteSaver` fields are only ever added at the end: a route saved before `level` existed still reads (`LevelsUiTest`).
- The mode row (`Daily/Random · Levels · Training`, `LaunchOptions`) sits above the tier row. Daily and Random share the first
  segment, the one in use large over the other small; a tap on it while selected swaps them (150 ms, snapped under reduced
  motion), and from another mode it returns to the last of the two (`tappedMode`, `dayMode` in `DaybookApp`; Daily on launch,
  the mode is never stored). Every segment has a small second line (the other of Daily/Random, "Level N", "N of 21"
  graduated) so the three share one height; the swap scales the two lines rather than measuring them again, so the row's
  height never changes mid-swap. In Levels and Training the tier row stays laid out, invisible, under the level strip or the
  training strip, so the grid below cannot move when the mode changes. The sticky rows sit tight (4dp over, 4dp between,
  6dp under): at 375x537 the grid starts 12dp higher than with the old one-line row, and no row of tiles was lost.
- **A sideways swipe on Home changes the tier** (`ui/home/TierSwipe.kt`): left harder, right easier, nothing past the ends or in
  Levels or Training, nothing from within 28dp of a side. Decided by the pure `swipedTier` (`TierSwipeTest`); the detector watches on the Initial
  pass and consumes only once a move is plainly sideways (past slop, twice as far across as down, before the long-press timeout), so
  taps, the long-press tile drag and the list's scroll keep working. It calls the same `onDifficulty` as the tier row, whose pill slides
  (`SegmentedControl`), and the new tier's name shows in a solid pill over the grid, then fades (`TierFlash`). Mouse drags count too (Compose's scroll ignores the mouse).

## Daily flow (Today's three, Next puzzle today)

Details: `docs/REWARDS.md` (Today's three; the finished frame).
- **Today's three is the date alone** (`core/TodaysThree.kt`: a quick, a mid-weight and a word/number puzzle, each slot stepping
  through its own list; the lengths, 11 / 13 / 7, stay pairwise coprime, and a test says so), the same for everyone, and it absorbed today's pick ("Try me!" and its first-screen square are gone;
  the gold sparkles mark the three's next board). `TodaysThreeTest` pins three dates: editing a list moves everyone's three.
  **A new player gets the starter three** (`TodaysThree.starter`: fewer than `NEWCOMER_SOLVES` = 5 solves, counted from the
  once-a-day basis so a fifth solve never swaps the three mid-day): the shared quick slot, then `STARTER_MID` (Sudoku,
  Nonogram, Mines) and `STARTER_WORDS` (Lexicon, Word Wheel, Crossword, Target), the gentlest of each kind in
  `PuzzleOrder.GENTLEST` (a test ties them). The achievement accepts either three for a day.
  The home row is always laid out at one height (unseen in Levels), so it never moves the grid.
- **"Next puzzle today"** (`nextToday`, `NextKind.TODAY`) is offered only on today's daily opened from Home, and opens the next
  board as from Home, so back from it is Home, as from the board solved. Not on an archive board (its back is its own archive).
- Set share cards (`threeShareCard`, `levelShareCard`) are derived from completions and `level_solves`; nothing new is stored.
  Share cards say what was done, never what is left (`REWARDS.md`).

## Daily reminder (Android)

One notification a day at the player's hour, off by default. Shared: `ui/home/Reminder.kt` (the text, the offer rule,
Home's offer card), the three `reminder*` keys in `LaunchPreferences` (launch preferences, never progress), the row in
Settings. Android: `platform/AndroidReminder.kt` (in `web/build.gradle.kts`'s `androidOnly`); the web has
`OFFERS_REMINDER = false` and stubs, since a closed page cannot notify.

- **Asked once.** Home shows one card under the grid after the first solve on a second day (`reminderOfferDue`) until the
  player answers either way, then never again; a choice in Settings answers it too. Notification permission (Android 13+)
  is requested only when the player turns the reminder on; refused, it stays off and Settings says why.
- **No nagging.** One inexact `AlarmManager` alarm (`setAndAllowWhileIdle`, no exact-alarm permission, no dependency), set
  again when it fires, on boot or update (`ReminderRestoreReceiver`) and on app open. When it fires it posts nothing if a
  daily is solved today, the reminder is off, or it is before 5 am. The text (`reminderText`) greets for the time of day,
  says how much of the day is left and invites; no streaks, no loss framing, no follow-ups (`ReminderTest` guards the words).

## Typography

Fredoka (SIL OFL) on both builds, applied by each seam's `platformTypography` via `Typography.withFamily`;
`Palette.kt` names no family. Static instances only; how they were made and cut: `docs/WEB_BUILD.md` (Fonts).

**Glyphs inside a board are sized from their square with `boardSp()` (`ui/BoardText.kt`), never `(cell.value * k).sp`**, so the
system font size cannot push them out of their square; text outside the grid scales, but chrome in a fixed slot (play
header, tier pills, pen buttons, tile names) stops at 1.3x (`CappedFontScale`; web `?fontscale=2` to check). Rule and the line-height catch:
`docs/PUZZLE_STANDARDS.md` section 6; `BoardTextSizeTest` fails on the old pattern.
Digits take a role from the one scale, `BoardGlyph` in the same file (given, entered, clue, corner, note: a share of
the square and a weight); heavy region walls and frames take `ColorScheme.wall` (`ui/BoardDraw.kt`), never black or `onBackground`.

## Colour

Boards were coming out beige and one-note. The shared hue family, the three layers (scheme / accent / content hues)
and the rules are in `docs/COLOUR.md`; read it before choosing colours for a new board.
An accent drawn as text goes through `BoardHues.textOn` (fills and rings keep the raw accent); `BoardHues.ink` steps its
lightness to 4.5:1 on the surface and background, so token palettes pinned by CIEDE2000 use the fixed `BoardHues.deep`; a
state shown by colour also changes shape or weight (Nonogram's faded/dashed clues, Tally's dashed red chip). `ContrastTest`
holds all of it, and `scheme.error` is a text-safe Clay in each scheme.
The accents were designed as one set (`docs/COLOUR.md`, layer 2): one luminance band where every raw accent reaches 3:1
on both schemes, clear of the error red and the success green, spread for the largest closest pair (CIEDE2000 5.79 normal,
2.90 colour-blind). Text in an accent goes through `BoardHues.textOn`, or `onTint` on the accent's own tint. A new puzzle's
accent comes from `AccentSpacingTest`, which prints the candidate that keeps those rules furthest from every accent and pins
the floors; the palette tests share its maths (`ColourDistance`).

## Rules that keep being relearned

**Never ship a board the generator has not proved.** Kings, LITS, Mosaic and Shikaku each had a
fallback that was reachable and unvetted, and in three of them that fallback was what players
actually got. LITS's real generator had *never once run*. When you add a fallback, ask what
fraction of seeds reach it, and measure rather than assume. Size the test's sample to the rate:
Atoms once fell back on 13 of 365 Expert days (3.6%), which `FallbackTest`'s twenty seeds miss
about half the time; its Atoms test now walks a full year of daily seeds on every tier (~2 s).
Every board with a fallback has `generateVerified` (null when nothing was proved), which is what tests assert: on the puzzle object for Kings, Atoms, Sets (public) and LITS, Mambo, Shikaku, Snap, Inequality, Cipher, Cages, Sudoku, Pipes (`internal`); on the rules object (`RegionsLogic`, `TentsLogic`, `TallyLogic`, `SandLogic`, `TargetLogic`, `WordWheelLogic`, `PointersLogic`, `NonogramLogic`, `DominoLogic`, `MinesLogic`, `LampsLogic`, `CodewordLogic`, `MiniLogic`) for the rest. `FallbackTest` walks a year of daily seeds on every tier through it for Kings, Atoms, LITS, Shikaku, Snap, Inequality, Regions, Tents, Cipher, Word Wheel, Sand, Sudoku, Pointers, Pipes, Cages, Tally, Dominoes, Mines, Lamps, Codeword and Mini (none ever falls back; `assertYearProved`, whose `onProved` hook lets Cages, Tally, Dominoes and Lamps count attempts too); Sets, Mambo and Target have their year in their own rules tests.

**A truncated search is not a proof.** LITS reported "gave up" as "exactly one solution" because
its node budget returned quietly. Make the distinction structural — a type where only the proved
case carries a result — not a count a caller can misread.

**`solved` must check the rules, not compare to a stored answer.** Kings and LITS both rejected
correct solutions this way. Keep the stored solution for hints; let a validator decide success.

**Size layouts from both axes.** Three home-screen motifs and the Sets board each drew outside
their box because height fell out of width via `aspectRatio` and nothing consulted the height
available. `Mosaic.Board` has the right shape: `minOf(maxWidth / w, maxHeight / h)`. Seven more boards
sized from width alone until the taller hint slot ran Pipes' 5x7 board a row under the panel at
390dp, and Sudoku over the header on a 693dp-tall screen; ten now read `maxHeight`, and Tower, whose
guess list is a bottom-anchored `LazyColumn` with `weight(1f, fill = false)`, is bounded by its box instead
(audited at 390x844, 390x664 and 360x640: every grid is within a cell of the box on its binding axis,
and the same size before and after solving).
Check the *short* end too: a phone browser with both toolbars showing is ~540dp tall, where the old
reserved 156dp hint slot left Pipes a board a third of the width (the hint is now a popover, so the
board keeps the whole box). Test layouts at 375x537 as well as a tall screen.

**`PlayScreen` pushes an undo entry for every state it is handed.** Transient UI state —
selected colour, palette choice, drag in progress, a settle timer — must live in
`remember`/`rememberSaveable` inside the composable, never in `PuzzleState`. A drag or a
double-tap must emit exactly one combined state.

**The play log is part of the saved game.** `SavedGame.log` (`ui/play/ShareCard.kt`, `PlayLog`) is one character per move, undo,
restart and counted hint, and the share graphic is drawn from it. Append to it exactly where the hint count goes up and where
`push`/`undo`/`restart` change the board; keep it out of `PuzzleState`; it defaults to "" so older saves load (`ShareCardTest`).
Restart is undoable (except in Pointers and Mines, which have no Undo): it pushes the cleared board, and `SavedGame.restarts` marks where, so
an Undo that crosses one logs `u` and `PlayLog.played` hands the restarted moves back.

**The streak counts only dailies solved on their own day** (the owner). `Completion.playedOn` (a sixth field, absent from
records written before it, which count as before) is when a solve was made; read `Completion.streakDay`, never `day`, for
anything about the streak or days played. `day` is the board's date (archive marks, best times, "solved today"). `docs/REWARDS.md`.

**Back goes where the screen was opened from** (`backFrom` in `ui/DaybookApp.kt`): an archive game returns to its archive at that
day, for system back, the web's back, the BackButton and the finish frame's Done alike. The archive opens by day (`Route.Days`,
`ArchiveDaysScreen` in `ui/archive/ArchiveScreen.kt`: a month calendar over that day's puzzles, in the home grid's order, each tier
one tap; it opens on yesterday), from Home's Archive button and from a tapped day on the streak calendar (`fromStats`, so the chain
ends at Statistics). A puzzle's name there drills down to its own calendar (`Route.Archive`, `fromDays`). `fromDays`/`fromStats` ride
on `Route.Play` so back walks the same chain. Route strings only gain fields at the end,
read with `getOrNull`, so a route saved by an older build still decodes (`RouteTest`).

**Feedback must not move the board.** Mambo's caption shifted it ~45dp when an error appeared,
which causes mistaps. Reserve the space (`minLines == maxLines` works well), or float it over the content as the hint
popover does. Violations also
wait ~1s debounced, because a player passing through an illegal intermediate state should not be
shouted at.

**Forcing a board is not the same as forcing it minimally.** Snap added clues until its solver
said "one answer" and stopped there, which left every clue that had been essential when it went on
and redundant five clues later — 62% of every board numbered, and Expert boards with 39 of 42
squares filled in. Adding until forced is only half the job: take back everything the rest of the
board already implies, and give the result a budget it has to come in under. Ask what fraction of
the board a generator gives away, and measure it, because a board with one answer can still be a
board with nothing left to work out.

**A drag that emits state per step cannot key `pointerInput` on the state.** Every other board
accumulates its sweep in `remember` and emits one combined state on lift, so keying on `s` is fine
there. Snap emits a square at a time, so it keyed on `s.waypoints` to stop the detector restarting
mid-drag — and because those never change, the coroutine never restarted and the `s` it captured
stayed pinned to the board as first composed. Lifting your finger and starting a second drag reset
to that empty board and wiped the line; Snap was completable only in one unbroken 42-square drag.
If a gesture detector must outlive state changes, read the board through `rememberUpdatedState`,
never the captured value.

**Derive, don't store, anything computed from board state.** Kings' eliminations and LITS's
impossible squares are recomputed per render. The exception is the player's own marks: Sudoku's pencil
marks (`SudokuState.notes`, a 9-bit mask per cell, defaulted so old saves load) are stored and always
drawn, never refused or hidden for contradicting a peer digit (a hidden note looks like a refused tap;
the owner wants bad judgements allowed, and conflict display catches them). Placing a digit strikes it
from its peers' notes in the same state, so one undo restores both; erasing does not resurrect them.
The notes mode is `rememberSaveable` in the board, not state. Storing them means owning which to retract when a
piece is lifted, which is where the feature rots.
Keyboard (web, or a hardware one): `puzzles/SudokuKeys.kt` maps a key to a `SudokuKeyAction` (digits, clear, arrows; pure, tested) and applies it through the pad's own `enter`/`withCell`/`toggleNote`, so notes, undo and peer-striking cannot differ; chords are ignored and a held digit is not repeated. The board's box takes focus (clicking a cell, or a hint opening, hands it back; on the web `focusCanvas` in `web/Main.kt` gives Compose's canvas DOM focus on load, so a link or a refresh takes keys without a click).

**Verify by rendering, not reasoning.** Icons, motifs, crescents, pipe joints and crosses have
all failed at true size in ways nobody predicted — a crown read as a comb, pages as a boat hull,
Atoms as a wireframe. A Java2D harness driving the real geometry is the established approach (not
checked in). Then check on the emulator; several bugs only appeared there. For the web build there is a
checked-in harness: `tools/render/render.py` (see "Rendering harness" under Web build).

**README screenshots go stale silently.** `docs/screenshots/*.png` are emulator captures at half
scale (540x1200), taken on a *clean install* with the date pinned so the home screen shows a fresh streak badge and unplayed
boards rather than whatever the session happened to leave behind. Nothing checks them, so a change
to a board's look or to the home grid means recapturing them in the same pass — the home shot in
particular names the puzzle count in its alt text.

**Write agent patches early.** One agent lost a complete implementation by leaving
`git diff --cached > patch` until the end and dying on a rate limit.

## Web build (`web/`)

The whole app in a browser via Compose Multiplatform on Kotlin/Wasm. Live at https://knowhowit.com.au/daybook/.
Full detail (seam, storage, fonts, harnesses, parity, lessons): `docs/WEB_BUILD.md`. The essentials:

- **One copy of the code.** `web/` compiles `app/src/main/java` itself, minus an `androidOnly` list, so shared
  files must stay free of `android.*` and `java.*` (`java.time`, `String.format`, `System.*` all fail the wasm
  compile). Platform differences go through the `com.joebywan.daybook.platform` seam, one file per build.
- **Hash iteration order must never reach the `Rng`.** The JVM and Wasm walk hash containers differently, so
  the same seed carved a different board. Audit any new generator code, and replay (don't re-pick) an order
  already baked into shipped boards (`core/JvmHashOrder.kt`).
- **Parity:** CI diffs a year of boards between JVM and browser (`tools/web-parity/check.py`): the required `web-parity`
  check on pull requests, and again before the deploy.
- **Check any web layout change in WebKit as well as Chromium**, at 375x537 as well as a tall screen:
  `tools/render/render.py`.
- **Every drawn character needs a bundled glyph.** One no bundled font has makes Compose fetch a Noto font from
  fonts.gstatic.com, which the CSP blocks (a box) and the privacy promise forbids. Re-cut with `tools/subset-font.py`
  (Fonts in `docs/WEB_BUILD.md`); `tools/render/glyphs.py` fails on any request off the local server.
- WebKit's "Refused to apply a stylesheet ... style-src" console error, once per screenshot, is Playwright's own injected
  style, not the app's (a blank page with only the CSP shows it); `render.py` ignores it in WebKit. Don't report it.
- Build: `./gradlew :web:wasmJsBrowserDistribution`. Several sessions share the one browser pane: pass a `tabId`.

## Tests

New tests should be **independent of the code they check** — Mambo, LITS, Kings,
Shikaku, Mosaic and Snap tests each carry their own solver or rule checker, deliberately written on
a different principle so the two cannot share a blind spot. `LitsAuditTest` and `MosaicOptimumTest`
are differential; `LitsMarkingTest` brute-forces every legal shading of a fixed board.
`SnapCluesTest` is the clearest case of the principle: its solver is naive depth-first with no node
budget, no pruning, and the waypoint order checked only once a full line exists — the opposite of
the generator's on every one of those points. It is slow, several seconds per Expert board, so the
sample thins as the boards grow rather than dropping the large ones.

`FallbackTest` exists because a generator can degrade silently. It has twice needed tightening
after passing on plainly broken boards: once while LITS fell back on every board, because it
asserted a property the fallback also satisfied, and once while Snap numbered 39 of 42 squares,
because it asserted only "fewer than every square". A test that cannot fail is not a test, and an
assertion loose enough to survive the bug is the same thing wearing a number.

## Git and releases

- **Standing instruction: finish the job.** When the work is done and the local tests are green, push
  the branch, open the PR and merge it once CI's `build` and `web-parity` are green, without asking first. The owner
  has authorised this for good and is tired of repeating it. Only stop and ask if CI fails in a way
  you cannot fix, or the change touches something under "Settled — do not reopen".
- Never commit to `main`. Branch, PR, merge — I do the merging, not the owner. This is now enforced: a
  repository ruleset ("Protect main", set up 2026-10-02) requires a pull request, requires the CI
  checks named `build` and `web-parity` to pass, and blocks force-pushes and deletion. Branches need not be up to
  date with `main`: that rule (tried 2026-10-23) stalled every open PR behind each merge, and a merge queue needs an
  organisation-owned repo. Two PRs green alone can still break `main` together; `release.yml` tests any tree CI did not
  pass, so that stops the release rather than shipping. The repo owner's account can
  bypass on pull requests only.
- **Docs-only changes do not need CI, and the owner has said so outright** ("it doesn't impact tests,
  and it doesn't impact the app, why burn the compute time?"). A PR that touches only documentation
  (`*.md`, everything under `docs/`, including the images and the `docs/` helper scripts that nothing
  in the build runs) is opened and merged straight away with `gh pr merge <n> --merge --admin`,
  without waiting for `build`. That is the owner's standing authorisation to use the bypass for this
  case and this case only. The `build` check is still required by the ruleset, so it cannot be skipped
  by path-filtering the workflow (a skipped required check blocks the merge). A change that touches code,
  Gradle files, `web/`, `tools/` or a workflow, even alongside docs, waits for green CI. Nothing in the workflows pushes commits to `main` (releases are created
  with `gh release create`), so the rule does not get in their way.
- Renovate's PRs get their `build` from `tools/dispatch-ci-for-renovate.sh`, which dispatches
  `ci.yml` on their branch; a dispatched run posts `build` and `web-parity` commit statuses because its check runs
  alone do not satisfy the ruleset. Keep those steps in `ci.yml` and the jobs named `build` and `web-parity`.
- Commits use `4845431+joebywan@users.noreply.github.com`. The personal address must never reach
  a commit or a remote. No `Co-Authored-By: Claude` trailer.
- Push to `main` builds a signed APK and publishes a GitHub Release automatically. It runs the unit tests again unless
  `ci.yml` already passed the identical source tree (its `tested-tree-<sha>` artifact); a different tree is always tested.
- **Versions are `<version.txt>.<run number>`, so 1.x.y.** The last number is the CI run and rises on its own: fixes,
  tuning and docs leave `version.txt` alone. **A change that adds a feature a player would notice (a new puzzle, a new
  kind of help or setting) bumps the middle number in `version.txt` in the same PR** (`1.0` to `1.1`). The first number
  moves only when the owner says so. `versionCode` is the run number whatever the name says, so Play and F-Droid never
  depend on the bump; it went unbumped at `0.1` for 150 releases before this rule was written down.
- **That release now reaches Google Play automatically, so a merge to `main` ships.** After the GitHub
  Release is made, `release.yml` calls `publish-play.yml`, which uploads the signed `.aab` and rolls it
  out to everyone on the `production` track (full rollout, not staged: nobody would raise a staged
  percentage by hand).
  The version code is the workflow run number, so it rises on its own. Google reviews every update
  before testers or users see it, so "released" means uploaded and in review, not live. The upload
  authenticates with the `PLAY_SERVICE_ACCOUNT_JSON` secret, a service account allowed to release to
  testing tracks and production (granted October 2026). Test as if every merge reaches users, because it will: the owner has accepted
  that a free puzzle app carries little risk and has asked for hands-off rollout, on the footing that
  testing is Claude's responsibility.
  - A manual upload is Actions > "Publish to Google Play" with a tag and a track.
  - Saved games are the real danger: they are serialised by class name, and a bad build cannot be undone
    on someone's phone. Treat any change to a `PuzzleState`, a serialised class or `DataStoreKeyValueStore`
    as one that must load an old save. `SaveCompatibilityTest` reads saves a released build wrote
    (`app/src/test/resources/saves/`, never edited) and fails if any no longer loads or loses a field.
    That is the debug build; `release.yml` runs the same saves through the signed, R8-minified APK on an emulator
    before publishing (`tools/save-check/check.py`, `.github/actions/save-check`), and a lost or changed save blocks
    the release. Run it locally on any APK, or on a published one with Actions > "Release save check".
- **"What's new" is `fastlane/metadata/android/en-US/changelogs/default.txt`, and every release shows it**: Play gets it
  through `publish-play.yml` (`whatsNewDirectory`; over 500 characters fails the upload, so `ChangelogLengthTest` fails the PR first), our F-Droid repo files it under the
  newest APK's version code (`tools/fdroid/build-repo.sh`). Rewrite it in the PR that changes what players see, or the next
  release repeats old news. The GitHub Release carries it too (`release.yml`, above the generated notes), which is what
  f-droid.org shows: its entry's `Changelog:` link is the Releases page (f-droid.org does not read the file itself; it wants
  `changelogs/<versionCode>.txt` in the tagged commit, a number not known until the release runs).
- **A merge to `main` also reaches our own F-Droid repo** (`publish-fdroid.yml`, same APK and key as the GitHub
  Release; `docs/fdroid/README.md`). The listing text and images are `fastlane/metadata/`, the one copy for both stores: a
  merge that changes the text pushes it to Play (`play-listing.yml`), so edit it there, never in Play Console. The code is AGPL-3.0-or-later (`LICENSE`, chosen by the owner); f-droid.org's entry is live (https://f-droid.org/packages/com.joebywan.daybook/).
- Signing key: `~/Documents/github/Claude/daybook-android-signing/` — the only readable copy.
  Certificate pinned in `android/release-key.sha256`; `tools/verify-apk.sh` fails a release whose
  certificate, name or debuggable flag is wrong. A green Gradle build is **not** proof of signing.
- Actions are pinned to commit SHAs (`# vX.Y.Z` beside each); pin a new one the same way. Gradle
  verifies every dependency against `gradle/verification-metadata.xml` (sha256). A dependency or
  plugin change must regenerate it: `./gradlew --no-daemon --write-verification-metadata sha256 test
  assembleRelease bundleRelease :app:assembleDebug :web:wasmJsBrowserDistribution`, and the diff
  reviewed. Renovate does not do it here (it needs `allowedUnsafeExecutions`), so its Gradle PRs fail
  CI until someone does.

## Settled — do not reopen

- `applicationId = com.joebywan.daybook` and the signing certificate are permanent.
- The app is named Daybook. Display name can change freely; the package cannot.
- Mosaic's move limit equals the proven optimum, slack zero on every tier. Difficulty comes from
  board size, sections and palette, not from spare moves.
- Mosaic is a Kami-style region flood-fill, not Fill-a-Pix.
- Snap boards keep **exactly one** answer, and their difficulty comes from how little is given
  away rather than how much. Clue counts are minimal at every tier — about a fifth to a quarter of
  the board — and Standard being harder for it is the intended trade, confirmed by the owner:
  "I should have to make choices and think. It's boring otherwise." Do not add clues back to soften
  a tier, and do not relax uniqueness to open the board up further.
- LITS regions are capped at seven squares, and its win check does **not** require the shading to
  be connected (PR #15: a player's valid disconnected answer was refused). So a board must have one
  answer *without* that rule: the carve steps search connected shadings (fast, monotone), and the
  finished candidate is proved again with no connectivity rule before it ships. Do not restore the
  connectivity rule to the win check to make uniqueness easier. `LitsUniquenessTest` asserts it
  with the independent `LitsOracle`; `FallbackTest` walks a year per tier through `generateVerified`.
  Tiers are gated on the teacher's walk (`LitsTeacher.depth`, `Lits.score`): Standard never needs neighbour or what-if,
  Expert takes what-if in its first eight proved draws, else neighbour (`docs/TEACHING.md`, Tier depth).
- Screen readers are not a goal: these are visual puzzles a blind player could not play however the boards were
  exposed (the owner, 2026-10-22). Text size and colour-only signals were offered and left;
  reduced motion (following the system setting) was added at the owner's request, 2026-10-23.
- **Any orientation, any window size** (the owner reversed the old portrait lock in October 2026: Android 16 ignores
  the lock on large screens anyway, and Play flags it). Do not bring the lock back. A wide window puts the play screen's
  board beside a 360dp side panel (title, clock, the hint as a panel rather than a popover, toolbar at its foot, and the
  finish frame and pill) whenever that gives the board more room than stacking: `PlayLayout` in `PlayScreen.kt`, one
  `Layout` for both shapes so a rotation keeps every board's `rememberSaveable` state. A phone in portrait gets the stacked
  layout, pixel for pixel what it was. The home grid takes a column per 150dp (3 to 7); a short wide window (under 480dp tall: a phone on its side) puts the date and streak beside the title and the two toggles side by side, and adds columns, down to 110dp tiles, until two rows fit (`homeColumns`). Boards size from both axes, as
  below; Tower caps its width at 480dp. Check a layout change on a tablet in both orientations, in a resized window and
  on a phone on its side, as well as the phone sizes.
- If this reaches the Play Store, the developer name and contact details the listing publishes are
  acceptable to the owner ("I don't care about my name being on the playstore"). Not a blocker.
- `gradle/actions` stays on v5. v6 requires accepting Gradle's Terms of Use for a proprietary
  caching component, and the owner has declined for now; `renovate.json` holds it back.

## Open

**The full list of outstanding work is `docs/TODO.md`.** Keep it current as you work: add what you
find but are not fixing, delete what you finish, in the same PR.
