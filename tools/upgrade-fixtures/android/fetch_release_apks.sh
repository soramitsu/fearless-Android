#!/usr/bin/env bash
# Downloads every official Fearless Android release APK listed in releases.json
# into a local artifact cache and checks each file against its pinned sha256.
# APKs are never committed (test-matrix decision D3); extract_room_schema.py
# reads them from the cache.
#
# Usage: fetch_release_apks.sh <cache-dir> [--verify-only]
#
# --verify-only downloads nothing and fails if any pinned APK is missing or
# differs. Files already present with the right hash are never fetched again.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
RELEASES="${RELEASES_JSON:-$SCRIPT_DIR/releases.json}"
REPOSITORY="${RELEASES_REPOSITORY:-soramitsu/fearless-Android}"

usage() {
  echo "usage: $0 <cache-dir> [--verify-only]" >&2
  exit 2
}

[[ $# -ge 1 && $# -le 2 ]] || usage
CACHE="$1"
VERIFY_ONLY=0
if [[ $# -eq 2 ]]; then
  [[ "$2" == "--verify-only" ]] || usage
  VERIFY_ONLY=1
fi

command -v python3 >/dev/null || { echo "[fetch-apks][error] python3 is required" >&2; exit 1; }
if [[ $VERIFY_ONLY -eq 0 ]]; then
  command -v gh >/dev/null || { echo "[fetch-apks][error] the GitHub CLI (gh) is required" >&2; exit 1; }
fi

sha256_of() {
  if command -v sha256sum >/dev/null; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

# One line per APK: <tag> TAB <asset> TAB <sha256> TAB <cache path>
binaries="$(python3 -I - "$RELEASES" <<'PY'
import json, sys
document = json.load(open(sys.argv[1], encoding="utf-8"))
for release in document["releases"]:
    for binary in [release["binary"], *release.get("other_binaries", [])]:
        if binary["kind"] != "github-apk":
            continue
        for value in (release["tag"], binary["asset_name"], binary["sha256"], binary["cache_path"]):
            if "\t" in value or "\n" in value:
                raise SystemExit(f"unsafe value in releases.json: {value!r}")
        if binary["cache_path"].startswith("/") or ".." in binary["cache_path"].split("/"):
            raise SystemExit(f"unsafe cache path: {binary['cache_path']}")
        print("\t".join((release["tag"], binary["asset_name"], binary["sha256"], binary["cache_path"])))
PY
)"

mkdir -p "$CACHE"
fetched=0
present=0
failed=0
while IFS=$'\t' read -r tag asset expected path; do
  target="$CACHE/$path"
  if [[ -f "$target" && "$(sha256_of "$target")" == "$expected" ]]; then
    present=$((present + 1))
    continue
  fi
  if [[ $VERIFY_ONLY -eq 1 ]]; then
    echo "[fetch-apks][error] missing or different: $path" >&2
    failed=$((failed + 1))
    continue
  fi
  staging="$(mktemp -d "$CACHE/.fetch.XXXXXX")"
  gh release download "$tag" --repo "$REPOSITORY" --pattern "$asset" --dir "$staging" --clobber
  actual="$(sha256_of "$staging/$asset")"
  if [[ "$actual" != "$expected" ]]; then
    echo "[fetch-apks][error] $tag/$asset sha256 $actual != pinned $expected" >&2
    rm -rf -- "$staging"
    failed=$((failed + 1))
    continue
  fi
  mkdir -p "$(dirname "$target")"
  mv -f -- "$staging/$asset" "$target"
  rm -rf -- "$staging"
  fetched=$((fetched + 1))
done <<< "$binaries"

echo "[fetch-apks] $present present, $fetched downloaded, $failed failed"
[[ $failed -eq 0 ]]
