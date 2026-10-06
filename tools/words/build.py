#!/usr/bin/env python3
"""Rebuild the word lists in app/src/main/java/com/joebywan/daybook/puzzles/LexiconWords.kt.

Input is the SCOWL word lists as packaged by the npm module `wordlist-english` 1.2.1 (MIT-style
SCOWL licence, see docs/word-lists/LICENSE-SCOWL.txt):

    npm pack wordlist-english@1.2.1 && tar xzf wordlist-english-1.2.1.tgz
    mkdir wn && npm pack wordnet-db@3.1.14 && tar xzf wordnet-db-3.1.14.tgz -C wn      # WordNet 3.1
    curl -LO https://raw.githubusercontent.com/dolph/dictionary/master/enable1.txt     # ENABLE, public domain
    python3 tools/words/build.py package wn/package/dict enable1.txt

Two lists per word length (4 and 5 letters):

  guesses  every plain a-z word at SCOWL level 70 or lower in the dialect-neutral list or in the
           American, Australian, British or Canadian one, so colour and color are both accepted;
           plus every word that is in both ENABLE and WordNet (two independent sources agreeing
           keeps out the proper names, numerals and misspellings that WordNet or ENABLE alone carry),
           and ENABLE's plural of each such word (WordNet lists base forms only);
           plus tools/words/guess-extras.txt, words in none of them (larp). Guesses only, never answers.
  answers  common words only: SCOWL levels 10, 20 and 35 of the dialect-neutral list (so no
           word that has an American/Australian spelling variant), minus inflections of another
           word on the list (plurals, -ed, -er/-est), minus tools/words/exclude.txt (offensive,
           obscure, or not a word a person would guess), plus tools/words/include.txt.

tools/words/clues.txt holds one crossword-style clue ("word: clue") for every answer, written into
LexiconClues.kt; the build fails if an answer has no clue or a clue has no answer. A clue must not
contain its answer, and no two answers share a clue (LexiconCluesTest checks both).

Every list is written sorted and unique. The answer is picked by index, so the order is part of the
contract: re-running this must not reorder anything unless a word is added or removed on purpose.
"""
import json, os, re, sys

LENGTHS = (4, 5)
HERE = os.path.dirname(os.path.abspath(__file__))
PUZZLES = os.path.join(HERE, "../../app/src/main/java/com/joebywan/daybook/puzzles")
OUT = os.path.join(PUZZLES, "LexiconWords.kt")
CLUES_OUT = os.path.join(PUZZLES, "LexiconClues.kt")
PLAIN = re.compile(r"[a-z]+")


def load(pkg, dialect, levels):
    words = set()
    for level in levels:
        with open(os.path.join(pkg, f"{dialect}-words-{level}.json")) as f:
            words |= {w for w in json.load(f) if PLAIN.fullmatch(w)}
    return words


def read_list(name):
    path = os.path.join(HERE, name)
    if not os.path.exists(path):
        return set()
    with open(path) as f:
        return {line.split("#")[0].strip() for line in f if line.split("#")[0].strip()}


def inflection_of(w, everything):
    """Whether w is a plural, past tense or comparative of a shorter word that is also a word."""
    stems = []
    if w.endswith("ies"):
        stems.append(w[:-3] + "y")
    if w.endswith("es"):
        stems += [w[:-2], w[:-1]]
    if w.endswith("s") and not w.endswith("ss"):
        stems.append(w[:-1])
    if w.endswith("ied"):
        stems.append(w[:-3] + "y")
    if w.endswith("ed"):
        stems += [w[:-2], w[:-1]]
        if len(w) > 4 and w[-3] == w[-4]:
            stems.append(w[:-3])
    if w.endswith("ing"):
        stems += [w[:-3], w[:-3] + "e"]
        if len(w) > 5 and w[-4] == w[-5]:
            stems.append(w[:-4])
    if w.endswith("ier") or w.endswith("iest"):
        stems.append(w[: w.rindex("i")] + "y")
    if w.endswith("er"):
        stems += [w[:-2], w[:-1]]
    if w.endswith("est"):
        stems += [w[:-3], w[:-2]]
    return any(s != w and len(s) >= 3 and s in everything for s in stems)


def plural_stems(w):
    stems = []
    if w.endswith("ies"):
        stems.append(w[:-3] + "y")
    if w.endswith("es"):
        stems.append(w[:-2])
    if w.endswith("s") and not w.endswith("ss"):
        stems.append(w[:-1])
    return stems


def wordnet(dict_dir):
    words = set()
    for kind in ("noun", "verb", "adj", "adv"):
        with open(os.path.join(dict_dir, f"index.{kind}")) as f:
            words |= {line.split()[0] for line in f if not line.startswith(" ")}
    return {w for w in words if PLAIN.fullmatch(w)}


def main(pkg, wordnet_dir, enable_path):
    with open(enable_path) as f:
        enable = {w.strip() for w in f}
    both = wordnet(wordnet_dir) & enable
    # WordNet lists base forms only: take ENABLE's plural of each, so "etui" brings "etuis".
    both |= {w for w in enable if any(s in both for s in plural_stems(w))}
    extras = read_list("guess-extras.txt")
    dialects = ("english", "american", "australian", "british", "canadian")
    all_levels = (10, 20, 35, 40, 50, 55, 60, 70)
    everything = set()
    for d in dialects:
        everything |= load(pkg, d, all_levels)
    common = load(pkg, "english", (10, 20, 35))
    exclude, include = read_list("exclude.txt"), read_list("include.txt")
    out = {}
    for n in LENGTHS:
        guesses = {w for w in everything | both | extras if len(w) == n}
        answers = {w for w in common if len(w) == n and not inflection_of(w, everything)}
        answers = (answers - exclude) | {w for w in include if len(w) == n}
        guesses |= answers
        out[n] = (sorted(guesses), sorted(answers))
    return out


def kotlin(out):
    lines = [
        "package com.joebywan.daybook.puzzles",
        "",
        "// GENERATED by tools/words/build.py from SCOWL (see docs/word-lists/LICENSE-SCOWL.txt). Do not edit by hand:",
        "// the answer is picked by index into these sorted lists, so reordering changes every daily board.",
        "// Each list is chunks of space-separated words, because a JVM string constant is capped at 64 KB.",
        "",
        "internal object LexiconWords {",
    ]
    for n in LENGTHS:
        for kind, words in zip(("GUESSES", "ANSWERS"), out[n]):
            lines.append(f"    val {kind}_{n}: List<String> = listOf(")
            chunk, size = [], 0
            for w in words:
                chunk.append(w)
                size += len(w) + 1
                if size > 8000:
                    lines.append(f'        "{" ".join(chunk)}",')
                    chunk, size = [], 0
            if chunk:
                lines.append(f'        "{" ".join(chunk)}",')
            lines.append("    ).joinToString(\" \").split(\" \")")
            lines.append("")
    lines[-1] = "}"
    return "\n".join(lines) + "\n"


def clues_kotlin(out):
    clues = dict(line.rstrip("\n").split(": ", 1) for line in open(os.path.join(HERE, "clues.txt")) if line.strip())
    answers = {w for n in LENGTHS for w in out[n][1]}
    if answers != set(clues):
        sys.exit(f"clues.txt does not match the answers: missing {sorted(answers - set(clues))[:20]}, "
                 f"extra {sorted(set(clues) - answers)[:20]}")
    lines = [
        "package com.joebywan.daybook.puzzles",
        "",
        "// GENERATED by tools/words/build.py from tools/words/clues.txt. Do not edit by hand.",
        "// One crossword-style clue per answer word, as chunks of lines `word: clue`, because a JVM string",
        "// constant is capped at 64 KB.",
        "",
        "internal object LexiconClueText {",
        "    val ALL: String = listOf(",
    ]
    chunk, size = [], 0
    def flush():
        esc = lambda s: s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
        body = "\\n".join(esc(x) for x in chunk)
        lines.append(f'        "{body}",')
    for w in sorted(clues):
        chunk.append(f"{w}: {clues[w]}")
        size += len(chunk[-1]) + 1
        if size > 8000:
            flush()
            chunk, size = [], 0
    if chunk:
        flush()
    lines.append('    ).joinToString("\\n")')
    lines.append("}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    result = main(sys.argv[1], sys.argv[2], sys.argv[3])
    for n in LENGTHS:
        print(n, "guesses", len(result[n][0]), "answers", len(result[n][1]), file=sys.stderr)
    if "--words" in sys.argv:
        for n in LENGTHS:
            print("\n".join(result[n][1]), file=open(f"/tmp/answers{n}.txt", "w"))
    else:
        with open(OUT, "w") as f:
            f.write(kotlin(result))
        with open(CLUES_OUT, "w") as f:
            f.write(clues_kotlin(result))
