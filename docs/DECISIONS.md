# Architecture Decision Records

Each entry follows a lightweight ADR format: **Status / Context / Decision /
Consequences**. New decisions are appended; existing ones are amended by
superseding records rather than rewriting history.

| ID | Decision |
|----|----------|
| [ADR-001](#adr-001-offline-first-no-network-dependencies) | Offline-first: no network dependencies in MVP 1 |
| [ADR-002](#adr-002-whispercpp-via-jni-for-transcription) | ~~whisper.cpp via JNI~~ → superseded by [ADR-009](#adr-009-sherpa-onnx-for-on-device-stt) |
| [ADR-003](#adr-003-android-pdfdocument-not-itext) | Android `PdfDocument` for PDF export |
| [ADR-004](#adr-004-single-app-module-for-mvp-1) | Single `:app` module for MVP 1 |
| [ADR-005](#adr-005-room-hilt-ksp-for-storage-and-di) | Room + Hilt + KSP for storage and DI |
| [ADR-006](#adr-006-post-hoc-not-real-time-transcription) | Post-hoc transcription only |
| [ADR-007](#adr-007-audio-retention-is-opt-in) | Audio retained only if user opts in |
| [ADR-008](#adr-008-agp-9-built-in-kotlin) | AGP 9 built-in Kotlin (no `kotlin-android` plugin) |
| [ADR-009](#adr-009-sherpa-onnx-for-on-device-stt) | sherpa-onnx (Whisper base via ONNX) for STT |
| [ADR-010](#adr-010-sherpa-onnx-piper-for-on-device-tts) | sherpa-onnx (Piper) for on-device TTS |
| [ADR-011](#adr-011-llamacpp-for-the-on-device-llm-planner) | llama.cpp (Q4 Phi-3-mini / Gemma-2-2B) for the LLM planner |
| [ADR-012](#adr-012-accessibilityservice-for-ui-execution) | AccessibilityService for UI execution |
| [ADR-013](#adr-013-native-android-only-rejected-alternatives) | Native Android only (rejected: Flutter/RN, vision agent, ADB/root/Shizuku, cloud) |

---

## ADR-001: Offline-first, no network dependencies

**Status:** Accepted (MVP 1)

**Context:** Target users have unreliable/no internet. PRD requires 100% of P0
features to work in airplane mode, and cloud processing is explicitly out of
scope for MVP 1.

**Decision:** MVP 1 ships no network stack at all — no HTTP client, no
accounts, no telemetry that leaves the device. Transcription, storage, and PDF
export are fully local. Even model files are bundled into the APK.

**Consequences:** Small APK, no backend to run or pay for, strong privacy story
("nothing leaves the device"). Trade-off: model quality is capped by what fits
in ≤ 80 MB APK; larger LLM models are deferred to MVP 2 as an optional
download.

---

## ADR-002: whisper.cpp via JNI for transcription

**Status:** ~~Accepted~~ **Superseded by [ADR-009](#adr-009-sherpa-onnx-for-on-device-stt)**

> This record is kept for history. sherpa-onnx is strictly faster than
> whisper.cpp for the same Whisper model on Android and replaces it for all
> speech work — see ADR-009/010.

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

**Status:** Accepted (supersedes [ADR-002](#adr-002-whispercpp-via-jni-for-transcription))

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
| whisper.cpp | ~50× slower on Android for the same model (measured RTF 0.13 for sherpa-onnx vs 3.52 for whisper.cpp). No reason to keep it. |
| Vosk | Worse accuracy (~15%+ WER on clean audio) |
| Google Speech API / ML SpeechRecognizer | Needs network + per-character cost + privacy violation; `SpeechRecognizer` needs GMS. Violates offline-first (ADR-001). |

**Consequences:** Same runtime powers STT (Whisper) and TTS (Piper) — one
library, one ONNX runtime, one dependency to keep updated. Model files are
`.onnx` (not GGML). The scaffold's `WhisperNative` JNI stub is superseded; the
real implementation uses the sherpa-onnx AAR (see `ml/README.md`).

---

## ADR-010: sherpa-onnx (Piper) for on-device TTS

**Status:** Accepted (MVP 3)

**Context:** The MVP 3 agent must speak confirmations ("Done. Email sent.")
offline. Candidates: cloud TTS (ElevenLabs/Azure), Android's default TTS,
Kokoro, or Piper via sherpa-onnx.

**Decision:** Use **Piper voices through sherpa-onnx** — the same library as
STT (ADR-009), same ONNX Runtime session, zero additional dependency.

**Why THIS, not the alternatives:**

| Alternative | Why not |
|-------------|---------|
| Cloud TTS (ElevenLabs, Azure) | 300–800 ms network latency, $5–330/mo, privacy violation (sends speech to a server) |
| Android default TTS | Robotic quality, no neural voices |
| Kokoro | 2× slower, 5× larger model; marginal quality gain not worth it on 4 GB RAM |

**Consequences:** One native dependency covers both directions of speech.
TTS models are loaded only during speech output (see resource budget in
`docs/ARCHITECTURE.md`).

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
| Flutter / React Native | Can't access AccessibilityService directly; JNI bridge to ONNX adds 50–200 ms latency per call. For a voice agent where every 100 ms matters, native is non-negotiable |
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
| whisper.cpp | Strictly slower than sherpa-onnx (ADR-009) |

**Consequences:** Single platform, single codebase, best performance and
framework access. Cost: no cross-platform reuse — acceptable for a student
niche on Android.
