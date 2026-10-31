# Dwindle: spec and decisions

Working name, `id = "dwindle"`. **Neither is approved: the owner chooses before it ships, because the id is hashed into every seed and can
never change.** Inspired by a game a player likes; its name, "Word Tiles" and its art are never used.

## The game

A grid of letter tiles and a dashed list of goal words, filled in when found. Most tiles are used by more than one goal, and each shows how many goals
still run through it. A tile with none left vanishes, so a board starts crowded and gets easier. Spell by swiping through touching tiles (or tapping them,
or typing). Other real words are bonus words, as in Word Wheel, and use nothing up. No lives, no way to lose.

## Decisions

**Tiles do not fall.** Falling would make new neighbours depending on the order words are found, so a goal could be cut off or a new way open, and the
proof would become a search over every order. It would also move the board under the finger (CLAUDE.md, "Feedback must not move the board") and make a hint
go stale. A vanished tile leaves a faint place, and the neighbours stay as they were.

**Diagonals count.** Both were built and measured (200 draws a tier, same specs): orthogonal only draws boards about as often but shares less, 55 / 69 / 75%
of tiles used twice or more before the gate against 55 / 73 / 82%. Eight neighbours also let a word wander, so it cannot be read along a row, and they are what
players of this family of games expect. The cost is mis-swipes, so while swiping a tile answers only within 0.42 of a tile of its middle: the corner a diagonal
crosses is nobody's (`Dwindle.tileAt`, tested). This is a judgement rather than a forced result; orthogonal stays possible if play says otherwise.

**A goal has exactly one way on the full grid, and that is the whole proof.** Taking tiles away can only remove ways of spelling a word, never add one, because nothing
falls. A tile on a goal's way counts that goal until it is found, so the way stays whole until then, whatever else has been found. So **no order can get stuck**, and a
word swiped is the goal, on its stored tiles, with no choice to make wrong. `DwindleLogic.proved` checks it with `ways`, an exhaustive count with no budget returning
`Verdict` (only `ExactlyOne` carries a way). `DwindleOracle` does not rely on the argument: it writes out every walk of tiles as text and visits all 2^n subsets of
found goals, and asserts each goal left can still be spelled.

**Bonus words use nothing up.** Otherwise a bonus word could spend a tile a goal needs. They must be on tiles still live, and they use `WheelWords.isWord`, so a real word
no Word Wheel root can spell is refused (see Rough).

**Goals come from `WheelWords.grid`**, Word Wheel's screened and clued lists, read only. Nothing is regenerated, so no other word game's boards move. A goal's list
position does not say when it was laid (longest first, ties shuffled).

## Generation

`DwindleLogic.build`: cover, then share. Pool: the tier's grid words, seed-shuffled from sorted lists. While a square is empty: take the most boxed-in, and walk the pool (at
most `MAX_SCAN` words, then the draw is abandoned) for a word that can run through it over at least the tier's share of lettered tiles, with every word so far keeping one
way. Then add words the full grid already holds, one way each, those over the least-used tiles first, to the tier's count. `accepts` wants the word band, the shared share and
`proved`. `generateVerified` retries `Rng(seed + attempt)` up to 40 times; `lastResort` is seed 1's board. A year per tier never reaches it (`FallbackTest`). Only lists and
probes: no hash container is iterated.

## Tiers

| | Standard | Hard | Expert |
|---|---|---|---|
| Tiles | 4x4 | 5x5 | 6x6 |
| Words, gate | 6-7 | 10-12 | 15-18 |
| Longest word | 5 | 6 | 7 |
| Share of tiles used 2+ times, gate | 55% | 65% | 75% |

Measured over 365 daily boards a tier (`DwindleRulesTest`, `build/reports/dwindle-tiers.txt`, which asserts words, sharing and uses all rise):

| | Standard | Hard | Expert |
|---|---|---|---|
| Mean words | 6.8 | 11.3 | 16.8 |
| Mean tiles shared (worst) | 66% (56%) | 78% (68%) | 86% (75%) |
| Mean uses a tile | 1.83 | 2.14 | 2.32 |
| JVM generation ms, median / p95 / max | 1.0 / 4.7 / 13.8 | 2.0 / 6.7 / 28.7 | 5.6 / 22.0 / 285.6 |

Browser generation over the same year, median / p95 / max ms: Chromium 1 / 4 / 8, 4 / 13 / 61, 9 / 38 / 512; WebKit 3 / 8 / 13, 7 / 22 / 67, 16 / 61 / 808. The Expert
maximum is one slow day of draws abandoned at `MAX_SCAN`; it sits behind the "Setting out" screen.

## Hints

`DwindleTeacher`. `deduce` sees the letters, the counts, the open dash lengths and the words tried. A word *can be* a goal if it is on the public list, has an open dash, was not
tried, has one way on the full grid and that way is all live tiles. Then, simplest first:

- `through`: a tile whose uses left equal the words that can run through it: each is a goal.
- `length`: a length whose open dashes equal the words of that length that can be goals.
- `all-tiles`: a word in every way of using up all the tiles and dashes (`covers`, a search capped at `MAX_NODES`; running out returns `Truncated`, and the rung does not fire).
- `clue`: the fallback. It reads a stored goal through the tile with the fewest spare words, and says it does.

Every step gives the word's crossword clue. Only "Show me" enters it, and no more than one tile glows, since the word's own tiles would spell it. No mistake exists.

Fallback, hints alone over a year a tier: 19.5 / 14.9 / 17.5% of steps; boards needing no clue 132 / 110 / 84 of 365 (`build/reports/dwindle-teaching-coverage.txt`). Without
`all-tiles` it was 59 / 64 / 65%. Requiring boards with no clue was not done: it would keep about a quarter of draws and cost a cover search per draw.

## Left out

The player's marked-letter daily scoring mode: `docs/TODO.md`.

## Rough

- `all-tiles`' explanation states the conclusion, not the working (Cage's and Cipher's chain steps have the same gap). It is the commonest step.
- Bonus words are Word Wheel's: a real word its roots cannot spell is "not in the word list" (rarer letters especially).
