# Puzzle standards

What every Daybook puzzle must have, derived from the eleven that exist. Written against `main` at
`b09c763` (2026-10-01). Where this file and the code disagree, the code wins; fix this file.

Paths are relative to `app/src/main/java/com/joebywan/daybook/` (shortened to `core/`, `puzzles/`,
`ui/`) unless they start with `app/src/test/` (shortened to `test/`, the package directory
`com/joebywan/daybook/`). `CLAUDE.md` holds the *why* behind most rules here; this file is the *what to
build*.

## 1. How to use this

- **Adding a puzzle:** read section 2 and the checklist, then follow the recipe in section 13.
- **Revising a puzzle:** find its row in the conformance matrix (section 12), and check the change
  against the checklist.
- **Copy, don't invent.** Each concern below names one reference implementation. If you are about to
  write something with no precedent here, find out why nobody has before you do.

### Definition of done

A puzzle is done when all of these are true:

1. `PuzzleType` object in `puzzles/<Name>.kt`, `@Serializable` state in the same package, one line in `PuzzleRegistry.all`.
2. Three tiers (Standard / Hard / Expert) that differ in size or clue density, never in spare slack.
3. `generate(seed, difficulty)` is pure, deterministic, and ships only boards it has proved (one answer, or the
   deliberate several-answer policy of section 4). Any fallback is named, reachable on a measured fraction of
   seeds, and pinned by a test.
4. `solved` checks the rules, not a stored answer (or is provably equivalent because uniqueness is proved).
5. No `java.*` / `android.*`, and no hash-iteration order reaching the `Rng` (section 5); a parity pin added.
6. The board takes one state per gesture, sizes from both axes, keeps transient UI out of the state, and
   does not move when feedback appears.
7. Hints: a `<Name>Teacher.kt` whose `deduce` sees only visible state, with a measured fallback rate
   (or `offersHints = false` with the measurements written in the doc comment).
8. A walkthrough: `tutorial` frames on a hand-built tiny board, each waiting for the real gesture, ending in
   a free-play frame the hints can finish.
9. A fixed-motif `Preview`; the board reports its highlight geometry (`core/HighlightBounds.kt`).
10. Tests per section 10; the rendered result checked at true size on dark and light (section 6).

## 2. The contract: `core/PuzzleType.kt`

| Member | Required? | Notes |
|---|---|---|
| `id` | yes | Saved games, completions, seeds and `?puzzle=` key off it. Never rename. It is hashed into the seed (`SeedHash.stableHash`), so renaming also changes every board. |
| `displayName`, `tagline` | yes | Tagline is one line on the home card. |
| `rules: List<String>` | yes | Short summary. Shown as "Rules" inside the walkthrough, and directly by "How to play" if there is no walkthrough. |
| `accent: Long` (ARGB) | yes | Card colour, board highlights, walkthrough buttons. |
| `generate(seed, difficulty)` | yes | Pure. Section 4. |
| `Preview(modifier)` | effectively yes | Default is a plain accent block. All eleven override it. Fixed motif; never calls `generate`. |
| `Board(state, onState, interactive)` | yes | Section 6. |
| `teach(state): Deduction?` | yes unless `offersHints = false` | Section 7. |
| `hint(state)` | no | Legacy "move goes straight on the board", used only when `teach` returns null. Six boards still define it (`Lits`, `Mambo`, `Mosaic`, `Sets`, `Shikaku`, `Sudoku`) as a fallback; a new puzzle should not write one. |
| `tutorial: List<TutorialFrame>` | yes | Section 8. |
| `offersHints` | default true | `false` only for Snap (section 7). Hides the Hint button in `PlayScreen` and the walkthrough's free-play frame. |

`PuzzleState` (`puzzles/PuzzleState.kt`): `solved` (computed), `moves`, optional `failed`
(computed; only `MosaicState` and `TowerState` override it, and only their own boards draw the lost
state: `PlayScreen` never reads it).

Registry line: `core/PuzzleRegistry.kt` `all` is the home-grid order. `featured(epochDay)` rotates over it.
The home grid is three columns by four rows (`ui/home/HomeScreen.kt` `COLUMNS`, with a comment that eleven
tiles is what one phone screenful holds); a twelfth fits, a thirteenth means checking that screen.

## 3. Difficulty

`core/Difficulty.kt`: `STANDARD`, `HARD`, `EXPERT`. The tier is chosen once on the home grid (persisted
by `ui/home/LaunchOptions.kt`) and applies to all puzzles; `Difficulty.fromKey` falls back to Standard.
Each puzzle defines its own mapping in one private function. Copy the shape from `Kings.sizeFor`
(`puzzles/Kings.kt`) for one-knob puzzles or `Mosaic.specFor` for several knobs.

What varies, as built:

| Puzzle | Knob | Standard / Hard / Expert |
|---|---|---|
| Sudoku | clue target (grid fixed 9x9) | 38 / 30 / 24 (`clueTarget`) |
| Kings | grid | 7 / 8 / 9 |
| Mambo | grid | 6 / 8 / 10 |
| Pipes | grid | 5x7 / 6x9 / 8x11 |
| Shikaku | grid, max rectangle area | 6x7 (6) / 8x9 (8) / 9x11 (10) |
| Mosaic | grid, blobs, colours, optimum | 8x12, 3 colours / 9x14, 4 / 10x16, 5; slack 0 |
| Sets | cards on table, sets to find | 9 / 3, 12 / 4, 12 / 6 |
| Atoms | lattice, atoms | 7 (10) / 9 (16) / 11 (24) |
| Snap | grid (clue density is a budget, not a tier knob) | 5x5 / 6x6 / 6x7 |
| LITS | grid | 6x6 / 7x7 / 8x8 |
| Tower | slots, colours, guesses | 4/5/10, 5/6/12, 5/8/14 |

Rules for the tiers:

- **Three tiers, ascending.** Tower once shipped 10 / 10 / 9 guesses for 1,296 / 16,807 / 32,768 codes, a
  curve that ran backwards. `test/TowerBalanceTest` plays each tier with a consistent guesser to stop it
  recurring; `test/SetsRulesTest` `the tiers get harder in the order they are offered` does the same for Sets.
  A new puzzle needs a test that can fail if a harder tier becomes easier.
- **Vary size, clue density or technique depth. Never vary slack.** Mosaic's move limit is the proven
  optimum on every tier (`Spec.slack` is 0; `test/MosaicOptimumTest` `the limit is exactly the optimum on every
  tier`). CLAUDE.md "Settled".
- **Do not soften a tier to be kind.** Snap's clue counts are minimal at every tier and Standard being hard is
  the owner's stated intent ("I should have to make choices and think"). Sparser beats friendlier. Do not add
  clues back and do not relax uniqueness to open the board up. Memory: puzzles should demand thought.
- **Measure what a tier gives away.** Snap once numbered 62% of every board (`test/FallbackTest` `snap never
  falls back to numbering every square` pins at most a third). A board with one answer can still have nothing
  left to work out.
- **The tier is part of the seed**, as `(difficulty.ordinal + 1)` mixed in `core/SeedHash.daily`. Reordering
  `Difficulty` or its entries changes every board of every puzzle.
- **Cost budget.** `test/GeneratorTest` `generation stays inside a sane time budget` fails a tier whose
  generation averages over 2.5 s on the JVM. The web build generates on one thread behind a "Setting out"
  screen and pre-generates today's boards (CLAUDE.md "Generation on one thread"), so a slow Expert is felt.

## 4. Generation

**Seeding.** `core/DailySeed.seedFor(date, puzzleId, difficulty)` -> `core/SeedHash.daily` for the daily
board; `randomSeed` for random boards. Use only `core/Rng` (splitmix64, hand-rolled so it cannot drift): never
`kotlin.random`, `java.util.Random`, `Math.random`, clocks or counters. Retry with `Rng(seed + attempt)` so a
board that needs three attempts still depends only on its seed (`Kings.generateVerified`).

**Purity and determinism.** `test/GeneratorTest` checks every registered puzzle on every tier: starts unsolved,
same seed gives an equal state, inside the time budget. It iterates the registry, so a new puzzle is covered
without editing it.

**Prove, then ship.** CLAUDE.md "Never ship a board the generator has not proved". The standard, by case:

- *Unique-answer puzzles* (Sudoku, Kings, Shikaku, Atoms, Snap, LITS, Mambo): the generator must prove one
  answer by an exhaustive search. Reference: `Kings.generateVerified` (loop of `ATTEMPTS`, null if none
  proved) with `lastResort` as the only unproved path.
- *Make the distinction structural.* A bounded search must return a type where only the proved case carries a
  board, so a caller cannot read "gave up" as "exactly one". Reference implementation: `Lits.Verdict`
  (`puzzles/Lits.kt`: `None`, `ExactlyOne(shading)`, `Ambiguous`, `Truncated`; only `ExactlyOne` may ship).
  Snap has the same idea as a private enum (`Snap.Verdict`: `UNIQUE` is the only proof). Mosaic's `solve`
  returns `null` when the budget runs out, and `test/MosaicOptimumTest` `a search that runs out of budget reports
  nothing at all` pins that.
- *Expose the split.* `generateVerified(seed, difficulty): State?` (null when nothing was proved) is what tests
  assert on. It exists on `Kings` and `Atoms` (public) and `Lits` (internal). `generate` is
  `generateVerified(...) ?: lastResort(...)`.
- *Fallbacks.* Keep it named, tiny and honest (`Atoms.lastResort` is a three-atom chain; Snap's is a numbered
  boustrophedon). Before adding one, ask what fraction of seeds reach it and measure it; size the test sample
  to the rate, not to a convenient number. Reference test: `test/FallbackTest`
  `atoms proves every daily board of a year on every tier` (365 days x 3 tiers, asserting on
  `generateVerified` so a property the fallback also satisfies cannot let it pass). A fallback test must be one
  that can fail: it twice passed on plainly broken boards (CLAUDE.md "Tests").
- *When a fix changes generation,* reject-and-retry **after** the existing attempts so boards that were already
  sound keep their layout, and prove it with the parity dump before and after (section 5).
- *Several answers are allowed only by policy.* Pipes never claims uniqueness (a random spanning tree,
  scrambled), and LITS's real rules admit boards a connectivity-based search cannot see, so its final
  candidate is re-proved with no connectivity rule (CLAUDE.md "Settled", `test/LitsUniquenessTest` via
  `test/LitsOracle`). Where several answers are possible, the teacher and `solved` must be written for
  that (below).
- *No unique answer to prove:* Tower (a random code), Sets (target = the number of sets on the table;
  `Sets.generateVerified` redraws up to `DRAWS` and returns null if none lands, then `Sets.lastResort`, an
  exhaustive search for a board of the tier's own shape, ships instead of throwing; a year per tier
  reaches neither, pinned by `test/SetsRulesTest`).

**`solved` checks the rules, not a stored answer.** Kings and LITS both rejected correct solutions by comparing
to the stored one. Keep the stored solution for the teacher; let a validator decide. References:
`KingsState.solved` -> `Kings.isSolved`; `Mambo.isSolved`; `AtomsState.solved`; `Snap.obeysRules`;
`PipesState.solved`; `Sudoku.isSolved`; `Shikaku.isSolved`. Tests for the principle: `test/KingsRulesTest` `a legal placement wins, whether or not it
is the stored one`, `test/MamboSolvedTest`, `test/LitsAuditTest` `validator agrees with an independent checker`.
`test/SolvedRulesTest` covers Sudoku and Shikaku: a legal fill that is not the stored one wins, near misses
do not, and both are cross-checked against an independent checker.

**An independent checker, in tests.** New tests are written on a different principle from the code they
check, so the two cannot share a blind spot. Exemplars: `test/SnapCluesTest` (naive DFS, no budget, order
checked at the leaf: the opposite of the generator on every point), `test/KingsRulesTest` (brute force over
columns), `test/ShikakuClueTest` (its own `countTilings`), `test/LitsOracle`, `test/MosaicOptimumTest`
(breadth-first on small boards), `test/MamboRulesTest` (propagation re-written), `test/MamboSolvedTest`.

**The generator must not give away the board** (Snap's "forced minimally" lesson, CLAUDE.md): after forcing
uniqueness, take back every clue the rest already implies, and give the result a budget the test enforces.
Mambo does the same by carving until `solvableByLogic` stops holding, so every board is solvable by the rules
alone (`test/MamboRulesTest` `every generated board is solvable by propagation alone`).

## 5. Web parity

The web build compiles `app/src/main/java` itself (CLAUDE.md "One copy of the code"). A new file is on the web
by default, so:

- **No `android.*`, no `java.*`, no `System.*`, no `String.format`, no `java.time`.** Use `kotlinx.datetime`,
  `kotlin.time.TimeSource`, `core/Rng`, `core/SeedHash`. (`Integer.bitCount`, `sortedSetOf`, `toSortedSet` also
  fail the wasm compile.) Currently no non-platform file imports `java.*`.
- **Hash iteration order must never reach the `Rng` or a "first"/"min" pick.** The JVM walks small ints
  ascending; Wasm walks insertion order. The audit: `git grep -n "HashSet\|HashMap\|toSet()\|groupBy\|distinct"
  app/src/main/java/com/joebywan/daybook/puzzles/` and follow each hit. Sort first (Kings did), or use a
  list. Probe-only containers (Atoms' `occupied`, `Lits`' probe sets, `MosaicTeacher`'s memo) are fine; say so
  in a comment. Teachers carry the same rule: every loop walks cells/units in index order (stated in each
  `*Teacher.kt` header).
- **Order already baked into shipped boards:** replay, don't re-pick. `core/JvmHashOrder.kt`
  (`jvmHashSetOrder`) replays `java.util.HashMap`; `test/JvmHashOrderTest` diffs it against the real
  `HashSet`. LITS is the only user (`puzzles/Lits.kt`). Do not add a second.
- **Speed changes keep every `Rng` draw and node count.** Mosaic, Atoms and LITS were sped up without
  moving a board; the proof is the year dump before and after (below).
- **Touch on the web** differs (TouchEvent not PointerEvent; slop wider than 12px). Gesture code that must
  start *on* something small uses the `onDragStart` overload that receives the `down` change (Atoms). See
  CLAUDE.md "Learned the hard way".

**What a new puzzle must add**

1. A `when` branch in `core/ParityFingerprint.body` for its state class. The `when` is over a sealed type,
   so the build fails until you do. The body must pin the board *and* its stored answer in a format that is
   identical on both platforms.
2. A pin test: three dates x three tiers printed and asserted, in the style of
   `test/WebParityShikakuSnapSudokuTest` (`PARITY_DATES` 2026-01-01, 2026-09-30, 2027-02-28; `FINGERPRINTS`).
   Write the test with an empty list, run it, copy the printed lines in, and review them. The test files are
   grouped (`WebParityTest` for Kings, `LitsWebParityTest`, `MosaicAtomsWebParityTest`,
   `WebParityShikakuSnapSudokuTest`, `WebParityMamboPipesSetsTowerTest`); a new puzzle may add its own.
3. Nothing for the dump itself: `test/WebParityDumpTest` iterates the registry (and asserts it names every
   puzzle). Run `DAYBOOK_PARITY_DUMP=<file> ./gradlew :app:testDebugUnitTest --tests '*WebParityDumpTest*'`
   and compare with the page's `?dump&range=365` output (strip the `RANGE ` prefix); the files must be
   identical. Take the same dump before and after any generator change to prove Android boards unchanged.

## 6. The board UI

Reference boards: **Kings** (`Kings.Board`, gestures, marks, highlight), **Mosaic** (`Mosaic.Board`, sizing
and palette), **Snap** (continuous drag), **Sudoku** / **Tower** (controls beside the grid).

- **One emitted state per gesture.** `ui/play/PlayScreen.kt` `push` adds an undo entry for every state the
  board hands it. A drag or a double-tap accumulates in `remember` and emits one combined state on lift
  (`Kings.paint`, `toggleKing`; `test/KingsMarkingTest` pins "one gesture, one undo"). The one exception is
  a drag that must show progress per square: Snap emits per square and must read the board through
  `rememberUpdatedState` (`Snap.Board`: `val latest by rememberUpdatedState(s)`), never key `pointerInput` on
  the state. The walkthrough runner drops intermediate states unless a frame sets `passes` (section 8).
- **Transient UI state is not `PuzzleState`.** Selected colour, palette choice, drag in progress, settle
  timers live in `remember`/`rememberSaveable`. (Sudoku's *selected cell* is in `SudokuState.select` and is
  therefore an undo entry; the pattern to follow is Mosaic's palette in `remember` or Tower's
  `selectedColour`, and Sudoku's own notes mode, a `rememberSaveable` in `Board`: the notes themselves are
  state, the mode that writes them is not.) Animations run in a `rememberCoroutineScope`, not a `LaunchedEffect(state)` that every
  tap cancels (Pipes' spin bug, CLAUDE.md).
- **Undo, restart and save.** `PlayScreen` owns undo (history in the saved game, bounded to
  `SavedGame.UNDO_DEPTH` = 24), restart (back to `initial`, clears the hint session), the clock and the results
  card. A board never implements these. Undo and Restart also clear any open hint.
- **Size from both axes.** Use `BoxWithConstraints` and `minOf(maxWidth / w, maxHeight / h)`. Reference:
  `Mosaic.Board`. Sets' `Sets.cardWidth(available, availableHeight, cards)` is tested in
  `test/SetsStripTest`. Tower is a different shape (a bottom-anchored `LazyColumn` with
  `weight(1f, fill = false)` above fixed-height controls) and uses no `maxHeight`; it fits the box by being
  bounded rather than by arithmetic. The board's box is the whole space between header and toolbar and must be
  the same size before and after solving and while a hint opens (CLAUDE.md audits at 390x844, 390x664,
  360x640).
- **Feedback must not move the board.** Reserve the space (`minLines == maxLines`) or float over the content.
  Reference: `Mambo.CAPTION_LINES` with `test/MamboCaptionTest` (every message must fit the reserved lines;
  overflow ellipsises silently). Rule violations are debounced ~1 s so passing through an illegal
  intermediate state is not shouted at.
- **Derive, don't store.** Anything computable from the board is recomputed per render: Kings'
  `eliminated()`, LITS' `impossible()`, Pipes' wet tiles. Store only the player's marks. A mistake that cannot be
  read off the board needs a history in state (`MosaicState.trail`). The exception is the player's own marks:
  Sudoku's notes are stored and always drawn, and placing a digit strikes it from its peers' notes *in the same
  state* (one undo restores both). They are not derived (hidden when a peer holds the digit) because a hidden
  note is indistinguishable from a refused tap, and a player may make a wrong judgement; conflicts show once
  a digit is placed.
- **Colour, contrast, legibility.** Draw with `MaterialTheme.colorScheme` so light and dark both work
  (`ui/theme/Palette.kt`: `LightScheme`, `DarkScheme`; the web follows `prefers-color-scheme`). Colour is never
  the only signal: Kings' region colours are picked by CIEDE2000 distance including colour-blind vision
  (`test/KingsPaletteTest`; comment on `Kings.Preview`). Error is `scheme.error`, hint glow `scheme.onBackground`.
- **Highlight.** Read `LocalBoardHighlight.current`: strong cells glow, soft are marked, everything else dims
  while anything is highlighted; `warning` draws in the error colour. Section 7 covers reporting bounds.
- **`Preview(modifier)`:** a hand-picked static motif, drawn at 72-96 dp, cheap (eleven draw on every
  composition) and never calling `generate()`. Draw the *real* glyphs (Kings draws the real crown and
  crosses) and make the motif a legal crop of a board (`Kings.motifMarks` comment). Check the colours are
  distinguishable in a thumbnail. References: `Kings.Preview`, `Tower.Preview`/`PreviewPips`.
- **Verify by rendering, not reasoning.** Icons, motifs, joints and crosses have all failed at true size in
  ways nobody predicted. Render the real geometry at true size (a Java2D harness is the established
  approach), then check the emulator, then the web page in a narrow viewport in both colour schemes. No
  harness is checked in; expect to write one. Screenshots in `docs/screenshots/` (README) go stale silently:
  recapture them on a clean install if the home grid or an existing board's look changed (and the home
  shot's alt text names the puzzle count; `README.md` also says "Eleven").
- **Input is currently not accessible** (section 11).

## 7. Hints

Shared machinery: `core/Teaching.kt` (`Deduction`, `BoardHighlight`, `LocalBoardHighlight`, `TutorialFrame`),
`ui/teach/Hints.kt` (`HintSession`, `WatchHint`, `HintPopover`, `HintPanel`), `ui/play/PlayScreen.kt`,
`core/HighlightBounds.kt`. You write one file, `puzzles/<Name>Teacher.kt`, and a ~25-line `teach` override.

**Behaviour (settled by the owner, CLAUDE.md "Teaching").** Tap 1 nudges (where to look), tap 2 explains
(why, with cited cells), the player makes the move, and the panel confirms and clears when `isReached` sees it.
Only an explicit "Show me" (the third tap) applies it. One hint is counted per deduction opened; explaining
and Show me are free. Hints guide, they do not give.

**Shape of a teacher** (reference: `puzzles/KingsTeacher.kt`; its `Kings.teach` wrapper at
`puzzles/Kings.kt`):

```
internal object <Name>Teacher {
    const val TECHNIQUE = "name"; ... FALLBACK, MISTAKE
    val TECHNIQUES = listOf(...)          // in the order deduce tries them, simplest first; for coverage tests
    class Step(...)                       // nudge, explanation, focus, cited, targets + the move
    fun teach(s: State): Step? {          // mistakes first, then reasoning, then the honest fallback
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(<visible fields only>) ?: fallback(s)
    }
    fun deduce(<visible fields only>): Step?   // NO parameter through which the answer could arrive
}
```

and in the puzzle object, `teach(state)` maps `Step` to `Deduction(technique, nudge, explanation, focus, cited,
targets, mistake, fallback, applyTo, reachedBy)`. Copy `Kings.teach` / `Kings.applyStep`.

Rules, each tied to a precedent:

- **The solver cannot see the answer.** `deduce` takes only visible state; the signature is the guarantee.
  The answer is read in two places only, both in `teach`: to decide mistakes, and for `FALLBACK`, which says
  openly it is pointing at the answer. Puzzles with no hidden answer need neither (`SetsTeacher`,
  `TowerTeacher`: the "fallback" is a guess consistent with every score, flagged `CONSISTENT`/`ONLY_CODE`;
  `MosaicTeacher`: the fallback `KEEPS` offers a proven fill).
- **Simplest technique first**, because the hint should be the step a person would have found next. Cap
  chains so one sentence carries them (`MAX_CHAIN` in `SudokuTeacher`, `MAX_WHAT_IF_REASONS` in
  `ShikakuTeacher`). Drop a technique no board needs (Pipes dropped two after measuring) and one whose
  explanation cannot fit the panel. Teachers only learn facts they can explain (`TowerTeacher`).
- **A mistake means "no legal answer keeps this", not "differs from the stored one".** Where boards have
  several answers: `Mambo.someAnswerKeeps`, LITS (`test/LitsTeachingTest` `a square from a different legal
  answer is not called a mistake`), Pipes (`test/PipesTeachingTest` `on a board with two answers, following the
  other one is not a mistake`), Tower (any code that fits). Kings, Sudoku, Shikaku and Atoms judge against the
  stored answer, which is sound only because their verified boards have one.
- **Mistakes outrank steps** (reasoning from a falsehood teaches nothing), and taking the mistake back
  clears the hint.
- **Every step ends in a move the player can make with the board's gestures.** Sudoku's pencil marks are the
  player's own and the teacher never reads or writes them, so every Sudoku step ends in a placement; Atoms steps are bonds; Shikaku steps are one rectangle.
- **Cell indices** in `focus`/`cited`/`targets` mean whatever the board means; document the mapping
  (`AtomsTeacher.pairCell`: atom `a` is cell `a`, line `p` is `atoms.size + p`). Sudoku's keys and Tower's pegs
  use named index constants (`SudokuTeacher.pad`, `TowerTeacher.peg`).
- **Text must fit the panel.** Limits in the code: Sudoku/Mambo `MAX_EXPLANATION = 200`, Tower 170, LITS
  explanation 170 / nudge 70, Mosaic 180 / 60, Atoms measured to 230 (scrolls). Test it.
- **Off the main thread.** `teach` runs on `Dispatchers.Default`. Keep it pure and re-entrant; a hard hint over
  roughly 100 ms on a desktop JVM is a problem on a phone (Mosaic's took 1.2 s on the emulator).
- **Open hints re-check themselves** against the board (`HintSession.standsOn`); the deduction is re-derived
  after process death (teachers are pure). Do not store a `Deduction` anywhere.

**Highlight-bounds reporting contract** (`core/HighlightBounds.kt`). The popover needs to know where the
highlight is, in window coordinates, or it assumes "below". The board must report with one of:

| Call | Use when | Example |
|---|---|---|
| `Modifier.highlightGrid(cols, rows)` | an even grid fills the node | `Kings.Board` (`.highlightGrid(s.size, s.size)`), also Lits, Mambo, Mosaic, Pipes, Sets, Shikaku, Snap, Sudoku |
| `Modifier.highlightAnchor(index)` | one element *is* a highlight index | `Tower.Board` (pegs, swatches, submit), Sudoku's digit keys |
| `Modifier.reportHighlight { size, highlight -> Rect? }` | custom geometry | `Atoms.Board` (an atom's cell, a line's two atoms) |
| `Modifier.keepClear()` | a control the player needs for the move (digit pad, palette, peg rows) | `Sudoku.Board`, `Mosaic.Board`, `Tower.Board` |

Outside the play screen (the walkthrough) the local is null and these do nothing, so reporting is always
safe. Placement is `ui/teach/Hints.kt` `HintPopover`: opposite half to the highlight, overlap with the
highlight weighs 4x overlap with `keepClear`. No per-puzzle code is needed for it.

**Measuring the fallback rate.** Every teacher has a `coverage - which techniques boards need, per
difficulty` test that walks boards from empty by hints alone, prints per-technique counts, writes
`build/reports/<puzzle>-teaching-coverage.txt` and asserts a ceiling (Kings: under a tenth; Pipes:
under 1%, `hints alone solve every board, and the fallback is rare`). The numbers are the point; the table
in CLAUDE.md "Teaching" records them. Update it when a teacher changes.

**Hint tests** (copy `test/KingsTeachingTest`, the cleanest; section 10 lists them): every step agrees with
the answer and changes something; steps stay sound from boards a player made (not only the solver's path); on
boards with several answers, every step holds for every answer still possible (soundness on a unique board
cannot tell reasoning from peeking); a wrong move is addressed first and a correct one is never called wrong.

**When `offersHints = false` is acceptable.** Only when a teacher was written and measured and could not
explain the real choices. Snap is the sole case: its teacher fell back on 6/13/16% of steps and 89-100% of boards,
and the choices left are exactly what the sparse numbering exists to leave the player. The reason, with the
numbers, is in the doc comment above `Snap.offersHints` (`puzzles/Snap.kt`); a puzzle that opts out must write
its own, and must teach through its walkthrough instead (`test/SnapTutorialTest`). `PlayScreen` then shows no
Hint button and the walkthrough's free-play frame omits it.

## 8. The walkthrough

Interactive, played on the puzzle's real `Board` by `ui/tutorial/TutorialRunner.kt`, offered once, replayable.

**Behaviour.**
- Offered once as one passive line over the clock on a first visit until the first move; recorded the moment it
  is shown in `ProgressStore.tutorialsOffered`. No popups, no focus stealing. "How to play" (`?`) opens it
  whenever; the `rules` list is the runner's "Rules" button. Skip is always on screen.
- Frame, board and hint state are transient (never in a saved game). The walkthrough draws over the game, so
  the board, undo stack and clock are where the player left them, and the clock pauses.

**Writing the frames** (`TutorialFrame`, `core/Teaching.kt`; reference: `Kings.tutorial` in `puzzles/Kings.kt`,
7 frames, and its `only(base, changes)` helper):

1. Hand-build a tiny board (4x4 to 5x5; `Kings.TUTORIAL_REGIONS`/`TUTORIAL_SOLUTION`/`tutorialBoard`). It has
   **exactly one answer** and that answer is the stored one; a test enumerates it independently.
2. Open with explanatory frames (`accepts = null`, board not playable, Next to continue) on a solved board
   with a `BoardHighlight` showing the rule.
3. Then teach each gesture with a frame whose `accepts` demands exactly the resulting state
   (`only(...)` compares the whole mark list, so a stray tap cannot slip through) and whose `highlight` points at
   the cell. Each frame's `state` is the previous frame's result, so a caption never describes a board the
   player is not looking at. Write `retry` (what to do) and `done` (what that taught) for each.
4. Cover every distinct gesture the board has: Kings tap/double-tap/sweep; Snap start-drag, resume, cut-back,
   rub-out; Sets pick/unpick/third card; Tower copy/change/submit.
5. **A refused state is dropped**, so a move must be a single emitted state. If a gesture legitimately emits
   several (Sets' three taps, a Snap drag a square at a time), set `passes`, or write one frame per emission as
   the existing walkthroughs do. (Note: no walkthrough currently sets `passes`; see section 12 gaps.)
6. **Finish with a free-play frame** (`freePlay = true`): every move applies, hints are available (when the puzzle
   offers them) and the frame ends when the board is solved. The test must prove hints alone finish it with no
   fallback and no mistake.
7. Captions: at most four lines in the runner's fixed `HintSlotHeight` (156 dp) slot; the tests cap them at
   170 chars (LITS, Sudoku, Tower, Sets) or 200 (Snap). Teach with the board's own numbers and names, not
   abstractions (Kings builds region names with `regionNames`).

**The test pattern** (copy `test/KingsTeachingTest` `each walkthrough frame accepts its move, made by the real
gestures, and rejects a wrong one`, and `the walkthrough board has exactly one answer, and it is the stored
one`): assert the frame count, assert which frames are Next-only, build each accepted state by calling the
board's real state functions (`toggleMark`, `paint`, `tap`...), assert `accepts` true for it and false for a
near miss (wrong cell, wrong gesture, partial sweep), assert each frame's board is the previous frame's result,
assert each claim a caption makes about the board, and walk the last frame with `teach` until solved asserting
`!d.fallback`. `test/SnapTutorialTest` is the pattern for a puzzle with no teacher (its own enumerator and
`movesFrom`, every move the gestures can make).

## 9. State, saves, stats

- `PuzzleState` is a sealed `@Serializable` interface; implementations must live in package
  `com.joebywan.daybook.puzzles` (Kotlin's sealed rule is per package) and be immutable data classes whose
  mutators return a new state and bump `moves`.
- `solved` and `failed` are computed properties, never written (`test/StateSerializationTest` `the computed win
  condition is never written`). Nested types (`Block`, `Link`, `Card`, `Atom`, enums) need `@Serializable` too.
- **Saved games are keyed `(puzzle, difficulty, seed)`** (`test/SavedGameTest`) and decoded with
  `ignoreUnknownKeys = true`, unreadable ones are dropped, not thrown (`data/ProgressStore.kt`). So: never
  rename a field or a class; add fields **with defaults** (Sets' `lastPick`, `lastRepeat`, pinned by
  `test/SetsTeachingTest` `an old save without the rejected pick still loads`; Sudoku's `notes`, whose
  default means a game without any is written as it always was, pinned by `test/SudokuNotesTest` `a save written
  before notes existed still loads, with none` against a JSON string captured from the build before); do not change what a stored
  field means; changing a generator changes what a saved seed's board is, so a saved game in progress on the old
  board then fails to match its seed's board (parity dump before and after).
- Saved undo history is bounded (`SavedGame.UNDO_DEPTH` = 24); no state may be big enough to make 24 copies a
  problem.
- **Stats, streaks, archive, home card, results card** pick the puzzle up through the registry
  (`ui/stats/StatsScreen.kt`, `ui/home/HomeScreen.kt`, `ui/archive/ArchiveScreen.kt`). A completion records
  puzzle id, difficulty, date, seconds and hint count; nothing per-puzzle is needed.
- `moves` is shown on the results card; keep it honest (one per player action).
- Round-trip test: `test/StateSerializationTest` iterates the registry but needs a branch in its `mutate`
  (an exhaustive `when`; the build fails until you add one), which must change the board.

## 10. Tests required

Each puzzle `<N>`. "Template" is the file to copy.

| Concern | What it proves | Template |
|---|---|---|
| Rules / validator | each rule on its own refuses a placement; the win check accepts a legal answer that is not the stored one | `KingsRulesTest`, `MamboRulesTest` + `MamboSolvedTest`, `LitsRulesTest` |
| Independent checker | uniqueness of generated boards by a solver sharing nothing with the generator | `SnapCluesTest`, `ShikakuClueTest`, `LitsUniquenessTest` + `LitsOracle` |
| Generator contract | unsolved start, deterministic, time budget | `GeneratorTest` (automatic via registry) |
| Proof / fallback | the verified path never gives up over a rate-sized sample; each fallback pinned by a property only the real generator has | `FallbackTest` (year x tier), `KingsRulesTest` `the proved path never abdicates` |
| Tier balance | harder tiers are not easier; no slack creeping in | `TowerBalanceTest`, `MosaicOptimumTest`, `SetsRulesTest` |
| Reachability | tapping the real handler can reach the stated target | `SetsRulesTest` `claiming every set on the board finishes the puzzle` |
| Serialization | part-played state round-trips; computed fields not written | `StateSerializationTest` (add a `mutate` branch) |
| Teaching soundness | steps hold for every answer still possible; sound from player-made boards; mistakes outrank steps and are real | `KingsTeachingTest` |
| Teaching coverage | which techniques boards need per tier; fallback ceiling asserted | `KingsTeachingTest` `coverage - ...` |
| Text fit | nudge/explanation/caption inside the panel | `LitsTeachingTest`, `SetsTeachingTest` `every explanation and nudge fits the panel` |
| Tutorial completion | one answer; each frame accepts its real-gesture move and rejects a near miss; free-play finishable by hints, no fallback | `KingsTeachingTest`, `SnapTutorialTest` |
| Gesture geometry | drag/hit maths without Compose | `AtomsDragTest`, `KingsMarkingTest` |
| Layout budgets | the reserved slot or card size fits at the smallest width | `MamboCaptionTest`, `SetsStripTest` |
| Parity pins | boards identical on JVM and wasm | `WebParityShikakuSnapSudokuTest` + the dump (section 5) |
| Seeding | untouched; pinned by `WebParityTest` `epochDay agrees with LocalDate...` and `CompletionFormatTest` | n/a |

A test that cannot fail is not a test: assert the property the *bug* violates, not a looser one the bug also
satisfies. Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` with Java 17 and `--no-daemon`
(`CLAUDE.md` "Build"; never `./gradlew --stop`).

## 11. Accessibility and other constraints

**Accessibility is knowingly absent and deliberately deferred while the app is sideloaded** (CLAUDE.md
"Settled"). Concretely: eight boards (Atoms, Kings, LITS, Mambo, Mosaic, Pipes, Shikaku, Snap) use raw
`pointerInput` and expose no click actions, so a screen reader cannot operate them; Sets, Sudoku and Tower use
`clickable` (partial). The walkthrough and hint text are real text and readable. No semantics, no content
descriptions on cells, no reduced-motion handling, no minimum touch-target audit beyond Tower's swatch note.
New puzzles are not required to fix this, but should not make it worse (do not put the only signal in colour;
prefer `clickable`/`semantics` where the gesture is a simple tap).

Other settled constraints on design (CLAUDE.md "Settled", "Rules"):
- Mosaic's limit equals the proven optimum with zero slack; Mosaic is a Kami-style flood fill.
- Snap keeps exactly one answer with minimal clues; do not add clues to soften a tier.
- LITS regions cap at seven squares and its win check does not require connectivity.
- `applicationId` and the signing key are permanent; the puzzle `id` is too (for saves).
- Notifications and offers are passive and once (memory: unobtrusive notification UX); a hint never steals focus
  or moves the board.
- Sudoku's pencil marks (`SudokuState.notes`) are visible state that the teacher deliberately ignores; do not
  add hidden state a hint cannot see.

## 12. Conformance matrix

Verified against the code and tests on 2026-10-01 (grep and reading, not memory). Y = yes, P = partial, N = no.

| | Tiers | Proved gen | Rules `solved` | Indep. check | Teacher | Tutorial | Highlight | Parity pin | Preview | Text-fit |
|---|---|---|---|---|---|---|---|---|---|---|
| Sudoku | Y clues 38/30/24 | Y dug to uniqueness (`countSolutions`), no fallback | Y `Sudoku.isSolved` | Y `SudokuTeachingTest` enumerator | Y | Y 6 | Y grid + anchor + keepClear | Y | Y | Y 200 |
| Kings | Y 7/8/9 | Y `generateVerified`, `lastResort` | Y | Y `KingsRulesTest` | Y | Y 7 | Y grid | Y `WebParityTest` | Y | N |
| Mambo | Y 6/8/10 | Y by construction (carve to propagation-solvable) | Y | Y `MamboRulesTest`, `MamboSolvedTest` | Y | Y 8 | Y grid | Y | Y | Y 200 |
| Pipes | Y | n/a no uniqueness claimed | Y | Y `PipesTeachingTest` enumerator | Y | Y 7 | Y grid | Y | Y | P sentences, not length |
| Shikaku | Y | P `countTilings` loop + `fallbackBoard`, no `generateVerified` | Y `Shikaku.isSolved` | Y `ShikakuClueTest` | Y | Y 7 | Y grid | Y | Y | Y |
| Mosaic | Y slack 0 | Y `solve` or null; last pass stripes | Y (all one colour) | Y `MosaicOptimumTest` BFS | Y | Y 6 | Y grid + keepClear | Y | Y | Y 180 |
| Sets | Y | n/a target = sets present; `generateVerified` else exhaustive `lastResort` | Y | Y `SetsRulesTest` plays the handler | Y no fallback | Y 9 | Y grid | Y | Y | Y |
| Atoms | Y | Y `generateVerified`, year test | Y | Y `AtomsTeachingTest` enumerator | Y | Y 7 | Y custom | Y | Y | P 230 in coverage |
| Snap | Y | P `Verdict` enum, no `generateVerified`; fallback pinned by clue budget | Y `obeysRules` | Y `SnapCluesTest` | N by design (`offersHints = false`) | Y 9 | Y grid | Y | Y | Y 200 |
| LITS | Y | Y `generateVerified` (internal), sealed `Verdict`, year test | Y | Y `LitsOracle`, `LitsAuditTest` | Y | Y 8 | Y grid | Y | Y | Y |
| Tower | Y slots, colours, guesses | n/a random code | Y | Y `TowerBalanceTest` solver | Y | Y 7 | Y anchors + keepClear | Y | Y | Y 170 |

Details behind the P/N cells:

- **Sudoku, Shikaku teachers' `mistake`:** `solved` is now the rules, but both teachers still judge a
  mistake by difference from the stored answer (Sudoku: a digit != `solution`; Shikaku: a block not in
  `solution`). Right only while the board has one answer, which both generators prove (the Shikaku
  `fallbackBoard`, a clue in each rectangle's corner, is unique by construction). Not yet a "no legal
  answer keeps this" check.
- **Shikaku, Snap proved generation:** the proof exists (exhaustive count; `Verdict.UNIQUE`) but `generate`
  does not expose a `generateVerified` null-on-failure split, so no test asserts "the fallback is never
  reached over a year". Their fallbacks are pinned by property (`ShikakuClueTest` `the fallback board carries
  no clue of 1 either`; `FallbackTest` clue budget) on six or twenty seeds rather than a year. Shikaku's
  `ATTEMPTS = 2000` comment records a 20,000-seed sweep with no fallback.
- **Kings proved gen sample:** `KingsRulesTest` asserts the proved path over 30 seeds per tier, not a year; the
  analytic margin (40 attempts at a one-third to two-thirds success each) makes that adequate, but it is the
  weaker form of the Atoms/LITS test.
- **Mambo proved gen:** no explicit fallback; `fullGrid(rng, n) ?: fullGrid(Rng(seed + 1), n)!!` can throw on a
  double failure. Six seeds per tier in `MamboRulesTest`.
- **Sets:** `generate` is `generateVerified ?: lastResort`; the sampler fails on about 1 seed in 10^40 (no seed in
  four years of daily boards or 500,000 random seeds per tier came near), and `SetsRulesTest` walks a year per tier
  on `generateVerified` and runs `lastResort` directly.
- **Pipes:** no generator uniqueness claim (accepted); its teacher and tests are written for several answers.
- **Text-fit tests:** missing for Kings, partial for Pipes and Atoms. Kings' captions and explanations are
  untested for panel length.
- **Tower sizing** is by a bounded `LazyColumn`, not `maxHeight`; CLAUDE.md says all eleven "consult the
  height". They fit the box, by a different mechanism.

### Known gaps and open items

1. `TutorialFrame.passes` (`core/Teaching.kt`, honoured in `ui/tutorial/TutorialRunner.kt`) is not set by any
   puzzle. CLAUDE.md says Sets, Mambo and Snap use it; they instead write one frame per emitted state. The
   mechanism is untested dead API until something needs it.
2. `generateVerified` is not a universal convention (Kings, Atoms, LITS and Sets only). Shikaku and Snap should get
   one and a year-long `FallbackTest` entry; Mambo a year-long test of its "cannot fail" path.
3. Sudoku's and Shikaku's teachers judge a mistake against the stored answer (see section 12); sound
   while their boards are unique, which the generators prove.
4. Kings (and Pipes, Atoms captions) have no text-fit test.
5. Accessibility (section 11), already in CLAUDE.md "Open".
6. No Java2D/emulator render harness is checked in; "verify by rendering" is by convention only.
7. README "Adding a puzzle" and the `PuzzleType` KDoc say two steps and that purity is "the only hard rule".
   In practice a new puzzle also needs the compile-forced `ParityFingerprint.body` branch,
   `StateSerializationTest.mutate` branch, a parity pin, and (for a good one) a teacher and walkthrough.
   This file is the fuller list.
8. Coverage table in CLAUDE.md "Teaching" is measured data that goes stale when a teacher or generator changes.

## 13. Adding a new puzzle: the recipe

In order. `X` is the new puzzle's name. Do this on a branch, never `main`.

1. **Decide the rules and the tiers on paper.** What is the unique answer? How do the tiers differ (section 3)?
   Is there a hidden answer to leak into hints? Can a player make an intermediate state that is wrong?
2. **`puzzles/X.kt`:** `@Serializable data class XState(..., override val moves: Int = 0) : PuzzleState` with
   a computed `solved`, plus `object X : PuzzleType` with `id`, `displayName`, `tagline`, `accent`, `rules`.
   Put the rules in one function the validator, the live feedback and the generator's last check all call
   (`Mambo.violationsOf`). Provide state mutators that return one new state per gesture.
3. **Generator:** `generate` via `generateVerified(...) ?: lastResort(...)`, `Rng(seed + attempt)`, a proof
   with a sealed or enum verdict, a documented fallback. Run the section 5 grep.
4. **`PuzzleRegistry.all`:** add `X` (home order).
5. **Build the board:** `Board` with the section 6 rules; a `highlightGrid`/`highlightAnchor`/`reportHighlight`
   report; `keepClear` on any control the player uses for a hinted move.
6. **`Preview`:** a fixed motif of the real glyphs. Render it at true size, both themes.
7. **`ParityFingerprint.body`:** add the `when` branch (the build tells you).
8. **Tests, first pass:** `XRulesTest` (rules, independent checker, win by a non-stored legal answer),
   `StateSerializationTest.mutate` branch, parity pin test (run, copy the printed lines in).
   Run `GeneratorTest` and a year-long proof of the verified path.
9. **`puzzles/XTeacher.kt`:** technique ladder, mistake, `deduce` over visible state only, fallback if the
   puzzle has a hidden answer; the `X.teach` wrapper and `applyStep`. Copy `KingsTeacher`. Write
   `test/XTeachingTest` from `KingsTeachingTest`. Measure the fallback rate per tier; add a row to the CLAUDE.md
   "Teaching" table. If you decide hints cannot work, `offersHints = false` with the measurements in the doc
   comment (Snap precedent) and say so in the final report.
10. **Walkthrough:** `tutorialBoard`, constants, `tutorial` frames; tests per section 8.
11. **Web:** run the section 5 dump comparison; check the page at a phone width in both colour schemes
    (`?puzzle=x&tier=expert`); check generation time on the web for Expert (`?dump&range=N&times`).
12. **Housekeeping:** README puzzle list and "Eleven" count; recapture `docs/screenshots/home.png` (and its alt
    text); `HomeScreen` comment on the row count if the grid changes shape; CLAUDE.md puzzle count and
    teaching table; this file's matrix.
13. **Commit and PR:** branch, PR, review and merge per CLAUDE.md "Git and releases". Push to `main` builds a
    signed release automatically.
