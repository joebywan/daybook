#!/usr/bin/env python3
"""Diff the browser's boards against the JVM's, in Chromium and WebKit.

The web build compiles the same generators as Android (CLAUDE.md, "Web build"), and a board that
differs between the two engines is a bug that nothing else catches: the per-puzzle parity tests pin
only a few boards. This loads the built wasm distribution with `?dump&range=N&puzzle=<id>`, collects
the `RANGE <fingerprint>` console lines until `DUMP DONE`, and compares them with the JVM's
`WebParityDumpTest` output, line for line.

    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk
    ./gradlew :web:wasmJsBrowserDistribution
    DAYBOOK_PARITY_DAYS=60 DAYBOOK_PARITY_DUMP=/tmp/jvm.txt \\
        ./gradlew :app:testDebugUnitTest --tests '*WebParityDumpTest*'
    python3 tools/web-parity/check.py --jvm /tmp/jvm.txt

The range is read from the JVM file (lines per puzzle / 3 tiers), so the two cannot disagree about
it. One page per puzzle, all at once: a year is eleven independent pages, and the slowest puzzle sets
the pace. Exits 0 when every line of every puzzle matches in every browser; 1 on any difference,
missing line, page error, crash or timeout; 2 on bad inputs.

Needs `pip install playwright` and `python -m playwright install chromium webkit` (add
`--with-deps` on a fresh Linux machine). Only the standard library otherwise.
"""
import argparse
import asyncio
import functools
import http.server
import socketserver
import sys
import threading
import time
from pathlib import Path

TIERS = 3  # Standard, Hard, Expert: the order the JVM file and the page both use


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, ".wasm": "application/wasm"}

    def log_message(self, *args):  # keep the report readable
        pass


def serve(directory: Path) -> tuple[socketserver.TCPServer, int]:
    class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
        daemon_threads = True

        def handle_error(self, request, client_address):  # a page closed mid-download: not news
            pass

    server = Server(("127.0.0.1", 0), functools.partial(Handler, directory=str(directory)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, server.server_address[1]


def read_expected(path: Path) -> dict[str, list[str]]:
    expected: dict[str, list[str]] = {}
    for line in path.read_text().splitlines():
        if line:
            expected.setdefault(line.split(" ", 1)[0], []).append(line)
    return expected


def first_difference(a: str, b: str) -> str:
    """Where two fingerprint lines part, with a little context either side."""
    i = next((k for k in range(min(len(a), len(b))) if a[k] != b[k]), min(len(a), len(b)))
    lo = max(0, i - 30)
    return f"column {i}\n      jvm: ...{a[lo:i + 40]}\n      web: ...{b[lo:i + 40]}"


def describe(line: str) -> str:
    parts = line.split(" ", 3)
    return " ".join(parts[:3]) if len(parts) >= 3 else line[:60]


def compare(puzzle: str, want: list[str], got: list[str], limit: int) -> list[str]:
    problems: list[str] = []
    if len(got) != len(want):
        problems.append(f"{puzzle}: the page printed {len(got)} RANGE lines, the JVM file has {len(want)}")
    shown = 0
    bad = 0
    for k, (w, g) in enumerate(zip(want, got)):
        if w != g:
            bad += 1
            if shown < limit:
                shown += 1
                problems.append(
                    f"{puzzle}: line {k + 1} differs: {describe(w)}\n    first difference at {first_difference(w, g)}"
                )
    if bad > shown:
        problems.append(f"{puzzle}: {bad - shown} more differing lines not shown ({bad} of {len(want)} differ)")
    return problems


async def dump_one(browser, base: str, puzzle: str, days: int, timeout: float, errors: list[str]) -> list[str] | None:
    """One page: load ?dump for one puzzle and return its RANGE lines (None on failure)."""
    context = await browser.new_context()
    page = await context.new_page()
    lines: list[str] = []
    done = asyncio.Event()

    def on_console(msg):
        text = msg.text
        if text.startswith("RANGE "):
            lines.append(text[len("RANGE "):])
        elif text == "DUMP DONE":
            done.set()

    # Console errors are not failures: the Compose app starts after the dump and headless WebKit
    # logs WebGL complaints about it. An exception in the dump itself is an uncaught `pageerror`
    # (or no DUMP DONE), and both fail.
    page.on("console", on_console)
    page.on("pageerror", lambda e: errors.append(f"{puzzle}: page error: {str(e)[:300]}"))
    page.on("crash", lambda: (errors.append(f"{puzzle}: the page crashed"), done.set()))
    try:
        await page.goto(f"{base}/index.html?dump&range={days}&puzzle={puzzle}", wait_until="commit")
        await asyncio.wait_for(done.wait(), timeout)
    except asyncio.TimeoutError:
        errors.append(f"{puzzle}: no DUMP DONE within {timeout:.0f}s ({len(lines)} of {days * TIERS} RANGE lines seen)")
        return None
    except Exception as e:  # noqa: BLE001 - report any browser failure as one
        errors.append(f"{puzzle}: {type(e).__name__}: {str(e)[:300]}")
        return None
    finally:
        await context.close()
    return lines


async def check_browser(pw, name: str, base: str, expected: dict[str, list[str]], days: int, timeout: float, limit: int) -> list[str]:
    browser = await getattr(pw, name).launch()
    started = time.monotonic()
    errors: list[str] = []
    try:
        results = await asyncio.gather(*(dump_one(browser, base, p, days, timeout, errors) for p in expected))
    finally:
        await browser.close()
    problems = list(errors)
    for puzzle, got in zip(expected, results):
        if got is not None:
            problems += compare(puzzle, expected[puzzle], got, limit)
    status = "FAIL" if problems else "ok"
    print(f"[{name}] {status}: {len(expected)} puzzles x {days} days x {TIERS} tiers in {time.monotonic() - started:.0f}s", flush=True)
    return [f"[{name}] {p}" for p in problems]


async def main_async(args, expected: dict[str, list[str]], days: int) -> int:
    from playwright.async_api import async_playwright

    server, port = serve(args.dist)
    base = f"http://127.0.0.1:{port}"
    problems: list[str] = []
    try:
        async with async_playwright() as pw:
            # One browser at a time: eleven wasm pages already saturate a CI runner's cores, and
            # two browsers at once would only make both slower and the timeout less meaningful.
            for name in args.browsers:
                problems += await check_browser(pw, name, base, expected, days, args.timeout, args.show)
    finally:
        server.shutdown()
    if problems:
        print("\nWEB PARITY FAILED\n", file=sys.stderr)
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"web parity holds: {sum(map(len, expected.values()))} boards identical in {', '.join(args.browsers)}")
    return 0


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--jvm", type=Path, required=True, help="file written by WebParityDumpTest (DAYBOOK_PARITY_DUMP)")
    ap.add_argument("--dist", type=Path, default=root / "web/build/dist/wasmJs/productionExecutable",
                    help="the built wasm distribution (default: %(default)s)")
    ap.add_argument("--browsers", default="chromium,webkit", help="comma-separated (default: %(default)s)")
    ap.add_argument("--timeout", type=float, default=900, help="seconds each page may take (default: %(default)s)")
    ap.add_argument("--show", type=int, default=5, help="differing lines to show per puzzle (default: %(default)s)")
    args = ap.parse_args()
    args.browsers = [b for b in args.browsers.split(",") if b]

    if not (args.dist / "index.html").is_file():
        print(f"no index.html in {args.dist}: build it with ./gradlew :web:wasmJsBrowserDistribution", file=sys.stderr)
        return 2
    if not args.jvm.is_file():
        print(f"{args.jvm} does not exist: run WebParityDumpTest with DAYBOOK_PARITY_DUMP set", file=sys.stderr)
        return 2
    expected = read_expected(args.jvm)
    sizes = {len(v) for v in expected.values()}
    if not expected or len(sizes) != 1 or sizes.pop() % TIERS:
        print(f"{args.jvm} is not a parity dump (puzzles: {[(k, len(v)) for k, v in expected.items()]})", file=sys.stderr)
        return 2
    days = len(next(iter(expected.values()))) // TIERS
    print(f"JVM dump: {len(expected)} puzzles x {days} days x {TIERS} tiers; browsers: {', '.join(args.browsers)}", flush=True)
    return asyncio.run(main_async(args, expected, days))


if __name__ == "__main__":
    sys.exit(main())
