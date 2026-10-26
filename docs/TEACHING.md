# Teaching: hints, walkthroughs and the play screen

Moved out of CLAUDE.md. The *what to build* for a teacher is `PUZZLE_STANDARDS.md` section 7; this file holds the settled play-screen UX and the measured fallback table.


All puzzles teach. The shared pieces are `core/Teaching.kt` (the contract), `ui/teach/Hints.kt`
(the session, the panel, `WatchHint`), `ui/tutorial/TutorialRunner.kt` and `ui/play/PlayScreen.kt`;
each puzzle has a `<Name>Teacher.kt` except Snap and Pointers, whose teaching is their walkthrough
(`offersHints = false`). Pointers has no undo either, nor has Mines (`offersUndo = false`): see CLAUDE.md, the owner's decision. Everything in the contract has a default, so a board adopts it alone:

- `teach(state): Deduction?` — a mistake to take back, or one step reasoned from what the player can
  see: nudge + focus cells, explanation + cited cells, targets, and `apply`/`isReached` closures.
  Null means "not adopted", and `PlayScreen` falls back to `hint()`.
- `LocalBoardHighlight` — a CompositionLocal, **not** a `Board` parameter, so boards that ignore it
  compile untouched. Strong cells get the outline; everything else dims.
- `tutorial: List<TutorialFrame>` — played by `TutorialRunner` on the puzzle's real `Board`. A frame's
  `accepts` (one emitted state is the move) or `steps` (`TutorialStep.REJECT` / `PROGRESS` / `DONE`, for a
  move of several states: Lexicon's BOAT then Enter, Sudoku's square then digit, the digit-pad puzzles' clash made and cleared) gates the move; a refused
  state is dropped and the progress so far stays. The "your turn" frame shows Hint only when the puzzle
  offers hints. Writing them: `docs/PUZZLE_STANDARDS.md` section 8, with the style rules.
- **A finished walkthrough move moves on by itself** (owner's request: Next after a move only confirmed what the
  player had just done). The move lands and chimes, its `done` line shows for `TUTORIAL_HOLD_MS` (1 s) while the
  board takes no input (so a quick extra tap cannot count twice or land on the next board), then the next frame
  opens with "✓ done" in its feedback line until that frame has news of its own. Next appears only on READ frames
  (and as Play on the last); on a move frame its place stays empty, so the row keeps its shape. Back cancels a
  hold and lands on the previous frame's start. The hold is not an animation, so reduced motion leaves it alone.
- UX, settled by the owner: tap 1 nudges, tap 2 explains, the **player makes the move**, and the
  panel confirms and clears when `isReached` sees it. Only an explicit "Show me" applies it. **One
  hint is counted per deduction opened**; explaining and Show me are free. The panel carries the next step beside Close
  (Why?, then Show me), where the reader's eye already is, and so does the toolbar's Hint button; both run the one
  `HintSession.tap`, so they cannot count or step differently (#287 had taken it out of the panel; the owner wanted it back).
- **Working it out rings the chime** (owner's request): when the player makes a hint's move themselves, the confirm plays
  the solve chime (`WatchHint`'s `onReached`, the play screen's and free play's `LocalChime`), muted by the Sound switch.
  "Show me" clears without confirming, so it never rings, and a move that solves the board has only the solve's own chime.
- The panel is a **popover over the play screen** (`HintPopover`), not a slot in its column, so the
  board's box is the whole of the space between header and toolbar and never changes size: not when
  a hint opens, not for the first-visit offer, not on solve. (A reserved slot did the opposite: a
  blank ~156dp band all game on every teaching puzzle, and a visible jump larger on completion.)
  What it keeps off is everything the hint names at any stage, not only what glows: the nudge lights the focus alone,
  but `HighlightBounds.named` adds the squares the explanation will cite and change, so the popover does not sit on them
  and does not jump when the second tap lights them.
  It sits on the opposite half from the highlight: highlight low, popover above (under the clock);
  highlight high, popover below (hugging the toolbar, which it never covers). Both clear, then
  the half rule decides, ties below. A control the player needs (`keepClear`: Sudoku's digit pad,
  Mosaic's palette, Tower's peg and swatch rows) is kept clear above all (overlap weighs 1000 against
  the highlight's 4): when "below" would land on one, a third place just above it is tried, so on a
  short screen the popover covers the board's own rows rather than the pad. The rule is the pure
  `placePopover` (`ui/teach/PopoverPlacement.kt`, pinned by `PopoverPlacementTest`). A
  side that still overlaps slides as far as it can (above may rise over the header) and then shrinks
  to 128dp (four lines of text), its text scrolling behind a fade. Taps outside the card reach the board, so the move can
  be made with the explanation up. Changes of side or highlight glide (220ms).
- **In a wide window** (board beside a side panel: `PlayLayout` in `PlayScreen.kt`) there is no popover: the same
  `HintPanel` sits in the side panel between the clock and the toolbar, so it covers nothing of the board and the board's
  box still never changes. The finish frame and the results pill keep to the side panel there too.
- **Boards report where the highlight is**, in window coordinates, through `core/HighlightBounds.kt`
  (`LocalHighlightBounds`): `highlightGrid(cols, rows)` on an even grid's node, `highlightAnchor(i)`
  on one element that is itself a highlight index (Tower, Sudoku's keys), `reportHighlight { }` for
  custom geometry (Atoms: an atom's cell, a line's two atoms). A new board must report or the
  popover assumes "below". Outside the play screen (the walkthrough) the local is null and these
  do nothing.
- The walkthrough's `TutorialRunner` keeps its own fixed `HintSlotHeight` (156dp) slot: its board
  never changes size either way.
- The finish is a compact "Congratulations!" frame (`FinishedFrame`) docked to the bottom of the play screen, an
  overlay with the time, hints and next-step tiles; the toolbar stays laid out (hidden), so
  completing a puzzle cannot resize the board. It is docked on every height: centred, it hid 50-58% of
  Snap's board at 390x844, while docked it clears the board there (and on short screens covers only the last
  row or two). (Snap, the one board that never reached
  it through hints, was driven to a solved state in Chromium and WebKit at 390x844, 390x664, 360x640
  and 375x537, light and dark: the board's box is identical before and after, and the tiles take taps
  over the board's raw-pointer canvas, which is switched off once solved.) The tiles come from the pure `nextOptions(daily, tier,
  doneTiers)` in `ui/play/NextSteps.kt` (`NextStepsTest`): a random board offers Another/Easier/Harder/
  Done; a daily offers Easier daily/Random/Harder daily/Done, skipping tiers already done *that date*
  (`tiersDoneOn(completions, puzzle, day)`; random games never count) and counting the tier just played
  as done, because `onSolved` records it asynchronously. A Daily tile keeps the route's `day`, so on an
  archive day it is that past date's board.
- The solve sound is the owner's pick of five candidates: a two-note marimba pluck, G5 then C6, ~0.8 s,
  synthesised (no audio file) by the pure `core/SolveTone.kt` (`SolveToneTest`), so both builds play
  identical samples. `rememberSolveSoundPlayer(enabled)` is the platform seam: Android plays a static
  `AudioTrack` (USAGE_GAME, so media volume; skipped when the ringer is silent/vibrate; built and
  released off the main thread), the web a Web Audio buffer whose context the first taps wake (Safari
  only allows it inside a gesture). `DaybookApp`'s `onSolved` calls it, which `PlayScreen` runs once per
  solve (`recorded` is saved), so rotation or reopening a solved board stays quiet. The "Sound" switch
  sits under the timer's in Settings (`LaunchPreferences.playSound`, default on).
- `teach` runs on `Dispatchers.Default` (the Hint button reads "Thinking..." meanwhile): Mosaic's
  hardest hint took 1.2 s on the emulator, which on the main thread was a frozen frame.
- An open hint re-checks itself when the board changes some other way. If the teacher's next step on
  the new board is the same kind (mistake/step) touching the same cells, it stays; otherwise it
  **closes quietly** — no swapped text, no new charge; the next Hint reasons afresh.
- Across recreation (rotation is locked off, but theme, font size and process death remain) the
  route, the game and the hint's stage are saved; the deduction is re-derived from the board.
- The walkthrough is offered once, as one passive line over the clock on a player's first visit, until their first move;
  `ProgressStore.tutorialsOffered` records it the moment it is shown. "How to play" opens it; the
  rules list is its "Rules" summary.

**The solver must not be able to see the answer.** Each teacher's reasoning entry point takes only
visible state, so a step cannot lean on the answer — a guarantee of the signature. The answer is read
only to flag mistakes and for the fallback, which says openly that it is pointing at the answer.
Soundness on a *unique* board cannot tell reasoning from peeking, so the tests also walk boards with
several answers and require each step to hold for every answer still possible.

What each teaches, and how often a player walking a board by hints alone reaches the fallback
(200 daily boards per tier from 2026-01-01, Standard / Hard / Expert, share of boards):

| puzzle | teaches | fallback |
|---|---|---|
| Sudoku | full house, hidden/naked singles, locked candidates, pairs, a bounded what-if chain; every step ends in a placement and never reads the player's pencil marks | none on a generated board: the generator digs only while the tier's ladder finishes it (a year of daily boards per tier, and 300 a tier in `SudokuTeachingTest`; before that gate 0.5 / 6 / 34% of boards). Per board: naked single 0 / 1.7 / 1.1 steps, locked candidates 0 / 0 / 0.7, pairs 0 / 0 / 0.3, what-if 0 / 0 / 0.5 |
| Kings | last square, locked to a line, would empty, N confined, what-if | none: the generator keeps a board only if the ladder finishes it from blank and the tier's gate holds (a year per tier, hints alone finish all) |
| Mambo | pair, sandwich, link, quota, almost, what-if | none: the generator's ladder is the teacher's, and each tier is gated on the rung it needs (a year per tier and 500 boards a tier, hints alone finish all; almost-met 0 / 2.9 / 5.7 and what-if 0 / 0 / 4.1 steps a board; the fallback exists for boards not made here) |
| Pipes | border, set neighbour / whichever way it turns, no loop | 0: the generator ships only boards the reasoning settles (`PipesTeacher.settles`, which also proves one answer); 300 boards per tier, hints alone finish all, no-loop 0.6 / 0.8 / 0.8% of steps |
| Shikaku | only fits, only reaches, common cells, what-if | 0 (the generator ships only boards the ladder finishes; was 0.5 / 2.5 / 3.5%) |
| Mosaic | finish, count fills against colours, biggest swallow, centre; fallback offers a proven fill | 1.5 / 9 / 12.5% |
| Sets | two cards fix the third, trait by trait; continue a pick, fresh, reuse a tinted card | none exists |
| Atoms | one neighbour, all forced, at least one, crossing, isolation, only way out, what-if | none on a generated board: a board ships only if hints finish it (the depth gate, below) |
| Snap | walkthrough only: corners, dead ends, cutting back, rubbing out | no hints |
| Pointers | walkthrough only: a free arrow leaving, a bump costing a life, the blocker cleared first | no hints (nothing to teach: a free arrow is visibly free and taking one never hurts) |
| LITS | whole region, overlap, avoid 2x2 / letter clash, neighbour, what-if | 0 / 0 / 0% (300 boards per tier). It was 42 / 23 / 30% while 43 / 22 / 29% of boards had several answers under the win check; the generator now proves one (the test holds one-answer boards under 2%) |
| Tower | one change, only colour left, accounted for, what-if; else a guess that fits every score | every board, 28-35% of turns: Mastermind is mostly choosing a guess |
| Lexicon | a typed row that cannot be submitted, a letter pinned to its slot by the marks, one word left, else the word that leaves the fewest, with how it splits them ("7 words ... fit every mark. Try the one that goes “...” (5): it can be marked 5 ways, and none leaves more than 2"; given as a crossword clue, not the word). The opener is one of seven per length, picked per board (`LexiconState.opener`) | every board; 93% of turns after the opener (pins are the other 7%): choosing a word is most of the game. A pin holds for every accepted word that fits, not only the answer list |
| Inequality | last square, only digit, only place, then the same two once the signs are counted (candidates cut by `a < b` bounds, to a fixpoint), then a naked pair and a hidden pair in a line (Hard and Expert) | none: the generator keeps a board only while these finish it (200 boards per tier, hints alone finish all; the fallback exists for boards not made here) |
| Nonogram | one line at a time: clues fill the line, clue already complete, overlap of the clue's slides, what the marks already in the line leave possible | none exists: every board is line-solvable, so some line always has a square to settle (200 boards per tier, hints alone finish all) |
| Tents | one fact at a time, mirroring the generator's ladder: no tree beside it, row/column done or full, around a tent, a tree's one square left, a square touching all of a tree's choices, a square whose trees already have their tents. Crosses are notes and never read; a mistake is a tent no answer keeps (no tree, touches a tent, over a clue; sound because the board has one answer) | none: the ladder finishes every shipped board (200 boards per tier, hints alone finish all, 11.6 / 18.4 / 27.1 steps per board; a cross group is one step; the fallback exists for boards not made here). Steps also checked against every answer of 255 random 6x6 boards with several |
| Regions | one fact at a time, mirroring the generator's ladder: the last square of a region, the only digit its region and touching squares leave, the only place in a region for a digit (every other empty square has that digit touching it), then (Hard and Expert) a locked candidate: a region's digit whose every place touches one square outside it strikes it there, and that square is left one digit or its region's digit one place. A mistake is a digit the stored answer lacks (sound because the board has one answer): too big for its region, repeats in its region, or touches an equal digit | none: the ladder finishes every shipped board (200 boards per tier, hints alone finish all; 18.2 / 39.7 / 67.4 steps per board, mostly only-digit, 4.0 / 8.1 locked a Hard / Expert board; the fallback exists for boards not made here). Steps also checked against every answer of boards with several (an independent enumerator, 60 Standard boards with givens removed) |
| Cage Sudoku | full house, cage last (a cage with one square left), hidden/naked singles, cage combination (the open squares must total what is left, which only certain digit sets make: "a 2-cage totalling 3 is 1 and 2"), the 45-rule (a row, column, box or neighbouring group holds 45 a unit, so the squares its whole cages leave over, or spill into, have a known total), linked totals (two or three of those groups sharing squares, each cutting the next one's digits, named one by one: "The cage totalling 10 leaves the shared squares only 1 or 4. Then by the 45-rule, row 3's marked squares total 12, which leaves the glowing square only 8"; up to 300 characters, as Cipher's, since the popover scrolls), then cage chain (every group worked through together; named as such, not explained group by group). Pencil marks are never read; every step ends in a placement; a mistake is a digit the stored answer lacks (a repeat in a cage, a cage over or wrong, a clash, else the answer), sound because the board has one answer | none: the generator's ladder is the teacher's (200 boards per tier and a year of daily boards, hints alone finish all; 67 / 73 / 81 steps per board, of which cage combination 7.6 / 14.3 / 13.5, 45-rule 0.1 / 3.8 / 6.7, linked totals 0 / 0.6 / 3.1 and cage chain 0 / 0.16 / 3.4 per board (before linked totals, cage chain 0 / 0.7 / 6.3: 1 Expert step in 13, now 1 in 24); the fallback exists for boards not made here). Linked totals are a slice of what `CageLogic.settle` already does (the same `Sight.allowed` cuts, in order, from the plain view), so no board moved. Measured on the chain's steps (40 Expert boards): 42% need two groups, 8% three, 3% four, the rest longer chains; the cut pairs are mostly a cage and an innie or outie overlapping it (cages never overlap each other). Steps also checked against every answer of 12 boards with several, and 61 linked-totals steps against `CageOracle` |
| Target | a step on a shortest road, found by an exhaustive search of the visible tiles: finish (two tiles make the target), else the answer nearest the target, with the steps still to go. A mistake is a step after which no sequence reaches the target (the same search; merely slower is fine); "Show me" takes it and what followed back | none exists: the teacher reads no stored answer (`par` is not read; a test changes it and the hint is identical), and hints alone finish every sampled board in exactly `par` steps (200 boards per tier, 3.55 / 4.22 / 5.00 steps, each checked against an independent breadth-first oracle). First hint on Expert, desktop JVM: median 18 ms, p95 30 ms, max 34 ms |
| Cipher | one column at a time with the carries: the leading carry (a total longer than the addends starts with 1), one column ("in the tens column, 4 + R + 1 carried ends in 2, so R is 7"), the only letter left for 0 when ten letters use every digit, two columns through the carry between them, all columns together (named as such, as Cage's chain), then a what-if on two neighbouring columns, never a trial of the whole sum. Candidates are re-derived from placed digits on every call; every step ends in a placement; the explanation shows the working (`CipherWorking`: each carry used with its reason, then the column with every other digit ruled out by name, else each other digit followed until it breaks) on 89 / 80 / 75% of steps (Standard / Hard / Expert), the rest, mostly all-columns and what-if steps, in summary; a mistake is a letter whose digit the stored answer lacks (a shared digit, a word starting with 0, a column that doesn't add up, else the answer), sound because the board has one answer | none: a sum is listed only if the ladder finishes it, and a tier is the hardest technique it needs (200 boards per tier, hints alone finish all; steps per board, Standard / Hard / Expert: leading carry 1 / 1 / 1, column 3.2 / 2.9 / 2.7, pair 2.7 / 2.1 / 1.9, all columns 0 / 1.4 / 0.3, two-column what-if 0 / 0 / 1.3). Steps also checked against every answer of 40 random sums with several |
| Word Wheel | pinned (an empty slot whose length, crossing letters and the wheel leave one grid word, found and bonus words excluded; reads the public grid list, never a slot's word; given as the word's crossword clue, never the word), crossing (of the words that fit an empty slot, only one leaves each empty slot crossing it a different word that fits and agrees at the shared square; same list, same clue), else clue (the slot with the fewest words that fit, given as its word's crossword clue; reads the stored word). Both light only the slot's squares, never the wheel's letters (the owner: the clue is the hint, the word is Show me's). No mistake exists: a wrong word is just not accepted | the clue is the fallback: 32.7 / 36.9 / 33.4% of steps (a year of daily boards per tier; 1754 / 2435 / 3200 steps; pinned 56 / 54 / 60%, crossing 11 / 9 / 7%), and 61 / 22 / 16 of 365 boards need none. It was 41.2 / 44.1 / 38.2% before crossing. An exhaustive search over every consistent filling of the empty slots would pin only 0.1-0.2 points more, and slots sharing their only words ("two slots, two words") pinned nothing more, so what is left is honestly open: several fillings fit what shows. Each crossing step is checked against that exhaustive search (every other fitting word leaves no filling). Every board ships only if no clued slot has more than 6 grid words that fit (mean worst 2.6 / 3.4 / 3.7), so a player who knows the list can always try their way to the next slot. A pinned step is checked against a pattern match over the list from boards a player made. First hint on Expert, desktop JVM: median 0.02 ms, p95 0.07 ms |
| Tally | one line at a time, mirroring the generator's ladder: the circled numbers already make the target (the rest go), a number bigger than the line still needs goes, a number bigger than the line's surplus stays, what is left makes it exactly (all stays: tidying a line already green, so it waits for the others), then the ways of one line (a number in every way stays, in none goes; up to three ways are listed: "makes 9 only as 6+3 or 6+2+1"), then a what-if (suppose a number stays or goes, follow at most three lines, and one is left with no way). A mistake is a removed number the answer keeps or a circled one it removes, sound because the board has one answer | none: the ladder finishes every shipped board (200 boards per tier and a year per tier, hints alone finish all; 13.5 / 20.8 / 31.9 steps per board, of which the ways of a line 0 / 2.6 / 8.5 and what-if 0 / 0 / 1.7 per board, every Hard board needing the first and every Expert board the second). Steps also checked against every answer the marks allow on 450 small boards, 43 of them with several answers |
| Dominoes (not yet registered) | a ladder over every candidate (tray domino, two free neighbouring squares, way round), emitted only when one domino's placement settles, so every hint is a placement: one free neighbour, one place left for a domino, one domino fits a square (rules checked with 0..6 on the region's other squares), then what a region can be made of from the values the tray can still put there, counting a value's halves left in the tray against the squares that must have it, then a what-if (a square with two or three candidates, all but one running into a contradiction within two rounds). A mistake is a domino the one answer does not have there that way round | none: the generator's acceptance test is this ladder (a year per tier, hints alone finish every board; 6.5 / 9.5 / 13.1 steps per board over 200 boards a tier, region values 1.6 / 0.6 / 0.9, counting 0 / 2.0 / 1.3, what-if 0 / 0 / 2.3 per board; every Standard board needs a region-value step, every Hard board counting, every Expert board a what-if). Steps checked against every answer still possible on small boards with several answers (`DominoTeachingTest`) |
| Mines | the generator's ladder, one fact at a time: a number with all its mines found (the rest are safe), a number with as many hidden squares as mines (all mines), one number's squares inside another's (the difference is in the other's extra squares), two that share squares (the 1-2-1), the mine count (all found, or as many mines as hidden squares), the count against the numbers (numbers on squares none share need every mine left, so the rest are safe), a what-if (suppose a square beside a number, follow single numbers at most three times, one breaks; the chain is said number by number). Flags are never knowledge; a step whose mines are all flagged counts as shown. A mistake is a flag on a safe square, sound because the board has one answer | none: the ladder clears every shipped board from its opening (200 boards per tier and a year per tier, hints alone finish all; 20.8 / 32.8 / 39.5 steps per board, of which subset 0 / 2.1 / 2.6, pair 0 / 0.8 / 1.2, the count against the numbers 0 / 0 / 0.9 and what-if 0 / 0 / 0.11 per board; every Hard board needs a subset or pair, every Expert board the count against the numbers or a what-if). Every step checked against an independent enumeration of every layout the numbers allow, from the ladder's path and from boards a player made |
| Lamps | one fact at a time, mirroring the generator's ladder: the squares beside a 0, a number with all its lamps (the rest of its squares hold none), a number with just enough room (its free squares are all lamps), a square only one lamp can light (that one is a lamp, perhaps the square itself), then a look-ahead (a lamp here would light squares beside a number and leave it too few, or light every square that could light another), then a what-if (suppose a lamp here, or none, follow at most three direct facts, and a number goes over or short or a square can never be lit). Crosses are notes and never read; a mistake is a lamp the one answer lacks (it lights another lamp, a number beside it is over, else no way to finish keeps it) | none: the ladder finishes every shipped board (200 boards per tier and a year per tier, hints alone finish all; 11.0 / 25.4 / 43.2 steps a board, of which starve 0 / 2.7 / 5.9, dark 0 / 0.4 / 0.7 and what-if 0 / 0 / 5.5 a board; every Hard board needs a look-ahead and every Expert board a what-if). Steps also checked against every answer of 274 small boards with several. Hint on Expert, desktop JVM, over whole walks: median 0.04 ms, p95 0.5 ms, max 10 ms |
| Codeword | the words that fit each run, from candidates per number derived on every call (a placed number its letter, any other every letter no number holds; a fitting word has the run's shape, equal numbers equal letters, and only letters its numbers can take): a run only one word fits ("Only EXTRA fits E 6 T 1 A, so 6 is X"), a letter every fitting word shares, a number cut by every run it is in (each run's letters listed), then every word worked through together (named as such, as Cipher's chain). No "only number for a letter": a grid uses part of the alphabet. A mistake is a number whose letter differs from the stored answer (a letter on two numbers, then a run no word fits, else the answer), sound because the answer is proved unique | none: a grid is listed only if its tier's ladder finishes it from its givens (200 boards a tier, hints alone finish all; steps per board, Standard / Hard / Expert: one word 12.2 / 14.9 / 16.4, a letter every word shares 0.6 / 0.4 / 0.4, crossing 0 / 1.5 / 0.8, every word together 0 / 0 / 1.1). Steps also checked against every answer of 60 two-word boards with many (`CodewordTeachingTest`) |
| Pearls | the pearls' and squares' own rules (a black pearl's arms, a white pearl's straight line and its turn beside it, a line with one way on, a dead end), then the small loop (joining two ends that would close a loop leaving something out), then a what-if (an edge supposed a line or a cross, followed at most six rounds; if a player's own order has left no short one, the shortest of any length). A square with two lines has its other edges read as crossed, so no hint asks for tidying. A mistake is a line off the one loop or a cross on it | none: the generator's acceptance test is this ladder (a year per tier; hints alone finish every board). Over 200 boards a tier: 20.7 / 41.1 / 69.9 steps per board; small loop 0 / 2.1 / 2.7 and what-if 0 / 0 / 3.7 per board; every Hard board needs the small loop, every Expert board a what-if. Steps checked against every loop still possible on small boards with several answers (`PearlsTeachingTest`) |
| Who's Who | a clue that names a person (tick, cross, or either-or crosses), the one item left in a cell or the one person left for an item, two things a clue ties together carried through what is known, next to / left of against the houses still possible, then a what-if (suppose a tick; at most three of those steps reach a cell or item with nothing left or a clue no placement keeps). Every step ends in ticks or crosses; a mistake is a mark the one answer contradicts | none: the generator's ladder is the teacher's (200 boards a tier, hints alone finish all; 10.7 / 22.4 / 37.2 steps a board, link 0 / 3.7 / 6.9, house 0 / 7.6 / 10.1, what-if 0 / 0 / 4.7 a board, every Expert board at least one). From boards a player marked their own way, 8 of 7,167 steps needed the teacher's longer what-if (up to 12 steps, summarised by its clues). Steps checked against every answer still possible on Hard boards with a clue dropped |
| Crossword (mini) | the words that fit each slot (the grid list's words of its length agreeing with the letters in it): a slot only one word fits ("2 Down reads A _ E. Only one word in the list fits those letters: the answer to \"Pub beer\""), of the words that fit, the one that leaves every crossing slot a word (those slots named), then every crossing worked through together (named as such). A clue is only quoted: matching it to its word would be reading the answer. A mistake is a letter off the stored answer, sound because a clue names one word. Word steps are typed by the player; only Show me types them | `clue`, by design: when no crossing settles a word, its first tap names the open slot the fewest words fit with that word's second clue ("Another way in: ..."), and only the next tap reads the answer, says so, and gives one letter (one hint counted, as for any step). If the player solves that second clue, the crossings move on by themselves 27.2 / 32.9 / 28.5% of the time (proxy, `MiniTeachingTest`: the whole word typed, then is the walk's next step logic?); the letter alone would have settled that word through the crossings 52.5 / 54.1 / 56.6% of the time; words fitting the slot it names, median 22 / 8 / 6. From an empty grid nothing else starts, so every board needs some, and their share is the tier measure (`docs/MINI_SPEC.md`). 200 boards a tier, hints alone finish all: 44 / 54 / 62% of steps; steps a board, Standard / Hard / Expert: clue letters 5.6 / 9.4 / 15.2, one word 3.6 / 3.8 / 4.9, crossing 2.0 / 2.2 / 2.1, every crossing together 1.5 / 2.0 / 2.3. Word steps checked against `MiniOracle`: no fill keeping the letters puts another word in the slot (`MiniTeachingTest`). Hint, desktop JVM: median 0.2-0.3 ms, p95 1.2 ms, max 2.9 ms |
| Sand | finish a tube, gather onto a matching colour (one that uncovers a colour with somewhere to go first), use an empty tube to free a buried colour, else plan ahead (the search's next pour, explained by the pour it makes way for). Each is offered only when the board stays sortable after it, and the first three only when they make progress (fewer places two colours meet in a tube, else fewer tubes in use, else more finished), which no pour ever undoes, so following hints cannot circle. A mistake is a board no order of pours sorts, proved exhaustively; stuck is the same with no pours left; a search out of budget is never a mistake and says so. The pour named is the one after the last board in the state's pour log that still sorts, and "Show me" goes back to that board | none exists: nothing is hidden. Hints alone sort every sampled board (200 per tier; 16.3 / 23.3 / 27.0 pours a board; finish 5.0 / 7.0 / 8.0, gather 8.0 / 11.9 / 13.9, spare 3.2 / 4.4 / 5.1, plan 0 / 0 / 0.005). **Truncated: 0 of 726 / 1,118 / 1,311 verdicts** from positions reached by random pours on daily boards (largest search 33 / 58 / 452 positions of a 200,000 budget). From 360 such positions a tier the hint called 45 / 143 / 192 unsortable, each confirmed by an independent breadth-first search, as was the claim that the named pour lost it. First hint on Expert, desktop JVM: median 0.03 ms, p95 0.2 ms, max 0.7 ms |
| Mate (chess) | mate in one (why it is mate: check, each neighbouring square own piece or covered, checker cannot be taken), forcing key (every reply allows mate, the two most forcing named in SAN), quiet key (the same for a key that is not a check). The prover is the reasoning; a mistake is a move after which `forcedMate` is `None` (a second mating line or a non-shortest one is fine) | none exists: the teacher reads no stored answer, and hints alone finish every sampled board (200 / 100 / 60) with no mistake. Cost of the first hint over all 1,500 positions per tier (median / p95 / max ms): 0 / 0 / 0, 1 / 9 / 43, 8 / 105 / 487 on a desktop JVM. After the faster `forcedMate` (make/unmake, Zobrist table), on the x86 Android emulator (KVM, 400 positions per tier, first hint, warm; median / p95 / max ms): 0.5 / 1.2 / 3.9, 2.1 / 6.3 / 18, 11 / 94 / 234 (was 1 / 4 / 32, 8 / 42 / 98, 47 / 585 / 1353); first 50 on a fresh process (cold) Expert 16 / 109 / 263, Hard 4 / 31 / 138. Web (wasm, single thread, headless Chromium): Expert 7 / 74 / 181 warm, 7 / 54 / 108 cold. Inside the 300 ms p95 / 1.5 s max budget; a real phone is likely slower |

Adopting it, or changing a teacher — the lessons so far:

- **Measure the fallback rate; don't assume it.** Test soundness against an independent solver.
- **A mistake means "no legal answer keeps this", not "differs from the stored answer".** LITS and
  Pipes had boards with several answers, and calling a correct move wrong is the PR #15 bug again.
- **Check that a generator's stored answer obeys its own rules.** Atoms' did not on 4-35% of boards
  by tier, and every mistake judged against it was suspect.
- **Fix a generator by rejecting and retrying after the existing attempts**, so the daily boards
  that were already sound keep their layout.
- **A mistake that can't be read off the board needs a history in state.** Mosaic keeps its fills
  (`MosaicState.trail`) to say which one lost the board.
- **Teachers only learn facts they can explain.** Tower drops a fact whose sentence would not fit,
  so no later step can lean on something the player was never shown.
- Keep teacher files free of `java.*`: the web compiles them automatically.
- **A new technique needs a name** in `core/TechniqueNames.kt`, in the words its hint uses: the finished frame names it
  ("You learned the 45 rule.", `docs/REWARDS.md`), and `TechniqueNamesTest` fails without one.

## Tier depth (measured 2026-10)

`TierDepthReportTest` (env-gated: `DAYBOOK_DEPTH_REPORT=1`, or `=<days>`; about a minute) generates a year of daily boards
per tier (2026-01-01 on, seeds exactly as the daily makes them), walks each with its own teacher from the blank board to
the end, applying every step, and writes `build/reports/tier-depth.txt`: the deepest technique each walk needed (in the
ladder's order; fallback steps counted apart), fallback boards and steps, median steps, and each technique's mean steps a
board. No board changed (Mambo's and LITS's rows are after their gates, below). Deepest technique, share of the year's boards (%):

| puzzle | tier | deepest technique (% of boards) | fallback boards / steps % | median steps |
|---|---|---|---|---|
| Sudoku (gated) | Standard | hidden-single-box 26, hidden-single-line 74 | 0 / 0 | 53 |
|  | Hard | naked-single 100 | 0 / 0 | 53 |
|  | Expert | locked-candidates 38, naked-pair 13, hidden-pair 5, what-if 44 | 0 / 0 | 53 |
| Kings | Standard | last-square <1, locked-to-a-line 4, would-empty 95 (gated: was 58, with confined 33, what-if 6) | 0 / 0 | 13 |
|  | Hard | confined 100, every one two colours in two lines (gated: was would-empty 52, confined 41, what-if 6) | 0 / 0 | 18 |
|  | Expert | confined 68 (all three or more colours), what-if 32 (gated: was would-empty 39, confined 55, what-if 5) | 0 / 0 | 20 |
| Mambo | Standard | quota-met 100 | 0 / 0 | 30 |
|  | Hard | quota-almost-met 100 | 0 / 0 | 56 |
|  | Expert | what-if 100 | 0 / 0 | 87 |
| Shikaku | Standard | only-fits 41, only-reaches 59 (was only-fits 49, only-reaches 42, common-cells 5, what-if 4) | 0 / 0 (was 1.4 / 0.15) | 12 |
|  | Hard | only-reaches 74, common-cells 14, what-if 12 (was only-fits 21, only-reaches 64, common-cells 7, what-if 7) | 0 / 0 (was 4.1 / 0.26) | 17 |
|  | Expert | common-cells 47, what-if 53 (was only-fits 13, only-reaches 62, common-cells 15, what-if 11) | 0 / 0 (was 3.6 / 0.16) | 21 |
| Atoms (gated) | Standard | one-neighbour 4, all-forced 80, at-least-one 16 | 0 / 0 | 7 |
|  | Hard | crossing 58, isolation 42 | 0 / 0 | 15 |
|  | Expert | isolation 76, what-if 24 | 0 / 0 | 24 |
| Inequality (gated) | Standard | only-digit 1, sign-bound 43, sign-place 57 | 0 / 0 | 16 |
|  | Hard | naked-pair 100 | 0 / 0 | 25 |
|  | Expert | hidden-pair 100 | 0 / 0 | 35 |
| Tents | Standard | line-full 89, one-spot 10, shared 1, served 1 | 0 / 0 | 10 |
|  | Hard | one-spot 78, shared 8, served 14 (gated: was line-full 64, one-spot 28, shared 4, served 4) | 0 / 0 | 15 |
|  | Expert | shared 33, served 67 (gated: was line-full 39, one-spot 43, shared 7, served 12) | 0 / 0 | 21 |
| Nonogram | Standard | line-logic 100; line passes 2: 72, 3: 27, 4+: 2 | 0 / 0 | 13 |
|  | Hard | line-logic 100; passes 3: 82, 4+: 18 | 0 / 0 | 38 |
|  | Expert | line-logic 100; passes 4: 80, 5+: 20 | 0 / 0 | 80 |
| LITS (gated) | Standard | avoid-2x2 2, letter-clash 97, neighbour <1 | 0 / 0 | 15 |
|  | Hard | avoid-2x2 1, letter-clash 63, neighbour 35, what-if 2 | 0 / 0 | 21 |
|  | Expert | letter-clash <1, neighbour 64, what-if 35 | 0 / 0 | 30 |
| Regions (gated) | Standard | only-digit 49, only-place 51 | 0 / 0 | 18 |
|  | Hard | locked-candidate 100 (at least 2 a board; 4.0 mean) | 0 / 0 | 40 |
|  | Expert | locked-candidate 100 (at least 6 a board; 8.2 mean) | 0 / 0 | 67 |

Nonogram's ladder says nothing (any line with a mark in it is `line-logic`), so its depth is **line passes**: from a blank
board, settle every row with what the board then holds, then every column, and count the passes until nothing changes
(one pass is each line's overlap alone; more means the lines must keep handing each other squares).

Verdicts (a gate is Tally's `Spec.needs`: a tier's draw is redrawn unless the walk needs its technique; only Mambo's is built):

- **Sudoku: gated (2026-10).** Before, Hard and Expert overlapped and Expert fell back: Standard hidden-single-box 93,
  hidden-single-line 6; Hard hidden singles 77, naked-single 12, locked or deeper 12, fallback 6.6% of boards; Expert hidden
  singles 34, naked-single 24, locked / pairs / what-if 44, fallback 32.3% (1.02% of steps); median steps 43 / 51 / 57. Now
  `Sudoku.generateVerified` digs symmetric pairs only while the tier's ladder finishes the board (singles in a unit / up to
  naked single / everything but the fallback) and redraws a board whose walk did not need the tier's technique (Hard a naked
  single, Expert locked candidates or deeper); the table above is the result, no tier overlaps another and nothing falls back.
- **Kings: gated (built 2026-10).** Before, would-empty / confined / what-if were 58/33/6, 52/41/6, 39/55/5% across the
  tiers and only size differed. Now (`Kings.fitsTier`, on `KingsTeacher.depth`, the hint walk from blank): Standard needs
  nothing past would-empty; Hard needs a confinement of two colours and nothing wider or a what-if; Expert needs a
  confinement of three or more or a what-if. Expert on what-if alone was costed and dropped: 2% of 9x9 draws pass (a
  9x9 carve fails about two times in three), a mean of 51 draws a board, 91 / 341 / 733 ms p50 / p95 / max on the JVM.
  With the wide confinement too, 7% pass: draws a board mean 2.4 / 5.7 / 14.6, most 13 / 41 / 94 of 400 (`KingsRulesTest`). Expert in the browser, p50 / p95 / max: 28 / 102 / 209 ms in Chromium, 39 / 142 / 290 in WebKit.
- **Mambo: gated (built).** It was one depth everywhere (quota-met 100 on every tier: the carver kept only boards the first
  four finish). The carver now takes clues back while the ladder up to the tier's rung still finishes the board, and the
  tier is redrawn unless the walk needs that rung: Standard the first four (its boards did not move), Hard almost-met and no
  what-if, Expert what-if. Draws a board over the year: 1.00 / 1.42 / 1.01 mean, 1 / 6 / 2 most. The rows above are
  after the gate.
- **Shikaku: gated (2026-10-25).** Was overlapping and fell back. Tilings are now grown (about half non-guillotine) and
  `Shikaku.accepts` gates every draw: Standard nothing past only-reaches, Hard something past only-fits, Expert common-cells
  or what-if, never the fallback; Expert clues avoid their rectangle's corners (common-cells or what-if: 22% to 27% of proved
  draws). Rows above are after the gate. Draws a board over a year: mean 8 / 11 / 33, p95 25 / 32 / 100, max 47 / 77 / 231 of
  2000; JVM median 0.4 / 0.5 / 1.7 ms a board, Expert p95 6 ms, max 16 ms; Expert in Chromium 4 / 14 / 32 ms (median / p95
  / max), WebKit 5 / 17 / 38 ms.
- **Atoms: gated (2026-10).** Before, graded loosely: 16% of Expert boards needed nothing past all-forced and 14% of Standard
  boards isolation or what-if. Now each new atom sprouts 2-6 squares away (2-4 before), Hard has 18 atoms (16) and Expert 28
  (24), and the gate is Standard at most at-least-one, Hard crossing or isolation, Expert isolation or deeper, every walk
  finished. Ungated, reach alone barely moved the depth (mean bond 2.7 to 3.1 squares; Expert crossing line pairs 4.0 to 4.9);
  the extra atoms did (Expert 8.0 crossing pairs, crossing steps 0.9 to 1.9 a board). Gated, a year of daily seeds: steps a
  board crossing 0 / 1.1 / 2.1, isolation 0 / 0.5 / 1.4, what-if 0 / 0 / 0.4; draws 1.9 / 5.5 / 10.9 mean, 10 / 39 / 71 most
  of 2000; JVM generation 0.4 / 1.4 / 8.7 ms median, 2.3 / 8.8 / 37 ms p95, 39 / 26 / 89 ms worst (a loaded desktop).
  Only-way-out stays shadowed (2 steps in 500 Expert boards).
- **Inequality: gated (2026-10).** Before, 91-96% of Hard and Expert boards topped out at sign-place (Standard only-digit 1,
  sign-bound 43, sign-place 57; Hard sign-bound 9, sign-place 91; Expert sign-bound 4, sign-place 96; median steps 16 / 24 / 33).
  The ladder gained a naked pair and a hidden pair in a line (each a cut, then a single, counting the signs again if needed); a
  sign chain needed nothing new, since the sign cut already runs to a fixpoint. With the full ladder and no gate, 12% of 5x5 and
  27% of 6x6 draws need a pair (8% a hidden one). Now Standard is untouched, Hard (ladder to naked pair) must need a naked pair,
  Expert (the whole ladder) must need a hidden pair: 1.3 naked pair steps a Hard board, 0.5 naked and 1.5 hidden on Expert.
- **Tents: gated (built 2026-10).** `TentsLogic.fitsTier`, on `TentsTeacher.depth` (the hint walk from blank): Hard needs
  one-spot or deeper, Expert needs shared or served; Standard is not gated. Draws a board over a year (a draw must also be
  finished by the ladder and unique): mean 1.3 / 5.6 / 23, most 5 / 27 / 143 of 2000; JVM p50 / p95 / max 0.1 / 0.1 / 0.4,
  2.6 / 9.3 / 41, 5.1 / 27 / 191 ms. Expert in the browser, p50 / p95 / max: 14 / 66 / 151 ms in Chromium, 17 / 75 / 125 in WebKit. `touch` is never needed.
- **Nonogram: gated** (2026-10). Ungated, Hard and Expert overlapped in passes (3+: 54% / 75%) and 13% of Standard boards
  took one pass, so a board must need 2 / 3 / 4 passes by tier (`Spec.minPasses`); the rows above are with the gate. The gate
  came in with mirrored pictures (shallower still: one pass on half of Standard); those were dropped on 2026-10-26 because half
  the picture gave the rest away, and the gate stayed.
  Deeper than that needs a technique line logic lacks (a what-if across two lines).
- **LITS: gated (2026-10).** Before, the tiers were the same depth: Standard avoid-2x2 1, letter-clash 48, neighbour 47,
  what-if 4; Hard letter-clash 62, neighbour 35, what-if 2; Expert letter-clash 35, neighbour 60, what-if 5. Now each proved
  board is walked by `LitsTeacher.depth` (the teacher's ladder from blank, no answer) and `Lits.score` keeps it or draws
  again: Standard must not need neighbour or what-if; Hard keeps its first proved board; Expert takes
  the first board needing what-if among its first eight proved draws, else the first needing neighbour. A strict Expert
  gate cost 20 draws a board (each a carve, ~15 ms on the JVM): median 240 ms, p95 1.3 s on the JVM, so it is capped;
  sixteen draws would give what-if 37% at 177 ms median, eight give 35% at about 105-150 ms. Draws over the year: 2.1 /
  1.0 / 6.4 mean, 14 / 1 / 8 most. The search budget was tripled (a board inside the old one did not move: 3 Hard days of
  the year had run out of it under the cap and gone uncapped, and now stay under it), and a board never leaves the
  seven-square cap while a proved one exists under it. One Standard and one Expert day ran out of budget and ship the
  nearest proved board (neighbour, letter-clash). Generation, a year a tier, median / p95 / max: JVM 19 / 79 / 228,
  11 / 44 / 114, 137 / 262 / 341 ms (a loaded desktop); Chromium 27 / 117 / 414, 20 / 69 / 151, 228 / 434 / 562 ms; WebKit
  33 / 135 / 569, 26 / 76 / 180, 309 / 542 / 782 ms. 9x9 for Expert was not tried: the generator's squares are bits of a
  `Long` (64 squares), so it would be a rewrite, and it would cost more per draw, not less.
- **Regions: gated (2026-10).** Before, 97% of Expert and 81% of Hard needed only-place (Hard only-digit 19, only-place 81;
  Expert only-digit 3, only-place 97; median steps 18 / 38 / 65). The ladder gained the locked candidate (one elimination, then
  a placement). With it and no gate, locked steps a board were 0 on 25% of 5x5 draws, and at least 2 on 81% of 7x7 and 6 on
  58% of 9x9 draws. Now Standard is untouched, Hard must take at least two locked steps and Expert at least six; givens fell
  from 21.6 / 19.1% to 19.1 / 16.6% of squares on Hard / Expert.

**Snap feasibility.** A test-code prototype (`SnapDeducerReportTest`, since deleted; in git history) tried a whole-board
deducer: every joint between neighbours in, out or unknown; *degree* (two joints a square, one at the 1 and
at the highest number: a square with two usable joints uses both), *pieces* (no joint closing a loop, joining the two
ends' pieces early, or putting the numbers along a piece out of order: a piece holds consecutive numbers in order) and
*pockets* (the usable joints stay connected, a bridge must be used, a square that cuts the board in two uses one joint into
each side and each side holds one end); then separately a *what-if* one deep (suppose a joint, run the rules, a
contradiction settles it). A year of daily boards per tier, walking the answer from the 1 as a player draws it:

| | Standard 5x5 | Hard 6x6 | Expert 6x7 |
|---|---|---|---|
| rules: boards finished from blank | 55% | 35% | 30% |
| rules: line settled from blank | 73% | 57% | 52% |
| rules: walk steps whose next square is settled | 96.9% | 95.0% | 93.1% |
| + what-if: boards finished from blank | 100% | 98.9% | 98.6% |
| + what-if: walk steps settled | 100% | 99.97% | 99.97% |
| what-if chain before the contradiction (joints, median / p90 / max) | 13 / 22 / 32 | 13 / 30 / 48 | 14 / 32 / 63 |

The pocket rule never set a joint (degree and pieces had always got there first) and removing it changed nothing.
**Recommendation: not worth a teacher yet.** The rules explain more than the end-of-line teacher did (they leave 3 / 5 /
7% of steps against its 6 / 13 / 16%) but still leave the choice unexplained on 45 / 66 / 70% of boards, and those
choices are the puzzle. What settles them is a trial whose contradiction arrives after a median 13 forced joints: true,
but not a sentence a popover can show, and "suppose this, and 13 squares later it breaks" is the guess the sparse
numbering exists to leave the player. Revisit only with a named technique that turns most of those chains into one or two
joints (parity of the squares between two numbers is the likeliest candidate, not measured here).
