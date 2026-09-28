#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
# Reviewed JSON is data, never shell code. Environment commit values cannot
# replace the artifact's checked-in source pins.
rows="$(python3 - "$ROOT_DIR/config/android-runtime-source-pins.json" <<'PY'
import json,re,sys
from pathlib import Path
p=Path(sys.argv[1])
if p.is_symlink() or not p.is_file():raise SystemExit('Runtime source pins must be a regular file')
v=json.loads(p.read_text())
if set(v)!={'schemaVersion','utils','websocket'} or v['schemaVersion']!=1:raise SystemExit('Invalid runtime source pin schema')
for name in ['utils','websocket']:
 s=v[name]
 if set(s)!={'repository','commit','tree'}:raise SystemExit('Invalid source pin fields')
 if not re.fullmatch(r'soramitsu/[A-Za-z0-9_.-]+',s['repository']):raise SystemExit('Invalid repository')
 if not all(re.fullmatch(r'[0-9a-f]{40}',s[k]) for k in ['commit','tree']):raise SystemExit('Invalid source object')
 print('\t'.join([name,s['repository'],s['commit'],s['tree']]))
PY
)"
while IFS=$'\t' read -r name repository commit tree; do
  case "$name" in
    utils) source_path="${FEARLESS_UTILS_PATH:-$ROOT_DIR/fearless-utils-Android}" ;;
    websocket) source_path="${FEARLESS_NV_WEBSOCKET_PATH:-$ROOT_DIR/fearless-nv-websocket-client}" ;;
    *) exit 1 ;;
  esac
  [[ ! -L "$source_path" && -d "$source_path" ]] || { echo "Invalid $name source path" >&2; exit 1; }
  FEARLESS_UTILS_PATH="$source_path" FEARLESS_UTILS_COMMIT="$commit" \
    FEARLESS_UTILS_REPOSITORY="$repository" FEARLESS_UTILS_LIBRARY_ONLY=true \
    "$ROOT_DIR/scripts/ensure-fearless-utils.sh"
  [[ "$(GIT_NO_REPLACE_OBJECTS=1 git -C "$source_path" rev-parse 'HEAD^{tree}')" == "$tree" ]] || {
    echo "$name source tree does not match artifact pins" >&2; exit 1;
  }
  echo "[android-runtime-source] $name $repository $commit $tree"
done <<< "$rows"
