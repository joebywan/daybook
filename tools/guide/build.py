#!/usr/bin/env python3
"""Turns tools/guide/pages/<puzzle id>.txt into the player guide: guide/index.html and guide/<id>/index.html.

Standard library only. Format and conventions: tools/guide/AUTHORING.md. The facts are checked by GuideTest.

    python3 tools/guide/build.py [--out DIR]    # default: inside the web distribution, as pages.yml wants it
"""
import argparse
import html
import re
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PAGES = REPO / "tools/guide/pages"
FONTS = REPO / "web/src/wasmJsMain/composeResources/font"
DIST = REPO / "web/build/dist/wasmJs/productionExecutable/guide"

# Nothing off-site can load, whatever a page says: that is the guide's privacy promise, enforced by the browser.
HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'self'; font-src 'self'; img-src 'self'">
<title>{title}</title>
<meta name="description" content="{description}">
<link rel="icon" href="{root}../favicon.svg" type="image/svg+xml">
<link rel="stylesheet" href="{root}guide.css">
</head>
<body>
<main>
"""
CELL = 40
TOP = '<p class="top"><a href="#top">Back to top</a></p>'


def fail(where, message):
    sys.exit(f"{where}: {message}")


def inline(text):
    return re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", html.escape(text, quote=False))


def cells(spec, where, width, height):
    """`r1c1` or the rectangle `r1c1-r3c9`, 1-based as the hints count rows, to (row, col) pairs."""
    found = []
    for part in spec.split():
        m = re.fullmatch(r"r(\d+)c(\d+)(?:-r(\d+)c(\d+))?", part)
        if not m:
            fail(where, f"not a square or a range: {part}")
        r1, c1 = int(m[1]), int(m[2])
        r2, c2 = int(m[3] or r1), int(m[4] or c1)
        if not (1 <= r1 <= r2 <= height and 1 <= c1 <= c2 <= width):
            fail(where, f"off the board: {part}")
        found += [(r - 1, c - 1) for r in range(r1, r2 + 1) for c in range(c1, c2 + 1)]
    return found


def marks(spec, where, width, height):
    """`r3c4=17 r5c5=2` to {(row, col): "17", ...}."""
    found = {}
    for part in spec.split():
        square, _, value = part.partition("=")
        if not value:
            fail(where, f"needs square=value: {part}")
        found[cells(square, where, width, height)[0]] = value
    return found


def board(lines, where):
    """One ~~~board block to an inline SVG: real rows of tokens, then `key: value` marks (AUTHORING.md lists them)."""
    rows, fields = [], {}
    for line in lines:
        key, colon, value = line.partition(":")
        if colon:
            if key.strip() not in ("box", "regions", "cages", "focus", "cite", "answer", "notes", "struck", "lines", "moves", "draw", "edges",
                                "corners", "solid", "tiles", "top", "left", "caption"):
                fail(where, f"unknown board key: {key}")
            fields[key.strip()] = value.strip()
        elif line.strip():
            rows.append(line.split())
    if not rows or len({len(r) for r in rows}) != 1:
        fail(where, "a board needs rows of equal width")
    if "caption" not in fields:
        fail(where, "a board needs a caption")
    w, h = len(rows[0]), len(rows)
    W, H = w * CELL, h * CELL
    top, left = fields.get("top", "").split(), fields.get("left", "").split()
    if (top and len(top) != w) or (left and len(left) != h):
        fail(where, "top needs one label per column, left one per row")
    get = lambda key, parse: parse(fields.get(key, ""), where, w, h)
    focus, cite = set(get("focus", cells)), get("cite", cells)
    solid = set(get("solid", cells))
    answer, notes, struck = get("answer", marks), get("notes", marks), get("struck", marks)
    tiles = get("tiles", marks)
    if set(tiles.values()) - {"right", "near", "out"}:
        fail(where, "tiles are right, near or out")
    regions = fields.get("regions", "").split()
    cages = fields.get("cages", "").split()
    if cages and (len(cages) != h or {len(r) for r in cages} != {w}):
        fail(where, "cages needs one string per row, one letter per square")
    if regions and (len(regions) != h or {len(r) for r in regions} != {w}):
        fail(where, "regions needs one string per row, one letter per square")
    box = int(fields.get("box", 0))
    region_at = lambda r, c: regions[r][c] if regions else (r // box, c // box) if box else 0
    hues = {}
    svg, walls = [], []
    for r in range(h):
        for c in range(w):
            if rows[r][c] == "_":
                continue
            x, y = c * CELL, r * CELL
            svg.append(f'<rect class="sq" x="{x}" y="{y}" width="{CELL}" height="{CELL}"/>')
            if regions and regions[r][c] != ".":  # "." has no tint, and no walls between its squares
                hue = hues.setdefault(regions[r][c], len(hues) % 5)
                svg.append(f'<rect class="hue{hue}" x="{x}" y="{y}" width="{CELL}" height="{CELL}"/>')
            if (r, c) in solid:
                svg.append(f'<rect class="solid" x="{x}" y="{y}" width="{CELL}" height="{CELL}"/>')
            if (r, c) in tiles:
                # As the app does, a shape as well as a colour: right has an inset ring, near a corner dot.
                svg.append(f'<rect class="tile-{tiles[r, c]}" x="{x}" y="{y}" width="{CELL}" height="{CELL}"/>')
                if tiles[r, c] == "right":
                    svg.append(f'<rect class="tile-ring" x="{x + 4}" y="{y + 4}" width="{CELL - 8}" height="{CELL - 8}" rx="4"/>')
                elif tiles[r, c] == "near":
                    svg.append(f'<circle class="tile-dot" cx="{x + CELL - 8}" cy="{y + 8}" r="3"/>')
            if (r, c) in focus:
                svg.append(f'<rect class="focus" x="{x}" y="{y}" width="{CELL}" height="{CELL}"/>')
            # A wall on the right and the bottom of any square whose neighbour is another region (or nothing).
            if c + 1 == w or rows[r][c + 1] == "_" or region_at(r, c) != region_at(r, c + 1):
                walls.append((x + CELL, y, x + CELL, y + CELL))
            if r + 1 == h or rows[r + 1][c] == "_" or region_at(r, c) != region_at(r + 1, c):
                walls.append((x, y + CELL, x + CELL, y + CELL))
            if c == 0 or rows[r][c - 1] == "_":
                walls.append((x, y, x, y + CELL))
            if r == 0 or rows[r - 1][c] == "_":
                walls.append((x, y, x + CELL, y))
    if fields.get("draw") != "dots":  # On a dot board the sides are the loop's own edges: no wall may look like a line.
        svg += [f'<line class="wall" x1="{a}" y1="{b}" x2="{c}" y2="{d}"/>' for a, b, c, d in walls]
    centre = lambda r, c: (c * CELL + CELL // 2, r * CELL + CELL // 2)
    # A dashed outline just inside each cage: a side wherever the neighbour is another cage, joined round its corners.
    inset, half = 4, CELL // 2
    in_cage = lambda r, c, k: 0 <= r < h and 0 <= c < w and cages[r][c] == k
    for r in range(h if cages else 0):
        for c in range(w):
            k = cages[r][c]
            for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                if in_cage(r + dr, c + dc, k):
                    continue
                tr, tc = abs(dc), abs(dr)
                reach = lambda s: (half - inset if not in_cage(r + s * tr, c + s * tc, k)
                                   else half + inset if in_cage(r + s * tr + dr, c + s * tc + dc, k) else half)
                x, y = centre(r, c)
                x, y = x + dc * (half - inset), y + dr * (half - inset)
                svg.append(f'<line class="cage" x1="{x - tc * reach(-1)}" y1="{y - tr * reach(-1)}" x2="{x + tc * reach(1)}" y2="{y + tr * reach(1)}"/>')
    draw = fields.get("draw", "")
    if draw not in ("", "spokes", "discs", "pearls", "dots"):
        fail(where, f"unknown draw: {draw}")
    # `a-b` a line, `a=b` two side by side, `axb` a cross on the edge between two squares. `moves` are the step's, on top.
    for key, kind in (("lines", "run done" if "moves" in fields else "run"), ("moves", "run move")):
        for part in fields.get(key, "").split():
            m = re.fullmatch(r"(r\d+c\d+)([-=x])(r\d+c\d+)", part)
            if not m:
                fail(where, f"not a line: {part}")
            if draw == "dots":
                # Lines run along the squares' sides, from dot to dot: r1c1 is the top-left corner of square r1c1.
                (r1, c1), (r2, c2) = (cells(end, where, w + 1, h + 1)[0] for end in (m[1], m[3]))
                (x1, y1), (x2, y2) = (c1 * CELL, r1 * CELL), (c2 * CELL, r2 * CELL)
            else:
                (r1, c1), (r2, c2) = (cells(end, where, w, h)[0] for end in (m[1], m[3]))
                (x1, y1), (x2, y2) = centre(r1, c1), centre(r2, c2)
            if m[2] == "x":
                mx, my = (x1 + x2) // 2, (y1 + y2) // 2
                svg += [f'<line class="{kind} x" x1="{mx - 5}" y1="{my - 5}" x2="{mx + 5}" y2="{my + 5}"/>',
                        f'<line class="{kind} x" x1="{mx - 5}" y1="{my + 5}" x2="{mx + 5}" y2="{my - 5}"/>']
            else:
                dx, dy = (4, 0) if x1 == x2 else (0, 4)
                for k in ((-1, 1) if m[2] == "=" else (0,)):
                    svg.append(f'<line class="{kind}" x1="{x1 + k * dx}" y1="{y1 + k * dy}" x2="{x2 + k * dx}" y2="{y2 + k * dy}"/>')
    if draw == "dots":
        svg += [f'<circle class="dot" cx="{c * CELL}" cy="{r * CELL}" r="3.5"/>' for r in range(h + 1) for c in range(w + 1)]
    for r, c in cite:
        svg.append(f'<rect class="cite" x="{c * CELL + 3}" y="{r * CELL + 3}" width="{CELL - 6}" height="{CELL - 6}" rx="6"/>')
    for r, c in answer:
        svg.append(f'<rect class="answer" x="{c * CELL + 2}" y="{r * CELL + 2}" width="{CELL - 4}" height="{CELL - 4}" rx="6"/>')
    for r in range(h):
        for c in range(w):
            x, y = centre(r, c)
            token = answer.get((r, c), rows[r][c])
            if token in (".", "_"):
                continue
            size = "small" if len(token) > 3 else "big"
            kind = f"{size} on" if (r, c) in answer else f"{size} onsolid" if (r, c) in solid else f"{size} over" if "lines" in fields else size
            if draw == "spokes" and set(token) <= set("urdl"):
                # A pipe tile: an arm from the middle to each side named (up, right, down, left).
                ends = {"u": (x, y - CELL // 2), "r": (x + CELL // 2, y), "d": (x, y + CELL // 2), "l": (x - CELL // 2, y)}
                pipe = "pipe on" if (r, c) in answer else "pipe"
                svg += [f'<line class="{pipe}" x1="{x}" y1="{y}" x2="{ends[t][0]}" y2="{ends[t][1]}"/>' for t in token]
            elif draw == "pearls" and token in ("b", "w"):
                svg.append(f'<circle class="pearl {token}" cx="{x}" cy="{y}" r="11"/>')
            else:
                if draw == "discs":
                    svg.append(f'<circle class="disc" cx="{x}" cy="{y}" r="15"/>')
                shown = token.replace("_", " ")  # An answer's value cannot hold a space: not_4.
                svg.append(f'<text class="{kind}" x="{x}" y="{y}">{html.escape(shown)}</text>')
    for (r, c), value in get("corners", marks).items():
        on = " on" if (r, c) in answer else ""
        svg.append(f'<text class="corner{on}" x="{c * CELL + 5}" y="{r * CELL + 10}">{html.escape(value)}</text>')
    # A note sits where the app pencils that digit, a 3x3 inside the square; a note that is not a digit, in the middle.
    for table, kind in ((notes, "note"), (struck, "note struck")):
        for (r, c), value in table.items():
            if (r, c) in answer:
                fail(where, "no pencil marks on the answer's square: they cannot be read over its digit")
            # Words, comma-separated, stack down the square: a pencil place means nothing for "cat,dog".
            words = value.split(",") if "," in value else None
            for i, ch in enumerate(words or value):
                x, y = centre(r, c)
                if words:
                    y += (i - (len(words) - 1) / 2) * min(9, 36 / len(words))
                    svg.append(f'<text class="note word" x="{x}" y="{y}">{html.escape(ch)}</text>')
                    continue
                if ch.isdigit() and ch != "0":
                    x += ((int(ch) - 1) % 3 - 1) * 12
                    y += ((int(ch) - 1) // 3 - 1) * 12
                svg.append(f'<text class="{kind}" x="{x}" y="{y}">{ch}</text>')
                if kind != "note":
                    svg.append(f'<line class="strike" x1="{x - 6}" y1="{y + 5}" x2="{x + 6}" y2="{y - 5}"/>')
    # Something printed between two neighbours: `r1c1-r1c2=<` says the first is smaller, and the chevron's point is at it.
    for part in fields.get("edges", "").split():
        ends, _, value = part.partition("=")
        (r1, c1), (r2, c2) = (cells(end, where, w, h)[0] for end in ends.split("-"))
        if abs(r1 - r2) + abs(c1 - c2) != 1 or not value:
            fail(where, f"an edge is two neighbours and what is between them: {part}")
        x, y = (c1 + c2 + 1) * CELL // 2, (r1 + r2 + 1) * CELL // 2
        if value in ("<", ">"):
            point = -1 if value == "<" else 1
            ax, ay, bx, by = (0, point * 5, 8, 0) if c1 == c2 else (point * 5, 0, 0, 8)
            shape = f"{x - ax - bx},{y - ay - by} {x + ax},{y + ay} {x - ax + bx},{y - ay + by}"
            svg.append(f'<polyline class="edge-back" points="{shape}"/><polyline class="edge" points="{shape}"/>')
        else:
            svg.append(f'<circle class="sign" cx="{x}" cy="{y}" r="8"/><text class="signtext" x="{x}" y="{y}">{html.escape(value)}</text>')
    # Labels sit outside the grid. A narrow labelled board gets side margins, or it would scale up to fill the page.
    side = max(len(label) for label in left) * 8 + 6 if left else 0
    head = 18 if top else 0
    svg += [f'<text class="label" x="{c * CELL + CELL // 2}" y="{-head // 2}">{html.escape(t)}</text>' for c, t in enumerate(top)]
    svg += [f'<text class="label" x="{-side // 2}" y="{r * CELL + CELL // 2}">{html.escape(t)}</text>' for r, t in enumerate(left)]
    pad = max(0, 240 - W - side) // 2 if top or left else 0
    caption = inline(fields["caption"])
    edge = 10 if draw == "dots" else 2  # Dots and lines sit on the board's own edge: room for them.
    return (f'<figure><svg viewBox="{-edge - side - pad} {-edge - head} {W + 2 * edge + side + 2 * pad} {H + 2 * edge + head}" role="img" aria-label="{html.escape(fields["caption"])}">'
            + "".join(svg) + f"</svg><figcaption>{caption}</figcaption></figure>")


def page(path):
    """A page's source to (title, tagline, body html): contents first, and a way back to them after every section."""
    title = tagline = None
    out, paragraph, items, block = [], [], [], None
    block_start = 0
    sections, techniques = [], []

    def flush():
        if paragraph:
            out.append(f"<p>{inline(' '.join(paragraph))}</p>")
            paragraph.clear()
        if items:
            out.append("<ul>" + "".join(f"<li>{inline(i)}</li>" for i in items) + "</ul>")
            items.clear()

    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        where = f"{path.name}:{n}"
        if block is not None:
            if line.strip() == "~~~":
                out.append(board(block, f"{path.name}:{block_start}"))
                block = None
            else:
                block.append(line)
        elif line.strip() == "~~~board":
            flush()
            block, block_start = [], n
        elif line.startswith("# "):
            title = line[2:].strip()
        elif line.startswith("> "):
            tagline = line[2:].strip()
        elif line.startswith("## "):
            flush()
            name = line[3:].strip()
            slug = re.sub(r"[^a-z0-9]+", "-", name.lower())
            if sections:
                out.append(TOP)
            sections.append((slug, name))
            out.append(f'<h2 id="{slug}">{inline(name)}</h2>')
        elif line.startswith("### "):
            flush()
            tid, bar, name = line[4:].partition("|")
            if not bar:
                fail(where, "a technique heading is ### id | Name")
            if techniques:
                out.append(TOP)
            techniques.append((tid.strip(), name.strip()))
            out.append(f'<h3 id="{html.escape(tid.strip())}"><a href="#{html.escape(tid.strip())}">{inline(name.strip())}</a></h3>')
        elif line.startswith("- "):
            if paragraph:
                flush()
            items.append(line[2:].strip())
        elif not line.strip():
            flush()
        elif line.startswith(("#", "~~~", ">")):
            fail(where, f"not part of the format: {line}")
        else:
            paragraph.append(line.strip())
    flush()
    if block is not None:
        fail(f"{path.name}:{block_start}", "a board is never closed")
    if not title or not tagline:
        fail(path.name, "a page starts with # Title and > tagline")
    links = lambda pairs: "".join(f'<li><a href="#{t}">{inline(n)}</a></li>' for t, n in pairs)
    nested = f"<ul>{links(techniques)}</ul>" if techniques else ""
    contents = '<ul class="toc">' + "".join(
        f'<li><a href="#{s}">{inline(n)}</a>{nested if n == "Techniques" else ""}</li>' for s, n in sections
    ) + "</ul>"
    return title, tagline, "\n".join([contents, *out, TOP])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=DIST)
    out = parser.parse_args().out
    (out / "fonts").mkdir(parents=True, exist_ok=True)
    shutil.copy(PAGES.parent / "guide.css", out)
    for font in ("fredoka_regular.ttf", "fredoka_semibold.ttf"):
        shutil.copy(FONTS / font, out / "fonts")

    entries = []
    for path in sorted(PAGES.glob("*.txt")):
        title, tagline, body = page(path)
        entries.append((title, tagline, path.stem))
        (out / path.stem).mkdir(exist_ok=True)
        (out / path.stem / "index.html").write_text(
            HEAD.format(title=html.escape(f"{title}: a Daybook guide"), description=html.escape(tagline), root="../")
            + f'<nav><a href="../">All puzzles</a></nav>\n<h1>{html.escape(title)}</h1>\n<p class="tagline">{html.escape(tagline)}</p>\n'
            + body + '\n<footer><a href="../">All puzzles</a> · <a href="../../">Play Daybook</a></footer>\n</main>\n</body>\n</html>\n',
            encoding="utf-8",
        )
    items = "".join(
        f'<li><a href="{i}/"><strong>{html.escape(t)}</strong><span>{html.escape(g)}</span></a></li>'
        for t, g, i in sorted(entries, key=lambda e: e[0].lower())
    )
    (out / "index.html").write_text(
        HEAD.format(title="Daybook guide", description="How to play every Daybook puzzle, and the techniques its hints teach.", root="")
        + '<h1>Daybook guide</h1>\n<p class="tagline">How to play each puzzle, what changes per tier, and the techniques its hints teach.</p>\n'
        + f'<ul class="puzzles">{items}</ul>\n<footer><a href="../">Play Daybook</a></footer>\n</main>\n</body>\n</html>\n',
        encoding="utf-8",
    )
    print(f"{len(entries)} guide page(s) in {out}")


if __name__ == "__main__":
    main()
