# Codeword (a crossword with numbers for letters) — spec

A daily **codeword**: a filled crossword grid, words across and down with black squares between, in which every letter
has been replaced by a number. The same number is the same letter everywhere, different numbers are different letters,
and a few letters are given. Work out which letter each number stands for. Read `docs/PUZZLE_STANDARDS.md` first; this
file records only what is specific to this puzzle. Where the two disagree on a point not listed here, the standards win.

## Decisions

| | |
|---|---|
| `id` | `"codeword"` (hashed into every seed: never rename). Display name **Codeword**. Not Cipher: Cipher works back from columns and carries, Codeword from the shapes of words. |
| Words | Word Wheel's grid lists `WordWheelWords.GRID_3..7`: Lexicon's answer screening at every length (well known, no plurals or inflections), so 4 and 5 letters are exactly Lexicon's answers. Not widened, not edited (they are a contract of Lexicon's and Word Wheel's boards). |
| Grids | **Fully checked**: every white square is in an across word *and* a down word, every word 3 to 7 letters (no two-letter words: the lists have none, and a two-letter run would be a word nobody can check). Black squares are symmetric under a half-turn, the white squares are connected, no word appears twice in a grid. |
| Built offline | A dense fill from a 5,000-word list takes a backtracking search per grid (and many patterns have none), far too slow and too variable to run on a phone. `CodewordGridsBuildTest` (env-gated, fixed seed, reproducible) writes `puzzles/CodewordGrids.kt`: per tier, a sorted list of entries, each a filled grid and its given letters. The list's order is a contract: adding or removing an entry moves every daily board after it, and `BoardPinTest` goes red on purpose. |
| Numbers | Not stored in the list: the device numbers the grid's letters 1..k by a shuffle drawn from the board's `Rng`, so the same fill comes back with other numbers. |
| Answer | **Exactly one substitution** consistent with the numbers, the givens and the word lists, proved on the device by an exhaustive search (no budget, sealed verdict). The builder also proves it against the lists plus `WordWheelWords.BONUS` (Lexicon's guess rule, about 6,000 more real words), so a player who finds a less common word that fits has still found no second answer. `solved` checks the rules, not the stored letters. |
| Givens | A given letter is shown on every square of its number and cannot be changed. **Minimal for the tier's ladder**: the builder starts from every letter given and takes letters back, in a random order, while the ladder still finishes the board; every board keeps at least one. |
| Tiers | Size and ladder depth together (Tally's `Spec.needs` precedent), measured below. |
| Input | Tap a square or a number in the key to select that number; tap a letter (A to Z, under the key) to give it that letter, tap it again to clear. A letter another number holds is greyed with that number under it, never refused. Keyboard: a letter key types it, Backspace clears, arrows walk the numbers. |
| Selection | In the state (Cipher's precedent), dropped by `withoutSelection` so Undo takes back letters, not highlights. |

## Rules (player-facing)

- Every number stands for one letter, the same letter wherever it appears. Different numbers are different letters.
- Every across and down run of white squares is a word.
- A few letters are given. Find the rest: there is exactly one way.
- Tap a square or a number, then a letter. Tap the letter again to clear it.
- A word no word in the list can match, or a letter on two numbers, turns red.

## State (`puzzles/Codeword.kt`)

```kotlin
@Serializable data class CodewordState(
    val size: Int,                 // the grid is size x size
    val numbers: List<Int>,        // size*size, 0 for a black square, else 1..k
    val solution: List<Int>,       // k, the letter (0..25) of number i + 1
    val given: List<Int>,          // the given numbers, ascending
    val letters: List<Int>,        // k, the letter the player has for number i + 1 (givens included), -1 for none
    val selected: Int? = null,     // a number 1..k
    override val moves: Int = 0,
) : PuzzleState
```

Squares are `row * size + col`. Keep the names and meanings fixed once shipped (saves).

## Logic (`puzzles/CodewordRules.kt`)

- `runs(size, numbers)`: every across run then every down run, as square lists, in reading order.
- `isSolved`: every number has a letter, no letter on two numbers, every run spells a word of `WheelWords.grid`. The
  givens are not checked against anything (they cannot be changed).
- `solve(state shape, givens)`: an exhaustive search for every substitution, counted to two: the run with the fewest
  words still fitting is branched on, each word that fits assigns its numbers. Only `Verdict.ExactlyOne` carries an
  answer (`None`, `Several` otherwise). No budget, so it cannot end early and pass for a proof.
- `generateVerified(seed, difficulty)`: picks an entry with `Rng(seed + attempt)`, numbers it with the same `Rng`,
  requires the ladder to finish it from its givens and `solve` to find exactly one answer, which becomes the stored
  solution; null when no attempt proved one. `lastResort(difficulty)` is one fixed entry a tier, proved by a test.

## Teacher (`puzzles/CodewordTeacher.kt`)

`deduce` sees the numbers and the letters placed, never `solution`. Candidates are derived on every call: a placed
number has its letter, any other number every letter no other number holds. The words that fit a run are the list's
words of its length that agree with the candidates, give equal numbers equal letters and different numbers different
letters. Every step ends in one letter placed. Techniques, simplest first:

1. `one-word`: a run that only one word fits. Its first open number takes its letter.
2. `run-agrees`: every word that fits a run has the same letter for one of its numbers.
3. `crossing`: a number's letters, cut by every run it is in, leave one.
4. `all-words`: every run and every number worked through together (a letter pinned to one number is taken from the
   rest), to a fixpoint. Says so, as Cipher's chain does.

A fifth, `what-if` (a run with at most four words left: all but one, followed through step 4, leave some run with no
word), was built for Expert and dropped: no board kept needed it (Measured).

There is no "only number left for a letter": a grid uses a part of the alphabet, so a letter missing from every
number's candidates may simply not be in the grid. A mistake is a number whose letter differs from the stored answer,
sound because the answer is proved unique. The fallback names the first open number's letter from the answer and says
so; a board made here never reaches it. Highlight indices: squares `0 until size*size`, number key `KEY + n`, letter key
`LETTER + l`.

## The filler (for the mini crossword that may follow)

`test/CrosswordFill.kt` holds the pattern maker and the dense filler, free of anything Codeword-specific:
`CrosswordFill.pattern(rng, size, spec)` draws a symmetric, connected, fully checked block pattern;
`CrosswordFill.fill(rng, pattern, words, budget)` fills it from per-length word lists by backtracking (the slot with the
fewest words still fitting first, each slot's words tried in an order shuffled by `rng`), no word twice, giving up after
`budget` nodes. A mini crossword would call the same two with its own clued list and size.

## Measured

- **Tiers as built:** Standard 7x7 (runs to 7 letters, about 15 black squares), finished one run at a time (`one-word`,
  `run-agrees`); Hard 9x9 (runs to 6, about 27 black), must need `crossing`; Expert 11x11 (runs to 6, about 41 black), must
  need `all-words`. Givens, minimal for each tier's ladder: 1.8 / 1.1 / 1.0 a board. Letters in play: 14.4 / 17.6 / 19.6.
  From a single given letter the lists are so tight that the shallow steps finish most grids, so Hard and Expert are cut
  by what they need, not by their givens. `what-if` was built and dropped: with Expert taking letters back under step 4,
  no board needed it (0 of 1,440 draws).
- **The build:** 1,000 grids a tier, from 2,880 / 2,880 / 54,720 draws (fill failures within a 20,000-node budget, the
  tier's depth test, and about one fill in four losing uniqueness once `BONUS` words are allowed). 12 minutes on 12
  desktop threads, almost all of it Expert. Scattering black squares (no 2x2 block) made fills nearly impossible
  (2 in 480 at 7x7), so blocks are allowed. `CodewordGrids.kt` is 256 KB of source.
- **On the device (JVM, a year of daily boards):** generation median / p95 / max 1 / 3 / 3 ms Standard, 5 / 8 / 10 Hard,
  8 / 14 / 20 Expert (`CodewordRulesTest`); a hint median / p95 0.1 / 0.2 ms Standard, 0.2 / 0.5 Hard, 0.4 / 0.9 Expert,
  max 2 ms. Steps a board, hints alone from the givens (200 boards a tier, no fallback): one-word 12.0 / 14.4 / 16.4,
  run-agrees 0.5 / 0.5 / 0.4, crossing 0 / 1.6 / 0.7, all-words 0 / 0 / 1.1.
