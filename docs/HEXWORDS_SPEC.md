# Hexwords (working name)

**Name and id wait for the owner.** `id = "hexwords"` is hashed into every seed and keys every save, so the PR stays a draft until
the owner approves both. Inspired by a game a player likes; that game's name and art appear nowhere.

## The game

A block of letters in hexes. Drag through touching hexes to spell a word of three to seven letters (or tap them in turn and tap
the word). The word scores 3, 5, 8, 12 or 17 for three to seven letters, and its letters leave the board for good. Reach the target
to finish; the board shows the points to go. No lives, no way to lose: Undo puts a word back. A word may come again from other
letters.

## Decisions

- **Letters vanish on every tier, not only on Expert.** With letters that stay, the best player is whoever lists the most words:
  persistence, the word search `docs/TODO.md` rejects. A mechanic that changes between tiers would also need two sets of rules and
  two walkthroughs. So one rule, and the tiers differ in size and in which simple player the target sets out to beat.
- **Holes stay holes.** Nothing falls. Removal only ever takes options away, so reaching the target is a question about one set of
  hexes (a bitmask), and an exact search is cheap. It also means the board a player studies is the board they will play.
- **No word-once rule.** A second copy of a word uses other letters and scores again. The search would otherwise have to carry the
  words used, for a case that hardly arises.
- **Tiers beat a player, they do not vary slack.** All from the board's own common-word roads, and proved reachable:

  | Tier | Block | Words planted | Target |
  |---|---|---|---|
  | Standard | 19 hexes (3-4-5-4-3) | up to 5 letters | the middle of 12 biggest-word-first tries |
  | Hard | 29 hexes (5-6-7-6-5) | up to 6 | one more than the best of those tries |
  | Expert | 44 hexes (5-6-7-8-7-6-5) | up to 7 | one more than those and a player who looks one word ahead |

  The target is not set to the board's proved best: that would make every board one exact tiling, a test of vocabulary more than
  of planning. It sits as low as beating the player allows, so several roads usually reach it. (Measured below: 18 / 17 / 31% of
  targets happen to be the best anyway.)
- **Lists.** Proof and hints use `WheelWords.grid` (common, screened, clued). Any word `WheelWords.isWord` takes is accepted and
  scores, as Word Wheel's bonus rule has it; so a wide vocabulary helps, and that is measured too. No list was regenerated; no other
  game's board moved.
- **Generation** is pure: words planted end to end over every hex (`HexLogic.plant`: the most boxed-in free hex starts, the walk keeps
  to boxed-in neighbours, a walk that boxes itself in under three letters redraws; half run backwards, or a top row reads straight
  off as a word), so a board is made of words rather than noise.
  `HexLogic.roads` then finds every way any common word traces (exhaustive, in list order, no hash order). The planted words are thrown
  away: they are not stored and nothing reads them.
- **Proof:** `HexSearch.reach` is an exact search over the hexes left (the first free hex is on some road or wasted, with a bound
  and a memo), and its verdict is sealed: `HexReach.Yes(road)`, `No`, `Truncated`. Only `Yes` ships, only `No` calls a position lost.
  The budget counts positions, so it is the same on every platform; a run out of budget is its own verdict, never a `No`.
- **No stored answer**, so `solved` is the score rule and no hint can read one. The fallback, [`UNCHECKED`], is the search running out of
  budget; it says so.
- **Hints:** the biggest common word that keeps the target in reach, given by its crossword clue (`BIGGEST`). If the biggest word on
  the board would lose the target, the step says so and marks that word's hexes (`HOLD_BACK`): the game's lesson. `RARE` is for a
  position only an uncommon word can save: it has no clue, so it gives length and first letter. Only "Show me" spells a word; the
  highlight is the first hex alone.
- **A mistake** is a position `No` from every accepted word (the PR #15 rule). The step names the earliest word after which that was so,
  and Show me puts it back, with the words after it.
- **The board never says a target is lost.** Working that out is the game. It says only when no word at all is left, which the player
  could see; a hint says the rest. Undo and Restart are the way back.

## Measured

`HexwordsRulesTest` (120 daily boards, 20 tries each; written to `build/reports/hexwords-balance.txt`) and `HexwordsTeachingTest`
(a year a tier, `build/reports/hexwords-teaching-coverage.txt`).

| | Standard | Hard | Expert |
|---|---|---|---|
| Mean target | 21 | 41 | 71 |
| Biggest word first reaches it | 78% | 3% | 1% |
| ... knowing every accepted word | 77% | 37% | 14% |
| One word of lookahead reaches it | 100% | 86% | 0% |
| ... knowing every accepted word | 100% | 97% | 81% |
| Any word at random | 9% | 0% | 0% |
| Target is the board's proved best | 18% | 17% | 31% |
| Hint walk: biggest / hold back | 1265 / 7 | 1843 / 376 | 2745 / 499 |
| Hint fallback | 0 | 0 | 0 |
| Generation, JVM median / p95 / max ms | 0.3 / 0.6 / 1.5 | 0.6 / 1.9 / 2.9 | 13 / 67 / 127 |
| A hint, JVM median / p95 / max ms | 0.5 / 0.7 / 1 | 0.6 / 1 / 3.5 | 1.4 / 3.6 / 49 |

A year per tier never reaches `lastResort` (`FallbackTest`). The lookahead player tries every road on the board, which no person
does; its 86% on Hard is the top of what thought gets you, not what a person will see. Knowing every accepted word and trying every
road beats Expert's target 81% of the time: the gate is on common words, on purpose, since those are what the proof uses.

The search's own numbers: a few hundred positions on Standard and Hard, 2,000 to 14,000 on Expert (100,000 at worst with every accepted
word), against budgets of 2,000,000 (generator) and 300,000 (a hint).

## Not done

- **Whether a person enjoys Expert's target** is unmeasured: the numbers say what simple players reach, not how it feels. The owner should
  play Hard and Expert before approving; the knobs are `HexLogic.targetFor` and `specFor`.
- A themed daily was declined by the owner (2026-10-25: no curated lists).
- No Training course.
