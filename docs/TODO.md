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

- [ ] **Sudoku and Shikaku decide `solved` by comparing to the stored answer** (`SudokuState.solved` is
  `cells == solution`; `ShikakuState.solved` is `blocks.toSet() == solution.toSet()`). Safe only while
  the board has exactly one solution; Kings and LITS both once rejected correct answers this way. Make
  both check the rules, keep the answer for hints only. *M.* (`docs/PUZZLE_STANDARDS.md` §4, §12)
- [ ] **Sets can fail outright.** `Sets.generate` throws via `error()` after 4000 redraws, and its tests
  walk only 12 seeds. Measure the failure rate over a few years of daily seeds, then make failure
  impossible or structural. *M.*
- [ ] **Mambo's generator has a `!!` on a second attempt** that could throw. Measure how often the second
  attempt is reached and make it safe. *S.*
- [ ] **Atoms Standard sometimes comes out lighter than the tier intends:** one 2026 board with 6 atoms and
  two with 9, where the tier asks for 10. Still valid puzzles. Decide whether to fix (changes those
  boards) or accept; if fixing, regenerate the pinned parity lines for the dates that move. *S.*
- [ ] **Hint popover can cover Sudoku's digit pad** on a very short screen (360x640) when the hint is in
  the top-left box: the other side would cover the highlight. Close and Show me stay reachable.
  Consider shrinking the pad or nudging the popover. *S.*
- [ ] **Undo leaves a selection highlight behind in Sudoku and Sets** on the web (the move itself is undone).
  Cosmetic; check whether Android does the same. *S.*
- [ ] **Loading state polish.** "Setting out X..." is a plain text line. (a) Android: add a spinner, since
  generation is off the main thread and it would animate. (b) Both: show the message only if generation
  takes longer than ~150 ms, so quick boards do not flash it. The web cannot animate while generating
  (one thread). Since the LITS speed-up the slowest board is under 0.1 s, so this is low urgency. *S.*
- [ ] **Sudoku pencil marks.** (also in `CLAUDE.md` Open and the README) *M–L.*
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
  all eleven boards consult `maxHeight` (Tower fits its box with a bounded `LazyColumn`); the README and
  `PuzzleType` KDoc say adding a puzzle is two steps, but it also needs a `ParityFingerprint.body`
  branch, a `StateSerializationTest.mutate` branch and a parity pin. *S.*
- [ ] **Snap's solved layout was never screenshotted** (it has no hints, so the harness never reached it).
  It shares the code path, so low risk. *S.*

## Infrastructure

- [ ] **CI on Renovate's PRs.** PRs opened with the default `GITHUB_TOKEN` do not trigger CI, yet GitHub
  Actions bumps automerge. Add a `RENOVATE_TOKEN` PAT (README, "Dependency updates") so automerge is
  gated by a real build. *S, needs the owner to create the token.*
- [ ] **Pages HTTPS.** The Pages API reports `https_enforced=false` with no CNAME on the project site
  (it is served under the owner's existing custom domain). It works over HTTPS today; confirm
  enforcement is on at the domain level, since the service worker requires HTTPS. *S.*
- [ ] **Runner label change.** `ubuntu-latest` moves to Ubuntu 26 on **2026-10-19**. Watch the first run
  after that date; Android SDK setup or the wasm toolchain could break.
- [ ] **Dependency majors** are well behind: Kotlin 2.2.10 (2.4.x available), Compose Multiplatform 1.9.3,
  AGP 8.x (9 available), Gradle 8.14 (9 available), kotlinx-datetime 0.7.1 (0.8.0). Renovate opens PRs;
  the majors move together and need Android and web checked as a pair. CI's JDK is held at 17 until
  Gradle 9 (`renovate.json`).
- [ ] **Web: a board already generating cannot be interrupted.** A tap during a slow pre-generation on Home
  waits for it. Fine at current speeds; a Web Worker would fix it if generators slow down again. *L.*

## Owner's call

- **`gradle/actions` v6.** Newer than the v5 we use, but upgrading means accepting Gradle's Terms of Use
  for a proprietary caching component. Held at v5 in `renovate.json` until decided.
- **Play Store.** Listing not created (README, "Publishing to Google Play"). Creating a developer account
  publishes a developer name and contact details, which is the real privacy decision. The package name
  and signing key are settled. (`CLAUDE.md`, "Settled")
- **Portrait lock.** The app is locked to portrait because landscape broke most boards. Revisit if wanted.
- **Where the web version lives.** It is served from a path on the owner's existing custom domain via
  Pages. A dedicated domain or `github.io` address is possible if that stops being convenient.
