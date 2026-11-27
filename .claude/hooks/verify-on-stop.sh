#!/bin/sh
# Stop hook: when an agent turn ends with uncommitted changes, runs the full build and reports a failure back to
# the agent. Exits 0 without building when there is nothing to build, when the working tree is clean (committed
# work is covered by CI, and turns without edits do not need a build), and when the agent is already continuing
# because of this hook, so that an unfixable failure cannot keep the agent in a loop.

input=$(cat)
if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[ -f mvnw ] || exit 0

if changes=$(git status --porcelain 2>/dev/null) && [ -z "$changes" ]; then
  exit 0
fi

if ! output=$(sh ./mvnw -q -B verify 2>&1); then
  {
    echo "./mvnw verify failed. Do not mark tasks as done until it passes. Last lines:"
    printf '%s\n' "$output" | tail -n 40
  } >&2
  exit 2
fi
