#!/usr/bin/env python3
"""Screenshot the Daybook web build in real browsers, at phone sizes, in both colour schemes.

"Verify by rendering" (CLAUDE.md) is how layout bugs in this app have been found, and every session
used to rebuild this harness. It serves a built web distribution on a private port, opens each
puzzle's daily board with the app's own query hooks (?puzzle= ?tier= ?date=), waits until Compose has
actually painted, and writes one PNG per combination:

    <puzzle>-<tier>-<W>x<H>-<scheme>-<browser>.png

Usage (see tools/render/README.md for set-up):

    ./gradlew :web:wasmJsBrowserDistribution                       # once, with Java 17
    python3 tools/render/render.py                                 # all puzzles, all sizes, both browsers
    python3 tools/render/render.py -p pipes -p sudoku -s 375x537 -b webkit --scheme dark
    python3 tools/render/render.py --home -s 390x844               # the home grid instead of a board
    python3 tools/render/render.py --measure -p snap -t standard   # also print the board's bounding box

Nothing global is touched: Playwright is imported from whatever Python runs this (a venv; see the
README), the server binds 127.0.0.1 on a free port and dies with the script, and the default output
directory is under the system temp dir, never the repo.

Exit status is 0 only if every screenshot was taken, none was blank, and no page raised an error or
logged a console error. Anything else is listed at the end and gives status 1; a missing dependency
is status 2.
"""
from __future__ import annotations

import argparse
import functools
import http.server
import json
import re
import sys
import tempfile
import threading
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_DIST = REPO / "web" / "build" / "dist" / "wasmJs" / "productionExecutable"
DEFAULT_OUT = Path(tempfile.gettempdir()) / "daybook-render"

# The sizes CLAUDE.md audits layouts at: a tall phone, two shorter ones, and an iPhone with both
# Safari toolbars showing (375x537), which is what exposed the board shrinking under the old hint slot.
DEFAULT_SIZES = ["390x844", "390x664", "360x640", "375x537"]
TIERS = ["standard", "hard", "expert"]
BROWSERS = ["chromium", "webkit"]
SCHEMES = ["light", "dark"]
DEVICE_SCALE = 2
AFTER_TAP_SETTLE_S = 8

# Console noise that is not the app's fault. Matched against the message text.
IGNORED_CONSOLE = [
    re.compile(r"favicon", re.I),
    # Kotlin/Wasm's own loader, on every page of the current toolchain.
    re.compile(r"Accessing `memory` via `wasmExports` is deprecated"),
    # Headless WebKit on Linux (software GL) logs a stream of these from Skia's WebGL probing,
    # on every page, whether or not anything is wrong. Chromium logs none, so they are only
    # ignored when they start with the "WebGL:" prefix WebKit gives them.
    re.compile(r"^WebGL: "),
]


# ---------------------------------------------------------------------------------------------
# Puzzle ids, read from the code so a new puzzle is picked up without editing this file.
# ---------------------------------------------------------------------------------------------

def puzzle_ids() -> list[str]:
    """The ids of every puzzle in core/PuzzleRegistry.kt, in home-grid order."""
    core = REPO / "app" / "src" / "main" / "java" / "com" / "joebywan" / "daybook"
    try:
        registry = (core / "core" / "PuzzleRegistry.kt").read_text()
        body = registry.split("val all: List<PuzzleType> = listOf(", 1)[1].split(")", 1)[0]
        names = [n.strip() for n in body.split(",") if n.strip()]
        ids = []
        for name in names:
            source = (core / "puzzles" / f"{name}.kt").read_text()
            ids.append(re.search(r'override val id\s*=\s*"([a-z0-9_]+)"', source).group(1))
        if ids:
            return ids
    except (OSError, IndexError, AttributeError):
        pass
    sys.exit("could not read puzzle ids from core/PuzzleRegistry.kt; pass them with -p")


# ---------------------------------------------------------------------------------------------
# Static server
# ---------------------------------------------------------------------------------------------

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    # Streaming instantiation of the wasm needs application/wasm; newer Pythons know it, older
    # ones do not, so say it outright.
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".wasm": "application/wasm",
        ".js": "text/javascript",
        ".webmanifest": "application/manifest+json",
    }

    def log_message(self, *_args):
        pass


class Server:
    """http.server on 127.0.0.1, port chosen by the OS, torn down on exit."""

    def __init__(self, directory: Path):
        handler = functools.partial(QuietHandler, directory=str(directory))
        self.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.httpd.server_address[1]}/"

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *_exc):
        self.httpd.shutdown()
        self.httpd.server_close()


# ---------------------------------------------------------------------------------------------
# Dependencies
# ---------------------------------------------------------------------------------------------

SETUP_HINT = """\
Set-up (once; nothing here needs sudo):
    python3 -m venv /tmp/pwenv && . /tmp/pwenv/bin/activate
    pip install playwright pillow numpy
    python -m playwright install webkit chromium        # skip its install-deps step; it needs sudo
then run this script with that venv's python. WebKit may also need libavif; see tools/render/README.md."""


def need_playwright():
    try:
        from playwright.sync_api import sync_playwright  # noqa: F401
    except ImportError:
        print("error: the Python package 'playwright' is not installed for this interpreter "
              f"({sys.executable}).\n{SETUP_HINT}", file=sys.stderr)
        sys.exit(2)


def launch(p, name: str):
    """Launch a browser, turning the usual failures into an instruction instead of a stack trace."""
    engine = getattr(p, name)
    exe = Path(engine.executable_path)
    if not exe.exists():
        print(f"error: {name} is not installed for Playwright (expected {exe}).\n{SETUP_HINT}", file=sys.stderr)
        sys.exit(2)
    try:
        return engine.launch()
    except Exception as e:  # noqa: BLE001 - playwright raises a bare Error with the log inside
        text = str(e)
        hint = ""
        if name == "webkit" and ("libavif" in text or "error while loading shared libraries" in text):
            hint = ("\nWebKit is missing a shared library. On Ubuntu 24.04 it is libavif16: see "
                    "'WebKit on Linux' in tools/render/README.md for the no-sudo copy into the "
                    "browser's sys/lib.")
        print(f"error: could not launch {name}:\n{text}{hint}", file=sys.stderr)
        sys.exit(2)


# ---------------------------------------------------------------------------------------------
# Waiting for a paint
# ---------------------------------------------------------------------------------------------

def frame(page):
    """Two animation frames after a `resize`. The hidden-pane / headless-WebKit quirk (CLAUDE.md,
    "Headless WebKit is not Safari") is that Compose does not repaint after a screen change until
    something prompts it; a resize event is what prompts it."""
    page.evaluate(
        "() => new Promise(r => { window.dispatchEvent(new Event('resize'));"
        " requestAnimationFrame(() => requestAnimationFrame(() => r())); })"
    )


def blank_fraction(png: bytes) -> float:
    """Fraction of pixels that are not the most common colour (0 = a flat, empty page)."""
    import io
    import numpy as np
    from PIL import Image

    a = np.asarray(Image.open(io.BytesIO(png)).convert("RGB"))
    flat = (a[..., 0].astype(np.uint32) << 16) | (a[..., 1].astype(np.uint32) << 8) | a[..., 2]
    _, counts = np.unique(flat, return_counts=True)
    return 1.0 - counts.max() / flat.size


def settle(page, timeout_s: float, stable_for: int = 3):
    """Wait until the page has painted something and then stopped changing.

    The loading note is removed once Compose starts; after that the board is generated (on the web,
    after "Setting out ..." has been painted), so a single early screenshot is often the loading
    screen. We screenshot at intervals, prodding a repaint each time, and return when `stable_for`
    consecutive frames are byte-identical and not blank.

    Returns (png, settled). A page that never goes still (the hint highlight pulses) is returned
    with settled=False once the time is up - painted, so usable, but the caller should say so. A
    page that is still blank at the deadline raises TimeoutError.
    """
    deadline = time.monotonic() + timeout_s
    page.wait_for_function("() => !document.getElementById('loading')", timeout=int(timeout_s * 1000))
    last, same = None, 0
    while True:
        frame(page)
        png = page.screenshot()
        painted = blank_fraction(png) > 0.01
        if png == last and painted:
            same += 1
            if same >= stable_for - 1:
                return png, True
        else:
            same = 0
        last = png
        if time.monotonic() >= deadline:
            if painted:
                return png, False
            raise TimeoutError(f"page was still blank after {timeout_s:.0f}s")
        page.wait_for_timeout(250)  # Playwright's own wait, so console events are still delivered


# ---------------------------------------------------------------------------------------------
# Measuring
# ---------------------------------------------------------------------------------------------

def measure(png: bytes, scale: int = DEVICE_SCALE, tolerance: int = 10, merge_gap_css: float = 14.0):
    """Find content bands in a screenshot, in CSS px.

    Compose draws into a canvas, so there is no DOM to ask. Instead: take the most common colour as
    the page background, mark every pixel that differs from it, and group rows that have any marked
    pixel into vertical bands (rows closer than `merge_gap_css` join, so a board's own gaps do not
    split it). Headers, boards, hint popovers and toolbars come out as separate bands; the band whose
    bounding box has the largest area is reported as the board. This is a heuristic, but it is a
    stable one for the question it exists for - "did the board's box change between two states" -
    because the same board gives the same band whenever the surroundings differ only in text.
    Returns (board, bands) with each as (x, y, w, h).
    """
    import io
    import numpy as np
    from PIL import Image

    a = np.asarray(Image.open(io.BytesIO(png)).convert("RGB")).astype(np.int32)
    flat = (a[..., 0] << 16) | (a[..., 1] << 8) | a[..., 2]
    # The page background is the commonest colour on the outer ring of pixels. (The commonest colour
    # of the whole frame is wrong whenever a board's own cells cover more area than the page does.)
    ring = np.concatenate([flat[0], flat[-1], flat[:, 0], flat[:, -1]])
    values, counts = np.unique(ring, return_counts=True)
    bg = values[counts.argmax()]
    bg_rgb = np.array([(bg >> 16) & 255, (bg >> 8) & 255, bg & 255])
    mask = (np.abs(a - bg_rgb).max(axis=2) > tolerance)
    rows = np.flatnonzero(mask.any(axis=1))
    if rows.size == 0:
        return None, []
    gap = int(merge_gap_css * scale)
    runs, start, prev = [], rows[0], rows[0]
    for r in rows[1:]:
        if r - prev > gap:
            runs.append((start, prev))
            start = r
        prev = r
    runs.append((start, prev))
    bands = []
    for top, bottom in runs:
        cols = np.flatnonzero(mask[top:bottom + 1].any(axis=0))
        left, right = cols[0], cols[-1]
        bands.append((left / scale, top / scale, (right - left + 1) / scale, (bottom - top + 1) / scale))
    board = max(bands, key=lambda b: b[2] * b[3])
    return board, bands


def fmt_box(b):
    return "x={:g} y={:g} w={:g} h={:g}".format(*[round(v, 1) for v in b])


# ---------------------------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------------------------

def parse_size(text: str):
    m = re.fullmatch(r"(\d+)x(\d+)", text)
    if not m:
        raise argparse.ArgumentTypeError(f"size must look like 390x844, got {text!r}")
    return int(m.group(1)), int(m.group(2))


def parse_tap(text: str):
    m = re.fullmatch(r"(\d+(?:\.\d+)?),(\d+(?:\.\d+)?)", text)
    if not m:
        raise argparse.ArgumentTypeError(f"tap must look like 120,300 (CSS px), got {text!r}")
    return float(m.group(1)), float(m.group(2))


def build_parser():
    ap = argparse.ArgumentParser(
        description=__doc__.split("\n\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Output files: <puzzle>-<tier>-<W>x<H>-<scheme>-<browser>.png (home: home-<tier>-...).")
    ap.add_argument("-p", "--puzzle", action="append", metavar="ID",
                    help="puzzle id, repeatable (default: every puzzle in PuzzleRegistry)")
    ap.add_argument("-t", "--tier", action="append", choices=TIERS, help="repeatable (default: standard)")
    ap.add_argument("-s", "--size", action="append", type=parse_size, metavar="WxH",
                    help=f"viewport in CSS px, repeatable (default: {' '.join(DEFAULT_SIZES)})")
    ap.add_argument("-b", "--browser", action="append", choices=BROWSERS, help="repeatable (default: both)")
    ap.add_argument("--scheme", action="append", choices=SCHEMES, help="repeatable (default: both)")
    ap.add_argument("--date", default="2026-06-15", metavar="YYYY-MM-DD",
                    help="the app's 'today' (?date=); default is fixed so boards are reproducible")
    ap.add_argument("--home", action="store_true", help="screenshot the home grid instead of a puzzle")
    ap.add_argument("--dist", type=Path, default=DEFAULT_DIST, help=f"built web distribution (default: {DEFAULT_DIST})")
    ap.add_argument("-o", "--out", type=Path, default=DEFAULT_OUT, help=f"output directory (default: {DEFAULT_OUT})")
    ap.add_argument("--measure", action="store_true",
                    help="print the board's bounding box (CSS px) found by pixel analysis for each screenshot")
    ap.add_argument("--tap", action="append", type=parse_tap, metavar="X,Y",
                    help="after settling, tap this CSS-px point (repeatable, in order), then settle and screenshot "
                         "again as <name>-after.png; with --measure both boxes are printed")
    ap.add_argument("--storage", type=Path, metavar="JSON",
                    help="a JSON object of localStorage entries ({\"file.key\": \"value\"}) to seed before the page "
                         "loads, e.g. a saved game copied out of a browser; this is how a solved board is shown")
    ap.add_argument("--timeout", type=float, default=60, help="seconds to wait for a page to settle (default 60)")
    ap.add_argument("--list", action="store_true", help="print the puzzle ids and exit")
    return ap


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    all_ids = puzzle_ids()
    if args.list:
        print("\n".join(all_ids))
        return 0
    puzzles = args.puzzle or all_ids
    unknown = [p for p in puzzles if p not in all_ids]
    if unknown:
        print(f"error: unknown puzzle id(s) {unknown}; known: {all_ids}", file=sys.stderr)
        return 2
    tiers = args.tier or ["standard"]
    sizes = args.size or [parse_size(s) for s in DEFAULT_SIZES]
    browsers = args.browser or BROWSERS
    schemes = args.scheme or SCHEMES
    if not (args.dist / "index.html").exists():
        print(f"error: no web build at {args.dist}\nbuild it: JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 "
              "ANDROID_HOME=$HOME/Android/Sdk ./gradlew :web:wasmJsBrowserDistribution", file=sys.stderr)
        return 2
    need_playwright()
    try:
        import numpy, PIL  # noqa: F401,E401
    except ImportError:
        print(f"error: pillow and numpy are needed (blank-frame detection).\n{SETUP_HINT}", file=sys.stderr)
        return 2
    seed = json.loads(args.storage.read_text()) if args.storage else None
    args.out.mkdir(parents=True, exist_ok=True)

    from playwright.sync_api import sync_playwright

    problems: list[str] = []
    written = 0
    targets = ["home"] if args.home else puzzles

    with Server(args.dist) as server, sync_playwright() as p:
        print(f"serving {args.dist} at {server.url}; writing to {args.out}", flush=True)
        for browser_name in browsers:
            browser = launch(p, browser_name)
            try:
                for (w, h) in sizes:
                    for scheme in schemes:
                        # A fresh context per size and scheme: empty localStorage, so every board is
                        # a first visit (the walkthrough offer line is on screen, as for a new player).
                        ctx = browser.new_context(
                            viewport={"width": w, "height": h}, device_scale_factor=DEVICE_SCALE,
                            has_touch=True, color_scheme=scheme, locale="en-US",
                            service_workers="block",  # a cached shell would hide a rebuilt dist
                        )
                        if seed:
                            ctx.add_init_script(
                                "(() => { const s = %s; for (const k in s) { try { if (localStorage.getItem(k) === null)"
                                " localStorage.setItem(k, s[k]); } catch (e) {} } })()" % json.dumps(seed))
                        try:
                            for puzzle in targets:
                                for tier in tiers:
                                    tag = f"{puzzle}-{tier}-{w}x{h}-{scheme}-{browser_name}"
                                    problems += shoot(ctx, server.url, args, puzzle, tier, w, h, tag)
                                    written += 1
                        finally:
                            ctx.close()
            finally:
                browser.close()

    print(f"\n{written} combination(s) attempted, {len(problems)} problem(s)")
    for line in problems:
        print("  PROBLEM:", line)
    return 1 if problems else 0


def shoot(ctx, base_url, args, puzzle, tier, w, h, tag) -> list[str]:
    problems: list[str] = []
    notes: list[str] = []
    page = ctx.new_page()

    def on_console(msg):
        text = msg.text
        if msg.type == "error" and not any(rx.search(text) for rx in IGNORED_CONSOLE):
            line = f"{tag}: console error: {text}"
            if line not in problems:
                problems.append(line)

    page.on("console", on_console)
    page.on("pageerror", lambda e: problems.append(f"{tag}: page error: {e}"))
    query = f"?date={args.date}&tier={tier}" + ("" if args.home else f"&puzzle={puzzle}")
    try:
        page.goto(base_url + query, wait_until="load", timeout=int(args.timeout * 1000))
        png, settled = settle(page, args.timeout)
        if not settled:
            notes.append("did not go still (animating?); screenshot is the last frame")
    except Exception as e:  # noqa: BLE001
        problems.append(f"{tag}: {type(e).__name__}: {str(e).splitlines()[0]}")
        page.close()
        return problems
    (args.out / f"{tag}.png").write_bytes(png)
    report = measure_line(tag, png) if args.measure else None
    if args.tap:
        try:
            for (x, y) in args.tap:
                page.touchscreen.tap(x, y)
                frame(page)
                page.wait_for_timeout(150)
            png2, settled2 = settle(page, AFTER_TAP_SETTLE_S)
            if not settled2:
                notes.append("after tap: did not go still (clock or hint pulse?); screenshot is the last frame")
        except Exception as e:  # noqa: BLE001
            problems.append(f"{tag}: after tap: {type(e).__name__}: {str(e).splitlines()[0]}")
        else:
            (args.out / f"{tag}-after.png").write_bytes(png2)
            if args.measure:
                report = (report or "") + "\n" + measure_line(tag + "-after", png2)
    print(report or f"ok  {tag}", flush=True)
    for note in notes:
        print(f"    note: {note}", flush=True)
    page.close()
    return problems


def measure_line(tag, png) -> str:
    board, bands = measure(png)
    if board is None:
        return f"MEASURE {tag}: nothing but background"
    others = "; ".join(fmt_box(b) for b in bands)
    return f"MEASURE {tag}: board {fmt_box(board)} | bands {others}"


if __name__ == "__main__":
    sys.exit(main())
