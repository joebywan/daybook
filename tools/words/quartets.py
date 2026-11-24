#!/usr/bin/env python3
"""Lint Quartets' category bank and write app/src/main/java/com/joebywan/daybook/puzzles/QuartetsWords.kt.

Source: tools/words/quartets/*.txt, read in filename order, one category a line:

    Title shown to the player | level | word word ~quiet word ...

A word written ~word is a QUIET member: it fits the category (the proof and every board's masks count it, so a player's
group with it is never refused for want of it), but it is never one of the four the generator deals for that category.
It is for a true fit nobody should be shown as the answer (Head + stone, Black + eye). The generated line then carries a
fourth field of quiet words; it is empty for a category with none.

Blank lines and '#' comments (whole-line or trailing) are ignored. A title may appear more than once, in one file or
several: its members are merged (union), and every line must give it the same level. So a later cross-tagging file can
add members to a category an earlier file made. See docs/QUARTETS_SPEC.md ("The bank").

Errors (all collected, printed with file:line, exit status 1): a level not 1, 2 or 3; a word not a-z or not 3 to 8
letters; a word twice on one line (quiet or not); a quiet word that is also a plain member of its category; a merged category under 6 plain (non-quiet) members; a level clash between lines of one title; a title
over 28 characters, empty, holding '|' or '"', or holding one of its own members as a whole word (any case); two titles
equal but for case; a word in tools/words/grim.txt or tools/words/exclude.txt (read as build.py reads them); two
categories with the same members. Quiet words obey every word rule, grim.txt and exclude.txt included.

Warnings (printed, not fatal): a category whose every member is in one other category (a subset); a word that is
another bank word plus "s" (maybe a singular and its plural); a quiet word that is no category's plain member (no board
can show it, so listing it does nothing).

    python3 tools/words/quartets.py            lint, then write the Kotlin file (--out to put it elsewhere)
    python3 tools/words/quartets.py --lint     lint only
    python3 tools/words/quartets.py --stats    lint, then print counts and the most shared words (writes nothing)
    --dir DIR                                  read DIR/*.txt instead of tools/words/quartets

The output is byte-for-byte deterministic: lines sorted by title (codepoint order), words sorted.
"""
import argparse, glob, os, re, sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "quartets")
OUT = os.path.join(HERE, "../../app/src/main/java/com/joebywan/daybook/puzzles/QuartetsWords.kt")
WORD = re.compile(r"[a-z]{3,8}")
MIN_MEMBERS = 6
MAX_TITLE = 28


def read_list(path):
    """A word list as tools/words/build.py reads exclude.txt and grim.txt: '#' starts a comment, one word a line."""
    if not os.path.exists(path):
        return set()
    with open(path) as f:
        return {line.split("#")[0].strip() for line in f if line.split("#")[0].strip()}


class Category:
    def __init__(self, title, level, where):
        self.title, self.level, self.where = title, level, where
        self.words = set()
        self.quiet = set()


def parse(directory, screened):
    """(categories by title, in first-seen order; errors; warnings). Each error and warning is a printable line."""
    errors, warnings = [], []
    cats = {}
    files = sorted(glob.glob(os.path.join(directory, "*.txt")))
    for path in files:
        name = os.path.relpath(path)
        with open(path) as f:
            for number, raw in enumerate(f, 1):
                line = raw.split("#")[0].strip()
                if not line:
                    continue
                where = f"{name}:{number}"
                parts = [p.strip() for p in line.split("|")]
                if len(parts) != 3:
                    errors.append(f"{where}: expected 'Title | level | words', got {len(parts)} field(s)"
                                  + (" (a '|' in the title?)" if len(parts) > 3 else ""))
                    continue
                title, level_text, word_text = parts
                if level_text not in ("1", "2", "3"):
                    errors.append(f"{where}: level must be 1, 2 or 3, not {level_text!r}")
                    continue
                level = int(level_text)
                tokens = word_text.split()
                words, quiet = [], []
                seen = set()
                for token in tokens:
                    w = token[1:] if token.startswith("~") else token
                    (quiet if token.startswith("~") else words).append(w)
                    if not WORD.fullmatch(w):
                        errors.append(f"{where}: {w!r} is not 3 to 8 letters a-z")
                    if w in seen:
                        errors.append(f"{where}: {w!r} twice on one line")
                    seen.add(w)
                    if w in screened:
                        errors.append(f"{where}: {w!r} is screened out ({screened[w]})")
                cat = cats.get(title)
                if cat is None:
                    cat = cats[title] = Category(title, level, where)
                elif cat.level != level:
                    errors.append(f"{where}: {title!r} is level {level} here but level {cat.level} at {cat.where}")
                    continue
                cat.words |= {w for w in words if WORD.fullmatch(w)}
                cat.quiet |= {w for w in quiet if WORD.fullmatch(w)}
    if not files:
        errors.append(f"{os.path.relpath(directory)}: no *.txt files")
    return cats, errors, warnings


def lint(cats, errors, warnings):
    by_fold = {}
    for cat in cats.values():
        t = cat.title
        if not t:
            errors.append(f"{cat.where}: empty title")
        if len(t) > MAX_TITLE:
            errors.append(f"{cat.where}: title {t!r} is {len(t)} characters, over {MAX_TITLE}")
        if '"' in t or "|" in t:
            errors.append(f"{cat.where}: title {t!r} holds '|' or '\"'")
        title_words = set(re.findall(r"[a-z]+", t.lower()))
        for w in sorted(cat.words & title_words):
            errors.append(f"{cat.where}: title {t!r} gives away its member {w!r}")
        for w in sorted(cat.words & cat.quiet):
            errors.append(f"{cat.where}: {w!r} is both a plain and a quiet member of {t!r}")
        for w in sorted(cat.quiet & title_words):
            errors.append(f"{cat.where}: title {t!r} gives away its quiet member {w!r}")
        if len(cat.words) < MIN_MEMBERS:
            errors.append(f"{cat.where}: {t!r} has {len(cat.words)} plain member(s), under {MIN_MEMBERS}")
        other = by_fold.get(t.lower())
        if other is not None:
            errors.append(f"{cat.where}: title {t!r} differs only in case from {other.title!r} at {other.where}")
        else:
            by_fold[t.lower()] = cat
    by_members = {}
    for cat in cats.values():
        key = frozenset(cat.words)
        other = by_members.get(key)
        if other is not None and key:
            errors.append(f"{cat.where}: {cat.title!r} has exactly the members of {other.title!r} at {other.where}")
        else:
            by_members[key] = cat
    ordered = sorted(cats.values(), key=lambda c: c.title)
    for a in ordered:
        for b in ordered:
            if a is not b and a.words and a.words < b.words:
                warnings.append(f"{a.where}: every member of {a.title!r} is in {b.title!r} ({b.where})")
    bank = set().union(*(c.words for c in cats.values())) if cats else set()
    for cat in ordered:
        for w in sorted(cat.quiet - bank):
            warnings.append(f"{cat.where}: quiet {w!r} in {cat.title!r} is no category's plain member, so no board shows it")
    for w in sorted(bank):
        if w + "s" in bank:
            warnings.append(f"{w!r} and {w + 's'!r} are both in the bank (a singular and its plural?)")


def screened_words():
    screened = {}
    for name in ("exclude.txt", "grim.txt"):
        for w in read_list(os.path.join(HERE, name)):
            screened.setdefault(w, f"tools/words/{name}")
    return screened


def kotlin_string(s):
    return s.replace("\\", "\\\\").replace("$", "\\$").replace('"', '\\"')


def kotlin(cats):
    lines = [
        "package com.joebywan.daybook.puzzles",
        "",
        "// Generated by tools/words/quartets.py from tools/words/quartets/*.txt. Do not edit: change the bank and re-run it.",
        "",
        "internal object QuartetsWords {",
        '    /** "Title|level|word,word,...|quiet,...": words lowercase and sorted, quiet words (fit, never dealt) after the',
        "     *  last bar, empty when none; lines sorted by title. */",
        "    val CATEGORIES: List<String> = listOf(",
    ]
    for cat in sorted(cats.values(), key=lambda c: c.title):
        entry = f"{cat.title}|{cat.level}|{','.join(sorted(cat.words))}|{','.join(sorted(cat.quiet))}"
        lines.append(f'        "{kotlin_string(entry)}",')
    lines += ["    )", "}"]
    return "\n".join(lines) + "\n"


def stats(cats):
    out = []
    levels = defaultdict(int)
    where = defaultdict(list)
    for cat in sorted(cats.values(), key=lambda c: c.title):
        levels[cat.level] += 1
        for w in cat.words:
            where[w].append(cat.title)
    out.append(f"categories: {len(cats)} ("
               + ", ".join(f"level {lv}: {levels[lv]}" for lv in (1, 2, 3)) + ")")
    out.append(f"distinct words: {len(where)}")
    out.append(f"words in 2+ categories: {sum(1 for t in where.values() if len(t) > 1)}")
    out.append(f"quiet memberships: {sum(len(c.quiet) for c in cats.values())}")
    shared = sorted(where.items(), key=lambda kv: (-len(kv[1]), kv[0]))[:30]
    out.append("most shared:")
    for w, titles in shared:
        out.append(f"  {w} ({len(titles)}): {'; '.join(titles)}")
    return "\n".join(out)


def main(argv=None):
    ap = argparse.ArgumentParser(description="Lint Quartets' bank and write QuartetsWords.kt.")
    ap.add_argument("--lint", action="store_true", help="check only, write nothing")
    ap.add_argument("--stats", action="store_true", help="print counts and the most shared words, write nothing")
    ap.add_argument("--dir", default=SOURCE, help="source directory of *.txt files")
    ap.add_argument("--out", default=OUT, help="the Kotlin file to write")
    args = ap.parse_args(argv)
    cats, errors, warnings = parse(args.dir, screened_words())
    lint(cats, errors, warnings)
    for w in warnings:
        print(f"warning: {w}", file=sys.stderr)
    for e in errors:
        print(f"error: {e}", file=sys.stderr)
    if args.stats:
        print(stats(cats))
    if errors:
        print(f"{len(errors)} error(s)", file=sys.stderr)
        return 1
    if args.lint or args.stats:
        return 0
    text = kotlin(cats)
    with open(args.out, "w", newline="\n") as f:
        f.write(text)
    print(f"wrote {len(cats)} categories to {os.path.relpath(args.out)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
