#!/usr/bin/env bash
# Existing release-control entry point, now testing sources owned by the Android checkout.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
exec python3 "$ROOT_DIR/scripts/test-android-runtime-source-pins.py"
