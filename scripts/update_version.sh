#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/update_version.sh            # updates app/build.gradle.kts versionName and versionCode from commit_count+1
#   ./scripts/update_version.sh --dry-run  # prints the would-be new file to stdout (no write)

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
new_build=$((commit_count + 1))

# Derive version fields
version_code="$new_build"
major=$((new_build / 100))
minor=$((new_build % 100))
# Zero-pad minor to 2 digits
printf -v minor_padded "%02d" "$minor"
version_name="${major}.${minor_padded}"

file="$ROOT_DIR/app/build.gradle.kts"
if [[ ! -f "$file" ]]; then
  echo "Error: expected file not found: $file" >&2
  exit 3
fi

# Replace versionCode (int) and versionName (quoted string) using robust captures.
# - Only replace the assigned value, keeping existing whitespace and formatting.
VC="$version_code" VN="$version_name" perl -0777 -pe '
  BEGIN { $vc = $ENV{"VC"}; $vn = $ENV{"VN"}; }
  s/(?m)^(\s*versionCode\s*=\s*)\d+/${1}$vc/;
  s/(?m)^(\s*versionName\s*=\s*")[^"]*(")/${1}$vn$2/;
' "$file" > "$file.tmp"

# Basic validation to ensure substitutions occurred
if ! grep -qE "^\s*versionCode\s*=\s*${version_code}\b" "$file.tmp"; then
  echo "Error: failed to update versionCode in $file" >&2
  rm -f "$file.tmp"
  exit 4
fi
if ! grep -qE "^\s*versionName\s*=\s*\"${version_name}\"\b" "$file.tmp"; then
  echo "Error: failed to update versionName in $file" >&2
  rm -f "$file.tmp"
  exit 5
fi

if [[ $DRY_RUN -eq 1 ]]; then
  echo "Commit count: $commit_count"
  echo "New build (commit_count + 1): $new_build"
  echo "Would set versionCode: $version_code"
  echo "Would set versionName: $version_name"
  echo "--- DRY RUN: new file contents ---"
  cat "$file.tmp"
  rm -f "$file.tmp"
  exit 0
fi

# Replace original file
mv "$file.tmp" "$file"

echo "Commit count: $commit_count"
echo "Updated $file -> versionCode $version_code, versionName $version_name"
exit 0
