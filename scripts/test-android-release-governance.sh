#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VERIFIER="$ROOT_DIR/scripts/verify-android-release-governance.sh"
mkdir -p "$ROOT_DIR/build/test-tmp"
tmp_dir="$(mktemp -d "$ROOT_DIR/build/test-tmp/android-release-governance.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-release-governance-test][error] $*" >&2
  exit 1
}

repository="soramitsu/fearless-Android"

environment_fixture() {
  local name="$1"
  shift
  local reviewers="[]"
  local reviewer_id

  for reviewer_id in "$@"; do
    reviewers="$(
      jq -cn \
        --argjson existing "$reviewers" \
        --argjson reviewer_id "$reviewer_id" \
        '$existing + [{
          type: "User",
          reviewer: {id: $reviewer_id, type: "User"}
        }]'
    )"
  done
  jq -cn \
    --arg name "$name" \
    --argjson reviewers "$reviewers" '{
      name: $name,
      can_admins_bypass: false,
      protection_rules: [
        {
          type: "required_reviewers",
          prevent_self_review: true,
          reviewers: $reviewers
        },
        {type: "branch_policy"}
      ],
      deployment_branch_policy: {
        protected_branches: true,
        custom_branch_policies: false
      }
    }'
}

valid_build_environment="$(environment_fixture android-release-build 1 7)"
valid_signing_environment="$(environment_fixture android-release-signing 2 9)"
valid_single_build_environment="$(environment_fixture android-release-build 11)"
valid_single_signing_environment="$(environment_fixture android-release-signing 12)"
valid_numeric_sort_build_environment="$(environment_fixture android-release-build 2 10)"
valid_numeric_sort_signing_environment="$(environment_fixture android-release-signing 3 11)"
valid_rulesets="$(
  jq -cn --arg repository "$repository" '[{
    id: 19780597,
    name: "Immutable Android release tags",
    target: "tag",
    source_type: "Repository",
    source: $repository,
    enforcement: "active",
    bypass_actors: [],
    conditions: {
      ref_name: {
        include: ["refs/tags/v*+android.*"],
        exclude: []
      }
    },
    rules: [
      {type: "update"},
      {type: "deletion"},
      {type: "non_fast_forward"}
    ]
  }]'
)"

positive_count=0
negative_count=0

run_case() {
  local case_name="$1"
  local expectation="$2"
  local expected_message="$3"
  local build_environment="$4"
  local signing_environment="$5"
  local rulesets="$6"
  local build_ids="1,7"
  local signing_ids="2,9"
  local output="$tmp_dir/$case_name.log"

  if (( $# >= 7 )); then
    build_ids="$7"
  fi
  if (( $# >= 8 )); then
    signing_ids="$8"
  fi

  if env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    GITHUB_REPOSITORY="$repository" \
    EXPECTED_ANDROID_RELEASE_BUILD_REVIEWER_IDS="$build_ids" \
    EXPECTED_ANDROID_RELEASE_SIGNING_REVIEWER_IDS="$signing_ids" \
    ANDROID_RELEASE_GOVERNANCE_TEST_MODE=true \
    ANDROID_RELEASE_GOVERNANCE_BUILD_ENVIRONMENT_JSON="$build_environment" \
    ANDROID_RELEASE_GOVERNANCE_SIGNING_ENVIRONMENT_JSON="$signing_environment" \
    ANDROID_RELEASE_GOVERNANCE_RULESETS_JSON="$rulesets" \
    "$VERIFIER" >"$output" 2>&1; then
    [[ "$expectation" == "success" ]] ||
      fail "$case_name unexpectedly passed"
    grep -Fq \
      "disjoint protected build/signing environments and immutable release-tag ruleset verified" \
      "$output" ||
      fail "$case_name omitted its success evidence"
    positive_count=$((positive_count + 1))
  else
    [[ "$expectation" == "failure" ]] || {
      sed -n '1,160p' "$output" >&2
      fail "$case_name unexpectedly failed"
    }
    grep -Fq "$expected_message" "$output" || {
      sed -n '1,160p' "$output" >&2
      fail "$case_name omitted its fail-closed diagnostic"
    }
    negative_count=$((negative_count + 1))
  fi
}

environment_error() {
  printf '%s must deny admin bypass and self-review, require only approved User reviewers, and allow protected branches only.' "$1"
}

build_environment_error="$(environment_error android-release-build)"
signing_environment_error="$(environment_error android-release-signing)"
build_allowlist_error="android-release-build reviewers do not match the exact approved User allowlist."
signing_allowlist_error="android-release-signing reviewers do not match the exact approved User allowlist."
ruleset_error="Release tags must be covered by the exact active no-bypass immutability ruleset."

run_case valid-multiple success "" \
  "$valid_build_environment" "$valid_signing_environment" "$valid_rulesets"
run_case valid-single success "" \
  "$valid_single_build_environment" "$valid_single_signing_environment" \
  "$valid_rulesets" "11" "12"
run_case valid-numeric-sort success "" \
  "$valid_numeric_sort_build_environment" \
  "$valid_numeric_sort_signing_environment" \
  "$valid_rulesets" "2,10" "3,11"

run_case build-admin-bypass failure "$build_environment_error" \
  "$(jq -c '.can_admins_bypass = true' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-admin-bypass failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '.can_admins_bypass = true' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-self-review-allowed failure "$build_environment_error" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .prevent_self_review) = false' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-self-review-allowed failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .prevent_self_review) = false' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-empty-reviewers failure "$build_environment_error" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers) = []' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-team-reviewer failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].type) = "Team"' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-bot-reviewer failure "$build_environment_error" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].type) = "Bot"' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-nested-team-reviewer failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].reviewer.type) = "Team"' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case signing-string-id failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].reviewer.id) = "2"' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-zero-id failure "$build_environment_error" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].reviewer.id) = 0' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-fractional-id failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].reviewer.id) = 2.5' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-duplicate-reviewer failure "$build_environment_error" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[1].reviewer.id) = 1' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-missing-reviewer-type failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c 'del(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[0].type)' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-unapproved-reviewer failure "$build_allowlist_error" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[1].reviewer.id) = 8' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-unapproved-reviewer failure "$signing_allowlist_error" \
  "$valid_build_environment" \
  "$(jq -c '(.protection_rules[] | select(.type == "required_reviewers") | .reviewers[1].reviewer.id) = 10' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-wrong-name failure "$build_environment_error" \
  "$(jq -c '.name = "android-release-signing"' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-no-reviewer-rule failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '.protection_rules |= map(select(.type != "required_reviewers"))' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-duplicate-reviewer-rule failure "$build_environment_error" \
  "$(jq -c '.protection_rules += [.protection_rules[0]]' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-no-branch-policy failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '.protection_rules |= map(select(.type != "branch_policy"))' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-extra-wait-timer failure "$build_environment_error" \
  "$(jq -c '.protection_rules += [{type:"wait_timer",wait_timer:1}]' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-unprotected-branches failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c '.deployment_branch_policy.protected_branches = false' <<<"$valid_signing_environment")" \
  "$valid_rulesets"
run_case build-custom-branches failure "$build_environment_error" \
  "$(jq -c '.deployment_branch_policy.custom_branch_policies = true' <<<"$valid_build_environment")" \
  "$valid_signing_environment" "$valid_rulesets"
run_case signing-missing-deployment-policy failure "$signing_environment_error" \
  "$valid_build_environment" \
  "$(jq -c 'del(.deployment_branch_policy)' <<<"$valid_signing_environment")" \
  "$valid_rulesets"

run_case overlapping-reviewer-sets failure \
  "Android release build and signing reviewer allowlists must be disjoint." \
  "$valid_build_environment" \
  "$(environment_fixture android-release-signing 7 9)" \
  "$valid_rulesets" "1,7" "7,9"
run_case empty-build-allowlist failure \
  "ANDROID_RELEASE_BUILD_REVIEWER_IDS must be a nonempty comma-separated list of numeric User IDs." \
  "$valid_build_environment" "$valid_signing_environment" "$valid_rulesets" "" "2,9"
run_case malformed-signing-allowlist failure \
  "ANDROID_RELEASE_SIGNING_REVIEWER_IDS must be a nonempty comma-separated list of numeric User IDs." \
  "$valid_build_environment" "$valid_signing_environment" "$valid_rulesets" "1,7" "2,team"
run_case duplicate-build-allowlist failure \
  "ANDROID_RELEASE_BUILD_REVIEWER_IDS must be unique and numerically sorted." \
  "$valid_build_environment" "$valid_signing_environment" "$valid_rulesets" "1,1,7" "2,9"
run_case unsorted-signing-allowlist failure \
  "ANDROID_RELEASE_SIGNING_REVIEWER_IDS must be unique and numerically sorted." \
  "$valid_build_environment" "$valid_signing_environment" "$valid_rulesets" "1,7" "9,2"
run_case malformed-build-json failure "$build_environment_error" \
  '{"not":"an-environment"}' "$valid_signing_environment" "$valid_rulesets"
run_case malformed-signing-json failure "$signing_environment_error" \
  "$valid_build_environment" '{"not":"an-environment"}' "$valid_rulesets"

run_case no-ruleset failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" '[]'
run_case disabled-ruleset failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].enforcement = "disabled"' <<<"$valid_rulesets")"
run_case wrong-ruleset-name failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].name = "Almost immutable Android tags"' <<<"$valid_rulesets")"
run_case branch-ruleset failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].target = "branch"' <<<"$valid_rulesets")"
run_case bypass-actor failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].bypass_actors = [{actor_type:"RepositoryRole",actor_id:5,bypass_mode:"always"}]' <<<"$valid_rulesets")"
run_case broad-tag-pattern failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].conditions.ref_name.include = ["~ALL"]' <<<"$valid_rulesets")"
run_case excluded-release failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].conditions.ref_name.exclude = ["refs/tags/v4*"]' <<<"$valid_rulesets")"
run_case missing-deletion failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].rules |= map(select(.type != "deletion"))' <<<"$valid_rulesets")"
run_case missing-non-fast-forward failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].rules |= map(select(.type != "non_fast_forward"))' <<<"$valid_rulesets")"
run_case extra-rule failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].rules += [{type:"creation"}]' <<<"$valid_rulesets")"
run_case wrong-source failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '.[0].source = "attacker/fork"' <<<"$valid_rulesets")"
run_case malformed-rulesets failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  '{"not":"an-array"}'
run_case duplicate-valid-rulesets failure "$ruleset_error" \
  "$valid_build_environment" "$valid_signing_environment" \
  "$(jq -c '. + .' <<<"$valid_rulesets")"

[[ "$positive_count" == "3" ]] ||
  fail "expected 3 positive cases; got $positive_count"
[[ "$negative_count" == "43" ]] ||
  fail "expected 43 negative cases; got $negative_count"

echo \
  "[android-release-governance-test] $positive_count positive + $negative_count negative cases passed"
