# ML runtimes & models (sherpa-onnx, llama.cpp)

This directory is the home for the machine-learning side of the app. It holds
the integration plan, model manifests, and build scripts for the three on-device
runtimes:

| Runtime | Purpose | Model | Used by |
|---------|---------|-------|---------|
| **sherpa-onnx** | Speech-to-text (STT) | Whisper `base` exported to ONNX | MVP 1 + MVP 3 |
| **sherpa-onnx** | Text-to-speech (TTS) | Piper voice ONNX | MVP 3 |
| **llama.cpp** | LLM planner | Phi-3-mini 3.8B Q4 / Gemma-2-2B Q4 (GGUF) | MVP 2 + MVP 3 |

Rationale for choosing these (and rejecting whisper.cpp / Vosk / Ollama /
LangChain / cloud APIs) is recorded in `docs/DECISIONS.md` ADR-009 → ADR-013.

> The original scaffold shipped a whisper.cpp JNI stub (`WhisperNative.kt`).
> That approach was **superseded by ADR-009** — sherpa-onnx is ~50× faster than
> whisper.cpp for the same Whisper model on Android. The real implementation
> uses sherpa-onnx's official Android bindings instead of a custom JNI bridge.

---

## 1. STT — sherpa-onnx (Whisper base via ONNX)

- **Library:** `com.k2fsa.sherpa.onnx:sherpa-onnx` (official Android AAR with
  Kotlin/Java bindings). No GMS dependency.
- **Model:** Whisper `base` exported to ONNX (int8 or float32; int8 preferred
  for RAM). Audio is fed as 16 kHz mono PCM — the `PcmDecoder` pipeline
  (`MediaCodecPcmDecoder`) already produces this.
- **API shape (verify exact names against the pinned sherpa-onnx release):**
  `OfflineRecognizer` / `OfflineRecognizerConfig` / `OfflineStream`, feeding
  the 16 kHz PCM and reading segment results.
- **Performance:** RTF ~0.13 on a mid-range device (vs ~3.52 for whisper.cpp)
  — well under the ≤ 1.5× real-time NFR.
- **Threading:** cap threads (Android AAR defaults are fine; keep ≤ 4 on 4 GB
  devices) and run inference off the main thread.

### Integration steps

1. Add the sherpa-onnx AAR to `gradle/libs.versions.toml` + `app/build.gradle.kts`.
2. Bundle Whisper ONNX + `tokens.txt` under `app/src/main/assets/models/`.
3. Copy models to `filesDir/models/` on first launch (the existing
   `WhisperTranscriber.resolveModelFile` flow, adapted to the AAR API).
4. Replace the `WhisperNative` JNI stub with a `SherpaOnnxTranscriber`
   implementing the same `Transcriber` interface (keeps `core/` untouched).

## 2. TTS — sherpa-onnx (Piper)

- **Library:** same sherpa-onnx AAR — zero additional dependency; shares the
  same ONNX Runtime session as STT.
- **Model:** a Piper voice `.onnx` (small, ~20–60 MB depending on voice) +
  its `.onnx.json` config.
- **API shape (verify against release):** `OfflineTts` / `OfflineTtsConfig`.
- **Loading:** keep TTS loaded only while speaking; unload afterward (see the
  resource budget).

## 3. LLM planner — llama.cpp

- **Library:** llama.cpp built for Android (or the prebuilt llama-android
  artifacts). The C API is called directly from Kotlin via JNI — no server, no
  Ollama, no LangChain.
- **Models (GGUF, Q4_K_M):**

| Model | RAM | Notes |
|-------|-----|-------|
| Phi-3-mini 3.8B Q4 | ~2.5 GB | Default on 6 GB devices |
| Gemma-2-2B Q4 | ~1.5 GB | "Low-RAM" default on 4 GB devices |

- **Loading:** load on demand (when the user speaks / triggers structuring),
  unload after the response. This is the single biggest RAM lever.
- **Prompt:** see `docs/PRD.md §5.6` (structuring) and the agent plan/act loop
  (`docs/ARCHITECTURE.md §10`).

## 4. Resource budget (4 GB device)

| Component | RAM |
|-----------|-----|
| STT (Whisper base) | ~200 MB |
| TTS (Piper) | ~150 MB |
| LLM (Phi-3-mini Q4) | ~2.5 GB |
| LLM (Gemma-2-2B Q4) | ~1.5 GB |

STT and TTS are loaded independently of the LLM. Acceptance gates live in
`docs/NFR.md §9`.

## 5. APK-size interplay

Whisper `base` ONNX (~74 MB) + a bundled Piper voice (~20 MB) will exceed the
80 MB APK NFR if both are embedded. Decide per `docs/NFR.md §2`: bundle `tiny`
STT in the base APK and offer `base` (and later Piper/LLM models) as a
one-time optional download.

## 6. Open implementation tasks (tracked in docs/ROADMAP.md M1/M5)

- [ ] Add sherpa-onnx AAR dependency + model bundling script
- [ ] Replace `WhisperNative` JNI stub with `SherpaOnnxTranscriber`
- [ ] Implement 16 kHz resampling in `MediaCodecPcmDecoder`
- [ ] Piper TTS bridge (`TtsEngine`) — MVP 3
- [ ] llama.cpp integration + on-demand load/unload — MVP 2/3
- [ ] Model download flow (optional, with consent) — MVP 1 M2
