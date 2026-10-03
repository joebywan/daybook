# tools/chess

`build.py` regenerates `app/src/main/java/com/joebywan/daybook/puzzles/ChessPositions.kt` from the Lichess puzzle
database (CC0). Nothing from the dump or the venv is committed.

```bash
python3 -m venv /tmp/chess-venv && /tmp/chess-venv/bin/pip install chess zstandard
curl -O https://database.lichess.org/lichess_db_puzzle.csv.zst
/tmp/chess-venv/bin/python tools/chess/build.py lichess_db_puzzle.csv.zst 2026-10-02   # last arg: the dump's date
```

Takes about 20 minutes on 32 cores (mate-in-4 proofs dominate). Output is deterministic: same dump, same file, byte for
byte. A different dump moves every later daily board, so `ChessPositions.kt` changes on purpose only. Tuning knobs
(`POOL`, `CAP`, `TARGET`) are at the top of the script; changing them has the same effect.
