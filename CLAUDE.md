# Daybook — working notes for Claude

A daily logic-puzzle Android app. Eleven puzzles, generated on device, no ads, no
subscription, no network. Read this before changing anything; it exists so you don't
rediscover what has already been learned here the hard way.

## Build

Gradle **requires Java 17**. The system default is Java 25 and Gradle refuses to run on it.

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
  eleven of them draw on every composition.

## Teaching: hints that explain, and walkthroughs

All eleven puzzles teach. The shared pieces are `core/Teaching.kt` (the contract), `ui/teach/Hints.kt`
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
  emitted state** — unless the frame sets `passes`, which admits the intermediate states of a
  several-step move (Sets' three taps, Mambo's second tap, Snap's drag) without ending the frame.
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
  the half rule decides, ties below. Overlap with the highlight weighs 4x overlap with a control the
  player needs (`keepClear`: Sudoku's digit pad, Mosaic's palette, Tower's peg and swatch rows). A
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
- The solved card is drawn over the toolbar's own box (the toolbar stays laid out, hidden), one row
  tall, so completing a puzzle cannot resize the board either.
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
| Sudoku | full house, hidden/naked singles, locked candidates, pairs, a bounded what-if chain; every step ends in a placement (no pencil marks) | 0.5 / 6 / 34% (1% of Expert steps: Expert is dug for uniqueness, not rated) |
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

Adopting it, or changing a teacher — the lessons of eleven of them:

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

## Rules that keep being relearned

**Never ship a board the generator has not proved.** Kings, LITS, Mosaic and Shikaku each had a
fallback that was reachable and unvetted, and in three of them that fallback was what players
actually got. LITS's real generator had *never once run*. When you add a fallback, ask what
fraction of seeds reach it, and measure rather than assume. Size the test's sample to the rate:
Atoms once fell back on 13 of 365 Expert days (3.6%), which `FallbackTest`'s twenty seeds miss
about half the time; its Atoms test now walks a full year of daily seeds on every tier (~2 s).
Kings and Atoms expose `generateVerified` (null when nothing was proved), which is what tests assert.

**A truncated search is not a proof.** LITS reported "gave up" as "exactly one solution" because
its node budget returned quietly. Make the distinction structural — a type where only the proved
case carries a result — not a count a caller can misread.

**`solved` must check the rules, not compare to a stored answer.** Kings and LITS both rejected
correct solutions this way. Keep the stored solution for hints; let a validator decide success.

**Size layouts from both axes.** Three home-screen motifs and the Sets board each drew outside
their box because height fell out of width via `aspectRatio` and nothing consulted the height
available. `Mosaic.Board` has the right shape: `minOf(maxWidth / w, maxHeight / h)`. Seven more boards
sized from width alone until the taller hint slot ran Pipes' 5x7 board a row under the panel at
390dp, and Sudoku over the header on a 693dp-tall screen; all eleven now consult the height
(audited at 390x844, 390x664 and 360x640: every grid is within a cell of the box on its binding axis,
and the same size before and after solving).

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
impossible squares are recomputed per render. Storing them means owning which to retract when a
piece is lifted, which is where the feature rots.

**Verify by rendering, not reasoning.** Icons, motifs, crescents, pipe joints and crosses have
all failed at true size in ways nobody predicted — a crown read as a comb, pages as a boat hull,
Atoms as a wireframe. A Java2D harness driving the real geometry is the established approach.
Then check on the emulator; several bugs only appeared there.

**README screenshots go stale silently.** `docs/screenshots/*.png` are emulator captures at half
scale (540x1200), taken on a *clean install* so the home screen shows a 0-day streak and unplayed
boards rather than whatever the session happened to leave behind. Nothing checks them, so a change
to a board's look or to the home grid means recapturing them in the same pass — the home shot in
particular names the puzzle count in its alt text.

**Write agent patches early.** One agent lost a complete implementation by leaving
`git diff --cached > patch` until the end and dying on a rate limit.

## Web build (`web/`)

The whole app — all eleven puzzles, home, play, archive, stats, walkthroughs, saves — in a
browser, via Compose Multiplatform 1.9.3 on Kotlin/Wasm (`wasmJs`) with the repo's Kotlin 2.2.10.
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
- **Generation on one thread.** Android generates off the main thread (`generateBoard` in the seam
  is the `withContext(Dispatchers.Default)` it always was). Wasm has one thread, so the web's
  `generateBoard` first waits until "Setting out …" has been *painted* (`requestAnimationFrame` →
  `setTimeout`), and `prepareBoards` — called by `DaybookApp` while Home is showing, a no-op on
  Android — makes today's eleven boards at the grid's tier in advance, one per turn of the event
  loop, into a small cache. A tap usually finds its board ready. A board already underway cannot be
  interrupted, so a tap during a slow pre-generation still waits for it.
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
  lets a harness run the eleven in parallel pages; `&times` adds a `TIME <id> <date> <tier> <ms>`
  line per board, for medians and percentiles.
- **Parity:** `DAYBOOK_PARITY_DUMP=<file> ./gradlew :app:testDebugUnitTest --tests
  '*WebParityDumpTest*'` writes the JVM's year in the same order; strip `RANGE ` from the page's
  lines and the files must be identical. The same file, taken before and after a change, is how
  Android boards are proved unchanged. `WebParityTest`, `LitsWebParityTest`,
  `MosaicAtomsWebParityTest`, `WebParityShikakuSnapSudokuTest` and
  `WebParityMamboPipesSetsTowerTest` pin a few boards per puzzle outright. For LITS alone,
  `DAYBOOK_LITS_DUMP=<file>` (`DAYBOOK_LITS_DAYS`, default 730) runs `LitsYearDumpTest`: the same
  lines over two years plus per-board JVM times in `<file>.times`.
- **Timings** (a year's worst board per tier, Chromium, eleven pages sharing the CPU): Mosaic
  Expert 0.3 s, Snap 0.4 s, Sudoku Expert 0.1 s, the rest under 0.1 s. LITS was 1.9 / 3.2 / 4.5 s
  (WebKit up to 11 s) until its generator moved to bitmasks; over 2026-2027 its worst board is now
  46 / 75 / 93 ms in Chromium and 52 / 80 / 99 ms in WebKit, alone (see below).
  The no-connectivity re-proof (and the retries it causes for the ~30% of boards that needed one)
  raised the JVM mean per board from 5 / 9 / 13 ms to 9 / 11 / 17 ms and the worst of 2026-2027 from
  41 / 62 / 85 ms to 57 / 69 / 118 ms; those browser figures predate it and were not re-measured.
- Dark theme follows `prefers-color-scheme`; Playwright's `color_scheme="dark"` context option is
  enough to screenshot it.

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

About 200 of them. New tests should be **independent of the code they check** — Mambo, LITS, Kings,
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

- Never commit to `main`. Branch, PR, merge — I do the merging, not the owner.
- Commits use `4845431+joebywan@users.noreply.github.com`. The personal address must never reach
  a commit or a remote. No `Co-Authored-By: Claude` trailer.
- Push to `main` builds a signed APK and publishes a GitHub Release automatically.
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

## Open

Sudoku pencil marks; accessibility — eight boards use raw pointer input and expose no click
actions, so a screen reader cannot operate them.

**The full list of outstanding work is `docs/TODO.md`.** Keep it current as you work: add what you
find but are not fixing, delete what you finish, in the same PR.
