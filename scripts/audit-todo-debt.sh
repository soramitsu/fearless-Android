#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${TODO_AUDIT_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
BASELINE_FILE="${TODO_AUDIT_BASELINE:-$ROOT_DIR/config/todo-debt-baseline.tsv}"

log() { echo "[todo-audit] $*"; }
fail() {
  echo "[todo-audit][error] $*" >&2
  exit 1
}

if [[ ! -d "$ROOT_DIR" ]]; then
  fail "Root directory does not exist: $ROOT_DIR"
fi

if [[ ! -f "$BASELINE_FILE" ]]; then
  fail "Baseline file does not exist: $BASELINE_FILE"
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

current="$tmp_dir/current.tsv"
baseline="$tmp_dir/baseline.tsv"
new_markers="$tmp_dir/new.tsv"
stale_markers="$tmp_dir/stale.tsv"
executable_todos="$tmp_dir/executable_todos.txt"

grep_source_files() {
  local pattern="$1"

  (
    cd "$ROOT_DIR"
    find . \
      \( -path './build' -o -path './build/*' \
        -o -path '*/build' -o -path '*/build/*' \
        -o -path './.gradle' -o -path './.gradle/*' \
        -o -path '*/.gradle' -o -path '*/.gradle/*' \
        -o -path './fearless-utils-Android' -o -path './fearless-utils-Android/*' \
        -o -path './.git' -o -path './.git/*' \
        -o -path '*/src/main/res/values*' -o -path '*/src/main/res/values*/*' \) -prune \
      -o -type f \
      \( -name '*.kt' -o -name '*.kts' -o -name '*.java' -o -name '*.xml' -o -name '*.gradle' -o -name '*.gradle.kts' \) \
      -print0 |
      xargs -0 grep -HInEi "$pattern" || true
  )
}

scan_marker_debt() {
  if command -v rg >/dev/null 2>&1; then
    (
      cd "$ROOT_DIR"
      rg -n --no-heading -i '\b(todo|fixme|stopship)\b' \
        --glob '*.kt' \
        --glob '*.kts' \
        --glob '*.java' \
        --glob '*.xml' \
        --glob '*.gradle' \
        --glob '*.gradle.kts' \
        --glob '!**/build/**' \
        --glob '!**/.gradle/**' \
        --glob '!fearless-utils-Android/**' \
        --glob '!**/.git/**' \
        --glob '!**/src/main/res/values*/**/*.xml' \
        . || true
    )
  else
    grep_source_files '(^|[^[:alnum:]_])(todo|fixme|stopship)([^[:alnum:]_]|$)'
  fi | awk -F: '
    {
      line = $0
      sub(/^[^:]+:[0-9]+:/, "", line)
      path = $1
      sub(/^\.\//, "", path)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      print path "\t" line
    }
  ' | LC_ALL=C sort
}

scan_executable_todos() {
  if command -v rg >/dev/null 2>&1; then
    (
      cd "$ROOT_DIR"
      rg -n --no-heading '(^|[^[:alnum:]_])TODO[[:space:]]*\(' \
        --glob '*.kt' \
        --glob '*.kts' \
        --glob '*.java' \
        --glob '*.gradle' \
        --glob '*.gradle.kts' \
        --glob '!**/build/**' \
        --glob '!**/.gradle/**' \
        --glob '!fearless-utils-Android/**' \
        --glob '!**/.git/**' \
        . || true
    )
  else
    grep_source_files '(^|[^[:alnum:]_])TODO[[:space:]]*\('
  fi
}

LC_ALL=C sort "$BASELINE_FILE" > "$baseline"
scan_marker_debt > "$current"
scan_executable_todos > "$executable_todos"

if [[ -s "$executable_todos" ]]; then
  echo "Executable TODO calls are forbidden because they can crash runtime or preview paths:" >&2
  sed -n '1,40p' "$executable_todos" >&2
  fail "Remove TODO(...) calls instead of baselining them."
fi

comm -23 "$current" "$baseline" > "$new_markers"
comm -13 "$current" "$baseline" > "$stale_markers"

if [[ -s "$new_markers" ]]; then
  echo "New TODO/FIXME/STOPSHIP markers found outside the baseline:" >&2
  sed -n '1,80p' "$new_markers" >&2
  fail "Resolve the markers or intentionally update config/todo-debt-baseline.tsv."
fi

if [[ -s "$stale_markers" ]]; then
  echo "Baseline entries no longer exist in source:" >&2
  sed -n '1,80p' "$stale_markers" >&2
  fail "Remove stale entries from config/todo-debt-baseline.tsv."
fi

log "TODO/FIXME debt matches baseline."
