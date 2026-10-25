#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIT_SCRIPT="$SCRIPT_DIR/audit-no-destructive-migration.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fearless-android-rollback-audit.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

POLICY="core-db/src/main/java/jp/co/soramitsu/coredb/AppDatabaseMigrationPolicy.kt"
DATABASE="core-db/src/main/java/jp/co/soramitsu/coredb/AppDatabase.kt"

fail() {
  echo "[no-destructive-migration-audit-test] ERROR: $*" >&2
  exit 1
}

fixture_git() {
  local repo="$1"
  shift
  GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 git -C "$repo" \
    -c user.name=fixture -c user.email=fixture@example.invalid \
    -c commit.gpgsign=false -c init.defaultBranch=main "$@"
}

write_file() {
  local path="$1"
  mkdir -p "$(dirname "$path")"
  cat > "$path"
}

write_policy() {
  local repo="$1"
  local version="$2"
  write_file "$repo/$POLICY" <<EOF
package jp.co.soramitsu.coredb

internal const val APP_DATABASE_VERSION = $version
internal const val EARLIEST_SUPPORTED_DATABASE_VERSION = 9
EOF
}

write_database() {
  local repo="$1"
  local builder_call="$2"
  write_file "$repo/$DATABASE" <<EOF
package jp.co.soramitsu.coredb

abstract class AppDatabase : RoomDatabase() {
    fun create() = Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
        .addMigrations(*migrations)
        $builder_call
        .build()
}
EOF
}

# Floor is 81: version.properties is at 231, so the 232 target is not counted.
new_repo() {
  local repo="$TMP_DIR/$1"
  mkdir -p "$repo"
  fixture_git "$repo" init -q
  write_file "$repo/versioning/version.properties" <<'EOF'
versionCode=231
versionName=4.2.0
EOF
  write_file "$repo/core-db/released-database-versions.txt" <<'EOF'
# Fixture ledger.
4.2.0+229=75
4.2.0+230=80

4.2.0+231=81
4.3.0+232=82
EOF
  write_policy "$repo" 81
  write_database "$repo" ".addCallback(callback)"
  fixture_git "$repo" add .
  fixture_git "$repo" commit -q -m "safe base"
  printf '%s' "$repo"
}

commit_branch() {
  local repo="$1"
  local branch="$2"
  fixture_git "$repo" checkout -q -b "$branch" main
  fixture_git "$repo" add -A .
  fixture_git "$repo" commit -q -m "$branch"
  fixture_git "$repo" checkout -q main
}

expect_pass() {
  local label="$1"
  shift
  local output="$TMP_DIR/$label.out"
  bash "$AUDIT_SCRIPT" "$@" >"$output" 2>&1 || {
    cat "$output" >&2
    fail "expected $label to pass"
  }
}

expect_fail() {
  local label="$1"
  local expected="$2"
  shift 2
  local output="$TMP_DIR/$label.out"
  if bash "$AUDIT_SCRIPT" "$@" >"$output" 2>&1; then
    cat "$output" >&2
    fail "expected $label to fail"
  fi
  grep -q "ERROR" "$output" || { cat "$output" >&2; fail "$label failed without an audit error"; }
  grep -qF -- "$expected" "$output" || {
    cat "$output" >&2
    fail "$label did not report: $expected"
  }
}

REPO="$(new_repo branches)"
expect_pass "safe-base" --root "$REPO" main

write_database "$REPO" ".fallbackToDestructiveMigration()"
commit_branch "$REPO" destructive
expect_fail "destructive-fallback" "AppDatabase.kt:6:" --root "$REPO" destructive

write_database "$REPO" ".fallbackToDestructiveMigrationOnDowngrade()"
commit_branch "$REPO" downgrade-fallback
expect_fail "downgrade-fallback" "can drop the user database" --root "$REPO" downgrade-fallback

write_file "$REPO/feature-wallet-impl/src/main/java/Reset.kt" <<'EOF'
fun reset(context: Context) = context.deleteDatabase("app.db")
EOF
commit_branch "$REPO" wipe-helper
expect_fail "wipe-helper" "Reset.kt:1:" --root "$REPO" wipe-helper

write_policy "$REPO" 80
commit_branch "$REPO" below-floor
expect_fail "below-floor" "is at database 80, below the shipped floor 81" --root "$REPO" below-floor

fixture_git "$REPO" rm -q "$POLICY"
write_file "$REPO/$DATABASE" <<'EOF'
@Database(
    entities = [],
    version = 76,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase()
EOF
commit_branch "$REPO" pre-policy
expect_fail "pre-policy-version" "is at database 76, below the shipped floor 81" --root "$REPO" pre-policy

write_file "$REPO/core-db/src/androidTest/java/DowngradeTest.kt" <<'EOF'
val db = builder.fallbackToDestructiveMigration().build()
EOF
write_file "$REPO/app/src/testDebug/java/ResetTest.kt" <<'EOF'
fun reset(context: Context) = context.deleteDatabase("test.db")
EOF
commit_branch "$REPO" test-only
expect_pass "test-sources-ignored" --root "$REPO" test-only

expect_fail "missing-ref" "no-such-ref is not a commit" --root "$REPO" no-such-ref

# Without refs the audit covers HEAD and the local release lines.
DEFAULTS="$(new_repo defaults)"
expect_pass "default-refs-safe" --root "$DEFAULTS"
fixture_git "$DEFAULTS" branch -q release/4.2.0 main
fixture_git "$DEFAULTS" checkout -q -b develop main
write_database "$DEFAULTS" ".fallbackToDestructiveMigration()"
fixture_git "$DEFAULTS" commit -q -am "develop keeps the fallback"
fixture_git "$DEFAULTS" checkout -q main
expect_fail "default-refs-develop" "develop (" --root "$DEFAULTS"
grep -qF "release/4.2.0 (" "$TMP_DIR/default-refs-develop.out" ||
  fail "default refs did not include release/*"

MALFORMED="$(new_repo malformed)"
printf '4.2.0 231 81\n' >> "$MALFORMED/core-db/released-database-versions.txt"
expect_fail "malformed-ledger" "<versionName>+<versionCode>=<database>" --root "$MALFORMED" main

UNRECORDED="$(new_repo unrecorded)"
printf 'versionCode=228\nversionName=4.2.0\n' > "$UNRECORDED/versioning/version.properties"
expect_fail "no-recorded-build" "records no build up to versionCode 228" --root "$UNRECORDED" main

# A schema bump that leaves this build's ledger line behind under-records the floor.
UNDER_RECORDED="$(new_repo under-recorded)"
write_policy "$UNDER_RECORDED" 82
fixture_git "$UNDER_RECORDED" commit -q -am "bump the schema without the ledger"
expect_fail "under-recorded-ledger" \
  "records database 81 for versionCode 231, but APP_DATABASE_VERSION is 82" \
  --root "$UNDER_RECORDED" main

UNLISTED="$(new_repo unlisted)"
printf 'versionCode=233\nversionName=4.3.1\n' > "$UNLISTED/versioning/version.properties"
expect_fail "current-build-unlisted" "has no line for versionCode 233" --root "$UNLISTED" main

echo "[no-destructive-migration-audit-test] Rollback base audit self-test passed."
