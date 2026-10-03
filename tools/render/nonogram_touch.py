#!/usr/bin/env python3
"""Touch-drag sweeps on the Nonogram board, in Chromium and WebKit.

Compose on the web reads TouchEvents (CLAUDE.md), so a drag is real touch input: CDP
Input.dispatchTouchEvent in Chromium, the mouse in WebKit (synthetic TouchEvents are ignored there). Steps are
12px+, wider than the 15x15 square, so the sweep's start must come from the *down*.

Checks, per tier x size x scheme x browser (the board is read back from pixels, there is no DOM):
  1. a Fill sweep along a row, and one down a column, paints exactly those squares, and ONE Undo undoes it;
  2. a Fill sweep across crossed squares leaves the crosses alone, and ONE Undo undoes it;
  3. the grid's box (and the rest of the page) is pixel-identical in position before and after.

    python tools/render/nonogram_touch.py [-t hard -t expert] [-s 390x844] [-b webkit] [--scheme dark]
"""
from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import numpy as np  # noqa: E402
import render as R  # noqa: E402
from PIL import Image  # noqa: E402

N = {"standard": 5, "hard": 10, "expert": 15}
SCALE = R.DEVICE_SCALE


def arr(png):
    return np.asarray(Image.open(io.BytesIO(png)).convert("RGB")).astype(np.int32)


def bgmask(a):
    ring = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    vals, counts = np.unique(ring, axis=0, return_counts=True)
    return np.abs(a - vals[counts.argmax()]).max(axis=2) > 10


def find_layout(png, n):
    """Grid box (css px) and the css y of the pen row and the toolbar, from the screenshot."""
    a = arr(png)
    m = bgmask(a)
    H, W = m.shape
    rows = m.sum(axis=1)
    # the grid's top frame: the first long unbroken run well below the header and clues
    long_rows = [y for y in range(H // 6, H) if rows[y] > 0.45 * W]
    top = long_rows[0]
    cols = np.flatnonzero(m[top])
    left, right = cols[0], cols[-1] + 1
    u = (right - left) / n
    # bottom of the grid: the n-th cell below the top
    bottom = top + round(n * u)
    # bands below the grid: pen row, then toolbar
    ys = [y for y in range(bottom + 4, H) if m[y].any()]
    bands, start, prev = [], ys[0], ys[0]
    for y in ys[1:]:
        if y - prev > 6 * SCALE:
            bands.append((start, prev))
            start = y
        prev = y
    bands.append((start, prev))
    pen, bar = bands[0], bands[-1]
    return dict(gx=left / SCALE, gy=top / SCALE, u=u / SCALE, pen_y=(pen[0] + pen[1]) / 2 / SCALE,
                bar_y=(bar[0] + bar[1]) / 2 / SCALE, W=W / SCALE)


def cell_xy(L, r, c):
    return L["gx"] + (c + 0.5) * L["u"], L["gy"] + (r + 0.5) * L["u"]


def read_state(png, L, n, base):
    """'.', 'F' or 'X' per square: filled changes the square's upper middle, a cross only its centre."""
    a = arr(png)
    out = []
    for r in range(n):
        for c in range(n):
            x, y = cell_xy(L, r, c)
            def px(dx, dy):
                return a[int((y + dy * L["u"]) * SCALE), int((x + dx * L["u"]) * SCALE)]
            if np.abs(px(0, -0.3) - base[(r, c, 0)]).max() > 25:
                out.append("F")
            elif np.abs(px(0, 0) - base[(r, c, 1)]).max() > 25:
                out.append("X")
            else:
                out.append(".")
    return "".join(out)


def base_samples(png, L, n):
    a = arr(png)
    d = {}
    for r in range(n):
        for c in range(n):
            x, y = cell_xy(L, r, c)
            d[(r, c, 0)] = a[int((y - 0.3 * L["u"]) * SCALE), int(x * SCALE)]
            d[(r, c, 1)] = a[int(y * SCALE), int(x * SCALE)]
    return d


class Finger:
    def __init__(self, page, browser):
        self.page, self.browser = page, browser
        self.cdp = page.context.new_cdp_session(page) if browser == "chromium" else None

    def send(self, kind, x, y):
        if self.cdp:
            self.cdp.send("Input.dispatchTouchEvent", {
                "type": {"touchstart": "touchStart", "touchmove": "touchMove", "touchend": "touchEnd"}[kind],
                "touchPoints": [] if kind == "touchend" else [{"x": x, "y": y, "id": 1}]})
        else:
            # ponytail: headless WebKit ignores hand-built TouchEvents (isTrusted=false; tried touch-only,
            # pointer-only and both). Its real input is the mouse, which Compose reads as a pointer drag with
            # the mouse's own slop; WebKit's touch slop stays unchecked (docs/TODO.md).
            {"touchstart": lambda: (self.page.mouse.move(x, y), self.page.mouse.down()),
             "touchmove": lambda: self.page.mouse.move(x, y),
             "touchend": lambda: self.page.mouse.up()}[kind]()
            R.frame(self.page)

    def drag(self, p0, p1, step=12):
        (x0, y0), (x1, y1) = p0, p1
        dist = max(abs(x1 - x0), abs(y1 - y0))
        k = max(1, int(dist // step))
        self.send("touchstart", x0, y0)
        self.page.wait_for_timeout(30)
        for i in range(1, k + 1):
            self.send("touchmove", x0 + (x1 - x0) * i / k, y0 + (y1 - y0) * i / k)
            self.page.wait_for_timeout(15)
        self.send("touchend", x1, y1)

    def tap(self, x, y):
        if self.cdp:
            self.send("touchstart", x, y)
            self.send("touchend", x, y)
        else:
            self.page.touchscreen.tap(x, y)
            R.frame(self.page)  # headless WebKit drops the next tap if no frame was painted in between
            self.page.wait_for_timeout(150)


def shot(page):
    png, _ = R.settle(page, 20)
    return png


def run(ctx, url, browser, tier, w, h, scheme, date):
    n = N[tier]
    page = ctx.new_page()
    errs = []
    page.on("pageerror", lambda e: errs.append(str(e)))
    page.goto(f"{url}?date={date}&tier={tier}&puzzle=nonogram", wait_until="load")
    png0 = shot(page)
    L = find_layout(png0, n)
    base = base_samples(png0, L, n)
    f = Finger(page, browser)
    bad = []
    tag = f"{browser} {tier} {w}x{h} {scheme}"
    mid = n // 2
    empty = "." * n * n

    def state():
        return read_state(shot(page), L, n, base)

    def undo():
        f.tap(L["W"] * 0.19, L["bar_y"])
        page.wait_for_timeout(100)

    def expect(label, got, want):
        if got != want:
            bad.append(f"{tag}: {label}: got {got!r} want {want!r}")

    def pen(which):
        f.tap(L["W"] * (0.32 if which == "fill" else 0.68), L["pen_y"])
        page.wait_for_timeout(100)

    def want(cells, mark, over=empty):
        s = list(over)
        for i in cells:
            if s[i] == ".":
                s[i] = mark
        return "".join(s)

    # 1. a row sweep, then a column sweep: each exactly its squares, each one undo
    row = [mid * n + c for c in range(n)]
    f.drag(cell_xy(L, mid, 0), cell_xy(L, mid, n - 1))
    s1 = state()
    expect("row sweep", s1, want(row, "F"))
    col = [r * n + mid for r in range(n)]
    f.drag(cell_xy(L, 0, 1), cell_xy(L, n - 1, 1))
    s2 = state()
    expect("column sweep", s2, want([r * n + 1 for r in range(n)], "F", s1))
    undo()
    expect("one undo after column sweep", state(), s1)
    undo()
    expect("one undo after row sweep", state(), empty)
    # 2. crosses survive a fill sweep
    pen("cross")
    for c in (1, 3):
        f.tap(*cell_xy(L, 0, c))
    sx = state()
    expect("cross taps", sx, want([1, 3], "X"))
    pen("fill")
    f.drag(cell_xy(L, 0, 0), cell_xy(L, 0, n - 1))
    s3 = state()
    expect("fill over crosses", s3, want(range(n), "F", sx))
    undo()
    expect("one undo after fill over crosses", state(), sx)
    # 3. nothing moved
    png1 = shot(page)
    L1 = find_layout(png1, n)
    if any(abs(L[k] - L1[k]) > 0.6 for k in ("gx", "gy", "u", "pen_y", "bar_y")):
        bad.append(f"{tag}: layout moved {L} -> {L1}")
    if errs:
        bad.append(f"{tag}: page errors {errs}")
    print(("FAIL " if bad else "ok   ") + tag, flush=True)
    page.close()
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-t", "--tier", action="append", choices=list(N))
    ap.add_argument("-s", "--size", action="append", type=R.parse_size)
    ap.add_argument("-b", "--browser", action="append", choices=R.BROWSERS)
    ap.add_argument("--scheme", action="append", choices=R.SCHEMES)
    ap.add_argument("--date", default="2026-06-15")
    ap.add_argument("--dist", type=Path, default=R.DEFAULT_DIST)
    a = ap.parse_args()
    R.need_playwright()
    from playwright.sync_api import sync_playwright
    problems = []
    with R.Server(a.dist) as server, sync_playwright() as p:
        for b in a.browser or R.BROWSERS:
            br = R.launch(p, b)
            for (w, h) in a.size or [R.parse_size(s) for s in R.DEFAULT_SIZES]:
                for scheme in a.scheme or R.SCHEMES:
                    for tier in a.tier or ["hard", "expert"]:
                        ctx = br.new_context(viewport={"width": w, "height": h}, device_scale_factor=SCALE,
                                             has_touch=True, color_scheme=scheme, locale="en-US",
                                             service_workers="block")
                        try:
                            problems += run(ctx, server.url, b, tier, w, h, scheme, a.date)
                        except Exception as e:  # noqa: BLE001
                            problems.append(f"{b} {tier} {w}x{h} {scheme}: {type(e).__name__}: {str(e)[:200]}")
                            print("ERR ", problems[-1], flush=True)
                        finally:
                            ctx.close()
            br.close()
    print(f"\n{len(problems)} problem(s)")
    for line in problems:
        print("  PROBLEM:", line)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
