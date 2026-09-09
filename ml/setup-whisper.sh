#!/usr/bin/env bash
# Fetch the pinned whisper.cpp submodule and (optionally) the GGML models.
#
# Usage:
#   bash ml/setup-whisper.sh            # submodule only (needed to build libwhisper.so)
#   bash ml/setup-whisper.sh --models   # submodule + models (for bundling)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

echo "==> Fetch pinned whisper.cpp submodule (v1.9.3) ..."
git -C "$ROOT" submodule update --init --recursive

if [[ "${1:-}" == "--models" ]]; then
  echo "==> Downloading GGML models ..."
  bash "$HERE/download-models.sh"
fi

echo "==> Done."
echo "    Native build: open in Android Studio (needs CMake + NDK) or run ./gradlew assembleDebug"
echo "    (externalNativeBuild in app/build.gradle.kts is enabled because the submodule is present)."
