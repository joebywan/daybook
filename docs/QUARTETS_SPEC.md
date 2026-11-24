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
- **Quiet members**, written `~word`: a true fit nobody should be dealt as the answer. Two kinds, both in `cross.txt`:
  grim or crude (Head + stone, Black + eye, head and mail, Water + board, Add S + hat), and real but obscure, technical or
  regional (moonfish, wardship, firebrick, sward, MINI as a road vehicle). A quiet word fits the category in every mask
  and proof, so a player who argues for it is never refused, but the generator never deals it as one of its category's
  four, a draw whose answer group fits a category whole with one of its quiet words in it is turned down, and the tier
  gate does not count it (a quiet fit is no spare, a category met three times only through quiet fits is no phantom).
  The lint holds a quiet word to every word rule (grim.txt and exclude.txt included), refuses one that is also a plain
  member of its category, and wants 6 plain members besides. When unsure between quiet and leaving a real word out,
  quiet: it costs a few redraws and never a refused right answer.
- **The second sweep** (2026-11-24, `cross.txt`, "Second sweep"): every bank word tried as the partner of every `___`
  category, closed and open, against `/usr/share/dict/web2` and `web2a` and (for the partners where it does not compound
  freely) the macOS spelling dictionary; the mechanical categories (hide a number, animal or body part; Add S; backwards;
  palindromes) re-run over the whole vocabulary against web2 and the app's word lists; every plain category read again
  against the vocabulary. It added 9 plain memberships (ONYX a rock, BALL of the foot, Black + mark, headliner,
  leaderboard, hardball, BUCKle, CLAMp, sCUBA) and 450 quiet ones. What it left out was checked: with every candidate
  it skipped made a member, 11 daily boards of a year's 1,095 gain a second answer on plain categories (KEY a fastener,
  EYELET a fastener, DRILL with teeth, MARCH and LINE dances, OIL a shade of black, DUST weather, PEARL with a string,
  PARROT a fish: none true), and 145 if every archaic compound web2 and web2a list is counted as well (galleyman,
  firecoat, snowproof, teaman: nobody's answer).
- `tools/words/quartets.py` lints every file and writes `puzzles/QuartetsWords.kt` (generated, never edited;
  `QuartetsBank.parse` also takes three-field lines, which the tests' fixture uses):

```kotlin
internal object QuartetsWords {
    /** "Title|level|word,word,...|quiet,...": words lowercase and sorted, quiet words (fit, never dealt) after the
     *  last bar, empty when none; lines sorted by title. */
    val CATEGORIES: List<String> = listOf(
        "Black ___|3|ball,berry,...,top|eye,head,mail",
        "Planets|1|earth,jupiter,mars,mercury,neptune,saturn,uranus,venus|",
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

`QuartetsLogic.draw(seed, attempt, ...)`, for attempt 0 up to `ATTEMPTS` (400): the first category, of the tier's
`needsLevel`, comes from `Rng(seed - 1 - attempt / PER_FIRST)`, so it is kept for `PER_FIRST` (20) draws in a row and
every category of that level leads about as many boards however often its draws fail the gate; the rest of the draw is
`Rng(seed + attempt)`. The next `linked` categories are drawn among those that one of the drawn categories' words fits
plainly (where spares come from; a quiet fit makes no spare), weighted by the inverse of how many categories link to each (`QuartetsBank.links`: without it
the most shared, `Add S at the front`, was on 16% of Expert boards); the rest from all the tier allows.
A draw with fewer than `deep` categories of level 2 or 3 is turned down; then four of each category's own words (never its
quiet ones), sixteen different words, shuffled; `titles`/`masks` from the whole bank. The draw ships only if
`split(masks, all sixteen)` is `ExactlyOne`, no answer group fits a category whole through one of its quiet words, and
the overlap gate holds. No hash container's order reaches the `Rng`. `lastResort` is a fixed board per tier, proved by a
test; a year of daily seeds reaches it on no tier (`QuartetsBankTest`).

Overlap, measured on the dealt board over plain fits only (`QuartetsLogic.plainMasks`; the split that names the answer
groups counts every fit):
- a **spare** is a word that fits an answer group's category plainly but belongs to another group (the category fits 5+);
- a **phantom** is a category that is no answer group yet fits three or more of the sixteen plainly.

`QuartetsLogic.overlap(state)` counts every fit, quiet ones included (a state's masks do not tell them apart); the gate
uses `overlap(state, plainMasks(state, bank))`. With quiet fits counted, 144 of the year's Standard boards, 97 of Hard's and
37 of Expert's would fail their gate: moonfish and the like would have made a Standard board Hard while showing the player
nothing they could see as a second group.

| Tier | Category levels (`levels`, first `needsLevel`, `deep`) | `linked` | Gate (`spares`, spares + phantoms `overlap`) |
|---|---|---|---|
| Standard | 1 only | 0 | no spare, no phantom |
| Hard | 1 and 2, the first level 2, at least one of level 2 | 1 | 1 or 2 spares, 1 or 2 spares and phantoms together |
| Expert | 1 to 3, the first level 3, at least two of level 2 or 3 | 3 | 3 to 6 spares, 3 to 7 together |

Hard and Expert gate on spares, not only on spares and phantoms together, so the tier notes are true: a Hard board always
has a word that fits a second group, an Expert board always three. A phantom alone (three words that look like a group
that is not there) no longer makes a board Hard.

Tier notes (what the player sees change): "No word fits two groups" / "One or two words fit a second group" /
"Wordplay groups, and three or more words that fit a second group".

## Hints (`puzzles/QuartetsTeacher.kt`)

The teacher reads the words left and the categories that fit them (`titles`, `masks`): public knowledge, as Word
Wheel's grid list is. It never reads which categories are the answer (the state does not hold that). Ladder:

1. `last-four`: four words are left.
2. `exactly-four`: some category fits exactly four of the words left, and a word of those fits no other live category.
3. `spare`: a category fits five to seven of the words left, and exactly four of them fit no other live category. Each
   of those four can only be grouped inside this category, and seven words cannot hold two groups, so the four are one
   group; the hint names the spare word and where else it fits. (The first cut, "another category needs one of them to
   reach four", was not sound: the teacher's tests enumerate every split and refused it.)
4. `every-split` (the fallback, said openly): the one way to split what is left, worked through.

A hint names the theme and how many words fit it, and glows **at most one tile** (the four would be the answer);
only "Show me" submits the group. No mistake exists: a refused four is spent, not on the board. Categories with identical fits are read as one.

Measured (`QuartetsTeachingTest`, a year of daily boards a tier, walked by "Show me"): every board finishes in four steps
and the fallback is never needed (0 of 1,460 steps on every tier); all but one step of the year are `exactly-four` or
`last-four`, because every answer group of a shipped board still has a word that fits only its own category. On 3,000
random positions with several splits all four rungs fire, and every step that is not the fallback holds in every split.
Open for the owner: a hint names the theme outright, which in this game is most of the answer.

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

Bank (2026-11-24, after the second sweep): 110 categories (level 1 / 2 / 3: 51 / 29 / 30), 915 words, 438 of them in two
or more categories, 1,607 plain and 456 quiet memberships (were 436 and 6). A year of daily seeds from 2026-01-01 per tier
(`QuartetsBankTest`, `build/reports/quartets-generation.txt`; times from a desktop JVM). Before is the bank and gate of the
first build (times then from the Linux test container, a JVM under emulation):

| | Standard (before) | Standard | Hard (before) | Hard | Expert (before) | Expert |
|---|---|---|---|---|---|---|
| Proved / `lastResort` | 365 / 0 | 365 / 0 | 365 / 0 | 365 / 0 | 365 / 0 | 365 / 0 |
| Draws a board, mean / most (of 400) | 1.8 / 12 | 1.8 / 12 | 3.5 / 25 | 4.0 / 26 | 8.8 / 48 | 12.6 / 89 |
| Generation ms, median / p95 / max | 0.1 / 1.2 / 7.0 | 0.1 / 0.5 / 6.9 | 0.2 / 1.6 / 19.7 | 0.2 / 1.4 / 5.8 | 0.8 / 3.7 / 18.4 | 1.0 / 4.0 / 9.1 |
| Spares a board | 0 always | 0 always | 1 on 344, 2 on 21 (1.06) | 1 on 347, 2 on 18 (1.05) | 3-6: 318 / 38 / 8 / 1 (3.16) | 3-5: 314 / 45 / 6 (3.16) |
| Phantoms a board | 0 always | 0 always | 0 on 282, 1 on 83 (0.23) | 0 on 268, 1 on 97 (0.27) | 0-4: 185 / 110 / 53 / 16 / 1 (0.73) | 0-4: 201 / 113 / 44 / 6 / 1 (0.61) |
| Groups of level 2 or 3, mean | 0 | 0 | 2.0 | 2.1 | 3.0 (wordplay 2.1) | 2.9 (wordplay 1.9) |
| Categories dealt / allowed | 51 / 51 | 51 / 51 | 80 / 80 | 80 / 80 | 106 / 110 | 106 / 110 |
| Most / least dealt (boards of 365) | Parts of a tree 43 / Fruit 17 | Parts of a tree 43 / Fruit 17 | In a beehive 48 / Herbs 5 | In a beehive 50 / Road vehicles 5 | `___ house` 38 / Herbs 1 | `Rain ___` 34 / Herbs 1 |
| Different words shown | 515 | 514 | 698 | 707 | 820 | 844 |

- The sweep's quiet fits cost Expert most: draws turned down for a second split rose (599 of 4,599 draws in a year), so
  Expert takes 12.6 draws a board on average and 89 at most, still under a quarter of `ATTEMPTS` (the test's bound). `Spec`
  did not change. Draws turned down for a quiet word in an answer group: 7 / 6 / 19 a year.
- With four groups a board, a category's fair share is 4 / (categories allowed): 7.8% of boards on Standard, 5% on Hard,
  3.6% on Expert (about 7% for each of the 30 wordplay categories, which Expert leans on). The most dealt is 11.8 / 13.7 /
  9.3%. Hard's leaders are where its spares come from: the linked second category (Rodents is the linked draw for Marks
  on the skin's MOLE, Birds for SWIFT and KITE); weighting the linked draw by 1 / links, by its square root or not at all
  all left In a beehive at 48-56 boards. Expert never deals Sea mammals, Shades of pink, Shades of purple or Shellfish:
  plain categories that share no word with a wordplay one only come in through the open draw, which `deep` and the spares
  gate rarely keep.
- The big mechanical categories (Add S at the front, 61 plain words; Hide an animal, 56; Backwards, another word, 46) are
  not over-dealt: on Expert 29 / 31 / 24 boards. As phantoms they cost Standard 67 of its 642 draws in a year and Hard 34
  of 1,443; the gate holds them out of Standard entirely.
