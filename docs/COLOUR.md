# Colour: the board palette

Why this exists: the app theme (`ui/theme/Palette.kt`) is deliberately quiet, ink and parchment with moss. A board that
draws only with `colorScheme` plus its one `accent` comes out beige and one-note (Inequality shipped that way: surface
grey, one teal). The boards that read well (Shikaku's hue-per-region, Nonogram's blended pairs, Kings' regions) all
bring several hues. This is the shared set to bring them from, derived from the colours already shipped.

## Three layers

1. **Structure** is always `MaterialTheme.colorScheme`: board background, grid lines, given digits, text, `error`,
   hint glow. Never a hex literal, so light and dark both work.
2. **Identity** is the puzzle's `accent` (card, selection, walkthrough buttons). One per puzzle, already chosen; the
   thirteen in use sit at hues 5-327 with saturation 0.4-0.65, lightness 0.45-0.67. Keep new ones inside that box and
   at least ~25 degrees of hue from every existing accent (Lits' tan, s 0.21, is the one deliberate low-saturation case).
3. **Content** is the hue family below. Anything the player *places, groups or compares* (regions, tokens, runs,
   paths, a solved flourish) takes its colour from here, not from the surface greys.

## The hue family

Eight hues about 45 degrees apart, each in four steps built with one HSL recipe (the one Shikaku's `hue()` already
uses), so any two hues sit at the same weight and can share a board. Light/dark is chosen by the scheme.

| Hue | h | Fill, light | Fill, dark | Ink, light | Ink, dark | Mark (either) |
|---|---|---|---|---|---|---|
| Coral | 8 | `E8A69C` | `6B362E` | `963A2C` | `D67E71` | `CD6251` |
| Amber | 34 | `E8C79C` | `6B512E` | `96682C` | `D6AA71` | `CD9751` |
| Gold | 46 | `E8D69C` | `6B5D2E` | `967D2C` | `D6BE71` | `CDB051` |
| Green | 145 | `9CE8BB` | `2E6B47` | `2C9658` | `71D69B` | `51CD85` |
| Teal | 175 | `9CE8E1` | `2E6B66` | `2C968D` | `71D6CD` | `51CDC2` |
| Blue | 215 | `9CBBE8` | `2E476B` | `2C5896` | `719BD6` | `5185CD` |
| Violet | 268 | `BF9CE8` | `4A2E6B` | `5D2C96` | `A071D6` | `8B51CD` |
| Rose | 330 | `E89CC2` | `6B2E4D` | `962C61` | `D671A3` | `CD518F` |

Recipe: fill = hsl(h, .62, .76) light / hsl(h, .40, .30) dark; ink = hsl(h, .55, .38) light / hsl(h, .55, .64) dark;
mark = hsl(h, .55, .56). **Fill** is a cell or region background and carries `onSurface` text. **Ink** is text or a thin
stroke drawn on the surface. **Mark** is a solid shape (token, path, dot). Generate in code with `Color.hsl(h, s, l)`
from the table's `h`; do not paste the hexes in (they are for reading and for the harness).

## Rules

- **Two content hues minimum, five at most per board.** One hue is a monochrome board; six or more cannot be told apart.
  When a board needs N distinct groups, take N hues spread across the table (every other row first: Coral, Green,
  Violet, Amber, Teal, Rose), not neighbours.
- **Semantic colours are fixed and are not content hues.** Correct = Green mark, present/partial = Gold (Lexicon's
  `4E9F6C` / `D1A32F` are these two), error = `scheme.error` (Clay `C0563F`), warning is the error colour too. Do not
  spend Coral or Green on an unrelated group *in a board that also shows right/wrong*; use the other six.
- **Colour is never the only signal** (see PUZZLE_STANDARDS section on legibility): pair every hue with a shape,
  digit or position. Where groups must be told apart by colour alone (Kings' regions) pick by CIEDE2000 as
  `KingsPaletteTest` does, not by eye.
- **Derive, never store.** A board's colours come from its picture, seed or state (Nonogram hashes the solution to
  pick a pair). Nothing about colour goes into `PuzzleState`.
- **Alpha on surface, not grey.** Selection and "same digit" tints are `Color(accent).copy(alpha = 0.2..0.4f)`; a
  highlight state is never a surface grey alone.
- **Previews use the same hues**, drawn static; the home grid is where a bland board is most visible, since the cards sit
  side by side.

## Checking

Render at true size in both schemes (`tools/render/render.py`, or the emulator) and ask: can I count at least two hues
that are not the accent or a grey? Are the dark-mode fills still distinguishable from the surface (`FF1B2F29`)? The
dark fills above sit at lightness 0.30 against the surface's 0.14: keep that gap.

## Status

The table is a proposal derived from shipped colours, not yet used by any board. Auditing each board against it, and
moving the recipe into one shared helper (`ui/theme/`, web-safe: no `java.*`) when the first board adopts it, is in
`docs/TODO.md`.
