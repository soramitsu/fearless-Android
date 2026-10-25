#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
exec python3 "$ROOT_DIR/scripts/verify-android-runtime-sources.py"
