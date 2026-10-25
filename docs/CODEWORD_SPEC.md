# Codeword (a crossword with numbers for letters) — spec

A daily **codeword**: a filled crossword grid, words across and down with black squares between, in which every letter
has been replaced by a number. The same number is the same letter everywhere, different numbers are different letters,
and a few letters are given. Work out which letter each number stands for. Read `docs/PUZZLE_STANDARDS.md` first; this
file records only what is specific to this puzzle. Where the two disagree on a point not listed here, the standards win.

## Decisions

| | |
|---|---|
| `id` | `"codeword"` (hashed into every seed: never rename). Display name **Codeword**. Not Cipher: Cipher works back from columns and carries, Codeword from the shapes of words. |
| Words | Word Wheel's grid lists `WordWheelWords.GRID_3..7`: Lexicon's answer screening at every length (well known, no plurals or inflections), so 4 and 5 letters are exactly Lexicon's answers. Not widened, not edited (they are a contract of Lexicon's and Word Wheel's boards). The builder skips a few words those lists hold that no grid should show (`CodewordGridsBuildTest.SKIP`); they stay in the lists, so they are still words to the rules and the proof, which only makes the proof stronger. |
| Grids | **Fully checked**: every white square is in an across word *and* a down word, every word 3 to 7 letters (no two-letter words: the lists have none, and a two-letter run would be a word nobody can check). Black squares are symmetric under a half-turn, the white squares are connected, no word appears twice in a grid. |
| Built offline | A dense fill from a 5,000-word list takes a backtracking search per grid (and many patterns have none), far too slow and too variable to run on a phone. `CodewordGridsBuildTest` (env-gated, fixed seed, reproducible) writes `puzzles/CodewordGrids.kt`: per tier, a sorted list of entries, each a filled grid and its given letters. The list's order is a contract: adding or removing an entry moves every daily board after it, and `CodewordWebParityTest` goes red on purpose. |
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
5. `what-if`: a run with at most four words left: all but one of them, followed through step 4, leave some run with no
   word.

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

Filled in by the build; see the bottom of this file once it lands.
