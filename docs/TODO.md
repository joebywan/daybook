# Daybook TODO


## Arcade: what is left

Every board is converted (`docs/COLOUR.md`, top). Left over from the conversions:
- Links' pale pair colours are weak on the white Arcade tiles; Links, Trail and Hexwords draw their letters and digits with a `TextStyle` naming no family (check they are Fredoka). *S.*
- Lamps: the dark lit wash reads brownish, and plain walls blend into the frame in Arcade light (they read as holes at the edge). Selected, peeked and error states of the regions/pieces boards were not render-checked. *S.*
- Thermometers' mercury shows seams between squares on the Arcade gradient. *S.*
- Unblock's "All three tiers!" header overlaps the Moves · par line at 1080x2400. *S.*
- Arcade hint card and finish frame not yet seen in WebKit dark at 375x537. *S.*
- Not seen on the emulator in Arcade: Atoms, Pearls, Pipes, Arrows, Trail, Cipher, Codeword, Hexwords, Codebreaker, Inequality, Regions, Sun & Moon, Tally; the line boards' WebKit, Hard/Expert and Classic-solved renders. *S.*
- Classic was rendered on the web (#472); Classic of Who's Who's graph not rendered in WebKit. *S.*

The one list of outstanding work. Small enough to read in a minute; maintained as part of normal work.

**How to keep it current**
- Found something you are not fixing now? Add it here in the same PR, with enough detail that a fresh
  session can start without asking: what, why it matters, where in the code, rough size.
- Finished one? Delete the line in the PR that fixes it. History lives in git; this is not a changelog.
- Something here turned out wrong or already fixed? Fix the entry rather than working around it.
- Decisions that are the owner's to make go under "Owner's call", not in the work lists.
- Settled decisions live in `CLAUDE.md` ("Settled — do not reopen"), not here.

Size: **S** under an hour, **M** a few hours, **L** a day or more. Sources are noted where an item came
from a measurement or review, so you can check it is still true.

## Could affect players

- [ ] **Boards not yet on the digit scale** (`BoardGlyph`, `ui/BoardText.kt`). Most moved with their Arcade conversion
  (2026-11-13). Check what is left with a grep for `* 0.` font sizes and `.sp` in board files; pick each a role and render
  before and after. *S each.*

- [ ] **Streak calendar day cells** (Statistics card) are ~42dp at 360 wide; 48 needs the card's padding reduced. *S.*
- [ ] **The walkthrough offer: players from before the re-offer.** The offer now returns on every visit until the walkthrough is
  opened or the puzzle solved, recorded in the same `tutorialsOffered` set the once-only offer wrote on a first visit, so a
  player who saw the old offer once is never offered it again (nor shown the first-board walkthrough). A new key would
  re-offer them. *S.*
- [ ] **Walkthroughs: two moves not walked through.** Every walkthrough follows the style rules (`docs/PUZZLE_STANDARDS.md`
  section 8). Not walked through, because neither emits a state a frame could ask for: Word Wheel's Shuffle (a labelled
  button) and Sand's refused pour (it shakes and tints). *S.*
- [ ] **Web: a touch right after a mouse gesture may be dropped.** Driving the walkthroughs in Playwright (Chromium and
  WebKit), the first touch tap after a mouse drag or click did nothing (Shikaku's remove, LITS's 2x2, Atoms' gap); the
  same taps by mouse, and by touch on the Android emulator, work. Probably the harness mixing pointer types, but a
  touchscreen laptop mixes them too: check on real hardware before closing. *S.*
- [ ] **Fredoka: board glyphs, decide.** Canvas-drawn numerals (Sudoku, Snap, Nonogram, Atoms, Mosaic, ...) are drawn with
  a bare `TextStyle` that names no family, yet on both builds they already render in Fredoka (digit shapes match the UI
  text; seen in the 2026-10 recapture, Android and Chromium). Recommendation: leave them, and if one needs pinning, do it
  where the shared `TextMeasurer` is made rather than per board. Confirm in WebKit before closing this. Settings and
  Statistics were checked on Android and the web in Fredoka and are fine.
- [ ] **Mate: polish left.** The hint popover reports the right side (the board reports its highlight; checked on a flipped Expert
  board at 375x537 in Chromium and WebKit) but it still covers the ranks nearest the player when the highlight is far side,
  which can include the player's own (unhighlighted) king; that is the placement rule, not a missing report. The promotion button's
  glow ring is now rounded to match its clip but has not been seen rendered (no way to reach a promotion in the web harness; a
  `?fen=` style hook would do it). *S.*
- [ ] **Regions: README screenshots.** (Its sparser boards came with the locked candidate.) *S.*
- [ ] **Regions: Expert's fill is slow in the browser.** `randomBoard` is about 85 ms of a 9x9 draw on the JVM, and 93% of Expert
  generation; Expert is 99 ms median, 0.43 s p95, 0.8 s worst in Chromium, 0.17 / 0.72 / 1.4 s in WebKit (a quiet
  machine, 2026-10-27); the worst days are those whose first fills dead-end and are redrawn (3-4 draws). A cheaper fill
  (fewer restarts, or growing regions where the fill is already forced) would fix it; it moves every board. *M.*

- [ ] **Cage Sudoku: the dashes hide a labelled square's bottom notes** where its cage closes along the bottom (7, 8, 9 lose their lower half, a highlighted 9's disc too;
  `?puzzle=cages&date=2026-06-15&tier=standard`, r5c1). Dashes crossing notes predate #407; whether its tighter rows worsened this square was not measured. Needs the note rows laid out inside the dash inset. *S.*
- [ ] **Cage Sudoku: the cage-first test oracle is slow on Expert** (3-13 s a board, so `CageRulesTest` samples three). Not a
  problem until the tiers change; a smarter independent solver would let it sample a year. *S.*
- [ ] **Word Wheel: more wheels, fewer clue steps.** Grid words are limited to clued ones, so 6/7-letter words are a hand-picked 716 / 745
  (`tools/words/wheel-clues.txt`); cluing more widens the boards (every added word moves daily boards, as Lexicon's do). About a third of
  hint steps are still the clue fallback, and no reasoning over what shows can cut that (an exhaustive search of every filling pins
  0.1-0.2 points more, `docs/TEACHING.md`): only the boards can. The generator could reject boards whose hints-alone walk (with
  `crossing`) needs many clues; that moves daily boards and costs attempts, not measured. Expert grids reach 8x8 and get ~20dp squares at 375x537. *M.*

- [ ] **Share card: two readings to confirm.** Restart counts as undoing every move so far (an Undo of the restart hands them back), and Target's "steps" is the
  operations still on the board at the finish (not only those that build the target); both are simple readings the owner may
  want changed (`ui/play/ShareCard.kt`).

- [ ] **Rate link: check on a phone.** Settings ends with a row that opens Google Play's page for a Play install, or
  GitHub for any other install and on the web (`ui/settings/RateRow.kt`, `rememberRateLink` in both platform files). It was
  compiled by CI only. Check a Play install opens the Play Store app, and a sideloaded or F-Droid install opens GitHub in
  the browser. A one-time nudge on the finish frame (say after the tenth solve) would bring more ratings than a settings
  row, but the finish frame is settled UX, so that is the owner's call. *S.*
- [ ] **Large system font (2x): what is left outside the boards.** The play header, clock, tier pills, pen buttons, home date,
  streak badge and tile names, Codebreaker's Submit and Who's Who's board now stop at 1.3x (`CappedFontScale`). Still
  at 2x (web `?fontscale=2`, 375x537): the toolbar (Undo / Restart / Hint) is half again as tall; the walkthrough offer
  overflows its 40dp slot; Lexicon's and Codebreaker's "guesses left" lines and Crossword's clue bar take board height
  (Lexicon's board is still small); Codeword's number and letter key strips. Not checked on the emulator (shared). *S.*

## Rewards

Design and decisions: `docs/REWARDS.md`. Items 1 (streak, `core/Streak.kt`), 2 and 3 (finished-frame praise, `ui/play/FinishPraise.kt`), 4 (achievements) 5 (calendar) and 6 (daily variety) are done; the rest are separate PRs.

- [ ] **Achievements follow-ups.** No fanfare yet (the solve chime plays; a distinct short tone would need a second
  sample path in both platform players). The two
  registry-wide achievements (one of each, clean sweep) read unearned again when a puzzle is added.
- [ ] **Move feel: boards left out.** `ui/MoveFeel.kt` (settle and unit glow) is on every grid board but these: Mate (the
  reply already lands in a second beat with the last move marked), Pointers (its slide), Sets (a pick, not a placement), Tower
  (submitting clears the row being built, so a settle would pop empty pegs; the new guess row could settle instead). No unit
  glow on Cipher (a column is judged with its carry), Target, Lexicon (no unit short of the solve). The board arrival fade is also off
  Atoms, Dominoes and Sand, which draw from the feel by hand (`scale`/`glow` only): they would need `alpha` wired in. The
  boards left out above get no solve wave, though the finished frame still waits for one. *S each.*
- [ ] **Puzzle of the week.** A larger board once a week (e.g. Sundays), from the same `hash(date, puzzle, tier)` scheme so it
  stays offline and free; most generators already scale by size. The owner wants it argued with worked examples per puzzle that fit a
  phone screen first: Nonogram Expert is already as big as a phone allows. Decide which puzzles take part (only those whose larger
  sizes are proved and fast on the web) and whether it counts toward streaks. *M.*

## Infrastructure

- [ ] **Can Renovate's token update workflow files?** No Renovate PR among the last 60 touched `.github/`, and GitHub
  normally refuses workflow changes from a token without the `workflows` permission. If a GitHub Actions update fails on
  the Dependency Dashboard, it needs a `RENOVATE_TOKEN` PAT with that scope. Owner only. *S.*
- [ ] **Verify Renovate's CI workaround on the first real Monday run.** Renovate runs daily and
  `tools/dispatch-ci-for-renovate.sh` dispatches `ci.yml` on `renovate/*` branches, where it posts a
  `build` commit status (README, "Dependency updates"). Proven on a throwaway PR with a stand-in
  build; not yet on a real Renovate PR. Check: the PR gets `build`, a patch/Actions PR merges by
  itself, a rebase builds again. *Recommended: turn on "Allow auto-merge" in the repo settings so
  such PRs merge the moment the build is green rather than on the next daily run.*
- [ ] **Release secrets into an environment** (security review, 2026-10-27). Move `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` and `PLAY_SERVICE_ACCOUNT_JSON` into a `release`
  environment restricted to `main`, so no other branch's workflow can read them. Repo settings, owner only. *S.*
- [ ] **Bind the required checks to GitHub Actions.** The ruleset's `build` and `web-parity` accept a status from anyone;
  bind both to the GitHub Actions app (integration_id 15368) so a stray token cannot post them. Owner only. *S.*
- [ ] **Require SHA-pinned actions.** Once the SHA-pinning PR merges, turn on "Require actions to be pinned to a
  full-length commit SHA" in the repo's Actions settings. Owner only. *S.*
- [ ] **Consider one approval for `.github/` changes** (a ruleset path rule), so a workflow change cannot merge unseen.
- [ ] **kotlinx-serialization 1.13.0** (2026-11-11) waits for Renovate's 7-day minimum age; the androidx libraries are
  current. The JDK stays 17 (CI too; `renovate.json` holds it); Gradle 9 would run on newer, so moving it is its own change.
- [ ] **Web icons are pinned at `material-icons-extended` 1.7.3** (`gradle/libs.versions.toml`, the last
  published; move to Material Symbols vector resources eventually). The deprecated `compose.*` accessors and
  `getting` in `web/build.gradle.kts` are gone. *S.*
- [ ] **Web: a board already generating cannot be interrupted.** A tap during a slow pre-generation on Home
  waits for it. Fine at current speeds; a Web Worker would fix it if generators slow down again. *L.*

## Keyboard input

Every board but Atoms, Shikaku, Snap and Mate takes a keyboard (`core/BoardKeys.kt` is the shared plumbing; each puzzle
has a pure, tested `<Name>Keys.kt`; the rules dialogs and the wide side panel show a "Keyboard" line from
`PuzzleType.keyboardHelp`). Sudoku was driven with `keyboard.press` in Chromium and WebKit (headless WebKit drops
frames, so settle each step with two `requestAnimationFrame`s and a `resize` before trusting a screenshot).
- [ ] **Undo drops the keyboard cursor.** History entries are stored without the selection (`withoutSelection`),
  so after Ctrl+Z the next arrow starts again from square 0. Keeping the current selection across undo would fix it. *S.*
- [ ] **Mate keys.** Arrows move a square cursor, Enter or Space picks up and puts down, as Pointers' cursor. *M.*
- [ ] **Atoms, Shikaku, Snap keys, only if full coverage is wanted.** Each is a drag gesture needing its own
  mode (Atoms: arrow to the neighbouring atom, Space cycles the bond; Shikaku: Space anchors a corner, arrows
  grow, Enter commits, Esc cancels; Snap: arrows extend the path via `extend`, Backspace retracts). The mouse
  suits all three better, so leaving them pointer-only is defensible. *M-L each.*

## Council review (2026-10-25)

Five independent design reviews (puzzle construction, player experience, daily meta, word/number games, genre mix),
accepted by the owner except where noted. Each item is one workstream and one PR; delete it here when it lands.
Generator changes move every daily board of that puzzle (parity pins go red on purpose): batch them, one PR per puzzle.

- [ ] **Daily reminder follow-ups** (`ui/home/Reminder.kt`). The hour is one of five presets (8 am to 9 pm), not a
  clock picker: add one if players ask. Home's one offer counts the days of the boards solved, so an archive board can
  stand in for a second day; read the day a solve was made instead once completions record it (WS6). *S.*
- [ ] **Snap depth gate:** not possible yet. Snap has no teacher (a whole-board deducer leaves the real choices to a
  13-joint what-if; `docs/TEACHING.md`).

## The five puzzles added 2026-11-09 (Loop, Thermometers, Unblock, Equation, Links)

Built in parallel and merged in sequence (1.41-1.45); all five checked on the emulator (2026-11-13). Open items they left:

- [ ] **Codebreaker on the shared guess marker.** Equation lifted Lexicon's marking into `core/GuessMarks.kt`; Tower keeps
  its own peg scoring (a count is not a per-slot mark). Look at whether its teacher can share the rest. *S.*
## Future puzzle candidates

- **Dwindle's marked-letter daily scoring mode.** The player's game also has a daily mode scored on letters marked on the board. Left out of the first
  build on instruction. A score is new state on `DwindleState` (defaulted, so saves load) and a new share-card line; the board itself would not change.

Ideas for further puzzles, none started. Any new puzzle follows `docs/PUZZLE_STANDARDS.md` (three
tiers, proved or deliberately-unproved boards, a teacher, a walkthrough, parity pins, tests).

**What makes a candidate fit Daybook:** it generates on the device from `hash(date, puzzle, tier)` with
no network; the board has one answer we can prove (or, like Tower and Sets, an answer we do not need
to prove); it demands thought rather than luck (the owner's stance, recorded under Snap in `CLAUDE.md`'s
"Settled" list: "I should have to make choices and think"; sparse beats friendly); a teacher can explain a step from what the player can see; and it
plays with a thumb on a phone in portrait. **Names:** the puzzles are classic genres rebuilt from their
published rules. Do not use a newspaper's or a game show's trademarked name for one (several of the ideas
below have a famous branded version; use the descriptive name).

### Asked for by the owner

- [ ] **Nonogram and Lexicon: short phone screens on the emulator.** Both were driven on the Android emulator
  (1080x2160 and 1080x2400, 2026-10-03): the Lexicon hint popover over a highlighted row and keys, its nine-frame
  walkthrough, hardware keys, and a Nonogram 5x5 swept by touch drag to the finish frame all work, and a save made on
  the build before the toolchain upgrade (a Sudoku with two digits placed and a running timer) loaded on this one.
  Not done: 360x640 and 390x664, and the 10x10 and 15x15 Nonogram tiers by touch on the emulator.
- [ ] **Nonogram: a real WebKit touch drag.** Checked 2026-10-03 with `tools/render/nonogram_touch.py`: 10x10 and 15x15,
  all four sizes, light and dark. Chromium by CDP touch (12px steps), WebKit by the mouse: a row sweep and a column sweep
  paint exactly their squares, one Undo takes back each, Fill sweeps leave crosses alone, and the grid, pen row and
  toolbar do not move. Static renders of all three tiers matched pixel for pixel across both browsers and both schemes.
  Not done: headless WebKit ignores hand-built TouchEvents, so its touch slop on a 15x15 sweep is unchecked (only a real
  iPhone or the emulator can), and the 5x5 finish frame's cover of the last rows at 375x537 is by design.
- [ ] **Web: a late home-grid value can reach a tile stale.** The "In progress" set, passed down as a plain `Set` and landing in
  the first frames, reached `PuzzleTile` stale in about half of Chromium loads (DaybookApp had the new set; the tile recomposed
  with the old one; WebKit and Android never showed it). It is now a state read in the tile (`inProgress: () -> Set<String>`),
  6 of 6 loads right. Root cause not found (the grid sits in `BoxWithConstraints` inside a `LazyColumn` item); other values
  that arrive late (`doneToday` from completions) go the same way and may be worth a check. *M.*

- [ ] **Target: web hint cost.** The first hint costs 18 ms median, 30 ms p95 on a desktop JVM (Expert, six tiles); the wasm and phone figures were
  not measured (a board generated in the browser at 375x537 and a hint both ran without trouble).
- [ ] **Cipher: other shapes need longer words.** Every tier is 4 + 4 = 5 because the leading carry is the foothold the no-trial ladder needs, and the
  answer lists stop at five letters. A screened six-letter answer list (SCOWL through `tools/words/build.py`, screened by hand as the others were) would
  allow 5 + 5 = 6, which would give Hard and Expert a different look. Measured without it: 5 + 5 = 5 gave one ladder board per 30 pairs, three addends none.
- [ ] **Cipher: show the working for the deepest steps too.** `CipherWorking` shows its reasoning on 90 / 80 / 75% of steps (Standard / Hard /
  Expert); most all-columns steps and nearly every two-column what-if still say "only X = d" in summary. Those need two columns combined as algebra
  ("U + O + carry is 11, and O is 1 + U + carry, so 2U ...") or a case split on a carry whose branches stall without a second split; tried, the second
  split ran every explanation past 260 characters. A hint in two parts (the carry first, then the letter) may be the shape that fits. *M–L.*
- [ ] **Cipher: candidate notes, if players ask.** Not built: every hint ends in a digit placed, so none relies on an elimination the player must track.
  Sudoku's notes are the model (a mask per letter, defaulted; the mode `rememberSaveable`).

- [ ] **Tally: a regions variant, deliberately not built.** The published genre also comes with coloured regions whose kept numbers must hit
  a region target as well. It adds a third kind of line to the ladder and a colour layer to a board that already carries targets on two
  edges; left out until the plain board has been played.

### Suggested

**Demand, measured (2026-11-08).** Google Play detail pages carry Google's exact lifetime install count. Of the top ten
search results per genre, the biggest app that really is the genre: Android only, lifetime installs not active players,
old and heavily advertised apps inflated, and a genre that lives inside a bundle (NYT Games, Tatham's collection) undercounted.
For scale, genres Daybook already has: Sudoku 127M, Arrows 121M, Sand 100M, Word Wheel 78M, Nonogram 73M, Mines 22M, Cage
Sudoku 19M, Lexicon 18M, Who's Who 14M, Tally 9.4M, Tents 1.7M, Kings 1.3M, Atoms 1.2M, Codeword 1.0M, Inequality 413K,
Regions 209K, Sun & Moon 175K, Shikaku 76K, Trail 83K, Lamps 22K, Pearls 3.6K. A small number is not a bad fit (Pearls,
Lamps); a genre a big publisher has made a hit of is the strongest signal. NYT's own 2025 counts: Pips (Dominoes) 612M plays
in its first four and a half months.

Before adding one, read the existing puzzles' `rules`, not just their names: Bridges (Hashi) turned out to be Atoms, Star
Battle is Kings, Takuzu is Mambo, Hidato / Number chain (1.07M) is Trail, and Kakuro is Cages.

**Build rule for all of these: reuse, never copy.** Each brief names the shared code to call. Where a helper is private
to one puzzle and a new one needs it, lift it into `core/` or `ui/` (or make it `internal` and call it) in the same PR,
with the old caller moved onto it; never paste a second copy. Shared already: `core/Grid.kt` (neighbours), `core/Bits.kt`
(masks, `countMaskSolutions`), `core/Verdict.kt` (exhaustive counts), `core/PenMarks.kt` + `ui/PenBoard.kt` (two-mark pen,
sweeps), `ui/TwinTaps.kt` (tap / double tap), `ui/Settle.kt` (1 s red), `ui/BoardDraw.kt` (`fitStep`, `gridCellAt`,
`drawWalls`, hint dim and rings), `ui/DigitPad.kt`, `core/Teaching.kt` (`placementDeduction`, `answerFallback`,
`TutorialPath`), `ui/MoveFeel.kt`, `core/TierNotes.kt`, `core/TechniqueNames.kt`.

In order of measured demand, then fit:

- [ ] **Battleships (Conceptis 152K).** Place a fleet (one 4, two 3s, three 2s, four 1s on 10x10, smaller on lower tiers)
  so ships never touch, even diagonally, matching row and column counts and a few given segments. Close to Tents, so reuse
  Tents' count chips, no-touch logic and `countSolutions` shape; input is Tents' (tap water, double tap ship). *M.*
- [ ] **Dominosa (63K).** A pip grid holds each domino 0-0..N-N once; find where each lies. Tap between two squares to
  place one. Reuse Dominoes' domino drawing and tray. *M.*
- [ ] **Hitori (57K).** Shade squares so no digit repeats in a line; shaded squares never touch, unshaded stay connected.
  Tap-only (`TwinTaps`: shade / circle); the connectivity proof is LITS's shape. *M.*
- [ ] **Skyscrapers (34K).** A Latin square with edge clues (how many buildings are visible from that side). Inequality's
  Latin-square rules, generator, prover, keys and pad carry over; only the clue type is new. *S–M.*
- [ ] **Kurodoko (Kuromasu 33K).** Shade squares so each number counts the white squares it sees in its row and column,
  itself included; shaded never touch, white stay connected. Shares Hitori's shading, `TwinTaps` and connectivity. *M.*
- [ ] **Word ladder (29K).** Change one letter at a time from a start word to an end word through valid words.
  Lexicon's lists (`LexiconWords`, `WordList`); choose pairs with a unique shortest ladder to keep it provable. *M.*
- [ ] **Word tiling (LinkedIn's Wend; clones 6K, the real one lives inside LinkedIn).** Cut a grid of letters into words of
  given lengths, every letter used once, words traced through side-touching squares. Goals from `WheelWords.grid`, a unique
  tiling proved with `BONUS` words allowed (Codeword's precaution); swipe input is `swipePoints` (Dwindle, Hexwords).
  Hints give the clue, as Dwindle's do. *M.*
- [ ] **Letter box (clones 3K).** Twelve letters on a square's four sides; words chain last letter to first, never two
  letters from one side in a row; use every letter in at most N words. Many answers (Target's stance: prove reachability,
  not uniqueness); tiers by N. `WheelWords.isWord`, Word Wheel's swipe. *M.*
- [ ] **Galaxies (about 1K).** Cut the grid into regions, each symmetric under a half-turn about its dot. Shikaku's input,
  a different deduction. *M–L.*
- [ ] **Slant (Gokigen Naname; under 1K).** A diagonal in every square; corner numbers count the lines meeting there; no
  closed loops. Tap-only (`TwinTaps`: one diagonal / the other). Little demand. *M.*
- [ ] **Fillomino (under 1K).** Split the grid into regions where each number is its region's size and same-size regions
  never touch. Little demand, and near the declined island puzzle: show the owner an example first. *M.*
- Magnets and map colouring: no dedicated Android app found. Not suggested.

- [ ] **Reckon (KenKen / Calcudoku, 557K; issue #483, wanted 2026-11-14).** 6x6 arithmetic cages on a Latin square.
  Spec: `docs/RECKON_SPEC.md`. `CageLogic` is 9x9-bound, so the base is `InequalityLogic` plus a new cage ladder; the
  grid and pad are Sudoku's generalised to n. *M.*

Close to existing puzzles, so not suggested: Kakuro (Cages), Kropki (Inequality), Hidato (Trail).

**Not recommended** (noted so nobody re-investigates): plain word-search and sliding-tile puzzles are generator-easy
but do not demand thought, against the owner's stated taste. (A crossword was here for want of a clue database; the
hand-written clues are one now, see the council list.) Curated themed puzzles (four groups of four, themed word hunts)
were declined by the owner (2026-10-25): a shipped list runs out and would need revisiting every year.
An island-and-sea puzzle (Nurikabe) was shown to the owner as a 6x6 example and declined (2026-10-22).
A practice mode per technique, once declined, is now wanted (2026-10-26): Training, for Sudoku, Cage Sudoku,
Inequality and Regions (`docs/TRAINING_SPEC.md`). Open: keeping a half-played final board when the player leaves it; the
hint outline's pulse dims to 45% on every board, which on a mid-tone fill is faint at its low point (the verdict breathes
too; holding a verdict's outline still would be the fix, not decided);
Kings has the general practice shape (`MoveDrill`), and Pipes, Pearls and Atoms use it too, and so do Who's Who,
Cipher (without "only letter", never reached), Chess, Mambo, Nonogram, Tally, Mines, Lamps, Tents,
Shikaku, Dominoes and LITS; Mosaic has its own judge
(`MosaicDrill`); the other puzzles' courses are classified and batched in `docs/TRAINING_SPEC.md`.

## Who's Who (the graph, graduated 2026-11-13)

- [ ] **Clues can't be struck from the keyboard**: arrows move among the pills only. *S.*
- [ ] **The saved choice across rotation** (`rememberSaveable`, matched by the board's content after a restore) was reasoned, not seen on a device. *S.*

## Owner's call


- **Web address.** The web version is served at `knowhowit.com.au/daybook/`, a path on the owner's
  existing domain. That works, and nothing needs doing unless it matters. The one thing to know: a
  browser keys saved progress and a home-screen install to the address, so moving the site later
  strands existing installs (the export/import backup on the stats screen is the way across). Decide
  the final address before many people install it, if it is going to change at all. Other sites on
  that domain share its browser storage, so keep key names distinct.
- **Declined: own origin for the web game** (security review, 2026-10-27). Daybook shares `knowhowit.com.au` with the
  owner's blog (which loads Google Analytics) and six other Pages sites, so script on any of them could read or alter its
  `localStorage` or plant an entry in its service-worker cache. The fix would be its own subdomain. The owner declined: a
  free app, not happening. Do not reopen.
- **Declined: HTTPS enforcement** (same review). `http://knowhowit.com.au/daybook/` is served over plain HTTP ("Enforce
  HTTPS" is off for this repo's Pages). Declined for the same reason. Do not reopen.
- **F-Droid.** Our own repo is automatic (`publish-fdroid.yml`; `docs/fdroid/README.md`). f-droid.org: merge request [fdroiddata!51019](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51019) is open (entry: `docs/fdroid/com.joebywan.daybook.yml`); answer the reviewers on GitLab, and when it merges check the app appears and that a later release is picked up
  (lints; never built by F-Droid). It is set up as a reproducible build, so its installs update over Play and GitHub ones if F-Droid's build matches our APK. Once the first run has
  pushed the `fdroid` branch, check the repo address in the F-Droid app and that a new release shows up in it.
- **Play Store.** Production access granted (October 2026; the service account has "Release to production"); every merge to `main` uploads a bundle to
  the `production` track through `publish-play.yml`. Check the Console home page's Android developer verification notice: sideloaded
  APKs may need their own registration (package name plus signing key).
  The owner is fine with the developer name being public.
- **Sets is probably underrated in deal 3's spread.** What makes it hard is finding a set among the cards, which a hint walk
  of three or four steps cannot see (`docs/LEVELS_SPEC.md`, "Level difficulty"). Solve times by tier would settle it; a
  re-rating ships as a new deal.
- **Colour rollout** (`docs/COLOUR.md`). Rough audit: only Shikaku and Nonogram bring several hues of their own; Lits,
  Sets, Tower and Mosaic no longer hard-code hex lists (their Arcade conversions, 2026-11-13); the rest
  are scheme greys plus one accent. One PR per step, each with a true-size render in both schemes (`tools/render/render.py`),
  a check at 375x537, and no change to any seed, state or save.
  5. Atoms, Pipes, Mambo, Snap, Kings, Lexicon: scheme plus accent today. Give each two content hues (Atoms: element
     types; Pipes: flow/source colouring; Mambo: the two states; Snap: path stretches or numbers; Kings: crowns vs. marks;
     Lexicon: keep Green/Gold, add a hue to the keyboard's used letters). Kings must keep its CIEDE2000 test.
  6. Nonogram and Shikaku: move their `PALETTES` / `hsl` onto the helper; Nonogram's pairs become pairs of table hues (`BoardHues.HUE_PAIRS`/`pair`, which changes its current colours, so that step re-pins its tests).
  7. Home-grid `Preview` for each, in the same pass as its board; then recapture `docs/screenshots/*.png` (clean install,
     date pinned) and update the alt text.
  8. Acceptance for the whole thing: every board shows at least two non-grey, non-accent hues in both schemes; dark
     fills stay clear of the surface; no hex literal for content colour outside the helper; `PUZZLE_STANDARDS.md`
     checklist gets a "content hues from the helper" row so new puzzles start compliant.
- **Declined: players adding words** (the owner, 2026-11-13): the games are quick pickup and play, and typing words in would drag
  that down. Do not reopen.
- **Declined: checking on a real iPhone or iPad** (the owner, 2026-11-13): the web build is checked in desktop WebKit and Chromium
  only, as well as can be done; there is no Apple device to test on and no App Store build. The items that needed one (first touch,
  first-frame nudge, repaint, offline install, phone speed, Kings' dark glare, sounds, Safari saves, the iPad freeze and short page)
  are dropped. Do not reopen.
