#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "[release-snapshot][error] $*" >&2
  exit 1
}

[[ "$#" -eq 2 ]] || {
  echo "Usage: scripts/emit-verified-release-snapshot.sh <file> <expected-sha256>" >&2
  exit 2
}

source_file="$1"
expected_sha256="$2"

[[ "$expected_sha256" =~ ^[0-9a-f]{64}$ ]] ||
  fail "Expected SHA-256 must be exactly 64 lowercase hexadecimal characters."
[[ -f "$source_file" && ! -L "$source_file" && -s "$source_file" ]] ||
  fail "Release input must be a non-empty regular, non-symlink file."

# Keep the verified bytes in this process rather than reopening the source path
# after validation. The record is JSON, so it cannot contain an unescaped NUL;
# a non-newline sentinel preserves every trailing newline through command
# substitution. Consumers receive the exact snapshot whose digest was checked.
sentinel=$'\036'
snapshot="$(
  cat -- "$source_file" || exit 1
  printf '%s' "$sentinel"
)"
[[ "$snapshot" == *"$sentinel" ]] ||
  fail "Could not capture a complete release-input snapshot."
snapshot="${snapshot%"$sentinel"}"

if command -v sha256sum >/dev/null 2>&1; then
  actual_sha256="$(printf '%s' "$snapshot" | sha256sum | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  actual_sha256="$(printf '%s' "$snapshot" | shasum -a 256 | awk '{print $1}')"
else
  fail "Neither sha256sum nor shasum is available."
fi

[[ "$actual_sha256" == "$expected_sha256" ]] ||
  fail "Release input changed after its trusted digest was recorded."

printf '%s' "$snapshot"
