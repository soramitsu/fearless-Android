#!/usr/bin/env bash
# Live pairing against https://taira.sora.org: the upstream iroha-js 0.0.3 app SDK registers a Connect session and
# plays the dApp; the Fearless debug build on an emulator joins through the same-device irohaconnect:// link, and a
# person approves the session and each signature on the device. Every signature is verified against its payload hash.
# Nothing is submitted to the chain.
#
#   WEB=~/dev/fearless/fearless-wallet-web bash live.sh <evidence.json> <adb serial> [web-commit]
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
web="${WEB:?set WEB to a fearless-wallet-web checkout}"
evidence="$(cd "$(dirname "${1:?evidence path}")" && pwd)/$(basename "$1")"
serial="${2:?adb serial}"
commit="${3:-6f1fc6b5658d2d371f9ade08a28eeaeaf0cda22c}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
svc=src/extension/background/extension-base/src/services/iroha-connect-service
mkdir -p "$work/ext/svc" "$work/src/util"
for file in wire.ts transactionReview.ts index.ts types.ts; do git -C "$web" show "${commit}:${svc}/${file}" > "$work/ext/svc/$file"; done
for file in iroha.ts irohaKeyring.ts slip10Ed25519.ts irohaSdkBrowserBinding.ts; do git -C "$web" show "${commit}:src/util/${file}" > "$work/src/util/$file"; done
cp -R "$here/stubs/ext" "$work/ext/stubs"
cp -R "$here/stubs/src/." "$work/src/"
cp "$here/live-taira-dapp.ts" "$here/hooks.mjs" "$here/register.mjs" "$work/"
ln -s "$web/node_modules" "$work/node_modules"
(cd "$work" && ADB="${ADB:-adb}" node --no-warnings --no-experimental-strip-types --conditions=browser --import ./register.mjs live-taira-dapp.ts "$evidence" "$serial")
