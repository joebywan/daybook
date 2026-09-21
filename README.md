# Daybook

A daily logic-puzzle app for Android. Ten puzzle types, a new set every day, the entire back
catalogue open from day one.

No ads. No subscription. No accounts. No network permission in the manifest at all.

## Why it works offline and for free

Every daily puzzle is *generated on the device* from its date:

```
seed = hash(date, puzzleId, difficulty)   ->   puzzle
```

The generator is a pure function of that seed and uses a hand-rolled splitmix64 PRNG rather than
`java.util.Random`, so the same date produces the same puzzle on every device and every Android
version, forever, with nothing downloaded.

That one decision is what removes the paywall. There is no archive to host, so there is no archive
to charge for — "play any past day" is just asking for a different date.

## The puzzles

Each is a classic, published puzzle genre, implemented from its rules.

| Name | Genre | What you do |
|---|---|---|
| Sudoku | Sudoku | 1–9 once per row, column and box |
| Kings | Star Battle / Queens family | One king per row, column and region, none touching |
| Mambo | Takuzu / Binairo | Two symbols, balanced lines, never three alike, `=` and `x` links |
| Pipes | Net | Rotate tiles until every pipe joins into one network |
| Shikaku | Shikaku (Nikoli) | Cut the grid into rectangles, one number each, number = area |
| Mosaic | Fill-a-Pix | Each number counts filled squares in its 3×3 neighbourhood |
| Sets | SET | Triples that are all-alike or all-different in four traits |
| Atoms | Hashiwokakero (Bridges) | Bond atoms into one molecule, no crossings |
| Snap | Hamiltonian path | One line through every square, numbers in ascending order |
| LITS | LITS (Nikoli) | One L/I/T/S tetromino per region, connected, no 2×2, no same letter touching |
| Tower | Mastermind | Break the hidden colour code from scored guesses |

Three difficulties each, which generally means a larger grid and fewer clues.

## Every board is fair

Generators do not just emit a random board and hope. Each one either constructs a solution first
and works backwards, or verifies with a solver that the clues admit **exactly one** answer:

- **Sudoku, Mambo, Shikaku, Kings, Atoms** — carve or grow a board, then count solutions with a
  backtracking solver and reject anything with two.
- **Mosaic** — a stricter bar: clues are stripped only while the board stays solvable by
  *propagation alone*, so it never requires a guess.
- **LITS** — the shading is laid down first as a legal tetromino set, then regions are grown around
  it, then the solver confirms uniqueness. (Regions drawn at random essentially never work; this
  was rewritten once after a test caught the fallback firing on every board.)
- **Snap** — a Hamiltonian path is generated, then numbers are added one at a time until no other
  path obeys them.
- **Pipes** — the solved board is a random spanning tree, so a fully-joined loop-free answer always
  exists.

`app/src/test/.../FallbackTest.kt` exists specifically to catch a generator quietly degrading to
its safety fallback, which would otherwise still pass every solvability test.

## Adding a puzzle

Two steps.

1. Add a file under `puzzles/` with a state class implementing `PuzzleState` and an object
   implementing `PuzzleType`.
2. Add that object to `PuzzleRegistry.all`.

Home screen, daily rotation, archive, streaks, statistics, hints, undo, restart and the results
card all pick it up automatically. `generate(seed, difficulty)` must be pure — that is the only
hard rule, and it is what keeps the archive free.

## Building

Gradle needs **Java 17**; the system default here is Java 25, which Gradle 8.14 rejects.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
```

Run the generator tests (these are the ones worth keeping green):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew test
```

## Releases, CI and signing

Every push to `main` builds a signed release APK and publishes it as a GitHub Release.
Versions come out as `<version.txt>.<run number>` — bump `version.txt` for a meaningful
version, otherwise the run number keeps `versionCode` rising on its own.

**Signing is what makes updates install over the top.** Three things are checked before
anything is published, because each has broken an Android release before:

1. **Signed at all.** A green Gradle build proves nothing — with any signing variable missing,
   AGP quietly emits `app-release-unsigned.apk` and exits zero. `-PrequireSigning=true` turns
   that into a hard failure, and `tools/verify-apk.sh` rejects an artifact named `*unsigned*`.
2. **Signed with *the* key.** The certificate SHA-256 is committed to `android/release-key.sha256`
   and the build fails if the APK does not match it. A changed key means every installed copy has
   to be uninstalled before the next version will install.
3. **Not debuggable.** Release builds set `isDebuggable = false` explicitly and the artifact is
   checked with `aapt2`. A debuggable APK is the profile Play Protect scrutinises hardest, and
   declining its scan is what surfaces as Android's generic "App wasn't installed".

`tools/apk-cert.py` reads the certificate out of the APK's own signing block rather than shelling
out to `apksigner verify --print-certs`, which has been seen to print nothing on a CI runner and
still exit zero — turning the guard into a no-op.

CI builds `assembleRelease` too, never `assembleDebug`. Testing a different variant from the one
that ships is how a broken release goes out repeatedly behind a green check.

### Repository secrets

| Secret | What |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the keystore, `base64 -w0` |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | `daybook` |
| `ANDROID_KEY_PASSWORD` | key password (same as the store password) |
| `RENOVATE_TOKEN` | *optional* PAT — see below |

GitHub secrets are write-only. The keystore and its password live outside the repo at
`~/Documents/github/Claude/daybook-android-signing/` (0700, files 0600) and that is the only
readable copy. Losing it means every installed copy must be uninstalled to update.

## Dependency updates

Renovate runs weekly from `.github/workflows/renovate.yml`, self-hosted so it needs no GitHub App
installed. Patch bumps and GitHub Actions automerge; minor and major open a PR to look at.

One caveat worth knowing: pull requests opened with the default `GITHUB_TOKEN` do **not** trigger
other workflows, so CI does not run on Renovate's PRs as shipped. Either install the
[Renovate App](https://github.com/apps/renovate) or add a PAT as `RENOVATE_TOKEN`, and CI will
run on them — at which point automerge can safely be widened to minor updates.

## Not done yet

- Pencil marks / candidate notes in Sudoku
- Per-puzzle "give up and reveal" is implemented on the type but not wired to a button
- No app icon beyond a placeholder vector
- No Play Store listing; releases are sideloaded APKs from the Releases page
