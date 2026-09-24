# Daybook

A daily logic-puzzle app for Android. Eleven puzzle types, a new set every day, the entire back
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
| Mosaic | Flood-fill (Kami family) | Recolour an area, merging it with its neighbours, until the board is one colour |
| Sets | SET | Triples that are all-alike or all-different in four traits |
| Atoms | Hashiwokakero (Bridges) | Bond atoms into one molecule, no crossings |
| Snap | Hamiltonian path | One line through every square, numbers in ascending order |
| LITS | LITS (Nikoli) | One L/I/T/S tetromino per region, no 2×2, no same letter touching |
| Tower | Mastermind | Break the hidden colour code from scored guesses |

Three difficulties each, which generally means a larger grid and fewer clues.

## Every board is fair

Generators do not just emit a random board and hope. Each one either constructs a solution first
and works backwards, or verifies with a solver that the clues admit **exactly one** answer:

- **Sudoku, Shikaku, Atoms** — carve or grow a board, then count solutions with a backtracking
  solver and reject anything with two.
- **Mambo** — a stricter bar: clues are stripped only while the board stays solvable by
  *propagation alone*, so it never requires a guess. Mosaic once held this bar too, before it
  turned out to be the wrong puzzle entirely.
- **Mosaic** — the move limit is the proven optimum, found by an exact search over every legal
  fill. Exhausting the search budget yields nothing and the board is reseeded; a truncated search
  must never become a shipped limit.
- **LITS and Kings** — the answer is laid down first, then regions are grown around it one square
  at a time with uniqueness rechecked at each step, because solution count only ever rises as a
  region gains squares. Regions drawn at random essentially never admit one answer. Both were
  rewritten after players hit boards the generator had never actually vetted. LITS additionally
  caps regions at seven squares, keeping the densest of 24 piece layouts per attempt, after about
  one region in six came out at eight or more.
- **Snap** — a Hamiltonian path is generated, then numbers are added until no other path obeys
  them, and then **taken back off again**: every number the rest of the board already implies is
  removed, and a board still over its clue budget is abandoned for a different seed. Stopping at
  the first forced board, which is what this used to do, left every redundant clue on the grid and
  shipped Expert boards with 39 of 42 squares numbered.
- **Pipes** — the solved board is a random spanning tree, so a fully-joined loop-free answer always
  exists.

Two things make that harder than it sounds, and both have gone wrong here:

**A bounded search that gives up must say so.** A solver with a node budget that returns quietly
looks exactly like a solver that finished, so "I ran out of time" gets read as "exactly one
answer". Snap's solver returns a verdict whose give-up case is a *name*, not a number a caller can
misread, and only the proved case may ship. Mosaic reseeds rather than trusting a truncated search
for its move limit. LITS shipped the bug before either did.

**An assertion has to be able to fail.** `app/src/test/.../FallbackTest.kt` exists to catch a
generator quietly degrading to its safety fallback, which would otherwise pass every solvability
test. It has twice needed tightening after passing on boards that were plainly broken — once when
LITS fell back on every single board, and once when it asserted only that Snap numbered *fewer than
every* square while Snap was numbering 39 of 42.

Generation is not always instant: LITS takes roughly 0.1–0.5s and up to 2s on a bad seed, so boards
are generated off the main thread behind a dealing animation.

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

## Publishing to Google Play

Every release already builds and attaches a signed `.aab` alongside the `.apk`. Once Play is set
up, `.github/workflows/publish-play.yml` uploads that bundle to the **internal testing** track.
Until the `PLAY_SERVICE_ACCOUNT_JSON` secret exists the workflow logs a notice and does nothing, so
it is safe sitting here unconfigured.

`release.yml` calls it directly as a reusable workflow rather than letting it wait on the
`release: published` event, because a release created with `GITHUB_TOKEN` does not trigger other
workflows — the same quirk that stops CI running on Renovate's pull requests.

### Get the signing decision right first

Play App Signing is mandatory for new apps: Google holds the key that signs what users actually
download, and you sign uploads with an *upload key*. When you create the app you choose where
Google's copy comes from, and the choice is effectively permanent:

- **Upload the existing `daybook-release.jks` as the app signing key** (Play Console offers
  "export and upload a key from a Java keystore", using Google's PEPK tool). Play-delivered builds
  then carry the same certificate as the sideloaded ones, so a sideloaded install upgrades to a
  Play install in place. **This is the one to pick** given in-place updates are the whole point of
  the signing setup here.
- **Let Google generate a fresh app signing key.** Simpler, but Play builds then have a different
  certificate from the sideloaded APKs, so any device already carrying a sideloaded Daybook has to
  uninstall — losing its streaks and statistics — before it can install from Play.

### The steps only a human can do

1. **Play Console → Create app.** Name, default language, "App", free.
2. **Set up app signing** and upload `~/Documents/github/Claude/daybook-android-signing/daybook-release.jks`
   as the app signing key, per the choice above. Alias `daybook`; the store and key passwords are
   the same string, in `keystore-password.txt` (no trailing newline).
3. **Fill in the declarations:** privacy policy URL, data safety, content rating, target audience,
   ads (none). [`PRIVACY.md`](PRIVACY.md) is written for this — its rendered GitHub URL works as
   the policy link, and the data safety answers are all "no data collected", which is true: the app
   declares no `INTERNET` permission.
4. **Upload one bundle by hand** to internal testing. Play will not accept API uploads for an app
   that has never had a release created in the console.
5. **Make a service account:** Google Cloud Console → IAM → Service Accounts → create → create a
   JSON key. Then Play Console → Users and permissions → invite that service account's email →
   grant *View app information* and *Release to testing tracks*.
6. **Add the JSON** as the `PLAY_SERVICE_ACCOUNT_JSON` repository secret.

From then on it is automatic. `workflow_dispatch` on that workflow also lets you push an existing
release to `alpha`, `beta` or `production` by hand.

### A note on the bundle check

`tools/verify-aab.sh` fails an unsigned bundle but does **not** pin its fingerprint, unlike the APK
check. With Play App Signing the bundle carries the upload key, which is allowed to differ from the
app signing key and may be rotated. Worth knowing: an unsigned bundle is still called
`app-release.aab`, with no `-unsigned` in the name to give it away, which is why that check looks
inside for a signature block rather than trusting the filename.

## Dependency updates

Renovate runs weekly from `.github/workflows/renovate.yml`, self-hosted so it needs no GitHub App
installed. Patch bumps and GitHub Actions automerge; minor and major open a PR to look at.

One caveat worth knowing: pull requests opened with the default `GITHUB_TOKEN` do **not** trigger
other workflows, so CI does not run on Renovate's PRs as shipped. Either install the
[Renovate App](https://github.com/apps/renovate) or add a PAT as `RENOVATE_TOKEN`, and CI will
run on them — at which point automerge can safely be widened to minor updates.

## Not done yet

- Pencil marks / candidate notes in Sudoku
- No accessibility work: the eight boards drawn with raw pointer input expose no click actions, so
  a screen reader cannot operate them
- Play Store listing not yet created — see "Publishing to Google Play" above
