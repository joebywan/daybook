#!/usr/bin/env python3
"""Rebuild app/src/main/java/com/joebywan/daybook/puzzles/ChessPositions.kt (the "Mate" puzzle's positions).

Input is the Lichess puzzle database (CC0, https://database.lichess.org/#puzzles):

    python3 -m venv venv && venv/bin/pip install chess zstandard
    curl -O https://database.lichess.org/lichess_db_puzzle.csv.zst
    venv/bin/python tools/chess/build.py lichess_db_puzzle.csv.zst [YYYY-MM-DD dump date]

Themes mateIn2/3/4 -> Standard/Hard/Expert, each within a rating band (BANDS: Lichess Rating, and RatingDeviation at most
MAX_RD so the rating is settled), so the tiers ascend in difficulty by rating as well as by length. The CSV FEN is the position before the opponent's move and Moves[0] is
that move: we apply it and store the position the player sees plus that move ("last").

Every candidate is then PROVED with python-chess by exhaustive search (the Lichess solution is only a move-ordering
hint, never the proof): the side to move has no forced mate in N-1 and exactly one first move forces mate in N.
Failures are dropped. Candidates are taken in sha1(FEN) order, up to POOL per tier; on Expert every candidate whose
Lichess key is quiet (no check, no capture) is verified as well, and up to QUIET survivors with a proved quiet key are
chosen first. Survivors are chosen with a cap per
(key piece type, last-move square) for variety, deduped by FEN, and written sorted by FEN: the file is a pure function
of the input (the dump's date goes in the header). Verification runs in parallel; results do not depend on that.
"""
import csv, hashlib, io, multiprocessing as mp, os, sys, time
import chess, zstandard

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "../../app/src/main/java/com/joebywan/daybook/puzzles/ChessPositions.kt")
TIERS = (("STANDARD", "mateIn2", 2), ("HARD", "mateIn3", 3), ("EXPERT", "mateIn4", 4))
POOL = {2: 6000, 3: 6000, 4: 3500}   # candidates verified per tier (after sha1 ordering)
BANDS = {2: (700, 1200), 3: (1300, 1750), 4: (1800, 99999)}   # Lichess Rating, lo <= r < hi
MAX_RD = 90                           # Lichess RatingDeviation: drop puzzles whose rating has not settled
QUIET = {4: 400}                      # max survivors with a quiet (non-checking, non-capturing) key, taken first
CAP = 12                              # max survivors per (key piece, last square)
TARGET = 1500                         # stop selecting at this many per tier
CHUNK = 60_000                        # characters per string constant (JVM limit is 64 KB)


def win(b, n, cache):
    """Attacker to move: is there a forced mate in at most n? Exhaustive (a mate always starts with check at n=1)."""
    k = (b._transposition_key(), n)
    r = cache.get(k)
    if r is not None:
        return r
    r = False
    for m in sorted(b.legal_moves, key=lambda m: not b.gives_check(m)):
        if n == 1 and not b.gives_check(m):
            continue
        if _after(b, m, n, cache):
            r = True
            break
    cache[k] = r
    return r


def _after(b, m, n, cache):
    """Play attacker move m with n moves left: does every defence still lose?"""
    b.push(m)
    if b.is_checkmate():
        ok = True
    elif n == 1 or b.is_stalemate() or b.is_insufficient_material():
        ok = False
    else:
        ok = True
        for mv in b.legal_moves:
            b.push(mv)
            ok = win(b, n - 1, cache)
            b.pop()
            if not ok:
                break
    b.pop()
    return ok


def verify(job):
    """job = (fen_before, moves, n). Returns (fen_no_clocks, last_uci, key_piece_type, key_is_quiet) or None."""
    fen0, moves, n = job
    b = chess.Board(fen0)
    last = chess.Move.from_uci(moves[0])
    if last not in b.legal_moves:
        return None
    b.push(last)
    if b.is_game_over():
        return None
    cache = {}
    if n > 1 and win(b, n - 1, cache):
        return None
    keys, quiet = [], False
    hint = chess.Move.from_uci(moves[1]) if len(moves) > 1 else None
    for m in sorted(b.legal_moves, key=lambda m: m != hint):
        pt = b.piece_type_at(m.from_square)
        if _after(b, m, n, cache):
            keys.append(pt)
            quiet = not b.gives_check(m) and not b.is_capture(m)
            if len(keys) > 1:
                return None
    if len(keys) != 1:
        return None
    fen = " ".join(b.fen().split(" ")[:4])
    return fen, last.uci(), keys[0], quiet


def lichess_key_quiet(fen0, mv):
    """Cheap pre-filter: is the Lichess line's first player move quiet? (The proof decides; this only widens the pool.)"""
    b = chess.Board(fen0)
    b.push_uci(mv[0])
    k = chess.Move.from_uci(mv[1])
    return not b.gives_check(k) and not b.is_capture(k)


def main():
    path = sys.argv[1]
    date = sys.argv[2] if len(sys.argv) > 2 else "unknown"
    pools = {n: [] for _, _, n in TIERS}
    with open(path, "rb") as f:
        text = io.TextIOWrapper(zstandard.ZstdDecompressor().stream_reader(f), encoding="utf-8", newline="")
        for row in csv.DictReader(text):
            th = row["Themes"].split()
            for _, theme, n in TIERS:
                lo, hi = BANDS[n]
                if theme in th and lo <= int(row["Rating"]) < hi and int(row["RatingDeviation"]) <= MAX_RD:
                    mv = row["Moves"].split()
                    h = hashlib.sha1((row["FEN"] + " " + mv[0]).encode()).hexdigest()
                    pools[n].append((h, row["FEN"], mv))
    out = {}
    with mp.Pool() as pool:
        for name, _, n in TIERS:
            t0 = time.time()
            ordered = sorted(pools[n], key=lambda c: c[0])
            cands = ordered[:POOL[n]]
            if n in QUIET:
                cands += [c for c in ordered[POOL[n]:] if lichess_key_quiet(c[1], c[2])]
            res = pool.map(verify, [(f, m, n) for _, f, m in cands], chunksize=8)
            good = [r for r in res if r]
            seen, picked, buckets = set(), [], {}
            quiet_max = QUIET.get(n, 0)
            nq = 0
            for r in sorted(good, key=lambda r: (not (quiet_max and r[3]), hashlib.sha1(r[0].encode()).hexdigest())):
                if r[0] in seen or len(picked) >= TARGET:
                    continue
                if r[3] and quiet_max and nq >= quiet_max:
                    continue
                bk = (r[2], r[1][2:4])
                if buckets.get(bk, 0) >= CAP:
                    continue
                seen.add(r[0]); buckets[bk] = buckets.get(bk, 0) + 1; picked.append(r); nq += r[3]
            out[name] = sorted(f"{r[0]} {r[1]}" for r in picked)
            print(name, "band=%s csv=%d tried=%d verified=%d (quiet %d) kept=%d (quiet %d) (%.0fs)" % (
                BANDS[n], len(pools[n]), len(cands), len(good), sum(r[3] for r in good), len(picked),
                sum(r[3] for r in picked), time.time() - t0), file=sys.stderr)
    with open(OUT, "w") as f:
        f.write("package com.joebywan.daybook.puzzles\n\n")
        f.write("// GENERATED by tools/chess/build.py from the Lichess puzzle database (CC0; docs/chess-positions/NOTICE.md),\n")
        f.write(f"// dump dated {date}. Do not edit by hand: every position was proved by exhaustive search (shortest forced mate is\n")
        f.write("// exactly N, one key move). Each tier is sorted by FEN and the daily board is picked by index, so adding or removing\n")
        f.write("// a position moves every later daily board. Entry: \"<fen without clocks> <last move uci>\".\n")
        f.write("// Lichess rating bands, mate in 2 / 3 / 4: " + " / ".join(
            f"{lo}-{hi - 1}" if hi < 99999 else f"{lo}+" for lo, hi in (BANDS[n] for _, _, n in TIERS)) + ".\n")
        f.write("// Each list is chunks of newline-separated entries, because a JVM string constant is capped at 64 KB.\n\n")
        f.write("internal object ChessPositions {\n")
        for name, _, _ in TIERS:
            chunks, cur, size = [], [], 0
            for e in out[name]:
                if size + len(e) + 2 > CHUNK:
                    chunks.append(cur); cur, size = [], 0
                cur.append(e); size += len(e) + 2
            chunks.append(cur)
            f.write(f"    val {name}: List<String> = listOf(\n")
            for c in chunks:
                f.write('        "' + "\\n".join(c) + '",\n')
            f.write("    ).flatMap { it.split('\\n') }\n\n")
        f.write("}\n")


if __name__ == "__main__":
    main()
