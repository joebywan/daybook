# Rewards and motivation

The aim is to lift mood, not to extract time or money. Everything here works offline and is derived from
the play history, not stored as counters (old saves must keep loading). Tasks are in `docs/TODO.md`
under "Rewards".

## Decided (with the owner)

- **A streak is a habit, not a perfect record.** It stays alive while the player has played on **at least 5 of the
  last 7 days**. The longest possible gap is two days, however long the streak is, so a 100-day streak cannot hide a
  20-day absence. The count is **days played**, not calendar days. Today is not a miss until the day is over.
- **Welcome back, never a bare 0.** After a break, say so warmly. Show "best streak" only when it differs from
  the current one and there is room.
- **Praise is public and loud.** Beyond finishing the board, say what else was just achieved: first solve of the day
  (extends the streak; bigger on 7/30/100), personal bests, achievements. One celebration per solve, queued, never
  blocking the next move; the existing mute setting silences them.
- **Hints are never scolded.** Praise names the deduction the hint showed ("look for it next time") and the
  perseverance on a hard board. No hint-count shaming.
- **Offline comparisons are against the player's own history** ("faster than 80% of your solves"). Do not claim a
  percentile of *players*: there is no population data, and the app must not state things that are not true.
  Population baselines would need real times from testers, which is its own decision.
- **No goodbye screen.** Predicting when the player is done needs usage tracking; "all three Kings done today"
  (per-puzzle completion) covers it without guessing.
- **Not doing:** points/XP (levels as fixed packs of boards are a separate idea, reopened by the owner on 2026-10-22: `docs/LEVELS_SPEC.md`), loss-framed notifications ("your streak is about to end"), leaderboards,
  random or login rewards.

## Streak implementation (`core/Streak.kt`)

`streakOf(playedDays, today): Streak(current, best, atRisk)` (+ `alive`, `lapsed`), derived from the daily
`Completion.day`s; nothing new is stored. A run starts on a played day and is judged each day over the last
seven days, never reaching back before the run's first day: alive while at most 2 of those days were missed
(which is 5 of 7 for an established run, and "at most 2 missed since the first play" for a new one). The third
miss ends the run and the next play starts a fresh one. `current` counts days played. Home shows "Welcome back"
in place of the badge when lapsed, and "Day one awaits" before the first daily solve, so never a bare 0 (the stats
screen's Streak tile shows "-" for both); best streak is on the stats screen (the home header has no room for it).
The rule is told once, under the stats calendar: "Play on 5 of any 7 days to keep your streak going."

## Finished-frame praise (`ui/play/FinishPraise.kt`)

`finishPraise(history, solve, today, puzzleName): Praise(title, big, lines)` is pure and tested (`FinishPraiseTest`);
`PlayScreen` asks for it once, before `onSolved` records the solve, so `history` never contains it. Never stored.
- **Title.** The first daily solve of *today's* date (archive days and practice get none) announces the streak: "N day
  streak" (bigger and bold at 7/30/100), "Day one", or "Welcome back" after a lapse. When yesterday was missed and the run
  goes on, the line is "You're back, and your streak is still going." Any other solve gets a short hand-written title
  (`finishTitle`), rarest first: the first solve of that puzzle ever "First one done.", a record at the tier "A new
  best.", an Expert solve "Expert, cracked.", then a pool: no hints "All your own work." / "Worked it out." / "Clear
  thinking.", with hints "Nicely done." / "Well solved." / "Lovely work." / "There it is." / "Neatly done." / "That's
  the one.". The pool line is picked by a hash of the day, puzzle id and tier written out in the code (never `Random`,
  never `String.hashCode`), so a solve always reads the same and a rotation cannot change it. Every title is at most
  `TITLE_MAX` (18) characters, which fits the banner between its poppers at 360dp. "Congratulations!" is left only for a
  frame nobody judged (a reopened solved board, whose frame keeps its Share button: the card is drawn again from the saved
  board and log when the pill opens it). A level's fifth board says "Level N done".
- **One line** beneath, in priority order: fastest at this tier (strictly faster than a non-empty tier history; a tie is
  not a record), perseverance (>= 10 min, or >= 2x the tier's median with 5+ solves and >= 4 min), "Faster than N%
  of your own solves" (5+ solves, N rounded down, shown from 75), then the technique line (`techniqueLine`, below), else
  hints ("Hints are how a move sticks. Look for that one next time.", never a count), else the puzzle note, else "No hints needed."
- **The technique line names the method**, because praise that names what was done beats generic praise. Names are
  `core/TechniqueNames.kt`: a short noun phrase for every teacher's technique, in the hints' own words, at most 40
  characters (`TechniqueNamesTest` fails for a technique without one; mistakes and fallbacks have none). Two honest sources:
  - **A hint solve:** the techniques of the hints the player opened. `SavedGame.taught` records the opened deduction's
    technique beside each counted hint (mistakes left out; defaulted, so old saves load; `PlayLog`'s characters are
    unchanged). The hardest of them (each puzzle's list follows its ladder, simplest first) gives "You learned a digit's
    only place in a box." Only praised techniques count: steps that are not a skill the player now has (`NOT_PRAISED`:
    Mosaic's last fill and fill within the limit, Sets' continued and fresh sets, Tower's and Lexicon's opener, submit,
    guess and last code or word, Target's steps, Word Wheel's clue, Sand's finishing pour) keep a name but are skipped, and
    a solve whose hints showed only those gets the generic hint line. Every teacher but Target has something praised
    (`TechniqueNamesTest`).
  - **A no-hint solve** claims nothing the player did, only what the board needed: "This board needed the ways a line can
    make its target: solved without help.", in the puzzle note's place. From `PuzzleType.neededTechnique`, which only four
    puzzles answer, because only their generators already measure it exactly and cheaply: Tally (`TallyLogic.depth`: the ways
    of a line on Hard, a what-if on Expert), Cipher (the hardest step of `CipherTeacher.walk`, which is how its tiers are cut)
    Dominoes (`DominoTeacher.solveLevel`: region values, counting, a what-if by tier) and Codeword (the hardest step of
    `CodewordTeacher.walk` from the givens: a crossing on Hard, every word together on Expert). Not shown when it is the puzzle's
    simplest step. The rest keep the note: their boards are proved by a ladder or a search, but nothing measures the hardest
    step a board needs without walking the whole teacher (Cage's tiers, for one, only make the 45 rule likely, not certain).
- No new sound: the existing solve chime plays, muted by the existing setting. A milestone fanfare is not done.

## Achievements (`core/Achievements.kt`, `ui/stats/AchievementsScreen.kt`)

18 of them, all derived from the completion history; nothing is stored. Each is a predicate over the history; a solve
*earns* one when it is false over the history before and true with the solve added (`newlyEarned`). An old player whose
history already satisfies one sees it earned in the list and is never told, so there is no flood on update.
- Solves: first solve; 100 solves. Days: play on 30 different days.
- Streak (best run ever, so it never goes away): 3, 7, 14, 30, 60, 100, 365 days. Per puzzle (that puzzle's own played
  days through `streakOf`): 7 and 30 days.
- Top tier (Expert): first solve; a solve with no hints.
- One of each puzzle; all three difficulties of one puzzle's daily on the same day; every puzzle's daily on one day;
  today's three on one tier on the day (see "Today's three").
  The last two and "one of each" read the ids from `PuzzleRegistry`, so they scale; adding a puzzle makes them unearned
  until it is played (nothing is stored to keep them). Tests pass a fake id list.
- Finished frame: see "The finished frame" below. `Praise.achievements` is the list this solve earned (id, title,
  description), in list order; the old single "Achievement: A +2" line is gone.
- Badges (`ui/stats/AchievementBadge.kt`): drawn in Canvas, no assets. One medal-on-ribbons shape per
  `AchievementCategory` (streak green, per-puzzle streak amber, coverage clay, milestone/top tier blue), centre glyph per
  id from `BADGE_GLYPHS` (the number, a star or a crown; a test fails on an id without one). Unearned = a flat
  low-contrast silhouette of the same outline, no glyph. The Achievements screen is a 3-column grid in definition order
  (so badges keep their place), title and description under each. Home header has a rosette button (`MedalIcon`) that opens
  it; back returns to wherever it was opened from (`Route.Achievements.fromHome`). The header buttons are 36dp and the
  title 24sp so they fit at 360dp. Statistics has no button: the streak badge opens it (a chart icon on its left).
- `streakOf` is only monotone when the new play is the newest day; an archive play can in rare cases split a run, so
  a streak achievement could in theory read unearned again. The diff never announces an already-earned one.

## The finished frame (`ui/play/PlayScreen.kt`, `ui/play/FinishExtras.kt`)

Owner's design (Figma "Daybook-achieve-draft"): a full-screen overlay on the solved board. A BANNER at the top
("Congratulations!" or the streak title between two drawn party poppers, `PartyPopper` in `FinishExtras.kt`, then the time
and one personal line), the next-step BUTTONS in a box at the bottom, and the CENTRE left open so the board shows through.
- **Full show or light flourish** (`Praise.full`, decided in `finishPraise`, tested in `FinishPraiseTest`): the full
  confetti for the first daily solve of today, an Expert solve, a personal record, a streak milestone (7, 30, 100) or an
  earned achievement; any other solve gets a light flourish, 14 pieces for 1.2 s. A frame with no verdict (the render
  hook) is full. The banner and poppers are the same either way.
- **The solving move** runs one wave of the board's accent across every square in reading order (350 ms, `SOLVE_WAVE_MS` in
  `ui/MoveFeel.kt`), and the frame and the confetti wait that long before they show. Not when the board was already solved
  when it opened, and not under reduced motion (then the frame shows at once).
- **Milestones move** the first time the frame shows (as the confetti plays, so not on a rotation or once it has finished):
  an extended streak counts up in the title, from 1 to its new length over 600 ms, drawn over the final title's own box so the
  banner does not change width; at 7/30/100 the title stamps in from 130% with a small spring settle; an earned badge flips in
  about its vertical axis (as does each next badge tapped to). Under reduced motion: the final values at once.
- **Confetti** (`Confetti`): 70 pieces on a Canvas over the whole screen, ~2 s, once per finish (`confettiPlayed` is
  `rememberSaveable`, so a rotation does not replay it), takes no input. Not drawn at all under the system's reduced motion
  (`LocalReducedMotion`, below); the Sound switch governs the chime only. No emoji: the web build has no emoji font.
- **Centre** (`ResultsBlock`), only when the solve earned achievements; otherwise empty (no silhouette, no block). One
  achievement at a time: badge (`AchievementBadge`, 72dp, 44dp under 640dp tall), title, description. With several: "1 of 3",
  dots and "Tap to see next achievement"; one tap on the block shows the next and it **wraps** after the last.
- **"Only X to go"** (`core/Achievements.kt`: `remaining(id, history, today, ...)`, `nearestToGo`): one line for the
  nearest achievement 1 or 2 steps away after this solve (fewest steps, then list order). It sits under the badge description
  when something was earned, else in the banner under the praise line. Wording keeps lifetime and today apart: lifetime goals
  say what the player "hasn't tried yet" ("Lexicon is the only puzzle you haven't tried yet.", "2 puzzles you haven't tried
  yet: A and B."), today's say "today" ("Only Hard on Kings left for all done today!", "...left for a clean sweep today!").
  Covers streaks (current run, only while it is alive, so never a lapsed one), per-puzzle streaks, 100 solves, 30 days, one
  of each, full set and clean sweep. Null for earned ones, first solve and top tier. Positive wording only; never a loss.
- **Next puzzle today.** On today's own daily opened from Home (not an archive day), the frame's first button, filled as
  the main one and the pill's way on, is "Next puzzle" with the puzzle named under it (`nextToday` in `ui/play/NextSteps.kt`): the first of today's
  three not yet solved at this tier, else the first unsolved daily at this tier in the home grid's order; none once every
  daily at the tier is done. It opens that board as from Home, so back (and Done) from it goes Home, where the board just
  solved was opened from. An archive board does not offer it: its back is its own puzzle's archive, which another puzzle's
  board could not keep.
- **A set's share card.** Beside Share, a second chip when the solve finished a set: "Share level" on a level's fifth board
  (`levelShareCard`: "Daybook · Level 14 · 5/5", a mark per board, green unaided, red with hints, then the total time and
  hints, from the stored `level_solves` plus this solve; nothing new is stored), or "Share three" on the third of today's
  three at a tier. Not on a reopened board (the home row shares the three at any time).
- **The day's card never counts what is left.** `dayShareCard`'s nudge is "Every Standard board done. A clean sweep." on a
  full tier, else "Well played. Same time tomorrow?".
- **Tap to hide.** Tapping anywhere that is not a button (a full-screen scrim under the frame, so the open centre and the board, and the
  banner) hides the frame; a floating row at the same spot takes its place: "Show results", the first non-Done
  next step, and Done. The hidden state (`resultsHidden`) is `rememberSaveable`, never `PuzzleState`; the board is not
  resized or moved either way, and the confetti is not interactive.
- Render it: `render.py --finish N [--togo]` (docs/WEB_BUILD.md); with `-p ID`, `--query taught=TECHNIQUE` shows a hint
  solve's technique line and `--query needed` a no-hint one (of the seed-1 board).

## Techniques (`ui/stats/TechniquesScreen.kt`, `TechniquesMet` in `data/ProgressStore.kt`)

- A record, not practice: per puzzle that teaches, its praised techniques (`praisedTechniquesOf`, in `TechniqueNames.kt`'s
  order), with a tick on each a hint has explained. The rest are plain, never greyed, locked or counted "of" a total; the
  owner declined a per-technique practice mode, so nothing here starts a drill. Pointers and Snap (no teacher) and Target
  (its steps are not skills) have no list.
- Opened from a row on Statistics under Achievements ("N shown" once any is), back returns to Statistics; route `techniques`.
- Stored as its own key set, `techniques_met` (`"puzzleId|technique"`), written when a hint opens a deduction (mistakes
  left out), so a hint on an unfinished board counts too. `SavedGame.taught` is cleared with the save on a solve, so the
  page starts from nothing on the build that added it and says so ("Techniques you've been shown in hints"). No serialised
  class changed; the web backup carries the key because it copies every entry of the store, and an older backup without
  it imports as nothing met.

## Calendar (`ui/stats/StreakCalendar.kt`)

A month grid card on the stats screen, under the tiles: filled circle = day played, ring = a missed day the
streak absorbed (`forgivenDays` in `core/Streak.kt`, from the same walk as `streakOf`), outline = today, dim =
future. Monday-first; previous/next stop at the first month with a play and the current one. Decision: the miss
that ended a run is not ringed, but earlier misses of a run that later died stay ringed, because they were
forgiven when they happened. `StreakCalendar(played, today)` takes the played-day set, so other screens can reuse it.

## Daily variety (`core/PuzzleNotes.kt`, `core/TodaysThree.kt`)

- **Note.** A short hand-checked technique tip per puzzle id, picked by `puzzleNote(id, epochDay)` (pure: the day plus the id
  walks the list). It sits last in `finishPraise`, in place of the bare "No hints needed.", so it only shows on a no-hint
  solve that earned no achievement, record, percentile or board-needed line; a solve with hints keeps its hint line. At most 64 characters.
  Only say what the rules make true: no history, no attribution. `PuzzleNotesTest` fails for a registered puzzle without notes.
- **Today's three** (`core/TodaysThree.kt`, `ui/home/TodaysThreeRow.kt`). One quick puzzle, one mid-weight, one word or
  number puzzle, from the date alone (each slot steps one place a day through its own list: quick 9, words 5, mid-weight
  everything else, 15; the mid-weight slot steps once more each 45 days, so no set repeats within a year whatever that
  list's length), so it is the same for everyone and a shared card means the same three. It replaced today's pick
  (`PuzzleRegistry.featured`, stepped past the player's five most played): a per-player pick could not be the shared set,
  and two daily highlights would compete, so the pick's gold ring and sparkles moved onto the three. Home shows it as one
  row over the grid: "Today's three" and, once one is solved today at the grid's tier, a Share button; then a chip per
  puzzle (motif and name), tinted and ticked when solved today at the grid's tier, the first unsolved one in the sparkle
  frame (still under reduced motion; nothing animates once all three are done). A tap opens that daily at the grid's tier,
  in Daily or Random mode. Always laid out at the same height (the Share button sits in a caption line of fixed height), and
  laid out unseen in Levels, so it never moves the grid. "Try me!" and the grid's pick square are gone; nothing is stored.
  Its share card (`threeShareCard`): "Daybook · 25 Oct · Today's three · Standard", a mark per puzzle and "2/3", the solved
  boards' time and hints, the streak. An achievement, "Today's three" (all three on one tier, on the day), reads the
  registry's lists, so changing a list (or adding a puzzle) can make an old day's read unearned, as with the other
  registry-wide ones.
- **In progress.** A tile whose board at the grid's tier and mode (today's daily; in Random, the tier's current random board)
  has a saved game that is unsolved and has a move in it says "In progress", tilted across the thumbnail with a halo, in the
  scheme's moss on its surface (`InProgressLabel`). That is exactly the game `PlayScreen` keeps: a restart, or only a selection,
  clears the save. `ProgressStore.inProgress` reads each save
  only as far as its key and decodes in full just the boards the tiles would open; it follows the store, so a game saved on the
  way out of the play screen shows at once.

## Puzzle order (`PuzzleOrder` in `data/ProgressStore.kt`, `PuzzleGrid` in `ui/home/HomeScreen.kt`)

The grid scrolls (more than twenty puzzles), so the order decides what is on the first screen. Nothing is hidden; the owner
would rather let people rearrange than hide.

- **Gentlest first, then most played** (the default): solves per puzzle, dailies of the last 60 days plus every practice game
  (they carry no day); ties and never-played puzzles keep `PuzzleOrder.GENTLEST`, the owner's one table of how quickly a
  newcomer gets going on Standard (see it, do it: Pointers, Pipes, Snap; then Sudoku, Lexicon, Sand; familiar: Word Wheel,
  Target, Nonogram; quick to learn: Kings, Mambo, Mosaic, Sets, Inequality, Tents, Regions, Tally; takes a bit: Shikaku,
  Atoms, LITS, Mate, Cage Sudoku, Cipher, Dominoes, Codeword; and Tower last, as much the same game as Lexicon). So a new player's grid is that table, and Sudoku and Lexicon open the
  second row of a three-column phone (`HomeOrderTest` pins it); a puzzle missing from it goes last. The solves are read once
  per app start and day, not live, so coming home from a solve never moves a tile under the player's finger.
- **My own order**: press and hold a tile on the home grid, then drag it (the owner's call, replacing #231's Arrange screen
  of up and down buttons). The tile lifts (a 6% scale and a shadow, a haptic tick) and follows the finger, the others slide
  out of its way in 150 ms (at once under reduced motion), the list scrolls while the finger is within 64dp of the pinned
  tier rows or the screen's foot, and the lifted tile is kept out from under the pinned rows, so nothing lands where it
  cannot be seen. The drop ticks again and is one write: the first one stores the whole current order as the player's own
  (`puzzle_order` in the launch file, ids joined by commas; empty means most played). Only a long press starts it, so a tap
  still opens a puzzle and a swipe still scrolls; a press held still and let go does nothing. The drag is `remember` state
  until it drops. A screen reader gets "Move earlier" and "Move later" on every tile instead. On load, unknown ids drop out
  and puzzles added later join at the end (`PuzzleOrder.arranged`). Until an order is stored, and once three different
  puzzles have been solved (the welcome says it first), a line under the grid says "Press and hold a puzzle to move it.";
  Settings' "Puzzle order" row says the same, or, once there is one, "Your own
  order" with "Reset to most played", which clears it. Pinned by `HomeOrderTest`; an "arrange|..." route saved by the old
  screen opens Home (`RouteTest`).

## Welcome (`ui/home/Welcome.kt`)

One calm screen shown in place of the grid, once, to a new player: one line on what Daybook is (new boards every day, free,
no ads, no account, offline), then four pointers with small icons: today's three and the one-minute walkthrough under "How to play";
Daily / Levels / Random and the three tiers; the archive, stats and achievements buttons; press and hold to move a puzzle.
One button, Start. No question, no pages, nothing drawn over the grid (the owner's call). `LaunchPreferences.needsWelcome`:
only when the launch file has no `welcomed` key and `ProgressStore.hasPlayed()` is false (the timer default's rule); a
player who has played is marked welcomed on the spot, so an existing player never sees it. Start writes `welcomed`. Home
draws nothing until the answer is known, so neither the grid nor the welcome flashes. `render.py --home` on clean storage
shows it; `tools/screenshots/capture.sh` taps Start.

## Continue (`ContinueRow` in `ui/home/HomeScreen.kt`)

When a board the grid would open (the tier and mode shown; in Levels, the current level's) has a game in progress, a slim row
under the pinned rows says "Continue Kings · Hard" beside the puzzle's motif and opens it: the most recently saved
(`ProgressStore.inProgress` lists newest first). Its room is taken only while it shows. It follows the store until the first
touch on that visit (the board just left is saved as Play fades out, so it can land a moment after Home appears), then holds
until Home is entered again or the tier or mode changes, so it never appears or goes under a finger.

## This week (`ui/stats/ThisWeek.kt`)

The top card of the stats screen: a circle for today and each of the six days before (filled and numbered with the boards solved
that day, today outlined), then "N days played, M daily boards solved.", or "A fresh week: today's boards are ready." when the
week is empty; never a count of what is missing. Under each puzzle's tiers, once its most solved tier (the easier on a tie) has a
solve in 3 of the last 8 weeks, "Your recent median" with that tier and time (the latest
week with a solve), then a line through each week's median (gaps bridged). No axis, no "faster" or "slower": the line is the player's own times and says nothing about them.
Decision: **only dated completions count.** A `Completion` holds the day of the board it solved and no time of play, so practice
and level solves (`day == null`) cannot be put in a week; an archive board counts on its own day. Weeks end today, so none splits
at a month or year. Pure and tested: `ThisWeek.days`, `summary`, `weeklyMedians`, `trend` (`ThisWeekTest`). Static: nothing animates.

## Kept share cards (`KeptCard` in `data/ProgressStore.kt`, `ui/stats/StatsScreen.kt`)

Every solve keeps its share card (`share_cards`, one JSON `KeptCard` per entry), so Statistics can share it again: each tier row
has **Best** and **Last**, always laid out, greyed when nothing is kept. Last is the last *random* board solved at that puzzle and
tier, always replaced. Best is any board (daily, level or random, as best times count them all), ranked in the owner's order:
**fewest hints, then fastest, then fewest undos (as the share card counts them), then most bonus words (Word Wheel; 0 elsewhere)**.
Only a strict win replaces it; a full tie keeps the old card. Bests start from the build that added them, so the first card
kept is the best until beaten, and Best can be slower than the best time beside it when the faster solve used a hint.

## Reduced motion and haptics (`ui/Motion.kt`, the platform seam)

- **Reduced motion** follows the system: Android's "Remove animations" (animator duration scale 0), the web's
  `prefers-reduced-motion: reduce`. Both are watched, so a change applies at once. `rememberReducedMotion` is read once
  in `DaybookApp` and handed down as `LocalReducedMotion`. Under it: no confetti; Today's three's sparkles drawn still (no
  infinite transition runs); the hint glow steady at full strength (`rememberHintPulse`, which every board uses); Pointers'
  arrow simply goes and a bump flashes red without the shake; Sand has no pour stream and no refusal shake (the refused tube's red tint, shown always, is held 400 ms
  instead of fading), and a lifted tube rises at once; a Pipes tile shows its new turn; the hint popover jumps rather than slides. Fades (the finished frame's
  fade-in) and the loading spinner stay: they are not motion across the screen. A new decorative animation should read
  `LocalReducedMotion` too. Also off under it: the move feel below, and the 200 ms fade between screens (`DaybookApp`'s
  `Crossfade`; screens cut).
- **Move feel** (`ui/MoveFeel.kt`, `rememberMoveFeel`): the squares a move changed are drawn 8% large and ease back over
  120 ms; a unit the move finished by the rules (a Sudoku row, column, box or cage; an Inequality line; a Regions region; a
  Kings row, column or region; a Tents or Mambo line; a Nonogram or Tally line, beside their green clue; a LITS region with its
  piece; a Dominoes region keeping its rule; a full Sand tube; a found Word Wheel word; a Codeword word spelled; an Atoms atom with all its bonds)
  has a soft band of the puzzle's accent run along it once (400 ms, at most 35%). Only a move fires it (`LocalMoveCount`,
  raised by `PlayScreen.push`): a board loaded, restarted, undone into a finished unit or shown in the walkthrough stays
  quiet. An Undo (`LocalUndoCount`, raised by `PlayScreen.undo` when it takes something back) dips the squares it changed to
  92% and eases them back over the same 120 ms: the reverse of the settle (what was taken back is already gone, so it is the
  square that dips, not the old mark that shrinks). The move that solves (`LocalSolveCount`) runs the solve wave above. A
  board's squares fade in, staggered in reading order, over 250 ms when it first shows on the play screen
  (`LocalBoardArrives`, from a `rememberSaveable` flag, so not on a rotation, an undo, a restart or coming back from the
  walkthrough). Draw phase only; nothing moves or resizes. Boards left out: `docs/TODO.md`.
- **Home acknowledges the solve.** Back on Home after a solve of today's daily, the tier dot of the tier just solved pops
  in (0 to 220% to 100%, 250 ms) and the tile's tint fills with it, once, starting when the 200 ms fade into Home has finished.
  Home tiles have no corner tick: the dots say which tiers are done. A level tile keeps its tick, which pops (to 115%) the
  same way. `DaybookApp` hands the puzzle and tier to Home as `LocalJustSolved` (never stored; never for a random or
  archive solve, which marks no tile) and clears it when Home goes away. Random mode shows the same daily marks as Daily.
  Under reduced motion the dot, tick and tint are simply there.
- **Haptics** (`rememberHaptics`, `LocalHaptics`): `tick` when a push changes the board (the same test as the play log's
  move: a selection or a typed letter is silent), `bump` for a Pointers bump or a wrong Sets pick, `success` on a solve
  beside the chime (the solving move does not tick as well). Android uses `performHapticFeedback` (CLOCK_TICK; REJECT and
  CONFIRM from Android 11, LONG_PRESS and VIRTUAL_KEY before), which honours the system's touch-feedback setting; the web
  uses `navigator.vibrate` (8 ms, 50 ms, a 20-60-40 pattern), which iOS lacks. The walkthrough does not buzz. The
  Settings switch "Vibration" (`LaunchPreferences.vibrate`, key `vibrate`, default on) turns it all off.

## Claim to keep honest

Puzzles making you better at other things is weakly supported. Claim the habit and the satisfaction, not "makes you smarter".
