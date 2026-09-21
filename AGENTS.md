# AGENTS.md — for AI agents & Copilot working in this repo

Concise orientation so you (an agent) and any human contributor know exactly
where you are and how to contribute safely. For the full human guide see
[`docs/ONBOARDING.md`](docs/ONBOARDING.md).

## What this is

**Voice to PDF** — an offline-first Android app (Kotlin + Jetpack Compose) that
lets students in low-connectivity regions record/import a lecture, transcribe
it on-device with **whisper.cpp**, edit it, and export a print-ready A4 PDF.
No cloud, no account, $0, MIT. Future: MVP 2 = AI structuring, MVP 3 = voice
agent via AccessibilityService.

## Where we are (status)

- **MVP 1, Milestone M0 (scaffold): ~done.** Structure, toolchain (AGP 9.4 /
  Kotlin 2.3 / Gradle 9.7), UI shell, Room, PDF export all present.
  ⚠️ The project has **never been compiled on a real machine** (this sandbox
  has no JDK/Android SDK). First task anyone does must be
  `./gradlew assembleDebug test lintDebug` and fix any errors.
- **Milestone M1 (core flow): IN PROGRESS.** STT (whisper.cpp) is scaffolded
  but **not wired**: no `libwhisper.so` built, no `ggml` model bundled yet.
  So today, "Start recording → Stop" ends in an `ERROR` session until the
  native lib + model exist. See `docs/ROADMAP.md`.

## Commands

```bash
./gradlew assembleDebug                  # build debug APK (requires NDK+CMake once submodule present)
./gradlew test                           # JVM unit tests (TranscriptFormatterTest)
./gradlew lintDebug                      # Android lint
./gradlew connectedDebugAndroidTest      # device tests (needs arm64 device/emulator — NOT x86_64)
bash ml/setup-whisper.sh --models        # fetch whisper.cpp submodule + GGML models (M1 step 1)
```

> Gotcha: the APK is **arm64-v8a-only**. A standard x86_64 CI emulator can't
> install it; device tests run on arm64 hardware / Firebase Test Lab physical
> devices (`docs/TESTING.md`). On-CI device tests live in
> `.github/workflows/firebase-test-lab.yml` — a no-op unless the
> `FIREBASE_*` secrets are set (see `docs/SETUP.md §8`).

## Repo map (you are here)

```
app/src/main/java/com/sttapp/
├── core/          # PURE KOTLIN, NO Android imports — models, interfaces, logic
│   ├── model/     # Session, WhisperModel, SupportedLanguage
│   ├── audio/     # AudioRecorder interface
│   ├── recognition/ # Transcriber interface + progress/segments
│   ├── pdf/       # PdfExporter + PdfDestination (sealed)
│   └── format/    # TranscriptFormatter (pause-based paragraph breaks)
├── data/          # Android implementations
│   ├── local/     # Room: SessionEntity, SessionDao, AppDatabase
│   ├── recognition/ # WhisperNative (JNI) + WhisperTranscriber + MediaCodecPcmDecoder
│   ├── pdf/       # AndroidPdfExporter (PdfDocument)
│   ├── audio/     # AndroidAudioRecorder (MediaRecorder)
│   └── settings/  # SettingsRepository (SharedPreferences)
├── di/            # Hilt modules (@Provides / @Binds)
├── ui/            # Compose screens + ViewModels (home/sessions/session/settings/navigation/theme)
└── STtApp.kt / MainActivity.kt
app/src/main/cpp/
├── CMakeLists.txt + jni.cpp   # whisper.cpp JNI bridge -> libwhisper.so
└── whisper.cpp/               # pinned git SUBMODULE (v1.9.3)
ml/               # ML docs + download/setup scripts
docs/             # PRD, ARCHITECTURE, DECISIONS(ADRs), NFR, TESTING, ROADMAP, SETUP, ONBOARDING
```

## Hard rules for this repo

- **`core/` must stay Android-free.** A new `import android.*` in `core/` is a
  bug (see ADR/review history — we removed `android.net.Uri` from
  `PdfExporter`). Interfaces for Android-y things belong in `core/` (names,
  types), implementations go in `data/`.
- **Offline-first & privacy.** All P0 features work in airplane mode. `INTERNET`
  is declared only for **opt-in, minimal, PII-free** telemetry/update checks —
  never add network use in the default path.
- **No comments** unless the task explicitly asks (follow repo file conventions;
  KDoc on public API is welcome).
- **Conventional Commits** for commit/PR titles: `feat fix docs build ci chore
  refactor test`. Enforced by `pr-title.yml`.
- **Run `./gradlew test` and `./gradlew lintDebug`** before declaring work done.
- **Never commit** keystores, `.env`, `keystore.properties`, secrets
  (`.gitignore` covers them; commit `keystore.properties.example` instead).
- **Room migrations are append-only** — never hand-edit `app/schemas/`.
- **Don't delete/rename existing `.md` docs** without a strong reason; keep
  docs (README, SETUP, ml/README, DECISIONS) in sync with code changes.
- Follow ADRs in `docs/DECISIONS.md` (e.g. whisper.cpp not sherpa-onnx — ADR-015;
  don't "improve" these without a new superseding ADR). Record new decisions as
  a new ADR.

## CI & releases (workflows are the source of reliability)

- A green run = trustworthy artifact. Required checks: `unit-tests`, `lint`,
  `assemble`, `repo-integrity`, `docs-integrity`, `action-pin`,
  `dependency-review`, `pr-title`.
- **Never use `secrets` in an `if:`** (GitHub forbids it). Gate jobs on
  `vars.X == 'true'`, steps on `env.X`. This already bit us once (0s failures).
- Version comes from the **git tag** at release: `release.yml` reads
  `v1.2.3` → `-PversionName=1.2.3 -PversionCode=10203`. Local builds still
  work (defaults in `app/build.gradle.kts`).
- Release is self-contained: `build-signed` re-downloads the model, builds the
  signed APK, `apksigner`-verifies, checks ≤100 MB + arm64-only + bundled
  model, computes SHA-256, and publishes a DRAFT release. `workflow_dispatch`
  is a dry-run (builds + verifies, no publish).

## Where to look for decisions & progress- Requirements: `docs/PRD.md` (feature IDs like F2 = transcription)
- Why we chose the stack: `docs/DECISIONS.md` (ADRs)
- Architecture & agent design: `docs/ARCHITECTURE.md`
- Non-functional targets + measurement: `docs/NFR.md`
- Roadmap / definition of done: `docs/ROADMAP.md`
- Build/sign/model steps: `docs/SETUP.md`, `ml/README.md`

## Common traps

- If a task says "make transcription work", the native lib + model are NOT
  present — you must build `libwhisper.so` and run `ml/setup-whisper.sh
  --models`, which needs a machine with NDK (⚠️ user toolchain, likely not in
  your sandbox).
- `media-codec` decode materializes full PCM in RAM — don't regress the memory
  bound for long lectures.
- Don't add an x86_64 ABI to the release build (size budget ≤ 100 MB / NFR §2).
