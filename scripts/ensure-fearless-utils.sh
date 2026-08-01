#!/usr/bin/env bash
set -euo pipefail

# Git replace refs must never be able to substitute a different tree for the
# pinned commit while retaining the expected object name.
export GIT_NO_REPLACE_OBJECTS=1

EXPECTED_COMMIT="${FEARLESS_UTILS_COMMIT:-7500809f33243ee47ecb2ec8563fc284ac4de0d6}"
EXPECTED_REPOSITORY="${FEARLESS_UTILS_REPOSITORY:-soramitsu/fearless-utils-Android}"
LIBRARY_ONLY="${FEARLESS_UTILS_LIBRARY_ONLY:-false}"

repo_root="$(cd "$(dirname "$0")/.." && pwd -P)"

if [[ -n "${FEARLESS_UTILS_PATH:-}" ]]; then
  utils_path="$FEARLESS_UTILS_PATH"
elif [[ -e "$repo_root/fearless-utils-Android/.git" ]]; then
  utils_path="$repo_root/fearless-utils-Android"
else
  utils_path="$(cd "$repo_root/.." && pwd -P)/fearless-utils-Android"
fi

fail() {
  echo "[fearless-utils][error] $*" >&2
  exit 1
}

cleanup() {
  if [[ -n "${tmp_dir:-}" && -d "$tmp_dir" ]]; then
    rm -rf "$tmp_dir"
  fi
}
trap cleanup EXIT HUP INT TERM

canonical_repository_from_url() {
  local url="$1"
  local repository

  case "$url" in
    https://github.com/*)
      repository="${url#https://github.com/}"
      ;;
    git@github.com:*)
      repository="${url#git@github.com:}"
      ;;
    ssh://git@github.com/*)
      repository="${url#ssh://git@github.com/}"
      ;;
    *)
      return 1
      ;;
  esac

  repository="${repository%/}"
  repository="${repository%.git}"
  [[ "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || return 1
  printf '%s\n' "$repository"
}

verify_origin() {
  local -a configured_urls=()
  local -a effective_urls=()
  local configured_repository
  local effective_repository

  while IFS= read -r url; do
    configured_urls+=("$url")
  done < <(git -C "$utils_path" config --get-all remote.origin.url || true)
  while IFS= read -r url; do
    effective_urls+=("$url")
  done < <(git -C "$utils_path" remote get-url --all origin 2>/dev/null || true)

  [[ "${#configured_urls[@]}" -eq 1 ]] ||
    fail "fearless-utils-Android must have exactly one configured origin URL; found ${#configured_urls[@]}"
  [[ "${#effective_urls[@]}" -eq 1 ]] ||
    fail "fearless-utils-Android must resolve origin to exactly one URL; found ${#effective_urls[@]}"

  configured_repository="$(canonical_repository_from_url "${configured_urls[0]}")" ||
    fail "origin must use an uncredentialed HTTPS or git SSH GitHub URL for $EXPECTED_REPOSITORY; found ${configured_urls[0]}"
  effective_repository="$(canonical_repository_from_url "${effective_urls[0]}")" ||
    fail "effective origin URL is not an approved GitHub URL: ${effective_urls[0]}"

  [[ "$configured_repository" == "$EXPECTED_REPOSITORY" ]] ||
    fail "origin must identify $EXPECTED_REPOSITORY; found $configured_repository"
  [[ "$effective_repository" == "$EXPECTED_REPOSITORY" ]] ||
    fail "effective origin must identify $EXPECTED_REPOSITORY; found $effective_repository (check Git URL rewrite configuration)"
}

verify_committed_overlay_patch() {
  local patch_relative="scripts/fearless-utils-library-only.patch"
  local patch_file="$repo_root/$patch_relative"
  local wallet_top
  local head_blob
  local index_blob
  local worktree_blob

  [[ -f "$patch_file" && ! -L "$patch_file" ]] ||
    fail "library-only overlay must be a regular, non-symlink file at $patch_file"

  wallet_top="$(git -C "$repo_root" rev-parse --show-toplevel 2>/dev/null)" ||
    fail "unable to verify the committed library-only overlay because $repo_root is not a Git worktree"
  wallet_top="$(cd "$wallet_top" && pwd -P)"
  [[ "$wallet_top" == "$repo_root" ]] ||
    fail "Android checkout root mismatch while verifying the library-only overlay: expected $repo_root, found $wallet_top"

  head_blob="$(git -C "$repo_root" rev-parse --verify "HEAD:$patch_relative" 2>/dev/null)" ||
    fail "$patch_relative is not committed at Android HEAD"
  index_blob="$(git -C "$repo_root" rev-parse --verify ":$patch_relative" 2>/dev/null)" ||
    fail "$patch_relative is missing from the Android index"
  worktree_blob="$(git -C "$repo_root" hash-object --no-filters "$patch_file")" ||
    fail "unable to hash $patch_relative"

  [[ "$index_blob" == "$head_blob" ]] ||
    fail "$patch_relative has staged drift; commit the reviewed overlay before use"
  [[ "$worktree_blob" == "$head_blob" ]] ||
    fail "$patch_relative differs from the committed Android HEAD overlay"

  printf '%s\n' "$patch_file"
}

# Kotlin's Gradle compiler runner writes crash diagnostics and a live compiler
# session marker below these exact repository-local cache paths. They are not
# build inputs, but older fearless-utils checkouts do not ignore them. Keep the
# narrowly validated exceptions here (rather than in a broad Git ignore) so
# every other untracked path remains provenance drift.
KOTLIN_DIAGNOSTIC_MAX_FILES=128
KOTLIN_DIAGNOSTIC_MAX_FILE_BYTES=1048576
KOTLIN_DIAGNOSTIC_MAX_TOTAL_BYTES=16777216

has_single_hard_link() {
  local path="$1"
  local link_count

  if link_count="$(stat -f '%l' -- "$path" 2>/dev/null)"; then
    :
  elif link_count="$(stat -c '%h' -- "$path" 2>/dev/null)"; then
    :
  else
    return 1
  fi

  [[ "$link_count" == "1" ]]
}

kotlin_diagnostic_cache_file_size() {
  local path="$1"
  local full_path="$utils_path/$path"
  local timestamp
  local first_line
  local second_line
  local size

  [[ "$path" =~ ^\.kotlin/errors/errors-([0-9]{13})\.log$ ]] || return 1
  timestamp="${BASH_REMATCH[1]}"
  [[ "$timestamp" != "0000000000000" ]] || return 1
  [[ -f "$full_path" && ! -L "$full_path" && ! -x "$full_path" ]] || return 1
  has_single_hard_link "$full_path" || return 1

  size="$(LC_ALL=C wc -c < "$full_path")" || return 1
  size="${size//[[:space:]]/}"
  [[ "$size" =~ ^[0-9]+$ ]] || return 1
  (( size > 0 && size <= KOTLIN_DIAGNOSTIC_MAX_FILE_BYTES )) || return 1

  {
    IFS= read -r first_line
    IFS= read -r second_line
  } < "$full_path" || return 1
  [[ "$first_line" =~ ^kotlin[[:space:]]version:[[:space:]][0-9] ]] || return 1
  [[ "$second_line" == "error message: "* ]] || return 1

  printf '%s\n' "$size"
}

kotlin_session_cache_file_size() {
  local path="$1"
  local full_path="$utils_path/$path"
  local session_id
  local size
  local LC_ALL=C

  [[ "$path" =~ ^\.kotlin/sessions/kotlin-compiler-([1-9][0-9]{0,19})\.salive$ ]] || return 1
  session_id="${BASH_REMATCH[1]}"
  if [[ "${#session_id}" -eq 20 ]]; then
    [[ "$session_id" < "18446744073709551616" ]] || return 1
  fi
  [[ -f "$full_path" && ! -L "$full_path" && ! -x "$full_path" ]] || return 1
  has_single_hard_link "$full_path" || return 1

  size="$(LC_ALL=C wc -c < "$full_path")" || return 1
  size="${size//[[:space:]]/}"
  [[ "$size" == "0" ]] || return 1

  printf '0\n'
}

kotlin_generated_cache_file_size() {
  local path="$1"
  local size

  if size="$(kotlin_diagnostic_cache_file_size "$path")"; then
    printf '%s\n' "$size"
    return 0
  fi
  if size="$(kotlin_session_cache_file_size "$path")"; then
    printf '%s\n' "$size"
    return 0
  fi
  return 1
}

index_matches_worktree() {
  local index_file="$1"
  local entry
  local metadata
  local path
  local mode
  local expected_blob
  local stage
  local actual_blob
  local full_path
  local raw_mismatch=""
  local diagnostic_size
  local diagnostic_count=0
  local diagnostic_total_bytes=0

  # read-tree creates an index without worktree stat data. Refresh only the
  # temporary index so diff-files hashes content instead of reporting every
  # entry as racily clean/unknown. A mismatch is expected to make refresh
  # non-zero and is evaluated by diff-files below.
  GIT_INDEX_FILE="$index_file" \
    git -C "$utils_path" update-index -q --really-refresh >/dev/null 2>&1 || true

  GIT_INDEX_FILE="$index_file" \
    git -C "$utils_path" diff-files --quiet --ignore-submodules=none -- || return 1

  # diff-files honors configured clean filters. Compare raw worktree bytes with
  # the temporary index blobs as well, so a malicious/global filter cannot make
  # modified source appear clean. Gitlink contents are checked separately by
  # verify_submodules_clean and diff-files.
  while IFS= read -r -d '' entry; do
    metadata="${entry%%$'\t'*}"
    path="${entry#*$'\t'}"
    IFS=' ' read -r mode expected_blob stage <<< "$metadata"
    full_path="$utils_path/$path"

    [[ "$stage" == "0" ]] || {
      raw_mismatch="$path"
      break
    }

    case "$mode" in
      100644|100755)
        [[ -f "$full_path" && ! -L "$full_path" ]] || {
          raw_mismatch="$path"
          break
        }
        if [[ "$mode" == "100755" ]]; then
          [[ -x "$full_path" ]] || {
            raw_mismatch="$path"
            break
          }
        elif [[ -x "$full_path" ]]; then
          raw_mismatch="$path"
          break
        fi
        actual_blob="$(git -C "$utils_path" hash-object --no-filters -- "$path")" || {
          raw_mismatch="$path"
          break
        }
        [[ "$actual_blob" == "$expected_blob" ]] || {
          raw_mismatch="$path"
          break
        }
        ;;
      120000)
        [[ -L "$full_path" ]] || {
          raw_mismatch="$path"
          break
        }
        actual_blob="$(readlink -n -- "$full_path" | git -C "$utils_path" hash-object --stdin)" || {
          raw_mismatch="$path"
          break
        }
        [[ "$actual_blob" == "$expected_blob" ]] || {
          raw_mismatch="$path"
          break
        }
        ;;
      160000)
        [[ -d "$full_path" ]] || {
          raw_mismatch="$path"
          break
        }
        actual_blob="$(git -C "$full_path" rev-parse --verify HEAD 2>/dev/null)" || {
          raw_mismatch="$path"
          break
        }
        [[ "$actual_blob" == "$expected_blob" ]] || {
          raw_mismatch="$path"
          break
        }
        ;;
      *)
        raw_mismatch="$path"
        break
        ;;
    esac
  done < <(GIT_INDEX_FILE="$index_file" git -C "$utils_path" ls-files --stage -z)
  [[ -z "$raw_mismatch" ]] || return 1

  while IFS= read -r -d '' path; do
    diagnostic_size="$(kotlin_generated_cache_file_size "$path")" || return 1
    diagnostic_count=$((diagnostic_count + 1))
    diagnostic_total_bytes=$((diagnostic_total_bytes + diagnostic_size))
    (( diagnostic_count <= KOTLIN_DIAGNOSTIC_MAX_FILES )) || return 1
    (( diagnostic_total_bytes <= KOTLIN_DIAGNOSTIC_MAX_TOTAL_BYTES )) || return 1
  done < <(
    GIT_INDEX_FILE="$index_file" \
      git -C "$utils_path" ls-files --others --exclude-standard -z
  )

  return 0
}

report_index_mismatch() {
  local index_file="$1"
  local label="$2"
  local count=0
  local path
  local diagnostic_size
  local diagnostic_count=0
  local diagnostic_total_bytes=0

  echo "[fearless-utils][error] checkout does not equal $label; unexpected paths:" >&2

  GIT_INDEX_FILE="$index_file" \
    git -C "$utils_path" update-index -q --really-refresh >/dev/null 2>&1 || true

  while IFS= read -r -d '' path; do
    printf '  - %q\n' "$path" >&2
    count=$((count + 1))
    [[ "$count" -ge 20 ]] && break
  done < <(
    GIT_INDEX_FILE="$index_file" \
      git -C "$utils_path" diff-files --name-only -z --ignore-submodules=none --
  )

  if [[ "$count" -lt 20 ]]; then
    while IFS= read -r -d '' path; do
      if diagnostic_size="$(kotlin_generated_cache_file_size "$path")"; then
        diagnostic_count=$((diagnostic_count + 1))
        diagnostic_total_bytes=$((diagnostic_total_bytes + diagnostic_size))
        if (( diagnostic_count <= KOTLIN_DIAGNOSTIC_MAX_FILES &&
              diagnostic_total_bytes <= KOTLIN_DIAGNOSTIC_MAX_TOTAL_BYTES )); then
          continue
        fi
      fi
      printf '  - %q\n' "$path" >&2
      count=$((count + 1))
      [[ "$count" -ge 20 ]] && break
    done < <(
      GIT_INDEX_FILE="$index_file" \
        git -C "$utils_path" ls-files --others --exclude-standard -z
    )
  fi

  if [[ "$count" -eq 0 ]]; then
    echo "  - submodule state or file metadata differs" >&2
  elif [[ "$count" -ge 20 ]]; then
    echo "  - (additional paths omitted)" >&2
  fi
}

verify_submodules_clean() {
  local status_output
  local status_line

  status_output="$(git -C "$utils_path" submodule status --recursive 2>/dev/null)" ||
    fail "unable to inspect fearless-utils-Android submodules"
  while IFS= read -r status_line; do
    case "${status_line:0:1}" in
      -|+|U)
        fail "fearless-utils-Android contains an uninitialized, mismatched, or conflicted submodule"
        ;;
    esac
  done <<< "$status_output"

  if ! git -C "$utils_path" submodule foreach --quiet --recursive \
    'test -z "$(git status --porcelain=v1 --untracked-files=all --ignore-submodules=none)"'; then
    fail "fearless-utils-Android contains dirty submodule content"
  fi
}

verify_no_replace_refs() {
  local replace_refs

  replace_refs="$(git -C "$utils_path" for-each-ref --format='%(refname)' refs/replace/)" ||
    fail "unable to inspect Git replacement refs"
  [[ -z "$replace_refs" ]] ||
    fail "Git replacement refs are not allowed in the pinned fearless-utils-Android checkout"
}

[[ "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]] ||
  fail "FEARLESS_UTILS_COMMIT must be a full lowercase 40-character Git commit"
[[ "$EXPECTED_REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] ||
  fail "FEARLESS_UTILS_REPOSITORY must be an owner/repository identifier"
[[ "$LIBRARY_ONLY" == "true" || "$LIBRARY_ONLY" == "false" ]] ||
  fail "FEARLESS_UTILS_LIBRARY_ONLY must be exactly true or false"

git -C "$utils_path" rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  fail "fearless-utils-Android checkout not found at $utils_path. Clone https://github.com/$EXPECTED_REPOSITORY.git or set FEARLESS_UTILS_PATH."

utils_top="$(git -C "$utils_path" rev-parse --show-toplevel 2>/dev/null)" ||
  fail "unable to resolve fearless-utils-Android worktree root at $utils_path"
utils_top="$(cd "$utils_top" && pwd -P)"
requested_path="$(cd "$utils_path" && pwd -P)"
[[ "$utils_top" == "$requested_path" ]] ||
  fail "FEARLESS_UTILS_PATH must identify the repository root; expected $utils_top, found $requested_path"
utils_path="$utils_top"

verify_origin
verify_no_replace_refs

actual_commit="$(git -C "$utils_path" rev-parse --verify HEAD^{commit} 2>/dev/null)" ||
  fail "fearless-utils-Android HEAD is not a commit"
if [[ "$actual_commit" != "$EXPECTED_COMMIT" ]]; then
  fail "fearless-utils-Android must be at $EXPECTED_COMMIT, found $actual_commit. Set FEARLESS_UTILS_COMMIT to the full reviewed commit only for an intentional source bump."
fi

head_tree="$(git -C "$utils_path" rev-parse --verify "$actual_commit^{tree}" 2>/dev/null)" ||
  fail "unable to resolve the pinned fearless-utils-Android tree"
index_tree="$(git -C "$utils_path" write-tree 2>/dev/null)" ||
  fail "fearless-utils-Android index contains unresolved or invalid staged entries"
if [[ "$index_tree" != "$head_tree" ]]; then
  fail "fearless-utils-Android index differs from pinned HEAD; staged changes are not allowed"
fi

verify_submodules_clean

echo "[fearless-utils] Using $utils_path at $actual_commit"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fearless-utils-derived-tree.XXXXXX")" ||
  fail "unable to create temporary verification directory"
head_index="$tmp_dir/head.index"
expected_index="$tmp_dir/library-only.index"

GIT_INDEX_FILE="$head_index" git -C "$utils_path" read-tree "$actual_commit" ||
  fail "unable to construct the pinned fearless-utils-Android source tree"

if [[ "$LIBRARY_ONLY" == "false" ]]; then
  if ! index_matches_worktree "$head_index"; then
    report_index_mismatch "$head_index" "the pristine pinned HEAD tree"
    fail "tracked, untracked, or submodule drift is not allowed"
  fi

  echo "[fearless-utils] Verified pristine $utils_path at $actual_commit from $EXPECTED_REPOSITORY"
  echo "[fearless-utils] Effective source tree $head_tree"
  exit 0
fi

patch_file="$(verify_committed_overlay_patch)"
cp "$head_index" "$expected_index"
GIT_INDEX_FILE="$expected_index" \
  git -C "$utils_path" apply --cached --check --unidiff-zero --whitespace=error-all "$patch_file" ||
  fail "committed library-only overlay does not apply cleanly to pinned commit $actual_commit"
GIT_INDEX_FILE="$expected_index" \
  git -C "$utils_path" apply --cached --unidiff-zero --whitespace=error-all "$patch_file" ||
  fail "unable to construct the expected library-only derived tree"
effective_tree="$(GIT_INDEX_FILE="$expected_index" git -C "$utils_path" write-tree)" ||
  fail "unable to fingerprint the expected library-only derived tree"
[[ "$effective_tree" =~ ^[0-9a-f]{40}$ ]] ||
  fail "expected library-only derived tree fingerprint is malformed"

if index_matches_worktree "$expected_index"; then
  echo "[fearless-utils] Verified exact library-only derived tree at $actual_commit from $EXPECTED_REPOSITORY"
  echo "[fearless-utils] Effective source tree $effective_tree"
  exit 0
fi

# Preserve the historical CI/developer behavior of applying the overlay, but do
# so only when the checkout is demonstrably pristine. A dirty or partial tree is
# rejected without being modified.
if index_matches_worktree "$head_index"; then
  git -C "$utils_path" apply --check --unidiff-zero --whitespace=error-all "$patch_file" ||
    fail "library-only overlay cannot be applied to the pristine checkout"
  git -C "$utils_path" apply --unidiff-zero --whitespace=error-all "$patch_file" ||
    fail "unable to apply the library-only overlay"

  if ! index_matches_worktree "$expected_index"; then
    report_index_mismatch "$expected_index" "pinned HEAD plus the committed library-only overlay"
    fail "checkout changed while the library-only overlay was being applied"
  fi

  echo "[fearless-utils] Applied and verified exact library-only derived tree at $actual_commit from $EXPECTED_REPOSITORY"
  echo "[fearless-utils] Effective source tree $effective_tree"
  exit 0
fi

report_index_mismatch "$expected_index" "pinned HEAD plus the committed library-only overlay"
fail "refusing to modify a dirty or partially overlaid checkout; reset it to the pinned commit and rerun"
