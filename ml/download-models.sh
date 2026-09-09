#!/usr/bin/env bash
# Download the whisper.cpp GGML models we bundle with the app.
#
# STT runtime: whisper.cpp (docs/DECISIONS.md ADR-015). We bundle the
# int8 q8_0 models because they keep base-level accuracy at a size that
# lets the release APK stay <= 100 MB (see docs/NFR.md §2):
#
#   ggml-base-q8_0.bin    ~78 MB  -> ~99 MB APK   (default)
#   ggml-tiny-q8_0.bin    ~41.5 MB -> ~60 MB APK  (low-RAM fallback)
#
# Source repo: https://huggingface.co/ggerganov/whisper.cpp (MIT licensed models)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${1:-$HERE/../app/src/main/assets/models}"
BASE_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

# Model(s) to fetch. Change to ("ggml-base-q8_0.bin" "ggml-tiny-q8_0.bin") to include both.
MODELS=("ggml-base-q8_0.bin")

# Expected sizes in bytes (verified 2026-09). Script fails if they drift by >2%.
declare -A EXPECTED=(
  ["ggml-base-q8_0.bin"]=81768585
  ["ggml-tiny-q8_0.bin"]=43537433
  ["ggml-tiny.bin"]=77691713
  ["ggml-base.bin"]=147951465
)

mkdir -p "$DEST"

for model in "${MODELS[@]}"; do
  out="$DEST/$model"
  if [[ -f "$out" ]] && [[ "$(stat -c%s "$out")" -eq "${EXPECTED[$model]:-0}" ]]; then
    echo "OK    $model already present ($(du -h "$out" | cut -f1))"
    continue
  fi
  echo "Fetch $model ..."
  curl -fL --retry 3 --progress-bar "$BASE_URL/$model" -o "$out"
  actual="$(stat -c%s "$out")"
  expected="${EXPECTED[$model]:-0}"
  if (( expected > 0 )); then
    lo=$(( expected * 98 / 100 ))
    hi=$(( expected * 102 / 100 ))
    if (( actual < lo || actual > hi )); then
      echo "ERROR $model size $actual differs from expected $expected" >&2
      exit 1
    fi
  fi
  echo "OK    $model downloaded ($(du -h "$out" | cut -f1))"
done

echo
echo "Done. Models are in: $DEST"
echo "The app copies them to filesDir/models/ on first launch and the release"
echo "APK size gate in CI is <= 100 MB (see docs/NFR.md §2)."
