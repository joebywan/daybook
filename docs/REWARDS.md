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

## Claim to keep honest

Puzzles making you better at other things is weakly supported. Claim the habit and the satisfaction, not "makes you smarter".
