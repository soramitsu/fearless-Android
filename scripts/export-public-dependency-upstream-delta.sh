#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
[[ "$#" == 2 && "$1" == --output ]] || { echo "Usage: $0 --output <directory>" >&2; exit 2; }
python3 "$ROOT_DIR/scripts/verify-android-runtime-sources.py"
python3 "$ROOT_DIR/scripts/verify-owned-remote-config.py"
mkdir -p "$2"
cp "$ROOT_DIR/config/android-runtime-source-pins.json" "$2/android-runtime-source-pins.json"
cp "$ROOT_DIR/config/remote-config-imports.json" "$2/remote-config-imports.json"
printf '%s\n' '{"schemaVersion":2,"ownership":"Android application","externalInternalRuntimeDependencies":false,"releaseQualified":false}' > "$2/manifest.json"
echo "[android-source-provenance] exported app-owned source and configuration manifests"
