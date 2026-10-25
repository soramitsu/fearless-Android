#!/usr/bin/env bash
# Compatibility entry point for existing source-bound release receipt tooling.
# This verifies app-owned source only; it never clones, switches, patches or resolves an external library.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
python3 "$ROOT_DIR/scripts/verify-android-runtime-sources.py"
python3 - "$ROOT_DIR/config/android-runtime-source-pins.json" <<'PY'
import json,sys
source=json.load(open(sys.argv[1]))['utils']
print('[fearless-utils] Imported source at '+source['commit'])
print('[fearless-utils] Effective source tree '+source['tree'])
print('[fearless-utils] Imported upstream provenance only; actual source is bound by the app commit and file digests')
PY
