#!/usr/bin/env bash
# Give Renovate's pull requests a real `build` check.
#
# Pull requests and pushes made with the default GITHUB_TOKEN do not trigger other workflows, so
# CI never starts on them, and `main`'s ruleset requires a check named `build`. But
# workflow_dispatch is exempt from that rule, so ci.yml is run by hand on the Renovate branch.
# A dispatched run's own check run is NOT counted by the pull request (verified: the PR stayed
# BLOCKED with an empty rollup), so ci.yml also posts a commit status named `build` on the head
# commit, and that does satisfy the ruleset. This script is what decides to dispatch.
#
# For every open PR whose head branch starts with $BRANCH_PREFIX (default `renovate/`), dispatch
# ci.yml on that branch unless its current head commit already has a `build` commit status or
# check run (pending, passed or failed) or a dispatched run is already in flight. A failed build
# is deliberately NOT re-run: it should be looked at. A cancelled one (status `error`) is. When Renovate rebases a
# branch the head SHA changes, so the next run dispatches again; a PR already built is left alone.
#
# Needs GH_TOKEN with `actions: write`, `checks: read`, `statuses: read`, `pull-requests: read`.
# Environment: REPO (default $GITHUB_REPOSITORY), BRANCH_PREFIX, WORKFLOW (default ci.yml),
# CHECK_NAME (default build), DRY_RUN=1 to only print what would be dispatched.
set -euo pipefail

repo="${REPO:-${GITHUB_REPOSITORY:?set REPO or GITHUB_REPOSITORY}}"
prefix="${BRANCH_PREFIX:-renovate/}"
workflow="${WORKFLOW:-ci.yml}"
check="${CHECK_NAME:-build}"
failed=0

# Tab-separated "branch<TAB>sha" for same-repo PRs only (a fork's branch cannot be dispatched).
prs=$(gh pr list --repo "$repo" --state open --limit 100 \
  --json headRefName,headRefOid,isCrossRepository \
  --jq ".[] | select(.isCrossRepository == false and (.headRefName | startswith(\"$prefix\")))
        | [.headRefName, .headRefOid] | @tsv")

while IFS=$'\t' read -r branch sha; do
  [ -n "$branch" ] || continue

  # A `build` commit status (what a dispatched run posts) or check run (what a pull_request run
  # produces, e.g. if a PAT is in use), ignoring errored/cancelled ones.
  statuses=$(gh api "repos/$repo/commits/$sha/status" \
    --jq "[.statuses[] | select(.context == \"$check\" and .state != \"error\")] | length")
  runs=$(gh api "repos/$repo/commits/$sha/check-runs?per_page=100" \
    --jq "[.check_runs[] | select(.name == \"$check\" and .conclusion != \"cancelled\")] | length")
  if [ $((statuses + runs)) -gt 0 ]; then
    echo "$branch ($sha): '$check' already present, leaving alone"
    continue
  fi

  # A dispatched run may not have created its check run yet; do not dispatch twice.
  inflight=$(gh run list --repo "$repo" --workflow "$workflow" --branch "$branch" \
    --event workflow_dispatch --json headSha,status \
    --jq "[.[] | select(.headSha == \"$sha\" and (.status == \"queued\" or .status == \"in_progress\" or .status == \"waiting\" or .status == \"requested\" or .status == \"pending\"))] | length")
  if [ "$inflight" -gt 0 ]; then
    echo "$branch ($sha): a dispatched run is already in flight"
    continue
  fi

  if [ "${DRY_RUN:-0}" = 1 ]; then
    echo "$branch ($sha): would dispatch $workflow"
  elif gh workflow run "$workflow" --repo "$repo" --ref "$branch"; then
    echo "$branch ($sha): dispatched $workflow"
  else
    echo "::error::could not dispatch $workflow on $branch"
    failed=1
  fi
done <<< "$prs"

exit "$failed"
