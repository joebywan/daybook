# Dwindle: spec and decisions

`id = "dwindle"` is hashed into every seed: never rename it. Inspired by a game a player likes; its name, "Word Tiles" and its art are never used.

## The game

A grid of letter tiles and a dashed list of goal words, filled in when found. Most tiles are used by more than one goal, and each shows how many goals
still run through it. A tile with none left vanishes, so a board starts crowded and gets easier. Spell by swiping through tiles that share a side (or tapping them,
or typing). Other real words are bonus words, as in Word Wheel, and use nothing up. No lives, no way to lose.

## Decisions

**Tiles do not fall.** Falling would make new neighbours depending on the order words are found, so a goal could be cut off or a new way open, and the
proof would become a search over every order. It would also move the board under the finger (CLAUDE.md, "Feedback must not move the board") and make a hint
go stale. A vanished tile leaves a faint place, and the neighbours stay as they were.

**Diagonals do not count (2026-11-01; the first build had them).** Play said so: 35 bonus words and one goal, and the owner gave up. Common words the tiles spell besides
the goals, mean a board over 60 days: 58 / 123 / 196 with diagonals, 14 / 30 / 47 without, and 8 / 18 / 32 with the fewest of six draws kept. Circular tiles, floated to
ease diagonal swipes, are not needed without them. A save from the first build has no `diagonal` key, which reads as true, so its diagonal goals stay spellable.

**Fewest bonus words of six.** `generateVerified` takes the first `KEEP` = 6 proved draws and ships the one with the fewest `commonBonus` words: the words a player tries
before reaching the goals. Common words (`WheelWords.grid`) are counted, not every accepted word, since those are what get tried.

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
| Mean words | 6.9 | 11.5 | 17.1 |
| Mean tiles shared (worst) | 63% (56%) | 75% (68%) | 80% (75%) |
| Mean uses a tile | 1.75 | 2.01 | 2.13 |
| JVM generation ms, median / p95 / max (a loaded machine) | 6.3 / 10.7 / 17.2 | 13.2 / 25.0 / 35.2 | 29.4 / 50.0 / 75.0 |

Browser generation was last measured before the six draws (Expert median 9 ms in Chromium, 16 in WebKit), so expect about six times that; it sits behind the "Setting out" screen.

## Hints

`DwindleTeacher`. `deduce` sees the letters, the counts, the open dash lengths and the words tried. A word *can be* a goal if it is on the public list, has an open dash, was not
tried, has one way on the full grid and that way is all live tiles. Then, simplest first:

- `through`: a tile whose uses left equal the words that can run through it: each is a goal.
- `length`: a length whose open dashes equal the words of that length that can be goals.
- `all-tiles`: a word in every way of using up all the tiles and dashes (`covers`, a search capped at `MAX_NODES`; running out returns `Truncated`, and the rung does not fire).
- `clue`: the fallback. It reads a stored goal through the tile with the fewest spare words, and says it does.

Every step gives the word's crossword clue. Only "Show me" enters it, and no more than one tile glows, since the word's own tiles would spell it. No mistake exists.

Fallback, hints alone over a year a tier: 3.7 / 4.2 / 4.2% of steps; boards needing no clue 287 / 227 / 188 of 365 (`build/reports/dwindle-teaching-coverage.txt`). Requiring boards with no clue was not done: it would keep about a quarter of draws and cost a cover search per draw.

## Left out

The player's marked-letter daily scoring mode: `docs/TODO.md`.

## Rough

- `all-tiles`' explanation states the conclusion, not the working (Cage's and Cipher's chain steps have the same gap). It is the commonest step.
- Bonus words are Word Wheel's: a real word its roots cannot spell is "not in the word list" (rarer letters especially).
