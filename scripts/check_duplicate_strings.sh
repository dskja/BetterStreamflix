#!/usr/bin/env bash
# Fails if any values*/strings.xml contains duplicate <string name="..."> entries.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FOUND=0

while IFS= read -r -d '' file; do
  dups=$(grep -oE 'name="[^"]+"' "$file" | sort | uniq -d || true)
  if [[ -n "${dups}" ]]; then
    echo "Duplicate string names in $file:"
    echo "$dups"
    FOUND=1
  fi
done < <(find "$ROOT/app/src" -type f -path '*/res/values*/strings.xml' -print0)

if [[ "$FOUND" -ne 0 ]]; then
  echo "Duplicate string resources found. Fix before merging."
  exit 1
fi

echo "No duplicate string names found."
