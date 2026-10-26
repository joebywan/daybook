# Lamps (light every square) — spec

A daily **lamp-placing** puzzle: a square grid of white squares and black walls, some walls carrying a number 0-4. Put
lamps on white squares so that every white square is lit, no lamp lights another, and every numbered wall touches exactly
that many lamps. Read `docs/PUZZLE_STANDARDS.md` first; this file records only what is specific to this puzzle. Where the
two disagree on a point not listed here, the standards win.

## Decisions

| | |
|---|---|
| `id` | `"lamps"` (hashed into every seed: never rename). Display name **Lamps**. Tagline "Light every square, no lamp seeing another". The genre is published by Nikoli as *Akari* (also "Light Up"): neither name, nor any Nikoli wording, appears anywhere the player sees. |
| Answer | **Exactly one lamp set**, proved exhaustively. `solved` checks the rules, never the stored answer. |
| Marks | A lamp, or a cross (a note that no lamp goes there; never read by the rules or the teacher). |
| Input | Kings' and Tents' model, taught in the same words: tap a square for a cross, tap again to clear it; double-tap for a lamp; tap a lamp to take it away; drag to lay crosses across squares (one state on lift). A single tap is drawn at once and emitted after the double-tap window, so a double tap is one lamp move, never a cross under it. |
| Bad judgements allowed | Any lamp on a white square is accepted, even one lit by another lamp or one too many for a number. Breaks are shown (debounced 1 s), never refused. |

## Rules

- A lamp lights its own square and every white square in its row and column until a wall or the edge.
- Every white square is lit.
- No lamp lights another lamp (two lamps never share a row or column run with no wall between).
- A numbered wall has exactly that many lamps on the up to four squares beside it (up, down, left, right). An
  unnumbered wall says nothing.

## State (`puzzles/LampsRules.kt`)

```kotlin
@Serializable data class LampsState(
    val size: Int,
    val walls: String,     // per square, row by row: '.' white, '#' blank wall, '0'..'4' numbered wall
    val solution: String,  // per square: '1' lamp in the one answer, '0' not; for mistakes, the fallback and tests only
    val marks: String = ".".repeat(size * size),  // '.' nothing, 'L' lamp, 'x' cross; walls always '.'
    override val moves: Int = 0,
) : PuzzleState
```

Strings, as Tally's and Nonogram's: a saved game keeps two dozen undo copies. What is lit, which lamps clash and which
numbers are broken are derived on every render, never stored. Highlight indices are squares `row * size + col` (walls are
squares too, so a cited number is its wall's index); the board reports them with `highlightGrid(size, size)`.

## Logic (object `LampsLogic`)

- `isSolved(walls, lamps)`: the one rule function the validator, the board's feedback and the generator's last check call.
- Knowledge for the ladder is a grid of `OPEN` / `LAMP` / `NO` per white square; a square lit by a known lamp can never
  hold one, so it is not a *candidate*. A square's *sources* are the candidates that would light it (itself included).
- `countSolutions(size, walls): LampsVerdict` — exhaustive, **no budget**: `None` / `ExactlyOne(lamps)` / `Several`. Only
  `ExactlyOne` carries an answer. Branch on the unlit square with the fewest sources (lamp or not on one of them), with
  the direct rules propagated at every node.

## The ladder (the teacher's and the generator's)

One fact at a time, simplest first, restarting after each:

1. `zero` — a 0: no square beside it holds a lamp (cross them).
2. `done` — a number with all its lamps placed: the rest of its squares hold none.
3. `room` — a number with exactly as many candidate squares beside it as lamps still owed: all are lamps.
4. `only-light` — an unlit square with one source left: that source is a lamp (a square no other square can light is its
   own lamp).
5. `starve` — a lamp on this candidate would light squares beside a number and leave it too few to reach its count: no lamp.
6. `dark` — a lamp on this candidate would light every source of some other square without lighting it: no lamp.
7. `what-if` — suppose a lamp here (or none), follow at most `MAX_LINKS` = 3 of steps 1-4, and a number goes over or short
   or a square can no longer be lit: the opposite holds. Shortest chain first, a lamp tried before none, reading order on a tie.

Rungs: **direct** (1-4), **look-ahead** (5-6, a lamp's consequence one step away), **what-if** (7). Starting knowledge is
the player's lamps (once no mistake is among them); crosses are notes and are never read. A fact the board already shows
(every target crossed, lit or a lamp) is skipped, so a hint is the first thing the player has not yet put down.

**Mistake:** a lamp the one answer does not have. Explained as the first that applies: it lights another lamp; a number
beside it has too many; else "no way to finish keeps it". Sound because the answer is proved unique. Show me takes it away.
**Fallback** (a lamp of the stored answer, `fallback = true`) exists only for boards not made here.

## Tiers and the gate

| | Standard | Hard | Expert |
|---|---|---|---|
| Grid | 7x7 | 10x10 | 12x12 |
| Walls | about a fifth of the squares, symmetric under a half turn | same | same |
| Ladder must need | direct only | look-ahead at least once, no what-if | a what-if at least once |

Tally's `Spec.needs`: a draw whose walk needs more or less than its tier's rung is redrawn, so the rungs set the tiers
apart and not only the size. `LampsRulesTest` asserts every daily board of a year needs exactly its tier's rung.

## Generation

1. Walls: a random half-turn-symmetric set (`Rng(seed + attempt)`), a fifth of the squares.
2. Lamps: shuffle the white squares and put a lamp on each one still unlit; the result lights everything (an unlit square
   can always take a lamp), so it is a legal answer.
3. Number every wall from that answer.
4. Take numbers back, in random order, while the tier's ladder still finishes the board (the Snap lesson: numbers must not
   do all the work). The ladder is sound, so finishing it proves the answer unique; `countSolutions` proves it again
   (`ExactlyOne`, equal to the drawn lamps) before the board ships.
5. The gate above. Up to `ATTEMPTS` draws; none accepted → `null`, and `lastResort` (a fixed board per tier, itself
   accepted by a test) ships instead. Only lists reach the `Rng`; no hash order.

`LampsOracle` (test) is the independent check: one horizontal run at a time in reading order (no lamp, or one of its
squares), columns checked as they go, lit squares and numbers checked once their runs are decided; no propagation at all.

## Board

One canvas, `minOf(maxWidth, maxHeight) / size` a square. Walls in `onSurface` with their number in `surface`; a number
whose lamps are met is drawn in `primary`, one over its count or unable to reach it (lamps + unlit empty squares beside
it, crosses ignored) in `scheme.error` after a 1 s settle. Lit squares take Amber `fill` (static, so reduced motion has
nothing to still); a lamp is an Amber `mark` bulb with an `ink` rim, drawn in `scheme.error` when it lights another lamp
(1 s settle). Crosses are Blue `ink`. Move feel: squares settle, and a numbered wall's squares glow once when its count is
met (`rememberMoveFeel`); haptics are central.

## Walkthrough

A 5x5 board with one answer, enumerated by a test: read the rules (lit runs), a cross on a 0's neighbour (tap), a lamp a
number forces (double-tap), a sweep of crosses, then free play by hints.

## Share card

The generic play-log card; nothing puzzle-specific.

## Measured

A year of daily seeds per tier from 2026-01-01 unless stated: `LampsRulesTest` `a year of daily boards ...`
(`app/build/reports/lamps-generation.txt`), `FallbackTest` `lamps proves every daily board of a year on every tier` and
`LampsTeachingTest` `coverage ...` (`app/build/reports/lamps-teaching-coverage.txt`, 200 boards a tier).

| | Standard | Hard | Expert |
|---|---|---|---|
| Grid | 7x7 | 10x10 | 12x12 |
| Walls (share of squares) | 28.1% | 26.0% | 26.4% |
| Walls numbered (share of walls / of squares) | 50.0% / 14.1% | 51.9% / 13.5% | 48.7% / 12.9% |
| Rung needed (every board of the year) | direct | look-ahead | what-if |
| Draws per board (mean / most, of `ATTEMPTS` = 400) | 2.1 / 10 | 6.2 / 34 | 8.7 / 45 |
| Days reaching `lastResort` | 0 | 0 | 0 |
| Hint steps a board | 11.0 | 25.4 | 43.2 |
| of which starve / dark / what-if | 0 / 0 / 0 | 2.7 / 0.4 / 0 | 5.9 / 0.7 / 5.5 |
| Generation ms, desktop JVM (median / p95 / max; a year a tier, quiet machine, 2026-10-27) | 0.3 / 0.4 / 2.0 | 1.7 / 4.0 / 8.6 | 20 / 71 / 154 |
| Generation ms, Chromium (same) | 0.3 / 0.5 / 0.6 | 3.3 / 7.6 / 18 | 39 / 136 / 296 |
| Generation ms, WebKit (same) | 0 / 1 / 1 | 5 / 12 / 26 | 47 / 149 / 308 |

Walls were first a fifth of the squares: nine in ten Expert draws then failed at once (fully numbered, the ladder still
could not finish), and Expert took 365 ms median on the JVM. At about a quarter, most draws pass and the numbers left are
about half the walls. The longest explanation over a year of walks is 230 characters (a what-if naming its chain), inside
`MAX_EXPLANATION` = 260. Hints over whole Expert walks on the JVM: median 0.04 ms, p95 0.5 ms, max 10 ms.
