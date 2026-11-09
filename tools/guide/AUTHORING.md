# Writing a guide page

One puzzle, one file: `tools/guide/pages/<puzzle id>.txt` (`id` from the puzzle's object; the app will link to `guide/<id>#<technique>`). The index
picks it up. Copy `pages/sudoku.txt` and replace everything. `.txt`, not `.md`: `pages.yml` ignores `**.md`, and the docs-only
merge rule would skip the check.

```bash
python3 tools/guide/build.py --out /tmp/guide    # then serve /tmp/guide and look, at 375x537 and tall, light and dark
./gradlew :app:testDebugUnitTest --tests '*GuideTest*'
```

## Where each fact comes from

| Part of the page | Source |
|---|---|
| `# Title` | the puzzle's `displayName` (Trail, Sun & Moon, Codebreaker, Arrows..., never the code name) |
| `> tagline` | its `tagline` |
| `## How to play` | Working the screen: its `rules` (the gestures), `keyboardHelp`, what feedback looks like, the "Teaching" notes in `CLAUDE.md`. No hints or undo? Say so (Arrows, Mines) |
| `## Rules` | What the player must do to succeed, and nothing else: its `rules` with the gestures taken out. No controls, feedback, strategy or promises about the board |
| `## What changes per tier` | `- Standard: `, `- Hard: `, `- Expert: `, each starting with the exact line from `core/TierNotes.kt`, then anything else the player can see, from `CLAUDE.md` |
| `## Techniques` | `core/TechniqueNames.kt`: every id, in that order, under `### id \| Name`, the name with its first letter capitalised, as Training shows it. Nothing else: mistakes and fallbacks have no name and no entry. A step in `NOT_PRAISED` only points at a word or a guess, or a hint never gives it, so include it only if there is something real to say, in prose; it needs no board. A puzzle with nothing to say has no Techniques section |
| What a technique means | the teacher's own `explanation` and the comment at the top of `puzzles/<Name>Teacher.kt` |

Those four sections, in that order, nothing else. Voice: plain, friendly, specific, and short. Never "harder": say what changes. No genre or
trademark names (`GuideTest` holds the list, and `CLAUDE.md`'s section for the puzzle says what it must not be called); add to the list if
your puzzle has one it lacks. Measurements a player cannot see (draws, milliseconds, percentages of a walk) stay out.

Format: blank lines separate paragraphs, `- ` lists, `**bold**`. Nothing else is Markdown.

## Boards

Every praised technique has at least one. A board is real text, drawn as an inline SVG, so it follows light and dark and needs no capture when the art changes:

```
~~~board
5 3 .
. x _
box: 3
regions: aab acc
focus: r1c1-r1c3
cite: r2c2
answer: r1c3=7
notes: r2c1=27
struck: r1c1=4
lines: r1c1-r1c2 r1c2-r2c2
edges: r1c1-r1c2=<
cages: aab acc
corners: r1c1=12
solid: r2c3
caption: One or two sentences saying what the board shows. Required.
~~~
```

- **Rows** are space-separated tokens, one per square: any short text, `.` an empty square, `_` no square at all (a clue margin, a hole).
- `box: n` thick walls every n squares; `regions:` one string a row, one letter a square: a tint and walls per letter.
- `focus` the tint (the step's `focus`), `cite` a ring (what the reasoning leans on), `answer` the filled square that is the move.
- `notes` small marks, a digit in its pencil place; `struck` the same, crossed out. Never on the `answer` square.
- `notes` with a comma (`r2c3=cat,dog`) stacks small words down the square instead.
- `lines` thick lines between square centres (bonds, loops, paths): `r1c1-r1c4` one line, `r1c1=r1c4` two side by side, `r1c1xr1c2` a cross on the edge between two squares. A board with `lines` gives its digits a halo.
- `moves` the same, drawn over `lines` (which turn grey) in the move's colour, for a move that is a line rather than a square.
- `edges` something printed between two neighbours: `r1c1-r1c2=<` and `>` draw a chevron (the first is smaller, bigger), anything else short sits on a disc (`r1c1-r2c1=x`).
- `cages` one string a row, one letter a square: a dashed outline per letter, with `box` walls still drawn.
- `corners` small text in a square's top-left (a cage total, a region's rule).
- `solid` squares filled dark in both schemes, their text reversed (a wall, a hidden square, a filled square of a picture).
- `tiles` marks on a guess: `r1c1=right`, `near` or `out`, each its own shape as well as a colour.
- `draw` how a token is drawn: `spokes` (a pipe tile, its arms named from `urdl`), `discs` (a number in a disc, so lines pass behind it), `pearls` (`b` and `w`), `dots` (a dot at every corner, and `lines` and `moves` run along the squares' sides from dot to dot: `r1c1` is the top-left corner of square r1c1, up to one past the last row and column).
- `top` and `left` label the columns and rows, outside the grid, one space-separated label each (`top: a b c d e f g h`, `left: Ann Ben`). A narrow board with labels gets side margins.
- In `regions`, `.` is a square in no region: no tint, and no walls between such squares (a clue margin).
- A token over three characters is drawn small, and `_` inside a longer token is a space (`r3c1=not_4`).
- Squares are `r<row>c<col>` from 1, and `r1c1-r3c3` a rectangle.

For what these cannot draw (a hex, a wheel, a tube): first try tokens in a grid (Cipher's sum is rows of letters). If that really cannot
show it, add one small key to `board()` in `build.py`, with its check in `GuideTest`. Do not paste in PNGs, and do not draw for one page what others could share.

**Positions come from the game, never from your head.** Find them:

```bash
DAYBOOK_GUIDE_FIND=<id> ./gradlew :app:testDebugUnitTest --tests '*GuideTest*' --rerun
```

writes `app/build/guide-positions-<id>.txt`: up to 40 positions per technique, each with the seed, the step's `focus`, `cited`, `targets`, its words and the
state as JSON. Pick one where the point is easy to see (few cited squares, short explanation) and copy the board out. Work it through by hand before writing the caption.

**Add your puzzle to `GuideTest.teachers`.** A page with no line there fails. The line turns a board into the puzzle's state and returns what its teacher says
on it; the test then requires that step's technique to be the heading it sits under, its `focus` to be the board's and its `targets` the `answer` squares (a
board whose highlights are not `row * width + col` maps them first). Add what else can be checked cheaply (`sudokuStep`: one answer, the digit, pencil marks really possible).
A teacher that reads more than the rows show (a stored list, a clue) takes the rest from a fixture in the line.

## Before you finish

The page, with its `GuideTest.teachers` line, is the whole change. Look at the rendered page, every board, at phone width in both schemes. Report any fact you
could not trace to a source rather than guessing.
