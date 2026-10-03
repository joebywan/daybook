# Teaching: hints, walkthroughs and the play screen

Moved out of CLAUDE.md. The *what to build* for a teacher is `PUZZLE_STANDARDS.md` section 7; this file holds the settled play-screen UX and the measured fallback table.


All puzzles teach. The shared pieces are `core/Teaching.kt` (the contract), `ui/teach/Hints.kt`
(the session, the panel, `WatchHint`), `ui/tutorial/TutorialRunner.kt` and `ui/play/PlayScreen.kt`;
each puzzle has a `<Name>Teacher.kt` except Snap, whose teaching is its walkthrough
(`offersHints = false`). Everything in the contract has a default, so a board adopts it alone:

- `teach(state): Deduction?` — a mistake to take back, or one step reasoned from what the player can
  see: nudge + focus cells, explanation + cited cells, targets, and `apply`/`isReached` closures.
  Null means "not adopted", and `PlayScreen` falls back to `hint()`.
- `LocalBoardHighlight` — a CompositionLocal, **not** a `Board` parameter, so boards that ignore it
  compile untouched. Strong cells get the outline; everything else dims.
- `tutorial: List<TutorialFrame>` — played by `TutorialRunner` on the puzzle's real `Board`. A frame's
  `accepts` gates the move; a refused state is dropped, so **a walkthrough move must be a single
  emitted state**. A gesture that emits several (Sets' three taps, Snap's drag a square at a time) is
  written as one frame per emission, each frame's `state` the previous one's result.
  The "your turn" frame shows Hint only when the puzzle offers hints.
- UX, settled by the owner: tap 1 nudges, tap 2 explains, the **player makes the move**, and the
  panel confirms and clears when `isReached` sees it. Only an explicit "Show me" applies it. **One
  hint is counted per deduction opened**; explaining and Show me are free.
- The panel is a **popover over the play screen** (`HintPopover`), not a slot in its column, so the
  board's box is the whole of the space between header and toolbar and never changes size: not when
  a hint opens, not for the first-visit offer, not on solve. (A reserved slot did the opposite: a
  blank ~156dp band all game on every teaching puzzle, and a visible jump larger on completion.)
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
  Done; a daily offers Easier Daily/Random/Harder Daily/Done, skipping tiers already done *that date*
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
| Sudoku | full house, hidden/naked singles, locked candidates, pairs, a bounded what-if chain; every step ends in a placement and never reads the player's pencil marks | 0.5 / 6 / 34% (1% of Expert steps: Expert is dug for uniqueness, not rated) |
| Kings | last square, locked to a line, would empty, N confined, what-if | 0.5 / 0 / 0.5% |
| Mambo | pair, sandwich, link, quota, almost, what-if | 0 (boards are carved so the first four finish them) |
| Pipes | border, set neighbour / whichever way it turns, no loop | 0 |
| Shikaku | only fits, only reaches, common cells, what-if | 0.5 / 2.5 / 3.5% |
| Mosaic | finish, count fills against colours, biggest swallow, centre; fallback offers a proven fill | 1.5 / 9 / 12.5% |
| Sets | two cards fix the third, trait by trait; continue a pick, fresh, reuse a tinted card | none exists |
| Atoms | one neighbour, all forced, at least one, crossing, isolation, only way out, what-if | 0 / 0 / 0.5% |
| Snap | walkthrough only: corners, dead ends, cutting back, rubbing out | no hints |
| LITS | whole region, overlap, avoid 2x2 / letter clash, neighbour, what-if | 0 / 0 / 0% (300 boards per tier). It was 42 / 23 / 30% while 43 / 22 / 29% of boards had several answers under the win check; the generator now proves one (the test holds one-answer boards under 2%) |
| Tower | one change, only colour left, accounted for, what-if; else a guess that fits every score | every board, 28-35% of turns: Mastermind is mostly choosing a guess |
| Lexicon | a typed row that cannot be submitted, a letter pinned to its slot by the marks, one word left, else the word that leaves the fewest | every board; 93% of turns after the opener (pins are the other 7%): choosing a word is most of the game. A pin holds for every accepted word that fits, not only the answer list |
| Inequality | last square, only digit, only place, then the same two once the signs are counted (candidates cut by `a < b` bounds, to a fixpoint) | none: the generator keeps a board only while these finish it (200 boards per tier, hints alone finish all; the fallback exists for boards not made here) |
| Nonogram | one line at a time: clues fill the line, clue already complete, overlap of the clue's slides, what the marks already in the line leave possible | none exists: every board is line-solvable, so some line always has a square to settle (200 boards per tier, hints alone finish all) |
| Mate (chess) | mate in one (why it is mate: check, each neighbouring square own piece or covered, checker cannot be taken), forcing key (every reply allows mate, the two most forcing named in SAN), quiet key (the same for a key that is not a check). The prover is the reasoning; a mistake is a move after which `forcedMate` is `None` (a second mating line or a non-shortest one is fine) | none exists: the teacher reads no stored answer, and hints alone finish every sampled board (200 / 100 / 60) with no mistake. Cost of the first hint over all 1,500 positions per tier (median / p95 / max ms): 0 / 0 / 0, 1 / 9 / 43, 8 / 105 / 487 |

Adopting it, or changing a teacher — the lessons so far:

- **Measure the fallback rate; don't assume it.** Test soundness against an independent solver.
- **A mistake means "no legal answer keeps this", not "differs from the stored answer".** LITS and
  Pipes have boards with several answers, and calling a correct move wrong is the PR #15 bug again.
- **Check that a generator's stored answer obeys its own rules.** Atoms' did not on 4-35% of boards
  by tier, and every mistake judged against it was suspect.
- **Fix a generator by rejecting and retrying after the existing attempts**, so the daily boards
  that were already sound keep their layout.
- **A mistake that can't be read off the board needs a history in state.** Mosaic keeps its fills
  (`MosaicState.trail`) to say which one lost the board.
- **Teachers only learn facts they can explain.** Tower drops a fact whose sentence would not fit,
  so no later step can lean on something the player was never shown.
- Keep teacher files free of `java.*`: the web compiles them automatically.
