#!/usr/bin/env python3
"""Strip a Claude attribution footer from the end of a pull request or comment body.

The GitHub connector Claude's cloud sessions post through appends one itself, after the text has
passed `tools/block-claude-attribution.sh`, so the hook cannot stop it. `attribution.yml` runs this
on every PR body and comment as it lands. Only a trailing footer block is touched: text that merely
quotes the phrase mid-body is left for the check to report. Reads stdin, writes stdout.
"""
import re
import sys

LINK = r"\[Claude Code\]\(https://claude\.(?:ai/code|com/claude-code)[^)]*\)"
FOOTER = re.compile(
    r"(?:\s*\n)?(?:-{3,}\s*\n)?\s*"
    r"_?(?:\U0001F916\s*)?Generated (?:with|by) " + LINK + r"_?\s*\Z",
    re.IGNORECASE,
)

body = sys.stdin.read()
stripped = body
while FOOTER.search(stripped):  # the harness's footer and the connector's can both be there
    stripped = FOOTER.sub("", stripped).rstrip()
sys.stdout.write(stripped + "\n" if stripped != body else body)
