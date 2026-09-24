# Daybook — working notes for Claude

A daily logic-puzzle Android app. Eleven puzzles, generated on device, no ads, no
subscription, no network. Read this before changing anything; it exists so you don't
rediscover what has already been learned here the hard way.

## Build

Gradle **requires Java 17**. The system default is Java 25 and Gradle refuses to run on it.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/user/Android/Sdk
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

`extraWarnings` is **on**. It was turned on after a probe showed the build reported nothing
for unused declarations, which made "compiles with no warnings" a much weaker claim than it
sounded. Do not turn it off.

## Architecture

- `core/PuzzleType.kt` — the contract. Adding a puzzle is one file in `puzzles/` plus one line
  in `core/PuzzleRegistry.kt`; home grid, archive, streaks, stats, hints and saves pick it up.
- `generate(seed, difficulty)` **must be pure**. Daily boards come from
  `hash(date, puzzleId, difficulty)`, so purity is what makes the whole archive free and offline.
- `PuzzleState` is a sealed `@Serializable` interface living in `puzzles/` — Kotlin requires
  sealed implementations to share the sealed type's *package*, not merely its module.
- `Preview(modifier)` draws a fixed motif for the home grid. **Never call `generate()` in it** —
  eleven of them draw on every composition.

## Rules that keep being relearned

**Never ship a board the generator has not proved.** Kings, LITS, Mosaic and Shikaku each had a
fallback that was reachable and unvetted, and in three of them that fallback was what players
actually got. LITS's real generator had *never once run*. When you add a fallback, ask what
fraction of seeds reach it, and measure rather than assume.

**A truncated search is not a proof.** LITS reported "gave up" as "exactly one solution" because
its node budget returned quietly. Make the distinction structural — a type where only the proved
case carries a result — not a count a caller can misread.

**`solved` must check the rules, not compare to a stored answer.** Kings and LITS both rejected
correct solutions this way. Keep the stored solution for hints; let a validator decide success.

**Size layouts from both axes.** Three home-screen motifs and the Sets board each drew outside
their box because height fell out of width via `aspectRatio` and nothing consulted the height
available. `Mosaic.Board` has the right shape: `minOf(maxWidth / w, maxHeight / h)`.

**`PlayScreen` pushes an undo entry for every state it is handed.** Transient UI state —
selected colour, palette choice, drag in progress, a settle timer — must live in
`remember`/`rememberSaveable` inside the composable, never in `PuzzleState`. A drag or a
double-tap must emit exactly one combined state.

**Feedback must not move the board.** Mambo's caption shifted it ~45dp when an error appeared,
which causes mistaps. Reserve the space (`minLines == maxLines` works well). Violations also
wait ~1s debounced, because a player passing through an illegal intermediate state should not be
shouted at.

**Forcing a board is not the same as forcing it minimally.** Snap added clues until its solver
said "one answer" and stopped there, which left every clue that had been essential when it went on
and redundant five clues later — 62% of every board numbered, and Expert boards with 39 of 42
squares filled in. Adding until forced is only half the job: take back everything the rest of the
board already implies, and give the result a budget it has to come in under. Ask what fraction of
the board a generator gives away, and measure it, because a board with one answer can still be a
board with nothing left to work out.

**A drag that emits state per step cannot key `pointerInput` on the state.** Every other board
accumulates its sweep in `remember` and emits one combined state on lift, so keying on `s` is fine
there. Snap emits a square at a time, so it keyed on `s.waypoints` to stop the detector restarting
mid-drag — and because those never change, the coroutine never restarted and the `s` it captured
stayed pinned to the board as first composed. Lifting your finger and starting a second drag reset
to that empty board and wiped the line; Snap was completable only in one unbroken 42-square drag.
If a gesture detector must outlive state changes, read the board through `rememberUpdatedState`,
never the captured value.

**Derive, don't store, anything computed from board state.** Kings' eliminations and LITS's
impossible squares are recomputed per render. Storing them means owning which to retract when a
piece is lifted, which is where the feature rots.

**Verify by rendering, not reasoning.** Icons, motifs, crescents, pipe joints and crosses have
all failed at true size in ways nobody predicted — a crown read as a comb, pages as a boat hull,
Atoms as a wireframe. A Java2D harness driving the real geometry is the established approach.
Then check on the emulator; several bugs only appeared there.

**Write agent patches early.** One agent lost a complete implementation by leaving
`git diff --cached > patch` until the end and dying on a rate limit.

## Tests

128 of them. New tests should be **independent of the code they check** — Mambo, LITS, Kings,
Shikaku, Mosaic and Snap tests each carry their own solver or rule checker, deliberately written on
a different principle so the two cannot share a blind spot. `LitsAuditTest` and `MosaicOptimumTest`
are differential; `LitsMarkingTest` brute-forces every legal shading of a fixed board.
`SnapCluesTest` is the clearest case of the principle: its solver is naive depth-first with no node
budget, no pruning, and the waypoint order checked only once a full line exists — the opposite of
the generator's on every one of those points. It is slow, several seconds per Expert board, so the
sample thins as the boards grow rather than dropping the large ones.

`FallbackTest` exists because a generator can degrade silently. It has twice needed tightening
after passing on plainly broken boards: once while LITS fell back on every board, because it
asserted a property the fallback also satisfied, and once while Snap numbered 39 of 42 squares,
because it asserted only "fewer than every square". A test that cannot fail is not a test, and an
assertion loose enough to survive the bug is the same thing wearing a number.

## Git and releases

- Never commit to `main`. Branch, PR, merge — I do the merging, not the owner.
- Commits use `4845431+joebywan@users.noreply.github.com`. The personal address must never reach
  a commit or a remote. No `Co-Authored-By: Claude` trailer.
- Push to `main` builds a signed APK and publishes a GitHub Release automatically.
- Signing key: `~/Documents/github/Claude/daybook-android-signing/` — the only readable copy.
  Certificate pinned in `android/release-key.sha256`; `tools/verify-apk.sh` fails a release whose
  certificate, name or debuggable flag is wrong. A green Gradle build is **not** proof of signing.

## Settled — do not reopen

- `applicationId = com.joebywan.daybook` and the signing certificate are permanent.
- The app is named Daybook. Display name can change freely; the package cannot.
- Mosaic's move limit equals the proven optimum, slack zero on every tier. Difficulty comes from
  board size, sections and palette, not from spare moves.
- Mosaic is a Kami-style region flood-fill, not Fill-a-Pix.
- Snap boards keep **exactly one** answer, and their difficulty comes from how little is given
  away rather than how much. Clue counts are minimal at every tier — about a fifth to a quarter of
  the board — and Standard being harder for it is the intended trade, confirmed by the owner:
  "I should have to make choices and think. It's boring otherwise." Do not add clues back to soften
  a tier, and do not relax uniqueness to open the board up further.
- LITS regions are capped at seven squares, and its win check does **not** require the shading to
  be connected. The generator still proves uniqueness among connected shadings; that only decides
  which boards ship.
- Accessibility is knowingly absent and deliberately deferred while this is sideloaded.

## Open

Sudoku pencil marks; accessibility — eight boards use raw pointer input and expose no click
actions, so a screen reader cannot operate them.
