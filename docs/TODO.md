# Daybook TODO

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

- [ ] **Touch targets left under 48dp.** Fixed in the UX pass: Home header buttons (48dp targets round 36dp circles), the
  play screen's help button, the web back arrow, the calendar arrows, the archive tier buttons and the web backup buttons.
  Not fixed: the "One-minute walkthrough" offer over the clock (the clock row's height, ~24dp; growing it moves the board)
  and the calendar's day cells (sized from width, ~40dp at 360). *S each.*
- [ ] **Walkthroughs: rewrite to the style rules.** The runner now advances by itself and takes multi-state moves
  (`docs/PUZZLE_STANDARDS.md` section 8); only Lexicon's BOAT and Sudoku's cell-then-digit were converted. The other 23 still
  split one idea over several frames (Inequality, Regions, Cages, Cipher select then digit), teach gestures only in captions,
  and carry claims the rules contradict (Lexicon frame 4: "no guess can tell COAT, GOAT and MOAT apart", but CLOG does). *M.*
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
- [ ] **Regions: sparser boards, and README screenshots.** The ladder is three single-digit techniques, so boards carry
  about 27 / 21 / 19% of squares as givens. A locked-candidate technique (a digit whose every place in a region touches
  one square rules that square out) would let the generator take more back, but it ends in an elimination, not a
  placement, and Regions has no pencil marks; it would need a step that explains one elimination and then a placement
  (Inequality's `SIGN_BOUND` is the model). *M.*

- [ ] **Cage Sudoku: name what `cage-chain` does.** About 1 step in 13 on Expert (6.3 a board) is "every cage total worked through
  together": true, but it points at a square without naming the groups. Locked candidates and pairs, run on the cage-aware candidates,
  would explain some; each new rule must also go into `CageLogic.Sight` so the generator's ladder stays the teacher's. *M.*
- [ ] **Cage Sudoku: the cage-first test oracle is slow on Expert** (3-13 s a board, so `CageRulesTest` samples three). Not a
  problem until the tiers change; a smarter independent solver would let it sample a year. *S.*
- [ ] **Word Wheel: more wheels, fewer clue steps.** Grid words are limited to clued ones, so 6/7-letter words are a hand-picked 716 / 745
  (`tools/words/wheel-clues.txt`); cluing more widens the boards (every added word moves daily boards, as Lexicon's do). About 40% of
  hint steps are the clue fallback; a "two slots, two words" step (slots that share their only candidates) would turn some into
  reasoning. Expert grids reach 8x8 and get ~20dp squares at 375x537. *M.*

- [ ] **Share card: two readings to confirm.** Restart counts as undoing every move so far (an Undo of the restart hands them back), and Target's "steps" is the
  operations still on the board at the finish (not only those that build the target); both are simple readings the owner may
  want changed (`ui/play/ShareCard.kt`).

- [ ] **Rate link: check on a phone.** Settings ends with a row that opens Google Play's page for a Play install, or
  GitHub for any other install and on the web (`ui/settings/RateRow.kt`, `rememberRateLink` in both platform files). It was
  compiled by CI only. Check a Play install opens the Play Store app, and a sideloaded or F-Droid install opens GitHub in
  the browser. A one-time nudge on the finish frame (say after the tenth solve) would bring more ratings than a settings
  row, but the finish frame is settled UX, so that is the owner's call. *S.*

## Rewards

Design and decisions: `docs/REWARDS.md`. Items 1 (streak, `core/Streak.kt`), 2 and 3 (finished-frame praise, `ui/play/FinishPraise.kt`), 4 (achievements) 5 (calendar) and 6 (daily variety) are done; the rest are separate PRs.

- [ ] **Achievements follow-ups.** No fanfare yet (the solve chime plays; a distinct short tone would need a second
  sample path in both platform players). The two
  registry-wide achievements (one of each, clean sweep) read unearned again when a puzzle is added.
- [ ] **Move feel: boards left out.** `ui/MoveFeel.kt` (settle and unit glow) is on every grid board but these: Mate (the
  reply already lands in a second beat with the last move marked), Pointers (its slide), Pipes (its turn), Snap (the line follows
  the finger square by square), Sets (a pick, not a placement), Tower (submitting clears the row being built, so a settle would
  pop empty pegs; the new guess row could settle instead). No unit glow on Cipher (a column is judged with its carry), Mosaic,
  Target, Lexicon, Shikaku (no unit short of the solve, or a rectangle is itself the move). Home: the solved tick could pop in
  on the way back from a solve (not built). *S each.*
- [ ] **Puzzle of the week.** A larger board once a week (e.g. Sundays), from the same `hash(date, puzzle, tier)` scheme so it
  stays offline and free; most generators already scale by size. The owner wants it argued with worked examples per puzzle that fit a
  phone screen first: Nonogram Expert is already as big as a phone allows. Decide which puzzles take part (only those whose larger
  sizes are proved and fast on the web) and whether it counts toward streaks. *M.*

## Needs a real iPhone

Nothing below can be verified from a desktop browser. The tester has the device.
- [ ] **Does the first-frame nudge matter?** `nudgeFirstFrame()` in `web/.../Main.kt` exists because headless
  WebKit dropped Compose's first frame until a touch. If real Safari does not need it, remove it.
- [ ] **Repaint after screen changes.** Headless WebKit did not redraw after a screen change until something
  prompted a frame; the test harness nudges it. Confirm real Safari repaints on its own.
- [ ] **Offline and Add to Home Screen.** The service worker was proven offline on the shell branch in desktop
  browsers, but not re-run after the integration merge, and never on iOS. Check: install to home
  screen, go offline, open, play, solve; and that a new deploy replaces the old copy.
- [ ] **Speed on a real phone.** All timings are desktop WebKit/Chromium. Check LITS Expert, Snap and Mosaic
  Expert board creation on the phone.
- [ ] **Kings dark mode glare.** The new opaque palette is bright tiles on a near-black page. Ask whether it
  is too much; the alternative (dimmer dark-mode tiles) costs crown/cross contrast.
- [ ] **Does the solve sound play?** The web build wakes its `AudioContext` on the first tap (`rememberSolveSoundPlayer`
  in `web/.../WebPlatform.kt`) and plays the marimba pluck on a solve. Headless Chromium has no audio output, so
  nobody has heard it on the web. Check on the iPhone that it is audible after a solve, that the Settings
  "Sound" switch silences it, and that the Settings note ("a browser cannot see your phone's silent switch") reads right.
- [ ] **Saves survive?** Safari can clear site data after ~7 days unused, but home-screen web apps are
  exempt. Confirm progress persists from the home-screen icon; the export/import backup on the stats
  screen is the safety net.

## Infrastructure

- [ ] **Verify Renovate's CI workaround on the first real Monday run.** Renovate runs daily and
  `tools/dispatch-ci-for-renovate.sh` dispatches `ci.yml` on `renovate/*` branches, where it posts a
  `build` commit status (README, "Dependency updates"). Proven on a throwaway PR with a stand-in
  build; not yet on a real Renovate PR. Check: the PR gets `build`, a patch/Actions PR merges by
  itself, a rebase builds again. *Recommended: turn on "Allow auto-merge" in the repo settings so
  such PRs merge the moment the build is green rather than on the next daily run.*
- [ ] **Pages HTTPS.** The Pages API reports `https_enforced=false` with no CNAME on the project site
  (it is served under the owner's existing custom domain). It works over HTTPS today; confirm
  enforcement is on at the domain level, since the service worker requires HTTPS. *S.*
- [ ] **Dependencies still behind after the toolchain majors.** The `androidx` libraries (Compose BOM
  2025.09 -> 2026.09, lifecycle, activity, navigation, datastore, core-ktx) and kotlinx-serialization 1.11
  are still on older versions; Renovate opens those. The build and CLAUDE.md still say JDK 17 (CI uses
  17 too); Gradle 9 would run on newer, so moving the JDK is its own change (`renovate.json` holds it at 17).
- [ ] **`compileSdk` 36 -> 37 before `core-ktx` 1.19.** `androidx.core:core-ktx` 1.19.1 requires `compileSdk` 37
  (`checkDebugAarMetadata` fails, and CI then stops on the SDK preview licence), so Renovate's PR (#185) was closed rather than
  merged. Move `compileSdk` to 37 on its own (it does not change `targetSdk` or runtime behaviour, but needs SDK 37 in CI), then
  let Renovate offer the update again. *S.*
- [ ] **Web icons are pinned at `material-icons-extended` 1.7.3** (`gradle/libs.versions.toml`, the last
  published; move to Material Symbols vector resources eventually). The deprecated `compose.*` accessors and
  `getting` in `web/build.gradle.kts` are gone. *S.*
- [ ] **`app/proguard-rules.pro` is not used.** The release build type names no `proguardFiles`, so R8 runs on the
  library consumer rules alone and the file's `-keepclassmembers class com.joebywan.daybook.core.**` never applies (found
  while building the release save check: a rule added there changed nothing until `proguardFiles(...)` was added). Saves load
  without it (the check passes), so either delete the file or wire it in; wiring it in changes the shipped code, so run
  `tools/save-check/check.py` on the result. *S.*
- [ ] **Web: a board already generating cannot be interrupted.** A tap during a slow pre-generation on Home
  waits for it. Fine at current speeds; a Web Worker would fix it if generators slow down again. *L.*

## Keyboard input

Every board but Atoms, Shikaku and Snap takes a keyboard (`core/BoardKeys.kt` is the shared plumbing; each puzzle
has a pure, tested `<Name>Keys.kt`; the rules dialogs show a "Keyboard" line from `PuzzleType.keyboardHelp`).
Still open: WebKit pass with `keyboard.press` (only Chromium was driven).
- [ ] **Atoms, Shikaku, Snap keys, only if full coverage is wanted.** Each is a drag gesture needing its own
  mode (Atoms: arrow to the neighbouring atom, Space cycles the bond; Shikaku: Space anchors a corner, arrows
  grow, Enter commits, Esc cancels; Snap: arrows extend the path via `extend`, Backspace retracts). The mouse
  suits all three better, so leaving them pointer-only is defensible. *M-L each.*

## Future puzzle candidates

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
- [ ] **Nonogram: pictures are noise.** The boards are random squares, not drawings. One smoothing pass gave blobbier
  pictures but failed the shape rules on 96% of 5x5 draws (about 25% of 10x10, 45% of 15x15), so it would need a
  size-by-size retune; measure the pass rate before trying again.
- [ ] **Lexicon: a no-repeat cycle for daily words.** The word is a pure function of the seed, so it can recur
  (about even odds of a repeat within two months of Standard days). A cycle needs the day count, which the
  `generate(seed, difficulty)` contract does not carry. Only if repeats bother anyone.

- [ ] **Dominoes: recapture the home screenshot.** Dominoes is the twenty-fifth tile, opening a ninth row on a phone;
  `docs/screenshots/home.png` (and the store screenshots) were not recaptured with it. The "Arrange puzzles" link under the
  grid is new as well; a clean install shows registry order (nothing played), so the shot's order is unchanged.
- [ ] **Web: a late home-grid value can reach a tile stale.** The "In progress" set, passed down as a plain `Set` and landing in
  the first frames, reached `PuzzleTile` stale in about half of Chromium loads (DaybookApp had the new set; the tile recomposed
  with the old one; WebKit and Android never showed it). It is now a state read in the tile (`inProgress: () -> Set<String>`),
  6 of 6 loads right. Root cause not found (the grid sits in `BoxWithConstraints` inside a `LazyColumn` item); other values
  that arrive late (`doneToday` from completions) go the same way and may be worth a check. *M.*
- [ ] **Arrange puzzles: drag to reorder.** `ArrangeScreen` has up and down buttons only (accessible, but 24 taps to move the
  last puzzle to the top). A long-press drag, or a "move to top" button, would help a long list. *S.*
- [ ] **Recapture home screenshots after the UX PRs.** The badge now says "Day one awaits" on a clean install, not "0 day
  streak", and more home changes are landing; recapture every `1-home.png` and `docs/screenshots/home.png` once they are in
  (`tools/screenshots/capture.sh`). *S.*

- [ ] **Target: web hint cost.** The first hint costs 18 ms median, 30 ms p95 on a desktop JVM (Expert, six tiles); the wasm and phone figures were
  not measured (a board generated in the browser at 375x537 and a hint both ran without trouble).
- [ ] **Cipher: other shapes need longer words.** Every tier is 4 + 4 = 5 because the leading carry is the foothold the no-trial ladder needs, and the
  answer lists stop at five letters. A screened six-letter answer list (SCOWL through `tools/words/build.py`, screened by hand as the others were) would
  allow 5 + 5 = 6, which would give Hard and Expert a different look. Measured without it: 5 + 5 = 5 gave one ladder board per 30 pairs, three addends none.
- [ ] **Cipher: candidate notes, if players ask.** Not built: every hint ends in a digit placed, so none relies on an elimination the player must track.
  Sudoku's notes are the model (a mask per letter, defaulted; the mode `rememberSaveable`).

- [ ] **Tally: a regions variant, deliberately not built.** The published genre also comes with coloured regions whose kept numbers must hit
  a region target as well. It adds a third kind of line to the ladder and a colour layer to a board that already carries targets on two
  edges; left out until the plain board has been played.

### Suggested

Roughly in order of how well they fit, best first. Before adding one, read the existing puzzles' `rules`, not just their
names: Bridges (Hashi) turned out to be Atoms, Star Battle is Kings, Takuzu is Mambo, Calcudoku is Cage Sudoku plus Inequality,
and Battleships is close to Tents.
- [ ] **Word ladder.** Change one letter at a time from a start word to an end word through valid words.
  Needs the same word lists as the word-deduction puzzle (the lists now exist: `LexiconWords`, `WordList`, rebuilt by `tools/words/build.py`); choose pairs
  with a unique shortest ladder to keep it provable. *M after the lists exist.*
- [ ] **Loop through the dots (Slitherlink-style).** Draw one closed loop so each numbered square has
  exactly that many loop edges around it. Provable and rich, but tapping edges on a phone is fiddly:
  prototype the input before committing. *L.*
- [ ] **Mines, no guessing.** Minesweeper rules: tap reveals, long-press flags. Ship only boards logic clears from the
  first reveal (a solver proves it, as the Tents and Tally ladders do); a hint names the deduction ("this 2 already
  touches two flags, so the rest are safe"). Tiers: size and density. A mine loses the board, as Pointers' lives do. *M.*
- [ ] **Codeword.** A filled crossword grid with a number for each letter and a few letters given; work out the
  substitution. No clues, so it avoids the clue-database problem below. Reuses Lexicon's and Word Wheel's lists and
  Word Wheel's grid layout; uniqueness is pattern matching against the list. Not Cipher: word shapes, not columns. *M–L.*
- [ ] **Skyscrapers.** A Latin square with edge clues (how many buildings are visible from that side). Inequality's
  Latin-square rules, generator and prover carry over; only the clue type is new. *S–M.*
- [ ] **Hitori.** Shade squares so no digit repeats in a line; shaded squares never touch, unshaded stay connected.
  Tap-only; the connectivity proof is LITS's shape. *M.*
- [ ] **Pearl loop (Masyu rules).** One closed loop through squares: it turns on black pearls, goes straight through
  white ones. Drawn through squares, not along edges, so Snap's drag works and the edge-tapping problem goes away. *L.*
- [ ] **Dominosa.** A pip grid holds each domino 0-0..N-N once; find where each lies. Tap between two squares to
  place one. Pairs well with the domino-placement idea above. *M.*
- [ ] **Galaxies.** Cut the grid into regions, each symmetric under a half-turn about its dot. Shikaku's input,
  a different deduction. *M–L.*
- [ ] **Unblock (sliding cars; not the trademarked name).** Slide cars to free the red one. Breadth-first search gives
  the minimum, so tiers by minimum moves and a cap equal to it (Mosaic's zero slack). Planning, not the 15-puzzle
  ruled out below, but the least deductive of these. *M.*

Close to existing puzzles, so not suggested: Kakuro (Cages), Kropki (Inequality), Battleships (Tents). Any new
puzzle first needs the accent-spacing rule relaxed (see "Accents are crowded" below).

**Not recommended** (noted so nobody re-investigates): a crossword needs a licensed clue database, which
we cannot generate or ship offline; plain word-search and sliding-tile puzzles are generator-easy but do
not demand thought, against the owner's stated taste.
An island-and-sea puzzle (Nurikabe) was shown to the owner as a 6x6 example and declined (2026-10-22).
A practice mode per technique was declined by the owner: players can use the hints themselves, and it would
only spend their time railroaded through a tutorial.

## Owner's call

- **Web address.** The web version is served at `knowhowit.com.au/daybook/`, a path on the owner's
  existing domain. That works, and nothing needs doing unless it matters. The one thing to know: a
  browser keys saved progress and a home-screen install to the address, so moving the site later
  strands existing installs (the export/import backup on the stats screen is the way across). Decide
  the final address before many people install it, if it is going to change at all. Other sites on
  that domain share its browser storage, so keep key names distinct.
- **F-Droid.** Our own repo is automatic (`publish-fdroid.yml`; `docs/fdroid/README.md`). f-droid.org: merge request [fdroiddata!51019](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51019) is open (entry: `docs/fdroid/com.joebywan.daybook.yml`); answer the reviewers on GitLab, and when it merges check the app appears and that a later release is picked up
  (lints; never built by F-Droid). It is set up as a reproducible build, so its installs update over Play and GitHub ones if F-Droid's build matches our APK. Once the first run has
  pushed the `fdroid` branch, check the repo address in the F-Droid app and that a new release shows up in it.
- **Play Store.** Production access granted (October 2026; the service account has "Release to production"); every merge to `main` uploads a bundle to
  the `production` track through `publish-play.yml`. Check the Console home page's Android developer verification notice: sideloaded
  APKs may need their own registration (package name plus signing key).
  The owner is fine with the developer name being public.
- **Accents are crowded.** With twenty-four puzzles no hue is 25 degrees from every accent (`docs/COLOUR.md` asks for that);
  Sand took the widest gap, hue 346, 19 degrees from the accents either side of it. A next puzzle needs the rule relaxed
  or a lightness/saturation difference to carry it.
- **Colour rollout** (`docs/COLOUR.md`). Rough audit: only Shikaku and Nonogram bring several hues of their own; Lits,
  Sets, Tower and Mosaic hard-code hex lists that are the old accents (so they overlap right/wrong colours); the rest
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
