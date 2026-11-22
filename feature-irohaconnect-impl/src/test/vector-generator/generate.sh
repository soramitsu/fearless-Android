#!/usr/bin/env bash
# Regenerates src/test/resources/irohaconnect/web-vectors.json from the browser wallet's Iroha Connect implementation
# (fearless-wallet-web, PR #1122) and the iroha-js 0.0.3 app SDK it vendors. Nothing is written to the web checkout.
#
#   WEB=~/dev/fearless/fearless-wallet-web bash generate.sh [web-commit]
#
# Needs Node 22+ and the web checkout's installed node_modules (esbuild, @iroha/iroha-js, @noble/*).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
web="${WEB:?set WEB to a fearless-wallet-web checkout}"
commit="${1:-6f1fc6b5658d2d371f9ade08a28eeaeaf0cda22c}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
svc=src/extension/background/extension-base/src/services/iroha-connect-service
mkdir -p "$work/ext/svc" "$work/src/util"
for file in wire.ts transactionReview.ts index.ts types.ts; do git -C "$web" show "${commit}:${svc}/${file}" > "$work/ext/svc/$file"; done
for file in iroha.ts irohaKeyring.ts slip10Ed25519.ts irohaSdkBrowserBinding.ts; do git -C "$web" show "${commit}:src/util/${file}" > "$work/src/util/$file"; done
cp -R "$here/stubs/ext" "$work/ext/stubs"
cp -R "$here/stubs/src/." "$work/src/"
cp "$here/gen.ts" "$here/hooks.mjs" "$here/register.mjs" "$work/"
ln -s "$web/node_modules" "$work/node_modules"
out="$here/../resources/irohaconnect/web-vectors.json"
(cd "$work" && node --no-warnings --no-experimental-strip-types --conditions=browser --import ./register.mjs gen.ts "$out")
