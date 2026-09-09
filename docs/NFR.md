# Non-Functional Requirements & Measurement

These targets come from `docs/PRD.md`. Each has: the **target**, **how we
measure it**, and **when it's checked** (CI, release gate, or manual
benchmark).

## 1. Transcription speed

| Target | Measurement |
|--------|-------------|
| ≤ 1.5× real-time on a 4 GB RAM device (30 min audio → ≤ 45 min) | Instrumented benchmark: transcribe a fixed 10-min mono 16 kHz WAV with `base` model on a real device; record wall time. Store results in `docs/benchmarks/`. |

- Benchmarked **per model size** (tiny / base / small) and per ABI (arm64-v8a
  is the target; armeabi-v7a optional).
- Run at every release on a reference device list (see `docs/TESTING.md`).

## 2. APK size ≤ 100 MB (bundled `base`)

| Target | Measurement |
|--------|-------------|
| ≤ 100 MB release APK | `ls -lh app/build/outputs/apk/release/app-release.apk` in CI; fail the release job if exceeded. |

Budget breakdown (indicative — whisper.cpp + GGML `base` q8_0):

| Component | Size |
|-----------|------|
| App code + Compose | ~12 MB |
| libwhisper.so (arm64-v8a) | ~8 MB |
| `ggml-base-q8_0.bin` (bundled default, INT8) | ~78 MB |
| **Total** | **~98 MB** ✅ within ceiling |

- The 100 MB ceiling matches the realistic Play base-limit and broad-device
  install needs. It is attainable because whisper.cpp GGML models are far
  smaller than sherpa-onnx's ONNX exports (which were the original blocker —
  see `docs/DECISIONS.md` ADR-015), and it replaces the earlier unachievable
  80 MB number that no bundled ONNX model could meet.
- Default bundle: `base` q8_0 (~78 MB). Low-RAM optional/fallback:
  `tiny` q8_0 (~41.5 MB) → ~60 MB APK. A bundled Piper TTS voice (MVP 3) would
  add ~20 MB — keep it an optional download.

## 3. Battery ≤ 15% per session

| Target | Measurement |
|--------|-------------|
| ≤ 15% drain per 30-min record + transcribe session | Manual benchmark: fully charge, run one record+transcribe cycle, measure delta; on reference devices. Log CPU busy-time via `adb shell dumpsys batterystats`. |

Optimizations if exceeded:
- Cap Whisper threads to `min(cpuCount, 4)` (already in `WhisperTranscriber`).
- Screen-off tolerant background via a foreground service (post-scaffold task).
- Do not poll progress faster than needed; keep UI light during inference.

## 4. Storage

| Target | Measurement |
|--------|-------------|
| Transcripts plain text; audio opt-in only | Code review + instrumented test asserting `cacheDir` is pruned after READY. |

## 5. Offline (airplane mode)

| Target | Measurement |
|--------|-------------|
| 100% of P0 works in airplane mode | Manual test matrix: full happy path in airplane mode on reference devices. All speech/PDF features are fully local and never require a network call. |

`INTERNET` permission is declared for **opt-in telemetry and update checks
only** (see "Telemetry & privacy" below); no feature depends on it.

## 6. Memory (4 GB devices)

| Target | Measurement |
|--------|-------------|
| No OOM on ≥ 4 GB RAM devices | RAM gate at launch (< 4 GB → "requires 4 GB" screen). Instrumented test on low-RAM emulator (`adb shell setprop dalvik.vm.heapsize ...`). Monitor `android.util.MemoryInfo`. |

Whisper `base` on 4 GB devices is expected to peak ~600–900 MB RSS during
inference; `small` may exceed it — hence the model-size warning in settings.

**Bounded-audio note:** `MediaCodecPcmDecoder` currently materializes the full
decoded PCM in RAM (~2× audio-duration bytes). For devices at 4 GB and very
long recordings, stream PCM to whisper.cpp in chunks or cap session length
(this is a known 4 GB risk — see `docs/ARCHITECTURE.md §4`).

## 7. Min Android & device reach

| Target | Measurement |
|--------|-------------|
| minSdk 26 (Android 8.0) | Enforced by `app/build.gradle.kts` (`minSdk = 26`). |
| Mid-range 4 GB RAM | Manual matrix below. |

## 8. Stability / crash-free

| Target | Measurement |
|--------|-------------|
| ≥ 70% of sessions complete without crash | Play Console crash-free users + session-completion funnel metric (local analytics event, opt-in, crash-safe). |
| Rating ≥ 4.2 | Play Store reviews. |

---

## Reference device matrix (manual benchmark)

| Device | OS | RAM | Notes |
|--------|----|-----|-------|
| Pixel 6a / mid-range reference | 13+ | 6 GB | Performance reference |
| Low-end Android Go device | 10 | 2 GB | Must show RAM gate, never crash |
| Old flagship (e.g. Galaxy S8-class) | 8 | 4 GB | Core perf target |
| Budget 2023 device | 12 | 4 GB | Core perf target |

## When each gate runs

| Gate | Frequency |
|------|-----------|
| Unit tests + lint + assembleDebug | Every commit (CI) |
| Release APK size check (≤ 100 MB) + signed release publish | Every tag (CI) |
| Transcription speed + battery benchmarks | Every release |
| Manual offline happy path | Every release |

---

## Telemetry & privacy (opt-in)

The app is **offline-first**: all P0 features work with no network. It may
additionally send **minimal, non-identifying diagnostics** only when **all**
of these hold:

- The user has **explicitly opted in** (a visible, in-app toggle/consent;
  never silently without consent).
- Connectivity currently exists.
- The payload is **least-data and PII-free**: session completion/CRT counts,
  an anonymous install id (no IMEI/phone/email/location), and coarse RTF/RAM
  benchmark samples. No audio, no transcript text, no contacts.

| Requirement | Measurement |
|-------------|-------------|
| Opt-in before any network send | Manual review + instrumented test: no send before consent |
| Least-data / no PII | Code review of the payload builder; strip audio/text/identifiers |
| No functional dependence on network | Offline full-happy-path test in airplane mode (NFR §5) |

Until this is built, the app declares `INTERNET` but sends nothing. The
"share diagnostics via WhatsApp" flow (export a local log) is the zero-
infrastructure fallback for the first test rounds.

---

## 9. MVP 3 agent resource budget (additive module)

Applies when the voice agent (PRD §7) is installed. Speech targets the same
4 GB devices as MVP 1.

| Component | RAM | Notes |
|-----------|-----|-------|
| Android OS + system | ~1.2 GB | Baseline |
| whisper.cpp STT (Whisper base q8_0) | ~150–300 MB | Loaded only during transcription |
| Piper TTS | ~150 MB | Loaded only during speech (MVP 3) |
| llama.cpp (Phi-3-mini Q4) | ~2.5 GB | **Bottleneck** |
| App UI + AccessibilityService | ~150 MB | |
| **Total peak** | **~4.2 GB** | ⚠️ Exceeds the 4 GB budget |

**Acceptance gates (MVP 3):**

| Requirement | Target | Measurement |
|-------------|--------|-------------|
| Peak RSS on 4 GB device | ≤ 3.5 GB (safety headroom under 4 GB) | Instrumented agent-run benchmark on reference devices (`dumpsys meminfo`) |
| Low-RAM default | Gemma-2-2B Q4 (~1.5 GB) → total ~3.2 GB | Same benchmark with Gemma-2-2B active |
| LLM on-demand | Not resident when idle | Verify unload after each response (`ActivityManager.getProcessMemoryInfo`) |
| Agent end-to-end (4 GB) | STT RTF ≤ 0.15; LLM plan ≤ 5 s; TTS first-audio ≤ 300 ms | Per-stage timers in the benchmark harness |
| Sensitive-action guard | 100% of send/call/account actions require the allow dialog | Instrumented test asserting `VoiceAgent.run` blocks without confirmation |

Mitigations already decided (PRD §7.8): Gemma-2-2B instead of Phi-3-mini on
4 GB, load/unload LLM on demand, STT/TTS loaded independently, and 6 GB
devices default to Phi-3-mini.
