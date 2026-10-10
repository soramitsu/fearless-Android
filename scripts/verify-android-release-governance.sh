#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "[android-release-governance][error] $*" >&2
  exit 1
}

canonical_reviewer_ids() {
  local value="$1"

  [[ "$value" =~ ^[1-9][0-9]*(,[1-9][0-9]*)*$ ]] || return 1
  tr ',' '\n' <<<"$value" |
    sort -n -u |
    paste -sd, -
}

validate_environment() {
  local expected_name="$1"
  local expected_ids="$2"
  local environment="$3"
  local actual_ids

  jq -e \
    --arg expected_name "$expected_name" '
      type == "object" and
      .name == $expected_name and
      .can_admins_bypass == false and
      (.protection_rules | type == "array") and
      ([.protection_rules[] | select(.type == "required_reviewers")] | length) == 1 and
      ([.protection_rules[] | select(.type == "branch_policy")] | length) == 1 and
      ([.protection_rules[] | .type] | sort) ==
        ["branch_policy", "required_reviewers"] and
      (
        .protection_rules[] |
        select(.type == "required_reviewers") |
        .prevent_self_review == true and
        (.reviewers | type == "array" and length > 0) and
        all(
          .reviewers[];
          type == "object" and
          .type == "User" and
          (.reviewer | type == "object") and
          .reviewer.type == "User" and
          (.reviewer.id | type == "number" and . > 0 and floor == .)
        ) and
        ([.reviewers[].reviewer.id] | length) ==
          ([.reviewers[].reviewer.id] | unique | length)
      ) and
      (.deployment_branch_policy | type == "object") and
      .deployment_branch_policy.protected_branches == true and
      .deployment_branch_policy.custom_branch_policies == false
    ' <<<"$environment" >/dev/null 2>&1 ||
    fail "$expected_name must deny admin bypass and self-review, require only approved User reviewers, and allow protected branches only."

  actual_ids="$(
    jq -er '
      [
        .protection_rules[] |
        select(.type == "required_reviewers") |
        .reviewers[].reviewer.id
      ] |
      sort |
      map(tostring) |
      join(",")
    ' <<<"$environment"
  )" ||
    fail "$expected_name contains malformed reviewer identities."
  [[ "$actual_ids" == "$expected_ids" ]] ||
    fail "$expected_name reviewers do not match the exact approved User allowlist."
}

repository="${GITHUB_REPOSITORY:-}"
[[ "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] ||
  fail "GITHUB_REPOSITORY is missing or malformed."

expected_build_ids="${EXPECTED_ANDROID_RELEASE_BUILD_REVIEWER_IDS:-}"
canonical_build_ids="$(canonical_reviewer_ids "$expected_build_ids")" ||
  fail "ANDROID_RELEASE_BUILD_REVIEWER_IDS must be a nonempty comma-separated list of numeric User IDs."
[[ "$canonical_build_ids" == "$expected_build_ids" ]] ||
  fail "ANDROID_RELEASE_BUILD_REVIEWER_IDS must be unique and numerically sorted."

expected_signing_ids="${EXPECTED_ANDROID_RELEASE_SIGNING_REVIEWER_IDS:-}"
canonical_signing_ids="$(canonical_reviewer_ids "$expected_signing_ids")" ||
  fail "ANDROID_RELEASE_SIGNING_REVIEWER_IDS must be a nonempty comma-separated list of numeric User IDs."
[[ "$canonical_signing_ids" == "$expected_signing_ids" ]] ||
  fail "ANDROID_RELEASE_SIGNING_REVIEWER_IDS must be unique and numerically sorted."

overlapping_ids="$(
  {
    tr ',' '\n' <<<"$expected_build_ids"
    tr ',' '\n' <<<"$expected_signing_ids"
  } |
    sort -n |
    uniq -d |
    paste -sd, -
)"
[[ -z "$overlapping_ids" ]] ||
  fail "Android release build and signing reviewer allowlists must be disjoint."

if [[ "${ANDROID_RELEASE_GOVERNANCE_TEST_MODE:-}" == "true" ]]; then
  [[ "${CI:-}" == "true" ]] ||
    fail "Governance fixtures are restricted to explicit CI tests."
  build_environment="${ANDROID_RELEASE_GOVERNANCE_BUILD_ENVIRONMENT_JSON:-}"
  signing_environment="${ANDROID_RELEASE_GOVERNANCE_SIGNING_ENVIRONMENT_JSON:-}"
  rulesets="${ANDROID_RELEASE_GOVERNANCE_RULESETS_JSON:-}"
else
  command -v gh >/dev/null 2>&1 || fail "gh is required."
  build_environment="$(
    gh api \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "/repos/$repository/environments/android-release-build"
  )" || fail "Unable to read the android-release-build environment."
  signing_environment="$(
    gh api \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "/repos/$repository/environments/android-release-signing"
  )" || fail "Unable to read the android-release-signing environment."

  ruleset_ids="$(
    gh api \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "/repos/$repository/rulesets?includes_parents=true&targets=tag&per_page=100" |
      jq -r '.[]?.id | select(type == "number")'
  )" || fail "Unable to list Android tag rulesets."
  rulesets="[]"
  while IFS= read -r ruleset_id; do
    [[ -z "$ruleset_id" ]] && continue
    [[ "$ruleset_id" =~ ^[1-9][0-9]*$ ]] ||
      fail "GitHub returned a malformed ruleset identifier."
    ruleset="$(
      gh api \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "/repos/$repository/rulesets/$ruleset_id?includes_parents=true"
    )" || fail "Unable to read Android tag ruleset $ruleset_id."
    rulesets="$(
      jq -cn \
        --argjson existing "$rulesets" \
        --argjson next "$ruleset" \
        '$existing + [$next]'
    )" || fail "GitHub returned malformed tag-ruleset JSON."
  done <<<"$ruleset_ids"
fi

validate_environment \
  "android-release-build" \
  "$expected_build_ids" \
  "$build_environment"
validate_environment \
  "android-release-signing" \
  "$expected_signing_ids" \
  "$signing_environment"

jq -e \
  --arg repository "$repository" '
    type == "array" and
    ([.[] | select(
      .name == "Immutable Android release tags" and
      .target == "tag" and
      .source_type == "Repository" and
      .source == $repository and
      .enforcement == "active" and
      .bypass_actors == [] and
      .conditions.ref_name.include == ["refs/tags/v*+android.*"] and
      .conditions.ref_name.exclude == [] and
      ([.rules[]?.type] | sort) ==
        ["deletion", "non_fast_forward", "update"]
    )] | length) == 1
  ' <<<"$rulesets" >/dev/null 2>&1 ||
  fail "Release tags must be covered by the exact active no-bypass immutability ruleset."

echo \
  "[android-release-governance] disjoint protected build/signing environments and immutable release-tag ruleset verified"
