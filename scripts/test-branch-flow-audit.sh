#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIT_SCRIPT="$SCRIPT_DIR/audit-branch-flow.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

write_valid_repo() {
  local repo="$1"
  mkdir -p "$repo/.github/workflows"
  cat > "$repo/Jenkinsfile" <<'EOF'
def pipeline = new org.android.AppPipeline(
    uploadToNexusFor: ['master','develop']
)
EOF
  cat > "$repo/.github/workflows/android-ci.yml" <<'EOF'
name: Android CI
on:
  push:
    branches: [ master, develop ]
  pull_request:
    branches:
      - develop
      - master
EOF
}

expect_pass() {
  local repo="$1"
  bash "$AUDIT_SCRIPT" "$repo" >/dev/null
}

expect_fail() {
  local repo="$1"
  local label="$2"
  local output="$TMP_DIR/$label.out"
  if bash "$AUDIT_SCRIPT" "$repo" >"$output" 2>&1; then
    echo "[branch-flow-audit-test] ERROR: expected failure for $label" >&2
    exit 1
  fi
  if ! grep -q "ERROR" "$output"; then
    echo "[branch-flow-audit-test] ERROR: $label failed without an audit error" >&2
    cat "$output" >&2
    exit 1
  fi
}

VALID_REPO="$TMP_DIR/valid"
write_valid_repo "$VALID_REPO"
expect_pass "$VALID_REPO"

JENKINS_STAGING_REPO="$TMP_DIR/jenkins-staging"
write_valid_repo "$JENKINS_STAGING_REPO"
cat > "$JENKINS_STAGING_REPO/Jenkinsfile" <<'EOF'
def pipeline = new org.android.AppPipeline(
    uploadToNexusFor: ['master','develop','staging']
)
EOF
expect_fail "$JENKINS_STAGING_REPO" "jenkins-staging"

WORKFLOW_STAGING_REPO="$TMP_DIR/workflow-staging"
write_valid_repo "$WORKFLOW_STAGING_REPO"
cat > "$WORKFLOW_STAGING_REPO/.github/workflows/android-ci.yml" <<'EOF'
name: Android CI
on:
  push:
    branches: [ master, develop, staging ]
EOF
expect_fail "$WORKFLOW_STAGING_REPO" "workflow-staging"

MISSING_UPLOAD_REPO="$TMP_DIR/missing-upload"
write_valid_repo "$MISSING_UPLOAD_REPO"
cat > "$MISSING_UPLOAD_REPO/Jenkinsfile" <<'EOF'
def pipeline = new org.android.AppPipeline(
    publishCmd: 'publishReleaseApk'
)
EOF
expect_fail "$MISSING_UPLOAD_REPO" "missing-upload"

echo "[branch-flow-audit-test] Branch flow audit self-test passed."
