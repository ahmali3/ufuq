#!/usr/bin/env bash
# Publishes this run's logs, test summaries and screenshots to the orphan branch
# "ci-results" (overwritten each run), so they can be read without signing in.
set -euo pipefail

SRC="${1:-results}"
WORK=$(mktemp -d)
mkdir -p "$SRC"
cp -r "$SRC"/. "$WORK"/
# HTML reports are large and only useful locally; keep summaries, logs and screenshots.
find "$WORK" -type d -name reports -prune -exec rm -rf {} + || true
find "$WORK" -type d -name test-results -prune -exec rm -rf {} + || true

{
  echo "# CI results"
  echo
  echo "Commit: ${GITHUB_SHA:-?}  "
  echo "Run: ${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
  echo
  for f in "$WORK"/*/status.txt; do
    [ -f "$f" ] || continue
    echo "## $(basename "$(dirname "$f")")"
    echo '```'; cat "$f"; echo '```'
  done
} > "$WORK/README.md"

cd "$WORK"
git init -q
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git checkout -q -b ci-results
git add -A
git commit -q -m "CI results for ${GITHUB_SHA:-unknown}"
git push -q --force "https://x-access-token:${GITHUB_TOKEN:-$INPUT_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" ci-results
echo "Published to ci-results"
