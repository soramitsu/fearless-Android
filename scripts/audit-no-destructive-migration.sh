#!/usr/bin/env bash
# Read-only check that a ref is a safe release or hotfix base.
#
# Usage: scripts/audit-no-destructive-migration.sh [--root <repo>] [<ref>...]
#
# A ref passes when its shipped Kotlin/Java sources never call
# fallbackToDestructiveMigration*, clearAllTables( or deleteDatabase(, and its
# Room schema version is at least the floor in this checkout's
# core-db/released-database-versions.txt (every line up to this checkout's
# versionCode). The ledger must record this checkout's own versionCode at this
# checkout's APP_DATABASE_VERSION, so a schema bump cannot leave the floor
# under-recorded. Without refs it audits HEAD plus the local develop, master,
# release/* and hotfix/* branches. Remote refs such as origin/master are
# audited only when named, after a fetch by the release owner.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REFS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --root)
      ROOT_DIR="$(cd "$2" && pwd)"
      shift 2
      ;;
    *)
      REFS+=("$1")
      shift
      ;;
  esac
done

LEDGER="$ROOT_DIR/core-db/released-database-versions.txt"
VERSION_PROPERTIES="$ROOT_DIR/versioning/version.properties"
POLICY_SOURCE="core-db/src/main/java/jp/co/soramitsu/coredb/AppDatabaseMigrationPolicy.kt"
DATABASE_SOURCE="core-db/src/main/java/jp/co/soramitsu/coredb/AppDatabase.kt"
FAILURES=0

log() {
  echo "[no-destructive-migration-audit] $*"
}

record_failure() {
  echo "[no-destructive-migration-audit] ERROR: $*" >&2
  FAILURES=1
}

[[ -f "$LEDGER" ]] || { record_failure "missing ${LEDGER#$ROOT_DIR/}"; exit 1; }
[[ -f "$VERSION_PROPERTIES" ]] || { record_failure "missing ${VERSION_PROPERTIES#$ROOT_DIR/}"; exit 1; }

current_code="$(awk -F= '$1 == "versionCode" { print $2 }' "$VERSION_PROPERTIES" | tr -d '[:space:]')"
[[ "$current_code" =~ ^[1-9][0-9]*$ ]] || {
  record_failure "versionCode in ${VERSION_PROPERTIES#$ROOT_DIR/} is not numeric"
  exit 1
}

malformed="$(grep -vnE '^[[:space:]]*(#.*)?$|^[[:space:]]*[0-9A-Za-z.-]+\+[1-9][0-9]*=[1-9][0-9]*[[:space:]]*$' "$LEDGER" || true)"
if [[ -n "$malformed" ]]; then
  record_failure "${LEDGER#$ROOT_DIR/} has lines that are not <versionName>+<versionCode>=<database>:"
  printf '%s\n' "$malformed" >&2
  exit 1
fi

floor="$(awk -F'[+=]' -v code="$current_code" '
  /^[[:space:]]*(#|$)/ { next }
  $2 + 0 <= code + 0 && $3 + 0 > max { max = $3 + 0 }
  END { print max + 0 }
' "$LEDGER")"
if [[ "$floor" -eq 0 ]]; then
  record_failure "${LEDGER#$ROOT_DIR/} records no build up to versionCode $current_code"
  exit 1
fi

# The floor protects later builds only when this build's own line is exact.
current_database="$(sed -nE 's/.*APP_DATABASE_VERSION = ([0-9]+).*/\1/p' \
  "$ROOT_DIR/$POLICY_SOURCE" 2>/dev/null | head -n 1 || true)"
if [[ -z "$current_database" ]]; then
  record_failure "$POLICY_SOURCE in this checkout declares no APP_DATABASE_VERSION"
  exit 1
fi
recorded="$(awk -F'[+=]' -v code="$current_code" '
  /^[[:space:]]*(#|$)/ { next }
  $2 + 0 == code + 0 { print $3 + 0 }
' "$LEDGER")"
if [[ -z "$recorded" ]]; then
  record_failure "${LEDGER#$ROOT_DIR/} has no line for versionCode $current_code; add" \
    "<versionName>+$current_code=$current_database before distributing it"
  exit 1
fi
if [[ "$recorded" == *$'\n'* ]]; then
  record_failure "${LEDGER#$ROOT_DIR/} repeats versionCode $current_code"
  exit 1
fi
if [[ "$recorded" -ne "$current_database" ]]; then
  record_failure "${LEDGER#$ROOT_DIR/} records database $recorded for versionCode" \
    "$current_code, but APP_DATABASE_VERSION is $current_database; update that line"
  exit 1
fi

database_version_at() {
  local ref="$1"
  local version
  version="$(git -C "$ROOT_DIR" show "$ref:$POLICY_SOURCE" 2>/dev/null |
    sed -nE 's/.*APP_DATABASE_VERSION = ([0-9]+).*/\1/p' | head -n 1 || true)"
  if [[ -z "$version" ]]; then
    # Releases before the migration policy declared the version inline.
    version="$(git -C "$ROOT_DIR" show "$ref:$DATABASE_SOURCE" 2>/dev/null |
      sed -nE 's/^[[:space:]]*version = ([0-9]+),?[[:space:]]*$/\1/p' | head -n 1 || true)"
  fi
  printf '%s' "$version"
}

audit_ref() {
  local ref="$1"
  local commit
  if ! commit="$(git -C "$ROOT_DIR" rev-parse --verify --quiet "$ref^{commit}")"; then
    record_failure "$ref is not a commit"
    return
  fi

  local matches status=0
  matches="$(git -C "$ROOT_DIR" grep -n -E \
    'fallbackToDestructiveMigration|clearAllTables\(|deleteDatabase\(' "$commit" -- \
    '*.kt' '*.java' \
    ':(exclude,glob)**/src/test*/**' \
    ':(exclude,glob)**/src/androidTest*/**' \
    ':(exclude,glob)**/src/sharedTest/**')" || status=$?
  if [[ "$status" -gt 1 ]]; then
    record_failure "$ref: git grep failed with status $status"
    return
  fi

  local ok=1
  if [[ -n "$matches" ]]; then
    record_failure "$ref ($commit) can drop the user database:"
    printf '%s\n' "${matches//$commit:/}" >&2
    ok=0
  fi

  local version
  version="$(database_version_at "$commit")"
  if [[ -z "$version" ]]; then
    record_failure "$ref ($commit) declares no Room schema version"
    ok=0
  elif [[ "$version" -lt "$floor" ]]; then
    record_failure "$ref ($commit) is at database $version, below the shipped floor $floor"
    ok=0
  fi

  if [[ "$ok" -eq 1 ]]; then
    log "$ref ($commit) passes: database $version >= floor $floor, no destructive calls"
  fi
}

if [[ "${#REFS[@]}" -eq 0 ]]; then
  REFS=(HEAD)
  while IFS= read -r ref; do
    REFS+=("$ref")
  done < <(git -C "$ROOT_DIR" for-each-ref --format='%(refname:short)' \
    refs/heads/develop refs/heads/master 'refs/heads/release/*' 'refs/heads/hotfix/*')
fi

for ref in "${REFS[@]}"; do
  audit_ref "$ref"
done

if [[ "$FAILURES" -ne 0 ]]; then
  exit 1
fi

log "All audited refs are safe release bases."
