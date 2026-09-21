# Onboarding — where you are, and how to contribute

Welcome 👋. This guide gets you oriented in minutes, tells you exactly where
the project stands, and gives you a repeatable path to make a useful, safe
contribution. It targets **humans** and **AI agents** alike; if you're an
agent, the terse version is in [`AGENTS.md`](../AGENTS.md).

> Quick mental model: **Voice to PDF** is an offline, $0, MIT Android app that
> turns a recorded lecture into a print-ready PDF on-device (whisper.cpp STT →
> edit → A4 PDF). It targets students in low-connectivity regions on 4 GB ARM
> phones. Everything must work with no internet.

---

## 1. Where are we? (status snapshot)

Three product stages are defined — see `docs/PRD.md`:

| MVP | What it is | Status |
|-----|------------|--------|
| **MVP 1** | Record/import → on-device Whisper transcript → edit → export A4 PDF. P0: F1–F5; P1: F6–F8. | **In progress** |
| **MVP 2** | AI structuring of raw transcripts (headings, bullets, diff review). | Not started (future) |
| **MVP 3** | Voice-controlled phone agent (hear → plan → act → speak) via AccessibilityService. | Not started (future) |

### Current milestone: M0 ✅ → M1 🚧 (`docs/ROADMAP.md`)

- ✅ **M0 (scaffold)** — structure, build toolchain, UI shell, Room, PDF
  export, docs, CI all in place.
- 🚧 **M1 (core flow)** — make `record → transcribe → edit → export PDF` work
  end-to-end on a real device. **This is the most useful place to help.**

### What works today vs. what's stubbed

| Area | State |
|------|-------|
| UI navigation (Home / Sessions / Session / Settings) | ✅ Compose screens present |
| Settings (model size, language) | ✅ persisted via SharedPreferences |
| Session list + storage | ✅ Room `sessions` table |
| PDF export (A4, 11pt, 1.5 line spacing, header) | ✅ `AndroidPdfExporter` |
| Paragraph breaks on pause > 2 s | ✅ `TranscriptFormatter` + unit test |
| Audio recording (mic) | ✅ `AndroidAudioRecorder` (MediaRecorder) |
| **Transcription (F2)** | ⚠️ **NOT wired** — needs `libwhisper.so` + bundled model (M1) |
| Import MP3/OGG (F1) | ✅ decode + 16 kHz resampler implemented; verify on device |
| **End-to-end happy path** | ❌ blocked by transcription not being wired |

> ⚠️ **"Where you are" surprise:** because the native STT isn't wired yet,
> tapping "Start recording → Stop" puts the session into `ERROR` (the missing
> `.so`/model is caught). This is expected until M1. The project has **never
> been compiled on a machine that has a JDK/Android SDK**, so even
> `./gradlew assembleDebug` on a real toolchain is unverified.

---

## 2. Map of the repository (you are here)

```
STT-app/
├── AGENTS.md                  # terse agent instructions (start here if you're an AI agent)
├── app/
│   ├── build.gradle.kts       # module build: deps, arm64-only ABI, conditional signing
│   ├── proguard-rules.pro     # R8 rules (keeps WhisperNative JNI class)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/sttapp/
│       │   │   ├── STtApp.kt / MainActivity.kt
│       │   │   ├── core/      # PURE KOTLIN — models, interfaces, TranscriptFormatter
│       │   │   ├── data/      # Android impls — Room, whisper.cpp JNI, PdfDocument, MediaRecorder
│       │   │   ├── di/        # Hilt modules
│       │   │   └── ui/        # Compose screens + ViewModels
│       │   ├── cpp/           # whisper.cpp native STT (CMake + jni.cpp + submodule)
│       │   └── res/
│       ├── test/              # JVM unit tests  (core/format/TranscriptFormatterTest)
│       └── androidTest/       # On-device smoke test (ExampleInstrumentedTest)
├── ml/                        # ML docs, download-models.sh, setup-whisper.sh
├── docs/                      # All the documentation (see §3)
├── gradle/libs.versions.toml  # dependency version catalog
├── .github/                   # CI/CD workflows + Dependabot + issue/PR templates
└── .gitmodules                # whisper.cpp pinned submodule
```

**Golden rule:** `core/` never imports Android. Interfaces live there
(`Transcriber`, `AudioRecorder`, `PdfExporter`…); the platform implementations
live in `data/`. Dependency flow is always inward: `ui → data → core`.

---

## 3. Read-first documentation map

| If you want to… | Read |
|-----------------|------|
| Understand product goals, scope, NFRs, risks | `docs/PRD.md` |
| See the layered architecture, data flow, diagrams | `docs/ARCHITECTURE.md` |
| Know *why* each choice was made (and whether you may change it) | `docs/DECISIONS.md` (ADRs) |
| See performance/size/battery targets and how to measure | `docs/NFR.md` |
| Know what's next and what "done" means | `docs/ROADMAP.md` |
| Test strategy (unit/device/manual, CI device approach) | `docs/TESTING.md` |
| Set up the build, signing, models | `docs/SETUP.md` |
| ML runtime + model details | `ml/README.md` |
| Report a vulnerability | `SECURITY.md` |

---

## 4. Set up your machine (one time)

Prerequisites (details in `docs/SETUP.md`):

1. **JDK 17** and **Android Studio** (current stable).
2. Install the **Android SDK** (platform 36) — Studio does this.
3. Install **NDK + CMake** (SDK Manager → SDK Tools). Needed for whisper.cpp.
4. Clone **with submodules** and fetch models:

   ```bash
   git clone --recurse-submodules git@github.com:Franck-Sorel/voice-to-pdf.git
   cd voice-to-pdf
   bash ml/setup-whisper.sh --models   # submodule + ggml-base-q8_0.bin (~78 MB)
   ```

---

## 5. Build, test, run

```bash
./gradlew assembleDebug            # build debug APK
./gradlew test                     # JVM unit tests
./gradlew lintDebug                # lint
./gradlew connectedDebugAndroidTest# on-device tests (arm64 device/emulator only)
```

- ⚠️ **arm64-only:** the APK targets `arm64-v8a`. A plain x86_64 emulator can't
  install it. Use an arm64 device/emulator or Firebase Test Lab **physical**
  devices (`docs/TESTING.md`).
- Run checks **before** opening a PR: `./gradlew test lintDebug assembleDebug`.

---

## 6. How to contribute effectively

### a) Pick a task

Start with the **help-wanted** items in `docs/ROADMAP.md` (Milestone M1/M2)
and `CONTRIBUTING.md`. The single most impactful M1 task: **wire whisper.cpp
STT end-to-end on a device** (`ml/README.md §7` and `docs/SETUP.md §4`).

### b) Do the work safely

1. Branch from `main`: `git checkout -b feat/<short-name>` (one logical change
   per branch).
2. Follow the code conventions (§7).
3. **Keep `core/` Android-free** and **keep docs in sync** (update the `.md`
   that describes anything you change).
4. Run `./gradlew test lintDebug assembleDebug`.

### c) Open a PR correctly

- **Title must be Conventional Commits** (`feat:`, `fix:`, `docs:`, `ci:`,
  `build:`, `refactor:`, `test:`, `chore:`) — enforced by the `pr-title.yml`
  status check.
- Use the PR template (`.github/PULL_REQUEST_TEMPLATE.md`): summary, type,
  checklist, linked issue.
- Reference the PRD requirement you're addressing (e.g. "F2").
- Expect CI to run: lint, tests, build, dependency review, CodeQL, and the
  `pr-title` check.

### d) What CI enforces for you

See `README → CI/CD & hygiene`: unit tests, lint, debug + signed release
builds, 100 MB APK gate, `apksigner` verify, high-severity dependency review,
CodeQL SAST, Conventional-Commits PR titles, and Dependabot dependency bumps.

---

## 7. Code conventions (short version)

- Kotlin, strict mode, **no `Any`** — use `unknown` + narrow.
- **Named exports**; **interfaces over type aliases** for object shapes; `const`
  by default.
- No code comments unless the task requires them; KDoc on public API is fine.
- Room migrations are **append-only** — never edit `app/schemas/`.
- Never commit secrets/keystores (`.gitignore` handles them).
- Don't add interactivity to the default path (offline-first); any network is
  opt-in, minimal, PII-free telemetry only.

---

## 8. Troubleshooting / gotchas

| Symptom | Cause / fix |
|---------|-------------|
| `System.loadLibrary("whisper")` UnsatisfiedLinkError | `libwhisper.so` not built — finish M1 native build (`docs/SETUP.md §4`) |
| "Model file not found" | Run `bash ml/setup-whisper.sh --models` (bundles `ggml-base-q8_0.bin`) |
| Can't install APK on emulator | It's arm64-only → use an arm64 device/emulator or Firebase physical device |
| Build fails at CMake configure | Submodule missing → `git submodule update --init --recursive`; ensure NDK+CMake installed |
| Transcription always ends `ERROR` | Expected until STT is wired (M1) — the missing lib/model is caught |
| `kapt` errors | Project uses KSP only; don't add kapt (incompatible with AGP 9 built-in Kotlin) |

---

## 9. Decisions & how to record new ones

Existing choices are frozen as **ADRs** in `docs/DECISIONS.md` (e.g. whisper.cpp
over sherpa-onnx = ADR-015; `PdfDocument` over iText = ADR-003). To change or
add a decision, **append a new ADR** with status/context/decision/consequences
rather than editing history — and reference it from the index table.

---

## 10. Community

- Be kind — see [`CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md).
- Report vulnerabilities privately — see [`SECURITY.md`](../SECURITY.md).
- Ask questions / propose features via GitHub Issues (templates provided).
- General contribution guide: [`CONTRIBUTING.md`](../CONTRIBUTING.md).

---

*You now know where you are. Pick a small, well-scoped task from the ROADMAP,
make a clean Conventional-Commit PR, and ship it.*
