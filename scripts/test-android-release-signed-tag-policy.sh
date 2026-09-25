#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORKFLOW="$ROOT_DIR/.github/workflows/android-release.yml"
VERIFY_SIGNATURE="$ROOT_DIR/scripts/verify-android-release-tag-signature.sh"
STEP_NAME="Validate signed tag, master identity, and source version"
RELEASE_TAG="v4.2.0+android.230"
EXPECTED_TAGGER_EMAIL="release-test@example.invalid"
ATTACKER_TAGGER_EMAIL="replacement@example.invalid"

mkdir -p "$ROOT_DIR/build/test-tmp"
tmp_dir="$(
  mktemp -d "$ROOT_DIR/build/test-tmp/android-signed-tag-policy.XXXXXX"
)"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-release-signed-tag-policy-test][error] $*" >&2
  exit 1
}

for command_name in awk base64 git jq sha256sum ssh-keygen; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
[[ -f "$VERIFY_SIGNATURE" && ! -L "$VERIFY_SIGNATURE" ]] ||
  fail "release-tag signature verifier is missing or unsafe"

generate_signing_key() {
  local private_key="$1"
  local identity="$2"
  ssh-keygen \
    -q \
    -t ed25519 \
    -N '' \
    -C "$identity" \
    -f "$private_key"
}

signer_fingerprint() {
  local public_key="$1"
  ssh-keygen -l -E sha256 -f "$public_key" |
    awk 'NF >= 2 { print $2 }'
}

public_key_b64() {
  local public_key="$1"
  base64 <"$public_key" | tr -d '\n'
}

APPROVED_PRIVATE_KEY="$tmp_dir/approved-ed25519"
ATTACKER_PRIVATE_KEY="$tmp_dir/attacker-ed25519"
generate_signing_key \
  "$APPROVED_PRIVATE_KEY" \
  "Fearless Approved Release Test"
generate_signing_key \
  "$ATTACKER_PRIVATE_KEY" \
  "Fearless Replacement Release Test"
APPROVED_FINGERPRINT="$(signer_fingerprint "$APPROVED_PRIVATE_KEY.pub")"
ATTACKER_FINGERPRINT="$(signer_fingerprint "$ATTACKER_PRIVATE_KEY.pub")"
APPROVED_PUBLIC_KEY_B64="$(public_key_b64 "$APPROVED_PRIVATE_KEY.pub")"
ATTACKER_PUBLIC_KEY_B64="$(public_key_b64 "$ATTACKER_PRIVATE_KEY.pub")"
[[ "$APPROVED_FINGERPRINT" =~ ^SHA256:[A-Za-z0-9+/]{43}$ &&
  "$ATTACKER_FINGERPRINT" =~ ^SHA256:[A-Za-z0-9+/]{43}$ &&
  "$APPROVED_FINGERPRINT" != "$ATTACKER_FINGERPRINT" ]] ||
  fail "could not create distinct SSH signer fixtures"

[[ "$(grep -Fxc "      - name: $STEP_NAME" "$WORKFLOW")" == "1" ]] ||
  fail "workflow must contain exactly one '$STEP_NAME' step"

run_block="$tmp_dir/validate-release-tag.sh"
awk -v step_name="$STEP_NAME" '
  $0 == "      - name: " step_name {
    in_step = 1
    next
  }
  in_step && $0 == "        run: |" {
    in_run = 1
    found_run = 1
    next
  }
  in_run && /^      - name:/ {
    exit
  }
  in_run {
    if ($0 == "") {
      print ""
      next
    }
    if (substr($0, 1, 10) != "          ") {
      print "unexpected workflow run-block indentation: " $0 > "/dev/stderr"
      extraction_failed = 1
      exit
    }
    print substr($0, 11)
  }
  END {
    if (!found_run || extraction_failed) {
      exit 42
    }
  }
' "$WORKFLOW" >"$run_block" ||
  fail "unable to extract the exact '$STEP_NAME' run block"

[[ -s "$run_block" ]] || fail "extracted workflow run block is empty"
[[ "$(sed -n '1p' "$run_block")" == "set -euo pipefail" ]] ||
  fail "extracted workflow run block has an unexpected first command"

fake_bin="$tmp_dir/fake-bin"
mkdir -p "$fake_bin"
fake_gh="$fake_bin/gh"
cat >"$fake_gh" <<'FAKE_GH'
#!/usr/bin/env bash
set -euo pipefail

[[ "${1:-}" == "api" && "$#" == "2" ]] || {
  echo "unexpected fake gh invocation: $*" >&2
  exit 64
}

case "$2" in
  "/repos/$GITHUB_REPOSITORY/git/ref/tags/$RELEASE_TAG_INPUT")
    printf '%s\n' "$FAKE_GH_REF_JSON"
    ;;
  "/repos/$GITHUB_REPOSITORY/git/tags/$FAKE_GH_TAG_ENDPOINT_SHA")
    printf '%s\n' "$FAKE_GH_TAG_JSON"
    ;;
  *)
    echo "unexpected fake gh endpoint: $2" >&2
    exit 65
    ;;
esac
FAKE_GH
chmod 700 "$fake_gh"

CASE_REPO=""
CASE_TAG_OBJECT=""
CASE_RELEASE_COMMIT=""
CASE_RELEASE_TREE=""
CASE_MASTER_COMMIT=""
CASE_GITHUB_ENV=""
CASE_GITHUB_OUTPUT=""

create_fixture() {
  local case_name="$1"
  local version_name="$2"
  local tag_kind="$3"
  local placement="$4"
  local signer_mode="$5"
  local case_dir="$tmp_dir/$case_name"
  local origin="$case_dir/origin.git"
  local repo="$case_dir/repo"

  mkdir -p "$case_dir"
  git init --bare --quiet "$origin"
  git init --quiet --initial-branch=master "$repo"
  git -C "$repo" config user.name "Fearless Release Test"
  git -C "$repo" config user.email "release-test@example.invalid"
  git -C "$repo" config commit.gpgSign false
  git -C "$repo" config tag.gpgSign false
  git -C "$repo" remote add origin "$origin"
  mkdir -p "$repo/versioning"
  mkdir -p "$repo/scripts" "$repo/.github/release"
  cp "$VERIFY_SIGNATURE" \
    "$repo/scripts/verify-android-release-tag-signature.sh"
  chmod 0755 "$repo/scripts/verify-android-release-tag-signature.sh"
  printf '%s\n' "$APPROVED_FINGERPRINT" \
    >"$repo/.github/release/android-tag-signer-fingerprints.txt"
  printf 'versionName=%s\nversionCode=230\n' "$version_name" \
    >"$repo/versioning/version.properties"
  git -C "$repo" add \
    versioning/version.properties \
    scripts/verify-android-release-tag-signature.sh \
    .github/release/android-tag-signer-fingerprints.txt
  git -C "$repo" commit --quiet -m "released source"
  git -C "$repo" push --quiet --set-upstream origin master

  if [[ "$placement" == "off-master" ]]; then
    git -C "$repo" switch --quiet --create release-off-master
    printf 'off-master\n' >"$repo/off-master-marker"
    git -C "$repo" add off-master-marker
    git -C "$repo" commit --quiet -m "off-master release candidate"
  elif [[ "$placement" != "master" && "$placement" != "historical" ]]; then
    fail "unknown fixture placement: $placement"
  fi

  case "$tag_kind" in
    annotated)
      case "$signer_mode" in
        approved)
          git -C "$repo" \
            -c gpg.format=ssh \
            -c "user.signingkey=$APPROVED_PRIVATE_KEY" \
            tag --sign "$RELEASE_TAG" \
            --message "Fearless Android release"
          ;;
        attacker)
          git -C "$repo" config user.email "$ATTACKER_TAGGER_EMAIL"
          git -C "$repo" \
            -c gpg.format=ssh \
            -c "user.signingkey=$ATTACKER_PRIVATE_KEY" \
            tag --sign "$RELEASE_TAG" \
            --message "Fearless Android release"
          ;;
        unsigned)
          git -C "$repo" tag --annotate "$RELEASE_TAG" \
            --message "Fearless Android release"
          ;;
        *) fail "unknown annotated-tag signer mode: $signer_mode" ;;
      esac
      ;;
    lightweight)
      [[ "$signer_mode" == "none" ]] ||
        fail "lightweight fixture cannot use signer mode: $signer_mode"
      git -C "$repo" tag "$RELEASE_TAG"
      ;;
    *)
      fail "unknown fixture tag kind: $tag_kind"
      ;;
  esac
  git -C "$repo" push --quiet origin "refs/tags/$RELEASE_TAG"
  if [[ "$placement" == "historical" ]]; then
    git -C "$repo" switch --quiet master
    printf 'newer master\n' > "$repo/newer-master-marker"
    git -C "$repo" add newer-master-marker
    git -C "$repo" commit --quiet -m "newer protected master"
    git -C "$repo" push --quiet origin master
  fi
  git -C "$repo" checkout --quiet --detach "$RELEASE_TAG"

  CASE_REPO="$repo"
  CASE_TAG_OBJECT="$(
    git -C "$repo" rev-parse "refs/tags/$RELEASE_TAG"
  )"
  CASE_RELEASE_COMMIT="$(
    git -C "$repo" rev-parse "refs/tags/$RELEASE_TAG^{commit}"
  )"
  CASE_RELEASE_TREE="$(
    git -C "$repo" rev-parse "$CASE_RELEASE_COMMIT^{tree}"
  )"
  CASE_MASTER_COMMIT="$(
    git -C "$repo" rev-parse refs/remotes/origin/master
  )"
  CASE_GITHUB_ENV="$case_dir/github-env"
  CASE_GITHUB_OUTPUT="$case_dir/github-output"
  : >"$CASE_GITHUB_ENV"
  : >"$CASE_GITHUB_OUTPUT"
}

positive_count=0
negative_count=0

run_case() {
  local case_name="$1"
  local expected_outcome="$2"
  local expected_diagnostic="$3"
  local version_name="$4"
  local tag_kind="$5"
  local placement="$6"
  local signer_mode="$7"
  local api_ref_type="$8"
  local api_ref_sha_mode="$9"
  shift 9
  local api_tag_mode="$1"
  local api_target_mode="$2"
  local api_verified="$3"
  local api_reason="$4"
  local api_tagger_mode="${5:-local}"
  local expected_tagger_mode="${6:-local}"
  local public_key_mode="${7:-approved}"
  local api_ref_sha="$CASE_TAG_OBJECT"
  local api_tag="$RELEASE_TAG"
  local api_target="$CASE_RELEASE_COMMIT"
  local api_tagger_email="$EXPECTED_TAGGER_EMAIL"
  local expected_tagger_email="$EXPECTED_TAGGER_EMAIL"
  local signer_public_key_b64="$APPROVED_PUBLIC_KEY_B64"
  local ref_json
  local tag_json
  local output
  local dispatch_commit

  create_fixture \
    "$case_name" \
    "$version_name" \
    "$tag_kind" \
    "$placement" \
    "$signer_mode"
  dispatch_commit="$CASE_RELEASE_COMMIT"
  if [[ "$placement" == "historical" ]]; then
    dispatch_commit="$CASE_MASTER_COMMIT"
  fi

  case "$api_ref_sha_mode" in
    local) api_ref_sha="$CASE_TAG_OBJECT" ;;
    mismatch) api_ref_sha="0000000000000000000000000000000000000000" ;;
    *) fail "unknown API ref SHA mode: $api_ref_sha_mode" ;;
  esac
  case "$api_tag_mode" in
    input) api_tag="$RELEASE_TAG" ;;
    mismatch) api_tag="v9.9.9+android.999" ;;
    *) fail "unknown API tag mode: $api_tag_mode" ;;
  esac
  case "$api_target_mode" in
    local) api_target="$CASE_RELEASE_COMMIT" ;;
    mismatch) api_target="1111111111111111111111111111111111111111" ;;
    *) fail "unknown API target mode: $api_target_mode" ;;
  esac
  case "$api_tagger_mode" in
    local) api_tagger_email="$EXPECTED_TAGGER_EMAIL" ;;
    mismatch) api_tagger_email="unapproved@example.invalid" ;;
    attacker) api_tagger_email="$ATTACKER_TAGGER_EMAIL" ;;
    *) fail "unknown API tagger mode: $api_tagger_mode" ;;
  esac
  case "$expected_tagger_mode" in
    local) expected_tagger_email="$EXPECTED_TAGGER_EMAIL" ;;
    attacker) expected_tagger_email="$ATTACKER_TAGGER_EMAIL" ;;
    *) fail "unknown expected tagger mode: $expected_tagger_mode" ;;
  esac
  case "$public_key_mode" in
    approved) signer_public_key_b64="$APPROVED_PUBLIC_KEY_B64" ;;
    attacker) signer_public_key_b64="$ATTACKER_PUBLIC_KEY_B64" ;;
    *) fail "unknown signer public-key mode: $public_key_mode" ;;
  esac

  ref_json="$(
    jq -cn \
      --arg ref "refs/tags/$RELEASE_TAG" \
      --arg type "$api_ref_type" \
      --arg sha "$api_ref_sha" \
      '{ref: $ref, object: {type: $type, sha: $sha}}'
  )"
  tag_json="$(
    jq -cn \
      --arg tag "$api_tag" \
      --arg target "$api_target" \
      --arg tagger_email "$api_tagger_email" \
      --argjson verified "$api_verified" \
      --arg reason "$api_reason" \
      '{
        tag: $tag,
        object: {type: "commit", sha: $target},
        tagger: {email: $tagger_email},
        verification: {verified: $verified, reason: $reason}
      }'
  )"
  output="$tmp_dir/$case_name/output.log"
  mkdir -p "$(dirname "$output")"

  if (
    cd "$CASE_REPO"
    PATH="$fake_bin:$PATH" \
      GH_TOKEN="fake-token" \
      GITHUB_REPOSITORY="soramitsu/fearless-Android" \
      GITHUB_ENV="$CASE_GITHUB_ENV" \
      GITHUB_OUTPUT="$CASE_GITHUB_OUTPUT" \
      DISPATCH_COMMIT="$dispatch_commit" \
      EXPECTED_RELEASE_TAGGER_EMAIL="$expected_tagger_email" \
      ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64="$signer_public_key_b64" \
      RELEASE_TAG_INPUT="$RELEASE_TAG" \
      FAKE_GH_REF_JSON="$ref_json" \
      FAKE_GH_TAG_JSON="$tag_json" \
      FAKE_GH_TAG_ENDPOINT_SHA="$api_ref_sha" \
      bash "$run_block"
  ) >"$output" 2>&1; then
    if [[ "$expected_outcome" != "success" ]]; then
      fail "$case_name unexpectedly passed"
    fi

    [[ ! -s "$CASE_GITHUB_ENV" ]] ||
      fail "$case_name leaked release identity into mutable job environment"
    grep -Fxq "release_commit=$CASE_RELEASE_COMMIT" "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output the exact release commit"
    grep -Fxq "release_tag_object=$CASE_TAG_OBJECT" "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output the exact release tag object"
    grep -Fxq "master_commit=$CASE_MASTER_COMMIT" "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output the exact master commit"
    grep -Fxq "release_tree=$CASE_RELEASE_TREE" "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output the exact release tree"
    grep -Fxq "version_name=4.2.0" "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output the exact version name"
    grep -Fxq "version_code=230" "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output the exact version code"
    grep -Eq '^version_properties_sha256=[0-9a-f]{64}$' \
      "$CASE_GITHUB_OUTPUT" ||
      fail "$case_name did not output a valid source-version digest"
    [[ "$(wc -l <"$CASE_GITHUB_OUTPUT" | tr -d ' ')" == "7" ]] ||
      fail "$case_name output an unexpected release metadata entry"
    positive_count=$((positive_count + 1))
    return
  fi

  if [[ "$expected_outcome" == "success" ]]; then
    sed -n '1,160p' "$output" >&2
    fail "$case_name unexpectedly failed"
  fi
  [[ ! -s "$CASE_GITHUB_ENV" ]] ||
    fail "$case_name exported partial release state before failing"
  [[ ! -s "$CASE_GITHUB_OUTPUT" ]] ||
    fail "$case_name output partial release state before failing"
  if [[ "$expected_diagnostic" != "-" ]]; then
    grep -Fq "$expected_diagnostic" "$output" || {
      sed -n '1,160p' "$output" >&2
      fail "$case_name did not emit the expected diagnostic: $expected_diagnostic"
    }
  fi
  negative_count=$((negative_count + 1))
}

run_case \
  valid-annotated-api-evidence success - \
  4.2.0 annotated master approved tag local input local true valid
run_case \
  lightweight-tag failure \
  "Release tag must be an annotated signed tag" \
  4.2.0 lightweight master none tag local input local true valid
run_case \
  unsigned-annotated-tag failure \
  "Release tag must contain exactly one SSH signature" \
  4.2.0 annotated master unsigned tag local input local true valid
run_case \
  api-ref-type-mismatch failure - \
  4.2.0 annotated master approved commit local input local true valid
run_case \
  api-ref-sha-mismatch failure \
  "Local and GitHub release tag objects do not match" \
  4.2.0 annotated master approved tag mismatch input local true valid
run_case \
  api-tag-name-mismatch failure \
  "GitHub did not verify a valid signature on the release tag" \
  4.2.0 annotated master approved tag local mismatch local true valid
run_case \
  api-target-commit-mismatch failure \
  "GitHub did not verify a valid signature on the release tag" \
  4.2.0 annotated master approved tag local input mismatch true valid
run_case \
  api-unverified-signature failure \
  "GitHub did not verify a valid signature on the release tag" \
  4.2.0 annotated master approved tag local input local false valid
run_case \
  api-invalid-signature-reason failure \
  "GitHub did not verify a valid signature on the release tag" \
  4.2.0 annotated master approved tag local input local true unsigned
run_case \
  api-unapproved-tagger-email failure \
  "GitHub did not verify a valid signature on the release tag" \
  4.2.0 annotated master approved tag local input local true valid mismatch
run_case \
  repo-variable-and-signer-replaced failure \
  "Signer public key does not match the source-controlled fingerprint" \
  4.2.0 annotated master attacker tag local input local true valid \
  attacker attacker attacker
run_case \
  source-version-tag-mismatch failure \
  "release_tag must be exactly v4.2.1+android.230" \
  4.2.1 annotated master approved tag local input local true valid
run_case \
  off-master-tag failure \
  "Release tag, checkout, dispatch SHA, and current master must be identical" \
  4.2.0 annotated off-master approved tag local input local true valid
run_case \
  historical-ancestor-tag failure \
  "Release tag, checkout, dispatch SHA, and current master must be identical" \
  4.2.0 annotated historical approved tag local input local true valid

[[ "$positive_count" == "1" ]] ||
  fail "expected exactly 1 positive case; got $positive_count"
[[ "$negative_count" == "13" ]] ||
  fail "expected exactly 13 negative cases; got $negative_count"

echo \
  "[android-release-signed-tag-policy-test] $positive_count positive + $negative_count negative cases passed"
