#!/usr/bin/env bash
set -euo pipefail

MAX_ALLOWLIST_BYTES=16384
MAX_PUBLIC_KEY_B64_CHARS=16384
MAX_PUBLIC_KEY_BYTES=8192
SIGNER_PRINCIPAL="fearless-android-release"

fail() {
  echo "[android-release-tag-signature][error] $*" >&2
  exit 1
}

usage() {
  echo \
    "Usage: scripts/verify-android-release-tag-signature.sh <release-tag> <tag-object-sha> <commit-sha>" \
    >&2
}

[[ "$#" -eq 3 ]] || {
  usage
  exit 2
}

release_tag="$1"
expected_tag_object="$2"
expected_commit="$3"

[[ "$release_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?\+android\.[1-9][0-9]*$ ]] ||
  fail "Release tag is malformed."
[[ "$expected_tag_object" =~ ^[0-9a-f]{40}$ ]] ||
  fail "Expected tag-object SHA is malformed."
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "Expected release-commit SHA is malformed."

for command_name in awk git grep mktemp python3 ssh-keygen tr wc; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "Required command is unavailable: $command_name"
done

repository_root="$(git rev-parse --show-toplevel 2>/dev/null)" ||
  fail "The release tag must be verified inside a Git worktree."
repository_root="$(cd "$repository_root" && pwd -P)"
allowlist="$repository_root/.github/release/android-tag-signer-fingerprints.txt"
[[ "$allowlist" != *$'\n'* && "$allowlist" != *$'\r'* ]] ||
  fail "Signer-allowlist path is malformed."
[[ -f "$allowlist" && ! -L "$allowlist" && -s "$allowlist" ]] ||
  fail "Signer allowlist must be a non-empty regular, non-symlink file."
allowlist_bytes="$(wc -c <"$allowlist" | tr -d '[:space:]')"
[[ "$allowlist_bytes" =~ ^[1-9][0-9]*$ ]] ||
  fail "Signer-allowlist size is malformed."
(( allowlist_bytes <= MAX_ALLOWLIST_BYTES )) ||
  fail "Signer allowlist exceeds its maximum size."

allowed_fingerprints=()
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" == \#* ]] && continue
  [[ "$line" =~ ^SHA256:[A-Za-z0-9+/]{43}$ ]] ||
    fail "Signer allowlist is unconfigured or contains a non-canonical fingerprint."
  for existing in "${allowed_fingerprints[@]:-}"; do
    [[ "$existing" != "$line" ]] ||
      fail "Signer allowlist contains a duplicate fingerprint."
  done
  allowed_fingerprints+=("$line")
done <"$allowlist"
[[ "${#allowed_fingerprints[@]}" == "1" ]] ||
  fail "Signer allowlist must contain exactly one reviewed SSH fingerprint."
allowed_fingerprint="${allowed_fingerprints[0]}"

public_key_b64="${ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64:-}"
[[ -n "$public_key_b64" ]] ||
  fail "ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64 is required."
(( ${#public_key_b64} <= MAX_PUBLIC_KEY_B64_CHARS )) ||
  fail "Android release tag signer public key is oversized."
[[ "$public_key_b64" =~ ^[A-Za-z0-9+/]+={0,2}$ ]] ||
  fail "Android release tag signer public key is not canonical base64."
(( ${#public_key_b64} % 4 == 0 )) ||
  fail "Android release tag signer public key has invalid base64 length."

test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
[[ -d "$test_tmp_root" && ! -L "$test_tmp_root" ]] ||
  fail "Temporary directory is missing or unsafe."
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/android-release-tag-signature.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

public_key="$tmp_dir/authorized-signer.pub"
python3 - "$public_key" "$MAX_PUBLIC_KEY_BYTES" "$public_key_b64" <<'PY'
import base64
import binascii
import os
import sys

destination, maximum_bytes, encoded = sys.argv[1], int(sys.argv[2]), sys.argv[3]
try:
    decoded = base64.b64decode(encoded, validate=True)
except (binascii.Error, ValueError) as error:
    print(
        "[android-release-tag-signature][error] "
        f"Unable to decode signer public key: {error}",
        file=sys.stderr,
    )
    raise SystemExit(1)
if not decoded or len(decoded) > maximum_bytes:
    print(
        "[android-release-tag-signature][error] "
        "Decoded signer public key is empty or oversized.",
        file=sys.stderr,
    )
    raise SystemExit(1)
if b"\x00" in decoded or b"\r" in decoded:
    print(
        "[android-release-tag-signature][error] "
        "Decoded signer public key contains forbidden bytes.",
        file=sys.stderr,
    )
    raise SystemExit(1)
try:
    text = decoded.decode("ascii")
except UnicodeDecodeError:
    print(
        "[android-release-tag-signature][error] "
        "Decoded signer public key is not ASCII.",
        file=sys.stderr,
    )
    raise SystemExit(1)
lines = text.splitlines()
if len(lines) != 1 or not lines[0].startswith("ssh-ed25519 "):
    print(
        "[android-release-tag-signature][error] "
        "Signer bundle must contain exactly one OpenSSH Ed25519 public key.",
        file=sys.stderr,
    )
    raise SystemExit(1)
flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
if hasattr(os, "O_NOFOLLOW"):
    flags |= os.O_NOFOLLOW
descriptor = os.open(destination, flags, 0o600)
try:
    with os.fdopen(descriptor, "wb", closefd=False) as output:
        output.write((lines[0] + "\n").encode("ascii"))
        output.flush()
        os.fsync(output.fileno())
finally:
    os.close(descriptor)
PY
[[ -f "$public_key" && ! -L "$public_key" && -s "$public_key" ]] ||
  fail "Decoded signer public key is missing or unsafe."

key_fields="$(awk 'NF >= 2 { print $1 " " $2 }' "$public_key")"
[[ "$(awk 'NF >= 2 { count += 1 } END { print count + 0 }' "$public_key")" == "1" ]] ||
  fail "Signer public key is malformed."
[[ "$key_fields" =~ ^ssh-ed25519\ [A-Za-z0-9+/]+={0,2}$ ]] ||
  fail "Signer public key is not canonical OpenSSH Ed25519 material."
actual_fingerprint="$(
  ssh-keygen -l -E sha256 -f "$public_key" 2>/dev/null |
    awk 'NF >= 2 { print $2 }'
)"
[[ "$actual_fingerprint" =~ ^SHA256:[A-Za-z0-9+/]{43}$ ]] ||
  fail "Signer public-key fingerprint could not be derived."
[[ "$actual_fingerprint" == "$allowed_fingerprint" ]] ||
  fail "Signer public key does not match the source-controlled fingerprint."

actual_tag_object="$(git rev-parse --verify "refs/tags/$release_tag")" ||
  fail "Release tag does not exist locally."
[[ "$actual_tag_object" == "$expected_tag_object" ]] ||
  fail "Local release tag object does not match the approved object."
[[ "$(git cat-file -t "$actual_tag_object")" == "tag" ]] ||
  fail "Release tag must be an annotated SSH-signed tag."
actual_commit="$(git rev-parse --verify "refs/tags/$release_tag^{commit}")" ||
  fail "Release tag does not resolve to a commit."
[[ "$actual_commit" == "$expected_commit" ]] ||
  fail "Release tag does not resolve to the approved commit."

tag_payload="$tmp_dir/tag-object.txt"
git cat-file tag "$actual_tag_object" >"$tag_payload" ||
  fail "Unable to read the release tag object."
[[ "$(grep -c '^-----BEGIN SSH SIGNATURE-----$' "$tag_payload" || true)" == "1" &&
  "$(grep -c '^-----END SSH SIGNATURE-----$' "$tag_payload" || true)" == "1" ]] ||
  fail "Release tag must contain exactly one SSH signature."

allowed_signers="$tmp_dir/allowed-signers"
printf '%s namespaces="git" %s\n' \
  "$SIGNER_PRINCIPAL" \
  "$key_fields" >"$allowed_signers"
chmod 0600 "$allowed_signers"
verification_status="$tmp_dir/ssh-verification.txt"
if ! git \
  -c gpg.format=ssh \
  -c "gpg.ssh.allowedSignersFile=$allowed_signers" \
  verify-tag --raw "refs/tags/$release_tag" \
  >/dev/null 2>"$verification_status"; then
  fail "Release tag signature is not valid under the pinned signer key."
fi
verified_fingerprint_lines="$(
  grep -Eo 'SHA256:[A-Za-z0-9+/]{43}' "$verification_status" || true
)"
verified_fingerprint_count="$(
  printf '%s\n' "$verified_fingerprint_lines" |
    awk 'NF > 0 { count += 1 } END { print count + 0 }'
)"
[[ "$verified_fingerprint_count" == "1" ]] ||
  fail "Release tag must produce exactly one valid SSH signing fingerprint."
verified_fingerprint="$verified_fingerprint_lines"
[[ "$verified_fingerprint" == "$allowed_fingerprint" ]] ||
  fail "Release tag was signed by a key outside the source-controlled allowlist."
grep -Fqx \
  "Good \"git\" signature for $SIGNER_PRINCIPAL with ED25519 key $allowed_fingerprint" \
  "$verification_status" ||
  fail "Release tag did not verify for the required SSH signing principal."

echo \
  "[android-release-tag-signature] tag-object=$actual_tag_object fingerprint=$allowed_fingerprint"
