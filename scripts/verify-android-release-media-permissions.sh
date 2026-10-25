#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "[android-release-media-permissions][error] $*" >&2
  exit 1
}

[[ "$#" -eq 1 ]] || {
  echo "Usage: scripts/verify-android-release-media-permissions.sh <AndroidManifest.xml>" >&2
  exit 2
}

manifest="$1"
[[ ! -L "$manifest" && -f "$manifest" && -s "$manifest" ]] ||
  fail "Manifest must be a non-empty regular, non-symlink file."
(( $(wc -c < "$manifest") <= 4194304 )) || fail "Manifest exceeds the 4 MiB safety limit."

for permission in \
  android.permission.READ_MEDIA_IMAGES \
  android.permission.READ_MEDIA_VIDEO \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.MANAGE_EXTERNAL_STORAGE; do
  if grep -Fq "android:name=\"$permission\"" "$manifest"; then
    fail "Release manifest contains forbidden broad media/storage permission: $permission"
  fi
done

echo "[android-release-media-permissions] passed: $manifest"
