#!/usr/bin/env python3
"""Regenerates docs/social-preview/social-preview.png, the 1280x640 card GitHub shows when the repo
is linked. GitHub has no API for setting it: upload the PNG by hand under
Settings > General > Social preview.

Needs Pillow. Title uses the serif already bundled for the web build; the tagline uses DejaVu Sans
(any sans will do if that is missing).
Run from the repository root:  python3 docs/social-preview/make.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "android" / "play-icon-512.png"
SERIF = ROOT / "web/src/wasmJsMain/composeResources/font/noto_serif_bold.ttf"
SANS_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/Library/Fonts/Arial.ttf",
    "C:/Windows/Fonts/arial.ttf",
]
OUT = Path(__file__).with_name("social-preview.png")

BG = (0x11, 0x20, 0x1C)       # the launcher icon's own background, so the icon sits flush
CREAM = (0xF6, 0xF1, 0xE7)
MUTED = (0xB9, 0xC4, 0xBE)
ACCENT = (0xCE, 0x8B, 0x35)   # the sun

W, H = 1280, 640
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

icon = Image.open(ICON).convert("RGB").resize((380, 380), Image.LANCZOS)
img.paste(icon, (100, (H - 380) // 2))

sans = next((p for p in SANS_CANDIDATES if Path(p).exists()), None)
title = ImageFont.truetype(str(SERIF), 128)
tag = ImageFont.truetype(sans, 38) if sans else ImageFont.load_default()
small = ImageFont.truetype(sans, 30) if sans else ImageFont.load_default()

x = 560
d.text((x, 185), "Daybook", font=title, fill=CREAM)
d.rectangle((x + 4, 346, x + 124, 352), fill=ACCENT)
d.text((x, 392), "A daily logic-puzzle app.", font=tag, fill=CREAM)
d.text((x, 446), "Eleven puzzles, new every day.", font=tag, fill=MUTED)
d.text((x, 520), "No ads  ·  No subscription  ·  No network", font=small, fill=MUTED)

img.save(OUT, optimize=True)
print(f"wrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")
