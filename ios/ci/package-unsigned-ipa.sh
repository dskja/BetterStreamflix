#!/usr/bin/env bash
# Package an unsigned IPA from an .xcarchive (no Apple signing).
set -euo pipefail

ARCHIVE_PATH="${1:?Usage: package-unsigned-ipa.sh <path/to/App.xcarchive> [output.ipa]}"
OUTPUT_IPA="${2:-BetterStreamflix-unsigned.ipa}"

APP_PATH="$(find "${ARCHIVE_PATH}/Products/Applications" -maxdepth 1 -name '*.app' -print -quit)"
if [[ -z "${APP_PATH}" ]]; then
  echo "No .app found inside ${ARCHIVE_PATH}/Products/Applications" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

mkdir -p "${WORK}/Payload"
cp -R "${APP_PATH}" "${WORK}/Payload/"

(
  cd "${WORK}"
  zip -qr "${OLDPWD}/${OUTPUT_IPA}" Payload
)

echo "Wrote ${OUTPUT_IPA}"
