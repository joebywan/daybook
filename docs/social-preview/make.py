#!/usr/bin/env python3
"""Regenerates the two social cards from one layout:
  docs/social-preview/social-preview.png                  1280x640 (2:1), GitHub's repo card. GitHub
      has no API for setting it: upload the PNG by hand under Settings > General > Social preview.
  web/src/wasmJsMain/resources/social-preview.png         1200x630 (1.91:1), the og:image/twitter:image
      of the web app (index.html); it ships in the site, so it lives under web/ (a change under docs/
      does not trigger the pages workflow).
  docs/play/feature-graphic.png                           1024x500, Google Play's feature graphic (the same card scaled
      by 0.8 and cropped 6px top and bottom; Play rejects an alpha channel, so it is saved as RGB).
All three are committed; rerun this after changing the wording or the icon.

Needs Pillow. Title uses Fredoka Bold, the app's typeface; the tagline uses DejaVu Sans
(any sans will do if that is missing).
Run from the repository root:  python3 docs/social-preview/make.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "android" / "play-icon-512.png"
SERIF = ROOT / "app/src/main/res/font/fredoka_bold.ttf"
SANS_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/Library/Fonts/Arial.ttf",
    "C:/Windows/Fonts/arial.ttf",
]
OUTS = [
    (ROOT / "docs/social-preview/social-preview.png", 1280, 640),
    (ROOT / "web/src/wasmJsMain/resources/social-preview.png", 1200, 630),
]

BG = (0x11, 0x20, 0x1C)       # the launcher icon's own background, so the icon sits flush
CREAM = (0xF6, 0xF1, 0xE7)
MUTED = (0xB9, 0xC4, 0xBE)
ACCENT = (0xCE, 0x8B, 0x35)   # the sun

sans = next((p for p in SANS_CANDIDATES if Path(p).exists()), None)
title = ImageFont.truetype(str(SERIF), 128)
tag = ImageFont.truetype(sans, 38) if sans else ImageFont.load_default()
small = ImageFont.truetype(sans, 30) if sans else ImageFont.load_default()
icon = Image.open(ICON).convert("RGB").resize((380, 380), Image.LANCZOS)


def card(W, H):
    """The text block is a fixed 1280x640 layout shifted to centre in the canvas, so both sizes are
    the same picture with a little more or less margin."""
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    dx, dy = (W - 1280) // 2, (H - 640) // 2
    img.paste(icon, (100 + dx, (640 - 380) // 2 + dy))
    x = 560 + dx
    d.text((x, 185 + dy), "Daybook", font=title, fill=CREAM)
    d.rectangle((x + 4, 346 + dy, x + 124, 352 + dy), fill=ACCENT)
    d.text((x, 392 + dy), "A daily logic-puzzle app.", font=tag, fill=CREAM)
    d.text((x, 446 + dy), "New puzzles every day.", font=tag, fill=MUTED)
    d.text((x, 520 + dy), "No ads  \u00b7  No subscription  \u00b7  Works offline", font=small, fill=MUTED)
    return img


for out, w, h in OUTS:
    card(w, h).save(out, optimize=True)
    print(f"wrote {out.relative_to(ROOT)} {w}x{h} ({out.stat().st_size // 1024} KB)")

FEATURE = ROOT / "docs/play/feature-graphic.png"
FEATURE.parent.mkdir(parents=True, exist_ok=True)
scaled = card(1280, 640).resize((1024, 512), Image.LANCZOS).crop((0, 6, 1024, 506))
scaled.save(FEATURE, optimize=True)
print(f"wrote {FEATURE.relative_to(ROOT)} 1024x500 ({FEATURE.stat().st_size // 1024} KB)")
