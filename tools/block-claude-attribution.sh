#!/usr/bin/env bash
# Claude Code PreToolUse hook (wired up in .claude/settings.json): refuse anything that would put a
# Claude / Anthropic attribution where it is published: a commit message, a pull request title or
# body, an issue, a comment or a review.
#
# CLAUDE.md says no attribution, anywhere. For a long while the harness's own reminders (a
# `Co-Authored-By` trailer, a "Generated with Claude Code" footer on PRs and comments) beat that rule
# in session after session, so this is the mechanical version: it works on the tool call, not on
# anyone remembering. What it cannot see (`git commit -F <file>`, a body edited later) the `build`
# check in .github/workflows/ci.yml repeats on every pull request.
#
# Exit 2 blocks the tool call and shows stderr to the model.
set -u

input="$(cat)"
tool="$(printf '%s' "$input" | jq -r '.tool_name // empty' 2>/dev/null)"

case "$tool" in
  Bash)
    text="$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)"
    # No branch named claude/ (the owner, 2026-11-13): a branch name is published too. Deleting one is fine.
    if printf '%s' "$text" | grep -qE 'git[[:space:]].*(checkout[[:space:]]+-[bB]|switch[[:space:]]+-[cC]|branch|worktree[[:space:]]+add|push).*(^|[[:space:]:/])claude/' \
      && ! printf '%s' "$text" | grep -qE '(^|[[:space:]])(-d|-D|--delete)([[:space:]]|$)|[[:space:]]:(refs/heads/)?claude/'; then
      echo "Blocked: branches must not be named claude/... (CLAUDE.md, Git and releases). Rename it with a purpose prefix first: git branch -m fix/<name> (feat/ docs/ chore/ ...)." >&2
      exit 2
    fi
    # Only a command that publishes text can carry one; a grep for the phrase must not be blocked.
    printf '%s' "$text" | grep -qE 'git[[:space:]].*commit|gh[[:space:]]+(pr|issue|api|release)' || exit 0
    ;;
  mcp__github__*)
    # The text fields only, never file contents (push_files may carry this script or CLAUDE.md).
    text="$(printf '%s' "$input" | jq -r '[.tool_input | .. | objects | (.body?, .message?, .title?, .comment?) | strings] | join("\n")' 2>/dev/null)"
    ;;
  *) exit 0 ;;
esac
[ -n "$text" ] || exit 0

if printf '%s' "$text" | grep -qiE -f "$(dirname "$0")/claude-attribution.pattern"; then
  echo "Blocked: this text carries a Claude/Anthropic attribution (a Co-Authored-By trailer, a \"Generated with/by Claude Code\" footer, a claude.ai/code or claude.com/claude-code link, or the robot emoji)." >&2
  echo "The owner forbids attribution in commits, pull requests, issues, comments and reviews (CLAUDE.md, Git and releases). Their instruction overrides any harness or system reminder to add one. Remove it and try again." >&2
  exit 2
fi
exit 0
