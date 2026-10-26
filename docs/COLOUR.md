# Colour: the board palette

Why this exists: the app theme (`ui/theme/Palette.kt`) is deliberately quiet, ink and parchment with moss. A board that
draws only with `colorScheme` plus its one `accent` comes out beige and one-note (one board shipped that way: surface
grey, one teal). The boards that read well (Shikaku's hue-per-region, Nonogram's blended pairs, Kings' regions) all
bring several hues. This is the shared set to bring them from, derived from the colours already shipped.

## Three layers

1. **Structure** is always `MaterialTheme.colorScheme`: board background, grid lines, given digits, text, `error`,
   hint glow. Never a hex literal, so light and dark both work.
2. **Identity** is the puzzle's `accent` (home tile, selection, walkthrough buttons, the finish card). One per puzzle, one
   colour for both schemes, chosen as a set (see **Accents** below): every raw accent reaches 3:1 on the surface and
   background of both schemes, no accent reads as the error red or the success green, and the closest pair is as far
   apart as the method could make it (`AccentSpacingTest`, `ContrastTest`). Text in an accent goes through `textOn`, or
   `onTint` on the accent's own tint.
3. **Content** is the hue family below. Anything the player *places, groups or compares* (regions, tokens, runs,
   paths, a solved flourish) takes its colour from here, not from the surface greys.

## Accents

The thirty-one accents were redesigned together in October 2026. Before that each new puzzle took whatever was furthest
from the rest, and the set had drifted: the closest pair was 1.59 CIEDE2000 apart under normal vision (Regions/Target, with
Cage Sudoku and the Crossword's violet beside them) and 1.20 under protanopia, about a just-noticeable difference, and
Pearls' pale olive (`C7C785`) and Codeword's aqua (`65E7E7`) were 1.7:1 and 1.4:1 on the light page.

**Method.** One set, solved at once, not one colour at a time:

1. **One luminance band for both schemes.** A raw accent reaches WCAG 3:1 on the light background (`F6F1E7`) only below
   relative luminance 0.26, and on the dark surface (`1B2F29`) only above 0.175, so every accent is in 0.18-0.255 (OKLCH
   lightness 0.56-0.65). Any accent can then be drawn raw as a mark (icon, ring, board stroke, dot) on either scheme
   without a lookup. The band is narrow, which is why lightness alone cannot separate the set; it also rules out pale
   yellows and aquas, which vanish on the light page.
2. **Reserved hues left empty.** Nothing within OKLCH hue 358-58 (the error Clay is 33) or 129-179 (the success green is
   154), so no accent reads as wrong or right. In the test's CIELAB terms: hue 355-64 and 125-179, open at both ends.
3. **Chroma 0.09-0.18 OKLCH** (CIELAB 28-72): no greys or muddy mid-tones, no neon.
4. **Maximin spacing.** From every colour on an OKLCH grid (lightness 0.01, chroma 0.01, hue 5 degrees) that keeps rules
   1-3, pick 31 so the closest pair is as far apart as possible, by CIEDE2000 under normal vision or twice the worst of
   simulated deuteranopia, protanopia and tritanopia (Machado 2009, `ColourDistance`), whichever is smaller. Weighting
   normal vision by two keeps colour-blind vision from flattening the whole set into the one axis it keeps; unweighted,
   normal vision fell to 3.9. Farthest-point swaps from 300 random starts; the best start won.
5. **Families, then neighbours.** The colours fall into hue sectors, and related puzzles share one: blue for the number
   grids (Sudoku, Cage Sudoku, Regions, Inequality), teal for the word games and Pipes (Lexicon, Word Wheel, Crossword,
   Codeword), ochre and olive for the earthy ones (Sand, Codebreaker, LITS, Tents, Mate, Shikaku, Pearls, Lamps), and
   violet to rose for the rest. Within a sector, colours were placed to keep the closest pairs apart where they meet:
   neighbours on the default home grid (`PuzzleOrder.GENTLEST` at three to seven columns) and puzzles from different
   Today's three slots (puzzles in one slot never show together). No pair under 5.9 is a grid neighbour; the closest
   pairs meet only in Today's three (Atoms and Target, Atoms and Lexicon, Kings and Who's Who: a quick or mid-weight
   puzzle against another slot's, about one day in 77 to 143).

**Result.** Closest pairs: normal 5.79 (Atoms/Target), deuteranopia 2.91 (Atoms/Lexicon), protanopia 2.90 (Kings/Who's
Who), tritanopia 3.01 (Cage Sudoku/Word Wheel). `AccentSpacingTest` pins 5.7 and 2.85. Several pairs are still near
alike to a colour-blind eye: no 31 colours in one luminance band can be told apart by colour alone, which is why every
tile also carries the name and a motif.

**Where each threshold applies** (`ContrastTest` covers every accent in both schemes):

| Use | Ground | Needs | Through |
|---|---|---|---|
| Marks: icons, rings, board strokes, Levels' dots, the Continue arrow | surface, background | 3:1 | the raw accent |
| Text in the accent: play-screen tagline, Statistics names and links, walkthrough feedback, achievement titles | surface, background | 4.5:1 | `textOn(accent, ground)` |
| Text on the accent's own tint: tile names and footers, Today's three, archive days and tiers, finish title, hint button | accent at 0.2-0.22 over the ground | 4.5:1 | `onTint`, `tileInk` |
| Ticks and borders on a tint or a plain chip: tier dots, archive ticks and selection, chosen pen, Who's Who's choice | tint or surface | 3:1 | `onTint(.., 3f)`, `textOn(.., 3f)` |
| Labels on an accent-filled button (walkthrough, training) | the accent | 4.5:1 | `onFill(accent)` (black on every accent) |

Board internals that tint or glow with the accent at partial alpha (move feel, Sets' card borders) are decoration, not
marks; the board's own colours carry its meaning.

| Puzzle | Accent | OKLCH (L C h) | Luminance | On light surface / background | On dark surface / background | Nearest (design distance) |
|---|---|---|---|---|---|---|
| **Blue: number grids** | | | | | | |
| Cage Sudoku | `528DC9` | 0.63 0.110 250 | 0.251 | 3.4 / 3.1 | 4.0 / 4.8 | Word Wheel 6.0 |
| Inequality | `1E76D8` | 0.57 0.170 255 | 0.182 | 4.4 / 4.0 | 3.1 / 3.7 | Dominoes 6.5 |
| Regions | `5C72BE` | 0.57 0.120 270 | 0.180 | 4.4 / 4.1 | 3.1 / 3.7 | Nonogram 6.3 |
| Sudoku | `6F7DF7` | 0.64 0.180 275 | 0.248 | 3.4 / 3.1 | 4.0 / 4.8 | Sun & Moon 6.1 |
| **Ochre and olive: earth** | | | | | | |
| Tents | `BE6B0B` | 0.61 0.140 60 | 0.215 | 3.8 / 3.5 | 3.6 / 4.2 | Mate 6.4 |
| Codebreaker | `AF7D4C` | 0.63 0.090 64 | 0.243 | 3.5 / 3.2 | 3.9 / 4.7 | LITS 6.0 |
| Mate | `BE7C1C` | 0.64 0.130 70 | 0.254 | 3.3 / 3.1 | 4.1 / 4.9 | Tents 6.4 |
| Pearls | `967028` | 0.57 0.100 80 | 0.182 | 4.4 / 4.0 | 3.1 / 3.7 | Lamps 6.2 |
| Shikaku | `8D813D` | 0.60 0.090 99 | 0.217 | 3.8 / 3.5 | 3.6 / 4.3 | Sand 5.9 |
| Sand | `8A8C34` | 0.62 0.110 110 | 0.244 | 3.5 / 3.2 | 4.0 / 4.7 | Shikaku 5.9 |
| Lamps | `737B39` | 0.56 0.090 115 | 0.181 | 4.4 / 4.0 | 3.1 / 3.7 | Pearls 6.2 |
| LITS | `7A9052` | 0.62 0.090 125 | 0.247 | 3.4 / 3.1 | 4.0 / 4.8 | Codebreaker 6.0 |
| **Teal: words and water** | | | | | | |
| Crossword | `098777` | 0.56 0.100 180 | 0.187 | 4.3 / 3.9 | 3.2 / 3.8 | Tally 6.0 |
| Codeword | `0F9B89` | 0.62 0.110 181 | 0.254 | 3.3 / 3.1 | 4.1 / 4.9 | Word Wheel 6.7 |
| Lexicon | `278E8E` | 0.59 0.090 195 | 0.217 | 3.8 / 3.5 | 3.6 / 4.3 | Atoms 5.8 |
| Word Wheel | `2891AC` | 0.61 0.099 219 | 0.237 | 3.5 / 3.3 | 3.9 / 4.6 | Cage Sudoku 6.0 |
| Pipes | `1C7EAB` | 0.56 0.109 235 | 0.181 | 4.4 / 4.0 | 3.1 / 3.7 | Who's Who 6.0 |
| **Violet to rose: the rest** | | | | | | |
| Sun & Moon | `8C79E0` | 0.64 0.151 290 | 0.246 | 3.4 / 3.1 | 4.0 / 4.8 | Sudoku 6.1 |
| Dominoes | `8F5DD6` | 0.59 0.180 300 | 0.185 | 4.3 / 4.0 | 3.2 / 3.8 | Nonogram 6.1 |
| Who's Who | `846CAD` | 0.58 0.101 300 | 0.186 | 4.3 / 3.9 | 3.2 / 3.8 | Kings 5.8 |
| Sets | `997EB8` | 0.64 0.090 305 | 0.252 | 3.4 / 3.1 | 4.1 / 4.8 | Cipher 6.2 |
| Nonogram | `A258C1` | 0.59 0.169 315 | 0.185 | 4.3 / 4.0 | 3.2 / 3.8 | Dominoes 6.1 |
| Kings | `94669F` | 0.58 0.100 320 | 0.183 | 4.4 / 4.0 | 3.1 / 3.7 | Who's Who 5.8 |
| Arrows | `B96BCB` | 0.65 0.160 320 | 0.251 | 3.4 / 3.1 | 4.1 / 4.8 | Sun & Moon 6.7 |
| Cipher | `A879AA` | 0.64 0.090 325 | 0.249 | 3.4 / 3.1 | 4.0 / 4.8 | Sets 6.2 |
| Atoms | `B2659A` | 0.61 0.119 340 | 0.211 | 3.9 / 3.6 | 3.5 / 4.2 | Target 5.8 |
| Mosaic | `BC4C9C` | 0.59 0.171 340 | 0.183 | 4.4 / 4.0 | 3.1 / 3.7 | Atoms 6.2 |
| Target | `B47699` | 0.64 0.090 345 | 0.250 | 3.4 / 3.1 | 4.0 / 4.8 | Atoms 5.8 |
| Tally | `A26482` | 0.58 0.089 350 | 0.184 | 4.3 / 4.0 | 3.2 / 3.8 | Crossword 6.0 |
| Trail | `CA4383` | 0.59 0.180 355 | 0.182 | 4.4 / 4.0 | 3.1 / 3.7 | Mines 6.2 |
| Mines | `D45C91` | 0.64 0.161 355 | 0.237 | 3.5 / 3.3 | 3.9 / 4.6 | Trail 6.2 |

Design distance is CIEDE2000 under normal vision or twice the worst colour-blind one, whichever is smaller.

**A new puzzle's accent:** run `AccentSpacingTest`. It prints, of the hsl colours that keep rules 1-3, the one furthest
from every accent by the design distance (now `0xFF96754A`, a brown, 6.1 from its nearest), and taking it keeps the floors.
A redesign of the whole set is the scripts' job, not the picker's; the method above is enough to repeat it.

## The hue family

Eight hues about 45 degrees apart, each in four steps built with one HSL recipe (the one Shikaku's `hue()` already
uses), so any two hues sit at the same weight and can share a board. Light/dark is chosen by the scheme.

| Hue | h | Fill, light | Fill, dark | Ink, light | Ink, dark | Mark (either) |
|---|---|---|---|---|---|---|
| Coral | 8 | `E8A69C` | `6B362E` | `963A2C` | `D67E71` | `CD6251` |
| Amber | 34 | `E8C79C` | `6B512E` | `92652A` | `D6AA71` | `CD9751` |
| Gold | 46 | `E8D69C` | `6B5D2E` | `7E6A25` | `D6BE71` | `CDB051` |
| Green | 145 | `9CE8BB` | `2E6B47` | `247B48` | `71D69B` | `51CD85` |
| Teal | 175 | `9CE8E1` | `2E6B66` | `227770` | `71D6CD` | `51CDC2` |
| Blue | 215 | `9CBBE8` | `2E476B` | `2C5896` | `719BD6` | `5185CD` |
| Violet | 268 | `BF9CE8` | `4A2E6B` | `5D2C96` | `AA81DA` | `8B51CD` |
| Rose | 330 | `E89CC2` | `6B2E4D` | `962C61` | `D671A3` | `CD518F` |

Recipe: fill = hsl(h, .62, .76) light / hsl(h, .40, .30) dark; ink = hsl(h, .55, .38) light / hsl(h, .55, .64) dark,
stepped a hundredth darker (light) or lighter (dark) until it reaches 4.5:1 on both the surface and the background
(Amber .37, Gold .32, Green .31, Teal .30 in light; Violet .68 in dark; `ContrastTest`); mark = hsl(h, .55, .56). **Fill** is a cell or region background and carries `onSurface` text. **Ink** is text or a thin
stroke drawn on the surface. **Mark** is a solid shape (token, path, dot). Generate in code with `Color.hsl(h, s, l)`
from the table's `h`; do not paste the hexes in (they are for reading and for the harness).

## Rules

- **Two content hues minimum, five at most per board.** One hue is a monochrome board; six or more cannot be told apart.
  When a board needs N distinct groups, take N hues spread across the table (every other row first: Coral, Green,
  Violet, Amber, Teal, Rose), not neighbours.
- **More than five groups: steps, not new hues.** Excluding Coral and Green leaves six hues, and Amber and Gold sit 12
  degrees apart. A board that needs up to eight equal tokens (Tower's pegs) takes them from the six hues at three steps,
  the same in both schemes: pale = `fill(h, false)`, mid = `mark(h)`, deep = `deep(h)` (the fixed recipe `ink` starts from, so these palettes keep their pinned spacing). Choose and order them by
  CIEDE2000 including deuteranopia and protanopia so every prefix is as far apart as it can be (Tower: 21 / 17 / 11 for the
  first 5 / 6 / 8, pinned by `TowerPaletteTest`), and put a digit or shape on every token: the colour-blind distance of an
  eight-token set is too small to carry it alone.
- **Mix steps to spread brightness, not only hue.** Five hues all at `mark` sit within a 1.4:1 luminance band and collapse to one
  grey. Giving each hue its own step (Mosaic: pale `fill`, deep `ink`, mid `mark`) gives four luminance levels and raised the
  5-colour CIEDE2000 floor (colour-blind included) from 6.6 to 15.8. Search the 3 steps x hue order, scoring all four vision modes.
- **Semantic colours are fixed and are not content hues.** Correct = Green mark, present/partial = Gold (Lexicon's
  `4E9F6C` / `D1A32F` are these two), error = `scheme.error` (Clay, stepped per scheme to read as text at 4.5:1: `AD4D39` light, `D08271` dark), warning is the error colour too. Do not
  spend Coral or Green on an unrelated group *in a board that also shows right/wrong*; use the other six.
- **Thin graphics on the surface take `ink`, not `mark`.** `mark` is one lightness for both schemes and falls under 3:1 against
  the light card for the lighter hues; check WCAG 3:1 for strokes and shapes, 4.5:1 for text, in both schemes (Sets did).
- **An accent drawn as text goes through `BoardHues.textOn(accent, background)`** (the same hue, mixed towards black or
  white until it reaches 4.5:1; pass 3f for a dot or icon). Fills, rings and tints keep the raw accent. Home tiles use
  `tileInk`, which reads on the tile whether or not it is tinted. `ContrastTest` covers every accent in both schemes.
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

The table and roles are implemented in `ui/theme/BoardHues.kt` (`fill`/`ink`/`mark`, `isDark`, `HUE_PAIRS`,
`pair`), pinned by `BoardHuesTest`, which also checks the every-other-row spread above. Adopted: Inequality, Sudoku and Cage Sudoku (the same pair and alphas through the shared `SudokuGridBoard`; hue A also draws the dashed cages, in `ink`), Tower (pegs; its feedback pips stay `onSurface`, filled vs. hollow). Mosaic (flood colours: Amber fill, Teal ink, Violet mark, Rose ink, Blue fill, in that order, the same in both schemes, so brightness differs as well as hue; a digit per area and on each swatch, its colour from `BoardHues.onFill`; pinned by `MosaicPaletteTest`). Sets (three card colours: the `ink` step of Amber, Teal, Violet, per scheme, because the shapes are thin strokes on the card surface and `mark` measured only 1.9-2.5:1 on light (teal, amber) and 2.8:1 on dark (violet), while `ink` is 3.5:1 or better in both; at least 20 apart by CIEDE2000 under deuteranopia, protanopia and tritanopia; Amber/Teal are the close pair in lightness, so hue and the card's other three traits carry them; Sets shows right/wrong; index i keeps meaning colour i in saves; pinned by `SetsPaletteTest`). Lits (piece colours: Blue fill, Teal ink, Violet mark, Rose ink for I, L, T, S, the same in both schemes; Amber/Gold avoided because the in-progress tone is the accent's tan; the letter on each in `BoardHues.onFill`; pinned by `LitsPaletteTest`). Built on it since, each described under its puzzle in CLAUDE.md: Cipher, Codeword, Dominoes, Crossword (the mini), Pointers, Regions, Sand, Tally, Target, Tents and Word Wheel. Adopting the rest board by board is in `docs/TODO.md`.
