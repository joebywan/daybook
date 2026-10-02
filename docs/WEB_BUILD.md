# Web build (`web/`)

Moved out of CLAUDE.md. Read before touching `web/`, the platform seam, or anything shared that the wasm build compiles.


The whole app — all puzzles, home, play, archive, stats, walkthroughs, saves — in a
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
  Android — makes today's boards for every puzzle at the grid's tier in advance, one per turn of the event
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
  lets a harness run every puzzle in parallel pages; `&times` adds a `TIME <id> <date> <tier> <ms>`
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
  you: it serves the built dist, loads `?dump&range=N&puzzle=<id>` for all puzzles at once in
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
- **Timings** (a year's worst board per tier, Chromium, one page per puzzle sharing the CPU): Mosaic
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
