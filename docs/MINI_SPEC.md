# Mini crossword — spec

A daily **mini crossword**: a small, fully checked grid of words across and down with black squares between, and a
clue for every word. Fill the grid. Read `docs/PUZZLE_STANDARDS.md` first; this file records only what is specific to
this puzzle. Where the two disagree on a point not listed here, the standards win.

## Decisions

| | |
|---|---|
| `id` | `"mini"` (hashed into every seed: never rename). Display name **Crossword** ("Mini Crossword" took two lines on a three-column home tile); the tagline says mini. Not named after any newspaper's mini. |
| Words | Word Wheel's grid lists `WordWheelWords.GRID_3..7` (Lexicon's answer screening at every length; 4 and 5 letters are exactly Lexicon's answers), less a breakfast-table list (`MiniGridsBuildTest.SKIP`: death, murder, vomit and the like, which those lists keep because a guess or a wheel word is never shown under a clue). The lists themselves are not edited: they are Lexicon's and Word Wheel's contract. |
| Second clues | `tools/words/second-clues.txt`, compiled into `MiniSecondClues.kt` by `build.py` (`--second-clues` alone needs no inputs): one per word the grids use, hand-written from a different angle than the first (another sense, a fill-in-the-blank, a category), never containing the word, never any grid word's first clue, no two alike (`MiniSecondCluesTest`). Stored on the board as `secondClues`, as the clues are. The owner: "I'd rather a 2nd clue before giving the answer." |
| Clues | `WheelWords.clue`: `tools/words/clues.txt` for four and five letters (Lexicon's), `tools/words/wheel-clues.txt` for three, six and seven. Hand-written, neutral English, linted by `build.py`. **No two answers share a clue** (`LexiconCluesTest`, `WordWheelRulesTest`), so a clue names exactly one word. The board stores its clue texts, so a saved game keeps its clues if a clue is reworded later. |
| Grids | **Fully checked**: every white square is in an across word *and* a down word, every word 3 letters or more. Black squares symmetric under a half-turn, white squares connected, no word twice in a grid. |
| Built offline | `MiniGridsBuildTest` (env-gated, fixed seed, reproducible) writes `puzzles/MiniGrids.kt` with Codeword's pattern maker and filler (`test/CrosswordFill.kt`): per tier, a sorted list of filled grids. The list's order is a contract: adding or removing an entry moves every daily board after it, and `BoardPinTest` goes red on purpose. Offline because a dense fill is a backtracking search per pattern whose cost varies (two 7x7 patterns in five give up within the budget), and because the tier test walks the hints over every candidate. |
| Answer | Unique by the clues (each clue names one word). `solved` checks the rules: every slot spells the word whose clue the slot shows (`MiniLogic.isSolved`), never the stored answer. |
| Proof | The device takes an entry and proves it again before it ships (`MiniLogic.board`): every slot a different grid word with a clue, the hints' walk finishes it with its clue share in the tier's band, and an exhaustive search over the grid lists (no budget, sealed `Verdict`) finds exactly one fill given only the letters the walk read from the clues. So the crossings, not luck, settle everything the clues did not. `MiniOracle` (test: square by square over the alphabet, prefix sets, no slot list) is the independent check. |
| Tiers | Size and **clue share** together: the letters the hints' walk must read from the clues, as a share of the white squares. Measured below. |
| Input | Tap a square to put the cursor there; tap it again (or the clue bar) to turn between Across and Down. Type on the keys under the grid; the cursor moves on along the word, to the next empty square. Del clears the cursor's letter, or with none steps back and clears that. ‹ and › on the clue bar go to the previous and next word. |
| Selection | The cursor and its direction are in the state (Codeword's and Cipher's precedent), dropped by `withoutSelection` so Undo takes back letters, not cursor moves. |

## Rules (player-facing)

- Every row and column of white squares is a word, and each word has a clue: 1 Across, 2 Down and so on.
- Tap a square, then type. Tap it again to turn between Across and Down, or tap the clue.
- Letters cross: a letter in one word is a letter of the word across it.
- A word that is no word in the list turns red. Each clue has exactly one answer.

## State (`puzzles/Mini.kt`)

```kotlin
@Serializable data class MiniState(
    val size: Int,                 // the grid is size x size
    val answer: String,            // size*size, '#' black, else the answer's letter
    val clues: List<String>,       // one per slot, across then down, in MiniLogic.slots order
    val letters: String,           // size*size, '#' black, ' ' open, else the player's letter
    val cursor: Int? = null,       // the selected square
    val across: Boolean = true,    // the way the cursor reads
    override val moves: Int = 0,
    val secondClues: List<String> = emptyList(), // one per slot, as clues: the fallback's first tap (absent in old saves)
) : PuzzleState
```

Squares are `row * size + col`. Keep the names and meanings fixed once shipped (saves).

## Logic (`puzzles/MiniRules.kt`)

- `slots(size, grid)`: every across slot then every down slot, in reading order of their first squares, numbered as a
  printed crossword is (a square starting a word either way takes the next number).
- `isSolved`: every slot spells a grid word whose clue is the slot's clue.
- `deadSlots`: a slot full and spelling no word of the list. Shown red after a 1 s settle. A real word under another
  clue is not flagged: that would be reading the answer.
- `solve(size, letters)`: every fill of the open squares in which every slot is a grid word, counted to two, branching
  on the open slot fewest words fit. No budget. Only `Verdict.ExactlyOne` carries a grid.
- `generateVerified(seed, difficulty)`: picks an entry with `Rng(seed + attempt)` and proves it (`board`); null when no
  attempt proved one. `lastResort(difficulty)` is each list's first entry, proved by a test.

## Teacher (`puzzles/MiniTeacher.kt`)

`deduce` sees the letters on the board and the clues (to quote them), never `answer`. The words that *fit* a slot are
the grid list's words of its length that agree with the letters in it. The teacher cannot solve a clue: matching a clue
to its word is reading the answer. Every word step ends in the player typing the word; only "Show me" types it.

1. `one-word`: the letters in a slot leave one word that fits. "2 Down reads A _ E. Only one word in the list fits those
   letters: the answer to "Pub beer"."
2. `crossing`: of the words that fit a slot, only one leaves every slot crossing it a word that fits; the explanation
   names those crossing slots.
3. `all-words`: every square keeps only the letters both its words allow, to a fixpoint, and a slot is left one word.
   Says so, as Codeword's does.
4. `clue`, the fallback: no crossing settles a word. The first tap names the open slot the fewest words fit (chosen
   from the board) with its word's second clue: "Another way in: 1 Across, "Expert pilot, or a serve no one returns"."
   Only the next tap reads the answer and says so, giving one letter: "No crossing letters settle a word yet, so this
   hint reads the answer: 1 Across, "Top card in a deck", starts with A." Show me places that letter. One hint is
   counted, as for any step. A save without second clues gets the old nudge, "Try 1 Across." From an empty grid nothing else can start, so every board needs some: these
   are the letters a player must find from the clues alone, and the tiers are cut by them. Measured, never assumed: the
   share is `docs/TEACHING.md`'s row.

A mistake is a letter that differs from the stored answer, sound because each clue names one word. Mistakes come
first; taking the letter back clears the hint. Highlight indices: squares `0 until size*size`, letter key
`MiniLogic.letterKey(l)`, the clue bar `MiniLogic.CLUE_BAR`.

## Tiers: why clue share, not ladder depth

Codeword cut its tiers by the deepest rung a board needs (Tally's `Spec.needs`). Here that measure does not separate
anything: from a few clue letters, nearly every grid of every size needs all three rungs (93% of 5x5 fills used the
`all-words` step; under 1% needed only `one-word`). What does vary, and what a crossword solver feels, is how much of
the grid the crossings give away and how much must come from knowing the clues. So a tier is a size and a band of
**clue share** (`MiniLogic.Spec`): the hints' walk reads more than `abovePercent` and at most `upToPercent` of the white
squares from the clues. Bigger grids interlock more, so left alone their share *falls* (7x7 fills with 13 black squares
had a median share of 25%, below 5x5's 33%): Expert would be bigger and easier. The band turns that round.

| Tier | Grid | Black squares | Clue share band |
|---|---|---|---|
| Standard | 5x5 | 4 | at most 30% |
| Hard | 6x6 | about 9 | over 30%, at most 40% |
| Expert | 7x7 | about 15 | over 40% |

## Measured

- **The build:** 1,000 grids a tier, from 6,480 / 144,240 / 23,040 draws (fills that give up within a 20,000-node budget,
  clue shares out of the band, and repeats: Hard's band holds the fewest distinct 6x6 fills, so most of its late draws
  were grids already kept). About five minutes on 12 desktop threads. `MiniGrids.kt` is 114 KB of source. Black squares
  4 / 9 / 15.1, white squares 21 / 27 / 33.9, words 10 / 12 / 15.9 a grid.
- **Clue share over every fill drawn, kept or not** (the sweep, `DAYBOOK_MINI_SWEEP`): 5x5 with 4 black, median 33%;
  6x6 with 8, 29%; 7x7 with 13, 25%; 7x7 with 15, 29%. Of 5x5 fills 92% needed a crossing step and 94% every crossing
  together; under 1% needed only one-word steps.
- **On the device, a year of daily boards** (`MiniRulesTest`, `build/reports/mini-tiers.txt`): clue letters 5.6 / 9.4 /
  15.2 a board, share 26.7 / 34.9 / 44.8%. Generation (the walk and the proof), median / p95 / max: JVM 9.5 / 16 / 33 ms,
  15 / 18 / 25, 17 / 24 / 34; Chromium 20 / 31 / 61, 31 / 37 / 49, 36 / 52 / 67; WebKit 38 / 60 / 121, 59 / 72 / 96,
  69 / 100 / 131. No day of a year reaches `lastResort` on any tier (`FallbackTest`).
- **Hints** (`MiniTeachingTest`, 200 boards a tier, hints alone finish all, no mistake): the clue fallback is 44 / 54 / 62%
  of steps; per board one-word 3.6 / 3.8 / 4.9, crossing 2.0 / 2.2 / 2.1, every crossing together 1.5 / 2.0 / 2.3. A
  hint on the JVM: median 0.2-0.3 ms, p95 1.2 ms, max 2.9 ms.
- **Second clue** (`MiniTeachingTest`, `build/reports/mini-second-clue.txt`, 200 boards a tier, 1,123 / 1,888 / 3,037
  fallback steps): the walk reads letters, so on its own terms a second clue moves nothing. The proxy: suppose the player
  solves it and types the word; the walk's next step is then a logic step (or the board is done) 28.3 / 32.1 / 29.4% of
  the time, else another fallback. The one letter of the next tap would have settled the same word through the crossings
  53.4 / 53.9 / 56.1% of the time. Words fitting the slot the clue names: median 23 / 8 / 5, p90 439 / 439 / 33. The
  fallback share of steps is unchanged (44 / 54 / 62%): the second clue and the letter are one step's two taps.
- **Layout:** at 375x537 Expert's squares are about 25 dp, with the clue bar (44 dp) and three rows of 40 dp keys under
  the grid; checked in Chromium and WebKit, light and dark, and on the emulator (1080x2400).
