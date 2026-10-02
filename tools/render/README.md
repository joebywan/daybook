# Rendering harness

`render.py` screenshots the **web build** of Daybook in headless Chromium and WebKit at phone sizes,
in light and dark, so "verify by rendering" (CLAUDE.md) is one command instead of a harness rebuilt
every session. It is a developer tool: nothing in the build or CI runs it.

## Set-up (once, nothing global, no sudo)

```bash
python3 -m venv /tmp/pwenv && . /tmp/pwenv/bin/activate
pip install playwright pillow numpy
python -m playwright install webkit chromium     # skip its install-deps step; it needs sudo
```

Pillow and numpy are used to tell a painted frame from a blank one and for `--measure`.
The script checks for all of this and says what is missing (exit status 2).

### WebKit on Linux

Ubuntu 24.04 lacks one shared library WebKit needs, `libavif16`. If `webkit` fails to launch with
"error while loading shared libraries", copy it in (the launcher overwrites `LD_LIBRARY_PATH`, which
is why it goes into the browser's own `sys/lib`):

```bash
apt download libavif16 && for d in libavif16*.deb; do dpkg -x $d root; done
cp root/usr/lib/x86_64-linux-gnu/lib{avif,yuv,gav1}.so* ~/.cache/ms-playwright/webkit-*/minibrowser-wpe/sys/lib/
```

## Use

Build the distribution (Java 17, see CLAUDE.md), then:

```bash
./gradlew :web:wasmJsBrowserDistribution
python tools/render/render.py                                # every puzzle x 4 sizes x 2 schemes x 2 browsers
python tools/render/render.py -p pipes -p sudoku -s 375x537 -b webkit --scheme dark
python tools/render/render.py -p mosaic -t expert -s 390x664 --measure
python tools/render/render.py --home                         # the home grid
python tools/render/render.py --list                         # puzzle ids, from PuzzleRegistry
```

Defaults: all puzzles, tier `standard`, sizes 390x844 390x664 360x640 375x537, both browsers, both
schemes, `?date=2026-06-15` (fixed so boards reproduce), device scale 2, touch on. A full default run
is 11 x 4 x 2 x 2 = 176 screenshots; narrow it. Output goes to `$TMPDIR/daybook-render/` (`-o` to
change; keep it out of the repo tree) as `<puzzle>-<tier>-<W>x<H>-<scheme>-<browser>.png`.
`--dist` points at a different build.

Each page is served from a private port on 127.0.0.1 (chosen by the OS, with `.wasm` served as
`application/wasm`) that is shut down on exit. Each viewport/scheme gets a fresh browser context, so
every board is a first visit with empty `localStorage`: the walkthrough offer line is showing, as it
would for a new player. The service worker is blocked so a rebuilt dist is never masked by a cached one.

### How it waits

After `load` it waits for the `#loading` note to go, then repeatedly dispatches `resize`, waits two
animation frames and screenshots, until three consecutive frames are byte-identical and not a flat
colour. That covers the first-frame drop and the no-repaint-after-a-screen-change quirks of headless
WebKit, and board generation (which on the web paints "Setting out ..." first). It uses Playwright's
own waits, never `time.sleep`, because Playwright's sync API only delivers console events inside a
Playwright call.

### Exit status

0 if every screenshot was taken, settled and non-blank with no page error and no console error;
1 otherwise, with each problem listed; 2 for a missing dependency, browser or build.

### `--measure`

Compose draws into a canvas, so there is no DOM to ask for the board's box. Measurement is pixel
analysis: the commonest colour on the screenshot's outer ring is the page background; rows containing anything else are grouped
into vertical bands (rows less than 14 CSS px apart join, so a board's own gaps do not split it);
the band with the largest bounding box is reported as the board. All bands are printed too (header,
board, popover, toolbar), in CSS px. It is a heuristic, but a stable one for "did the board's box
change?" because the same board gives the same band. Compare two runs' lines, or use
`--tap X,Y` (repeatable, CSS px, in order) to move and print the box before and after
(`<name>-after.png`). A board closer than 14 CSS px to its neighbours (Mosaic under its caption,
Sets) is reported with them, Tower's is only its `LazyColumn` strip, and a popover that overlaps the
board merges with it, so compare like with like (same puzzle, tier, size, state) rather than reading
the absolute numbers as the board's exact box. Across browsers and schemes the same board measures
identically, which is the check that the heuristic is stable. A page that never goes still (the
running clock, a hint pulse) is screenshot at the last frame with a note, not an error.

### `--storage`

`--storage saved.json` seeds `localStorage` (a JSON object of `{"file.key": "value"}`, the format
described under "Storage" in CLAUDE.md) before the page loads. There is no `--solved` flag: producing
a solved state needs each puzzle's answer, which is per-puzzle code. To screenshot a solved board,
solve one by hand in a browser, copy its `localStorage` entries into a JSON file, and pass that
(open the board with the same `--date` and tier).

## Known limits

- Headless WebKit on Linux is the nearest stand-in for iPhone Safari, not Safari. See CLAUDE.md
  "Headless WebKit is not Safari".
- Touch drags are not scripted. Compose reads `TouchEvent`s, not `PointerEvent`s: use
  `page.touchscreen.tap` (works in WebKit), CDP `Input.dispatchTouchEvent` in Chromium, or build
  `TouchEvent`s with `document.createTouch` in WebKit, and step 10px+ per move (CLAUDE.md, web section).
- Pass a high enough `--timeout` if the machine is busy; Mosaic and LITS Expert are the slow boards.
