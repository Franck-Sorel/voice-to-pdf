# Architecture Decision Records

Each entry follows a lightweight ADR format: **Status / Context / Decision /
Consequences**. New decisions are appended; existing ones are amended by
superseding records rather than rewriting history.

| ID | Decision |
|----|----------|
| [ADR-001](#adr-001-offline-first-no-network-dependencies) | Offline-first: no network dependencies in MVP 1 |
| [ADR-002](#adr-002-whispercpp-via-jni-for-transcription) | whisper.cpp via JNI — the STT runtime (see [ADR-015](#adr-015-whispercpp-ggml-restored-as-the-on-device-stt-runtime)) |
| [ADR-003](#adr-003-android-pdfdocument-not-itext) | Android `PdfDocument` for PDF export |
| [ADR-004](#adr-004-single-app-module-for-mvp-1) | Single `:app` module for MVP 1 |
| [ADR-005](#adr-005-room-hilt-ksp-for-storage-and-di) | Room + Hilt + KSP for storage and DI |
| [ADR-006](#adr-006-post-hoc-not-real-time-transcription) | Post-hoc transcription only |
| [ADR-007](#adr-007-audio-retention-is-opt-in) | Audio retained only if user opts in |
| [ADR-008](#adr-008-agp-9-built-in-kotlin) | AGP 9 built-in Kotlin (no `kotlin-android` plugin) |
| [ADR-009](#adr-009-sherpa-onnx-for-on-device-stt) | ~~sherpa-onnx for STT~~ → superseded by [ADR-015](#adr-015-whispercpp-ggml-restored-as-the-on-device-stt-runtime) |
| [ADR-010](#adr-010-sherpa-onnx-piper-for-on-device-tts) | Piper for on-device TTS (MVP 3, deferred; re-evaluate runtime) |
| [ADR-011](#adr-011-llamacpp-for-the-on-device-llm-planner) | llama.cpp (Q4 Phi-3-mini / Gemma-2-2B) for the LLM planner |
| [ADR-012](#adr-012-accessibilityservice-for-ui-execution) | AccessibilityService for UI execution |
| [ADR-013](#adr-013-native-android-only-rejected-alternatives) | Native Android only (rejected: Flutter/RN, vision agent, ADB/root/Shizuku, cloud) |
| [ADR-015](#adr-015-whispercpp-ggml-restored-as-the-on-device-stt-runtime) | whisper.cpp + GGML q8_0 restored as the on-device STT runtime |

---

## ADR-001: Offline-first, no network dependencies

**Status:** Accepted (MVP 1); **amended** to allow opt-in telemetry

**Context:** Target users have unreliable/no internet. PRD requires 100% of P0
features to work in airplane mode, and cloud processing is explicitly out of
scope for MVP 1.

**Decision:** The app is **offline-first** — every P0 feature (record →
transcribe → edit → export PDF) works fully in airplane mode; model files are
bundled into the APK. Networking is limited to **opt-in, minimal, PII-free
diagnostics and update checks**, sent only when the user consents *and*
connectivity exists (see `docs/NFR.md "Telemetry & privacy"`). Declares
`INTERNET` permission for this, but no feature depends on it.

**Consequences:** Small-ish APK, no mandatory backend, strong privacy story —
but not literally "no INTERNET permission." The CI gate that *failed* on the
presence of `INTERNET` was removed accordingly. Model quality is capped by
what fits the ~100 MB release ceiling; larger LLM models are deferred to
MVP 2/3 as optional downloads.

---

## ADR-002: whisper.cpp via JNI for transcription

**Status:** ~~Superseded by ADR-009~~ **Restored as the STT runtime by [ADR-015](#adr-015-whispercpp-ggml-restored-as-the-on-device-stt-runtime)**

> This record was briefly superseded by ADR-009 (sherpa-onnx). Real model-size
> data showed sherpa-onnx's ONNX exports are too large (base int8 ≈ 152 MB) to
> bundle for broad-device distribution, so whisper.cpp + GGML is the STT
> runtime again — see ADR-015.

**Context:** Need on-device Whisper (tiny/base/small) on CPU with no GPU, on
4 GB RAM devices, no Google Play Services, Apache-2.0 licensing. Options:
whisper.cpp (JNI), whisper-kt, Google ML Kit (requires GMS, cloud-adjacent),
server (out of scope).

**Decision:** Use **whisper.cpp** compiled for Android ABIs and bridged through
a thin JNI wrapper (`WhisperNative`). Ship `tiny`, `base`, `small` GGML
models; default to `base`.

**Consequences:** Best control over CPU threads and memory; fully offline;
Apache-2.0. Cost: we maintain a native build (see `ml/README.md`) and the
JNI symbol contract must match exactly. A `System.loadLibrary` failure is
handled lazily so the app still launches without the `.so`.

---

## ADR-003: Android PdfDocument, not iText

**Status:** Accepted

**Context:** PDF export (F4) needs A4, 11 pt, 1.5 line spacing, header with
title/date. Candidates: Android `PdfDocument` (built-in, Apache-2.0) vs iText 7
(AGPL). The project is MIT-licensed; linking an AGPL library would impose
AGPL obligations on users distributing the app.

**Decision:** Use the platform's built-in `android.graphics.pdf.PdfDocument`.
Layout stays minimal (single column, word wrap, page breaks) to satisfy the
PRD and printer-reliability risk.

**Consequences:** Zero dependency cost, no AGPL conflict, APK stays small.
Limits: no tables, no embedded fonts, manual text layout. Revisit iText (or
`com.itextpdf:pdfhtml`) only if MVP 2 presentation mode demands more layout
control.

---

## ADR-004: Single `:app` module for MVP 1

**Status:** Accepted (revisit at MVP 2)

**Context:** MVP 2 may ship as a separate APK/module. The MVP 1 feature set is
small enough to live in one module; multi-module Gradle adds build complexity.

**Decision:** Keep one `:app` module with clear package boundaries
(`core/`, `data/`, `ui/`, `di/`). The domain interfaces in `core/` are the
future seam: splitting into `:core`, `:recognition`, `:pdf` modules later is a
mechanical move that does not change the interfaces.

**Consequences:** Faster to build and navigate today. The module split is
deferred until MVP 2 structuring code actually needs its own build artifact.

---

## ADR-005: Room + Hilt + KSP for storage and DI

**Status:** Accepted

**Context:** Need local session persistence (F5) and dependency injection for
testability. Room and Hilt are the standard Android choices; both support KSP,
which is required under AGP 9 built-in Kotlin (kapt is incompatible).

**Decision:** Room (SQLite) for storage, Hilt for DI, all annotation
processing via **KSP**. Room schema exports to `app/schemas/` (append-only).

**Consequences:** Standard, well-documented stack. Enums are stored as strings
(no type converters needed). No kapt anywhere.

---

## ADR-006: Post-hoc, not real-time, transcription

**Status:** Accepted

**Context:** PRD explicitly excludes real-time transcription. On-device
Whisper on a 4 GB device cannot keep up with live audio reliably.

**Decision:** Transcription runs only after recording stops, on
`Dispatchers.Default`, with progress reported as `processedMs / totalMs`.

**Consequences:** Simpler concurrency (one native call at a time), predictable
battery/CPU profile, and no partial-transcript UI complexity. UX trade-off:
the user waits after stopping — surfaced in the UI with a progress bar.

---

## ADR-007: Audio retained only if user opts in

**Status:** Accepted

**Context:** PRD storage rule: transcripts as plain text; audio retained only
if the user opts in. Audio files are large and sensitive.

**Decision:** Recordings live in `cacheDir` and are pruned after a successful
transcription unless the user explicitly chooses to keep the audio (future F-flag
on the session).

**Consequences:** Small storage footprint and a stronger privacy posture.
Trade-off: re-transcribing an old session needs the original audio, which by
default is gone — acceptable for MVP 1 (edit the transcript instead).

---

## ADR-008: AGP 9 built-in Kotlin (no `kotlin-android` plugin)

**Status:** Accepted

**Context:** AGP 9.0+ ships built-in Kotlin and removed compatibility with the
standalone `org.jetbrains.kotlin.android` plugin. Building on the current
stable toolchain (AGP 9.4, Gradle 9.7) means adopting built-in Kotlin.

**Decision:** Apply only `com.android.application`, plus
`org.jetbrains.kotlin.plugin.compose` (version = Kotlin version), `ksp`, and
`hilt` plugins. Kotlin version is pinned to 2.3.21 so KSP (2.3.11) stays
compatible.

**Consequences:** No `kotlinOptions` block; set `compileOptions` to Java 17.
If the project ever downgrades to AGP 8.x, the `org.jetbrains.kotlin.android`
plugin must be re-added — noted here so nobody is surprised later.

---

## ADR-009: sherpa-onnx for on-device STT

**Status:** ~~Accepted~~ **Superseded by [ADR-015](#adr-015-whispercpp-ggml-restored-as-the-on-device-stt-runtime)**

> Kept for history. Adopted because a flawed benchmark suggested sherpa-onnx
> was ~50× faster than whisper.cpp. Real model-size data overrides it: see
> ADR-015.

**Context:** The app transcribes lectures (MVP 1) and, from MVP 3, hears voice
commands for a phone agent — all offline on 4 GB devices. Early candidate
whisper.cpp measured far slower on Android than sherpa-onnx for the *same*
Whisper model.

**Decision:** Use **sherpa-onnx** (Whisper `base` exported to ONNX) for speech
recognition. A single C library with official Kotlin/Java bindings for
Android. No GMS dependency (unlike `SpeechRecognizer`).

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| whisper.cpp | Believed ~50× slower (measured RTF 0.13 vs 3.52) — later shown to be a misconfigured whisper.cpp benchmark artifact (see ADR-015). |
| Vosk | Worse accuracy (~15%+ WER on clean audio) |
| Google Speech API / ML SpeechRecognizer | Needs network + per-character cost + privacy violation; `SpeechRecognizer` needs GMS. Violates offline-first (ADR-001). |

**Consequences (overtaken):** The claimed benefits (single runtime for STT+TTS,
small `.onnx`) were undercut by real sizes: **base int8 ≈ 152 MB**, **tiny int8
≈ 98 MB** — too large to bundle under the ~100 MB release ceiling. Replaced by
whisper.cpp + GGML (ADR-015).

---

## ADR-010: Piper for on-device TTS

**Status:** Accepted (MVP 3, **deferred**; re-evaluate runtime at MVP 3)

**Context:** The MVP 3 agent must speak confirmations ("Done. Email sent.")
offline. Candidates: cloud TTS (ElevenLabs/Azure), Android's default TTS,
Kokoro, or a Piper voice.

**Decision:** Use a **Piper voice** for on-device TTS. The original ADR
chose Piper *through sherpa-onnx* to share STT's runtime — but STT is now
whisper.cpp (ADR-015), so the TTS runtime is reopened. Recommended at MVP 3:
a stand-alone Piper engine (Piper is MIT and engine-agnostic). This decision
is recorded but intentionally deferred.

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| Cloud TTS (ElevenLabs, Azure) | 300–800 ms network latency, $5–330/mo, privacy violation (sends speech to a server) |
| Android default TTS | Robotic quality, no neural voices |
| Kokoro | 2× slower, 5× larger model; marginal quality gain not worth it on 4 GB RAM |

**Consequences:** One independent, MIT TTS dependency for MVP 3. TTS is
loaded only during speech output. Exact engine (Piper-standalone vs
sherpa-onnx for TTS only) to be chosen at MVP 3 kickoff.

---

## ADR-011: llama.cpp for the on-device LLM planner

**Status:** Accepted (MVP 3)

**Context:** The agent's "plan → act" loop needs a local LLM (single prompt,
one tool call) on a 4 GB phone with no server process and no orchestration
framework.

**Decision:** Use **llama.cpp** with a Q4-quantized model — **Phi-3-mini 3.8B**
as default on 6 GB devices, **Gemma-2-2B Q4 (~1.5 GB RAM)** as the "low-RAM"
option on 4 GB devices. The C API is called directly from Kotlin.

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| Ollama | Server-side inference framework; requires a running daemon. Designed for desktop/server. On Android you'd run a local HTTP server for no reason. |
| LangChain / LangChain4j / Spring AI | Orchestration frameworks for multi-step RAG pipelines. Our loop is `transcript → LLM → action → result` — one prompt, one tool call. They add 50+ MB of Java deps and cognitive overhead for zero benefit. |
| MLC-LLM | Viable, but less mature on Android and more build complexity |

**Consequences:** The LLM is the RAM bottleneck (~2.5 GB for Phi-3-mini Q4).
It must be loaded/unloaded on demand and is optional/absent on 4 GB default
flows. See resource budget in `docs/ARCHITECTURE.md`.

---

## ADR-012: AccessibilityService for UI execution

**Status:** Accepted (MVP 3)

**Context:** The agent must open apps, tap, type, and scroll on the student's
phone — in production, without a cable, without root, without any developer
setup.

**Decision:** Execute UI actions through the platform **AccessibilityService**
API. The user grants the permission once in Android Settings.

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| ADB | Requires USB or wireless debugging — breaks in the field, not viable for students |
| Root | Not available on student devices |
| Shizuku | Requires one-time ADB setup, less stable |
| Vision-based agent (screenshot + VLM) | Requires a 7B+ VLM (4+ GB extra RAM), ~10× slower per step. The accessibility tree is structured, labeled UI elements — free, no image understanding needed |

**Consequences:** Zero-friction, production-viable path to control other apps.
The accessibility tree also gives us app/activity/button labels for free.
Brings Play Store policy and privacy responsibility: the service must be
visibly disclosed, and the agent must not read personal content (see the hard
boundary in `docs/PRD.md §7`).

---

## ADR-013: Native Android only (rejected alternatives)

**Status:** Accepted (whole app)

**Context:** The stack must interop with AccessibilityService, NDK, Camera2,
and JNI. Every 100 ms matters for a voice agent.

**Decision:** **Kotlin** on a native Android app. Reject cross-platform
frameworks and every alternative execution path.

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| Swift | iOS-only |
| Flutter / React Native | Can't access AccessibilityService directly; JNI/NDK bridge for on-device ML adds 50–200 ms latency per call. For a voice agent where every 100 ms matters, native is non-negotiable |
| Java | No advantage, worse ergonomics |

**Rejected stack components (explicit):**

| Rejected | Reason |
|----------|--------|
| Flutter / React Native | See above — AccessibilityService + JNI latency |
| Ollama | Server daemon, desktop/server design |
| LangChain4j / Spring AI | 50+ MB deps for a one-prompt loop |
| Vision-based agent (screenshot + VLM) | 7B+ VLM, 4+ GB RAM, 10× slower; accessibility tree is free |
| ADB / Root / Shizuku | Not production-viable on student devices |
| Google Speech API / Cloud STT | Network + cost + privacy; offline-first is the point |
| sherpa-onnx (for STT) | ONNX exports too large to bundle at target accuracy (see ADR-015) |

**Consequences:** Single platform, single codebase, best performance and
framework access. Cost: no cross-platform reuse — acceptable for a student
niche on Android.

---

## ADR-015: whisper.cpp + GGML restored as the on-device STT runtime

**Status:** Accepted (replaces ADR-009 for STT; restores ADR-002)

**Context:** ADR-009 chose sherpa-onnx because a benchmark appeared to show it
~50× faster than whisper.cpp. On re-measurement with real artifacts and sizes:

- sherpa-onnx's official Whisper ONNX exports are far larger than their
  nominal sizes suggest: **base int8 ≈ 152 MB** (encoder 27.8 + decoder
  124.6 MB), **tiny int8 ≈ 98 MB**.
- The "RTF 0.13 vs 3.52" figure is **not apples-to-apples** (different models,
  different runtimes) and the whisper.cpp 3.52 number is a misconfigured-build
  artifact — a properly built whisper.cpp easily beats real-time.
- Target is a **reliable, well-designed, reusable STT layer** that must run on
  the *majority* of (low/mid-range) African phones — so install size and
  accuracy both matter, and cost $0.

**Decision:** Use **whisper.cpp** (MIT) as the on-device STT runtime with
**GGML** int8 models, bridged through the minimal `WhisperNative` JNI wrapper
behind the `Transcriber` interface. Default bundle: **`ggml-base-q8_0.bin`
(~78 MB)** → ~99 MB release APK (within the ~100 MB ceiling); low-RAM
fallback: `ggml-tiny-q8_0.bin` (~41.5 MB) → ~60 MB.

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| sherpa-onnx (ONNX) | Real sizes (base int8 ≈ 152 MB, tiny int8 ≈ 98 MB) can't meet base accuracy + ~100 MB / broad-device fit |
| Vosk | Worse accuracy (~15%+ WER) — unacceptable for lecture reliability |
| faster-whisper / CTranslate2 | Not Android-viable (no Android support) |
| Cloud STT | Network + per-char cost + privacy; fails offline-first (ADR-001) |

**Consequences:** Base-level transcription quality at a size that installs on
the majority of target devices, offline day-one. Cost: we own the native build
(CMake/NDK, `arm64-v8a`) and the JNI contract — documented in `ml/README.md`.
The `Transcriber`/`PcmDecoder` seam makes this a clean, reusable `:recognition`
layer. TTS (MVP 3) is decoupled (ADR-010).

---

