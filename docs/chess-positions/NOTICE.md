# Chess positions

The positions in `app/src/main/java/com/joebywan/daybook/puzzles/ChessPositions.kt` come from the
[Lichess puzzle database](https://database.lichess.org/#puzzles) (`lichess_db_puzzle.csv.zst`), released under
[CC0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain dedication). Dump used: the file served on
2026-10-02 (Last-Modified Fri, 02 Oct 2026 08:51:45 GMT, ETag "6abf70a1-125007eb",
SHA-256 76335bfa7d7c4a7f93c1366d81549e53951ebb79dd43d34904cab8f22d962f8d).

Only the starting positions are used. Each was re-verified by `tools/chess/build.py` (shortest forced mate exactly N,
one key move). The themes and ratings choose which puzzles are candidates (each tier a rating band); nothing else from the
database (ratings, themes, solutions) is shipped.
