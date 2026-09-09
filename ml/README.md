# ML runtimes & models (whisper.cpp, llama.cpp)

This directory is the home for the machine-learning side of the app: the
integration plan, build scripts, and model manifests.

| Runtime | Purpose | Model | Used by |
|---------|---------|-------|---------|
| **whisper.cpp** | Speech-to-text (STT) | GGML `base` q8_0 (~78 MB), `tiny` q8_0 (~41.5 MB) | MVP 1 + MVP 3 |
| Piper / sherpa-onnx | Text-to-speech (TTS) | Piper voice | MVP 3 (deferred) |
| **llama.cpp** | LLM planner | Phi-3-mini 3.8B Q4 / Gemma-2-2B Q4 (GGUF) | MVP 2 + MVP 3 |

> **2026-09-08 decision (ADR-015).** The on-device STT runtime is
> **whisper.cpp + GGML**, *not* sherpa-onnx. Reason: sherpa-onnx's official
> Whisper ONNX exports are far larger than their nominal sizes suggest —
> **base int8 ≈ 152 MB** (encoder 27.8 + decoder 124.6) and even **tiny int8 ≈
> 98 MB** — so no bundled ONNX model can fit the ~100 MB / broad-device
> release target. whisper.cpp GGML `base` q8_0 (~78 MB) keeps base-level
> accuracy at ~99 MB APK. The earlier "sherpa-onnx is ~50× faster" claim was
> based on a misconfigured whisper.cpp benchmark (RTF 3.52 is an artifact; a
> properly built whisper.cpp runs far faster than real-time).
>
> This supersedes ADR-009/ADR-010's reasoning for STT. TTS (Piper) and the
> LLM remain deferred to MVP 2/3 and are independent of the STT runtime.

## 1. STT — whisper.cpp (GGML)

- **Purpose:** a reliable, reusable on-device STT layer for this app and
  other projects (see "Reusable layer" below).
- **Library:** whisper.cpp (MIT), built for Android ABIs and bridged through
  the tiny JNI wrapper in `WhisperNative.kt`.
- **Models:** GGML INT8 files. Default **`ggml-base-q8_0.bin`** (~78 MB);
  low-RAM fallback **`ggml-tiny-q8_0.bin`** (~41.5 MB).
- **Get them:** run `bash ml/download-models.sh` (fetches + size-verifies).
- **Build the native lib:** see the "Native build" section.

### Integration steps (ROADMAP M1)

1. `bash ml/download-models.sh` → drops models + tokens into
   `app/src/main/assets/models/`.
2. Build `libwhisper.so` for `arm64-v8a` (CMake/NDK, see below) and place
   under `app/src/main/jniLibs/arm64-v8a/`.
3. First launch copies models from `assets` to `filesDir/models/`
   (`WhisperTranscriber.resolveModelFile` updated to GGML `.bin` names).
4. Fair RTF benchmark on the target 4 GB device: same model + threads (4) +
   greedy decoding + fixed audio; confirm ≤ 1.5× real-time before locking
   `base` as default.

### Native build (CMake + AGP externalNativeBuild)

```gradle
android {
    defaultConfig { externalNativeBuild { cmake { arguments("-DWHISPER_BUILD_EXAMPLES=OFF", "-DWHISPER_BUILD_TESTS=OFF") } } }
    externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt") } }
    ndk { abiFilters += "arm64-v8a" }   // already set in app/build.gradle.kts
}
```

- ABI `arm64-v8a` is sufficient (already configured). Skip x86/armeabi-v7a to
  keep the APK small.
- Cap threads to `min(cpuCount, 4)` on 4 GB devices (already in
  `WhisperTranscriber`).
- JNI symbol contract: `WhisperNative.whisperInit / whisperTranscribe /
  whisperRelease` → `Java_com_sttapp_data_recognition_WhisperNative_*`.
- Ensure the JNI bridge and models are kept in R8: the `WhisperNative` keep
  rule is already in `app/proguard-rules.pro`.

## 2. TTS — Piper (MVP 3, deferred)

Voice confirmation ("Done. Email sent.") uses a Piper voice. It is **not** in
scope for the current MVP-1 STT push and is independent of the whisper.cpp STT
runtime. Re-evaluate runtime choice (sherpa-onnx or stand-alone Piper engine)
when MVP 3 starts. Piper voices are MIT.

## 3. LLM planner — llama.cpp (MVP 2/3, deferred)

Same as documented before: llama.cpp (GGUF), Phi-3-mini 3.8B Q4 (~2.5 GB RAM,
default on 6 GB) / Gemma-2-2B Q4 (~1.5 GB RAM, low-RAM on 4 GB); load on
demand. See `docs/PRD.md §5.5, §7.2`.

## 4. Resource budget (4 GB device)

| Component | RAM |
|-----------|-----|
| STT (Whisper `base` q8_0) | ~150–300 MB (model + decode buffers) |
| TTS (Piper) | ~150 MB (MVP 3) |
| LLM (Phi-3-mini Q4) | ~2.5 GB (MVP 2/3, loaded on demand) |

Acceptance gates live in `docs/NFR.md`.

## 5. APK-size interplay

| Bundle | Model | APK ~ |
|--------|-------|-------|
| `base` q8_0 (default) | ~78 MB | ~99 MB |
| `tiny` q8_0 (low-RAM) | ~41.5 MB | ~60 MB |
| `base` + `tiny` | ~119 MB | ~140 MB ⚠️ not for distribution |

The release CI gate is ≤ 100 MB (`docs/NFR.md §2`). Ship `base` only; keep
`tiny` as an optional download / low-RAM override.

## 6. Reusable layer

The `Transcriber` interface + `PcmDecoder` + `WhisperNative` form a self-
contained `:recognition` seam. To reuse in another project: extract the
`core/recognition` interfaces, `WhisperTranscriber`, `MediaCodecPcmDecoder`
and the whisper.cpp CMake build into a library module; no UI/DI changes are
required because everything depends on `Transcriber`, not on whisper.cpp
directly.

## 7. Open implementation tasks (ROADMAP M1)

- [ ] `bash ml/download-models.sh` and bundle `ggml-base-q8_0.bin`
- [ ] whisper.cpp CMake build `/src/main/cpp` → `libwhisper.so` (arm64-v8a)
- [ ] First-launch model copy `assets -> filesDir/models`
- [ ] Fair RTF benchmark on target device; lock `base` default
- [ ] Whisper `small` as a future optional download (needs ~244 MB; unlikely
      to fit a mid-range default — keep as opt-in only)
