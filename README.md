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

## Not done yet

- Pencil marks / candidate notes in Sudoku
- Per-puzzle "give up and reveal" is implemented on the type but not wired to a button
- No app icon beyond a placeholder vector
- Release signing is not configured
