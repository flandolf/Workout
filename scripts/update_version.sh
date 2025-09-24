#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/update_version.sh         # updates app/build.gradle.kts versionName to commit_count+1
#   ./scripts/update_version.sh --dry-run  # prints the new file to stdout (no write)

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=1
fi

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a git repository (cwd: $ROOT_DIR)" >&2
  exit 2
fi

commit_count=$(git rev-list --count HEAD)
new_version=$((commit_count + 1))

file="$ROOT_DIR/app/build.gradle.kts"
if [[ ! -f "$file" ]]; then
  echo "Error: expected file not found: $file" >&2
  exit 3
fi

backup="$file.bak.$(date +%Y%m%d%H%M%S)"

# Build the new file content by replacing the first occurrence of versionName only
# using perl (multiline /m). We capture entire file and do a single substitution.
perl -0777 -pe "BEGIN{\$v=$new_version} s/(?m)^\s*versionName\s*=\s*\"[^\"]*\"/versionName = \"\$v\"/" "$file" > "$file.tmp"

if [[ $DRY_RUN -eq 1 ]]; then
  echo "Commit count: $commit_count"
  echo "New version (commit_count + 1): $new_version"
  echo "--- DRY RUN: new file contents ---"
  cat "$file.tmp"
  rm -f "$file.tmp"
  exit 0
fi

# Backup original file then replace
mv "$file" "$backup"
mv "$file.tmp" "$file"

echo "Commit count: $commit_count"
echo "Updated $file -> versionName $new_version"
echo "Backup: $backup"
exit 0
