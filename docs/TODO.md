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

## Tests and tooling

- [ ] **The finished frame hides about half the board on tall screens.** At 390x844 the centred frame
  (rows 321-523dp) covers 58% of Snap's Standard/Hard board (235-583dp) and 50% of Expert's; below 700dp
  the bottom-docked frame covers 12-32%. The board never moves and every tile works, so this is
  visibility, not breakage. Dock to the bottom whenever the space under the board can hold the frame
  (at 844 it would clear all three Snap boards), rather than by the fixed 700dp. Owner's call: the
  centred frame was deliberate. *S.*

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
- [ ] **Repo description is stale.** GitHub's "About" text still says "ten" puzzle types (it is twelve) and "for
  Android" (there is a web version too). Settings > About on the repo page; no API needed, but it is the owner's call.
- [ ] **Move the runners to Ubuntu 26.04.** Every workflow pins `ubuntu-24.04`, so GitHub's move of
  `ubuntu-latest` to 26.04 on 2026-10-19 does not touch this repo. CI's `build` and `web-parity` both
  passed on `ubuntu-26.04` on 2026-10-02 (PR #91's trial commit); `pages`, `release`, `publish-play` and
  `renovate` were not tried there. Move them all in one PR when convenient (Renovate may open it, since
  it tracks runner labels); 24.04 should stay available until the next Ubuntu LTS. *S.*
- [ ] **Dependencies still behind after the toolchain majors.** The `androidx` libraries (Compose BOM
  2025.09 -> 2026.09, lifecycle, activity, navigation, datastore, core-ktx) and kotlinx-serialization 1.11
  are still on older versions; Renovate opens those. The build and CLAUDE.md still say JDK 17 (CI uses
  17 too); Gradle 9 would run on newer, so moving the JDK is its own change (`renovate.json` holds it at 17).
- [ ] **`web/build.gradle.kts` uses APIs Compose 1.12 and Gradle 9.6 deprecate.** `compose.runtime` and the
  other accessors want direct coordinates, `getting` wants `getByName`, and `materialIconsExtended` is pinned
  at 1.7.3 (move to Material Symbols vector resources eventually). Warnings only; the build is green. *S.*
- [ ] **Web: a board already generating cannot be interrupted.** A tap during a slow pre-generation on Home
  waits for it. Fine at current speeds; a Web Worker would fix it if generators slow down again. *L.*

## Keyboard input (potential, not started)

Sudoku and Lexicon already take a hardware keyboard (`SudokuKeys.kt`, `LexiconKeys.kt`); the other eleven boards
take none. Survey and per-puzzle key proposals: the owner asked for these to be recorded, not built. Do them
in this order. Keep every key map a pure, unit-tested function; cursor state is `remember`, never `PuzzleState`;
chords stay ignored; check in Chromium and WebKit with `keyboard.press`.
- [ ] **Optional per-puzzle "Keyboard" line in Rules.** A `keyboardHelp` list on `PuzzleType`, empty by default,
  shown on the web build only ("Arrows move, Space cycles"). Do it with the helper if wanted. *S.*
- [ ] **Mambo, Pipes, Sets keys.** Arrows move a cursor; Space cycles (Mambo), rotates (Pipes) or picks (Sets);
  Mambo's two symbols may also have direct keys. *S each.*
- [ ] **Tower keys.** 1..N picks a colour and fills the next empty peg, Backspace removes the last, Enter
  submits. No cursor. *S.*
- [ ] **Kings and LITS keys.** Cursor plus mark keys. Kings: Space pencils out, K/Enter crowns, calling
  `toggleMark`/`toggleKing` directly (not the double-tap timer). LITS: Space toggles, Shift+arrow could paint. *S-M.*
- [ ] **Mosaic keys.** 1..N picks the palette colour; a cursor over cells; Space/Enter floods. *M.*
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
- [ ] **Nonogram: WebKit and the larger tiers' touch sweep.** Chromium touch checks (2026-10-02, 5x5 at 390x844,
  390x664, 360x640 and 375x537): a drag along a row or column is one undo step, Fill never overwrites a cross, and the
  solved board's box is identical before and after. The finish frame covers the last row or two of a 5x5 board at 375x537
  (it sits at the bottom there by design). Not done: WebKit rendering and the 10x10 and 15x15 sweeps in the browser.
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
- [ ] **Inequality Latin square.** A small grid (4x4 to 6x6) with each digit once per row and column and
  `<` / `>` signs between some neighbours. Tiny to generate, a clean uniqueness proof, a nice quick
  puzzle between the heavy ones. *S–M.*
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
- [ ] **Tents and trees.** Place a tent next to every tree, no two tents touching, with row and column
  counts. Close cousin of Kings (non-touching placement plus counts), so much of the board code and
  teaching carries over. *M.*
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
- **Play Store listing.** Set up and publishing: the signing key is chosen, the service account exists,
  and every merge to `main` uploads a bundle to the closed testing track (`alpha`) through
  `publish-play.yml`. What remains is the owner's: run the closed test (12 testers opted in for 14
  continuous days), then request production access; when Google grants it, move `publish-play.yml`'s
  default track to `production`, roll out in stages, and give the service account the production
  permission. Also check the Console home page's Android developer verification notice: sideloaded
  APKs may need their own registration (package name plus signing key). Recapture the screenshots at
  1080x1920 if the 9:16 promotion eligibility matters.
  The owner is fine with the developer name being public.
