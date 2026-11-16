# Stats reporting: design

Status: proposal. Nothing here is built yet.

## What it is for

To find out which puzzles get played and which don't, which daily boards people give up on, and
where the hints stop helping. All of this without adding the `INTERNET` permission, a tracking
SDK, or anything that would put a Tracking anti-feature on the f-droid.org listing.

Questions it should answer:

- Which puzzles and tiers get opened, finished and abandoned.
- **Which specific daily boards were abandoned by players who usually finish that puzzle.** That
  points to a difficulty spike in the generator, or a technique the walkthrough and hints don't
  teach well enough.
- Where hints reach the fallback, meaning the hint gives up and points at the answer.
- How the same player changes over time.

## Constraints

- The Android manifest has no `INTERNET` permission, and it keeps none.
- knowhowit.com.au is static GitHub Pages: no server and no logs.
- Sending is opt-in, one tap at a time. Nothing is sent in the background.
- A failed send never reaches the player. The page always says "Thanks!".
- The Cloudflare account stays on the free plan with no card on file, so it can't bill.

## How a report travels

```
Android: Stats → "Send stats"
   preview of the exact JSON
   → opens the browser at knowhowit.com.au/daybook/report#<payload>
Web build: Stats → "Send stats" → same page, same payload

report page (static, on Pages)
   shows the payload in plain words
   [Send] → POST to the receiver, without waiting for an answer → "Thanks!"

receiver (Cloudflare Worker)
   validates → one row in D1

nightly: wrangler d1 export → local SQLite → dashboard (Metabase / Grafana / Datasette)
```

- **Android never touches the network.** Opening a link needs no permission. The payload rides in
  the URL fragment (after `#`), which the browser never sends to the server, so nothing leaves the
  phone until the player taps Send on the page.
- **The payload is deflated and base64url-encoded**, using `java.util.zip` on Android and
  `CompressionStream` in the browser. Sixty days of results compress to a few KB, well inside what
  a URL can carry.
- **There's one consent flow for both builds.** The web build opens the same page with the same
  payload.

## The install ID

- It's a UUID made from 128 random bits: `SecureRandom` on Android, `crypto.getRandomValues` on the
  web, behind the platform seam. It's created the first time the player sends, not at install.
- It isn't derived from anything (device, account, IP), so there's nothing to reverse. All it does
  is link one player's reports to each other.
- **Reset ID** sits next to the Send button. Uninstalling removes the ID.
- Android Auto Backup carries it to a new phone on purpose, because that's the same player.
- With the ID, sending twice is never counted as two players, and change over time can be measured.

## Schema, version 1

Each send is a running total, so a report that's lost costs nothing: the next one includes it.

```json
{
  "schema": 1,
  "install": "0b6c5e2a-…",
  "app": "0.1.212",
  "platform": "android",
  "sentOn": "2026-11-17",
  "firstPlayed": "2026-09-02",
  "sends": 6,
  "puzzles": [
    {
      "id": "mosaic", "tier": "EXPERT",
      "opened": 41, "finished": 29, "abandoned": 9,
      "dailyFinished": 22, "practiceFinished": 7,
      "medianSeconds": 214,
      "hintsUsed": 17, "hintFallbacks": 3,
      "walkthroughFinished": true
    }
  ],
  "days": [
    { "d": "2026-11-15", "p": "mosaic", "t": "EXPERT", "r": "abandoned", "sec": 380, "h": 2 },
    { "d": "2026-11-15", "p": "sudoku", "t": "HARD",   "r": "finished",  "sec": 402, "h": 0 }
  ]
}
```

- `puzzles` holds one entry per puzzle and tier the player has touched, 33 at most. These are all-time totals.
- `days` covers every daily board the player opened in the last 60 days, with its result: `finished`, or
  `abandoned` if it was opened and never finished. Practice games aren't included. The date, puzzle
  and tier identify the exact board, because daily boards are `hash(date, puzzleId, difficulty)`:
  the dashboard can regenerate the board someone gave up on.
- Sixty days means a player who sends less often than every two months loses the oldest days in
  between. The totals in `puzzles` still cover them.
- Excluded on purpose: time of day, individual moves, device model and language. Add them in
  `schema: 2` if a question needs them.

### What the app has to start recording

What's stored today (`data/ProgressStore.kt`) is finished games (`puzzleId|tier|epochDay|seconds|hints`)
and at most 12 unfinished games, which are evicted and deleted when finished. That can't show what
was opened and left. New on-device records:

- Per puzzle and tier: `opened`, `abandoned`, `hintFallbacks` and `walkthroughFinished` counters.
- A daily-board log of `(day, puzzle, tier, result, seconds, hints)`, trimmed to 60 days.
- The install ID, `firstPlayed` and the `sends` count.

All of these go in the existing `KeyValueStore`, so the web build's export and import cover them
without extra work.

## Receiver

- It accepts a POST of at most 64 KB. It rejects anything that isn't `schema: 1`, a known puzzle ID
  or tier, or a well-formed UUID and dates.
- It writes one row: `(install, sentOn, received, payload JSON)`, with `PRIMARY KEY (install, sentOn)`.
  - Sending again the same day overwrites that day's row.
  - Earlier days' rows are kept. Each carries 60 days, so together they keep the whole per-day
    history even though any one report covers only two months.
- It doesn't store the IP address. The harm to a player would be slight, but storing it would make
  it personal information under Australia's Privacy Act and the GDPR. That brings disclosure and
  access-request obligations for data that's never used.
- It returns 204 whatever happens. The page doesn't wait for the answer anyway.
- A free per-IP rate-limit rule on the route stops one source from filling the daily quota.

## Free-plan limits

| Limit | Free | What uses it up | How to stretch it |
|---|---|---|---|
| Worker requests | 100k/day | each send | the page fires once, with no automatic retries |
| D1 rows written | 100k/day | 1 per send, plus 1 per index on a written column | primary key only, no other indexes |
| D1 rows read | 5M/day | queries | the dashboard reads the nightly local copy, never D1 directly |
| D1 size | 500 MB per database, 5 GB per account | roughly 5 KB per send | prune old sends once a newer one from the same install is past 60 days |

Going over a limit makes requests fail until 00:00 UTC. It never bills on the free plan.

## Dashboard

The nightly `wrangler d1 export` produces a local SQLite file. The latest row per install gives the totals.
`json_each(payload, '$.days')`, de-duplicated on `(install, d, p, t)` with the newest send kept,
gives per-board results. Starter queries:

- Abandon rate per puzzle and tier: abandoned ÷ opened.
- **Board spikes:** daily boards abandoned by players whose finish rate for that puzzle and tier
  is otherwise high. Regenerate the board from its date and investigate.
- Fallback rate per puzzle and tier, compared with the rates `CLAUDE.md` measures on synthetic walks.
- Each player's history: finish rate and median time over successive sends.

## Who sends, and who doesn't

Only players who choose to send are counted, so the data shows the keen ones. Reading the gaps:

- Where keen players quit is an upper bound on how well the puzzle holds anyone else.
- Everyone, counted without IDs, sets the denominator: Play Console (installs, uninstalls,
  retention), GitHub release download counts, and the request counts on our own F-Droid repo
  (below).
- Trends from repeat senders are the most trustworthy signal, because the bias cancels out
  within one player.
- An optional "Anything to add?" text box on the report page is the only way to learn *why*.

## Rewarding sending (proposal)

Badges for sending stats on 1, 3, 7, 14, 30 and 60 distinct days. The app counts taps on Send,
because it can't know whether a send arrived. It never knows, by design.

- This deepens the bias towards keen players, but every extra send gives more history per player.
- The app has no achievements system yet, so this is a new feature in its own right. Keep it
  quiet: no nagging, and no prompt to send outside the Stats screen.

## Download counts for our own F-Droid repo

GitHub Pages keeps no logs. The DNS for knowhowit.com.au is on afraid.org, pointing at Pages. The options:

1. **Move the domain's DNS to Cloudflare and proxy it.** URLs don't change, so existing F-Droid
   clients keep working. Cloudflare's free traffic analytics then counts requests per path:
   - APK downloads, which are installs and updates.
   - `entry.jar` / `index-v2.json` fetches. Each client with the repo added fetches these on
     every refresh, which gives a rough active-client count.
   - Not yet checked: how much per-path detail the free plan's analytics shows.
2. **A Worker on `/daybook/fdroid/repo/*`.** Same domain move, plus a few lines that count each
   request into Workers Analytics Engine (free, built for counters) and pass it through to Pages.
   Use this if option 1's detail isn't enough.

Either one changes how the whole site is served. Cloudflare in front of Pages is a common
set-up, but it's a change to the domain, not just to this app.

## f-droid.org download counts

f-droid.org shows no per-app counts. Its public metrics (fdroid.gitlab.io/metrics) publish only
the most-requested paths per mirror per week, and as of 2026-11-17 the newest file is the week of
2026-08-24, before Daybook was listed. The newest weeks list almost no paths. Daybook won't show up
there in practice. Treat any third-party figure built on that feed as missing.

## Changes to the privacy policy

`PRIVACY.md` keeps "the app has no `INTERNET` permission". It adds: what Send transmits (the schema
above, shown before sending), that the IP address isn't stored, how Reset ID works, and where the
data lives (Cloudflare D1).
