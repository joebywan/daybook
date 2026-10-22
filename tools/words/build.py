#!/usr/bin/env python3
"""Rebuild the word lists in app/src/main/java/com/joebywan/daybook/puzzles/LexiconWords.kt, and Word Wheel's
WordWheelWords.kt and WordWheelClues.kt beside it (see wheel(): Lexicon's files come out byte-identical).

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


def main(pkg, wordnet_dir, enable_path, lengths=LENGTHS):
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
    for n in lengths:
        guesses = {w for w in everything | both | extras if len(w) == n}
        answers = {w for w in common if len(w) == n and not inflection_of(w, everything)}
        answers = (answers - exclude) | {w for w in include if len(w) == n}
        guesses |= answers
        out[n] = (sorted(guesses), sorted(answers))
    return out


def word_list(name, words):
    """`val NAME: List<String>`, as chunks of space-separated words (a JVM string constant is capped at 64 KB)."""
    lines = [f"    val {name}: List<String> = listOf("]
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
    return lines


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
            lines += word_list(f"{kind}_{n}", words)
    lines[-1] = "}"
    return "\n".join(lines) + "\n"


def read_clues(name):
    return dict(line.rstrip("\n").split(": ", 1) for line in open(os.path.join(HERE, name)) if line.strip())


def clue_text(obj, source, clues):
    """`internal object OBJ { val ALL: String }`: lines `word: clue`, in chunks under the 64 KB string cap."""
    lines = [
        "package com.joebywan.daybook.puzzles",
        "",
        f"// GENERATED by tools/words/build.py from tools/words/{source}. Do not edit by hand.",
        "// One crossword-style clue per answer word, as chunks of lines `word: clue`, because a JVM string",
        "// constant is capped at 64 KB.",
        "",
        f"internal object {obj} {{",
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


def clues_kotlin(out):
    clues = read_clues("clues.txt")
    answers = {w for n in LENGTHS for w in out[n][1]}
    if answers != set(clues):
        sys.exit(f"clues.txt does not match the answers: missing {sorted(answers - set(clues))[:20]}, "
                 f"extra {sorted(set(clues) - answers)[:20]}")
    return clue_text("LexiconClueText", "clues.txt", clues)


# ---- Word Wheel ---------------------------------------------------------------------------------

WHEEL_LENGTHS = (3, 4, 5, 6, 7)
WHEEL_OUT = os.path.join(PUZZLES, "WordWheelWords.kt")
WHEEL_CLUES_OUT = os.path.join(PUZZLES, "WordWheelClues.kt")

# A wheel word (the root, all its letters) must spell at least this many other grid words, and a
# seven-letter one a six-letter grid word, so every tier's grid has words to choose from.
ROOT_MIN_WORDS = {5: 8, 6: 12, 7: 14}


def letters(w):
    return "".join(sorted(w))


def sub_multisets(w):
    """Every sorted letter multiset of three or more of w's letters."""
    out = set()
    n = len(w)
    for mask in range(1, 1 << n):
        if bin(mask).count("1") >= 3:
            out.add("".join(sorted(w[i] for i in range(n) if mask >> i & 1)))
    return out


def wheel(lists):
    """
    Word Wheel's lists, from the same screening as Lexicon's (lists = main(..., WHEEL_LENGTHS)):

      GRID_n   answer-grade words (SCOWL 10/20/35, dialect-neutral, no inflections, minus exclude.txt) that
               have a clue: clues.txt gives every 4- and 5-letter one, wheel-clues.txt every 3-letter one and
               the 6- and 7-letter words chosen for the wheel. Only a clued word can be a grid word, because
               the hints give a slot's word as its clue.
      ROOTS_n  grid words of 5, 6 and 7 letters that spell enough other grid words (ROOT_MIN_WORDS).
      BONUS    every other accepted word (Lexicon's guess rule at every length) that some root can spell:
               a word no wheel can make could never be typed, so it is left out.
    """
    lex, extra = read_clues("clues.txt"), read_clues("wheel-clues.txt")
    clued = set(lex) | set(extra)
    answers = {n: set(lists[n][1]) for n in WHEEL_LENGTHS}
    stray = sorted(w for w in extra if len(w) not in (3, 6, 7) or w not in answers[len(w)])
    if stray:
        sys.exit(f"wheel-clues.txt clues words that are not 3-, 6- or 7-letter answers: {stray[:20]}")
    unclued = sorted(answers[3] - set(extra))
    if unclued:
        sys.exit(f"wheel-clues.txt is missing three-letter answers: {unclued[:20]}")
    if set(lex) & set(extra):
        sys.exit(f"clued twice: {sorted(set(lex) & set(extra))[:20]}")
    grid = {n: sorted(answers[n] & clued) for n in WHEEL_LENGTHS}
    by_letters = {}
    for n in WHEEL_LENGTHS:
        for w in grid[n]:
            by_letters.setdefault(letters(w), []).append(w)
    roots = {}
    for n in (5, 6, 7):
        roots[n] = []
        for w in grid[n]:
            spelt = [x for k in sub_multisets(w) for x in by_letters.get(k, []) if x != w]
            if len(spelt) >= ROOT_MIN_WORDS[n] and (n < 7 or any(len(x) == 6 for x in spelt)):
                roots[n].append(w)
    reach = set()
    for n in (5, 6, 7):
        for w in roots[n]:
            reach |= sub_multisets(w)
    grid_all = {w for n in WHEEL_LENGTHS for w in grid[n]}
    bonus = sorted(w for n in WHEEL_LENGTHS for w in lists[n][0] if letters(w) in reach and w not in grid_all)
    return grid, roots, bonus, extra


def wheel_kotlin(grid, roots, bonus):
    lines = [
        "package com.joebywan.daybook.puzzles",
        "",
        "// GENERATED by tools/words/build.py from SCOWL (see docs/word-lists/LICENSE-SCOWL.txt). Do not edit by hand:",
        "// a board's wheel is an index into a ROOTS list, and its grid is laid from the GRID lists in order, so",
        "// reordering any of them changes every daily board. Each list is chunks of space-separated words, because a",
        "// JVM string constant is capped at 64 KB.",
        "",
        "internal object WordWheelWords {",
    ]
    for n in WHEEL_LENGTHS:
        lines += word_list(f"GRID_{n}", grid[n])
    for n in (5, 6, 7):
        lines += word_list(f"ROOTS_{n}", roots[n])
    lines += word_list("BONUS", bonus)
    lines[-1] = "}"
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    result = main(sys.argv[1], sys.argv[2], sys.argv[3])
    for n in LENGTHS:
        print(n, "guesses", len(result[n][0]), "answers", len(result[n][1]), file=sys.stderr)
    grid, roots, bonus, wheel_clues = wheel(main(sys.argv[1], sys.argv[2], sys.argv[3], WHEEL_LENGTHS))
    for n in WHEEL_LENGTHS:
        print("wheel", n, "grid", len(grid[n]), "roots", len(roots.get(n, [])), file=sys.stderr)
    print("wheel bonus", len(bonus), "clues written for the wheel", len(wheel_clues), file=sys.stderr)
    if "--words" in sys.argv:
        for n in LENGTHS:
            print("\n".join(result[n][1]), file=open(f"/tmp/answers{n}.txt", "w"))
    else:
        with open(OUT, "w") as f:
            f.write(kotlin(result))
        with open(CLUES_OUT, "w") as f:
            f.write(clues_kotlin(result))
        with open(WHEEL_OUT, "w") as f:
            f.write(wheel_kotlin(grid, roots, bonus))
        with open(WHEEL_CLUES_OUT, "w") as f:
            f.write(clue_text("WordWheelClueText", "wheel-clues.txt", wheel_clues))
