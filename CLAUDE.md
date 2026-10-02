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
  in `core/PuzzleRegistry.kt`; home grid, archive, streaks, stats, hints and saves pick it up.
  What every puzzle must have beyond that (tiers, proof, hints, walkthrough, tests): `docs/PUZZLE_STANDARDS.md`.
- `generate(seed, difficulty)` **must be pure**. Daily boards come from
  `hash(date, puzzleId, difficulty)`, so purity is what makes the whole archive free and offline.
- `PuzzleState` is a sealed `@Serializable` interface living in `puzzles/` — Kotlin requires
  sealed implementations to share the sealed type's *package*, not merely its module.
- `Preview(modifier)` draws a fixed motif for the home grid. **Never call `generate()` in it** —
  thirteen of them draw on every composition.

## Teaching: hints that explain, and walkthroughs

All thirteen puzzles teach. The shared pieces are `core/Teaching.kt` (the contract), `ui/teach/Hints.kt`
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
  to 104dp, its text scrolling behind a fade. Taps outside the card reach the board, so the move can
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
| Nonogram | one line at a time: clues fill the line, clue already complete, overlap of the clue's slides, what the marks already in the line leave possible | none exists: every board is line-solvable, so some line always has a square to settle (200 boards per tier, hints alone finish all) |

Adopting it, or changing a teacher — the lessons of thirteen of them:

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

## Lexicon (the word game)

Mastermind for words: `puzzles/Lexicon.kt` (board, keyboard, walkthrough), `LexiconRules.kt` (state, marking,
the tier shapes, `WordList`), `LexiconTeacher.kt`, `LexiconKeys.kt` (hardware keys), `LexiconWords.kt`
(generated). `id = "words"` is hashed into every seed: never rename it.

- **The word is an index into a sorted list**, so the lists' order is part of the contract. `LexiconWords.kt` is
  generated by `tools/words/build.py` from SCOWL (the npm module `wordlist-english` 1.2.1; notice in
  `docs/word-lists/LICENSE-SCOWL.txt`, which has to travel with the lists). Adding or removing a word moves
  every daily board after it, so `LexiconWebParityTest` goes red on purpose. Screening lives in
  `tools/words/exclude.txt`: offensive, informal, obscure, plurals the stem rule cannot see. Answers are
  dialect-neutral SCOWL levels 10/20/35 minus inflections; guesses are every plain a-z word to level 70 in
  every dialect, so colour/color style pairs are both accepted and neither pair is ever an answer.
- **Tiers: 5 letters / 6 guesses, 5 letters / 5 guesses, 4 letters / 7 guesses.** Any real word is a legal guess on
  every tier: there is **no hard mode** (the owner: don't force players to use their clues, "if they don't, that's on
  them"; an earlier build had one, and it is gone). Four letters is the *harder* length: a player who always guesses
  a word still possible wins every five-letter board in six and 98% in five, but only 92% of four-letter boards in
  six and 96% in seven (families like -ATE); one who also spends turns on words that cannot be the answer wins them
  all. So Expert gets a guess more than Hard and is still the tier that asks the most. `LexiconBalanceTest` measures
  both players; the constants alone prove nothing. A saved game from the hard-mode build still loads (the old
  `hard` key is ignored) and plays under the new rules.
- **A probe is never a mistake.** The teacher's only mistake is a row that is not a word. Calling a legal probe
  wrong is the PR #15 bug.
- **A pin is a fact about the marks alone**, so `LexiconTeachingTest` checks it against every accepted word that
  fits, not only the answer list. `ONLY_WORD` and `CHOOSE` do read the answer list, say so, and count as the
  fallback.
- The typed row and the "Not in the word list" note are `remember` state, never `PuzzleState`; the note sits in
  the header line whose room is always reserved, so a refusal cannot move the board.

## Nonogram

Run-length clues beside a grid; fill the squares that give every row and column its numbers. `puzzles/NonogramRules.kt`
(state, rules, line solver, generator), `NonogramTeacher.kt`, `Nonogram.kt` (board, walkthrough, motif). `id = "nonogram"` is hashed
into every seed: never rename it.

- **Tiers are size alone: 5x5 / 10x10 / 15x15**, all at 55% filled. The same line logic and a longer way to carry it.
- **A board ships only if lines alone finish it** (`NonogramLogic.lineSolvable`: per line, the squares every legal layout of
  the clue agrees on, to a fixpoint). That also makes the answer unique, and `NonogramRulesTest` checks it with an
  independent oracle that tries every arrangement of every row. About 50-80% of random pictures pass, so 400 draws per seed
  never fall back (a year per tier is tested); `lastResort` is a fixed diagonal, itself proved line-solvable.
- **`solved` checks the clues, not the stored picture.** Crosses are notes and never count.
- **Hints cannot fall back**, so there is no fallback to measure: if the player's marks are right, some line always has a
  square to settle (settled squares only ever help). The mistake is a mark the stored picture disagrees with, sound because the
  picture is the board's one answer. Highlight indices past the squares are the clues (`rowClueIndex`, `colClueIndex`).
- **The pen (Fill/Cross) is `rememberSaveable` in the board, not state.** A sweep is one state: it locks to the row or column
  of the larger move, a pen only writes over untouched squares, and an eraser only takes out the mark it started on.
  `onDragStart` uses the overload that receives the *down*, since the web's touch slop is wider than a 15x15 square.
- The pictures are random noise, not drawings. Smoothing them into blobs made 5x5 boards fail the filter too often
  (4% passed the shape rules); see `docs/TODO.md`.

## Rules that keep being relearned

**Never ship a board the generator has not proved.** Kings, LITS, Mosaic and Shikaku each had a
fallback that was reachable and unvetted, and in three of them that fallback was what players
actually got. LITS's real generator had *never once run*. When you add a fallback, ask what
fraction of seeds reach it, and measure rather than assume. Size the test's sample to the rate:
Atoms once fell back on 13 of 365 Expert days (3.6%), which `FallbackTest`'s twenty seeds miss
about half the time; its Atoms test now walks a full year of daily seeds on every tier (~2 s).
Every board with a fallback has `generateVerified` (null when nothing was proved), which is what tests assert: Kings, Atoms, Sets (public) and LITS, Mambo, Shikaku, Snap (`internal`). `FallbackTest` walks a year of daily seeds on every tier through it for Kings, Atoms, LITS, Shikaku and Snap (none ever falls back); Sets and Mambo have their year in their own rules tests.

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
Keyboard (web, or a hardware one): `puzzles/SudokuKeys.kt` maps a key to a `SudokuKeyAction` (digits, clear, arrows; pure, tested) and applies it through the pad's own `enter`/`withCell`/`toggleNote`, so notes, undo and peer-striking cannot differ; chords are ignored and a held digit is not repeated. The board's box takes focus (clicking a cell, or a hint opening, hands it back; the page needs a click first, since the canvas is not DOM-focused on load).

**Verify by rendering, not reasoning.** Icons, motifs, crescents, pipe joints and crosses have
all failed at true size in ways nobody predicted — a crown read as a comb, pages as a boat hull,
Atoms as a wireframe. A Java2D harness driving the real geometry is the established approach (not
checked in). Then check on the emulator; several bugs only appeared there. For the web build there is a
checked-in harness: `tools/render/render.py` (see "Rendering harness" under Web build).

**README screenshots go stale silently.** `docs/screenshots/*.png` are emulator captures at half
scale (540x1200), taken on a *clean install* so the home screen shows a 0-day streak and unplayed
boards rather than whatever the session happened to leave behind. Nothing checks them, so a change
to a board's look or to the home grid means recapturing them in the same pass — the home shot in
particular names the puzzle count in its alt text.

**Write agent patches early.** One agent lost a complete implementation by leaving
`git diff --cached > patch` until the end and dying on a rate limit.

## Web build (`web/`)

The whole app — all thirteen puzzles, home, play, archive, stats, walkthroughs, saves — in a
browser, via Compose Multiplatform 1.12.1 on Kotlin/Wasm (`wasmJs`) with the repo's Kotlin 2.4.20.
Live at https://knowhowit.com.au/daybook/. Needs Safari 18.2+ / iOS 18.2+ for WasmGC.

### How it is put together

- **One copy of the code.** `web/build.gradle.kts` compiles `app/src/main/java` itself, minus an
  `androidOnly` list (`MainActivity`, `DataStoreKeyValueStore`, `platform/AndroidPlatform.kt`). A
  new file in app/ is on the web by default, so it must stay free of `android.*` and `java.*` —
  `Integer.bitCount`, `sortedSetOf`, `toSortedSet`, `java.util.Arrays`, `String.format`, `System.*`
  and `java.time` all fail the wasm compile. That is why the seed arithmetic is `core/SeedHash.kt`, dates are
  `kotlinx.datetime.LocalDate`, and Kings times double taps with `TimeSource.Monotonic`. The
  patterns apply to web/'s own source directory too, so a web file must never share a path with
  one on that list.
- **Platform seam, not expect/actual.** Shared files call top-level functions in
  `com.joebywan.daybook.platform`; app/ defines them in `platform/AndroidPlatform.kt` (the exact code
  the screens used to run inline), web/ in `platform/WebPlatform.kt`. Same names, same package, one
  file per build; a signature added to one and not the other is a compile error in the other build.
  It covers dates and formatting, storage, back, the backup controls, board generation and fonts.
- **Dates are `kotlinx.datetime.LocalDate`.** Nothing on disk stores a date object (completions hold
  an epoch-day number), so saves are untouched; `CompletionFormatTest` pins that. Tests keep writing
  `java.time` dates through `JavaDates.kt`. Formatting goes through the seam so Android keeps its
  locale-aware `DateTimeFormatter`; the web spells out English names.
- **Storage is `data/KeyValueStore`.** Android: the same two DataStore files and keys as ever
  (`DataStoreKeyValueStore`). Web: `localStorage`, key `file.key`, sets as JSON arrays. The web
  backup (Stats screen, export/import) is every one of those entries, so it needs no change when a
  store does — a browser can drop a site's storage and there is no cloud backup behind it.
- **Back** is the browser's history (one entry pushed per enabled back handler), plus a visible
  arrow via the seam's `BackButton`, because a home-screen web app on iOS has no back gesture.
  Android's `BackButton` draws nothing.
- **Offline:** `sw.js` is network-first for unhashed files (page, `daybook.js`, manifest) and
  cache-first for the content-hashed `.wasm`; the page posts its resource list to the worker, since
  the first visit loads before the worker controls it. `manifest.webmanifest` and the icons make it
  installable to the home screen.
- **Favicon and link preview.** `favicon.svg` (the launcher mark simplified for a tab: no rays,
  bigger sun lifted clear of a wider book — a sun touching the book reads as a head), `favicon.ico`
  (16/32/48) and `icons/icon-16|32.png` are rendered from that SVG by hand (Chromium screenshot at
  each size, ICO via Pillow); the 192 PNG the page used to link mushed the rays at 16px. They are
  `rel="icon"` links in `index.html`, *relative* (the page is always at `/daybook/`); the domain
  root's `/favicon.ico` belongs to another site. The link preview (`og:*`, `twitter:*`) uses
  *absolute* `https://knowhowit.com.au/daybook/...` URLs, since a crawler has no base. The card is
  `web/src/wasmJsMain/resources/social-preview.png` (1200x630), emitted with the repo's 1280x640
  card by `python3 docs/social-preview/make.py`; both PNGs are committed. It lives under `web/`
  because `docs/**` does not trigger the pages workflow, so a copy under docs/ would never deploy.
  The same mark sits beside the "Daybook" title on Home (`ui/home/DaybookMark.kt`, shared with
  Android, drawn bare from the favicon's paths with no tile, so it holds on light and dark pages —
  keep the two in step) and above the `#loading` note in `index.html` (`<img src="favicon.svg">`).
  No puzzle count in any public text (it ages). The favicons are in `sw.js`'s precache, the card is
  not (crawlers do not run the worker); changing the SHELL list means bumping `CACHE`, whose old
  names `activate` deletes. Link unfurls are cached by the platforms: after a change, re-scrape in
  Facebook's Sharing Debugger; Discord and Slack refresh on their own schedule.
- **Generation on one thread.** Android generates off the main thread (`generateBoard` in the seam
  is the `withContext(Dispatchers.Default)` it always was). Wasm has one thread, so the web's
  `generateBoard` first waits until "Setting out …" has been *painted* (`requestAnimationFrame` →
  `setTimeout`), and `prepareBoards` — called by `DaybookApp` while Home is showing, a no-op on
  Android — makes today's thirteen boards at the grid's tier in advance, one per turn of the event
  loop, into a small cache. A tap usually finds its board ready. A board already underway cannot be
  interrupted, so a tap during a slow pre-generation still waits for it. A cached board skips the
  loading screen (`readyBoard`). The loading screen waits `LOADING_MESSAGE_DELAY_MS` (150 ms) before
  showing its message on Android, where it also turns a spinner (`GENERATION_ANIMATES`); on the web
  both are off: the delay is zero because nothing can be painted once a board is underway, and a
  spinner would sit frozen.
- **Fonts.** A browser lends wasm none of its fonts; without help, text falls back to the one font
  Compose ships, which has no `→` and no serif. `platformTypography` in the seam swaps the bundled
  Noto Serif Bold (Android's serif) into the serif styles and preloads a few arrows from Noto Sans
  Symbols, which Skia then uses as a fallback for glyphs the default lacks. Both live in
  `web/src/wasmJsMain/composeResources/font`, cut down to Latin-1 plus common punctuation (and the
  arrows) with glyph ids kept, so kerning still applies: 134 KB + 17 KB, 57 KB + 5 KB gzipped,
  against 612 KB for the whole serif. `tools/subset-font.py` cut them: it keeps glyph ids, empties every other
  glyph, rewrites `cmap` and drops `GSUB` (whose ligatures would land on emptied glyphs); a new
  non-Latin-1 character in a serif string needs the font re-cut. Check any non-ASCII glyph in a
  shared string by rendering it.
- The Material icons: `compose.materialIconsExtended` costs 4.5 KB of wasm (<1 KB gzipped) over the
  core set, because Kotlin/Wasm drops every unreferenced icon.

### Building, serving, testing

- **Build:** `./gradlew :web:wasmJsBrowserDistribution` → `web/build/dist/wasmJs/productionExecutable/`
  (~12.8 MB without source maps, ~4.5 MB gzipped: Skia's wasm is 8.4 MB, the app's 3.6 MB). Serve it with `python3 -m http.server <port> --bind 0.0.0.0` from that directory
  (Python maps `.wasm` to `application/wasm`, which streaming instantiation requires).
- The Node/Yarn/Binaryen downloads are declared in `settings.gradle.kts`, because
  `FAIL_ON_PROJECT_REPOS` rejects the repositories the Kotlin plugin adds per project. Switching the
  plugin's own URL off needs `convention(null)` as well as `set(null)` — `set(null)` alone falls
  back to the convention, which *is* the URL.
- **Query hooks:** `?date=YYYY-MM-DD` is the app's "today"; `?tier=standard|hard|expert` sets the
  home grid's difficulty (it persists, as a tap would); `?puzzle=<id>` opens that puzzle's daily
  board instead of Home. `?dump` prints every puzzle's `core/ParityFingerprint` line to the
  console — `PARITY` for the dates the per-puzzle parity tests pin, `TODAY` for `?date`/`?tier` —
  and `&range=N` adds `RANGE` lines for every tier of N days from 2026-01-01 plus `TIMING` per
  puzzle and tier, ending with `DUMP DONE`. `&puzzle=<id>` narrows the dump to one puzzle, which
  lets a harness run the thirteen in parallel pages; `&times` adds a `TIME <id> <date> <tier> <ms>`
  line per board, for medians and percentiles.
- **Parity:** `DAYBOOK_PARITY_DUMP=<file> ./gradlew :app:testDebugUnitTest --tests
  '*WebParityDumpTest*'` writes the JVM's year in the same order; strip `RANGE ` from the page's
  lines and the files must be identical. The same file, taken before and after a change, is how
  Android boards are proved unchanged. `WebParityTest`, `LitsWebParityTest`,
  `MosaicAtomsWebParityTest`, `WebParityShikakuSnapSudokuTest` and
  `WebParityMamboPipesSetsTowerTest` pin a few boards per puzzle outright. For LITS alone,
  `DAYBOOK_LITS_DUMP=<file>` (`DAYBOOK_LITS_DAYS`, default 730) runs `LitsYearDumpTest`: the same
  lines over two years plus per-board JVM times in `<file>.times`.

  **CI runs the year.** `tools/web-parity/check.py --jvm <file>` does the comparison by hand above for
  you: it serves the built dist, loads `?dump&range=N&puzzle=<id>` for all thirteen puzzles at once in
  Chromium then WebKit (Playwright, pinned in `tools/web-parity/requirements.txt`), strips `RANGE `,
  and diffs each puzzle against the JVM file (N is read from it), printing the first differences by
  puzzle, date and tier. Exit 1 on any difference, missing line, page error or timeout. A full year
  (12,045 boards) takes 30 s in Chromium and 40 s in WebKit on a 32-core machine, 36 s and 49 s pinned
  to 4 cores, so there is no short-range variant. `WebParityDumpTest` takes `DAYBOOK_PARITY_DAYS`
  (default 365) for a quicker local look. CI wires it as the composite action
  `.github/actions/web-parity`, used twice: by `web-parity.yml` (a pull-request job, *not* `build`,
  so it is visible but never holds up the required check; path-filtered to app/src/main, web/, Gradle
  files and itself) and by `pages.yml`, between building the dist and uploading it, so a divergence
  that got past review stops the deploy. Renovate's pull requests get no `web-parity` run (their
  token triggers nothing), so for them `pages.yml` is the gate. The dump test must be run with
  `--rerun` (the env var is not a Gradle input; a cached test would write no file). Playwright's own
  console errors are not failures (headless WebKit complains about WebGL); a `pageerror`, a crash or
  no `DUMP DONE` is.
- **Timings** (a year's worst board per tier, Chromium, twelve pages sharing the CPU): Mosaic
  Expert 0.3 s, Snap 0.4 s, Sudoku Expert 0.1 s, the rest under 0.1 s. LITS was 1.9 / 3.2 / 4.5 s
  (WebKit up to 11 s) until its generator moved to bitmasks; over 2026-2027 its worst board is now
  46 / 75 / 93 ms in Chromium and 52 / 80 / 99 ms in WebKit, alone (see below).
  The no-connectivity re-proof (and the retries it causes for the ~30% of boards that needed one)
  raised the JVM mean per board from 5 / 9 / 13 ms to 9 / 11 / 17 ms and the worst of 2026-2027 from
  41 / 62 / 85 ms to 57 / 69 / 118 ms; those browser figures predate it and were not re-measured.
- Dark theme follows `prefers-color-scheme`; Playwright's `color_scheme="dark"` context option is
  enough to screenshot it.
- **Rendering harness: `tools/render/render.py`.** Serves the built dist on its own free port and
  screenshots any puzzles x tiers x viewports (default 390x844, 390x664, 360x640, 375x537) x browsers
  (Chromium, WebKit) x schemes into `$TMPDIR/daybook-render/<puzzle>-<tier>-<W>x<H>-<scheme>-<browser>.png`,
  with the touch/scale/resize/first-frame handling below built in. `--measure` prints each board's
  bounding box (pixel analysis; the canvas has no DOM), `--tap X,Y` shoots again after a tap, `--storage
  file.json` seeds `localStorage`, `--home` shoots the home grid; it exits non-zero on page or console
  errors or a blank frame. `tools/render/README.md` has the set-up and limits (no `--solved`: that
  needs each puzzle's answer). Use it rather than rebuilding a script, and extend it when it lacks
  something.
- **Check any web layout change in Playwright WebKit as well as Chromium.** There is no iOS
  Simulator on Linux, and WebKit is the engine behind every iPhone browser, so it is the nearest
  stand-in. Emulate the phone with `has_touch=True`, `device_scale_factor=2` and a *short* viewport:
  **375x537** is an iPhone with both Safari toolbars showing, the size that exposed the board
  shrinking under the reserved hint slot (a 750dp-tall screen hid it). Run it against
  `?puzzle=<id>` on a local `http.server` of the build, with a `resize` dispatched before the
  screenshot (see "Headless WebKit is not Safari" below). Set-up that works without sudo:
  ```bash
  python3 -m venv /tmp/pwenv && . /tmp/pwenv/bin/activate && pip install playwright
  python -m playwright install webkit chromium     # its install-deps step needs sudo; skip it
  apt download libavif16 && tar xzf libavif16.tar.gz && for d in libavif16/*.deb; do dpkg -x $d root; done
  cp root/usr/lib/x86_64-linux-gnu/lib{avif,yuv,gav1}.so* ~/.cache/ms-playwright/webkit-*/minibrowser-wpe/sys/lib/
  ```
  The copy into `sys/lib` is needed because the launcher overwrites `LD_LIBRARY_PATH`; `libavif16`
  is the one library missing on Ubuntu 24.04. `p.webkit.launch()` then works headless. (The harness
  also needs `pillow numpy`; its README repeats this set-up.)

### Learned the hard way

**Hash iteration order is a platform detail — never let it reach the `Rng`.** Kings shuffled a
`HashSet`'s `toList()`. The JVM walks small integers ascending, Kotlin/Wasm in insertion order, so
the same seed carved a different board in the browser: seeds and stored answers matched, regions did
not. Sorting first reproduced the JVM's order, so no Android board changed. **Any new generator code
needs the same audit** — `grep -n "HashSet\|HashMap\|toSet()\|groupBy\|distinct" puzzles/` and
follow each one to see whether its order reaches the `Rng` or a "first"/"min" pick. Probe-only
hash containers (Atoms, `MosaicTeacher`'s memo) are fine; say so in a comment.

**When the order is already baked into Android boards, replay it — don't pick a new one.** LITS
picked with the `Rng` from a `HashSet<List<Int>>` (`quadsContaining`); iterating it in insertion
order, as Wasm does, changed all 1095 boards of a year. List hashes collide enough that plain
"sort by bucket" is wrong too: buckets reach nine deep, which on the JVM doubles a small table early
or turns a bucket into a red-black tree bin. `core/JvmHashOrder.kt` replays `java.util.HashMap`
exactly and `JvmHashOrderTest` diffs it against the real `HashSet`.

**Wasm is not what makes a generator slow.** Mosaic and Atoms Expert ran within 1.2-1.5x of the warm
JVM; the search itself was the cost. Both were sped up with changes that leave every node and budget
count alone (Atoms precomputes which pairs cross; Mosaic skips the two BFS when the colour bound
already prunes), so no board moved. Profile on the JVM first. LITS was the exception to the
headline, not the rule: ~2.5x (Chromium) to ~4x (WebKit) the warm JVM, because it was allocation —
`List<Int>` quads, `listOf` per 2x2 check, a `HashSet` per connectivity test, and a fresh recursive
walk per candidate lookup. On `Long` bitmasks over a per-size table of every placement it is ~20x
faster on the JVM and ~35x in Chromium, with every board byte-identical, by the same rule as Mosaic
and Atoms: each `Rng` draw and every list it picks from keep their order (`quadsContaining`'s walk
is run once per square and *filtered*, which keeps the HashSet insertion order `JvmHashOrder`
replays; the replay only runs when there are two or more candidates, but `nextInt(1)` is still
drawn), and the search visits the same nodes, so `NODE_BUDGET` truncates exactly where it did.
The proof is `LitsYearDumpTest` before and after, plus the wasm `?dump&range=730` in both browsers.

**Compose web reads `TouchEvent`s for fingers, not `PointerEvent`s.** Synthetic `pointerdown` with
`pointerType: 'touch'` does nothing. Playwright's `touchscreen.tap` works in WebKit; for a touch
drag, dispatch `TouchEvent`s built with `document.createTouch`/`createTouchList` (WebKit on Linux has
no `Touch` constructor). In Chromium, CDP `Input.dispatchTouchEvent` is real touch.

**Compose's touch slop on the web is far wider than Android's** (over 12px, under 20px; Android's is
8dp). `detectDragGestures`' plain `onDragStart(offset)` gives that slop-crossing point, not the
down, so a gesture that must *start on* something small misses: no Atoms Expert drag could ever
begin on an atom. Use the overload whose `onDragStart` receives the `down` change. A touch-drag test
with small steps hides this; step 10px+ per move.

**Animations belong to a scope that outlives the state that started them.** Pipes' spins ran as
children of `LaunchedEffect(s.cells)`; every tap restarts that effect, so a tap on one tile
cancelled another's turn mid-way and left it frozen at a slant (on Android too). They now run in a
`rememberCoroutineScope`.

**Headless WebKit is not Safari.** With `is_mobile` on, Playwright's Linux WebKit loses WebGL from
screenshots — emulate the phone with `has_touch` and `device_scale_factor` instead. It dropped
Compose's *first* frame until the first touch (`nudgeFirstFrame()` in `Main.kt` asks for a spare
one), does not repaint after a screen change until something prompts a frame, and sometimes skips an
animation's last frame; harnesses dispatch a `resize` after each tap and before a settled
screenshot. Whether real Safari needs any of this is unknown — check on an iPhone before removing
`nudgeFirstFrame()`.

**The shared browser pane.** Several sessions drive the one browser pane at once, so always pass
a `tabId`, and pick a local server port of your own. The hidden pane needs a `resize` event to
repaint after a tap, like headless WebKit.

**Harness odds and ends.** Playwright's sync API only delivers console events while it is inside a
Playwright call, so wait with `page.wait_for_timeout`, never `time.sleep`. `pkill -f "http.server
8775"` matches the shell running it; write `http[.]server`. When several worktrees build at once,
one `./gradlew --stop` stops every daemon on the machine and kills the others' builds mid-run — use
`--no-daemon`, or a private `-Dorg.gradle.daemon.registry.base=<own dir>`.

## Tests

About 350 of them. New tests should be **independent of the code they check** — Mambo, LITS, Kings,
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
  the branch, open the PR and merge it once CI's `build` is green, without asking first. The owner
  has authorised this for good and is tired of repeating it. Only stop and ask if CI fails in a way
  you cannot fix, or the change touches something under "Settled — do not reopen".
- Never commit to `main`. Branch, PR, merge — I do the merging, not the owner. This is now enforced: a
  repository ruleset ("Protect main", set up 2026-10-02) requires a pull request, requires the CI
  check named `build` to pass, and blocks force-pushes and deletion. The repo owner's account can
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
  `ci.yml` on their branch; a dispatched run posts a `build` commit status because its check run
  alone does not satisfy the ruleset. Keep those steps in `ci.yml` and the job named `build`.
- Commits use `4845431+joebywan@users.noreply.github.com`. The personal address must never reach
  a commit or a remote. No `Co-Authored-By: Claude` trailer.
- Push to `main` builds a signed APK and publishes a GitHub Release automatically.
- **That release now reaches Google Play automatically, so a merge to `main` ships.** After the GitHub
  Release is made, `release.yml` calls `publish-play.yml`, which uploads the signed `.aab` and rolls it
  out on the `alpha` track (Play's "Closed testing - Alpha"; the owner does not use internal testing).
  The version code is the workflow run number, so it rises on its own. Google reviews every update
  before testers or users see it, so "released" means uploaded and in review, not live. The upload
  authenticates with the `PLAY_SERVICE_ACCOUNT_JSON` secret, a service account allowed to release to
  testing tracks only. Test as if every merge reaches users, because it will: the owner has accepted
  that a free puzzle app carries little risk and has asked for hands-off rollout, on the footing that
  testing is Claude's responsibility.
  - A manual upload is Actions > "Publish to Google Play" with a tag and a track.
  - A personal developer account needs a closed test (12 testers opted in for 14 continuous days) before
    Google grants production access. When it does: move the default to `production`, roll out in stages,
    and give the service account the production permission in Play Console. Status: `docs/TODO.md`.
  - Saved games are the real danger: they are serialised by class name, and a bad build cannot be undone
    on someone's phone. Treat any change to a `PuzzleState`, a serialised class or `DataStoreKeyValueStore`
    as one that must load an old save.
- Signing key: `~/Documents/github/Claude/daybook-android-signing/` — the only readable copy.
  Certificate pinned in `android/release-key.sha256`; `tools/verify-apk.sh` fails a release whose
  certificate, name or debuggable flag is wrong. A green Gradle build is **not** proof of signing.

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
- Accessibility is knowingly absent and deliberately deferred while this is sideloaded.
- The app is locked to portrait. Landscape broke most boards and the owner does not want it;
  do not design for it.
- If this reaches the Play Store, the developer name and contact details the listing publishes are
  acceptable to the owner ("I don't care about my name being on the playstore"). Not a blocker.
- `gradle/actions` stays on v5. v6 requires accepting Gradle's Terms of Use for a proprietary
  caching component, and the owner has declined for now; `renovate.json` holds it back.

## Open

Accessibility — eight boards use raw pointer input and expose no click
actions, so a screen reader cannot operate them.

**The full list of outstanding work is `docs/TODO.md`.** Keep it current as you work: add what you
find but are not fixing, delete what you finish, in the same PR.
