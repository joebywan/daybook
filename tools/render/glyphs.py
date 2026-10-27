#!/usr/bin/env python3
"""Fails if a web screen that draws a symbol outside Latin-1 makes any request off the local server.

Compose fetches a Noto font from fonts.gstatic.com for a character no bundled font has; the CSP
blocks it and the glyph draws as a box. This opens each screen that draws one (WordWheel ↻ ✕,
Mini and the streak calendar ‹ ›, Target −, the share previews' shape marks), in Chromium and
WebKit at 375x537, and writes a screenshot of each to the output directory. The walkthrough's ✓
needs a move made first, so it is not here. Set-up and options are render.py's.

    python3 tools/render/glyphs.py [out-dir [dist-dir]]
"""
import json
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).parent))
import render as R  # noqa: E402

PAGES = ["?puzzle=wordwheel", "?puzzle=mini", "?puzzle=target", "?puzzle=dominoes", "?screen=stats",
         "?screen=stats&share", "?puzzle=tower&finish=0&share", "?puzzle=pointers&finish=0&share"]
BAD_CONSOLE = ("gstatic", "FallbackFont", "Content Security Policy", "Refused to connect")


def main():
    R.need_playwright()
    from playwright.sync_api import sync_playwright
    out = Path(sys.argv[1] if len(sys.argv) > 1 else Path(tempfile.gettempdir()) / "daybook-glyphs")
    out.mkdir(parents=True, exist_ok=True)
    seen = json.dumps(R.puzzle_ids())  # no walkthrough offer over the boards
    failures = []
    dist = Path(sys.argv[2]) if len(sys.argv) > 2 else R.DEFAULT_DIST
    with R.Server(dist) as server, sync_playwright() as p:
        for engine in R.BROWSERS:
            browser = R.launch(p, engine)
            for i, query in enumerate(PAGES):
                ctx = browser.new_context(viewport={"width": 375, "height": 537}, device_scale_factor=2,
                                          has_touch=True, locale="en-US")
                ctx.add_init_script("localStorage.setItem(%s, %s)" % (json.dumps(R.SEEN_KEY), json.dumps(seen)))
                page = ctx.new_page()
                off = []
                page.on("request", lambda r: urlparse(r.url).hostname not in ("127.0.0.1", None) and off.append(r.url))
                page.on("requestfailed", lambda r: off.append("failed: " + r.url))
                bad = []
                # WebKit's harness injects a `body {}` style, which the CSP refuses: not the app's.
                page.on("console", lambda m: any(b in m.text for b in BAD_CONSOLE) and "style-src" not in m.text
                        and bad.append(m.text))
                page.goto(server.url + query)
                png, _ = R.settle(page, 30)
                page.wait_for_timeout(1000)
                R.frame(page)
                (out / f"{i}-{engine}.png").write_bytes(page.screenshot())
                print(f"{engine:8} {query:32} requests off 127.0.0.1: {len(off)}  console: {len(bad)}")
                failures += [f"{engine} {query}: {x}" for x in off + bad]
                ctx.close()
            browser.close()
    print("\n".join(failures) or f"OK, screenshots in {out}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
