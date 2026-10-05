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

- [ ] **Accessibility.** Eight boards use raw pointer input and expose no click actions, so a screen reader
  cannot operate them. Knowingly deferred while sideloaded; becomes real the day this reaches a store
  or another user. *L.* (`CLAUDE.md`, "Settled")

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

## Rewards

Design and decisions: `docs/REWARDS.md`. Items 1 (streak, `core/Streak.kt`), 2 and 3 (finished-frame praise, `ui/play/FinishPraise.kt`), 4 (achievements) 5 (calendar) and 6 (daily variety) are done; the rest are separate PRs.

- [ ] **Achievements follow-ups.** No fanfare yet (the solve chime plays; a distinct short tone would need a second
  sample path in both platform players). The two
  registry-wide achievements (one of each, clean sweep) read unearned again when a puzzle is added.

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
- [ ] **Repo description is stale.** GitHub's "About" text still says "ten" puzzle types (it is fourteen) and "for
  Android" (there is a web version too). Settings > About on the repo page; no API needed, but it is the owner's call.
- [ ] **Dependencies still behind after the toolchain majors.** The `androidx` libraries (Compose BOM
  2025.09 -> 2026.09, lifecycle, activity, navigation, datastore, core-ktx) and kotlinx-serialization 1.11
  are still on older versions; Renovate opens those. The build and CLAUDE.md still say JDK 17 (CI uses
  17 too); Gradle 9 would run on newer, so moving the JDK is its own change (`renovate.json` holds it at 17).
- [ ] **Web icons are pinned at `material-icons-extended` 1.7.3** (`gradle/libs.versions.toml`, the last
  published; move to Material Symbols vector resources eventually). The deprecated `compose.*` accessors and
  `getting` in `web/build.gradle.kts` are gone. *S.*
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

Ideas for puzzles 13 onwards, none started. Any new puzzle follows `docs/PUZZLE_STANDARDS.md` (three
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

- [ ] **Domino placement on a region board.** A board of cells grouped into coloured regions; each
  region carries a rule (all cells equal, all different, total equals N, total less than or more than
  N, or no rule). A tray holds a set of dominoes (two pip counts, 0–6 each); place every domino over
  two adjacent cells so that every region's rule holds.
  - *Generation:* tile an irregular shape with dominoes, assign pips at random, read the region rules
    off the solution, then prove uniqueness with an independent solver and tighten or loosen the rules
    until there is exactly one answer (the same shape as Kings' proof). Tiers differ by board size,
    region count and which rule types appear; keep slack at zero like Mosaic.
  - *Teaching:* the deductions are crisp ("a two-cell region that must total 12 needs a double six, and
    the tray has one"), which suits the teacher pattern well.
  - *Input:* drag a domino from the tray with snap-to-cell, tap a placed domino to rotate or lift it.
    The tray competes with the toolbar for height; Tower's controls are the nearest precedent.
  - *Size: L.*

### Suggested

Roughly in order of how well they fit, best first.
- [ ] **Cage-sum Sudoku.** Sudoku plus cages whose digits must total a given sum without repeats. Reuses
  Sudoku's grid, digit pad, notes and conflict display, so most of the UI exists. Needs a uniqueness
  solver that handles cages; a teacher using cage combinations ("a 2-cage totalling 3 is {1,2}"). *M.*
- [ ] **Island-and-sea puzzle.** Numbered cells grow islands of that size, islands never touch, the sea is
  one connected body with no 2x2 pool. Same family as Kings/LITS (region logic, a connectivity
  proof in the solver); the teacher can name each rule it uses. *M–L.*
- [ ] **Number-target arithmetic.** Six numbers and a target; combine them with + − × ÷ to reach the
  target. Generated so a solution always exists; tiers vary the target distance and how many numbers
  must be used. No unique answer, like Tower. Tiny UI, fast to play, very different feel from the
  grid puzzles. *S–M.*
- [ ] **Word ladder.** Change one letter at a time from a start word to an end word through valid words.
  Needs the same word lists as the word-deduction puzzle (the lists now exist: `LexiconWords`, `WordList`, rebuilt by `tools/words/build.py`); choose pairs
  with a unique shortest ladder to keep it provable. *M after the lists exist.*
- [ ] **Loop through the dots (Slitherlink-style).** Draw one closed loop so each numbered square has
  exactly that many loop edges around it. Provable and rich, but tapping edges on a phone is fiddly:
  prototype the input before committing. *L.*
- [ ] **Region-digit puzzle (no touching repeats).** Each region of size n holds 1..n, and equal digits
  may not touch, even diagonally. Small, regions-based, reuses Kings' region drawing. *S–M.*

**Not recommended** (noted so nobody re-investigates): a crossword needs a licensed clue database, which
we cannot generate or ship offline; plain word-search and sliding-tile puzzles are generator-easy but do
not demand thought, against the owner's stated taste.

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
- **Play Store listing.** Set up and publishing: the signing key is chosen, the service account exists,
  and every merge to `main` uploads a bundle to the closed testing track (`alpha`) through
  `publish-play.yml`. What remains is the owner's: run the closed test (12 testers opted in for 14
  continuous days), then request production access; when Google grants it, move `publish-play.yml`'s
  default track to `production`, roll out in stages, and give the service account the production
  permission. Also check the Console home page's Android developer verification notice: sideloaded
  APKs may need their own registration (package name plus signing key).
  The owner is fine with the developer name being public.
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
