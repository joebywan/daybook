# Quartets (test puzzle): spec

Sixteen words; find the four groups of four that share something. The genre is published by the New York Times as
*Connections* (and, earlier, as the wall in the BBC's *Only Connect*); neither name, nor their art, colours or boards,
appears anywhere a player sees. This is a **lab puzzle** (`PuzzleRegistry.lab`, id `test-quartets`, display name
`test:Quartets`): it exists so the owner can play it and decide. Graduating it means a new id in `all` and the full
standards (`docs/PUZZLE_STANDARDS.md`); nothing here is a contract yet, the bank's order included.

## Why this is not the "curated list" the owner declined (2026-10-25)

Curated boards were declined because a shipped list runs out. Nothing here ships a board. What ships is a **bank of
categories**, each with more members than a board uses; the device deals four categories and four members of each from
the seed, proves the board has one answer, and redraws if not. Boards are generated, pure in `(seed, difficulty)`.

## The rules the player is told

1. Sixteen words hide four groups of four; the words of a group share something.
2. Pick four words and submit them. A right group is set aside with what it shares.
3. A wrong four costs one of four lives. Lose all four and the board is lost (Try again deals the same board afresh).
4. Some words fit more than one group. There is exactly one way to split all sixteen.

## Decisions made for the lab build (all negotiable by the owner on playing it)

- **Four lives; a lost board offers Try again** (`retry()`, one state, as Mines). No undo (`offersUndo = false`): undo
  could only hand back a life (Pointers' reasoning).
- **"One away" is said** when a refused four is one word off a group of the answer. A four already refused since the
  last retry is not taken again and costs nothing ("Already tried").
- **All tiers are sixteen words.** A tier is how much the words overlap, never the size.
- **Single words only**, a-z, 3 to 8 letters, shown in capitals. No people, brands, places smaller than a country,
  sport, pop culture, or anything one dialect lacks (the app's clue rules; `tools/words/build.py`'s lint is the model).
  No word on `tools/words/grim.txt` or `tools/words/exclude.txt`.
- **No stored answer.** The state holds the words and, for every bank category at least three of them fit, its title
  and which words fit. The rules decide everything from that: what is accepted, `solved`, what a lost board reveals.

## The bank

Source: `tools/words/quartets/*.txt`, one category a line, blank lines and `#` comments ignored:

```
Title shown to the player | level | word word word ...
```

- `level` is 1 (a plain category anyone knows: planets, things in a kitchen), 2 (needs a second look: a less obvious
  property, a narrower set) or 3 (wordplay: `___ fish`, words hiding a number, homophones of letters).
- At least 6 members a category, so a board's four are a draw and a category can come back with other words.
- **Membership is complete over the bank's vocabulary**: a word is listed under *every* category in the bank it fits.
  This is the one thing no solver can check, and the uniqueness proof is only as good as it. Words with two lives
  (MERCURY, PITCH, BASS, JACK) are wanted: they are what Hard and Expert are made of.
- `tools/words/quartets.py` lints every file and writes `puzzles/QuartetsWords.kt` (generated, never edited):

```kotlin
internal object QuartetsWords {
    /** "Title|level|word,word,...": words lowercase and sorted, lines sorted by title. */
    val CATEGORIES: List<String> = listOf(
        "Planets|1|earth,jupiter,mars,mercury,neptune,saturn,uranus,venus",
    )
}
```

## State and rules (`puzzles/QuartetsRules.kt`)

```kotlin
@Serializable
data class QuartetsState(
    val words: List<String>,              // 16, lowercase, in dealt order (the board's first display order)
    val titles: List<String>,             // every bank category >= 3 of these words fit, sorted by title
    val masks: List<Int>,                 // parallel to titles: bit i set when words[i] fits
    val found: List<Int> = emptyList(),   // masks of the groups accepted, in the order found
    val misses: List<Int> = emptyList(),  // masks of the fours refused since the last retry
    override val moves: Int = 0,
) : PuzzleState {
    val remaining: Int                    // mask of the words in no found group
    val livesLeft: Int                    // QuartetsLogic.LIVES - misses.size
    override val solved: Boolean          // QuartetsLogic.isSolved(this): a rule check
    override val failed: Boolean          // !solved && livesLeft == 0
    fun guess(mask: Int): QuartetsState?  // null: not four remaining words, a repeat of a miss, or the board is over
    fun retry(): QuartetsState            // found and misses cleared, moves + 1
}

object QuartetsLogic {
    const val LIVES = 4
    const val SUBMIT = 100                               // highlight index of the Submit button; tiles are 0..15
    sealed interface Split { object None; data class ExactlyOne(val groups: List<Int>); object Several }
    fun valid(s: QuartetsState, mask: Int): Boolean      // four words that all fit one category
    fun split(masks: List<Int>, remaining: Int): Split   // every way to cut `remaining` into valid fours; exhaustive, no budget
    fun accepts(s: QuartetsState, mask: Int): Boolean    // valid, and what is left still splits
    fun oneAway(s: QuartetsState, mask: Int): Boolean    // refused, and three of the four are in one group of the split
    fun titleOf(s: QuartetsState, mask: Int): String     // the category a found group is shown under
    fun isSolved(s: QuartetsState): Boolean              // found groups are valid, disjoint and cover all sixteen
    fun reveal(s: QuartetsState): List<Int>              // the split of what remains, for a lost board
    fun generateVerified(seed: Long, difficulty: Difficulty, bank: QuartetsBank = QuartetsBank.shipped): QuartetsState?
    fun generate(seed: Long, difficulty: Difficulty): QuartetsState   // generateVerified ?: lastResort(difficulty)
}
```

- **A guess is right when no answer is lost by it** (`accepts`), never "equals a stored group" (the PR #15 rule). On a
  shipped board the split is unique, so the two agree; the rule is what is checked.
- **`solved` checks the rules.** `QuartetsBank` is the parsed bank (`shipped` parses `QuartetsWords`; tests pass their
  own), so every rule and the generator can be tested on a fixture bank without touching the shipped words.

## Generation

`Rng(seed + attempt)`, up to `ATTEMPTS`: draw four different categories the tier allows, four members of each (sixteen
different words), shuffle the sixteen, build `titles`/`masks` from the whole bank, and keep the draw only if
`split(masks, all sixteen)` is `ExactlyOne` and the tier's overlap gate holds. No hash container's order reaches the
`Rng`. `lastResort` is a fixed board per tier, proved by a test; measure how often a year of daily seeds reaches it.

Overlap, measured on the dealt board:
- a **spare** is a word that fits an answer group's category but belongs to another group (the category fits 5+);
- a **phantom** is a category that is no answer group yet fits three or more of the sixteen.

| Tier | Category levels | Gate (first cut; tune against the real bank, then write the measured numbers here) |
|---|---|---|
| Standard | 1 only | no spare, no phantom |
| Hard | 1 and 2 | one or two spares or phantoms |
| Expert | at least one level 3 | three or more spares or phantoms |

Tier notes (what the player sees change): "No word fits two groups" / "One or two words fit a second group" /
"Wordplay groups, and three or more words that fit a second group".

## Hints (`puzzles/QuartetsTeacher.kt`)

The teacher reads the words left and the categories that fit them (`titles`, `masks`): public knowledge, as Word
Wheel's grid list is. It never reads which categories are the answer (the state does not hold that). Ladder:

1. `last-four`: four words are left.
2. `exactly-four`: some category fits exactly four of the words left, and a word of those fits no other live category.
3. `spare`: a category fits five or more, and another category needs one of them to reach four.
4. `every-split` (the fallback, said openly): the one way to split what is left, worked through.

A hint names the theme and how many words fit it, and glows **at most one tile** (the four would be the answer);
only "Show me" submits the group. No mistake exists: a refused four is spent, not on the board. Measure the fallback.

## Board (`puzzles/Quartets.kt`)

- A 4-column grid, four rows tall whatever is found: a found group becomes a full-width band (title over its four
  words) at the top, in the order found; the tiles left close up under the bands. Sized from both axes.
- Tap a tile to pick it (up to four), again to let go. Under the grid, always laid out: lives as four dots, then
  Shuffle (display only, a `rememberSaveable` count), Clear, Submit (enabled at four). The pick is `remember`, never
  state; one submitted four is one state.
- A reserved one-line note over the grid: "One away", "Already tried". A refused four shakes sideways (still under
  reduced motion). A lost board shows the groups not found as dimmed bands, and "Try again".
- Band colours are four hues of the shared family in found order (not Coral, Green or Rose: nothing is right or
  wrong by colour). Both looks. Accent shared with Lexicon (the Arcade accent set is full; lab precedent).
- Lab scope: no walkthrough, no keyboard, no training, no guide page, no parity pin.

## Measured

(Filled in by the tuning task: bank size, attempts per board and `lastResort` rate over a year per tier, spares and
phantoms per board per tier, the hints' fallback share.)
