// The stats receiver (docs/stats-reporting.md, schema 1).
//
// POST /report with a report as JSON. A valid one becomes one row; anything else is dropped. The answer is always
// 204 either way: the report page never waits on it, and a sender learns nothing from probing. The IP address is
// never stored, and request logs are off (wrangler.jsonc).

const ORIGIN = "https://knowhowit.com.au";
const MAX_BYTES = 256 * 1024; // sixty days of a heavy player's dailies is ~50 KB
const TIERS = new Set(["STANDARD", "HARD", "EXPERT"]);
const RESULTS = new Set(["finished", "abandoned"]);
const PLATFORMS = new Set(["android", "web"]);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const PUZZLE_ID = /^[a-z][a-z0-9_]{0,23}$/;
const FIRST_DAY = "2026-01-01";

const CORS = {
  "Access-Control-Allow-Origin": ORIGIN,
  "Access-Control-Allow-Methods": "POST",
  "Access-Control-Allow-Headers": "Content-Type",
  "Access-Control-Max-Age": "86400",
};

export default {
  async fetch(request, env) {
    if (new URL(request.url).pathname !== "/report") return new Response(null, { status: 404 });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (request.method !== "POST") return new Response(null, { status: 405, headers: CORS });

    const report = normalise(await readJson(request), today());
    if (report) {
      // A failed write (the free plan's daily limit, an outage) is dropped like a bad report: the next send carries it.
      await env.DB.prepare(
        "INSERT INTO reports (install, sent_on, received, payload) VALUES (?1, ?2, ?3, ?4) " +
          "ON CONFLICT (install, sent_on) DO UPDATE SET received = excluded.received, payload = excluded.payload",
      )
        .bind(report.install, report.sentOn, today(), JSON.stringify(report))
        .run()
        .catch((e) => console.error("write failed:", e.message)); // the reason only, never the report or sender
    }
    return new Response(null, { status: 204, headers: CORS });
  },
};

async function readJson(request) {
  if (Number(request.headers.get("Content-Length") ?? 0) > MAX_BYTES) return null;
  const text = await request.text();
  if (text.length > MAX_BYTES) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

const today = () => new Date().toISOString().slice(0, 10);

/** The report with only known fields, each checked; null if anything is wrong. Unknown fields are dropped, not kept. */
function normalise(r, now) {
  if (!r || typeof r !== "object" || r.schema !== 1) return null;
  const tomorrow = new Date(Date.parse(now) + 86400000).toISOString().slice(0, 10); // a sender a timezone ahead
  if (!(typeof r.install === "string" && UUID.test(r.install))) return null;
  if (!isDay(r.sentOn) || r.sentOn > tomorrow) return null;
  if (!(r.firstPlayed === null || (isDay(r.firstPlayed) && r.firstPlayed <= r.sentOn))) return null;
  if (!PLATFORMS.has(r.platform) || !(typeof r.app === "string" && r.app.length <= 32)) return null;
  if (!count(r.sends) || !Array.isArray(r.puzzles) || !Array.isArray(r.days)) return null;
  if (r.puzzles.length > 400 || r.days.length > 8000) return null;

  const puzzles = [];
  for (const p of r.puzzles) {
    if (!p || !PUZZLE_ID.test(p.id) || !TIERS.has(p.tier) || typeof p.walkthroughFinished !== "boolean") return null;
    const fields = ["opened", "finished", "abandoned", "dailyFinished", "practiceFinished", "medianSeconds", "hintsUsed", "hintFallbacks"];
    if (!fields.every((f) => count(p[f]))) return null;
    puzzles.push({ id: p.id, tier: p.tier, ...Object.fromEntries(fields.map((f) => [f, p[f]])), walkthroughFinished: p.walkthroughFinished });
  }
  const days = [];
  for (const d of r.days) {
    if (!d || !isDay(d.d) || d.d > r.sentOn || !PUZZLE_ID.test(d.p) || !TIERS.has(d.t) || !RESULTS.has(d.r)) return null;
    if (!count(d.sec) || !count(d.h)) return null;
    days.push({ d: d.d, p: d.p, t: d.t, r: d.r, sec: d.sec, h: d.h });
  }
  return {
    schema: 1, install: r.install, app: r.app, platform: r.platform, sentOn: r.sentOn,
    firstPlayed: r.firstPlayed, sends: r.sends, puzzles, days,
  };
}

function isDay(s) {
  if (typeof s !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(s) || s < FIRST_DAY) return false;
  return new Date(s + "T00:00:00Z").toISOString().slice(0, 10) === s; // rejects 2026-02-30
}

const count = (n) => Number.isInteger(n) && n >= 0 && n <= 10_000_000;
