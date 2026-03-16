#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"
LIBSODIUM_SRC="${LIBSODIUM_SRC:-${ROOT_DIR}/third_party/libsodium}"

if [[ ! -d "${LIBSODIUM_SRC}" ]]; then
  echo "error: libsodium sources not found at ${LIBSODIUM_SRC}" >&2
  echo "       set LIBSODIUM_SRC to a libsodium checkout or place it in third_party/libsodium" >&2
  exit 1
fi

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/28.0.12674087}"
if [[ ! -d "${ANDROID_NDK_HOME}" ]]; then
  echo "error: ANDROID_NDK_HOME (${ANDROID_NDK_HOME}) does not exist" >&2
  exit 1
fi

PREBUILT_BASE="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt"
if [[ ! -d "${PREBUILT_BASE}" ]]; then
  echo "error: LLVM toolchain base not found under ${PREBUILT_BASE}" >&2
  exit 1
fi

detect_host_tag() {
  local base="$1"
  if [[ -n "${NDK_HOST_TAG:-}" && -d "${base}/${NDK_HOST_TAG}" ]]; then
    echo "${NDK_HOST_TAG}"
    return 0
  fi

  local uname_s uname_m
  uname_s="$(uname -s)"
  uname_m="$(uname -m)"
  local -a candidates=()
  case "${uname_s}" in
    Darwin)
      if [[ "${uname_m}" == "arm64" ]]; then
        candidates=("darwin-arm64" "darwin-x86_64")
      else
        candidates=("darwin-x86_64")
      fi
      ;;
    Linux)
      candidates=("linux-x86_64")
      ;;
    MINGW*|MSYS*|CYGWIN*|Windows_NT)
      candidates=("windows-x86_64")
      ;;
  esac

  for tag in "${candidates[@]}"; do
    if [[ -d "${base}/${tag}" ]]; then
      echo "${tag}"
      return 0
    fi
  done

  local -a dirs=()
  shopt -s nullglob
  for dir in "${base}"/*; do
    [[ -d "${dir}" ]] && dirs+=("$(basename "${dir}")")
  done
  shopt -u nullglob

  if [[ ${#dirs[@]} -eq 1 ]]; then
    echo "${dirs[0]}"
    return 0
  fi

  echo "error: unable to determine NDK host tag automatically; set NDK_HOST_TAG or install a supported prebuilt under ${base}" >&2
  if [[ ${#dirs[@]} -gt 0 ]]; then
    echo "       available entries: ${dirs[*]}" >&2
  else
    echo "       no toolchains found under ${base}" >&2
  fi
  return 1
}

HOST_TAG="$(detect_host_tag "${PREBUILT_BASE}")" || exit 1
TOOLCHAIN="${PREBUILT_BASE}/${HOST_TAG}"
if [[ ! -d "${TOOLCHAIN}" ]]; then
  echo "error: LLVM toolchain not found under ${TOOLCHAIN}" >&2
  exit 1
fi

API_LEVEL="${ANDROID_API_LEVEL:-26}"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN)}"

ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

COMMON_LDFLAGS="-Wl,-z,relro -Wl,-z,now -Wl,-z,noexecstack -Wl,--gc-sections -Wl,-z,common-page-size=4096 -Wl,-z,max-page-size=16384"
COMMON_CFLAGS="-fPIC -O2"

OUTPUT_BASE="${ROOT_DIR}/app/src/main/jniLibs"
BUILD_BASE="${ROOT_DIR}/build/libsodium"
mkdir -p "${OUTPUT_BASE}" "${BUILD_BASE}"

echo "Building libsodium with API level ${API_LEVEL} from ${LIBSODIUM_SRC}"

for ABI in "${ABIS[@]}"; do
  case "${ABI}" in
    "arm64-v8a")
      HOST="aarch64-linux-android"
      CC_PREFIX="${TOOLCHAIN}/bin/${HOST}${API_LEVEL}-clang"
      CXX_PREFIX="${TOOLCHAIN}/bin/${HOST}${API_LEVEL}-clang++"
      ;;
    "armeabi-v7a")
      HOST="arm-linux-androideabi"
      CC_PREFIX="${TOOLCHAIN}/bin/armv7a-linux-androideabi${API_LEVEL}-clang"
      CXX_PREFIX="${TOOLCHAIN}/bin/armv7a-linux-androideabi${API_LEVEL}-clang++"
      ;;
    "x86")
      HOST="i686-linux-android"
      CC_PREFIX="${TOOLCHAIN}/bin/${HOST}${API_LEVEL}-clang"
      CXX_PREFIX="${TOOLCHAIN}/bin/${HOST}${API_LEVEL}-clang++"
      ;;
    "x86_64")
      HOST="x86_64-linux-android"
      CC_PREFIX="${TOOLCHAIN}/bin/${HOST}${API_LEVEL}-clang"
      CXX_PREFIX="${TOOLCHAIN}/bin/${HOST}${API_LEVEL}-clang++"
      ;;
    *)
      echo "Unsupported ABI ${ABI}" >&2
      exit 1
      ;;
  esac

  BUILD_DIR="${BUILD_BASE}/${ABI}"
  PREFIX_DIR="${BUILD_DIR}/prefix"
  INSTALL_LIB_DIR="${OUTPUT_BASE}/${ABI}"
  rm -rf "${BUILD_DIR}"
  mkdir -p "${BUILD_DIR}" "${INSTALL_LIB_DIR}"

  EXTRA_CFLAGS="${COMMON_CFLAGS}"
  case "${ABI}" in
    "armeabi-v7a")
      EXTRA_CFLAGS+=" -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mthumb"
      ;;
    "arm64-v8a")
      EXTRA_CFLAGS+=" -march=armv8-a+crypto"
      ;;
  esac

  pushd "${BUILD_DIR}" >/dev/null
    echo ">>> Configuring libsodium for ${ABI}"
    "${LIBSODIUM_SRC}/configure" \
      --host="${HOST}" \
      --prefix="${PREFIX_DIR}" \
      --disable-dependency-tracking \
      --enable-minimal-build \
      --enable-shared \
      --disable-static \
      CC="${CC_PREFIX}" \
      CXX="${CXX_PREFIX}" \
      AR="${TOOLCHAIN}/bin/llvm-ar" \
      RANLIB="${TOOLCHAIN}/bin/llvm-ranlib" \
      STRIP="${TOOLCHAIN}/bin/llvm-strip" \
      CFLAGS="${EXTRA_CFLAGS}" \
      LDFLAGS="${COMMON_LDFLAGS}"

    echo ">>> Building (${ABI})"
    make -j "${JOBS}"
    echo ">>> Installing (${ABI})"
    make install

    LIB_PATH="${PREFIX_DIR}/lib/libsodium.so"
    if [[ ! -f "${LIB_PATH}" ]]; then
      LIB_PATH="src/libsodium/.libs/libsodium.so"
    fi

    if [[ ! -f "${LIB_PATH}" ]]; then
      echo "error: libsodium.so not produced for ${ABI}" >&2
      exit 1
    fi

    cp "${LIB_PATH}" "${INSTALL_LIB_DIR}/"
  popd >/dev/null
done

echo "All libsodium builds completed. Updated libraries are in app/src/main/jniLibs/*/libsodium.so"
