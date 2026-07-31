#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"

fail() {
  echo "[moonpay-client-policy][error] $*" >&2
  exit 1
}

require_file() {
  [[ -f "$ROOT/$1" ]] || fail "Required policy input is missing: $1"
}

is_android_policy_input() {
  local path="$1"

  case "$path" in
    third_party/*|*/build/*|.gradle/*|.git/*|node_modules/*)
      return 1
      ;;
    scripts/verify-android-moonpay-client-secret-policy.sh|\
      scripts/test-android-moonpay-client-secret-policy.sh)
      return 1
      ;;
    .github/workflows/*.yml|.github/workflows/*.yaml|\
      scripts/*.sh|\
      build.gradle|build.gradle.kts|settings.gradle|settings.gradle.kts|\
      gradle.properties|*.gradle|*.gradle.kts|*.properties|*.toml|\
      buildSrc/*|gradle/*|versioning/*|config/*|*/src/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

scan_forbidden_pattern() {
  local description="$1"
  local pattern="$2"
  local scope="${3:-all}"
  local path
  local found=false

  for path in "${policy_inputs[@]}"; do
    if [[ "$scope" == "text" ]] &&
      LC_ALL=C grep -IiqE "$pattern" "$ROOT/$path"; then
      echo "[moonpay-client-policy][error] forbidden $description in $path" >&2
      found=true
    elif [[ "$scope" == "all" ]] &&
      LC_ALL=C grep -aiqE "$pattern" "$ROOT/$path"; then
      echo "[moonpay-client-policy][error] forbidden $description in $path" >&2
      found=true
    fi
  done

  [[ "$found" == "false" ]] ||
    fail "MoonPay client signing material must remain absent."
}

scan_forbidden_obfuscated_pattern() {
  local description="$1"
  local pattern="$2"
  local path
  local found=false

  for path in "${policy_inputs[@]}"; do
    if [[ "$path" == .github/workflows/* ]]; then
      if LC_ALL=C sed '/android-moonpay-client-secret-policy\.sh/d' \
        "$ROOT/$path" |
        LC_ALL=C grep -aiE "$pattern" >/dev/null; then
        echo "[moonpay-client-policy][error] forbidden $description in $path" >&2
        found=true
      fi
    elif LC_ALL=C grep -aiqE "$pattern" "$ROOT/$path"; then
      echo "[moonpay-client-policy][error] forbidden $description in $path" >&2
      found=true
    fi
  done

  [[ "$found" == "false" ]] ||
    fail "MoonPay client signing material must remain absent."
}

hash_stdin() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

require_file "feature-wallet-impl/build.gradle"
require_file "feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
require_file "common/src/main/java/jp/co/soramitsu/common/utils/CryptoUtils.kt"

git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  fail "The policy root must be a Git worktree so only tracked inputs are audited."

provider_path="feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/MoonPayProvider.kt"
[[ ! -e "$ROOT/$provider_path" ]] ||
  fail "$provider_path must remain absent until MoonPay signing is server-side."

policy_inputs=()
while IFS= read -r -d '' path; do
  is_android_policy_input "$path" || continue
  [[ -e "$ROOT/$path" || -L "$ROOT/$path" ]] || continue
  [[ -f "$ROOT/$path" && ! -L "$ROOT/$path" ]] ||
    fail "Tracked Android policy input is missing or unsafe: $path"
  policy_inputs+=("$path")
done < <(git -C "$ROOT" ls-files -z)

(( ${#policy_inputs[@]} > 0 )) ||
  fail "No tracked Android source, build, script, or workflow inputs were found."

scan_forbidden_pattern \
  "MoonPay credential/configuration identifier" \
  'MOONPAY_([A-Z0-9_]*_)?(SECRET|PRIVATE_KEY|SIGNING_KEY|HMAC_KEY|PUBLIC_KEY|HOST)|moonpay[A-Za-z0-9]*(Secret|PrivateKey|SigningKey|HmacKey)'

scan_forbidden_pattern \
  "MoonPay client signer/provider" \
  "MoonPayProvider|moonpay[^[:alnum:]]*(hmac|signature|private|secret|sign|signer|signing)|((hmac|signature|private|secret|sign|signer|signing)[^[:alnum:]]*)moonpay|https?://[^[:space:]\"']*moonpay"

scan_forbidden_pattern \
  "generic HMAC helper formerly used by MoonPay" \
  'fun[[:space:]]+String\.hmacSHA256|HmacSHA256' \
  text

scan_forbidden_obfuscated_pattern \
  "split or encoded MoonPay client signer" \
  'm[^[:alnum:]]*o[^[:alnum:]]*o[^[:alnum:]]*n[^[:alnum:]]*p[^[:alnum:]]*a[^[:alnum:]]*y.{0,160}(s[^[:alnum:]]*e[^[:alnum:]]*c[^[:alnum:]]*r[^[:alnum:]]*e[^[:alnum:]]*t|p[^[:alnum:]]*r[^[:alnum:]]*i[^[:alnum:]]*v[^[:alnum:]]*a[^[:alnum:]]*t[^[:alnum:]]*e|h[^[:alnum:]]*m[^[:alnum:]]*a[^[:alnum:]]*c|s[^[:alnum:]]*i[^[:alnum:]]*g[^[:alnum:]]*n)|bW9vbnBheQ|TU9PTlBBWQ|6[dD]6[fF]6[fF]6[eE]7[0P]6[1A]7[9Y]|SG1hY1NIQTI1Ng'

wallet_module="$ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
provider_dir="feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken"
expected_provider_files="$(
  printf '%s\n' \
    "$provider_dir/CoinbaseProvider.kt" \
    "$provider_dir/ExternalProvider.kt" \
    "$provider_dir/RampProvider.kt"
)"
actual_provider_files="$(
  while IFS= read -r provider_file; do
    [[ -f "$ROOT/$provider_file" && ! -L "$ROOT/$provider_file" ]] &&
      printf '%s\n' "$provider_file"
  done < <(git -C "$ROOT" ls-files "$provider_dir") |
    LC_ALL=C sort
)"
[[ "$actual_provider_files" == "$expected_provider_files" ]] ||
  fail "The buy-provider implementation inventory differs from the exact approved set."

provider_block="$(
  awk '
    /fun provideBuyTokenIntegration\(\)/ { capture=1 }
    capture { print }
    capture && /^    }$/ { exit }
  ' "$wallet_module"
)"
[[ -n "$provider_block" ]] ||
  fail "The exact buy-provider registry block is missing."
provider_block_sha256="$(
  printf '%s' "$provider_block" |
    LC_ALL=C tr -d '[:space:]' |
    hash_stdin
)"
[[ "$provider_block_sha256" == \
  "4da5b1ba05ee54247c11df1889a947ac6cba79c8186a6e3cb0e9415494b75d87" ]] ||
  fail "The buy-provider registry differs from the exact Ramp/Coinbase allowlist."

echo \
  "[moonpay-client-policy] PASS: ${#policy_inputs[@]} tracked Android source/build/workflow inputs contain no client signing material."
