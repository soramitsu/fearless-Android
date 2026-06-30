#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PUBLIC_REPO_DIR="${PUBLIC_REPO_DIR:-$PWD}"
PRIVATE_REPO_DIR="${PRIVATE_REPO_DIR:-../fearless-Android-priv}"
MAX_REPORT_LINES="${MAX_REPORT_LINES:-120}"
PRIVATE_OVERLAY_REPORT="${PRIVATE_OVERLAY_REPORT:-$PUBLIC_REPO_DIR/build/reports/private-overlay-boundary.tsv}"

fail() {
  echo "[private-overlay-audit][error] $*" >&2
  exit 1
}

info() {
  echo "[private-overlay-audit] $*"
}

[[ -d "$PUBLIC_REPO_DIR/.git" ]] || fail "PUBLIC_REPO_DIR is not a Git checkout: $PUBLIC_REPO_DIR"
[[ -d "$PRIVATE_REPO_DIR/.git" ]] || fail "PRIVATE_REPO_DIR is not a Git checkout: $PRIVATE_REPO_DIR"

is_allowed_overlay_path() {
  case "$1" in
    README.md|LICENSE|.gitignore|Jenkinsfile)
      return 0
      ;;
    .github/*|docs/release*|docs/rollback*|docs/private-overlay*|metadata/*|play/*|release/*|fastlane/*)
      return 0
      ;;
    app/src/release/*|app/src/release/**)
      return 0
      ;;
    scripts/restore-release-overlays.sh|scripts/audit-private-overlay-boundary.sh)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

private_files="$tmpdir/private-files"
unexpected="$tmpdir/unexpected"

git -C "$PRIVATE_REPO_DIR" ls-files | sort > "$private_files"

: > "$unexpected"

while IFS= read -r path || [[ -n "$path" ]]; do
  [[ -n "$path" ]] || continue
  is_allowed_overlay_path "$path" && continue

  public_path="$PUBLIC_REPO_DIR/$path"
  private_path="$PRIVATE_REPO_DIR/$path"
  if [[ ! -f "$public_path" ]]; then
    printf 'A\t%s\n' "$path" >> "$unexpected"
  elif [[ ! -f "$private_path" ]]; then
    printf 'A\t%s\n' "$path" >> "$unexpected"
  elif ! cmp -s "$public_path" "$private_path"; then
    printf 'M\t%s\n' "$path" >> "$unexpected"
  else
    printf 'T\t%s\n' "$path" >> "$unexpected"
  fi
done < "$private_files"

if [[ -s "$unexpected" ]]; then
  count="$(wc -l < "$unexpected" | tr -d '[:space:]')"
  mkdir -p "$(dirname "$PRIVATE_OVERLAY_REPORT")"
  cp "$unexpected" "$PRIVATE_OVERLAY_REPORT"
  echo "[private-overlay-audit][error] private Android repo is not overlay-only." >&2
  echo "[private-overlay-audit][error] Unexpected added or modified paths: $count" >&2
  echo "[private-overlay-audit][error] Full report: $PRIVATE_OVERLAY_REPORT" >&2
  sed -n "1,${MAX_REPORT_LINES}p" "$unexpected" >&2
  if (( count > MAX_REPORT_LINES )); then
    echo "[private-overlay-audit][error] Output truncated at $MAX_REPORT_LINES paths." >&2
  fi
  fail "move product code and public config back to the public repo; keep only release overlays in private storage"
fi

info "Private Android overlay boundary passed."
