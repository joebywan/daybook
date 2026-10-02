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

- [ ] **CI does not check web parity.** `pages.yml` builds the wasm distribution but nothing runs the
  JVM-vs-browser board comparison, so a generator change that diverges on the web would deploy
  silently. Add a CI job: Playwright (WebKit + Chromium) loading `?dump&range=N` against the JVM
  `WebParityDumpTest` output. Was entirely manual to date. *L.*
- [ ] **Generation fallbacks are not all structurally separated.** Only Kings, Atoms and LITS split a proved
  board from a fallback (`generateVerified`-style). Shikaku and Snap prove uniqueness but nothing
  asserts their fallback is never reached over a year; Kings' proved-path test samples 30 seeds per
  tier, not a year. Add year-long fallback tests for each. *M.*
- [ ] **No text-fit test for Kings,** and only partial ones for Pipes and Atoms (hint text can overflow the
  popover). *S–M.*
- [ ] **No checked-in rendering harness.** "Verify by rendering" is convention only; a Java2D harness drove
  past icon/motif checks and is not in the repo. Check one in (or a Playwright screenshot script for the
  web build) so it stops being rebuilt per session. *M.*
- [ ] **`TutorialFrame.passes` is dead API.** `CLAUDE.md` says Sets, Mambo and Snap use it; no puzzle sets
  it (they write one frame per emitted state). Delete the field or use it, and fix the docs. *S.*
- [ ] **Docs that disagree with the code** (found while writing `docs/PUZZLE_STANDARDS.md`):
  `CLAUDE.md` says only Kings and Atoms have `generateVerified` (LITS has it too, `internal`) and that
  all eleven boards consult `maxHeight` (Tower fits its box with a bounded `LazyColumn`); the `PuzzleType`
  KDoc still says purity is the only hard rule, but a new puzzle also needs a `ParityFingerprint.body`
  branch, a `StateSerializationTest.mutate` branch and a parity pin (the README now says so). *S.*
- [ ] **Snap's solved layout was never screenshotted** (it has no hints, so the harness never reached it).
  It shares the code path, so low risk. *S.*

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
- [ ] **Repo description is stale.** GitHub's "About" text still says "ten" puzzle types (it is eleven) and "for
  Android" (there is a web version too). Settings > About on the repo page; no API needed, but it is the owner's call.
- [ ] **Runner label change.** `ubuntu-latest` moves to Ubuntu 26 on **2026-10-19**. Watch the first run
  after that date; Android SDK setup or the wasm toolchain could break.
- [ ] **Dependency majors** are well behind: Kotlin 2.2.10 (2.4.x available), Compose Multiplatform 1.9.3,
  AGP 8.x (9 available), Gradle 8.14 (9 available), kotlinx-datetime 0.7.1 (0.8.0). Renovate opens PRs;
  the majors move together and need Android and web checked as a pair. CI's JDK is held at 17 until
  Gradle 9 (`renovate.json`).
- [ ] **Web: a board already generating cannot be interrupted.** A tap during a slow pre-generation on Home
  waits for it. Fine at current speeds; a Web Worker would fix it if generators slow down again. *L.*

## Future puzzle candidates

Ideas for puzzles 12 onwards, none started. Any new puzzle follows `docs/PUZZLE_STANDARDS.md` (three
tiers, proved or deliberately-unproved boards, a teacher, a walkthrough, parity pins, tests).

**What makes a candidate fit Daybook:** it generates on the device from `hash(date, puzzle, tier)` with
no network; the board has one answer we can prove (or, like Tower and Sets, an answer we do not need
to prove); it demands thought rather than luck (the owner's stance, recorded under Snap in `CLAUDE.md`'s
"Settled" list: "I should have to make choices and think"; sparse beats friendly); a teacher can explain a step from what the player can see; and it
plays with a thumb on a phone in portrait. **Names:** the puzzles are classic genres rebuilt from their
published rules. Do not use a newspaper's or a game show's trademarked name for one (several of the ideas
below have a famous branded version; use the descriptive name).

### Asked for by the owner

- [ ] **Word deduction (Mastermind for English words).** The player guesses a hidden word of N letters
  in a limited number of tries; after each guess every letter is marked *right letter, right place*,
  *right letter, wrong place* or *not in the word*, with the usual rule for repeated letters. The
  on-screen keyboard keys take the colour of what is known about them. Closest existing puzzle is
  **Tower** (guess history, scored feedback, no unique answer to prove): copy its structure.
  - *Tiers:* word length 4 / 5 / 6, with the guess count scaled; consider a "hard mode" rule (any
    revealed hint must be reused) as the Expert tier rather than a toggle.
  - *Answer:* picked by seed from a curated list of common words, **stored in a fixed sorted order**
    and indexed by the seed's value, so Android and the browser always agree (never a hash set; see
    the web-parity rules). Guesses are checked against a larger list of valid words.
  - *Word lists are the real work.* Needs a list with a licence we can ship (check public-domain and
    permissively licensed lists; do not use a newspaper's list), screened for obscure words in the
    answer list and for offensive ones. Decide **Australian vs US spelling** (colour/color): answers
    should avoid words with variants, and both spellings should be accepted as guesses. Budget for the
    size: tens of thousands of words, a few hundred KB, which is fine on Android and ~100–200 KB
    gzipped on the web, loaded as a resource.
  - *Teaching:* a hint that counts the words still possible, nudges toward a letter whose position
    the clues have already pinned down, then explains the deduction. A teacher here works on the
    remaining candidate list and must not see the answer.
  - *Input:* an on-screen keyboard drawn in Compose, so the web needs no phone keyboard.
  - *Size: L* (mostly the word lists and their tests).
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
- [ ] **Nonogram (picture logic).** Row and column run-length clues; fill squares to reveal a picture.
  One answer is provable; hints are line-solving ("this row's 8 clue on 10 squares forces the middle
  six"), the classic teachable technique. Generate from a random pattern and keep it only if a
  line-logic solver finishes it, which also guarantees no guessing. Tap to fill, drag to paint, a
  cross mode (like Kings' crosses). *M–L.* Sizes 5x5 / 10x10 / 15x15 fit a phone.
- [ ] **Cage-sum Sudoku.** Sudoku plus cages whose digits must total a given sum without repeats. Reuses
  Sudoku's grid, digit pad, notes and conflict display, so most of the UI exists. Needs a uniqueness
  solver that handles cages; a teacher using cage combinations ("a 2-cage totalling 3 is {1,2}"). *M.*
  Wait for Sudoku notes to land first.
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
  Needs the same word lists as the word-deduction puzzle (build that first and share them); choose pairs
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
- **Play Store listing.** The app exists in the owner's personal Play account (draft) with the store
  listing, content rating (all ages), target audience (13+), data safety (nothing collected) and every
  other declaration done. What remains is the owner's: choose the app signing key (upload
  `daybook-release.jks` as the app signing key, as the README says, *before* the first bundle is
  uploaded, because Play defaults to its own key and the choice is permanent), upload the first
  bundle to internal testing, make the service account, then run the closed test (12 testers opted in
  for 14 continuous days) before requesting production. Also check the Console home page's Android
  developer verification notice: sideloaded APKs may need their own registration (package name plus
  signing key). Recapture the screenshots at 1080x1920 if the 9:16 promotion eligibility matters.
  The owner is fine with the developer name being public.
