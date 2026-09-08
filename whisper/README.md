# whisper.cpp for Android — build & JNI plan

Transcription uses **whisper.cpp** compiled as `libwhisper.so` and called from
Kotlin through the JNI bridge in
`app/src/main/java/com/sttapp/data/recognition/WhisperNative.kt`.

This directory is the home for the native side (source, build scripts, model
tooling). The scaffold does **not** vendor whisper.cpp — it's large and pinned
by submodule or git tag.

## 1. Contract: the Kotlin side (do not change)

```kotlin
// com.sttapp.data.recognition.WhisperNative
object WhisperNative {
    fun load()
    external fun whisperInit(modelPath: String, language: String, threads: Int): Long
    external fun whisperTranscribe(context: Long, pcm16kHzMono: ShortArray, sampleRate: Int): String
    external fun whisperRelease(context: Long)
}
```

Native functions are implemented in C++ with the default JNI symbol naming
(no `RegisterNatives`), so exported names must match:

| Kotlin | JNI symbol |
|--------|-----------|
| `whisperInit` | `Java_com_sttapp_data_recognition_WhisperNative_whisperInit` |
| `whisperTranscribe` | `Java_com_sttapp_data_recognition_WhisperNative_whisperTranscribe` |
| `whisperRelease` | `Java_com_sttapp_data_recognition_WhisperNative_whisperRelease` |

Semantics:

- `whisperInit(modelPath, language, threads)` loads the GGML model file and
  returns an opaque `whisper_context*` cast to `jlong`. Return `0` on failure.
- `whisperTranscribe(ctx, pcm, sampleRate)` runs
  `whisper_full` on the 16 kHz mono PCM (`jshortArray` → `const float*`),
  joins the resulting segments into a single `std::string`, and returns it as a
  `jstring` (UTF-8). The Kotlin side later splits segments for F7 paragraph
  breaks.
- `whisperRelease(ctx)` frees the context.
- `load()` is safe to call multiple times (`System.loadLibrary` is idempotent
  per class loader); the Kotlin wrapper guards it with a boolean anyway.

## 2. Building the library

Recommended: **CMake + Android Gradle Plugin's externalNativeBuild** with a
`CMakeLists.txt` that builds whisper.cpp as a static lib and a small `jni.cpp`
wrapper as a shared `whisper` target. Add:

```gradle
android {
    defaultConfig { externalNativeBuild { cmake { arguments("-DWHISPER_BUILD_EXAMPLES=OFF") } } }
    externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt") } }
}
```

ABIs: build `arm64-v8a` (required) and `armeabi-v7a` (optional). Skip x86
unless emulator dev needs it. Build once with `./gradlew :app:assembleDebug`.

Key whisper.cpp settings for low-end devices:

```cmake
-DWHISPER_COREML=OFF          # no CoreML on Android
-DWHISPER_METAL=OFF           # no GPU
-DWHISPER_BUILD_TESTS=OFF
-DWHISPER_BUILD_EXAMPLES=OFF  # we only link the library
```

Thread count is capped in Kotlin (`min(cpuCount, 4)`) to bound memory/CPU on
4 GB devices — do not allocate >4 threads in native code.

## 3. Models

GGML models: `ggml-tiny.bin` (~39 MB), `ggml-base.bin` (~74 MB),
`ggml-small.bin` (~244 MB). Plan:

- MVP 1: bundle the default model in `app/src/main/assets/models/`, copy to
  `filesDir/models/` on first launch (`WhisperTranscriber.resolveModelFile`).
- Choose the bundled size against the **80 MB APK NFR** (see
  `docs/NFR.md §2`); `base` (~74 MB) likely needs the optional-download
  approach to stay in budget.
- Future: per-size optional download with a "model trade-off" warning, then
  fully offline after one-time download.

## 4. Memory & perf guardrails (4 GB devices)

- Default `base` model; `tiny` for low RAM; `small` only with a warning.
- Single context at a time; `whisperRelease` in `finally`.
- Keep peak RSS observable: log `ActivityManager.MemoryInfo` around a full
  session during benchmarks (`docs/NFR.md §6`).

## 5. Open implementation tasks (tracked in docs/ROADMAP.md M1)

- [ ] Add `src/main/cpp/CMakeLists.txt` + `jni.cpp` wrapper
- [ ] Vendor whisper.cpp via submodule/git-tag (do not copy wholesale here)
- [ ] Wire `externalNativeBuild` in `app/build.gradle.kts`
- [ ] Model bundling + first-launch copy
- [ ] 16 kHz resampling for imported files (`MediaCodecPcmDecoder`)
