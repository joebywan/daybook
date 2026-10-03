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
- **Not doing:** points/XP/levels, loss-framed notifications ("your streak is about to end"), leaderboards,
  random or login rewards.

## Streak implementation (`core/Streak.kt`)

`streakOf(playedDays, today): Streak(current, best, atRisk)` (+ `alive`, `lapsed`), derived from the daily
`Completion.day`s; nothing new is stored. A run starts on a played day and is judged each day over the last
seven days, never reaching back before the run's first day: alive while at most 2 of those days were missed
(which is 5 of 7 for an established run, and "at most 2 missed since the first play" for a new one). The third
miss ends the run and the next play starts a fresh one. `current` counts days played. Home shows "Welcome back"
in place of the badge when lapsed; best streak is on the stats screen (the home header has no room for it).

## Finished-frame praise (`ui/play/FinishPraise.kt`)

`finishPraise(history, solve, today, puzzleName): Praise(title, big, lines)` is pure and tested (`FinishPraiseTest`);
`PlayScreen` asks for it once, before `onSolved` records the solve, so `history` never contains it. Never stored.
- **Title** replaces "Congratulations!" only for the first daily solve of *today's* date (archive days and practice
  get none): "N day streak" (bigger and bold at 7/30/100), "Day one", or "Welcome back" after a lapse. When yesterday
  was missed and the run goes on, the line is "You're back, and your streak is still going."
- **One line** beneath, in priority order: fastest at this tier (strictly faster than a non-empty tier history; a tie is
  not a record), perseverance (>= 10 min, or >= 2x the tier's median with 5+ solves and >= 4 min), "Faster than N%
  of your own solves" (5+ solves, N rounded down, shown from 75), hints (praise, never a count: the technique ids in
  `Deduction` are test identifiers, not prose, so the wording is generic), "No hints needed."
- No new sound: the existing solve chime plays, muted by the existing setting. A milestone fanfare is not done.

## Achievements (`core/Achievements.kt`, `ui/stats/AchievementsScreen.kt`)

17 of them, all derived from the completion history; nothing is stored. Each is a predicate over the history; a solve
*earns* one when it is false over the history before and true with the solve added (`newlyEarned`). An old player whose
history already satisfies one sees it earned in the list and is never told, so there is no flood on update.
- Solves: first solve; 100 solves. Days: play on 30 different days.
- Streak (best run ever, so it never goes away): 3, 7, 14, 30, 60, 100, 365 days. Per puzzle (that puzzle's own played
  days through `streakOf`): 7 and 30 days.
- Top tier (Expert): first solve; a solve with no hints.
- One of each puzzle; all three difficulties of one puzzle's daily on the same day; every puzzle's daily on one day.
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
  title 24sp so four fit at 360dp.
- `streakOf` is only monotone when the new play is the newest day; an archive play can in rare cases split a run, so
  a streak achievement could in theory read unearned again. The diff never announces an already-earned one.

## The finished frame (`ui/play/PlayScreen.kt`, `ui/play/FinishExtras.kt`)

Owner's design: header ("Congratulations!" or the streak title), time, one personal line, an OPTIONAL middle block,
the next-step buttons at the bottom.
- **Confetti** (`Confetti`): 70 pieces on a Canvas over the whole screen, ~2 s, once per finish (`confettiPlayed` is
  `rememberSaveable`, so a rotation does not replay it), takes no input. There is no reduced-motion setting in the app,
  so none is honoured; the Sound switch governs the chime only. No emoji: the web build has no emoji font.
- **Middle block** (`ResultsBlock`), only when the solve earned achievements or there is a near goal. One achievement
  at a time: badge (`AchievementBadge`), title, description. With several: "1 of 3", dots and "Tap to see next
  achievement"; one tap on the block shows the next and it **wraps** after the last. The buttons are never behind it.
- **"Only X to go"** (`core/Achievements.kt`: `remaining(id, history, today, ...)`, `nearestToGo`): one line for the
  nearest achievement 1 or 2 steps away after this solve (fewest steps, then list order), e.g. "Only Hard on Kings
  left for all done today!" or "2 more days to your 30-day streak." Covers streaks (current run, only while it is alive,
  so never a lapsed one), per-puzzle streaks, 100 solves, 30 days, one of each, full set and clean sweep (today's
  dailies). Null for earned ones, first solve and top tier. Positive wording only; never a loss. It can show with
  nothing earned.
- **Tap to hide.** Tapping anywhere that is not a button (a full-screen scrim under the frame, and the frame's own
  background) hides the frame; a floating row at the same spot takes its place: "Show results", the first non-Done
  next step, and Done. The hidden state (`resultsHidden`) is `rememberSaveable`, never `PuzzleState`; the board is not
  resized or moved either way, and the confetti is not interactive.
- Render it: `render.py --finish N [--togo]` (docs/WEB_BUILD.md).

## Calendar (`ui/stats/StreakCalendar.kt`)

A month grid card on the stats screen, under the tiles: filled circle = day played, ring = a missed day the
streak absorbed (`forgivenDays` in `core/Streak.kt`, from the same walk as `streakOf`), outline = today, dim =
future. Monday-first; previous/next stop at the first month with a play and the current one. Decision: the miss
that ended a run is not ringed, but earlier misses of a run that later died stay ringed, because they were
forgiven when they happened. `StreakCalendar(played, today)` takes the played-day set, so other screens can reuse it.

## Daily variety (`core/PuzzleNotes.kt`, `PuzzleRegistry.featured`)

- **Note.** A short hand-checked technique tip per puzzle id, picked by `puzzleNote(id, epochDay)` (pure: the day plus the id
  walks the list). It sits last in `finishPraise`, in place of the bare "No hints needed.", so it only shows on a no-hint
  solve that earned no achievement, record or percentile line; a solve with hints keeps its hint line. At most 64 characters.
  Only say what the rules make true: no history, no attribution. `PuzzleNotesTest` fails for a registered puzzle without notes.
- **Today's pick.** `PuzzleRegistry.featured(epochDay)` rotates through the registry, so it scales as puzzles are added. Its
  home tile gets a thin accent ring and a small star (drawn on a canvas; the core icon set has no star), with the content
  description "Today's pick". No text label: a label would change the tile's height. The grid does not reorder.
  Nothing is stored.

## Claim to keep honest

Puzzles making you better at other things is weakly supported. Claim the habit and the satisfaction, not "makes you smarter".
