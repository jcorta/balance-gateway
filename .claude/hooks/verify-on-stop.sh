#!/bin/sh
# Stop hook: runs the full build when an agent turn ends and reports a failure back to the agent.
# Exits 0 when there is nothing to build, and when the agent is already continuing because of this
# hook, so that an unfixable failure cannot keep the agent in a loop.

input=$(cat)
if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[ -f mvnw ] || exit 0

if ! output=$(sh ./mvnw -q -B verify 2>&1); then
  {
    echo "./mvnw verify failed. Do not mark tasks as done until it passes. Last lines:"
    printf '%s\n' "$output" | tail -n 40
  } >&2
  exit 2
fi
