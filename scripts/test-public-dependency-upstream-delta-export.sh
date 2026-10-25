#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
python3 "$ROOT_DIR/scripts/test-android-runtime-source-pins.py"
bash "$ROOT_DIR/scripts/export-public-dependency-upstream-delta.sh" --output "$TEMP/export"
cmp "$ROOT_DIR/config/android-runtime-source-pins.json" "$TEMP/export/android-runtime-source-pins.json"
cmp "$ROOT_DIR/config/remote-config-imports.json" "$TEMP/export/remote-config-imports.json"
if bash "$ROOT_DIR/scripts/export-public-dependency-upstream-delta.sh" --wrong "$TEMP/bad"; then
  echo "Invalid export arguments unexpectedly accepted" >&2; exit 1
fi
echo "[android-source-provenance-test] owned sources, exact exported manifests and invalid arguments verified"
