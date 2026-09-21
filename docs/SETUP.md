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

`.github/workflows/ci.yml` runs lint, unit tests, and `assembleDebug` on every
push/PR. See `docs/TESTING.md` for the full gate table. Also present:
`codeql.yml`, `dependency-review.yml`, `pr-title.yml`, `labels.yml`,
`pr-labeler.yml`, `issue-triage.yml`, `good-first-issue.yml`, and
`firebase-test-lab.yml`.

## 8. Firebase Test Lab (CI device tests)

`firebase-test-lab.yml` runs the on-device instrumented smoke test on real
**physical arm64** Pixel hardware (our APK is arm64-only). The job is skipped
unless the secrets are set, so a fork with no Firebase stays green.

Enable it once:

1. Create a **Firebase/GCP project** and enable **Firebase Test Lab**
   (Firebase console → Test Lab → first-run onboarding).
2. **IAM & Admin → Service Accounts** → create a service account, download its
   **JSON key**.
3. Grant it a role so it can run tests:
   - Least-privilege with your own results bucket: `Cloud Test Lab Admin`
     (`roles/cloudtestservice.testAdmin`) + `Firebase Analytics Viewer` and
     object-creator on the bucket, **or**
   - Simplest: project **Editor** (`roles/editor`) if you use the default
     results bucket.
4. Add **repository secrets** (Settings → Secrets and variables → Actions):
   - `FIREBASE_SERVICE_ACCOUNT` — the service-account JSON (base64 or raw)
   - `FIREBASE_PROJECT_ID` — your Firebase project id
   - `FIREBASE_RESULTS_BUCKET` *(optional)* — your own GCS results bucket
5. Push to `main` (or click **Run workflow** → `Firebase Test Lab`) — the job
   builds the APK + test APK, runs them on `shiba` (Pixel 8), `panther`
   (Pixel 7) and `oriole` (Pixel 6), and fails if any execution fails. Results
   appear in your results bucket and the Firebase console.
