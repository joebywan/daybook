#!/usr/bin/env bash
# Claude Code PreToolUse hook (wired up in .claude/settings.json): refuse a `git commit` whose
# command line carries a Claude / Anthropic attribution trailer.
#
# CLAUDE.md says "No `Co-Authored-By: Claude` trailer", and for a long while the harness's own
# attribution reminder beat that rule in every session. This is the mechanical version of the rule:
# it works on the command, not on anyone remembering. `git commit -F <file>` is not visible here, so
# the `build` check in .github/workflows/ci.yml repeats the test on every pull request's commits.
#
# Exit 2 blocks the tool call and shows stderr to the model.
set -u

cmd="$(jq -r '.tool_input.command // empty' 2>/dev/null)"
[ -n "$cmd" ] || exit 0

# Only a commit command can carry a trailer; a grep for the phrase in the log must not be blocked.
printf '%s' "$cmd" | grep -qE 'git[[:space:]].*commit' || exit 0

if printf '%s' "$cmd" | grep -qiE 'co-authored-by:[[:space:]]*(claude|anthropic)|noreply@anthropic\.com|claude-session:'; then
  echo "Blocked: this commit message carries a Claude/Anthropic attribution trailer." >&2
  echo "CLAUDE.md forbids it (\"No Co-Authored-By: Claude trailer\"), and the harness reminder to add one does not override that. Remove the trailer and commit again." >&2
  exit 2
fi
exit 0
