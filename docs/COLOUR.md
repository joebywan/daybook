# Colour: the board palette

Why this exists: the app theme (`ui/theme/Palette.kt`) is deliberately quiet, ink and parchment with moss. A board that
draws only with `colorScheme` plus its one `accent` comes out beige and one-note (one board shipped that way: surface
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
- **More than five groups: steps, not new hues.** Excluding Coral and Green leaves six hues, and Amber and Gold sit 12
  degrees apart. A board that needs up to eight equal tokens (Tower's pegs) takes them from the six hues at three steps,
  the same in both schemes: pale = `fill(h, false)`, mid = `mark(h)`, deep = `ink(h, false)`. Choose and order them by
  CIEDE2000 including deuteranopia and protanopia so every prefix is as far apart as it can be (Tower: 21 / 17 / 11 for the
  first 5 / 6 / 8, pinned by `TowerPaletteTest`), and put a digit or shape on every token: the colour-blind distance of an
  eight-token set is too small to carry it alone.
- **Mix steps to spread brightness, not only hue.** Five hues all at `mark` sit within a 1.4:1 luminance band and collapse to one
  grey. Giving each hue its own step (Mosaic: pale `fill`, deep `ink`, mid `mark`) gives four luminance levels and raised the
  5-colour CIEDE2000 floor (colour-blind included) from 6.6 to 15.8. Search the 3 steps x hue order, scoring all four vision modes.
- **Semantic colours are fixed and are not content hues.** Correct = Green mark, present/partial = Gold (Lexicon's
  `4E9F6C` / `D1A32F` are these two), error = `scheme.error` (Clay `C0563F`), warning is the error colour too. Do not
  spend Coral or Green on an unrelated group *in a board that also shows right/wrong*; use the other six.
- **Colour is never the only signal** (see PUZZLE_STANDARDS section on legibility): pair every hue with a shape,
  digit or position. Where groups must be told apart by colour alone (Kings' regions) pick by CIEDE2000 as
  `KingsPaletteTest` does, not by eye.
- **Derive, never store.** A board's colours come from its picture, seed or state (`BoardHues.pair` takes a hash of the solution). Nothing about colour goes into `PuzzleState`.
- **Alpha on surface, not grey.** Selection and "same digit" tints are `Color(accent).copy(alpha = 0.2..0.4f)`; a
  highlight state is never a surface grey alone.
- **Previews use the same hues**, drawn static; the home grid is where a bland board is most visible, since the cards sit
  side by side.

## Roles

A board that is not a set of equal groups (Sudoku-likes, grids with signs or lines) takes one pair from `BoardHues.pair(seed)`
and gives the two hues fixed jobs, so every board reads the same way:

- **Hue A: the board's structure and identity**, what is given or selected: signs, lines, the selected thing. `ink` for
  strokes, `mark` for a solid selection, `fill` at about 0.45 alpha for row/column bands.
- **Alphas** (over the surface; dark needs more because a dark fill is already close to it): alternating region tint,
  `fill` at 0.30 light / 0.55 dark; selected cell, `mark` at 0.40 light / 0.55 dark; row/column band, `fill` at 0.45;
  same-value twins, `fill` at 0.8.
- **Hue B: what the player placed and its echoes.** `ink` for the player's digits, `fill` for same-value twins.
- Givens and clues stay `scheme.onSurface`; errors and conflicts stay `scheme.error`.
- **Seed:** `BoardHues.pair(solution.hashCode())` (or a hash of the picture), recomputed per render, never stored, so each
  board has its own colours and saves are untouched. `HUE_PAIRS` excludes Coral and Green and its order is part of the
  contract: reordering recolours every board.
- **Dark mode:** get `dark` from `BoardHues.isDark(scheme)`. Check each dark fill against the surface; if a band reads
  muddy, lower its alpha or use the `mark` step.
- **Previews** use one fixed pair (`HUE_PAIRS[0]`), static.

## Design review: required before any colour change ships

Numbers first, then eyes. A colour change is not done until the PR body shows each of these, measured, for light AND
dark, on the real render (not the palette table). "Looks fine" is not evidence.

1. **Contrast, by WCAG relative luminance.** Text and digits on their fill: 4.5:1 (the small digits on Mosaic's areas count
   as small text). Graphics that carry meaning (pegs, region fills, strokes, selection) against what they sit on: 3:1.
   Pick the digit colour per fill by computing it (`BoardHues.onFill`: ink, white or black, the first that reaches 4.5), at full alpha; never one colour for all.
2. **Lightness is a separate axis from hue.** Equal HSL lightness is not equal brightness: teal and gold read far lighter
   than violet or blue at the same L. Hues that sit at nearly the same luminance (pairwise ratio under about 1.3) collapse
   into one grey for colour-blind players and in greyscale. Spread the set across at least three clearly different
   luminance levels where groups must be told apart; otherwise a second cue (digit, shape) must carry them and be big
   enough to read.
3. **Greyscale and colour-blind views of the actual screenshot** (desaturate; simulate deuteranopia, protanopia,
   tritanopia). Can every group still be told apart, or at least by its second cue?
4. **Dark mode is not the light palette on black.** Saturated colour on a dark surface vibrates and glares: lighten and
   desaturate slightly, and check no fill is brighter than it needs to be. Check the fill against the surface, and
   against the board border.
5. **Consistent visual weight.** No one hue should dominate the board (the lightest, most saturated one pulls the eye).
   Saturation and lightness should be evenly spread or deliberately graded.
6. **States survive the colours.** Selected, hint, error, conflict and "done" must stay distinct against every content
   hue (e.g. an error red must not be confusable with Rose or Coral content; a selection ring must contrast with all of them).
7. **Real size, worst case.** View at 375x537 and a tall phone, light and dark, with the busiest tier (most colours,
   adjacent groups touching). Squint test: step back, do the groups still separate?
8. **Harmony.** The set should feel like one family with the scheme (the moss and parchment theme); no neon, no muddy
   mid-tones, no two hues that vibrate when adjacent (saturated complements side by side).

## Checking

Render at true size in both schemes (`tools/render/render.py`, or the emulator) and ask: can I count at least two hues
that are not the accent or a grey? Are the dark-mode fills still distinguishable from the surface (`FF1B2F29`)? The
dark fills above sit at lightness 0.30 against the surface's 0.14: keep that gap.

## Status

The table and roles are implemented in `ui/theme/BoardHues.kt` (`fill`/`ink`/`mark`, `contentHues(n)`, `isDark`, `HUE_PAIRS`,
`pair`), pinned by `BoardHuesTest`. Adopted: Inequality, Sudoku, Tower (pegs; its feedback pips stay `onSurface`, filled vs. hollow). Mosaic (flood colours: Amber fill, Teal ink, Violet mark, Rose ink, Blue fill, in that order, the same in both schemes, so brightness differs as well as hue; a digit per area and on each swatch, its colour from `BoardHues.onFill`; pinned by `MosaicPaletteTest`). Adopting the rest board by board is in `docs/TODO.md`.
