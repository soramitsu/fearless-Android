#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
AUDIT="$ROOT_DIR/scripts/verify-android-release-media-permissions.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

valid="$tmp_dir/valid.xml"
cat > "$valid" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
</manifest>
XML
"$AUDIT" "$valid" >/dev/null

negative_count=0
for permission in \
  android.permission.READ_MEDIA_IMAGES \
  android.permission.READ_MEDIA_VIDEO \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.MANAGE_EXTERNAL_STORAGE; do
  fixture="$tmp_dir/${permission##*.}.xml"
  cat > "$fixture" <<XML
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="$permission" />
</manifest>
XML
  if "$AUDIT" "$fixture" >/dev/null 2>&1; then
    echo "[android-release-media-permissions-test][error] accepted $permission" >&2
    exit 1
  fi
  negative_count=$((negative_count + 1))
done

if "$AUDIT" "$tmp_dir/missing.xml" >/dev/null 2>&1; then
  echo "[android-release-media-permissions-test][error] accepted a missing manifest" >&2
  exit 1
fi
negative_count=$((negative_count + 1))

ln -s "$valid" "$tmp_dir/symlink.xml"
if "$AUDIT" "$tmp_dir/symlink.xml" >/dev/null 2>&1; then
  echo "[android-release-media-permissions-test][error] accepted a symlink manifest" >&2
  exit 1
fi
negative_count=$((negative_count + 1))

echo "[android-release-media-permissions-test] 1 positive + $negative_count negative cases passed"
