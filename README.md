# Voice to PDF

> Offline, free, on-device note-taking for students in low-connectivity
> environments. Record a lecture — or import an audio file — and get a
> print-ready PDF the same day. No internet, no cloud, no accounts, $0.

**MVP 1** covers the full pipeline **record → transcribe → edit → export PDF**,
100% offline on a mid-range Android phone. **MVP 2** (later, optional module)
adds AI structuring of raw transcripts into sections, bullets, and headings.
**MVP 3** (planned) turns the phone into a voice-controlled agent that opens
apps, taps, types, and scrolls from a spoken command — still fully offline.

---

## Why this app exists

Students at many African universities have mid-range Android phones (4 GB RAM)
but unreliable or absent internet. Taking notes during a 30–90 minute lecture
means slow handwriting, hiring a typist, or paying for unreliable cloud
transcription. The "last mile" — turning spoken words into a print-ready PDF —
is broken.

Voice to PDF fixes exactly that last mile: everything runs **on the device**,
so a student can capture a full lecture and walk out with a PDF the same day.

---

## Feature summary (MVP 1)

| #  | Feature | Status |
|----|---------|--------|
| F1 | Record audio in-app, or import MP3/WAV/OGG | Skeleton |
| F2 | Local transcription with Whisper (tiny/base/small) | Skeleton (JNI bridge stubbed) |
| F3 | Editable transcript field | Implemented |
| F4 | Export print-ready PDF (A4, 11 pt, 1.5 spacing, header) | Implemented |
| F5 | Local session history (Room/SQLite) | Implemented |
| F6 | Share/print PDF via Android intent | Scaffolded (SAF) |
| F7 | Auto-paragraph breaks on pause > 2 s | Implemented + unit-tested |
| F8 | Language selection (EN/FR/PT/SW/AR) | Implemented |

See [docs/PRD.md](docs/PRD.md) for the full spec, including MVP 2.

---

## Tech stack

| Layer        | Choice                                        | Why |
|--------------|-----------------------------------------------|-----|
| Language     | Kotlin                                        | Native Android + on-device ML |
| UI           | Jetpack Compose (Material 3)                  | Modern, fast to build |
| STT          | whisper.cpp (Whisper `base` GGML q8_0)        | Base accuracy at ~99 MB APK; mature portable C core (ADR-015) |
| TTS (MVP 3)  | Piper (engine TBD at MVP 3)                   | Deferred; independent of the STT runtime |
| LLM (MVP 2/3)| llama.cpp (Phi-3-mini / Gemma-2-2B Q4)        | On-device planner, no server, no framework |
| UI execution (MVP 3) | AccessibilityService (native)         | No root/ADB/Shizuku; one-time grant in Settings |
| PDF          | Android `PdfDocument` (built-in)              | Zero deps, Apache-2.0, keeps APK small |
| Storage      | Room (SQLite)                                 | Local session management |
| DI           | Hilt                                          | Standard, compile-safe |
| Build        | Gradle 9.7 + AGP 9.4 (built-in Kotlin)        | Current stable toolchain |

---

## Project structure

```
STT-app/
├── app/
│   ├── build.gradle.kts            # Module build config
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/sttapp/
│       │   │   ├── STtApp.kt / MainActivity.kt
│       │   │   ├── core/           # Pure domain: models, interfaces, logic
│       │   │   │   ├── model/      # Session, WhisperModel, SupportedLanguage
│       │   │   │   ├── audio/      # AudioRecorder interface
│       │   │   │   ├── recognition/# Transcriber interface + progress/segments
│       │   │   │   ├── pdf/        # PdfExporter interface + PrintConfig
│       │   │   │   └── format/     # TranscriptFormatter (paragraph breaks)
│       │   │   ├── data/           # Implementations (Android-dependent)
│       │   │   │   ├── local/      # Room: entity, DAO, database
│       │   │   │   ├── settings/   # Preferences-backed settings
│       │   │   │   ├── audio/      # MediaRecorder implementation
│       │   │   │   ├── recognition/# whisper.cpp STT (JNI) + PCM decoder
│       │   │   │   └── pdf/        # PdfDocument implementation
│       │   │   ├── di/             # Hilt modules
│       │   │   └── ui/             # Compose screens (theme/navigation/home/…)
│       │   ├── cpp/                # whisper.cpp native STT (CMakeLists + jni.cpp)
│       │   │   └── whisper.cpp/    #   pinned git submodule (v1.9.3)
│       │   └── res/
│       └── test/                   # JVM unit tests
├── ml/                             # ML runtimes & models (whisper.cpp, llama.cpp)
├── docs/                           # All documentation (see below)
├── gradle/libs.versions.toml       # Version catalog
├── .github/workflows/ci.yml        # CI (fetches the whisper.cpp submodule)
└── .gitmodules                     # Whisper.cpp pinned submodule
```

**The `core/` package never touches Android.** It holds the domain model and
interfaces the whole app depends on. `data/` provides Android-backed
implementations wired together by Hilt. This keeps logic unit-testable on the
JVM (e.g. `TranscriptFormatterTest`).

---

## Quick start

Prerequisites: **JDK 17**, an Android SDK (Android Studio Ladybug+ recommended).

```bash
# Build the debug APK
./gradlew assembleDebug

# Run JVM unit tests
./gradlew test

# Lint
./gradlew lintDebug
```

> ⚠️ Transcription (F2) requires building the whisper.cpp runtime (`libwhisper.so`)
> and bundling a GGML model — the scaffold ships with a stubbed STT bridge.
> See [docs/SETUP.md](docs/SETUP.md) and [ml/README.md](ml/README.md).

---

## Documentation index

| Doc | What it covers |
|-----|----------------|
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | **Start here** — status, repo map, setup, how to contribute |
| [AGENTS.md](AGENTS.md) | Terse instructions for AI agents / Copilot working in the repo |
| [docs/PRD.md](docs/PRD.md) | Full product requirements: MVP 1/2/3, scope, priorities, NFRs, success metrics, risks |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, module map, data flow, threading, offline design, agent architecture, diagrams |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Architecture Decision Records (why each choice was made) |
| [docs/NFR.md](docs/NFR.md) | Non-functional targets & how to measure them |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy: unit / instrumented / manual device matrix |
| [docs/ROADMAP.md](docs/ROADMAP.md) | MVP 1 → MVP 2 → MVP 3 milestones and definition of done |
| [docs/SETUP.md](docs/SETUP.md) | Build environment, signing, ML runtimes, APK size budget |
| [ml/README.md](ml/README.md) | whisper.cpp (STT) and llama.cpp (LLM) integration plan |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute, ground rules, milestones |
| [SECURITY.md](SECURITY.md) | How to report a vulnerability responsibly |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community guidelines |

## CI/CD & hygiene

The pipeline is the project's **source of reliability**: a green run means the
artifact is trustworthy. GitHub Actions workflows under `.github/workflows/`:

| Workflow | Runs | Purpose |
|----------|------|---------|
| `ci.yml` | every push / PR / manual | unit tests, lint (warnings-as-errors), debug+release build with **native + ABI audit** |
| `release.yml` | `v*` tag / manual dry-run | tag-derived version, **signed** APK, **`apksigner` verify**, **≤100 MB** + ABI/model audit, **SHA-256** checksums, publish **draft** GitHub Release (arm64, for Obtainium) |
| `repo-integrity.yml` | every push / PR | secrets/keystore scan, wrapper validation, core-purity, no-kapt, offline/ABI/model invariants, no `.md` deletion |
| `docs.yml` | every push / PR | internal markdown links resolve; README CI table matches workflows |
| `action-pin.yml` | every push / PR | every `uses:` pinned to `@vMajor`/SHA (no `@main`/`@latest`) |
| `codeql.yml` | push / PR / weekly | CodeQL SAST on Kotlin/Java (C++ excluded — NDK not traceable) |
| `dependency-review.yml` | every PR | fails on high-severity dependency advisories |
| `firebase-test-lab.yml` | push / PR / manual | on-device test on **physical arm64** Firebase Test Lab Pixels (no-op unless `RUN_FIREBASE=true`) |
| `pr-title.yml` | every PR | enforces Conventional Commits titles |
| `pr-labeler.yml` / `labels.yml` | PR / push | path labels + label sync |
| `issue-triage.yml` / `good-first-issue.yml` | issues | `needs-triage` + onboarding welcome |

Dependency bumps are proposed by **Dependabot** (`.github/dependabot.yml`).
Issue/PR templates auto-apply labels (`bug`, `enhancement`).

---

## Status

This is a **scaffold**: the structure, build files, domain model, Room layer,
PDF export, and UI skeleton are in place. whisper.cpp STT integration and full
end-to-end transcription are the next implementation milestones — see
[docs/ROADMAP.md](docs/ROADMAP.md).

License: [MIT](LICENSE). Report security issues via [SECURITY.md](SECURITY.md).
