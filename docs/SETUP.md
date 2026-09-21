# Setup & Build

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | AGP 9 requires JDK 17+ to run the build |
| Android Studio | Current stable (Ladybug+) | Bundles the Android SDK + SDK Manager |
| Android SDK | platform 36, build-tools 36.0.0 | AGP downloads SDK components automatically |
| NDK + CMake | latest stable (SDK-managed) | Required for the whisper.cpp native build (`externalNativeBuild`) |
| Gradle | none needed | The wrapper (`./gradlew`) downloads Gradle 9.7.1 |

To install NDK + CMake: **Android Studio → SDK Manager → SDK Tools** → tick
"NDK (Side by side)" and "CMake".

Verify toolchain (these versions are pinned in `gradle/libs.versions.toml`):

- AGP `9.4.0` — note: uses **built-in Kotlin**; there is deliberately no
  `org.jetbrains.kotlin.android` plugin (see `docs/DECISIONS.md` ADR-008).
- Kotlin `2.3.21`, KSP `2.3.11`, Compose BOM `2026.08.00`.
- Room `2.8.4`, Hilt `2.60.1`, Navigation Compose `2.10.0`.

## 2. First build

```bash
./gradlew assembleDebug        # debug APK
./gradlew test                 # JVM unit tests
./gradlew lintDebug            # lint
./gradlew connectedDebugAndroidTest   # instrumented (needs device/emulator)
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

> If you open the project in Android Studio, let it sync once; it will
> auto-provision the SDK and wrapper.

## 3. Signing a release build

Never commit keystores. Generate a **permanent** release keystore once (keep
it backed up offline — losing it means you can never update sideloaded
installs), then copy `keystore.properties.example` → `keystore.properties`
(gitignored) and fill it in:

```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias voicetopdf -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12
```

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=...
keyAlias=voicetopdf
keyPassword=...
```

`app/build.gradle.kts` reads `keystore.properties` **if present** (release
builds are then signed); if absent, release builds are unsigned — CI signs via
GitHub secrets (see `.github/workflows/ci.yml`). The same keystore later
enables migrating sideloaded testers to Play with the same signing key.

## 4. ML runtime (required for transcription, F2)

Transcription uses **whisper.cpp** (GGML). The `WhisperNative` JNI bridge and
the native build (`app/src/main/cpp/CMakeLists.txt` + `jni.cpp`) are already
scaffolded. One time:

1. `bash ml/setup-whisper.sh --models` → fetches the pinned **whisper.cpp
   submodule** (v1.9.3) and `ggml-base-q8_0.bin` (~78 MB) into
   `app/src/main/assets/models/`.
2. Ensure **CMake + NDK** are installed (see §1); `externalNativeBuild` in
   `app/build.gradle.kts` is wired and produces `libwhisper.so` for
   `arm64-v8a`. Building locally: `./gradlew assembleDebug`.
3. First launch copies the model from `assets` to `filesDir/models/`
   (`WhisperTranscriber.resolveModelFile` handles the copy).
4. Verify with `./gradlew assembleDebug`.
5. For CI, no extra step — the submodule is fetched automatically by
   `actions/checkout` with `submodules: recursive` (see `.github/workflows/ci.yml`).

Until steps 1–2 are done, tapping "Start recording → Stop" runs the pipeline
but fails at the STT step — expected; see `docs/ROADMAP.md` M1.

## 5. APK size budget

See `docs/NFR.md §2`. Gate: `ls -lh app/build/outputs/apk/release/app-release.apk`
must be ≤ 100 MB or the release CI job fails. Bundle `ggml-base-q8_0.bin`
(~78 MB → ~98 MB APK); `tiny` q8_0 is the low-RAM fallback.

## 6. Common issues

| Symptom | Fix |
|---------|-----|
| `System.loadLibrary("whisper")` UnsatisfiedLinkError | Build & bundle `libwhisper.so` (step 4) |
| Model file not found | Run `bash ml/download-models.sh` + copy to `filesDir/models/` |
| KSP/AGP version conflict | Keep Kotlin 2.3.x + KSP 2.3.x as pinned in the catalog |
| `kapt` requested | This project uses KSP only; do not add kapt (incompatible with built-in Kotlin) |

## 7. CI

The pipeline is the project's **source of reliability** — a green run means the
artifact is trustworthy. `.github/workflows/`:

| Workflow | Purpose |
|----------|---------|
| `ci.yml` | unit tests, lint (warnings-as-errors), debug+release builds with native + ABI audit |
| `repo-integrity.yml` | secrets/keystore scan, wrapper validation, core-purity, no-kapt, offline/ABI/model invariants, no `.md` deletion |
| `docs.yml` | internal markdown links resolve; README CI table matches workflows |
| `action-pin.yml` | every `uses:` pinned to `@vMajor` or SHA (no `@main`/`@latest`) |
| `codeql.yml` | SAST on Kotlin/Java (C++ excluded — NDK not traceable) |
| `dependency-review.yml` | fails on high-severity dependency advisories |
| `release.yml` | tag `v*` → tag-derived version, signed APK, apksigner verify, ≤100 MB, ABI/model/checksum audit, DRAFT GitHub Release; `workflow_dispatch` = dry-run (build+verify, no publish) |
| `firebase-test-lab.yml` | on-device tests on physical arm64 Pixels (gated) |
| `pr-title.yml`, `pr-labeler.yml`, `issue-triage.yml`, `labels.yml`, `good-first-issue.yml` | hygiene |

**Recommended branch protection on `main`** (Settings → Branches): require a PR,
branches up-to-date, conversation resolution, and the required checks:
`unit-tests`, `lint`, `assemble`, `repo-integrity`, `docs-integrity`,
`action-pin`, `dependency-review`, `pr-title`.

## 8. Firebase Test Lab (CI device tests) — deferred setup guide

`firebase-test-lab.yml` runs the on-device instrumented smoke test on real
**physical arm64** Pixel hardware. The job is a **no-op (never fails)** until
you configure it — you can safely defer and finish this later.

> **Cost caveat (important):** since Feb 2026, Firebase Test Lab writes its
> results to a Google Cloud Storage (GCS) bucket, and **creating a new bucket
> (including Firebase's default) requires the paid **Blaze** plan (a linked
> billing account)** — there's no way around it on the free **Spark** plan. So
> plan to enable this only when you have billing (or can reuse an existing
> bucket). It is intentionally OFF by default.

### What to configure (later)

1. **Firebase/GCP project** → enable **Firebase Test Lab** (onboarding).
2. **IAM & Admin → Service Accounts** → create a service account, download its
   **JSON key**.
3. **Least-privilege roles** on that service account:
   - `Cloud Test Lab Admin` (`roles/cloudtestservice.testAdmin`)
   - `Firebase Analytics Viewer` (`roles/firebase.analyticsViewer`)
   - object creator on the results bucket (needed to write results)
   - *(or, simpler but broader: project `Editor` if using the default bucket)*
4. **Secrets** (Settings → Secrets and variables → Actions):
   - `FIREBASE_SERVICE_ACCOUNT` — the service-account **JSON, raw** (do **NOT**
     base64-encode it; our `google-github-actions/auth@v3` reads raw JSON).
   - `FIREBASE_PROJECT_ID` — your Firebase project id.
   - `FIREBASE_RESULTS_BUCKET` — your GCS bucket (required once you're on Blaze).
5. **Repository Variable** (Settings → Secrets and variables → Actions →
   Variables): `RUN_FIREBASE = true`.

> The job only fires when **`RUN_FIREBASE=true` AND** `FIREBASE_SERVICE_ACCOUNT`
> and `FIREBASE_PROJECT_ID` are set — so leaving the variable present without
> the secrets still stays green (it prints "skipping"), and forks are green too.

Trigger: push to `main`, or **Run workflow → Firebase Test Lab**. It runs on
`shiba` (Pixel 8), `panther` (Pixel 7) and `oriole` (Pixel 6); any failed
execution fails the job. Results appear in your bucket / Firebase console.

**Security:** the JSON is a secret — paste it **only** into the
`FIREBASE_SERVICE_ACCOUNT` field, never into an issue, commit, or chat. The
`repo-integrity` workflow also greps for accidentally-committed key files.
